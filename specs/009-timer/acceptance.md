# 009 — Timer · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-009-XX`, estável e imutável |
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
| AC-009-01 | Feliz | Início do cronômetro | RN-152 |
| AC-009-02 | Feliz | Pausa | RN-154 |
| AC-009-03 | Feliz | Retomada | RN-156 |
| AC-009-04 | Feliz | Encerramento gera work log | RN-159 |
| AC-009-05 | Feliz | Exemplo normativo de cálculo | §6.2 |
| AC-009-06 | Feliz | Edição durante a execução | RN-161 |
| AC-009-07 | Feliz | Descarte confirmado | RN-162 |
| AC-009-08 | Feliz | Troca de tarefa atômica | RN-166 |
| AC-009-09 | Feliz | Recuperação de abandonado | RN-165 |
| AC-009-10 | Feliz | Estado sobrevive ao fechamento do navegador | RN-151 |
| AC-009-11 | Feliz | Encerramento forçado notifica o dono | OWN-05 |
| AC-009-12 | Feliz | Visão de cronômetros da equipe | `TIMER_VIEW_ANY` |
| AC-009-13 | Erro | Segundo cronômetro do mesmo usuário | RN-150 |
| AC-009-14 | Erro | Pausar cronômetro já pausado | RN-153 |
| AC-009-15 | Erro | Retomar cronômetro em execução | RN-155 |
| AC-009-16 | Erro | Encerramento sem descrição preserva o timer | RN-158, RN-160 |
| AC-009-17 | Erro | Encerramento com sobreposição preserva o timer | RN-102, RN-160 |
| AC-009-18 | Erro | Encerramento com saldo insuficiente preserva o timer | RN-231, RN-160 |
| AC-009-19 | Erro | Encerramento com contrato encerrado preserva o timer | RN-306, CE-12 |
| AC-009-20 | Erro | Descarte sem confirmação | RN-162 |
| AC-009-21 | Erro | Recuperação após 7 dias | RN-165 |
| AC-009-22 | Erro | Operação em cronômetro encerrado | §4.8 SM |
| AC-009-23 | Erro | `VIEWER` tenta iniciar | CE-P-06 |
| AC-009-24 | Extremo | Cronômetro em dois tenants | CE-13 |
| AC-009-25 | Extremo | Cinquenta pausas em uma sessão | RN-157 |
| AC-009-26 | Extremo | Pausas somando o tempo bruto | RN-157 |
| AC-009-27 | Extremo | Timer de 8h notifica uma única vez | RN-163 |
| AC-009-28 | Extremo | Timer de 16h vira abandonado | RN-164 |
| AC-009-29 | Extremo | Timer pausado também é abandonado | CX-07 |
| AC-009-30 | Extremo | Recuperação com período fechado | CE-ME-04 |
| AC-009-31 | Extremo | Recuperação gerando mais de 24h | CX-09 |
| AC-009-32 | Extremo | Troca de tarefa com encerramento falho | CX-17 |
| AC-009-33 | Extremo | Cronômetro iniciado e encerrado no mesmo minuto | CX-21 |
| AC-009-34 | Extremo | Divergência entre acumulado e canônico | §6.2 |
| AC-009-35 | Extremo | Reinício do backend | RN-167 |
| AC-009-36 | Extremo | Timer pausado bloqueia conclusão do ticket | CE-ME-01 |
| AC-009-37 | Extremo | Membro removido com timer pausado | RN-460, CE-ME-06 |
| AC-009-38 | Segurança | Cronômetro de outro tenant retorna 404 | RN-002 |
| AC-009-39 | Segurança | `startedAt` forjado é ignorado | SG-05 |
| AC-009-40 | Segurança | `accumulatedActiveSeconds` forjado é ignorado | SG-06 |
| AC-009-41 | Segurança | `MANAGER` não encerra cronômetro alheio | OWN-05 |
| AC-009-42 | Segurança | Visão da equipe não expõe pausas | §19.1 |
| AC-009-43 | Concorrência | Cem inícios simultâneos | RN-150 |
| AC-009-44 | Concorrência | Duas abas encerrando ao mesmo tempo | §4.8 SM |
| AC-009-45 | Concorrência | Pausa e encerramento simultâneos | INV-TMR-02 |

