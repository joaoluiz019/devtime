# 020 — AI *(pós-MVP)*

| Campo | Valor |
|---|---|
| **Feature** | 020 |
| **Épico** | EP-20 (Inteligência Artificial) |
| **Fase** | F7 |
| **Depende de** | `008-worklogs`, `011-bank-hours`, `012-reports` |
| **Status** | `FUTURE` — não implementável (FU-01) |

> **Nenhum enunciado desta spec é normativo.** As regras candidatas estão em `docs/07-backlog/future.md` §9.4 com prefixo `RN-F-` — que marca justamente que **não** são `RN-XXX` de `business-rules.md`. Implementá-las viola SP-01 e IA-01.

## 1. Objetivo

Assistir o usuário com quatro capacidades derivadas do histórico de trabalho: resumo executivo de período, geração de tickets a partir de texto livre, estimativa de horas e detecção de inconsistências nos registros.

## 2. Princípio inviolável

> **A IA assiste, nunca decide (PR-07).**
> Nenhuma saída de IA altera dado de negócio sem confirmação humana explícita.

Este princípio não é uma diretriz de implementação — é o que define a fronteira desta feature. Ele é a razão pela qual as quatro capacidades da §5 produzem **rascunho, sugestão ou apontamento**, e nunca escrita direta.

A consequência prática: nenhuma saída de IA pode alcançar `WorkLog`, `ContractPeriod` ou `PeriodSnapshot` sem passar por uma ação afirmativa de uma pessoa. Um resumo de período gerado por IA é um texto que o usuário revisa e edita antes de enviar; uma estimativa é um número exibido ao lado do campo, não dentro dele.

## 3. Por que está fora do MVP

**Exige base histórica.** As quatro capacidades derivam de padrões nos dados: estimativa depende de tickets similares anteriores, detecção de anomalia depende de uma média pessoal, resumo depende de descrições suficientes para sintetizar. Em um tenant novo, todas produzem saída de baixa qualidade — e uma sugestão ruim é pior que nenhuma sugestão, porque ensina o usuário a ignorá-las.

`docs/07-backlog/future.md` §9.5 é explícito quanto à pré-condição: **6 meses de histórico e 100.000 registros**.

O segundo motivo é de **custo e privacidade**: enviar dados a um provedor externo exige consentimento, remoção de dado de cliente, orçamento por tenant e uma política de privacidade atualizada. Nenhuma dessas decisões faz sentido antes de haver receita que sustente o custo (PC-03).

## 4. Fronteira preservada hoje

| Item | Onde já existe | Estado no MVP |
|---|---|---|
| `WorkLogSource` com `AI_SUGGESTION` | §6.13 `entities.md` | **Já no enum**, sem caminho de entrada |
| `WorkLogSource` com `IMPORT` | §6.13 | Idem — mesmo padrão de origem não humana |
| RN-126: `source` é imutável | `business-rules.md` §5.4 | Normativa — garante que a métrica de origem é confiável |
| `WorkLogService.create` como único caminho de validação | RN-159, OB-08 de `008` | Sugestão aceita entra por aqui, com **todas** as validações |
| Descrições de work log em texto livre | §6.13 | São a entrada principal das quatro capacidades |
| Categorias com semântica estável | §6.10, RN-501 | Base para "categoria incoerente" |
| Histórico de tags por work log | `work_log_tags` | Base de treino para sugestão de tag (OB-06 de `006`) |
| Agregações por dia, cliente e categoria | `010-dashboard` §13.4 | Índices cobertos que a detecção reusaria |
| `PeriodSnapshot.payload` com lista completa de registros | §6.9 `entities.md` | Entrada natural do resumo de período |
| Trilha de auditoria com `actorType` | §6.20 | Base para registrar ação originada de sugestão |
| Spike SP-05 previsto | §13 `future.md` | Decisão de provedor ainda **não** tomada |

> **Consequência:** o MVP já produz e armazena tudo de que esta feature precisa como entrada, e já reserva `AI_SUGGESTION` como origem. Nada precisa ser reconstruído — o que falta é a decisão de provedor, o consentimento e a base histórica.

