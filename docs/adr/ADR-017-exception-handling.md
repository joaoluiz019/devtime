# ADR-017 — Tratamento global de erros com RFC 7807 e códigos `DEVTIME-XXXX` estáveis

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-072`, `ART-113`.

## Data

2026-07-29

## Contexto

O erro é parte do contrato da API, não uma exceção a ele. Três consumidores dependem dele:

| Consumidor | Necessidade |
|---|---|
| Frontend | Distinguir "corrija este campo" de "esta operação é impossível agora" e reagir diferente |
| Suporte | Correlacionar o erro que o usuário viu com o log do servidor |
| Integrador externo (F8) | Tratar cada condição de erro programaticamente, sem parsear texto |

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Todo erro retorna RFC 7807 com `code`, `traceId` e `errors[]` | `ART-072` |
| R-02 | Todo erro de negócio tem código estável `DEVTIME-XXXX` | `ART-113` |
| R-03 | A resposta **nunca** contém stack trace, SQL, nome de tabela ou coluna, nem dado de outro tenant | `architecture.md` §8.2 |
| R-04 | Recurso de outro tenant retorna `404`, indistinguível de inexistente | `ART-024` |
| R-05 | Mensagens visíveis ao usuário passam por i18n | `ART-095` |
| R-06 | O `traceId` correlaciona a resposta com o log (AQ-11) | `architecture.md` §12 |

## Decisão

| # | Regra |
|---|---|
| EX-01 | Todo erro é traduzido por um **`@RestControllerAdvice` global** (`GlobalExceptionHandler`). Nenhum `try/catch` de tradução em Controller. |
| EX-02 | O corpo segue **RFC 7807** (`application/problem+json`), com os campos padrão `type`, `title`, `status`, `detail`, `instance`, estendidos por `code`, `traceId`, `timestamp` e, quando aplicável, `errors[]` e `conflictingResource`. |
| EX-03 | Todo erro carrega um `code` `DEVTIME-XXXX` **estável e imutável**, dentro das faixas de §6 da constituição. Um código aposentado nunca é reutilizado. |
| EX-04 | O mapeamento exceção → status é fixo e exaustivo (tabela abaixo). Não há exceção sem mapeamento: qualquer exceção não prevista vira `500` `DEVTIME-9001`. |
| EX-05 | Toda exceção `500` é registrada em nível `ERROR` com stack trace **no log**, nunca na resposta (R-03). |
| EX-06 | O `traceId` da resposta é o mesmo do log e do trace distribuído (R-06). |
| EX-07 | Exceções de negócio são uma hierarquia `sealed` com raiz `BusinessRuleException`, cada subtipo carregando seu `code`. |
| EX-08 | Violação de constraint de banco é mapeada **por nome de constraint** para um código de negócio específico; constraint não mapeada vira `500` `DEVTIME-9001`, sem vazar o nome. |
| EX-09 | `InvalidStateTransitionException` retorna `409` com `availableTransitions`, informando ao cliente o que **é** possível. |
| EX-10 | Mensagens são internacionalizadas por chave (R-05); o `code` é o identificador estável, a mensagem é apresentação. |
| EX-11 | O handler **nunca** engole exceção: ou traduz e responde, ou relança. |
| EX-12 | Erros `4xx` são registrados em nível `WARN` sem stack trace; erros `5xx` em `ERROR` com stack trace. |
| EX-13 | Todo novo `code` é registrado em `docs/02-domain/business-rules.md` §17 no mesmo PR que o introduz. |

### Mapeamento canônico

| Exceção | Status | Código base | Observação |
|---|---|---|---|
| `MethodArgumentNotValidException` / `ConstraintViolationException` | `400` | `DEVTIME-2000` | `errors[]` campo a campo |
| `HttpMessageNotReadableException` | `400` | `DEVTIME-2000` | JSON malformado; nunca expor a mensagem do parser |
| `AuthenticationException` | `401` | `DEVTIME-1001` | Mensagem uniforme |
| `AccessDeniedException` | `403` | `DEVTIME-1101` | Após confirmar que o recurso é do tenant |
| `EntityNotFoundException` | `404` | `DEVTIME-2002` | **Também** para recurso de outro tenant (R-04) |
| `OptimisticLockException` | `409` | `DEVTIME-2004` | Conflito de concorrência |
| `InvalidStateTransitionException` | `409` | `DEVTIME-2010` | Com `availableTransitions` |
| `DataIntegrityViolationException` | `409` | `DEVTIME-2001` | Mapeado por nome de constraint (EX-08) |
| `BusinessRuleException` e subtipos | `422` | Específico | Faixa conforme o domínio |
| `RateLimitExceededException` | `429` | `DEVTIME-9002` | Com `Retry-After` |
| Qualquer outra | `500` | `DEVTIME-9001` | Log `ERROR` + `traceId` |

**Formato canônico:**

```json
{
  "type": "https://devtime.app/errors/business-rule",
  "title": "Regra de negócio violada",
  "status": 422,
  "code": "DEVTIME-2102",
  "detail": "Já existe um registro de horas neste intervalo",
  "instance": "/api/v1/work-logs",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "timestamp": "2026-07-29T14:32:10.123-03:00",
  "errors": [
    { "field": "startedAt", "message": "Conflita com o registro CT-0001-42 (09:00–11:00)" }
  ],
  "conflictingResource": { "type": "WORK_LOG", "id": "0192f3a4-..." }
}
```

## Motivação

**Por que RFC 7807 (EX-02):** é o padrão IETF para erro em API HTTP. Adotá-lo entrega estrutura previsível sem inventar formato próprio, é suportado nativamente pelo Spring 6 (`ProblemDetail`) e é reconhecido por ferramentas e integradores — relevante para F8.

**Por que código estável além do status (EX-03):** o status HTTP é grosseiro. `422` cobre dezenas de regras diferentes; o cliente precisa distinguir "sobreposição de horário" de "período fechado" para reagir de forma útil. O `code` é o identificador **programático**; a mensagem é apresentação e pode mudar. Sem código, o cliente acabaria comparando strings — que quebram na primeira revisão de texto ou tradução.

**Por que tratamento global (EX-01):** `try/catch` disperso produz respostas inconsistentes e é o caminho mais comum para vazar stack trace. Centralizando, a garantia R-03 é aplicada em **um** lugar auditável.

**Por que `500` para exceção não mapeada (EX-04):** o comportamento padrão precisa ser **fechar**, não abrir. Uma exceção nova não deve produzir resposta vazada; deve produzir erro genérico, log completo e alerta.

**Por que mapear constraint por nome (EX-08):** `DataIntegrityViolationException` é genérica; sua mensagem contém nome de tabela, coluna e valor — tudo proibido por R-03. Mapear pelo nome da constraint (`uq_clients_tenant_document` → `DEVTIME-2401`) produz erro útil sem vazar estrutura. Isso torna a **convenção de nomenclatura de constraints parte do contrato de erro**, o que reforça a exigência de nomes explícitos nas migrations ([ADR-007](ADR-007-flyway.md)).

**Por que `availableTransitions` (EX-09):** um `409` que apenas diz "transição inválida" obriga o cliente a conhecer a máquina de estados. Informar as transições possíveis torna a resposta acionável e mantém a máquina de estados como conhecimento do servidor.

**Por que `traceId` na resposta (EX-06):** AQ-11 exige que o usuário reporte um erro citando um código e que o suporte recupere a requisição completa. Sem o `traceId` na resposta, a correlação depende de horário aproximado.

## Alternativas consideradas

### A1 — Formato de erro próprio (não RFC 7807)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Liberdade total de estrutura; possivelmente mais enxuto. |
| **Contras** | Integradores precisam aprender um formato específico; sem suporte nativo do Spring; perde-se o `Content-Type` padronizado; reinventa uma discussão já resolvida. |
| **Por que foi descartada** | Nenhum benefício técnico sobre o padrão. `ART-072` já fixa RFC 7807. |

### A2 — Apenas status HTTP, sem código de negócio

| Aspecto | Avaliação |
|---|---|
| **Prós** | Simplicidade; menos códigos a manter e documentar. |
| **Contras** | O cliente não distingue regras diferentes sob o mesmo status; tratamento programático impossível sem parsear mensagem; internacionalização quebra qualquer lógica baseada em texto. |
| **Por que foi descartada** | `422` sem código é praticamente inútil para o cliente. `ART-113` exige código estável. |

### A3 — Erros retornados como parte do corpo `200` (envelope de resultado)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Uniformidade de tratamento no cliente; comum em GraphQL e em algumas APIs RPC. |
| **Contras** | Descarta a semântica HTTP; proxies, monitoramento e ferramentas passam a ver 100% de sucesso, tornando a taxa de erro (alerta crítico de `architecture.md` §12) inútil; `Retry-After`, cache e clientes HTTP genéricos deixam de funcionar. |
| **Por que foi descartada** | Perderia a observabilidade de erro por status, que é a base do alerta de 5xx. |

### A4 — `try/catch` por Controller com respostas específicas

| Aspecto | Avaliação |
|---|---|
| **Prós** | Controle fino por endpoint; mensagens contextualizadas. |
| **Contras** | Repetição em todo Controller; inconsistência entre features; alto risco de vazamento (R-03); viola CL-02 de [ADR-016](ADR-016-controller-service-repository.md). |
| **Por que foi descartada** | Consistência de erro é requisito de contrato, não de conveniência local. |

### A5 — Exceções com mensagem pronta em português, sem i18n

| Aspecto | Avaliação |
|---|---|
| **Prós** | Mais simples; mensagem visível no código junto da regra. |
| **Contras** | Viola `ART-095`; impede internacionalização futura; acopla a camada de domínio à apresentação; mudar um texto vira alteração de código de domínio. |
| **Por que foi descartada** | O `code` é o contrato; a mensagem é apresentação. Separá-los permite ajustar texto sem tocar o domínio. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Formato de erro uniforme em toda a API, garantido em um único ponto. |
| C+02 | Cliente trata condições específicas por `code`, sem parsear texto. |
| C+03 | `traceId` correlaciona resposta, log e trace (AQ-11). |
| C+04 | Nenhum detalhe interno vaza (R-03), verificável em um lugar. |
| C+05 | `409` acionável com `availableTransitions`. |
| C+06 | Documentação de erro gerada e enumerada no OpenAPI (OA-06). |
| C+07 | Métricas por código de erro tornam-se possíveis. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Catálogo de códigos a manter e documentar. | Organizado por faixas (§6 da constituição) e obrigatório por EX-13. |
| C-02 | Toda regra nova exige decidir o código. | Força explicitação — é uma virtude. |
| C-03 | Handler centralizado cresce com o número de tipos de exceção. | Organizado por método por tipo, cada um trivial. |
| C-04 | Mapeamento por nome de constraint (EX-08) exige manutenção. | Poucas constraints geram erro esperado; o padrão é `500`. |
| C-05 | i18n adiciona indireção entre código e mensagem. | Necessária por `ART-095`. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Erros de negócio não se acumulam: a primeira violação interrompe (C-05 de [ADR-015](ADR-015-validation.md)). |
| L-02 | O `type` é uma URI de documentação; se a página não existir, perde valor. |
| L-03 | Erros ocorridos após o início da resposta (streaming de exportação) não podem ser convertidos em Problem Details. |

### Custos

| Item | Custo |
|---|---|
| Implementação | ~2 dias (handler, hierarquia de exceções, mapeamento de constraints) |
| Manutenção | Um código novo por regra nova |
| Runtime | Desprezível |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Simplicidade** (só status HTTP) | Tratamento programático preciso | Sem código, o cliente não consegue reagir de forma útil. |
| **Mensagens contextuais por endpoint** | Consistência e não-vazamento | Contexto é obtido por `detail` e `errors[]`, sem abrir mão da centralização. |
| **Detalhe técnico na resposta** (útil em depuração) | Segurança | O detalhe está no log, acessível por `traceId`. |
| **Mensagens no código** | Internacionalização e desacoplamento | O `code` é o contrato; o texto é apresentação. |
| **Acúmulo de erros de negócio** | Simplicidade e consistência de estado | Regras encadeadas não podem ser avaliadas isoladamente. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `shared/error` | `GlobalExceptionHandler`, hierarquia `sealed` de exceções, `ErrorCode`, mapeamento de constraints. |
| `shared/observability` | `TraceIdFilter` fornece o `traceId` (EX-06). |
| Toda feature | Lança exceções de negócio tipadas; nunca constrói resposta de erro. |
| `<feature>/controller` | Nenhum `try/catch` de tradução (EX-01). |

| Documento dependente | Relação |
|---|---|
| `docs/ai/project-constitution.md` §6 | Faixas de códigos |
| `docs/02-domain/business-rules.md` §17 | Catálogo de códigos (EX-13) |
| `docs/03-architecture/architecture.md` §8.2 | Fluxo de tratamento |
| `docs/04-api/*` | Códigos por endpoint |
| `docs/03-architecture/frontend.md` §11 | Tratamento no cliente |

| Spec dependente | Relação |
|---|---|
| Todas as specs | Seção "Casos de erro" referencia os códigos aplicáveis |

| ADR relacionado | Relação |
|---|---|
| [ADR-011](ADR-011-rest-api.md) | Semântica de status |
| [ADR-015](ADR-015-validation.md) | Origem de `400` e `422` |
| [ADR-012](ADR-012-openapi.md) | Documentação dos erros |
| [ADR-019](ADR-019-logging.md) / [ADR-046](ADR-046-observability.md) | `traceId` e níveis de log |
| [ADR-001](ADR-001-multi-tenant.md) | `404` para outro tenant |

## Impacto no banco

| Item | Impacto |
|---|---|
| Nomenclatura de constraints | Torna-se **parte do contrato de erro** (EX-08): `uq_*`, `ck_*`, `fk_*` conforme §5 da constituição. Renomear uma constraint mapeada altera o comportamento de erro. |
| Optimistic locking | A coluna `version` (`ART-052`) origina `409` `DEVTIME-2004`. |
| Mensagens | Nenhuma mensagem do banco chega ao cliente. |

## Impacto na API

| Item | Impacto |
|---|---|
| `Content-Type` | `application/problem+json` em toda resposta de erro. |
| Campos | Conforme EX-02; `errors[]` presente em `400` e opcional em `422`. |
| Documentação | Toda resposta de erro documentada com os códigos possíveis (OA-06). |
| Estabilidade | Códigos são contrato: remover ou ressignificar um código é mudança incompatível ([ADR-043](ADR-043-api-versioning.md)). |
| `429` | Acompanhado de `Retry-After` ([ADR-045](ADR-045-rate-limit.md)). |

## Impacto no Frontend

| Item | Impacto |
|---|---|
| Interceptor | `errorInterceptor` único traduz `ProblemDetail` em ação de UI. |
| `400` | `errors[]` mapeado para os controles do formulário por `field`. |
| `422` | Mensagem de negócio no nível do formulário ou em *toast*, por `code`. |
| `401` | Dispara refresh ([ADR-009](ADR-009-refresh-token.md)); falha leva ao login. |
| `403` | Mensagem de permissão insuficiente. |
| `404` | "Não encontrado", sem sugerir existência em outro contexto. |
| `409` | Conflito de concorrência oferece recarregar; conflito de estado usa `availableTransitions` para orientar. |
| `500` | Mensagem genérica **exibindo o `traceId`**, para que o usuário possa informá-lo ao suporte (AQ-11). |
| Regra | O frontend trata por `code`, **nunca** por texto da mensagem. |

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Logs | `4xx` em `WARN`, `5xx` em `ERROR` (EX-12); volume dimensionado na retenção. |
| Alertas | Taxa de 5xx acima de 1% em 5 min é alerta crítico (`architecture.md` §12). |
| Métricas | Contador por `code` e por status, com cardinalidade controlada. |
| Proxy | Não deve substituir a página de erro da aplicação por página própria em respostas `5xx` da API. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | R-03 é o controle central: nenhum stack trace, SQL, nome de tabela/coluna ou caminho de arquivo na resposta. |
| S-02 | Mensagens uniformes em autenticação evitam enumeração de contas (A07 de OWASP). |
| S-03 | `404` para recurso de outro tenant (R-04) impede enumeração cross-tenant. |
| S-04 | O `traceId` é opaco e não revela informação; é seguro exibi-lo ao usuário. |
| S-05 | EX-08 impede que o nome de uma constraint revele a estrutura do banco. |
| S-06 | **Multi-tenant:** nenhuma mensagem de erro pode conter dado de outro tenant — inclusive em `conflictingResource`, que só referencia recursos do tenant corrente. |
| S-07 | **LGPD:** mensagens não ecoam valores de campos sensíveis; `detail` não repete CPF, e-mail ou conteúdo de descrição. |
| S-08 | **Auditoria:** erros `403` e `401` recorrentes são sinal de sondagem e geram evento de segurança. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Construir a resposta de erro é barato; o custo real é a criação da exceção (preenchimento de stack trace). |
| P-02 | Exceções de negócio esperadas e de alta frequência podem suprimir o stack trace (`writableStackTrace = false`) sem perda, pois seu contexto é o `code`. |
| P-03 | Exceção não deve ser usada como fluxo de controle normal em caminho quente. |
| P-04 | Log de `5xx` com stack trace é custoso; a taxa é monitorada. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | O catálogo de códigos cresce com as features, organizado por faixas. |
| E-02 | O handler não mantém estado; escala com as instâncias. |
| E-03 | Métricas por código exigem controlar cardinalidade (o conjunto de códigos é finito e conhecido, portanto seguro). |
| E-04 | Em F8, integradores externos dependerão da estabilidade dos códigos — o que reforça EX-03. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Vazamento de detalhe interno em alguma resposta de erro | Média | Alto | **Alta** |
| RK-02 | Código reutilizado com outro significado, quebrando clientes | Baixa | Alto | Média |
| RK-03 | Exceção não mapeada gerando `500` em fluxo esperado | Média | Médio | Média |
| RK-04 | Frontend tratando erro por texto em vez de `code` | Média | Médio | Média |
| RK-05 | Constraint renomeada quebrando o mapeamento de EX-08 | Baixa | Médio | Baixa |
| RK-06 | Catálogo de códigos divergir da documentação | Média | Médio | Média |
| RK-07 | Erro em resposta já iniciada (L-03) deixar o cliente sem informação | Baixa | Médio | Baixa |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Teste que percorre todos os tipos de exceção e verifica ausência de padrões proibidos (`Exception`, `SELECT`, nome de tabela) no corpo | Suíte de erro |
| RK-02 | VR-01 análogo: código aposentado nunca reutilizado; teste que compara o catálogo entre releases | Teste de compatibilidade |
| RK-03 | Monitoramento da taxa de `DEVTIME-9001`; toda ocorrência em fluxo esperado vira tarefa de mapeamento | [ADR-047](ADR-047-monitoring.md) |
| RK-04 | Regra explícita no frontend; revisão; testes de UI baseados em `code` | `review-checklist.md` |
| RK-05 | Teste de integração que provoca cada violação mapeada e verifica o código retornado | Teste de constraint |
| RK-06 | EX-13; teste que compara os códigos do enum com a tabela de `business-rules.md` §17 | Teste de conformidade |
| RK-07 | Exportações grandes usam padrão assíncrono com recurso de execução, evitando resposta em streaming com erro tardio | [ADR-036](ADR-036-report-generation.md) |

## Referências

| Fonte | Uso |
|---|---|
| [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) | Formato adotado (sucessora da RFC 7807) |
| [RFC 7807 — Problem Details (original)](https://www.rfc-editor.org/rfc/rfc7807) | Referência citada por `ART-072` |
| [Spring — ProblemDetail e ErrorResponse](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) | Implementação EX-01/EX-02 |
| [Stripe — Error handling](https://docs.stripe.com/error-handling) | Referência de códigos estáveis |
| [OWASP — Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html) | Base de R-03 |
| [Google — API Design: Errors](https://cloud.google.com/apis/design/errors) | Estrutura e códigos |
| `docs/02-domain/business-rules.md` §17 | Catálogo de códigos |
