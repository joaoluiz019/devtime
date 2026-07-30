# 011 — Bank Hours · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-011-XX`, estável e imutável |
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
| AC-011-01 | Feliz | Cálculo do exemplo normativo | RN-218 a RN-222 |
| AC-011-02 | Feliz | Horas não faturáveis fora do saldo | RN-223 |
| AC-011-03 | Feliz | Extrato explicativo soma ao saldo | §10 contracts.md |
| AC-011-04 | Feliz | Ajuste de crédito | RN-215 |
| AC-011-05 | Feliz | Ajuste de débito | RN-237 |
| AC-011-06 | Feliz | Estorno de ajuste | RN-236 |
| AC-011-07 | Feliz | Carry-over `NONE` | RN-225 |
| AC-011-08 | Feliz | Carry-over `FULL` | RN-226 |
| AC-011-09 | Feliz | Carry-over `CAPPED` limitado pelo teto | RN-227 |
| AC-011-10 | Feliz | Carry-over `CAPPED` abaixo do teto | RN-227 |
| AC-011-11 | Feliz | Fechamento completo em 7 passos | RN-241 |
| AC-011-12 | Feliz | Propagação de `carriedOut` | RN-229 |
| AC-011-13 | Feliz | Reabertura preserva o snapshot | RN-243 |
| AC-011-14 | Feliz | Refechamento gera segundo snapshot | CX-18 |
| AC-011-15 | Feliz | Relatório de período fechado vem do snapshot | RN-701 |
| AC-011-16 | Feliz | Projeção de consumo | §6.7 entities.md |
| AC-011-17 | Erro | Ajuste com justificativa curta | RN-215 |
| AC-011-18 | Erro | Ajuste deixando saldo negativo | RN-237 |
| AC-011-19 | Erro | Ajuste em período fechado | RN-235 |
| AC-011-20 | Erro | Tentativa de editar ajuste | RN-236 |
| AC-011-21 | Erro | Fechamento antes do `endDate` sem confirmação | RN-239 |
| AC-011-22 | Erro | Fechamento com timer ativo | RN-240 |
| AC-011-23 | Erro | Reabertura sem justificativa | RN-242 |
| AC-011-24 | Erro | Reabertura com posterior fechado | RN-244 |
| AC-011-25 | Erro | Edição de work log em período fechado | RN-121 |
| AC-011-26 | Erro | `MANAGER` tenta fechar | RN-238 |
| AC-011-27 | Extremo | `available` zero e `consumed` zero | CX-01 |
| AC-011-28 | Extremo | `available` zero e `consumed` positivo | CX-02 |
| AC-011-29 | Extremo | Contrato `HOURLY_OPEN` | CX-03 |
| AC-011-30 | Extremo | Saldo negativo não transporta | RN-228 |
| AC-011-31 | Extremo | `CAPPED` com teto zero | CX-05 |
| AC-011-32 | Extremo | Ajuste que deixa disponível exatamente zero | CX-08 |
| AC-011-33 | Extremo | Justificativa com exatamente 10 caracteres | CX-09 |
| AC-011-34 | Extremo | Fechamento com desnormalizado divergente | CX-10, FA-16 |
| AC-011-35 | Extremo | Fechamento sem nenhum work log | CX-11 |
| AC-011-36 | Extremo | Fechamento do último período do contrato | CX-12 |
| AC-011-37 | Extremo | Fechamento com excedente | RN-245 |
| AC-011-38 | Extremo | Período preso em `CLOSING` | CE-ME-07 |
| AC-011-39 | Extremo | Reabertura em cascata de três períodos | CE-ME-03 |
| AC-011-40 | Extremo | Ajuste zerando exatamente o excedente | CE-14 |
| AC-011-41 | Extremo | Expiração de carry-over | RN-230 |
| AC-011-42 | Extremo | `rolloverExpiryPeriods` igual a zero | CX-20 |
| AC-011-43 | Segurança | Período de outro tenant retorna 404 | RN-002 |
| AC-011-44 | Segurança | Nenhuma rota altera ajuste ou snapshot | RN-236, INV-SNP-01 |
| AC-011-45 | Segurança | Checksum divergente é detectado, não corrigido | CX-21 |
| AC-011-46 | Segurança | `consumedMinutes` forjado é ignorado | SG-06 |
| AC-011-47 | Segurança | `MEMBER` não vê valores monetários | SG-09 |
| AC-011-48 | Concorrência | Dois fechamentos simultâneos | CE-ME-08 |
| AC-011-49 | Concorrência | Work log durante o fechamento | RN-121 |
| AC-011-50 | Concorrência | Ajustes simultâneos e saldo | RN-218 |
| AC-011-51 | Concorrência | Falha no passo 4 do fechamento | RN-241 |

