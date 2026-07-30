# 007 — Tickets · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-007-XX`, estável e imutável |
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
| AC-007-01 | Feliz | Criação com chave legível | RN-301, RN-302 |
| AC-007-02 | Feliz | Numeração sequencial por contrato | RN-302 |
| AC-007-03 | Feliz | Criação com responsável notifica | RN-304 |
| AC-007-04 | Feliz | Priorização e início do trabalho | RN-310 |
| AC-007-05 | Feliz | `startedAt` só na primeira entrada | RN-310 |
| AC-007-06 | Feliz | Bloqueio com motivo | §4.7 SM |
| AC-007-07 | Feliz | Envio a revisão e aprovação | §4.7 SM |
| AC-007-08 | Feliz | Conclusão preenche `completedAt` | RN-310 |
| AC-007-09 | Feliz | Reabertura limpa `completedAt` | RN-310 |
| AC-007-10 | Feliz | Cancelamento preserva horas | RN-314 |
| AC-007-11 | Feliz | Reativação de cancelado | §4.7 SM |
| AC-007-12 | Feliz | Movimentação entre contratos preserva a chave | RN-305 |
| AC-007-13 | Feliz | Totais atualizados a cada work log | RN-308 |
| AC-007-14 | Feliz | Reabertura automática ao receber work log | RN-312 |
| AC-007-15 | Feliz | Busca por chave | §6 tickets.md |
| AC-007-16 | Feliz | Quadro agrupado por status | §7 tickets.md |
| AC-007-17 | Feliz | Vínculo de tags | RN-313 |
| AC-007-18 | Feliz | Exclusão sem horas | RN-003 |
| AC-007-19 | Erro | Contrato inexistente no tenant | RN-301 |
| AC-007-20 | Erro | Contrato encerrado | RN-306 |
| AC-007-21 | Erro | Título com 2 caracteres | RN-303 |
| AC-007-22 | Erro | Responsável inativo | RN-304 |
| AC-007-23 | Erro | Transição fora da matriz | ME-04 |
| AC-007-24 | Erro | `DONE → CANCELLED` proibido | §4.7 SM |
| AC-007-25 | Erro | Conclusão com timer ativo | RN-311 |
| AC-007-26 | Erro | Bloqueio sem motivo | §4.7 SM |
| AC-007-27 | Erro | Movimentação com horas | RN-305 |
| AC-007-28 | Erro | Movimentação para outro cliente | RN-305 |
| AC-007-29 | Erro | Exclusão com horas | RN-307 |
| AC-007-30 | Erro | 11ª tag | RN-313 |
| AC-007-31 | Erro | Alteração de `key` ou `reporterId` | RN-011 |
| AC-007-32 | Erro | `MEMBER` transiciona ticket alheio | nota ⁴ |
| AC-007-33 | Extremo | Título com exatamente 3 e 200 caracteres | CX-12 |
| AC-007-34 | Extremo | Ticket sem estimativa | CX-09 |
| AC-007-35 | Extremo | Estouro de estimativa não bloqueia | RN-309, CX-08 |
| AC-007-36 | Extremo | Conclusão com timer pausado | CX-11 |
| AC-007-37 | Extremo | Exclusão do work log que reabriu não reverte | CX-06 |
| AC-007-38 | Extremo | Auto-transição sem efeito | CX-16 |
| AC-007-39 | Extremo | Reativação com contrato encerrado | CX-15 |
| AC-007-40 | Extremo | Lacuna de numeração após falha | CX-02 |
| AC-007-41 | Extremo | Responsável removido do tenant | CX-10 |
| AC-007-42 | Extremo | Chave de ticket excluído não é reutilizada | CX-21 |
| AC-007-43 | Extremo | `blockReason` com 4 caracteres | CX-20 |
| AC-007-44 | Segurança | Ticket de outro tenant retorna 404 | RN-002 |
| AC-007-45 | Segurança | Busca por chave de outro tenant | SG-01 |
| AC-007-46 | Segurança | `MEMBER` não vê horas de colegas | SG-04 |
| AC-007-47 | Segurança | `reporterId` forjado é ignorado | SG-07 |
| AC-007-48 | Segurança | `spentMinutes` forjado é ignorado | SG-08 |
| AC-007-49 | Segurança | XSS em descrição Markdown | SG-05 |
| AC-007-50 | Concorrência | Cem criações simultâneas no mesmo contrato | RN-302 |
| AC-007-51 | Concorrência | Duas transições simultâneas | ME-01 |
| AC-007-52 | Concorrência | Conclusão e início de timer simultâneos | RN-311 |
| AC-007-53 | Concorrência | Work logs simultâneos e totais | RN-308 |

