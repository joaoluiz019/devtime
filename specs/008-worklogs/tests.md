# 008 — Work Logs · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-008-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-102: permite sessões que se tocam exatamente`.

> **SQ-02 — Ordem inegociável.** Complexidade **Crítica**. As suítes `TS-008-01` (sobreposição) e `TS-008-05` (cálculo) são escritas, revisadas e **aprovadas** antes de qualquer linha de `OverlapDetector`, `WorkLogCalculator` ou `RoundingPolicy`.
>
> As duas tabelas normativas da spec são o oráculo. Um erro de comparação (`<` em vez de `<=`) ou de direção de arredondamento passa despercebido em revisão de código e produz **superfaturamento silencioso** — a falha que RP-01 descreve e que destrói a confiança do cliente de forma irrecuperável.
>
> **SQ-03:** duas aprovações obrigatórias no PR.

**Relógio:** todo teste temporal injeta um `Clock` fixo. Nenhum usa `Instant.now()` real — testes que dependem do momento de execução falham na virada do dia, no horário de verão e em 29 de fevereiro, e passam despercebidos o resto do tempo.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `OverlapDetector`, `WorkLogCalculator`, `RoundingPolicy`, `WorkDateResolver`, `WorkLogValidator`, `OveragePolicyEvaluator` | JUnit 5 + AssertJ + `@ParameterizedTest` | **≥ 95%** |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Ordem da §6.1, propagação, travamento |
| **Concorrência** | Sobreposição, fechamento, desnormalizados | JUnit + `CountDownLatch` | Sem violação de invariante |
| Temporal | Fuso, meia-noite, virada de período, horário de verão | JUnit + `Clock` fixo | 4 bordas |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Os 9 endpoints |
| Isolamento | Tenancy + escopo de `MEMBER` | Suíte dedicada + inspeção de SQL | Todos os endpoints |
| Contrato cruzado | Cálculo do frontend × do backend | Jest + JUnit sobre a mesma tabela | 100% das linhas |
| Frontend | Stores, formulário de horário, avisos | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Registrar, editar, excluir, conferir saldo | Playwright | Jornada completa |
| Performance | Detecção de sobreposição, criação, listagem | k6 + JMH | Metas da §20 |
| Segurança | Isolamento, escopo, campos forjados | JUnit + scripts | Vetores da §19 |
| Regressão | Tabelas normativas | CI | 100% verde |

---

## 3. Testes unitários

### TS-008-01 — Tabela normativa de sobreposição (RN-102)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `OverlapDetector` reproduz **exatamente** os 9 casos da §6.2 da spec |
| **Pré-condição** | `worklog-overlap-cases.csv` com os 9 casos normativos |
| **Passos** | Para cada caso, com a sessão existente 09:00–11:00, testar a nova sessão e comparar com o resultado esperado |
| **Resultado esperado** | Casos 1, 2 e 3 permitidos (tocam-se ou não intersectam); casos 4 a 9 rejeitados com `DEVTIME-2102`. **Igualdade exata nos 9** |

**Casos verificados:**

| # | Existente | Nova | Esperado |
|:--:|---|---|:--:|
| 1 | 09:00–11:00 | 11:00–12:00 | ✅ |
| 2 | 09:00–11:00 | 13:00–14:00 | ✅ |
| 3 | 09:00–11:00 | 08:00–09:00 | ✅ |
| 4 | 09:00–11:00 | 09:30–10:30 | ❌ |
| 5 | 09:00–11:00 | 10:00–12:00 | ❌ |
| 6 | 09:00–11:00 | 08:00–10:00 | ❌ |
| 7 | 09:00–11:00 | 08:00–15:00 | ❌ |
| 8 | 09:00–11:00 | 09:00–11:00 | ❌ |
| 9 | 09:00–11:00 | 10:59–11:01 | ❌ |

> Os casos 1 e 3 são os que falham contra uma implementação com comparação **não estrita** (`<=`). São a razão de esta suíte existir.

