# 010 — Dashboard · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-010-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra ou da regra de composição — exemplo: `CP-04: retorna 30 pontos com zeros nos dias sem registro`.

> **Uma suíte escrita antes do código:** `TS-010-01` (equivalência com `011`). Não por SQ-02 — a complexidade desta feature é Média —, mas porque o modo de falha mais provável é reimplementar o cálculo de saldo em vez de consumir `BalanceService`. Um teste escrito depois passaria contra a reimplementação, e a divergência apareceria como uma reclamação de cliente sobre saldos diferentes em duas telas (R-02, reportável como RP-03).

**Relógio:** todo teste temporal injeta um `Clock` fixo. "Hoje", "semana" e a guarda de 3 dias úteis são inverificáveis com relógio real.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| **Equivalência** | Saldo do dashboard × `BalanceService` | JUnit | 100% dos campos |
| Unitário | `SeverityCalculator`, `ProjectionCalculator`, `ChartGapFiller`, `PercentageNormalizer`, `DashboardPeriodResolver`, `DashboardScopeResolver` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + agregações + PostgreSQL | Testcontainers | Os seis blocos |
| Temporal | Fuso, meia-noite, horário de verão, dias úteis | JUnit + `Clock` fixo | 4 bordas |
| API | Controller + serialização + permissões | `@WebMvcTest` | Os 2 endpoints |
| Isolamento | Tenancy + escopo de `MEMBER` + cache | Suíte dedicada + inspeção de SQL | Todos os blocos |
| Frontend | Store por bloco, gráficos, estados vazios | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Carga, troca de período, erro parcial | Playwright | Jornada completa |
| **Performance** | Meta de p95 < 800 ms com 100.000 registros | k6 + `EXPLAIN` | RNF-003 |
| Segurança | Escopo, monetários, cache, ausência de escrita | JUnit + scripts | Vetores da §19 |
| Regressão | Equivalência e performance | CI | 100% verde |

---

## 3. Teste de equivalência

### TS-010-01 — Saldo idêntico ao de `011` (INV-DSH-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o dashboard **não recalcula** saldo |
| **Pré-condição** | 20 combinações de período: com carry-over, com ajustes, com excedente, `HOURLY_OPEN`, `available = 0`, fechado e reaberto |
| **Passos** | Para cada combinação: 1. Consultar `GET /dashboard`. 2. Consultar `GET /contract-periods/{id}`. 3. Comparar `availableMinutes`, `consumedMinutes`, `remainingMinutes`, `overageMinutes` e `consumptionRate` |
| **Resultado esperado** | Igualdade **exata** nos cinco campos, nas 20 combinações. Adicionalmente: inspeção de código confirma que `DashboardService` chama `BalanceService.getBalance` e não contém nenhuma fórmula de saldo |

> Este teste é o guardião de OB-01. Ele falha imediatamente se alguém reimplementar o cálculo "para evitar a chamada ao service".

---

## 4. Testes unitários

### TS-010-02 — `SeverityCalculator` com limiares do contrato (§6.2, CP-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a escala usa `contract.notificationThresholds` |
| **Pré-condição** | `severity-cases.csv` com limiares default `[50,80,100]` e personalizados `[70,90]` |
| **Passos** | Para cada par (limiares, `rate`), calcular a severidade |
| **Resultado esperado** | Com default: 30% → `OK`, 60% → `INFO`, 85% → `WARNING`, 105% → `CRITICAL`. Com `[70,90]`: 75% → **`INFO`** (não `OK`), 92% → `WARNING`. **Este teste falha contra limiares fixos no código** — é o seu propósito |

### TS-010-03 — `SeverityCalculator` em bordas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento nos limites exatos |
| **Passos** | `rate` de 49,99%, 50%, 79,99%, 80%, 99,99%, 100%, 100,01% |
| **Resultado esperado** | 50% já é `INFO`; 80% já é `WARNING`; 100% já é `CRITICAL`. Os limiares são inclusivos no início da faixa |

### TS-010-04 — `ProjectionCalculator` (§6.3, CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os quatro estados e a guarda de 3 dias úteis |
| **Passos** | (a) 2 dias úteis decorridos; (b) 3 dias com projeção abaixo do saldo; (c) projeção 5% acima; (d) projeção 30% acima; (e) `available = 0` |
| **Resultado esperado** | (a) `NOT_APPLICABLE`; (b) `WITHIN_LIMIT`; (c) `AT_RISK`; (d) `WILL_EXCEED`; (e) `NOT_APPLICABLE`. A guarda de (a) é o que impede alarmes falsos no início de todo período (OB-04) |

