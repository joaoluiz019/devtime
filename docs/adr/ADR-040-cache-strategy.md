# ADR-040 — Cache local em duas camadas, com chave sempre prefixada por tenant e invalidação por evento

## Status

**Aceito** em 2026-07-29.
Fase F2. Evolui para cache distribuído em F6 ([ADR-041](ADR-041-redis.md)).

## Data

2026-07-29

## Contexto

Algumas leituras se repetem com alta frequência e mudam raramente:

| Dado | Frequência de leitura | Frequência de mudança |
|---|---|---|
| `passwordChangedAt` e `roleChangedAt` (JW-08/JW-09) | **Toda requisição autenticada** | Raríssima |
| Membership ativo e papel | Toda requisição autenticada | Rara |
| Configuração do tenant (fuso, plano, preferências) | Muitas requisições | Rara |
| Categorias e tags do tenant | Listagens e formulários | Rara |
| Agregações do dashboard | Alta em horário de trabalho | A cada work log |
| Contagem de notificações não lidas | Polling de todos os usuários ativos | Média |

Ao mesmo tempo, o domínio é financeiro: saldo de banco de horas exibido de cache desatualizado leva o usuário a decisões erradas.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | PostgreSQL é a única fonte de verdade; nenhum dado de negócio vive exclusivamente em cache | DA-01, `ART-080` |
| R-02 | Sem Redis no MVP | `architecture.md` §5 |
| R-03 | Instâncias são stateless e replicáveis | `ART-080` |
| R-04 | Dashboard p95 < 800 ms com 100k work logs | AQ-01, F2-04 |
| R-05 | Isolamento entre tenants é inviolável | [ADR-001](ADR-001-multi-tenant.md) |

## Decisão

| # | Regra |
|---|---|
| CA-01 | O cache do MVP é **local ao processo** (in-heap, Caffeine), acessado pela abstração de cache do Spring. Não há cache distribuído (R-02). |
| CA-02 | **Toda chave de cache é prefixada pelo `tenantId`**, sem exceção. Uma chave sem prefixo de tenant é falha de segurança bloqueante (R-05). |
| CA-03 | Existem **duas camadas** com políticas distintas: **camada A — dados de sessão e configuração** (TTL muito curto, 30–60 s) e **camada B — dados de referência do tenant** (TTL de minutos, com invalidação por evento). |
| CA-04 | **Nada financeiro é cacheado com TTL longo.** Saldo de banco de horas, totais de período e valores monetários **não** entram em cache, ou entram apenas na camada A com TTL de segundos e invalidação obrigatória por evento. |
| CA-05 | A invalidação preferencial é **por evento de domínio** (`WorkLogCreatedEvent`, `MembershipRoleChangedEvent`), não por expiração. O TTL é a **rede de segurança**, não o mecanismo principal. |
| CA-06 | Como o cache é local (CA-01), a invalidação por evento alcança **apenas a instância que processou o evento**. Por isso, **todo** cache tem TTL máximo curto o suficiente para que a divergência entre instâncias seja tolerável para aquele dado. |
| CA-07 | Todo cache declara explicitamente: nome, chave, TTL, tamanho máximo, eventos que o invalidam e a **consequência de servir dado obsoleto**. Cache sem essa declaração não é aprovado. |
| CA-08 | O tamanho máximo de cada cache é limitado (entradas ou peso), evitando pressão de memória e OOM. |
| CA-09 | Métricas de taxa de acerto, tamanho e evicção são expostas por cache ([ADR-046](ADR-046-observability.md)). |
| CA-10 | Cache **nunca** armazena dado sensível (senha, token, documento completo). |
| CA-11 | A ausência de cache **nunca** altera o resultado: desabilitar todos os caches deve produzir exatamente as mesmas respostas, apenas mais lentas. Isso é verificável por teste. |
| CA-12 | A migração para cache distribuído (F6) altera apenas a configuração do provedor; as chaves, os TTLs e a invalidação por evento permanecem ([ADR-041](ADR-041-redis.md)). |

