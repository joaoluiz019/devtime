# 007 — Tickets · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-007-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-311: rejeita conclusão com cronômetro pausado`.

> **Duas suítes escritas antes do código** (`TS-007-01` e `TS-007-06`). Justificativa em `tasks.md` §9: a geração de `number` falha **silenciosamente** sob concorrência, e as 27 transições proibidas da matriz §4.7 são exatamente as que um teste escrito depois não cobriria — porque ele testaria o que o código faz, e o código não faz o que é proibido.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `TicketStateMachine`, `TicketKeyBuilder`, `ActiveTimerGuard`, `ContractMoveGuard`, `TicketDeletionGuard`, `AssigneeValidator` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Criação, transições, totais, movimentação |
| Concorrência | Sequência de `number`, transições, totais | JUnit + `CountDownLatch` | Sem duplicata nem perda |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Todos os endpoints da §14 |
| Isolamento | Tenancy por id **e** por `key` | Suíte dedicada | Todos os endpoints |
| Frontend | Stores, quadro, formulário, Markdown, transições | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Criar → priorizar → trabalhar → concluir → reabrir | Playwright | Jornada completa |
| Performance | Quadro, listagem, linha do tempo, totais | k6 | Metas da §20 |
| Segurança | Isolamento, XSS, campos forjados, escopo de horas | JUnit + scripts | Vetores da §19 |
| Regressão | Matriz de transições e sequência | CI | 100% verde |

---

## 3. Testes unitários

### TS-007-01 — Tabela normativa de chaves (RN-302)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `TicketKeyBuilder` reproduz exatamente a tabela da §6.2 |
| **Pré-condição** | `ticket-key-cases.csv` com as linhas normativas |
| **Passos** | Para cada linha (`contract.code`, `number`), montar a chave e comparar |
| **Resultado esperado** | Igualdade exata: `CT-0001`+`1` → `CT-0001-1`; `CT-0001`+`42` → `CT-0001-42`; `CT-0010`+`137` → `CT-0010-137`. Nenhum zero à esquerda no número |

### TS-007-02 — `startedAt` e `completedAt` (RN-310)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o preenchimento e a limpeza exatos |
| **Passos** | 1. `BACKLOG → IN_PROGRESS`. 2. `→ BLOCKED → IN_PROGRESS`. 3. `→ DONE`. 4. `→ IN_PROGRESS`. 5. `→ DONE` de novo |
| **Resultado esperado** | (1) `startedAt` preenchido; (2) `startedAt` **inalterado** — só a 1ª entrada conta; (3) `completedAt` preenchido; (4) `completedAt` nulo e `startedAt` ainda o original; (5) `completedAt` com o **novo** instante |

### TS-007-03 — `ActiveTimerGuard` (RN-311)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `PAUSED` conta como ativo |
| **Passos** | Tentar `→ DONE` com timer: (a) `RUNNING`; (b) `PAUSED`; (c) `COMPLETED`; (d) `DISCARDED`; (e) `ABANDONED`; (f) nenhum; (g) timer de **outro** ticket |
| **Resultado esperado** | (a) e (b) `DEVTIME-2311` (CE-ME-01); (c), (d), (f) e (g) permitidos; (e) permitido — timer abandonado não gera work log automaticamente e não produziria tempo órfão |

### TS-007-04 — `ContractMoveGuard` (RN-305)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas condições da regra |
| **Passos** | Mover ticket: (a) sem work logs, mesmo cliente; (b) sem work logs, outro cliente; (c) com work logs, mesmo cliente; (d) com work logs, outro cliente; (e) para contrato `ENDED` |
| **Resultado esperado** | (a) permitido; (b), (c) e (d) `DEVTIME-2305`; (e) `DEVTIME-2306`. Em (a), `number` e `key` **inalterados** |

### TS-007-05 — `AssigneeValidator` (RN-304)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a exigência de membership ativo |
| **Passos** | Atribuir a membership: (a) `ACTIVE`; (b) `INVITED`; (c) `SUSPENDED`; (d) `REMOVED`; (e) de outro tenant; (f) nulo |
| **Resultado esperado** | (a) permitido; (b), (c) e (d) `DEVTIME-2304`; (e) `DEVTIME-2002`; (f) permitido — remove o responsável |

