# 017 — Permissions *(pós-MVP)*

| Campo | Valor |
|---|---|
| **Feature** | 017 |
| **Épico** | EP-16 (Equipe e Permissões) · EP-17 (Aprovação e Custos) |
| **Fase** | F5 |
| **Depende de** | `016-teams` |
| **Status** | `FUTURE` — não implementável (FU-01) |

> **Nenhum enunciado desta spec é normativo.** As regras da §6 são candidatas e não existem em `docs/02-domain/business-rules.md`. Implementá-las viola SP-01 e IA-01.

## 1. Objetivo

Refinar a autorização além do RBAC fixo: conceder a um `MEMBER` acesso explícito a contratos específicos, introduzir fluxo de aprovação de horas e habilitar custo interno por membro para análise de margem.

## 2. Por que está fora do MVP

O RBAC com escopo de dados da §7 de `permissions.md` cobre integralmente os segmentos-alvo. São 5 papéis e mais de 60 células de matriz, todos definidos, testáveis exaustivamente e auditáveis.

Permissões granulares por contrato resolvem um problema que só existe com equipe grande e clientes que exigem segregação — situação que a persona primária não tem. Fluxo de aprovação resolve um problema de **hierarquia**, que pressupõe alguém aprovando o trabalho de outro.

Antecipar transformaria uma matriz verificável em um sistema de políticas cuja combinação é impossível de testar exaustivamente. §14 de `permissions.md` é explícito: *"o modelo é RBAC com escopo de dados, não ABAC. Papéis fixos são suficientes para os segmentos-alvo e mantêm a matriz auditável."*

## 3. Fronteira preservada hoje

| Item | Onde já existe | Estado no MVP |
|---|---|---|
| **Verificação por permissão atômica, não por papel** | §14 `permissions.md` | Implementado — é a decisão que torna esta feature aditiva |
| `@PreAuthorize("hasPermission(...)")` no **service** | IMP-01 | Implementado em todas as features |
| Escopo de dados por `Specification` no repositório | IMP-02 | Implementado em `003`, `007`, `008`, `010`, `012` |
| Definição operacional de "contrato vinculado" | §9 `permissions.md` | Já normativa: work log ou ticket como relator/responsável |
| `Membership.defaultHourlyCost` e `costCurrency` | §6.3 `entities.md` | **Persistidos, sem uso** — reservados para EP-17 |
| `WORKLOG_UPDATE_LOCKED` | §6.6 `permissions.md` | Já no catálogo, restrita a `OWNER`/`ADMIN` |
| Teste por célula da matriz | IMP-07, CA-01 | Implementado; é a base sobre a qual o modelo granular é validado |
| Modelo de evolução declarado | §14 `permissions.md` | Tabela de F5 a F8 já registra `contract_members` e `approvalStatus` |

> **A decisão de FR-04 de `016-teams` é o que torna esta feature viável:** como o código verifica **permissões atômicas**, e não papéis, a única mudança estrutural é a **origem** do conjunto de permissões. Nenhuma verificação de autorização precisa ser reescrita.

## 4. O que não pode ser quebrado

**Esta é a única seção com efeito vinculante hoje.**

| # | Decisão a preservar | Consequência de quebrar |
|---|---|---|
| FR-01 | **Código verifica permissão atômica, nunca papel** | Verificar `role == 'ADMIN'` em qualquer ponto obrigaria a reescrever aquele ponto quando papéis se tornarem customizáveis. É a decisão mais importante desta fronteira |
| FR-02 | **`@PreAuthorize` no service, não apenas no controller** (IMP-01) | Um serviço chamado por job ou por outra feature ficaria desprotegido; com políticas granulares, o número de chamadores cresce |
| FR-03 | **Escopo de dados por `Specification` no repositório** (IMP-02) | Políticas granulares tornam o escopo dinâmico; filtrar em memória seria inviável e vazaria |
| FR-04 | **Definição operacional de "contrato vinculado" (§9)** | `contract_members` de F5 **estende** essa definição; alterá-la agora mudaria o escopo atual de `MEMBER` |
| FR-05 | **`Membership.defaultHourlyCost` persistido** | É o campo que EP-17 consome; removê-lo por ser código morto custaria uma migration e a perda do dado histórico |
| FR-06 | **IMP-05: toda negação registrada em log estruturado** | Com políticas compostas, o log de negação é a única forma de depurar por que um acesso foi recusado |
| FR-07 | **`requiredPermission` na resposta `403`** (§13 `permissions.md`) | É o que permite à UI orientar o usuário a solicitar acesso |
| FR-08 | **Ordem obrigatória das 8 verificações** (§4.1 `permissions.md`) | Tenancy antes de ownership impede que recurso de outro tenant produza `403` (ART-024) |
| FR-09 | **RN-121: `lockedAt` impede edição, independentemente de permissão** | OWN-02 é explícito: ownership não sobrepõe guarda de estado. Aprovação de horas não pode criar exceção a isso |
| FR-10 | **Nenhum work log possui campo de aprovação hoje** | Adicionar `approvalStatus` é aditivo; **presumir** um valor default errado no MVP criaria dado inconsistente |

## 5. Dependências