### TS-010-05 — `ChartGapFiller` (CP-04, INV-DSH-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os 30 pontos obrigatórios |
| **Passos** | (a) mês com 12 dias de registro; (b) mês vazio; (c) mês com um único dia; (d) mês completo |
| **Resultado esperado** | Exatamente 30 pontos em **todos** os casos; dias sem registro com `netMinutes = 0` e `billableMinutes = 0`; ordem cronológica; nenhuma data duplicada nem faltante |

### TS-010-06 — `PercentageNormalizer` (CP-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a soma é sempre 100 |
| **Passos** | (a) três fatias iguais (33,33% cada); (b) valores que somam 99,99%; (c) valores que somam 100,01%; (d) uma única fatia; (e) nenhuma fatia |
| **Resultado esperado** | Soma exatamente 100 em (a) a (d); o resto é atribuído à **maior** fatia; nenhum percentual negativo; (e) lista vazia sem erro |

### TS-010-07 — `DashboardPeriodResolver` (RN-009, RN-705)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a resolução no fuso do tenant e o limite de 366 dias |
| **Passos** | (a) `CURRENT_PERIOD`; (b) `LAST_7_DAYS`; (c) `LAST_30_DAYS`; (d) `CUSTOM` com 366 dias; (e) `CUSTOM` com 367; (f) `CUSTOM` sem `from`; (g) `to` anterior a `from` |
| **Resultado esperado** | (a) a (d) resolvidos no fuso do tenant; (e) `DEVTIME-3001`; (f) e (g) `422` |

### TS-010-08 — `DashboardScopeResolver` (CP-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a resolução por papel |
| **Passos** | Resolver o escopo para `OWNER`, `ADMIN`, `MANAGER`, `MEMBER` e `VIEWER` |
| **Resultado esperado** | `TENANT` para os quatro papéis com `DASHBOARD_VIEW_ANY`, **incluindo `VIEWER`**; `USER` apenas para `MEMBER` |

---

## 5. Testes de integração

### TS-010-09 — Ordenação por criticidade (CP-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ordem de dois critérios |
| **Pré-condição** | 5 contratos: `rate` 105%, 95%, 85%, 60% e 30%; dois com 85% e `daysRemaining` 2 e 10 |
| **Passos** | Consultar o dashboard e verificar a ordem |
| **Resultado esperado** | `CRITICAL`, `WARNING`(95%), `WARNING`(85%, 2 dias), `WARNING`(85%, 10 dias), `INFO`, `OK`. Desempate por `daysRemaining` **crescente** |

### TS-010-10 — Alertas do estado atual (CP-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `alerts` não vem de `notifications` |
| **Passos** | 1. Contrato com 105% de consumo e notificação registrada. 2. Consultar o dashboard. 3. Aplicar ajuste que elimina o excedente. 4. Consultar novamente. 5. Verificar a tabela de notificações |
| **Resultado esperado** | (2) alerta `CRITICAL` presente; (4) alerta **ausente**; (5) notificação **ainda presente** no histórico (CE-11). Inspeção confirma que `DashboardAlertService` não consulta `notifications` |

### TS-010-11 — Os seis blocos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a composição completa |
| **Passos** | Consultar o dashboard em tenant com dados em todos os blocos |
| **Resultado esperado** | `quickStats` com os 4 valores e rótulos; `contracts` ordenado; `alerts` derivado; `recentWorkLogs` com exatamente 5; `openTickets` sem `DONE` nem `CANCELLED`; `charts` com os três conjuntos |

### TS-010-12 — Agregações em paralelo (§20.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a execução concorrente |
| **Passos** | Instrumentar os seis blocos com marcação de início e fim; consultar o dashboard |
| **Resultado esperado** | Os blocos se sobrepõem no tempo; o tempo total é próximo ao do bloco mais lento, **não** à soma dos seis |

### TS-010-13 — Estados vazios (CX-01, CX-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os dois tipos de vazio |
| **Passos** | (a) tenant sem contratos; (b) tenant com contratos e sem registros no período |
| **Resultado esperado** | (a) estrutura vazia, **nenhuma** agregação de gráfico executada (verificado por inspeção de SQL); (b) estrutura completa com zeros e 30 pontos em zero |

