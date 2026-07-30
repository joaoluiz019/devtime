# 006 — Tags · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-006-XX`, estável e imutável |
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
| AC-006-01 | Feliz | Criação com normalização de espaços | RN-506 |
| AC-006-02 | Feliz | Criação implícita ao rotular um ticket | §9.2 users.md |
| AC-006-03 | Feliz | Autocompletar sugere tag existente | §7 spec |
| AC-006-04 | Feliz | Vínculo a ticket incrementa `usageCount` | INV-TAG-04 |
| AC-006-05 | Feliz | Desvínculo decrementa `usageCount` | INV-TAG-04 |
| AC-006-06 | Feliz | Renomeação preserva vínculos | FA-03 |
| AC-006-07 | Feliz | Alteração de cor | FA-04 |
| AC-006-08 | Feliz | Exclusão removendo vínculos | §9.3 users.md |
| AC-006-09 | Feliz | Listagem ordenada por uso | §9.1 users.md |
| AC-006-10 | Feliz | Filtro por uso mínimo | §9.1 users.md |
| AC-006-11 | Feliz | Sugestão de limpeza de tags órfãs | RN-508 |
| AC-006-12 | Feliz | Prévia da normalização no formulário | FM-02 |
| AC-006-13 | Erro | Nome já existente após normalização | RN-507 |
| AC-006-14 | Erro | Nome com 1 caractere | RN-507 |
| AC-006-15 | Erro | Nome com 41 caracteres após normalização | RN-507 |
| AC-006-16 | Erro | 11ª tag em um ticket | RN-313 |
| AC-006-17 | Erro | 11ª tag em um work log | INV-TAG-01 |
| AC-006-18 | Erro | Cor fora do formato hexadecimal | `DEVTIME-2000` |
| AC-006-19 | Erro | Conflito de `version` | RN-004 |
| AC-006-20 | Erro | `VIEWER` tenta criar tag | §7 permissions |
| AC-006-21 | Extremo | Nome só com espaços | CX-03 |
| AC-006-22 | Extremo | Acentos são preservados e coexistem | CX-02 |
| AC-006-23 | Extremo | Nome já hifenizado é idempotente | CX-07 |
| AC-006-24 | Extremo | Nome longo com espaços encolhe para válido | CX-05 |
| AC-006-25 | Extremo | Emoji e caractere não latino aceitos | CX-06 |
| AC-006-26 | Extremo | Tag excluída e recriada não recupera vínculos | CX-08 |
| AC-006-27 | Extremo | Vínculo duplicado é idempotente | CX-10 |
| AC-006-28 | Extremo | Troca da 10ª tag é permitida | CX-11 |
| AC-006-29 | Extremo | Exclusão com 3.500 vínculos | CX-12 |
| AC-006-30 | Extremo | Tag órfã há 89 dias não é sugerida | CX-14 |
| AC-006-31 | Extremo | Tag sugerida recebe vínculo e sai da lista | CX-15 |
| AC-006-32 | Extremo | Work log com 10 tags é editado sem tocar nelas | CX-17 |
| AC-006-33 | Segurança | Tag de outro tenant retorna 404 | RN-002 |
| AC-006-34 | Segurança | Vínculo cruzado entre tenants | SG-02 |
| AC-006-35 | Segurança | Nome com `<script>` é renderizado como texto | SG-04 |
| AC-006-36 | Segurança | `usageCount` no payload é ignorado | SG-05 |
| AC-006-37 | Segurança | Nome de tag nunca aparece em log | §28 spec |
| AC-006-38 | Concorrência | Duas criações do mesmo nome normalizado | RN-507 |
| AC-006-39 | Concorrência | Vínculos simultâneos e `usageCount` | INV-TAG-04 |
| AC-006-40 | Concorrência | Limite de 10 sob requisições simultâneas | RN-313 |
| AC-006-41 | Concorrência | Exclusão e vínculo simultâneos | INV-TAG-05 |

---

## 3. Cenários felizes