---

## 3. Cenários felizes

### AC-009-01 — Início do cronômetro
```gherkin
Dado que estou autenticado com a permissão TIMER_USE
E que não possuo nenhum cronômetro ativo em nenhum tenant
Quando eu envio POST /api/v1/timers com um ticket válido e uma categoria ativa
Então recebo 201 Created
E o cronômetro é criado com status "RUNNING"
E startedAt e lastResumedAt são iguais ao instante atual
E accumulatedActiveSeconds é 0
E pausedMinutes é 0
E billable vem da categoria selecionada
E um AuditLog com action "TIMER_STARTED" foi gravado
```

### AC-009-02 — Pausa
```gherkin
Dado um cronômetro RUNNING iniciado às 09:00:00
Quando eu envio POST /api/v1/timers/current/pause às 10:30:00
Então recebo 200 OK
E o status passa a "PAUSED"
E accumulatedActiveSeconds passa a 5400
E uma TimerPause é aberta com pausedAt igual ao instante atual e resumedAt nulo
```

### AC-009-03 — Retomada
```gherkin
Dado um cronômetro PAUSED com uma pausa aberta desde 10:30:00
Quando eu envio POST /api/v1/timers/current/resume às 11:00:00
Então recebo 200 OK
E o status volta a "RUNNING"
E a TimerPause é fechada com resumedAt e durationSeconds igual a 1800
E pausedMinutes passa a 30
E lastResumedAt é atualizado para o instante atual
E nenhuma pausa aberta permanece
```

### AC-009-04 — Encerramento gera work log
```gherkin
Dado um cronômetro RUNNING sem conflitos de validação
Quando eu envio POST /api/v1/timers/current/stop com uma descrição válida
Então recebo 201 Created
E um work log é criado com source "TIMER" e timerId preenchido
E o startedAt do work log é igual ao startedAt do cronômetro
E o endedAt do work log é igual ao stoppedAt do cronômetro
E o cronômetro passa a "COMPLETED" com workLogId preenchido
E a resposta traz o work log e o saldo do período atualizado
```

### AC-009-05 — Exemplo normativo de cálculo
```gherkin
Dado um cronômetro iniciado às 09:00:00
E pausado às 10:30:00 e retomado às 11:00:00
Quando eu o encerro às 12:15:40 com descrição válida
Então o work log gerado possui grossMinutes igual a 195
E pausedMinutes igual a 30
E netMinutes igual a 165
E o valor 165 provém de gross menos paused
E não de accumulatedActiveSeconds
```

### AC-009-06 — Edição durante a execução
```gherkin
Dado um cronômetro RUNNING
Quando eu envio PATCH /api/v1/timers/current alterando ticket, categoria, descrição e faturável
Então recebo 200 OK
E os quatro campos são atualizados
E o status permanece "RUNNING"
E startedAt, accumulatedActiveSeconds e pausedMinutes permanecem inalterados
```

### AC-009-07 — Descarte confirmado
```gherkin
Dado um cronômetro RUNNING com 2 horas decorridas
Quando eu envio DELETE /api/v1/timers/current com confirm igual a verdadeiro
Então recebo 204 No Content
E o cronômetro passa a "DISCARDED"
E workLogId permanece nulo
E nenhum work log é criado
E um AuditLog com action "TIMER_DISCARDED" registra o tempo descartado
```

### AC-009-08 — Troca de tarefa atômica
```gherkin
Dado um cronômetro RUNNING no ticket A
Quando eu envio POST /api/v1/timers com o ticket B e stopCurrent igual a verdadeiro
Então recebo 201 Created
E o cronômetro do ticket A passa a "COMPLETED" com work log gerado
E um novo cronômetro RUNNING é criado no ticket B
E ambas as operações ocorreram na mesma transação
```

