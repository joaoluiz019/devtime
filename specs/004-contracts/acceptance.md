# 004 — Contracts & Periods · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-004-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-004-01 | Feliz | Criação em `DRAFT` sem períodos | RN-201 |
| AC-004-02 | Feliz | Prévia de períodos | §6 contracts.md |
| AC-004-03 | Feliz | Ativação gera o 1º período | RN-209 |
| AC-004-04 | Feliz | Geração automática do período seguinte | RN-213 |
| AC-004-05 | Feliz | Abertura do período agendado | §4.6 SM |
| AC-004-06 | Feliz | Rateio do período parcial | RN-217 |
| AC-004-07 | Feliz | Contrato `HOURLY_OPEN` | RN-210 |
| AC-004-08 | Feliz | Suspensão e retomada | §4.5 SM |
| AC-004-09 | Feliz | Encerramento trunca o período | RN-214 |
| AC-004-10 | Feliz | Exclusão em `DRAFT` | RN-205 |
| AC-004-11 | Erro | Cliente inativo | RN-201, RN-405 |
| AC-004-12 | Erro | `monthlyMinutes` fora da faixa | RN-202 |
| AC-004-13 | Erro | `billingDay` fora de 1–28 | RN-203 |
| AC-004-14 | Erro | `endDate` anterior a `startDate` | RN-204 |
| AC-004-15 | Erro | Exclusão com work logs | RN-205 |
| AC-004-16 | Erro | Alteração de `type` fora de `DRAFT` | RN-206 |
| AC-004-17 | Erro | Alteração de `monthlyMinutes` afetando período fechado | RN-207 |
| AC-004-18 | Erro | Alteração de `billingDay` com horas lançadas | RN-208 |
| AC-004-19 | Erro | Transição proibida `ENDED → ACTIVE` | §4.5 SM |
| AC-004-20 | Erro | `HOURLY_OPEN` com `monthlyMinutes` | INV-CTR-03 |
| AC-004-21 | Erro | `CAPPED` sem `rolloverCapMinutes` | INV-CTR-04 |
| AC-004-22 | Erro | Suspender com timer ativo | §4.5 SM |
| AC-004-23 | Extremo | `startDate` igual ao `billingDay` | CX-03 |
| AC-004-24 | Extremo | `billingDay = 28` em fevereiro | CX-01, CE-05 |
| AC-004-25 | Extremo | `endDate` dentro do primeiro período | CX-05 |
| AC-004-26 | Extremo | Contrato de um único dia | CX-04 |
| AC-004-27 | Extremo | Retomada após 2 ciclos suspensos | CX-06, CE-ME-09 |
| AC-004-28 | Extremo | `prorateFirstPeriod = false` | CX-07 |
| AC-004-29 | Extremo | Contrato com `startDate` retroativa | CX-12, CE-06 |
| AC-004-30 | Extremo | Todos os 5 cenários da tabela normativa | RN-211, RN-212 |
| AC-004-31 | Segurança | Contrato de outro tenant retorna 404 | RN-002 |
| AC-004-32 | Segurança | `MANAGER` não encerra nem cancela | nota ³ |
| AC-004-33 | Segurança | `MEMBER` não vê valores monetários | `CONTRACT_VIEW_FINANCIAL` |
| AC-004-34 | Segurança | `status` não muda por `PATCH` | ME-05 |
| AC-004-35 | Segurança | Job respeita o escopo de tenant | JB-06 |
| AC-004-36 | Concorrência | Duas ativações simultâneas | INV-PER-01 |
| AC-004-37 | Concorrência | Job em duas instâncias | ADR-007 |
| AC-004-38 | Concorrência | Sobreposição bloqueada pelo banco | INV-PER-02 |
| AC-004-39 | Concorrência | Edição concorrente de contrato | RN-004 |

---

## 3. Cenários felizes