### AC-006-01 — Criação com normalização de espaços
```gherkin
Dado que estou autenticado com a permissão TAG_MANAGE
Quando eu envio POST /api/v1/tags com o nome "  Code   Review  "
Então recebo 201 Created
E o nome persistido é "code-review"
E a resposta retorna name igual a "code-review"
E usageCount é 0
E a cor é "#94A3B8" por padrão
E um AuditLog com action "TAG_CREATED" registra o nome bruto digitado
```

### AC-006-02 — Criação implícita ao rotular um ticket
```gherkin
Dado um ticket sem etiquetas
E que nenhuma etiqueta "migracao-v2" existe no tenant
Quando eu digito "Migracao V2" no campo de etiquetas do ticket e confirmo a criação
Então uma etiqueta "migracao-v2" é criada
E ela é vinculada ao ticket na mesma operação
E seu usageCount passa a 1
E a interface exibe "migracao-v2"
```

### AC-006-03 — Autocompletar sugere tag existente
```gherkin
Dado uma etiqueta "code-review" com usageCount igual a 12
Quando eu digito "code" no campo de etiquetas
Então a etiqueta "code-review" é sugerida
E as sugestões são ordenadas por usageCount decrescente
E no máximo 20 sugestões são retornadas
E a opção de criar nova etiqueta aparece apenas se nada corresponder exatamente
```

### AC-006-04 — Vínculo a ticket incrementa `usageCount`
```gherkin
Dado uma etiqueta com usageCount igual a 3
Quando eu a vinculo a um ticket
Então recebo 200 OK
E usageCount passa a 4 imediatamente na mesma transação
E uma linha é criada em ticket_tags
```

### AC-006-05 — Desvínculo decrementa `usageCount`
```gherkin
Dado uma etiqueta com usageCount igual a 4, vinculada a um ticket
Quando eu a desvinculo desse ticket
Então recebo 200 OK
E usageCount passa a 3
E a linha correspondente é removida de ticket_tags
```

### AC-006-06 — Renomeação preserva vínculos
```gherkin
Dado uma etiqueta "refatoracao" vinculada a 8 tickets e 20 work logs
Quando eu a renomeio para "Débito Técnico"
Então recebo 200 OK
E o nome passa a "débito-técnico"
E usageCount permanece 28
E todos os 28 vínculos permanecem intactos
```

### AC-006-07 — Alteração de cor
```gherkin
Dado uma etiqueta com cor "#94A3B8"
Quando eu altero a cor para "#EF4444"
Então recebo 200 OK
E a cor é atualizada
E usageCount e os vínculos permanecem inalterados
```

### AC-006-08 — Exclusão removendo vínculos
```gherkin
Dado uma etiqueta vinculada a 12 tickets e 45 work logs
Quando eu envio DELETE /api/v1/tags/{id}
Então recebo 200 OK com unlinkedFromTickets igual a 12
E unlinkedFromWorkLogs igual a 45
E a etiqueta recebe deletedAt preenchido
E nenhuma linha de vínculo permanece
E os 12 tickets e os 45 work logs continuam existindo e íntegros
E um AuditLog com action "TAG_DELETED" registra as duas contagens
```

### AC-006-09 — Listagem ordenada por uso
```gherkin
Dado etiquetas com usageCount 34, 12 e 0
Quando eu envio GET /api/v1/tags
Então recebo 200 OK
E a ordem retornada é 34, 12, 0
E o desempate entre contagens iguais é feito pelo nome
```

### AC-006-10 — Filtro por uso mínimo
```gherkin
Dado etiquetas com usageCount 34, 12, 5 e 0
Quando eu envio GET /api/v1/tags?minUsage=5
Então recebo 200 OK
E apenas as etiquetas com usageCount 34, 12 e 5 são retornadas
```

