# Requisitos Detalhados — DevTime

## 1. Objetivo

Formalizar cada requisito do DevTime em formato verificável: enunciado, prioridade, entradas, saídas, validações, critérios de aceite em Gherkin, dependências e rastreabilidade. Este documento é a ponte entre o [`prd.md`](prd.md) (o quê) e a implementação (como), e serve de base direta para [`06-testing/test-cases.md`](../06-testing/test-cases.md).

## 2. Escopo

| Dentro | Fora |
|---|---|
| Detalhamento de todos os `RF-XXX` do MVP | Priorização e sequenciamento (`07-backlog/`) |
| Critérios de aceite em Gherkin | Design de telas (`05-ui/`) |
| Matriz de rastreabilidade requisito × regra × teste | Contratos HTTP (`04-api/`) |
| Requisitos não funcionais mensuráveis | Regras de negócio (`02-domain/business-rules.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Requisito funcional (RF)** | Comportamento observável que o sistema deve apresentar. |
| **Requisito não funcional (RNF)** | Atributo de qualidade mensurável. |
| **Gherkin** | Formato `Dado / Quando / Então` para critérios de aceite executáveis. |
| **Rastreabilidade** | Ligação bidirecional entre requisito, regra de negócio, endpoint, tela e teste. |
| **Prioridade MoSCoW** | `Must` (sem ele não há MVP), `Should` (importante, adiável), `Could` (desejável), `Won't` (fora). |

### 3.1 Estrutura de cada requisito detalhado

| Seção | Conteúdo |
|---|---|
| Identificação | ID, prioridade, fase, personas, épico |
| Enunciado | Frase única e não ambígua |
| Entradas | Campos, tipos, obrigatoriedade, restrições |
| Processamento | Regras aplicadas em ordem |
| Saídas | O que o sistema retorna ou exibe |
| Critérios de aceite | Cenários Gherkin |
| Exceções | Erros tratados |
| Rastreabilidade | RN, endpoint, tela, teste |

---

## 4. Índice de requisitos por área

| Área | Faixa | Total | Must | Should | Could |
|---|---|:--:|:--:|:--:|:--:|
| Autenticação e conta | RF-001–019 | 12 | 10 | 2 | 0 |
| Clientes | RF-020–039 | 9 | 6 | 2 | 1 |
| Contratos e períodos | RF-040–069 | 17 | 12 | 4 | 1 |
| Categorias e tags | RF-070–079 | 5 | 3 | 2 | 0 |
| Tickets | RF-080–109 | 13 | 7 | 6 | 0 |
| Registro de horas | RF-110–139 | 13 | 10 | 2 | 1 |
| Timer | RF-140–159 | 12 | 9 | 2 | 1 |
| Dashboard | RF-160–179 | 10 | 6 | 4 | 0 |
| Relatórios | RF-180–209 | 16 | 10 | 6 | 0 |
| Notificações | RF-210–229 | 12 | 6 | 6 | 0 |
| Auditoria | RF-230–239 | 4 | 2 | 2 | 0 |
| **Total** | | **123** | **81** | **38** | **4** |

---

## 5. Requisitos detalhados — Autenticação

### RF-001 — Cadastro de conta

| Campo | Valor |
|---|---|
| **Prioridade** | Must |
| **Fase** | F0 |
| **Personas** | Rafael, Camila |
| **Épico** | EP-02 |

**Enunciado:** O sistema deve permitir que uma pessoa crie uma conta informando e-mail, senha e nome completo, criando automaticamente um `Tenant`, um `Membership` com papel `OWNER` e as categorias padrão.

**Entradas:**

| Campo | Tipo | Obrig. | Restrições |
|---|---|:--:|---|
| `email` | string | ✔ | Formato RFC 5322, ≤ 255 caracteres, único global (RN-452) |
| `password` | string | ✔ | ≥ 10 caracteres, 1 maiúscula, 1 minúscula, 1 dígito, fora da lista de senhas comuns (RN-451) |
| `fullName` | string | ✔ | 2–150 caracteres |
| `tenantName` | string | ✖ | Default: `fullName`. 2–120 caracteres |
| `timezone` | string | ✖ | Default `America/Sao_Paulo`, IANA válido |
| `acceptedTerms` | boolean | ✔ | Deve ser `true` |

**Processamento:**

1. Validar formato e unicidade do e-mail (RN-452).
2. Validar a política de senha (RN-451).
3. Criar `User` com `status = PENDING_ACTIVATION` e senha em BCrypt custo 12.
4. Criar `Tenant` com `status = ACTIVE`, slug derivado e único.
5. Criar `Membership` `OWNER` já `ACTIVE`.
6. Criar as 9 categorias padrão (RN-501).
7. Registrar consentimento dos termos (RNF-062).
8. Enviar e-mail de verificação com token válido por 7 dias.
9. Registrar `AuditLog`.

**Saídas:** `201 Created` com `{ userId, tenantId, email, status: "PENDING_ACTIVATION" }`. Nenhum token é emitido antes da verificação.

**Critérios de aceite:**

```gherkin
Cenário: Cadastro bem-sucedido
  Dado que não existe usuário com o e-mail "rafael@exemplo.com"
  Quando eu me cadastro com e-mail "rafael@exemplo.com", senha "SenhaForte123" e nome "Rafael Mendes"
  Então o sistema retorna 201
  E um usuário é criado com status "PENDING_ACTIVATION"
  E um tenant é criado com status "ACTIVE"
  E existe um membership com papel "OWNER" e status "ACTIVE"
  E existem exatamente 9 categorias padrão no tenant
  E um e-mail de verificação é enviado

Cenário: E-mail já cadastrado
  Dado que existe um usuário com o e-mail "rafael@exemplo.com"
  Quando eu me cadastro com o mesmo e-mail
  Então o sistema retorna 409 com código "DEVTIME-2452"

Cenário: Senha fraca
  Quando eu me cadastro com a senha "12345678"
  Então o sistema retorna 422 com código "DEVTIME-2451"
  E a mensagem indica quais requisitos não foram atendidos

Cenário: Termos não aceitos
  Quando eu me cadastro com acceptedTerms igual a false
  Então o sistema retorna 422
```

**Exceções:** falha no envio de e-mail não impede a criação da conta; o reenvio fica disponível na tela de verificação.

**Rastreabilidade:** RN-451, RN-452, RN-501 · `POST /api/v1/auth/register` · `05-ui/pages.md#registro` · TC-0001–TC-0008.

---

### RF-003 — Autenticação

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F0 · **Épico** EP-02 |

**Enunciado:** O sistema deve autenticar por e-mail e senha, retornando um access token JWT (15 min) e um refresh token opaco (30 dias).

**Processamento:**

1. Localizar o usuário pelo e-mail normalizado.
2. Verificar `lockedUntil` (RN-453).
3. Comparar a senha com o hash BCrypt.
4. Em falha: incrementar `failedLoginAttempts`; ao atingir 5 em 15 minutos, bloquear por 30 minutos.
5. Em sucesso: zerar contador, atualizar `lastLoginAt`.
6. Listar memberships ativos:
   - **0 memberships** → erro `DEVTIME-1003`;
   - **1 membership** → emitir tokens já com `tid` e `role`;
   - **2+ memberships** → emitir token de pré-seleção sem `tid`, exigindo `/auth/select-tenant`.
7. Persistir o hash do refresh token com user agent e IP.

**Critérios de aceite:**

```gherkin
Cenário: Login com um único tenant
  Dado um usuário ativo com um membership
  Quando autentico com credenciais válidas
  Então recebo accessToken com claims sub, tid e role
  E recebo refreshToken
  E o accessToken expira em 15 minutos

Cenário: Login com múltiplos tenants
  Dado um usuário com dois memberships ativos
  Quando autentico com credenciais válidas
  Então recebo um token de pré-seleção sem a claim tid
  E recebo a lista de tenants disponíveis
  E qualquer endpoint de negócio retorna 401 com "DEVTIME-1002"

Cenário: Bloqueio após 5 falhas
  Dado um usuário ativo
  Quando erro a senha 5 vezes em 15 minutos
  Então a 6ª tentativa retorna 423 com "DEVTIME-1006"
  E o usuário fica bloqueado por 30 minutos
  E um e-mail de alerta de segurança é enviado

Cenário: Credenciais inválidas não revelam existência do e-mail
  Quando autentico com e-mail inexistente
  Então recebo 401 com "DEVTIME-1001"
  E a mensagem é idêntica à de senha incorreta
```

**Rastreabilidade:** RN-453, ART-080, ART-081 · `POST /api/v1/auth/login` · TC-0010–TC-0020.

---

## 6. Requisitos detalhados — Contratos e banco de horas

### RF-040 — Criação de contrato mensal

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F1 · **Épico** EP-05 · **Personas** Rafael, Camila |

**Entradas:**

| Campo | Tipo | Obrig. | Restrições |
|---|---|:--:|---|
| `clientId` | UUID | ✔ | Cliente `ACTIVE` do tenant (RN-201) |
| `name` | string | ✔ | 2–150 caracteres |
| `code` | string | ✖ | Default sequencial `CT-0001`; único por tenant |
| `type` | enum | ✔ | `MONTHLY_HOURS` \| `HOURLY_OPEN` |
| `monthlyMinutes` | int | condicional | Obrigatório em `MONTHLY_HOURS`; 1–44.640 (RN-202) |
| `startDate` | date | ✔ | — |
| `endDate` | date | ✖ | ≥ `startDate` (RN-204) |
| `billingDay` | int | ✔ | 1–28 (RN-203) |
| `rolloverPolicy` | enum | ✔ | `NONE` \| `FULL` \| `CAPPED` |
| `rolloverCapMinutes` | int | condicional | Obrigatório em `CAPPED`; ≥ 0 |
| `rolloverExpiryPeriods` | int | ✖ | Default 1; ≥ 0 |
| `overagePolicy` | enum | ✔ | `BLOCK` \| `WARN` \| `ALLOW_BILLABLE` |
| `hourlyRate` | decimal | ✖ | ≥ 0 |
| `overageRate` | decimal | ✖ | Default `hourlyRate` |
| `notificationThresholds` | int[] | ✖ | Default `[50,80,100]`; valores entre 1 e 500 |
| `prorateFirstPeriod` | boolean | ✖ | Default `true` (RN-217) |

**Saídas:** `201 Created` com o contrato em `status = DRAFT` e uma prévia dos 3 primeiros períodos que serão gerados na ativação.

**Critérios de aceite:**

```gherkin
Cenário: Criação válida de contrato mensal
  Dado um cliente ativo
  Quando crio um contrato de 2400 minutos, billingDay 1, rollover CAPPED com teto 300
  Então o contrato é criado com status "DRAFT"
  E a resposta inclui a prévia dos 3 primeiros períodos

Cenário: CAPPED sem teto informado
  Quando crio um contrato com rolloverPolicy "CAPPED" e sem rolloverCapMinutes
  Então recebo 422 apontando o campo rolloverCapMinutes

Cenário: Dia de faturamento inválido
  Quando crio um contrato com billingDay 31
  Então recebo 422 com "DEVTIME-2203"
  E a mensagem explica que o intervalo permitido é de 1 a 28

Cenário: Cliente inativo
  Dado um cliente com status "INACTIVE"
  Quando crio um contrato para ele
  Então recebo 422 com "DEVTIME-2201"
```

**Rastreabilidade:** RN-201 a RN-204, RN-217 · `POST /api/v1/contracts` · `05-ui/pages.md#contrato-form` · TC-0100–TC-0125.

---

### RF-046 — Cálculo do banco de horas

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F2 · **Épico** EP-08 |

**Enunciado:** O sistema deve calcular, para cada período de contrato, o saldo de horas de forma determinística e explicável.

**Processamento (ordem obrigatória):**

| # | Passo | Fórmula |
|---|---|---|
| 1 | Somar os registros faturáveis do período | `consumedMinutes = Σ billableMinutes` (RN-219) |
| 2 | Somar os ajustes | `adjustmentMinutes = Σ adjustments.minutes` |
| 3 | Calcular o disponível | `availableMinutes = contracted + carriedIn + adjustments` (RN-218) |
| 4 | Calcular o restante | `remainingMinutes = available − consumed` (RN-220) |
| 5 | Calcular o excedente | `overageMinutes = max(0, consumed − available)` (RN-221) |
| 6 | Calcular a taxa | `consumptionRate` (RN-222) |
| 7 | Calcular o ritmo e a projeção | `burnRate`, `projectedConsumption` |

**Saídas — estrutura do extrato:**

| Linha do extrato | Valor de exemplo |
|---|---|
| Horas contratadas | 40:00 |
| (+) Transportado do período anterior | 05:00 |
| (+) Ajustes | 01:00 (1 ajuste) |
| (=) **Total disponível** | **46:00** |
| (−) Horas consumidas (faturáveis) | 48:20 |
| (=) **Saldo** | **−02:20** |
| **Excedente** | **02:20** |
| Horas não faturáveis (informativo) | 03:15 |
| Taxa de consumo | 105,07% |

**Critérios de aceite:**

```gherkin
Cenário: Cálculo com carry-in e ajuste
  Dado um período com 2400 minutos contratados
  E 300 minutos transportados do período anterior
  E um ajuste de +60 minutos
  E registros faturáveis somando 2900 minutos
  Então availableMinutes é 2760
  E remainingMinutes é -140
  E overageMinutes é 140
  E consumptionRate é 105.07

Cenário: Horas não faturáveis não consomem saldo
  Dado um período com 2400 minutos disponíveis
  E um registro faturável de 600 minutos
  E um registro não faturável de 300 minutos
  Então consumedMinutes é 600
  E nonBillableMinutes é 300
  E remainingMinutes é 1800

Cenário: Determinismo
  Dado um período com 500 registros
  Quando recalculo o saldo 10 vezes
  Então todos os resultados são idênticos

Cenário: Contrato de horas abertas
  Dado um contrato do tipo HOURLY_OPEN
  Então availableMinutes é 0
  E consumptionRate é 0
  E nenhum alerta de consumo é gerado
```

**Rastreabilidade:** RN-218 a RN-223, CE-10 · `GET /api/v1/contract-periods/{id}/balance` · `05-ui/pages.md#contrato-detalhe` · TC-0200–TC-0240.

---

### RF-051 — Fechamento de período

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F3 · **Épico** EP-12 · **Permissão** `PERIOD_CLOSE` |

**Processamento:** sequência atômica de 7 passos definida em RN-241.

**Critérios de aceite:**

```gherkin
Cenário: Fechamento bem-sucedido
  Dado um período OPEN cuja endDate já passou
  E nenhum cronômetro ativo no período
  Quando fecho o período
  Então o status passa a "CLOSED"
  E todos os registros do período recebem lockedAt
  E um snapshot é criado com checksum SHA-256
  E carriedOutMinutes é calculado conforme a política do contrato
  E o período seguinte recebe carriedInMinutes igual ao carriedOut
  E uma notificação PERIOD_CLOSED é gerada

Cenário: Bloqueio por cronômetro ativo
  Dado um período OPEN
  E um cronômetro RUNNING iniciado dentro do período
  Quando tento fechar o período
  Então recebo 409 com "DEVTIME-2240"
  E a resposta identifica o cronômetro e seu dono
  E o período permanece "OPEN"

Cenário: Fechamento antecipado exige confirmação
  Dado um período OPEN cuja endDate ainda não chegou
  Quando tento fechar sem confirmação
  Então recebo 409 com "DEVTIME-2239"
  Quando tento fechar com confirmEarlyClose igual a true
  Então o fechamento é concluído

Cenário: Registro travado não pode ser editado
  Dado um período CLOSED
  Quando tento editar um registro do período
  Então recebo 409 com "DEVTIME-2121"

Cenário: Falha em qualquer passo faz rollback
  Dado um período OPEN pronto para fechar
  E uma falha simulada na geração do snapshot
  Quando fecho o período
  Então o período permanece "OPEN"
  E nenhum registro recebe lockedAt
```

**Rastreabilidade:** RN-239 a RN-245 · `POST /api/v1/contract-periods/{id}/close` · TC-0300–TC-0330.

---

## 7. Requisitos detalhados — Registro de horas

### RF-110 — Registro manual de horas

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F1 · **Épico** EP-07 · **Personas** Rafael, Diego |

**Entradas:**

| Campo | Tipo | Obrig. | Restrições |
|---|---|:--:|---|
| `ticketId` | UUID | ✔ | Ticket do tenant, contrato aceitando registros (RN-101, RN-306) |
| `workDate` | date | ✖ | Default: data local de `startedAt` |
| `startedAt` | datetime | ✔ | ISO-8601 com offset |
| `endedAt` | datetime | condicional | Obrigatório se `durationMinutes` ausente |
| `durationMinutes` | int | condicional | Alternativa a `endedAt` (RF-111); 1–1440 |
| `pausedMinutes` | int | ✖ | Default 0; < duração bruta |
| `categoryId` | UUID | ✔ | Categoria ativa do tenant (RN-104) |
| `description` | string | ✔ | 3–2000 caracteres (RN-105) |
| `billable` | boolean | ✖ | Default: `category.billableByDefault` |
| `tagIds` | UUID[] | ✖ | Máximo 10 |
| `userId` | UUID | ✖ | Somente com `WORKLOG_CREATE_FOR_OTHER` (RN-106) |

**Processamento:** a sequência de validações é exatamente a do fluxograma da seção 5.5 de `business-rules.md`. A ordem é normativa — o primeiro erro encontrado interrompe o processamento e é o único retornado, exceto em erros de validação de formato, que são agregados.

**Saídas:** `201 Created` com o registro criado, o saldo atualizado do período e eventuais `warnings[]`.

**Critérios de aceite:**

```gherkin
Cenário: Registro válido
  Dado um ticket ativo e um período aberto
  Quando registro de 09:00 às 11:30 na categoria "Desenvolvimento" com descrição "Implementação do checkout"
  Então recebo 201
  E netMinutes é 150
  E o saldo do período é reduzido em 150 minutos
  E ticket.spentMinutes aumenta em 150

Cenário: Entrada por duração
  Quando registro com startedAt 09:00 e durationMinutes 90
  Então endedAt é calculado como 10:30
  E netMinutes é 90

Cenário: Sobreposição rejeitada
  Dado um registro meu das 09:00 às 11:00
  Quando registro das 10:00 às 12:00
  Então recebo 422 com "DEVTIME-2102"
  E a resposta identifica o registro conflitante

Cenário: Sessões que se tocam são permitidas
  Dado um registro meu das 09:00 às 11:00
  Quando registro das 11:00 às 12:00
  Então recebo 201

Cenário: Sessão acima de 24 horas
  Quando registro das 08:00 do dia 10 às 09:00 do dia 11
  Então recebo 422 com "DEVTIME-2103"

Cenário: Sessão atravessando a meia-noite
  Quando registro das 22:00 do dia 10 às 01:30 do dia 11
  Então recebo 201
  E workDate é o dia 10
  E netMinutes é 210

Cenário: Segundos são truncados
  Quando registro das 09:00:00 às 11:30:59
  Então netMinutes é 150

Cenário: Pausa consome toda a sessão
  Quando registro das 09:00 às 10:00 com pausedMinutes 60
  Então recebo 422 com "DEVTIME-2115"

Cenário: Registro fora da vigência do contrato
  Dado um contrato vigente de 01/02 a 31/12
  Quando registro com data 15/01
  Então recebo 422 com "DEVTIME-2117"

Cenário: Política BLOCK impede estouro
  Dado um contrato com overagePolicy "BLOCK" e 60 minutos disponíveis
  Quando registro 90 minutos faturáveis
  Então recebo 422 com "DEVTIME-2220"
  E a mensagem informa os 60 minutos disponíveis

Cenário: Política WARN permite com aviso
  Dado um contrato com overagePolicy "WARN" e 60 minutos disponíveis
  Quando registro 90 minutos faturáveis
  Então recebo 201
  E a resposta contém um warning com código "DEVTIME-2221"
  E uma notificação CONTRACT_OVERAGE é gerada
```

**Rastreabilidade:** RN-101 a RN-126, RN-231 a RN-233 · `POST /api/v1/work-logs` · `05-ui/pages.md#worklog-form` · TC-0400–TC-0480.

---

### RF-140 a RF-150 — Cronômetro

| Campo | Valor |
|---|---|
| **Prioridade** | Must · **Fase** F1 · **Épico** EP-07 |

**Critérios de aceite consolidados:**

```gherkin
Cenário: Iniciar cronômetro
  Dado que não tenho cronômetro ativo
  Quando inicio um cronômetro no ticket "CT-0001-42"
  Então recebo 201 com status "RUNNING"
  E startedAt é o instante atual
  E o cronômetro aparece na barra global

Cenário: Segundo cronômetro é rejeitado
  Dado um cronômetro RUNNING
  Quando tento iniciar outro
  Então recebo 409 com "DEVTIME-2150"

Cenário: Troca atômica de tarefa
  Dado um cronômetro RUNNING no ticket A
  Quando inicio um cronômetro no ticket B com stopCurrent igual a true
  Então o cronômetro do ticket A é encerrado e gera um registro
  E um novo cronômetro RUNNING é criado no ticket B
  E a operação é atômica

Cenário: Pausar e retomar
  Dado um cronômetro RUNNING iniciado às 09:00
  Quando pauso às 10:30
  Então o status é "PAUSED" e o tempo exibido congela em 01:30
  Quando retomo às 11:00
  Então o status é "RUNNING"
  E pausedMinutes é 30

Cenário: Encerrar gerando registro
  Dado um cronômetro iniciado às 09:00 com 30 minutos de pausa
  Quando encerro às 12:15 com descrição "Ajustes no relatório"
  Então um registro é criado com grossMinutes 195, pausedMinutes 30 e netMinutes 165
  E o cronômetro fica com status "COMPLETED"

Cenário: Encerramento sem descrição
  Quando encerro sem informar descrição
  Então recebo 422 com "DEVTIME-2105"
  E o cronômetro permanece no estado anterior

Cenário: Falha de validação preserva o cronômetro
  Dado um cronômetro que geraria sobreposição
  Quando encerro
  Então recebo 422 com "DEVTIME-2102"
  E o cronômetro permanece ativo
  E nenhum tempo é perdido

Cenário: Persistência entre sessões
  Dado um cronômetro RUNNING
  Quando recarrego a página, troco de dispositivo ou o backend reinicia
  Então o cronômetro continua ativo com o tempo correto

Cenário: Alerta de cronômetro longo
  Dado um cronômetro RUNNING há 8 horas
  Quando o job de verificação executa
  Então uma notificação TIMER_LONG_RUNNING é gerada uma única vez

Cenário: Abandono automático
  Dado um cronômetro RUNNING há 16 horas
  Quando o job de verificação executa
  Então o status passa a "ABANDONED"
  E nenhum registro é gerado automaticamente
  E o usuário é notificado com a opção de recuperar

Cenário: Recuperação de cronômetro abandonado
  Dado um cronômetro ABANDONED há 2 dias
  Quando o recupero informando endedAt válido
  Então um registro é criado com o tempo informado
  E o status passa a "COMPLETED"
```

**Rastreabilidade:** RN-150 a RN-167 · `/api/v1/timers/*` · `05-ui/components.md#timer-bar` · TC-0500–TC-0560.

---

## 8. Requisitos detalhados — Relatórios

### RF-180 / RF-185 / RF-187 — Relatório de período e exportação PDF

```gherkin
Cenário: Relatório de período fechado vem do snapshot
  Dado um período CLOSED com snapshot gerado
  Quando altero o nome do cliente
  E gero o relatório do período
  Então o relatório exibe o nome do cliente vigente no fechamento
  E o conteúdo é idêntico ao do primeiro fechamento

Cenário: Relatório de período aberto é marcado como parcial
  Dado um período OPEN
  Quando gero o relatório
  Então todas as páginas contêm a marcação "PARCIAL"
  E o PDF exibe aviso de que os dados podem mudar

Cenário: PDF determinístico
  Dado um período CLOSED
  Quando gero o PDF duas vezes
  Então o conteúdo é idêntico exceto pelo carimbo de emissão

Cenário: Registro excluído não aparece
  Dado um registro excluído logicamente em período aberto
  Quando gero o relatório
  Então o registro não consta e não é somado

Cenário: Limite de intervalo
  Quando solicito um relatório de 400 dias
  Então recebo 400 com "DEVTIME-3001"

Cenário: Excel com duas colunas de duração
  Quando exporto em XLSX
  Então existe uma coluna "Duração" no formato HH:MM como texto
  E uma coluna "Horas decimais" numérica com 2 casas
  E a soma da coluna decimal confere com o total do relatório

Cenário: MEMBER exporta apenas os próprios registros
  Dado que sou MEMBER
  Quando exporto um relatório sem filtro de usuário
  Então o relatório contém apenas os meus registros
  Quando exporto filtrando por outro usuário
  Então recebo 403 com "DEVTIME-1101"

Cenário: URL de download expira
  Dado um relatório exportado
  Quando acesso a URL de download após 16 minutos
  Então recebo 403
```

**Rastreabilidade:** RN-701 a RN-712 · `/api/v1/reports/*` · TC-0600–TC-0660.

---

## 9. Requisitos não funcionais mensuráveis

| ID | Requisito | Métrica | Meta | Método de verificação | Frequência |
|---|---|---|---|---|---|
| RNF-001 | Latência de leitura | p95 | < 300 ms | k6 com 100k work logs | A cada release |
| RNF-002 | Latência de escrita | p95 | < 500 ms | k6 | A cada release |
| RNF-003 | Dashboard | p95 | < 800 ms | k6 | A cada release |
| RNF-004 | PDF de 1.000 linhas | tempo | < 5 s | Teste automatizado | A cada release |
| RNF-005 | Exportação de 5.000 linhas | tempo | < 15 s | Teste automatizado | A cada release |
| RNF-006 | FCP do frontend | mediana | < 1,5 s | Lighthouse CI | A cada PR |
| RNF-007 | Bundle inicial | tamanho | < 500 KB gzip | Análise de bundle | A cada PR |
| RNF-010 | Tenants por instância | quantidade | 10.000 | Teste de volume | Trimestral |
| RNF-011 | Work logs por tenant | quantidade | 500.000 | Teste de volume | Trimestral |
| RNF-012 | Usuários concorrentes | quantidade | 1.000 | Teste de carga | Trimestral |
| RNF-020 | Disponibilidade | uptime mensal | ≥ 99,5% | Monitor externo | Contínuo |
| RNF-021 | RPO | perda máxima | ≤ 15 min | Teste de restauração | Mensal |
| RNF-022 | RTO | tempo de recuperação | ≤ 4 h | Simulação de desastre | Trimestral |
| RNF-032 | Isolamento entre tenants | vazamentos | 0 | Suíte dedicada | A cada PR |
| RNF-034 | Rate limit de login | req/min | 10 por IP+e-mail | Teste de integração | A cada release |
| RNF-038 | Vulnerabilidades | HIGH/CRITICAL | 0 | OWASP Dependency-Check | A cada PR |
| RNF-042 | Acessibilidade | violações AA | 0 nas telas principais | axe-core + auditoria manual | A cada release |
| RNF-050 | Cobertura de testes | % de linhas | ≥ 80% (≥ 90% em serviços) | JaCoCo | A cada PR |

---

## 10. Matriz de rastreabilidade

| RF | Regras de negócio | Endpoints | Telas | Épico | Fase | Casos de teste |
|---|---|---|---|---|:--:|---|
| RF-001 | RN-451, 452, 501 | `POST /auth/register` | Registro | EP-02 | F0 | TC-0001–0008 |
| RF-003 | RN-453 | `POST /auth/login` | Login | EP-02 | F0 | TC-0010–0020 |
| RF-009 | CE-P-01 | `GET /auth/tenants`, `POST /auth/select-tenant` | Seletor | EP-02 | F0 | TC-0025–0030 |
| RF-020–026 | RN-401–407 | `/clients` | Clientes | EP-04 | F1 | TC-0050–0090 |
| RF-040–045 | RN-201–217 | `/contracts` | Contratos | EP-05 | F1 | TC-0100–0180 |
| RF-046–049 | RN-218–223 | `/contract-periods/{id}/balance` | Detalhe do contrato | EP-08 | F2 | TC-0200–0240 |
| RF-050 | RN-215, 235–238 | `/contract-periods/{id}/adjustments` | Ajuste | EP-08 | F2 | TC-0250–0270 |
| RF-051–052 | RN-239–245 | `/contract-periods/{id}/close`, `/reopen` | Fechamento | EP-12 | F3 | TC-0300–0330 |
| RF-070–074 | RN-501–508 | `/categories`, `/tags` | Configurações | EP-06 | F1 | TC-0350–0380 |
| RF-080–092 | RN-301–314 | `/tickets` | Tickets | EP-06 | F1 | TC-0400–0399* |
| RF-110–122 | RN-101–126 | `/work-logs` | Registro | EP-07 | F1 | TC-0400–0480 |
| RF-140–151 | RN-150–167 | `/timers` | Barra global | EP-07 | F1 | TC-0500–0560 |
| RF-160–169 | — | `/dashboard` | Dashboard | EP-09 | F2 | TC-0570–0590 |
| RF-180–195 | RN-701–712 | `/reports` | Relatórios | EP-11 | F3 | TC-0600–0660 |
| RF-210–221 | RN-601–610 | `/notifications` | Central | EP-10 | F2 | TC-0700–0730 |
| RF-230–233 | RN-006 | `/audit-logs` | Auditoria | EP-15 | F4 | TC-0750–0770 |

\* Faixa de tickets: TC-0390–0399 e TC-0481–0499.

---

## 11. Casos especiais

| # | Caso | Requisito afetado | Tratamento |
|---|---|---|---|
| CE-R-01 | Requisito conflita com uma regra de negócio | Qualquer | A regra de negócio prevalece; o requisito é corrigido |
| CE-R-02 | Requisito `Should` bloqueia um `Must` | — | O `Should` é promovido a `Must` mediante registro |
| CE-R-03 | Critério de aceite impossível de automatizar | Qualquer | Torna-se checklist manual em `06-testing/acceptance.md` |
| CE-R-04 | Requisito depende de item fora do MVP | — | O requisito é rebaixado para a fase da dependência |
| CE-R-05 | Duas personas exigem comportamentos opostos | — | Aplica-se a resolução de `personas.md` §13 |

## 12. Casos de erro

| Situação | Tratamento |
|---|---|
| Requisito implementado sem critério de aceite | PR bloqueado |
| Critério de aceite sem teste correspondente | PR bloqueado (ART-101) |
| Requisito sem rastreabilidade a regra de negócio ou persona | Requisito rejeitado na revisão |
| Divergência entre requisito e endpoint | Corrigir o endpoint ou abrir emenda ao requisito |

## 13. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Todo `RF-XXX` do PRD possui detalhamento ou está explicitamente marcado como trivial |
| CA-02 | Todo requisito `Must` possui ao menos 3 cenários Gherkin, incluindo um de exceção |
| CA-03 | Toda linha da matriz de rastreabilidade possui as 6 colunas preenchidas |
| CA-04 | Todo RNF possui métrica, meta, método e frequência de verificação |
| CA-05 | Nenhum requisito contradiz uma regra de `02-domain/business-rules.md` |
| CA-06 | Todo cenário Gherkin é traduzível diretamente em teste automatizado |

## 14. Dependências e impactos

| Documento | Relação |
|---|---|
| `prd.md` | Fonte dos requisitos detalhados aqui |
| `02-domain/business-rules.md` | Fonte normativa das regras citadas |
| `user-stories.md` | Narrativa das mesmas capacidades na voz das personas |
| `04-api/*` | Implementa entradas e saídas especificadas |
| `06-testing/test-cases.md` | Deriva os casos de teste da matriz de rastreabilidade |
| `07-backlog/stories.md` | Converte requisitos em itens executáveis |

**Impacto:** alterar um requisito `Must` exige revisão do backlog da fase, dos casos de teste vinculados e da documentação de API correspondente.