### TS-008-02 — Escopo da detecção de sobreposição
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as cinco dimensões da §6.2 |
| **Passos** | Verificar sobreposição contra: (a) registro de **outro** usuário; (b) registro **excluído**; (c) **o próprio** registro em edição; (d) registro em **outro ticket**; (e) registro de **outro tenant** |
| **Resultado esperado** | (a) permitido — restrição é por usuário (CE-07); (b) ignorado; (c) excluído da comparação — editar sem mudar horário não conflita consigo mesmo; (d) **rejeitado** — a sobreposição é por pessoa, não por ticket; (e) não considerado (RS-04, OB-03) |

### TS-008-03 — Consulta de sobreposição não carrega entidades (CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia de `EXISTS` + `LIMIT 1` |
| **Passos** | Detectar sobreposição em usuário com 10.000 registros, inspecionando o SQL emitido |
| **Resultado esperado** | Uma consulta com `EXISTS` e `LIMIT 1`; **nenhum** `SELECT *` carregando registros; segunda consulta pelo conflitante ocorre **apenas** quando há conflito |

### TS-008-04 — Detecção usa o índice dedicado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `idx_work_logs_overlap` é utilizado |
| **Passos** | Inspecionar o plano de execução com 100.000 registros |
| **Resultado esperado** | Index scan sobre `idx_work_logs_overlap`; **nenhum** sequential scan em `work_logs` |

### TS-008-05 — Tabela normativa de cálculo (RN-110 a RN-113)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `WorkLogCalculator` e `RoundingPolicy` reproduzem **exatamente** os 8 casos da §6.3 |
| **Pré-condição** | `worklog-calculation-cases.csv` com os casos normativos |
| **Passos** | Para cada linha, calcular `gross`, `net` e `billable` e comparar |
| **Resultado esperado** | Igualdade exata, incluindo: 11:30:59 → 150 min (truncamento); 112 min com `rounding = 15` → **105**, nunca 120; sessão 22h→01h30 → 210 min com `workDate` do dia de início; casos inválidos rejeitados por RN-103 e RN-115 |

### TS-008-06 — Direção do arredondamento (RN-113, PR-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o arredondamento é **sempre** para baixo |
| **Passos** | Para `roundingMinutes` de 5, 6, 10, 15 e 30, arredondar valores logo acima e logo abaixo de cada múltiplo; testar `roundingMinutes = 0` |
| **Resultado esperado** | Todo resultado é ≤ ao valor original; **nenhum** caso produz valor maior; `0` desativa o arredondamento. Um único caso de arredondamento para cima falha o build |

### TS-008-07 — Truncamento de segundos (RN-010)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `floor` em vez de `round` |
| **Passos** | Sessões com 0, 1, 29, 30, 31 e 59 segundos além do minuto exato |
| **Resultado esperado** | Todas produzem o **mesmo** `grossMinutes`. Uma implementação com `round` divergiria a partir de 30 segundos |

### TS-008-08 — `WorkDateResolver` (RN-108, RN-009)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a conversão de fuso antes da extração da data |
| **Pré-condição** | Tenant em `America/Sao_Paulo`; instantes em UTC |
| **Passos** | 1. `startedAt` 2026-07-10T23:00Z (20:00 local). 2. `startedAt` 2026-07-11T02:00Z (23:00 local do dia 10). 3. Sessão 22:00→01:30 local |
| **Resultado esperado** | (1) `workDate` = 10/07; (2) `workDate` = **10/07**, não 11/07 — a conversão precede a extração; (3) `workDate` = dia de início |

### TS-008-09 — `OveragePolicyEvaluator` (RN-231 a RN-234)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as três políticas e a ausência de divisão |
| **Passos** | Com saldo restante de 30 min, registrar 120 min faturáveis sob: (a) `BLOCK`; (b) `WARN`; (c) `ALLOW_BILLABLE`; (d) `BLOCK` com registro não faturável; (e) contrato `HOURLY_OPEN` |
| **Resultado esperado** | (a) `DEVTIME-2220`, **nenhum** registro parcial de 30 min criado (RN-234); (b) `201` com `warnings[]` contendo `DEVTIME-2221`; (c) `201` com excedente marcado; (d) `201` — não faturável não consome saldo; (e) `201` sem avaliação de limiar (CE-10) |

