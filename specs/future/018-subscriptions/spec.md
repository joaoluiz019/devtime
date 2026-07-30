# 018 — Subscriptions *(pós-MVP)*

| Campo | Valor |
|---|---|
| **Feature** | 018 |
| **Épico** | EP-18 (Planos e Cobrança) · EP-19 (Portal do Cliente) |
| **Fase** | F6 |
| **Depende de** | `016-teams` |
| **Status** | `FUTURE` — não implementável (FU-01) |

> **Nenhum enunciado desta spec é normativo.** As regras da §6 são candidatas e não existem em `docs/02-domain/business-rules.md`. Implementá-las viola SP-01 e IA-01.

## 1. Objetivo

Monetizar o produto: planos com limites, cobrança recorrente, ciclo de vida da assinatura e — como consequência natural do mesmo épico — o portal de acesso do cliente final aos próprios contratos, em leitura.

## 2. Por que está fora do MVP

**Validar valor antes de monetizar.** Construir cobrança para um produto cujo valor não foi provado é a inversão mais custosa possível: gateway de pagamento, ciclo de faturamento, tratamento de inadimplência, notas fiscais e conciliação — tudo para um produto que talvez precise mudar de forma depois do beta.

O MVP contempla explicitamente a suspensão por inadimplência (RN-007) e o cancelamento (RN-008) **sem** contemplar o pagamento que os causaria. É deliberado: o comportamento do sistema sob um tenant suspenso é uma decisão de arquitetura que precisa existir desde o início; quem dispara a suspensão é uma decisão comercial que pode esperar.

`docs/07-backlog/future.md` §12 registra o princípio: monetização entra quando houver evidência de disposição a pagar, não quando o produto estiver "pronto".

## 3. Fronteira preservada hoje

| Item | Onde já existe | Estado no MVP |
|---|---|---|
| `Tenant.planCode` | §6.1 `entities.md` | **Persistido** com default `FREE`, sem uso |
| `TenantStatus` com `SUSPENDED` e `CANCELLED` | §4.1 `state-machines.md` | Máquina de estados **completa**, com efeitos definidos |
| RN-007: tenant suspenso permite leitura e exportação, rejeita escrita | `business-rules.md` §4 | **Normativa e implementada** |
| RN-008: tenant cancelado rejeita acesso após 30 dias de retenção | `business-rules.md` §4 | Normativa e implementada |
| `TENANT_BILLING` | §6.1 `permissions.md` | Permissão **já** no catálogo, restrita a `OWNER` |
| Quota de 1 GB por tenant | RN-801 | Implementada com limite **fixo** |
| Exportação completa dos dados do tenant | §4.1 SM | Disponibilizada no cancelamento |
| `Contact` como base de identidade externa | §6.5 `entities.md` | Entidade completa; `receivesReports` persistido sem uso (OB-04 de `003`) |
| `CLIENT_PORTAL` como papel | §5 `permissions.md` | **Já previsto** no enum, marcado `(v2.x)` |
| Trilha de auditoria completa | §6.20 `entities.md` | Base para disputa de cobrança |

> **Consequência:** o produto já sabe se comportar como um SaaS pago — ele apenas não cobra. A suspensão por inadimplência, o efeito dela em cada feature e a retenção de dados no cancelamento estão implementados e testados.

## 4. O que não pode ser quebrado

**Esta é a única seção com efeito vinculante hoje.**