---

## 3. Cenários felizes

### AC-011-01 — Cálculo do exemplo normativo
```gherkin
Dado um período com contractedMinutes 2400, carriedInMinutes 300 e adjustmentMinutes 60
E consumedMinutes igual a 2900
Quando eu consulto o saldo do período
Então availableMinutes é 2760
E remainingMinutes é -140
E overageMinutes é 140
E consumptionRate é 105,07
E todos os valores de minutos são inteiros
```

### AC-011-02 — Horas não faturáveis fora do saldo
```gherkin
Dado um período com availableMinutes 2400 e consumedMinutes 0
Quando um work log de 300 minutos com billable falso é registrado no período
Então consumedMinutes permanece 0
E nonBillableMinutes passa a 300
E remainingMinutes permanece 2400
E o work log aparece no extrato marcado como não faturável
```

### AC-011-03 — Extrato explicativo soma ao saldo
```gherkin
Dado um período com contratado, transportado, dois ajustes e dez work logs
Quando eu consulto o extrato do período
Então cada lançamento é exibido em ordem cronológica com o saldo acumulado
E a soma dos créditos menos a soma dos débitos é igual ao remainingMinutes exibido
E cada work log exibe data, ticket, categoria, autor e minutos
E cada ajuste exibe autor, motivo e justificativa
```

### AC-011-04 — Ajuste de crédito
```gherkin
Dado um período OPEN com availableMinutes 2400
E que estou autenticado com a permissão PERIOD_ADJUST
Quando eu envio POST com minutes 120, reason "NEGOTIATED_EXTRA" e uma justificativa de 30 caracteres
Então recebo 201 Created
E adjustmentMinutes passa a 120
E availableMinutes passa a 2520
E um AuditLog com action "PERIOD_ADJUSTMENT_APPLIED" registra a justificativa
```

### AC-011-05 — Ajuste de débito
```gherkin
Dado um período OPEN com availableMinutes 2400 e adjustmentMinutes 0
Quando eu aplico um ajuste de -300 minutos com justificativa válida
Então recebo 201 Created
E adjustmentMinutes passa a -300
E availableMinutes passa a 2100
```

### AC-011-06 — Estorno de ajuste
```gherkin
Dado um ajuste de +120 minutos aplicado por engano
Quando eu aplico um novo ajuste de -120 minutos com justificativa de estorno
Então recebo 201 Created
E adjustmentMinutes volta ao valor anterior
E ambos os ajustes permanecem visíveis no extrato
E o ajuste original não foi alterado nem removido
```

### AC-011-07 — Carry-over `NONE`
```gherkin
Dado um contrato com rolloverPolicy NONE
E um período com availableMinutes 2400 e consumedMinutes 1800
Quando o período é fechado
Então carriedOutMinutes é 0
E o saldo de 600 minutos é perdido
E o período seguinte recebe carriedInMinutes igual a 0
```

### AC-011-08 — Carry-over `FULL`
```gherkin
Dado um contrato com rolloverPolicy FULL
E um período com availableMinutes 2400 e consumedMinutes 1800
Quando o período é fechado
Então carriedOutMinutes é 600
E o período seguinte recebe carriedInMinutes igual a 600
```

### AC-011-09 — Carry-over `CAPPED` limitado pelo teto
```gherkin
Dado um contrato com rolloverPolicy CAPPED e rolloverCapMinutes 300
E um período com availableMinutes 2400 e consumedMinutes 1800
Quando o período é fechado
Então carriedOutMinutes é 300
E não 600
```

### AC-011-10 — Carry-over `CAPPED` abaixo do teto
```gherkin
Dado um contrato com rolloverPolicy CAPPED e rolloverCapMinutes 300
E um período com availableMinutes 2400 e consumedMinutes 2250
Quando o período é fechado
Então carriedOutMinutes é 150
E o teto não é aplicado, pois o restante é menor que ele
```

