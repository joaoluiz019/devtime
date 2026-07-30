# 004 — Contracts & Periods · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-004-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-211: primeiro período termina no dia anterior ao próximo billingDay`.

> **SQ-02 — Ordem inegociável.** Esta feature é de complexidade **Crítica**. As suítes `TS-004-01` a `TS-004-06` (temporais e de contiguidade) e `TS-004-16` (matriz de transições) são escritas, revisadas e **aprovadas** antes de qualquer linha de `PeriodGenerator`, `ProrationCalculator` ou `ContractStateMachine`. Escrever essas suítes depois produziria testes que confirmam o que o código faz, não o que a regra exige — exatamente o modo de falha que RP-01 descreve.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `PeriodGenerator`, `ProrationCalculator`, `PeriodContiguityValidator`, `ContractStateMachine`, `ContractCodeGenerator`, `ContractTypeCoherenceValidator` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Ativação, suspensão, encerramento, jobs |
| API | Controller + serialização + permissões + OpenAPI | `@WebMvcTest` | Todos os endpoints da §14 |
| Isolamento | Tenancy em contratos e períodos | Suíte dedicada | Todos os endpoints |
| Frontend | Stores, prévia, formulário, diálogos de transição | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Criar → prever → ativar → acompanhar períodos → encerrar | Playwright | Jornada completa |
| Performance | `resolveOpenPeriod`, listagem, jobs em lote | k6 + JMH | Metas da §20 |
| Segurança | Isolamento, campos monetários, imutabilidade | JUnit + scripts | Vetores da §19 |
| Regressão | Suíte temporal completa em toda alteração de calendário | CI | 100% verde |

**Decisão de ferramenta:** a suíte temporal usa `@ParameterizedTest` com `@CsvFileSource`, não casos escritos à mão. Um arquivo CSV de combinações torna trivial adicionar uma borda descoberta em produção sem tocar no código de teste — e mantém a tabela normativa da §6.2 da spec verificável linha a linha.

**Relógio:** todo teste temporal injeta um `Clock` fixo (`Clock.fixed`). Nenhum teste usa `LocalDate.now()` real — testes que dependem do dia da execução falham em 29 de fevereiro e passam despercebidos o resto do ano.

---

## 3. Testes unitários

### TS-004-01 — Geração dos 5 cenários normativos (RN-211, RN-212)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o gerador reproduz **exatamente** a tabela normativa da §6.2 da spec, que é cópia de `business-rules.md` §7.2 |
| **Pré-condição** | Contrato `MONTHLY_HOURS`, 2.400 min, `autoRenew = true`, `Clock` fixo |
| **Passos** | Para cada uma das 5 linhas da tabela (`startDate` × `billingDay`), gerar os 3 primeiros períodos e comparar `startDate` e `endDate` de cada um |
| **Resultado esperado** | Igualdade exata nas 15 datas. Qualquer divergência falha o build — esta suíte é o oráculo do gerador |

**Casos normativos verificados:**

| # | `startDate` | `billingDay` | Período 1 esperado | Período 2 esperado | Período 3 esperado |
|:--:|---|:--:|---|---|---|
| 1 | 2026-01-01 | 1 | 01/01 – 31/01 | 01/02 – 28/02 | 01/03 – 31/03 |
| 2 | 2026-01-10 | 1 | 10/01 – 31/01 | 01/02 – 28/02 | 01/03 – 31/03 |
| 3 | 2026-01-15 | 15 | 15/01 – 14/02 | 15/02 – 14/03 | 15/03 – 14/04 |
| 4 | 2026-01-20 | 5 | 20/01 – 04/02 | 05/02 – 04/03 | 05/03 – 04/04 |
| 5 | 2026-02-28 | 28 | 28/02 – 27/03 | 28/03 – 27/04 | 28/04 – 27/05 |

### TS-004-02 — `startDate` coincide com `billingDay` (RN-211)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o primeiro período é um **ciclo cheio**, não um período de um dia |
| **Passos** | 1. `startDate = 2026-03-15`, `billingDay = 15`. 2. Gerar o período 1 |
| **Resultado esperado** | `15/03 – 14/04`; `contractedMinutes` integral, **sem** rateio (o período não é parcial). Caso 3 de TS-004-01 confirma a continuidade |

