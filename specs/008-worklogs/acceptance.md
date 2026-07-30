# 008 — Work Logs · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-008-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

**Regras de escrita:**
- Um cenário verifica **um** comportamento.
- `Então` descreve resultado **observável**, nunca implementação.
- Todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.
- Todo cenário é executável sem conhecimento adicional.

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-008-01 | Feliz | Registro manual simples | RN-101, RN-110 |
| AC-008-02 | Feliz | Segundos são truncados | RN-010, RN-110 |
| AC-008-03 | Feliz | Registro com pausa | RN-111 |
| AC-008-04 | Feliz | Registro não faturável não consome saldo | RN-112 |
| AC-008-05 | Feliz | Arredondamento para baixo | RN-113 |
| AC-008-06 | Feliz | Sessão atravessando a meia-noite | RN-108 |
| AC-008-07 | Feliz | Sessões que se tocam exatamente | RN-102 |
| AC-008-08 | Feliz | Categoria pré-selecionada e `billable` herdado | RN-104, RN-112 |
| AC-008-09 | Feliz | Resolução automática do período | RN-107 |
| AC-008-10 | Feliz | Cópia imutável de contrato e cliente | RN-109 |
| AC-008-11 | Feliz | Lançamento em nome de outro membro | RN-106 |
| AC-008-12 | Feliz | Edição do próprio registro | RN-122, RN-123 |
| AC-008-13 | Feliz | Exclusão devolve saldo | RN-125 |
| AC-008-14 | Feliz | Mudança de data entre períodos abertos | RN-124 |
| AC-008-15 | Feliz | Validação prévia sem persistir | FA-01 |
| AC-008-16 | Feliz | Duplicação com novo horário | FA-14 |
| AC-008-17 | Feliz | Excedente com política `WARN` | RN-232 |
| AC-008-18 | Feliz | Resposta traz o saldo atualizado | §6.1 |
| AC-008-19 | Erro | Sobreposição contida | RN-102 |
| AC-008-20 | Erro | Sobreposição parcial | RN-102 |
| AC-008-21 | Erro | Sessão acima de 24 horas | RN-103 |
| AC-008-22 | Erro | `endedAt` menor ou igual a `startedAt` | RN-114 |
| AC-008-23 | Erro | Tempo líquido zero | RN-115 |
| AC-008-24 | Erro | Pausa maior que o tempo bruto | RN-116 |
| AC-008-25 | Erro | Fora da vigência do contrato | RN-117 |
| AC-008-26 | Erro | `endedAt` no futuro | RN-118 |
| AC-008-27 | Erro | Data futura não permitida | RN-119 |
| AC-008-28 | Erro | Fora da janela retroativa | RN-120 |
| AC-008-29 | Erro | Sem período para a data | RN-107 |
| AC-008-30 | Erro | Edição em período fechado | RN-121 |
| AC-008-31 | Erro | Mudança para período fechado | RN-124 |
| AC-008-32 | Erro | Descrição com 2 caracteres | RN-105 |
| AC-008-33 | Erro | Categoria inativa | RN-104 |
| AC-008-34 | Erro | Contrato encerrado | RN-306 |
| AC-008-35 | Erro | Excedente com política `BLOCK` | RN-231 |
| AC-008-36 | Erro | Alteração de `source` ou `timerId` | RN-126 |
| AC-008-37 | Erro | `MEMBER` lança em nome de outro | RN-106 |
| AC-008-38 | Extremo | Registro de 1 minuto | CE-08 |
| AC-008-39 | Extremo | Sobreposição de exatamente 1 minuto | CX-07 |
| AC-008-40 | Extremo | Dois usuários no mesmo ticket ao mesmo tempo | CE-07 |
| AC-008-41 | Extremo | `endedAt` dentro da tolerância de 2 minutos | CX-09 |
| AC-008-42 | Extremo | Sessão de exatamente 1.440 minutos | CX-11 |
| AC-008-43 | Extremo | Arredondamento que zera o líquido | CX-14 |
| AC-008-44 | Extremo | Descrição só com espaços | CX-16 |
| AC-008-45 | Extremo | Horário de verão — hora repetida | CE-03 |
| AC-008-46 | Extremo | Sessão atravessa a virada do período | CE-02 |
| AC-008-47 | Extremo | Não faturável não estoura o saldo | CX-21 |
| AC-008-48 | Extremo | `BLOCK` faltando 5 minutos não divide | RN-234 |
| AC-008-49 | Extremo | Contrato `HOURLY_OPEN` sem alerta | CE-10 |
| AC-008-50 | Extremo | Lançamento em período reaberto | CX-24 |
| AC-008-51 | Extremo | Exclusão abaixo de limiar notificado | CE-11 |
| AC-008-52 | Extremo | Edição excluindo o próprio id da sobreposição | CX-17 |
| AC-008-53 | Segurança | Registro de outro tenant retorna 404 | RN-002 |
| AC-008-54 | Segurança | `MEMBER` não vê registro de colega | §9 permissions |
| AC-008-55 | Segurança | Escopo aplicado na contagem e nos totais | SG-03 |
| AC-008-56 | Segurança | `contractId` forjado é ignorado | SG-06 |
| AC-008-57 | Segurança | `netMinutes` forjado é ignorado | SG-09 |
| AC-008-58 | Segurança | `source` forjado é ignorado | SG-07 |
| AC-008-59 | Segurança | Guarda de período fechado no service | SG-05 |
| AC-008-60 | Concorrência | Cem criações sobrepostas simultâneas | RN-102 |
| AC-008-61 | Concorrência | Registro durante fechamento de período | RN-121 |
| AC-008-62 | Concorrência | Registros simultâneos e `consumedMinutes` | RN-219 |
| AC-008-63 | Concorrência | Edição simultânea do mesmo registro | RN-004 |

