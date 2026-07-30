# 011 — Bank Hours · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-011-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-228: não transporta saldo negativo`.

> **SQ-02 — Ordem inegociável.** Complexidade **Crítica**. Três suítes são escritas, revisadas e **aprovadas** antes do código correspondente:
>
> | Suíte | Precede |
> |---|---|
> | `TS-011-01` (fórmulas canônicas) | `BalanceCalculator` |
> | `TS-011-04` (carry-over) | `RolloverCalculator` |
> | `TS-011-14` (atomicidade dos 7 passos) | `PeriodClosingService` |
>
> **SQ-03:** duas aprovações obrigatórias no PR.
>
> **SQ-10:** qualquer divergência de saldo reportada em produção **bloqueia toda a fila** de desenvolvimento até a causa raiz ser corrigida. Estes testes são a principal defesa contra esse cenário.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `BalanceCalculator`, `RolloverCalculator`, `AdjustmentValidator`, `ClosingGuard`, `ReopeningGuard`, `SnapshotBuilder` | JUnit 5 + AssertJ + `@ParameterizedTest` | **≥ 95%** |
| **Atomicidade** | Falha em cada um dos 7 passos de RN-241 | JUnit + injeção de falha | 7 pontos |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Fechamento, reabertura, ajustes |
| **Concorrência** | Fechamento duplo, work log durante fechamento, ajustes | JUnit + `CountDownLatch` | Sem violação de invariante |
| Determinismo | Checksum do snapshot | JUnit | 100% reprodutível |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Os 8 endpoints |
| Isolamento | Tenancy + ausência de rotas de escrita | Suíte dedicada + inspeção de rotas | Todos os endpoints |
| Frontend | Stores, prévia de ajuste, selo de parcial | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Ciclo de apuração e fechamento | Playwright | Jornada completa |
| Performance | Fechamento, extrato, cálculo | k6 | Metas da §20 |
| Segurança | Imutabilidade, checksum, permissões | JUnit + scripts | Vetores da §19 |
| Regressão | Fórmulas, carry-over, atomicidade | CI | 100% verde |

---

## 3. Testes de cálculo

### TS-011-01 — Fórmulas canônicas (RN-218 a RN-222)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `BalanceCalculator` reproduz **exatamente** o exemplo normativo da §6.1 |
| **Pré-condição** | `balance-formula-cases.csv` com o exemplo normativo e variações |
| **Passos** | Para cada linha, calcular `available`, `remaining`, `overage` e `rate` |
| **Resultado esperado** | Exemplo normativo: `available = 2760`, `remaining = −140`, `overage = 140`, `rate = 105,07`. Igualdade **exata** em todos os campos |

### TS-011-02 — Ramos de `consumptionRate` com `available = 0` (RN-222)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não existe divisão por zero |
| **Passos** | (a) `available = 0`, `consumed = 0`; (b) `available = 0`, `consumed = 300`; (c) `available = 100`, `consumed = 0` |
| **Resultado esperado** | (a) `rate = 0`; (b) `rate = 100`; (c) `rate = 0`. **Nenhuma** exceção aritmética em nenhum caso |

### TS-011-03 — Aritmética inteira e reprodutibilidade (CP-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum cálculo usa ponto flutuante binário |
| **Passos** | 1. Inspecionar os tipos de `BalanceCalculator`. 2. Calcular 10.000 combinações e repetir a execução. 3. Verificar `consumptionRate` com valores que produziriam dízima em `double` |
| **Resultado esperado** | Nenhum `double` ou `float` no caminho; resultados **bit a bit idênticos** entre execuções; `105,07` exibido como `105,07`, nunca `105.06999999` |

### TS-011-04 — Tabela normativa de carry-over (RN-224 a RN-228)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as três políticas nas 6 linhas da §6.2 |
| **Pré-condição** | `rollover-cases.csv` com as 6 linhas normativas |
| **Passos** | Para cada linha, calcular `carriedOut` |
| **Resultado esperado** | `NONE` → 0; `FULL` → 600; `CAPPED` com teto 300 → 300; `CAPPED` abaixo do teto → 150; `FULL` com negativo → **0**; consumo exato → 0. Igualdade exata nas 6 |

