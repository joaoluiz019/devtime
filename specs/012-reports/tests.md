# 012 — Reports & Export · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-012-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-701: serve período fechado do snapshot ignorando alterações no banco`.

> **Uma suíte escrita antes do código:** `TS-012-01` (fonte de dados). O modo de falha de R-02 é silencioso — o relatório funciona, os números parecem corretos, e o defeito só aparece quando alguém altera um dado e o "documento definitivo" muda. Escrita depois, a suíte passaria contra a implementação errada.
>
> **Dependência temporal declarada:** o fechamento de período é entregue em `011` na sprint **S10**, posterior a esta. Em S9, `TS-012-01` roda contra snapshots de fixture. Sua reexecução contra o fechamento real é `TS-012-38`, executada em S10. Marcar RN-701 como provada antes disso seria declarar verificado algo que nunca foi exercitado com dado real.

**Relógio:** todo teste de expiração injeta um `Clock` fixo. Os limiares de 15 minutos e 7 dias são inverificáveis com relógio real.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| **Fonte de dados** | Snapshot × cálculo ao vivo | JUnit + Testcontainers | Todos os estados de período |
| **Determinismo** | PDF de período fechado | JUnit + comparação de bytes | 100% reprodutível |
| Unitário | `ReportGroupingPolicy`, `ReportScopePolicy`, `DurationFormatter`, `MoneyFormatter`, `FormulaInjectionSanitizer`, `ReportPeriodValidator` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + renderers + PostgreSQL + storage | Testcontainers + storage local | Os 5 tipos × 3 formatos |
| **Memória** | Exportação de 50.000 linhas | JUnit + medição de heap | Sem esgotamento |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Os 10 endpoints |
| Isolamento | Tenancy + escopo de exportação | Suíte dedicada | Todos os endpoints |
| Frontend | Stores, aviso de parcial, *polling* | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Gerar, exportar, acompanhar, baixar | Playwright | Jornada completa |
| Performance | Geração por tipo e formato | k6 | Metas da §20 |
| Segurança | Fórmula, XSS, escopo, expiração | JUnit + scripts + abertura real de planilha | Vetores da §19 |
| **Visual** | Qualidade do PDF | **Avaliação por pessoa externa** | Critério de RP-04 |
| Regressão | Fonte e determinismo | CI | 100% verde |

---

## 3. Testes de fonte de dados

### TS-012-01 — Período fechado servido do snapshot (RN-701)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o relatório de período fechado **ignora** o estado atual do banco |
| **Pré-condição** | Período `CLOSED` com snapshot; work logs, categorias, cliente e contrato conhecidos |
| **Passos** | 1. Gerar o relatório e registrar a saída. 2. Alterar no banco: nome do cliente, nome de categoria, título de ticket e nome de usuário. 3. Gerar novamente. 4. Comparar |
| **Resultado esperado** | As duas saídas são **idênticas** nos rótulos; nenhuma alteração posterior aparece; o relatório é marcado como definitivo. Inspeção confirma que a leitura vem de `SnapshotService.getForReport` |

### TS-012-02 — Matriz de resolução de fonte (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão para cada estado de período |
| **Passos** | Gerar o relatório para períodos em `SCHEDULED`, `OPEN`, `CLOSING`, `CLOSED` e `REOPENED` |
| **Resultado esperado** | `CLOSED` → snapshot, definitivo; `OPEN` → ao vivo, **parcial**; `REOPENED` → ao vivo, **parcial com aviso de reabertura**; `SCHEDULED` → indisponível; `CLOSING` → indisponível temporariamente |

### TS-012-03 — Estrutura idêntica entre as duas fontes
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `SnapshotReportMapper` e `LiveReportMapper` produzem a mesma forma |
| **Passos** | 1. Gerar o relatório de um período aberto. 2. Fechar o período. 3. Gerar novamente. 4. Comparar as **estruturas** das duas respostas |
| **Resultado esperado** | Mesmos campos, mesma hierarquia, mesmos tipos; apenas `isPartial` difere. O frontend não precisa saber a origem do dado |