---

## 3. Cenários felizes

### AC-008-01 — Registro manual simples
```gherkin
Dado um ticket de um contrato ACTIVE com período OPEN
E que estou autenticado com a permissão WORKLOG_CREATE
Quando eu envio POST /api/v1/work-logs com startedAt 09:00:00, endedAt 11:30:00,
      descrição válida, categoria ativa e billable verdadeiro
Então recebo 201 Created com o header Location
E grossMinutes é 150
E netMinutes é 150
E billableMinutes é 150
E source é "MANUAL"
E o spentMinutes do ticket aumenta em 150
E o consumedMinutes do período aumenta em 150
E um AuditLog com action "WORK_LOG_CREATED" foi gravado
```

### AC-008-02 — Segundos são truncados
```gherkin
Quando eu registro uma sessão de 09:00:00 a 11:30:59
Então grossMinutes é 150
E não 151
E o resultado é idêntico ao de uma sessão de 09:00:00 a 11:30:00
```

### AC-008-03 — Registro com pausa
```gherkin
Quando eu registro uma sessão de 09:00:00 a 12:00:00 com 25 minutos de pausa
Então grossMinutes é 180
E pausedMinutes é 25
E netMinutes é 155
E billableMinutes é 155
```

### AC-008-04 — Registro não faturável não consome saldo
```gherkin
Quando eu registro uma sessão de 14:00:00 a 15:00:00 com billable falso
Então recebo 201 Created
E netMinutes é 60
E billableMinutes é 0
E o consumedMinutes do período permanece inalterado
E o nonBillableMinutes do período aumenta em 60
```

### AC-008-05 — Arredondamento para baixo
```gherkin
Dado que o tenant possui roundingMinutes igual a 15
Quando eu registro uma sessão de 09:00:00 a 10:52:00
Então grossMinutes é 112
E netMinutes é 105
E nunca 120
E a interface exibe o valor bruto e o arredondado lado a lado
```

### AC-008-06 — Sessão atravessando a meia-noite
```gherkin
Quando eu registro uma sessão iniciada às 22:00 do dia 10 e encerrada às 01:30 do dia 11
Então recebo 201 Created
E um único registro é criado
E netMinutes é 210
E workDate é o dia 10
E nenhuma divisão automática entre dias ocorre
```

### AC-008-07 — Sessões que se tocam exatamente
```gherkin
Dado um registro meu de 09:00 a 11:00
Quando eu registro uma sessão de 11:00 a 12:00
Então recebo 201 Created
E ambos os registros coexistem
E nenhum erro de sobreposição é retornado
```

### AC-008-08 — Categoria pré-selecionada e `billable` herdado
```gherkin
Dado um ticket com defaultCategoryId apontando para uma categoria ativa
E que essa categoria possui billableByDefault igual a falso
Quando eu abro o formulário de registro para esse ticket
Então a categoria vem pré-selecionada
E o campo faturável vem desmarcado
Quando eu salvo sem alterar esses campos
Então o registro é criado com billable falso e billableMinutes 0
```