### TS-004-03 — Bordas de calendário (RP-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento em meses curtos, ano bissexto e viradas de ano |
| **Pré-condição** | `Clock` fixo; `billingDay` de 1 a 28 |
| **Passos** | Gerar 14 períodos consecutivos para: (a) `billingDay = 28` iniciando em 2024-01-28 (bissexto); (b) `billingDay = 28` iniciando em 2026-01-28 (não bissexto); (c) `startDate = 2026-12-20`, `billingDay = 5` (virada de ano); (d) `billingDay = 1` iniciando em 2026-01-31 |
| **Resultado esperado** | (a) fevereiro de 2024 gera `28/02 – 27/03`, incluindo o dia 29; (b) `28/02 – 27/03`; (c) período 1 = `20/12 – 04/01`, atravessando o ano; (d) período 1 = `31/01 – 31/01`, um único dia, com `contractedMinutes` rateado a 1/31. Nenhum caso lança exceção nem produz `endDate < startDate` |

**Por que `billingDay` de 29 a 31 não é testado:** RN-203 os proíbe. O teste correspondente é `TS-004-11`, que verifica a **rejeição** na entrada — a ambiguidade é eliminada na fronteira, não tratada no gerador.

### TS-004-04 — Rateio proporcional (RN-217)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a fórmula `round(monthlyMinutes × diasDoPeríodo / diasDoCicloCheio)` e a ausência de ponto flutuante |
| **Pré-condição** | `ProrationCalculator` isolado |
| **Passos** | 1. Reproduzir o exemplo normativo: 2.400 min, período de 22 dias, ciclo de 31 dias. 2. Bordas de arredondamento: resultados terminando em `,5` exatos. 3. Período de 1 dia. 4. Período de ciclo cheio. 5. `prorateFirstPeriod = false` |
| **Resultado esperado** | (1) **exatamente 1.703** min, conforme o exemplo da doc; (2) `HALF_UP` sobre inteiros, sem `double` em nenhum ponto do caminho; (3) valor > 0; (4) valor igual a `monthlyMinutes`, sem passar pelo rateio; (5) `monthlyMinutes` integral mesmo em período parcial |

### TS-004-05 — Truncamento por `endDate` (RN-214)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a geração para em `contract.endDate` e que o último período é rateado |
| **Passos** | 1. Contrato de 01/01 a 20/03, `billingDay = 1`. 2. Gerar todos os períodos. 3. Tentar gerar um período posterior |
| **Resultado esperado** | Períodos `01/01–31/01`, `01/02–28/02`, `01/03–20/03`; o último com `contractedMinutes` rateado a 20/31; (3) nenhum período adicional é criado e o gerador retorna vazio sem erro |

### TS-004-06 — Contiguidade exaustiva (RN-216, INV-PER-02, INV-PER-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar contiguidade e ausência de sobreposição em larga escala |
| **Pré-condição** | Matriz de 1.000+ combinações: `startDate` cobrindo os 366 dias de um ano bissexto × `billingDay` de 1 a 28 × `endDate` presente/ausente |
| **Passos** | Para cada combinação, gerar 24 períodos e verificar as invariantes |
| **Resultado esperado** | Para todo `n`: `período[n].startDate == período[n−1].endDate + 1 dia` (INV-PER-03); nenhum par de períodos com interseção (INV-PER-02); `endDate ≥ startDate` (INV-PER-04); `sequence` estritamente crescente sem lacuna. Zero exceções em toda a matriz |

### TS-004-07 — Coerência de tipo (INV-CTR-02, INV-CTR-03, INV-CTR-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `ContractTypeCoherenceValidator` em todas as combinações |
| **Passos** | 1. `MONTHLY_HOURS` sem `monthlyMinutes`. 2. `MONTHLY_HOURS` com `monthlyMinutes = 0` e com `44641`. 3. `HOURLY_OPEN` com `monthlyMinutes` preenchido. 4. `HOURLY_OPEN` com `rolloverPolicy = FULL`. 5. `CAPPED` sem `rolloverCapMinutes`. 6. `CAPPED` com `rolloverCapMinutes = 0` |
| **Resultado esperado** | (1) e (2) `422 DEVTIME-2202`; (3) e (4) `422` — `HOURLY_OPEN` não aceita saldo nem rollover; (5) `422 DEVTIME-2000`; (6) aceito — teto zero é válido e equivale a `NONE`, comportamento documentado |

