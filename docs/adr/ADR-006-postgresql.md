# ADR-006 — PostgreSQL 16 como único banco de dados transacional

## Status

**Aceito** em 2026-07-29.
Fundamenta `DA-01` de `docs/03-architecture/architecture.md` §10.1.

## Data

2026-07-29

## Contexto

O DevTime possui um domínio fortemente relacional e transacional: `Tenant → Client → Contract → ContractPeriod → Ticket → WorkLog`, com invariantes que precisam ser garantidas atomicamente. O caso mais crítico é o fechamento de período (RN-241): sete passos que devem ocorrer todos ou nenhum, envolvendo cálculo de saldo, geração de snapshot assinado, transporte de rollover e criação do período seguinte.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Cálculo financeiro exige consistência forte; consistência eventual é inaceitável | `ART-003`, RN-241 |
| R-02 | Multi-tenancy por coluna discriminadora exige índices compostos eficientes | [ADR-001](ADR-001-multi-tenant.md) |
| R-03 | Durações em inteiros e dinheiro em decimal exato | `ART-034`, `ART-040` |
| R-04 | Instantes em UTC com fuso explícito; datas de calendário no fuso do tenant | `ART-030`, `ART-031` |
| R-05 | Snapshots imutáveis em estrutura flexível | RN-701 |
| R-06 | `audit_logs` cresce 5–10× mais que `work_logs` e precisa de particionamento | `architecture.md` §10.2 |
| R-07 | Sem DBA nem SRE dedicado no MVP | `docs/01-product/prd.md` |

## Decisão

| # | Regra |
|---|---|
| PG-01 | O DevTime usa **PostgreSQL 16** como único banco de dados transacional e **única fonte de verdade** (DA-01). |
| PG-02 | Nenhum dado de negócio existe exclusivamente em cache, fila, arquivo ou memória. |
| PG-03 | O schema é gerenciado exclusivamente por Flyway ([ADR-007](ADR-007-flyway.md)); `ddl-auto=validate` (`ART-054`). |
| PG-04 | Recursos usados de forma deliberada: tipo `uuid`, `TIMESTAMPTZ`, `NUMERIC`, `JSONB`, índices parciais, índices compostos, particionamento declarativo por range, `SELECT ... FOR UPDATE`, `CHECK` constraints. |
| PG-05 | Recursos **não** utilizados no MVP, por decisão explícita: tipo `ENUM` nativo, constraint `EXCLUDE`, extensões não incluídas na distribuição padrão, *stored procedures* com regra de negócio, triggers com regra de negócio. |
| PG-06 | Regra de negócio **nunca** reside no banco. O banco garante **integridade estrutural** (FK, `NOT NULL`, `CHECK`, unicidade); a regra de negócio reside na camada de serviço (`ART-062`). |
| PG-07 | Nível de isolamento padrão: `READ COMMITTED` (TX-04). `SERIALIZABLE` apenas onde uma `RN-XXX` exigir, com justificativa. |
| PG-08 | Concorrência em operações críticas usa *optimistic locking* (`@Version`, `ART-052`); o fechamento de período usa adicionalmente lock pessimista (TX-05). |
| PG-09 | Réplica de leitura não existe no MVP; sua introdução exige ADR próprio, por causa do risco de *replication lag* em leitura pós-escrita. |
| PG-10 | A versão maior é atualizada por ADR, com plano de migração e ensaio em staging. Não há atualização automática de versão maior. |

## Motivação

**Por que um banco relacional:** o domínio é um grafo de entidades com invariantes de integridade referencial e uma operação central (RN-241) que exige ACID real. Em um armazenamento sem transações multi-documento confiáveis, essa operação viraria uma saga com compensação — e compensação em cálculo financeiro é fonte de divergência de centavos e de disputa com o cliente.

**Por que PostgreSQL especificamente:**

| Recurso | Requisito atendido |
|---|---|
| Tipo `uuid` nativo de 16 bytes | [ADR-002](ADR-002-uuid.md) — sem desperdício de `VARCHAR(36)` |
| `TIMESTAMPTZ` com normalização para UTC | R-04 / `ART-030` |
| `NUMERIC` de precisão arbitrária | R-03 / `ART-040` — aritmética decimal exata, sem IEEE-754 |
| Índices **parciais** (`WHERE deleted_at IS NULL`) | [ADR-003](ADR-003-soft-delete.md) SD-06 / `ART-055` — este recurso é decisivo e não existe em MySQL |
| Índices compostos com estatísticas multivariadas | R-02 — `tenant_id` como primeira coluna |
| `JSONB` indexável | R-05 — snapshots e configurações |
| Particionamento declarativo por range | R-06 — `audit_logs` por mês, `work_logs` por `work_date` |
| `SELECT ... FOR UPDATE` | TX-05 — fechamento de período |
| MVCC | Leitura não bloqueia escrita, e vice-versa |

