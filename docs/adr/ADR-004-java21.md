# ADR-004 — Java 21 LTS como linguagem e plataforma do backend

## Status

**Aceito** em 2026-07-29.
Fundamenta `docs/03-architecture/backend.md` §4.

## Data

2026-07-29

## Contexto

O backend do DevTime é um monólito modular transacional, com domínio rico em regras de cálculo (banco de horas, políticas de rollover, excedente, rateio de período) e alta densidade de estruturas de dados imutáveis (DTOs, snapshots, resultados de cálculo).

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Equipe pequena, sem especialista em runtime alternativo | `docs/01-product/prd.md` |
| R-02 | Implementação majoritária por agentes de IA, que produzem melhor código em ecossistemas com grande volume de material canônico | `docs/ai/` |
| R-03 | Domínio transacional ligado a banco relacional, com alta concorrência de I/O e baixa carga de CPU | `architecture.md` §5 |
| R-04 | Suporte de longo prazo obrigatório: o produto tem horizonte de anos e não pode depender de versão sem correções de segurança | `ART-103` |
| R-05 | Cálculo financeiro exige aritmética exata e tipos precisos | `ART-034`, `ART-040` |

A escolha da versão da plataforma é estruturalmente irreversível dentro de um ciclo de produto: ela determina bibliotecas, sintaxe, ferramentas, imagem base e modelo de concorrência.

## Decisão

| # | Regra |
|---|---|
| JV-01 | O backend usa **Java 21 (LTS)** como *source*, *target* e *runtime*. |
| JV-02 | **Virtual threads** são habilitadas (`spring.threads.virtual.enabled=true`), tornando o modelo de programação bloqueante viável sob alta concorrência. |
| JV-03 | `record` é a forma **padrão** de DTO de entrada e saída, e de todo objeto de valor imutável (resultado de cálculo, chave composta, evento de domínio). |
| JV-04 | *Pattern matching* para `switch` e `sealed interface` são a forma padrão de modelar hierarquias fechadas (políticas, tipos de erro, transições de estado). |
| JV-05 | *Text blocks* são usados para SQL nativo e templates; concatenação de string para SQL é proibida (`A03` de OWASP). |
| JV-06 | A imagem de runtime usa a distribuição **Eclipse Temurin 21** (ver [ADR-020](ADR-020-docker.md)). |
| JV-07 | A atualização para a próxima LTS (Java 25) é avaliada em até 12 meses após sua disponibilidade geral, por ADR próprio. Não há atualização automática para versões *non-LTS*. |
| JV-08 | Recursos em *preview* (`--enable-preview`) são **proibidos** em qualquer ambiente. |
| JV-09 | Bloqueio de thread virtual em `synchronized` deve ser evitado; seções críticas usam `ReentrantLock`. |

## Motivação

**Por que 21 e não 17:**

| Recurso do 21 | Impacto direto no DevTime |
|---|---|
| Virtual threads (JEP 444) | Resolve R-03: o gargalo do produto é espera por I/O de banco, não CPU. Uma thread de plataforma consome ~1 MB de pilha; uma virtual thread, centenas de bytes. Isso permite milhares de requisições concorrentes bloqueantes sem *tuning* de pool nem reescrita reativa. |
| `record` pattern e pattern matching para `switch` (JEP 440/441) | Substitui cadeias `if/instanceof` nas políticas de rollover e excedente por `switch` exaustivo, verificado pelo compilador. |
| Sealed classes (JEP 409, desde 17, maduras no 21) | Permite que o compilador prove que todos os casos de uma hierarquia fechada foram tratados — elimina uma classe inteira de bug em máquinas de estado. |
| Sequenced collections (JEP 431) | API uniforme de primeiro/último elemento, útil em listas ordenadas de períodos e lançamentos. |
| Melhorias de GC (Generational ZGC, JEP 439) | Pausas sub-milissegundo disponíveis se o perfil de latência exigir. |