### TS-004-08 — `HOURLY_OPEN` sem saldo (RN-210)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que contrato de horas abertas nunca produz teto nem alerta |
| **Passos** | 1. Ativar contrato `HOURLY_OPEN`. 2. Gerar 3 períodos. 3. Inspecionar `contractedMinutes` e `overagePolicy` efetiva |
| **Resultado esperado** | `contractedMinutes = 0` em todos; `overagePolicy` ignorada; nenhum limiar de consumo avaliado (CE-10) |

### TS-004-09 — Código sequencial do contrato (INV-CTR-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar unicidade e formato de `CT-XXXX` por tenant |
| **Passos** | 1. Criar 3 contratos no tenant A. 2. Criar 1 no tenant B. 3. Excluir logicamente o `CT-0002` de A e criar outro |
| **Resultado esperado** | A recebe `CT-0001`, `CT-0002`, `CT-0003`; B recebe `CT-0001` — a sequência é **por tenant**; (3) o novo recebe `CT-0004`, sem reutilizar o número excluído (reuso quebraria a `key` histórica dos tickets, RN-302) |

### TS-004-10 — Matriz de transições do contrato (§4.5 SM)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `ContractStateMachine` implementa a matriz 5×5 integralmente |
| **Passos** | Para cada uma das 25 células (origem × destino), tentar a transição |
| **Resultado esperado** | As 8 células ✅ da matriz executam; as 12 células ❌ retornam `409 DEVTIME-2010` com `availableTransitions[]` correto; as 5 auto-transições retornam `200` sem efeito (ME-03). Nenhuma célula sem teste |

---

## 4. Testes de integração

### TS-004-11 — Validação de entrada na criação (RN-202, RN-203, RN-204)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ordem de aplicação da §6.1 e a rejeição de valores fora de faixa |
| **Passos** | 1. `billingDay = 0`, `29`, `31`. 2. `monthlyMinutes = 0` e `44641`. 3. `endDate < startDate`. 4. Cliente `INACTIVE`. 5. Cliente de outro tenant |
| **Resultado esperado** | (1) `422 DEVTIME-2203` nos três; (2) `422 DEVTIME-2202`; (3) `422 DEVTIME-2204`; (4) `422 DEVTIME-2405`; (5) `404 DEVTIME-2002`, nunca `403`. A validação de cliente (3ª) ocorre antes da de tipo (4ª), conforme a §6.1 |

### TS-004-12 — Ativação atômica gera o primeiro período (RN-209, INV-CTR-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que ativação e geração do período ocorrem na mesma transação |
| **Pré-condição** | Contrato em `DRAFT` com todos os campos obrigatórios |
| **Passos** | 1. Ativar. 2. Verificar contrato e período. 3. Injetar falha no `PeriodGenerator` e ativar outro contrato. 4. Verificar o estado após a falha |
| **Resultado esperado** | (2) contrato `ACTIVE` e exatamente um período `OPEN` com `sequence = 1`; (4) **rollback total** — contrato permanece `DRAFT` e nenhum período órfão existe. Um contrato `ACTIVE` sem período violaria INV-CTR-06 e tornaria RN-107 insatisfazível |

### TS-004-13 — Nenhum período em `DRAFT`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão da §6.1 |
| **Passos** | 1. Criar contrato. 2. Editar `startDate` e `billingDay` 5 vezes. 3. Consultar períodos |
| **Resultado esperado** | Zero períodos em todas as etapas; nenhuma estrutura precisou ser destruída entre as edições |