### TS-010-14 — Falha isolada por bloco (CX-17, §10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o dashboard não falha inteiro |
| **Passos** | Para cada um dos seis blocos: injetar falha e consultar o dashboard |
| **Resultado esperado** | Em todos os seis casos, os outros cinco blocos são retornados com dados; o bloco com falha vem marcado como erro; `200` na resposta, não `500`; a métrica `dashboard.block.failed` é incrementada com a tag do bloco |

### TS-010-15 — Período fechado servido do snapshot (CX-10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a coerência com RN-701 |
| **Passos** | 1. Contrato com período corrente `CLOSED`. 2. Alterar dados no banco. 3. Consultar o dashboard |
| **Resultado esperado** | Valores do snapshot; `isPartial` falso; alterações posteriores não aparecem |

### TS-010-16 — Índices cobertos (§13.4, DoD-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que as agregações são index-only |
| **Passos** | `EXPLAIN (ANALYZE, BUFFERS)` nas quatro consultas de agregação |
| **Resultado esperado** | `Index Only Scan` nos três índices com `INCLUDE`; **nenhum** acesso ao heap para as somas; nenhum sequential scan em `work_logs` |

---

## 6. Testes temporais

### TS-010-17 — Fuso do tenant (RN-009, CX-19)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que "hoje" é o dia local do tenant |
| **Pré-condição** | Tenant em `America/Sao_Paulo`; `Clock` fixo; registro às 22:00 locais |
| **Passos** | Consultar `quickStats` e o gráfico diário |
| **Resultado esperado** | O registro é contabilizado no dia local do tenant, **não** no dia UTC seguinte; o mesmo vale para "semana" e para o agrupamento diário |

### TS-010-18 — Horário de verão (CX-20)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum dia é duplicado ou omitido |
| **Passos** | Gráfico diário em período que contém a transição, em ambas as direções |
| **Resultado esperado** | Exatamente 30 pontos; o dia da transição aparece uma única vez; o total corresponde aos registros com aquela data local |

### TS-010-19 — Contagem de dias úteis (§6.3)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda da projeção |
| **Pré-condição** | `tenant.settings.workDays = [1,2,3,4,5]`; `Clock` fixo |
| **Passos** | Períodos iniciando em segunda, sexta e sábado, avançando o relógio dia a dia |
| **Resultado esperado** | A projeção só aparece a partir do 3º **dia útil**, não do 3º dia corrido; fins de semana não contam |

---

## 7. Testes de API

### TS-010-20 — Contrato dos 2 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar `GET /dashboard` com os 4 tipos de período e `GET /dashboard/chart/{type}` com os 6 tipos |
| **Resultado esperado** | Estrutura conforme a §10.1 de `reports.md`; os 6 tipos de gráfico respondem; tipo inválido retorna `422`; erros em RFC 7807 |

### TS-010-21 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Consultar o dashboard como cada um dos 5 papéis, e como usuário sem nenhuma permissão de dashboard |
| **Resultado esperado** | `OWNER`, `ADMIN`, `MANAGER` e `VIEWER` recebem `scope = TENANT`; `MEMBER` recebe `USER`; usuário sem permissão recebe `403 DEVTIME-1101` |

### TS-010-22 — Recarga isolada de gráfico (§10.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas o gráfico é recalculado |
| **Passos** | Chamar `GET /dashboard/chart/by-category` inspecionando as consultas emitidas |
| **Resultado esperado** | Apenas a agregação de categorias é executada; nenhuma consulta de saldo, tickets ou timer |

---

## 8. Testes de frontend

### TS-010-23 — Estado por bloco na store
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `loading` e `errors` independentes |
| **Passos** | Simular respostas com um bloco em erro e os demais com dados |
| **Resultado esperado** | Cada bloco tem seu próprio estado; o bloco com erro exibe `dt-block-error` com ação de repetir; os demais renderizam normalmente; a página não fica em branco |

### TS-010-24 — Zeros visíveis no gráfico diário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-04 na renderização |
| **Passos** | Renderizar com 12 dias de dados e 18 zeros |
| **Resultado esperado** | 30 barras renderizadas; as 18 de valor zero são visíveis como espaço no eixo; o eixo não é comprimido |