### TS-007-06 — Matriz completa de transições (§4.7)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as **49 células** (7×7) da matriz |
| **Pré-condição** | `ticket-transition-matrix.csv` com origem, destino e resultado esperado |
| **Passos** | Para cada célula, colocar o ticket no estado de origem e tentar a transição |
| **Resultado esperado** | 22 células válidas executam com seus efeitos; 20 células proibidas retornam `409 DEVTIME-2010` com `availableTransitions[]` correto; 7 auto-transições retornam `200` sem efeito e **sem** auditoria (ME-03). Nenhuma célula sem teste |

**Células proibidas de verificação obrigatória:**

| Transição | Verificação específica |
|---|---|
| `DONE → CANCELLED` | Rejeitada e ausente de `availableTransitions` |
| `IN_PROGRESS → BACKLOG` | Rejeitada — o trabalho já começou |
| `BLOCKED → TODO`/`IN_REVIEW`/`DONE` | Rejeitadas — o desbloqueio passa por `IN_PROGRESS` |
| `BACKLOG`/`TODO` → `BLOCKED` | Rejeitadas — não se bloqueia o que não começou |
| `CANCELLED → *` exceto `BACKLOG` | Rejeitadas |
| `DONE → BACKLOG`/`TODO`/`BLOCKED` | Rejeitadas |

### TS-007-07 — `availableTransitions` por estado e papel (ME-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a lista reflete estado **e** permissão |
| **Passos** | Para cada estado × cada papel, ler `availableTransitions` |
| **Resultado esperado** | `MEMBER` em ticket alheio recebe lista **vazia**; `MEMBER` em ticket próprio recebe as mesmas transições de `MANAGER`; `VIEWER` sempre recebe lista vazia |

---

## 4. Testes de integração

### TS-007-08 — Ordem de aplicação na criação (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ordem e a não-ocorrência de efeitos colaterais em falha |
| **Passos** | 1. Contrato inexistente. 2. Contrato `ENDED`. 3. Título curto. 4. Responsável inativo. 5. 11 tags. 6. Payload válido |
| **Resultado esperado** | (1) `404`; (2) `DEVTIME-2306`; (3) `DEVTIME-2303`; (4) `DEVTIME-2304`; (5) `DEVTIME-2313`. Em **todos** os casos de falha, **nenhum número de sequência é consumido** — provado comparando o `number` do ticket criado em (6) com o esperado |

### TS-007-09 — Sequência atômica sob concorrência (RN-302, INV-TCK-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar R-01 mitigado |
| **Pré-condição** | Contrato sem tickets; 100 threads sincronizadas por `CountDownLatch` |
| **Passos** | 100 criações simultâneas no mesmo contrato |
| **Resultado esperado** | 100 sucessos; `numbers` exatamente 1 a 100, sem repetição nem lacuna; 100 `keys` distintas; nenhuma violação de índice único. **Este teste falha contra uma implementação `MAX(number) + 1`** — é o seu propósito |

### TS-007-10 — Sequência independente por contrato
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a sequência é por contrato, não por tenant |
| **Passos** | Criar 3 tickets em `CT-0001` e 2 em `CT-0002` |
| **Resultado esperado** | `CT-0001-1/2/3` e `CT-0002-1/2`. O prefixo do contrato garante a unicidade global da chave |

### TS-007-11 — Lacuna de numeração (CX-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o número não é reciclado |
| **Passos** | 1. Criar até `number = 5`. 2. Injetar falha após a aquisição do número 6. 3. Criar com sucesso |
| **Resultado esperado** | O novo ticket recebe `number = 7`; o 6 nunca é reutilizado. A lacuna é o comportamento correto |

