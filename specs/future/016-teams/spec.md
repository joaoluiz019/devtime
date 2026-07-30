# 016 — Teams *(pós-MVP)*

| Campo | Valor |
|---|---|
| **Feature** | 016 |
| **Épico** | EP-16 (Equipe e Permissões) |
| **Fase** | F5 |
| **Depende de** | `002-users` |
| **Status** | `FUTURE` — não implementável (FU-01) |

> **Nenhum enunciado desta spec é normativo.** As regras da §6 são candidatas e não existem em `docs/02-domain/business-rules.md`. Implementá-las viola SP-01 e IA-01.

## 1. Objetivo

Transformar o tenant de conta individual em espaço de trabalho compartilhado: convidar pessoas por e-mail, aceitar convite, gerenciar o ciclo de vida do vínculo e operar o produto com mais de um executor.

## 2. Por que está fora do MVP

A persona primária de `docs/01-product/personas.md` **opera sozinha**. O freelancer que registra as próprias horas e emite os próprios relatórios não precisa convidar ninguém — e construir gestão de equipe para ele seria resolver um problema que ele não tem.

O adiamento não é por falta de tempo. É porque o valor do produto precisa ser provado com um único usuário antes de assumir a complexidade de vários: escopo de dados por papel, notificações a terceiros, conflitos de edição concorrente e a pergunta "quem pode ver as horas de quem", que atravessa quase todas as features.

## 3. Fronteira preservada hoje

O MVP **já** contém a estrutura completa de multiusuário. Nada precisa ser reconstruído:

| Item | Onde já existe | Estado no MVP |
|---|---|---|
| Entidade `Membership` com `role` e `status` | §6.3 `entities.md` | Criada e usada; o tenant nasce com um `Membership` `OWNER` |
| Máquina de estados de `Membership` | §4.3 `state-machines.md` | 4 estados, incluindo `INVITED` — **já modelada** |
| Papéis `OWNER`, `ADMIN`, `MANAGER`, `MEMBER`, `VIEWER` | §5 `permissions.md` | Enum completo; matriz de 60+ células definida |
| Escopo de dados por papel | §9 `permissions.md` | Definido e **implementado** em `003`, `007`, `008`, `010`, `012` |
| `MEMBER_INVITE`, `MEMBER_UPDATE_ROLE`, `MEMBER_SUSPEND`, `MEMBER_REMOVE` | §6.2 `permissions.md` | Permissões **já** no catálogo |
| RN-455 a RN-461 | `business-rules.md` §10 | Regras de membro **já normativas** |
| Efeitos cruzados da remoção | §5 `state-machines.md` | Timer descartado (RN-460); tickets reatribuídos ao `OWNER` |
| Tela P32 e P07 | `pages.md` | Previstas, marcadas F5 |

> **Consequência:** esta feature é, em grande medida, **expor o que já existe**. O modelo de dados, as permissões e as regras estão prontos. O que falta é o fluxo de convite, as telas e a validação sob uso real com múltiplas pessoas.

## 4. O que não pode ser quebrado

**Esta é a única seção com efeito vinculante hoje.**

| # | Decisão a preservar | Consequência de quebrar |
|---|---|---|
| FR-01 | **`Membership` como entidade separada de `User`** | Um `User` participa de N tenants com papéis distintos (CE-P-01). Fundir as duas tornaria impossível o mesmo profissional atender dois clientes no produto |
| FR-02 | **Papel vem exclusivamente do `TenantContext`** (IMP-03) | Papel vindo da requisição é escalonamento horizontal de privilégio |
| FR-03 | **Escopo de dados aplicado no repositório** (IMP-02) | Filtragem em memória vaza por contagem, paginação e tempo de resposta — e o vazamento só aparece com múltiplos usuários, ou seja, exatamente quando esta feature entrar |
| FR-04 | **Código verifica permissões atômicas, nunca papéis** (§14 `permissions.md`) | É o que permite papéis customizados em `017` sem reescrever autorização |
| FR-05 | **RN-455: sempre existe ao menos um `OWNER` ativo** | Um tenant sem `OWNER` é irrecuperável sem intervenção manual |
| FR-06 | **OWN-01: work log pertence ao `userId`, não a quem criou** | Sem isso, `MANAGER` lançando por terceiro se tornaria dono do registro |
| FR-07 | **RN-458: remover membro preserva work logs, tickets e comentários** | Perda de dado contratual devido ao cliente |
| FR-08 | **RN-460: timer de membro removido é descartado** | Timer órfão sem dono, impossível de encerrar |
| FR-09 | **IMP-04: alterar papel invalida os access tokens no tenant** | Janela de até 15 min com privilégio revogado ainda válido |
| FR-10 | **RN-102 (sobreposição) restrita por `userId`** | Com vários executores, verificar globalmente rejeitaria trabalho paralelo legítimo (CE-07) |
| FR-11 | **§9 de `permissions.md`: `MEMBER` vê todos os tickets, apenas as próprias horas** | A assimetria é deliberada e é o núcleo da privacidade do produto (§19.1 de `008` e `009`) |
| FR-12 | **`Membership.defaultHourlyCost` já persistido, sem uso** | É o campo que `017` consome para custo interno; removê-lo por ser "código morto" custaria uma migration |