### TS-008-10 — `RetroactiveWindowPolicy` (RN-120)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a janela e a exceção por papel |
| **Passos** | Com `retroactiveLimitDays = 30`, lançar em 29, 30, 31 e 45 dias atrás, como `MEMBER` e como `ADMIN` |
| **Resultado esperado** | `MEMBER`: 29 e 30 aceitos, 31 e 45 rejeitados com `DEVTIME-2120`; `ADMIN`: todos aceitos |

---

## 4. Testes de integração

### TS-008-11 — Ordem de aplicação da §6.1 (OB-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a ordem é **normativa**, determinando qual erro o usuário vê |
| **Passos** | Enviar payloads que violam várias regras simultaneamente: (a) sobreposto **e** descrição curta; (b) acima de 24h **e** fora da vigência; (c) sem período **e** categoria inativa; (d) sem permissão **e** ticket inexistente |
| **Resultado esperado** | (a) `DEVTIME-2102` — sobreposição vem antes de descrição; (b) `DEVTIME-2103` — 24h antes de vigência; (c) `DEVTIME-2107` — período antes de categoria; (d) `DEVTIME-1101` — permissão antes de existência. Alterar a ordem quebra este teste, que é seu propósito |

### TS-008-12 — Validações puras antes de I/O
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de desempenho da §6.1 |
| **Passos** | Enviar payload com `endedAt < startedAt`, inspecionando as consultas emitidas |
| **Resultado esperado** | Rejeição sem **nenhuma** consulta de sobreposição, de período ou de saldo. Requisição obviamente inválida não gera I/O |

### TS-008-13 — Cópia imutável de contrato e cliente (RN-109, INV-WKL-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-005 |
| **Passos** | 1. Criar work log em ticket do contrato X. 2. Conferir `contractId` e `clientId`. 3. Mover o ticket para o contrato Y. 4. Conferir novamente. 5. Tentar alterar por `PATCH` |
| **Resultado esperado** | (2) e (4) valores **idênticos** — o registro não segue o ticket; (5) campos ignorados. Um relatório passado não muda porque um ticket foi reclassificado hoje |

### TS-008-14 — Propagação transacional (RN-308, RN-312, `consumedMinutes`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que os três desnormalizados são atualizados na mesma transação |
| **Passos** | 1. Criar work log em ticket `DONE`. 2. Conferir `ticket.spentMinutes`, `ticket.status` e `period.consumedMinutes`. 3. Injetar falha após a persistência. 4. Conferir o estado. 5. Inspecionar o SQL do incremento |
| **Resultado esperado** | (2) todos atualizados e o ticket reaberto para `IN_PROGRESS`; (4) **rollback total** — nenhum total alterado, nenhum work log órfão; (5) `UPDATE ... SET x = x + ?`, nunca leitura-modificação-escrita |

### TS-008-15 — Resposta traz o saldo atualizado (OB-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a justificativa da atualização síncrona |
| **Passos** | Criar work log e inspecionar o corpo da resposta `201` |
| **Resultado esperado** | `availableMinutes`, `consumedMinutes` e `remainingMinutes` já refletem o registro criado; nenhuma segunda requisição é necessária |

### TS-008-16 — Travamento e destravamento (RN-121, INV-WKL-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda em todos os caminhos |
| **Passos** | 1. Fechar o período. 2. Tentar editar. 3. Tentar excluir. 4. Tentar mover a data. 5. Reabrir o período. 6. Repetir 2 a 4 |
| **Resultado esperado** | (2) a (4) `409 DEVTIME-2121`; (6) todas permitidas após a reabertura. A guarda está no **service**, verificada também por chamada interna (SG-05) |

### TS-008-17 — Transferência entre períodos (RN-124)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a exigência de ambos abertos |
| **Passos** | Mover `workDate` entre períodos nas combinações: `OPEN`→`OPEN`, `OPEN`→`REOPENED`, `OPEN`→`CLOSED`, `REOPENED`→`OPEN`, `OPEN`→`SCHEDULED` |
| **Resultado esperado** | As três primeiras combinações com ambos abertos são permitidas e recalculam os dois períodos; destino `CLOSED` → `DEVTIME-2124`; destino `SCHEDULED` → `DEVTIME-2107` |

