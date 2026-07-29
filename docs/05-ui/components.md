# Componentes — DevTime

## 1. Objetivo

Especificar o catálogo de componentes da interface: componentes PrimeNG utilizados, componentes customizados do DevTime, suas propriedades, estados, comportamentos, acessibilidade e regras de uso. Nenhum componente pode ser criado sem constar aqui.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Catálogo de componentes PrimeNG e customizados | Tokens visuais (`design-system.md`) |
| Contratos de entrada/saída e estados | Estrutura de página (`layouts.md`) |
| Comportamento, validação e acessibilidade | Composição de telas (`pages.md`) |
| Regras de uso e alternativas rejeitadas | Implementação técnica (`03-architecture/frontend.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Componente de apresentação** | Sem dependência de serviço; recebe `input()` e emite `output()`. |
| **Componente conectado** | Injeta store ou serviço; orquestra dados. |
| **Componente composto** | Combina outros componentes com regra própria. |
| **Slot** | Área de conteúdo projetado (`ng-content`). |

---

## 4. Regras gerais

| # | Regra | Origem |
|---|---|---|
| CP-01 | Prefixo de seletor `dt-` | ART-090 |
| CP-02 | `ChangeDetectionStrategy.OnPush` obrigatório | ART-092 |
| CP-03 | `input()`/`output()` baseados em Signals | §9.2 de `frontend.md` |
| CP-04 | Componentes de `shared/` **nunca** injetam serviço de dados | FR-02 |
| CP-05 | Toda cor, espaçamento e tamanho vem de tokens | CA-01 de `design-system.md` |
| CP-06 | Nenhum texto fixo — sempre i18n | ART-095 |
| CP-07 | Todo elemento interativo possui rótulo acessível | AC-04 |
| CP-08 | Preferir o componente PrimeNG existente a criar um customizado | ART-093 |
| CP-09 | Componente customizado exige justificativa do que o PrimeNG não atende | Revisão |

---

## 5. Componentes PrimeNG utilizados

| Componente | Uso no DevTime | Configuração obrigatória |
|---|---|---|
| `p-button` | Todas as ações | `severity` conforme a hierarquia da §6.1 |
| `p-inputText` | Texto curto | Sempre com `<label>` associado |
| `p-inputTextarea` | Descrições | `autoResize`, contador de caracteres |
| `p-inputNumber` | Valores numéricos | `locale="pt-BR"`, `mode` conforme o dado |
| `p-calendar` | Datas | `dateFormat="dd/mm/yy"`, `showIcon` |
| `p-dropdown` | Seleção única | `filter` quando houver mais de 10 opções |
| `p-multiSelect` | Seleção múltipla | `display="chip"`, `filter` |
| `p-autoComplete` | Busca de ticket, cliente e contrato | `forceSelection`, `minLength=2`, debounce de 300ms |
| `p-checkbox` / `p-radioButton` | Opções | Sempre com `<label>` clicável |
| `p-inputSwitch` | Ativação | Rótulo à esquerda |
| `p-selectButton` | Escolha entre 2–4 opções | Para políticas de contrato |
| `p-table` | Todas as listagens | `lazy`, `paginator`, `sortMode="multiple"` |
| `p-paginator` | Paginação | `rowsPerPageOptions=[10,20,50,100]` |
| `p-dialog` | Diálogos | `modal`, `dismissableMask=false` em ações destrutivas |
| `p-confirmDialog` | Confirmações | Via `ConfirmationService` |
| `p-toast` | Feedback efêmero | `position="bottom-right"`, `life=3000` |
| `p-message` / `p-messages` | Erros e avisos inline | `severity` conforme o design system |
| `p-progressBar` | Consumo e progresso | `showValue` |
| `p-tag` | Selos de status | Cor conforme a severidade |
| `p-chip` | Tags e filtros ativos | `removable` em filtros |
| `p-badge` | Contadores | Notificações não lidas |
| `p-menu` / `p-tieredMenu` | Menus contextuais | — |
| `p-tabView` | Abas de detalhe | Sincronizado com a URL |
| `p-steps` | Wizard | — |
| `p-chart` | Gráficos (Chart.js) | Tema aplicado por token |
| `p-skeleton` | Carregamento | Reproduz a estrutura final |
| `p-tooltip` | Dicas e atalhos | `showDelay=500` |
| `p-overlayPanel` | Painéis flutuantes | Notificações, filtros |
| `p-fileUpload` | Anexos | `customUpload`, validação prévia |
| `p-avatar` | Usuários | Iniciais como fallback |
| `p-divider` | Separação | — |
| `p-breadcrumb` | Trilha de navegação | — |

### 5.1 Componentes PrimeNG explicitamente **não** utilizados

| Componente | Motivo |
|---|---|
| `p-carousel` | Nenhum conteúdo do produto justifica rotação automática |
| `p-galleria` | Sem galeria de imagens |
| `p-scrollTop` | Rolagem interna à área de conteúdo torna o comportamento confuso |
| `p-terminal` | Sem aplicação |
| `p-organizationChart` | Sem hierarquia visual de organização |
| `p-speedDial` | Conflita com o padrão de ação primária única (DS-06) |

---

## 6. Componentes customizados

### 6.1 `dt-button` — política de uso

Não é um componente novo; é a **política de aplicação** de `p-button`.

| Hierarquia | `severity` | Uso | Máximo por tela |
|---|---|---|---|
| Primária | `primary` (preenchido) | A ação principal da tela | **1** |
| Secundária | `secondary` (contorno) | Ações de apoio | 3 |
| Terciária | `text` | Ações de baixa relevância | Sem limite |
| Destrutiva | `danger` | Excluir, descartar, cancelar contrato | 1 por contexto |

| # | Regra |
|---|---|
| BT-01 | Rótulo sempre verbo no infinitivo: "Salvar", "Registrar horas", "Fechar período" |
| BT-02 | Botão com atalho exibe a tecla no tooltip |
| BT-03 | Botão apenas com ícone exige `aria-label` |
| BT-04 | Durante o envio: `loading=true`, rótulo mantido, botão desabilitado |
| BT-05 | Ação destrutiva sempre passa por confirmação |
| BT-06 | Botão nunca é desabilitado por formulário inválido (FM-04 de `frontend.md`) |

---

### 6.2 `dt-duration-input`

**Justificativa:** nenhum componente PrimeNG aceita entrada flexível de duração. Este é o campo mais usado do produto (RF-111) e seu atrito impacta diretamente a adoção (PR-01).

| Entrada | Interpretação | Resultado |
|---|---|---|
| `90` | Minutos | 90 min |
| `1:30` | Horas:minutos | 90 min |
| `1h30` | Horas e minutos | 90 min |
| `1h30m` | Idem | 90 min |
| `1,5h` | Horas decimais | 90 min |
| `1.5h` | Idem | 90 min |
| `90m` | Minutos | 90 min |
| `2h` | Horas | 120 min |

**Contrato:**

| Propriedade | Tipo | Descrição |
|---|---|---|
| `value` | `number \| null` | Minutos (modelo bidirecional) |
| `min` / `max` | `number` | Limites em minutos; default 1 e 1440 |
| `showQuickOptions` | `boolean` | Exibe atalhos 15m, 30m, 1h, 2h, 4h |
| `allowNegative` | `boolean` | Para ajustes de saldo; default `false` |
| `valueChange` | `output<number>` | Emite ao confirmar |

| # | Regra |
|---|---|
| DI-01 | Ao perder o foco, o valor é normalizado para `HH:MM` |
| DI-02 | Entrada inválida mantém o texto e exibe erro sob o campo, sem apagar o digitado |
| DI-03 | Setas para cima e para baixo ajustam em 15 minutos |
| DI-04 | O valor emitido é sempre inteiro em minutos (ART-034) |
| DI-05 | Fonte monoespaçada |

---

### 6.3 `dt-timer-bar`

**Barra global do cronômetro.** Componente conectado ao `TimerStore`.

| Estado | Aparência | Ações |
|---|---|---|
| Sem cronômetro | Não renderizada | — |
| `RUNNING` | Fundo `--dt-color-primary`, ícone pulsante | Pausar, Parar, Trocar ticket |
| `PAUSED` | Fundo `--dt-color-warning`, ícone estático, tempo congelado | Retomar, Parar, Descartar |
| Rodando há mais de 8h | Aviso inline adicional | Idem + "Encerrar agora" |
| Reconectando | Indicador de sincronização; tempo continua localmente | Ações desabilitadas |

| # | Regra |
|---|---|
| TB-01 | O tempo é derivado do estado do servidor (RN-151); o contador local apenas anima os segundos |
| TB-02 | Ressincroniza a cada 60s, ao ganhar foco e ao voltar a conexão |
| TB-03 | "Parar" abre o diálogo de descrição, nunca encerra diretamente |
| TB-04 | "Descartar" exige confirmação com o tempo que será perdido explicitado |
| TB-05 | Falha ao encerrar mantém a barra ativa e exibe o erro com a correção sugerida (RN-160) |
| TB-06 | O tempo decorrido aparece no título da aba do navegador (RF-151) |
| TB-07 | Atalho `T` alterna iniciar/parar |

---

### 6.4 `dt-balance-bar`

Barra de progresso de consumo do contrato.

| Propriedade | Tipo | Descrição |
|---|---|---|
| `availableMinutes` | `number` | Total disponível |
| `consumedMinutes` | `number` | Consumido |
| `size` | `'sm' \| 'md' \| 'lg'` | Densidade |
| `showLabels` | `boolean` | Exibe valores numéricos |
| `showProjection` | `boolean` | Marca a projeção de consumo |

**Comportamento visual:**

```
Saudável (41%)   ▓▓▓▓░░░░░░  verde   ✓ 16:24 / 40:00 · restam 23:36
Atenção (84%)    ▓▓▓▓▓▓▓▓░░  âmbar   ⚠ 33:36 / 40:00 · restam 06:24
Excedido (105%)  ▓▓▓▓▓▓▓▓▓▓  vermelho ✕ 42:00 / 40:00 · excedente 02:00
                            ┆ projeção
```

| # | Regra |
|---|---|
| BB-01 | A cor segue exatamente a tabela de severidade (§5.3 de `design-system.md`) |
| BB-02 | Ícone e texto sempre acompanham a cor (DS-05) |
| BB-03 | Acima de 100%, a barra permanece cheia e o excedente é indicado por textura e texto |
| BB-04 | A projeção aparece como marcador tracejado sobre a barra |
| BB-05 | `role="progressbar"` com `aria-valuenow`, `aria-valuemin`, `aria-valuemax` e `aria-label` descritivo |
| BB-06 | Contrato `HOURLY_OPEN` renderiza apenas o total consumido, sem barra |

---

### 6.5 `dt-balance-statement`

Extrato explicativo do saldo — materializa o momento de verdade MV-02.

| Propriedade | Tipo |
|---|---|
| `statement` | `PeriodStatement` (resposta de `/statement`) |
| `expandable` | `boolean` |

**Renderização:**

| Linha | Estilo |
|---|---|
| `CONTRACTED`, `CARRIED_IN`, `ADJUSTMENT` | Texto normal, valor à direita com sinal `+` |
| `SUBTOTAL_AVAILABLE` | Negrito, com linha superior |
| `CONSUMED` | Valor com sinal `−`, clicável (abre os registros) |
| `BALANCE` | Negrito, cor por sinal, com linha superior dupla |
| `NON_BILLABLE` | Cor secundária, marcado como informativo |

| # | Regra |
|---|---|
| BS-01 | Linhas de valor zero são exibidas, nunca ocultadas (EX-02) |
| BS-02 | Cada ajuste exibe motivo, justificativa, autor e data ao expandir |
| BS-03 | Linhas com `drillDown` são navegáveis por clique e por teclado |
| BS-04 | Renderizada como `<table>` com cabeçalhos associados (A11Y-12) |
| BS-05 | Falha no cálculo exibe "—" com alerta, nunca um número (CE-D-05) |

---

### 6.6 `dt-contract-card`

| Propriedade | Tipo |
|---|---|
| `contract` | `ContractSummary` |
| `compact` | `boolean` |

**Composição:** nome do contrato, cliente com cor de identificação, selo de status, `dt-balance-bar`, dias restantes, indicador de projeção e ação rápida de registrar horas.

| # | Regra |
|---|---|
| CC-01 | O card inteiro é clicável e navega para o contrato |
| CC-02 | A ação rápida não propaga o clique do card |
| CC-03 | Severidade `CRITICAL` recebe borda esquerda em `--dt-color-danger` |
| CC-04 | Projeção de estouro exibe aviso: "No ritmo atual, excederá em 3 dias" |

---

### 6.7 `dt-work-log-form`

Formulário de registro de horas. Componente composto.

**Campos, em ordem de tabulação:**

| # | Campo | Componente | Obrigatório |
|---|---|---|:--:|
| 1 | Ticket | `p-autoComplete` com criação inline | ✔ |
| 2 | Data | `p-calendar` | ✔ |
| 3 | Hora inicial | `dt-time-input` | ✔ |
| 4 | Duração ou hora final | `dt-duration-input` com alternador | ✔ |
| 5 | Categoria | `p-dropdown` | ✔ |
| 6 | Descrição | `p-inputTextarea` | ✔ |
| 7 | Faturável | `p-inputSwitch` | ✖ |
| 8 | Tags | `p-multiSelect` | ✖ |

| # | Regra |
|---|---|
| WF-01 | Máximo de 4 campos obrigatórios visíveis por padrão; tags e faturável ficam em "mais opções" (ID-02) |
| WF-02 | Selecionar o ticket exibe cliente, contrato e saldo atual em painel lateral |
| WF-03 | A categoria é pré-selecionada conforme RN-104 |
| WF-04 | Validação em tempo real via `POST /work-logs/validate`, com debounce de 500ms |
| WF-05 | Sobreposição detectada exibe o registro conflitante com ação de ajuste em um clique |
| WF-06 | O impacto no saldo é exibido antes de salvar: "Após salvar: restam 04:54 (89%)" |
| WF-07 | Aviso de estouro aparece antes do envio, não apenas depois |
| WF-08 | "Salvar e criar outro" mantém ticket, categoria e data, limpando horário e descrição |
| WF-09 | Criar ticket inline abre um diálogo com apenas título e contrato |

**Justificativa de WF-09:** RN-101 exige ticket para todo registro. Sem criação inline, o usuário que esqueceu de criar o ticket precisaria abandonar o formulário — atrito inaceitável (PR-01, CP-01 do PRD).

---

### 6.8 `dt-status-badge`

| Propriedade | Tipo |
|---|---|
| `status` | string (qualquer enum de status do domínio) |
| `entityType` | `'CONTRACT' \| 'PERIOD' \| 'TICKET' \| 'TIMER' \| 'MEMBER'` |
| `size` | `'sm' \| 'md'` |

**Mapeamento de cores:**

| Entidade | Estado | Cor | Ícone |
|---|---|---|---|
| Contrato | `DRAFT` | Neutro | `pi-pencil` |
| Contrato | `ACTIVE` | Sucesso | `pi-check` |
| Contrato | `SUSPENDED` | Atenção | `pi-pause` |
| Contrato | `ENDED` | Neutro | `pi-flag` |
| Contrato | `CANCELLED` | Perigo | `pi-times` |
| Período | `SCHEDULED` | Neutro | `pi-calendar` |
| Período | `OPEN` | Informação | `pi-unlock` |
| Período | `CLOSING` | Atenção | `pi-spinner` |
| Período | `CLOSED` | Neutro | `pi-lock` |
| Período | `REOPENED` | Atenção | `pi-replay` |
| Ticket | `BACKLOG` / `TODO` | Neutro | — |
| Ticket | `IN_PROGRESS` | Informação | `pi-play` |
| Ticket | `BLOCKED` | Perigo | `pi-ban` |
| Ticket | `IN_REVIEW` | Atenção | `pi-eye` |
| Ticket | `DONE` | Sucesso | `pi-check` |
| Ticket | `CANCELLED` | Neutro | `pi-times` |

| # | Regra |
|---|---|
| SB-01 | Sempre com texto, nunca apenas cor ou ícone (DS-05) |
| SB-02 | Rótulos traduzidos, nunca o valor do enum |
| SB-03 | Selo de "período fechado" tem tooltip explicando que os registros estão travados |

---

### 6.9 `dt-data-table`

Encapsula `p-table` com os padrões obrigatórios de listagem.

| Propriedade | Tipo |
|---|---|
| `columns` | `ColumnDef[]` |
| `data` | `T[]` |
| `loading` | `boolean` |
| `page` | `PageInfo` |
| `summary` | `Record<string, unknown> \| null` |
| `emptyState` | `EmptyStateConfig` |
| `rowActions` | `RowAction[]` |

| # | Regra |
|---|---|
| DT-01 | Paginação, ordenação e filtro sempre no servidor |
| DT-02 | O estado é refletido na URL (LS-03) |
| DT-03 | Colunas visíveis configuráveis, persistidas por usuário |
| DT-04 | Barra de totais do conjunto filtrado acima da tabela (LS-02) |
| DT-05 | Estado vazio obrigatório, com título, texto e ação (DS-08) |
| DT-06 | Em `xs`, renderiza cartões com os 4 campos marcados como prioritários |
| DT-07 | Esqueleto durante a carga inicial; conteúdo atenuado em recargas |
| DT-08 | `J`/`K` navegam; `Enter` abre o item |
| DT-09 | Cabeçalho fixo na rolagem vertical |

---

### 6.10 `dt-empty-state`

| Propriedade | Tipo |
|---|---|
| `icon` | string (PrimeIcons) |
| `title` | string |
| `description` | string |
| `action` | `{ label: string; handler: () => void } \| null` |
| `variant` | `'empty' \| 'no-results' \| 'error'` |

Conteúdos padronizados na §8.3 de `design-system.md`.

---

### 6.11 `dt-notification-panel`

Painel suspenso da barra superior.

| # | Regra |
|---|---|
| NP-01 | Lista as 10 mais recentes, com link para a central completa |
| NP-02 | Não lidas com fundo destacado e marcador lateral |
| NP-03 | Ícone e cor conforme a severidade |
| NP-04 | Clique navega para a ação e marca como lida |
| NP-05 | "Marcar todas como lidas" no cabeçalho |
| NP-06 | Atualização em tempo real via SSE; fallback para consulta a cada 60s |
| NP-07 | `role="log"` com `aria-live="polite"` para novas notificações |

---

### 6.12 `dt-confirm-dialog`

Encapsula `p-confirmDialog` com regras de segurança.

| Nível | Uso | Exigência |
|---|---|---|
| `simple` | Ações reversíveis | Botão de confirmação |
| `destructive` | Exclusão, descarte de cronômetro | Botão em `danger` + descrição do impacto |
| `critical` | Cancelar contrato, cancelar organização | Digitar uma palavra de confirmação |

| # | Regra |
|---|---|
| CD-01 | Toda ação destrutiva descreve o impacto exato ("você perderá 02:45 de tempo registrado") |
| CD-02 | Em nível `critical`, o botão só habilita após a digitação correta |
| CD-03 | O foco inicial vai para o botão de cancelar, nunca para o de confirmar |
| CD-04 | `Esc` cancela; `Enter` **não** confirma em nível destrutivo ou crítico |

---

### 6.13 Demais componentes customizados

| Componente | Finalidade | Regra principal |
|---|---|---|
| `dt-time-input` | Entrada de horário `HH:mm` | Aceita `9`, `900`, `9:00`, `09h` |
| `dt-page-header` | Cabeçalho padrão de página | Trilha, título, selo, ações |
| `dt-client-avatar` | Identificação visual do cliente | Iniciais sobre a cor do cliente |
| `dt-user-avatar` | Identificação do usuário | Imagem ou iniciais |
| `dt-tag-list` | Exibição e edição de tags | Máximo 10; criação inline |
| `dt-period-selector` | Seleção de período do contrato | Exibe status e saldo de cada opção |
| `dt-severity-icon` | Ícone de severidade | Sempre com `aria-label` |
| `dt-money` | Exibição de valor monetário | Moeda do contrato; oculto sem permissão |
| `dt-duration` | Exibição de duração | `HH:MM` monoespaçado; sinal para negativos |
| `dt-relative-date` | Data relativa | Até 7 dias; depois, absoluta |
| `dt-markdown-view` | Renderização de Markdown | HTML já sanitizado no servidor |
| `dt-attachment-list` | Lista de anexos | Bloqueia download conforme `scanStatus` |
| `dt-timer-quick-start` | Botão de início rápido | Presente em cards de ticket |
| `dt-keyboard-shortcuts` | Diálogo de atalhos | Aberto por `?` |
| `dt-command-palette` | Paleta de comandos | Aberta por `Ctrl/Cmd + K` |

---

## 7. Composição de gráficos

| Gráfico | Tipo Chart.js | Uso |
|---|---|---|
| Horas por dia | Barras verticais | Dashboard, 30 dias |
| Distribuição por cliente | Rosca | Dashboard |
| Distribuição por categoria | Rosca | Dashboard, extrato |
| Tendência de consumo | Linhas | Histórico do contrato |
| Faturável vs. não faturável | Barras empilhadas | Relatório de produtividade |

| # | Regra |
|---|---|
| CH-01 | Cores derivadas dos tokens; cores de identificação para clientes e categorias |
| CH-02 | Toda série possui legenda com rótulo textual |
| CH-03 | Tooltip exibe valores formatados (`HH:MM`, não minutos brutos) |
| CH-04 | Sem dados: área com mensagem, nunca gráfico vazio (§17 de `design-system.md`) |
| CH-05 | Eixos, grades e rótulos adaptados ao tema |
| CH-06 | Toda visualização possui alternativa acessível em tabela, disponível por botão |
| CH-07 | Animações desabilitadas com `prefers-reduced-motion` |
| CH-08 | Carregados com `@defer`, após o conteúdo crítico |

---

## 8. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-CO-01 | `dt-duration-input` recebe valor acima do máximo | Exibe erro sob o campo; não trunca automaticamente |
| CE-CO-02 | `dt-timer-bar` sem conexão | Continua contando localmente com indicador de reconexão; ações desabilitadas |
| CE-CO-03 | `dt-balance-bar` com `availableMinutes = 0` | Renderiza sem barra, exibindo apenas o consumido |
| CE-CO-04 | `dt-data-table` com 500 colunas de configuração | Impossível — máximo de 20 colunas por tabela |
| CE-CO-05 | `dt-markdown-view` com HTML malicioso | Já sanitizado no servidor; o cliente nunca usa `innerHTML` com conteúdo bruto |
| CE-CO-06 | `dt-attachment-list` com anexo em verificação | Item exibido com estado "verificando" e download desabilitado |
| CE-CO-07 | `dt-tag-list` no limite de 10 tags | Campo desabilitado com mensagem explicativa |
| CE-CO-08 | Gráfico com mais de 12 categorias | As 10 maiores + "Outros" agregado |
| CE-CO-09 | `dt-work-log-form` com contrato sem período para a data | Erro `DEVTIME-2107` com explicação e link para o contrato |

## 9. Casos de erro

| Situação | Tratamento |
|---|---|
| Componente recebe dado em formato inesperado | Renderiza estado de erro isolado; não propaga exceção |
| Falha de carregamento de imagem em avatar | Substituto com iniciais |
| Erro de validação vindo do servidor | Mapeado para o campo correspondente via `errors[]` |
| Componente conectado com store em erro | Exibe `dt-empty-state` com `variant="error"` e ação de nova tentativa |

## 10. Critérios de aceite

| # | Critério |
|---|---|
| CA-01 | Todo componente customizado tem justificativa do que o PrimeNG não atende |
| CA-02 | Todos usam `OnPush` e `input()`/`output()` de Signals |
| CA-03 | Nenhum componente de `shared/` injeta serviço de dados |
| CA-04 | Zero violações do axe-core em todos os componentes |
| CA-05 | `dt-duration-input` aceita todos os formatos da tabela §6.2 |
| CA-06 | `dt-timer-bar` exibe o tempo correto após recarga, hibernação e reconexão |
| CA-07 | `dt-balance-bar` respeita exatamente as faixas de severidade |
| CA-08 | Toda tabela possui estado vazio configurado |
| CA-09 | Todo gráfico possui alternativa em tabela |
| CA-10 | Toda ação destrutiva descreve seu impacto antes da confirmação |
| CA-11 | Nenhum texto fixo em qualquer componente |

## 11. Dependências e impactos

| Documento | Relação |
|---|---|
| `design-system.md` | Fornece tokens e padrões visuais |
| `layouts.md` | Define onde os componentes se posicionam |
| `pages.md` | Compõe telas com estes componentes |
| `03-architecture/frontend.md` | Define os padrões técnicos de implementação |
| `04-api/*` | Fornece os contratos de dados consumidos |

**Impacto:** alterar um componente compartilhado exige revisão de todas as telas que o utilizam e revalidação de acessibilidade.