### Caches previstos

| Cache | Camada | TTL | Invalidação por evento | Consequência de dado obsoleto |
|---|---|---|---|---|
| `session-validity` (`passwordChangedAt`, `roleChangedAt`) | A | 30 s | Troca de senha, alteração de papel | Revogação demora até 30 s |
| `tenant-config` | A | 60 s | Alteração de configuração do tenant | Fuso ou plano desatualizado por 60 s |
| `membership` | A | 30 s | Alteração/suspensão de membership | Permissão desatualizada por 30 s |
| `categories` / `tags` | B | 5 min | CRUD de categoria/tag | Item novo demora a aparecer em seletor |
| `dashboard-summary` | A | 30 s | `WorkLogCreated/Updated/Deleted` | Dashboard levemente defasado |
| `unread-count` | A | 15 s | Nova notificação, marcação de leitura | Contador levemente defasado |

## Motivação

**Por que cachear apenas o que é lido em toda requisição:** o maior ganho está no que se repete mais. `session-validity` é consultado em **toda** requisição autenticada por causa de JW-08/JW-09 ([ADR-008](ADR-008-jwt.md)); sem cache, isso adiciona uma consulta ao banco por requisição, o que anula parte do benefício de o JWT ser stateless.

**Por que prefixo de tenant obrigatório (CA-02) — a regra mais importante:** uma chave de cache sem `tenantId` faz o tenant B receber o dado do tenant A. É vazamento cross-tenant tão grave quanto uma query sem filtro, e **mais difícil de detectar**, porque não deixa rastro no SQL. RK-07 de [ADR-001](ADR-001-multi-tenant.md) identifica esse risco explicitamente; CA-02 é sua mitigação.

**Por que nada financeiro com TTL longo (CA-04):** o usuário que consulta o saldo do banco de horas está decidindo se pode trabalhar mais naquele contrato. Um saldo defasado em minutos o leva a estourar o limite. O custo de recalcular é muito menor que o custo dessa decisão errada.

**Por que invalidação por evento com TTL como rede (CA-05/CA-06):** invalidação por evento é precisa, mas em cache local ela só afeta a instância que processou o evento — as demais continuariam servindo dado obsoleto até a expiração. Por isso a decisão **combina** os dois: evento invalida onde alcança, TTL curto limita a divergência onde não alcança. Essa é a razão pela qual os TTLs são curtos mesmo com invalidação implementada, e é a principal limitação que [ADR-041](ADR-041-redis.md) resolverá.

**Por que declarar a consequência de dado obsoleto (CA-07):** força a pergunta certa antes de cachear. "O que acontece se o usuário vir este dado 30 segundos desatualizado?" Se a resposta for "toma uma decisão errada com dinheiro", o dado não deve ser cacheado.

**Por que o cache não pode alterar o resultado (CA-11):** cache é otimização. Se o comportamento depende dele, ele deixou de ser cache e virou fonte de verdade — violando R-01 e DA-01. O teste correspondente é simples e valioso.

## Alternativas consideradas

### A1 — Sem cache algum

| Aspecto | Avaliação |
|---|---|
| **Prós** | Nenhum dado obsoleto; nenhuma invalidação a manter; nenhum risco de vazamento por chave mal formada; comportamento sempre previsível. |
| **Contras** | Uma consulta por requisição para `session-validity`; dashboard consultado repetidamente; contagem de notificações no polling de todos os usuários; risco a AQ-01 e F2-04. |
| **Por que foi descartada** | O custo de `session-validity` sem cache é estrutural: incide em **toda** requisição autenticada. O cache de 30 s elimina isso com divergência desprezível. |

### A2 — Redis desde o MVP

