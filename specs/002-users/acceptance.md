# 002 — Users & Tenant · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-002-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-002-01 | Feliz | Edição de perfil | — |
| AC-002-02 | Feliz | Alteração de preferências aplica o tema | §6.2.1 |
| AC-002-03 | Feliz | Edição de dados da organização | RN-004 |
| AC-002-04 | Feliz | Alteração de configurações operacionais | §6.2 |
| AC-002-05 | Feliz | Convite de membro | RN-457 |
| AC-002-06 | Feliz | Alteração de papel | RN-455 |
| AC-002-07 | Feliz | Remoção de membro preserva registros | RN-458, RN-460 |
| AC-002-08 | Feliz | Consulta da trilha de auditoria | INV-AUD-01 |
| AC-002-09 | Feliz | Exportação completa dos dados | AQ-12 |
| AC-002-10 | Feliz | Cancelamento da organização | RN-008 |
| AC-002-11 | Erro | Remoção do último OWNER | RN-455 |
| AC-002-12 | Erro | Rebaixamento do último OWNER | RN-455 |
| AC-002-13 | Erro | Auto-alteração de papel | RN-456 |
| AC-002-14 | Erro | ADMIN age sobre OWNER | nota ¹ |
| AC-002-15 | Erro | Alteração de `slug` | RN-011 |
| AC-002-16 | Erro | `settings` fora da faixa | §6.2 |
| AC-002-17 | Erro | `timerAutoAbandon` menor que `timerLongRunning` | CX-09 |
| AC-002-18 | Erro | Conflito de `version` | RN-004 |
| AC-002-19 | Erro | Escrita em tenant suspenso | RN-007 |
| AC-002-20 | Erro | Cancelamento com senha incorreta | SG-04 |
| AC-002-21 | Erro | `size` acima de 100 na auditoria | RN-012 |
| AC-002-22 | Extremo | Alterar `roundingMinutes` não recalcula registros | CX-08 |
| AC-002-23 | Extremo | Alterar `timezone` não recalcula `workDate` | CX-07 |
| AC-002-24 | Extremo | Membro removido com timer pausado | CX-04 |
| AC-002-25 | Extremo | Convite para e-mail com membership removido | CX-06 |
| AC-002-26 | Extremo | Convite para membro já ativo | CX-05 |
| AC-002-27 | Extremo | Exportação de tenant com grande volume | CX-11 |
| AC-002-28 | Extremo | Cancelamento com período em `CLOSING` | CX-12 |
| AC-002-29 | Extremo | Auditoria sem filtro de período | CX-13 |
| AC-002-30 | Extremo | `notificationThresholds` desordenado | CX-10 |
| AC-002-31 | Segurança | Trilha de auditoria é imutável | INV-AUD-01 |
| AC-002-32 | Segurança | Membro de outro tenant retorna 404 | RN-002 |
| AC-002-33 | Segurança | Alteração de papel invalida os tokens do alvo | IMP-04 |
| AC-002-34 | Segurança | Exportação é auditada | SG-05 |
| AC-002-35 | Segurança | Avatar com conteúdo malicioso é rejeitado | RN-802 |
| AC-002-36 | Segurança | MEMBER não acessa gestão de equipe | §7 permissions |
| AC-002-37 | Concorrência | Dois ADMINs rebaixam o mesmo OWNER | CX-02 |
| AC-002-38 | Concorrência | Remoção e criação de work log simultâneas | RN-458 |
| AC-002-39 | Concorrência | Edição concorrente de `settings` | RN-004 |

---

## 3. Cenários felizes

### AC-002-01 — Edição de perfil
```gherkin
Dado que estou autenticado
Quando eu envio PATCH /api/v1/users/me com nome "Rafael Mendes Silva" e fuso "America/Manaus"
Então recebo 200 OK
E meu nome e fuso são atualizados
E o e-mail permanece inalterado
E a resposta não contém passwordHash
E um AuditLog com action "USER_PROFILE_UPDATED" registra apenas os campos alterados
```

### AC-002-02 — Alteração de preferências aplica o tema
```gherkin
Dado que minha preferência de tema é "SYSTEM"
Quando eu envio PATCH /api/v1/users/me/preferences com tema "DARK"
Então recebo 200 OK
E a preferência é persistida
E a interface aplica o tema escuro imediatamente, sem recarregar a página
```