**Por que virtual threads em vez de programação reativa:** o domínio é transacional e depende de JPA, `@Transactional` e `ThreadLocal` (o `TenantContext` de [ADR-001](ADR-001-multi-tenant.md)). Todo esse ferramental é construído sobre o modelo bloqueante. Virtual threads entregam a escalabilidade de I/O do modelo reativo **preservando** o modelo mental sequencial — o que importa duplamente sob R-02: código sequencial é o que agentes de IA escrevem com menos erro, e é o que um humano depura com *stack trace* legível.

**Por que LTS (R-04):** versões *non-LTS* recebem atualizações por 6 meses. Um produto com ciclo de vida de anos que dependa de uma delas fica sem correção de segurança em menos de um ano — o que colide com `ART-103` (build falha em CVE `HIGH`/`CRITICAL`).

**Por que `record` como padrão (JV-03):** DTOs são estruturas de dados sem comportamento. `record` garante imutabilidade, `equals`/`hashCode`/`toString` corretos por construção e elimina a maior fonte de boilerplate e de bug sutil (setter esquecido, `equals` incompleto) na camada de fronteira.

## Alternativas consideradas

### A1 — Java 17 LTS

| Aspecto | Avaliação |
|---|---|
| **Prós** | Também LTS, com suporte até 2029; ecossistema totalmente maduro; `record` e `sealed` já disponíveis; menor risco percebido. |
| **Contras** | Sem virtual threads — exigiria dimensionamento cuidadoso do pool de threads do Tomcat e limitaria a concorrência a algumas centenas de requisições simultâneas por instância; sem pattern matching completo para `switch`; sem sequenced collections. |
| **Por que foi descartada** | Escolher 17 hoje significa migrar para 21 dentro de 18 meses para obter virtual threads, pagando o custo de migração duas vezes. Não há risco material em 21: é LTS desde setembro de 2023, com todo o ecossistema (Spring Boot 3.2+, Hibernate 6.4+, Testcontainers) suportando-o oficialmente. |

### A2 — Java 25 LTS (última LTS)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Suporte mais longo; recursos adicionais. |
| **Contras** | Ecossistema de bibliotecas de nicho (geração de PDF, POI, agentes de observabilidade, ferramentas de bytecode) tipicamente leva meses para certificar uma nova LTS; risco de incompatibilidade em dependências que o projeto não controla. |
| **Por que foi descartada para o MVP** | O risco de encontrar uma incompatibilidade em biblioteca de terceiros durante F0 não é compensado por nenhum recurso necessário. JV-07 prevê a avaliação formal da migração; a decisão não é "nunca", é "não agora, e por ADR". |

### A3 — Kotlin sobre a JVM

| Aspecto | Avaliação |
|---|---|
| **Prós** | Null-safety no sistema de tipos; sintaxe mais concisa; *data class*, *coroutines*, *extension functions*; excelente suporte no Spring. |
| **Contras** | Base de conhecimento menor, e o material canônico de Spring/Hibernate é majoritariamente em Java — o que degrada a qualidade da geração por agentes de IA (R-02); interoperabilidade com MapStruct e Lombok exige configuração adicional (`kapt`/KSP); tempo de compilação maior; contratação e substituição de pessoas mais difícil (R-01). |
| **Por que foi descartada** | O ganho principal (null-safety) é parcialmente obtido em Java com Bean Validation, `Optional` e anotações de nulidade. O custo (qualidade da geração por IA e liquidez de conhecimento) incide sobre o principal modo de produção do projeto. |

### A4 — Node.js / TypeScript

| Aspecto | Avaliação |
|---|---|
| **Prós** | Linguagem única entre frontend e backend; ecossistema enorme; *startup* rápido. |
| **Contras** | Sem equivalente maduro a JPA/Hibernate com filtros automáticos por tenant (camada 2 de [ADR-001](ADR-001-multi-tenant.md) exigiria implementação própria); aritmética numérica em `number` é IEEE-754, hostil a R-05; transações declarativas e AOP não são idiomáticas; modelo single-thread com CPU-bound (geração de PDF/XLSX) exige workers. |
| **Por que foi descartada** | As duas garantias mais críticas do produto — isolamento automático por tenant e aritmética exata — teriam de ser construídas e mantidas manualmente, sem rede de proteção do ecossistema. |

### A5 — Go