### AC-006-11 — Sugestão de limpeza de tags órfãs
```gherkin
Dado uma etiqueta cujo usageCount está em 0 há 91 dias
Quando eu envio GET /api/v1/tags/cleanup-suggestions
Então recebo 200 OK
E a etiqueta consta na lista com o campo orphanSince preenchido
Mas ela não foi excluída automaticamente
E ela continua disponível para uso
```

### AC-006-12 — Prévia da normalização no formulário
```gherkin
Dado que estou no formulário de criação de etiqueta
Quando eu digito "Code Review"
Então a interface exibe que a etiqueta será salva como "code-review"
E ao salvar o nome persistido coincide exatamente com a prévia exibida
```

---

## 4. Cenários de erro

### AC-006-13 — Nome já existente após normalização
```gherkin
Dado uma etiqueta "code-review"
Quando eu envio POST /api/v1/tags com o nome "Code Review"
Então recebo 409 Conflict com o código DEVTIME-2604
E a mensagem indica que a etiqueta já existe
E nenhuma etiqueta é criada
```

### AC-006-14 — Nome com 1 caractere
```gherkin
Quando eu envio POST /api/v1/tags com o nome "a"
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem indica que o nome deve ter entre 2 e 40 caracteres
```

### AC-006-15 — Nome com 41 caracteres após normalização
```gherkin
Quando eu envio POST /api/v1/tags com um nome que normaliza para 41 caracteres
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E nenhuma etiqueta é criada
```

### AC-006-16 — 11ª tag em um ticket
```gherkin
Dado um ticket com 10 etiquetas vinculadas
Quando eu tento vincular uma 11ª etiqueta
Então recebo 422 Unprocessable Entity com o código DEVTIME-2313
E a mensagem indica o máximo de 10 etiquetas por ticket
E nenhuma etiqueta é vinculada
E o usageCount da 11ª etiqueta permanece inalterado
```

### AC-006-17 — 11ª tag em um work log
```gherkin
Dado um work log com 10 etiquetas vinculadas
Quando eu tento vincular uma 11ª etiqueta
Então recebo 422 Unprocessable Entity com o código DEVTIME-2313
E nenhuma etiqueta é vinculada
```

### AC-006-18 — Cor fora do formato hexadecimal
```gherkin
Quando eu envio POST /api/v1/tags com cor "azul"
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem indica que a cor é inválida
```

### AC-006-19 — Conflito de `version`
```gherkin
Dado uma etiqueta com version igual a 2
Quando eu envio PATCH /api/v1/tags/{id} com version igual a 1
Então recebo 409 Conflict com o código DEVTIME-2004
E nenhuma alteração é persistida
```

### AC-006-20 — `VIEWER` tenta criar tag
```gherkin
Dado que estou autenticado com o papel VIEWER
Quando eu envio POST /api/v1/tags
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica TAG_MANAGE
Mas eu consigo listar as etiquetas normalmente com GET /api/v1/tags
```

---

## 5. Cenários extremos

### AC-006-21 — Nome só com espaços
```gherkin
Quando eu envio POST /api/v1/tags com o nome "   "
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E nenhuma etiqueta é criada
```

### AC-006-22 — Acentos são preservados e coexistem
```gherkin
Dado uma etiqueta "refatoração"
Quando eu envio POST /api/v1/tags com o nome "refatoracao"
Então recebo 201 Created
E as duas etiquetas coexistem no tenant
E ambas mantêm sua grafia original, com e sem acento
```

### AC-006-23 — Nome já hifenizado é idempotente
```gherkin
Quando eu envio POST /api/v1/tags com o nome "code-review"
Então recebo 201 Created com o nome "code-review"
E aplicar a normalização novamente sobre esse nome produz o mesmo resultado
```

### AC-006-24 — Nome longo com espaços encolhe para válido
```gherkin
Quando eu envio POST /api/v1/tags com um nome de 58 caracteres contendo muitos espaços consecutivos
E esse nome normaliza para 38 caracteres
Então recebo 201 Created
E o nome persistido tem 38 caracteres
```

