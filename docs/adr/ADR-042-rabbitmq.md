# ADR-042 — RabbitMQ para eventos assíncronos e workers a partir de F6

## Status

**Proposto** em 2026-07-29.
**Não é vinculante e não pode ser implementado** enquanto não for aceito (ADR-U02 do `README.md` deste diretório).
Alvo: fase **F6 — SaaS Comercial**.

## Data

2026-07-29

## Contexto

No MVP, eventos de domínio são publicados por `ApplicationEventPublisher` e consumidos **no mesmo processo**, dentro da transação ou após o commit (JB-11 de [ADR-039](ADR-039-background-jobs.md)). Essa escolha foi deliberada: mensageria adicionaria um contêiner e uma classe de falha sem ganho no volume esperado.

Três cargas, porém, têm perfil de recurso distinto do tráfego transacional e são candidatas naturais a workers:

| Carga | Perfil | Referência |
|---|---|---|
| Geração de relatórios (PDF, XLSX) | CPU-intensa, minutos | [ADR-036](ADR-036-report-generation.md) |
| Envio de e-mail | I/O externo, latência variável, retry | [ADR-037](ADR-037-notification-strategy.md) |
| Verificação antivírus de anexos | CPU e I/O, latência variável | [ADR-038](ADR-038-file-storage.md) |

`architecture.md` §13 já prevê RabbitMQ e workers em F6, e define a ordem de extração de módulos: relatórios → notificações → IA.

Restrições e premissas:

| # | Premissa |
|---|---|
| P-01 | Eventos que **devem** ser consistentes com a operação continuam síncronos, dentro da transação (JB-11) |
| P-02 | A abstração `DomainEventPublisher` já isola o mecanismo de publicação (JB-12) |
| P-03 | PostgreSQL permanece a única fonte de verdade (DA-01) |
| P-04 | O isolamento entre tenants continua inviolável ([ADR-001](ADR-001-multi-tenant.md)) |

## Decisão

**Proposta:** introduzir RabbitMQ em F6 para **processamento assíncrono de trabalho**, não para comunicação entre módulos do domínio.

| # | Regra proposta |
|---|---|
| MQ-01 | RabbitMQ é adotado para **descarregar trabalho** de perfil distinto do transacional: geração de relatórios, envio de e-mail e verificação antivírus. |
| MQ-02 | Eventos que exigem **consistência com a operação** permanecem **síncronos e dentro da transação** (P-01). A fila **nunca** é usada para manter invariantes de domínio. |
| MQ-03 | A publicação usa o padrão **transactional outbox**: o evento é gravado em uma tabela na **mesma transação** da operação de negócio, e um processador o publica na fila depois. Nunca se publica na fila dentro da transação. |
| MQ-04 | O consumo é **idempotente** por construção, com chave de idempotência por mensagem — a mesma regra de JB-04 de [ADR-039](ADR-039-background-jobs.md). Entrega "ao menos uma vez" é o modelo assumido. |
| MQ-05 | Toda fila possui **dead-letter queue** e política de retry com backoff. Mensagem que esgota as tentativas vai para a DLQ e gera alerta; nunca é descartada silenciosamente. |
| MQ-06 | Toda mensagem carrega `tenantId`, `traceId` e `schemaVersion`. O worker **estabelece o contexto de tenant** antes de processar (P-04). |
| MQ-07 | O **payload da mensagem contém referências, não dados**: identificadores e o mínimo de contexto. O worker lê o estado atual do banco. Isso evita mensagens grandes, dados obsoletos e vazamento de dado sensível para a fila. |
| MQ-08 | Os **workers rodam o mesmo artefato** da aplicação, sob perfil `worker`, exatamente como o scheduler (JB-01 de [ADR-039](ADR-039-background-jobs.md)). |
| MQ-09 | A aplicação **continua funcionando** com o RabbitMQ indisponível: os eventos acumulam na tabela de outbox e são publicados quando o serviço voltar. Nenhuma operação de negócio falha por indisponibilidade da fila. |
| MQ-10 | O **agendamento periódico permanece no scheduler** ([ADR-039](ADR-039-background-jobs.md)); a fila recebe o **trabalho** disparado por ele, não o agendamento. |
| MQ-11 | O contrato de mensagem é versionado (`schemaVersion`) e evolui de forma compatível: consumidores toleram campos novos e desconhecidos. |
| MQ-12 | A introdução exige atualizar [ADR-036](ADR-036-report-generation.md), [ADR-037](ADR-037-notification-strategy.md), [ADR-038](ADR-038-file-storage.md) e [ADR-039](ADR-039-background-jobs.md) com o novo mecanismo, **sem alterar o comportamento observável**. |