## 5. Capacidades e guardrails

Reproduz §9.2 de `docs/07-backlog/future.md`:

| # | Capacidade | Entrada | Saída | **Guardrail** |
|---|---|---|---|---|
| FT-071 | Resumo de período | Descrições e categorias dos registros | Texto executivo | **Rascunho editável**; nunca enviado sem revisão |
| FT-072 | Geração de tickets | Texto livre do usuário | Lista estruturada | **Confirmação individual** antes de criar cada um |
| FT-073 | Estimativa de horas | Título, descrição e histórico de tickets similares | Faixa com intervalo de confiança | **Exibida como sugestão**; nunca preenche o campo automaticamente |
| FT-074 | Detecção de inconsistências | Registros do período | Lista de apontamentos | **Apenas sinaliza**; nunca altera |

### 5.1 Catálogo de detecção (§9.3 de `future.md`)

| Tipo | Descrição | Severidade |
|---|---|---|
| Lacuna de tempo | Intervalo longo sem registro em dia com atividade | Informação |
| Descrição genérica | "Ajustes", "Desenvolvimento", "Trabalho" sem contexto | Aviso |
| Descrição duplicada | Mesma descrição em muitos registros | Informação |
| Pico anômalo | Dia com volume muito acima da média pessoal | Aviso |
| Sessão suspeita | Duração exata e repetida | Aviso |
| Categoria incoerente | Descrição sugere categoria diferente da escolhida | Informação |
| Ticket sem progresso | Muitas horas sem mudança de status | Informação |

## 6. O que não pode ser quebrado

**Esta é a única seção com efeito vinculante hoje.**

| # | Decisão a preservar | Consequência de quebrar |
|---|---|---|
| FR-01 | **`WorkLogSource.AI_SUGGESTION` no enum, sem caminho de entrada** | Remover o valor por ser código morto custaria migration de enum. Mantê-lo é gratuito |
| FR-02 | **RN-126: `source` e `timerId` imutáveis** | É o que torna a métrica "% de horas por origem" confiável. Sem ela, seria impossível medir a qualidade das sugestões |
| FR-03 | **`WorkLogService.create` é o único caminho de validação** (RN-159, CP-14 de `008`) | Uma sugestão aceita **precisa** passar pelas mesmas RN-102 a RN-120. Um caminho paralelo produziria registros sugeridos que violam sobreposição ou saldo |
| FR-04 | **§19.1 de `009-timer`: histórico de pausas não é exposto nem obrigatório** | É a decisão que impede o produto de virar ferramenta de vigilância. A detecção de anomalia é o vetor mais provável de erodi-la |
| FR-05 | **§9 de `permissions.md`: `MEMBER` vê apenas as próprias horas** | Detecção de inconsistência apresentada ao gestor de forma nominal violaria isso e NO-05 |
| FR-06 | **Descrições de work log nunca entram em log de aplicação** (§28 de `008`) | São a entrada principal da IA; tratá-las como dado sensível desde já é o que torna o consentimento defensável |
| FR-07 | **Toda saída de IA é rascunho, sugestão ou apontamento** | PR-07. Uma única exceção transformaria a feature em automação de decisão de negócio |
| FR-08 | **`PeriodSnapshot` imutável** (INV-SNP-01) | Um resumo gerado por IA **não** pode ser gravado no snapshot; ele é conteúdo derivado, não registro contratual |
| FR-09 | **Categorias com `isSystem` e semântica estável** (RN-501, RN-503) | "Categoria incoerente" pressupõe que o significado das categorias não muda arbitrariamente |
| FR-10 | **RN-113: arredondamento sempre para baixo** (PR-03) | Uma estimativa de IA nunca pode virar um valor cobrado; a fronteira entre sugestão e cobrança é aritmética e absoluta |
| FR-11 | **Nenhum dado é enviado a provedor externo hoje** | O MVP não tem integração de IA. Introduzi-la sem consentimento implementado seria tratamento de dado sem base legal |