| # | Decisão a preservar | Consequência de quebrar |
|---|---|---|
| FR-01 | **RN-007: tenant suspenso permite leitura E exportação** | Bloquear a exportação de um tenant inadimplente é retê-lo como garantia — problema legal, não apenas de produto. `012-reports` implementa isso explicitamente (§12 daquela spec) |
| FR-02 | **RN-008: 30 dias de retenção antes da purga** | Purgar imediatamente no cancelamento elimina a possibilidade de reversão e viola o direito de acesso aos próprios dados |
| FR-03 | **`Tenant.planCode` persistido** | É o campo sobre o qual toda a lógica de plano se apoia; removê-lo por ser código morto custaria uma migration |
| FR-04 | **`TENANT_BILLING` restrita a `OWNER`** | `ADMIN` não gerencia cobrança (§7 `permissions.md`); afrouxar isso agora criaria expectativa a reverter depois |
| FR-05 | **Limites verificados **após** a permissão, com código próprio** (§14 `permissions.md`) | `DEVTIME-1300` está reservado para limite de plano. Misturá-lo com `DEVTIME-1101` tornaria impossível distinguir "sem permissão" de "plano insuficiente" |
| FR-06 | **`CLIENT_PORTAL` no enum de papéis, sem permissões atribuídas** | Remover o valor exigiria migration de enum; mantê-lo sem uso é gratuito |
| FR-07 | **`Contact.receivesReports` persistido** | É a base de identidade externa do portal (OB-04 de `003`) |
| FR-08 | **Quota como valor consultado, não constante espalhada** | `QuotaService` de `015` centraliza o limite; se `1 GB` estiver escrito em vários lugares, cada um vira uma alteração |
| FR-09 | **Máquina de estados de `Tenant` com 3 estados** | `SUSPENDED` e `CANCELLED` já têm efeitos cruzados definidos em §5 de `state-machines.md`; adicionar estados de cobrança (`TRIAL`, `PAST_DUE`) é aditivo, mas alterar os existentes quebraria os efeitos |
| FR-10 | **Nenhum dado financeiro do tenant é armazenado hoje** | O MVP não guarda cartão, CNPJ de faturamento nem histórico de pagamento. Introduzi-los antes de haver decisão sobre PCI-DSS e retenção seria assumir obrigação regulatória sem necessidade |
| FR-11 | **Exportação completa disponível independentemente do plano** | É o que torna o produto não aprisionador; condicioná-la ao plano é decisão que precisa ser tomada com consciência |

## 5. Dependências

| Feature | Tipo | O que consome |
|---|---|---|
| `016-teams` | **Bloqueante** | Limite de membros por plano só faz sentido com equipe |
| `002-users` | Bloqueante | `Tenant`, `planCode`, máquina de estados |
| `015-attachments` | Bloqueante | Quota de storage por plano |
| `003-clients` | Bloqueante | `Contact` como identidade do portal |
| `012-reports` | Bloqueante | Relatórios são o conteúdo principal do portal |
| Gateway de pagamento | **Externo** | Decisão de `integrations.md`, ainda não tomada |

## 6. Regras preliminares — **candidatas, não normativas**

### 6.1 Planos e limites (EP-18)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-9xx?` | Cada plano define limites de membros ativos, clientes, contratos e storage | §7.2 de `future.md` traz a estrutura preliminar |
| `RN-9xx?` | Exceder limite retorna `DEVTIME-1300`, **verificado após a permissão** | Código já reservado (§14 `permissions.md`) |
| `RN-9xx?` | Rebaixar plano com uso acima do novo limite exige redução prévia | Não desativa dado automaticamente |
| `RN-9xx?` | Período de teste com todos os recursos, sem cartão | Reduz atrito de entrada |
| `RN-9xx?` | Fim do teste sem pagamento leva o tenant a `SUSPENDED`, não a `CANCELLED` | Reusa RN-007; o dado permanece acessível em leitura |
| `RN-9xx?` | Falha de pagamento gera tentativas antes da suspensão | Análogo ao backoff de RN-610 |

### 6.2 Portal do cliente (EP-19)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-9xx?` | `Contact` com acesso ao portal recebe convite por e-mail e cria credencial própria | Reusa `001-authentication` |
| `RN-9xx?` | `CLIENT_PORTAL` acessa **somente leitura**, restrito aos contratos do próprio cliente | Papel já no enum |
| `RN-9xx?` | O portal exibe saldo, extrato e relatórios **fechados**; períodos abertos aparecem como parciais | RN-701 e RN-702 **já** garantem isso |
| `RN-9xx?` | Comentários internos **não** são visíveis no portal | Exige campo novo em `Comment` — ver OB-08 de `014` |
| `RN-9xx?` | Toda visualização pelo portal é auditada | Base para disputa contratual |