### TS-004-14 — Constraint `EXCLUDE` contra sobreposição (INV-PER-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o banco impede sobreposição **mesmo contornando a aplicação** |
| **Pré-condição** | Contrato com 2 períodos contíguos |
| **Passos** | 1. `INSERT` direto no banco de um período sobreposto ao existente. 2. `INSERT` de um período que apenas encosta (`start = anterior.end + 1`). 3. `INSERT` idêntico a um existente |
| **Resultado esperado** | (1) e (3) violação de `EXCLUDE USING gist`, transação abortada; (2) aceito. A aplicação não é a única linha de defesa — se um job futuro tiver um erro, o banco recusa |

### TS-004-15 — Único período `OPEN` por contrato (INV-PER-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o índice único parcial |
| **Passos** | 1. Contrato com período `OPEN`. 2. Tentar abrir um segundo período via `INSERT` direto com `status = OPEN` |
| **Resultado esperado** | Violação de índice único parcial. Dois períodos abertos tornariam RN-107 ambígua e alocariam horas ao período errado |

### TS-004-16 — Alteração de `monthlyMinutes` (RN-207)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que períodos fechados nunca mudam e que o aberto exige confirmação |
| **Pré-condição** | Contrato com um período `CLOSED`, um `OPEN` e um `SCHEDULED` |
| **Passos** | 1. Alterar `monthlyMinutes` sem confirmação. 2. Alterar com confirmação. 3. Inspecionar os três períodos |
| **Resultado esperado** | (1) `409 DEVTIME-2207` informando o período aberto afetado; (2) `200`; (3) `CLOSED` **inalterado**, `OPEN` atualizado, `SCHEDULED` atualizado. Alterar o fechado reescreveria um relatório entregue (ART-005) |

### TS-004-17 — Alteração de `billingDay` (RN-208)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o bloqueio com horas lançadas |
| **Passos** | 1. Contrato com período `OPEN` **sem** work logs → alterar `billingDay`. 2. Registrar um work log → alterar novamente |
| **Resultado esperado** | (1) `200`, períodos futuros recalculados; (2) `409 DEVTIME-2208`. Redefinir o ciclo com horas lançadas realocaria horas entre períodos silenciosamente |

### TS-004-18 — Imutabilidade de `type` (RN-206, INV-CTR-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o modelo comercial não muda após vigorar |
| **Passos** | 1. Alterar `type` em `DRAFT`. 2. Ativar. 3. Alterar `type` |
| **Resultado esperado** | (1) `200`; (3) `422 DEVTIME-2003`. Mudar de `MONTHLY_HOURS` para `HOURLY_OPEN` invalidaria todo o histórico de saldo já apurado |

### TS-004-19 — Suspensão e retomada com lacuna (CE-ME-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a geração dos períodos faltantes preservando contiguidade |
| **Passos** | 1. Suspender o contrato. 2. Avançar o `Clock` em 2 ciclos completos. 3. Verificar que nenhum período foi gerado. 4. Retomar. 5. Verificar a sequência completa |
| **Resultado esperado** | (3) nenhum período novo durante a suspensão; (5) os 2 períodos faltantes são criados com `contractedMinutes` rateado, mantendo INV-PER-03 sem lacuna temporal. `sequence` continua sem saltos |

### TS-004-20 — Encerramento e cancelamento (RN-214)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o truncamento do período corrente em cada caminho |
| **Passos** | 1. Encerrar (`ENDED`) contrato com período aberto. 2. Cancelar (`CANCELLED`) outro contrato equivalente. 3. Verificar os períodos e os work logs |
| **Resultado esperado** | (1) período truncado em `contract.endDate`, fechamento automático agendado para +3 dias (CE-ME-02); (2) período truncado em `now()`; (3) work logs **preservados** em ambos os casos, nenhum saldo devolvido |

### TS-004-21 — Guarda de timer ativo nas transições
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que suspensão e encerramento exigem ausência de timer |
| **Passos** | 1. Iniciar timer em ticket do contrato. 2. Tentar suspender. 3. Tentar encerrar. 4. Encerrar o timer. 5. Repetir |
| **Resultado esperado** | (2) e (3) `409` com a lista de timers ativos; (5) ambos executam. Cobre também timer `PAUSED`, que conta como ativo (CE-ME-01) |