### TS-011-05 — Saldo negativo nunca transporta (RN-228)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a regra em todas as políticas |
| **Passos** | Com `remaining = −500`, calcular `carriedOut` sob `NONE`, `FULL` e `CAPPED` |
| **Resultado esperado** | `0` nas três; **nunca** um valor negativo. Transportar dívida é a falha que tornaria o saldo incompreensível ao cliente |

### TS-011-06 — Bordas do carry-over
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os casos limite |
| **Passos** | (a) `remaining = 0`; (b) `CAPPED` com teto 0; (c) `CAPPED` com teto maior que o restante; (d) `HOURLY_OPEN` |
| **Resultado esperado** | (a) `carriedOut = 0` em todas as políticas; (b) equivale a `NONE` (CX-05); (c) transporta o restante integral; (d) carry-over não se aplica (CX-03) |

### TS-011-07 — Horas não faturáveis fora do saldo (RN-223)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a separação entre consumo e registro |
| **Passos** | Registrar work logs faturáveis e não faturáveis; consultar o saldo |
| **Resultado esperado** | `consumedMinutes` conta apenas faturáveis; `nonBillableMinutes` conta os demais; `remaining` não é afetado pelos não faturáveis |

---

## 4. Testes de ajuste

### TS-011-08 — Validação de ajuste (RN-215, RN-237)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas regras de validação |
| **Passos** | Justificativa de 9, 10 e 11 caracteres; `minutes = 0`; ajuste deixando `available` em −1, 0 e +1 |
| **Resultado esperado** | 9 caracteres → `DEVTIME-2215`; 10 e 11 aceitos; `minutes = 0` → `400`; `available = −1` → `DEVTIME-2237`; `available = 0` **aceito** (CX-08); `+1` aceito |

### TS-011-09 — Imutabilidade de ajuste (RN-236, INV-ADJ-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a implementação por **ausência** (OB-03) |
| **Passos** | 1. Inspecionar todas as rotas expostas. 2. Inspecionar `PeriodAdjustmentRepository`. 3. Tentar `PATCH` e `DELETE` no ajuste. 4. Aplicar um estorno |
| **Resultado esperado** | (1) nenhuma rota de escrita além de `POST`; (2) nenhum método de atualização ou exclusão; (3) `404` ou `405`; (4) estorno criado, original **intacto** e ambos visíveis no extrato |

### TS-011-10 — Ajuste por estado do período (RN-235)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda de estado |
| **Passos** | Aplicar ajuste em período `SCHEDULED`, `OPEN`, `CLOSING`, `CLOSED` e `REOPENED` |
| **Resultado esperado** | `OPEN` e `REOPENED` aceitos; os demais `409 DEVTIME-2235` |

### TS-011-11 — Extrato soma ao saldo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a coerência do extrato — a garantia de que o cliente consegue conferir |
| **Pré-condição** | Período com contratado, transportado, 3 ajustes e 50 work logs |
| **Passos** | 1. Percorrer todas as páginas do extrato. 2. Somar créditos e débitos. 3. Comparar com o saldo exibido |
| **Resultado esperado** | A soma dos lançamentos é **exatamente** igual ao `remainingMinutes`; nenhum lançamento repetido ou omitido entre páginas; ordem cronológica estável |

---

## 5. Testes de fechamento

### TS-011-12 — Guardas de fechamento (RN-239, RN-240)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas guardas |
| **Passos** | (a) antes do `endDate` sem confirmação; (b) antes do `endDate` com confirmação; (c) após o `endDate`; (d) com timer `RUNNING`; (e) com timer `PAUSED`; (f) com timer `COMPLETED` |
| **Resultado esperado** | (a) `DEVTIME-2239`; (b) e (c) aceitos; (d) e (e) `DEVTIME-2240` com a lista de timers (CE-ME-01); (f) aceito |

### TS-011-13 — Reconciliação no passo 1 (OB-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o fechamento não congela uma divergência |
| **Pré-condição** | `consumedMinutes` persistido em 2.000; soma real dos work logs em 2.150 |
| **Passos** | 1. Fechar. 2. Verificar o snapshot. 3. Verificar a resposta. 4. Verificar auditoria e métrica |
| **Resultado esperado** | Snapshot com **2.150**, o valor real; resposta com `reconciliationDelta = 150`; `AuditLog` `PERIOD_CONSUMPTION_RECONCILED`; métrica `period.reconciliation.delta` incrementada |

