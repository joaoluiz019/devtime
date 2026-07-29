# Casos de Teste — DevTime

## 1. Objetivo

Catalogar os casos de teste do DevTime com identificador estável, pré-condições, passos, resultado esperado e rastreabilidade até a regra de negócio ou requisito de origem. É o inventário executável derivado de [`strategy.md`](strategy.md) e [`acceptance.md`](acceptance.md).

## 2. Escopo

| Dentro | Fora |
|---|---|
| Casos de teste `TC-XXXX` por área funcional | Estratégia e ferramentas (`strategy.md`) |
| Casos de borda, exceção e concorrência | Critérios de aceite de negócio (`acceptance.md`) |
| Matriz de rastreabilidade regra × caso | Implementação dos testes |
| Massa de dados de referência | Processo de release |

## 3. Definições

| Termo | Definição |
|---|---|
| **Caso de teste (TC)** | Verificação única e independente, identificada por `TC-XXXX`. |
| **Pré-condição** | Estado necessário antes da execução. |
| **Resultado esperado** | Comportamento observável que determina aprovação. |
| **Caso de borda** | Verificação em valor-limite. |
| **Caso negativo** | Verificação de rejeição esperada. |

### 3.1 Faixas de numeração

| Faixa | Área |
|---|---|
| `TC-0001`–`TC-0049` | Autenticação e conta |
| `TC-0050`–`TC-0099` | Clientes |
| `TC-0100`–`TC-0199` | Contratos e períodos |
| `TC-0200`–`TC-0299` | Banco de horas |
| `TC-0300`–`TC-0389` | Fechamento e reabertura |
| `TC-0390`–`TC-0399` | Categorias e tags |
| `TC-0400`–`TC-0499` | Registros de horas |
| `TC-0500`–`TC-0569` | Cronômetro |
| `TC-0570`–`TC-0599` | Dashboard |
| `TC-0600`–`TC-0699` | Relatórios e exportação |
| `TC-0700`–`TC-0749` | Notificações |
| `TC-0750`–`TC-0799` | Auditoria e permissões |
| `TC-0800`–`TC-0849` | Equipe e convites |
| `TC-0900`–`TC-0949` | Isolamento entre tenants |
| `TC-0950`–`TC-0999` | Não funcionais |

---

## 4. Massa de dados de referência

Todos os casos abaixo assumem a fixture padrão, salvo indicação contrária.

| Objeto | Identificação | Configuração |
|---|---|---|
| Tenant A | `TEN-A` | Fuso `America/Sao_Paulo`, moeda `BRL`, sem arredondamento |
| Tenant B | `TEN-B` | Usado apenas em testes de isolamento |
| Usuário OWNER | `USR-OWN` | `OWNER` em `TEN-A` |
| Usuário MEMBER | `USR-MEM` | `MEMBER` em `TEN-A` |
| Usuário MANAGER | `USR-MAN` | `MANAGER` em `TEN-A` |
| Usuário VIEWER | `USR-VIE` | `VIEWER` em `TEN-A` |
| Cliente | `CLI-1` | Acme Corporation, `ACTIVE` |
| Contrato A | `CTR-A` | 2400 min/mês, `billingDay` 1, `NONE`, `WARN` |
| Contrato B | `CTR-B` | 2400 min/mês, `billingDay` 1, `CAPPED` 300, `BLOCK` |
| Contrato C | `CTR-C` | 2400 min/mês, `billingDay` 15, `FULL`, `ALLOW_BILLABLE` |
| Contrato D | `CTR-D` | `HOURLY_OPEN` |
| Período aberto | `PER-OPEN` | 2026-07-01 a 2026-07-31, `OPEN` |
| Período fechado | `PER-CLOSED` | 2026-06-01 a 2026-06-30, `CLOSED` |
| Ticket | `TKT-1` | `CT-0001-42`, vinculado a `CTR-A` |
| Categoria faturável | `CAT-DEV` | Desenvolvimento, faturável |
| Categoria não faturável | `CAT-INT` | Interno, não faturável |

**Relógio fixo padrão:** `2026-07-28T14:00:00-03:00`.

---

## 5. Autenticação — `TC-0001` a `TC-0049`