### TS-012-04 — Período reaberto e refechado (CE-R-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o relatório passa a refletir o novo snapshot |
| **Passos** | 1. Fechar e gerar. 2. Reabrir, corrigir um registro, refechar. 3. Gerar novamente. 4. Verificar os snapshots no banco |
| **Resultado esperado** | (3) reflete o **novo** snapshot com a correção; (4) **ambos** os snapshots existem, mas apenas o mais recente é servido |

---

## 4. Testes de determinismo

### TS-012-05 — PDF determinístico (RN-708)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar R-03 mitigado |
| **Pré-condição** | Período `CLOSED` com 500 registros de datas, tickets e horários variados |
| **Passos** | 1. Gerar o PDF. 2. Aguardar e gerar novamente. 3. Gerar em outra instância. 4. Comparar os bytes, mascarando a região do carimbo de emissão |
| **Resultado esperado** | Bytes **idênticos** nas três gerações, exceto o carimbo. Divergência indica ordenação não determinística, metadado variável no PDF ou fonte de dado instável |

### TS-012-06 — Ordenação normativa (§6.3, CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a base do determinismo |
| **Pré-condição** | Registros com datas repetidas, tickets diferentes e horários variados |
| **Passos** | 1. Gerar o relatório 10 vezes. 2. Verificar a ordem. 3. Tentar passar um parâmetro de ordenação |
| **Resultado esperado** | Ordem idêntica nas 10 gerações: data crescente, `ticket.key`, `startedAt`; nenhum parâmetro altera a ordenação |

---

## 5. Testes unitários

### TS-012-07 — `ReportScopePolicy` e CE-P-10 (RN-711)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a restrição mais dura da feature |
| **Passos** | Para cada papel, tentar cada combinação de filtro: `myWorkLogs`, por cliente, por contrato, por usuário, por categoria, sem filtro |
| **Resultado esperado** | `MEMBER`: **apenas** `myWorkLogs` permitido; todos os outros `403 DEVTIME-1101`. Papéis com `REPORT_VIEW_ANY`: todos permitidos. `VIEWER` exporta consolidado |

### TS-012-08 — Escopo verificado antes da existência (§6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o código de erro não vaza existência |
| **Passos** | `MEMBER` solicita relatório de: (a) contrato existente ao qual não tem acesso; (b) contrato inexistente; (c) contrato de outro tenant |
| **Resultado esperado** | (a) `403` por escopo — **não** `404`; (b) e (c) `404`. A ordem impede inferir a existência de um contrato pelo código de erro |

### TS-012-09 — `ReportGroupingPolicy` (§6.3)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as combinações válidas |
| **Passos** | Para cada um dos 5 tipos × 6 agrupamentos, verificar a aceitação |
| **Resultado esperado** | Conforme a tabela da §6.3; combinações inválidas retornam `DEVTIME-2000`; `por semana` só em folha de horas e produtividade |

### TS-012-10 — `DurationFormatter` (RN-710)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas representações |
| **Passos** | Formatar 0, 1, 59, 60, 90, 149×60+30 e 8.970 minutos |
| **Resultado esperado** | `HH:MM` correto em todos (`149:30` para 8.970); decimal com 2 casas (`149,50`); a soma das decimais reproduz o total em minutos |

### TS-012-11 — `MoneyFormatter` (RN-709)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `HALF_UP` e a moeda do contrato |
| **Passos** | Formatar valores com terceira casa 4, 5 e 6; moedas `BRL`, `USD` e `EUR`; contrato sem `hourlyRate` |
| **Resultado esperado** | Terceira casa 5 arredonda **para cima** (`HALF_UP`); símbolo conforme a moeda do contrato; sem `hourlyRate`, retorna vazio para omissão da coluna |