### AC-008-09 — Resolução automática do período
```gherkin
Dado um contrato com um período OPEN de 01/07 a 31/07
Quando eu registro horas com workDate 15/07
Então o contractPeriodId é resolvido automaticamente para esse período
E eu não informo o período em nenhum momento
```

### AC-008-10 — Cópia imutável de contrato e cliente
```gherkin
Dado um ticket pertencente ao contrato X do cliente Y
Quando eu registro horas nesse ticket
Então o work log é criado com contractId igual a X e clientId igual a Y
Quando o ticket é posteriormente movido para outro contrato
Então o contractId e o clientId do work log permanecem X e Y
```

### AC-008-11 — Lançamento em nome de outro membro
```gherkin
Dado que estou autenticado com o papel MANAGER
E um membro ACTIVE do tenant
Quando eu envio POST /api/v1/work-logs informando o userId desse membro
Então recebo 201 Created
E o work log é criado com userId do membro indicado
E a verificação de sobreposição foi feita contra os registros DESSE membro
E o AuditLog registra quem criou e para quem
```

### AC-008-12 — Edição do próprio registro
```gherkin
Dado um work log meu com version 0, em período OPEN
Quando eu envio PATCH alterando a descrição e a hora de término, com version 0
Então recebo 200 OK
E editCount passa a 1
E os minutos são recalculados
E o spentMinutes do ticket e o consumedMinutes do período são ajustados
E um AuditLog registra os valores anteriores de netMinutes
```

### AC-008-13 — Exclusão devolve saldo
```gherkin
Dado um work log de 150 minutos faturáveis em período OPEN
Quando eu envio DELETE /api/v1/work-logs/{id}
Então recebo 204 No Content
E o registro recebe deletedAt preenchido
E o spentMinutes do ticket diminui em 150
E o consumedMinutes do período diminui em 150
E o registro some de todas as consultas e relatórios
```

### AC-008-14 — Mudança de data entre períodos abertos
```gherkin
Dado dois períodos consecutivos ambos com status OPEN ou REOPENED
E um work log alocado no primeiro
Quando eu altero a workDate para uma data do segundo período
Então recebo 200 OK
E o contractPeriodId é atualizado para o segundo período
E o consumedMinutes do primeiro diminui e o do segundo aumenta
```

### AC-008-15 — Validação prévia sem persistir
```gherkin
Quando eu envio POST /api/v1/work-logs/validate com dados que causariam sobreposição
Então recebo 200 OK
E a resposta traz valid falso e a lista de conflitos
E a resposta traz o cálculo previsto e a prévia de saldo
E nenhum work log é persistido
E nenhum total de ticket ou de período é alterado
```

### AC-008-16 — Duplicação com novo horário
```gherkin
Dado um work log existente
Quando eu envio POST /api/v1/work-logs/{id}/duplicate informando um novo horário sem conflito
Então recebo 201 Created
E o novo registro copia ticket, categoria, descrição e faturável do original
E o novo registro possui o horário informado
E o original permanece inalterado
```

### AC-008-17 — Excedente com política `WARN`
```gherkin
Dado um contrato com overagePolicy igual a WARN e saldo restante de 30 minutos
Quando eu registro 120 minutos faturáveis
Então recebo 201 Created
E a resposta traz warnings contendo o código DEVTIME-2221
E o registro é persistido integralmente
E o overageMinutes do período passa a 90
E uma notificação CONTRACT_OVERAGE é gerada
```

### AC-008-18 — Resposta traz o saldo atualizado
```gherkin
Quando eu crio um work log com sucesso
Então a resposta 201 traz o saldo do período já atualizado
E availableMinutes, consumedMinutes e remainingMinutes refletem o registro recém-criado
E não é necessária uma segunda requisição para conhecer o saldo
```

---

## 4. Cenários de erro

### AC-008-19 — Sobreposição contida
```gherkin
Dado um registro meu de 09:00 a 11:00
Quando eu tento registrar uma sessão de 09:30 a 10:30
Então recebo 422 Unprocessable Entity com o código DEVTIME-2102
E a resposta identifica o registro conflitante
E nenhum registro é criado
```

### AC-008-20 — Sobreposição parcial
```gherkin
Dado um registro meu de 09:00 a 11:00
Quando eu tento registrar uma sessão de 10:00 a 12:00
Então recebo 422 Unprocessable Entity com o código DEVTIME-2102
E nenhum registro é criado
```