### AC-011-11 — Fechamento completo em 7 passos
```gherkin
Dado um período OPEN cujo endDate já passou, sem nenhum cronômetro ativo
E que estou autenticado com a permissão PERIOD_CLOSE
Quando eu envio POST /api/v1/contract-periods/{id}/close
Então recebo 200 OK
E consumedMinutes foi reconciliado por agregação real dos work logs
E carriedOutMinutes foi calculado conforme a política do contrato
E todos os work logs do período receberam lockedAt preenchido
E um PeriodSnapshot foi criado com checksum SHA-256
E o status passou a CLOSED com closedAt e closedBy preenchidos
E o período seguinte recebeu carriedInMinutes
E uma notificação PERIOD_CLOSED foi criada
E a resposta traz o resumo com a diferença de reconciliação e o checksum
```

### AC-011-12 — Propagação de `carriedOut`
```gherkin
Dado dois períodos consecutivos do mesmo contrato
Quando o primeiro é fechado com carriedOutMinutes 400
Então o carriedInMinutes do segundo passa a 400
E o availableMinutes do segundo é recalculado incluindo esse valor
```

### AC-011-13 — Reabertura preserva o snapshot
```gherkin
Dado um período CLOSED com um snapshot gerado
E que estou autenticado com a permissão PERIOD_REOPEN
Quando eu envio POST /api/v1/contract-periods/{id}/reopen com uma justificativa de 20 caracteres
Então recebo 200 OK
E o status passa a REOPENED
E reopenCount é incrementado
E o lockedAt de todos os work logs do período é limpo
E o snapshot anterior permanece existindo e inalterado
E um AuditLog registra a justificativa e quem reabriu
```

### AC-011-14 — Refechamento gera segundo snapshot
```gherkin
Dado um período REOPENED que já possui um snapshot
Quando eu o fecho novamente
Então um novo PeriodSnapshot é criado com snapshotAt distinto
E o snapshot anterior permanece existindo
E o carriedOutMinutes é recalculado
E o carriedInMinutes do período seguinte é atualizado
```

### AC-011-15 — Relatório de período fechado vem do snapshot
```gherkin
Dado um período CLOSED com snapshot
Quando eu consulto o saldo ou o relatório desse período
Então os valores retornados vêm do snapshot
E não são recalculados a partir do estado atual do banco
E o resultado é marcado como definitivo, não parcial
```

### AC-011-16 — Projeção de consumo
```gherkin
Dado um período de 30 dias com 10 dias decorridos e 800 minutos consumidos
Quando eu consulto a projeção do período
Então burnRate reflete o consumo por dia útil decorrido
E projectedConsumption estima o consumo até o fim do período
E os valores são exibidos apenas para períodos abertos
```

---

## 4. Cenários de erro

### AC-011-17 — Ajuste com justificativa curta
```gherkin
Quando eu aplico um ajuste com justificativa de 9 caracteres
Então recebo 422 Unprocessable Entity com o código DEVTIME-2215
E nenhum ajuste é criado
E o saldo permanece inalterado
```

### AC-011-18 — Ajuste deixando saldo negativo
```gherkin
Dado um período com availableMinutes 500
Quando eu aplico um ajuste de -600 minutos com justificativa válida
Então recebo 422 Unprocessable Entity com o código DEVTIME-2237
E nenhum ajuste é criado
E availableMinutes permanece 500
```

### AC-011-19 — Ajuste em período fechado
```gherkin
Dado um período com status CLOSED
Quando eu tento aplicar um ajuste
Então recebo 409 Conflict com o código DEVTIME-2235
E a mensagem indica que o ajuste só é permitido em período aberto
```

### AC-011-20 — Tentativa de editar ajuste
```gherkin
Dado um ajuste existente
Quando eu tento alterá-lo ou excluí-lo por qualquer rota da API
Então nenhuma rota de alteração ou exclusão de ajuste existe
E a tentativa resulta em 404 ou 405
E a única correção possível é um novo ajuste de sinal contrário
```

### AC-011-21 — Fechamento antes do `endDate` sem confirmação
```gherkin
Dado um período OPEN cujo endDate ainda não chegou
Quando eu envio o fechamento sem o campo confirmed
Então recebo 409 Conflict com o código DEVTIME-2239
E o período permanece OPEN
Quando eu envio novamente com confirmed verdadeiro
Então o fechamento é executado
```

