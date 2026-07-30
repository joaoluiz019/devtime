# ADR-005 — Spring Boot 3 com Spring MVC como framework de aplicação

## Status

**Aceito** em 2026-07-29.
Depende de [ADR-004](ADR-004-java21.md).

## Data

2026-07-29

## Contexto

Definido Java 21 como plataforma ([ADR-004](ADR-004-java21.md)), resta escolher o framework de aplicação. Ele determina o modelo de injeção de dependências, o modelo web, a integração com persistência, o modelo de transação, a configuração por ambiente e a superfície de segurança.

Requisitos que o framework precisa atender:

| # | Requisito | Origem |
|---|---|---|
| R-01 | Transação declarativa na camada de serviço, com propagação e isolamento configuráveis | TX-01 a TX-07 de `architecture.md` §8.3 |
| R-02 | Interceptação de requisição para popular `TenantContext` e ativar o filtro Hibernate | [ADR-001](ADR-001-multi-tenant.md) MT-04/MT-05 |
| R-03 | Autorização por método com expressões (`@PreAuthorize`) | AZ-01 de `security.md` §7.2 |
| R-04 | Tratamento global de exceções traduzindo para RFC 7807 | `ART-072` |
| R-05 | Agendamento de jobs com lock distribuído | [ADR-039](ADR-039-background-jobs.md) |
| R-06 | Configuração tipada por perfil de ambiente | `backend.md` §13 |
| R-07 | Geração de OpenAPI a partir do código | `ART-076` |
| R-08 | Suporte de primeira classe a testes de integração com contêiner real | `ART-102` |

## Decisão

| # | Regra |
|---|---|
| SB-01 | O backend usa **Spring Boot 3.x** (última versão estável da linha), sobre Jakarta EE 10. |
| SB-02 | O modelo web é **Spring Web MVC** (servlet, bloqueante), **não** WebFlux. |
| SB-03 | Virtual threads são habilitadas (`spring.threads.virtual.enabled=true`), conforme JV-02 de [ADR-004](ADR-004-java21.md). |
| SB-04 | Persistência via **Spring Data JPA** sobre Hibernate 6.x. |
| SB-05 | Segurança via **Spring Security 6.x**, com `@EnableMethodSecurity(prePostEnabled = true)`. |
| SB-06 | Agendamento via `@Scheduled` + ShedLock, no mesmo artefato, sob perfil dedicado. |
| SB-07 | Configuração exclusivamente por **`@ConfigurationProperties` tipadas**; `@Value` disperso é proibido fora de casos triviais. |
| SB-08 | `spring.jpa.hibernate.ddl-auto` é **sempre** `validate`, em todos os perfis (`ART-054`). |
| SB-09 | `spring-boot-starter-actuator` é habilitado, com exposição restrita: `health`, `info` e `prometheus`. Nenhum outro endpoint é exposto em produção. |
| SB-10 | Cada dependência entra como *starter* oficial quando existir, para herdar o gerenciamento de versões do BOM. |
| SB-11 | Nenhuma classe de domínio depende de tipo do Spring. A dependência do framework fica confinada a Controller, configuração e adaptadores de infraestrutura. |

## Motivação

**Por que Spring Boot:**
1. **Cobertura integral dos requisitos R-01 a R-08 sem integração manual.** Transação declarativa, AOP para autorização, `HandlerInterceptor` para tenancy, `@RestControllerAdvice` para erro, `@Scheduled` para jobs, `@ConfigurationProperties` para configuração, Actuator para health — todos coesos e versionados juntos pelo BOM.
2. **Gestão de versões pelo BOM.** O maior risco operacional de um stack Java é a matriz de compatibilidade entre Hibernate, driver, Jackson, Micrometer e Security. O BOM do Spring Boot resolve isso como um único número de versão, reduzindo drasticamente a superfície de incompatibilidade.
3. **Densidade de material canônico.** É o framework Java com mais documentação, exemplos e código público — fator determinante para a qualidade da geração por agentes de IA (R-02 de [ADR-004](ADR-004-java21.md)).
4. **Spring Security é a peça insubstituível.** A combinação de cadeia de filtros, `@PreAuthorize` com `PermissionEvaluator` customizado e negação por padrão (`ART-085`) atende diretamente ao modelo de autorização em duas camadas (`ART-082`). Reimplementar isso é a decisão errada em qualquer projeto.