---

## 3. Cenários felizes

### AC-007-01 — Criação com chave legível
```gherkin
Dado um contrato ACTIVE com code "CT-0001" e nenhum ticket
E que estou autenticado com a permissão TICKET_CREATE
Quando eu envio POST /api/v1/tickets com contractId desse contrato e título "Ajustar relatório"
Então recebo 201 Created com o header Location
E o ticket é criado com number igual a 1
E key igual a "CT-0001-1"
E status igual a "BACKLOG"
E reporterId igual ao meu usuário
E spentMinutes e billableMinutes iguais a 0
E um AuditLog com action "TICKET_CREATED" foi gravado
```

### AC-007-02 — Numeração sequencial por contrato
```gherkin
Dado dois contratos com code "CT-0001" e "CT-0002"
Quando eu crio três tickets em "CT-0001" e um em "CT-0002"
Então as chaves geradas são "CT-0001-1", "CT-0001-2", "CT-0001-3" e "CT-0002-1"
E a sequência é independente por contrato
```

### AC-007-03 — Criação com responsável notifica
```gherkin
Dado um membership ACTIVE do tenant
Quando eu crio um ticket informando esse membro como responsável
Então recebo 201 Created
E o assigneeId é preenchido
E um evento TicketAssignedEvent é publicado após o commit
E o responsável recebe uma notificação
```

### AC-007-04 — Priorização e início do trabalho
```gherkin
Dado um ticket em "BACKLOG"
Quando eu envio POST /tickets/{id}/transition com targetStatus "TODO"
Então recebo 200 OK e o status passa a "TODO"
Quando eu envio nova transição para "IN_PROGRESS"
Então recebo 200 OK
E startedAt é preenchido com o instante atual
E um AuditLog com action "TICKET_STATUS_CHANGED" registra ambas as transições
```

### AC-007-05 — `startedAt` só na primeira entrada
```gherkin
Dado um ticket que já esteve em "IN_PROGRESS" e possui startedAt preenchido
Quando ele vai para "BLOCKED" e retorna para "IN_PROGRESS"
Então o startedAt permanece com o valor original
E não é sobrescrito pelo instante da segunda entrada
```

### AC-007-06 — Bloqueio com motivo
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando eu envio transição para "BLOCKED" com blockReason "Aguardando acesso ao servidor"
Então recebo 200 OK
E o status passa a "BLOCKED"
E o blockReason é persistido
E um comentário de sistema é gerado registrando o impedimento
```

### AC-007-07 — Envio a revisão e aprovação
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando eu o envio para "IN_REVIEW"
Então recebo 200 OK e o relator é notificado
Quando eu o aprovo transicionando para "DONE"
Então recebo 200 OK e o status passa a "DONE"
```

### AC-007-08 — Conclusão preenche `completedAt`
```gherkin
Dado um ticket em "IN_PROGRESS" sem nenhum timer ativo
Quando eu o transiciono para "DONE"
Então recebo 200 OK
E completedAt é preenchido com o instante atual
E o relator é notificado
```

### AC-007-09 — Reabertura limpa `completedAt`
```gherkin
Dado um ticket em "DONE" com completedAt preenchido
Quando eu o transiciono para "IN_PROGRESS"
Então recebo 200 OK
E completedAt volta a ser nulo
E startedAt permanece com o valor original
```

### AC-007-10 — Cancelamento preserva horas
```gherkin
Dado um ticket em "IN_PROGRESS" com 40 horas registradas
Quando eu o transiciono para "CANCELLED"
Então recebo 200 OK
E os work logs permanecem existindo e vinculados ao ticket
E o consumedMinutes do período do contrato permanece inalterado
E nenhuma hora é devolvida ao saldo
E um comentário de sistema registra o cancelamento
```

### AC-007-11 — Reativação de cancelado
```gherkin
Dado um ticket em "CANCELLED" cujo contrato está ACTIVE
Quando eu o transiciono para "BACKLOG"
Então recebo 200 OK
E o status passa a "BACKLOG"
E as horas registradas permanecem inalteradas
```