| ID | Título | Pré-condição | Passos | Resultado esperado | Origem |
|---|---|---|---|---|---|
| TC-0001 | Cadastro cria tenant, membership e categorias | E-mail inexistente | `POST /auth/register` com dados válidos | `201`; usuário `PENDING_ACTIVATION`; tenant `ACTIVE`; membership `OWNER`; 9 categorias criadas | RF-001, RN-501 |
| TC-0002 | Cadastro com e-mail duplicado | Usuário existente | `POST /auth/register` com o mesmo e-mail | `409 DEVTIME-2452` | RN-452 |
| TC-0003 | Cadastro com senha fraca | — | Senha `12345678` | `422 DEVTIME-2451` com lista de requisitos | RN-451 |
| TC-0004 | Cadastro sem aceite dos termos | — | `acceptedTerms=false` | `422` | RF-001 |
| TC-0005 | E-mail normalizado para minúsculas | — | Cadastro com `RAFAEL@EX.COM` | Persistido como `rafael@ex.com` | AU-03 |
| TC-0006 | Verificação de e-mail ativa a conta | Conta `PENDING_ACTIVATION` | `POST /auth/verify-email` com token válido | `200`; status `ACTIVE`; tokens emitidos | RF-002 |
| TC-0007 | Verificação é idempotente | Token já usado | Repetir a chamada | `200` sem erro | §5.6 de `authentication.md` |
| TC-0008 | Verificação com token expirado | Token com mais de 7 dias | `POST /auth/verify-email` | `410 DEVTIME-1009` | §5.6 |
| TC-0010 | Login com um tenant | `USR-OWN` ativo | `POST /auth/login` | `200`; token com `tid` e `role`; cookie de refresh | RF-003 |
| TC-0011 | Login com múltiplos tenants | Usuário com 2 memberships | `POST /auth/login` | `200`; `tenantSelectionRequired=true`; token sem `tid` | RF-009 |
| TC-0012 | Endpoint de negócio sem tenant selecionado | Token de pré-seleção | `GET /clients` | `401 DEVTIME-1002` | ART-021 |
| TC-0013 | Login com senha incorreta | — | Senha errada | `401 DEVTIME-1001` | AU-01 |
| TC-0014 | Login com e-mail inexistente | — | E-mail inexistente | `401 DEVTIME-1001`, mensagem **idêntica** a TC-0013 | AU-01 |
| TC-0015 | Tempo de resposta indistinguível | — | Comparar TC-0013 e TC-0014 | Diferença inferior a 50ms | AU-02 |
| TC-0016 | Bloqueio após 5 falhas | — | 5 tentativas erradas em 15 min | 6ª retorna `423 DEVTIME-1006`; e-mail de alerta | RN-453 |
| TC-0017 | Contador zera em login bem-sucedido | 4 falhas registradas | Login correto | `failedLoginAttempts=0` | RN-453 |
| TC-0018 | Rotação de refresh token | Refresh válido | `POST /auth/refresh` | Novo access e novo refresh; anterior invalidado | RT-03 |
| TC-0019 | Reuso de refresh revoga a cadeia | Token já rotacionado | Reutilizar | `401 DEVTIME-1005`; todas as sessões revogadas; evento crítico | RN-005 |
| TC-0020 | Refresh de membership suspenso | Membership `SUSPENDED` | `POST /auth/refresh` | `403 DEVTIME-1102` | RN-459 |
| TC-0025 | Seleção de tenant | Token de pré-seleção | `POST /auth/select-tenant` | `200` com `tid` e `role` | RF-009 |
| TC-0026 | Seleção de tenant sem membership | — | Tenant de terceiro | `403 DEVTIME-1102` | CE-P-01 |
| TC-0030 | Alteração de senha revoga outras sessões | 3 sessões ativas | `POST /auth/change-password` | Sessão atual mantida; demais revogadas | RN-454 |
| TC-0031 | Redefinição sempre retorna sucesso | E-mail inexistente | `POST /auth/forgot-password` | `202` com mensagem neutra | PW-07 |
| TC-0032 | Token de redefinição é de uso único | Token já usado | Reutilizar | `410 DEVTIME-1007` | RN-461 |
| TC-0033 | Token de redefinição expira em 1h | Token com 61 min | Usar | `410 DEVTIME-1007` | RN-461 |
| TC-0040 | Alteração de papel invalida o token | `USR-MEM` autenticado | Alterar papel para `MANAGER` | Access token corrente rejeitado | TK-05, IMP-04 |
| TC-0041 | Token emitido antes da troca de senha é rejeitado | Token válido | Trocar senha e usar o token | `401` | TK-04 |
| TC-0042 | `/auth/me` retorna sessão, permissões e cronômetro | Cronômetro ativo | `GET /auth/me` | Resposta contém `activeTimer` preenchido | §5.10 |

---

## 6. Registros de horas — `TC-0400` a `TC-0499`

### 6.1 Cálculo de duração

| ID | Cenário | Entrada | `gross` | `paused` | `net` | Origem |
|---|---|---|---|---|---|---|
| TC-0400 | Sessão simples | 09:00:00 → 11:30:00 | 150 | 0 | 150 | RN-110 |
| TC-0401 | Segundos truncados | 09:00:00 → 11:30:59 | 150 | 0 | 150 | RN-010 |
| TC-0402 | Segundos no início | 09:00:59 → 11:30:00 | 149 | 0 | 149 | RN-010 |
| TC-0403 | Com pausa | 09:00 → 12:00, pausa 25 | 180 | 25 | 155 | RN-111 |
| TC-0404 | Duração mínima | 09:00 → 09:01 | 1 | 0 | 1 | CE-W-04 |
| TC-0405 | Limite exato de 24h | 08:00 d10 → 08:00 d11 | 1440 | 0 | 1440 | RN-103 |
| TC-0406 | Acima de 24h | 08:00 d10 → 08:01 d11 | 1441 | — | — | `422 DEVTIME-2103` |
| TC-0407 | Atravessa a meia-noite | 22:00 d10 → 01:30 d11 | 210 | 0 | 210 | RN-108 |
| TC-0408 | `workDate` em sessão noturna | TC-0407 | — | — | — | `workDate` = d10 |
| TC-0409 | Entrada por duração | 09:00 + `durationMinutes=90` | 90 | 0 | 90 | RF-111 |
| TC-0410 | `endedAt` e `duration` juntos | Ambos preenchidos | — | — | — | `400` |
| TC-0411 | Nenhum dos dois | Ambos ausentes | — | — | — | `400` |
| TC-0412 | Pausa consome toda a sessão | 09:00 → 10:00, pausa 60 | 60 | 60 | 0 | `422 DEVTIME-2115` |
| TC-0413 | Pausa maior que a sessão | 09:00 → 10:00, pausa 90 | 60 | 90 | — | `422 DEVTIME-2116` |
| TC-0414 | Pausa negativa | pausa = −10 | — | — | — | `400` |
| TC-0415 | Fim anterior ao início | 11:00 → 09:00 | — | — | — | `422 DEVTIME-2114` |
| TC-0416 | Fim igual ao início | 09:00 → 09:00 | 0 | — | — | `422 DEVTIME-2114` |
| TC-0417 | Arredondamento de 15 min | 112 min, config 15 | 112 | 0 | **105** | RN-113 |
| TC-0418 | Arredondamento de 6 min | 100 min, config 6 | 100 | 0 | **96** | RN-113 |
| TC-0419 | Arredondamento nunca sobe | 119 min, config 15 | 119 | 0 | **105** | PR-03 |
| TC-0420 | Arredondamento desativado | 112 min, config 0 | 112 | 0 | 112 | RN-113 |

### 6.2 Sobreposição — `TC-0425` a `TC-0439`

Base: registro existente de `USR-OWN` das 09:00 às 11:00.