### AC-006-25 — Emoji e caractere não latino aceitos
```gherkin
Quando eu envio POST /api/v1/tags com o nome "urgente🔥"
Então recebo 201 Created
E o nome é persistido com o emoji preservado
E nenhum caractere é filtrado
```

### AC-006-26 — Tag excluída e recriada não recupera vínculos
```gherkin
Dado uma etiqueta "urgente" vinculada a 10 tickets, posteriormente excluída
Quando eu envio POST /api/v1/tags com o nome "urgente"
Então recebo 201 Created
E a nova etiqueta possui um id distinto
E seu usageCount é 0
E ela não está vinculada a nenhum dos 10 tickets anteriores
```

### AC-006-27 — Vínculo duplicado é idempotente
```gherkin
Dado uma etiqueta com usageCount igual a 1, já vinculada a um ticket
Quando eu tento vinculá-la novamente ao mesmo ticket
Então a operação é aceita sem erro
E nenhuma linha adicional é criada em ticket_tags
E usageCount permanece 1
```

### AC-006-28 — Troca da 10ª tag é permitida
```gherkin
Dado um ticket com exatamente 10 etiquetas
Quando eu desvinculo uma delas e vinculo outra na mesma operação
Então recebo 200 OK
E o ticket permanece com 10 etiquetas
E nenhum erro DEVTIME-2313 é retornado
```

### AC-006-29 — Exclusão com 3.500 vínculos
```gherkin
Dado uma etiqueta vinculada a 500 tickets e 3.000 work logs
Quando eu a excluo
Então recebo 200 OK com unlinkedFromTickets igual a 500
E unlinkedFromWorkLogs igual a 3000
E a remoção é executada como exclusão em lote no banco
E nenhuma entidade de vínculo é carregada em memória
E a operação conclui em menos de 2 segundos
```

### AC-006-30 — Tag órfã há 89 dias não é sugerida
```gherkin
Dado uma etiqueta cujo usageCount está em 0 há exatamente 89 dias
Quando eu consulto as sugestões de limpeza
Então a etiqueta não consta na lista
Quando o relógio avança para 91 dias
Então a etiqueta passa a constar na lista
```

### AC-006-31 — Tag sugerida recebe vínculo e sai da lista
```gherkin
Dado uma etiqueta sugerida para limpeza
Quando ela é vinculada a um ticket
Então seu usageCount passa a 1
E ela deixa de constar nas sugestões de limpeza imediatamente
E nenhuma intervenção manual é necessária
```

### AC-006-32 — Work log com 10 tags é editado sem tocar nelas
```gherkin
Dado um work log com 10 etiquetas vinculadas
Quando eu edito apenas a descrição desse work log
Então recebo 200 OK
E nenhum erro DEVTIME-2313 é retornado
E as 10 etiquetas permanecem vinculadas
```

---

## 6. Cenários de segurança

