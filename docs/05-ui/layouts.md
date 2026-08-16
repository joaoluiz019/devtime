# Layouts — DevTime

## 1. Objetivo

Especificar as estruturas de layout da aplicação: o shell principal, os layouts de página, a grade de composição, o comportamento responsivo e as regiões fixas. Todo layout de tela deve derivar de um dos padrões aqui definidos.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Shell da aplicação e regiões fixas | Tokens visuais (`design-system.md`) |
| Padrões de layout de página | Conteúdo específico de cada tela (`pages.md`) |
| Grade, breakpoints e comportamento responsivo | Componentes individuais (`components.md`) |
| Navegação e hierarquia de informação | Implementação Angular (`03-architecture/frontend.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Shell** | Estrutura persistente que envolve todas as páginas autenticadas. |
| **Região fixa** | Área que permanece visível durante a rolagem. |
| **Layout de página** | Padrão de composição reutilizável (lista, detalhe, formulário, dashboard). |
| **Painel de detalhe** | Área lateral que exibe um item selecionado sem sair da lista. |
| **Área de conteúdo** | Região rolável entre o cabeçalho e o rodapé. |

---

## 4. Layouts existentes

| # | Layout | Uso | Telas |
|---|---|---|---|
| L1 | **Autenticação** | Fora do shell | Login, registro, recuperação, convite, seleção de organização |
| L2 | **Shell principal** | Todas as telas autenticadas | Envolve L3–L7 |
| L3 | **Dashboard** | Visão geral | Dashboard |
| L4 | **Lista** | Listagens com filtro | Clientes, contratos, tickets, registros de horas, notificações |
| L5 | **Lista + detalhe** | Lista com painel lateral | Tickets, registros de horas |
| L6 | **Detalhe** | Página dedicada a um recurso | Cliente, contrato, ticket |
| L7 | **Formulário** | Criação e edição | Todos os formulários |
| L8 | **Relatório** | Configuração e prévia | Relatórios |
| L9 | **Configurações** | Abas verticais | Perfil, organização, categorias, equipe |
| L10 | **Wizard** | Fluxo guiado em etapas | Onboarding inicial |

---

## 5. L1 — Layout de autenticação

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│                    ┌──────────────┐                      │
│                    │  Logo DevTime │                      │
│                    └──────────────┘                      │
│                                                          │
│              ┌────────────────────────────┐              │
│              │  Título da tela            │              │
│              │  Texto de apoio            │              │
│              │  ────────────────────────  │              │
│              │  [ Campos do formulário ]  │              │
│              │  ────────────────────────  │              │
│              │  [ Ação primária        ]  │              │
│              │  Link secundário           │              │
│              └────────────────────────────┘              │
│                                                          │
│           Termos · Privacidade · Suporte                 │
└──────────────────────────────────────────────────────────┘
```

| Aspecto | Regra |
|---|---|
| Largura do cartão | 400px (`xs`: 100% com margem de 16px) |
| Alinhamento | Centralizado vertical e horizontalmente |
| Fundo | `--dt-surface-page`, sem imagem decorativa (DS-01) |
| Ação primária | Botão de largura total |
| Foco inicial | Primeiro campo vazio, automaticamente |
| Envio | `Enter` submete o formulário |

---

## 6. L2 — Shell principal

```
┌────────────────────────────────────────────────────────────────────────┐
│ ⏱  CT-0001-42 · Acme Corporation      02:45:40   [⏸] [⏹]   [barra]    │ 48px
├──────────┬─────────────────────────────────────────────────────────────┤
│          │ [☰] Busca (/)              [+ Novo] [🔔 3] [Org ▾] [👤 ▾]  │ 56px
│  Logo    ├─────────────────────────────────────────────────────────────┤
│          │                                                             │
│ ▸ Dash   │                                                             │
│ ▸ Client │                    ÁREA DE CONTEÚDO                         │
│ ▸ Contra │                       (rolável)                             │
│ ▸ Ticket │                                                             │
│ ▸ Horas  │                                                             │
│ ▸ Relat. │                                                             │
│          │                                                             │
│ ──────── │                                                             │
│ ▸ Config │                                                             │
│ ▸ Ajuda  │                                                             │
│  240px   │                                                             │
└──────────┴─────────────────────────────────────────────────────────────┘
```

### 6.1 Regiões

| Região | Altura/Largura | Fixa | Condição de exibição |
|---|---|:--:|---|
| Barra do cronômetro | 48px | ✅ | Apenas com cronômetro ativo |
| Barra superior | 56px | ✅ | Sempre |
| Barra lateral | 240px / 64px | ✅ | `md` e acima; vira menu inferior em `xs` |
| Área de conteúdo | Restante | ❌ | Sempre |

### 6.2 Barra do cronômetro

**Decisão de projeto:** a barra do cronômetro fica **acima** da barra superior, ocupando toda a largura. Isso a torna o elemento mais proeminente da tela quando ativa — refletindo ID-01 e o fato de que o cronômetro rodando é a informação mais importante do momento.

| Elemento | Conteúdo | Comportamento |
|---|---|---|
| Ícone de estado | `pi-stopwatch` pulsante quando `RUNNING`; estático quando `PAUSED` | Respeita `prefers-reduced-motion` |
| Ticket | Chave + título truncado | Clique abre o ticket |
| Cliente | Nome com a cor de identificação | — |
| Cronômetro | `HH:MM:SS` em `--dt-text-timer` monoespaçado | Atualiza a cada segundo |
| Ações | Pausar/Retomar, Parar | `Parar` abre o diálogo de descrição |
| Cor de fundo | `--dt-color-primary` quando `RUNNING`; `--dt-color-warning` quando `PAUSED` | Estado óbvio sem leitura |
| Alerta de duração | Após 8h, exibe aviso inline "Rodando há mais de 8 horas" | RN-163 |

### 6.3 Barra superior

| Elemento | Posição | Comportamento |
|---|---|---|
| Alternar barra lateral | Esquerda | Recolhe para 64px |
| Busca global | Esquerda, após o botão | Atalho `/`; busca em tickets, clientes e contratos |
| Ação rápida "Novo" | Direita | Menu: registro de horas, ticket, cliente, contrato |
| Notificações | Direita | Contador de não lidas; painel suspenso |
| Seletor de organização | Direita | Exibido apenas com mais de um tenant |
| Menu do usuário | Direita | Perfil, tema, atalhos, sair |

### 6.4 Barra lateral

| Grupo | Rótulo de seção | Itens | Visibilidade |
|---|---|---|---|
| Abertura | — | Dashboard | Conforme permissão |
| Operação | "Operação" | Clientes, Contratos, Tickets, Registro de horas | Conforme permissão |
| Análise | "Análise" | Relatórios | Conforme permissão |
| Inferior | — | Configurações, Ajuda | Configurações apenas com `TENANT_UPDATE` |

Sete itens em lista corrida se leem inteiros a cada consulta. Os grupos separam o trabalho do dia do que se olha depois. O grupo de abertura não leva rótulo: um título sobre um item só é ruído. O grupo inferior é separado pelo espaçador, não por rótulo.

| # | Regra |
|---|---|
| SB-01 | Item sem permissão é **ocultado**, não desabilitado |
| SB-06 | Grupo cujos itens foram todos ocultados por permissão não renderiza o rótulo — uma seção vazia anuncia telas que aquele papel não pode abrir |
| SB-02 | Item ativo é destacado por cor e barra lateral esquerda |
| SB-03 | No modo recolhido, apenas ícones, com tooltip |
| SB-04 | O estado (expandida/recolhida) persiste nas preferências do usuário |
| SB-05 | Contratos em severidade `CRITICAL` exibem um indicador numérico no item "Contratos" |

### 6.5 Banners de contexto

Renderizados no topo da área de conteúdo, empilhados:

| Banner | Condição | Severidade |
|---|---|---|
| Organização suspensa | `tenant.status = SUSPENDED` | `WARNING` — "Apenas leitura disponível" |
| Organização cancelada | `tenant.status = CANCELLED` | `CRITICAL` — com prazo de retenção |
| E-mail não verificado | `emailVerifiedAt = null` | `INFO` — com ação de reenvio |
| Cronômetro abandonado | Existe cronômetro `ABANDONED` recuperável | `WARNING` — com ação de recuperar |
| Período pronto para fechar | Período com `endDate` vencida | `INFO` — com ação de fechar |

---

## 7. L3 — Layout de dashboard

```
┌─────────────────────────────────────────────────────────────────┐
│ Dashboard                              [Período: Mês atual ▾]   │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐                │
│ │ Hoje    │ │ Semana  │ │ Período │ │ Timer   │   estatísticas │
│ │ 05:30   │ │ 31:30   │ │ 149:00  │ │ 00:45   │                │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘                │
├─────────────────────────────────────────────────────────────────┤
│ Contratos                                          [Ver todos] │
│ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│ │ CT-0001 ⚠   │ │ CT-0002 ✓   │ │ CT-0003 ✕   │  cards        │
│ │ ▓▓▓▓▓▓▓░ 84%│ │ ▓▓▓░░░░░ 41%│ │ ▓▓▓▓▓▓▓▓105%│              │
│ └──────────────┘ └──────────────┘ └──────────────┘            │
├──────────────────────────────────┬──────────────────────────────┤
│ Horas por dia (30 dias)          │ Distribuição por cliente     │
│ ▁▃▅▂▇▅▃▁▅▇▃▂▅▇▅▃▁▂▅▇▃▅▂▁▃▅▇▅▃   │        (rosca)               │
├──────────────────────────────────┼──────────────────────────────┤
│ Registros recentes               │ Meus tickets em andamento    │
└──────────────────────────────────┴──────────────────────────────┘
```

| Seção | Grade `lg` | Grade `md` | Grade `xs` |
|---|---|---|---|
| Estatísticas rápidas | 4 colunas | 2 colunas | 2 colunas |
| Cards de contrato | 3 colunas | 2 colunas | 1 coluna |
| Gráficos | 2 colunas | 1 coluna | 1 coluna |
| Listas | 2 colunas | 1 coluna | 1 coluna |

| # | Regra |
|---|---|
| DB-01 | Cards de contrato ordenados por severidade decrescente, depois por dias restantes |
| DB-02 | Máximo de 6 cards; o excedente vai para "Ver todos" (CE-D-06) |
| DB-03 | Gráficos carregam com `@defer`, após o conteúdo crítico |
| DB-04 | `MEMBER` vê a versão pessoal, sem a seção de contratos consolidada |
| DB-05 | Cada seção falha isoladamente; um gráfico com erro não derruba a página |

---

## 8. L4 — Layout de lista

```
┌─────────────────────────────────────────────────────────────────┐
│ ← Título da página                          [Ação secundária]   │
│   Subtítulo com contagem                    [+ Ação primária]   │
├─────────────────────────────────────────────────────────────────┤
│ [Busca...]  [Filtro ▾] [Filtro ▾] [Filtro ▾]  [Limpar] [⚙]     │
│ Chips de filtros ativos:  (Status: Ativo ✕) (Cliente: Acme ✕)   │
├─────────────────────────────────────────────────────────────────┤
│ Total: 143 registros · 688:00 · 62 faturáveis        [Exportar] │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ Coluna  │ Coluna  │ Coluna  │ Coluna  │ Coluna  │  ⋮       │ │
│ ├─────────┼─────────┼─────────┼─────────┼─────────┼──────────┤ │
│ │ ...     │ ...     │ ...     │ ...     │ ...     │  ⋮       │ │
│ └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│ ◀ 1 2 3 ... 8 ▶            20 por página ▾        143 no total  │
└─────────────────────────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| LS-01 | Filtros ativos são exibidos como chips removíveis individualmente |
| LS-02 | Toda listagem exibe a barra de totais do **conjunto filtrado**, não da página |
| LS-03 | Filtros, paginação e ordenação vivem na URL (§6.1 de `frontend.md`) |
| LS-04 | Colunas visíveis são configuráveis e persistem por usuário |
| LS-05 | Clique na linha abre o detalhe; ações ficam no menu de contexto da linha |
| LS-06 | Em `xs`, a tabela vira cartões empilhados com os 4 campos mais relevantes |
| LS-07 | Ordenação por coluna com indicador visual e suporte a teclado |
| LS-08 | Seleção múltipla apenas onde há ação em lote (marcar notificações como lidas) |

---

## 9. L5 — Layout lista + detalhe

```
┌───────────────────────────────┬─────────────────────────────────┐
│ Lista compacta                │ Detalhe do item selecionado     │
│ ┌───────────────────────────┐ │ ┌─────────────────────────────┐ │
│ │ ▸ CT-0001-42  ⏱ 05:10    │ │ │ CT-0001-42            [✕]  │ │
│ │   Corrigir checkout       │ │ │ Corrigir cálculo de frete   │ │
│ ├───────────────────────────┤ │ │ ─────────────────────────── │ │
│ │ ▸ CT-0001-43  ⏱ 02:00    │ │ │ [Detalhes] [Horas] [Coment.]│ │
│ │   Ajustar relatório       │ │ │                             │ │
│ └───────────────────────────┘ │ │        conteúdo da aba      │ │
│         40%                   │ │            60%              │ │
└───────────────────────────────┴─────────────────────────────────┘
```

| # | Regra |
|---|---|
| LD-01 | Disponível apenas em `lg` e acima; abaixo disso, a lista navega para a página de detalhe |
| LD-02 | A seleção reflete na URL, permitindo compartilhar o link do item selecionado |
| LD-03 | `J`/`K` navegam entre itens mantendo o painel aberto |
| LD-04 | A proporção 40/60 é ajustável por arrasto e persiste nas preferências |
| LD-05 | `Esc` fecha o painel e devolve o foco à lista |

---

## 10. L6 — Layout de detalhe

```
┌─────────────────────────────────────────────────────────────────┐
│ ← Contratos / CT-0001                                           │
│ Sustentação Mensal            [ATIVO]      [Ações ▾] [Editar]  │
│ Acme Corporation · 40h/mês · Ciclo dia 1                        │
├─────────────────────────────────────────────────────────────────┤
│ [Visão geral] [Períodos] [Tickets] [Registros] [Histórico]      │
├─────────────────────────────────────────────────────────────────┤
│ ┌───────────────────────────────┬───────────────────────────┐   │
│ │ Conteúdo principal            │ Painel lateral            │   │
│ │ (extrato do saldo)            │ (informações do contrato) │   │
│ │            2/3                │           1/3             │   │
│ └───────────────────────────────┴───────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| DT-01 | Cabeçalho com trilha de navegação, título, selo de status e ações |
| DT-02 | Ações indisponíveis por estado ou permissão são **ocultadas** (§ME-06) |
| DT-03 | A aba ativa é refletida na URL |
| DT-04 | O painel lateral vai para o fim do conteúdo em `md` e abaixo |
| DT-05 | O cabeçalho fica fixo ao rolar, em versão compacta |

---

## 11. L7 — Layout de formulário

```
┌─────────────────────────────────────────────────────────────────┐
│ ← Voltar                                                        │
│ Novo contrato                                                   │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────┬──────────────────────────┐  │
│ │ Seção: Identificação            │ Prévia / Ajuda           │  │
│ │  Cliente        [___________▾]  │                          │  │
│ │  Nome           [_____________] │  Períodos que serão      │  │
│ │                                 │  gerados:                │  │
│ │ Seção: Horas e ciclo            │  1. 10/01–31/01  28:23   │  │
│ │  Horas/mês      [_____________] │  2. 01/02–28/02  40:00   │  │
│ │  Dia de fatur.  [___]  ⓘ        │  3. 01/03–31/03  40:00   │  │
│ │                                 │                          │  │
│ │ Seção: Políticas                │                          │  │
│ │  Transporte     ( ) ( ) ( )     │                          │  │
│ │  Excedente      ( ) ( ) ( )     │                          │  │
│ └─────────────────────────────────┴──────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                                     [Cancelar] [Salvar]         │
└─────────────────────────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| FM-01 | Campos agrupados em seções com título |
| FM-02 | Máximo de 7 campos por seção |
| FM-03 | Campos obrigatórios marcados com asterisco e `aria-required` |
| FM-04 | Barra de ações fixa no rodapé em formulários longos |
| FM-05 | Ação primária à direita; cancelar à esquerda dela |
| FM-06 | Erro exibido sob o campo, nunca em toast (FM-03 de `frontend.md`) |
| FM-07 | Ao submeter com erros, o foco vai para o primeiro campo inválido |
| FM-08 | Formulário sujo dispara confirmação ao sair |
| FM-09 | O painel de prévia atualiza em tempo real conforme o preenchimento |
| FM-10 | `Ctrl/Cmd + Enter` salva |

---

## 12. L8 — Layout de relatório

```
┌─────────────────────────────────────────────────────────────────┐
│ Relatórios                                                      │
├──────────────────┬──────────────────────────────────────────────┤
│ Configuração     │ Prévia                                       │
│                  │ ┌──────────────────────────────────────────┐ │
│ Tipo    [____▾]  │ │  [PARCIAL]                               │ │
│ Contrato[____▾]  │ │  Logo · Emissor · Cliente · Período       │ │
│ Período [____▾]  │ │  ──────────────────────────────────────  │ │
│ Agrupar [____▾]  │ │  Resumo do saldo                         │ │
│ ☑ Não faturáveis │ │  Detalhamento                            │ │
│ ☑ Valores        │ │  Totais                                  │ │
│                  │ └──────────────────────────────────────────┘ │
│ [PDF] [Excel]    │                                              │
│      320px       │                  restante                    │
└──────────────────┴──────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| RP-01 | A prévia reflete fielmente o arquivo que será gerado |
| RP-02 | A marcação **PARCIAL** aparece na prévia quando aplicável |
| RP-03 | A prévia atualiza com debounce de 500ms após mudança de filtro |
| RP-04 | A exportação assíncrona exibe progresso sem bloquear a navegação |
| RP-05 | Em `md` e abaixo, a configuração vira um painel expansível acima da prévia |

---

## 13. L9 — Layout de configurações

```
┌─────────────────────────────────────────────────────────────────┐
│ Configurações                                                   │
├──────────────────┬──────────────────────────────────────────────┤
│ ▸ Meu perfil     │                                              │
│ ▸ Preferências   │           Conteúdo da seção                  │
│ ▸ Notificações   │                                              │
│ ──────────────   │                                              │
│ ▸ Organização    │                                              │
│ ▸ Categorias     │                                              │
│ ▸ Tags           │                                              │
│ ▸ Equipe         │                                              │
│ ▸ Auditoria      │                                              │
│      240px       │                                              │
└──────────────────┴──────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| CF-01 | Grupos separados: pessoais (sempre visíveis) e da organização (por permissão) |
| CF-02 | Cada seção é uma rota própria |
| CF-03 | Alterações são salvas por seção, não globalmente |
| CF-04 | Em `xs`, a navegação vira uma lista que substitui o conteúdo |

---

## 14. L10 — Layout de wizard

```
┌─────────────────────────────────────────────────────────────────┐
│  ●━━━━━━━━●━━━━━━━━○━━━━━━━━○                                   │
│  Cliente   Contrato  Ticket   Pronto                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│              Título da etapa                                    │
│              Texto explicando por que esta etapa importa        │
│                                                                 │
│              [ Campos ]                                         │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│ [Pular configuração]                    [Voltar]  [Continuar]   │
└─────────────────────────────────────────────────────────────────┘
```

| # | Regra |
|---|---|
| WZ-01 | Máximo de 4 etapas |
| WZ-02 | O progresso é sempre visível |
| WZ-03 | "Pular configuração" sempre disponível — o wizard nunca é obrigatório |
| WZ-04 | Cada etapa explica **por que** importa, não apenas o que preencher |
| WZ-05 | Dados são persistidos a cada etapa; sair não perde o que já foi feito |
| WZ-06 | A etapa final oferece iniciar o cronômetro imediatamente |

---

## 15. Grade e responsividade

**Sistema:** PrimeFlex com 12 colunas, gutter de 16px (`--dt-space-4`).

| Breakpoint | Colunas usadas | Barra lateral | Tabela | Painel de detalhe |
|---|---|---|---|---|
| `xs` < 576px | 1 | Menu inferior | Cartões | Página separada |
| `sm` ≥ 576px | 1–2 | Menu inferior | Cartões | Página separada |
| `md` ≥ 768px | 2–3 | Recolhida (64px) | Colunas essenciais | Página separada |
| `lg` ≥ 992px | 3–4 | Expandida (240px) | Completa | Painel lateral |
| `xl` ≥ 1200px | 4–6 | Expandida | Completa | Painel lateral |
| `2xl` ≥ 1440px | 4–6 centralizado | Expandida | Completa | Painel lateral |

### 15.1 Navegação em `xs`

```
┌─────────────────────────────────┐
│ ⏱ 02:45  CT-0001-42   [⏸][⏹]  │  cronômetro compacto
├─────────────────────────────────┤
│                                 │
│         CONTEÚDO                │
│                                 │
├─────────────────────────────────┤
│  🏠     📋     ⏱     🔔    ☰   │  menu inferior fixo
│ Início Contr. Horas Notif. Mais │
└─────────────────────────────────┘
```

O item central (Horas) tem destaque visual e ação dupla: toque abre a lista; toque longo inicia o cronômetro.

---

## 16. Hierarquia de rolagem

| # | Regra |
|---|---|
| SC-01 | Apenas a área de conteúdo rola; o shell permanece fixo |
| SC-02 | Nenhuma rolagem horizontal na página; tabelas largas rolam internamente |
| SC-03 | Cabeçalhos de tabela ficam fixos durante a rolagem vertical |
| SC-04 | A posição de rolagem é restaurada ao voltar de uma navegação |
| SC-05 | Rolagem infinita **não** é usada — a paginação explícita é necessária para relatórios auditáveis |

---

## 17. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-L-01 | Cronômetro ativo + banner de contexto | O cronômetro fica sempre no topo; banners vêm abaixo da barra superior |
| CE-L-02 | Três ou mais banners simultâneos | Empilhados por severidade; os de menor severidade colapsam em "mais 2 avisos" |
| CE-L-03 | Tabela com 15 colunas | Colunas essenciais fixas à esquerda; as demais em rolagem horizontal interna |
| CE-L-04 | Painel de detalhe com item excluído por outro usuário | Painel exibe estado de erro com ação de fechar |
| CE-L-05 | Janela muito baixa (< 600px de altura) | Barra do cronômetro reduz para 36px; barra superior mantém 56px |
| CE-L-06 | Impressão | Shell removido; apenas o conteúdo, com cabeçalho de identificação |
| CE-L-07 | Zoom de 200% | O layout degrada para o comportamento de `xs`, mantendo a funcionalidade |
| CE-L-08 | Barra lateral recolhida com submenu | Submenu abre como painel flutuante ao passar o cursor |

## 18. Casos de erro

| Situação | Tratamento |
|---|---|
| Falha ao carregar a página | Estado de erro dentro da área de conteúdo; o shell permanece funcional |
| Falha em uma seção do dashboard | Apenas aquela seção exibe erro com ação de nova tentativa |
| Falha ao carregar um chunk lazy | Toast com ação de recarregar |
| Rota inexistente | Página de "não encontrado" dentro do shell, com sugestões de navegação |
| Sem permissão para a rota | Página de "acesso negado" com ação de voltar |

## 19. Critérios de aceite

| # | Critério |
|---|---|
| CA-01 | O shell permanece funcional mesmo com falha no conteúdo |
| CA-02 | A barra do cronômetro é visível em 100% das telas autenticadas quando há cronômetro ativo |
| CA-03 | Nenhuma página produz rolagem horizontal no `body` |
| CA-04 | Todos os layouts funcionam de 360px a 2560px |
| CA-05 | Filtro, ordenação e paginação são preservados na URL |
| CA-06 | Itens de menu sem permissão são ocultados, não desabilitados |
| CA-07 | A ordem de tabulação é lógica em todos os layouts |
| CA-08 | A posição de rolagem é restaurada ao navegar de volta |
| CA-09 | Todos os layouts são funcionais com zoom de 200% |

## 20. Dependências e impactos

| Documento | Relação |
|---|---|
| `design-system.md` | Fornece tokens, dimensões e breakpoints |
| `components.md` | Componentes que compõem estes layouts |
| `pages.md` | Cada tela declara qual layout utiliza |
| `03-architecture/frontend.md` | Implementa o shell e o roteamento |
| `02-domain/permissions.md` | Define a visibilidade dos itens de menu |

**Impacto:** alterar as dimensões das regiões fixas afeta o cálculo de altura da área de conteúdo em todas as telas.