### TS-012-12 — `FormulaInjectionSanitizer` (SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a neutralização dos quatro caracteres |
| **Passos** | Sanitizar células iniciando com `=`, `+`, `-`, `@`, tabulação e retorno de carro; e uma célula normal |
| **Resultado esperado** | Os quatro caracteres de fórmula neutralizados com prefixo; caracteres de controle removidos; célula normal inalterada |

### TS-012-13 — `ReportPeriodValidator` (RN-705)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limite exato |
| **Passos** | Intervalos de 1, 365, 366 e 367 dias; `to` anterior a `from` |
| **Resultado esperado** | Até 366 aceito; 367 → `DEVTIME-3001`; `to < from` → `400` |

---

## 6. Testes de integração

### TS-012-14 — Os cinco tipos de relatório
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a geração de cada tipo |
| **Passos** | Gerar os 5 tipos com dados completos |
| **Resultado esperado** | Estrutura conforme §6 e §7 de `reports.md`; cabeçalho presente em todos (RN-703); `issueId` único por emissão |

### TS-012-15 — Marcação de parcial nas três saídas (RN-702, CP-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-RPT-03 |
| **Passos** | Para período `OPEN` e `REOPENED`: gerar em tela, PDF e XLSX; inspecionar o conteúdo de cada saída |
| **Resultado esperado** | Indicação presente nas **três**; no PDF é visualmente proeminente, não rodapé; no XLSX está no cabeçalho da planilha; `REOPENED` traz também o aviso de reabertura |

### TS-012-16 — Exclusão de registros removidos (RN-704)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-RPT-02 |
| **Passos** | Período aberto com 10 registros, 3 excluídos logicamente; gerar em todos os formatos |
| **Resultado esperado** | 7 linhas nos três formatos; totais considerando apenas 7 |

### TS-012-17 — Limiar de assincronia (RN-706)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão no limiar exato |
| **Passos** | Exportar com 4.999, 5.000 e 5.001 linhas |
| **Resultado esperado** | 4.999 e 5.000 → `201` síncrono; 5.001 → `202` assíncrono. O limiar é "acima de 5.000" |

### TS-012-18 — Contagem após os filtros (§6.2, passo 10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o limiar é sobre o resultado filtrado |
| **Pré-condição** | Tenant com 100.000 registros |
| **Passos** | Exportar relatório de um único dia, com 20 linhas |
| **Resultado esperado** | `201` síncrono. O volume do tenant não influencia a decisão |

### TS-012-19 — `ReportExecution` com filtros (RN-707)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-RPT-05 |
| **Passos** | Exportar com filtros compostos; inspecionar `report_executions` e o `AuditLog` |
| **Resultado esperado** | Registro com tipo, formato, **filtros completos**, contagem e solicitante; `AuditLog` com os mesmos filtros |

### TS-012-20 — Ciclo de vida da exportação (§4.10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar todas as transições |
| **Passos** | 1. `QUEUED` → cancelar. 2. `QUEUED` → `PROCESSING` → `COMPLETED`. 3. `PROCESSING` → `FAILED` → `QUEUED` → `FAILED`. 4. Cancelar em `PROCESSING`. 5. Expirar após 7 dias |
| **Resultado esperado** | (1) cancelado; (3) permanece `FAILED` com `attemptCount = 2`, sem terceira tentativa; (4) `DEVTIME-3004`; (5) `EXPIRED` |

### TS-012-21 — URL assinada e expiração (RN-712)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-RPT-06 |
| **Pré-condição** | `Clock` fixo |
| **Passos** | 1. Baixar imediatamente. 2. Aos 14 minutos. 3. Aos 16 minutos. 4. Solicitar novamente o download |
| **Resultado esperado** | (1) e (2) sucesso; (3) recusado pelo storage; (4) **nova URL** gerada e o arquivo **não** é regerado — verificado pelo `storageKey` inalterado |

### TS-012-22 — Remoção física na expiração (SG-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-12 |
| **Passos** | 1. Exportação concluída há 8 dias. 2. Executar `ExportExpiryJob`. 3. Tentar acessar a chave de armazenamento diretamente |
| **Resultado esperado** | Status `EXPIRED`; **binário ausente** do storage; acesso direto falha. Marcar o status sem remover o arquivo deixaria dado pessoal acessível |