### TS-010-25 — Reutilização de componentes de `011`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não há duplicação visual |
| **Passos** | Inspecionar as importações de `dt-contract-status-card` |
| **Resultado esperado** | `dt-balance-summary`, `dt-consumption-gauge` e `dt-partial-badge` importados de `011`; nenhuma reimplementação local |

### TS-010-26 — Acessibilidade dos gráficos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 em conteúdo visual |
| **Passos** | Navegar P09 por teclado; verificar leitor de tela nos três gráficos |
| **Resultado esperado** | Cada gráfico possui alternativa textual com os valores; navegação completa por teclado; zero violações do axe-core |

### TS-010-27 — Seletor de período
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a persistência e a validação |
| **Passos** | Trocar o período; selecionar intervalo personalizado de 367 dias; recarregar a página |
| **Resultado esperado** | A troca dispara nova carga; 367 dias é bloqueado **no cliente** antes do envio; o período escolhido persiste em `preferences` e sobrevive ao recarregamento |

---

## 9. Testes E2E

### TS-010-28 — Carga e navegação
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a jornada do usuário |
| **Passos** | 1. Abrir P09 em tenant com dados. 2. Conferir a ordem dos cartões. 3. Clicar em um alerta e conferir a navegação. 4. Trocar o período de um gráfico. 5. Clicar em um registro recente |
| **Resultado esperado** | Cartões na ordem de criticidade; o alerta navega para o período correspondente; a troca de período do gráfico não recarrega a página; o registro navega para o detalhe |

### TS-010-29 — Tenant novo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o estado de boas-vindas |
| **Passos** | Abrir P09 em tenant sem contratos |
| **Resultado esperado** | Estado de boas-vindas com atalho para o onboarding; nenhum gráfico vazio confuso; nenhuma mensagem de erro |

---

## 10. Testes de performance

### TS-010-30 — Meta de p95 com volume (RNF-003, RP-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta central da feature |
| **Pré-condição** | Tenant com **100.000 work logs**, 20 contratos ativos, 10 clientes, 9 categorias |
| **Passos** | 500 cargas completas do dashboard medindo p95 e o tempo por bloco |
| **Resultado esperado** | **p95 < 800 ms**; `quickStats` < 150 ms; cartões < 300 ms; cada gráfico < 250 ms; index-only scans confirmados |

### TS-010-31 — Cinquenta contratos ativos (CX-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento com muitos cartões |
| **Passos** | Carregar dashboard com 50 contratos ativos |
| **Resultado esperado** | Os 10 mais críticos na primeira resposta; tempo dentro da meta; demais carregados por rolagem |

### TS-010-32 — Eficácia do cache
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o ganho e a invalidação |
| **Passos** | 1. Carregar gráfico duas vezes seguidas. 2. Criar work log. 3. Carregar novamente |
| **Resultado esperado** | (1) segunda chamada servida do cache, sem consulta ao banco; (3) cache invalidado, dados atualizados; `dashboard.chart.cache_hit_ratio` acima de 50% em uso normal |

---

## 11. Testes de segurança

### TS-010-33 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 |
| **Passos** | Consultar o dashboard e os 6 gráficos autenticado no tenant A, com dados existentes no tenant B |
| **Resultado esperado** | Nenhum dado do tenant B em nenhum bloco; nenhum total contaminado; referência a recurso do tenant B retorna `404 DEVTIME-2002` |

### TS-010-34 — Escopo de `MEMBER` em todos os blocos (INV-DSH-02, SG-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-02 em **todas** as agregações |
| **Pré-condição** | 100 work logs no tenant, 20 do `MEMBER` de teste |
| **Passos** | Consultar como `MEMBER` e inspecionar o SQL de cada um dos seis blocos |
| **Resultado esperado** | Filtro por `user_id` presente na cláusula `WHERE` de **todas** as agregações, inclusive `quickStats` e os três gráficos; `quickStats` reflete apenas os 20; nenhuma filtragem em memória |

### TS-010-35 — Ausência de valores monetários (INV-DSH-04, SG-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a omissão no mapper |
| **Passos** | Consultar como `MEMBER` e como `VIEWER`, inspecionando o **JSON da resposta** |
| **Resultado esperado** | `MEMBER`: nenhum campo monetário presente — verificado na resposta, não no DOM (IMP-06); `VIEWER`: campos presentes |