## 5. Dependências

| Feature | Tipo | O que consome |
|---|---|---|
| `002-users` | **Bloqueante** | `Membership`, papéis, `TenantContext`, RN-455 a RN-461 |
| `013-notifications` | Consumidora | Convite por e-mail reusa o canal transacional |
| `017-permissions` | Consumidora | Depende desta para existir |
| `018-subscriptions` | Consumidora | Limite de membros por plano |

## 6. Regras preliminares — **candidatas, não normativas**

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-462?` | Convite exige e-mail válido e papel; o convidado pode não ter conta no DevTime | RN-457 (expiração em 7 dias) **já** existe |
| `RN-463?` | Aceitar convite sem conta cria o `User` no mesmo fluxo | Reusa `001-authentication` |
| `RN-464?` | O mesmo e-mail não recebe dois convites pendentes no mesmo tenant | Coerente com INV-MEM-01 |
| `RN-465?` | Convite revogado invalida o token imediatamente | §4.3 SM já contempla `INVITED → REMOVED` |
| `RN-466?` | Readmissão gera **novo** `Membership`, preservando o anterior | §4.3 SM já proíbe `REMOVED → *` |
| `RN-467?` | Limite de membros ativos por plano | Depende de `018`; no MVP não há limite |

> Nenhum destes IDs existe. Ao promover a feature, os números definitivos são atribuídos em `business-rules.md`, respeitando a faixa `RN-450`–`RN-499` (§3.2 daquele documento).

## 7. Impacto nas features existentes

| Feature | O que muda |
|---|---|
| `002-users` | Ganha o fluxo de convite, aceite e revogação; telas P07 e P32 |
| `003-clients` | O escopo da nota ² passa a ter efeito real — hoje `MEMBER` vê lista vazia por ausência de work logs (OB-05 daquela spec) |
| `007-tickets` | Reatribuição ao `OWNER` na remoção passa a ocorrer de fato |
| `008-worklogs` | RN-106 (lançar por terceiro) passa a ser exercitada |
| `009-timer` | `TIMER_VIEW_ANY` e `TIMER_STOP_ANY` ganham uso real |
| `010-dashboard` | `scope = USER` para `MEMBER` passa a ter efeito visível |
| `012-reports` | CE-P-10 (`MEMBER` só exporta `myWorkLogs`) passa a ser exercitado |
| `013-notifications` | Destinatários múltiplos por evento (RN-607) passam a gerar N notificações |

> **Nenhuma dessas mudanças exige alteração de modelo de dados.** Todas as regras já estão implementadas e testadas; elas apenas deixam de ser exercitadas com um único usuário.

## 8. Riscos de antecipação

| # | Risco de construir antes do tempo |
|---|---|
| RA-01 | Fluxo de convite desenhado sem uso real produz atrito que só aparece com pessoas reais convidando pessoas reais |
| RA-02 | Escopo de dados de `MEMBER` é a área de maior risco de vazamento, e testá-lo exige cenários multiusuário que o MVP não tem |
| RA-03 | Toda tela ganha uma dimensão de "de quem é isso" — antecipar multiplica o esforço de UI antes de o valor central estar provado |
| RA-04 | Convite por e-mail depende de reputação de remetente; construir antes de ter tráfego real dificulta calibrar entregabilidade |

## 9. Gatilho de priorização

| # | Sinal objetivo |
|---|---|
| GP-01 | **3 ou mais participantes do beta fechado solicitarem** convidar alguém (CE-M-01 de `mvp.md`) |
| GP-02 | Um participante relatar que **deixou de usar** o produto por não poder convidar sócio ou colaborador |
| GP-03 | Demanda comercial explícita de conta com mais de um executor |

> **GP-01 é o gatilho canônico.** Antes dele, a decisão de adiar permanece correta, e antecipar é resolver um problema hipotético.
