# 013 — Notifications · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-013-XX`, estável e imutável |
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
| AC-013-01 | Feliz | Alerta de limiar de consumo | RN-602, RN-603 |
| AC-013-02 | Feliz | Alerta de excedente | RN-604 |
| AC-013-03 | Feliz | Destinatários de contrato e período | RN-607 |
| AC-013-04 | Feliz | Destinatário de timer é o dono | RN-607 |
| AC-013-05 | Feliz | Notificação de fechamento concluído | §6.1 |
| AC-013-06 | Feliz | Aviso de fechamento iminente | RN-605 |
| AC-013-07 | Feliz | Aviso de contrato terminando | RN-606 |
| AC-013-08 | Feliz | Notificação de ticket atribuído | §6.1 |
| AC-013-09 | Feliz | Central com listagem e contagem | §7 notifications.md |
| AC-013-10 | Feliz | Marcar como lida e como não lida | §8 |
| AC-013-11 | Feliz | Marcar todas como lidas | §8.2 |
| AC-013-12 | Feliz | Exclusão de notificação | RN-003 |
| AC-013-13 | Feliz | Entrega pelo fluxo em tempo real | §7.2 |
| AC-013-14 | Feliz | Alteração de preferências | §9 |
| AC-013-15 | Erro | Notificação de outro usuário | §16 |
| AC-013-16 | Erro | Tipo inválido em preferências | §9 |
| AC-013-17 | Erro | `size` acima de 100 | RN-012 |
| AC-013-18 | Erro | Nenhuma rota de criação | §14 |
| AC-013-19 | Extremo | Cem avaliações do mesmo limiar | RN-601 |
| AC-013-20 | Extremo | Consumo oscilando | §6.3, CE-11 |
| AC-013-21 | Extremo | Salto direto de 0% a 105% | CX-03 |
| AC-013-22 | Extremo | Limiares personalizados | CX-05 |
| AC-013-23 | Extremo | Contrato `HOURLY_OPEN` | CX-04 |
| AC-013-24 | Extremo | Ajuste que zera o excedente | CX-07 |
| AC-013-25 | Extremo | Período reaberto e refechado | CX-08 |
| AC-013-26 | Extremo | Dois `OWNER` no tenant | CX-09 |
| AC-013-27 | Extremo | Tipo silenciado após a criação | CX-12 |
| AC-013-28 | Extremo | Notificação lida há exatamente 90 dias | CX-16 |
| AC-013-29 | Extremo | Notificação não lida há dois anos | CX-17 |
| AC-013-30 | Extremo | Usuário com 5.000 notificações | CX-15 |
| AC-013-31 | Extremo | Job de lembrete executado duas vezes | CX-24 |
| AC-013-32 | Extremo | Menção a membro inativo | CX-23 |
| AC-013-33 | Segurança | In-app criada mesmo com tipo silenciado | RN-608 |
| AC-013-34 | Segurança | Falha de e-mail não reverte a in-app | RN-610 |
| AC-013-35 | Segurança | Fluxo isolado por destinatário | SG-03 |
| AC-013-36 | Segurança | `dedupeKey` não é exposto | CP-11 |
| AC-013-37 | Segurança | Corpo do e-mail sem dado sensível | §19.1 |
| AC-013-38 | Segurança | Notificação de outro tenant retorna 404 | RN-002 |
| AC-013-39 | Concorrência | Avaliações simultâneas do mesmo limiar | RN-601 |
| AC-013-40 | Concorrência | Reconexão do fluxo não perde notificação | ST-05 |
| AC-013-41 | Concorrência | Três tentativas de e-mail esgotadas | RN-610 |

---

## 3. Cenários felizes

### AC-013-01 — Alerta de limiar de consumo
```gherkin
Dado um contrato com notificationThresholds igual a 50, 80 e 100
E um período cujo consumo atinge 83% do saldo disponível
Quando o evento de alteração de consumo é processado
Então duas notificações são criadas para cada OWNER e ADMIN ativo do tenant
E uma possui dedupeKey "CONTRACT_USAGE:{periodId}:50" com severidade INFO
E a outra possui dedupeKey "CONTRACT_USAGE:{periodId}:80" com severidade WARNING
E ambas apontam para o período como entidade de origem
```