### AC-009-09 — Recuperação de abandonado
```gherkin
Dado um cronômetro ABANDONED há 3 dias, iniciado às 09:00
Quando eu envio POST /api/v1/timers/{id}/recover informando endedAt às 17:00 do mesmo dia
Então recebo 201 Created
E um work log é gerado com o endedAt informado
E o cronômetro passa a "COMPLETED"
E todas as validações de work log foram aplicadas ao endedAt informado
```

### AC-009-10 — Estado sobrevive ao fechamento do navegador
```gherkin
Dado um cronômetro RUNNING
Quando eu fecho o navegador e reabro a aplicação depois de 30 minutos
Então o cronômetro continua RUNNING
E o tempo decorrido exibido inclui os 30 minutos
E nenhum tempo foi perdido
```

### AC-009-11 — Encerramento forçado notifica o dono
```gherkin
Dado um cronômetro RUNNING de outro membro
E que estou autenticado com a permissão TIMER_STOP_ANY
Quando eu envio POST /api/v1/timers/{id}/force-stop com descrição válida
Então recebo 201 Created
E o work log é criado com userId do dono do cronômetro
E o dono recebe uma notificação informando quem encerrou
E um AuditLog com action "TIMER_FORCE_STOPPED" registra quem forçou
```

### AC-009-12 — Visão de cronômetros da equipe
```gherkin
Dado três membros com cronômetros ativos no tenant
E que estou autenticado com a permissão TIMER_VIEW_ANY
Quando eu envio GET /api/v1/timers/active
Então recebo 200 OK com os três cronômetros
E cada um traz usuário, ticket, horário de início e status
E nenhum traz o histórico de pausas
E nenhum traz a descrição
```

---

## 4. Cenários de erro

### AC-009-13 — Segundo cronômetro do mesmo usuário
```gherkin
Dado um cronômetro RUNNING meu
Quando eu envio POST /api/v1/timers para outro ticket sem stopCurrent
Então recebo 409 Conflict com o código DEVTIME-2150
E a resposta informa qual cronômetro está ativo e em qual tenant
E nenhum cronômetro novo é criado
```

### AC-009-14 — Pausar cronômetro já pausado
```gherkin
Dado um cronômetro PAUSED
Quando eu envio POST /api/v1/timers/current/pause
Então recebo 409 Conflict com o código DEVTIME-2153
E nenhuma pausa adicional é aberta
E accumulatedActiveSeconds permanece inalterado
```

### AC-009-15 — Retomar cronômetro em execução
```gherkin
Dado um cronômetro RUNNING
Quando eu envio POST /api/v1/timers/current/resume
Então recebo 409 Conflict com o código DEVTIME-2155
E lastResumedAt permanece inalterado
```

### AC-009-16 — Encerramento sem descrição preserva o timer
```gherkin
Dado um cronômetro RUNNING
Quando eu envio POST /api/v1/timers/current/stop sem descrição
Então recebo 422 Unprocessable Entity com o código DEVTIME-2105
E o cronômetro permanece com status "RUNNING"
E nenhum work log é criado
E nenhuma pausa foi fechada
E a resposta de erro contém o objeto do cronômetro ainda ativo
```

### AC-009-17 — Encerramento com sobreposição preserva o timer
```gherkin
Dado um cronômetro RUNNING cujo intervalo conflita com um work log existente meu
Quando eu o encerro com descrição válida
Então recebo 422 Unprocessable Entity com o código DEVTIME-2102
E o cronômetro permanece ativo com o mesmo status
E nenhum work log é criado
E a resposta traz uma sugestão de correção orientando a ajustar o horário de início
```

### AC-009-18 — Encerramento com saldo insuficiente preserva o timer
```gherkin
Dado um contrato com overagePolicy BLOCK e saldo insuficiente
E um cronômetro RUNNING em um ticket desse contrato
Quando eu o encerro com descrição válida
Então recebo 422 Unprocessable Entity com o código DEVTIME-2220
E o cronômetro permanece ativo
E a resposta sugere marcar como não faturável ou solicitar ajuste de saldo
```