### TS-012-23 — Contrato sem valor hora (CE-R-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a omissão sem erro |
| **Passos** | Gerar os 5 tipos para contrato com `hourlyRate` nulo, nos três formatos |
| **Resultado esperado** | Nenhuma coluna monetária; nenhum erro; nenhuma célula vazia com cabeçalho monetário órfão |

### TS-012-24 — Relatório sem registros (CE-R-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o caso vazio |
| **Passos** | Gerar com filtro sem resultado, nos três formatos |
| **Resultado esperado** | `200`; totais zerados; mensagem explícita; PDF válido e abrível; XLSX com cabeçalho e sem linhas |

---

## 7. Testes de memória e performance

### TS-012-25 — Exportação de 50.000 linhas sem esgotar memória (CP-13, R-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a escrita é em **fluxo** |
| **Pré-condição** | Relatório com 50.000 linhas; heap limitado deliberadamente |
| **Passos** | Exportar em XLSX e em PDF, medindo o consumo de heap ao longo da geração |
| **Resultado esperado** | Consumo **estável**, não crescente com o número de linhas; nenhuma `OutOfMemoryError`; conclusão em menos de 5 min. **Este teste falha contra uma implementação que constrói o documento em memória** — é o seu propósito |

### TS-012-26 — Metas de geração (§20)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as metas por tipo |
| **Passos** | Medir p95 de: período fechado, período aberto com 10.000 registros, folha de 366 dias, resumo de cliente com 50 contratos, produtividade com 20 membros |
| **Resultado esperado** | Fechado < 500 ms; aberto < 1,5 s; folha < 3 s; resumo < 2 s; produtividade < 2 s |

### TS-012-27 — PDF de 1.000 linhas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta de renderização |
| **Passos** | Gerar PDF de 1.000 linhas medindo duração |
| **Resultado esperado** | < 5 s; paginação correta; cabeçalho repetido nas páginas |

---

## 8. Testes de API

### TS-012-28 — Contrato dos 10 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `201` com URL, `202` com `pollUrl`; erros em RFC 7807; OpenAPI bate com o real |

### TS-012-29 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel |
| **Resultado esperado** | `client-summary` e `productivity` exigem `REPORT_VIEW_ANY` — `MEMBER` recebe `403`; `VIEWER` exporta; `MEMBER` exporta apenas `myWorkLogs` |

---

## 9. Testes de frontend

### TS-012-30 — Aviso de parcial proeminente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-03 na UI |
| **Passos** | Renderizar relatórios de período `OPEN`, `REOPENED` e `CLOSED` |
| **Resultado esperado** | Aviso destacado nos dois primeiros, com o motivo; `REOPENED` mostra o `reopenCount`; `CLOSED` mostra "definitivo"; o aviso não é uma nota discreta |

### TS-012-31 — *Polling* limitado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia da §21.3 |
| **Passos** | Solicitar exportação assíncrona e observar as requisições por 6 minutos |
| **Resultado esperado** | Intervalo de 3 s; interrompido ao concluir; interrompido também aos 5 minutos mesmo sem conclusão; a notificação de `013` chega independentemente |

### TS-012-32 — Tipos indisponíveis explicados
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ergonomia da restrição |
| **Passos** | Renderizar `dt-report-type-selector` como `MEMBER` |
| **Resultado esperado** | Tipos que exigem `REPORT_VIEW_ANY` desabilitados **com explicação**, não simplesmente ausentes — o usuário entende por que não pode |

### TS-012-33 — Colunas monetárias ocultas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `hasPermission` na visualização |
| **Passos** | Renderizar como `MEMBER` e como `VIEWER` |
| **Resultado esperado** | `MEMBER` sem colunas monetárias, ausência confirmada também na **resposta da API** (IMP-06); `VIEWER` com colunas |

---

## 10. Testes E2E