### AC-007-12 — Movimentação entre contratos preserva a chave
```gherkin
Dado um ticket "CT-0001-42" sem nenhum work log
E um segundo contrato "CT-0002" do MESMO cliente, com status ACTIVE
Quando eu envio POST /tickets/{id}/move-contract para "CT-0002" com confirmed verdadeiro
Então recebo 200 OK
E o contractId passa a ser o de "CT-0002"
E o number permanece 42
E a key permanece "CT-0001-42"
E um comentário de sistema registra a movimentação
```

### AC-007-13 — Totais atualizados a cada work log
```gherkin
Dado um ticket com spentMinutes igual a 0
Quando um work log de 120 minutos faturáveis é criado nesse ticket
Então spentMinutes passa a 120 e billableMinutes passa a 120
Quando um segundo work log de 60 minutos não faturáveis é criado
Então spentMinutes passa a 180 e billableMinutes permanece 120
Quando o primeiro work log é excluído
Então spentMinutes passa a 60 e billableMinutes passa a 0
```

### AC-007-14 — Reabertura automática ao receber work log
```gherkin
Dado um ticket em "DONE" com completedAt preenchido
Quando um work log é criado nesse ticket
Então o work log é criado com sucesso
E o ticket passa automaticamente para "IN_PROGRESS"
E completedAt volta a ser nulo
E o responsável é notificado
E um AuditLog com actorType "SYSTEM" registra a reabertura e o workLogId disparador
```

### AC-007-15 — Busca por chave
```gherkin
Dado um ticket com key "CT-0001-42"
Quando eu envio GET /api/v1/tickets/by-key/CT-0001-42
Então recebo 200 OK com o ticket correspondente
```

### AC-007-16 — Quadro agrupado por status
```gherkin
Dado tickets distribuídos em todos os 7 status
Quando eu envio GET /api/v1/tickets/board
Então recebo 200 OK com 7 colunas
E cada coluna traz o total de tickets e uma lista limitada
E a resposta é produzida por uma única consulta agrupada
```

### AC-007-17 — Vínculo de tags
```gherkin
Dado um ticket sem etiquetas
Quando eu o atualizo informando 10 etiquetas existentes
Então recebo 200 OK
E as 10 etiquetas são vinculadas
E o usageCount de cada uma é incrementado
```

### AC-007-18 — Exclusão sem horas
```gherkin
Dado um ticket sem nenhum work log
Quando eu envio DELETE /api/v1/tickets/{id}
Então recebo 204 No Content
E o ticket recebe deletedAt preenchido
E ele some de todas as consultas padrão
E suas etiquetas são desvinculadas
```

---

## 4. Cenários de erro

### AC-007-19 — Contrato inexistente no tenant
```gherkin
Quando eu envio POST /api/v1/tickets com um contractId que não existe no meu tenant
Então recebo 404 Not Found com o código DEVTIME-2002
E nenhum número de sequência é consumido
```

### AC-007-20 — Contrato encerrado
```gherkin
Dado um contrato com status "ENDED"
Quando eu envio POST /api/v1/tickets para esse contrato
Então recebo 422 Unprocessable Entity com o código DEVTIME-2306
E a mensagem indica que o contrato encerrado não aceita registros
E nenhum número de sequência é consumido
```

### AC-007-21 — Título com 2 caracteres
```gherkin
Quando eu envio POST /api/v1/tickets com o título "ab"
Então recebo 422 Unprocessable Entity com o código DEVTIME-2303
E a mensagem indica que o título deve ter entre 3 e 200 caracteres
```

### AC-007-22 — Responsável inativo
```gherkin
Dado um membership com status "SUSPENDED"
Quando eu envio POST /api/v1/tickets informando esse membro como responsável
Então recebo 422 Unprocessable Entity com o código DEVTIME-2304
E nenhum ticket é criado
```

### AC-007-23 — Transição fora da matriz
```gherkin
Dado um ticket em "BACKLOG"
Quando eu envio transição para "DONE"
Então recebo 409 Conflict com o código DEVTIME-2010
E a resposta traz currentStatus, requestedStatus e a lista availableTransitions
E o status permanece "BACKLOG"
```