### Gatilhos objetivos de adoção

| # | Gatilho |
|---|---|
| GT-01 | Geração de relatórios degradando a latência p95 da API (AQ-01 em risco) |
| GT-02 | Necessidade de escalar workers independentemente da API |
| GT-03 | Entrada em F6 com extração planejada do módulo de relatórios (`architecture.md` §13) |
| GT-04 | Volume de e-mails exigindo retry e DLQ mais sofisticados que o processador atual |

```mermaid
flowchart TD
    A["Operação de negócio"] --> B["Transação: grava estado + outbox (MQ-03)"]
    B --> C{Commit}
    C -->|falha| D["Nada publicado"]
    C -->|sucesso| E["Processador de outbox publica na fila"]
    E --> F[("RabbitMQ")]
    F --> G["Worker (perfil worker, MQ-08)"]
    G --> H["Estabelece contexto de tenant (MQ-06)"]
    H --> I["Lê estado atual do banco (MQ-07)"]
    I --> J{Processa — idempotente (MQ-04)}
    J -->|falha| K["Retry com backoff"]
    K -->|esgotado| L["DLQ + alerta (MQ-05)"]
    F -.indisponível.-> M["Outbox acumula (MQ-09)"]
```

## Motivação

**Por que fila para trabalho, não para domínio (MQ-01/MQ-02) — a distinção central:** a tentação ao adotar mensageria é mover toda a comunicação entre módulos para a fila. Isso trocaria consistência forte por consistência eventual em um domínio **financeiro**, onde o fechamento de período (RN-241) precisa ser atômico. A decisão limita a fila ao que é genuinamente assíncrono: trabalho que o usuário não espera de forma síncrona e cuja falha não invalida a operação de negócio.

**Por que transactional outbox (MQ-03):** publicar na fila **dentro** da transação cria o problema clássico do commit duplo — a transação pode falhar após a publicação (evento sobre fato que não aconteceu) ou a publicação pode falhar após o commit (fato sem evento). O outbox resolve gravando o evento na mesma transação: se a transação falha, o evento não existe; se tem sucesso, o evento está durável e será publicado. Isso também é o que viabiliza MQ-09.

**Por que idempotência no consumo (MQ-04):** RabbitMQ garante entrega "ao menos uma vez". Reentregas acontecem em reconexão, timeout de confirmação e reinício de worker. Um consumidor não idempotente gera relatório duplicado, e-mail duplicado ou verificação duplicada. Assim como em [ADR-039](ADR-039-background-jobs.md), **idempotência é a garantia; a fila é a otimização.**

**Por que payload com referências (MQ-07):** três razões. Mensagem pequena é mais barata e mais rápida. O estado lido no momento do processamento é o atual, não o do momento da publicação. E — decisivo — nenhum dado sensível trafega para um sistema que persiste mensagens em disco e as mantém em DLQ potencialmente por dias.

**Por que mesmo artefato (MQ-08):** mesma justificativa de JB-01: evita duplicar pipeline, imagem e configuração, e garante que worker e API executem exatamente a mesma versão da regra de negócio. O perfil `worker` permite escalar workers independentemente das instâncias de API.

**Por que degradação obrigatória (MQ-09):** uma fila indisponível não pode impedir o registro de horas. Com o outbox, os eventos acumulam de forma durável e são publicados quando o serviço retorna — a operação de negócio nunca é afetada.

**Por que o agendamento permanece no scheduler (MQ-10):** fila resolve **processamento assíncrono**, não **agendamento periódico**. Os treze jobs continuam sendo disparados por `@Scheduled` com ShedLock; o que muda é que alguns deles passam a **enfileirar** trabalho em vez de executá-lo diretamente.

## Alternativas consideradas

### A1 — Permanecer com eventos síncronos no processo (estado atual)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Nenhuma infraestrutura; consistência forte; depuração trivial; sem contrato de mensagem a versionar. |
| **Contras** | Trabalho pesado (relatórios) concorre com o tráfego transacional; sem escala independente; retry limitado ao que o processador implementa. |
| **Por que não é a decisão** | É a decisão **atual** e permanece válida até que um gatilho seja observado. Este ADR define quando substituí-la. |

### A2 — Apache Kafka