| Aspecto | Avaliação |
|---|---|
| **Prós** | Cache compartilhado entre instâncias: invalidação alcança todas; TTLs podem ser maiores; resolve CA-06 na origem. |
| **Contras** | Um contêiner e uma classe de falha a mais (R-02); latência de rede por acesso (vs. nanossegundos em heap); complexidade de serialização; custo operacional. |
| **Por que foi descartada para o MVP** | O volume não justifica. TTLs curtos tornam a divergência entre instâncias aceitável para todos os dados cacheados. CA-12 preserva a migração ([ADR-041](ADR-041-redis.md)). |

### A3 — Cache HTTP no cliente e no proxy (`Cache-Control`, `ETag`)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Zero custo no servidor; padrão da web; reduz tráfego. |
| **Contras** | Toda resposta de API é `no-store` por decisão de segurança (AP-13 de [ADR-011](ADR-011-rest-api.md)); dado de tenant em cache de proxy compartilhado seria vazamento; invalidação impossível de controlar. |
| **Por que foi descartada** | Conflita diretamente com AP-13 e com o isolamento de tenant. |

### A4 — Cache de segundo nível do Hibernate (entidades)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Transparente; sem código; cobre automaticamente entidades lidas com frequência. |
| **Contras** | Transparência é o problema: cachear entidades sem decidir conscientemente o TTL e a consequência viola CA-07; interage de forma sutil com o filtro de tenant e com `@SQLRestriction`, criando risco real de servir entidade de outro tenant ou entidade excluída; invalidação por escrita direta ou por job não é detectada. |
| **Por que foi descartada** | A combinação de cache automático de entidades com filtros dinâmicos de tenant é uma das formas mais eficientes de produzir vazamento cross-tenant silencioso. O cache é explícito, por método e por chave declarada. |

### A5 — Tabelas de agregação materializadas em vez de cache do dashboard

| Aspecto | Avaliação |
|---|---|
| **Prós** | Sempre disponível; sem divergência entre instâncias; consulta trivial; escala com volume. |
| **Contras** | Mais complexo (tabela, atualização por evento, job de reconciliação); é desnormalização, exigindo justificativa (DA-03). |
| **Por que não foi adotada no MVP** | O cache de 30 s resolve o caso atual com muito menos complexidade. A tabela de agregação é a **próxima** alavanca se AQ-01 não for atingida com índices e cache — e a decisão de escalada está registrada em RK-03. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Elimina uma consulta por requisição autenticada (`session-validity`). |
| C+02 | Dashboard e contagem de notificações atendem AQ-01 com folga. |
| C+03 | Nenhuma infraestrutura adicional (CA-01). |
| C+04 | Acesso em nanossegundos, sem latência de rede. |
| C+05 | Invalidação por evento mantém os dados razoavelmente frescos (CA-05). |
| C+06 | Migração para Redis sem alterar chaves nem lógica (CA-12). |
| C+07 | CA-11 garante que o cache jamais altere o comportamento. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Cache não é compartilhado: cada instância tem o seu (CA-06). | TTLs curtos limitam a divergência; resolvido em F6. |
| C-02 | Consome memória do heap. | Limitado por CA-08 e monitorado. |
| C-03 | Cache é perdido a cada reinício e deploy. | Aquecimento é rápido; nenhum dado de negócio se perde (R-01). |
| C-04 | Invalidação por evento adiciona acoplamento entre features. | Feita por evento de domínio, que já é o mecanismo previsto (`ART-065`). |
| C-05 | Dados podem ficar obsoletos por até o TTL. | Consequência declarada por cache (CA-07) e limitada por CA-04. |
| C-06 | Mais um lugar onde um bug pode causar comportamento estranho. | CA-11 permite desabilitar tudo e comparar. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Invalidação não alcança outras instâncias (CA-06) — a limitação estrutural desta decisão. |
| L-02 | Escala vertical: mais instâncias significam mais cópias do mesmo cache, não mais capacidade. |
| L-03 | Não serve para dados que precisam ser consistentes entre instâncias em tempo real (por isso rate limit e lock usam banco, não cache). |

### Custos