### TS-007-12 — Totais por incremento (RN-308, INV-TCK-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia de atualização e a convergência |
| **Passos** | 1. Criar work log de 120 min faturáveis. 2. Criar de 60 min não faturáveis. 3. Editar o 1º para 90 min. 4. Excluir o 2º. 5. Corromper os totais. 6. Executar o `DenormalizationReconcileJob`. 7. Inspecionar o SQL |
| **Resultado esperado** | (1) `spent=120`, `billable=120`; (2) `spent=180`, `billable=120`; (3) `spent=150`, `billable=90`; (4) `spent=90`; (6) valores restaurados por agregação real; (7) `UPDATE ... SET spent_minutes = spent_minutes + ?`, **nunca** leitura seguida de escrita |

### TS-007-13 — Reabertura automática (RN-312)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o efeito cruzado e sua não-reversão |
| **Passos** | 1. Ticket em `DONE`. 2. Criar work log. 3. Conferir status, `completedAt`, auditoria e notificação. 4. Excluir o work log. 5. Conferir novamente |
| **Resultado esperado** | (3) `IN_PROGRESS`, `completedAt` nulo, `AuditLog` com `actorType = SYSTEM` e o `workLogId` disparador, notificação ao responsável; (5) **permanece** `IN_PROGRESS` — a reabertura não é revertida (CX-06) |

### TS-007-14 — Cancelamento preserva horas (RN-314)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhuma hora é devolvida |
| **Pré-condição** | Ticket com 40h registradas em período `OPEN` |
| **Passos** | 1. Cancelar. 2. Conferir work logs. 3. Conferir `consumedMinutes` do período. 4. Conferir o relatório |
| **Resultado esperado** | Work logs intactos; `consumedMinutes` inalterado; as horas continuam no relatório. O trabalho foi realizado, independentemente do desfecho |

### TS-007-15 — Exclusão restrita (RN-307, INV-TCK-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda e o soft delete |
| **Passos** | 1. Excluir sem work logs. 2. Conferir tags desvinculadas. 3. Excluir com work logs. 4. Conferir o banco |
| **Resultado esperado** | (1) `204`, `deletedAt` preenchido; (2) `usageCount` das tags decrementado; (3) `409 DEVTIME-2307`; (4) nenhuma remoção física em nenhum caso |

### TS-007-16 — Chave preservada na movimentação (RN-305, CX-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de OB-01 |
| **Passos** | 1. Mover `CT-0001-42` para `CT-0002` (mesmo cliente, sem work logs). 2. Conferir `number`, `key` e `contractId`. 3. Buscar por `by-key/CT-0001-42` |
| **Resultado esperado** | `contractId` alterado; `number = 42` e `key = CT-0001-42` **inalterados**; a busca por chave continua encontrando o ticket |

### TS-007-17 — Guarda de reativação (CX-15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a dependência do estado do contrato |
| **Passos** | Reativar ticket `CANCELLED` com contrato: (a) `ACTIVE`; (b) `SUSPENDED`; (c) `ENDED`; (d) `CANCELLED` |
| **Resultado esperado** | (a) e (b) permitidos; (c) e (d) `409 DEVTIME-2010` |

### TS-007-18 — Reatribuição na remoção de membro (CX-10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o efeito cruzado com `Membership → REMOVED` |
| **Passos** | 1. Atribuir tickets abertos e concluídos a um membro. 2. Remover o membro. 3. Conferir os tickets |
| **Resultado esperado** | Tickets **abertos** reatribuídos ao `OWNER` (§4.3 SM); `assigneeId` nunca aponta para membership removido; `reporterId` histórico preservado |

### TS-007-19 — Quadro em consulta única (CP-14)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia da §20.1 |
| **Pré-condição** | Tickets nos 7 status |
| **Passos** | Chamar `GET /tickets/board` inspecionando o SQL emitido |
| **Resultado esperado** | **Uma** consulta agrupada, não 7; limite por coluna aplicado; total por coluna correto mesmo com a lista truncada |

### TS-007-20 — Linha do tempo paginada por cursor
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a união ordenada e a paginação |
| **Pré-condição** | Ticket com 200 eventos entre auditoria, comentários e work logs |
| **Passos** | 1. Primeira página. 2. Páginas seguintes por cursor. 3. Inspecionar o SQL |
| **Resultado esperado** | Ordem cronológica decrescente estável; nenhum evento repetido ou omitido entre páginas; **nenhum** `OFFSET` no SQL |