| Feature | Tipo | O que consome |
|---|---|---|
| `016-teams` | **Bloqueante** | Equipe real; sem múltiplos membros, permissões granulares não têm objeto |
| `002-users` | Bloqueante | `Membership`, papéis |
| `004-contracts` | Bloqueante | Contratos como alvo de `contract_members` |
| `008-worklogs` | Bloqueante | Work logs como alvo de aprovação |
| `019-public-api` | Consumidora | Escopos de chave de API mapeiam para o mesmo catálogo de permissões |

## 6. Regras preliminares — **candidatas, não normativas**

### 6.1 Permissões por contrato (EP-16)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-4xx?` | `contract_members (contractId, userId)` concede a um `MEMBER` acesso explícito a um contrato | §14 `permissions.md` já registra a tabela |
| `RN-4xx?` | A definição de "contrato vinculado" da §9 passa a considerar também `contract_members` | **Estende**, não substitui |
| `RN-4xx?` | Conceder acesso explícito exige `CONTRACT_UPDATE` | Reusa permissão existente |

### 6.2 Aprovação de horas (EP-17)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-1xx?` | `WorkLog` ganha `approvalStatus` com `PENDING`, `APPROVED`, `REJECTED` | §14 `permissions.md` já registra o campo |
| `RN-1xx?` | Nova permissão `WORKLOG_APPROVE` | Papéis existentes não mudam |
| `RN-1xx?` | Fechamento de período exige todos os work logs aprovados | **Alteraria RN-241** — decisão de alto impacto, ver §8 |
| `RN-1xx?` | Work log rejeitado volta a editável, mesmo fora da janela retroativa | Interage com RN-120 |
| `RN-1xx?` | Aprovação é imutável; reversão por novo evento | Coerente com RN-236 |

### 6.3 Custo interno (EP-17)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-1xx?` | Custo do work log deriva de `membership.defaultHourlyCost` no momento da criação | Congelamento, como `hourlyRateSnapshot` em `004` |
| `RN-1xx?` | Margem = valor faturado − custo interno | Novo relatório em `012` |
| `RN-1xx?` | Custo interno visível apenas com nova permissão `COST_VIEW` | `MEMBER` **nunca** vê o próprio custo nem o de colegas |

> Nenhum destes IDs existe. As faixas de numeração são `RN-100`–`RN-149` (work log) e `RN-450`–`RN-499` (membros), conforme §3.2 de `business-rules.md`.

## 7. Impacto nas features existentes

| Feature | O que muda | Aditivo? |
|---|---|---|
| `002-users` | Tela de concessão de acesso por contrato | ✔ |
| `003-clients` | Escopo da nota ² passa a considerar `contract_members` | ✔ — estende a `Specification` |
| `004-contracts` | Ganha aba de membros com acesso explícito | ✔ |
| `008-worklogs` | Ganha `approvalStatus`; edição de rejeitado; custo congelado | ⚠ — novo campo e novo estado |
| `010-dashboard` | Bloco de horas pendentes de aprovação | ✔ |
| `011-bank-hours` | **Fechamento pode exigir aprovação total** | ⚠ — **alteraria RN-241** |
| `012-reports` | Relatório de margem; filtro por status de aprovação | ✔ |
| `013-notifications` | Tipos de horas pendentes, aprovadas e rejeitadas | ✔ |

> **Apenas dois impactos não são aditivos.** O de `011` é o mais sério: exigir aprovação para fechar altera a sequência atômica de RN-241, que é o núcleo crítico do produto. Ver §8.

## 8. Riscos de antecipação

| # | Risco de construir antes do tempo |
|---|---|
| RA-01 | **Combinação explosiva de políticas.** RBAC fixo tem matriz finita e testável; políticas por contrato tornam o número de combinações inviável de cobrir exaustivamente, contrariando IMP-07 e CA-01 |
| RA-02 | **Aprovação alteraria RN-241.** Exigir aprovação total para fechar adiciona uma guarda à sequência atômica de 7 passos — a operação mais crítica do sistema (§6.3 de `011`). Fazer isso antes de o fechamento estar comprovadamente estável em produção multiplicaria o risco RP-03 |
| RA-03 | **Custo interno é dado sensível de relação de trabalho.** Expô-lo indevidamente é problema trabalhista, não apenas de privacidade. Exige decisão de produto explícita sobre quem vê o quê |
| RA-04 | Aprovação pressupõe hierarquia; introduzi-la em um produto usado por freelancer autônomo adiciona atrito sem contrapartida |
| RA-05 | Cada política granular é um caminho a mais para negação de acesso legítimo — e suporte a "não consigo ver X" é o tipo de chamado mais caro de diagnosticar |

## 9. Gatilho de priorização

| # | Sinal objetivo | Aciona |
|---|---|---|
| GP-01 | Tenant com equipe pedindo segregação de acesso entre clientes | EP-16 granular |
| GP-02 | Cliente final exigindo contratualmente que apenas pessoas designadas vejam seus dados | EP-16 granular |
| GP-03 | Tenant relatando horas incorretas chegando ao relatório por falta de revisão | EP-17 aprovação |
| GP-04 | Demanda explícita por análise de margem por projeto | EP-17 custos |

> **Pré-condição inegociável para EP-17 (aprovação):** o fechamento de período de `011` precisa estar **estável em produção por ao menos dois ciclos completos**, sem nenhuma divergência de saldo reportada (SQ-10). Adicionar uma guarda a RN-241 antes disso significaria alterar código crítico ainda não validado pelo uso real.