### AC-002-03 — Edição de dados da organização
```gherkin
Dado que sou OWNER de um tenant com version igual a 3
Quando eu envio PATCH /api/v1/tenant com nome "Mendes Software", moeda "BRL" e version igual a 3
Então recebo 200 OK
E a version passa a 4
E um AuditLog com action "TENANT_UPDATED" registra o antes e o depois do nome
E o campo slug permanece inalterado
```

### AC-002-04 — Alteração de configurações operacionais
```gherkin
Dado que sou ADMIN de um tenant
Quando eu envio PATCH /api/v1/tenant/settings com retroactiveLimitDays igual a 15,
      workDayMinutes igual a 420 e notificationThresholds igual a [60, 90]
Então recebo 200 OK
E as três chaves são persistidas
E as demais chaves mantêm seus valores anteriores
E um AuditLog com action "TENANT_SETTINGS_UPDATED" registra as três chaves alteradas
E novos registros de horas passam a respeitar a janela de 15 dias
```

### AC-002-05 — Convite de membro
```gherkin
Dado que sou OWNER e possuo a permissão MEMBER_INVITE
Quando eu envio POST /api/v1/members/invitations com o e-mail "ana@exemplo.com" e o papel "MEMBER"
Então recebo 201 Created
E existe um Membership com status "INVITED" e invitedAt preenchido
E um e-mail de convite é enviado após o commit
E o token de convite expira em 7 dias
E um AuditLog com action "MEMBERSHIP_INVITED" foi gravado
```

### AC-002-06 — Alteração de papel
```gherkin
Dado um tenant com dois OWNERs ativos
E que sou um deles
Quando eu envio PATCH /api/v1/members/{id}/role rebaixando o outro OWNER para "MANAGER"
Então recebo 200 OK
E o papel do membro passa a "MANAGER"
E os access tokens desse usuário naquele tenant são invalidados
E o membro é notificado
E um AuditLog com action "MEMBERSHIP_ROLE_CHANGED" registra "OWNER" como antes e "MANAGER" como depois
```

### AC-002-07 — Remoção de membro preserva registros
```gherkin
Dado um membro "MEMBER" com 200 work logs, 5 tickets abertos e 1 timer em execução
Quando eu envio DELETE /api/v1/members/{id}
Então recebo 204 No Content
E o Membership passa a "REMOVED"
E os 200 work logs continuam existindo e aparecem nos relatórios
E o saldo dos contratos permanece inalterado
E o timer em execução passa a "DISCARDED"
E os 5 tickets abertos são reatribuídos ao OWNER
E os refresh tokens desse usuário naquele tenant são revogados
E o OWNER recebe uma notificação sobre o timer descartado
```

### AC-002-08 — Consulta da trilha de auditoria
```gherkin
Dado que sou ADMIN e que existem 50 registros de auditoria nos últimos 7 dias
Quando eu envio GET /api/v1/audit-logs com occurredAtFrom e occurredAtTo cobrindo esses 7 dias
Então recebo 200 OK com resultado paginado
E cada entrada contém ator, ação, entidade, estado anterior, estado posterior e instante
E o endereço IP aparece com o último octeto mascarado
E os registros estão ordenados do mais recente para o mais antigo
```

### AC-002-09 — Exportação completa dos dados
```gherkin
Dado que sou OWNER de um tenant com dados em todas as entidades
Quando eu envio GET /api/v1/tenant/export
Então recebo 202 Accepted com um executionId
E ao concluir recebo um e-mail com uma URL assinada
E a URL expira em 15 minutos
E o arquivo contém clientes, contratos, períodos, tickets, registros de horas, categorias e tags em formato aberto
E o arquivo não contém passwordHash nem hash de token
E um AuditLog com action "TENANT_EXPORT_REQUESTED" foi gravado
```

### AC-002-10 — Cancelamento da organização
```gherkin
Dado que sou OWNER e que nenhum período está em "CLOSING"
Quando eu envio POST /api/v1/tenant/cancel com a senha correta e o nome exato da organização
Então recebo 202 Accepted
E o tenant passa a ter status "CANCELLED"
E todos os refresh tokens do tenant são revogados
E a purga é agendada para daqui a 30 dias
E a exportação continua disponível durante a retenção
E um AuditLog com action "TENANT_CANCELLED" foi gravado
```

---

## 4. Cenários de erro