### TS-010-36 — Carteira de clientes restrita (SG-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a nota ² de `permissions.md` no gráfico |
| **Pré-condição** | 10 clientes; `MEMBER` vinculado a contratos de 2 |
| **Passos** | Consultar `byClient` como `MEMBER` |
| **Resultado esperado** | Apenas 2 fatias; os outros 8 clientes ausentes das fatias **e** do total; percentuais recalculados sobre os 2 |

### TS-010-37 — Cache isolado por tenant (SG-07, R-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a chave de cache |
| **Passos** | 1. Carregar gráfico no tenant A. 2. Carregar o mesmo tipo e período no tenant B. 3. Repetir com `MANAGER` e `MEMBER` do mesmo tenant |
| **Resultado esperado** | (2) dados exclusivamente do tenant B; (3) `MANAGER` recebe `TENANT` e `MEMBER` recebe `USER`, sem contaminação. A chave inclui `tenantId`, escopo e `userId` quando aplicável |

### TS-010-38 — Ausência de escrita (RS-01, CP-13)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a feature é somente leitura |
| **Passos** | Consultar o dashboard e os 6 gráficos, capturando **todas** as instruções SQL e eventos |
| **Resultado esperado** | Nenhum `INSERT`, `UPDATE` ou `DELETE`; nenhum `AuditLog` criado; nenhum evento de domínio publicado por esta feature |

---

## 12. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-010-39 | Equivalência com `011` (`TS-010-01`) | **Toda** alteração em `011-bank-hours`, em `BalanceCalculator` ou nas fórmulas de RN-218 a RN-222 |
| TS-010-40 | Meta de performance (`TS-010-30`) | Toda alteração nas agregações, nos índices ou em `008-worklogs` |
| TS-010-41 | Índices cobertos (`TS-010-16`) | Toda migration que toque `work_logs` ou os índices da §13.4 |
| TS-010-42 | Escopo de `MEMBER` (`TS-010-34`) | Toda alteração em agregação, em `permissions.md` §9 ou em novo bloco |
| TS-010-43 | Severidade (`TS-010-02`) | Toda alteração em `notificationThresholds` ou em RN-602 — a escala do dashboard e os alertas de `013` devem permanecer coerentes |
| TS-010-44 | Isolamento e cache (`TS-010-33`, `TS-010-37`) | Todo bloco ou tipo de gráfico novo |

**Política:** `TS-010-01` e `TS-010-30` rodam em todo PR que toque esta feature **ou** `011-bank-hours`. Uma alteração nas fórmulas de saldo em `011` que não fosse refletida aqui produziria dois números diferentes para o mesmo período — o cenário que SQ-10 trata como bloqueio da fila.

---

## 13. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| INV-DSH-01 | TS-010-01, TS-010-39 | AC-010-02 |
| INV-DSH-02 | TS-010-34, TS-010-36 | AC-010-35, AC-010-37 |
| INV-DSH-03 | TS-010-05, TS-010-24 | AC-010-09, AC-010-21, AC-010-22 |
| INV-DSH-04 | TS-010-35 | AC-010-36 |
| CP-01 | TS-010-08, TS-010-21 | AC-010-13, AC-010-35, AC-010-42 |
| CP-02 | TS-010-09 | AC-010-03 |
| CP-03 | TS-010-10 | AC-010-06, AC-010-27 |
| CP-04 | TS-010-02, TS-010-05, TS-010-24 | AC-010-04, AC-010-09, AC-010-25 |
| CP-05 | TS-010-11 | AC-010-07 |
| CP-06 | TS-010-06 | AC-010-29 |
| §6.2 severidade | TS-010-02, TS-010-03 | AC-010-04, AC-010-24, AC-010-25 |
| §6.3 projeção | TS-010-04, TS-010-19 | AC-010-05, AC-010-23 |
| RN-218 a RN-222 | TS-010-01 | AC-010-02, AC-010-24 |
| RN-702 | TS-010-15 | AC-010-14, AC-010-28 |
| RN-705 | TS-010-07 | AC-010-15 |
| RN-009 | TS-010-17, TS-010-18 | AC-010-30, AC-010-31 |
| RN-002 | TS-010-33 | AC-010-34 |
| §10.1 reports.md | TS-010-11, TS-010-20 | AC-010-01, AC-010-07, AC-010-08, AC-010-10 |
| §10.2 reports.md | TS-010-22 | AC-010-11, AC-010-17 |
| §7 / §9 permissions | TS-010-21, TS-010-34, TS-010-36 | AC-010-13, AC-010-18, AC-010-35 a AC-010-37 |
| §10 spec (erro parcial) | TS-010-14, TS-010-23 | AC-010-32 |
| SG-04 / SG-07 | TS-010-36, TS-010-37 | AC-010-37, AC-010-38 |
| RS-01 | TS-010-38 | AC-010-39 |
| RNF-003 / RP-06 | TS-010-16, TS-010-30, TS-010-31 | — |