| Aspecto | Avaliação |
|---|---|
| **Prós** | Altíssimo throughput; retenção de log permitindo reprocessamento histórico; particionamento com ordenação por chave; base para *event sourcing* e analytics. |
| **Contras** | Complexidade operacional substancialmente maior; conceitos adicionais (partições, grupos de consumidores, offsets, compactação); dimensionado para volumes ordens de magnitude acima do previsto; sem DLQ nativa (exige implementação). |
| **Por que foi descartada** | Kafka é um **log distribuído**, ideal para streaming e reprocessamento. A necessidade aqui é uma **fila de trabalho** com retry e DLQ — exatamente o que RabbitMQ faz de forma mais simples. Kafka seria revisitado apenas se surgir necessidade de streaming de eventos ou de reprocessamento histórico. |

### A3 — Fila em tabela do PostgreSQL (`SELECT ... FOR UPDATE SKIP LOCKED`)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Nenhuma infraestrutura nova; transacional com o resto do sistema (o outbox **é** a fila); durável por construção; `SKIP LOCKED` funciona bem. |
| **Contras** | Polling em vez de push, com latência mínima igual ao intervalo; carga adicional de escrita e de `VACUUM` no banco transacional; sem roteamento, sem prioridade, sem DLQ nativa; escala limitada pela capacidade do banco. |
| **Por que não foi adotada** | É, na prática, **o que o MVP já faz** (a tabela `notifications` funciona como fila, e o outbox de MQ-03 é uma fila em tabela). A migração para RabbitMQ só se justifica quando os gatilhos aparecerem; até lá, esta é a resposta correta e está registrada em [ADR-037](ADR-037-notification-strategy.md) A4. |

### A4 — Serviço gerenciado de fila do provedor de nuvem

| Aspecto | Avaliação |
|---|---|
| **Prós** | Sem operação; escala automática; alta disponibilidade gerenciada; custo por uso. |
| **Contras** | Aprisionamento ao provedor; ambiente local exigiria emulador; semântica varia entre provedores; custo por mensagem em alto volume. |
| **Por que foi descartada como decisão** | Não é incompatível: se o provedor oferecer um serviço compatível com AMQP, ele atende esta decisão. A escolha entre RabbitMQ gerenciado e serviço nativo do provedor é de **infraestrutura**, decidida na implementação, desde que MQ-03 a MQ-11 sejam preservados. |

### A5 — Mover toda a comunicação entre módulos para a fila

| Aspecto | Avaliação |
|---|---|
| **Prós** | Desacoplamento máximo; prepara a extração de todos os módulos; escala independente por módulo. |
| **Contras** | Consistência eventual em operações que exigem atomicidade (RN-241); invariantes de domínio deixariam de ser garantidas por transação; depuração muito mais difícil; complexidade desproporcional. |
| **Por que foi descartada** | Viola diretamente a justificativa do monólito modular (`architecture.md` §6, ADR legado 001): o fechamento de período em microsserviços exigiria saga com compensação, elevando o risco em operação financeira. MQ-02 preserva essa fronteira. |

## Consequências

### Positivas (esperadas após a aceitação)

| # | Consequência |
|---|---|
| C+01 | Geração de relatórios deixa de concorrer com o tráfego transacional. |
| C+02 | Workers escalam independentemente da API. |
| C+03 | Retry com backoff e DLQ nativos, mais robustos que o processador atual. |
| C+04 | Outbox garante que nenhum evento se perca (MQ-03). |
| C+05 | Prepara a extração do módulo de relatórios (`architecture.md` §13). |
| C+06 | Picos de trabalho são absorvidos pela fila em vez de degradar a API. |
| C+07 | O domínio permanece inalterado (P-02, MQ-12). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Um serviço a mais para operar, monitorar e proteger. | Justificado apenas após os gatilhos. |
| C-02 | Depuração mais difícil: o fluxo atravessa processos. | Mitigado por `traceId` propagado em toda mensagem (MQ-06). |
| C-03 | Contrato de mensagem a versionar e manter compatível. | MQ-11 estabelece a regra. |
| C-04 | Idempotência obrigatória em cada consumidor. | Mesma disciplina já exigida dos jobs (JB-04). |
| C-05 | Outbox adiciona uma tabela e um processador. | É o que garante MQ-03 e MQ-09. |
| C-06 | Consistência eventual no que for movido para a fila. | Limitado por MQ-02 ao que tolera. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Não substitui transação: invariantes de domínio continuam garantidas pelo banco (MQ-02). |
| L-02 | Não oferece retenção histórica nem reprocessamento de eventos antigos (isso seria Kafka, A2). |
| L-03 | Ordenação entre mensagens não é garantida de forma geral; consumidores não podem depender dela. |

### Custos