### TS-008-18 — Exclusão devolve saldo (RN-125)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a reversão dos desnormalizados |
| **Passos** | 1. Criar work log de 150 min faturáveis. 2. Excluir. 3. Conferir ticket, período e consultas. 4. Conferir o banco fisicamente |
| **Resultado esperado** | (3) `spentMinutes` e `consumedMinutes` reduzidos em 150; registro invisível a consultas e relatórios (RN-704); (4) presente fisicamente com `deleted_at` preenchido |

### TS-008-19 — Equivalência entre `create` e `createFromTimer` (RN-159)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-14 — que não existem dois caminhos de validação |
| **Passos** | Para cada uma das regras RN-102 a RN-120, submeter um caso inválido pelos **dois** caminhos e comparar o erro retornado |
| **Resultado esperado** | Erro **idêntico** em ambos os caminhos, para todas as regras. Este teste é o que impede que uma alteração futura em `create` deixe o caminho do timer para trás |

### TS-008-20 — Validação prévia não persiste (FA-01, CP-19)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `/validate` é somente leitura |
| **Passos** | 1. Chamar `/validate` com dados válidos. 2. Com dados sobrepostos. 3. Conferir o banco após cada uma |
| **Resultado esperado** | Nenhum work log criado; nenhum total de ticket ou período alterado; nenhuma auditoria gerada; a resposta traz `conflicts[]`, `calculated` e `balancePreview` corretos |

### TS-008-21 — `MEMBER` e escopo de dados (§9 `permissions.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-02 em todas as saídas |
| **Pré-condição** | 100 work logs no tenant, 20 do usuário `MEMBER` |
| **Passos** | 1. Listar. 2. Conferir o total da paginação. 3. Consultar `/totals`. 4. Consultar `/calendar`. 5. Acessar registro de colega por id. 6. Inspecionar o SQL |
| **Resultado esperado** | (1) 20 registros; (2) total **20**, não 100; (3) e (4) totais apenas dos próprios; (5) `404 DEVTIME-2002`, nunca `403` (CE-P-04); (6) filtro na cláusula `WHERE`, nunca em memória |

---

## 5. Testes temporais

### TS-008-22 — Meia-noite e virada de período (RN-108, CE-01, CE-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar R-02 mitigado |
| **Pré-condição** | `Clock` fixo; períodos consecutivos com virada em 31/07 |
| **Passos** | 1. Sessão 22:00 (dia 10) → 01:30 (dia 11). 2. Sessão 23:00 (31/07) → 01:00 (01/08). 3. Sessão 23:59 → 00:01 |
| **Resultado esperado** | (1) registro único, `workDate` = 10; (2) alocada **integralmente** ao período de julho; (3) registro único de 2 minutos, `workDate` do dia de início. Nenhuma divisão automática em nenhum caso |

### TS-008-23 — Horário de verão (CE-03, CE-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que instantes em UTC eliminam a ambiguidade |
| **Pré-condição** | Fuso com transição de horário de verão; `Clock` fixo |
| **Passos** | 1. Sessão atravessando a hora **repetida**. 2. Sessão atravessando a hora **inexistente**. 3. `workDate` em ambos os casos |
| **Resultado esperado** | Duração calculada é o tempo **real** decorrido em ambos; `workDate` resolvido pela regra de transição da IANA; nenhuma exceção lançada |

### TS-008-24 — Tolerância de relógio (RN-118)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a janela de 2 minutos |
| **Passos** | `endedAt` a 0, 1, 2, 3 e 10 minutos no futuro |
| **Resultado esperado** | 0, 1 e 2 aceitos; 3 e 10 rejeitados com `DEVTIME-2118` |

### TS-008-25 — Calendário no fuso do tenant
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o agrupamento correto |
| **Pré-condição** | Registros próximos à meia-noite local |
| **Passos** | Consultar o calendário mensal e conferir os totais por dia |
| **Resultado esperado** | Agrupamento pela **data local**, não por UTC; um registro às 22:00 locais aparece no dia local, não no dia seguinte |

---

## 6. Testes de concorrência