### AC-007-24 — `DONE → CANCELLED` proibido
```gherkin
Dado um ticket em "DONE"
Quando eu envio transição para "CANCELLED"
Então recebo 409 Conflict com o código DEVTIME-2010
E o campo availableTransitions não inclui "CANCELLED"
E o status permanece "DONE"
```

### AC-007-25 — Conclusão com timer ativo
```gherkin
Dado um ticket em "IN_PROGRESS" com um cronômetro RUNNING apontando para ele
Quando eu envio transição para "DONE"
Então recebo 409 Conflict com o código DEVTIME-2311
E a resposta lista os cronômetros ativos
E o status permanece "IN_PROGRESS"
```

### AC-007-26 — Bloqueio sem motivo
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando eu envio transição para "BLOCKED" sem informar blockReason
Então recebo 422 Unprocessable Entity
E o status permanece "IN_PROGRESS"
```

### AC-007-27 — Movimentação com horas
```gherkin
Dado um ticket com pelo menos um work log registrado
Quando eu envio POST /tickets/{id}/move-contract para outro contrato do mesmo cliente
Então recebo 409 Conflict com o código DEVTIME-2305
E a mensagem indica que ticket com horas não pode mudar de contrato
E o contractId permanece inalterado
```

### AC-007-28 — Movimentação para outro cliente
```gherkin
Dado um ticket sem nenhum work log
E um contrato de destino pertencente a OUTRO cliente
Quando eu envio POST /tickets/{id}/move-contract para esse contrato
Então recebo 409 Conflict com o código DEVTIME-2305
E o contractId permanece inalterado
```

### AC-007-29 — Exclusão com horas
```gherkin
Dado um ticket com 3 work logs registrados
Quando eu envio DELETE /api/v1/tickets/{id}
Então recebo 409 Conflict com o código DEVTIME-2307
E a mensagem sugere cancelar o ticket
E o ticket permanece intacto
```

### AC-007-30 — 11ª tag
```gherkin
Dado um ticket com 10 etiquetas vinculadas
Quando eu tento vincular uma 11ª etiqueta
Então recebo 422 Unprocessable Entity com o código DEVTIME-2313
E nenhuma etiqueta é adicionada
```

### AC-007-31 — Alteração de `key` ou `reporterId`
```gherkin
Dado um ticket com key "CT-0001-42"
Quando eu envio PATCH /api/v1/tickets/{id} incluindo key, number e reporterId diferentes
Então os campos são ignorados ou rejeitados
E key, number e reporterId permanecem inalterados
```

### AC-007-32 — `MEMBER` transiciona ticket alheio
```gherkin
Dado que estou autenticado com o papel MEMBER
E um ticket em que eu não sou nem relator nem responsável
Quando eu envio transição para esse ticket
Então recebo 403 Forbidden com o código DEVTIME-1101
Mas eu consigo visualizar esse ticket normalmente
E eu consigo transicionar um ticket em que sou relator
```

---

## 5. Cenários extremos

### AC-007-33 — Título com exatamente 3 e 200 caracteres
```gherkin
Quando eu crio um ticket com título de exatamente 3 caracteres
Então recebo 201 Created
Quando eu crio um ticket com título de exatamente 200 caracteres
Então recebo 201 Created
Quando eu crio um ticket com título de 201 caracteres
Então recebo 422 com o código DEVTIME-2303
```

### AC-007-34 — Ticket sem estimativa
```gherkin
Dado um ticket criado sem estimatedMinutes
Quando eu consulto esse ticket após registrar 500 minutos
Então progressRate é nulo
E isOverEstimate é nulo
E nenhum selo de estouro é exibido
```

### AC-007-35 — Estouro de estimativa não bloqueia
```gherkin
Dado um ticket com estimatedMinutes igual a 600
Quando work logs somando 1.800 minutos são registrados
Então todos os registros são aceitos com sucesso
E isOverEstimate passa a verdadeiro
E progressRate é 300
E um selo de estouro é exibido na interface
Mas nenhum registro é bloqueado
```

### AC-007-36 — Conclusão com timer pausado
```gherkin
Dado um ticket em "IN_PROGRESS" com um cronômetro PAUSED apontando para ele
Quando eu envio transição para "DONE"
Então recebo 409 Conflict com o código DEVTIME-2311
E o cronômetro pausado é tratado como ativo
```

### AC-007-37 — Exclusão do work log que reabriu não reverte
```gherkin
Dado um ticket que estava em "DONE" e foi reaberto para "IN_PROGRESS" por um work log
Quando esse work log é excluído
Então o ticket permanece em "IN_PROGRESS"
E completedAt permanece nulo
E o ticket não volta automaticamente para "DONE"
E o usuário precisa concluí-lo manualmente
```

### AC-007-38 — Auto-transição sem efeito
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando eu envio transição para "IN_PROGRESS"
Então recebo 200 OK
E nenhum campo é alterado
E nenhum AuditLog é gerado
E nenhum comentário de sistema é criado
```

