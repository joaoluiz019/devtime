# 014 — Comments · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-014-XX`, estável e imutável |
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
| AC-014-01 | Feliz | Criação de comentário | RN-811 |
| AC-014-02 | Feliz | Menção a membro ativo notifica | RN-813 |
| AC-014-03 | Feliz | Resposta a comentário raiz | RN-814 |
| AC-014-04 | Feliz | Edição pelo autor dentro da janela | RN-812 |
| AC-014-05 | Feliz | Exclusão pelo autor | RN-812 |
| AC-014-06 | Feliz | Exclusão por moderador | RN-812 |
| AC-014-07 | Feliz | Comentário de sistema em mudança de status | RN-815 |
| AC-014-08 | Feliz | Listagem com respostas agrupadas | §11 tickets.md |
| AC-014-09 | Feliz | `canEdit` e `canDelete` vêm do servidor | §23 |
| AC-014-10 | Erro | Corpo vazio | RN-811 |
| AC-014-11 | Erro | Corpo acima de 10.000 caracteres | RN-811 |
| AC-014-12 | Erro | Edição após 24 horas | RN-812 |
| AC-014-13 | Erro | `ADMIN` tenta editar comentário de terceiro | §6.3 |
| AC-014-14 | Erro | Edição de comentário de sistema | RN-815 |
| AC-014-15 | Erro | Resposta a comentário de outro ticket | INV-CMT-02 |
| AC-014-16 | Erro | `VIEWER` tenta comentar | §7 permissions |
| AC-014-17 | Extremo | Corpo com 1 e com 10.000 caracteres | CX-01 |
| AC-014-18 | Extremo | Corpo só com espaços | CX-02 |
| AC-014-19 | Extremo | Resposta a uma resposta | CX-03 |
| AC-014-20 | Extremo | Menção a membro suspenso | CX-07 |
| AC-014-21 | Extremo | Menção inexistente | FA-04 |
| AC-014-22 | Extremo | Menção repetida | CX-05 |
| AC-014-23 | Extremo | Auto-menção | CX-06 |
| AC-014-24 | Extremo | Padrão de e-mail no corpo | CX-16 |
| AC-014-25 | Extremo | Edição em exatamente 24 horas | CX-09 |
| AC-014-26 | Extremo | Edição alterando menções | CX-10 |
| AC-014-27 | Extremo | Exclusão de raiz com respostas | CX-08 |
| AC-014-28 | Extremo | Autor removido do tenant | CX-11 |
| AC-014-29 | Extremo | Ticket com 500 comentários | CX-13 |
| AC-014-30 | Segurança | Comentário de outro tenant retorna 404 | RN-002 |
| AC-014-31 | Segurança | XSS por Markdown | SG-05 |
| AC-014-32 | Segurança | `authorId` forjado é ignorado | SG-07 |
| AC-014-33 | Segurança | `isSystem` forjado é ignorado | SG-08 |
| AC-014-34 | Segurança | Autocompletar não revela membros suspensos | SG-06 |
| AC-014-35 | Segurança | Corpo nunca aparece em log | §28 |
| AC-014-36 | Concorrência | Comentário de sistema com rollback da transição | RN-815 |
| AC-014-37 | Concorrência | Edições simultâneas do mesmo comentário | RN-004 |
| AC-014-38 | Concorrência | Exclusão e resposta simultâneas | CX-08 |

---

## 3. Cenários felizes

### AC-014-01 — Criação de comentário
```gherkin
Dado um ticket existente no meu tenant
E que estou autenticado com a permissão COMMENT_CREATE
Quando eu envio POST /api/v1/tickets/{id}/comments com um corpo de 200 caracteres
Então recebo 201 Created
E o comentário é criado com isSystem igual a falso
E authorId igual ao meu usuário
E parentCommentId nulo
E editedAt nulo
E um AuditLog com action "COMMENT_CREATED" foi gravado
```

### AC-014-02 — Menção a membro ativo notifica
```gherkin
Dado um membro ativo cujo identificador de exibição é "ana"
Quando eu crio um comentário com o corpo "@ana pode revisar isso?"
Então recebo 201 Created
E mentionedUserIds contém o identificador de Ana
E um evento de comentário criado é publicado após o commit
E Ana recebe uma notificação de menção
E o responsável do ticket também é notificado
```

### AC-014-03 — Resposta a comentário raiz
```gherkin
Dado um comentário raiz no ticket
Quando eu crio um comentário informando esse comentário como parentCommentId
Então recebo 201 Created
E parentCommentId aponta para o comentário raiz
E a listagem agrupa a resposta sob a raiz
```