| ID | Novo intervalo | Resultado | Justificativa |
|---|---|---|---|
| TC-0425 | 09:30 → 10:30 (contido) | `422 DEVTIME-2102` | RN-102 |
| TC-0426 | 10:00 → 12:00 (parcial à direita) | `422 DEVTIME-2102` | RN-102 |
| TC-0427 | 08:00 → 10:00 (parcial à esquerda) | `422 DEVTIME-2102` | RN-102 |
| TC-0428 | 08:00 → 12:00 (envolvente) | `422 DEVTIME-2102` | RN-102 |
| TC-0429 | 09:00 → 11:00 (idêntico) | `422 DEVTIME-2102` | RN-102 |
| TC-0430 | 11:00 → 12:00 (toca no fim) | `201` | RN-102 — semi-aberto |
| TC-0431 | 08:00 → 09:00 (toca no início) | `201` | RN-102 |
| TC-0432 | 12:00 → 13:00 (sem contato) | `201` | — |
| TC-0433 | Sobreposição de outro usuário | `201` | RN-102 é por usuário |
| TC-0434 | Sobreposição com registro excluído | `201` | RN-003 |
| TC-0435 | Edição criando sobreposição | `422 DEVTIME-2102` | RN-102 |
| TC-0436 | Edição do próprio registro sem mover | `200` | Exclusão do próprio ID da verificação |
| TC-0437 | Resposta de sobreposição traz o conflitante | `conflictingWorkLogs` preenchido | §5.4 de `worklogs.md` |
| TC-0438 | Resposta traz sugestão de horário | `suggestion.nextAvailableStart` preenchido | §5.4 |
| TC-0439 | Sobreposição entre tenants diferentes | `201` | Isolamento |

### 6.3 Vínculos e validações contextuais

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0440 | Registro sem ticket | `400` | RN-101 |
| TC-0441 | Ticket de outro tenant | `404 DEVTIME-2002` | ART-024 |
| TC-0442 | Contrato `ENDED` | `422 DEVTIME-2306` | RN-306 |
| TC-0443 | Contrato `CANCELLED` | `422 DEVTIME-2306` | RN-306 |
| TC-0444 | Contrato `SUSPENDED`, data na vigência | `201` | RN-306 |
| TC-0445 | Categoria inativa | `422 DEVTIME-2104` | RN-104 |
| TC-0446 | Descrição com 2 caracteres | `422 DEVTIME-2105` | RN-105 |
| TC-0447 | Descrição com 2001 caracteres | `422` | RN-105 |
| TC-0448 | Descrição apenas com espaços | `422 DEVTIME-2105` | RN-105 |
| TC-0449 | Data anterior ao início do contrato | `422 DEVTIME-2117` | RN-117 |
| TC-0450 | Data posterior ao fim do contrato | `422 DEVTIME-2117` | RN-117 |
| TC-0451 | `endedAt` no futuro (5 min) | `422 DEVTIME-2118` | RN-118 |
| TC-0452 | `endedAt` no futuro (1 min) | `201` | RN-118 — tolerância de 2 min |
| TC-0453 | `workDate` futura sem permissão da config | `422 DEVTIME-2119` | RN-119 |
| TC-0454 | Retroativo em 30 dias | `201` | RN-120 |
| TC-0455 | Retroativo em 31 dias como `MEMBER` | `422 DEVTIME-2120` | RN-120 |
| TC-0456 | Retroativo em 31 dias como `OWNER` | `201` | RN-120 |
| TC-0457 | Data sem período correspondente | `422 DEVTIME-2107` | RN-107 |
| TC-0458 | Registro em nome de outro como `MEMBER` | `403 DEVTIME-1101` | RN-106 |
| TC-0459 | Registro em nome de outro como `MANAGER` | `201` | RN-106 |
| TC-0460 | 11 tags | `422 DEVTIME-2313` | INV-TAG-01 |

### 6.4 Faturabilidade e política de excedente

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0465 | Não faturável não consome saldo | `consumedMinutes` inalterado; `nonBillableMinutes` incrementado | RN-223 |
| TC-0466 | `billable` herda da categoria | Categoria não faturável ⇒ `billable=false` | RN-110 |
| TC-0467 | `billable` explícito prevalece | Categoria não faturável + `billable=true` ⇒ `true` | RF-115 |
| TC-0468 | Política `BLOCK` impede estouro | `CTR-B`, 60 min disponíveis, registro de 90 | `422 DEVTIME-2220` | RN-231 |
| TC-0469 | `BLOCK` permite consumo exato | 60 disponíveis, registro de 60 | `201`; saldo zero | RN-231 |
| TC-0470 | `BLOCK` permite não faturável acima do saldo | 60 disponíveis, 90 não faturáveis | `201` | RN-223 |
| TC-0471 | Política `WARN` permite com aviso | `CTR-A`, estouro | `201` + `warnings` com `DEVTIME-2221` | RN-232 |
| TC-0472 | `ALLOW_BILLABLE` marca excedente | `CTR-C`, estouro | `201`; excedente cobrado à `overageRate` | RN-233 |
| TC-0473 | `BLOCK` não divide o registro | 60 disponíveis, 90 solicitados | Rejeição integral, sem criação parcial | RN-234 |

### 6.5 Edição e exclusão

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0480 | Edição incrementa `editCount` | `editCount` = 1 | RN-123 |
| TC-0481 | Edição recalcula somatórios | `ticket.spentMinutes` e `period.consumedMinutes` atualizados | RN-123 |
| TC-0482 | Edição de registro travado | `409 DEVTIME-2121` | RN-121 |
| TC-0483 | Edição por outro usuário como `MEMBER` | `403 DEVTIME-1103` | RN-122 |
| TC-0484 | Edição por `MANAGER` | `200` | RN-122 |
| TC-0485 | Edição por `VIEWER` | `403 DEVTIME-1101` | Matriz de permissões |
| TC-0486 | Mover para período fechado | `409 DEVTIME-2124` | RN-124 |
| TC-0487 | Mover entre períodos abertos | `200`; resposta traz os dois saldos | RN-124 |
| TC-0488 | Alterar `source` | `422 DEVTIME-2003` | RN-126 |
| TC-0489 | Alterar `userId` | `422 DEVTIME-2003` | RN-126 |
| TC-0490 | Exclusão devolve o saldo | `consumedMinutes` reduzido | RN-125 |
| TC-0491 | Exclusão é lógica | Registro permanece com `deletedAt` | RN-003 |
| TC-0492 | Exclusão de registro travado | `409 DEVTIME-2121` | RN-121 |
| TC-0493 | Conflito de versão | `409 DEVTIME-2004` | RN-004 |