### AC-011-22 — Fechamento com timer ativo
```gherkin
Dado um período OPEN com um cronômetro RUNNING em um ticket do contrato
Quando eu envio o fechamento
Então recebo 409 Conflict com o código DEVTIME-2240
E a resposta lista os cronômetros ativos
E o período permanece OPEN
E nenhum work log foi travado
```

### AC-011-23 — Reabertura sem justificativa
```gherkin
Dado um período CLOSED
Quando eu envio a reabertura sem informar o motivo
Então recebo 422 Unprocessable Entity
E o período permanece CLOSED
E nenhum work log é destravado
```

### AC-011-24 — Reabertura com posterior fechado
```gherkin
Dado três períodos consecutivos, todos CLOSED
Quando eu tento reabrir o primeiro
Então recebo 409 Conflict com o código DEVTIME-2244
E a mensagem indica qual período deve ser reaberto primeiro
E nenhum período é alterado
```

### AC-011-25 — Edição de work log em período fechado
```gherkin
Dado um período CLOSED com work logs travados
Quando eu tento editar ou excluir qualquer um deles
Então recebo 409 Conflict com o código DEVTIME-2121
E a mensagem orienta a solicitar a reabertura do período
```

### AC-011-26 — `MANAGER` tenta fechar
```gherkin
Dado que estou autenticado com o papel MANAGER
Quando eu tento fechar um período ou aplicar um ajuste
Então recebo 403 Forbidden com o código DEVTIME-1101 em ambos os casos
E o campo requiredPermission indica PERIOD_CLOSE ou PERIOD_ADJUST
Mas eu consigo consultar o período e o extrato normalmente
```

---

## 5. Cenários extremos

### AC-011-27 — `available` zero e `consumed` zero
```gherkin
Dado um período com availableMinutes 0 e consumedMinutes 0
Quando eu consulto o saldo
Então consumptionRate é 0
E nenhuma divisão por zero ocorre
```

### AC-011-28 — `available` zero e `consumed` positivo
```gherkin
Dado um período com availableMinutes 0 e consumedMinutes 300
Quando eu consulto o saldo
Então consumptionRate é 100
E overageMinutes é 300
E nenhuma divisão por zero ocorre
```

### AC-011-29 — Contrato `HOURLY_OPEN`
```gherkin
Dado um período de contrato do tipo HOURLY_OPEN
Quando work logs são registrados nele
Então availableMinutes permanece 0
E consumptionRate permanece 0
E nenhum alerta de limiar é gerado
E o carry-over não se aplica no fechamento
```

### AC-011-30 — Saldo negativo não transporta
```gherkin
Dado um contrato com rolloverPolicy FULL
E um período com availableMinutes 2400 e consumedMinutes 2900
Quando o período é fechado
Então carriedOutMinutes é 0
E não -500
E o período seguinte recebe carriedInMinutes igual a 0
E o excedente de 500 minutos é registrado no snapshot
```

### AC-011-31 — `CAPPED` com teto zero
```gherkin
Dado um contrato com rolloverPolicy CAPPED e rolloverCapMinutes 0
E um período com 600 minutos restantes
Quando o período é fechado
Então carriedOutMinutes é 0
E o comportamento equivale ao da política NONE
```

### AC-011-32 — Ajuste que deixa disponível exatamente zero
```gherkin
Dado um período com availableMinutes 500
Quando eu aplico um ajuste de -500 minutos com justificativa válida
Então recebo 201 Created
E availableMinutes passa a 0
E a regra proíbe negativo, não zero
```

### AC-011-33 — Justificativa com exatamente 10 caracteres
```gherkin
Quando eu aplico um ajuste com justificativa de exatamente 10 caracteres
Então recebo 201 Created
Quando eu aplico outro com 9 caracteres
Então recebo 422 com o código DEVTIME-2215
```

### AC-011-34 — Fechamento com desnormalizado divergente
```gherkin
Dado um período cujo consumedMinutes persistido é 2000
E cuja soma real dos work logs é 2150
Quando o período é fechado
Então o consumedMinutes é reconciliado para 2150
E o snapshot é gerado com o valor real de 2150
E a resposta informa a diferença de reconciliação de 150
E um AuditLog com action "PERIOD_CONSUMPTION_RECONCILED" registra a diferença
E um alerta de métrica é disparado
```