### AC-009-19 — Encerramento com contrato encerrado preserva o timer
```gherkin
Dado um cronômetro RUNNING cujo contrato foi encerrado durante a execução
Quando eu o encerro com descrição válida
Então recebo 422 Unprocessable Entity com o código DEVTIME-2306
E o cronômetro permanece ativo
E a resposta orienta a mover o ticket para outro contrato
```

### AC-009-20 — Descarte sem confirmação
```gherkin
Dado um cronômetro RUNNING
Quando eu envio DELETE /api/v1/timers/current sem o parâmetro confirm
Então recebo 422 Unprocessable Entity
E o cronômetro permanece ativo
E nada é descartado
```

### AC-009-21 — Recuperação após 7 dias
```gherkin
Dado um cronômetro ABANDONED há 8 dias
Quando eu envio POST /api/v1/timers/{id}/recover com um endedAt válido
Então recebo 409 Conflict com o código DEVTIME-2165
E nenhum work log é criado
```

### AC-009-22 — Operação em cronômetro encerrado
```gherkin
Dado um cronômetro COMPLETED
Quando eu tento pausá-lo, retomá-lo, encerrá-lo ou descartá-lo
Então recebo 409 Conflict com o código DEVTIME-2010 em todas as tentativas
E o estado permanece "COMPLETED"
```

### AC-009-23 — `VIEWER` tenta iniciar
```gherkin
Dado que estou autenticado com o papel VIEWER
Quando eu envio POST /api/v1/timers
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica TIMER_USE
E o componente de cronômetro não é exibido na interface
```

---

## 5. Cenários extremos

### AC-009-24 — Cronômetro em dois tenants
```gherkin
Dado que eu participo dos tenants A e B
E que possuo um cronômetro RUNNING no tenant A
Quando eu seleciono o tenant B e tento iniciar um cronômetro
Então recebo 409 Conflict com o código DEVTIME-2150
E a resposta indica que o cronômetro ativo está no tenant A
E o limite de um cronômetro é por usuário, não por organização
```

### AC-009-25 — Cinquenta pausas em uma sessão
```gherkin
Dado um cronômetro RUNNING
Quando eu executo 50 ciclos de pausa e retomada
Então todas as operações são aceitas
E pausedMinutes reflete a soma exata de todas as pausas
E nenhuma pausa permanece aberta ao final
E o encerramento produz netMinutes igual a gross menos a soma das pausas
```

### AC-009-26 — Pausas somando o tempo bruto
```gherkin
Dado um cronômetro cujo tempo total de pausas iguala o tempo bruto decorrido
Quando eu o encerro com descrição válida
Então recebo 422 Unprocessable Entity com o código DEVTIME-2116
E o cronômetro permanece ativo
```

### AC-009-27 — Timer de 8h notifica uma única vez
```gherkin
Dado que timerLongRunningMinutes está configurado em 480
E um cronômetro RUNNING há 8 horas
Quando o job de monitoramento é executado
Então uma notificação TIMER_LONG_RUNNING é gerada
E longRunningNotifiedAt é preenchido
Quando o job é executado novamente nas horas seguintes
Então nenhuma notificação adicional é gerada para esse cronômetro
```

### AC-009-28 — Timer de 16h vira abandonado
```gherkin
Dado que timerAutoAbandonMinutes está configurado em 960
E um cronômetro RUNNING há 16 horas
Quando o job de monitoramento é executado
Então o cronômetro passa a "ABANDONED"
E nenhum work log é gerado automaticamente
E uma notificação TIMER_ABANDONED é enviada ao dono, com ação de recuperar
```

### AC-009-29 — Timer pausado também é abandonado
```gherkin
Dado um cronômetro PAUSED cujo startedAt foi há 16 horas
E cujo tempo ativo acumulado é de apenas 2 horas
Quando o job de monitoramento é executado
Então o cronômetro passa a "ABANDONED"
E o critério aplicado foi o tempo desde o início, não o tempo ativo
```

### AC-009-30 — Recuperação com período fechado
```gherkin
Dado um cronômetro ABANDONED cujo período de contrato foi fechado
Quando eu tento recuperá-lo informando um endedAt válido
Então recebo 409 Conflict com o código DEVTIME-2121
E o cronômetro permanece ABANDONED
E a interface orienta a solicitar reabertura do período ou descartar
```