### AC-013-02 — Alerta de excedente
```gherkin
Dado um período cujo consumo atinge 105% do saldo disponível
Quando o evento de alteração de consumo é processado
Então uma notificação com dedupeKey "CONTRACT_USAGE:{periodId}:100" é criada
E também uma com dedupeKey "CONTRACT_OVERAGE:{periodId}"
E a notificação de excedente possui severidade CRITICAL
```

### AC-013-03 — Destinatários de contrato e período
```gherkin
Dado um tenant com um OWNER, dois ADMIN, um MANAGER, um MEMBER e um VIEWER, todos ativos
Quando um alerta de consumo é gerado
Então três notificações são criadas: uma para o OWNER e uma para cada ADMIN
E nenhuma notificação é criada para MANAGER, MEMBER ou VIEWER
```

### AC-013-04 — Destinatário de timer é o dono
```gherkin
Dado um cronômetro de um MEMBER que ultrapassa o limiar de timer longo
Quando o evento é processado
Então exatamente uma notificação é criada, para o MEMBER dono do cronômetro
E nenhuma notificação é criada para OWNER ou ADMIN
```

### AC-013-05 — Notificação de fechamento concluído
```gherkin
Dado um período que é fechado com sucesso
Quando o evento de fechamento é processado
Então uma notificação com dedupeKey "PERIOD_CLOSED:{periodId}" é criada
E os destinatários são OWNER e ADMIN
E a severidade é INFO
```

### AC-013-06 — Aviso de fechamento iminente
```gherkin
Dado um período cujo endDate é daqui a exatamente 3 dias
Quando o job de lembrete é executado
Então uma notificação com dedupeKey "PERIOD_CLOSING:{periodId}" é criada
E os destinatários são OWNER e ADMIN
```

### AC-013-07 — Aviso de contrato terminando
```gherkin
Dado um contrato cujo endDate é daqui a exatamente 15 dias
Quando o job de lembrete é executado
Então uma notificação com dedupeKey "CONTRACT_ENDING:{contractId}" é criada
E a severidade é WARNING
```

### AC-013-08 — Notificação de ticket atribuído
```gherkin
Dado um ticket sem responsável
Quando eu atribuo o ticket a um membro ativo
Então uma notificação com dedupeKey "TICKET_ASSIGNED:{ticketId}:{assigneeId}" é criada
E o destinatário é o novo responsável
Quando eu reatribuo o ticket a outro membro
Então uma nova notificação é criada para o novo responsável
E o responsável anterior não é notificado da remoção
```

### AC-013-09 — Central com listagem e contagem
```gherkin
Dado que eu possuo 12 notificações, das quais 4 não lidas
Quando eu envio GET /api/v1/notifications
Então recebo 200 OK com as 12 notificações, mais recentes primeiro
Quando eu envio GET /api/v1/notifications/unread-count
Então recebo 200 OK com count igual a 4
```

### AC-013-10 — Marcar como lida e como não lida
```gherkin
Dado uma notificação não lida
Quando eu envio POST /api/v1/notifications/{id}/read
Então recebo 200 OK
E readAt é preenchido
E a contagem de não lidas é decrementada
Quando eu envio POST /api/v1/notifications/{id}/unread
Então readAt volta a ser nulo
E a contagem é incrementada novamente
```

### AC-013-11 — Marcar todas como lidas
```gherkin
Dado que eu possuo 30 notificações não lidas
Quando eu envio POST /api/v1/notifications/read-all
Então recebo 200 OK
E todas as 30 recebem readAt preenchido
E a contagem de não lidas passa a 0
E notificações de outros usuários não são afetadas
```

### AC-013-12 — Exclusão de notificação
```gherkin
Dado uma notificação minha
Quando eu envio DELETE /api/v1/notifications/{id}
Então recebo 204 No Content
E a notificação recebe deletedAt preenchido
E ela deixa de aparecer na listagem
E ela permanece fisicamente na base
```

