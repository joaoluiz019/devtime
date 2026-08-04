# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) (RE-04).
Versionamento conforme [Semantic Versioning](https://semver.org/lang/pt-BR/); antes do lançamento a
versão permanece `0.x.y` (VR-04).

## [Não publicado]

### Adicionado

**Sprint S12 — fechamento das pendências de backend (transversal)**

Esta sprint não entrega feature nova: ela fecha as pendências que cada sprint anterior registrou
como "depende de uma feature que ainda não existe". Todas as dependências passaram a existir, e o
que restava eram pontos de aplicação marcados no código esperando a fonte.

- **`CategoryService.getAllForReport` e `TagService.getAllForReport`** (OB-04 de `005`, §22 de
  `006`), consumidas por `010` (CX-16) e `012` (CX-04). A decisão que faltava era como ler registro
  excluído **sem contornar o filtro de tenant**: SQL nativo escaparia dos dois cortes e exigiria
  `tenant_id = ?` à mão, que BR-046 proíbe. A resposta é um segundo mapeamento `@Immutable` sobre a
  mesma tabela — `CategoryHistory` e `TagHistory` —, que abre mão do `@SQLRestriction` e **preserva**
  o `@Filter`. As duas classes são isentas de BR-029 na suíte ArchUnit, com a mesma explicitação de
  `AuditLog`: a ausência da anotação é o contrato da classe, não um esquecimento.
- **RN-505 completa:** a migração de work logs na exclusão de categoria (passo 6 da §6.1) e o
  `DEVTIME-2603` do passo 4, que nunca chegava a ser lançado. `UPDATE` em lote sem carregar
  entidades, e a migração inclui registros de período fechado — eles estão travados contra edição
  pelo usuário (RN-241), não contra manutenção de catálogo, e o número do fechamento não muda porque
  categoria não entra em fórmula de saldo.
- **`RolloverExpiryJob` (RN-230) e `AutoClosePeriodJob` (CE-ME-02)**, desbloqueados pelos jobs de
  geração de período de `004`. A expiração é convergente por consulta — a presença do ajuste
  automático **é** a `dedupeKey` de §22.4 — e não age em período fechado (CX-19, OB-07).
- **RN-229 deixou de ser parcial:** o fechamento **cria** o período seguinte quando ele não existe,
  pelo mesmo `PeriodMaterializer` da ativação e da renovação (CA-01), apenas para contrato `ACTIVE` —
  sem essa guarda, cada fechamento de contrato encerrado geraria o período que o próximo fecharia.
- **`POST /contracts/{id}/duplicate`**, **guarda de cronômetro ativo em `suspend`/`end`**
  (`DEVTIME-2212`, pela mesma `PeriodActiveTimerSource` do fechamento) e **`activeTimer` em
  `GET /auth/me`** (§5.10), que era a última pendência da nota ⁷.
- **Work logs na linha do tempo do ticket**, com o escopo de dados de `MEMBER` aplicado **na
  consulta** (§9 de permissions.md, IMP-02) — filtrar depois de carregar vazaria pela paginação
  exatamente o que a restrição protege.
- **`DenormalizationReconcileJob`**, o job compartilhado que `003`, `006`, `007` e `011` previam cada
  uma na sua §22.4. Um agendamento, quatro reconciliadores registrados por interface em `shared`, e
  cada um falhando sozinho (JB-04). Divergência corrigida sai em `WARN`: ela significa que um
  incremento transacional se perdeu, e corrigir em silêncio esconderia o defeito de origem.
- **A purga de organização passou a alcançar as contas** (§19.1 de `specs/001`): quem não participa
  de nenhuma outra organização é anonimizado — e-mail em domínio não roteável, nome substituído,
  `passwordHash` descartado —, e não excluído, porque a conta é referenciada por trilha de auditoria
  e por snapshot de outros tenants.

**Defeito estrutural corrigido:** `period_adjustments.applied_by` era `NOT NULL` desde `V018`, sob o
pressuposto de que todo ajuste tem autor humano. RN-230 tem um ajuste sem autor, e o job de expiração
teria falhado na primeira execução, em produção, contra uma constraint. `V034` corrige de forma
aditiva. Inventar um usuário de sistema foi rejeitado: uma linha em `users` que ninguém autentica
apareceria em toda listagem de membros, e "quem aplicou este ajuste?" passaria a ter resposta falsa
em vez de nenhuma.

**Lacuna de documentação reportada e resolvida:** RN-230 diz que o saldo transportado "expira após
`rolloverExpiryPeriods` períodos", mas **nenhum documento define como a idade do saldo é
rastreada** — `contract_periods` não distingue, dentro de um saldo, o que veio do ciclo passado do
que veio de três atrás, e criar uma coluna seria decidir modelo de dados (IA-01). A leitura
implementada está isolada em `RolloverExpiryPolicy`, para que ganhar definição normativa signifique
mudar um arquivo.

**Divergência entre documentos, não resolvida — decisão pendente do produto:** `users.md` §6.4 exige
que `GET /tenant/export` (E-06, LGPD) use "o mesmo mecanismo de `reports.md`", enquanto §22.2 de
`specs/012` determina que `012` é folha no grafo e **não publica nada**. As duas afirmações não podem
valer juntas, e satisfazer uma exige violar a outra ou construir um segundo mecanismo de execução
assíncrona — que é exatamente o que §6.4 existe para evitar. **`GET /tenant/export` permanece não
implementado**, agora por conflito documental e não por dependência ausente.

**Sprint S9 — `012-reports` (backend)**

O relatório é o **entregável** do produto: todo o resto existe para produzi-lo. É o documento que o
freelancer anexa ao e-mail de cobrança e que sustenta a conversa quando o cliente discorda do número.

- Os cinco relatórios de §6 e §7 de `reports.md` — período de contrato, resumo por cliente, folha de
  horas, detalhamento por ticket e produtividade — e as cinco rotas de exportação de §8.
- **`ReportDataResolver` é o único ponto de decisão entre snapshot e cálculo ao vivo** (RN-701,
  RN-702). A suíte que o precede (T-012-04) foi escrita **antes** do código e não afirma apenas o que
  o resolvedor devolve: ela afirma **quem ele não chamou**. Com período fechado, `BalanceService`
  nunca é tocado. O modo de falha de R-02 é silencioso — um caminho que sirva período fechado do
  banco funciona, devolve números que parecem certos, e só se revela quando alguém altera um dado e o
  "documento definitivo" muda entre duas gerações.
- **`SnapshotReportMapper` e `LiveReportMapper` devolvem o mesmo tipo** (`ReportBody`). A simetria
  exigida por §34 deixou de ser convenção e passou a ser propriedade do compilador; o frontend e os
  três renderers tratam um formato só, e a diferença entre definitivo e parcial cabe em dois campos.
- **A ordenação é normativa e não configurável** (CP-05). Ela vem da consulta e o agrupamento não a
  toca — nem para reaplicar o mesmo critério. Sem ordem total, RN-708 é inverificável.
- **`MEMBER` exporta apenas os próprios registros** (CE-P-10), e o escopo é verificado **antes** da
  existência do recurso: confirmar primeiro que o contrato existe vazaria, pelo código de erro, que
  ele existe. A restrição vale também no snapshot — uma garantia de isolamento que se dissolvesse ao
  fechar o período seria uma regressão no exato momento em que o documento passa a circular.
- **Neutralização de fórmula em CSV e XLSX** (SG-05). A vítima não é o usuário do sistema: uma
  descrição `=SUM(...)` é texto inofensivo no banco e vira execução de código no Excel de um
  terceiro que nunca teve conta no DevTime.
- **Duas colunas de duração no XLSX** (RN-710): `HH:MM` como texto, para conferência visual, e horas
  decimais como número de verdade, para somar. O total usa `SUBTOTAL`, respondendo aos filtros que o
  próprio cliente aplicar.
- **Limiar de 5.000 linhas é "acima de", não "a partir de"** (CX-10, CX-11). A contagem é do
  resultado filtrado e acontece **antes** de qualquer linha ser materializada — contar carregando
  anularia a proteção no exato passo que existe para evitá-la.
- **`ExportExpiryJob` remove o binário**, não apenas marca `EXPIRED` (SG-09). E remove **antes** de o
  registro perder a chave: a ordem inversa deixaria o objeto no storage sem nada que o aponte.
- Migrations `V032` e `V033` (tabela e índices), com os `CHECK` de duas tentativas e de
  `storage_key` obrigatório em `COMPLETED` — a garantia estrutural que um defeito de contador esbarra
  no banco, não num `if` que alguém pode remover.
- **`EXPORT_COMPLETED` e `EXPORT_FAILED` ganharam produtor**, fechando a última pendência da nota ¹¹.

**Interfaces publicadas por outras features para esta**

Quatro tipos novos existem porque `ART-065` proíbe `012` de conhecer os enums de domínio de `004` e
`007`: `ContractService.getReportRef`, `ContractPeriodService.getReportRef` e
`TicketService.getReportRef` devolvem texto onde as respostas de tela devolvem enum, e a decisão de
§6.1 — snapshot ou cálculo ao vivo — chega **calculada** em `isClosed`/`isStarted`. É a mesma
inversão de `ContractRefResponse.acceptsWorkLogs`: quem é dono da máquina de estados decide o que ela
significa.

**Divergências entre documentos, reportadas e resolvidas**

- **Estrutura da resposta.** §23 de `specs/012` agrupa emissor, cliente, contrato e período em um
  `ReportHeaderDto`; §6 de `reports.md` os mostra no primeiro nível. Vale `reports.md` (IA-11), e o
  exemplo normativo daquele documento é o contrato que o frontend consome.
- **Faixa de códigos de erro.** §12 de `specs/012` atribui a `DEVTIME-3002`, `3003` e `3004`
  significados diferentes dos de §12 de `reports.md`. Vale `reports.md`, e `ErrorCode` segue a
  numeração de 3002 a 3007 daquele documento.
- **Numeração das migrations.** §13.3 prevê `V033`/`V034`; a sequência real ia até `V031`. Valem
  `V032`/`V033`, mesma resolução de `V029`, `V030` e `V031`. `V021`, reservada a esta tabela em
  `database.md` §8.1, permanece permanentemente vaga — um número de migration nunca é reaproveitado.
- **Sétimo agrupamento.** §6.3 de `specs/012` tabela seis; §5.1 de `reports.md` tabela sete, com
  `NONE`. Vale o segundo: sem `NONE` não há como pedir o detalhamento sem cabeçalhos de grupo, que é
  exatamente o que o CSV de §9.3 é.

**Lacunas de documentação reportadas**

- **`issueId` não estava no exemplo de §6.** RN-703 e PDF-07 exigem um identificador único de emissão
  rastreável do arquivo até o registro de exportação, e o exemplo normativo não o trazia. Publicado
  como `EM-{data}-{8 hexadecimais}` e `reports.md` §6 sincronizado.
- **`regularMinutes` do bloco `financial` só está definido para o caso de excedente.** O único
  exemplo de §6 tem consumo acima do saldo. Implementado como `min(consumido, disponível)` — a única
  leitura compatível com o exemplo e com ART-043 —, isolado em um método para ser corrigido em um
  lugar só quando a definição chegar.
- **`groupBy=TAG` não define o que fazer com registro sem etiqueta.** Descartá-lo faria os totais
  deixarem de somar as horas trabalhadas; ele forma um grupo de chave nula, e o rótulo fica com a
  camada de i18n em vez de ser inventado aqui.
- **Filtros por identificador não se aplicam a período fechado.** O payload congela rótulos, não
  chaves. Ignorados em vez de recusados, porque recusar tornaria o relatório mais importante do
  produto inacessível a partir de uma tela cujos filtros vêm preenchidos. `reports.md` §6 registra o
  comportamento em CP-08.
- **Endereço e autor do ajuste não foram congelados no snapshot.** A versão 2 do payload não guarda o
  endereço do emissor nem o nome de quem aplicou cada ajuste — só o identificador, que FR-129 proíbe
  exibir. Os campos saem nulos; buscá-los nas tabelas atuais violaria RN-701.

**Itens do checklist não cumpridos**

- **O PDF não é escrito em fluxo.** Flying Saucer monta a árvore do documento antes de paginar, e a
  API do motor não oferece paginação incremental. O que limita a memória é o limiar de 5.000 linhas
  de RN-706, acima do qual a geração é assíncrona e serializada pelo worker, e o arquivo passa por
  disco — nunca por um `byte[]` — antes de ir ao storage. O XLSX **é** em fluxo (SXSSF), que era o
  caso crítico de OB-06.
- **Determinismo do PDF verificado por conteúdo, não pelo arquivo inteiro.** Dois PDFs do mesmo
  período fechado têm tamanho idêntico e todo objeto de conteúdo idêntico, mas nunca são byte a byte
  iguais na região do `/ID` — que o formato define como derivado do instante da gravação e que vive
  dentro do fluxo de referência cruzada, comprimido. O produtor foi fixado para que a versão da
  biblioteca não entre nos metadados.
- **Testes de integração e de carga não executados nesta sessão.** A suíte de integração de
  relatórios foi escrita e compila, mas depende de Testcontainers e o Docker não estava disponível no
  ambiente. Continuam pendentes, como nas sprints anteriores, o teste de 50.000 linhas (T-012-35) e o
  ciclo de exportação contra storage real (T-012-36), que exigem a mesma infraestrutura.
- **Avaliação visual do PDF por pessoa externa (DoD-08).** É o critério de RP-04 e depende de uma
  pessoa fora do time; permanece aberto.
- **Frontend (T-012-21 a T-012-29).** Fora desta sprint, como nas demais.

**Sprint S8 — `010-dashboard` (backend)**

O painel resolve um problema de **atenção**, não de dado: toda informação que ele mostra já existe em
outra tela. O que ele acrescenta é a ordenação por criticidade — o contrato que exige ação hoje fica
no topo, não o primeiro em ordem alfabética.

- `GET /api/v1/dashboard` compõe seis blocos: estatísticas rápidas, cartões de contrato, alertas,
  cinco registros recentes, tickets abertos do usuário e três gráficos. `GET
  /api/v1/dashboard/chart/{type}` recarrega um gráfico isolado ao trocar o período, sem recarregar o
  resto.
- **Nenhum saldo é recalculado.** Todos os números de contrato vêm de `BalanceService`. O teste de
  equivalência foi escrito **antes** do código (T-010-02) porque o modo de falha mais provável de um
  painel é reimplementar a fórmula "porque é mais rápido que chamar o serviço" — produzindo um
  segundo número que diverge do primeiro na primeira mudança de regra.
- **A severidade usa os limiares do contrato**, nunca 50/80/100 fixos. Um contrato configurado com
  `[70, 90]` recebe alerta por e-mail em 70% (RN-602); fixar a escala do painel faria a tela mostrar
  "OK" no mesmo instante, e o usuário não saberia em qual dos dois confiar.
- **Os alertas derivam do estado presente**, não das notificações persistidas. Uma notificação é um
  evento passado e permanente; o painel responde "o que está errado agora". Se um ajuste resolveu o
  excedente ontem, o alerta some hoje — a notificação continua no histórico.
- **A projeção só aparece com 3 dias úteis decorridos.** Com um único dia, `burnRate × totalWorkDays`
  projeta cerca de 20× esse dia: correto e inútil, e pior, alarmante. "Vai estourar" no dia 2 e "tudo
  certo" no dia 10 destrói a confiança na projeção.
- **A série diária tem sempre 30 pontos, com os zeros visíveis.** Um gráfico de barras que omite os
  dias sem registro comprime o eixo e sugere trabalho contínuo onde houve pausa.
- **Erro parcial é requisito, não tolerância.** Cada bloco falha isoladamente e os demais são
  devolvidos, com a lista em `failedBlocks`. Por isso o serviço orquestrador **não** abre transação:
  dentro de uma única unidade de trabalho, a primeira consulta que falhasse a marcaria como
  `rollback-only` e derrubaria todos os blocos seguintes — a falha de um gráfico apagaria os cartões
  de contrato, que são o motivo de a tela existir.
- Migration `V031` com três índices **cobertos** (`INCLUDE`), o parcial de contratos ativos e
  `idx_tickets_open_assignee`. Os `INCLUDE` são a diferença entre atingir e não atingir a meta de p95
  < 800 ms: sem eles, nenhuma otimização de código compensa a leitura da tabela.
- O cache de gráficos tem TTL de cinco minutos e a chave **sempre** carrega tenant, escopo e — no
  escopo `USER` — o usuário. Sem isso o painel de um tenant responderia com o gráfico de outro.
  `quickStats` e cartões não são cacheados: são os valores que o usuário mais espera atualizados no
  instante em que registra horas.
- Nenhuma escrita, nenhum evento publicado, nenhum `AuditLog` — a feature é folha no grafo e
  integralmente de leitura.

**Divergências entre documentos, reportadas e resolvidas**

- **`DashboardAggregationRepository` (§25 de specs/010) foi movido de feature.** Como descrito, ele
  consultaria `work_logs` de dentro de `010`, o que AR-02 proíbe e a suíte ArchUnit reprova. As
  agregações passaram a ser publicadas por `WorkLogAggregationService` — a feature dona da tabela — e
  o painel as consome pela interface pública. Vale a hierarquia de IA-11: a Constituição (ART-065)
  prevalece sobre `specs/`.
- **Numeração da migration.** §13.3 prevê `V032`; a sequência real do repositório vai até `V030` e o
  próximo livre é `V031`. Mesma resolução das notas ¹ e ² de `database.md` §8.1 e do cabeçalho de
  `V016`.
- **`billable_minutes` não existe como coluna.** RN-112 a define como derivada, e ART-034 mantém
  apenas minutos inteiros persistidos; o índice coberto carrega `billable`, que é o que a expressão
  precisa.
- **Status do erro de parâmetro.** §12 atribui `422` ao tipo de gráfico inválido e ao intervalo
  personalizado incompleto, enquanto §17.1 do mesmo documento os classifica como validação de
  formato. Vale `400`: é a seção específica de validação, e `DEVTIME-2000` já está registrado com
  esse status no catálogo compartilhado — alterá-lo mudaria o significado de uma condição já
  integrada por outras features.
- **`CURRENT_PERIOD` não estava definido** para um tenant com contratos de dias de faturamento
  distintos, em que não existe um período de apuração único. Resolvido como o **mês corrente do
  calendário**, única leitura compatível com o exemplo normativo de `reports.md` §10.1; o saldo de
  cada cartão continua vindo do período de apuração real do seu contrato. `reports.md` §10 foi
  sincronizado.

**Lacunas de documentação reportadas**

- **`consumption-trend` é nomeado sem definição.** §14 de specs/010 e §10.2 de `reports.md` listam o
  tipo entre os seis, mas nenhum documento diz o que ele agrega. Implementado como o **acumulado da
  série diária**, a única leitura compatível com o nome e com a forma `points[]` de §23, isolado em um
  único método para poder ser corrigido em um lugar só quando a definição chegar.
- **CX-16 depende de uma interface ainda não publicada.** Exibir o nome vigente de uma categoria
  excluída exige `CategoryService.getAllForReport`, previsto em `specs/005` §22 para `012`. Publicá-lo
  exige antes decidir como ler registros com exclusão lógica sem contornar o filtro de tenant —
  `@SQLRestriction` e o `@Filter` de Hibernate são ignorados por consulta nativa, e escrever
  `tenant_id = ?` à mão viola BR-046. Enquanto isso a fatia é preservada sem rótulo, e não descartada:
  descartá-la faria os percentuais deixarem de somar os minutos realmente trabalhados. Na prática o
  caso não deve ocorrer — RN-505 migra os registros para uma categoria substituta na exclusão.

**Item do checklist não cumprido**

- **Execução paralela dos blocos (§34).** Não implementada, por segurança. `TenantContext` é um
  `ThreadLocal` e o filtro de tenant do Hibernate é ligado por transação, ambos presos à thread da
  requisição. Distribuir os blocos por um pool exigiria propagar a sessão manualmente a cada tarefa, e
  um único ponto esquecido produziria consulta sem filtro de tenant — a falha mais grave do modelo de
  ameaças (ART-021, SG-01). Os índices cobertos de `V031` são o que sustenta RNF-003; a paralelização
  é otimização a ser medida antes de aplicada (CG-10).

**Sprint de jobs de `004-contracts` — Ciclo de vida automático de contratos e períodos**

Fecha a pendência mais antiga da fila: `004` estava em `BACKEND_PARTIAL` desde S3, e os três jobs
que faltavam bloqueavam também `RolloverExpiryJob` e `AutoClosePeriodJob` de `011`.

- `GeneratePeriodsJob` (RN-213) cria o período seguinte como `SCHEDULED` quando faltam três dias ou
  menos. Nasce `SCHEDULED` e não `OPEN` porque o ciclo só passa a valer no seu `startDate`: abrir
  antes permitiria registrar horas em um período que ainda não começou.
- `OpenScheduledPeriodsJob` faz `SCHEDULED → OPEN`. `AutoEndContractsJob` encerra o contrato cuja
  vigência terminou, **delegando** a `ContractService.end` — duplicar a transição criaria dois
  encerramentos com efeitos possivelmente divergentes, e o automático precisa ser indistinguível do
  manual.
- `PeriodMaterializer` foi extraído de `ContractServiceImpl`: ativação, retomada e renovação
  automática passam pelo mesmo caminho, pela mesma razão que a prévia e a geração real compartilham
  `PeriodGenerator` (CA-01).
- O quarto job da §22.4, `ContractEndingReminderJob`, já existia em `013-notifications`. Nada foi
  duplicado.

**Duas regras ausentes descobertas durante a implementação**

- A guarda de §11 — `SCHEDULED → OPEN` exige o período anterior `CLOSED` — não estava implementada.
  O índice `uq_periods_single_open` a rejeitou no primeiro teste. Adiar a abertura é o comportamento
  correto: o ciclo anterior ainda recebe horas, e quem decide que ele terminou é o fechamento.
- **Defeito pré-existente em `013-notifications`:** `NotificationJobs` construía a sessão de
  plataforma com `userId` nulo, que o construtor canônico de `TenantSession` recusava. Toda iteração
  dos lembretes de RN-605 e RN-606 lançava e era engolida pelo `catch` do próprio job — os lembretes
  **nunca** notificavam, sem nenhum sintoma além de uma linha de log. Corrigido com a fábrica
  `TenantSession.system(...)`, que torna a ausência de usuário explícita e intencional (é o que faz
  a trilha registrar `actorType = SYSTEM`, CE-S-06). `TenantContext.requireUserId()` passou a falhar
  alto nessa sessão, em vez de devolver `null` a quem espera um identificador.

**Divergência aceita:** RS-06 prevê a geração às 03:00 *no fuso do tenant*. O agendamento é único,
no fuso do servidor: a janela de três dias de RN-213 absorve qualquer fuso, e um agendamento por
fuso multiplicaria execuções e locks sem mudar o resultado.

**Pendentes de `004`:** frontend, `POST /contracts/{id}/duplicate` e a guarda de cronômetro ativo em
`suspend`/`end` (`DEVTIME-2212`).

**Sprint de `002-users` — Conta, Organização, Membros e Auditoria** · `specs/002-users`

Próxima da fila pela §4 de `implementation-order.md`: ordem 2, `P0`, com `001-authentication` em
`BACKEND_DONE`. Entrega backend, testes e documentação.

Antes do código, **onze divergências** entre `specs/002-users` e `docs/04-api/users.md` foram
reportadas e resolvidas em favor do segundo, pela hierarquia de `project-constitution.md` §9
(`04-api/` prevalece sobre `specs/`). Todas estão tabeladas em `users.md` §11.2. A mais consequente:
a spec indica `DEVTIME-1003`/401 para senha incorreta no cancelamento, mas esse código já está
publicado em `authentication.md` §8 com o significado "usuário sem organização ativa" — ART-113
proíbe mudar o significado de um código publicado, então o fluxo usa `DEVTIME-1011`.

Perfil e preferências (`features/user/`)

- `UserProfileService` opera **sempre** sobre o titular da sessão: nenhuma assinatura aceita um
  identificador de usuário, o que elimina por construção a classe de erro "esqueci de verificar o
  ownership".
- `UserPreferencesCodec` aplica os padrões de `entities.md` §6.2.1 **na leitura**, e a escrita é
  mescla, nunca substituição — um cliente antigo não apaga uma preferência introduzida depois dele.
- `AvatarValidator` repete a defesa em duas camadas de `015`: tamanho antes de qualquer leitura de
  conteúdo, tipo declarado contra allowlist e assinatura binária **cruzada** contra o tipo
  declarado. WebP exige o rótulo de formato além do contêiner RIFF, que também hospeda WAV e AVI.

Organização (`features/tenant/`)

- `TenantSettingsValidator` valida o **valor efetivo** — mescla do que veio na requisição com o
  persistido —, e não a requisição isolada: enviar apenas `timerLongRunningMinutes = 1200` sobre um
  `timerAutoAbandonMinutes = 960` inverteria os limiares sem que a requisição, sozinha, revelasse o
  problema.
- Alterar `settings` **não recalcula nada** (CE-03, CP-03, ART-005). Um cliente que recebeu um
  relatório não pode vê-lo mudar porque o prestador ajustou uma configuração hoje.
- Cancelamento exige senha **e** a digitação de `CANCELAR` (SG-04), verifica ausência de período em
  `CLOSING` (CX-12) e agenda a purga para +30 dias. A purga é exclusão **lógica**: P-03 e ART-051
  proíbem `DELETE` físico, e a obrigação de guarda do documento fiscal é de cinco anos.
- Migration `V030` acrescenta `cancelled_at`, `purge_scheduled_at` e `cancellation_reason`.
  **Lacuna reportada**: `database.md` §7.1 e `entities.md` §6.1 não preveem coluna alguma para o
  instante do cancelamento, mas `users.md` §6.3 exige devolver `dataRetainedUntil`. Derivar de
  `updated_at` seria incorreto — qualquer alteração posterior reiniciaria a retenção.

Membros

- `MemberGuards` concentra as três guardas **na ordem normativa de §6.1**, e a ordem é a regra:
  auto-alteração (RN-456) antes de hierarquia, porque um OWNER tentando se rebaixar precisa ler "não
  é possível alterar o próprio papel"; último OWNER (RN-455) por último, porque é a única que custa
  uma consulta com **lock pessimista**. Sem o lock, dois ADMINs rebaixando OWNERs distintos ao mesmo
  tempo leriam a mesma contagem e ambos passariam, deixando o tenant sem proprietário — estado do
  qual não há saída pela própria API.
- `MemberRemovalOrchestrator` aplica RN-458 e RN-460 **dentro** da transação: um membro sem acesso
  com cronômetro ativo produziria, ao ser encerrado, um registro sem autor válido. A resposta
  devolve quantos registros foram preservados — sem esse número, remover um membro parece apagar o
  trabalho dele.
- O convite emite o token por `InvitationTokenPort`, implementada em `001`. A inversão evita o ciclo
  `tenant → auth → tenant`: a emissão é autenticada e pertence a `002`, o aceite é público e
  pertence a `001`, mas o token continua vivendo onde os outros dois fluxos de token vivem.

Auditoria

- `GET /audit-logs` é **somente leitura por construção** — a classe do controller não possui outro
  verbo, que é como INV-AUD-01 e CP-05 se manifestam na camada HTTP.
- Sem intervalo informado aplica 30 dias; acima de 90 dias devolve `DEVTIME-3001`. `audit_logs` é
  particionada por mês e cresce de 5 a 10× mais rápido que `work_logs`: uma consulta sem recorte
  varreria todas as partições, e seria a operação mais cara do sistema disponível a um clique.
- `AuditActorNameResolver` é declarada em `audit` e implementada em `user`. `user` já depende de
  `audit` — toda alteração de perfil é auditada —, então a chamada direta fecharia um ciclo (BR-008).

Correções estruturais

- `PasswordEncoderConfiguration` e `MethodSecurityConfiguration` foram separadas de `SecurityConfig`.
  Com `TenantServiceImpl` passando a declarar `@PreAuthorize`, a configuração de segurança HTTP
  fechava ciclo de criação de beans com a cadeia de filtros por dois caminhos distintos. Separar
  resolve na raiz: o codificador depende apenas de configuração, e a segurança de método apenas do
  avaliador de permissões.
- `IpAddressMasker` saiu de `auth` para `shared/observability`: a máscara de IP é controle de
  privacidade transversal (ART-084), e a trilha de auditoria passou a ser o segundo consumidor.

`MEMBER_JOINED` e `MEMBER_REMOVED` ganharam produtor, fechando a pendência registrada em
`notifications.md` §14.

**Pendências desta feature**

- Frontend (T-002-28 a T-002-39).
- **Exportação de dados do tenant (E-06)**: depende do mecanismo assíncrono de `report_executions`,
  que pertence a `012-reports` (F3) e não existe. Construir um mecanismo paralelo criaria duas
  formas de acompanhar execução assíncrona.
- **Redimensionamento do avatar para 256×256**: o JDK não decodifica WebP, e escolher uma biblioteca
  de imagem é decisão que exige registro.
- **`AuditArchiveJob`**: nenhum documento especifica o destino do arquivamento de partições com mais
  de 12 meses, e desanexar sem destino tornaria a trilha inconsultável.
- Blocos `stats` de `users.md` §6.1 e §7.1: exigem contadores públicos em `003`, `004`, `008`, `009`
  e `015` que não existem. Omitidos em vez de preenchidos com zeros, pela mesma razão que levou
  `005-categories` a omitir o bloco `usage`.

**Sprint S11 — Anexos** · `specs/015-attachments`

Escopo acordado: upload, download, versionamento, permissões, comentários, validações, auditoria,
testes e documentação. Última feature da fila (ordem 15 de 15), com `014-comments` e `007-tickets`
em `BACKEND_DONE`.

Antes de qualquer código, um item do escopo foi reportado como conflito e resolvido com o
solicitante: **"versionamento"**. `specs/015-attachments` §4 e RS-09 mantêm o versionamento *de
arquivo* fora do roadmap, e CP-13 proíbe qualquer rota de atualização; já `integrations.md` §6.2
SG-03 exige versionamento *de objeto no storage*, com retenção de 30 dias. Foi implementado o
segundo. As duas coisas coexistem sem contradição porque atuam em camadas distintas — o storage
guarda versões do binário, o domínio expõe um anexo imutável.

Infraestrutura (`shared/`) — pré-requisito bloqueante de T-015-01 e T-015-02

- `StoragePort` e `S3StorageAdapter` (S3 e compatíveis; MinIO em desenvolvimento). Nenhuma feature
  conhece o SDK; a biblioteca vence apenas na fronteira de integração (CE-G-07).
- `StorageBucketInitializer` aplica, na inicialização, as três propriedades que nenhum código
  corrige depois: **bloqueio de acesso público** (SG-01 — R-08 classifica um bucket acidentalmente
  público como risco crítico), **versionamento habilitado** e **expiração de versões anteriores em
  30 dias** (SG-03). Sem a regra de expiração o versionamento tornaria a remoção do binário exigida
  por RN-805 e INV-ATT-06 apenas aparente.
- `AntivirusPort` e `ClamAvAdapter`, falando `INSTREAM` direto no socket. **Nunca lança**: qualquer
  falha vira `FAILED`, que mantém o download bloqueado (AV-02). Uma exceção propagada transformaria
  a indisponibilidade do verificador em erro de requisição de quem apenas consultou um anexo.
- MinIO e ClamAV no `docker-compose.yml`. O backend depende de `service_started` do ClamAV, e não de
  `service_healthy`: a primeira carga da base de assinaturas leva minutos, e bloquear a subida
  transformaria uma degradação prevista (CE-I-03) em indisponibilidade total.

Anexos (`features/attachment/`)

- Ordem normativa de §6.1 **integral** (BR-062). Duas decisões dentro dela são fáceis de inverter e
  caras: o tamanho é validado **antes** de qualquer leitura de conteúdo (CE-02, SG-11) e o binário é
  gravado **depois** da validação de assinatura (CP-04). Ambas têm teste próprio — o primeiro conta
  quantas vezes o conteúdo foi aberto.
- `MagicNumberValidator` é a classe central (OB-01). A verificação é **cruzada**: pergunta se a
  assinatura é a do tipo declarado, não se a assinatura é conhecida. A segunda forma aceitaria um
  PDF válido declarado como `image/png`, porque a assinatura do PDF é perfeitamente conhecida. A
  suíte foi escrita antes do validador e tem 19 casos negativos cruzados.
- Manifesto interno lido nos formatos Office, com teto rígido de 64 KB e **uma única entrada**: §6.2
  exige a leitura e CP-17 proíbe descomprimir o ZIP; o teto é o que concilia as duas, porque nenhuma
  bomba de descompressão sobrevive a um limite que não depende do que o arquivo declara.
- `StorageKeyGenerator` produz chave opaca `{tenantId}/attachments/{yyyy}/{MM}/{checksum}`. O nome do
  arquivo não participa (CP-05): derivar a chave do nome reintroduziria, pela porta dos fundos, o
  vetor que `FileNameSanitizer` fecha.
- Deduplicação restrita ao tenant pelo **filtro de tenant**, não por uma cláusula no código — o que
  a torna consequência da arquitetura em vez de algo que alguém pode remover (CP-06).
- Máquina de §4.9 com até 3 tentativas. `INFECTED` remove o binário **no mesmo método** que muda o
  estado (INV-ATT-06): separar as duas mudanças deixaria um instante em que o registro está
  infectado e o arquivo continua disponível.
- `OrphanBinaryJob` **alerta sem remover** (CP-10). Remover com base numa inferência sobre a
  contagem de referências é irreversível se a contagem tiver defeito — mesmo princípio de
  `WorkLogConsistencyJob` e `SnapshotIntegrityJob`.
- **Nenhuma rota de atualização e nenhum caminho de liberação manual** (CP-02, CP-13). A ausência é
  a implementação da regra, e `NoManualReleasePathTest` a torna verificável: se alguém acrescentar a
  rota, o teste falha apontando para a decisão documentada.
- `ATTACHMENT_INFECTED` ganhou produtor, fechando a pendência registrada na nota ¹¹ de
  `implementation-order.md`. É o único evento em que **NT-05 não se aplica**: quem enviou é
  exatamente quem precisa saber, e sem o aviso veria apenas um download que não funciona.

Testes

- **EICAR isolado e dentro de ZIP, contra um ClamAV real** (DoD-02). É o gatilho de acionamento do
  risco crítico da feature. Um dublê programado para responder `INFECTED` provaria apenas que o
  dublê responde o que foi programado para responder.
- Remoção do binário comprovada **por acesso direto ao storage** (DoD-06), com MinIO real.

### Corrigido

- **`period_snapshots.checksum` era `CHAR(64)`** (`V020`, feature 011). O PostgreSQL reporta `CHAR`
  como `bpchar`, e a validação de schema do Hibernate — obrigatória por ART-054 — recusa a
  divergência contra o `String(64)` da entidade, **impedindo a aplicação de iniciar em banco limpo**.
  O defeito não aparecia porque nenhum banco havia sido migrado do zero desde `V020`; foi encontrado
  pela suíte de `015`, que sobe um banco limpo. Corrigido por `V029`, de forma aditiva — `V020` está
  mesclada e não foi alterada (BR-035, IA-03).
- **`FlywayMigrationIntegrationTest` verificava uma lista desatualizada de migrations**, omitindo
  `V015`, `V016`, `V018`–`V020` e `V028`, que existem. O teste falhava em qualquer execução com
  banco limpo.
- **`StorageBucketInitializer` derrubava o contexto quando o storage estava ausente.** O SDK sinaliza
  indisponibilidade por `SdkClientException`, que não é `S3Exception`. SG-05 e IN-04 exigem que falha
  de storage seja degradação — o registro de horas continua funcionando sem anexos.

### Alterado

- **`docs/04-api/tickets.md` §11 sincronizado com o comportamento implementado** (T-015-30). Quatro
  divergências foram resolvidas em favor de `02-domain/`, que precede `04-api/` na hierarquia IA-11:
  - `DEVTIME-2708` (quota), `DEVTIME-2709` (`INFECTED`) e `DEVTIME-2710` (`FAILED`) **retirados**:
    duplicavam condições que `business-rules.md` §17 já atribui a `DEVTIME-2701` e `DEVTIME-2703`.
    Nunca foram implementados nem publicados, então a retirada não quebra contrato; os códigos
    permanecem reservados, porque um código aposentado nunca é reutilizado (EX-03).
  - `checksumSha256` e `downloadUrl` **removidos** do exemplo de resposta (CP-07): o checksum
    permitiria verificar se um arquivo específico existe no tenant sem tê-lo.
  - Campo `description` no upload **removido**: não existe em `entities.md` §6.17 nem em §23 da spec.
  - Acrescentados os endpoints de listagem, de anexo em comentário, de exclusão e de quota, ausentes
    da seção.
- **Numeração das migrations seguindo `database.md` §8.1** (`V023`/`V024`) e não `specs/015` §13.3
  (`V038`/`V039`) — mesmo critério já aplicado a `V022` por `014-comments`. §8.1 reserva
  explicitamente estes dois números a `attachments`.
- `docs/07-backlog/future.md`: registradas a decisão de OB-02 (ausência de liberação manual) e a
  fronteira entre versionamento de arquivo e versionamento de objeto no storage.


**Sprint — Banco de horas (frontend)** · `specs/011-bank-hours`

Escopo acordado: frontend de `011`, cujo backend já estava `BACKEND_DONE`. A implementação foi
**interrompida antes de qualquer código** para reportar três divergências entre documentos; a
resolução acordada foi tratar o backend implementado como verdade e sincronizar a documentação.

Compartilhado (`shared/`)

- `dt-balance-summary`, `dt-consumption-gauge` e `dt-partial-badge` nascem em `shared/` porque
  `010-dashboard` os reutiliza por nome (T-010-14). Recriá-los lá produziria duas representações
  visuais do mesmo saldo, que divergiriam.
- `dt-consumption-gauge` **não** usa `p-progressBar`. Além de não marcar limiares (BB-04) nem
  distinguir o excedente (BB-03), a versão 21 do componente emite `aria-level="{valor}%"` por host
  binding — atributo inválido para `role="progressbar"`, que gera duas violações de axe-core e não
  é sobrescrevível de fora. FR-140 exige zero violações. O bug foi encontrado pelo teste, não em
  revisão.
- `dt-duration-input` com `allowNegative`, exigido por FR-112 para o campo de minutos do ajuste, e
  o parser dos oito formatos da tabela §6.2 de `components.md` (CA-05).
- `consumptionRatePipe` com `HALF_UP` explícito: `toFixed` sozinho devolve `83,6%` para `83.65`,
  divergindo do relatório do servidor no dígito que o cliente confere.
- `criticalityOf` em `shared/utils`, única fonte da tabela de severidade §5.3.

Banco de horas (`features/contracts/`)

- `PeriodApi` espelhando os sete endpoints publicados, sem transformação (FR-062) e sem tratamento
  de erro (FR-063).
- `PeriodStore` e `StatementStore` providos na rota de P16, não em `root` — o estado morre com a
  tela e trocar de organização não deixa saldo de outro tenant em memória (FR-051).
- `dt-adjustment-dialog` com **prévia do saldo resultante**. O ajuste é imutável (RN-236) e só se
  corrige por estorno, que fica para sempre no extrato do cliente: a prévia é a única defesa contra
  um valor digitado errado (risco R-08). A prévia é exibição, não cálculo canônico — o número que
  fica na tela depois de aplicar vem da API (CE-F-05, RP-03).
- Estorno implementado como novo ajuste de sinal contrário (FA-05); não há caminho de edição.
- Página P16 em `/contracts/:id/periods/:periodId` sob `permissionGuard(['PERIOD_VIEW'])`, com os
  quatro estados: esqueleto, erro com nova tentativa, vazio e normal.
- Sete códigos `DEVTIME-22xx` acrescentados ao mapa de mensagens localizadas (FR-071).

### Corrigido

- `04-api/contracts.md` §9.1 e §9.2 e `05-ui/components.md` §6.4 e §6.5 sincronizados com o contrato
  publicado no OpenAPI. O que a especificação previa e o backend não emite está preservado em
  §9.1.1 e §9.2.1 como escopo pendente, em vez de removido.

### Pendente

- `dt-projection-chart` e a marcação de projeção: `burnRate` e `projectedConsumption` não são
  expostos por nenhum DTO.
- Prévia de fechamento (T-011-29/T-011-31): sem endpoint. "Será transportado" e "registros a travar"
  aparecem como "—" — calcular carry-over no cliente reproduziria a fórmula canônica, que é
  exatamente o que RP-03 aponta como origem de divergência de saldo.
- Resumos por categoria e por ticket em P16, e paginação por cursor do extrato.
- Autor do ajuste: a API devolve apenas o UUID, e FR-129/CA-09 proíbem identificador técnico na
  interface.
- Navegação até P16 pela interface, que depende de P13/P14 (feature `004`).

**Sprint S8 — Notificações (backend)** · `specs/013-notifications`

Escopo acordado: backend de `013-notifications`, com testes e documentação. Frontend não foi
solicitado. **O alerta de "ticket parado" pedido no escopo não existe em nenhum documento** — a
lacuna está reportada abaixo e nada foi inventado (IA-01, CG-02).

Banco

- `V019__create_notifications.sql` — tabela e cinco índices. O único `(recipient_id, dedupe_key)`
  é a garantia estrutural de RN-601: sem ele, duas avaliações concorrentes do mesmo limiar
  criariam duas notificações idênticas e o `dedupeKey` seria apenas convenção. Ele **não** é
  parcial por `deleted_at` — uma notificação que o usuário excluiu não deve ser recriada pela
  avaliação seguinte; excluir é dizer "já vi isso".
- `idx_notifications_unread` é **parcial** sobre `read_at IS NULL`. É o que mantém a contagem
  barata: em um usuário com 5.000 notificações e 3 não lidas, o índice tem 3 entradas — e a
  contagem é consultada ao carregar toda tela.
- Coluna `email_attempts`, exigida pela idempotência do `EmailRetryJob` (§22.4 da spec). Sem
  contador não há como limitar a três tentativas; `entities.md` §6.18 não a declara, e a lacuna
  está registrada abaixo.

Deduplicação — o núcleo

- A inserção é tentada **sem verificação prévia** (CP-03). Verificar antes de inserir abriria uma
  janela de corrida entre a verificação e a inserção — exatamente o cenário de duas avaliações
  concorrentes do mesmo limiar. O índice único decide, e a violação é sucesso silencioso.
- Cada destinatário é inserido em transação própria (`REQUIRES_NEW`): sem isso, a violação
  esperada marcaria a transação como `rollback-only` e o segundo destinatário deixaria de ser
  notificado por causa do primeiro.
- `NotificationCommand.dedupeKeyFor` é uma **função** do destinatário, não um texto. §6.1 define os
  dois formatos: `CONTRACT_USAGE:{periodId}:{threshold}` é a mesma chave para todos, enquanto
  `ADJUSTMENT:{adjustmentId}:{userId}` e `TICKET_COMMENT:{commentId}:{userId}` incluem a pessoa.

Geração

- `ConsumptionAlertPolicy` lê `contract.notificationThresholds`, **nunca** 50/80/100 fixos (CP-05):
  valores fixos fariam a notificação divergir do painel do mesmo contrato. A política não decide se
  já notificou — monta um comando por limiar ultrapassado, sempre, e a deduplicação faz o resto.
- Contrato sem saldo disponível não avalia limiar algum (CE-10): um modelo de horas abertas não tem
  teto a ultrapassar. A verificação é sobre `availableMinutes`, e não sobre o tipo do contrato,
  porque AR-02 impede esta feature de conhecer `ContractType`.
- `RecipientResolver` exclui o **autor da ação** de todo conjunto (NT-05). Ninguém é avisado do que
  acabou de fazer, incluindo quem atribui um ticket a si mesmo — o caso mais comum de todos.
- Em cronômetro, o destinatário é sempre o dono, inclusive no encerramento forçado: quem encerrou
  não recebe, quem teve o cronômetro encerrado recebe (OWN-05, FA-20).
- CE-N-07: um responsável também mencionado recebe **uma** notificação, do tipo mais específico.
  O filtro é da regra, não do `dedupeKey` — as duas chaves são distintas por construção.

Entrega

- A in-app é criada **antes** de qualquer decisão sobre e-mail (§6.2). Não é organização de código:
  RN-610 exige que a falha de envio não impeça a notificação, e criar primeiro garante isso
  estruturalmente.
- `EmailDispatchPolicy` verifica as duas chaves independentes de RN-608, e **ignora ambas** em tipo
  crítico (§9.1): um contrato excedido e um anexo infectado são enviados de qualquer forma.
- O contador de tentativas é incrementado **antes** do envio: uma queda no meio da chamada ao
  provedor deixaria a tentativa não contabilizada, e o limite de três deixaria de valer.
- O backoff é o intervalo de 5 minutos do job, não uma espera no processo — uma tentativa a cada
  5, 10 e 15 minutos dá tempo ao provedor sem manter thread bloqueada.

Fluxo em tempo real

- Registro por `recipientId`, **nunca por tenant** (SG-03): um fluxo por organização entregaria a
  cada conectado as notificações dos colegas.
- `NotificationStreamRegistry` **não** é um `*Service`, divergindo do nome em §22.2 da spec:
  `SseEmitter` é um tipo web e BR-069 proíbe que um serviço conheça a camada HTTP. É o adaptador da
  borda, ao lado do controller.
- Toda falha de publicação é engolida por decisão: a notificação já está persistida, e uma exceção
  interromperia a criação para os destinatários seguintes. ST-05 garante que nada se perde.

Fora desta feature

- `014-comments` passou a publicar `CommentCreatedEvent` com responsável e mencionados resolvidos,
  como §15 da spec prevê. Comentários de **sistema** não geram evento: narram um fato que a feature
  de origem já notificou.
- Os eventos de cronômetro ganharam `ticketId`, e `TimerAbandonedEvent` ganhou `recoverableUntil`:
  o consumidor precisa da chave legível e do prazo, e recalcular a janela de 7 dias fora de
  `AbandonedTimerPolicy` a duplicaria.
- `TicketReopenedEvent` ganhou `assigneeId` pelo mesmo motivo.
- `MembershipService.activeMemberIdsWithRoles`, `ContractService.notificationThresholdsOf` e
  `findEndingOn`, `ContractPeriodService.findEndingOn` e
  `UserAccountService.updateNotificationPreferences` foram publicados como interfaces das features
  donas dos dados — nenhuma tabela é alcançada de fora da sua feature (AR-02).

### Lacuna de especificação — alerta de "ticket parado"

**Tarefa:** escopo da sprint, item "Ticket parado".
**Documentos consultados:** `docs/02-domain/business-rules.md` §12 (matriz de notificações),
`docs/04-api/notifications.md` §6 (catálogo completo), `specs/013-notifications/spec.md` §6.1.
**Lacuna:** nenhum dos três define um alerta de ticket sem movimentação. A matriz de RN-607 cobre
`TICKET_ASSIGNED`, `TICKET_COMMENTED`, `TICKET_MENTIONED` e `TICKET_BLOCKED`; não há tipo, gatilho,
limiar de inatividade, destinatário nem chave de deduplicação para "parado". Uma busca por
`estagnado`, `inatividade`, `sem atualização` e `staleDays` em `docs/` e `specs/` não retorna nada
aplicável.
**Impacto:** o item não foi implementado. Todo o restante do escopo está entregue.
**O que precisa ser decidido para desbloquear:** (a) o gatilho — dias sem alteração de status,
sem comentário ou sem registro de horas; (b) o limiar, e se é configurável por tenant ou por
contrato; (c) os destinatários — responsável, relator ou ambos; (d) a severidade; (e) a chave de
deduplicação, que precisa evitar um alerta diário sobre o mesmo ticket parado.
**Recomendação:** definir em `business-rules.md` §12 antes de implementar, porque a chave de
deduplicação é a decisão difícil: sem um discriminador que avance, o alerta será emitido uma única
vez e nunca mais; com um discriminador diário, vira o ruído que RN-601 existe para evitar.

### Conflitos entre documentos resolvidos por IA-11

| # | Conflito | Resolução |
|:--:|---|---|
| 1 | `notifications.md` §9.1 define `digestMode` e `quietHours` nas preferências; `entities.md` §6.2.1 não os inclui, e `specs/013` §4 e RS-08 declaram digest **fora do roadmap** | Prevalece `entities.md` (02-domain > 04-api). Nenhum dos dois foi implementado; `DEVTIME-4002` fica reservado para não mudar o significado de um código publicado |
| 2 | `notifications.md` §9.1 chama o campo de `mutedTypes`; `entities.md` §6.2.1 e `GET /auth/me` usam `mutedNotificationTypes` | Prevalece `entities.md`. Duas grafias para a mesma preferência obrigariam o cliente a conhecer as duas |
| 3 | `notifications.md` §6 lista `CONTRACT_USAGE_50/80/100` como tipos; §6.1 da spec e RN-602 derivam os limiares de `contract.notificationThresholds` | Um único tipo `CONTRACT_USAGE`, com o limiar no `dedupeKey` e na severidade. Tipos fixos quebrariam um contrato com `[70, 90]` (CP-05) |
| 4 | RN-003 torna lógica toda exclusão; RN-609 e §19.1 exigem **remoção** após 90 dias da leitura | A exclusão pelo usuário é lógica (estado "Excluída" de §10); a purga é física, porque uma exclusão lógica manteria o dado indefinidamente e descumpriria a retenção declarada. É a única exclusão física de entidade de domínio no sistema |
| 5 | `specs/013` §13.3 aloca `V035`/`V036`; `database.md` §8.1 aloca `V019` a `notifications` | Prevalece `database.md`, como em `V013`–`V016` |
| 6 | `entities.md` §6.18 não declara contador de tentativas de e-mail; §22.4 da spec exige idempotência "por contador por notificação" | Coluna `email_attempts` acrescentada. Sem ela, RN-610 não tem como limitar a três |

### Pendências desta sprint

- Frontend de `013`: P25, P28 e o sino global. Não foi solicitado.
- **Alerta de "ticket parado"**: bloqueado por lacuna de especificação (acima).
- `MEMBER_JOINED`, `MEMBER_REMOVED`, `EXPORT_COMPLETED`, `EXPORT_FAILED` e `ATTACHMENT_INFECTED`
  estão no catálogo sem produtor — chegam com `002`, `012` e `015`.
- `TICKET_BLOCKED` do catálogo de §6 não foi declarado: `007` não publica evento de bloqueio, e um
  tipo que nunca ocorre apareceria na tela de preferências sem propósito.
- **Testes de integração não executados nesta máquina**: Testcontainers exige Docker, indisponível
  no ambiente. Eles compilam; as suítes puras — 452 testes, incluindo ArchUnit — estão verdes.
- Teste de concorrência com 100 avaliações **simultâneas** (T-013-03): a suíte cobre 100 avaliações
  sequenciais, que provam a deduplicação; a corrida real exige execução paralela contra o banco.
- SSE limitado a deploy de instância única (OB-08): o registro de conexões vive em memória.

### Adicionado

**Sprints S5, S6 e S7 — Registro de horas, cronômetro e banco de horas (backend)** ·
`specs/008-worklogs`, `specs/009-timer`, `specs/011-bank-hours`

Escopo acordado: backend das três features, com testes e documentação. Frontend não foi solicitado
e permanece fora — ver "Pendências desta sprint". As três são de complexidade **Crítica** (SQ-02,
SQ-03): as suítes normativas de sobreposição e de cálculo foram escritas antes do código.

Banco

- `V015__create_timers.sql` — `timers` e `timer_pauses`. O índice `uq_timers_active_user` é sobre
  `(user_id)` **sem** `tenant_id`, deliberadamente: RN-150 limita a um cronômetro ativo por
  *pessoa* entre todos os tenants (CE-13), e incluir o tenant permitiria dois simultâneos. Ao
  contrário de RN-102, aqui a constraint de banco é viável — `Timer` não usa exclusão lógica.
- `V016__create_work_logs.sql` — a tabela e os sete índices, com `idx_work_logs_overlap` como o mais
  crítico da feature. **Não** existe constraint `EXCLUDE` para RN-102: ela colidiria com o soft
  delete, porque um registro excluído logicamente permanece na tabela e bloquearia o intervalo,
  impedindo o usuário de recriar o que ele mesmo apagou (OB-02). A garantia fica em três camadas —
  validação, índice dedicado e job de detecção com alerta crítico.
- `V018__create_period_adjustments.sql` e `V020__create_period_snapshots.sql`. A unicidade do
  snapshot é `(contract_period_id, snapshot_at)`, não apenas o período: um período reaberto e
  refechado gera um **segundo** snapshot, e a unicidade simples impediria o refechamento (CX-18).
- `V028__create_work_log_tags.sql` — incremental de `V017`, que só pôde criar `ticket_tags` porque
  `work_logs` ainda não existia (CE-O-03). `V017` não foi alterada (ART-053).

Registro de horas (`008`)

- Ordem normativa da §6.1 aplicada integralmente e na sequência exata. Ela decide **qual erro o
  usuário vê** quando o payload viola várias regras ao mesmo tempo: a sobreposição precede o
  cálculo porque é o problema mais difícil de perceber sozinho, e a política de excedente é a
  última porque depende do valor calculado e do período resolvido.
- `OverlapDetector` com comparação **estrita nos dois lados** e intervalos semi-abertos. A definição
  vive em `WorkLogInterval.overlaps`, testada contra os nove casos da tabela normativa; a consulta
  SQL a reproduz e usa `LIMIT 1` sobre `idx_work_logs_overlap` — o registro conflitante só é
  materializado quando já se sabe que há conflito.
- `WorkLogCalculator` trunca segundos por divisão inteira e `RoundingPolicy` arredonda **sempre para
  baixo**. Uma sessão de 10 minutos com múltiplo 15 resulta em zero e é rejeitada por RN-115: parece
  defeito e é a consequência aritmética inevitável de nunca cobrar tempo não trabalhado (PR-03).
- `contractId` e `clientId` são copiados do ticket e imutáveis; `netMinutes`, `source` e `timerId`
  estão **ausentes de todos os DTOs de escrita**. A ausência é a garantia: aceitar `netMinutes` do
  cliente permitiria inflar a cobrança com uma requisição.
- Desnormalizados atualizados por **incremento dentro da transação**, nunca por reagregação nem por
  evento assíncrono: a resposta `201` já devolve o saldo atualizado, e um saldo desatualizado no
  exato momento do registro destruiria a confiança no número que é o produto.
- `POST /work-logs/validate` relata **todos** os problemas de uma vez, ao contrário da criação, que
  interrompe no primeiro. O serviço é `readOnly` inteiro — nada é persistido.
- Escopo de `MEMBER` aplicado por `Specification`, inclusive na contagem da paginação e nos totais:
  filtrar em memória vazaria a existência de registros de colegas pela diferença.

Cronômetro (`009`)

- Estado 100% no servidor. O cliente recebe `startedAt`, `lastResumedAt` e
  `accumulatedActiveSeconds` e calcula o tempo decorrido localmente — um cronômetro que consultasse
  o servidor a cada segundo geraria 3.600 requisições por hora por pessoa.
- **RN-160 é aplicada por construção**, não por tratamento de erro: o encerramento monta o comando,
  delega a `WorkLogService.createFromTimer` e só marca `COMPLETED` **depois** de o work log existir.
  Qualquer falha reverte a transação e o cronômetro permanece exatamente como estava. `TIMER_STOP_FAILED`
  é registrado em transação própria (`REQUIRES_NEW`), justamente para sobreviver a esse rollback.
- `accumulatedActiveSeconds` **não** alimenta o work log: o valor canônico é sempre `gross − paused`
  (RN-111). Persistir a partir do acumulado produziria dois números para a mesma sessão.
- `TimerMonitorJob` marca `ABANDONED` e notifica, mas **não encerra nem gera work log**: encerrar
  exigiria inventar um horário de término (RN-164, PR-03).

Banco de horas (`011`)

- `BalanceCalculator` com as fórmulas canônicas em aritmética inteira; `consumptionRate` é o único
  valor fracionário e usa `BigDecimal` — `double` produziria `105.06999999` onde o cliente espera
  `105,07`.
- Fechamento atômico de sete passos sob **lock pessimista**. Com *optimistic locking*, dois
  fechamentos simultâneos executariam os sete passos e um falharia no commit — mas o passo 3 já
  teria travado work logs e o passo 4 já teria gerado um snapshot (CE-ME-08).
- O passo 1 é **reconciliação**, não leitura: o fechamento é o último momento em que uma divergência
  do desnormalizado ainda pode ser corrigida antes de o snapshot congelar o número. A diferença é
  auditada e vira alerta `ERROR`.
- Ajustes imutáveis: sem método de repositório, sem serviço e sem rota de edição ou exclusão. A
  correção é um estorno, que fica visível no extrato que o cliente lê.
- Snapshot com payload canônico (chaves ordenadas) e checksum SHA-256 sobre exatamente os bytes
  persistidos. `SnapshotIntegrityJob` **alerta sem corrigir**: reescrever o snapshot para "acertar"
  o checksum destruiria a única prova de que algo foi alterado (CX-21).

Fronteiras entre features

- Quatro interfaces de inversão foram criadas para manter o grafo de features **acíclico** (AR-09,
  BR-008): `TicketWorkLogCountSource` e `ActiveTimerSource` em `007`, `PeriodWorkLogSource` e
  `PeriodActiveTimerSource` em `004`/`011`. `worklog` e `timer` dependem de `ticket` e `contract`
  por regra de negócio (RN-101, RN-107, RN-306); consultá-los de volta fecharia o ciclo. Quem
  declara a interface é quem precisa do dado, quem a implementa é quem o possui — o mesmo arranjo já
  usado por `MemberContractLinkSource`.
- `TicketWorkLogGate` passou a usar a contagem real de work logs, e `ActiveTimerGuard` a consultar
  cronômetros reais: as duas classes existiam desde S4 justamente como ponto único de aplicação de
  RN-305/RN-307 e RN-311, e trocar a origem do dado não exigiu tocar em nenhuma transição.
- A metade pendente do escopo de dados de `MEMBER` — "contratos em que registrei horas" — foi
  fechada por `WorkLogSourceAdapters.MemberContractLinkAdapter`, dívida que `003` e `004`
  registraram por falta da tabela.
- `TenantSettingsService` passou a ser a fonte única dos padrões de `entities.md` §6.1.1. `008` e
  `009` os aplicam como regra de negócio (RN-113, RN-119, RN-120, RN-163, RN-164), e duas cópias
  divergiriam na primeira alteração — `MeResponseAssembler` passou a consumi-lo.

### Pendências desta sprint

- Frontend das três features (T-008-26 a T-008-35 e equivalentes de `009` e `011`): P21–P23, P16,
  o componente global de cronômetro e a sincronização entre abas. Não foi solicitado.
- Teste de concorrência de sobreposição (T-008-36) e teste de desempenho com 100.000 registros
  (T-008-43). Ambos exigem carga real; a garantia de RN-102 hoje se apoia na validação, no índice
  dedicado e no `WorkLogConsistencyJob`, e o risco residual continua declarado em R-01/OB-02.
- **Testes de integração não executados nesta máquina**: Testcontainers exige Docker, indisponível
  no ambiente. Eles compilam; as suítes puras — 395 testes, incluindo ArchUnit e as duas tabelas
  normativas — estão verdes.
- 3º elo da cadeia de RN-104 — `user.preferences.defaultCategoryId` —, que depende de `002-users`
  expor preferências. A cadeia degrada para ticket → contrato → primeira ativa, isolada em um único
  parâmetro.
- `RolloverExpiryJob` (RN-230) e `AutoClosePeriodJob` (CE-ME-02): dependem dos jobs de geração de
  período de `004`, ainda pendentes de S4.
- Criação do período seguinte quando ele não existe no fechamento (RN-229, FA-10): a geração de
  período pertence a `004` pela fronteira declarada na §4 de `specs/011`. O saldo fica preservado em
  `carriedOutMinutes` e é aplicado quando o próximo período for gerado.
- Notificações de `TIMER_LONG_RUNNING`, `TIMER_ABANDONED`, `CONTRACT_OVERAGE` e `PERIOD_CLOSED`: os
  eventos são publicados; a entrega é de `013-notifications`.
- Migrations em números reservados: `V015`, `V016`, `V018` e `V020` seguem `database.md` §8.1, que
  prevalece sobre `specs/*/tasks.md` (IA-11) e é o mesmo critério já adotado em `V013` e `V014`.
  Como `V025`–`V027` já foram aplicadas, um banco de desenvolvimento existente precisa ser recriado
  — `spring.flyway.out-of-order` permanece desabilitado por decisão.

**Sprint S2 — Autenticação (backend)** · `specs/001-authentication`

Escopo acordado: backend de `001-authentication`, com testes e documentação. Frontend
(T-001-39 a T-001-52) não foi solicitado e permanece fora — ver "Pendências desta sprint".
Exclusão de conta **não** entrou: é `POST /api/v1/tenant/cancel`, da feature `002-users` (§6.3),
e a spec de `001` a exclui explicitamente da §4.

Banco

- `V025__create_verification_tokens.sql` — token de uso único dos três fluxos que provam posse de
  e-mail. `consumed_at` e `invalidated_at` são colunas distintas: um link usado responde sucesso na
  segunda vez (§5.6, CA-08), enquanto um substituído por reenvio responde expirado (RN-457). Um
  único campo não distinguiria os casos, e o usuário que clicasse no e-mail antigo concluiria que
  ele valeu.
- `V026__create_rate_limit_counters.sql` — o "contador em banco no MVP" de `security.md` §8.1.
  Janela fixa, não deslizante: a deslizante exigiria uma linha por tentativa, multiplicando escritas
  justamente nos endpoints mais atacados.
- `V027__add_users_last_failed_login_at.sql` — sem esta coluna, RN-453 não tem janela: cinco erros
  de digitação espalhados por meses bloqueariam a conta.

Cadastro e verificação

- Cadastro atômico: organização, conta, vínculo `OWNER` ativo, as 9 categorias padrão (RN-501) e o
  token de verificação em **uma** transação (CE-01). Os identificadores são gerados antes dela
  porque o filtro de tenant é ativado na abertura da transação — sem isso o seed contaria as
  categorias de todos os tenants e concluiria que já existem.
- `SlugGenerator` com resolução de colisão por sufixo e fallback aleatório: o cadastro nunca falha
  por causa do slug (CX-03).
- Verificação de e-mail **idempotente** (CE-AU-04, CA-08) que também ativa os convites pendentes.
- Envio de e-mail exclusivamente após o commit (TX-06, CP-10): a indisponibilidade do provedor não
  desfaz cadastro, bloqueio de segurança nem troca de senha.

Sessão

- Login na ordem normativa da §6.1: bloqueio antes da senha, para que uma conta bloqueada não sirva
  de oráculo de senha correta; verificação de e-mail depois da senha, para não revelar cadastros.
  BCrypt executado mesmo sem usuário (AU-02).
- `LoginAttemptService` com `REQUIRES_NEW` no incremento de falhas: o login termina em `401`, e um
  contador que participasse da transação voltaria a zero a cada tentativa — RN-453 nunca bloquearia.
- Rotação de refresh com detecção de reuso. A revogação em cadeia commita em transação própria antes
  de o `401` subir, porque AC-001-31 exige os dois efeitos ao mesmo tempo. "Rotacionado" é
  verificado **antes** de "revogado": invertido, todo reuso seria classificado como logout (CX-06) e
  RN-005 nunca dispararia.
- `TenantContextFilter` passou a aplicar os passos 2 a 4 de `permissions.md` §4.1 — organização
  selecionada, situação da organização e do vínculo — por `SessionValidationService`, declarado em
  `shared` e implementado em `tenant` (inversão de dependência, não exceção a AR-01). Um membro
  removido deixa de operar imediatamente, em vez de esperar os 15 minutos do token (CE-AU-07).
- Cookie de refresh `HttpOnly`, `Secure`, `SameSite=Strict`, restrito a `/api/v1/auth`. O valor
  bruto sai apenas em `Set-Cookie`, nunca no corpo (CA-02).

Senha, sessões e convites

- Recuperação sempre `202`, com ou sem conta correspondente (PW-07, SG-02); redefinição de uso único
  que revoga todas as sessões e desbloqueia a conta (CX-07).
- Troca de senha preservando apenas a sessão corrente (RN-454).
- Listagem de sessões com IP parcialmente mascarado e ownership: sessão de outro usuário devolve
  `404`, nunca `403` (OWN-09, ART-024).
- Consumo e aceite de convite (RN-457). Aceite por quem já está autenticado **não** troca a
  organização da sessão corrente (CX-09).

Transversal

- Códigos `DEVTIME-1003` a `DEVTIME-1012` e `DEVTIME-2451` a `DEVTIME-2459` registrados conforme
  `docs/04-api/authentication.md` §8.
- Rate limit em `register`, `login`, `forgot-password` e `resend-verification`, com `Retry-After`. O
  IP vem de `getRemoteAddr()`, nunca de `X-Forwarded-For`: ler o header deixaria o cliente escolher
  o próprio identificador de limite.
- `MailPort` com adapter de log em `local`/`test` e SMTP em `staging`/`prod`; a porta **nunca lança**
  — a falha de envio é degradação prevista (AQ-09), medida por `auth.email.send_failures`.
- Jobs de limpeza de tokens e desbloqueio automático de contas.

### Corrigido

- `TenantContextNotInitializedException` respondia `500 DEVTIME-9001`. Passou a responder
  `401 DEVTIME-1002`: é o token de pré-seleção alcançando endpoint de negócio, estado previsto por
  CE-P-11 — e `500` faria o cliente tratar como indisponibilidade em vez de redirecionar para a
  seleção de organização.
- `uq_users_email` era traduzida para o `DEVTIME-2001` genérico. Passou a produzir `DEVTIME-2452`,
  para que a corrida entre cadastros simultâneos responda igual ao caminho verificado antes da
  inserção (CX-02, AC-001-40).
- O indicador de saúde de e-mail passou a ser desabilitado: o provedor é dependência degradável
  (`integrations.md` §4) e sua indisponibilidade não pode marcar a aplicação como fora do ar.

### Documentação

- `database.md` §8.1 e §7.12: acrescentadas `verification_tokens`, `rate_limit_counters` e
  `users.last_failed_login_at`, ausentes da sequência documentada.
- `entities.md` §6.19.1: entidade `VerificationToken`; §6.2: campo `lastFailedLoginAt`.
- `authentication.md`: `Location` no cadastro, estado de `activeTimer` em `/auth/me`, comportamento
  do aceite de convite e semântica de `userExists`.
- `specs/001-authentication/spec.md` §12 e `acceptance.md`: **conflito resolvido**. As duas
  atribuíam `DEVTIME-1003` a "credenciais inválidas" e `DEVTIME-1004` a "e-mail não verificado",
  divergindo de `docs/04-api/authentication.md` §8; e exigiam `410` no segundo uso do link de
  verificação, contra a idempotência de §5.6 e CA-08. Resolvido em favor de `docs/`, que
  `specs/README.md` §1 define como fonte normativa.

### Pendências desta sprint

- Frontend de `001` (T-001-39 a T-001-52): telas P01–P07, `AuthStore`, interceptor com fila de
  refresh e guards.
- `TenantPurgeJob`: depende do cancelamento de organização, que é `002-users`.
- `activeTimer` em `GET /auth/me`: depende de `009-timer`.
- Emissão de convites: pertence a `002-users`; aqui apenas se consome o token.
- `PW-03` pede as 10.000 senhas mais comuns; `security/common-passwords.txt` traz o núcleo dessa
  lista. Substituir o arquivo por um dump completo não exige alteração de código.

### Adicionado

**Sprint S4 — Etiquetas, Tickets e Comentários (backend)** · `specs/implementation-order.md` §4

Escopo acordado: backend de `006-tags`, `007-tickets` e `014-comments`, com testes e documentação.
O frontend das três features não foi solicitado e permanece fora — ver "Pendências desta sprint".

Etiquetas (`006`)

- Migrations `V009` (`tags`) e `V017` (`ticket_tags`), com índice único parcial sobre
  `(tenant_id, name)` (INV-TAG-02) e índice parcial de órfãs sobre `usage_count = 0`.
- `TagNormalizer` reproduzindo integralmente a tabela normativa da §6.1 da spec (RN-506):
  minúsculas, bordas aparadas, espaços internos colapsados e hifenizados. **Acentos preservados** e
  caracteres especiais não filtrados — `refatoração` e `refatoracao` coexistem por decisão (CX-02).
- Unicidade verificada sobre o nome **normalizado** (RN-507), com o índice parcial como barreira
  para a corrida entre criações simultâneas.
- `TagLinkService` com substituição atômica do conjunto de etiquetas do ticket, limite de 10 sobre o
  conjunto resultante (RN-313) e `usageCount` ajustado por `UPDATE ... SET x = x + ?` na mesma
  transação do vínculo (INV-TAG-04).
- Exclusão removendo os vínculos em lote e informando as contagens desvinculadas (§9.3 de
  `users.md`); sugestões de limpeza que **apenas sugerem** (RN-508).

Tickets (`007`)

- Migration `V014` com índice único `(contract_id, number)` (INV-TCK-01), os `CHECK` de INV-TCK-05,
  de INV-TCK-04 (`DONE` exige `completedAt`) e do motivo de impedimento.
- `TicketNumberGenerator` com lock consultivo de transação por contrato
  (`pg_advisory_xact_lock`): a serialização é obtida sem travar linhas, o que importa quando o
  contrato ainda não tem nenhum ticket — cenário em que `SELECT ... FOR UPDATE` sobre `tickets` não
  travaria nada e deixaria a primeira dupla de criações em corrida (RN-302, CP-03).
- `TicketKeyBuilder` reproduzindo a tabela normativa de chaves da §6.2 e a decomposição inversa,
  usada pela busca por chave legível.
- `TicketStateMachine` com as 49 células da matriz de `state-machines.md` §4.7 e
  `availableTransitions` por estado **e** permissão (ME-06).
- RN-310 exata: `startedAt` apenas na 1ª entrada em `IN_PROGRESS`, `completedAt` limpo em **toda**
  saída de `DONE`. `ActiveTimerGuard`, `BlockReasonValidator`, `AssigneeValidator`,
  `ContractMoveGuard` e `TicketDeletionGuard` como pontos únicos de RN-311, §4.7, RN-304, RN-305 e
  RN-307.
- Movimentação de contrato preservando `number` e chave (RN-011, CP-06); exclusão restrita com
  mensagem que aponta o cancelamento como caminho (RN-307, RN-314).
- `TicketTotalsService.applyWorkLogDelta` por incremento, nunca por reagregação (RN-308, CP-12), e
  `reopenOnWorkLog` aplicando RN-312 sem reversão na exclusão do work log (CX-06).
- Quadro servido por **uma** consulta agrupada com limite de 50 cartões por coluna (CP-14) e linha
  do tempo paginada por cursor, unindo auditoria e comentários por inversão de dependência.

Comentários (`014`)

- Migration `V022` com `CHECK (length(btrim(body)) BETWEEN 1 AND 10000)`, FK autorreferente e os
  quatro índices da §13.4 da spec.
- Hierarquia de um nível normalizada **na escrita** (RN-814): responder a uma resposta vincula à
  raiz, mantendo a árvore plana por construção.
- `MentionExtractor` resolvendo menções em duas consultas em lote, independentemente da quantidade,
  filtrando por membros ativos (RN-813). Menção não resolvida permanece como texto, sem erro;
  padrão de e-mail não é menção.
- `CommentEditPolicy` com janela estritamente menor que 24h (CX-09), `canEdit`/`canDelete`
  calculados no servidor e a distinção de §6.3: `ADMIN`/`OWNER` **excluem, mas não editam** —
  `COMMENT_UPDATE_ANY` não existe no catálogo de permissões.
- `SystemCommentListener` fecha a dívida OB-06 de `007`: os três gatilhos de RN-815 geram
  comentário de sistema **dentro** da transação da transição, sem ciclo entre as features — `007`
  publica o evento e não conhece `014`.

Transversal

- 16 códigos de erro de domínio registrados em `ErrorCode` (`DEVTIME-2104`, `23xx`, `2604`, `27xx`).
- `AuditService` ganha leitura (`findByEntity`) e contexto adicional em `metadata`, exigido por §18
  de `specs/007-tickets` para o `blockReason` e o `workLogId` que disparou a reabertura.
- Interfaces públicas mínimas de `002-users` publicadas por esta sprint: `MembershipService`
  (RN-304, RN-813) e `UserService` (exibição e menções). Escopo deliberadamente estreito — o ciclo
  de vida de usuário e membership continua pertencendo a `002`.
- OpenAPI descrevendo as 15 rotas novas, com os códigos de erro por resposta.

**Sprint S3 — Categorias, Clientes e Contratos (backend)** · `specs/implementation-order.md` §4

Escopo acordado: backend de `005-categories`, `003-clients` e `004-contracts` no recorte S3 (CRUD e
prévia de períodos). O frontend das três features e as dependências `001-authentication` e
`002-users` permanecem fora — ver "Pendências desta sprint".

Categorias (`005`)

- Migration `V008`, entidade `Category` e catálogo das 9 categorias de sistema de `entities.md`
  §6.10, com índice único parcial sobre `(tenant_id, lower(name))` (RN-502, INV-CAT-01).
- `CategoryService` com CRUD, inativação, reordenação atômica e exclusão na ordem normativa da §6.1
  da spec; `SystemCategoryGuard` (RN-503) e `CategoryReplacementValidator` (`DEVTIME-2605`).
- `CategorySeedService` exposto por `seedDefaults()`, idempotente (RN-501, CX-14).
- `DefaultCategoryResolver` com a cadeia ticket → contrato → usuário → primeira ativa (RN-104), que
  pula origens inativas e é determinística.

Clientes (`003`)

- Migrations `V010` e `V011`, entidades `Client` e `Contact` com o VO `Address` embutido.
- `DocumentValidator` de CPF e CNPJ por dígitos verificadores, rejeitando sequências repetidas
  (RN-402, CX-04), e `DocumentNormalizer` removendo máscara antes de validar e comparar (CX-03).
- CRUD com unicidade de nome e documento por tenant (RN-403, RN-404), busca sem acento e sem caixa,
  paginação, cor determinística derivada do nome, inativação com confirmação (RN-407) e exclusão
  restrita por contratos ativos (RN-401).
- `ContactService` com `PrimaryContactPolicy` (RN-406) e limite de 20 contatos por cliente.

Contratos (`004`)

- Migrations `V012` e `V013`, incluindo a constraint `EXCLUDE USING gist` de INV-PER-02, o índice
  único parcial de período `OPEN` (INV-PER-07) e as constraints de coerência de tipo
  (INV-CTR-02/03/04).
- `PeriodGenerator` e `ProrationCalculator` reproduzindo a tabela normativa de geração e o exemplo
  de rateio de RN-217 (1.703 minutos), com aritmética inteira — sem ponto flutuante em nenhum passo.
- `ContractStateMachine` com a matriz completa de `state-machines.md` §4.5 e `availableTransitions`.
- CRUD com código sequencial `CT-0001` por tenant, prévia de períodos sem persistência, ativação
  gerando o 1º período `OPEN` na mesma transação (RN-209, INV-CTR-06), suspensão, retomada com
  geração dos períodos faltantes (CE-ME-09), encerramento e cancelamento com truncamento (RN-214),
  exclusão restrita a `DRAFT` (RN-205) e histórico de períodos.
- `ContractChangeGuards` aplicando RN-207 e RN-208.

Transversal

- `AuditService` gravando a trilha na mesma transação da alteração (RN-006), com ator de sistema
  para a geração de períodos.
- 44 códigos de erro de domínio registrados em `ErrorCode` (`DEVTIME-22xx`, `24xx` e `26xx`).
- OpenAPI descrevendo as 21 rotas da sprint, com códigos de erro por resposta.

**Sprint S1 — Fundação técnica (F0)** · `specs/implementation-order.md` §3

Backend

- Projeto Spring Boot 3 com Java 21, pacote `com.devtime`, organizado por feature (ADR-027).
- `BaseEntity` e `TenantScopedEntity` com UUIDv7 gerado na aplicação, auditoria, exclusão lógica e
  concorrência otimista (ART-010, ART-050 a ART-052).
- `TenantContext`, `TenantContextFilter` e `TenantAwareInterceptor`: o `tenant_id` vem exclusivamente
  da claim `tid` do JWT e o filtro Hibernate é ativado em toda abertura de sessão (ART-021, ART-022).
- Anotação `@CrossTenant` como marcador de revisão para as exceções previstas em `backend.md` §7.4.
- Spring Security com JWT `HS256`, allowlist pública exaustiva, CORS por ambiente e os cabeçalhos de
  segurança de `security.md` §8.2.
- Enums `Role` e `Permission` com a matriz Papel × Permissão de `permissions.md` §7 e
  `DevTimePermissionEvaluator` para `@PreAuthorize`.
- Tratamento global de exceções em RFC 7807 com códigos `DEVTIME-XXXX`, mapeamento de constraint por
  nome e `traceId` em toda resposta (ADR-017).
- Log estruturado em JSON com máscara obrigatória de dados sensíveis (ART-084).
- `TenantClock`, `DateRange`, `DomainEventPublisher`, `PageResponse` e `PageRequestFactory`.
- OpenAPI 3.1 gerado a partir do código (ART-076).
- Migrations Flyway `V001`–`V007`: extensões, `tenants`, `users`, `memberships`, `refresh_tokens`,
  `audit_logs` particionada e `shedlock`.
- Entidades `Tenant`, `User`, `Membership`, `RefreshToken` e `AuditLog` com seus repositórios. Nenhum
  serviço nem endpoint de negócio.

Frontend

- Projeto Angular standalone com Signals, `OnPush` obrigatório, PrimeNG e PrimeFlex.
- Tokens de design `--dt-*` e tema claro/escuro conforme `design-system.md` §5 a §7 e §15.
- `AuthStore`, `TokenStorage` com access token apenas em memória e `AuthService` com fila única de
  refresh (FR-066, FR-068).
- Guards `authGuard`, `tenantSelectedGuard`, `guestGuard` e `permissionGuard`.
- Interceptors na ordem obrigatória de `frontend.md` §7.2: loading, auth, tenant, retry e error.
- Shell da aplicação (L2), layout de autenticação (L1), tela de login (P01), páginas de acesso negado
  e de recurso não encontrado.
- `DurationPipe` (`HH:MM`) e diretiva `dtHasPermission`.
- i18n com `@angular/localize` e mapa de mensagens por código `DEVTIME-XXXX`.

Infraestrutura

- Dockerfile multi-stage do backend e do frontend, `infra/docker-compose.yml` com PostgreSQL 16 e
  `.env.example` sem segredos.

Testes

- ArchUnit cobrindo `AR-01` a `AR-09` e as regras de persistência `BR-020` a `BR-035`.
- Suíte de isolamento entre dois tenants (critério de saída F0-01).
- Testes de `JwtService` (incluindo rejeição de `alg=none`), da matriz de permissões, da máscara de
  log, do contrato RFC 7807 e das migrations a partir de banco limpo.

Documentação

- `README.md` com o ambiente em 3 comandos (RE-03) e este `CHANGELOG.md` (RE-04).
- `ai/coding-guidelines.md` §5: árvore do repositório corrigida para `devtime-backend/` e
  `devtime-frontend/`, conforme ADR-022.

### Corrigido — Sprint S4

- **Escopo de dados de `MEMBER` sobre clientes.** A S3 deixou o escopo fechado por padrão porque
  `tickets` e `work_logs` não existiam ("as subconsultas `EXISTS` entram com `007`/`008`"). Com
  `tickets`, a metade que depende deles foi implementada: um contrato é visível ao membro quando ele
  é relator ou responsável de algum ticket nele, e um cliente é visível quando possui contrato
  visível (§9 de `permissions.md`). O grafo de features permanece acíclico por inversão — `client`
  declara `MemberScopeSource`, `contract` declara `MemberContractLinkSource`, e cada uma é
  implementada pela feature que possui a informação.
- **Ticket ilegível para `MEMBER`.** Como consequência do item acima, um `MEMBER` não conseguia
  abrir ticket algum: a resposta embute contrato e cliente, e o escopo fechado devolvia `404`.
  `ClientService.getRefById` e `ContractService.getRefById` passam a servir a identificação embutida
  sem aplicar o escopo de carteira — comportamento previsto e aceito em OB-04 de `specs/007`
  (`MEMBER` enxerga todos os tickets do tenant). Listar e detalhar clientes segue restrito.
- **Acoplamento entre features na fronteira do contrato.** `ContractResponse` expõe o enum
  `ContractStatus`, e consumi-lo em `007` violaria AR-02. `ContractRefResponse` devolve a situação
  como texto e `acceptsWorkLogs` já decidido por quem é dono de RN-306. Pelo mesmo motivo,
  `TicketStatusChangedEvent` carrega o nome da situação, não o enum.
- **Parâmetro opcional de texto em JPQL.** `(:param IS NULL OR ...)` com um `String` nulo faz
  Hibernate 6 enviar o parâmetro sem tipo, e o PostgreSQL recusa a comparação (`operator does not
  exist: character varying ~~ bytea`). As consultas afetadas passam a usar `cast(... as String)` e a
  receber o padrão de `LIKE` já montado.

### Conflitos de documentação — Sprint S4

Encontrados **antes** da implementação e resolvidos pela hierarquia de IA-11 (`project-constitution`
> `02-domain/` > `03-architecture/` > `04-api/` > `specs/`), conforme CE-G-02 manda: seguir a
hierarquia **e** reportar. Nenhum deles exigiu inventar regra de negócio.

| # | Conflito | Documentos | Resolução |
|:--:|---|---|---|
| C-01 | **Chave do ticket ao mover de contrato.** `04-api/tickets.md` §8.3 determinava um **novo** `number` e uma **nova** `key` no contrato de destino, com `previousKey` e aviso de quebra de referência. `entities.md` §6.12 marca ambos como imutáveis (🔒), RN-011 proíbe alterá-los, e `specs/007` §6.2, CX-04, CP-06, OB-01 e CA-12 exigem que permaneçam | `02-domain/entities.md`, `business-rules.md` × `04-api/tickets.md` | 02-domain prevalece: a chave **não** muda. `04-api/tickets.md` §8.3 foi corrigida neste PR (ART-111) |
| C-02 | **`key` persistida ou derivada.** `entities.md` §6.12 marca 📐 (campo derivado, não persistido) e `database.md` §7.7 não declara a coluna; `specs/007` §13.2 e §13.4 a descrevem persistida, com índice único `uq_tickets_tenant_key` | `02-domain/`, `03-architecture/` × `specs/007` | Derivada. `V014` não cria a coluna; a busca por chave decompõe o valor e resolve por `uq_tickets_contract_number` |
| C-03 | **`DEVTIME-2706` com dois significados.** `specs/014` §12 o atribui a `parentCommentId` inválido (422); `04-api/tickets.md` §10.2 e §13 o atribuem à janela de edição expirada (409) | `04-api/tickets.md` × `specs/014` | 04-api prevalece. Origem inválida responde `DEVTIME-2002`/404 (ART-024) e a decisão está documentada em `tickets.md` §10.2 |
| C-04 | **Numeração das migrations.** `specs/006` pede `V017`/`V018`, `specs/007` pede `V019`–`V021` e `specs/014` pede `V037`; `database.md` §8.1 aloca `V009`, `V014`, `V017` e `V022` | `03-architecture/database.md` × `specs/` | `database.md` prevalece, como já ocorrera em `V008` na S3 |
| C-05 | **Contagem de células da matriz de ticket.** `specs/007/tasks.md` T-007-08 fala em "22 válidas e 27 proibidas"; a matriz de `state-machines.md` §4.7 tem **19** válidas (a soma da spec não fecha 49 sob nenhuma contagem) | `02-domain/state-machines.md` × `specs/007` | A matriz prevalece. O teste cobre as 49 células e afirma 19 válidas |
| C-06 | **FK de `assignee_id`.** `specs/007` §17.3 aponta para `memberships.user_id`, que não é único isoladamente — um usuário participa de vários tenants — e portanto não pode ser alvo de FK; `database.md` §7.7 aponta para `users` | `03-architecture/database.md` × `specs/007` | FK para `users`; a validação de membership `ACTIVE` (RN-304) é da aplicação |
| C-07 | **`DEVTIME-9002` com dois significados.** `ADR-017` e `ADR-045` o atribuem a *rate limit* (`429`) e o código já o implementa assim; `state-machines.md` §7 e `specs/006`/`007` §17.3 o atribuem a estado inconsistente / violação de `CHECK` | `02-domain/state-machines.md` × `docs/adr/` + código | **Não resolvido nesta sprint** e sem impacto no escopo entregue: as violações de `CHECK` continuam caindo em `DEVTIME-9001` pelo comportamento de fechar-por-padrão do `ConstraintViolationMapper`. Exige decisão do Tech Lead: renumerar o código de *rate limit* (mudança de contrato público) ou emendar `state-machines.md` §7 |

### Verificado — Sprint S4

- Backend: **545 testes verdes** (unitários + integração com Testcontainers em PostgreSQL 16).
  Cobertura global de linhas 89,9% (gate 80%) e 92,8% em services, validators, policies e guards
  (meta ART-100: 90%).
- **Duas suítes escritas antes do código** (SQ-02): a tabela normativa de normalização de etiquetas
  (§6.1 de `specs/006`) e a matriz 7×7 do ticket, célula a célula, com aceitação **e** rejeição. A
  matriz esperada é transcrita do documento, não importada da implementação — uma suíte que
  consultasse a própria máquina provaria apenas que ela é consistente consigo mesma.
- **Atomicidade da sequência de `number` comprovada**: 100 criações simultâneas no mesmo contrato
  produzem 100 números distintos e consecutivos, sem lacuna (CA-02 de `specs/007`).
- Janela de 24h dos comentários verificada com `Clock` controlado em 0h, 1h, 23h, 23h59, 24h, 25h e
  30 dias — 24h exatas já está fora (CX-09).
- Isolamento entre tenants verde nos endpoints novos, por id **e** por chave legível; recurso de
  outro tenant responde `404` (ART-024).
- Migrations `V009`, `V014`, `V017` e `V022` aplicam do zero; `ddl-auto=validate` aprovou o
  mapeamento. Verificado por consulta ao catálogo que `tickets` **não** possui coluna `key` e que o
  índice `uq_tickets_contract_number` é parcial.

### Pendências — Sprint S4

Reportadas antes do início e confirmadas na entrega.

- **Frontend das três features** (T-006-10 a 14, T-007-23 a 32, T-014-10 a 15): não solicitado nesta
  sprint. SQ-09 exige que uma feature entregue backend **e** frontend; enquanto o frontend não
  existir, `006`, `007` e `014` permanecem `BACKEND_DONE`, não `DONE`.
- **Dependências não implementadas.** `001-authentication` e `002-users` continuam ausentes. Esta
  sprint publicou apenas as interfaces mínimas que RN-304 e RN-813 exigem (`MembershipService`,
  `UserService`); cadastro, convite, papel e ciclo de vida seguem pertencendo a `002`.
- **`ActiveTimerGuard` não bloqueia nada** enquanto `009-timer` não existir: sem a tabela `timers`,
  nenhum cronômetro pode existir. RN-311 tem ponto único de aplicação e testes que já passam por ele.
- **`TicketWorkLogGate` deriva a existência de horas de `spentMinutes`** enquanto `008` não publica
  `WorkLogService`. A derivação é exata (RN-115 exige `netMinutes > 0`), e a substituição pela
  contagem real é de uma linha.
- **Work logs na linha do tempo e escopo de horas de `MEMBER`** (IMP-02) entram com `008`, junto da
  fonte de eventos correspondente.
- **`work_log_tags` e `TagLinkService.linkToWorkLog`** entram com `008` (CE-O-03); `V017` cria apenas
  `ticket_tags`.
- **`DenormalizationReconcileJob`** continua inexistente — é compartilhado com `003`, `004` e `011` e
  também não foi construído na S3. Os pontos de reconciliação de `usageCount` e dos totais do ticket
  ficam para quando o job existir, e não foram deixados como código sem chamador (CG-09).
- **`TagCleanupSuggestionJob`** não implementado: registrar o instante exato em que `usageCount`
  chegou a zero exigiria um campo que `entities.md` §6.11 não define. As sugestões de RN-508 são
  calculadas ao vivo sobre `updatedAt`, que é o que o índice documentado em §13.4 sustenta.
- **`TagService.getAllForReport`** e **`CommentService.existsForComment`** só ganham consumidor em
  `012` e `015`; o segundo foi publicado, o primeiro não, para não deixar consulta sem chamador.
- **Busca sem acento** em tickets não é oferecida: exigiria a extensão `unaccent`, que não consta das
  instaladas em `V001`. A busca é sem diferenciar caixa, sobre título e descrição.
- **`DEVTIME-9002`** permanece com dois significados na documentação (C-07 abaixo).

### Verificado — Sprint S3

- Backend: **319 testes verdes** (unitários + integração com Testcontainers em PostgreSQL 16).
  Cobertura global de 88,6% (gate 80%) e 91,6% em services e validators (meta 90%).
- Suítes temporais de `004` escritas **antes** do gerador (regra SQ-02): os 5 cenários normativos de
  geração, 1.120 combinações de `startDate` × `billingDay` verificando contiguidade (INV-PER-03) e a
  matriz completa de transições, célula a célula, incluindo as proibidas.
- Constraints estruturais provadas por `INSERT` direto, contornando a aplicação: sobreposição de
  períodos, segundo período `OPEN` e sequência duplicada são rejeitados pelo banco.
- Isolamento entre tenants verificado nas três features; recurso de outro tenant responde `404`.
- Migrations `V008`, `V010`–`V013` aplicam do zero; `ddl-auto=validate` aprovou o mapeamento.

### Pendências — Sprint S3

Reportadas antes do início e confirmadas na entrega.

- **Dependências não implementadas.** `001-authentication` e `002-users` não existem: não há endpoint
  de login, e o gatilho de criação de tenant que deve chamar `CategoryService.seedDefaults()`
  (RN-501) pertence a `002`. A sprint foi executada sobre a fundação F0 por decisão explícita.
- **Frontend das três features** (T-005-12 a 17, T-003-15 a 22, T-004-32 a 46) fora do escopo
  acordado.
- **Jobs de S4** de `004`: `GeneratePeriodsJob` (RN-213), `OpenScheduledPeriodsJob`,
  `AutoEndContractsJob` e `ContractEndingReminderJob`.
- **Guarda de cronômetro ativo** em `suspend` e `end` (`DEVTIME-2212`): depende de `009-timer`.
- **Migração de work logs na exclusão de categoria** (RN-505) e estatística de uso: dependem de
  `008-worklogs`.
- **Escopo de dados de `MEMBER`** sobre clientes: aplicado na consulta, porém fechado por padrão —
  a definição de "cliente vinculado" depende de `work_logs` e `tickets`. As subconsultas `EXISTS`
  entram com `007`/`008`.

### Corrigido — Sprint S1

Defeitos encontrados pelos próprios gates da sprint de fundação, antes de qualquer feature depender
deles.

- **Isolamento entre tenants em `findById`.** O `@Filter` de Hibernate não é aplicado a
  `EntityManager.find()`, que é o que o `findById` padrão de Spring Data usa — então
  `repository.findById()` retornava registros de outro tenant, violando ART-022 e ART-024.
  `SoftDeleteRepository` passa a sobrescrever `findById` com JPQL, onde o filtro volta a valer. Regra
  ArchUnit adicional proíbe `getReferenceById`, que carrega um proxy e não pode ser filtrado.
- **Chave de assinatura do JWT.** `JwtService` tentava decodificar Base64 antes de recorrer a UTF-8.
  Decodificadores Base64 descartam caracteres inválidos em silêncio, então um segredo forte contendo
  `-` ou `!` produzia uma chave muito mais curta que o esperado — fraqueza criptográfica invisível na
  configuração. Passa a usar um único formato: bytes UTF-8.
- **Máscara de log.** `SensitiveDataMasker` mascarava apenas o esquema `Bearer` do cabeçalho
  `Authorization` e deixava o token exposto na sequência, violando ART-084.
- **Formato das respostas de erro.** `spring.mvc.problemdetails.enabled=true` fazia o Spring Boot
  registrar seu próprio handler, que respondia antes do `GlobalExceptionHandler` e produzia corpo RFC
  7807 sem as extensões obrigatórias `code`, `traceId` e `errors[]` (ART-072). Desabilitado, e o
  `GlobalExceptionHandler` passa a declarar precedência máxima.
- **Tipos `CHAR` no mapeamento JPA.** `tenants.currency`, `memberships.cost_currency` e
  `address_country` são `CHAR` no schema (database.md §4.2, ART-041), mas o padrão de Hibernate para
  `String` é `varchar` — `ddl-auto=validate` recusava a inicialização. Declarado
  `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Configuração de log que escondia falhas de inicialização.** Os appenders estavam declarados dentro
  de blocos `<springProfile>` que listavam perfis; sem perfil ativo nenhum bloco casava e o contexto
  ficava sem appender, descartando a mensagem de por que a aplicação não subia. O bloco padrão passa a
  usar a negação dos demais.
- **Build não reprodutível entre plataformas.** Arquivos em CRLF no Windows faziam o mesmo
  `spotless:check` falhar em Linux (imagem Docker e pipeline). Adicionado `.gitattributes` com
  `eol=lf` e fixado `<lineEndings>UNIX</lineEndings>` no Spotless.
- **Imagem do frontend servia a página padrão do nginx.** Com `localize: true`, o Angular emite a
  saída em subdiretório por locale (`browser/pt-BR/`); o `COPY` apontava um nível acima.
- **Cabeçalhos de segurança perdidos em rotas estáticas.** `add_header` em bloco `location` substitui
  os herdados do `server` em vez de somar, então `/index.html` e os assets respondiam sem
  `X-Content-Type-Options`, `X-Frame-Options` e `Referrer-Policy`. Extraídos para
  `security-headers.conf`, incluído em cada bloco.
- **Mensagens de validação de configuração.** O texto padrão do Bean Validation não indicava de onde o
  valor deveria vir; `DEVTIME_JWT_SECRET` agora explica o que definir e como gerar (ER-04).

### Verificado — Sprint S1

- Backend: 124 testes verdes (88 unitários + 36 de integração com Testcontainers), Spotless e gate de
  cobertura JaCoCo de 80% atendidos.
- **Regra F0-01 cumprida:** suíte de isolamento entre dois tenants verde.
- Migrations `V001`–`V007` aplicadas do zero em PostgreSQL 16 limpo; `ddl-auto=validate` aprovou o
  mapeamento; `audit_logs` criada particionada com 12 partições; extensões `pgcrypto`, `btree_gist` e
  `pg_trgm` instaladas.
- Frontend: 36 testes verdes, lint limpo, build de produção em 165,72 kB gzip (limite FR-167: 500 kB).
- Ambiente completo no Docker Compose: PostgreSQL, backend e frontend saudáveis; endpoint protegido
  responde `401` (ART-085), health check público, todos os cabeçalhos de §8.2 presentes, roteamento da
  SPA resolvendo `/dashboard` no index (FR-089).

### Pendências — Sprint S1

- Pipeline CI com os gates de `architecture.md` §11 fora do escopo acordado para a sprint.
- Angular 21.2.19 em vez da última versão estável (22.x) exigida por ART-090: o Angular 22 requer
  Node ≥ 22.22.3 e o ambiente tem 22.19.0.
- `CA-09` de `database.md` (impossibilidade de `UPDATE` em `audit_logs`) não verificável: a topologia
  de papéis do banco não está especificada.