| Item | Custo |
|---|---|
| Dependência | Caffeine (Apache 2.0), já disponível via Spring |
| Memória | Dezenas de MB por instância, limitados por CA-08 |
| Implementação | ~1 dia de infraestrutura + declaração por cache |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Consistência** entre instâncias | Ausência de infraestrutura no MVP | TTLs curtos limitam a divergência a segundos. |
| **TTLs longos** (mais acertos) | Frescor do dado | Dado financeiro obsoleto induz decisão errada. |
| **Transparência** do cache de segundo nível | Controle explícito e segurança de tenant | Cache automático de entidades é vetor real de vazamento. |
| **Compartilhamento** (Redis) | Simplicidade operacional | Migração preservada por CA-12. |
| **Cache de dados financeiros** | Correção | CA-04 é inegociável neste domínio. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `shared/cache` | Configuração dos caches, gerador de chave com prefixo de tenant (CA-02), métricas. |
| `shared/security` | `session-validity` e `membership`. |
| `tenant` | `tenant-config`, com invalidação por evento. |
| `category` | `categories`, `tags`. |
| `report` | `dashboard-summary`. |
| `notification` | `unread-count`. |
| Features | Publicam eventos que invalidam caches (CA-05). |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/architecture.md` §10.1 | DA-01 |
| `docs/03-architecture/backend.md` | Padrões de serviço |
| `docs/06-testing/strategy.md` | Teste de CA-11 |

| Spec dependente | Relação |
|---|---|
| `specs/010-dashboard` | `dashboard-summary` |
| `specs/013-notifications` | `unread-count` |
| `specs/005-categories`, `specs/006-tags` | Caches de referência |

| ADR relacionado | Relação |
|---|---|
| [ADR-041](ADR-041-redis.md) | Evolução para cache distribuído |
| [ADR-008](ADR-008-jwt.md) | `session-validity` viabiliza JW-08/JW-09 |
| [ADR-001](ADR-001-multi-tenant.md) | CA-02 mitiga RK-07 |
| [ADR-026](ADR-026-chartjs.md) | Agregações do dashboard |
| [ADR-045](ADR-045-rate-limit.md) | Rate limit **não** usa este cache (L-03) |

## Impacto no banco

| Item | Impacto |
|---|---|
| Carga | Redução significativa de leituras repetidas, especialmente de `session-validity`. |
| Fonte de verdade | Inalterada: o banco continua sendo a única (R-01, DA-01). |
| Consistência | O banco nunca lê do cache; escritas sempre vão direto ao banco. |
| Índices | Continuam necessários: CA-11 exige que o sistema funcione sem cache. |

## Impacto na API

Não se aplica ao contrato. Dois efeitos indiretos:

| Efeito | Descrição |
|---|---|
| Frescor | Respostas podem refletir estado defasado em até o TTL do cache correspondente; documentado por endpoint quando relevante. |
| Cabeçalhos | Nenhuma mudança: toda resposta continua `no-store` (AP-13); o cache é exclusivamente do servidor. |

## Impacto no Frontend

Não se aplica diretamente, porque o cache é do servidor. Relação importante: SG-09 de [ADR-024](ADR-024-signals.md) proíbe cache indefinido no cliente — a estratégia de cache do produto é **do servidor**, deliberadamente, porque ali ela é controlável, invalidável e escopada por tenant.

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Memória | Heap de cada instância dimensionado considerando os limites de CA-08. |
| Deploy | Cache perdido a cada deploy; aquecimento rápido e sem impacto funcional. |
| Monitoramento | Taxa de acerto, tamanho e evicção por cache ([ADR-047](ADR-047-monitoring.md)). |
| Escala | Mais instâncias significam mais cópias do cache (L-02), não mais capacidade. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | CA-02 é o controle central: chave sem prefixo de tenant é vazamento cross-tenant silencioso. |
| S-02 | CA-10: nenhum dado sensível em cache (senha, token, documento completo). |
| S-03 | O cache de `session-validity` **atrasa** a revogação por até 30 s — trade-off consciente, documentado em CA-07 e aceitável frente à janela de 15 min do access token. |
| S-04 | Cache em heap não é persistido; um *dump* de memória o exporia, o que reforça CA-10. |
| S-05 | **Multi-tenant:** a chave prefixada é obrigatória e verificada por teste automatizado (ver mitigação de RK-01). |
| S-06 | **LGPD:** dados pessoais cacheados são mínimos e efêmeros; não há persistência. |
| S-07 | **Auditoria:** o cache não intercepta escritas; toda operação auditável continua gerando trilha normalmente. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Acesso em nanossegundos, sem serialização nem rede. |
| P-02 | O ganho maior é em `session-validity`: uma consulta a menos por requisição autenticada. |
| P-03 | `dashboard-summary` e `unread-count` reduzem a carga nas consultas mais frequentes. |
| P-04 | CA-08 evita que o cache pressione o heap e provoque pausas de GC. |
| P-05 | CA-11 garante que a performance sem cache seja aceitável, ainda que inferior — o cache melhora, não viabiliza. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Escala horizontalmente, mas com duplicação de cache por instância (L-02). |
| E-02 | Com muitas instâncias, a taxa de acerto por instância cai, e a divergência entre elas aumenta — é o gatilho para F6. |
| E-03 | O gatilho objetivo de migração para Redis é: mais de 4 instâncias **ou** necessidade de TTL maior que 60 s em algum cache. |
| E-04 | Tabelas de agregação (A5) são a alavanca alternativa se o dashboard não escalar apenas com cache. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Chave de cache sem prefixo de tenant | Média | **Crítico** | **Crítica** |
| RK-02 | Dado financeiro cacheado por descuido | Média | Alto | **Alta** |
| RK-03 | Dashboard não atingir AQ-01 mesmo com cache | Média | Médio | Média |
| RK-04 | Divergência entre instâncias confundindo o usuário | Média | Baixo | Baixa |
| RK-05 | Cache crescendo e pressionando o heap | Baixa | Médio | Média |
| RK-06 | Invalidação por evento não implementada, dependendo só do TTL | Média | Médio | Média |
| RK-07 | Comportamento dependente do cache (violando CA-11) | Baixa | Alto | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Gerador de chave **centralizado** que injeta o `tenantId` obrigatoriamente — a chave não é montada manualmente; teste que popula o cache com o tenant A e verifica ausência de acerto para o tenant B | Teste de isolamento de cache |
| RK-02 | CA-04 explícita; CA-07 exige declarar a consequência; revisão bloqueia cache de valor financeiro | `review-checklist.md` |
| RK-03 | Índices primeiro, cache depois; se insuficiente, escalar para tabela de agregação (A5); teste de desempenho com volume realista (F2-04) | Teste de carga |
| RK-04 | TTLs curtos (CA-06); dados divergentes são de baixa consequência por CA-04 | Declaração de CA-07 |
| RK-05 | CA-08 (limite por cache); métrica de tamanho e evicção com alerta | [ADR-047](ADR-047-monitoring.md) |
| RK-06 | CA-07 exige declarar os eventos de invalidação; teste que altera o dado e verifica a invalidação na mesma instância | Teste de invalidação |
| RK-07 | Teste que executa a suíte de integração com **todos** os caches desabilitados e exige resultados idênticos (CA-11) | Execução alternativa da suíte |

## Referências

| Fonte | Uso |
|---|---|
| [Caffeine](https://github.com/ben-manes/caffeine) | Implementação (CA-01) |
| [Spring Framework — Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html) | Abstração usada |
| [Martin Fowler — Two Hard Things (cache invalidation)](https://martinfowler.com/bliki/TwoHardThings.html) | Fundamento de CA-05 |
| [AWS — Caching best practices](https://aws.amazon.com/caching/best-practices/) | TTL e invalidação |
| [OWASP — Multi-tenant cache isolation (Authorization Cheat Sheet)](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) | Base de CA-02 |
| `docs/03-architecture/architecture.md` §10.1 | DA-01 |