### AC-013-13 — Entrega pelo fluxo em tempo real
```gherkin
Dado que eu estou conectado ao fluxo GET /api/v1/notifications/stream
Quando uma notificação é criada para mim
Então eu recebo um evento no fluxo com o identificador, o tipo, a severidade,
      o título e a nova contagem de não lidas
E a notificação também está disponível em GET /api/v1/notifications
```

### AC-013-14 — Alteração de preferências
```gherkin
Quando eu envio PATCH /api/v1/notifications/preferences silenciando o tipo CONTRACT_USAGE_50
Então recebo 200 OK
E a preferência é persistida
Quando um alerta desse tipo é gerado para mim
Então a notificação in-app é criada normalmente
Mas nenhum e-mail é enviado
```

---

## 4. Cenários de erro

### AC-013-15 — Notificação de outro usuário
```gherkin
Dado uma notificação cujo destinatário é outro usuário do mesmo tenant
Quando eu envio POST de leitura ou DELETE nessa notificação
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
E a notificação não aparece na minha listagem
```

### AC-013-16 — Tipo inválido em preferências
```gherkin
Quando eu envio PATCH /api/v1/notifications/preferences com um tipo inexistente
      em mutedNotificationTypes
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E nenhuma preferência é alterada
```

### AC-013-17 — `size` acima de 100
```gherkin
Quando eu envio GET /api/v1/notifications com size igual a 200
Então recebo 400 Bad Request com o código DEVTIME-2006
```

### AC-013-18 — Nenhuma rota de criação
```gherkin
Quando eu inspeciono todas as rotas expostas pela aplicação
Então não existe nenhuma rota POST que crie uma notificação diretamente
E notificações nascem exclusivamente de eventos de domínio
E uma tentativa de POST em /api/v1/notifications retorna 404 ou 405
```

---

## 5. Cenários extremos

### AC-013-19 — Cem avaliações do mesmo limiar
```gherkin
Dado um período cujo consumo já ultrapassou o limiar de 80%
Quando 100 alterações de consumo são processadas ao longo do dia
Então exatamente uma notificação com dedupeKey "CONTRACT_USAGE:{periodId}:80"
      existe por destinatário
E nenhum erro é retornado em nenhuma das 99 tentativas seguintes
```

### AC-013-20 — Consumo oscilando
```gherkin
Dado um período cujo consumo atinge 82% e gera alertas de 50% e 80%
Quando registros são excluídos e o consumo cai para 70%
Então as notificações anteriores permanecem inalteradas
E nenhuma notificação é removida
Quando o consumo volta a 85%
Então nenhuma notificação nova é criada
```

### AC-013-21 — Salto direto de 0% a 105%
```gherkin
Dado um período com consumo zero e limiares 50, 80 e 100
Quando um único registro de horas leva o consumo a 105%
Então quatro notificações são criadas na mesma avaliação
E são os limiares de 50%, 80% e 100%, mais o excedente
E nenhuma é omitida por ter sido ultrapassada junto com as outras
```

### AC-013-22 — Limiares personalizados
```gherkin
Dado um contrato com notificationThresholds igual a 70 e 90
Quando o consumo atinge 75%
Então uma notificação com dedupeKey terminando em ":70" é criada
E nenhuma com ":50" ou ":80" é criada
Quando o consumo atinge 92%
Então uma notificação terminando em ":90" é criada
```

### AC-013-23 — Contrato `HOURLY_OPEN`
```gherkin
Dado um contrato do tipo HOURLY_OPEN
Quando 600 minutos faturáveis são registrados
Então nenhuma notificação de consumo é criada
E nenhuma notificação de excedente é criada
```

### AC-013-24 — Ajuste que zera o excedente
```gherkin
Dado um período com excedente e uma notificação CONTRACT_OVERAGE já criada
Quando um ajuste de saldo elimina completamente o excedente
Então a notificação de excedente permanece no histórico
E nenhuma notificação é removida
E nenhuma notificação nova é criada
```