**Critério de completude:** toda regra da §6, toda regra de composição da §6.1 e toda invariante da §6.4 da spec possuem ao menos uma linha nesta matriz.

---

## 14. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `severity-cases.csv` | Limiares default e `[70,90]` × faixas de `rate`, incluindo as bordas exatas | `TS-010-02`, `TS-010-03` |
| `projection-cases.csv` | Dias úteis decorridos × projeção × `available` | `TS-010-04` |
| `chart-gap-cases.csv` | Meses com 0, 1, 12 e 30 dias de registro | `TS-010-05` |
| `percentage-cases.csv` | Conjuntos que somam 99,99%, 100% e 100,01% | `TS-010-06` |
| `fixture-balance-variations` | 20 combinações de período: carry-over, ajustes, excedente, `HOURLY_OPEN`, `available = 0`, fechado, reaberto | `TS-010-01` — oráculo da equivalência |
| `fixture-five-contracts-severity` | 5 contratos nas quatro severidades, com empate de `rate` | `TS-010-09` |
| `fixture-50-contracts` | 50 contratos ativos | `TS-010-31` |
| `fixture-tenant-100k-logs` | 100.000 work logs, 20 contratos, 10 clientes, 9 categorias | `TS-010-30` |
| `fixture-member-scope` | 100 registros, 20 do `MEMBER`; 10 clientes, 2 vinculados | `TS-010-34`, `TS-010-36` |
| `fixture-clock-dst` | `Clock` fixo nas duas transições de horário de verão | `TS-010-18` |
| `fixture-clock-workdays` | `Clock` fixo em períodos iniciando em segunda, sexta e sábado | `TS-010-19` |
| `fixture-tenant-empty` | Tenant sem contratos | `TS-010-13`, `TS-010-29` |
| `fixture-tenant-b` | Segundo tenant com dados distintos | `TS-010-33`, `TS-010-37` |

**Regras de fixture:**
- `fixture-balance-variations` é a fixture mais importante: ela cobre os casos em que uma reimplementação do cálculo divergiria — carry-over, ajustes e `available = 0`. Um teste de equivalência com apenas o caso simples passaria contra código errado.
- `fixture-tenant-100k-logs` é gerada por `COPY` em massa; construí-la por inserções levaria mais que a suíte inteira.
- Nenhuma fixture usa data relativa ao momento da execução.

---

## 15. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-010-01` foi escrita **antes** de `DashboardService` |
| CC-02 | Equivalência com `011` verde nas 20 combinações, em todos os cinco campos |
| CC-03 | Inspeção de código confirma ausência de fórmula de saldo nesta feature |
| CC-04 | Severidade usa os limiares do contrato, provado com `[70,90]` |
| CC-05 | Projeção retorna `NOT_APPLICABLE` com menos de 3 **dias úteis** |
| CC-06 | `dailyMinutes` retorna exatamente 30 pontos em todos os cenários |
| CC-07 | Percentuais somam exatamente 100 em todos os conjuntos |
| CC-08 | `alerts` comprovadamente não consulta `notifications` |
| CC-09 | Falha em cada um dos seis blocos preserva os outros cinco |
| CC-10 | `EXPLAIN` confirma index-only scan nas três agregações |
| CC-11 | **p95 < 800 ms com 100.000 registros** (RNF-003) |
| CC-12 | Escopo de `MEMBER` comprovado em SQL em **todos** os seis blocos |
| CC-13 | Nenhum valor monetário na resposta para `MEMBER` |
| CC-14 | Cache isolado por tenant e por escopo, comprovado por teste |
| CC-15 | Nenhuma escrita, nenhum `AuditLog`, nenhum evento — comprovado por captura de SQL |
| CC-16 | Componentes de saldo reutilizados de `011`, sem reimplementação |
| CC-17 | Cobertura ≥ 95% em calculators e ≥ 90% em services |
| CC-18 | Zero violações do axe-core em P09, com alternativa textual nos gráficos |