> Nenhum destes IDs existe. A faixa `RN-900`+ não está atribuída em `business-rules.md` §3.2 e precisará ser criada ao promover a feature.

## 7. Impacto nas features existentes

| Feature | O que muda | Aditivo? |
|---|---|---|
| `002-users` | Telas de plano e cobrança; estados de assinatura em `Tenant` | ⚠ — possíveis estados novos |
| `003-clients` | `Contact` ganha credencial e convite ao portal | ✔ |
| `011-bank-hours` | Saldo exposto ao cliente pelo portal | ✔ — somente leitura |
| `012-reports` | Relatórios acessíveis pelo portal, sem exportação de dados de outros clientes | ✔ |
| `014-comments` | **Precisa distinguir comentário interno de visível ao cliente** | ⚠ — campo novo; OB-08 de `014` |
| `015-attachments` | Quota passa a vir do plano; anexos possivelmente visíveis no portal | ⚠ — visibilidade exige decisão |
| Todas as features de escrita | Verificação de limite de plano após a permissão | ✔ — camada adicional |

> **Dois impactos exigem decisão de produto antes de código.** Em `014`, o default para comentários existentes precisa ser **interno** — a única opção segura. Em `015`, expor anexos ao cliente exige decidir se todo anexo é visível ou apenas os marcados, e o default seguro é **não visível**.

## 8. Riscos de antecipação

| # | Risco de construir antes do tempo |
|---|---|
| RA-01 | **Cobrança é irreversível em percepção.** Um erro de cobrança destrói confiança mais rápido que qualquer defeito funcional, e o produto ainda não tem base instalada para absorver isso |
| RA-02 | **Dados financeiros trazem obrigação regulatória.** Armazenar cartão implica PCI-DSS; armazenar dados de faturamento implica retenção fiscal. Assumir isso sem receita é custo puro |
| RA-03 | **O portal do cliente amplia a superfície de ataque para usuários externos.** Hoje todo acesso é de membro do tenant; o portal introduz identidade fora da organização |
| RA-04 | **Limites de plano definidos sem dados de uso serão errados.** Sem saber quantos clientes e contratos um tenant real tem, qualquer limite é arbitrário — e corrigi-lo depois significa rebaixar ou beneficiar quem já assinou |
| RA-05 | Comentários e anexos visíveis ao cliente sem decisão explícita de default vazariam conversa interna na primeira liberação |
| RA-06 | Período de teste e política de inadimplência definidos antes de conhecer o ciclo de decisão do comprador produzem atrito ou perda de receita |

## 9. Gatilho de priorização

| # | Sinal objetivo | Aciona |
|---|---|---|
| GP-01 | **Participantes do beta manifestarem disposição a pagar**, espontaneamente | EP-18 |
| GP-02 | Marco M3 do MVP concluído — checklist 100% verde (§7 de `implementation-order.md`) | Pré-condição |
| GP-03 | Ao menos um ciclo completo de fechamento de período validado em produção | Pré-condição |
| GP-04 | Cliente final de um tenant solicitar acesso direto aos relatórios | EP-19 |

> **GP-01 é o gatilho canônico e é qualitativo por natureza.** Não há métrica que o substitua: disposição a pagar se manifesta em conversa, não em telemetria. O produto tem uma decisão explícita de esperar por esse sinal em vez de presumi-lo.
>
> **GP-02 e GP-03 são pré-condições, não gatilhos.** Mesmo com disposição a pagar demonstrada, monetizar um produto cujo cálculo de saldo ainda não passou por um ciclo real de fechamento significaria cobrar por um número não validado.