---

## 7. Cronômetro — `TC-0500` a `TC-0569`

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0500 | Iniciar cronômetro | `201`; `RUNNING`; `startedAt = now()` | RN-152 |
| TC-0501 | Segundo cronômetro rejeitado | `409 DEVTIME-2150` | RN-150 |
| TC-0502 | Concorrência de início | Duas requisições simultâneas ⇒ apenas uma sucede; constraint do banco garante | INV-TMR-01 |
| TC-0503 | Limite é por usuário, não por tenant | Usuário em 2 tenants ⇒ `409` no segundo | CE-13 |
| TC-0504 | Iniciar em contrato encerrado | `422 DEVTIME-2306` | RN-306 |
| TC-0505 | Pausar cronômetro em execução | `200`; `PAUSED`; pausa aberta criada | RN-154 |
| TC-0506 | Pausar cronômetro já pausado | `409 DEVTIME-2153` | RN-153 |
| TC-0507 | Retomar cronômetro pausado | `200`; `RUNNING`; pausa fechada | RN-156 |
| TC-0508 | Retomar cronômetro em execução | `409 DEVTIME-2155` | RN-155 |
| TC-0509 | Múltiplas pausas somam corretamente | 3 pausas de 10 min ⇒ `pausedMinutes = 30` | RN-157 |
| TC-0510 | Apenas uma pausa aberta por vez | Constraint do banco impede a segunda | INV-TMR-02 |
| TC-0511 | Cálculo completo com pausa | 09:00 → 12:15:40, pausa 10:30–11:00 ⇒ `gross=195`, `paused=30`, `net=165` | RN-159 |
| TC-0512 | Encerrar sem descrição | `422 DEVTIME-2105`; cronômetro inalterado | RN-158 |
| TC-0513 | Encerrar com sobreposição | `422 DEVTIME-2102`; `timerPreserved=true`; cronômetro ativo | RN-160 |
| TC-0514 | Encerrar acima de 24h | `422 DEVTIME-2103`; cronômetro preservado | RN-160 |
| TC-0515 | Encerrar com `adjustedEndedAt` | Registro criado com o horário informado | §8.4 de `worklogs.md` |
| TC-0516 | `adjustedEndedAt` no futuro | `422 DEVTIME-2118` | RN-118 |
| TC-0517 | `adjustedEndedAt` antes do início | `422 DEVTIME-2114` | RN-114 |
| TC-0518 | Registro gerado tem `source=TIMER` | `source=TIMER`; `timerId` preenchido | INV-WKL-09 |
| TC-0519 | Troca atômica de tarefa | Registro do ticket A criado + cronômetro no ticket B, em uma transação | RN-166 |
| TC-0520 | Troca atômica sem descrição | `422 DEVTIME-2105`; nenhum efeito | RN-166 |
| TC-0521 | Falha na troca não deixa estado parcial | Falha simulada ⇒ cronômetro original intacto, sem registro criado | ME-01 |
| TC-0522 | Descartar exige confirmação | Sem `confirm=true` ⇒ `400 DEVTIME-2151` | RN-162 |
| TC-0523 | Descarte não gera registro | `DISCARDED`; `workLogId` nulo | INV-TMR-05 |
| TC-0524 | Descarte é auditado com o tempo perdido | `AuditLog` registra os minutos descartados | RN-162 |
| TC-0525 | Alterar ticket em execução | `200`; ticket atualizado | RN-161 |
| TC-0526 | Alerta de 8 horas | Job gera `TIMER_LONG_RUNNING` uma única vez | RN-163 |
| TC-0527 | Alerta não se repete | Segunda execução do job não gera nova notificação | RN-163 |
| TC-0528 | Abandono em 16 horas | `ABANDONED`; nenhum registro gerado | RN-164 |
| TC-0529 | Recuperar abandonado em 7 dias | `200`; registro criado com `endedAt` informado | RN-165 |
| TC-0530 | Recuperar abandonado após 7 dias | `409 DEVTIME-2165` | RN-165 |
| TC-0531 | Recuperar em período fechado | `409 DEVTIME-2121` | CE-ME-04 |
| TC-0532 | Persistência após reinício | Cronômetro ativo com tempo correto | RN-167 |
| TC-0533 | `elapsedSeconds` reflete o tempo real | Após 1h em execução ⇒ ≈ 3600 | §8.1 |
| TC-0534 | `elapsedSeconds` congela quando pausado | Valor estável durante a pausa | §8.1 |
| TC-0535 | Encerramento de terceiro por `ADMIN` | `200`; dono notificado | §8.7 |
| TC-0536 | Encerramento de terceiro por `MANAGER` | `403 DEVTIME-1101` | Matriz de permissões |

---

## 8. Contratos, períodos e banco de horas

### 8.1 Geração de períodos — `TC-0100` a `TC-0149`