### AC-011-35 — Fechamento sem nenhum work log
```gherkin
Dado um período OPEN sem nenhum work log registrado
Quando eu o fecho
Então recebo 200 OK
E o snapshot é gerado com lista de work logs vazia
E consumedMinutes é 0
E carriedOutMinutes é calculado normalmente conforme a política
```

### AC-011-36 — Fechamento do último período do contrato
```gherkin
Dado um período OPEN que é o último gerado do contrato
E uma política de rollover que produz carriedOut maior que zero
Quando eu o fecho
Então um período seguinte é criado para receber o carriedInMinutes
E o valor transportado não é perdido
```

### AC-011-37 — Fechamento com excedente
```gherkin
Dado um período com consumedMinutes maior que availableMinutes
Quando eu o fecho
Então o fechamento é executado normalmente
E o overageMinutes é registrado no snapshot
E carriedOutMinutes é 0
E o relatório do período exibe o excedente
```

### AC-011-38 — Período preso em `CLOSING`
```gherkin
Dado um período que ficou com status CLOSING por falha de infraestrutura
Quando 10 minutos se passam e o job de reconciliação é executado
Então o período volta ao status OPEN
E um alerta operacional é disparado
E um AuditLog com actorType SYSTEM registra a reversão e o tempo preso
E nenhum snapshot parcial permanece
```

### AC-011-39 — Reabertura em cascata de três períodos
```gherkin
Dado três períodos consecutivos, todos CLOSED
Quando eu reabro o terceiro, depois o segundo e depois o primeiro
Então as três reaberturas são aceitas nessa ordem
E a cada refechamento o carriedIn do período seguinte é recalculado
Quando eu tento reabrir fora dessa ordem
Então recebo 409 com o código DEVTIME-2244
```

### AC-011-40 — Ajuste zerando exatamente o excedente
```gherkin
Dado um período com overageMinutes igual a 140 e uma notificação de excedente já gerada
Quando eu aplico um ajuste de +140 minutos
Então overageMinutes passa a 0
E a notificação anterior permanece no histórico
E nenhuma notificação é removida
```

### AC-011-41 — Expiração de carry-over
```gherkin
Dado um período com carriedInMinutes 300 e um contrato com rolloverExpiryPeriods 1
Quando o número de períodos configurado é ultrapassado e o job é executado
Então um ajuste automático de -300 minutos é criado
E o reason é OTHER
E a justificativa é "Expiração de saldo transportado"
E o ajuste é aplicado apenas se o período estiver aberto
```

### AC-011-42 — `rolloverExpiryPeriods` igual a zero
```gherkin
Dado um contrato com rolloverExpiryPeriods igual a 0
Quando o job de expiração é executado repetidamente ao longo de vários períodos
Então nenhum ajuste de expiração é criado
E o saldo transportado nunca expira
```

---

## 6. Cenários de segurança

### AC-011-43 — Período de outro tenant retorna 404
```gherkin
Dado um período pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu consulto, ajusto, fecho ou reabro esse período
Então recebo 404 Not Found com o código DEVTIME-2002 em todos os casos
E nunca recebo 403
```

### AC-011-44 — Nenhuma rota altera ajuste ou snapshot
```gherkin
Quando eu inspeciono todas as rotas expostas pela aplicação
Então não existe nenhuma rota PATCH, PUT ou DELETE para ajustes
E não existe nenhuma rota de escrita para snapshots
E os repositórios correspondentes não expõem métodos de atualização ou exclusão
```

### AC-011-45 — Checksum divergente é detectado, não corrigido
```gherkin
Dado um snapshot cujo payload foi adulterado diretamente no banco
Quando o job de verificação de integridade é executado
Então uma divergência de checksum é detectada
E um log de nível ERROR é emitido com o identificador do período
E um alerta operacional é disparado
E o snapshot não é corrigido nem regenerado automaticamente
```

### AC-011-46 — `consumedMinutes` forjado é ignorado
```gherkin
Quando eu envio qualquer requisição incluindo consumedMinutes, availableMinutes ou carriedOutMinutes
Então os campos são ignorados
E os valores permanecem os calculados pelo sistema
```

### AC-011-47 — `MEMBER` não vê valores monetários
```gherkin
Dado que estou autenticado com o papel MEMBER e vinculado ao contrato
Quando eu consulto o período e o extrato
Então eu vejo os minutos contratados, consumidos e restantes
E nenhum valor monetário é retornado
E o campo estimatedValue está ausente da resposta
```