### TS-004-22 — Exclusão restrita (RN-205)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-CTR-08 |
| **Passos** | Tentar excluir contrato em `DRAFT` (sem work logs), `ACTIVE` sem work logs e `ACTIVE` com work logs |
| **Resultado esperado** | `DRAFT` → `204`; `ACTIVE` sem work logs → `409` (só se exclui em `DRAFT`); com work logs → `409 DEVTIME-2205` sugerindo encerrar |

### TS-004-23 — Períodos retroativos de migração (CE-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a geração para contratos com `startDate` no passado |
| **Passos** | 1. Criar contrato com `startDate` 6 meses atrás. 2. Ativar. 3. Inspecionar os períodos gerados |
| **Resultado esperado** | Períodos passados criados como `CLOSED` **sem** snapshot, marcados `MIGRATION`; o período corrente é `OPEN`; contiguidade preservada; apenas `ADMIN` pode lançar horas nos passados |

### TS-004-24 — `resolveOpenPeriod` (RN-107)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a interface pública consumida por `008` |
| **Passos** | 1. Resolver com data no meio do período. 2. No `startDate` exato. 3. No `endDate` exato. 4. Um dia após o `endDate` do último. 5. Antes do `startDate` do primeiro |
| **Resultado esperado** | (1), (2) e (3) retornam o período correto — o intervalo de datas é **fechado** `[start, end]` (§7.2); (4) e (5) retornam vazio, produzindo `DEVTIME-2107` no chamador |

### TS-004-25 — Idempotência dos jobs (RN-213)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que reexecução e execução concorrente não duplicam períodos |
| **Passos** | 1. Executar `GeneratePeriodsJob`. 2. Reexecutar imediatamente. 3. Executar em duas instâncias simultâneas. 4. Verificar `@SchedulerLock`. 5. Executar `OpenScheduledPeriodsJob` duas vezes |
| **Resultado esperado** | Nenhum período duplicado em nenhum cenário; a segunda instância não adquire o lock e encerra sem trabalho; a contagem final é idêntica à de uma execução única |

### TS-004-26 — Janela de 3 dias do job (RN-213)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o gatilho temporal exato |
| **Passos** | Com `Clock` fixo, executar o job a 4, 3, 2 e 1 dia do fim do período, e com `autoRenew = false` |
| **Resultado esperado** | Nenhum período a 4 dias; período `SCHEDULED` criado a 3, 2 e 1 dia (idempotente — apenas um é criado); nenhum com `autoRenew = false` |

### TS-004-27 — `SCHEDULED → OPEN` no `startDate` (§4.6 SM)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a abertura automática |
| **Passos** | 1. Período `SCHEDULED` com `startDate` amanhã. 2. Executar o job. 3. Avançar o `Clock` para o `startDate`. 4. Executar o job |
| **Resultado esperado** | (2) permanece `SCHEDULED`; (4) passa a `OPEN`. A guarda "período anterior `CLOSED` ou inexistente" é verificada em `011` |

---

## 5. Testes de API

### TS-004-28 — Contrato dos endpoints da §14
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP de cada endpoint |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `Location` no `201`; toda resposta de erro em RFC 7807 com `code` `DEVTIME-XXXX`; `availableTransitions[]` presente (ME-06); OpenAPI gerado bate com o real |

### TS-004-29 — Prévia de períodos sem persistir (E-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a prévia é cálculo puro e coincide com o gerado |
| **Passos** | 1. Solicitar prévia com um conjunto de parâmetros. 2. Verificar o banco. 3. Criar e ativar o contrato com os mesmos parâmetros. 4. Comparar |
| **Resultado esperado** | (2) nenhuma escrita — nenhum contrato, período ou auditoria; (4) os períodos gerados são **idênticos** aos previstos, campo a campo. Divergência entre prévia e realidade destrói a confiança na tela de criação |

### TS-004-30 — Transições por endpoint de ação (ME-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `status` não muda por `PATCH` genérico |
| **Passos** | 1. `PATCH /contracts/{id}` com `{"status": "ACTIVE"}`. 2. `POST /contracts/{id}/activate` |
| **Resultado esperado** | (1) o campo é **ignorado** ou rejeitado como desconhecido; o status não muda e nenhum período é gerado; (2) executa com todas as guardas |