| ID | `startDate` | `billingDay` | Período 1 esperado | `contractedMinutes` P1 | Origem |
|---|---|:--:|---|---|---|
| TC-0100 | 2026-01-01 | 1 | 01/01–31/01 | 2400 | RN-211 |
| TC-0101 | 2026-01-10 | 1 | 10/01–31/01 | **1703** | RN-217 |
| TC-0102 | 2026-01-15 | 15 | 15/01–14/02 | 2400 | RN-211 |
| TC-0103 | 2026-01-20 | 5 | 20/01–04/02 | **1239** | RN-217 |
| TC-0104 | 2026-02-28 | 28 | 28/02–27/03 | 2400 | RN-203 |
| TC-0105 | 2028-02-29 | 28 | Erro na criação | — | RN-203 (dia > 28 impossível; 29 rejeitado) |
| TC-0106 | Rateio desabilitado | 1 | 10/01–31/01 | 2400 | RN-217 |
| TC-0110 | Períodos são contíguos | — | P[n].start = P[n−1].end + 1 dia | — | INV-PER-03 |
| TC-0111 | Períodos não se sobrepõem | — | Constraint `EXCLUDE` rejeita | — | INV-PER-02 |
| TC-0112 | Apenas um período `OPEN` | — | Índice único parcial rejeita o segundo | — | INV-PER-07 |
| TC-0113 | Ativação gera o 1º período | Contrato `DRAFT` → `ACTIVE` | Período `OPEN` criado | — | RN-209 |
| TC-0114 | Job gera o próximo período | 3 dias do fim | Período `SCHEDULED` criado | — | RN-213 |
| TC-0115 | `SCHEDULED` vira `OPEN` na data | Job diário | Status alterado | — | §4.6 de `state-machines.md` |
| TC-0116 | `endDate` trunca o período | Contrato encerrado em 15/07 | Período termina em 15/07 | — | RN-214 |
| TC-0117 | Contrato encerrado não gera novo período | — | Nenhum período posterior | — | RN-214 |
| TC-0118 | Retomada preenche lacunas | Suspenso 2 ciclos | Períodos faltantes gerados | — | CE-ME-09 |

### 8.2 Validações de contrato

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0120 | `billingDay` 0 | `422 DEVTIME-2203` | RN-203 |
| TC-0121 | `billingDay` 29 | `422 DEVTIME-2203` | RN-203 |
| TC-0122 | `billingDay` 31 | `422 DEVTIME-2203` | RN-203 |
| TC-0123 | `monthlyMinutes` 0 | `422 DEVTIME-2202` | RN-202 |
| TC-0124 | `monthlyMinutes` 44641 | `422 DEVTIME-2202` | RN-202 |
| TC-0125 | `MONTHLY_HOURS` sem `monthlyMinutes` | `422` | INV-CTR-02 |
| TC-0126 | `HOURLY_OPEN` com `monthlyMinutes` | `422 DEVTIME-2210` | INV-CTR-03 |
| TC-0127 | `CAPPED` sem teto | `422 DEVTIME-2209` | INV-CTR-04 |
| TC-0128 | `endDate` antes de `startDate` | `422 DEVTIME-2204` | RN-204 |
| TC-0129 | Cliente inativo | `422 DEVTIME-2201` | RN-201 |
| TC-0130 | Código duplicado | `409 DEVTIME-2206` | INV-CTR-01 |
| TC-0131 | Alterar `type` após ativação | `422 DEVTIME-2003` | RN-206 |
| TC-0132 | Alterar `billingDay` com horas lançadas | `409 DEVTIME-2208` | RN-208 |
| TC-0133 | Excluir contrato com registros | `409 DEVTIME-2205` | RN-205 |
| TC-0134 | Excluir contrato `DRAFT` | `204` | RN-205 |
| TC-0135 | Suspender com cronômetro ativo | `409 DEVTIME-2212` | §8.2 de `contracts.md` |
| TC-0136 | Transição `ENDED → ACTIVE` | `409 DEVTIME-2010` | CE-15 |
| TC-0137 | Transição `CANCELLED → *` | `409 DEVTIME-2010` | §4.5 de `state-machines.md` |
| TC-0138 | Prévia coincide com o gerado | Comparar prévia e períodos após ativação | Idênticos | CA-01 de `contracts.md` |

### 8.3 Banco de horas — `TC-0200` a `TC-0299`

| ID | `contratado` | `carriedIn` | `ajuste` | `consumido` | `available` | `remaining` | `overage` | `rate` |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| TC-0200 | 2400 | 0 | 0 | 0 | 2400 | 2400 | 0 | 0% |
| TC-0201 | 2400 | 0 | 0 | 1200 | 2400 | 1200 | 0 | 50% |
| TC-0202 | 2400 | 300 | 60 | 2900 | 2760 | −140 | 140 | 105,07% |
| TC-0203 | 2400 | 0 | 0 | 2400 | 2400 | 0 | 0 | 100% |
| TC-0204 | 2400 | 0 | −400 | 1000 | 2000 | 1000 | 0 | 50% |
| TC-0205 | 0 (`HOURLY_OPEN`) | 0 | 0 | 5000 | 0 | −5000 | 0 | 0% |

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0210 | Determinismo | 10 recálculos com 500 registros ⇒ resultados idênticos | PR-06 |
| TC-0211 | Não faturáveis fora do consumo | `nonBillableMinutes` separado | RN-223 |
| TC-0212 | Registros excluídos fora do consumo | Não somados | RN-003 |
| TC-0213 | Extrato tem todas as linhas | 7 tipos de linha presentes, inclusive zeradas | EX-02 |
| TC-0214 | `drillDown` reproduz o número | URL retorna exatamente `consumedMinutes` | EX-04 |
| TC-0215 | `MEMBER` não vê `byUser` | Campo omitido | EX-05 |
| TC-0216 | Sem permissão financeira, `financial` omitido | Campo ausente | SM-01 |
| TC-0217 | Contrato sem `hourlyRate` | `financial` ausente sem erro | CE-09 |
| TC-0218 | Projeção calculada | `burnRate × diasÚteisTotais` | T-44 |
| TC-0219 | Projeção ausente em período fechado | Campo omitido | §9.1 de `contracts.md` |

### 8.4 Carry-over — `TC-0220` a `TC-0239`

| ID | Política | Teto | `available` | `consumido` | `carriedOut` | Origem |
|---|---|---:|---:|---:|---:|---|
| TC-0220 | `NONE` | — | 2400 | 1800 | 0 | RN-225 |
| TC-0221 | `FULL` | — | 2400 | 1800 | 600 | RN-226 |
| TC-0222 | `CAPPED` | 300 | 2400 | 1800 | 300 | RN-227 |
| TC-0223 | `CAPPED` | 300 | 2400 | 2250 | 150 | RN-227 |
| TC-0224 | `CAPPED` | 300 | 2400 | 2400 | 0 | RN-227 |
| TC-0225 | `FULL` | — | 2400 | 2900 | 0 | RN-228 |
| TC-0226 | `NONE` | — | 2400 | 2400 | 0 | RN-225 |
| TC-0227 | Propagação ao próximo período | — | — | — | `carriedIn` do seguinte = `carriedOut` | RN-229 |
| TC-0228 | Expiração de saldo transportado | `rolloverExpiryPeriods=1` | — | — | Ajuste automático de débito criado | RN-230 |
| TC-0229 | `rolloverExpiryPeriods=0` | — | — | — | Saldo nunca expira | RN-230 |