### AC-004-01 — Criação em `DRAFT` sem períodos
```gherkin
Dado um cliente com status "ACTIVE"
Quando eu envio POST /api/v1/contracts com tipo "MONTHLY_HOURS", monthlyMinutes 2400,
      startDate 2026-08-01, billingDay 1 e política de rollover "FULL"
Então recebo 201 Created
E o contrato é criado com status "DRAFT"
E o código gerado segue o padrão "CT-0001"
E NENHUM ContractPeriod é criado
E o campo type ainda pode ser alterado
E um AuditLog com action "CONTRACT_CREATED" foi gravado
```

### AC-004-02 — Prévia de períodos
```gherkin
Quando eu envio POST /api/v1/contracts/preview-periods com monthlyMinutes 2400,
      startDate 2026-01-10 e billingDay 1
Então recebo 200 OK
E a resposta contém 3 períodos
E o primeiro vai de 2026-01-10 a 2026-01-31, marcado como parcial, com contractedMinutes 1703
E o segundo vai de 2026-02-01 a 2026-02-28 com contractedMinutes 2400
E o terceiro vai de 2026-03-01 a 2026-03-31 com contractedMinutes 2400
E NENHUM registro é persistido no banco
```

### AC-004-03 — Ativação gera o 1º período
```gherkin
Dado um contrato "DRAFT" com todos os campos obrigatórios preenchidos
E um cliente "ACTIVE"
Quando eu envio POST /api/v1/contracts/{id}/activate
Então recebo 200 OK
E o contrato passa a "ACTIVE"
E exatamente um ContractPeriod é criado com sequence 1 e status "OPEN"
E contractedMinutes, hourlyRateSnapshot, overageRateSnapshot e currency estão congelados no período
E activeContractsCount do cliente é incrementado
E o campo type torna-se imutável
E a criação do período ocorreu na mesma transação da ativação
```

### AC-004-04 — Geração automática do período seguinte
```gherkin
Dado um contrato "ACTIVE" com autoRenew verdadeiro
E um período aberto terminando em 3 dias
Quando o GeneratePeriodsJob é executado
Então um novo ContractPeriod é criado com sequence igual ao anterior mais 1
E o status do novo período é "SCHEDULED"
E seu startDate é o dia seguinte ao endDate do período anterior
E não há lacuna nem sobreposição entre os dois períodos
E os snapshots de valor do contrato são congelados no novo período
```

### AC-004-05 — Abertura do período agendado
```gherkin
Dado um ContractPeriod com status "SCHEDULED" cujo startDate é hoje
Quando o OpenScheduledPeriodsJob é executado
Então o período passa a "OPEN"
E o contrato passa a ter exatamente um período "OPEN"
E, se o período anterior estiver "CLOSED", carriedInMinutes é aplicado
```

### AC-004-06 — Rateio do período parcial
```gherkin
Dado um contrato com monthlyMinutes 2400, startDate 2026-01-10 e billingDay 1
Quando o contrato é ativado
Então o primeiro período vai de 2026-01-10 a 2026-01-31
E possui 22 dias corridos
E o ciclo cheio de janeiro possui 31 dias
E contractedMinutes é exatamente 1703
E o valor é um inteiro, sem qualquer uso de ponto flutuante
```

### AC-004-07 — Contrato `HOURLY_OPEN`
```gherkin
Dado um contrato do tipo "HOURLY_OPEN"
Quando o contrato é ativado
Então o período criado possui contractedMinutes igual a 0
E rolloverPolicy é "NONE"
E a política de excedente é ignorada
E nenhum alerta de consumo é gerado para esse contrato
E o período existe apenas para agrupar os registros de horas
```

### AC-004-08 — Suspensão e retomada
```gherkin
Dado um contrato "ACTIVE" sem nenhum timer ativo em seus tickets
Quando eu envio POST /api/v1/contracts/{id}/suspend com um motivo
Então recebo 200 OK e o contrato passa a "SUSPENDED"
E o período aberto permanece aberto
E o GeneratePeriodsJob deixa de criar novos períodos para esse contrato
Quando eu envio POST /api/v1/contracts/{id}/resume
Então o contrato volta a "ACTIVE"
E a geração de períodos é retomada mantendo a contiguidade
```