## 7. Dependências

| Feature | Tipo | O que consome |
|---|---|---|
| `008-worklogs` | **Bloqueante** | Descrições, categorias e padrões temporais — a matéria-prima das quatro capacidades |
| `011-bank-hours` | **Bloqueante** | Saldo e consumo para o resumo de período |
| `012-reports` | **Bloqueante** | O resumo é conteúdo de relatório; o snapshot é a entrada |
| `007-tickets` | Bloqueante | Histórico de tickets similares para estimativa |
| `006-tags` | Consumidora | Sugestão de tag a partir da descrição (OB-06 daquela spec) |
| `018-subscriptions` | **Bloqueante** | Orçamento de IA por tenant depende de plano (PC-03) |
| Provedor de IA | **Externo** | Decisão ainda não tomada — spike SP-05 |

## 8. Regras preliminares — **candidatas, não normativas**

Reproduz §9.4 de `docs/07-backlog/future.md`. O prefixo `RN-F-` marca que **não** são regras de `business-rules.md`:

| ID candidato | Enunciado candidato |
|---|---|
| `RN-F-030` | Nenhum dado é enviado a provedor de IA sem consentimento explícito por tenant, configurável e revogável |
| `RN-F-031` | Dados de cliente (nome, documento, valores) são **removidos** antes do envio; apenas descrições de trabalho são enviadas |
| `RN-F-032` | Orçamento mensal por tenant; ao esgotar, a funcionalidade fica indisponível com aviso claro |
| `RN-F-033` | Respostas são cacheadas por hash da entrada |
| `RN-F-034` | Toda saída de IA é visualmente sinalizada como gerada por IA |
| `RN-F-035` | Falha do provedor degrada a funcionalidade, nunca o sistema |
| `RN-F-036` | Nenhuma detecção de inconsistência gera notificação automática ao gestor sobre um membro específico |

### 8.1 Sobre `RN-F-036` — a regra mais importante desta feature

`docs/07-backlog/future.md` registra a justificativa: *"transformar a detecção em vigilância automatizada destruiria a confiança dos executores (persona Diego) e violaria NO-05. Os apontamentos são apresentados ao **próprio autor** dos registros e, de forma agregada e não nominal, ao gestor."*

É a mesma decisão que aparece em três lugares do MVP:

| Onde | Decisão coerente |
|---|---|
| §19.1 de `009-timer` | Histórico de pausas não é exposto; `reason` nunca obrigatório |
| §9 de `permissions.md` | `MEMBER` vê apenas as próprias horas |
| §4 de `010-dashboard` | "Indicadores de produtividade individual" explicitamente fora de escopo |

A detecção de inconsistências é o ponto em que essa decisão é mais fácil de quebrar — e o mais consequente. Um relatório automático de "pico anômalo" enviado ao gestor com o nome do executor converte uma ferramenta de registro em ferramenta de controle, e o executor deixa de registrar honestamente.

## 9. Impacto nas features existentes

| Feature | O que muda | Aditivo? |
|---|---|---|
| `002-users` | Consentimento por tenant; orçamento de IA nas configurações | ✔ |
| `006-tags` | Sugestão de tag a partir da descrição | ✔ |
| `007-tickets` | Geração de tickets a partir de texto livre; estimativa sugerida | ✔ — confirmação individual |
| `008-worklogs` | Caminho de aceite de sugestão, entrando por `create` com `source = AI_SUGGESTION` | ✔ — reusa validação |
| `010-dashboard` | Bloco de apontamentos, **apenas para o próprio usuário** | ⚠ — exige respeitar `RN-F-036` |
| `011-bank-hours` | Resumo de período como conteúdo derivado, **fora** do snapshot | ⚠ — não pode tocar INV-SNP-01 |
| `012-reports` | Resumo executivo como seção editável do relatório | ⚠ — precisa ser sinalizado (`RN-F-034`) |
| `013-notifications` | **Nenhuma** notificação nominal de inconsistência | ⚠ — `RN-F-036` |