### AC-008-21 — Sessão acima de 24 horas
```gherkin
Quando eu tento registrar uma sessão das 08:00 do dia 10 às 09:00 do dia 11
Então recebo 422 Unprocessable Entity com o código DEVTIME-2103
E a mensagem indica que a sessão não pode ultrapassar 24 horas
```

### AC-008-22 — `endedAt` menor ou igual a `startedAt`
```gherkin
Quando eu tento registrar uma sessão com endedAt igual a startedAt
Então recebo 422 Unprocessable Entity com o código DEVTIME-2114
Quando eu tento registrar com endedAt anterior a startedAt
Então recebo 422 Unprocessable Entity com o código DEVTIME-2114
```

### AC-008-23 — Tempo líquido zero
```gherkin
Quando eu tento registrar uma sessão de 09:00 a 10:00 com 60 minutos de pausa
Então recebo 422 Unprocessable Entity
E o código retornado é DEVTIME-2116, verificado antes de DEVTIME-2115
E nenhum registro é criado
```

### AC-008-24 — Pausa maior que o tempo bruto
```gherkin
Quando eu tento registrar uma sessão de 60 minutos com 90 minutos de pausa
Então recebo 422 Unprocessable Entity com o código DEVTIME-2116
```

### AC-008-25 — Fora da vigência do contrato
```gherkin
Dado um contrato vigente de 01/03 a 31/12
Quando eu tento registrar horas com startedAt em 15/02
Então recebo 422 Unprocessable Entity com o código DEVTIME-2117
```

### AC-008-26 — `endedAt` no futuro
```gherkin
Quando eu tento registrar uma sessão cujo endedAt está 3 minutos no futuro
Então recebo 422 Unprocessable Entity com o código DEVTIME-2118
```

### AC-008-27 — Data futura não permitida
```gherkin
Dado que o tenant possui allowFutureWorkLogs igual a falso
Quando eu tento registrar horas com workDate de amanhã
Então recebo 422 Unprocessable Entity com o código DEVTIME-2119
```

### AC-008-28 — Fora da janela retroativa
```gherkin
Dado que o tenant possui retroactiveLimitDays igual a 30
E que estou autenticado com o papel MEMBER
Quando eu tento registrar horas com workDate de 45 dias atrás
Então recebo 422 Unprocessable Entity com o código DEVTIME-2120
Mas um usuário ADMIN consegue registrar a mesma data com sucesso
```

### AC-008-29 — Sem período para a data
```gherkin
Dado um contrato cujo primeiro período começa em 01/07
Quando eu tento registrar horas com workDate em 20/06, dentro da vigência
Então recebo 422 Unprocessable Entity com o código DEVTIME-2107
E a mensagem indica o intervalo coberto pelos períodos existentes
```

### AC-008-30 — Edição em período fechado
```gherkin
Dado um work log com lockedAt preenchido
Quando eu tento editá-lo
Então recebo 409 Conflict com o código DEVTIME-2121
E a mensagem orienta a solicitar a reabertura do período
Quando eu tento excluí-lo
Então recebo 409 Conflict com o código DEVTIME-2121
```

### AC-008-31 — Mudança para período fechado
```gherkin
Dado um work log em período OPEN
E um período anterior com status CLOSED
Quando eu altero a workDate para uma data do período fechado
Então recebo 409 Conflict com o código DEVTIME-2124
E o registro permanece no período original
```

### AC-008-32 — Descrição com 2 caracteres
```gherkin
Quando eu tento registrar horas com a descrição "ab"
Então recebo 422 Unprocessable Entity com o código DEVTIME-2105
E a mensagem indica o mínimo de 3 caracteres
```

### AC-008-33 — Categoria inativa
```gherkin
Dado uma categoria com active igual a falso
Quando eu tento registrar horas informando explicitamente essa categoria
Então recebo 422 Unprocessable Entity com o código DEVTIME-2104
```

### AC-008-34 — Contrato encerrado
```gherkin
Dado um ticket cujo contrato está com status ENDED
Quando eu tento registrar horas nesse ticket
Então recebo 422 Unprocessable Entity com o código DEVTIME-2306
```