**Por que MVC e não WebFlux (SB-02):** WebFlux exigiria R2DBC, perdendo JPA — e com JPA perde-se o `@Filter` do Hibernate, que é a **camada 2 do isolamento de tenant** ([ADR-001](ADR-001-multi-tenant.md) MT-05). O isolamento passaria a depender de predicado manual em toda query, que é exatamente a alternativa A4 rejeitada naquele ADR. Além disso, `@Transactional` declarativo, `ThreadLocal` para contexto e *stack traces* legíveis são incompatíveis ou degradados no modelo reativo. Com virtual threads (SB-03), a vantagem de throughput do WebFlux praticamente desaparece, restando apenas seus custos.

**Por que `ddl-auto=validate` sempre (SB-08):** qualquer outro valor permite que o Hibernate altere o schema, tornando a migration ([ADR-007](ADR-007-flyway.md)) não-autoritativa. `validate` transforma divergência entre entidade e schema em **falha de inicialização** — detectada no deploy, não em produção sob carga.

**Por que configuração tipada (SB-07):** `@Value("${...}")` espalhado falha em runtime, no momento do uso, com mensagem ruim. `@ConfigurationProperties` com Bean Validation falha na inicialização, com mensagem precisa, e torna a superfície de configuração enumerável — condição para DP-05 (readiness só responde OK após validação).

## Alternativas consideradas

### A1 — Spring Boot 3 com WebFlux

| Aspecto | Avaliação |
|---|---|
| **Prós** | Backpressure nativo; alto throughput com poucas threads; adequado a *streaming* e a proxies. |
| **Contras** | Exige R2DBC (sem JPA, sem `@Filter`, sem *dirty checking*, sem `@Transactional` clássico); `ThreadLocal` inutilizável para `TenantContext` (exigiria `Context` do Reactor propagado manualmente); *stack traces* fragmentados; curva de aprendizado alta; ecossistema de bibliotecas de relatório é bloqueante de qualquer forma. |
| **Por que foi descartada** | Perder o `@Filter` do Hibernate significa reabrir a decisão de isolamento de tenant pela pior alternativa disponível. O ganho de throughput é anulado por virtual threads. Trocaria a garantia de segurança mais importante do produto por um ganho que já temos de outra forma. |

### A2 — Quarkus

| Aspecto | Avaliação |
|---|---|
| **Prós** | *Startup* muito rápido; consumo de memória baixo; excelente suporte a compilação nativa; Panache simplifica repositórios; Hibernate ORM com multitenancy embutido. |
| **Contras** | Ecossistema e volume de material canônico significativamente menores que Spring (impacto direto em R-02 de [ADR-004](ADR-004-java21.md)); algumas extensões amadurecem mais devagar; equipe teria curva de aprendizado sem ganho funcional correspondente; ferramental de terceiros (agentes, bibliotecas de PDF, integrações) frequentemente documenta Spring primeiro. |
| **Por que foi descartada** | Os ganhos de Quarkus (startup, memória, nativo) são decisivos em serverless e em escala de muitas instâncias efêmeras — cenário que a arquitetura de monólito modular não produz. Sem esse cenário, resta apenas o custo de menor liquidez de conhecimento. |

### A3 — Micronaut

| Aspecto | Avaliação |
|---|---|
| **Prós** | Injeção de dependências resolvida em tempo de compilação (sem reflexão); startup rápido; baixo consumo de memória; boa integração com GraalVM. |
| **Contras** | Comunidade e ecossistema menores; menos material canônico; integrações de terceiros mais escassas; suporte a JPA presente mas menos maduro que no Spring. |
| **Por que foi descartada** | Mesmo raciocínio de A2, com base de comunidade ainda menor. Nenhum requisito de R-01 a R-08 é atendido de forma superior. |

### A4 — Jakarta EE puro (Quarkus/WildFly com CDI, JAX-RS, JPA)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Padrão aberto sem *vendor lock-in*; especificações estáveis; portabilidade teórica entre servidores. |
| **Contras** | Ausência de camada de autoconfiguração; integração manual de segurança, observabilidade, migrations e configuração; ecossistema de segurança (Jakarta Security) muito menos capaz que Spring Security para RBAC com expressões; produtividade menor. |
| **Por que foi descartada** | A portabilidade entre servidores de aplicação não tem valor para um produto empacotado em contêiner. O custo de integrar manualmente o que o Boot autoconfigura é permanente. |

### A5 — Framework mínimo (Javalin, Spark, Helidon SE) com bibliotecas avulsas