### TS-011-14 — Atomicidade dos 7 passos (RN-241)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o fechamento é tudo ou nada |
| **Pré-condição** | Ponto de injeção de falha em cada um dos 7 passos |
| **Passos** | Para cada passo de 1 a 7: injetar falha, executar o fechamento, verificar o estado completo |
| **Resultado esperado** | Em **todos** os 7 casos: status volta a `OPEN`; **nenhum** work log com `lockedAt`; **nenhum** snapshot criado; `carriedIn` do período seguinte inalterado; nenhuma notificação criada. Rollback total, sem estado intermediário |

> Esta é a suíte que impede o pior cenário da feature: um período com work logs travados e sem snapshot, impossível de fechar e impossível de editar.

### TS-011-15 — Propagação de `carriedIn` (RN-229)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a transferência entre períodos |
| **Passos** | (a) fechar com período seguinte existente; (b) fechar sendo o último período; (c) fechar com `carriedOut = 0` |
| **Resultado esperado** | (a) o seguinte recebe `carriedIn` e recalcula `available`; (b) o período seguinte é **criado** (CX-12); (c) o seguinte recebe 0, sem erro |

### TS-011-16 — Travamento de work logs (RN-121, passo 3)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o efeito cruzado com `008` |
| **Passos** | 1. Fechar período com 500 work logs. 2. Verificar `lockedAt`. 3. Tentar editar e excluir. 4. Inspecionar o SQL |
| **Resultado esperado** | Todos com `lockedAt`; edição e exclusão retornam `DEVTIME-2121`; um `UPDATE` em lote, sem carregar entidades |

### TS-011-17 — Fechamento com excedente (RN-245)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o registro do excedente |
| **Passos** | Fechar período com `consumed > available` |
| **Resultado esperado** | Fechamento executado; `overageMinutes` registrado no snapshot; `carriedOut = 0` (RN-228); relatório exibe o excedente |

### TS-011-18 — Fechamento sem work logs (CX-11)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o caso vazio |
| **Passos** | Fechar período sem nenhum registro |
| **Resultado esperado** | `200`; snapshot com lista vazia; `consumed = 0`; `carriedOut` conforme a política; nenhuma exceção |

---

## 6. Testes de snapshot

### TS-011-19 — Determinismo do checksum (RN-708, R-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o payload é canônico |
| **Passos** | 1. Gerar o payload duas vezes para o mesmo período. 2. Gerar com ordem de carregamento das entidades alterada. 3. Gerar em JVMs distintas. 4. Comparar os checksums |
| **Resultado esperado** | Checksum **idêntico** nos quatro casos. Chaves ordenadas, datas em formato fixo, sem espaços variáveis. Sem isso, RN-708 (PDF determinístico) é inverificável |

### TS-011-20 — Imutabilidade do snapshot (INV-SNP-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a implementação por ausência |
| **Passos** | 1. Inspecionar rotas e repositório. 2. Reabrir o período. 3. Verificar o snapshot. 4. Refechar. 5. Verificar ambos |
| **Resultado esperado** | Nenhuma rota nem método de escrita além de `INSERT`; (3) snapshot **preservado** na reabertura; (5) **dois** snapshots com `snapshotAt` distintos, ambos íntegros (CX-18) |

### TS-011-21 — Constraint permite refechamento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `uq_snapshots_period_at` é **composto** |
| **Passos** | Fechar, reabrir e refechar o mesmo período |
| **Resultado esperado** | Segundo snapshot inserido sem violação. **Este teste falha contra um único simples por `contract_period_id`** — é o seu propósito |

### TS-011-22 — Verificação de integridade (SG-05, CX-21)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar detecção sem correção |
| **Passos** | 1. Adulterar o payload diretamente no banco. 2. Executar `SnapshotIntegrityJob`. 3. Verificar o snapshot após o job |
| **Resultado esperado** | Divergência detectada; log `ERROR`; alerta disparado; métrica incrementada; snapshot **não** corrigido nem regenerado (CP-17) |

### TS-011-23 — Relatório servido do snapshot (RN-701)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a imutabilidade percebida |
| **Passos** | 1. Fechar período. 2. Alterar dados no banco (nome de categoria, descrição de work log). 3. Consultar o relatório do período |
| **Resultado esperado** | O relatório reflete o **snapshot**, não o estado atual; alterações posteriores não aparecem; o resultado é marcado como definitivo |