| Aspecto | Avaliação |
|---|---|
| **Prós** | Binário único e leve; concorrência nativa; consumo de memória baixo; *startup* imediato. |
| **Contras** | Sem ORM com o nível de automação do Hibernate (filtros, listeners, *dirty checking*); ecossistema de relatórios/PDF/XLSX mais fraco; ausência de generics maduros para abstrações de repositório na época da avaliação; menos material canônico para o domínio de aplicações de negócio transacionais. |
| **Por que foi descartada** | O produto é dominado por regra de negócio e persistência, não por throughput bruto. O ganho de eficiência de runtime não compensa a perda de automação de persistência e tenancy. |

### A6 — Java 21 com GraalVM Native Image

| Aspecto | Avaliação |
|---|---|
| **Prós** | *Startup* em milissegundos; consumo de memória muito menor; imagem menor. |
| **Contras** | Compilação AOT lenta (minutos por build); reflexão exige configuração explícita, quebrando bibliotecas que a usam dinamicamente; depuração e *profiling* em produção limitados; **incompatível com virtual threads** de forma plena na época da decisão. |
| **Por que foi descartada** | Os benefícios (startup, memória) importam em serverless e em escala de muitas instâncias efêmeras — cenário que a decisão de monólito modular não produz. O custo em depurabilidade é alto justamente durante o MVP. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Alta concorrência de I/O sem código reativo e sem *tuning* fino de pool (JV-02). |
| C+02 | DTOs imutáveis por construção, reduzindo bug de estado compartilhado (JV-03). |
| C+04 | `switch` exaustivo verificado pelo compilador em políticas e máquinas de estado (JV-04). |
| C+05 | *Stack traces* completos e legíveis — vantagem decisiva sobre reativo em depuração de produção. |
| C+06 | Suporte de segurança e correções garantido por anos (R-04). |
| C+07 | Ecossistema com material canônico abundante, elevando a qualidade da geração por agentes (R-02). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Consumo de memória superior a Go/Node para carga equivalente. | Custo de RAM é baixo frente ao custo de engenharia. |
| C-02 | *Startup* de segundos, não milissegundos. | Irrelevante em serviço de execução contínua; relevante apenas em serverless, descartado. |
| C-03 | Virtual threads exigem atenção a *pinning* em `synchronized` (JV-09). | Regra explícita e verificável; `ReentrantLock` é o padrão. |
| C-04 | Bibliotecas que usam `ThreadLocal` de forma pesada podem se comportar mal com milhões de virtual threads. | O `TenantContext` é `ThreadLocal` por requisição, com ciclo de vida curto e limpeza garantida no filtro. |
| C-05 | Verbosidade maior que Kotlin. | Mitigada por `record`, Lombok e MapStruct. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Virtual threads não aceleram trabalho CPU-bound (geração de PDF e XLSX continuam limitadas por CPU). |
| L-02 | Recursos em *preview* são inacessíveis (JV-08), incluindo *structured concurrency* enquanto estiver em preview. |
| L-03 | A migração para a próxima LTS será um evento planejado, não contínuo. |

### Custos

