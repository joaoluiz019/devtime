# Design System — DevTime

## 1. Objetivo

Definir a linguagem visual e de interação do DevTime: tokens de design, tipografia, cores, espaçamento, iconografia, estados, padrões de feedback, acessibilidade e atalhos. É a fonte de verdade para qualquer decisão visual — nenhuma cor, espaçamento ou tamanho pode ser escolhido fora deste documento.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Tokens (cor, tipografia, espaçamento, sombra, raio) | Estrutura de layout (`layouts.md`) |
| Padrões de estado, feedback e formulário | Especificação de telas (`pages.md`) |
| Semântica de cor por severidade | Catálogo de componentes (`components.md`) |
| Acessibilidade e atalhos globais | Implementação Angular (`03-architecture/frontend.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Token** | Valor nomeado de design, exposto como variável CSS `--dt-*`. |
| **Token primitivo** | Valor bruto (ex.: `--dt-blue-500`). |
| **Token semântico** | Valor com significado de uso (ex.: `--dt-color-primary`). |
| **Severidade** | Classificação visual do estado de um contrato ou alerta. |
| **Densidade** | Quantidade de informação por área. |

---

## 4. Princípios de design

| # | Princípio | Aplicação prática | Origem |
|---|---|---|---|
| DS-01 | **Densidade sóbria** | Alta informação por tela, sem poluição. O usuário é técnico e prefere ver mais a rolar mais. | Persona Rafael §5.5 |
| DS-02 | **O saldo é sempre visível** | Toda tela relacionada a um contrato exibe o saldo do período. | PR-02 |
| DS-03 | **Nunca esconder um número atrás de um clique** | Valores críticos (saldo, tempo do dia) aparecem sem interação. | JTBD-02 |
| DS-04 | **Cor comunica severidade, nunca decora** | Verde, âmbar e vermelho têm significado fixo e exclusivo. | AC-08 |
| DS-05 | **Nenhuma informação apenas por cor** | Sempre acompanhada de ícone, texto ou padrão. | WCAG 1.4.1 |
| DS-06 | **Ação primária única por tela** | Uma única ação em destaque; as demais em hierarquia inferior. | PR-01 |
| DS-07 | **Erro explica e oferece a correção** | Nunca "operação inválida"; sempre o que está errado e como resolver. | RNF-047 |
| DS-08 | **Estado vazio ensina** | Toda lista vazia orienta a próxima ação. | RNF-048 |
| DS-09 | **Teclado é cidadão de primeira classe** | Toda ação frequente tem atalho. | ID-05 |
| DS-10 | **Modo escuro é equivalente, não secundário** | Ambos os temas recebem o mesmo cuidado. | ID-07 |

---

## 5. Cores

### 5.1 Tokens primitivos

| Escala | 50 | 100 | 300 | 500 | 700 | 900 |
|---|---|---|---|---|---|---|
| **Slate** (neutro) | `#F8FAFC` | `#F1F5F9` | `#CBD5E1` | `#64748B` | `#334155` | `#0F172A` |
| **Indigo** (primária) | `#EEF2FF` | `#E0E7FF` | `#A5B4FC` | `#6366F1` | `#4338CA` | `#312E81` |
| **Emerald** (sucesso) | `#ECFDF5` | `#D1FAE5` | `#6EE7B7` | `#10B981` | `#047857` | `#064E3B` |
| **Amber** (atenção) | `#FFFBEB` | `#FEF3C7` | `#FCD34D` | `#F59E0B` | `#B45309` | `#78350F` |
| **Red** (crítico) | `#FEF2F2` | `#FEE2E2` | `#FCA5A5` | `#EF4444` | `#B91C1C` | `#7F1D1D` |
| **Sky** (informação) | `#F0F9FF` | `#E0F2FE` | `#7DD3FC` | `#0EA5E9` | `#0369A1` | `#0C4A6E` |

As escalas acima alimentam o **tema claro**. O tema escuro usa a paleta Nocturne de §5.1.1: uma escala de luminosidade só dela, gerada em OKLCH, porque as escalas Tailwind acima foram desenhadas para tinta sobre papel e, invertidas, produzem um azul que vibra sobre fundo escuro.

### 5.1.1 Tokens primitivos Nocturne — exclusivos do tema escuro

| Escala | Passos |
|---|---|
| **Ink** (fundos) | `950 #161826` · `800 #232532` · `700 #2B2E3D` · `600 #3F424D` · `500 #595D6C` |
| **Mist** (texto) | `100 #E9E9ED` · `300 #B2B6CA` · `500 #75798C` |
| **Blurple** (primária) | `50 #F5F4FF` · `100 #E7E5FE` · `300 #D2CEFD` · `400 #B5ABFC` · `500 #9184D9` · `700 #5D5294` · `800 #423A6A` · `900 #2B2741` |
| **Severidade** | `sage-300 #7FD0A5` (sucesso) · `honey-300 #E0B57F` (atenção) · `clay-300 #E0A1A1` (crítico) · `steel-300 #8FA8E0` (informação) |

### 5.2 Tokens semânticos

| Token | Tema claro | Tema escuro | Uso |
|---|---|---|---|
| `--dt-color-primary` | `indigo-500` | `blurple-500` | Ação principal, links, foco |
| `--dt-color-primary-hover` | `indigo-700` | `blurple-300` | Estado de hover |
| `--dt-color-primary-contrast` | `#FFFFFF` | `ink-950` | Texto sobre preenchimento primário |
| `--dt-color-primary-soft` | `indigo-100` | `blurple-800` | Preenchimento de identificação (avatar, selo) |
| `--dt-color-primary-soft-contrast` | `indigo-700` | `blurple-100` | Texto sobre o preenchimento suave |
| `--dt-surface-page` | `slate-50` | `ink-950` | Fundo da página, da barra lateral e da barra superior |
| `--dt-surface-card` | `#FFFFFF` | `ink-800` | Cartões e painéis |
| `--dt-surface-raised` | `#FFFFFF` | `ink-700` | Diálogos e menus |
| `--dt-border` | `slate-300` | `ink-600` | Bordas e divisores |
| `--dt-text-primary` | `slate-900` | `mist-100` | Texto principal |
| `--dt-text-secondary` | `slate-500` | `mist-300` | Texto de apoio |
| `--dt-text-disabled` | `#94A3B8` | `mist-500` | Desabilitado |
| `--dt-color-success` | `emerald-500` | `sage-300` | Saldo saudável |
| `--dt-color-warning` | `amber-500` | `honey-300` | Saldo em atenção |
| `--dt-color-danger` | `red-500` | `clay-300` | Excedente, erro |
| `--dt-color-info` | `sky-500` | `steel-300` | Informação neutra |

> **Barra lateral e barra superior usam `--dt-surface-page`, não `--dt-surface-card`.** Painel e conteúdo compartilham o mesmo chão, separados apenas pela divisória; assim o cartão é a única superfície que se eleva, e a moldura da aplicação para de competir com ele por atenção.

### 5.3 Semântica de severidade — regra fixa

| Severidade | Faixa de consumo | Cor | Ícone | Rótulo | Uso |
|---|---|---|---|---|---|
| `OK` | 0–49% | `--dt-color-success` | `pi-check-circle` | "Saudável" | Barra de progresso, selo, card |
| `INFO` | 50–79% | `--dt-color-info` | `pi-info-circle` | "Em andamento" | Idem |
| `WARNING` | 80–99% | `--dt-color-warning` | `pi-exclamation-triangle` | "Atenção" | Idem |
| `CRITICAL` | ≥ 100% | `--dt-color-danger` | `pi-times-circle` | "Excedido" | Idem |

> **Regra inviolável (DS-04/DS-05):** estas cores **nunca** são usadas com outro significado. Um botão de exclusão usa vermelho porque a ação é destrutiva — coerente com "crítico". Um botão de sucesso genérico **não** usa verde, pois verde significa "saldo saudável" no contexto do produto. Toda aplicação de cor semântica é acompanhada de ícone e rótulo textual.

### 5.4 Cores de identificação

Clientes e categorias possuem cor própria, usada exclusivamente para **identificação** em gráficos, selos e barras laterais — nunca para comunicar estado. A paleta sugerida na criação evita os tons de severidade em suas formas puras.

---

## 6. Tipografia

| Token | Família | Uso |
|---|---|---|
| `--dt-font-sans` | `Inter, system-ui, -apple-system, "Segoe UI", sans-serif` | Interface |
| `--dt-font-mono` | `"JetBrains Mono", "Cascadia Code", monospace` | **Durações, horários, códigos, valores** |

> **Decisão:** durações e horários usam fonte monoespaçada. Em listas com dezenas de linhas, o alinhamento vertical dos dígitos permite comparação visual imediata — a diferença entre `01:30` e `11:30` deve ser óbvia sem leitura atenta.

### 6.1 Escala

| Token | Tamanho | Altura de linha | Peso | Uso |
|---|---|---|---|---|
| `--dt-text-xs` | 12px | 16px | 400 | Metadados, rodapés |
| `--dt-text-sm` | 14px | 20px | 400 | Texto de apoio, tabelas |
| `--dt-text-base` | 16px | 24px | 400 | Corpo padrão |
| `--dt-text-lg` | 18px | 28px | 500 | Subtítulos |
| `--dt-text-xl` | 20px | 28px | 600 | Título de seção |
| `--dt-text-2xl` | 24px | 32px | 600 | Título de página |
| `--dt-text-3xl` | 30px | 36px | 700 | Números de destaque |
| `--dt-text-timer` | 32px | 36px | 600 | Cronômetro na barra global (mono) |

**Regras:** máximo de 3 níveis hierárquicos por tela; corpo nunca abaixo de 14px; texto de tabela nunca abaixo de 13px; nenhum texto justificado.

---

## 7. Espaçamento e dimensões

| Token | Valor | Uso |
|---|---|---|
| `--dt-space-1` a `--dt-space-12` | 4, 8, 12, 16, 20, 24, 32, 40, 48, 64px | Escala base de 4px |
| `--dt-radius-sm` / `md` / `lg` / `full` | 4 / 8 / 12 / 9999px | Cantos |
| `--dt-shadow-sm` / `md` / `lg` | Elevação 1 / 2 / 3 | Cartões, menus, diálogos |

| Dimensão | Valor | Regra |
|---|---|---|
| Altura da barra superior | 56px | Fixa |
| Altura da barra do cronômetro | 48px | Fixa; visível quando há cronômetro ativo |
| Largura da barra lateral | 240px expandida / 64px recolhida | — |
| Altura mínima de alvo tocável | 44×44px | WCAG 2.5.5 |
| Largura máxima de conteúdo | 1440px | Centralizado acima disso |
| Altura de linha de tabela | 48px (padrão) / 40px (compacta) | Densidade configurável |

---

## 8. Estados

### 8.1 Estados de componente

| Estado | Tratamento visual | Regra |
|---|---|---|
| Padrão | Cor e borda base | — |
| Hover | Fundo com 4% de sobreposição; transição de 150ms | Apenas em dispositivos com ponteiro |
| Foco | Contorno de 2px em `--dt-color-primary` com deslocamento de 2px | **Nunca removido** (AC-03) |
| Ativo | Fundo com 8% de sobreposição | — |
| Desabilitado | Opacidade 0,5; cursor `not-allowed` | Acompanhado de `title` explicando o motivo |
| Carregando | Esqueleto ou *spinner* embutido | Nunca deixar a área em branco |
| Erro | Borda `--dt-color-danger` + mensagem abaixo | Nunca apenas cor |
| Somente leitura | Fundo neutro; sem borda de campo | Distinto de desabilitado |

### 8.2 Estados de carregamento

| Situação | Padrão |
|---|---|
| Carga inicial de página | Esqueleto reproduzindo a estrutura final |
| Recarga de lista com filtro | Conteúdo anterior atenuado + barra de progresso no topo |
| Ação em botão | *Spinner* dentro do botão; rótulo mantido; botão desabilitado |
| Ação otimista (timer, marcar lida) | Interface atualiza imediatamente; reverte com aviso em caso de falha |
| Operação longa (exportação) | Barra de progresso com percentual e possibilidade de sair da tela |

### 8.3 Estados vazios (DS-08)

| Contexto | Ilustração | Título | Texto | Ação |
|---|---|---|---|---|
| Sem clientes | Ícone de prancheta | "Nenhum cliente ainda" | "Cadastre seu primeiro cliente para começar a controlar horas." | **Novo cliente** |
| Sem contratos | Ícone de documento | "Nenhum contrato" | "Um contrato define quantas horas o cliente contratou por mês." | **Novo contrato** |
| Sem tickets | Ícone de etiqueta | "Nenhum ticket" | "Tickets organizam o trabalho e permitem registrar horas." | **Novo ticket** |
| Sem registros no período | Ícone de relógio | "Nenhuma hora registrada" | "Inicie o cronômetro ou lance um registro manual." | **Iniciar cronômetro** |
| Filtro sem resultado | Ícone de lupa | "Nada encontrado" | "Ajuste os filtros para ver mais resultados." | **Limpar filtros** |
| Sem notificações | Ícone de sino | "Tudo em dia" | "Você será avisado quando um contrato se aproximar do limite." | — |
| Erro de carregamento | Ícone de alerta | "Não foi possível carregar" | Mensagem específica + código do erro | **Tentar novamente** |

---

## 9. Feedback ao usuário

| Tipo | Componente | Duração | Uso |
|---|---|---|---|
| Sucesso de ação simples | Toast | 3s | Registro salvo, item excluído |
| Erro de campo | Mensagem sob o campo | Persistente | Validação de formulário |
| Erro de operação | Toast ou banner | 6s / persistente | Falha de rede, erro de negócio |
| Conflito de estado | Diálogo | Persistente | Cronômetro ativo bloqueando fechamento |
| Confirmação destrutiva | Diálogo com botão de confirmação em destaque | Persistente | Excluir, descartar cronômetro, cancelar contrato |
| Aviso de contexto | Banner na área afetada | Persistente | Período fechado, tenant suspenso |
| Progresso | Barra ou *spinner* | Enquanto durar | Exportação, upload |

### 9.1 Mapa de mensagens de erro (DS-07)

Todo código `DEVTIME-XXXX` possui uma mensagem em linguagem natural, com ação sugerida:

| Código | Mensagem exibida | Ação oferecida |
|---|---|---|
| `DEVTIME-2102` | "Você já registrou horas neste horário: CT-0002-11 (08:30–10:00)." | **Ajustar para 10:00** · **Ver registro** |
| `DEVTIME-2103` | "A sessão passa de 24 horas. Verifique o horário de término." | **Corrigir horário** |
| `DEVTIME-2121` | "Este registro pertence a um período já fechado e não pode ser alterado." | **Solicitar reabertura** (se tiver permissão) |
| `DEVTIME-2150` | "Você já tem um cronômetro rodando em CT-0001-42 há 02:45." | **Trocar de tarefa** · **Ver cronômetro** |
| `DEVTIME-2220` | "Restam apenas 01:00 no contrato e você está registrando 01:30." | **Reduzir tempo** · **Marcar como não faturável** · **Solicitar ajuste** |
| `DEVTIME-2240` | "Diego Alves tem um cronômetro ativo neste período." | **Ver cronômetros ativos** |
| `DEVTIME-2244` | "Reabra primeiro o período de agosto." | **Ir para agosto** |
| `DEVTIME-1101` | "Você não tem permissão para esta ação." | **Solicitar acesso ao proprietário** |

**Regra:** nenhuma mensagem técnica bruta chega ao usuário. O código aparece apenas em texto discreto, para suporte.

---

## 10. Formatação de dados

| Dado | Formato na interface | Exemplo | Regra |
|---|---|---|---|
| Duração | `HH:MM` monoespaçado | `07:30` | Nunca decimal na interface (ART-035) |
| Duração negativa | `-HH:MM` em `--dt-color-danger` | `-02:20` | Sempre rotulada como "excedente" |
| Duração longa | `HH:MM` sem limite de horas | `688:00` | Nunca converter para dias |
| Horário | `HH:mm` | `09:00` | Fuso do tenant |
| Data | `dd/MM/yyyy` | `28/07/2026` | Locale `pt-BR` |
| Data relativa | "hoje", "ontem", "há 3 dias" | — | Apenas até 7 dias; depois, data absoluta |
| Percentual | 1 casa decimal | `83,7%` | — |
| Moeda | Símbolo + separadores locais | `R$ 7.320,00` | Moeda do contrato, nunca do navegador |
| Chave de ticket | Monoespaçada | `CT-0001-42` | — |
| Identificador técnico | **Nunca exibido** | — | ART-024 e PDF-04 |

---

## 11. Iconografia

**Biblioteca:** PrimeIcons, uso exclusivo. Nenhum ícone customizado sem justificativa.

| Conceito | Ícone | Uso |
|---|---|---|
| Cronômetro | `pi-stopwatch` | Barra global |
| Iniciar | `pi-play` | Ação do cronômetro |
| Pausar | `pi-pause` | Ação do cronômetro |
| Parar | `pi-stop-circle` | Ação do cronômetro |
| Registro de horas | `pi-clock` | Menu, listas |
| Cliente | `pi-building` | Menu, listas |
| Contrato | `pi-file-edit` | Menu, listas |
| Ticket | `pi-ticket` | Menu, listas |
| Relatório | `pi-chart-bar` | Menu |
| Dashboard | `pi-home` | Menu |
| Notificação | `pi-bell` | Barra superior |
| Configurações | `pi-cog` | Menu |
| Saldo saudável | `pi-check-circle` | Severidade `OK` |
| Atenção | `pi-exclamation-triangle` | Severidade `WARNING` |
| Crítico | `pi-times-circle` | Severidade `CRITICAL` |
| Período fechado | `pi-lock` | Selo |
| Não faturável | `pi-eye-slash` | Marcação em registro |

**Regras:** todo ícone sem texto adjacente possui `aria-label`; ícones nunca são o único indicador de estado (DS-05); tamanho padrão de 16px, ou 20px em ações principais.

---

## 12. Atalhos de teclado (DS-09 / ID-05)

| Atalho | Ação | Escopo |
|---|---|---|
| `T` | Iniciar ou parar o cronômetro | Global |
| `N` | Novo registro de horas | Global |
| `Shift + N` | Novo ticket | Global |
| `/` | Focar a busca global | Global |
| `G` depois `D` | Ir para o dashboard | Global |
| `G` depois `C` | Ir para contratos | Global |
| `G` depois `T` | Ir para tickets | Global |
| `G` depois `L` | Ir para registros de horas | Global |
| `G` depois `R` | Ir para relatórios | Global |
| `?` | Exibir a lista de atalhos | Global |
| `Esc` | Fechar diálogo ou cancelar edição | Contextual |
| `Ctrl/Cmd + Enter` | Salvar o formulário | Formulário |
| `Ctrl/Cmd + K` | Paleta de comandos | Global |
| `J` / `K` | Navegar entre itens da lista | Lista |
| `Enter` | Abrir o item focado | Lista |

**Regras:** atalhos são desativados quando o foco está em campo de texto, exceto `Esc` e `Ctrl/Cmd + Enter`; toda ação com atalho exibe a tecla em seu tooltip; a lista completa é acessível por `?` e pelo menu de ajuda.

---

## 13. Acessibilidade

| # | Requisito | Verificação |
|---|---|---|
| A11Y-01 | Contraste mínimo de 4.5:1 para texto e 3:1 para elementos gráficos | axe-core em ambos os temas |
| A11Y-02 | Navegação completa por teclado, em ordem lógica | Teste manual |
| A11Y-03 | Foco sempre visível, nunca suprimido | Revisão de CSS |
| A11Y-04 | Todo campo possui rótulo associado | axe-core |
| A11Y-05 | Erros anunciados por `aria-live="polite"` | Leitor de tela |
| A11Y-06 | Diálogos prendem o foco e o devolvem ao fechar | PrimeNG + teste |
| A11Y-07 | Hierarquia de cabeçalhos sem saltos | axe-core |
| A11Y-08 | Nenhuma informação transmitida apenas por cor | Revisão de design |
| A11Y-09 | Regiões de marco em todas as páginas | Revisão |
| A11Y-10 | `prefers-reduced-motion` respeitado | CSS |
| A11Y-11 | Interface funcional com zoom de 200% | Teste manual |
| A11Y-12 | Tabelas com cabeçalhos associados via `scope` | axe-core |

**Meta:** WCAG 2.1 nível AA nas telas principais (RNF-042).

---

## 14. Responsividade

| Ponto de quebra | Largura | Comportamento |
|---|---|---|
| `xs` | < 576px | Coluna única; barra lateral vira menu inferior; tabelas viram cartões |
| `sm` | ≥ 576px | Coluna única com mais respiro |
| `md` | ≥ 768px | Duas colunas; barra lateral recolhida |
| `lg` | ≥ 992px | Barra lateral expandida; tabelas completas |
| `xl` | ≥ 1200px | Layout completo com painel lateral de detalhe |
| `2xl` | ≥ 1440px | Conteúdo centralizado com largura máxima |

**Estratégia:** desktop-first para telas de trabalho (registro, contratos, relatórios); as visões móveis priorizam **consulta e cronômetro**, não digitação intensa (persona Rafael §5.5).

| Funcionalidade | Mobile | Justificativa |
|---|---|---|
| Ver saldo dos contratos | ✅ Completo | Consulta rápida é caso de uso móvel real |
| Operar o cronômetro | ✅ Completo | Iniciar/pausar/parar em movimento |
| Registro manual | ⚠️ Simplificado | Formulário reduzido ao essencial |
| Gerenciar contratos | ⚠️ Somente leitura | Formulário complexo demais para toque |
| Relatórios | ⚠️ Visualização e exportação | Sem configuração avançada de filtros |
| Fechamento de período | ❌ Bloqueado | Operação crítica e irreversível; exige tela ampla |

---

## 15. Modo escuro (DS-10)

| # | Regra |
|---|---|
| DK-01 | Ativado por preferência do usuário ou pelo sistema operacional |
| DK-02 | Nenhum fundo preto puro; usar `ink-950` (azul-tinta) para reduzir fadiga |
| DK-03 | Cores semânticas usam o tom `300` da paleta Nocturne, preservando o contraste |
| DK-04 | Elevação é expressa por diferença de superfície, não por sombra |
| DK-05 | Cores de cliente e categoria são exibidas com opacidade reduzida sobre fundo escuro |
| DK-06 | Gráficos ajustam eixos, grades e rótulos ao tema |
| DK-07 | Todo componente é validado por contraste nos dois temas |

**Contraste medido no tema escuro.** `--dt-color-primary` sobre `--dt-surface-page` 5.3:1; texto secundário sobre a mesma superfície 8.7:1. O par mais apertado da paleta é `--dt-color-primary` sobre `--dt-surface-card`, em 4.6:1 — acima do mínimo AA de 4.5:1, mas sem folga: é o primeiro a revalidar se qualquer um dos dois tokens mudar.

---

## 16. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-D-01 | Duração maior que 999 horas | Exibida integralmente (`1024:30`); a coluna se ajusta |
| CE-D-02 | Nome de cliente muito longo | Truncado com reticências e `title` completo; nunca quebra o layout |
| CE-D-03 | Descrição de registro muito longa em tabela | Truncada em duas linhas com expansão ao clicar |
| CE-D-04 | Contrato sem valor hora | Colunas monetárias ocultadas, sem espaço vazio |
| CE-D-05 | Saldo indisponível por falha de cálculo | Exibe "—" com ícone de alerta e tooltip; **nunca** um número possivelmente errado (§15 do PRD) |
| CE-D-06 | Mais de 10 contratos no dashboard | Exibe os 6 mais críticos com link "ver todos" |
| CE-D-07 | Cor de cliente com contraste insuficiente | Ajustada automaticamente na exibição, preservando o valor salvo |
| CE-D-08 | Impressão de tela | Folha de estilo dedicada: sem menu, sem cores de fundo, com quebras de página coerentes |

## 17. Casos de erro visuais

| Situação | Tratamento |
|---|---|
| Imagem ou avatar não carrega | Substituto com iniciais sobre a cor de identificação |
| Gráfico sem dados | Área com mensagem "sem dados no período", nunca gráfico vazio |
| Texto excede o contêiner | Truncamento com reticências e conteúdo completo em `title` |
| Rede lenta | Esqueletos após 200ms; nunca tela branca |
| Falha em recurso não essencial | Área degrada silenciosamente, sem bloquear a tela |

## 18. Critérios de aceite

| # | Critério |
|---|---|
| CA-01 | Nenhum valor de cor, tamanho ou espaçamento é usado fora dos tokens |
| CA-02 | As cores de severidade têm significado único em todo o produto |
| CA-03 | Nenhuma informação é transmitida apenas por cor |
| CA-04 | Todos os atalhos funcionam e estão documentados na tela de ajuda |
| CA-05 | Zero violações do axe-core nas telas principais, nos dois temas |
| CA-06 | Todo estado vazio tem título, texto explicativo e ação |
| CA-07 | Todo código de erro possui mensagem em linguagem natural mapeada |
| CA-08 | Durações usam fonte monoespaçada em toda a interface |
| CA-09 | Nenhum identificador técnico aparece na interface |
| CA-10 | A interface é funcional com zoom de 200% |

## 19. Dependências e impactos

| Documento | Relação |
|---|---|
| `layouts.md` | Aplica os tokens na estrutura de página |
| `components.md` | Implementa os padrões visuais aqui definidos |
| `pages.md` | Compõe telas com estes componentes |
| `03-architecture/frontend.md` | Define a implementação técnica |
| `01-product/personas.md` | Fornece as preferências que originam os princípios |

**Impacto:** alterar um token semântico afeta toda a interface e exige revalidação de contraste nos dois temas.