### 8.5 Ajustes

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0250 | Ajuste positivo | `availableMinutes` incrementado | RN-218 |
| TC-0251 | Ajuste negativo | `availableMinutes` reduzido | RN-218 |
| TC-0252 | Ajuste zero | `422` | RN-215 |
| TC-0253 | Justificativa com 9 caracteres | `422 DEVTIME-2215` | RN-215 |
| TC-0254 | Ajuste em período fechado | `409 DEVTIME-2235` | RN-235 |
| TC-0255 | Ajuste que torna `available` negativo | `422 DEVTIME-2237` | RN-237 |
| TC-0256 | Ajuste por `MANAGER` | `403 DEVTIME-1101` | RN-238 |
| TC-0257 | Ajuste é imutável | Nenhum endpoint `PUT`/`DELETE` existe | RN-236 |
| TC-0258 | Estorno por ajuste contrário | Novo ajuste de sinal oposto criado | RN-236 |

### 8.6 Fechamento e reabertura — `TC-0300` a `TC-0389`

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0300 | Fechamento bem-sucedido | `CLOSED`; registros travados; snapshot criado | RN-241 |
| TC-0301 | Registros recebem `lockedAt` | Todos os registros do período | RN-241 |
| TC-0302 | Snapshot com checksum SHA-256 | `checksum` de 64 caracteres | RN-241 |
| TC-0303 | Reconciliação detecta divergência | `consumedMinutes` corrigido antes do snapshot | RN-241 |
| TC-0304 | Falha no passo 4 faz rollback | Período `OPEN`; nenhum registro travado; sem snapshot | ME-01 |
| TC-0305 | Cronômetro `RUNNING` bloqueia | `409 DEVTIME-2240` com lista de cronômetros | RN-240 |
| TC-0306 | Cronômetro `PAUSED` também bloqueia | `409 DEVTIME-2240` | CE-ME-01 |
| TC-0307 | Fechamento antecipado sem confirmação | `409 DEVTIME-2239` | RN-239 |
| TC-0308 | Fechamento antecipado com confirmação | `200` | RN-239 |
| TC-0309 | Fechamento por `MANAGER` | `403 DEVTIME-1101` | Matriz de permissões |
| TC-0310 | Fechamento concorrente | Segundo recebe `409 DEVTIME-2241` | CE-A-02 |
| TC-0311 | Fechamento é idempotente | Mesma `Idempotency-Key` ⇒ resposta original | ART-074 |
| TC-0312 | Notificação `PERIOD_CLOSED` gerada | Uma por destinatário | RN-241 |
| TC-0320 | Reabertura limpa `lockedAt` | Registros editáveis | RN-243 |
| TC-0321 | Reabertura preserva o snapshot | Snapshot anterior intacto | RN-243 |
| TC-0322 | Reabertura sem justificativa | `422 DEVTIME-2215` | RN-242 |
| TC-0323 | Reabertura com período posterior fechado | `409 DEVTIME-2244` | RN-244 |
| TC-0324 | Reabertura em cascata correta | Do mais recente ao mais antigo | RN-244 |
| TC-0325 | `reopenCount` incrementado | Valor = 1 | RN-243 |
| TC-0326 | Refechamento gera novo snapshot | Dois snapshots existentes | INV-SNP-01 |
| TC-0327 | Período preso em `CLOSING` é revertido | Job reverte após 10 min | CE-ME-07 |

---

## 9. Relatórios — `TC-0600` a `TC-0699`

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0600 | Período fechado vem do snapshot | `source="SNAPSHOT"` | RN-701 |
| TC-0601 | Alteração posterior não afeta o relatório | Nome antigo do cliente exibido | RN-701 |
| TC-0602 | Período aberto é parcial | `isPartial=true` | RN-702 |
| TC-0603 | PDF determinístico | Duas gerações idênticas exceto emissão | RN-708 |
| TC-0604 | Registro excluído não aparece | Ausente e não somado | RN-704 |
| TC-0605 | Intervalo de 367 dias | `400 DEVTIME-3001` | RN-705 |
| TC-0606 | Intervalo de 366 dias | `200` | RN-705 |
| TC-0607 | Acima de 5.000 linhas é assíncrono | `202` com `pollUrl` | RN-706 |
| TC-0608 | Exportação registrada | `ReportExecution` com filtros e solicitante | RN-707 |
| TC-0609 | URL expira em 15 min | Acesso após 16 min negado | RN-712 |
| TC-0610 | `MEMBER` sem filtro de usuário | Apenas os próprios registros | RN-711 |
| TC-0611 | `MEMBER` filtrando outro usuário | `403 DEVTIME-1101` | CE-P-10 |
| TC-0612 | XLSX com coluna decimal numérica | Tipo numérico; soma confere | RN-710 |
| TC-0613 | XLSX com `SUBTOTAL` | Fórmula responde a filtros | XLS-03 |
| TC-0614 | Relatório sem registros | Gerado com totais zerados | CE-R-06 |
| TC-0615 | Contrato sem valor hora | Colunas monetárias omitidas | CE-09 |
| TC-0616 | Moedas diferentes no cliente | Totais separados por moeda | CE-C-07 |
| TC-0617 | Nenhum UUID no PDF | Inspeção do conteúdo | PDF-04 |
| TC-0618 | Marca d'água PARCIAL | Presente em todas as páginas | PDF-06 |
| TC-0619 | Idempotência de exportação | Mesma chave ⇒ mesma exportação | CE-R-12 |
| TC-0620 | Todos os agrupamentos funcionam | 7 valores de `groupBy` verificados | §5.1 de `reports.md` |

---