| Aspecto | Avaliação |
|---|---|
| **Prós** | Controle total; sem "mágica"; artefato pequeno; startup imediato. |
| **Contras** | Toda a infraestrutura transversal (transação, segurança, validação, erro, configuração, health, métricas) vira código próprio a ser mantido e testado; a superfície de bug de segurança passa a ser nossa. |
| **Por que foi descartada** | Reimplementar infraestrutura transversal consome o orçamento do MVP e concentra risco de segurança em código sem revisão externa. A "mágica" do Spring é, na prática, código testado por milhões de instalações. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | R-01 a R-08 atendidos sem integração manual. |
| C+02 | Uma única versão (o BOM) governa a compatibilidade de dezenas de bibliotecas. |
| C+03 | `@Filter` do Hibernate viabiliza a camada 2 do isolamento de tenant. |
| C+04 | Spring Security fornece negação por padrão e autorização por método sem código próprio. |
| C+05 | `@SpringBootTest` + Testcontainers dá teste de integração realista com pouco código ([ADR-029](ADR-029-testcontainers.md)). |
| C+06 | Alta densidade de material canônico eleva a qualidade da geração por agentes. |
| C+07 | Actuator entrega health e métricas Prometheus sem esforço ([ADR-047](ADR-047-monitoring.md)). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Autoconfiguração é opaca: entender *por que* algo funciona exige conhecer o mecanismo. | Mitigado por configuração tipada explícita (SB-07) e por `--debug` de relatório de autoconfiguração. |
| C-02 | Uso intenso de reflexão e proxies; *startup* de segundos e maior consumo de memória. | Irrelevante em serviço de longa duração ([ADR-004](ADR-004-java21.md) C-02). |
| C-03 | Acoplamento ao framework; migrar para outro seria reescrita da camada de infraestrutura. | Mitigado por SB-11: o domínio não conhece o Spring. |
| C-04 | Atualizações de linha maior (Boot 3 → 4) exigem trabalho planejado. | Ciclo previsível e documentado; migração é evento, não surpresa. |
| C-05 | Proxies AOP impõem regras não óbvias (chamada interna a método `@Transactional` não abre transação). | Regra explícita em `backend-rules.md`; verificada em revisão. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Autorização por método via proxy não se aplica a chamadas internas dentro da mesma classe. |
| L-02 | O modelo servlet mantém uma requisição por thread (virtual), limitado pelo pool de conexões (E-03 de [ADR-004](ADR-004-java21.md)). |
| L-03 | Compilação nativa não é suportada plenamente em conjunto com virtual threads e reflexão intensa (ver A6 de [ADR-004](ADR-004-java21.md)). |

### Custos