### AC-008-35 — Excedente com política `BLOCK`
```gherkin
Dado um contrato com overagePolicy igual a BLOCK e saldo restante de 30 minutos
Quando eu tento registrar 120 minutos faturáveis
Então recebo 422 Unprocessable Entity com o código DEVTIME-2220
E a resposta informa os minutos disponíveis
E nenhum registro é criado
E nenhum registro parcial de 30 minutos é criado
```

### AC-008-36 — Alteração de `source` ou `timerId`
```gherkin
Dado um work log com source igual a MANUAL
Quando eu envio PATCH incluindo source igual a TIMER e um timerId
Então os campos são ignorados ou rejeitados
E source permanece MANUAL e timerId permanece nulo
```

### AC-008-37 — `MEMBER` lança em nome de outro
```gherkin
Dado que estou autenticado com o papel MEMBER
Quando eu envio POST /api/v1/work-logs informando o userId de outro membro
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica WORKLOG_CREATE_FOR_OTHER
E nenhum registro é criado
```

---

## 5. Cenários extremos

### AC-008-38 — Registro de 1 minuto
```gherkin
Quando eu registro uma sessão de 09:00:00 a 09:01:00
Então recebo 201 Created
E netMinutes é 1
E o registro é válido, pois netMinutes maior que zero é o único critério
```

### AC-008-39 — Sobreposição de exatamente 1 minuto
```gherkin
Dado um registro meu de 09:00 a 11:00
Quando eu tento registrar uma sessão de 10:59 a 11:01
Então recebo 422 Unprocessable Entity com o código DEVTIME-2102
```

### AC-008-40 — Dois usuários no mesmo ticket ao mesmo tempo
```gherkin
Dado um registro do usuário A de 09:00 a 11:00 em um ticket
Quando o usuário B registra 09:00 a 11:00 no mesmo ticket
Então recebo 201 Created
E ambos os registros coexistem
E a regra de sobreposição restringe apenas por usuário
```

### AC-008-41 — `endedAt` dentro da tolerância de 2 minutos
```gherkin
Quando eu registro uma sessão cujo endedAt está 1 minuto no futuro
Então recebo 201 Created
E a tolerância de relógio é aplicada
Quando eu registro com endedAt 3 minutos no futuro
Então recebo 422 com o código DEVTIME-2118
```

### AC-008-42 — Sessão de exatamente 1.440 minutos
```gherkin
Quando eu registro uma sessão de exatamente 1.440 minutos
Então recebo 201 Created
Quando eu registro uma sessão de 1.441 minutos
Então recebo 422 com o código DEVTIME-2103
```

### AC-008-43 — Arredondamento que zera o líquido
```gherkin
Dado que o tenant possui roundingMinutes igual a 15
Quando eu tento registrar uma sessão de 10 minutos
Então o netMinutes arredondado resulta em 0
E recebo 422 Unprocessable Entity com o código DEVTIME-2115
E a interface havia alertado sobre o efeito do arredondamento antes do envio
```

### AC-008-44 — Descrição só com espaços
```gherkin
Quando eu tento registrar horas com a descrição "     "
Então recebo 422 Unprocessable Entity com o código DEVTIME-2105
E a validação foi aplicada após aparar as bordas
```

### AC-008-45 — Horário de verão — hora repetida
```gherkin
Dado um tenant em fuso com horário de verão
Quando eu registro uma sessão que atravessa a hora repetida da transição
Então recebo 201 Created
E a duração calculada corresponde ao tempo real decorrido
E nenhuma ambiguidade ocorre, pois os instantes são persistidos em UTC
```

### AC-008-46 — Sessão atravessa a virada do período
```gherkin
Dado dois períodos consecutivos, o primeiro terminando em 31/07
Quando eu registro uma sessão iniciada às 23:00 de 31/07 e encerrada às 01:00 de 01/08
Então o registro é alocado integralmente ao período de julho
E workDate é 31/07
E nenhuma divisão entre períodos ocorre
```

### AC-008-47 — Não faturável não estoura o saldo
```gherkin
Dado um contrato com overagePolicy BLOCK e saldo restante de 10 minutos
Quando eu registro 300 minutos com billable falso
Então recebo 201 Created
E nenhum erro DEVTIME-2220 é retornado
E o consumedMinutes permanece inalterado
```

### AC-008-48 — `BLOCK` faltando 5 minutos não divide
```gherkin
Dado um contrato com overagePolicy BLOCK e saldo restante de 55 minutos
Quando eu tento registrar 60 minutos faturáveis
Então recebo 422 com o código DEVTIME-2220
E nenhum registro de 55 minutos é criado automaticamente
E a mensagem orienta a reduzir o tempo, marcar como não faturável ou solicitar ajuste
```