### TS-004-31 — Justificativa obrigatória no cancelamento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a exigência da §10 de `permissions.md` |
| **Passos** | 1. Cancelar sem justificativa. 2. Com justificativa de 3 caracteres. 3. Com justificativa válida |
| **Resultado esperado** | (1) e (2) `422`; (3) `200` e a justificativa persistida no `AuditLog` |

---

## 6. Testes de frontend

### TS-004-32 — `dt-period-preview` reativo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a atualização a cada alteração relevante |
| **Passos** | Alterar `startDate`, `billingDay`, `monthlyMinutes`, `endDate` e `type`; alterar um campo irrelevante (`notes`) |
| **Resultado esperado** | Nova prévia nos 5 primeiros; **nenhuma** chamada ao alterar `notes`; debounce impede rajada de requisições durante a digitação |

### TS-004-33 — Formulário condicional por tipo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a UI reflete INV-CTR-02/03 |
| **Passos** | Alternar entre `MONTHLY_HOURS` e `HOURLY_OPEN` |
| **Resultado esperado** | `monthlyMinutes`, rollover e overage visíveis e obrigatórios apenas em `MONTHLY_HOURS`; ocultos e limpos em `HOURLY_OPEN`; nenhum valor residual é enviado |

### TS-004-34 — Ações refletem `availableTransitions` e papel
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ME-06 e a nota ³ de `permissions.md` |
| **Passos** | Renderizar `dt-contract-actions` para cada estado × cada papel |
| **Resultado esperado** | Apenas as transições permitidas aparecem; `MANAGER` não vê "encerrar" nem "cancelar"; `MEMBER` e `VIEWER` não veem nenhuma ação de transição |

### TS-004-35 — Campos monetários por permissão
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `CONTRACT_VIEW_FINANCIAL` no cliente |
| **Passos** | Renderizar P14 como `MEMBER` e como `VIEWER` |
| **Resultado esperado** | `MEMBER` não vê `hourlyRate`, `overageRate` nem valor estimado; `VIEWER` vê. A ausência é verificada também na **resposta da API**, não apenas no DOM (IMP-06) |

### TS-004-36 — Mapeamento de erros `422` por campo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a legibilidade dos erros de negócio |
| **Passos** | Submeter payloads que disparem `DEVTIME-2202`, `2203`, `2204` e `2405` |
| **Resultado esperado** | Cada erro é exibido no campo correspondente, em pt-BR, sem jargão técnico e sem código bruto visível ao usuário |

---

## 7. Testes E2E

### TS-004-37 — Jornada completa do contrato
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário de ponta a ponta |
| **Pré-condição** | Cliente `ACTIVE` cadastrado |
| **Passos** | 1. Criar contrato em P15. 2. Conferir a prévia. 3. Salvar como `DRAFT`. 4. Ativar em P14. 5. Verificar o período `OPEN` na timeline. 6. Suspender e retomar. 7. Encerrar |
| **Resultado esperado** | Cada etapa reflete o estado correto na UI; a timeline mostra os períodos gerados; as ações disponíveis mudam a cada transição; nenhuma tela exibe dado desatualizado após a ação |

### TS-004-38 — Filtros preservados na URL
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a persistência de estado em P13 |
| **Passos** | Aplicar filtro de status e cliente, paginar, copiar a URL, abrir em outra aba |
| **Resultado esperado** | Estado idêntico ao original; nenhum filtro perdido no recarregamento |

---

## 8. Testes de performance

### TS-004-39 — `resolveOpenPeriod` com volume
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta da §20 no caminho mais quente do sistema |
| **Pré-condição** | 10.000 períodos no tenant, 200 contratos |
| **Passos** | 10.000 resoluções com datas aleatórias, medindo p95 |
| **Resultado esperado** | p95 < 50 ms; plano de execução usa `idx_periods_contract_dates`; **nenhum** *sequential scan*. Esta consulta ocorre em toda criação de work log — degradação aqui degrada o produto inteiro |