---

## 5. Testes de API

### TS-007-21 — Contrato dos endpoints da §14
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `Location` no `201`; `availableTransitions[]` presente (ME-06); erros em RFC 7807 com `code`; resposta de transição inválida traz `currentStatus`, `requestedStatus` e `availableTransitions` |

### TS-007-22 — Transição por endpoint de ação (ME-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `status` não muda por `PATCH` genérico |
| **Passos** | 1. `PATCH /tickets/{id}` com `{"status": "DONE"}`. 2. `POST /tickets/{id}/transition` |
| **Resultado esperado** | (1) campo ignorado ou rejeitado; status e `completedAt` inalterados; (2) executa com todas as guardas |

### TS-007-23 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel, executar; para `MEMBER`, testar em ticket próprio e alheio |
| **Resultado esperado** | Conforme a §7 de `permissions.md`; `MEMBER` transiciona e atribui **apenas** em tickets onde é relator ou responsável (nota ⁴), recebendo `403 DEVTIME-1101` nos demais; `VIEWER` só lê |

### TS-007-24 — Filtros compostos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a combinação de filtros da §23 |
| **Passos** | Combinar `status[]`, `priority[]`, `assigneeId`, `contractId`, `tagIds[]`, `search` e `isOverEstimate` |
| **Resultado esperado** | Resultados corretos em todas as combinações; paginação consistente; nenhum filtro aplicado em memória |

---

## 6. Testes de frontend

### TS-007-25 — Prévia da chave no formulário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a prévia coincide com a gerada |
| **Passos** | 1. Selecionar contrato e observar a prévia. 2. Salvar. 3. Comparar |
| **Resultado esperado** | A chave exibida antes de salvar coincide com a criada, **salvo** quando outro usuário cria um ticket no intervalo — caso em que a UI exibe a chave real retornada, sem erro |

### TS-007-26 — Quadro com arrastar e soltar
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o quadro respeita a matriz |
| **Passos** | Arrastar entre todas as combinações de colunas |
| **Resultado esperado** | Movimentos válidos aplicam; inválidos são impedidos visualmente **antes** do envio; se enviados, o erro `DEVTIME-2010` é exibido e o cartão volta à coluna original sem estado inconsistente |

### TS-007-27 — Quadro acessível por teclado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 |
| **Passos** | Mover cartões entre colunas usando apenas teclado; verificar leitor de tela |
| **Resultado esperado** | Movimentação completa sem mouse; coluna de destino anunciada; zero violações do axe-core em P17–P20 |

### TS-007-28 — Sanitização de Markdown
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-05 no cliente |
| **Passos** | Renderizar descrições com `<script>`, `<img onerror>`, `javascript:` em link e `<iframe>` |
| **Resultado esperado** | Todos neutralizados; apenas tags da allowlist preservadas; nenhum `innerHTML` cru no código |

### TS-007-29 — Ações refletem permissão e ownership
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `hasPermission` e `isOwnTicket` |
| **Passos** | Renderizar P19 como cada papel, em ticket próprio e alheio |
| **Resultado esperado** | `MEMBER` vê ações de transição só em tickets próprios; `VIEWER` não vê nenhuma; a ausência é confirmada também na resposta da API, não apenas no DOM (IMP-06) |

### TS-007-30 — Selo de estouro de estimativa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-309 na UI |
| **Passos** | Renderizar tickets com: sem estimativa; abaixo; exatamente igual; acima |
| **Resultado esperado** | Sem estimativa: nenhuma barra nem selo; igual: barra completa sem selo; acima: selo de estouro; **nenhum caso bloqueia** qualquer ação |

---

## 7. Testes E2E

### TS-007-31 — Jornada completa do ticket
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário de ponta a ponta |
| **Passos** | 1. Criar em P20. 2. Conferir a chave em P19. 3. Priorizar e iniciar em P18. 4. Bloquear e desbloquear. 5. Registrar horas. 6. Concluir. 7. Registrar hora nova e ver a reabertura. 8. Cancelar outro ticket com horas |
| **Resultado esperado** | Cada etapa reflete o estado correto; a reabertura em (7) aparece na linha do tempo com explicação; (8) preserva as horas |