### AC-014-04 — Edição pelo autor dentro da janela
```gherkin
Dado um comentário meu criado há 2 horas
Quando eu envio PATCH /api/v1/comments/{id} alterando o corpo, com a version correta
Então recebo 200 OK
E o corpo é atualizado
E editedAt é preenchido
E a interface indica que o comentário foi editado
E um AuditLog com action "COMMENT_UPDATED" registra o corpo anterior completo
```

### AC-014-05 — Exclusão pelo autor
```gherkin
Dado um comentário meu criado há 1 hora, sem respostas
Quando eu envio DELETE /api/v1/comments/{id}
Então recebo 204 No Content
E o comentário recebe deletedAt preenchido
E ele deixa de aparecer na listagem
E ele permanece fisicamente na base
```

### AC-014-06 — Exclusão por moderador
```gherkin
Dado um comentário de outro usuário criado há 5 dias
E que estou autenticado com a permissão COMMENT_DELETE_ANY
Quando eu envio DELETE /api/v1/comments/{id}
Então recebo 204 No Content
E o comentário é excluído logicamente
E um AuditLog registra quem excluiu e que não era o autor
```

### AC-014-07 — Comentário de sistema em mudança de status
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando o ticket é transicionado para "BLOCKED" com um motivo de impedimento
Então um comentário com isSystem igual a verdadeiro é criado no ticket
E ele registra a mudança de status e o motivo
E ele foi criado na mesma transação da transição
E ele aparece na linha do tempo do ticket
```

### AC-014-08 — Listagem com respostas agrupadas
```gherkin
Dado um ticket com 3 comentários raiz, um deles com 4 respostas
Quando eu envio GET /api/v1/tickets/{id}/comments
Então recebo 200 OK
E os 3 raízes são retornados em ordem cronológica
E as 4 respostas vêm agrupadas sob o raiz correspondente
E a estrutura possui no máximo dois níveis
E todas as respostas foram carregadas em uma única consulta
```

### AC-014-09 — `canEdit` e `canDelete` vêm do servidor
```gherkin
Dado comentários em situações distintas: meu com 1h, meu com 30h,
      de terceiro, e de sistema