### AC-013-25 — Período reaberto e refechado
```gherkin
Dado um período que já gerou a notificação "PERIOD_CLOSED:{periodId}"
Quando o período é reaberto e fechado novamente
Então nenhuma notificação nova de fechamento é criada
E a chave de deduplicação já existente impede a duplicata
```

### AC-013-26 — Dois `OWNER` no tenant
```gherkin
Dado um tenant com dois usuários no papel OWNER
Quando um alerta de consumo é gerado
Então duas notificações são criadas, uma para cada OWNER
E ambas possuem o mesmo dedupeKey
E nenhuma viola a unicidade, pois ela é por destinatário
```

### AC-013-27 — Tipo silenciado após a criação
```gherkin
Dado uma notificação já criada e um e-mail já enviado
Quando eu silencio esse tipo nas minhas preferências
Então a notificação existente permanece na central
E o e-mail já enviado não é revertido
E apenas notificações futuras desse tipo deixam de gerar e-mail
```

### AC-013-28 — Notificação lida há exatamente 90 dias
```gherkin
Dado uma notificação cujo readAt é de exatamente 90 dias atrás
Quando o job de limpeza é executado
Então a notificação não é removida
Quando o readAt passa a ser de 91 dias atrás e o job executa novamente
Então a notificação é removida
```

### AC-013-29 — Notificação não lida há dois anos
```gherkin
Dado uma notificação criada há dois anos e nunca lida
Quando o job de limpeza é executado repetidamente
Então a notificação nunca é removida
E ela continua contando na contagem de não lidas
```

### AC-013-30 — Usuário com 5.000 notificações
```gherkin
Dado um usuário com 5.000 notificações, das quais 3 não lidas
Quando ele consulta a contagem de não lidas
Então a resposta é 3
E o tempo de resposta permanece abaixo de 50 milissegundos
Quando ele lista as notificações
Então a listagem é paginada e responde dentro da meta
```

### AC-013-31 — Job de lembrete executado duas vezes
```gherkin
Dado um período cujo fechamento é iminente
Quando o job de lembrete é executado duas vezes no mesmo dia
Então exatamente uma notificação de fechamento iminente existe por destinatário
E a idempotência vem da chave de deduplicação, não de controle próprio do job
```

### AC-013-32 — Menção a membro inativo
```gherkin
Dado um comentário mencionando um membro cujo membership está suspenso
Quando o evento de comentário é processado
Então nenhuma notificação é criada para esse membro
E menções a membros ativos geram notificação normalmente
```

---

## 6. Cenários de segurança

### AC-013-33 — In-app criada mesmo com tipo silenciado
```gherkin
Dado que eu silenciei o tipo CONTRACT_OVERAGE
Quando um excedente ocorre em um contrato do meu tenant
Então a notificação in-app é criada e aparece na minha central
E nenhum e-mail é enviado
E a preferência silenciou o canal, não o registro
```

### AC-013-34 — Falha de e-mail não reverte a in-app
```gherkin
Dado que o provedor de e-mail está indisponível
Quando uma notificação é gerada para mim
Então a notificação in-app é criada normalmente
E ela aparece na minha central imediatamente
E o envio do e-mail é reagendado
E nenhuma parte da notificação é revertida
```

### AC-013-35 — Fluxo isolado por destinatário
```gherkin
Dado dois usuários do mesmo tenant, ambos conectados ao fluxo
Quando uma notificação é criada para o primeiro
Então apenas o primeiro recebe o evento no seu fluxo
E o segundo não recebe nada
E nenhum dado da notificação do primeiro chega ao segundo
```

### AC-013-36 — `dedupeKey` não é exposto
```gherkin
Quando eu consulto qualquer notificação pela API
Então o campo dedupeKey não está presente na resposta
E nenhum endpoint expõe a chave de deduplicação
```

### AC-013-37 — Corpo do e-mail sem dado sensível
```gherkin
Dado um alerta de consumo de um contrato com valor hora definido
Quando o e-mail é gerado
Então o corpo informa o nome do contrato e o percentual atingido
E não contém nenhuma descrição de registro de horas
E não contém nenhum valor monetário
E contém um link que leva ao sistema para consultar o detalhe
```