### TS-007-32 — Filtros preservados na URL
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a persistência de estado em P17 |
| **Passos** | Aplicar filtros compostos, paginar, copiar a URL, abrir em outra aba |
| **Resultado esperado** | Estado idêntico; nenhum filtro perdido no recarregamento |

---

## 8. Testes de performance

### TS-007-33 — Quadro com volume
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta da §20 |
| **Pré-condição** | 50.000 tickets distribuídos nos 7 status |
| **Passos** | 500 chamadas ao quadro, medindo p95 |
| **Resultado esperado** | p95 < 400 ms; uma consulta agrupada; limite por coluna respeitado; memória constante |

### TS-007-34 — Listagem com busca
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta de listagem |
| **Pré-condição** | 50.000 tickets |
| **Passos** | Listagem com filtros e busca textual, medindo p95 |
| **Resultado esperado** | p95 < 400 ms; `idx_tickets_search` utilizado; projeção sem `description`; nenhuma consulta N+1 ao carregar tags e responsável |

### TS-007-35 — Atualização de totais no caminho quente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que RN-308 não pesa na criação de work log |
| **Passos** | 10.000 aplicações de `applyWorkLogDelta`, medindo p95 |
| **Resultado esperado** | p95 < 30 ms; custo constante, independente do número de work logs do ticket — o que só é verdade com incremento, não com reagregação |

### TS-007-36 — Linha do tempo extensa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a paginação por cursor |
| **Pré-condição** | Ticket com 1.000 eventos |
| **Passos** | Percorrer todas as páginas, medindo o tempo de cada uma |
| **Resultado esperado** | Tempo **constante** por página; a última é tão rápida quanto a primeira — o que `OFFSET` não garante |

---

## 9. Testes de segurança

### TS-007-37 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 |
| **Passos** | Para cada endpoint da §14, acessar recurso do tenant B autenticado no tenant A, por **id** e por **chave** |
| **Resultado esperado** | `404 DEVTIME-2002` em todos; a busca por chave de outro tenant é indistinguível de chave inexistente (SG-02) |

### TS-007-38 — Escopo de horas de `MEMBER` (SG-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-02 na linha do tempo |
| **Pré-condição** | Ticket com work logs de 3 membros |
| **Passos** | Consultar `/activity` como `MEMBER` autor de um deles, inspecionando o SQL |
| **Resultado esperado** | Apenas o próprio work log retornado; filtro presente na **cláusula WHERE**, não em memória; `spentMinutes` total do ticket permanece visível; nenhum vazamento por contagem de eventos |

### TS-007-39 — Campos forjados (SG-07, SG-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que campos de sistema são inalteráveis |
| **Passos** | Enviar `reporterId`, `number`, `key`, `spentMinutes`, `billableMinutes` e `status` em `POST` e `PATCH` |
| **Resultado esperado** | Todos ignorados ou rejeitados; `reporterId` sempre do token; totais só mudam por work log |

### TS-007-40 — XSS em Markdown e em PDF (SG-05, SG-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape em todas as saídas |
| **Passos** | Criar ticket com payloads em `title` e `description`; renderizar em P17, P19, exportação CSV e PDF de `012` |
| **Resultado esperado** | Texto literal em todas as saídas; CSV sem fórmula injetável; PDF sem marcação interpretável |

### TS-007-41 — Ausência de texto livre em log (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-17 |
| **Passos** | Executar o fluxo completo capturando os logs |
| **Resultado esperado** | Nenhum log contém `title`, `description` ou `blockReason`; a `key` está presente e é suficiente para investigação |

---

## 10. Testes de concorrência

### TS-007-42 — Transições simultâneas (ME-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a atomicidade da transição |
| **Passos** | Duas requisições simultâneas transicionando o mesmo ticket para destinos distintos |
| **Resultado esperado** | Exatamente uma aplicada; a outra `409` por versão ou por transição inválida a partir do novo estado; nunca um estado intermediário |