### TS-008-26 — Cem criações sobrepostas simultâneas (R-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a mitigação do risco crítico da feature |
| **Pré-condição** | Nenhum work log; 100 threads sincronizadas por `CountDownLatch`; intervalos mutuamente sobrepostos |
| **Passos** | 100 criações simultâneas para o mesmo usuário |
| **Resultado esperado** | **No máximo um** registro persistido; as demais `422 DEVTIME-2102`; nenhuma sobreposição na base; `WorkLogConsistencyJob` não encontra violação de INV-WKL-05 |

> Este é o teste mais importante desta feature. A garantia de RN-102 é da **aplicação**, não do banco (OB-02, RS-05). Sem ele, a ausência de sobreposição é uma suposição, não um fato.

### TS-008-27 — Registro durante fechamento de período
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não há janela entre criação e travamento |
| **Passos** | Uma requisição cria work log enquanto outra fecha o período |
| **Resultado esperado** | Ou o registro é criado e travado pelo fechamento, ou a criação falha com `DEVTIME-2121`; **nunca** existe work log sem `lockedAt` em período `CLOSED` |

### TS-008-28 — Registros simultâneos e `consumedMinutes`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o incremento no banco não perde atualizações |
| **Passos** | 20 work logs de 30 min criados simultaneamente por usuários distintos no mesmo período |
| **Resultado esperado** | `consumedMinutes = 600`; nenhuma atualização perdida; havendo divergência, o reconciliador restaura |

### TS-008-29 — Edição simultânea do mesmo registro
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o *optimistic locking* (RN-004) |
| **Passos** | Duas edições simultâneas com a mesma `version` |
| **Resultado esperado** | Uma `200`, outra `409 DEVTIME-2004`; os totais refletem apenas a edição aplicada, sem soma dupla |

---

## 7. Testes de API

### TS-008-30 — Contrato dos 9 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `Location` no `201`; `warnings[]` presente em `WARN`; erros em RFC 7807 com `code`; o conflito de sobreposição traz o registro conflitante; OpenAPI bate com o real |

### TS-008-31 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel; para ownership, testar registro próprio e de terceiro |
| **Resultado esperado** | Conforme a §7 de `permissions.md`; `MEMBER` sem `WORKLOG_VIEW_ANY` nem `CREATE_FOR_OTHER`; `VIEWER` só lê; OWN-01 verificada (o dono é o `userId`, não quem criou) |

### TS-008-32 — Ownership de registro criado por terceiro (OWN-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o dono é o `userId`, não o criador |
| **Passos** | 1. `MANAGER` cria registro para o membro X. 2. X edita o registro. 3. Outro membro Y tenta editar |
| **Resultado esperado** | (2) permitido por `WORKLOG_UPDATE_OWN` — X é o dono; (3) `404` (fora do escopo de Y) |

---

## 8. Testes de contrato cruzado

### TS-008-33 — Cálculo do frontend × do backend (FM-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar R-07 mitigado |
| **Pré-condição** | O **mesmo** `worklog-calculation-cases.csv` consumido pelas suítes Jest e JUnit |
| **Passos** | Executar a tabela normativa nas duas linguagens e comparar linha a linha |
| **Resultado esperado** | 100% das linhas com resultado idêntico, incluindo truncamento de segundos e direção do arredondamento. Divergência aqui faz a UI exibir um valor e o servidor salvar outro |

---

## 9. Testes de frontend

### TS-008-34 — Cálculo ao vivo no formulário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o retorno imediato do `dt-time-range-input` |
| **Passos** | Alterar início, fim e pausas observando o valor de `net` |
| **Resultado esperado** | Atualização imediata; valor coincide com o salvo pelo servidor; sessão atravessando a meia-noite calculada corretamente |

### TS-008-35 — Exibição do arredondamento (OB-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a mitigação de CX-14 |
| **Passos** | Com `roundingMinutes = 15`: sessões de 10, 20 e 30 minutos |
| **Resultado esperado** | Valor bruto e arredondado exibidos lado a lado quando divergem; a sessão de 10 min exibe alerta de que o resultado será zero **antes** do envio, evitando o `DEVTIME-2115` surpresa |

### TS-008-36 — Aviso de sobreposição com link
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a acionabilidade do erro |
| **Passos** | Submeter registro sobreposto |
| **Resultado esperado** | `dt-overlap-warning` exibe o registro conflitante com horário, ticket e link navegável — o usuário resolve sem procurar manualmente |