### AC-004-09 — Encerramento trunca o período
```gherkin
Dado um contrato "ACTIVE" com um período aberto de 01/08 a 31/08
Quando eu envio POST /api/v1/contracts/{id}/end com data de término 2026-08-15
Então recebo 200 OK e o contrato passa a "ENDED"
E o período corrente passa a terminar em 2026-08-15
E nenhum período posterior é gerado
E activeContractsCount do cliente é decrementado
E o período é fechado automaticamente 3 dias após o término
```

### AC-004-10 — Exclusão em `DRAFT`
```gherkin
Dado um contrato em status "DRAFT"
Quando eu envio DELETE /api/v1/contracts/{id}
Então recebo 204 No Content
E o contrato é excluído logicamente
E nenhum registro é removido fisicamente
```

---

## 4. Cenários de erro

### AC-004-11 — Cliente inativo
```gherkin
Dado um cliente com status "INACTIVE"
Quando eu envio POST /api/v1/contracts referenciando esse cliente
Então recebo 422 com o código "DEVTIME-2405"
E nenhum contrato é criado
Dado um contrato "DRAFT" cujo cliente foi inativado depois
Quando eu tento ativá-lo
Então recebo 422 com o código "DEVTIME-2201"
```

### AC-004-12 — `monthlyMinutes` fora da faixa
```gherkin
Quando eu envio POST /api/v1/contracts com tipo "MONTHLY_HOURS" e monthlyMinutes igual a 0
Então recebo 422 com o código "DEVTIME-2202"
Quando eu envio com monthlyMinutes igual a 44641
Então recebo 422 com o código "DEVTIME-2202"
Quando eu envio tipo "MONTHLY_HOURS" sem monthlyMinutes
Então recebo 422 com o código "DEVTIME-2202"
```

### AC-004-13 — `billingDay` fora de 1–28
```gherkin
Quando eu envio POST /api/v1/contracts com billingDay igual a 29
Então recebo 422 com o código "DEVTIME-2203"
E o mesmo ocorre para 0, 31 e valores negativos
E a mensagem explica que o limite existe porque os dias 29 a 31 não existem em todos os meses
```

### AC-004-14 — `endDate` anterior a `startDate`
```gherkin
Quando eu envio POST /api/v1/contracts com startDate 2026-08-01 e endDate 2026-07-31
Então recebo 422 com o código "DEVTIME-2204"
E nenhum contrato é criado
```

### AC-004-15 — Exclusão com work logs
```gherkin
Dado um contrato "ACTIVE" com ao menos um registro de horas
Quando eu envio DELETE /api/v1/contracts/{id}
Então recebo 409 com o código "DEVTIME-2205"
E a mensagem indica encerrar ou cancelar como alternativas
E o contrato permanece não excluído
```

### AC-004-16 — Alteração de `type` fora de `DRAFT`
```gherkin
Dado um contrato "ACTIVE" do tipo "MONTHLY_HOURS"
Quando eu envio PATCH /api/v1/contracts/{id} alterando type para "HOURLY_OPEN"
Então recebo 422 com o código "DEVTIME-2003"
E o tipo permanece "MONTHLY_HOURS"
```

### AC-004-17 — Alteração de `monthlyMinutes` afetando período fechado
```gherkin
Dado um contrato com um período "CLOSED" e um período "OPEN"
Quando eu envio PATCH alterando monthlyMinutes sem confirmação
Então recebo 409 com o código "DEVTIME-2207"
Quando eu reenvio com confirmação explícita
Então recebo 200 OK
E o período fechado mantém seu contractedMinutes original
E o período aberto é atualizado
E os períodos futuros passam a usar o novo valor
```

### AC-004-18 — Alteração de `billingDay` com horas lançadas
```gherkin
Dado um contrato com período "OPEN" contendo registros de horas
Quando eu envio PATCH alterando billingDay
Então recebo 409 com o código "DEVTIME-2208"
E o billingDay permanece inalterado
Mas a alteração é aceita quando o período aberto não possui nenhum registro
```