> **Quatro impactos exigem decisão explícita antes de código**, e três deles são sobre a mesma coisa: garantir que a detecção não se torne vigilância e que a saída de IA não contamine registro contratual.

## 10. Riscos de antecipação

| # | Risco de construir antes do tempo |
|---|---|
| RA-01 | **Sugestão de baixa qualidade ensina o usuário a ignorá-la.** Sem base histórica, as quatro capacidades produzem saída ruim, e a feature perde credibilidade de forma difícil de recuperar |
| RA-02 | **`RN-F-036` é a regra mais fácil de quebrar sem perceber.** Um bloco de dashboard mostrando "apontamentos da equipe" ao gestor parece útil e destrói a confiança dos executores (NO-05) |
| RA-03 | **Custo por tenant não modelado vira prejuízo.** Sem `018-subscriptions`, não há como limitar consumo nem repassar custo (PC-03) |
| RA-04 | **Envio de dado a provedor externo sem consentimento é tratamento sem base legal.** `RN-F-030` e `RN-F-031` não são opcionais e exigem política de privacidade atualizada (PC-04) |
| RA-05 | **Uma saída de IA que escreve direto viola PR-07 permanentemente.** Reverter depois é possível no código, não na expectativa do usuário |
| RA-06 | **Estimativa exibida como número exato é interpretada como compromisso.** A faixa com intervalo de confiança de FT-073 existe para evitar isso |
| RA-07 | Resumo gravado no snapshot tornaria conteúdo gerado por IA parte de registro contratual imutável (FR-08) |
| RA-08 | Provedor escolhido sem spike produz custo, latência e qualidade desconhecidos (PC-02) |

## 11. Pré-condições

Reproduz §9.5 de `docs/07-backlog/future.md`. Todas são **obrigatórias**:

| # | Condição | Verificável por |
|---|---|---|
| PC-01 | Base histórica de ao menos **6 meses e 100.000 registros** | Contagem em `work_logs` |
| PC-02 | **ADR de provedor aprovada**, com custo por tenant projetado (spike SP-05) | `docs/03-architecture/architecture.md` |
| PC-03 | **Margem do plano suporta o custo de IA** | Depende de `018-subscriptions` |
| PC-04 | **Política de privacidade atualizada e consentimento implementado** | `RN-F-030` |

> **PC-01 é a razão de esta feature estar em F7 e não antes.** As outras três são consequência de decisões de produto e de negócio; esta é aritmética: sem dados, não há padrão a detectar.

## 12. Gatilho de priorização

| # | Sinal objetivo |
|---|---|
| GP-01 | **PC-01 a PC-04 integralmente atendidas** |
| GP-02 | Usuários relatando esforço repetitivo em escrever resumos de período para clientes |
| GP-03 | Evidência de estimativas sistematicamente incorretas — `ticket.over_estimate.ratio` de `007` acima de 60% |
| GP-04 | Demanda por revisão de qualidade dos registros antes do fechamento |

> **GP-03 é o gatilho mais informativo** porque é mensurável hoje: a métrica `ticket.over_estimate.ratio` já é coletada por `007-tickets` (§29 daquela spec). Se ela mostrar que as estimativas humanas erram sistematicamente, FT-073 tem valor demonstrável. Se não mostrar, a capacidade resolve um problema que não existe.

## 13. Observação final

Esta é a única feature do roadmap cujo **princípio** é mais restritivo que suas capacidades. PR-07 — *a IA assiste, nunca decide* — não é uma limitação técnica a ser superada em uma versão futura: é a definição do que a feature é.

Um sistema de registro de horas cujo número final foi decidido por IA não é auditável. O cliente que questiona uma linha do relatório precisa de uma resposta que termine em uma pessoa. Por isso `AI_SUGGESTION` existe como `source` desde o MVP: quando a sugestão for aceita, o registro carrega para sempre a marca de que **começou** como sugestão — e RN-126 garante que essa marca não pode ser apagada.