### AC-009-31 — Recuperação gerando mais de 24h
```gherkin
Dado um cronômetro ABANDONED iniciado há 3 dias
Quando eu tento recuperá-lo informando um endedAt que produziria 30 horas de sessão
Então recebo 422 Unprocessable Entity com o código DEVTIME-2103
E o cronômetro permanece ABANDONED
E eu posso tentar novamente com outro endedAt
```

### AC-009-32 — Troca de tarefa com encerramento falho
```gherkin
Dado um cronômetro RUNNING cujo encerramento falharia por sobreposição
Quando eu envio POST /api/v1/timers com outro ticket e stopCurrent igual a verdadeiro
Então recebo 422 com o código da regra violada
E o cronômetro original permanece ativo e inalterado
E nenhum cronômetro novo é criado
E nenhum work log é gerado
```

### AC-009-33 — Cronômetro iniciado e encerrado no mesmo minuto
```gherkin
Dado um cronômetro iniciado e encerrado dentro do mesmo minuto
Quando eu o encerro com descrição válida
Então o grossMinutes calculado é 0
E recebo 422 Unprocessable Entity com o código DEVTIME-2115
E o cronômetro permanece ativo
```

### AC-009-34 — Divergência entre acumulado e canônico
```gherkin
Dado um cronômetro cujo accumulatedActiveSeconds equivale a 165,67 minutos
Quando eu o encerro
Então o work log gerado possui netMinutes igual a 165
E o valor provém de grossMinutes menos pausedMinutes
E a divergência de truncamento é esperada e documentada
```

### AC-009-35 — Reinício do backend
```gherkin
Dado cronômetros RUNNING e PAUSED de vários usuários
Quando o backend é reiniciado
Então todos os cronômetros permanecem em seus estados originais
E o tempo decorrido continua correto após o reinício
E nenhum estado de cronômetro dependia de memória
```

### AC-009-36 — Timer pausado bloqueia conclusão do ticket
```gherkin
Dado um ticket com um cronômetro PAUSED apontando para ele
Quando alguém tenta transicionar o ticket para "DONE"
Então recebo 409 Conflict com o código DEVTIME-2311
E o cronômetro pausado é tratado como ativo
Quando o período do contrato desse ticket é fechado
Então recebo 409 Conflict com o código DEVTIME-2240
```

### AC-009-37 — Membro removido com timer pausado
```gherkin
Dado um membro com um cronômetro PAUSED
Quando esse membro é removido do tenant
Então o cronômetro passa a "DISCARDED"
E nenhum work log é gerado
E o OWNER recebe uma notificação
E o tempo decorrido fica registrado apenas na auditoria
```

---

## 6. Cenários de segurança