### AC-004-19 — Transição proibida `ENDED → ACTIVE`
```gherkin
Dado um contrato com status "ENDED"
Quando eu envio POST /api/v1/contracts/{id}/activate
Então recebo 409 com o código "DEVTIME-2010"
E a resposta contém currentStatus "ENDED" e availableTransitions vazio
E a mensagem orienta a criar um novo contrato
E o mesmo ocorre para toda transição a partir de "CANCELLED"
```

### AC-004-20 — `HOURLY_OPEN` com `monthlyMinutes`
```gherkin
Quando eu envio POST /api/v1/contracts com tipo "HOURLY_OPEN" e monthlyMinutes igual a 2400
Então recebo 422
E a mensagem indica que contratos de horas abertas não possuem saldo mensal
E o mesmo ocorre ao informar rolloverPolicy diferente de "NONE"
```

### AC-004-21 — `CAPPED` sem `rolloverCapMinutes`
```gherkin
Quando eu envio POST /api/v1/contracts com rolloverPolicy "CAPPED" e sem rolloverCapMinutes
Então recebo 422
E a mensagem indica que o teto de transporte é obrigatório nessa política
```

### AC-004-22 — Suspender com timer ativo
```gherkin
Dado um contrato "ACTIVE" com um timer em execução em um de seus tickets
Quando eu envio POST /api/v1/contracts/{id}/suspend
Então recebo 409
E a resposta lista os timers ativos que impedem a operação
E o contrato permanece "ACTIVE"
E o mesmo ocorre ao tentar encerrar
```

---

## 5. Cenários extremos

### AC-004-23 — `startDate` igual ao `billingDay`
```gherkin
Dado um contrato com startDate 2026-01-01 e billingDay 1
Quando o contrato é ativado
Então o primeiro período vai de 2026-01-01 a 2026-01-31
E é um ciclo cheio, não parcial
E contractedMinutes é igual a monthlyMinutes, sem rateio
```

### AC-004-24 — `billingDay = 28` em fevereiro
```gherkin
Dado um contrato com startDate 2026-02-28 e billingDay 28
Quando os períodos são gerados
Então o primeiro vai de 2026-02-28 a 2026-03-27
E o segundo vai de 2026-03-28 a 2026-04-27
E o terceiro vai de 2026-04-28 a 2026-05-27
E nenhum tratamento especial de mês é necessário
E o mesmo comportamento ocorre em ano bissexto
```

### AC-004-25 — `endDate` dentro do primeiro período
```gherkin
Dado um contrato com startDate 2026-01-01, billingDay 1 e endDate 2026-01-20
Quando o contrato é ativado
Então existe exatamente um período, de 2026-01-01 a 2026-01-20
E contractedMinutes é rateado para 20 dias de 31
E nenhum período posterior é gerado
E o GeneratePeriodsJob não cria nada para esse contrato
```

### AC-004-26 — Contrato de um único dia
```gherkin
Dado um contrato com startDate igual a endDate
Quando o contrato é ativado
Então existe exatamente um período de um único dia
E contractedMinutes é rateado para 1 dia do ciclo
E o valor resultante é um inteiro maior ou igual a 0
```

### AC-004-27 — Retomada após 2 ciclos suspensos
```gherkin
Dado um contrato suspenso em 2026-01-15 com período aberto até 2026-01-31
E que a retomada ocorre em 2026-04-10
Quando eu envio POST /api/v1/contracts/{id}/resume
Então os períodos de fevereiro e março são gerados
E o período de abril é gerado e aberto
E todos os períodos são contíguos, sem lacuna
E os períodos gerados retroativamente possuem contractedMinutes rateado conforme a política
E a validação de contiguidade passa
```