---

## 7. Testes de reabertura

### TS-011-24 — Reabertura básica (RN-242, RN-243)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar guardas e efeitos |
| **Passos** | (a) sem justificativa; (b) com justificativa de 9 caracteres; (c) válida; (d) como `MANAGER` |
| **Resultado esperado** | (a) e (b) `422`; (c) status `REOPENED`, `reopenCount++`, `lockedAt` limpo, snapshot preservado, justificativa auditada; (d) `403 DEVTIME-1101` |

### TS-011-25 — Ordem inversa obrigatória (RN-244, CE-ME-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda de período posterior |
| **Pré-condição** | Três períodos consecutivos, todos `CLOSED` |
| **Passos** | 1. Reabrir o primeiro. 2. Reabrir o terceiro. 3. Refechar o terceiro. 4. Reabrir o segundo. 5. Verificar `carriedIn` em cada etapa |
| **Resultado esperado** | (1) `409 DEVTIME-2244` indicando qual reabrir primeiro; (2) e (4) aceitos; a cada refechamento o `carriedIn` seguinte é **recalculado**; a guarda é verificada a cada reabertura, não apenas na primeira (SG-08) |

### TS-011-26 — Destravamento em lote
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CX-17 |
| **Passos** | Reabrir período com 10.000 work logs, inspecionando o SQL |
| **Resultado esperado** | Um `UPDATE` em lote; nenhuma entidade carregada; conclusão em menos de 3 s |

---

## 8. Testes de concorrência

### TS-011-27 — Dois fechamentos simultâneos (CE-ME-08, OB-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o lock pessimista |
| **Passos** | Duas requisições de fechamento em paralelo no mesmo período |
| **Resultado esperado** | Uma `200`; outra `409`; **exatamente um** snapshot; work logs travados uma vez; `carriedIn` propagado uma vez. A segunda execução **não começa** — o lock a bloqueia antes do passo 1 |

### TS-011-28 — Work log durante o fechamento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não há janela |
| **Passos** | Criar work log enquanto o período é fechado |
| **Resultado esperado** | Ou o work log entra na reconciliação, ou falha com `DEVTIME-2121`; **nunca** um work log sem `lockedAt` em período `CLOSED` |

### TS-011-29 — Ajustes simultâneos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhuma atualização é perdida |
| **Passos** | 5 ajustes de +60 min aplicados simultaneamente |
| **Resultado esperado** | Os 5 persistidos; `adjustmentMinutes = 300`; `available` reflete os cinco |

---

## 9. Testes de API

### TS-011-30 — Contrato dos 8 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `isPartial` verdadeiro em `OPEN`/`REOPENED` (RN-702); resposta de fechamento com `reconciliationDelta` e `snapshotChecksum`; erros em RFC 7807 |

### TS-011-31 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel |
| **Resultado esperado** | `PERIOD_VIEW`: todos, com `MEMBER` restrito aos contratos vinculados; `PERIOD_ADJUST`, `PERIOD_CLOSE` e `PERIOD_REOPEN`: **apenas** `OWNER` e `ADMIN` — `MANAGER` recebe `403` (RN-238) |

### TS-011-32 — Campos derivados forjados (SG-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o saldo não é manipulável |
| **Passos** | Enviar `consumedMinutes`, `availableMinutes`, `carriedInMinutes`, `carriedOutMinutes`, `adjustmentMinutes` e `status` em todos os endpoints |
| **Resultado esperado** | Todos ignorados; valores sempre calculados |

### TS-011-33 — Valores monetários por permissão (SG-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `CONTRACT_VIEW_FINANCIAL` |
| **Passos** | Consultar período e extrato como `MEMBER` e como `VIEWER` |
| **Resultado esperado** | `MEMBER` não recebe `estimatedValue` nem taxas; `VIEWER` recebe. A ausência é verificada na **resposta da API**, não apenas no DOM |

---

## 10. Testes de frontend

### TS-011-34 — Prévia do saldo no ajuste
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a mitigação de R-08 |
| **Passos** | Digitar valores de ajuste positivos e negativos no diálogo |
| **Resultado esperado** | O saldo resultante é exibido antes de aplicar; ajuste que deixaria negativo é sinalizado **antes** do envio; a prévia coincide com o resultado real |