### TS-007-43 — Conclusão e início de timer simultâneos (RN-311)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a guarda não tem janela de corrida |
| **Passos** | Uma requisição conclui o ticket enquanto outra inicia um timer nele |
| **Resultado esperado** | Uma das duas falha; **nunca** existe ticket em `DONE` com timer ativo apontando para ele |

### TS-007-44 — Work logs simultâneos e totais (RN-308)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o incremento no banco não perde atualizações |
| **Passos** | 20 work logs de 30 min criados simultaneamente no mesmo ticket |
| **Resultado esperado** | `spentMinutes = 600`; nenhuma atualização perdida, porque o incremento ocorre no `UPDATE`; havendo divergência, o reconciliador restaura |

---

## 11. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-007-45 | Matriz de transições (`TS-007-06`) | Toda alteração em `TicketStateMachine` ou em `state-machines.md` §4.7 |
| TS-007-46 | Sequência sob concorrência (`TS-007-09`) | Toda alteração em `TicketNumberGenerator` ou na migration da sequência |
| TS-007-47 | Totais (`TS-007-12`, `TS-007-44`) | Toda alteração em `008-worklogs` que toque criação, edição ou exclusão |
| TS-007-48 | Reabertura (`TS-007-13`) | Toda alteração em RN-312 ou nos eventos de work log |
| TS-007-49 | Escopo de horas de `MEMBER` (`TS-007-38`) | Toda alteração na linha do tempo ou em `permissions.md` §9 |
| TS-007-50 | XSS (`TS-007-40`) | Toda alteração em renderização de Markdown ou no gerador de PDF de `012` |
| TS-007-51 | Isolamento (`TS-007-37`) | Todo endpoint novo |

**Política:** `TS-007-06` e `TS-007-09` rodam integralmente em todo PR que toque esta feature. São os dois testes cuja ausência produziria defeitos irreversíveis — uma transição proibida executada corrompe o histórico, e uma chave duplicada é comunicada ao cliente antes de ser notada.

---

## 12. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-301 | TS-007-08 | AC-007-01, AC-007-19 |
| RN-302 | TS-007-01, TS-007-09, TS-007-10, TS-007-11 | AC-007-01, AC-007-02, AC-007-40, AC-007-50 |
| RN-303 | TS-007-08 | AC-007-21, AC-007-33 |
| RN-304 | TS-007-05, TS-007-18 | AC-007-03, AC-007-22, AC-007-41 |
| RN-305 | TS-007-04, TS-007-16 | AC-007-12, AC-007-27, AC-007-28 |
| RN-306 | TS-007-08 | AC-007-20 |
| RN-307 | TS-007-15 | AC-007-29 |
| RN-308 | TS-007-12, TS-007-35, TS-007-44 | AC-007-13, AC-007-53 |
| RN-309 | TS-007-30 | AC-007-34, AC-007-35 |
| RN-310 | TS-007-02 | AC-007-04, AC-007-05, AC-007-08, AC-007-09 |
| RN-311 | TS-007-03, TS-007-43 | AC-007-25, AC-007-36, AC-007-52 |
| RN-312 | TS-007-13 | AC-007-14, AC-007-37 |
| RN-313 | TS-007-08 | AC-007-17, AC-007-30 |
| RN-314 | TS-007-14 | AC-007-10 |
| RN-815 | TS-007-20 | AC-007-06, AC-007-10, AC-007-12 |
| RN-003 | TS-007-15 | AC-007-18, AC-007-42 |
| RN-004 | TS-007-42 | AC-007-51 |
| RN-011 | TS-007-39 | AC-007-31 |
| RN-012 | TS-007-24 | — |
| RN-001 / RN-002 | TS-007-37, TS-007-39 | AC-007-44, AC-007-45, AC-007-47 |
| RN-006 | TS-007-13 | AC-007-01, AC-007-14 |
| INV-TCK-01 | TS-007-09, TS-007-10 | AC-007-02, AC-007-50 |
| INV-TCK-02 | TS-007-04 | AC-007-27 |
| INV-TCK-03 | TS-007-15 | AC-007-29 |
| INV-TCK-04 | TS-007-02 | AC-007-08, AC-007-09 |
| INV-TCK-05 | TS-007-12 | AC-007-13, AC-007-48 |
| §4.7 SM | TS-007-06, TS-007-07, TS-007-17, TS-007-26 | AC-007-04 a AC-007-11, AC-007-23, AC-007-24 |
| ME-03 / ME-04 / ME-05 / ME-06 | TS-007-06, TS-007-07, TS-007-22 | AC-007-23, AC-007-24, AC-007-38 |
| nota ⁴ permissions | TS-007-07, TS-007-23, TS-007-29 | AC-007-32 |
| SG-04 | TS-007-38 | AC-007-46 |
| SG-05 / SG-06 | TS-007-28, TS-007-40 | AC-007-49 |
| SG-07 / SG-08 | TS-007-39 | AC-007-47, AC-007-48 |
| §7 tickets.md | TS-007-19, TS-007-33 | AC-007-16 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha. Toda célula da matriz §4.7 é coberta por `TS-007-06`.