### AC-002-11 — Remoção do último OWNER
```gherkin
Dado um tenant com exatamente um OWNER ativo
Quando eu envio DELETE /api/v1/members/{id} para esse OWNER
Então recebo 409 Conflict com o código "DEVTIME-2455"
E o membership permanece "ACTIVE"
E a mensagem sugere promover outro membro a proprietário antes
```

### AC-002-12 — Rebaixamento do último OWNER
```gherkin
Dado um tenant com exatamente um OWNER ativo
Quando eu envio PATCH /api/v1/members/{id}/role alterando esse OWNER para "ADMIN"
Então recebo 409 Conflict com o código "DEVTIME-2455"
E o papel permanece "OWNER"
```

### AC-002-13 — Auto-alteração de papel
```gherkin
Dado que sou OWNER de um tenant com outros dois OWNERs ativos
Quando eu envio PATCH /api/v1/members/{meuId}/role alterando meu papel para "ADMIN"
Então recebo 403 Forbidden com o código "DEVTIME-2456"
E meu papel permanece "OWNER"
E o mesmo ocorre para ADMIN, MANAGER e MEMBER tentando alterar o próprio papel
```

### AC-002-14 — ADMIN age sobre OWNER
```gherkin
Dado que sou ADMIN
E que existe um membro com papel "OWNER"
Quando eu envio PATCH /api/v1/members/{ownerId}/role
Então recebo 403 Forbidden com o código "DEVTIME-1104"
Quando eu envio DELETE /api/v1/members/{ownerId}
Então recebo 403 Forbidden com o código "DEVTIME-1104"
Quando eu envio PATCH /api/v1/members/{outroId}/role promovendo alguém a "OWNER"
Então recebo 403 Forbidden com o código "DEVTIME-1104"
```

### AC-002-15 — Alteração de `slug`
```gherkin
Dado que sou OWNER
Quando eu envio PATCH /api/v1/tenant incluindo um campo "slug" com valor diferente do atual
Então recebo 422 Unprocessable Entity com o código "DEVTIME-2003"
E o slug permanece inalterado
```

### AC-002-16 — `settings` fora da faixa
```gherkin
Quando eu envio PATCH /api/v1/tenant/settings com workDayMinutes igual a 2000
Então recebo 400 Bad Request
E errors[] indica o campo "workDayMinutes" e a faixa permitida de 60 a 1440
E nenhuma chave é alterada
```

### AC-002-17 — `timerAutoAbandon` menor que `timerLongRunning`
```gherkin
Quando eu envio PATCH /api/v1/tenant/settings com timerLongRunningMinutes igual a 600
      e timerAutoAbandonMinutes igual a 480
Então recebo 400 Bad Request
E a mensagem informa que o limiar de abandono deve ser maior que o de alerta
E nenhuma chave é alterada
```

### AC-002-18 — Conflito de `version`
```gherkin
Dado que o tenant possui version igual a 5
Quando eu envio PATCH /api/v1/tenant com version igual a 4
Então recebo 409 Conflict com o código "DEVTIME-2004"
E a resposta informa a version atual
E nenhum campo é alterado
```

### AC-002-19 — Escrita em tenant suspenso
```gherkin
Dado que o tenant possui status "SUSPENDED"
Quando eu envio PATCH /api/v1/tenant/settings com dados válidos
Então recebo 403 Forbidden com o código "DEVTIME-1201"
Mas GET /api/v1/tenant retorna 200 OK
E GET /api/v1/tenant/export retorna 202 Accepted
```

### AC-002-20 — Cancelamento com senha incorreta
```gherkin
Dado que sou OWNER
Quando eu envio POST /api/v1/tenant/cancel com a senha incorreta
Então recebo 401 Unauthorized com o código "DEVTIME-1003"
E o tenant permanece "ACTIVE"
Quando eu envio com a senha correta e o nome da organização digitado errado
Então recebo 400 Bad Request
E o tenant permanece "ACTIVE"
```

### AC-002-21 — `size` acima de 100 na auditoria
```gherkin
Quando eu envio GET /api/v1/audit-logs com size igual a 500
Então recebo 400 Bad Request com o código "DEVTIME-2006"
E nenhuma consulta é executada
```

---

## 5. Cenários extremos

### AC-002-22 — Alterar `roundingMinutes` não recalcula registros
```gherkin
Dado um tenant com roundingMinutes igual a 0
E um work log existente com netMinutes igual a 112
Quando eu altero roundingMinutes para 15
Então recebo 200 OK
E o work log existente continua com netMinutes igual a 112
E o saldo do período permanece inalterado
E um novo work log de 112 minutos passa a ser registrado com 105
E a interface exibiu o aviso de que a alteração vale apenas para registros futuros
```