### TS-011-35 — Selo de parcial (RN-702)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-14 |
| **Passos** | Renderizar períodos `SCHEDULED`, `OPEN`, `CLOSED` e `REOPENED` |
| **Resultado esperado** | `OPEN` e `REOPENED` exibem o selo "parcial"; `REOPENED` exibe também o `reopenCount`; `CLOSED` exibe "definitivo"; nenhum período aberto aparece sem marcação |

### TS-011-36 — Diálogo de fechamento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a informação antes da ação irreversível |
| **Passos** | Abrir o diálogo em período elegível e em período com timer ativo |
| **Resultado esperado** | Exibe o consumo reconciliado, o `carriedOut` previsto e a contagem de work logs a travar; com timer ativo, lista os timers e desabilita a confirmação |

### TS-011-37 — Diálogo de reabertura
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o alerta sobre o relatório emitido |
| **Passos** | Abrir o diálogo em período fechado |
| **Resultado esperado** | Justificativa obrigatória com mínimo de 10 caracteres validado no cliente; aviso explícito de que o relatório já pode ter sido entregue ao cliente |

---

## 11. Testes E2E

### TS-011-38 — Ciclo de apuração e fechamento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a jornada completa |
| **Passos** | 1. Registrar horas ao longo do período. 2. Acompanhar o saldo em P16. 3. Aplicar um ajuste. 4. Conferir o extrato. 5. Fechar. 6. Tentar editar um work log. 7. Reabrir. 8. Corrigir. 9. Refechar |
| **Resultado esperado** | Cada etapa reflete o estado correto; (6) bloqueado; (9) segundo snapshot; o saldo final é conferível pelo extrato em todas as etapas |

---

## 12. Testes de performance

### TS-011-39 — Fechamento com volume
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta da §20 |
| **Pré-condição** | Período com 10.000 work logs |
| **Passos** | Executar o fechamento medindo cada passo |
| **Resultado esperado** | Total p95 < 15 s; reconciliação < 3 s; travamento < 2 s; geração do snapshot < 5 s |

### TS-011-40 — Cálculo de saldo sob carga
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o saldo é constante no volume |
| **Pré-condição** | Períodos com 100, 10.000 e 100.000 work logs |
| **Passos** | Medir p95 do cálculo de saldo em cada caso |
| **Resultado esperado** | p95 < 200 ms nos três; tempo **independente** do volume — o que só é verdade servindo do desnormalizado |

### TS-011-41 — Extrato paginado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a paginação por cursor |
| **Pré-condição** | Período com 5.000 registros |
| **Passos** | Percorrer todas as páginas medindo cada uma |
| **Resultado esperado** | p95 < 600 ms; tempo **constante** por página; nenhum `OFFSET` no SQL |

---

## 13. Testes de segurança

### TS-011-42 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-024 |
| **Passos** | Para cada um dos 8 endpoints, acessar período do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em todos, nunca `403` |

### TS-011-43 — Ausência de caminhos de escrita (OB-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-03 e SG-04 |
| **Passos** | 1. Enumerar todas as rotas da aplicação. 2. Inspecionar `PeriodAdjustmentRepository` e `PeriodSnapshotRepository`. 3. Buscar por `save`, `update` e `delete` nesses agregados |
| **Resultado esperado** | Nenhuma rota de `PATCH`/`PUT`/`DELETE` para ajuste ou snapshot; nenhum método de atualização nos repositórios; a imutabilidade é estrutural, não verificada em runtime |

### TS-011-44 — Ausência de `justification` em log (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-20 |
| **Passos** | Aplicar ajustes e reabrir períodos capturando os logs |
| **Resultado esperado** | Nenhum log contém `justification` nem `reason` de reabertura; presentes apenas ids, minutos, `reason` enumerado e traceId |

---

## 14. Testes de jobs

### TS-011-45 — `StuckClosingJob` (CE-ME-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a recuperação automática |
| **Pré-condição** | `Clock` fixo; período em `CLOSING` há 9 e 11 minutos |
| **Passos** | Executar o job em ambos os casos |
| **Resultado esperado** | 9 min: nenhuma ação; 11 min: volta a `OPEN`, alerta disparado, `AuditLog` com `actorType = SYSTEM` e o tempo preso |