| Item | Custo |
|---|---|
| Licença | Zero (Apache 2.0) |
| Memória | ~300–600 MB de heap em operação típica |
| Atualização | Uma atualização de linha menor por trimestre, absorvida pelo Dependabot |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Transparência** (autoconfiguração opaca) | Velocidade de entrega e menos código próprio | O código não escrito é o código sem bug; a opacidade é gerenciável com configuração explícita. |
| **Startup e memória** de Quarkus/Micronaut | Ecossistema, segurança madura e material canônico | Nenhum cenário do produto depende de startup rápido. |
| **Throughput teórico** do WebFlux | JPA, `@Filter`, `@Transactional` e depurabilidade | O isolamento de tenant é inegociável; virtual threads recuperam o throughput. |
| **Independência de framework** | Produtividade | Mitigado por SB-11, que limita o raio de acoplamento ao adaptador. |
| **Artefato pequeno** | Bateria de recursos incluída | O tamanho da imagem é otimizado por multi-stage e camadas ([ADR-020](ADR-020-docker.md)). |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| Todo o backend | Modelo de componentes, injeção, ciclo de vida. |
| `shared/tenancy` | `OncePerRequestFilter` e `HandlerInterceptor` para `TenantContext` (R-02). |
| `shared/security` | `SecurityFilterChain`, `@PreAuthorize`, `PermissionEvaluator` (R-03). |
| `shared/error` | `@RestControllerAdvice` com `ProblemDetail` (R-04). |
| `shared/event` | `ApplicationEventPublisher` e `@TransactionalEventListener`. |
| `*/service` | `@Transactional` exclusivamente aqui (`ART-064`). |
| `*/domain` | **Sem** dependência do Spring (SB-11). |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/backend.md` | §4, §6, §12, §13 |
| `docs/03-architecture/security.md` §7 | Configuração do Spring Security |
| `docs/ai/backend-rules.md` | Padrões de Controller, Service, Repository |
| `docs/06-testing/strategy.md` §6.2 | `@SpringBootTest` |

| Spec dependente | Relação |
|---|---|
| Todas as specs de backend | Artefatos nomeados como `*Controller`, `*Service`, `*Repository`, `*Config` |

| ADR relacionado | Relação |
|---|---|
| [ADR-004](ADR-004-java21.md) | Plataforma; virtual threads |
| [ADR-001](ADR-001-multi-tenant.md) | `@Filter` do Hibernate depende de JPA |
| [ADR-016](ADR-016-controller-service-repository.md) | Camadas materializadas por estereótipos do Spring |
| [ADR-017](ADR-017-exception-handling.md) | `@RestControllerAdvice` |
| [ADR-039](ADR-039-background-jobs.md) | `@Scheduled` + ShedLock |
| [ADR-047](ADR-047-monitoring.md) | Actuator |

## Impacto no banco

| Item | Impacto |
|---|---|
| Acesso | Spring Data JPA sobre Hibernate 6.x; `Specification` para filtros dinâmicos. |
| Schema | `ddl-auto=validate` (SB-08): o Hibernate **nunca** altera o schema; a autoridade é o Flyway ([ADR-007](ADR-007-flyway.md)). |
| Pool | HikariCP (padrão do Boot), dimensionado explicitamente (RK-02 de [ADR-004](ADR-004-java21.md)). |
| Isolamento | Filtro Hibernate ativado por interceptor de sessão (MT-05). |
| Transação | `JpaTransactionManager`, `READ_COMMITTED` como padrão (TX-04). |

## Impacto na API

| Item | Impacto |
|---|---|
| Roteamento | `@RestController` com `@RequestMapping`, seguindo `ART-070`/`ART-071`. |
| Serialização | Jackson, configurado para `camelCase` (`ART-075`), ISO-8601 com offset (`ART-033`) e omissão de nulos onde a spec definir. |
| Validação | Jakarta Bean Validation integrada a `@Valid` no Controller ([ADR-015](ADR-015-validation.md)). |
| Erro | `ProblemDetail` nativo do Spring 6, estendido com `code`, `traceId` e `errors[]` (`ART-072`). |
| Paginação | `Pageable` do Spring Data, com `size` default 20 e máximo 100 (`ART-073`), normalizado para o envelope definido em `docs/04-api/`. |
| OpenAPI | springdoc-openapi 2.x gera o documento a partir dos controllers ([ADR-012](ADR-012-openapi.md)). |

## Impacto no Frontend

Não se aplica ao contrato, porque o frontend consome apenas HTTP/JSON. Dois efeitos indiretos:

| Efeito | Descrição |
|---|---|
| CORS | Configurado no `SecurityFilterChain`, com `allowCredentials = true` e origens explícitas por ambiente (`security.md` §8.3) — necessário para o cookie de refresh ([ADR-009](ADR-009-refresh-token.md)). |
| Formato de erro | O `ProblemDetail` define o contrato que o interceptor de erro do Angular consome ([ADR-017](ADR-017-exception-handling.md)). |

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Artefato | JAR executável com Tomcat embarcado; sem servidor de aplicação externo. |
| Imagem | Camadas do Spring Boot (`layertools`) aproveitadas no multi-stage ([ADR-020](ADR-020-docker.md)). |
| Perfis | `local`, `test`, `staging`, `prod` (`backend.md` §13.1); o perfil `scheduler` habilita os jobs. |
| Health | `/actuator/health/liveness` e `/readiness`, com readiness respondendo OK apenas após validação de schema (DP-05). |
| Métricas | `/actuator/prometheus` via Micrometer ([ADR-046](ADR-046-observability.md)). |
| Segredos | Apenas variáveis de ambiente (`ART-083`); nenhum segredo em `application.yml` versionado. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | Spring Security aplica negação por padrão (`ART-085`); rotas públicas são declaradas em allowlist explícita. |
| S-02 | A ordem da cadeia de filtros é parte do contrato de segurança: `TraceId → RateLimit → JwtAuthentication → TenantContext`. Alterá-la exige ADR. |
| S-03 | Endpoints do Actuator são superfície de ataque: apenas `health`, `info` e `prometheus` são expostos, e `prometheus` fica restrito à rede interna (SB-09). |
| S-04 | Swagger UI é desabilitado em produção (`A05` de OWASP, `security.md` §8). |
| S-05 | **Multi-tenant:** o `TenantContext` é populado por filtro após a autenticação e limpo no `finally`, garantindo que nenhuma requisição herde contexto de outra (RK-03 de [ADR-004](ADR-004-java21.md)). |
| S-06 | **LGPD:** o Spring não registra corpo de requisição por padrão; qualquer log de payload é proibido sem mascaramento ([ADR-019](ADR-019-logging.md)). |
| S-07 | **Auditoria:** `AuditingEntityListener` do Spring Data preenche autor e instante ([ADR-018](ADR-018-auditing.md)). |
| S-08 | CVEs do Spring são frequentes e de alta visibilidade; o Dependabot e o gate `G-06` são obrigatórios (`ART-103`). |

## Performance

| # | Consideração |
|---|---|
| P-01 | Overhead de proxies AOP é da ordem de microssegundos por chamada, desprezível frente ao acesso a banco. |
| P-02 | Startup de 3–8 s; irrelevante para disponibilidade, relevante apenas no tempo de deploy. |
| P-03 | Jackson é o componente mais custoso do caminho de resposta em payloads grandes; listagens usam projeções (DA-04) para reduzir o volume serializado. |
| P-04 | Spring Data gera SQL previsível; consultas críticas são revisadas por plano de execução (`database.md` §10.1). |
| P-05 | Risco de N+1 é inerente ao JPA e é tratado como falha de build (DA-05, gate do pipeline). |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Instâncias stateless replicáveis atrás do proxy reverso (`ART-080`). |
| E-02 | `@Scheduled` com ShedLock garante execução única com N instâncias (AQ-07). |
| E-03 | Nenhum estado de sessão no servidor (JWT stateless, [ADR-008](ADR-008-jwt.md)), portanto não há *sticky session*. |
| E-04 | A extração futura de módulos (relatórios, notificações) é facilitada porque cada feature já é um pacote coeso ([ADR-027](ADR-027-folder-structure.md)). |
| E-05 | O limite prático de escala é o pool de conexões agregado contra `max_connections` do PostgreSQL. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | CVE crítica no Spring Framework/Security exigindo atualização emergencial | Média | Alto | **Alta** |
| RK-02 | Chamada interna a método `@Transactional` não abre transação (L-01) | Média | Alto | Alta |
| RK-03 | Autoconfiguração habilitar comportamento não intencional | Baixa | Médio | Baixa |
| RK-04 | Endpoint do Actuator exposto indevidamente | Baixa | Alto | Média |
| RK-05 | Migração para Spring Boot 4 exigir esforço não planejado | Média | Médio | Média |
| RK-06 | Acoplamento do domínio ao framework dificultar teste e evolução | Média | Médio | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Dependabot ativo; gate `G-06` bloqueia CVE `HIGH`/`CRITICAL`; janela de atualização emergencial definida no runbook | Pipeline |
| RK-02 | Regra explícita em `backend-rules.md`; teste de integração que verifica rollback nas operações críticas (fechamento de período) | Teste de transação |
| RK-03 | Relatório de autoconfiguração revisado a cada atualização de linha; configuração explícita preferida (SB-07) | Revisão de release |
| RK-04 | Exposição declarada por allowlist (SB-09); teste que verifica `404` nos endpoints não expostos em perfil `prod` | Teste de configuração |
| RK-05 | Acompanhar o ciclo de suporte do Spring Boot; planejar a atualização como item de backlog técnico, não como emergência | Roadmap técnico |
| RK-06 | SB-11 verificado por ArchUnit: pacotes `*/domain` não podem importar `org.springframework.*` | ArchUnit |

## Referências

| Fonte | Uso |
|---|---|
| [Spring Boot — Reference Documentation](https://docs.spring.io/spring-boot/index.html) | Autoconfiguração, perfis, Actuator |
| [Spring Framework — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html) | R-01 e L-01 |
| [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) | R-03 |
| [Spring Boot — Virtual threads](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html) | SB-03 |
| [Spring Boot — Production-ready Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) | SB-09 |
| [Hibernate ORM 6 — Filters](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#pc-filter) | Camada 2 do isolamento |
| [Spring Boot — Support Policy](https://spring.io/projects/spring-boot#support) | RK-05 |