### TS-004-40 — Job em lote
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento com muitos contratos |
| **Pré-condição** | 5.000 contratos ativos distribuídos em 500 tenants |
| **Passos** | Executar `GeneratePeriodsJob` medindo duração e memória |
| **Resultado esperado** | Conclusão dentro da janela; processamento em lote sem carregar tudo em memória; `TenantContext` corretamente trocado a cada iteração e **limpo** ao final de cada uma |

### TS-004-41 — Listagem de contratos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta de listagem |
| **Passos** | 1.000 contratos, listagem com filtro e ordenação |
| **Resultado esperado** | p95 < 300 ms; projeção usada, nunca a entidade completa; nenhuma consulta N+1 ao carregar cliente e período corrente |

---

## 9. Testes de segurança

### TS-004-42 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 em todos os endpoints |
| **Passos** | Para cada endpoint da §14, acessar um recurso do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em **todos**, nunca `403`; nenhum vazamento por contagem, mensagem de erro ou tempo de resposta |

### TS-004-43 — `tenantId` da requisição é ignorado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-001 |
| **Passos** | Enviar `tenantId` de outro tenant no corpo e em header customizado |
| **Resultado esperado** | O valor é **ignorado**; o contrato é criado no tenant do token. Nenhum caminho aceita tenant da requisição |

### TS-004-44 — Matriz de permissões da feature
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel, executar e verificar |
| **Resultado esperado** | Conforme a §7 de `permissions.md`; `MANAGER` executa `DRAFT → ACTIVE` e `ACTIVE ↔ SUSPENDED` mas recebe `403 DEVTIME-1101` em `ENDED` e `CANCELLED` (nota ³); `MEMBER` só enxerga contratos vinculados (nota ²) |

### TS-004-45 — Imutabilidade de campos 🔒
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-011 |
| **Passos** | Tentar alterar `type` (após ativação), `sequence`, `contractedMinutes`, `hourlyRateSnapshot` e `startDate` de período |
| **Resultado esperado** | `422 DEVTIME-2003` em todos; os snapshots congelados na criação do período permanecem íntegros mesmo após alterar o contrato |

### TS-004-46 — Ausência de dado sensível em log
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a §28 da spec |
| **Passos** | Executar o fluxo completo capturando os logs |
| **Resultado esperado** | Nenhum `hourlyRate`, `overageRate` ou valor monetário em log; nenhuma stack trace ou nome de tabela em resposta de erro (EH-01) |

---

## 10. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-004-47 | Suíte temporal completa (`TS-004-01` a `TS-004-06`) | **Toda** alteração em `PeriodGenerator`, `ProrationCalculator` ou em qualquer código de calendário |
| TS-004-48 | Matriz de transições (`TS-004-10`) | Toda alteração em `ContractStateMachine` ou em `state-machines.md` §4.5 |
| TS-004-49 | Constraints de banco (`TS-004-14`, `TS-004-15`) | Toda migration que toque `contracts` ou `contract_periods` |
| TS-004-50 | Idempotência dos jobs (`TS-004-25`) | Toda alteração em job ou em agendamento |
| TS-004-51 | Isolamento (`TS-004-42`) | Todo endpoint novo, em qualquer feature |
| TS-004-52 | Prévia × geração real (`TS-004-29`) | Toda alteração no gerador ou na prévia — a divergência entre os dois é silenciosa e só aparece para o usuário |

**Política:** a suíte temporal é executada na íntegra em todo PR que toque esta feature, sem amostragem. Seu tempo de execução é o custo de não descobrir uma borda de calendário em produção, meses depois, num relatório já entregue ao cliente.

---