### AC-002-23 — Alterar `timezone` não recalcula `workDate`
```gherkin
Dado um tenant com fuso "America/Sao_Paulo"
E um work log com workDate igual a 2026-07-10
Quando eu altero o fuso do tenant para "America/Manaus"
Então recebo 200 OK
E o workDate do registro existente permanece 2026-07-10
E o período ao qual o registro pertence permanece o mesmo
E a interface exibiu o aviso correspondente
```

### AC-002-24 — Membro removido com timer pausado
```gherkin
Dado um membro com um timer em status "PAUSED" há 2 horas
Quando eu removo esse membro
Então o timer passa a "DISCARDED"
E nenhum work log é gerado
E o tempo decorrido é registrado apenas na auditoria
E o OWNER é notificado
```

### AC-002-25 — Convite para e-mail com membership removido
```gherkin
Dado um usuário cujo Membership neste tenant está "REMOVED"
Quando eu envio POST /api/v1/members/invitations com o e-mail desse usuário
Então recebo 201 Created
E um NOVO Membership com status "INVITED" é criado
E o Membership anterior com status "REMOVED" permanece no banco
E o histórico do vínculo anterior é preservado
```

### AC-002-26 — Convite para membro já ativo
```gherkin
Dado um usuário com Membership "ACTIVE" neste tenant
Quando eu envio POST /api/v1/members/invitations com o e-mail desse usuário
Então recebo 409 Conflict com o código "DEVTIME-2001"
E a mensagem informa que o usuário já participa da organização
E nenhum convite é enviado
```

### AC-002-27 — Exportação de tenant com grande volume
```gherkin
Dado um tenant com 500.000 registros de horas
Quando eu envio GET /api/v1/tenant/export
Então recebo 202 Accepted imediatamente
E o processamento ocorre de forma assíncrona
E o consumo de memória do processo permanece abaixo de 512 MB durante a geração
E ao concluir recebo o e-mail com a URL assinada
```

### AC-002-28 — Cancelamento com período em `CLOSING`
```gherkin
Dado que existe um ContractPeriod com status "CLOSING"
Quando eu envio POST /api/v1/tenant/cancel com credenciais corretas
Então recebo 409 Conflict com o código "DEVTIME-2010"
E o tenant permanece "ACTIVE"
E a mensagem informa que há um fechamento em andamento
```

### AC-002-29 — Auditoria sem filtro de período
```gherkin
Quando eu envio GET /api/v1/audit-logs sem informar occurredAtFrom nem occurredAtTo
Então recebo 200 OK
E o resultado abrange apenas os últimos 30 dias
E a resposta indica o período aplicado por padrão
E nenhuma partição anterior a 30 dias é varrida
```

### AC-002-30 — `notificationThresholds` desordenado
```gherkin
Quando eu envio PATCH /api/v1/tenant/settings com notificationThresholds igual a [100, 50, 80, 50]
Então recebo 200 OK
E o valor persistido é [50, 80, 100], ordenado e sem duplicatas
Quando eu envio com 6 valores distintos
Então recebo 400 Bad Request informando o máximo de 5 limiares
```

---

## 6. Cenários de segurança

### AC-002-31 — Trilha de auditoria é imutável
```gherkin
Dado que existem registros em audit_logs
Quando eu procuro por qualquer rota HTTP que permita PUT, PATCH ou DELETE sobre audit-logs
Então nenhuma rota desse tipo existe na aplicação
Quando a aplicação tenta executar UPDATE ou DELETE na tabela audit_logs
Então a operação é rejeitada por falta de permissão no banco
E a entidade AuditLog não possui os campos updatedAt nem deletedAt
```

### AC-002-32 — Membro de outro tenant retorna 404
```gherkin
Dado que estou autenticado no tenant A
E que existe um Membership com id M pertencente ao tenant B
Quando eu envio PATCH /api/v1/members/M/role
Então recebo 404 Not Found com o código "DEVTIME-2002"
E não recebo 403
E o membership do tenant B permanece inalterado
```