**Por que índices parciais são determinantes:** a combinação de soft delete obrigatório ([ADR-003](ADR-003-soft-delete.md)) com chaves naturais únicas por tenant (`ART-012`) **exige** índice único parcial. Sem ele, seria impossível recadastrar um cliente com o mesmo CNPJ após exclusão, e a solução de contorno seria justamente o `DELETE` físico proibido por `P-03`. MySQL não suporta índices parciais; isso, isoladamente, o elimina.

**Por que nenhuma regra no banco (PG-06):** regra em trigger ou *stored procedure* é invisível ao teste unitário, não aparece no *stack trace*, não é versionada junto ao código de forma legível e é a fonte clássica de comportamento "fantasma" em produção. Sob o modelo de execução deste projeto (implementação por agentes de IA a partir de `docs/`), lógica escondida no banco é especialmente perigosa: o agente não a lê.

**Por que sem réplica no MVP (PG-09):** *replication lag* introduz leitura desatualizada logo após escrita, quebrando a expectativa do usuário que acabou de registrar horas e não as vê no dashboard. Resolver isso exige roteamento consciente por operação — complexidade que não se justifica antes de haver evidência de gargalo de leitura.

## Alternativas consideradas

### A1 — MySQL 8 / MariaDB

| Aspecto | Avaliação |
|---|---|
| **Prós** | Ampla adoção; hospedagem gerenciada barata; replicação simples; boa performance em leitura. |
| **Contras** | **Sem índices parciais** — inviabiliza SD-06 sem contorno; `JSON` menos capaz que `JSONB` (sem indexação GIN equivalente); tipo `uuid` inexistente (armazenaria `BINARY(16)`, perdendo legibilidade e validação); particionamento mais limitado; `CHECK` constraints historicamente frágeis; comportamento de fuso horário menos rigoroso. |
| **Por que foi descartada** | A ausência de índice parcial colide frontalmente com a combinação soft delete + chave natural única, que é estrutural no produto. Não há contorno aceitável. |

### A2 — SQL Server

| Aspecto | Avaliação |
|---|---|
| **Prós** | Índices filtrados (equivalentes a parciais); ferramental de administração excelente; `MERGE`; ótimo suporte a particionamento. |
| **Contras** | Licenciamento comercial com custo relevante por núcleo, incompatível com a unidade econômica do plano individual ([ADR-001](ADR-001-multi-tenant.md) C+03); imagem de contêiner pesada, penalizando Testcontainers ([ADR-029](ADR-029-testcontainers.md)) e o ambiente local; menos idiomático no ecossistema Spring/Flyway. |
| **Por que foi descartada** | O custo de licença é um custo fixo recorrente em um produto cuja premissa de viabilidade é custo marginal próximo de zero por tenant. |

### A3 — MongoDB (documentos)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Schema flexível; escrita rápida; *sharding* nativo; documento aninhado modela bem agregados. |
| **Contras** | Transações multi-documento existem mas são caras e desencorajadas em escala; sem integridade referencial declarativa; sem `NUMERIC` decimal exato de precisão arbitrária (`Decimal128` tem limite fixo); consultas relacionais (relatório cruzando contrato, ticket, work log e usuário) exigem `$lookup`, que é ineficiente; agregações financeiras sem garantia transacional. |
| **Por que foi descartada** | O domínio é relacional e o requisito central é consistência forte em cálculo financeiro (R-01). Escolher documentos aqui trocaria a garantia mais importante do produto por flexibilidade de schema que o produto não precisa — o schema é justamente a parte mais estável e mais documentada (`docs/02-domain/entities.md`). |

### A4 — SQLite

| Aspecto | Avaliação |
|---|---|
| **Prós** | Zero operação; embutido; rápido para leitura; ótimo para desenvolvimento. |
| **Contras** | Escrita serializada por arquivo; concorrência limitada; sem tipos ricos (tipagem dinâmica); sem particionamento; sem `TIMESTAMPTZ`; inadequado a múltiplas instâncias de aplicação. |
| **Por que foi descartada** | Incompatível com escala horizontal de instâncias stateless e com multi-tenancy de muitos tenants concorrentes. |

