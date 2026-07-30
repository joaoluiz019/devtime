# ADR-011 — REST/JSON sobre HTTP como estilo de API

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-070` a `ART-075`. Complementado por [ADR-012](ADR-012-openapi.md), [ADR-017](ADR-017-exception-handling.md) e [ADR-043](ADR-043-api-versioning.md).

## Data

2026-07-29

## Contexto

A API do DevTime tem um consumidor no MVP (a SPA Angular) e consumidores previstos em F8 (API pública, webhooks, integrações com GitHub, Jira e Slack). O estilo escolhido determina o contrato, o ferramental, a curva de adoção por terceiros e a forma como erros, paginação e idempotência são expressos.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | O domínio é orientado a recursos (clientes, contratos, tickets, work logs) com operações CRUD e ações de máquina de estado | `docs/02-domain/entities.md` |
| R-02 | Toda listagem é paginada | `ART-073` |
| R-03 | Erros seguem RFC 7807 com código estável | `ART-072`, `ART-113` |
| R-04 | Documentação gerada e validável | `ART-076` |
| R-05 | Em F8 a API será pública, consumida por terceiros que não controlamos | `roadmap.md` |
| R-06 | Operações com efeito financeiro ou externo aceitam `Idempotency-Key` | `ART-074` |

## Decisão

| # | Regra |
|---|---|
| AP-01 | A API é **REST sobre HTTP/1.1+ com JSON**, servida sob o prefixo `/api/v1` ([ADR-043](ADR-043-api-versioning.md)). |
| AP-02 | Recursos são substantivos no **plural, kebab-case**: `/work-logs`, `/contract-periods`. Verbo em path é proibido (`ART-071`). |
| AP-03 | Exceção a AP-02: **ações de máquina de estado** usam sub-recurso verbal (`POST /timers/current/pause`, `POST /contract-periods/{id}/close`). Isso é deliberado: transições de estado não são CRUD e forçá-las em `PATCH` esconderia a semântica. |
| AP-04 | Semântica dos métodos: `GET` (leitura, seguro e idempotente), `POST` (criação e ações), `PUT` (substituição completa), `PATCH` (alteração parcial), `DELETE` (exclusão lógica, [ADR-003](ADR-003-soft-delete.md)). |
| AP-05 | Códigos de status: `200` leitura/atualização, `201` criação com `Location`, `204` exclusão e ações sem corpo, `400` validação, `401` não autenticado, `403` sem permissão, `404` inexistente **ou de outro tenant**, `409` conflito de estado ou concorrência, `422` regra de negócio violada, `429` rate limit, `500` erro inesperado. |
| AP-06 | Campos JSON em **camelCase** (`ART-075`); instantes em ISO-8601 **com offset** (`ART-033`); datas de calendário em `yyyy-MM-dd`; durações em **minutos inteiros** (`ART-034`). |
| AP-07 | Toda listagem é paginada com `page`, `size` (default 20, máximo 100) e `sort`, retornando um envelope com `content`, `page`, `size`, `totalElements`, `totalPages` (`ART-073`). |
| AP-08 | Filtros são parâmetros de query nomeados e documentados por endpoint. Não existe linguagem de consulta genérica na URL. |
| AP-09 | Erros seguem **RFC 7807** (`application/problem+json`), acrescidos de `code`, `traceId` e `errors[]` ([ADR-017](ADR-017-exception-handling.md)). |
| AP-10 | Operações com efeito financeiro ou externo aceitam `Idempotency-Key` (`ART-074`). |
| AP-11 | Concorrência otimista é exposta por `version` no payload; conflito retorna `409` `DEVTIME-2004`. |
| AP-12 | **Sem HATEOAS.** Respostas não carregam links de navegação. |
| AP-13 | Respostas de API sempre com `Cache-Control: no-store`. |
| AP-14 | Recursos aninhados são expressos por caminho apenas quando a relação é de composição estrita (`/contracts/{id}/periods`); relações por referência usam filtro (`/work-logs?ticketId=`). |

## Motivação

**Por que REST:** o domínio é naturalmente orientado a recursos (R-01), e REST mapeia recursos para URLs de forma que um desenvolvedor externo entende sem documentação prévia — propriedade decisiva para R-05. Além disso, todo o ferramental já decidido (Spring MVC, springdoc, `ProblemDetail`, `Pageable`) é nativamente REST.

**Por que JSON:** é o formato nativo do consumidor (JavaScript), legível por humanos durante depuração, e suportado por qualquer cliente HTTP.

**Por que ações verbais são exceção e não regra (AP-03):** modelar `POST /timers/current/pause` como `PATCH /timers/current {"status":"PAUSED"}` transferiria para o cliente a responsabilidade de conhecer a máquina de estados e permitiria transições inválidas na entrada. A ação explícita torna a transição um comando com pré-condições verificáveis e mensagem de erro precisa (`409` com `availableTransitions`).

**Por que sem HATEOAS (AP-12):** HAL/HATEOAS agrega valor quando o cliente é genérico e descobre a API em runtime. Nossos clientes são acoplados por contrato: a SPA é desenvolvida junto com a API, e o consumidor de F8 lerá o OpenAPI. Os links inflariam cada resposta com dados que nenhum cliente usa. A descoberta é feita pelo documento OpenAPI ([ADR-012](ADR-012-openapi.md)), que é a forma moderna e verificável do mesmo objetivo.

**Por que `404` e não `403` para outro tenant (AP-05):** conforme `ART-024` e [ADR-001](ADR-001-multi-tenant.md) MT-08 — `403` confirmaria a existência do recurso.

**Por que sem linguagem de consulta genérica (AP-08):** filtros arbitrários na URL (OData, RSQL) permitem que o cliente construa consultas que ninguém indexou, transformando o cliente em fonte de incidentes de performance e ampliando a superfície de injeção. Filtros nomeados são finitos, documentados, indexados e testados.

## Alternativas consideradas

### A1 — GraphQL

| Aspecto | Avaliação |
|---|---|
| **Prós** | O cliente pede exatamente os campos necessários, eliminando *over/under-fetching*; um único endpoint; schema fortemente tipado e introspectável; excelente para telas com agregações heterogêneas (dashboard). |
| **Contras** | Cache HTTP inutilizável (tudo é `POST` em um endpoint); paginação, erro e status HTTP precisam de convenções próprias, perdendo os códigos padronizados; consultas arbitrárias tornam o custo imprevisível e abrem porta a consultas maliciosamente profundas (exigindo análise de complexidade e *depth limiting*); **autorização por campo** é significativamente mais complexa — e o escopo de dados assimétrico de `MEMBER` (RB-06 de [ADR-010](ADR-010-role-permission.md)) precisaria ser reimplementado em cada resolver; N+1 é o modo de falha padrão, exigindo *dataloaders*. |
| **Por que foi descartada** | O modelo de autorização com escopo de dados por papel é a parte mais crítica do sistema, e GraphQL torna sua verificação exaustiva muito mais difícil. Somado ao risco de consultas imprevisíveis em um SaaS multi-tenant (vizinho barulhento, C-01 de [ADR-001](ADR-001-multi-tenant.md)), o custo supera o ganho de flexibilidade — que é modesto, dado que a SPA e a API evoluem juntas. |

### A2 — gRPC

| Aspecto | Avaliação |
|---|---|
| **Prós** | Contrato forte em Protobuf; serialização binária eficiente; geração de cliente automática; *streaming* bidirecional. |
| **Contras** | Não é consumível diretamente por navegador (exige gRPC-Web e proxy); depuração muito mais difícil (payload binário); ferramental de terceiros (Postman, curl) mais limitado; adoção por integradores externos em F8 seria uma barreira. |
| **Por que foi descartada** | O consumidor primário é um navegador. gRPC é adequado a comunicação **entre serviços**, cenário que a decisão de monólito modular não produz. |

### A3 — RPC sobre HTTP (endpoints tipo `/api/createWorkLog`)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Mapeamento direto de método para endpoint; sem discussão sobre "qual verbo usar"; ações complexas ficam naturais. |
| **Contras** | Perde a semântica de método HTTP (idempotência, segurança, cacheabilidade); perde os códigos de status como contrato; a URL deixa de identificar recurso, quebrando `Location`, `ETag` e ferramentas de proxy; nenhuma convenção de descoberta para terceiros (R-05). |
| **Por que foi descartada** | Descartaria décadas de semântica padronizada de HTTP em troca de conveniência de nomeação, com custo direto na adoção externa. |

### A4 — REST com HATEOAS (HAL / Spring HATEOAS)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Cliente descobre transições disponíveis dinamicamente; evolução do servidor sem quebrar cliente genérico; `_links` expressam a máquina de estado. |
| **Contras** | Payload significativamente maior; nenhum cliente real do produto usa os links; a SPA já conhece as rotas; a máquina de estado é melhor exposta por campo explícito (`availableTransitions`) que por links. |
| **Por que foi descartada** | Custo em cada resposta sem consumidor. A necessidade real ("o que posso fazer com este recurso agora?") é atendida por um campo explícito de transições disponíveis, que é mais direto e testável. |

### A5 — REST com JSON:API

| Aspecto | Avaliação |
|---|---|
| **Prós** | Especificação completa e madura para paginação, filtros, inclusão de relacionados e erros; evita reinventar convenções. |
| **Contras** | Envelope verboso (`data`, `attributes`, `relationships`, `included`); curva de aprendizado para consumidores não familiarizados; ferramental Java menos maduro que o suporte nativo do Spring; conflita com `ProblemDetail` do Spring 6 para erros. |
| **Por que foi descartada** | O ganho de padronização não compensa a verbosidade e o atrito com o ferramental já escolhido. As convenções necessárias (paginação, erro) já estão fixadas por `ART-072`/`ART-073` e por RFC 7807. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Contrato compreensível sem documentação prévia — relevante para F8. |
| C+02 | Semântica HTTP (status, idempotência, cache) aproveitada integralmente. |
| C+03 | Ferramental completo: Spring MVC, springdoc, Postman, curl, proxies. |
| C+04 | Autorização e escopo de dados verificáveis por endpoint, exaustivamente. |
| C+05 | Custo de cada requisição é previsível e indexável (AP-08). |
| C+06 | Erros padronizados e rastreáveis por `traceId` e `code`. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | *Over-fetching*: o cliente recebe campos que não usa. | Mitigado por projeções específicas por caso de uso (listagem enxuta vs. detalhe completo). |
| C-02 | *Under-fetching*: telas compostas exigem múltiplas requisições. | Mitigado por endpoints de agregação dedicados (ex.: `/dashboard/summary`), desenhados e indexados. |
| C-03 | Discussões recorrentes sobre modelagem de recurso e escolha de verbo. | Reduzidas por AP-02/AP-03/AP-14, que fixam as regras. |
| C-04 | Ações de estado quebram a pureza REST. | Escolha consciente (AP-03), com justificativa técnica. |
| C-05 | Versionamento de contrato exige disciplina. | Tratado em [ADR-043](ADR-043-api-versioning.md). |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Sem *streaming* nem *server push* nativo; atualização em tempo real exige polling ou, futuramente, SSE/WebSocket por ADR próprio. |
| L-02 | Sem consulta ad hoc: toda necessidade de filtro nova exige alteração da API. |
| L-03 | Operações em lote não são nativas; exigem endpoint dedicado quando necessárias. |

### Custos

| Item | Custo |
|---|---|
| Implementação | Distribuído entre as features; padrão fixado uma vez |
| Rede | Payload JSON maior que binário; mitigado por compressão no proxy |
| Manutenção | Um endpoint novo por caso de uso novo |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Flexibilidade de consulta** do GraphQL | Autorização verificável e custo previsível | O escopo de dados por papel é o controle mais difícil de acertar; REST o torna testável por endpoint. |
| **Eficiência de payload** do gRPC | Consumo direto pelo navegador e depurabilidade | O cliente é um navegador; binário seria um retrocesso operacional. |
| **Descoberta dinâmica** (HATEOAS) | Payload enxuto | Descoberta é feita por OpenAPI, com melhor ferramental. |
| **Padronização total** (JSON:API) | Simplicidade e alinhamento com o ferramental Spring | Convenções necessárias já estão fixadas por artigos da constituição. |
| **Pureza REST** | Clareza semântica das transições de estado | Uma ação nomeada comunica melhor a intenção que um `PATCH` de status. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `*/controller` | Todo Controller segue AP-02 a AP-07. |
| `shared/error` | `GlobalExceptionHandler` implementa AP-09. |
| `shared/web` | Configuração de Jackson, paginação e conversores. |
| `report` | Exportação usa endpoints assíncronos com recurso de execução (`/report-executions/{id}`), pois AP-05 não comporta resposta longa síncrona. |

| Documento dependente | Relação |
|---|---|
| `docs/04-api/*` | Contratos concretos de todos os endpoints |
| `docs/ai/project-constitution.md` §4.8 | ART-070 a ART-076 |
| `docs/03-architecture/backend.md` §6.2 | Padrão de Controller |

| Spec dependente | Relação |
|---|---|
| Todas as specs | Seção "Endpoints utilizados" |

| ADR relacionado | Relação |
|---|---|
| [ADR-012](ADR-012-openapi.md) | Documentação do contrato |
| [ADR-013](ADR-013-dto.md) | Forma dos payloads |
| [ADR-017](ADR-017-exception-handling.md) | AP-09 |
| [ADR-043](ADR-043-api-versioning.md) | AP-01 |
| [ADR-050](ADR-050-future-integrations.md) | API pública em F8 |

## Impacto no banco

Não se aplica diretamente, porque o estilo de API não determina o modelo de dados. Dois efeitos indiretos:

| Efeito | Descrição |
|---|---|
| Paginação | `page`/`size` mapeiam para `LIMIT`/`OFFSET`. Em tabelas muito grandes, `OFFSET` alto degrada; paginação por cursor é a evolução prevista, viabilizada pela ordenação natural do UUIDv7 ([ADR-002](ADR-002-uuid.md)). |
| Filtros | Cada filtro de AP-08 exige índice de suporte; filtro sem índice não é publicado. |

## Impacto na API

Este ADR **é** a decisão de API. Consequências consolidadas em `docs/04-api/`:

| Item | Regra |
|---|---|
| Prefixo | `/api/v1` |
| Recursos | Plural, kebab-case |
| Ações de estado | Sub-recurso verbal via `POST` |
| Paginação | Envelope padronizado, `size` máximo 100 |
| Erro | RFC 7807 + `code` + `traceId` + `errors[]` |
| Idempotência | `Idempotency-Key` onde `ART-074` exigir |
| Concorrência | `version` no payload; `409` em conflito |
| Cache | `no-store` em toda resposta |

## Impacto no Frontend

| Item | Impacto |
|---|---|
| Camada de API | Um `*ApiService` por feature encapsula as chamadas; componentes nunca usam `HttpClient` (`ART-094`). |
| Tipagem | Interfaces TypeScript espelham os DTOs; geradas ou revisadas contra o OpenAPI ([ADR-012](ADR-012-openapi.md)). |
| Paginação | Componente de tabela consome o envelope padrão diretamente. |
| Erro | Interceptor único traduz `ProblemDetail` em mensagem e ação. |
| Ações de estado | Chamadas explícitas por ação, refletindo AP-03 na camada de serviço do frontend. |
| Telas compostas | Podem exigir múltiplas chamadas (C-02); endpoints de agregação são criados quando o número de chamadas prejudicar AQ-01. |

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Proxy reverso | Compressão gzip/brotli para JSON; TLS; headers de segurança. |
| Rate limit | Aplicado por rota e escopo ([ADR-045](ADR-045-rate-limit.md)). |
| Observabilidade | Métricas por endpoint e status ([ADR-046](ADR-046-observability.md)); a cardinalidade é controlada usando o **template** da rota, nunca a URL com IDs. |
| CORS | Origens explícitas por ambiente, `allowCredentials = true`. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | Toda rota é negada por padrão; públicas em allowlist explícita (`ART-085`). |
| S-02 | Nenhum dado sensível em query string — apareceria em logs de proxy e no histórico do navegador. Filtros por dado pessoal usam `POST` de busca quando necessário. |
| S-03 | `404` uniforme para recurso de outro tenant (AP-05). |
| S-04 | Erro nunca vaza stack trace, SQL ou nome de tabela ([ADR-017](ADR-017-exception-handling.md)). |
| S-05 | **Multi-tenant:** nenhum endpoint aceita `tenantId` em qualquer posição (MT-04). |
| S-06 | **LGPD:** endpoints de exportação de dados do titular fazem parte do contrato (AQ-12). |
| S-07 | **Auditoria:** toda operação de escrita gera trilha; o `traceId` da resposta correlaciona com o registro. |
| S-08 | Limite de tamanho de payload configurado no proxy e na aplicação, evitando exaustão de memória. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Paginação obrigatória impede resposta ilimitada (`ART-073`). |
| P-02 | Listagens usam projeções, não o grafo completo (DA-04). |
| P-03 | Compressão reduz JSON verboso em ~70%. |
| P-04 | *Under-fetching* (C-02) é a principal ameaça a AQ-01 em telas compostas; endpoints de agregação são a resposta. |
| P-05 | `no-store` (AP-13) elimina cache HTTP; o cache é do servidor ([ADR-040](ADR-040-cache-strategy.md)), não do navegador. |
| P-06 | `OFFSET` alto em paginação profunda é o gargalo conhecido; mitigado por limite de página e, futuramente, por cursor. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Requisições stateless: qualquer instância atende qualquer requisição. |
| E-02 | Custo por requisição previsível (AP-08), o que torna o rate limit um controle efetivo. |
| E-03 | Endpoints de agregação e relatórios pesados são candidatos naturais a extração de módulo (`architecture.md` §13). |
| E-04 | Operações longas usam padrão assíncrono com recurso de execução, evitando conexões longas. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Proliferação de endpoints de agregação sob medida, difíceis de manter | Média | Médio | Média |
| RK-02 | Filtro publicado sem índice degradando o banco | Média | Alto | Alta |
| RK-03 | Inconsistência de convenção entre features | Média | Médio | Média |
| RK-04 | Paginação profunda degradar consultas | Média | Médio | Média |
| RK-05 | Quebra de contrato afetando a SPA em produção | Média | Alto | Alta |
| RK-06 | Dado sensível exposto em query string | Baixa | Alto | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Endpoint de agregação exige justificativa de performance na spec e teste de latência | Revisão de spec |
| RK-02 | Todo filtro novo declara o índice que o sustenta; `EXPLAIN` na revisão | `database.md` §10.1 |
| RK-03 | Convenções em `ART-070`–`ART-075`; teste de contrato validando o OpenAPI contra `docs/04-api/` | Gate de contrato |
| RK-04 | `size` máximo 100; monitoramento de consultas lentas; cursor como evolução | [ADR-047](ADR-047-monitoring.md) |
| RK-05 | Regra de compatibilidade de [ADR-043](ADR-043-api-versioning.md); teste de contrato no pipeline | Pipeline |
| RK-06 | Regra S-02 em revisão; nenhum filtro por documento ou e-mail completo em query string | `review-checklist.md` |

## Referências

| Fonte | Uso |
|---|---|
| [RFC 9110 — HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110) | Semântica de métodos e status |
| [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) | Formato de erro (sucessora da RFC 7807) |
| [Microsoft — REST API Guidelines](https://github.com/microsoft/api-guidelines) | Convenções de nomenclatura e paginação |
| [Google — API Design Guide](https://cloud.google.com/apis/design) | Recursos, métodos customizados (AP-03) |
| [Stripe API Reference](https://docs.stripe.com/api) | Referência de erro, idempotência e paginação |
| [GitHub REST API](https://docs.github.com/en/rest) | Referência de versionamento e convenções |
| `docs/04-api/` | Contratos concretos |