## 11. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-201 | TS-004-11 | AC-004-13 |
| RN-202 | TS-004-07, TS-004-11 | AC-004-14 |
| RN-203 | TS-004-11 | AC-004-15 |
| RN-204 | TS-004-11 | AC-004-16 |
| RN-205 | TS-004-22 | AC-004-19 |
| RN-206 | TS-004-18 | AC-004-20 |
| RN-207 | TS-004-16 | AC-004-17 |
| RN-208 | TS-004-17 | AC-004-18 |
| RN-209 | TS-004-12 | AC-004-04 |
| RN-210 | TS-004-08 | AC-004-08 |
| RN-211 | TS-004-01, TS-004-02, TS-004-03 | AC-004-01, AC-004-24 |
| RN-212 | TS-004-01, TS-004-06 | AC-004-02 |
| RN-213 | TS-004-25, TS-004-26 | AC-004-06 |
| RN-214 | TS-004-05, TS-004-20 | AC-004-07 |
| RN-216 | TS-004-06, TS-004-14 | AC-004-27, AC-004-31 |
| RN-217 | TS-004-04 | AC-004-03, AC-004-25 |
| RN-001 / RN-002 | TS-004-42, TS-004-43 | AC-004-28, AC-004-29 |
| RN-004 | TS-004-28 | AC-004-21 |
| RN-011 | TS-004-18, TS-004-45 | AC-004-20 |
| INV-CTR-01 | TS-004-09 | AC-004-05 |
| INV-CTR-02/03/04 | TS-004-07 | AC-004-14 |
| INV-CTR-06 | TS-004-12 | AC-004-04 |
| INV-CTR-08 | TS-004-22 | AC-004-19 |
| INV-PER-02 | TS-004-06, TS-004-14 | AC-004-31 |
| INV-PER-03 | TS-004-06, TS-004-19 | AC-004-27 |
| INV-PER-07 | TS-004-15 | AC-004-32 |
| §4.5 SM | TS-004-10, TS-004-30, TS-004-34 | AC-004-22, AC-004-23 |
| CE-06 | TS-004-23 | AC-004-26 |
| CE-ME-09 | TS-004-19 | AC-004-30 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz. Uma regra sem teste é uma regra que não existe (CA-01 de `business-rules.md`).

---

## 12. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `contract-normative-cases.csv` | As 5 linhas da tabela normativa da §6.2 | `TS-004-01` — fonte do `@CsvFileSource` |
| `contract-contiguity-matrix.csv` | 1.008 combinações: 366 `startDate` × 28 `billingDay`, amostradas | `TS-004-06` |
| `contract-proration-cases.csv` | Casos de rateio, incluindo bordas de arredondamento e o exemplo normativo de 1.703 min | `TS-004-04` |
| `contract-transition-matrix.csv` | As 25 células origem × destino com o resultado esperado | `TS-004-10` |
| `fixture-contract-monthly` | Contrato `MONTHLY_HOURS`, 2.400 min, `billingDay = 1`, `WARN`, `NONE` | Base da maioria dos testes |
| `fixture-contract-hourly-open` | Contrato `HOURLY_OPEN` sem saldo | `TS-004-08` |
| `fixture-contract-capped` | `CAPPED` com `rolloverCapMinutes = 300` | `TS-004-07` |
| `fixture-contract-migration` | `startDate` 6 meses no passado | `TS-004-23` |
| `fixture-tenant-b` | Segundo tenant com contratos espelhados | `TS-004-42`, `TS-004-43` |
| `fixture-clock-leap` | `Clock` fixo em 2024-02-28 | `TS-004-03` |

**Regra de fixture:** nenhuma fixture usa data relativa ao momento da execução. Todas as datas são absolutas e acompanhadas de um `Clock` fixo — caso contrário a suíte muda de significado a cada dia e falha de forma sazonal.

---

## 13. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | As suítes `TS-004-01` a `TS-004-06` e `TS-004-10` foram escritas e revisadas **antes** da implementação (SQ-02) |
| CC-02 | Os 5 cenários normativos são reproduzidos com igualdade exata nas 15 datas |
| CC-03 | O rateio produz exatamente 1.703 min no exemplo normativo |
| CC-04 | A matriz de contiguidade passa nas 1.000+ combinações, sem exceção |
| CC-05 | As 25 células da matriz de transições possuem teste de aceitação ou de rejeição |
| CC-06 | A constraint `EXCLUDE` é comprovada com `INSERT` direto no banco |
| CC-07 | Cobertura ≥ 90% em `PeriodGenerator`, `ProrationCalculator` e validators |
| CC-08 | `resolveOpenPeriod` atinge p95 < 50 ms com 10.000 períodos |
| CC-09 | Todos os endpoints passam na suíte de isolamento com `404` |
| CC-10 | Prévia e geração real coincidem campo a campo |