### A5 — PostgreSQL 15 ou 17

| Aspecto | Avaliação |
|---|---|
| **Prós** | 15: mais tempo de campo. 17: melhorias de `VACUUM`, performance de `COPY` e de índices B-Tree. |
| **Contras** | 15 está mais próximo do fim do ciclo de suporte; 17 é mais recente, com menor tempo de campo e suporte de serviços gerenciados às vezes atrasado. |
| **Por que 16 foi escolhida** | Equilíbrio entre maturidade (lançada em 2023, amplamente disponível em serviços gerenciados) e janela de suporte (até 2028). PG-10 define o processo para revisitar. |

### A6 — Banco relacional + banco de série temporal para `work_logs`

| Aspecto | Avaliação |
|---|---|
| **Prós** | Compressão e agregação temporal otimizadas; consultas de dashboard potencialmente mais rápidas. |
| **Contras** | Dois sistemas de armazenamento, duas fontes de verdade, sincronização a manter (viola DA-01/PG-02); `work_log` não é série temporal pura (é entidade editável, com relacionamentos e soft delete). |
| **Por que foi descartada** | `work_logs` é uma entidade de domínio mutável e relacionada, não uma métrica. Se a agregação se tornar gargalo, a resposta é particionamento e tabelas de agregação **dentro** do PostgreSQL — mantendo uma única fonte de verdade. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | RN-241 é uma única transação ACID, sem saga nem compensação. |
| C+02 | Integridade referencial garantida pelo banco: estado órfão é impossível por construção. |
| C+03 | Índices parciais viabilizam soft delete com chave natural única (SD-06). |
| C+04 | Tipos exatos (`NUMERIC`, `INTEGER`, `TIMESTAMPTZ`, `uuid`) eliminam classes inteiras de bug de conversão. |
| C+05 | Uma única tecnologia de dados a operar, monitorar e fazer backup (R-07). |
| C+06 | Disponível em todos os provedores gerenciados relevantes, evitando *lock-in*. |
| C+07 | `JSONB` cobre a necessidade de estrutura flexível sem introduzir um segundo banco. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Escala de escrita é vertical; não há *sharding* nativo. | O volume previsto está a ordens de magnitude do limite de uma instância bem dimensionada; a chave de shard (`tenant_id`) já existe se for necessário. |
| C-02 | `max_connections` é um recurso escasso e caro. | Pool dimensionado; PgBouncer previsto se necessário. |
| C-03 | Migração de versão maior exige planejamento e janela. | PG-10 define o processo. |
| C-04 | Ponto único de falha de dados. | Mitigado por backup, PITR e réplica de alta disponibilidade na infraestrutura. |
| C-05 | Schema rígido: toda mudança exige migration. | É um benefício disfarçado de custo — o schema é documentado e verificado (`ART-054`). |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Sem escala horizontal de escrita sem solução externa (Citus, particionamento manual). |
| L-02 | `VACUUM` e *bloat* exigem atenção operacional em tabelas com alta taxa de atualização. |
| L-03 | Consultas analíticas pesadas competem por recurso com o tráfego transacional enquanto não houver réplica (PG-09). |

### Custos