Quando eu listo os comentários como MEMBER autor de dois deles
Então cada comentário traz canEdit e canDelete calculados pelo servidor
E o meu de 1h traz canEdit verdadeiro
E o meu de 30h traz canEdit falso
E o de terceiro e o de sistema trazem canEdit falso
E o comportamento real das operações coincide com esses valores
```

---

## 4. Cenários de erro

### AC-014-10 — Corpo vazio
```gherkin
Quando eu envio POST de comentário com o corpo vazio
Então recebo 422 Unprocessable Entity com o código DEVTIME-2705
E nenhum comentário é criado
```

### AC-014-11 — Corpo acima de 10.000 caracteres
```gherkin
Quando eu envio POST de comentário com 10.001 caracteres
Então recebo 422 Unprocessable Entity com o código DEVTIME-2705
E a mensagem indica o limite de 10.000 caracteres
```

### AC-014-12 — Edição após 24 horas
```gherkin
Dado um comentário meu criado há 25 horas
Quando eu tento editá-lo
Então recebo 403 Forbidden com o código DEVTIME-1101
E o corpo permanece inalterado
E editedAt permanece nulo
```

### AC-014-13 — `ADMIN` tenta editar comentário de terceiro
```gherkin
Dado um comentário de outro usuário criado há 1 hora
E que estou autenticado com o papel ADMIN
Quando eu tento editar esse comentário
Então recebo 403 Forbidden com o código DEVTIME-1101
E o corpo permanece inalterado
Mas eu consigo excluí-lo com sucesso
```

### AC-014-14 — Edição de comentário de sistema
```gherkin
Dado um comentário com isSystem igual a verdadeiro
Quando eu tento editá-lo ou excluí-lo, como autor, como ADMIN ou como OWNER
Então recebo 403 Forbidden com o código DEVTIME-1101 em todos os casos
E o comentário permanece intacto
```

### AC-014-15 — Resposta a comentário de outro ticket
```gherkin
Dado um comentário pertencente a outro ticket do mesmo tenant
Quando eu tento criar uma resposta informando esse comentário como pai
Então recebo 422 Unprocessable Entity com o código DEVTIME-2706
E nenhum comentário é criado
```

### AC-014-16 — `VIEWER` tenta comentar
```gherkin
Dado que estou autenticado com o papel VIEWER
Quando eu envio POST de comentário
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica COMMENT_CREATE
Mas eu consigo ler os comentários normalmente
```

---

## 5. Cenários extremos

### AC-014-17 — Corpo com 1 e com 10.000 caracteres
```gherkin
Quando eu crio um comentário com exatamente 1 caractere
Então recebo 201 Created
Quando eu crio um comentário com exatamente 10.000 caracteres
Então recebo 201 Created
Quando eu crio um comentário com 10.001 caracteres
Então recebo 422 com o código DEVTIME-2705
```

### AC-014-18 — Corpo só com espaços
```gherkin
Quando eu crio um comentário cujo corpo contém apenas espaços e saltos de linha
Então recebo 422 Unprocessable Entity com o código DEVTIME-2705
E a validação foi aplicada após aparar as bordas
```

### AC-014-19 — Resposta a uma resposta
```gherkin
Dado um comentário raiz A e uma resposta B vinculada a A
Quando eu crio um comentário informando B como parentCommentId
Então recebo 201 Created
E o parentCommentId persistido aponta para A, não para B
E a estrutura permanece com no máximo dois níveis
E a interface indica a quem a resposta se dirige
```

### AC-014-20 — Menção a membro suspenso
```gherkin
Dado um membro cujo membership está suspenso, com identificador "bruno"
Quando eu crio um comentário com o corpo "@bruno confere?"
Então recebo 201 Created
E mentionedUserIds não contém o identificador de Bruno
E nenhuma notificação é enviada a ele
E o texto "@bruno" permanece visível no corpo do comentário
```

### AC-014-21 — Menção inexistente
```gherkin
Quando eu crio um comentário com o corpo "@carlos", sendo que carlos não existe
Então recebo 201 Created
E mentionedUserIds está vazio
E nenhum erro é retornado
E o texto permanece como escrito
```

### AC-014-22 — Menção repetida
```gherkin
Quando eu crio um comentário com o corpo "@ana e depois @ana de novo"
Então recebo 201 Created
E mentionedUserIds contém o identificador de Ana exatamente uma vez
E Ana recebe exatamente uma notificação
```

### AC-014-23 — Auto-menção
```gherkin
Dado que o meu identificador de exibição é "ana"
Quando eu crio um comentário com o corpo "@ana anotando para mim"
Então recebo 201 Created
E mentionedUserIds contém o meu identificador
Mas nenhuma notificação de menção é enviada a mim
```

### AC-014-24 — Padrão de e-mail no corpo
```gherkin
Quando eu crio um comentário com o corpo "enviar para contato@cliente.com"
Então recebo 201 Created
E mentionedUserIds está vazio
E nenhuma parte do endereço é tratada como menção
```

### AC-014-25 — Edição em exatamente 24 horas
```gherkin
Dado um comentário meu criado há exatamente 24 horas
Quando eu tento editá-lo
Então recebo 403 Forbidden com o código DEVTIME-1101
Quando eu tento editar outro criado há 23 horas e 59 minutos
Então recebo 200 OK
E o limite é estritamente menor que 24 horas
```

### AC-014-26 — Edição alterando menções
```gherkin
Dado um comentário meu mencionando Ana, criado há 1 hora
Quando eu o edito adicionando uma menção a Bruno, mantendo a de Ana
Então recebo 200 OK
E mentionedUserIds passa a conter Ana e Bruno
E Bruno recebe uma notificação de menção
E Ana não é notificada novamente
```

### AC-014-27 — Exclusão de raiz com respostas
```gherkin
Dado um comentário raiz com 3 respostas
Quando o comentário raiz é excluído
Então recebo 204 No Content
E o raiz recebe deletedAt preenchido
E as 3 respostas permanecem visíveis na listagem
E a interface exibe um marcador de comentário removido no lugar do original
```

### AC-014-28 — Autor removido do tenant
```gherkin
Dado comentários de um membro que é removido do tenant
Quando eu listo os comentários do ticket
Então os comentários permanecem visíveis
E o nome do autor é exibido como "Usuário Removido"
E o vínculo com o identificador do usuário é preservado
```

### AC-014-29 — Ticket com 500 comentários
```gherkin
Dado um ticket com 500 comentários, entre raízes e respostas
Quando eu listo os comentários
Então a listagem é paginada por cursor
E as respostas de cada página vêm carregadas com as respectivas raízes
E o tempo de resposta permanece constante entre a primeira e a última página
```

---

## 6. Cenários de segurança

### AC-014-30 — Comentário de outro tenant retorna 404
```gherkin
Dado um comentário pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu tento editá-lo ou excluí-lo por id
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-014-31 — XSS por Markdown
```gherkin
Dado um comentário cujo corpo contém "<script>alert(1)</script>",
      "<iframe src=x>" e um link com esquema javascript
Quando o comentário é renderizado na interface
Então todo o conteúdo é exibido como texto literal ou neutralizado
E nenhum script é executado
E nenhuma tag fora da allowlist é preservada
E a sanitização utilizada é a mesma de 007-tickets
```