### AC-013-38 — Notificação de outro tenant retorna 404
```gherkin
Dado uma notificação pertencente a um usuário do tenant B
E que estou autenticado no tenant A
Quando eu tento acessá-la por id
Então recebo 404 Not Found com o código DEVTIME-2002
```

---

## 7. Cenários de concorrência

### AC-013-39 — Avaliações simultâneas do mesmo limiar
```gherkin
Dado um período cujo consumo acaba de ultrapassar 80%
Quando 100 avaliações do mesmo limiar são processadas em paralelo
Então exatamente uma notificação é criada por destinatário
E as 99 tentativas restantes são ignoradas silenciosamente
E nenhuma exceção é propagada ao chamador
E a rejeição ocorre pela constraint do banco, não apenas por verificação prévia
```

### AC-013-40 — Reconexão do fluxo não perde notificação
```gherkin
Dado que eu estou conectado ao fluxo
Quando a conexão cai e três notificações são criadas para mim durante a queda
E eu reconecto ao fluxo
Então a aplicação recarrega o histórico e a contagem de não lidas
E as três notificações aparecem na minha central
E nenhuma delas é perdida
```

### AC-013-41 — Três tentativas de e-mail esgotadas
```gherkin
Dado que o provedor de e-mail falha em todas as tentativas
Quando o job de reprocessamento executa a primeira, a segunda e a terceira tentativa
Então nenhuma quarta tentativa é feita
E a notificação in-app permanece intacta e legível na central
E um log de nível ERROR registra que o destinatário não foi alcançado por e-mail
E a métrica de esgotamento é incrementada
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-601 | AC-013-19, AC-013-25, AC-013-26, AC-013-31, AC-013-39 | ✅ |
| RN-602 | AC-013-01, AC-013-20, AC-013-21 | ✅ |
| RN-603 | AC-013-01, AC-013-22, AC-013-26 | ✅ |
| RN-604 | AC-013-02, AC-013-21 | ✅ |
| RN-605 | AC-013-06, AC-013-31 | ✅ |
| RN-606 | AC-013-07 | ✅ |
| RN-607 | AC-013-03, AC-013-04, AC-013-08, AC-013-32 | ✅ |
| RN-608 | AC-013-14, AC-013-27, AC-013-33 | ✅ |
| RN-609 | AC-013-28, AC-013-29 | ✅ |
| RN-610 | AC-013-34, AC-013-41 | ✅ |
| RN-012 | AC-013-17 | ✅ |
| RN-003 | AC-013-12 | ✅ |
| RN-002 | AC-013-38 | ✅ |
| INV-NOT-01 | AC-013-19, AC-013-26, AC-013-39 | ✅ |
| INV-NOT-02 | AC-013-33, AC-013-34 | ✅ |
| INV-NOT-03 | AC-013-19, AC-013-20 | ✅ |
| INV-NOT-04 | AC-013-13, AC-013-40 | ✅ |
| INV-NOT-05 | AC-013-34, AC-013-41 | ✅ |
| §6.1 matriz | AC-013-01 a AC-013-08, AC-013-22 | ✅ |
| §6.3 oscilação | AC-013-20, AC-013-24 | ✅ |
| §14 sem rota de criação | AC-013-18 | ✅ |
| §16 escopo do destinatário | AC-013-15, AC-013-35, AC-013-38 | ✅ |
| §19.1 conteúdo | AC-013-37 | ✅ |
| CE-10 / CE-11 / CE-14 | AC-013-23, AC-013-20, AC-013-24 | ✅ |
| CP-11 | AC-013-36 | ✅ |
| SG-03 | AC-013-35 | ✅ |
| ST-05 | AC-013-13, AC-013-40 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. `AC-013-19`, `AC-013-20` e `AC-013-39` cobrem juntas o comportamento central da feature — a deduplicação sob repetição, oscilação e concorrência.