| Item | Custo |
|---|---|
| Licença | Zero (PostgreSQL License, tipo BSD) |
| Operação | Instância gerenciada; custo cresce com CPU, RAM e armazenamento |
| Backup | Diário com PITR; retenção conforme `database.md` §9 |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Escala horizontal de escrita** | Consistência forte e transação ACID | Cálculo financeiro sem ACID gera divergência que destrói a confiança no produto. |
| **Flexibilidade de schema** (NoSQL) | Integridade garantida e schema documentado | O schema é a parte mais estável do produto; rigidez aqui é qualidade. |
| **Regra no banco** (triggers, procedures) | Testabilidade e visibilidade da regra | Regra invisível ao teste e ao agente é dívida disfarçada de performance. |
| **Réplica de leitura** desde o início | Ausência de leitura desatualizada | Complexidade adiada até haver evidência de necessidade. |
| **Última versão** (17) | Maturidade e disponibilidade em serviços gerenciados | Processo de atualização definido em PG-10. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| Todo o backend | Persistência via JPA/Hibernate sobre PostgreSQL. |
| `shared/persistence` | Tipos canônicos, `BaseEntity`, optimistic locking. |
| `contract/period` | Lock pessimista no fechamento (TX-05). |
| `audit` | Tabela particionada por mês. |
| `report` | Consultas agregadas competem com o tráfego transacional (L-03). |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/database.md` | Documento inteiro |
| `docs/03-architecture/architecture.md` §10 | Estratégia de dados |
| `docs/06-testing/strategy.md` §6.2 | Testcontainers com PostgreSQL real |

| Spec dependente | Relação |
|---|---|
| Todas as specs com modelo de dados | Seção "Modelo de dados" |

| ADR relacionado | Relação |
|---|---|
| [ADR-001](ADR-001-multi-tenant.md) | Schema único com `tenant_id` |
| [ADR-002](ADR-002-uuid.md) | Tipo `uuid` nativo |
| [ADR-003](ADR-003-soft-delete.md) / [ADR-034](ADR-034-soft-delete-strategy.md) | Índices parciais |
| [ADR-007](ADR-007-flyway.md) | Autoridade sobre o schema |
| [ADR-029](ADR-029-testcontainers.md) | Teste com PostgreSQL real |
| [ADR-039](ADR-039-background-jobs.md) | Lock de jobs com backend PostgreSQL |
| [ADR-045](ADR-045-rate-limit.md) | Contador de rate limit em banco no MVP |

## Impacto no banco

Este ADR **é** a decisão de banco. Consequências diretas:

| Item | Impacto |
|---|---|
| Tipos canônicos | Conforme `database.md` §4.2 — nenhum tipo fora dessa tabela sem ADR. |
| Enums | `VARCHAR(30)` + `CHECK`, nunca `ENUM` nativo (PG-05): alterar um `ENUM` nativo exige `ALTER TYPE`, que não é reversível sem downtime e complica DP-02/DP-03. |
| Sobreposição de work logs | Validada na aplicação, **não** por constraint `EXCLUDE` (PG-05): a regra tem exceções de negócio (RN-102) que uma constraint não modela, e o erro do banco não produz mensagem utilizável. |
| Particionamento | `audit_logs` por mês desde o início; `work_logs` por range de `work_date` a partir de 50M linhas. |
| Extensões | Nenhuma obrigatória no MVP. `pg_stat_statements` recomendada em produção para diagnóstico. |
| Backup | Diário com PITR; teste de restauração periódico obrigatório. |

## Impacto na API

Não se aplica diretamente ao contrato, porque a API é independente do SGBD. Dois efeitos indiretos:

| Efeito | Descrição |
|---|---|
| Códigos de erro | Violação de constraint é traduzida para `409` `DEVTIME-2001`, mapeada **por nome de constraint** — o que torna a convenção de nomenclatura (`uq_*`, `ck_*`, `fk_*`) parte do contrato de erro. |
| Vazamento | A resposta de erro **nunca** contém SQL, nome de tabela ou de coluna (`architecture.md` §8.2). |

## Impacto no Frontend

Não se aplica, porque o frontend não conhece o banco. Efeito indireto: os limites de tipo (`VARCHAR(n)`, precisão de `NUMERIC`) definem os validadores de formulário, que devem espelhar exatamente os limites do schema ([ADR-015](ADR-015-validation.md)).

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Local | Contêiner `postgres:16-alpine` via Docker Compose ([ADR-021](ADR-021-docker-compose.md)). |
| CI | Testcontainers com a **mesma** versão maior de produção ([ADR-029](ADR-029-testcontainers.md)). |
| Produção | Instância gerenciada com alta disponibilidade, backup automático e PITR. |
| Conexões | HikariCP; `instâncias × pool ≤ max_connections`. |
| Monitoramento | Conexões ativas, *locks*, consultas lentas, *bloat*, atraso de replicação, tamanho por tabela ([ADR-047](ADR-047-monitoring.md)). |
| Criptografia | Em repouso (disco) e em trânsito (TLS obrigatório entre aplicação e banco). |

## Segurança

| # | Consideração |
|---|---|
| S-01 | A aplicação usa um usuário sem privilégio de superusuário e sem `CREATEDB`; migrations usam usuário próprio com privilégio de DDL. |
| S-02 | Injeção de SQL é mitigada por parâmetros vinculados do JPA e pela proibição de concatenação (OWASP A03). |
| S-03 | TLS obrigatório na conexão; credenciais apenas em variáveis de ambiente (`ART-083`). |
| S-04 | **Multi-tenant:** o banco não impõe isolamento no MVP; ele é responsabilidade da aplicação ([ADR-001](ADR-001-multi-tenant.md)). RLS é a camada 5 planejada para F6 — e o PostgreSQL é o SGBD que a torna possível. |
| S-05 | **LGPD:** criptografia em repouso; purga física executável (SD-09); exportação em formato aberto (AQ-12). |
| S-06 | **Auditoria:** `audit_logs` é append-only, sem `UPDATE` nem `DELETE` concedidos ao usuário da aplicação sobre ela. |
| S-07 | Backups contêm dado pessoal e recebem o mesmo nível de proteção e retenção controlada. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Índices compostos iniciados por `tenant_id` sustentam as consultas críticas (`database.md` §10.1). |
| P-02 | Índices parciais são menores e mais seletivos que os totais equivalentes. |
| P-03 | MVCC permite leitura sem bloqueio, essencial para relatórios concorrentes com registro de horas. |
| P-04 | `JSONB` de snapshot é comprimido (TOAST) e não é consultado em caminho quente. |
| P-05 | Meta AQ-01 (p95 < 800 ms com 100k work logs) depende de índice, projeção (DA-04) e ausência de N+1 (DA-05). |
| P-06 | `EXPLAIN (ANALYZE, BUFFERS)` é obrigatório na revisão de consulta crítica nova. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Escala vertical cobre com folga o horizonte de F0 a F6. |
| E-02 | Particionamento é a primeira alavanca de escala de dados, sem mudança de aplicação. |
| E-03 | Réplica de leitura é a segunda alavanca, mediante ADR (PG-09). |
| E-04 | *Sharding* por `tenant_id` é a terceira, viável porque a chave existe em todas as tabelas. |
| E-05 | `max_connections` é o limite que aparece primeiro na escala horizontal de instâncias; PgBouncer é a resposta planejada. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Esgotamento de conexões com o crescimento de instâncias | Média | Alto | **Alta** |
| RK-02 | Consulta sem índice adequado degradar o p95 em produção | Alta | Médio | Alta |
| RK-03 | *Bloat* por alta taxa de atualização (ex.: `timers`) | Média | Médio | Média |
| RK-04 | Perda de dados por falha de disco ou erro humano | Baixa | Crítico | **Alta** |
| RK-05 | Migração de versão maior falhar em produção | Baixa | Alto | Média |
| RK-06 | Transação longa segurar lock e bloquear escritas (TX-07) | Média | Alto | Alta |
| RK-07 | `JSONB` de snapshot crescer além do esperado | Baixa | Médio | Baixa |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Pool dimensionado e documentado; métrica de conexões com alerta; PgBouncer previsto | [ADR-047](ADR-047-monitoring.md) |
| RK-02 | `EXPLAIN` obrigatório em consulta crítica; `pg_stat_statements`; alerta de consulta lenta; teste de contagem de queries (DA-05) | Pipeline + monitoramento |
| RK-03 | `autovacuum` ajustado nas tabelas quentes; métrica de *bloat*; `timers` mantida enxuta por job de limpeza | Monitoramento |
| RK-04 | Backup diário com PITR; **teste de restauração periódico obrigatório** — backup não testado não é backup | Runbook |
| RK-05 | Ensaio da migração em staging com cópia do volume de produção; janela planejada; plano de rollback (PG-10) | Ensaio |
| RK-06 | Alerta de transação acima de 3 s (TX-07); chamada externa proibida dentro de transação (TX-06) | Métrica |
| RK-07 | Limite de tamanho no snapshot; compressão nativa do TOAST; monitoramento de tamanho da tabela | `database.md` §10 |

## Referências

| Fonte | Uso |
|---|---|
| [PostgreSQL 16 — Documentation](https://www.postgresql.org/docs/16/index.html) | Referência geral |
| [PostgreSQL — Versioning Policy](https://www.postgresql.org/support/versioning/) | Janela de suporte e PG-10 |
| [PostgreSQL — Partial Indexes](https://www.postgresql.org/docs/16/indexes-partial.html) | Recurso determinante da escolha |
| [PostgreSQL — Table Partitioning](https://www.postgresql.org/docs/16/ddl-partitioning.html) | R-06 |
| [PostgreSQL — Transaction Isolation](https://www.postgresql.org/docs/16/transaction-iso.html) | PG-07 |
| [PostgreSQL — Row Security Policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html) | Camada 5 futura |
| [Use The Index, Luke](https://use-the-index-luke.com/) | Base do argumento de índices compostos |
| `docs/03-architecture/database.md` | Modelo físico completo |