### TS-008-37 — Prévia de saldo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `dt-balance-preview` |
| **Passos** | Preencher o formulário e observar antes de salvar |
| **Resultado esperado** | Saldo antes e depois exibidos; em `WARN`, alerta de excedente antes do envio; em `BLOCK`, o botão de salvar indica que o registro será rejeitado |

---

## 10. Testes E2E

### TS-008-38 — Jornada completa do registro
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário de ponta a ponta |
| **Passos** | 1. Registrar em P23. 2. Conferir na lista P21 e no calendário P22. 3. Conferir o saldo do contrato. 4. Editar. 5. Duplicar com novo horário. 6. Tentar duplicar com o mesmo horário. 7. Excluir. 8. Conferir o saldo devolvido |
| **Resultado esperado** | Cada etapa reflete o estado correto; (6) rejeitada por sobreposição; (8) saldo volta ao valor anterior |

---

## 11. Testes de performance

### TS-008-39 — Detecção de sobreposição com volume (R-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta mais crítica da §20 |
| **Pré-condição** | 100.000 work logs do mesmo usuário |
| **Passos** | 10.000 detecções com intervalos aleatórios, medindo p95 |
| **Resultado esperado** | **p95 < 50 ms**; index scan sobre `idx_work_logs_overlap`; tempo praticamente independente do tamanho da tabela |

### TS-008-40 — Criação completa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta do caminho mais quente |
| **Passos** | 5.000 criações medindo p95 |
| **Resultado esperado** | p95 < 300 ms, incluindo todas as validações e a propagação dos três desnormalizados |

### TS-008-41 — Listagem e calendário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as metas de leitura |
| **Pré-condição** | Tenant com 500.000 registros |
| **Passos** | Listagem com filtros; calendário mensal; totais agregados |
| **Resultado esperado** | Listagem p95 < 400 ms com projeção; calendário p95 < 300 ms; totais p95 < 500 ms; nenhuma consulta N+1 |

---

## 12. Testes de segurança

### TS-008-42 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 |
| **Passos** | Para cada um dos 9 endpoints, acessar recurso do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em todos, nunca `403` |

### TS-008-43 — Campos derivados forjados (SG-06, SG-07, SG-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum campo calculado é aceito da requisição |
| **Passos** | Enviar `contractId`, `clientId`, `contractPeriodId`, `netMinutes`, `grossMinutes`, `billableMinutes`, `source`, `timerId`, `lockedAt` e `editCount` em `POST` e `PATCH` |
| **Resultado esperado** | Todos ignorados ou rejeitados; valores derivados corretamente. `netMinutes` forjado é o vetor mais grave — permitiria inflar a cobrança |

### TS-008-44 — Guarda de travamento em chamada interna (SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-01 |
| **Passos** | Chamar `WorkLogService.update` diretamente, sem passar pelo controller, sobre registro travado |
| **Resultado esperado** | `DEVTIME-2121`. A guarda está no service; um job ou outra feature que chamasse o serviço também seria bloqueado |

### TS-008-45 — Ausência de `description` em log (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-18, decorrente da análise de LGPD da §19.1 |
| **Passos** | Executar criação, edição, exclusão e rejeições capturando os logs |
| **Resultado esperado** | Nenhum log contém `description`; presentes apenas ids, `ticketKey`, minutos e traceId |

### TS-008-46 — XSS por descrição (SG-10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape em todas as saídas |
| **Passos** | Registro com payload em `description`; renderizar em P21, P23, CSV e PDF de `012` |
| **Resultado esperado** | Texto literal em todas as saídas; CSV sem fórmula injetável |

---