### AC-014-32 — `authorId` forjado é ignorado
```gherkin
Quando eu envio POST de comentário incluindo authorId de outro usuário
Então o campo é ignorado
E o comentário é criado com authorId igual ao meu usuário autenticado
```

### AC-014-33 — `isSystem` forjado é ignorado
```gherkin
Quando eu envio POST de comentário incluindo isSystem igual a verdadeiro
Então o campo é ignorado
E o comentário é criado com isSystem igual a falso
E ele permanece editável dentro da janela de 24 horas
```

### AC-014-34 — Autocompletar não revela membros suspensos
```gherkin
Dado um membro ativo "ana" e um membro suspenso "andre"
Quando eu digito "an" no campo de menção
Então apenas "ana" é sugerida
E nenhuma resposta permite distinguir um membro suspenso de um inexistente
```

### AC-014-35 — Corpo nunca aparece em log
```gherkin
Dado que eu crio, edito e excluo comentários
Quando eu inspeciono os logs de aplicação gerados
Então nenhum log contém o corpo de nenhum comentário
E os logs contêm apenas identificadores, contagens e traceId
```

---

## 7. Cenários de concorrência

### AC-014-36 — Comentário de sistema com rollback da transição
```gherkin
Dado um ticket em "IN_PROGRESS"
Quando uma transição de status falha após a criação do comentário de sistema
Então a transação inteira é revertida
E nenhum comentário de sistema permanece
E o status do ticket permanece inalterado
E nunca existe um comentário de sistema sem a transição correspondente
```

### AC-014-37 — Edições simultâneas do mesmo comentário
```gherkin
Dado um comentário meu com version igual a 0
Quando duas requisições simultâneas o editam, ambas informando version 0
Então exatamente uma recebe 200 OK
E a outra recebe 409 Conflict com o código DEVTIME-2004
E o corpo final corresponde a exatamente uma das duas edições
```

### AC-014-38 — Exclusão e resposta simultâneas
```gherkin
Dado um comentário raiz sem respostas
Quando uma requisição o exclui e outra cria uma resposta a ele, em paralelo
Então ambas podem concluir com sucesso
E, se a resposta for criada, ela permanece visível
E a interface exibe o marcador de comentário removido no lugar do raiz
E nunca resulta em uma resposta órfã sem indicação do raiz excluído
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-811 | AC-014-01, AC-014-10, AC-014-11, AC-014-17, AC-014-18 | ✅ |
| RN-812 | AC-014-04, AC-014-05, AC-014-06, AC-014-12, AC-014-13, AC-014-25 | ✅ |
| RN-813 | AC-014-02, AC-014-20 a AC-014-24, AC-014-26, AC-014-34 | ✅ |
| RN-814 | AC-014-03, AC-014-19 | ✅ |
| RN-815 | AC-014-07, AC-014-14, AC-014-36 | ✅ |
| RN-003 | AC-014-05, AC-014-27 | ✅ |
| RN-004 | AC-014-37 | ✅ |
| RN-011 | AC-014-32, AC-014-33 | ✅ |
| RN-012 | AC-014-29 | ✅ |
| RN-002 | AC-014-30 | ✅ |
| RN-006 | AC-014-01, AC-014-04, AC-014-06 | ✅ |
| INV-CMT-01 | AC-014-19 | ✅ |
| INV-CMT-02 | AC-014-15 | ✅ |
| INV-CMT-03 | AC-014-14 | ✅ |
| INV-CMT-04 | AC-014-20, AC-014-21 | ✅ |
| INV-CMT-05 | AC-014-32, AC-014-33 | ✅ |
| §6.2 tabela de menções | AC-014-02, AC-014-20 a AC-014-24 | ✅ |
| §6.3 janela e moderação | AC-014-04, AC-014-06, AC-014-13, AC-014-25 | ✅ |
| §7 permissions | AC-014-16 | ✅ |
| §23 `canEdit`/`canDelete` | AC-014-09 | ✅ |
| §28 logs | AC-014-35 | ✅ |
| CX-08 | AC-014-27, AC-014-38 | ✅ |
| SG-05 / SG-06 / SG-07 / SG-08 | AC-014-31 a AC-014-34 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. As 6 linhas da tabela normativa de menções (§6.2) são cobertas por `AC-014-02` e `AC-014-20` a `AC-014-24`.