| Item | Custo |
|---|---|
| Licença | Zero (Temurin, GPLv2 + Classpath Exception) |
| Memória | ~512 MB–1 GB por instância em operação típica |
| Migração futura | Estimada em 3–5 dias por LTS, incluindo atualização de dependências |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Eficiência de runtime** (memória, startup) de Go/Node/Native | Automação de persistência, tenancy e transações | O custo dominante do projeto é engenharia e risco de falha de isolamento, não RAM. |
| **Concisão** de Kotlin | Liquidez de conhecimento e qualidade da geração por IA | R-02 é característica estrutural do projeto, não preferência. |
| **Maturidade extra** do Java 17 | Virtual threads e pattern matching completo | 21 é LTS há tempo suficiente e é suportado por todo o stack escolhido. |
| **Recursos mais recentes** do Java 25 | Estabilidade do ecossistema no MVP | Decisão adiada com critério explícito (JV-07), não descartada. |
| **Throughput reativo teórico** máximo | Legibilidade, depurabilidade e compatibilidade com JPA | Virtual threads recuperam a maior parte do throughput sem o custo cognitivo. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| Todo o backend | Linguagem, sintaxe e modelo de concorrência. |
| `shared/tenancy` | `TenantContext` implementado sobre `ThreadLocal`, compatível com virtual threads (herdado por `InheritableThreadLocal` não é usado; o contexto é populado por filtro em cada requisição). |
| `*/dto` | `record` obrigatório (JV-03). |
| `contract/period` | Políticas de rollover e excedente modeladas como `sealed interface` + pattern matching (JV-04). |
| `shared/error` | Hierarquia de exceções de negócio como `sealed`. |
| `report` | Geração de PDF/XLSX é CPU-bound; não se beneficia de JV-02 (L-01) e é isolada em pool próprio. |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/backend.md` §4 | Tabela de stack e versões |
| `docs/ai/backend-rules.md` | `BR-100` a `BR-119` (DTOs como record) |
| `docs/06-testing/strategy.md` | Compatibilidade de JUnit 5 e Testcontainers |

| Spec dependente | Relação |
|---|---|
| Todas as specs de backend | Artefatos de código declarados como `record`, `sealed`, etc. |

| ADR relacionado | Relação |
|---|---|
| [ADR-005](ADR-005-spring-boot.md) | Spring Boot 3 exige Java 17+; MVC + virtual threads |
| [ADR-013](ADR-013-dto.md) | DTOs como `record` |
| [ADR-020](ADR-020-docker.md) | Imagem base Temurin 21 |
| [ADR-040](ADR-040-cache-strategy.md) | Cache local em heap dimensionado pela memória da JVM |

## Impacto no banco

Não se aplica diretamente, porque a versão da linguagem não altera o modelo de dados. Dois efeitos indiretos existem:

| Efeito | Descrição |
|---|---|
| Concorrência de conexões | Virtual threads permitem muito mais requisições simultâneas do que conexões no pool. O pool (HikariCP) passa a ser o **limitador efetivo** de concorrência e deve ser dimensionado conscientemente — caso contrário, virtual threads apenas enfileiram esperas por conexão. |
| Tipos | `java.time` (`OffsetDateTime`, `LocalDate`) mapeia diretamente para `TIMESTAMPTZ` e `DATE` (`ART-030`, `ART-031`). |

## Impacto na API

Não se aplica ao contrato, porque a API é definida por [ADR-011](ADR-011-rest-api.md) e por `docs/04-api/`, independentemente da linguagem. Impacto indireto: `record` como DTO de resposta produz serialização Jackson previsível e imutável, o que reforça a estabilidade do contrato.

## Impacto no Frontend

Não se aplica, porque o frontend consome apenas HTTP/JSON e é independente da plataforma do backend ([ADR-011](ADR-011-rest-api.md), [ADR-022](ADR-022-angular.md)).

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Imagem | `eclipse-temurin:21-jre-alpine` (ou variante *distroless*) na etapa final do multi-stage ([ADR-020](ADR-020-docker.md)). |
| Memória | Limite de contêiner definido; a JVM detecta *cgroup* e ajusta o heap automaticamente (`-XX:MaxRAMPercentage`). |
| GC | G1 por padrão; Generational ZGC avaliado apenas se a métrica de pausa exigir. |
| CI | Toolchain fixada em 21 no `pom.xml` e na action de setup, para eliminar divergência entre máquinas ([ADR-030](ADR-030-github-actions.md)). |
| Observabilidade | Agente OpenTelemetry Java compatível com 21 e com virtual threads ([ADR-046](ADR-046-observability.md)). |

## Segurança

| # | Consideração |
|---|---|
| S-01 | LTS garante fluxo contínuo de correções de segurança da plataforma — pré-requisito de `ART-103`. |
| S-02 | *Text blocks* (JV-05) reduzem a tentação de concatenar SQL, mitigando injeção (OWASP A03). Concatenação continua proibida. |
| S-03 | Imutabilidade de `record` elimina alteração acidental de DTO após validação — uma janela clássica de TOCTOU na camada de fronteira. |
| S-04 | **Multi-tenant:** virtual threads não compartilham `ThreadLocal` entre si; o `TenantContext` é populado e limpo por requisição, sem risco de vazamento entre tenants — mas a limpeza no `finally` do filtro é **obrigatória**. |
| S-05 | **LGPD:** `record` gera `toString()` com todos os campos. DTOs que carreguem dado sensível devem sobrescrever `toString()` ou nunca ser logados (`ART-084`). |
| S-06 | **Auditoria:** nenhum impacto direto; a trilha é independente da linguagem. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Virtual threads elevam a concorrência de I/O em ordens de magnitude sem alterar o código. |
| P-02 | O limitador efetivo passa a ser o pool de conexões e o próprio PostgreSQL — o dimensionamento correto do pool é agora **mais** importante, não menos. |
| P-03 | JIT (C2) atinge desempenho de pico após aquecimento; irrelevante em serviço de longa duração. |
| P-04 | `record` evita alocações intermediárias em comparação a *builders* mutáveis. |
| P-05 | Metas AQ-01 (p95 < 800 ms) e AQ-02 (< 200 ms percebido) são compatíveis com a plataforma; o gargalo esperado é sempre o banco. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Escala vertical: uma instância com virtual threads atende milhares de requisições concorrentes limitadas por I/O. |
| E-02 | Escala horizontal: instâncias stateless (`ART-080`), replicáveis sem coordenação. |
| E-03 | O pool de conexões por instância deve ser dimensionado considerando `instâncias × pool ≤ max_connections`; caso contrário a escala horizontal derruba o banco. Este é o principal limite de escala do modelo. |
| E-04 | Trabalho CPU-bound (relatórios) escala apenas com CPU; a extração para workers está prevista em F6 ([ADR-042](ADR-042-rabbitmq.md)). |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | *Pinning* de virtual thread em `synchronized` degrada a concorrência silenciosamente | Média | Médio | Média |
| RK-02 | Pool de conexões subdimensionado transforma o ganho de concorrência em fila de espera | Alta | Médio | Alta |
| RK-03 | `ThreadLocal` do `TenantContext` não limpo vaza contexto entre requisições | Baixa | Crítico | **Alta** |
| RK-04 | Biblioteca de terceiros incompatível com 21 | Baixa | Médio | Baixa |
| RK-05 | Fim de suporte do Java 21 sem plano de migração | Baixa | Alto | Média |
| RK-06 | `toString()` de `record` expor dado sensível em log | Média | Alto | Alta |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | JV-09 proíbe `synchronized` em seções que bloqueiam; `-Djdk.tracePinnedThreads=full` em staging; métrica de threads *pinned* | [ADR-047](ADR-047-monitoring.md) |
| RK-02 | Pool dimensionado explicitamente e documentado; métrica de tempo de espera por conexão com alerta; teste de carga (§9.1 de `strategy.md`) | Teste de carga |
| RK-03 | Limpeza no bloco `finally` do `TenantContextFilter`; teste que executa duas requisições de tenants distintos na mesma thread e verifica o isolamento | `TenantIsolationIT` |
| RK-04 | Verificação de compatibilidade antes de adicionar dependência (§9 da constituição); build em CI com Java 21 desde F0 | Pipeline |
| RK-05 | JV-07 fixa o gatilho de reavaliação em 12 meses após a GA da próxima LTS | Revisão de ADR |
| RK-06 | DTOs com dado sensível sobrescrevem `toString()`; filtro de máscara no appender ([ADR-019](ADR-019-logging.md)); revisão bloqueante (`ART-084`) | `review-checklist.md` |

## Referências

| Fonte | Uso |
|---|---|
| [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) | Base de JV-02 |
| [JEP 440/441 — Record Patterns e Pattern Matching for switch](https://openjdk.org/jeps/441) | Base de JV-04 |
| [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409) | Modelagem de hierarquias fechadas |
| [Oracle — Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) | Janela de suporte LTS |
| [Eclipse Temurin](https://adoptium.net/temurin/releases/) | Distribuição adotada (JV-06) |
| [Spring Boot — Virtual Threads support](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html) | Habilitação em Spring |
| [Oracle — Virtual Threads: pinning](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) | Base de JV-09 e RK-01 |