## 13. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-008-47 | Tabela de sobreposição (`TS-008-01`) | **Toda** alteração em `OverlapDetector`, no índice ou em RN-102 |
| TS-008-48 | Tabela de cálculo (`TS-008-05`, `TS-008-33`) | Toda alteração em `WorkLogCalculator`, `RoundingPolicy` ou no cálculo do frontend |
| TS-008-49 | Concorrência (`TS-008-26`) | Toda alteração no caminho de criação ou na estratégia de detecção |
| TS-008-50 | Temporais (`TS-008-22`, `TS-008-23`) | Toda alteração em `WorkDateResolver`, fuso ou resolução de período |
| TS-008-51 | Equivalência com o timer (`TS-008-19`) | **Toda** alteração em qualquer validação de work log — é o que impede o caminho do timer de divergir |
| TS-008-52 | Escopo de `MEMBER` (`TS-008-21`) | Toda alteração em listagem, totais ou calendário |
| TS-008-53 | Isolamento (`TS-008-42`) | Todo endpoint novo |

**Política:** `TS-008-01`, `TS-008-05` e `TS-008-26` rodam integralmente em todo PR que toque esta feature, sem amostragem. São os três testes cuja falha silenciosa produz cobrança incorreta ao cliente.

---

## 14. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-101 | TS-008-11 | AC-008-01 |
| RN-102 | TS-008-01, TS-008-02, TS-008-03, TS-008-04, TS-008-26, TS-008-39 | AC-008-07, AC-008-19, AC-008-20, AC-008-39, AC-008-40, AC-008-52, AC-008-60 |
| RN-103 | TS-008-05, TS-008-11 | AC-008-21, AC-008-42 |
| RN-104 | TS-008-11 | AC-008-08, AC-008-33 |
| RN-105 | TS-008-11 | AC-008-32, AC-008-44 |
| RN-106 | TS-008-31, TS-008-32 | AC-008-11, AC-008-37 |
| RN-107 | TS-008-17, TS-008-22 | AC-008-09, AC-008-29 |
| RN-108 | TS-008-08, TS-008-22 | AC-008-06, AC-008-46 |
| RN-109 | TS-008-13, TS-008-43 | AC-008-10, AC-008-56 |
| RN-110 a RN-112 | TS-008-05, TS-008-07, TS-008-33 | AC-008-01 a AC-008-04 |
| RN-113 | TS-008-05, TS-008-06, TS-008-35 | AC-008-05, AC-008-43 |
| RN-114 a RN-116 | TS-008-05, TS-008-12 | AC-008-22, AC-008-23, AC-008-24 |
| RN-117 | TS-008-11 | AC-008-25 |
| RN-118 | TS-008-24 | AC-008-26, AC-008-41 |
| RN-119 | TS-008-11 | AC-008-27 |
| RN-120 | TS-008-10 | AC-008-28 |
| RN-121 | TS-008-16, TS-008-27, TS-008-44 | AC-008-30, AC-008-59, AC-008-61 |
| RN-122 | TS-008-31, TS-008-32 | AC-008-12 |
| RN-123 | TS-008-14 | AC-008-12 |
| RN-124 | TS-008-17 | AC-008-14, AC-008-31 |
| RN-125 | TS-008-18 | AC-008-13 |
| RN-126 | TS-008-43 | AC-008-36, AC-008-58 |
| RN-159 | TS-008-19, TS-008-51 | — |
| RN-231 a RN-234 | TS-008-09, TS-008-37 | AC-008-17, AC-008-35, AC-008-47, AC-008-48 |
| RN-306 | TS-008-11 | AC-008-34 |
| RN-009 / RN-010 | TS-008-07, TS-008-08, TS-008-23, TS-008-25 | AC-008-02, AC-008-45, AC-008-46 |
| RN-003 | TS-008-18 | AC-008-13 |
| RN-004 | TS-008-29 | AC-008-63 |
| RN-001 / RN-002 | TS-008-42, TS-008-43 | AC-008-53, AC-008-56 |
| RN-006 | TS-008-14, TS-008-18 | AC-008-01, AC-008-12 |
| INV-WKL-01 a 04 | TS-008-05, TS-008-12 | AC-008-22 a AC-008-24, AC-008-42 |
| INV-WKL-05 | TS-008-01, TS-008-26 | AC-008-19, AC-008-60 |
| INV-WKL-06 | TS-008-13 | AC-008-10 |
| INV-WKL-07 | TS-008-16 | AC-008-30 |
| INV-WKL-08 | TS-008-22, TS-008-25 | AC-008-09, AC-008-46 |
| INV-WKL-09 | TS-008-43 | AC-008-58 |
| §9 permissions | TS-008-21, TS-008-31 | AC-008-54, AC-008-55 |
| SG-05 / SG-06 / SG-07 / SG-09 / SG-10 | TS-008-43, TS-008-44, TS-008-46 | AC-008-56 a AC-008-59 |
| §28 spec | TS-008-45 | — |
| FM-02 | TS-008-33, TS-008-34 | AC-008-05 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 15. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `worklog-overlap-cases.csv` | Os 9 casos normativos da §6.2 | `TS-008-01` — oráculo da sobreposição |
| `worklog-calculation-cases.csv` | Os 8 casos normativos da §6.3 | `TS-008-05`, `TS-008-33` — **compartilhado entre Jest e JUnit** |
| `worklog-rounding-cases.csv` | Múltiplos 5, 6, 10, 15, 30 com valores acima e abaixo de cada limiar | `TS-008-06` |
| `worklog-timezone-cases.csv` | Bordas de meia-noite, virada de período e horário de verão | `TS-008-08`, `TS-008-22`, `TS-008-23` |
| `fixture-contract-block` | Contrato com `overagePolicy = BLOCK` e saldo de 30 min | `TS-008-09` |
| `fixture-contract-warn` | Contrato com `WARN` | `TS-008-09` |
| `fixture-contract-hourly-open` | Contrato `HOURLY_OPEN` | `TS-008-09` |
| `fixture-period-closed` | Período `CLOSED` com work logs travados | `TS-008-16`, `TS-008-17` |
| `fixture-period-reopened` | Período `REOPENED` | `TS-008-17` |
| `fixture-user-100k-logs` | Usuário com 100.000 work logs | `TS-008-04`, `TS-008-39` |
| `fixture-tenant-500k-logs` | Tenant com 500.000 registros | `TS-008-41` |
| `fixture-member-scope` | 100 registros, 20 do `MEMBER` de teste | `TS-008-21` |
| `fixture-clock-dst` | `Clock` fixo nas transições de horário de verão | `TS-008-23` |
| `fixture-tenant-b` | Segundo tenant com registros espelhados | `TS-008-42` |