### TS-012-34 — Jornada completa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário |
| **Passos** | 1. Abrir P24. 2. Selecionar tipo e filtros. 3. Visualizar. 4. Trocar o agrupamento. 5. Exportar em PDF. 6. Baixar. 7. Exportar 20.000 linhas em XLSX. 8. Acompanhar até concluir. 9. Baixar |
| **Resultado esperado** | Cada etapa reflete o estado correto; (5) download imediato; (7) `202` com acompanhamento; (8) notificação ao concluir |

---

## 11. Testes de segurança

### TS-012-35 — Injeção de fórmula com abertura real (SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a neutralização de forma verificável |
| **Passos** | 1. Registros com descrições iniciando em `=`, `+`, `-` e `@`. 2. Exportar em CSV e XLSX. 3. **Abrir os arquivos em um leitor de planilha real** e inspecionar as células |
| **Resultado esperado** | Nenhuma fórmula avaliada; conteúdo como texto literal nos quatro casos, nos dois formatos. A abertura real é necessária: um teste que apenas inspeciona o texto do arquivo não prova que o leitor não interpretará |

### TS-012-36 — XSS no PDF e isolamento (SG-06, SG-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape e o isolamento |
| **Passos** | 1. Descrição com `<script>` e entidades HTML; exportar em PDF. 2. Para cada um dos 10 endpoints, acessar recurso do tenant B autenticado no tenant A |
| **Resultado esperado** | (1) texto literal, nenhuma marcação interpretada; (2) `404 DEVTIME-2002` em todos, nunca `403` |

### TS-012-37 — Monetários e exportações de terceiros (SG-04, SG-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas restrições no **arquivo** e na listagem |
| **Passos** | 1. `MEMBER` exporta; inspecionar o **conteúdo** do arquivo. 2. Listar exportações com uma de outro usuário existente. 3. Acessar a de terceiro por id |
| **Resultado esperado** | (1) nenhuma coluna monetária no arquivo — não apenas na tela; (2) exportação de terceiro ausente da listagem; (3) `404` |

---

## 12. Teste visual

### TS-012-38v — Avaliação visual do PDF (RP-04, DoD-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a qualidade percebida — o critério que nenhum teste automatizado cobre |
| **Pré-condição** | PDF de um período fechado real, com cabeçalho, logo, 200 linhas agrupadas e totais |
| **Passos** | 1. Gerar o PDF. 2. Apresentá-lo a **ao menos duas pessoas externas ao time de desenvolvimento**. 3. Perguntar se elas enviariam esse documento a um cliente próprio |
| **Resultado esperado** | Aprovação de todos os avaliadores. Reprovação **bloqueia** o DoD e aciona a mitigação de R-01 |

> Este é o único teste do projeto sem verificação automatizada, e é deliberado. RP-04 é um risco de **percepção**: um PDF pode estar tecnicamente perfeito e parecer um dump de planilha. Nenhuma assertion detecta isso.

---

## 13. Teste diferido para S10

### TS-012-38 — Reexecução contra o fechamento real de `011`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-701 com dado produzido pelo fechamento real, não por fixture |
| **Pré-condição** | `011-bank-hours` com o fechamento entregue (sprint **S10**) |
| **Passos** | Reexecutar `TS-012-01`, `TS-012-02`, `TS-012-04` e `TS-012-05` contra períodos fechados pelo `PeriodClosingService` real |
| **Resultado esperado** | Todos verdes. **Até esta execução, RN-701 não é considerada provada** — um teste de snapshot que nunca viu um fechamento real prova apenas que o mapper lê JSON |

---