### AC-007-39 — Reativação com contrato encerrado
```gherkin
Dado um ticket em "CANCELLED" cujo contrato está "ENDED"
Quando eu tento transicioná-lo para "BACKLOG"
Então recebo 409 Conflict com o código DEVTIME-2010
E o status permanece "CANCELLED"
```

### AC-007-40 — Lacuna de numeração após falha
```gherkin
Dado um contrato cujo último ticket possui number igual a 5
Quando uma criação consome o número 6 e falha antes de persistir
E em seguida uma nova criação é bem-sucedida
Então o novo ticket recebe number igual a 7
E o número 6 não é reutilizado
E a lacuna é aceita como comportamento correto
```

### AC-007-41 — Responsável removido do tenant
```gherkin
Dado um ticket em "IN_PROGRESS" atribuído a um membro
Quando esse membro é removido do tenant
Então o ticket é reatribuído ao OWNER
E o assigneeId nunca permanece apontando para um membership removido
E o reporterId histórico é preservado
```

### AC-007-42 — Chave de ticket excluído não é reutilizada
```gherkin
Dado um ticket "CT-0001-42" excluído logicamente
Quando eu busco GET /tickets/by-key/CT-0001-42
Então recebo 404 Not Found
Quando eu crio um novo ticket no contrato "CT-0001"
Então ele recebe um number maior que 42
E nunca reutiliza a chave "CT-0001-42"
```

### AC-007-43 — `blockReason` com 4 caracteres
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando eu envio transição para "BLOCKED" com blockReason "abcd"
Então recebo 422 Unprocessable Entity
E a mensagem indica o mínimo de 5 caracteres
E o status permanece "IN_PROGRESS"
```

---

## 6. Cenários de segurança

### AC-007-44 — Ticket de outro tenant retorna 404
```gherkin
Dado um ticket pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu envio GET, PATCH, DELETE ou transição em /api/v1/tickets/{idDoTenantB}
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-007-45 — Busca por chave de outro tenant
```gherkin
Dado um ticket com key "CT-0001-1" no tenant B
E que estou autenticado no tenant A, que não possui essa chave
Quando eu envio GET /api/v1/tickets/by-key/CT-0001-1
Então recebo 404 Not Found com o código DEVTIME-2002
E a resposta é indistinguível da de uma chave inexistente
```

### AC-007-46 — `MEMBER` não vê horas de colegas
```gherkin
Dado um ticket com work logs de três membros distintos
E que estou autenticado com o papel MEMBER, autor de apenas um deles
Quando eu envio GET /api/v1/tickets/{id}/activity
Então apenas o meu work log aparece na linha do tempo
E os work logs dos outros membros não aparecem
E a filtragem é aplicada na consulta ao banco, não em memória
E o spentMinutes total do ticket continua visível
```

### AC-007-47 — `reporterId` forjado é ignorado
```gherkin
Quando eu envio POST /api/v1/tickets incluindo reporterId de outro usuário
Então o campo é ignorado
E o ticket é criado com reporterId igual ao meu usuário autenticado
```

### AC-007-48 — `spentMinutes` forjado é ignorado
```gherkin
Dado um ticket com spentMinutes igual a 120
Quando eu envio PATCH /api/v1/tickets/{id} incluindo spentMinutes igual a 9999
Então o campo é ignorado
E spentMinutes permanece 120
```

### AC-007-49 — XSS em descrição Markdown
```gherkin
Dado um ticket cuja descrição contém "<script>alert(1)</script>" e "<img src=x onerror=alert(1)>"
Quando a descrição é renderizada na interface e em um relatório PDF
Então o conteúdo é exibido como texto literal
E nenhum script é executado
E nenhuma tag fora da allowlist é preservada na saída
```

---

## 7. Cenários de concorrência