## 10. Notificações — `TC-0700` a `TC-0749`

| ID | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0700 | Cruzar 50% gera notificação | Uma para `OWNER` e `ADMIN` | RN-602 |
| TC-0701 | Cruzar 50% e 80% no mesmo registro | Duas notificações | CE-N-01 |
| TC-0702 | Segunda passagem pelo mesmo limiar | Nenhuma nova notificação | RN-603 |
| TC-0703 | Consumo cai e volta a subir | Nenhuma nova notificação | CE-N-02 |
| TC-0704 | Excedente gera `CONTRACT_OVERAGE` | Severidade `CRITICAL` | RN-604 |
| TC-0705 | `HOURLY_OPEN` não gera alerta | Nenhuma notificação | CE-N-08 |
| TC-0706 | Autor não é notificado da própria ação | Nenhuma notificação para ele | NT-05 |
| TC-0707 | Menção a si mesmo | Nenhuma notificação | CE-N-06 |
| TC-0708 | Tipo silenciado ainda gera in-app | Notificação criada; e-mail não enviado | NT-01 |
| TC-0709 | Tipo crítico não pode ser silenciado | `422 DEVTIME-4001` | §9.1 de `notifications.md` |
| TC-0710 | Falha no e-mail preserva in-app | Notificação existe; falha registrada | RN-610 |
| TC-0711 | `dedupeKey` único por destinatário | Constraint do banco rejeita duplicata | INV-NOT-01 |
| TC-0712 | Marcar como lida é idempotente | `readAt` inalterado na segunda chamada | §8.1 |
| TC-0713 | Notificação de outro usuário | `404` | §12 de `notifications.md` |
| TC-0714 | `quietHours` não bloqueia crítico | E-mail enviado | CE-N-12 |
| TC-0715 | Limpeza após 90 dias | Notificações lidas antigas removidas | RN-609 |

---

## 11. Isolamento entre tenants — `TC-0900` a `TC-0949`

Executados para **cada** recurso: cliente, contrato, período, ticket, registro, categoria, tag, comentário, anexo, notificação, ajuste, exportação.

| ID (base) | Cenário | Resultado | Origem |
|---|---|---|---|
| TC-0900+n | `GET` recurso de outro tenant | `404 DEVTIME-2002` | ART-024 |
| TC-0910+n | `PUT`/`PATCH` recurso de outro tenant | `404`; recurso inalterado | ART-024 |
| TC-0920+n | `DELETE` recurso de outro tenant | `404`; recurso inalterado | ART-024 |
| TC-0930+n | Listagem não inclui outro tenant | Ausente; total não conta | ART-022 |
| TC-0940+n | Referência cruzada na criação | `404` | §6.3 de `security.md` |
| TC-0945 | `tenantId` no corpo é ignorado | Recurso criado no tenant do token | ART-021 |
| TC-0946 | Header `X-Tenant-Id` divergente é ignorado | Idem | ART-021 |
| TC-0947 | Tempo de resposta indistinguível | Diferença inferior a 50ms entre "inexistente" e "de outro tenant" | TI-05 |
| TC-0948 | Sobreposição não cruza tenants | Registro criado normalmente | TC-0439 |
| TC-0949 | `TenantContext` vazio lança exceção | `500` + alerta; nunca consulta sem filtro | TI-06 |

---

## 12. Permissões — `TC-0750` a `TC-0799`

Um caso por célula da matriz de `permissions.md` §7. Exemplos representativos:

| ID | Papel | Ação | Resultado | Origem |
|---|---|---|---|---|
| TC-0750 | `VIEWER` | Criar registro de horas | `403 DEVTIME-1101` | Matriz |
| TC-0751 | `VIEWER` | Exportar relatório | `200` | Matriz |
| TC-0752 | `MEMBER` | Ver registro de colega | `404` | CE-P-04 |
| TC-0753 | `MEMBER` | Ver contrato sem vínculo | `404` | CE-P-05 |
| TC-0754 | `MEMBER` | Ver contrato vinculado | `200` | Nota ² |
| TC-0755 | `MEMBER` | Ver valores monetários | Campo omitido | Matriz |
| TC-0756 | `MANAGER` | Fechar período | `403 DEVTIME-1101` | Matriz |
| TC-0757 | `MANAGER` | Editar registro de terceiro | `200` | Matriz |
| TC-0758 | `MANAGER` | Encerrar contrato | `403` | Nota ³ |
| TC-0759 | `ADMIN` | Rebaixar `OWNER` | `403 DEVTIME-1104` | Nota ¹ |
| TC-0760 | `ADMIN` | Cancelar tenant | `403 DEVTIME-1101` | Matriz |
| TC-0761 | `OWNER` | Alterar o próprio papel | `403 DEVTIME-2456` | RN-456 |
| TC-0762 | Último `OWNER` | Auto-remoção | `409 DEVTIME-2455` | RN-455 |
| TC-0763 | Ownership não sobrepõe estado | Autor edita registro travado ⇒ `409` | OWN-02 |
| TC-0764 | `availableActions` reflete papel | Ações filtradas corretamente | ME-06 |

---

## 13. Casos não funcionais — `TC-0950` a `TC-0999`