## 14. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-012-39 | Fonte de dados (`TS-012-01`, `TS-012-02`) | **Toda** alteração em `011-bank-hours`, no formato do snapshot ou em `ReportDataResolver` |
| TS-012-40 | Determinismo do PDF (`TS-012-05`, `TS-012-06`) | Toda alteração em `PdfRenderer`, na ordenação, na biblioteca de PDF ou no formato do snapshot |
| TS-012-41 | Memória (`TS-012-25`) | Toda alteração em qualquer renderer ou na biblioteca de planilha |
| TS-012-42 | Injeção de fórmula (`TS-012-35`) | Toda alteração em `CsvRenderer`, `XlsxRenderer` ou no sanitizador |
| TS-012-43 | Escopo (`TS-012-07`, `TS-012-08`) | Toda alteração em `permissions.md` §7/§9 ou em `ReportScopePolicy` |
| TS-012-44 | Isolamento (`TS-012-36`) | Todo endpoint novo |
| TS-012-45 | Avaliação visual (`TS-012-38v`) | Toda alteração no layout do PDF ou no `design-system.md` |

**Política:** `TS-012-01` e `TS-012-05` rodam em todo PR que toque esta feature **ou** `011-bank-hours`. Uma alteração no formato do payload do snapshot que não fosse refletida aqui produziria relatórios errados de períodos já entregues — o cenário mais grave possível para o produto.

**Regra adicional:** `TS-012-40` roda também quando a versão da biblioteca de PDF muda. Uma alteração em metadados embutidos (data de criação, produtor) quebraria o determinismo sem que nenhum código do projeto tivesse sido tocado.

---

## 15. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-701 | TS-012-01, TS-012-02, TS-012-04, TS-012-38 | AC-012-01, AC-012-23, AC-012-24, AC-012-44 |
| RN-702 | TS-012-02, TS-012-15, TS-012-30 | AC-012-03, AC-012-33 |
| RN-703 | TS-012-14 | AC-012-04 |
| RN-704 | TS-012-16 | AC-012-31 |
| RN-705 | TS-012-13 | AC-012-16, AC-012-32 |
| RN-706 | TS-012-17, TS-012-18 | AC-012-10, AC-012-11, AC-012-27 a AC-012-29 |
| RN-707 | TS-012-19 | AC-012-15 |
| RN-708 | TS-012-05, TS-012-06 | AC-012-02, AC-012-09 |
| RN-709 | TS-012-11, TS-012-23 | AC-012-13, AC-012-25 |
| RN-710 | TS-012-10 | AC-012-12 |
| RN-711 | TS-012-07, TS-012-08, TS-012-29, TS-012-37 | AC-012-21, AC-012-22, AC-012-40 |
| RN-712 | TS-012-21 | AC-012-14, AC-012-18, AC-012-45 |
| RN-002 | TS-012-36 | AC-012-37 |
| RN-006 | TS-012-19 | AC-012-15, AC-012-43 |
| INV-RPT-01 | TS-012-01, TS-012-05 | AC-012-02, AC-012-23 |
| INV-RPT-02 | TS-012-16 | AC-012-31 |
| INV-RPT-03 | TS-012-15 | AC-012-03, AC-012-33 |
| INV-RPT-04 | TS-012-07, TS-012-37 | AC-012-22, AC-012-40 |
| INV-RPT-05 | TS-012-19 | AC-012-15 |
| INV-RPT-06 | TS-012-21 | AC-012-14, AC-012-45 |
| §6.1 fonte | TS-012-02, TS-012-03 | AC-012-01, AC-012-03 |
| §6.2 ordem | TS-012-08, TS-012-18 | AC-012-22 |
| §6.3 agrupamento e ordenação | TS-012-06, TS-012-09 | AC-012-09, AC-012-20 |
| §4.10 SM | TS-012-20 | AC-012-17 a AC-012-19, AC-012-34, AC-012-46 |
| CE-P-10 | TS-012-07 | AC-012-22 |
| SG-05 | TS-012-12, TS-012-35 | AC-012-38 |
| SG-06 | TS-012-36 | AC-012-39 |
| SG-04 / SG-07 | TS-012-37 | AC-012-40, AC-012-41 |
| SG-09 | TS-012-22 | AC-012-42 |
| RP-04 | TS-012-27, TS-012-38v | — |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 16. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `fixture-period-closed-snapshot` | Período `CLOSED` com snapshot e dados conhecidos | `TS-012-01`, `TS-012-05` |
| `fixture-period-all-states` | Períodos em `SCHEDULED`, `OPEN`, `CLOSING`, `CLOSED`, `REOPENED` | `TS-012-02` |
| `fixture-period-reclosed` | Período reaberto e refechado, com dois snapshots | `TS-012-04` |
| `report-scope-matrix.csv` | Papel × combinação de filtro × resultado esperado | `TS-012-07` |
| `report-grouping-matrix.csv` | Os 5 tipos × 6 agrupamentos × validade | `TS-012-09` |
| `duration-format-cases.csv` | Minutos e as duas representações esperadas | `TS-012-10` |
| `money-format-cases.csv` | Valores com terceira casa 4, 5 e 6; três moedas | `TS-012-11` |
| `formula-injection-payloads.csv` | Células iniciando com `=`, `+`, `-`, `@` e caracteres de controle | `TS-012-12`, `TS-012-35` |
| `fixture-report-500-rows` | Período fechado com 500 registros variados | `TS-012-05`, `TS-012-06` |
| `fixture-report-50k-rows` | Relatório com 50.000 linhas | `TS-012-25` |
| `fixture-contract-no-rate` | Contrato com `hourlyRate` nulo | `TS-012-23` |
| `fixture-client-50-contracts` | Cliente com 50 contratos | `TS-012-26` |
| `fixture-tenant-100k-logs` | Tenant com 100.000 registros | `TS-012-18` |
| `fixture-clock-expiry` | `Clock` fixo em 14 min, 16 min, 7 e 8 dias | `TS-012-21`, `TS-012-22` |
| `fixture-tenant-b` | Segundo tenant com relatórios espelhados | `TS-012-36` |