### AC-007-50 — Cem criações simultâneas no mesmo contrato
```gherkin
Dado um contrato sem nenhum ticket
Quando 100 requisições de criação são processadas em paralelo nesse contrato
Então as 100 recebem 201 Created
E os numbers atribuídos são exatamente 1 a 100, sem repetição
E as 100 keys são distintas
E nenhuma violação de índice único ocorre
```

### AC-007-51 — Duas transições simultâneas
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando duas requisições simultâneas o transicionam, uma para "DONE" e outra para "BLOCKED"
Então exatamente uma é aplicada
E a outra recebe 409, por conflito de versão ou por transição inválida a partir do novo estado
E o status final corresponde a exatamente uma das duas requisições
E nunca resulta em um estado intermediário inconsistente
```

### AC-007-52 — Conclusão e início de timer simultâneos
```gherkin
Dado um ticket em "IN_PROGRESS" sem cronômetro ativo
Quando uma requisição o conclui e outra inicia um cronômetro nele, em paralelo
Então ou o ticket é concluído e o início do cronômetro falha
Ou o cronômetro inicia e a conclusão falha com DEVTIME-2311
E nunca existe um ticket em "DONE" com cronômetro ativo apontando para ele
```

### AC-007-53 — Work logs simultâneos e totais
```gherkin
Dado um ticket com spentMinutes igual a 0
Quando 20 work logs de 30 minutos cada são criados simultaneamente nesse ticket
Então spentMinutes final é 600
E nenhuma atualização é perdida, pois o incremento ocorre no banco
Ou, havendo divergência, o DenormalizationReconcileJob restaura o valor 600
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-301 | AC-007-01, AC-007-19 | ✅ |
| RN-302 | AC-007-01, AC-007-02, AC-007-40, AC-007-42, AC-007-50 | ✅ |
| RN-303 | AC-007-21, AC-007-33 | ✅ |
| RN-304 | AC-007-03, AC-007-22, AC-007-41 | ✅ |
| RN-305 | AC-007-12, AC-007-27, AC-007-28 | ✅ |
| RN-306 | AC-007-20 | ✅ |
| RN-307 | AC-007-29 | ✅ |
| RN-308 | AC-007-13, AC-007-53 | ✅ |
| RN-309 | AC-007-34, AC-007-35 | ✅ |
| RN-310 | AC-007-04, AC-007-05, AC-007-08, AC-007-09 | ✅ |
| RN-311 | AC-007-25, AC-007-36, AC-007-52 | ✅ |
| RN-312 | AC-007-14, AC-007-37 | ✅ |
| RN-313 | AC-007-17, AC-007-30 | ✅ |
| RN-314 | AC-007-10 | ✅ |
| RN-815 | AC-007-06, AC-007-10, AC-007-12 | ✅ |
| RN-003 | AC-007-18, AC-007-42 | ✅ |
| RN-004 | AC-007-51 | ✅ |
| RN-011 | AC-007-31 | ✅ |
| RN-001 / RN-002 | AC-007-44, AC-007-45, AC-007-47 | ✅ |
| RN-006 | AC-007-01, AC-007-04, AC-007-14 | ✅ |
| INV-TCK-01 | AC-007-02, AC-007-50 | ✅ |
| INV-TCK-02 | AC-007-27 | ✅ |
| INV-TCK-03 | AC-007-29 | ✅ |
| INV-TCK-04 | AC-007-08, AC-007-09 | ✅ |
| INV-TCK-05 | AC-007-13, AC-007-48 | ✅ |
| §4.7 SM | AC-007-04 a AC-007-11, AC-007-23, AC-007-24, AC-007-26, AC-007-38, AC-007-39, AC-007-43, AC-007-51 | ✅ |
| ME-03 | AC-007-38 | ✅ |
| ME-04 | AC-007-23, AC-007-24 | ✅ |
| ME-06 | AC-007-23, AC-007-24 | ✅ |
| nota ⁴ permissions | AC-007-32 | ✅ |
| SG-04 | AC-007-46 | ✅ |
| SG-05 | AC-007-49 | ✅ |
| SG-07 / SG-08 | AC-007-47, AC-007-48 | ✅ |
| §7 tickets.md | AC-007-16 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. As 27 transições proibidas da matriz §4.7 são cobertas coletivamente por `AC-007-23` e `AC-007-24`, e exaustivamente pela suíte `TS-007-06` de `tests.md`.