### AC-008-49 — Contrato `HOURLY_OPEN` sem alerta
```gherkin
Dado um contrato do tipo HOURLY_OPEN
Quando eu registro 600 minutos faturáveis
Então recebo 201 Created sem nenhum aviso
E consumptionRate do período permanece 0
E nenhuma notificação de limiar é gerada
```

### AC-008-50 — Lançamento em período reaberto
```gherkin
Dado um período com status REOPENED
Quando eu registro horas com workDate dentro desse período
Então recebo 201 Created
E o registro é alocado normalmente
E REOPENED aceita registros da mesma forma que OPEN
```

### AC-008-51 — Exclusão abaixo de limiar notificado
```gherkin
Dado um período cujo consumo ultrapassou 80% e gerou notificação
Quando eu excluo registros até o consumo cair para 70%
Então a notificação anterior permanece no histórico e não é removida
Quando o consumo volta a ultrapassar 80%
Então nenhuma notificação nova é gerada, pois o dedupeKey já existe
```

### AC-008-52 — Edição excluindo o próprio id da sobreposição
```gherkin
Dado um registro meu de 09:00 a 11:00
Quando eu edito esse mesmo registro alterando o término para 11:30
Então recebo 200 OK
E nenhum erro de sobreposição contra si mesmo é retornado
```

---

## 6. Cenários de segurança