---

## 7. Cenários de concorrência

### AC-011-48 — Dois fechamentos simultâneos
```gherkin
Dado um período OPEN elegível para fechamento
Quando duas requisições de fechamento são processadas em paralelo
Então exatamente uma recebe 200 OK
E a outra recebe 409 Conflict
E exatamente um snapshot é criado
E os work logs são travados uma única vez
E o carriedIn do período seguinte é propagado uma única vez
```

### AC-011-49 — Work log durante o fechamento
```gherkin
Dado um período OPEN
Quando uma requisição fecha o período e outra registra um work log nele, em paralelo
Então ou o work log é criado antes e entra na reconciliação
Ou o fechamento conclui primeiro e a criação falha com DEVTIME-2121
E nunca existe um work log sem lockedAt em um período CLOSED
```

### AC-011-50 — Ajustes simultâneos e saldo
```gherkin
Dado um período com adjustmentMinutes igual a 0
Quando cinco ajustes de +60 minutos são aplicados simultaneamente
Então os cinco são persistidos
E adjustmentMinutes final é 300
E availableMinutes reflete os cinco ajustes
E nenhuma atualização é perdida
```

### AC-011-51 — Falha no passo 4 do fechamento
```gherkin
Dado um período OPEN elegível para fechamento
Quando uma falha ocorre durante a geração do snapshot, no passo 4
Então a transação inteira é revertida
E o status volta a OPEN
E nenhum work log permanece com lockedAt preenchido
E nenhum snapshot parcial é criado
E o carriedIn do período seguinte permanece inalterado
E o mesmo comportamento se aplica a falhas em qualquer um dos 7 passos
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-218 a RN-222 | AC-011-01, AC-011-27, AC-011-28, AC-011-50 | ✅ |
| RN-223 | AC-011-02 | ✅ |
| RN-224 | AC-011-07 a AC-011-10 | ✅ |
| RN-225 | AC-011-07, AC-011-31 | ✅ |
| RN-226 | AC-011-08 | ✅ |
| RN-227 | AC-011-09, AC-011-10, AC-011-31 | ✅ |
| RN-228 | AC-011-30 | ✅ |
| RN-229 | AC-011-12, AC-011-36 | ✅ |
| RN-230 | AC-011-41, AC-011-42 | ✅ |
| RN-215 | AC-011-04, AC-011-17, AC-011-33 | ✅ |
| RN-235 | AC-011-19 | ✅ |
| RN-236 | AC-011-06, AC-011-20, AC-011-44 | ✅ |
| RN-237 | AC-011-05, AC-011-18, AC-011-32 | ✅ |
| RN-238 | AC-011-26 | ✅ |
| RN-239 | AC-011-21 | ✅ |
| RN-240 | AC-011-22 | ✅ |
| RN-241 | AC-011-11, AC-011-48, AC-011-51 | ✅ |
| RN-242 | AC-011-13, AC-011-23 | ✅ |
| RN-243 | AC-011-13, AC-011-14 | ✅ |
| RN-244 | AC-011-24, AC-011-39 | ✅ |
| RN-245 | AC-011-30, AC-011-37 | ✅ |
| RN-121 | AC-011-25, AC-011-49 | ✅ |
| RN-701 / RN-702 | AC-011-15 | ✅ |
| RN-002 | AC-011-43 | ✅ |
| RN-006 | AC-011-04, AC-011-11, AC-011-13, AC-011-34 | ✅ |
| INV-PER-05 | AC-011-18 | ✅ |
| INV-PER-06 | AC-011-11 | ✅ |
| INV-PER-08 | AC-011-11, AC-011-51 | ✅ |
| INV-ADJ-01 | AC-011-06, AC-011-20 | ✅ |
| INV-SNP-01 | AC-011-13, AC-011-14, AC-011-45 | ✅ |
| CE-10 / CE-14 | AC-011-29, AC-011-40 | ✅ |
| CE-ME-03 / CE-ME-07 / CE-ME-08 | AC-011-39, AC-011-38, AC-011-48 | ✅ |
| SG-06 / SG-09 | AC-011-46, AC-011-47 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. RN-241 — a sequência atômica — é verificada em três cenários, incluindo falha em cada um dos 7 passos (`AC-011-51`).