---

## 13. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `ticket-key-cases.csv` | Tabela normativa de chaves da §6.2 | `TS-007-01` |
| `ticket-transition-matrix.csv` | As 49 células com origem, destino e resultado esperado | `TS-007-06` — oráculo da máquina de estados |
| `ticket-transition-permissions.csv` | Estado × papel × `availableTransitions` esperadas | `TS-007-07` |
| `fixture-contract-active` | Contrato `ACTIVE` com `code = CT-0001` | Base da maioria dos testes |
| `fixture-contract-same-client` | Segundo contrato `CT-0002` do **mesmo** cliente | `TS-007-04`, `TS-007-16` |
| `fixture-contract-other-client` | Contrato de **outro** cliente | `TS-007-04` |
| `fixture-ticket-with-worklogs` | Ticket com 3 work logs de membros distintos | `TS-007-15`, `TS-007-38` |
| `fixture-ticket-done` | Ticket em `DONE` com `completedAt` preenchido | `TS-007-13` |
| `fixture-tenant-50k-tickets` | 50.000 tickets distribuídos nos 7 status | `TS-007-33`, `TS-007-34` |
| `fixture-ticket-1k-events` | Ticket com 1.000 eventos na linha do tempo | `TS-007-36` |
| `fixture-timers-all-states` | Timers em `RUNNING`, `PAUSED`, `COMPLETED`, `DISCARDED`, `ABANDONED` | `TS-007-03` |
| `fixture-xss-payloads` | Payloads de XSS para título, descrição e CSV | `TS-007-28`, `TS-007-40` |
| `fixture-tenant-b` | Segundo tenant com tickets de chaves idênticas | `TS-007-37` |

**Regra de fixture:** `fixture-tenant-b` contém tickets com as **mesmas chaves** do tenant A (`CT-0001-1`, etc.). É o que torna `TS-007-37` significativo: se a busca por chave não filtrar por tenant, ela retornará o ticket errado em vez de `404`.

---

## 14. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-007-01` e `TS-007-06` foram escritas e revisadas **antes** da implementação |
| CC-02 | A tabela normativa de chaves passa integralmente |
| CC-03 | As 49 células da matriz possuem teste de aceitação ou rejeição |
| CC-04 | 100 criações simultâneas produzem 100 números distintos e consecutivos |
| CC-05 | RN-310 verificada em sequência completa de entradas e saídas |
| CC-06 | `PAUSED` comprovadamente bloqueia a conclusão |
| CC-07 | Incremento de totais comprovado por inspeção de SQL |
| CC-08 | Quadro comprovadamente em **uma** consulta |
| CC-09 | Linha do tempo com tempo constante por página |
| CC-10 | Escopo de horas de `MEMBER` comprovado por inspeção de SQL |
| CC-11 | Cobertura ≥ 90% em `TicketStateMachine`, services e validators |
| CC-12 | Todos os endpoints passam na suíte de isolamento, por id **e** por chave |
| CC-13 | Nenhum log contém texto livre |
| CC-14 | Zero violações do axe-core em P17–P20, com quadro navegável por teclado |