**Regras de fixture:**
- `fixture-period-closed-snapshot` precisa ter os rótulos **alteráveis** no banco após o fechamento — é o que permite `TS-012-01` provar que o snapshot é a fonte.
- `formula-injection-payloads.csv` é o mesmo arquivo usado pelo teste de abertura real de planilha; qualquer payload novo descoberto entra aqui.
- `fixture-report-50k-rows` é gerada por `COPY` em massa.

---

## 17. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-012-01` foi escrita **antes** de `ReportDataResolver` |
| CC-02 | Período fechado comprovadamente servido do snapshot, ignorando alterações no banco |
| CC-03 | As duas fontes produzem a **mesma** estrutura de resposta |
| CC-04 | PDF determinístico provado por comparação de bytes em três gerações |
| CC-05 | Ordenação normativa idêntica em 10 gerações, sem parâmetro que a altere |
| CC-06 | Marcação de parcial presente nas **três** saídas, proeminente no PDF |
| CC-07 | CE-P-10 provado para `MEMBER` em **todas** as combinações de filtro |
| CC-08 | Escopo verificado antes da existência, sem vazar por código de erro |
| CC-09 | Duas colunas de duração no XLSX, com a decimal comprovadamente somável |
| CC-10 | `HALF_UP` provado com terceira casa 5 |
| CC-11 | Neutralização de fórmula provada com **abertura real** de planilha |
| CC-12 | 50.000 linhas exportadas com heap estável, sem `OutOfMemoryError` |
| CC-13 | Limiar de 5.000 provado nos três pontos (4.999, 5.000, 5.001) |
| CC-14 | URL expira em 15 min; nova solicitação não regera o arquivo |
| CC-15 | Binário **removido** do storage na expiração, comprovado por acesso direto |
| CC-16 | Nenhuma coluna monetária no **arquivo** para quem não tem permissão |
| CC-17 | Cobertura ≥ 90% em services, renderers e policies |
| CC-18 | Os 10 endpoints passam na suíte de isolamento com `404` |
| CC-19 | **Avaliação visual do PDF aprovada por ao menos duas pessoas externas** (RP-04) |
| CC-20 | `TS-012-38` reexecutado e verde em S10, contra o fechamento real de `011` |