### AC-006-33 — Tag de outro tenant retorna 404
```gherkin
Dado uma etiqueta pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu envio GET, PATCH ou DELETE em /api/v1/tags/{idDoTenantB}
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-006-34 — Vínculo cruzado entre tenants
```gherkin
Dado uma etiqueta do tenant A e um ticket do tenant B
Quando eu tento vincular a etiqueta ao ticket
Então recebo 404 Not Found com o código DEVTIME-2002
E nenhum vínculo é criado
E o usageCount da etiqueta permanece inalterado
```

### AC-006-35 — Nome com `<script>` é renderizado como texto
```gherkin
Dado uma etiqueta criada com o nome contendo "<script>alert(1)</script>"
Quando a etiqueta é exibida na interface e em um relatório PDF
Então o conteúdo é renderizado como texto literal
E nenhum script é executado
E o PDF não contém marcação interpretável
```

### AC-006-36 — `usageCount` no payload é ignorado
```gherkin
Dado uma etiqueta com usageCount igual a 5
Quando eu envio PATCH /api/v1/tags/{id} incluindo usageCount igual a 999
Então o campo é ignorado
E usageCount permanece 5
```

### AC-006-37 — Nome de tag nunca aparece em log
```gherkin
Dado que eu crio, renomeio, vinculo e excluo etiquetas
Quando eu inspeciono os logs de aplicação gerados
Então nenhum log contém o nome de nenhuma etiqueta
E os logs contêm apenas identificadores, contagens e traceId
```

---

## 7. Cenários de concorrência

### AC-006-38 — Duas criações do mesmo nome normalizado
```gherkin
Dado duas requisições simultâneas de criação, uma com "Code Review" e outra com "code-review"
Quando ambas são processadas em paralelo
Então exatamente uma recebe 201 Created
E a outra recebe 409 Conflict com o código DEVTIME-2604
E existe exatamente uma etiqueta "code-review" no tenant
E a rejeição ocorre pela constraint do banco, não apenas pela verificação prévia
```

### AC-006-39 — Vínculos simultâneos e `usageCount`
```gherkin
Dado uma etiqueta com usageCount igual a 0
Quando 20 requisições simultâneas a vinculam a 20 tickets distintos
Então as 20 linhas de vínculo são criadas
E ao final o usageCount é 20
Ou, havendo perda por contenção, o DenormalizationReconcileJob restaura o valor 20
E o valor final nunca fica negativo
```

### AC-006-40 — Limite de 10 sob requisições simultâneas
```gherkin
Dado um ticket com 9 etiquetas vinculadas
Quando duas requisições simultâneas tentam vincular a 10ª e a 11ª etiqueta
Então exatamente uma é aceita
E a outra recebe 422 Unprocessable Entity com o código DEVTIME-2313
E o ticket termina com exatamente 10 etiquetas
E nunca com 11
```

### AC-006-41 — Exclusão e vínculo simultâneos
```gherkin
Dado uma etiqueta existente e um ticket
Quando uma requisição exclui a etiqueta e outra a vincula ao ticket, em paralelo
Então ou o vínculo é criado e a exclusão o remove em seguida
Ou a exclusão conclui primeiro e o vínculo falha com 404 DEVTIME-2002
E nunca permanece um vínculo apontando para uma etiqueta excluída
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-506 | AC-006-01, AC-006-12, AC-006-21, AC-006-22, AC-006-23, AC-006-24, AC-006-25 | ✅ |
| RN-507 | AC-006-13, AC-006-14, AC-006-15, AC-006-26, AC-006-38 | ✅ |
| RN-508 | AC-006-11, AC-006-30, AC-006-31 | ✅ |
| RN-313 | AC-006-16, AC-006-28, AC-006-40 | ✅ |
| RN-003 | AC-006-08, AC-006-26 | ✅ |
| RN-004 | AC-006-19 | ✅ |
| RN-012 | AC-006-09 | ✅ |
| RN-001 | AC-006-34 | ✅ |
| RN-002 | AC-006-33, AC-006-34 | ✅ |
| RN-006 | AC-006-01, AC-006-08 | ✅ |
| INV-TAG-01 | AC-006-17, AC-006-32, AC-006-40 | ✅ |
| INV-TAG-02 | AC-006-13, AC-006-26, AC-006-38 | ✅ |
| INV-TAG-03 | AC-006-01, AC-006-12, AC-006-23 | ✅ |
| INV-TAG-04 | AC-006-04, AC-006-05, AC-006-27, AC-006-39 | ✅ |
| INV-TAG-05 | AC-006-41 | ✅ |
| §9.1 users.md | AC-006-09, AC-006-10 | ✅ |
| §9.2 users.md | AC-006-02, AC-006-03 | ✅ |
| §9.3 users.md | AC-006-08, AC-006-29 | ✅ |
| §7 permissions | AC-006-20 | ✅ |
| SG-02 | AC-006-34 | ✅ |
| SG-04 | AC-006-35 | ✅ |
| SG-05 | AC-006-36 | ✅ |
| §28 spec | AC-006-37 | ✅ |
| FM-02 | AC-006-12 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. Toda categoria exigida pelo template possui ao menos quatro cenários.