| ID | Cenário | Meta | Origem |
|---|---|---|---|
| TC-0950 | Listagem com 100k registros | p95 < 300ms | RNF-001 |
| TC-0951 | Dashboard com 100k registros | p95 < 800ms | RNF-003 |
| TC-0952 | Cálculo de saldo com 5k registros | p95 < 100ms | PF-03 |
| TC-0953 | Validação de sobreposição com 100k | p95 < 50ms | PF-04 |
| TC-0954 | PDF de 1.000 linhas | < 5s | RNF-004 |
| TC-0955 | XLSX de 5.000 linhas | < 15s | RNF-005 |
| TC-0956 | 1.000 usuários concorrentes | Metas mantidas | RNF-012 |
| TC-0960 | Bundle inicial | < 500 KB gzip | RNF-007 |
| TC-0961 | FCP | < 1,5s | RNF-006 |
| TC-0962 | Zero violações axe-core | Todas as telas, dois temas | RNF-042 |
| TC-0970 | E-mail indisponível | Notificação in-app criada | CE-I-01 |
| TC-0971 | Storage indisponível | Registro de horas funciona | CE-I-02 |
| TC-0972 | Antivírus indisponível | Anexo `PENDING`; download bloqueado | CE-I-03 |
| TC-0973 | Reinício com 50 cronômetros | 100% recuperados | AQ-04 |
| TC-0974 | Job em 10 instâncias | Executa exatamente uma vez | AQ-07 |
| TC-0980 | Arquivo EICAR | Detectado e bloqueado | CA-10 de `security.md` |
| TC-0981 | Dependência com CVE HIGH | Build falha | ART-103 |
| TC-0982 | Segredo versionado | Build falha | ART-083 |
| TC-0990 | Senha em log | Nenhuma ocorrência | ART-084 |
| TC-0991 | Token em log | Nenhuma ocorrência | ART-084 |
| TC-0992 | CPF completo em log | Nenhuma ocorrência | §9.2 de `security.md` |

---

## 14. Casos temporais obrigatórios

| ID | Cenário | Verificação |
|---|---|---|
| TC-0470T | Registro às 23:59 | `workDate` do dia corrente |
| TC-0471T | Registro às 00:00 | `workDate` do novo dia |
| TC-0472T | Sessão 23:30 → 00:30 | `workDate` do dia de início |
| TC-0473T | Virada de mês em sessão noturna | Período do dia de início |
| TC-0474T | Início do horário de verão | Duração real preservada |
| TC-0475T | Fim do horário de verão (hora repetida) | Duração real preservada |
| TC-0476T | Fevereiro com 28 dias | Períodos contíguos |
| TC-0477T | Fevereiro com 29 dias (bissexto) | Rateio correto |
| TC-0478T | `billingDay` 28 em todos os meses | 12 períodos contíguos |
| TC-0479T | Tenant em fuso diferente | `workDate` no fuso do tenant |

---

## 15. Matriz de rastreabilidade regra × caso

| Regra | Casos de teste |
|---|---|
| RN-101 | TC-0440, TC-0441 |
| RN-102 | TC-0425 a TC-0439 |
| RN-103 | TC-0405, TC-0406, TC-0514 |
| RN-104 | TC-0445 |
| RN-105 | TC-0446 a TC-0448, TC-0512 |
| RN-107 | TC-0457 |
| RN-108 | TC-0407, TC-0408, TC-0472T |
| RN-110–113 | TC-0400 a TC-0420 |
| RN-114–116 | TC-0412 a TC-0416 |
| RN-117–120 | TC-0449 a TC-0456 |
| RN-121–126 | TC-0480 a TC-0493 |
| RN-150–167 | TC-0500 a TC-0536 |
| RN-201–217 | TC-0100 a TC-0138 |
| RN-218–223 | TC-0200 a TC-0219 |
| RN-224–230 | TC-0220 a TC-0229 |
| RN-231–234 | TC-0468 a TC-0473 |
| RN-235–238 | TC-0250 a TC-0258 |
| RN-239–245 | TC-0300 a TC-0327 |
| RN-301–314 | TC-0442 a TC-0444, TC-0460 |
| RN-401–407 | TC-0050 a TC-0099 |
| RN-451–461 | TC-0001 a TC-0042 |
| RN-501–508 | TC-0390 a TC-0399 |
| RN-601–610 | TC-0700 a TC-0715 |
| RN-701–712 | TC-0600 a TC-0620 |
| RN-801–815 | TC-0980, casos de anexo em `tickets.md` |
| ART-021–024 | TC-0900 a TC-0949 |

**Regra de verificação automática:** um script do pipeline extrai as regras referenciadas nos `@DisplayName` dos testes e compara com o catálogo de `business-rules.md`. Regra sem caso correspondente bloqueia o build (G-04 de `strategy.md`).

---

## 16. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-TC-01 | Caso de teste que depende de duas regras | Referencia ambas no `@DisplayName` e aparece nas duas linhas da matriz |
| CE-TC-02 | Caso que exige volume alto | Movido para a suíte de carga; não roda no PR |
| CE-TC-03 | Caso de concorrência real | Implementado com múltiplas threads e verificação da constraint do banco |
| CE-TC-04 | Caso impossível de automatizar | Migra para checklist manual em `acceptance.md`, com referência cruzada |
| CE-TC-05 | Regra alterada | Os casos vinculados são revisados **antes** da alteração do código |
| CE-TC-06 | Caso duplicado descoberto | O de menor ID prevalece; o outro é removido e a matriz atualizada |

## 17. Casos de erro do processo

| Situação | Consequência |
|---|---|
| Regra sem caso de teste | Build bloqueado (G-04) |
| Caso de teste sem regra ou requisito de origem | Rejeitado na revisão |
| Caso removido sem justificativa | Rejeitado na revisão |
| Identificador `TC-XXXX` reutilizado | Rejeitado — identificadores são permanentes |
| Caso alterado mudando o que verifica | Deve receber novo identificador |

## 18. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Todo caso possui identificador estável, pré-condição, passos e resultado esperado |
| CA-02 | Todo caso rastreia até uma regra `RN-XXX`, requisito `RF-XXX` ou artigo `ART-XXX` |
| CA-03 | Toda regra de negócio aparece na matriz da §15 |
| CA-04 | Todo exemplo numérico de `business-rules.md` possui caso correspondente |
| CA-05 | Todo caso especial `CE-XX` da documentação possui caso de teste |
| CA-06 | Nenhum identificador é reutilizado |

## 19. Dependências e impactos

| Documento | Relação |
|---|---|
| `strategy.md` | Define como estes casos são executados |
| `acceptance.md` | Define os critérios que estes casos provam |
| `02-domain/business-rules.md` | Fonte das regras verificadas |
| `01-product/requirements.md` | Fonte dos requisitos verificados |
| `04-api/*` | Define os contratos exercitados |

**Impacto:** alterar uma regra de negócio obriga a revisão de todos os casos vinculados a ela na matriz da §15, **antes** de qualquer alteração de código.