### TS-011-46 — `RolloverExpiryJob` (RN-230, CX-19, CX-20)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a expiração |
| **Passos** | (a) `rolloverExpiryPeriods = 1` com prazo vencido em período aberto; (b) idem em período fechado; (c) `rolloverExpiryPeriods = 0` |
| **Resultado esperado** | (a) ajuste automático com `reason = OTHER` e justificativa padrão; (b) **adiado** para o próximo período aberto (CX-19); (c) **nunca** expira |

### TS-011-47 — Idempotência dos jobs
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a segurança de reexecução |
| **Passos** | Executar cada um dos 4 jobs duas vezes seguidas e em duas instâncias simultâneas |
| **Resultado esperado** | Nenhum efeito duplicado; `@SchedulerLock` impede a segunda instância; ajustes de expiração protegidos por `dedupeKey` |

---

## 15. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-011-48 | Fórmulas (`TS-011-01`, `TS-011-02`, `TS-011-03`) | **Toda** alteração em `BalanceCalculator` ou em RN-218 a RN-223 |
| TS-011-49 | Carry-over (`TS-011-04` a `TS-011-06`) | Toda alteração em `RolloverCalculator` ou nas políticas do contrato |
| TS-011-50 | Atomicidade (`TS-011-14`) | Toda alteração em `PeriodClosingService` ou em qualquer um dos 7 passos |
| TS-011-51 | Determinismo do checksum (`TS-011-19`) | Toda alteração em `SnapshotPayloadMapper`, nas entidades serializadas ou na biblioteca de JSON |
| TS-011-52 | Imutabilidade (`TS-011-09`, `TS-011-20`, `TS-011-43`) | Toda alteração em rotas ou repositórios de ajuste e snapshot |
| TS-011-53 | Reabertura em cascata (`TS-011-25`) | Toda alteração em `ReopeningGuard` ou na propagação de `carriedIn` |
| TS-011-54 | Isolamento (`TS-011-42`) | Todo endpoint novo |

**Política:** `TS-011-01`, `TS-011-04` e `TS-011-14` rodam integralmente em todo PR que toque esta feature. São os três testes cuja falha silenciosa produziria um saldo errado — o cenário que SQ-10 trata como bloqueio total da fila.

**Regra adicional:** `TS-011-51` roda também quando a versão da biblioteca de serialização JSON muda. Uma alteração na ordem de chaves padrão quebraria o determinismo do checksum sem que nenhum código do projeto tivesse sido tocado.

---