### AC-004-28 — `prorateFirstPeriod = false`
```gherkin
Dado um contrato com prorateFirstPeriod igual a falso, monthlyMinutes 2400,
      startDate 2026-01-10 e billingDay 1
Quando o contrato é ativado
Então o primeiro período vai de 2026-01-10 a 2026-01-31
E contractedMinutes é 2400, sem rateio
E o período continua marcado como parcial na resposta
```

### AC-004-29 — Contrato com `startDate` retroativa
```gherkin
Dado um contrato criado hoje com startDate 6 meses no passado
Quando o contrato é ativado
Então os períodos passados são criados com status "CLOSED"
E nenhum PeriodSnapshot é gerado para eles
E são marcados com a origem "MIGRATION"
E o período corrente é criado como "OPEN"
E somente ADMIN ou OWNER pode registrar horas nos períodos migrados
```

### AC-004-30 — Todos os 5 cenários da tabela normativa
```gherkin
Dado os parâmetros da tabela normativa da seção 6.2 da especificação
Quando os períodos são gerados para cada combinação
Então os períodos produzidos coincidem EXATAMENTE com a tabela:
  | startDate  | billingDay | Período 1              | Período 2         | Período 3         |
  | 2026-01-01 | 1          | 01/01 a 31/01          | 01/02 a 28/02     | 01/03 a 31/03     |
  | 2026-01-10 | 1          | 10/01 a 31/01 parcial  | 01/02 a 28/02     | 01/03 a 31/03     |
  | 2026-01-15 | 15         | 15/01 a 14/02          | 15/02 a 14/03     | 15/03 a 14/04     |
  | 2026-01-20 | 5          | 20/01 a 04/02 parcial  | 05/02 a 04/03     | 05/03 a 04/04     |
  | 2026-02-28 | 28         | 28/02 a 27/03          | 28/03 a 27/04     | 28/04 a 27/05     |
E em todos os casos os períodos são contíguos e não se sobrepõem
```

---

## 6. Cenários de segurança

### AC-004-31 — Contrato de outro tenant retorna 404
```gherkin
Dado que estou autenticado no tenant A
E que existe um contrato com id X no tenant B
Quando eu envio GET, PATCH, DELETE ou qualquer endpoint de transição em /api/v1/contracts/X
Então recebo 404 com o código "DEVTIME-2002" em todos os casos
E nunca recebo 403
E o contrato do tenant B permanece inalterado
```

### AC-004-32 — `MANAGER` não encerra nem cancela
```gherkin
Dado que estou autenticado com papel "MANAGER"
Quando eu envio POST /api/v1/contracts/{id}/activate
Então recebo 200 OK
Quando eu envio POST /api/v1/contracts/{id}/suspend
Então recebo 200 OK
Quando eu envio POST /api/v1/contracts/{id}/end
Então recebo 403 com o código "DEVTIME-1101"
Quando eu envio POST /api/v1/contracts/{id}/cancel
Então recebo 403 com o código "DEVTIME-1101"
E a interface não exibe os botões de encerrar e cancelar para esse papel
```

### AC-004-33 — `MEMBER` não vê valores monetários
```gherkin
Dado que estou autenticado com papel "MEMBER"
E que possuo vínculo com o contrato consultado
Quando eu envio GET /api/v1/contracts/{id}
Então recebo 200 OK
E a resposta não contém hourlyRate, overageRate nem estimatedValue
E contém as durações em minutos e o rótulo HH:MM
E a omissão ocorre no backend, não apenas na interface
```

### AC-004-34 — `status` não muda por `PATCH`
```gherkin
Dado um contrato em "DRAFT"
Quando eu envio PATCH /api/v1/contracts/{id} incluindo um campo "status" com valor "ACTIVE"
Então o campo é ignorado
E o contrato permanece "DRAFT"
E nenhum período é criado
E a mudança de estado só ocorre pelos endpoints de ação dedicados
```

### AC-004-35 — Job respeita o escopo de tenant
```gherkin
Dado dois tenants, cada um com contratos ativos
Quando o GeneratePeriodsJob é executado
Então os períodos de cada tenant são criados com o tenant_id correto
E nenhum período é criado em tenant incorreto
E o TenantContext é definido a cada iteração
E uma falha ao processar o tenant A não impede o processamento do tenant B
```