**Regras de fixture:**
- `worklog-calculation-cases.csv` é um **único arquivo** consumido por Java e TypeScript. Duplicá-lo permitiria que os dois lados divergissem sem que nenhum teste falhasse — exatamente o defeito que `TS-008-33` existe para impedir.
- `fixture-user-100k-logs` é gerada por `COPY` em massa; construí-la por inserções individuais levaria mais tempo que a suíte inteira.
- Nenhuma fixture usa data relativa ao momento da execução.

---

## 16. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-008-01` e `TS-008-05` foram escritas e **revisadas** antes da implementação (SQ-02) |
| CC-02 | Os 9 casos de sobreposição passam com igualdade exata |
| CC-03 | Os 8 casos de cálculo passam com igualdade exata, em Java e TypeScript, a partir do mesmo arquivo |
| CC-04 | Nenhum caso de arredondamento produz valor maior que o original |
| CC-05 | O truncamento de segundos é comprovado com 0, 29, 30, 31 e 59 segundos |
| CC-06 | `TS-008-26` (100 criações sobrepostas) verde, com no máximo um registro persistido |
| CC-07 | As 4 bordas temporais passam com `Clock` fixo |
| CC-08 | A ordem da §6.1 é comprovada com payloads multi-erro |
| CC-09 | Equivalência entre `create` e `createFromTimer` provada para RN-102 a RN-120 |
| CC-10 | Detecção de sobreposição com p95 < 50 ms sobre 100.000 registros |
| CC-11 | Escopo de `MEMBER` comprovado por inspeção de SQL, inclusive em contagem e totais |
| CC-12 | `/validate` comprovadamente não persiste nada |
| CC-13 | Cobertura ≥ 95% em `OverlapDetector`, `WorkLogCalculator` e `RoundingPolicy` |
| CC-14 | Cobertura ≥ 90% em services e validators |
| CC-15 | Os 9 endpoints passam na suíte de isolamento com `404` |
| CC-16 | Nenhum log contém `description` |
| CC-17 | `TS-005-33` e `TS-006-34`, herdados de `005` e `006`, reexecutados e verdes contra `work_logs` real |