### AC-008-53 — Registro de outro tenant retorna 404
```gherkin
Dado um work log pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu envio GET, PATCH ou DELETE nesse registro
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-008-54 — `MEMBER` não vê registro de colega
```gherkin
Dado que estou autenticado com o papel MEMBER
E um work log pertencente a outro membro do mesmo tenant
Quando eu envio GET /api/v1/work-logs/{id} desse registro
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
Quando eu listo os registros
Então apenas os meus aparecem
```

### AC-008-55 — Escopo aplicado na contagem e nos totais
```gherkin
Dado 100 work logs no tenant, dos quais 20 são meus
E que estou autenticado com o papel MEMBER
Quando eu envio GET /api/v1/work-logs
Então o total de elementos informado na paginação é 20
E não 100
Quando eu envio GET /api/v1/work-logs/totals
Então os totais consideram apenas os meus 20 registros
E o filtro está presente na consulta ao banco, não em memória
```

### AC-008-56 — `contractId` forjado é ignorado
```gherkin
Quando eu envio POST /api/v1/work-logs incluindo contractId e clientId de outro contrato
Então os campos são ignorados
E o registro é criado com o contractId e o clientId copiados do ticket informado
```

### AC-008-57 — `netMinutes` forjado é ignorado
```gherkin
Quando eu envio POST /api/v1/work-logs incluindo netMinutes igual a 9999
Então o campo é ignorado
E netMinutes é calculado a partir de startedAt, endedAt e pausedMinutes
```

### AC-008-58 — `source` forjado é ignorado
```gherkin
Quando eu envio POST /api/v1/work-logs incluindo source igual a TIMER e um timerId arbitrário
Então os campos são ignorados
E o registro é criado com source MANUAL e timerId nulo
E a métrica de percentual de horas via cronômetro permanece confiável
```

### AC-008-59 — Guarda de período fechado no service
```gherkin
Dado um work log com lockedAt preenchido
Quando a edição é solicitada por qualquer caminho, inclusive por chamada interna de serviço
Então a operação é rejeitada com DEVTIME-2121
E a verificação ocorre na camada de serviço, não apenas no controller
```

---

## 7. Cenários de concorrência

### AC-008-60 — Cem criações sobrepostas simultâneas
```gherkin
Dado nenhum work log existente para o usuário
Quando 100 requisições simultâneas tentam criar registros com intervalos mutuamente sobrepostos
Então no máximo um registro é persistido
E as demais recebem 422 com o código DEVTIME-2102
E nenhuma sobreposição permanece na base
E o job de consistência não encontra nenhuma violação de INV-WKL-05
```

### AC-008-61 — Registro durante fechamento de período
```gherkin
Dado um período OPEN
Quando uma requisição cria um work log e outra fecha o período, em paralelo
Então ou o registro é criado e depois travado pelo fechamento
Ou o fechamento conclui primeiro e a criação falha com DEVTIME-2121
E nunca existe um work log sem lockedAt em um período CLOSED
```

### AC-008-62 — Registros simultâneos e `consumedMinutes`
```gherkin
Dado um período com consumedMinutes igual a 0
Quando 20 work logs de 30 minutos faturáveis são criados simultaneamente por usuários distintos
Então consumedMinutes final é 600
E nenhuma atualização é perdida, pois o incremento ocorre no banco
Ou, havendo divergência, o DenormalizationReconcileJob restaura o valor 600
```

### AC-008-63 — Edição simultânea do mesmo registro
```gherkin
Dado um work log com version 3
Quando duas requisições simultâneas o editam, ambas informando version 3
Então exatamente uma recebe 200 OK
E a outra recebe 409 Conflict com o código DEVTIME-2004
E os totais do ticket e do período refletem apenas a edição aplicada
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-101 | AC-008-01 | ✅ |
| RN-102 | AC-008-07, AC-008-19, AC-008-20, AC-008-39, AC-008-40, AC-008-52, AC-008-60 | ✅ |
| RN-103 | AC-008-21, AC-008-42 | ✅ |
| RN-104 | AC-008-08, AC-008-33 | ✅ |
| RN-105 | AC-008-32, AC-008-44 | ✅ |
| RN-106 | AC-008-11, AC-008-37 | ✅ |
| RN-107 | AC-008-09, AC-008-29 | ✅ |
| RN-108 | AC-008-06, AC-008-46 | ✅ |
| RN-109 | AC-008-10, AC-008-56 | ✅ |
| RN-110 | AC-008-01, AC-008-02 | ✅ |
| RN-111 | AC-008-03 | ✅ |
| RN-112 | AC-008-04, AC-008-08, AC-008-47 | ✅ |
| RN-113 | AC-008-05, AC-008-43 | ✅ |
| RN-114 | AC-008-22 | ✅ |
| RN-115 | AC-008-23, AC-008-38, AC-008-43 | ✅ |
| RN-116 | AC-008-23, AC-008-24 | ✅ |
| RN-117 | AC-008-25 | ✅ |
| RN-118 | AC-008-26, AC-008-41 | ✅ |
| RN-119 | AC-008-27 | ✅ |
| RN-120 | AC-008-28 | ✅ |
| RN-121 | AC-008-30, AC-008-59, AC-008-61 | ✅ |
| RN-122 | AC-008-12 | ✅ |
| RN-123 | AC-008-12 | ✅ |
| RN-124 | AC-008-14, AC-008-31 | ✅ |
| RN-125 | AC-008-13 | ✅ |
| RN-126 | AC-008-36, AC-008-58 | ✅ |
| RN-231 | AC-008-35, AC-008-48 | ✅ |
| RN-232 | AC-008-17 | ✅ |
| RN-234 | AC-008-48 | ✅ |
| RN-306 | AC-008-34 | ✅ |
| RN-009 | AC-008-45, AC-008-46 | ✅ |
| RN-010 | AC-008-02 | ✅ |
| RN-003 | AC-008-13 | ✅ |
| RN-004 | AC-008-63 | ✅ |
| RN-001 / RN-002 | AC-008-53, AC-008-56 | ✅ |
| RN-006 | AC-008-01, AC-008-12, AC-008-13 | ✅ |
| INV-WKL-01 a 04 | AC-008-22, AC-008-23, AC-008-24, AC-008-42 | ✅ |
| INV-WKL-05 | AC-008-19, AC-008-60 | ✅ |
| INV-WKL-06 | AC-008-10 | ✅ |
| INV-WKL-07 | AC-008-30 | ✅ |
| INV-WKL-08 | AC-008-09, AC-008-46 | ✅ |
| INV-WKL-09 | AC-008-58 | ✅ |
| §9 permissions | AC-008-54, AC-008-55 | ✅ |
| CE-02 / CE-03 / CE-07 / CE-08 / CE-10 / CE-11 | AC-008-40, AC-008-38, AC-008-45, AC-008-46, AC-008-49, AC-008-51 | ✅ |
| SG-03 / SG-05 / SG-06 / SG-07 / SG-09 | AC-008-55 a AC-008-59 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. Os 9 casos da tabela normativa de sobreposição (§6.2) e os 8 da tabela de cálculo (§6.3) são cobertos exaustivamente pelas suítes parametrizadas `TS-008-01` e `TS-008-05` de `tests.md`.