| Item | Custo |
|---|---|
| Infraestrutura | Instância de RabbitMQ por ambiente |
| Implementação | ~5 dias (outbox, processador, workers, DLQ, monitoramento) |
| Operação | Monitoramento de filas, DLQ e consumidores |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Simplicidade** da topologia | Isolamento de carga pesada | Trocado apenas após um gatilho comprovar a necessidade. |
| **Consistência forte** no que for movido | Escala e resiliência | Limitado por MQ-02 ao que tolera consistência eventual. |
| **Depurabilidade** de fluxo em processo único | Escala independente | Mitigado por rastreamento distribuído. |
| **Throughput** e retenção do Kafka | Simplicidade operacional | O caso de uso é fila de trabalho, não streaming. |
| **Ausência de contrato de mensagem** | Desacoplamento entre produtor e consumidor | MQ-11 torna a evolução gerenciável. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `shared/event` | `DomainEventPublisher` passa a gravar no outbox; processador publica na fila. |
| `shared/messaging` | Configuração de filas, exchanges, DLQ, consumidores. |
| `report` | Geração movida para worker. |
| `notification` | Envio de e-mail movido para worker. |
| `attachment` | Verificação antivírus movida para worker. |
| Domínio | **Nenhum impacto** (P-02, MQ-12). |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/architecture.md` §5, §6, §13 | Eventos e evolução |
| `docs/03-architecture/integrations.md` | Padrão de resiliência |

| Spec dependente | Relação |
|---|---|
| `specs/012-reports`, `specs/013-notifications`, `specs/015-attachments` | Passam a executar em worker |

| ADR relacionado | Relação |
|---|---|
| [ADR-039](ADR-039-background-jobs.md) | Agendamento permanece; eventos migram (JB-12) |
| [ADR-036](ADR-036-report-generation.md) | Geração em worker |
| [ADR-037](ADR-037-notification-strategy.md) | Entrega em worker (NT-12) |
| [ADR-038](ADR-038-file-storage.md) | Antivírus em worker |
| [ADR-041](ADR-041-redis.md) | Também de F6; decisões independentes |
| [ADR-046](ADR-046-observability.md) | `traceId` propagado nas mensagens |

## Impacto no banco

| Item | Impacto |
|---|---|
| Tabela | `outbox_events (id, tenant_id, aggregate_type, aggregate_id, event_type, payload, schema_version, created_at, published_at, attempts)`. |
| Índice | `(published_at, created_at)` para o processador; parcial `WHERE published_at IS NULL`. |
| Escrita | Um `INSERT` adicional por evento, na mesma transação da operação. |
| Limpeza | Eventos publicados removidos por job após período de carência. |
| Fonte de verdade | Inalterada (P-03): o worker lê o estado atual do banco (MQ-07). |

## Impacto na API

| Item | Impacto |
|---|---|
| Contrato | Nenhuma mudança de endpoint. |
| Comportamento | Operações que já eram assíncronas ([ADR-036](ADR-036-report-generation.md) RP-08) permanecem com o mesmo contrato de recurso de execução. |
| Latência | Verificação de antivírus e envio de e-mail podem ficar ligeiramente mais lentos no pior caso (fila) e mais rápidos no caso comum (não bloqueiam). |
| Estados | Estados já existentes (`PENDING`, `PROCESSING`, `DONE`, `FAILED`) continuam sendo o contrato observável. |

## Impacto no Frontend

Não se aplica diretamente. Efeito indireto: o frontend já lida com operações assíncronas por recurso de execução ([ADR-036](ADR-036-report-generation.md)) e com estado de verificação de anexo ([ADR-038](ADR-038-file-storage.md)); nenhum ajuste é necessário se MQ-12 for respeitado.

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Serviço | RabbitMQ gerenciado ou em contêiner, na rede privada. |
| Local | Contêiner adicionado ao Docker Compose sob perfil ([ADR-021](ADR-021-docker-compose.md) DC-10). |
| Workers | Instâncias do mesmo artefato com perfil `worker` (MQ-08), escaláveis independentemente. |
| Filas | Uma por tipo de trabalho, cada uma com DLQ (MQ-05). |
| Monitoramento | Profundidade de fila, taxa de consumo, idade da mensagem mais antiga, tamanho da DLQ, conexões. |
| Alertas | Mensagem em DLQ; fila crescendo sem consumo; outbox com eventos não publicados há muito tempo. |
| Segurança | Autenticação, TLS, usuário por aplicação com permissões mínimas; nunca exposto publicamente. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | MQ-07: payload com referências, não dados. A fila persiste mensagens em disco e a DLQ pode retê-las por dias — dado sensível ali seria exposição prolongada. |
| S-02 | Autenticação e TLS obrigatórios; RabbitMQ nunca exposto publicamente; painel de administração restrito. |
| S-03 | Usuário da aplicação com permissões mínimas (publicar nas filas que usa, consumir das que consome). |
| S-04 | Mensagem envenenada (que sempre falha) vai para a DLQ e não trava o consumidor (MQ-05). |
| S-05 | **Multi-tenant:** MQ-06 exige `tenantId` em toda mensagem, e o worker **estabelece o contexto** antes de processar. Um worker que processe sem contexto de tenant é falha crítica. |
| S-06 | **LGPD:** com MQ-07, nenhum dado pessoal trafega ou é retido na fila. |
| S-07 | **Auditoria:** o worker executa com ator de sistema identificado e gera trilha normalmente ([ADR-018](ADR-018-auditing.md)); a mensagem carrega o `traceId` da operação original, permitindo correlação. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Trabalho pesado sai do caminho da requisição, protegendo AQ-01. |
| P-02 | O outbox adiciona um `INSERT` por evento; desprezível frente à operação de negócio. |
| P-03 | O processador de outbox opera em lotes, com intervalo curto. |
| P-04 | Workers escalam horizontalmente conforme a profundidade da fila. |
| P-05 | Picos de trabalho são absorvidos pela fila, transformando pico de latência em pico de fila. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Workers escalam independentemente da API (C+02). |
| E-02 | RabbitMQ suporta volume ordens de magnitude acima do previsto. |
| E-03 | Prepara a extração do módulo de relatórios como serviço independente (`architecture.md` §13). |
| E-04 | Filas separadas por tipo de trabalho permitem dimensionar consumidores por perfil de carga. |
| E-05 | Se surgir necessidade de streaming ou reprocessamento histórico, Kafka (A2) é reavaliado por ADR próprio. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Consumidor não idempotente processando mensagem duplicada | Média | Alto | **Alta** |
| RK-02 | Fila usada para manter invariante de domínio, violando MQ-02 | Média | **Crítico** | **Crítica** |
| RK-03 | RabbitMQ indisponível bloqueando operações de negócio | Baixa | Crítico | **Alta** |
| RK-04 | Mensagens acumulando em DLQ sem tratamento | Média | Médio | Média |
| RK-05 | Dado sensível no payload da mensagem | Média | Alto | Alta |
| RK-06 | Worker processando sem contexto de tenant | Baixa | **Crítico** | **Alta** |
| RK-07 | Contrato de mensagem incompatível durante deploy gradual | Média | Médio | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | MQ-04 obrigatória, com chave de idempotência; teste que entrega a mesma mensagem duas vezes e verifica efeito único | Teste de idempotência |
| RK-02 | MQ-02 explícita; revisão do Arquiteto obrigatória para cada novo consumidor; nenhum evento que sustente invariante pode ir para a fila | Revisão arquitetural |
| RK-03 | MQ-03 + MQ-09: o outbox é durável e a operação de negócio nunca depende da fila; teste de resiliência com RabbitMQ indisponível | Teste de resiliência |
| RK-04 | Alerta imediato ao primeiro item na DLQ; procedimento de reprocessamento documentado | [ADR-047](ADR-047-monitoring.md) |
| RK-05 | MQ-07 explícita; revisão de cada tipo de mensagem; teste que inspeciona os payloads publicados | Teste de contrato de mensagem |
| RK-06 | MQ-06; classe base de worker que **exige** o contexto antes de executar; teste de isolamento no worker | Teste de isolamento |
| RK-07 | MQ-11: consumidores toleram campos desconhecidos; `schemaVersion` na mensagem; teste de compatibilidade entre versões | Teste de deploy gradual |

## Referências

| Fonte | Uso |
|---|---|
| [RabbitMQ — Documentation](https://www.rabbitmq.com/docs) | Referência |
| [RabbitMQ — Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx) | MQ-05 |
| [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html) | MQ-03 |
| [Idempotent Consumer Pattern](https://microservices.io/patterns/communication-style/idempotent-consumer.html) | MQ-04 |
| [Apache Kafka](https://kafka.apache.org/documentation/) | Alternativa A2 |
| [PostgreSQL — `SKIP LOCKED`](https://www.postgresql.org/docs/16/sql-select.html#SQL-FOR-UPDATE-SHARE) | Alternativa A3 |
| [Spring AMQP](https://docs.spring.io/spring-amqp/reference/) | Integração |
| `docs/03-architecture/architecture.md` §13 | Evolução planejada |