### AC-009-38 — Cronômetro de outro tenant retorna 404
```gherkin
Dado um cronômetro pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu tento recuperá-lo ou encerrá-lo forçadamente por id
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-009-39 — `startedAt` forjado é ignorado
```gherkin
Quando eu envio POST /api/v1/timers incluindo startedAt de 5 horas atrás
Então o campo é ignorado
E o cronômetro é criado com startedAt igual ao instante atual do servidor
```

### AC-009-40 — `accumulatedActiveSeconds` forjado é ignorado
```gherkin
Dado um cronômetro RUNNING
Quando eu envio PATCH incluindo accumulatedActiveSeconds igual a 99999
Então o campo é ignorado
E o valor real permanece calculado pelo servidor
```

### AC-009-41 — `MANAGER` não encerra cronômetro alheio
```gherkin
Dado que estou autenticado com o papel MANAGER
E um cronômetro RUNNING de outro membro
Quando eu envio POST /api/v1/timers/{id}/force-stop
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica TIMER_STOP_ANY
E o cronômetro do outro membro permanece intacto
```

### AC-009-42 — Visão da equipe não expõe pausas
```gherkin
Dado um cronômetro de outro membro com 8 pausas registradas
E que estou autenticado com a permissão TIMER_VIEW_ANY
Quando eu envio GET /api/v1/timers/active
Então a resposta traz apenas usuário, ticket, horário de início e status
E nenhum horário de pausa é exposto
E nenhuma quantidade de pausas é exposta
E nenhuma descrição é exposta
```

---

## 7. Cenários de concorrência

### AC-009-43 — Cem inícios simultâneos
```gherkin
Dado que não possuo nenhum cronômetro ativo
Quando 100 requisições simultâneas de início são processadas para o meu usuário
Então exatamente uma recebe 201 Created
E as demais recebem 409 Conflict com o código DEVTIME-2150
E existe exatamente um cronômetro ativo meu na base
E a rejeição ocorre também pela constraint do banco, não apenas pela verificação prévia
```

### AC-009-44 — Duas abas encerrando ao mesmo tempo
```gherkin
Dado um cronômetro RUNNING aberto em duas abas
Quando ambas enviam o encerramento simultaneamente
Então exatamente uma recebe 201 Created com o work log
E a outra recebe 409 Conflict com o código DEVTIME-2010
E apenas um work log é criado
```

### AC-009-45 — Pausa e encerramento simultâneos
```gherkin
Dado um cronômetro RUNNING
Quando uma requisição o pausa e outra o encerra, em paralelo
Então exatamente uma das duas é aplicada
E, se o encerramento vencer, nenhuma pausa aberta permanece
E, se a pausa vencer, o cronômetro fica PAUSED com exatamente uma pausa aberta
E nunca resulta em um cronômetro COMPLETED com pausa aberta
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-150 | AC-009-13, AC-009-24, AC-009-43 | ✅ |
| RN-151 | AC-009-10, AC-009-35 | ✅ |
| RN-152 | AC-009-01 | ✅ |
| RN-153 | AC-009-14 | ✅ |
| RN-154 | AC-009-02 | ✅ |
| RN-155 | AC-009-15 | ✅ |
| RN-156 | AC-009-03 | ✅ |
| RN-157 | AC-009-25, AC-009-26 | ✅ |
| RN-158 | AC-009-16 | ✅ |
| RN-159 | AC-009-04, AC-009-05 | ✅ |
| RN-160 | AC-009-16, AC-009-17, AC-009-18, AC-009-19, AC-009-26, AC-009-31, AC-009-33 | ✅ |
| RN-161 | AC-009-06 | ✅ |
| RN-162 | AC-009-07, AC-009-20 | ✅ |
| RN-163 | AC-009-27 | ✅ |
| RN-164 | AC-009-28, AC-009-29 | ✅ |
| RN-165 | AC-009-09, AC-009-21, AC-009-30, AC-009-31 | ✅ |
| RN-166 | AC-009-08, AC-009-32 | ✅ |
| RN-167 | AC-009-35 | ✅ |
| RN-460 | AC-009-37 | ✅ |
| RN-311 / RN-240 | AC-009-36 | ✅ |
| RN-002 | AC-009-38 | ✅ |
| RN-006 | AC-009-01, AC-009-07, AC-009-11 | ✅ |
| INV-TMR-01 | AC-009-13, AC-009-24, AC-009-43 | ✅ |
| INV-TMR-02 / 03 | AC-009-02, AC-009-03, AC-009-45 | ✅ |
| INV-TMR-04 | AC-009-04 | ✅ |
| INV-TMR-05 | AC-009-07 | ✅ |
| §4.8 SM | AC-009-22, AC-009-44 | ✅ |
| OWN-05 | AC-009-11, AC-009-41 | ✅ |
| CE-P-06 | AC-009-23 | ✅ |
| CE-12 / CE-13 | AC-009-19, AC-009-24 | ✅ |
| CE-ME-01 / CE-ME-04 / CE-ME-06 | AC-009-36, AC-009-30, AC-009-37 | ✅ |
| §6.2 (valor canônico) | AC-009-05, AC-009-34 | ✅ |
| §19.1 (privacidade das pausas) | AC-009-42 | ✅ |
| SG-05 / SG-06 | AC-009-39, AC-009-40 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. RN-160 — a regra central da feature — é verificada em **sete** cenários distintos, um para cada classe de falha possível no encerramento.