### AC-002-33 — Alteração de papel invalida os tokens do alvo
```gherkin
Dado um membro "MANAGER" com uma sessão ativa e um access token válido por mais 10 minutos
Quando eu altero seu papel para "MEMBER"
Então o access token corrente desse usuário naquele tenant deixa de ser aceito
E a próxima requisição desse usuário retorna 401
E após o refresh o novo token traz o papel "MEMBER"
```

### AC-002-34 — Exportação é auditada
```gherkin
Quando qualquer usuário solicita GET /api/v1/tenant/export
Então um AuditLog com action "TENANT_EXPORT_REQUESTED" é gravado com ator, IP e executionId
E o OWNER recebe uma notificação sobre a solicitação
E a URL de download expira em 15 minutos
```

### AC-002-35 — Avatar com conteúdo malicioso é rejeitado
```gherkin
Quando eu envio POST /api/v1/users/me/avatar com um arquivo SVG
Então recebo 415 Unsupported Media Type com o código "DEVTIME-2702"
Quando eu envio um executável renomeado para .png
Então recebo 415, pois a assinatura binária não corresponde ao contentType declarado
Quando eu envio um arquivo de 12 MB
Então recebo 413 com o código "DEVTIME-2701"
```

### AC-002-36 — MEMBER não acessa gestão de equipe
```gherkin
Dado que estou autenticado com papel "MEMBER"
Quando eu envio POST /api/v1/members/invitations
Então recebo 403 Forbidden com o código "DEVTIME-1101"
E requiredPermission é "MEMBER_INVITE"
Quando eu envio GET /api/v1/audit-logs
Então recebo 403 Forbidden com o código "DEVTIME-1101"
Mas GET /api/v1/members retorna 200 OK, pois MEMBER_VIEW é concedida a todos os papéis
E a interface não exibe as ações de convidar, alterar papel nem remover
```

---

## 7. Cenários de concorrência

### AC-002-37 — Dois ADMINs rebaixam o mesmo OWNER
```gherkin
Dado um tenant com exatamente dois OWNERs ativos
Quando duas requisições de rebaixamento, uma para cada OWNER, chegam simultaneamente
Então exatamente uma recebe 200 OK
E a outra recebe 409 Conflict com o código "DEVTIME-2455"
E o tenant permanece com pelo menos um OWNER ativo
```

### AC-002-38 — Remoção e criação de work log simultâneas
```gherkin
Dado um membro ativo registrando horas
Quando a remoção desse membro e a criação de um work log por ele chegam simultaneamente
Então ou o work log é criado antes da remoção e é preservado,
      ou a criação falha com 403 por membership inativo
E em nenhum caso um work log órfão é persistido
E em nenhum caso um work log já criado é perdido
```

### AC-002-39 — Edição concorrente de `settings`
```gherkin
Dado que dois administradores carregaram o tenant com version igual a 7
Quando ambos enviam PATCH /api/v1/tenant/settings com version igual a 7
Então exatamente um recebe 200 OK e a version passa a 8
E o outro recebe 409 Conflict com o código "DEVTIME-2004"
E nenhuma alteração é perdida silenciosamente
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-002 | AC-002-32 | ✅ |
| RN-003 | AC-002-07, AC-002-25 | ✅ |
| RN-004 | AC-002-18, AC-002-39 | ✅ |
| RN-006 | AC-002-01, AC-002-03, AC-002-04, AC-002-06 | ✅ |
| RN-007 | AC-002-19 | ✅ |
| RN-008 | AC-002-10 | ✅ |
| RN-011 | AC-002-15 | ✅ |
| RN-012 | AC-002-21 | ✅ |
| RN-455 | AC-002-11, AC-002-12, AC-002-37 | ✅ |
| RN-456 | AC-002-13 | ✅ |
| RN-457 | AC-002-05, AC-002-25 | ✅ |
| RN-458 | AC-002-07, AC-002-38 | ✅ |
| RN-460 | AC-002-07, AC-002-24 | ✅ |
| RN-802 | AC-002-35 | ✅ |
| INV-AUD-01 | AC-002-31 | ✅ |
| INV-MEM-01 | AC-002-26 | ✅ |
| INV-MEM-02 | AC-002-11, AC-002-12, AC-002-37 | ✅ |
| INV-TEN-03 | AC-002-23 | ✅ |
| IMP-04 | AC-002-33 | ✅ |
| ART-005 | AC-002-22, AC-002-23 | ✅ |
| AQ-12 | AC-002-09, AC-002-27 | ✅ |
| nota ¹ permissions | AC-002-14 | ✅ |