---

## 7. Cenários de concorrência

### AC-004-36 — Duas ativações simultâneas
```gherkin
Dado um contrato em "DRAFT"
Quando duas requisições de ativação chegam simultaneamente
Então exatamente uma recebe 200 OK
E a outra recebe 409 com o código "DEVTIME-2010"
E existe exatamente um ContractPeriod com sequence 1
E o índice único (contract_id, sequence) impediu a duplicata
```

### AC-004-37 — Job em duas instâncias
```gherkin
Dado duas instâncias da aplicação com o perfil scheduler ativo
Quando o GeneratePeriodsJob dispara no mesmo horário em ambas
Então apenas uma instância executa o job, garantido pelo lock distribuído
E cada período é criado exatamente uma vez
E a reexecução manual do job não cria períodos duplicados
```

### AC-004-38 — Sobreposição bloqueada pelo banco
```gherkin
Dado um contrato com um período de 01/01 a 31/01
Quando um INSERT direto no banco tenta criar um período de 15/01 a 15/02 no mesmo contrato,
      contornando a camada de aplicação
Então a operação é rejeitada pela constraint de exclusão
E nenhuma sobreposição existe na tabela
E o mesmo ocorre para um período totalmente contido em outro
```

### AC-004-39 — Edição concorrente de contrato
```gherkin
Dado que dois usuários carregaram o mesmo contrato com version igual a 3
Quando ambos enviam PATCH com version igual a 3
Então exatamente um recebe 200 OK e a version passa a 4
E o outro recebe 409 com o código "DEVTIME-2004"
E nenhuma alteração é perdida silenciosamente
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-002 | AC-004-31 | ✅ |
| RN-004 | AC-004-39 | ✅ |
| RN-201 | AC-004-01, AC-004-11 | ✅ |
| RN-202 | AC-004-12, AC-004-20 | ✅ |
| RN-203 | AC-004-13, AC-004-24 | ✅ |
| RN-204 | AC-004-14 | ✅ |
| RN-205 | AC-004-10, AC-004-15 | ✅ |
| RN-206 | AC-004-16 | ✅ |
| RN-207 | AC-004-17 | ✅ |
| RN-208 | AC-004-18 | ✅ |
| RN-209 | AC-004-03, AC-004-36 | ✅ |
| RN-210 | AC-004-07 | ✅ |
| RN-211 | AC-004-02, AC-004-06, AC-004-23, AC-004-30 | ✅ |
| RN-212 | AC-004-04, AC-004-24, AC-004-30 | ✅ |
| RN-213 | AC-004-04, AC-004-37 | ✅ |
| RN-214 | AC-004-09, AC-004-25 | ✅ |
| RN-216 | AC-004-04, AC-004-27, AC-004-38 | ✅ |
| RN-217 | AC-004-06, AC-004-26, AC-004-28 | ✅ |
| INV-CTR-02 | AC-004-12 | ✅ |
| INV-CTR-03 | AC-004-20 | ✅ |
| INV-CTR-04 | AC-004-21 | ✅ |
| INV-CTR-05 | AC-004-14 | ✅ |
| INV-CTR-06 | AC-004-03 | ✅ |
| INV-CTR-07 | AC-004-16 | ✅ |
| INV-CTR-08 | AC-004-15 | ✅ |
| INV-PER-01 | AC-004-36 | ✅ |
| INV-PER-02 | AC-004-38 | ✅ |
| INV-PER-03 | AC-004-27, AC-004-30 | ✅ |
| INV-PER-07 | AC-004-05 | ✅ |
| ME-05 | AC-004-34 | ✅ |
| CE-06 | AC-004-29 | ✅ |
| CE-15 | AC-004-19 | ✅ |
| CE-ME-09 | AC-004-27 | ✅ |
| JB-04, JB-06 | AC-004-35 | ✅ |
| nota ³ permissions | AC-004-32 | ✅ |