## 16. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-218 a RN-222 | TS-011-01, TS-011-02, TS-011-03 | AC-011-01, AC-011-27, AC-011-28 |
| RN-223 | TS-011-07 | AC-011-02 |
| RN-224 a RN-227 | TS-011-04, TS-011-06 | AC-011-07 a AC-011-10, AC-011-31 |
| RN-228 | TS-011-05 | AC-011-30 |
| RN-229 | TS-011-15 | AC-011-12, AC-011-36 |
| RN-230 | TS-011-46 | AC-011-41, AC-011-42 |
| RN-215 | TS-011-08 | AC-011-04, AC-011-17, AC-011-33 |
| RN-235 | TS-011-10 | AC-011-19 |
| RN-236 | TS-011-09, TS-011-43 | AC-011-06, AC-011-20, AC-011-44 |
| RN-237 | TS-011-08 | AC-011-05, AC-011-18, AC-011-32 |
| RN-238 | TS-011-31 | AC-011-26 |
| RN-239 / RN-240 | TS-011-12 | AC-011-21, AC-011-22 |
| RN-241 | TS-011-13, TS-011-14, TS-011-27 | AC-011-11, AC-011-48, AC-011-51 |
| RN-242 / RN-243 | TS-011-24, TS-011-26 | AC-011-13, AC-011-23 |
| RN-244 | TS-011-25 | AC-011-24, AC-011-39 |
| RN-245 | TS-011-17 | AC-011-30, AC-011-37 |
| RN-121 | TS-011-16, TS-011-28 | AC-011-25, AC-011-49 |
| RN-701 / RN-702 | TS-011-23, TS-011-35 | AC-011-15 |
| RN-708 | TS-011-19 | — |
| RN-002 | TS-011-42 | AC-011-43 |
| RN-006 | TS-011-13, TS-011-24 | AC-011-11, AC-011-34 |
| INV-PER-05 / 06 | TS-011-08, TS-011-14 | AC-011-11, AC-011-18 |
| INV-PER-08 | TS-011-14 | AC-011-51 |
| INV-ADJ-01 | TS-011-09 | AC-011-20 |
| INV-SNP-01 | TS-011-20, TS-011-21, TS-011-22 | AC-011-13, AC-011-14, AC-011-45 |
| CE-ME-03 / 07 / 08 | TS-011-25, TS-011-45, TS-011-27 | AC-011-39, AC-011-38, AC-011-48 |
| SG-03 / SG-04 / SG-05 / SG-06 / SG-09 | TS-011-22, TS-011-32, TS-011-33, TS-011-43 | AC-011-44 a AC-011-47 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 17. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `balance-formula-cases.csv` | Exemplo normativo da §6.1 e variações, incluindo os ramos de `available = 0` | `TS-011-01`, `TS-011-02` |
| `rollover-cases.csv` | As 6 linhas normativas da §6.2 | `TS-011-04` — oráculo do carry-over |
| `closing-failure-points.csv` | Um ponto de injeção por passo de RN-241 | `TS-011-14` |
| `fixture-period-open` | Período `OPEN` com saldo e work logs | Base da maioria dos testes |
| `fixture-period-closed` | Período `CLOSED` com snapshot e work logs travados | `TS-011-20`, `TS-011-23` |
| `fixture-three-closed-periods` | Três períodos consecutivos `CLOSED` | `TS-011-25` |
| `fixture-period-drift` | Período com `consumedMinutes` divergente do real | `TS-011-13` |
| `fixture-period-10k-logs` | Período com 10.000 work logs | `TS-011-26`, `TS-011-39` |
| `fixture-period-100k-logs` | Período com 100.000 work logs | `TS-011-40` |
| `fixture-contract-policies` | Contratos com `NONE`, `FULL`, `CAPPED` (teto 0 e 300) e `HOURLY_OPEN` | `TS-011-04`, `TS-011-06` |
| `fixture-snapshot-tampered` | Snapshot com payload adulterado | `TS-011-22` |
| `fixture-clock-stuck` | `Clock` fixo em 9 e 11 minutos de `CLOSING` | `TS-011-45` |
| `fixture-tenant-b` | Segundo tenant com períodos espelhados | `TS-011-42` |

**Regra de fixture:** `fixture-period-drift` é construída **corrompendo deliberadamente** o desnormalizado após inserir os work logs. É o único modo de exercitar a reconciliação, que é a defesa contra R-02 — o risco de congelar uma divergência para sempre.

---

## 18. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-011-01`, `TS-011-04` e `TS-011-14` foram escritas e **revisadas** antes da implementação (SQ-02) |
| CC-02 | O exemplo normativo produz exatamente 2.760 / −140 / 140 / 105,07% |
| CC-03 | As 6 linhas de carry-over passam com igualdade exata |
| CC-04 | Nenhum cálculo usa ponto flutuante; resultados idênticos entre execuções |
| CC-05 | `consumptionRate` tratado nos dois ramos de `available = 0` |
| CC-06 | A atomicidade é provada com falha injetada em **cada** um dos 7 passos |
| CC-07 | A reconciliação é provada com desnormalizado corrompido |
| CC-08 | O checksum é determinístico em dupla geração, ordem variada e JVMs distintas |
| CC-09 | A constraint de snapshot permite refechamento, comprovado por teste |
| CC-10 | Nenhuma rota nem método de repositório permite alterar ajuste ou snapshot |
| CC-11 | Dois fechamentos simultâneos produzem exatamente um snapshot |
| CC-12 | Reabertura em cascata verificada nas duas ordens, com três períodos |
| CC-13 | Cálculo de saldo com tempo constante em 100, 10.000 e 100.000 work logs |
| CC-14 | Cobertura ≥ 95% em `BalanceCalculator` e `RolloverCalculator` |
| CC-15 | Cobertura ≥ 90% em services e validators |
| CC-16 | Os 8 endpoints passam na suíte de isolamento com `404` |
| CC-17 | `MANAGER` recusado em fechamento, ajuste e reabertura |
| CC-18 | Nenhum log contém `justification` |
| CC-19 | Alertas de `period.reconciliation.delta`, `period.stuck_closing` e `checksum_mismatch` configurados e testados |
