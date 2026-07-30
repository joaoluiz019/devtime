# 005 — Categories · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-005-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

**Regras de escrita:**
- Um cenário verifica **um** comportamento.
- `Então` descreve resultado **observável** (resposta, estado persistido, evento), nunca implementação.
- Todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.
- Todo cenário é executável sem conhecimento adicional.

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-005-01 | Feliz | Tenant recém-criado possui 9 categorias padrão | RN-501 |
| AC-005-02 | Feliz | Criação de categoria | RN-502 |
| AC-005-03 | Feliz | Edição de categoria | RN-004 |
| AC-005-04 | Feliz | Renomear categoria de sistema | RN-503 |
| AC-005-05 | Feliz | Inativação de categoria | RN-504 |
| AC-005-06 | Feliz | Reativação de categoria | §10 spec |
| AC-005-07 | Feliz | Exclusão sem vínculos | RN-003 |
| AC-005-08 | Feliz | Exclusão com migração para substituta | RN-505 |
| AC-005-09 | Feliz | Reordenação de categorias | §8.4 users.md |
| AC-005-10 | Feliz | Estatística de uso sob demanda | §8.1 users.md |
| AC-005-11 | Feliz | Pré-seleção pela categoria do ticket | RN-104 |
| AC-005-12 | Feliz | Pré-seleção cai para o contrato | RN-104 |
| AC-005-13 | Feliz | Pré-seleção cai para a preferência do usuário | RN-104 |
| AC-005-14 | Feliz | Pré-seleção cai para a primeira ativa | RN-104 |
| AC-005-15 | Feliz | `billable` herda `billableByDefault` | RN-112 |
| AC-005-16 | Erro | Nome duplicado | RN-502 |
| AC-005-17 | Erro | Exclusão de categoria de sistema | RN-503 |
| AC-005-18 | Erro | Exclusão com vínculos sem substituta | RN-505 |
| AC-005-19 | Erro | Substituta igual à excluída | `DEVTIME-2605` |
| AC-005-20 | Erro | Substituta inativa | `DEVTIME-2605` |
| AC-005-21 | Erro | Cor fora do formato hexadecimal | `DEVTIME-2000` |
| AC-005-22 | Erro | Conflito de `version` | RN-004 |
| AC-005-23 | Erro | Reordenação com lista incompleta | §8.4 users.md |
| AC-005-24 | Erro | Uso de categoria inativa em work log | RN-104 |
| AC-005-25 | Erro | `MEMBER` tenta criar categoria | §7 permissions |
| AC-005-26 | Extremo | Nome diferindo apenas na caixa | CX-01 |
| AC-005-27 | Extremo | Nomes diferindo por acento são distintos | CX-02 |
| AC-005-28 | Extremo | Nome liberado após exclusão | CX-03 |
| AC-005-29 | Extremo | Inativar todas as categorias | CX-06 |
| AC-005-30 | Extremo | Exclusão com 100.000 vínculos | CX-09 |
| AC-005-31 | Extremo | Work log mantém categoria inativada | CX-13 |
| AC-005-32 | Extremo | Cadeia pula categoria excluída do ticket | CX-15 |
| AC-005-33 | Extremo | Seed executado duas vezes | CX-14 |
| AC-005-34 | Extremo | Relatório histórico exibe categoria excluída | OB-04 |
| AC-005-35 | Segurança | Categoria de outro tenant retorna 404 | RN-002 |
| AC-005-36 | Segurança | Reordenação com id de outro tenant | SG-02 |
| AC-005-37 | Segurança | Migração para categoria de outro tenant | SG-03 |
| AC-005-38 | Segurança | `isSystem` enviado no payload é ignorado | SG-05 |
| AC-005-39 | Segurança | `tenantId` da requisição é ignorado | RN-001 |
| AC-005-40 | Concorrência | Duas categorias com o mesmo nome simultaneamente | RN-502 |
| AC-005-41 | Concorrência | Reordenações simultâneas | §8.4 users.md |
| AC-005-42 | Concorrência | Exclusão e criação de work log simultâneas | RN-505 |
| AC-005-43 | Concorrência | Falha no seed reverte a criação do tenant | INV-CAT-02 |

---

## 3. Cenários felizes

### AC-005-01 — Tenant recém-criado possui 9 categorias padrão
```gherkin
Dado que nenhum tenant existe para o e-mail informado
Quando um novo tenant é criado com sucesso
Então exatamente 9 categorias são criadas para esse tenant
E todas possuem isSystem igual a verdadeiro
E todas possuem active igual a verdadeiro
E os nomes são exatamente "Desenvolvimento", "Correção de Bug", "Reunião",
      "Suporte", "Análise / Planejamento", "Code Review", "Documentação",
      "Infraestrutura / Deploy" e "Interno (não faturável)"
E "Interno (não faturável)" possui billableByDefault igual a falso
E as outras 8 possuem billableByDefault igual a verdadeiro
E sortOrder vai de 0 a 8 na ordem acima
E um AuditLog com action "CATEGORY_SEEDED" e actorType "SYSTEM" foi gravado
```

### AC-005-02 — Criação de categoria
```gherkin
Dado que estou autenticado com a permissão CATEGORY_MANAGE
E existem 9 categorias no tenant
Quando eu envio POST /api/v1/categories com nome "Consultoria",
      cor "#F97316" e billableByDefault igual a verdadeiro
Então recebo 201 Created com o header Location
E a categoria é criada com isSystem igual a falso
E active igual a verdadeiro
E sortOrder igual a 9
E um AuditLog com action "CATEGORY_CREATED" foi gravado
```

### AC-005-03 — Edição de categoria
```gherkin
Dado uma categoria com version igual a 1
Quando eu envio PUT /api/v1/categories/{id} alterando a cor e a descrição, com version igual a 1
Então recebo 200 OK
E a version passa a 2
E um AuditLog com action "CATEGORY_UPDATED" registra apenas os dois campos alterados
```

### AC-005-04 — Renomear categoria de sistema
```gherkin
Dado a categoria de sistema "Reunião"
Quando eu envio PUT /api/v1/categories/{id} alterando o nome para "Alinhamento"
Então recebo 200 OK
E o nome passa a "Alinhamento"
E isSystem permanece verdadeiro
E a categoria continua não podendo ser excluída
```

### AC-005-05 — Inativação de categoria
```gherkin
Dado uma categoria ativa com 40 work logs vinculados
Quando eu a inativo
Então recebo 200 OK
E active passa a falso
E os 40 work logs permanecem inalterados e continuam vinculados a ela
E a categoria deixa de ser retornada pelo seletor de novos registros
E um AuditLog com action "CATEGORY_DEACTIVATED" foi gravado
```

### AC-005-06 — Reativação de categoria
```gherkin
Dado uma categoria inativa
Quando eu a reativo
Então recebo 200 OK
E active passa a verdadeiro
E ela volta a ser oferecida em novos registros
E nenhum work log existente é alterado
```

### AC-005-07 — Exclusão sem vínculos
```gherkin
Dado uma categoria não sistêmica sem nenhum work log vinculado
Quando eu envio DELETE /api/v1/categories/{id} sem replacementCategoryId
Então recebo 200 OK com migratedWorkLogs igual a 0
E a categoria recebe deletedAt preenchido
E ela deixa de aparecer em todas as consultas padrão
E ela permanece fisicamente na base
```

### AC-005-08 — Exclusão com migração para substituta
```gherkin
Dado uma categoria "Consultoria" com 87 work logs vinculados
E uma categoria ativa "Análise / Planejamento"
Quando eu envio DELETE /api/v1/categories/{id}?replacementCategoryId={idAnalise}
Então recebo 200 OK com migratedWorkLogs igual a 87
E migratedTo igual ao id de "Análise / Planejamento"
E os 87 work logs passam a apontar para "Análise / Planejamento"
E "Consultoria" recebe deletedAt preenchido
E um AuditLog com action "CATEGORY_DELETED" registra migratedWorkLogs igual a 87
```

### AC-005-09 — Reordenação de categorias
```gherkin
Dado 10 categorias no tenant, com sortOrder de 0 a 9
Quando eu envio PATCH /api/v1/categories/reorder com os 10 ids em ordem invertida
Então recebo 200 OK
E o sortOrder de cada categoria corresponde à sua posição na lista enviada
E a listagem padrão passa a retornar na nova ordem
E um AuditLog com action "CATEGORY_REORDERED" foi gravado
```

### AC-005-10 — Estatística de uso sob demanda
```gherkin
Dado uma categoria com 1.240 work logs somando 74.400 minutos
Quando eu envio GET /api/v1/categories sem o parâmetro includeUsage
Então recebo 200 OK
E o campo usage está ausente de todas as categorias
Quando eu envio GET /api/v1/categories?includeUsage=true
Então o campo usage traz workLogsCount igual a 1240 e totalMinutes igual a 74400
```

### AC-005-11 — Pré-seleção pela categoria do ticket
```gherkin
Dado um ticket com defaultCategoryId apontando para uma categoria ativa "Correção de Bug"
E um contrato com defaultCategoryId apontando para "Desenvolvimento"
E um usuário com preferences.defaultCategoryId apontando para "Suporte"
Quando a categoria padrão é resolvida para um novo registro nesse ticket
Então a categoria retornada é "Correção de Bug"
```

### AC-005-12 — Pré-seleção cai para o contrato
```gherkin
Dado um ticket sem defaultCategoryId
E um contrato com defaultCategoryId apontando para uma categoria ativa "Desenvolvimento"
E um usuário com preferences.defaultCategoryId apontando para "Suporte"
Quando a categoria padrão é resolvida
Então a categoria retornada é "Desenvolvimento"
```

### AC-005-13 — Pré-seleção cai para a preferência do usuário
```gherkin
Dado um ticket e um contrato ambos sem defaultCategoryId
E um usuário com preferences.defaultCategoryId apontando para uma categoria ativa "Suporte"
Quando a categoria padrão é resolvida
Então a categoria retornada é "Suporte"
```

### AC-005-14 — Pré-seleção cai para a primeira ativa
```gherkin
Dado que nenhuma das três origens possui categoria padrão definida
Quando a categoria padrão é resolvida
Então a categoria retornada é a primeira ativa ordenada por sortOrder e depois por name
E a resolução nunca retorna vazio
```

### AC-005-15 — `billable` herda `billableByDefault`
```gherkin
Dado a categoria "Interno (não faturável)" com billableByDefault igual a falso
Quando um work log é criado com essa categoria e sem informar billable
Então o work log é criado com billable igual a falso
E seu billableMinutes é 0
E ele não consome o saldo do contrato
```

---

## 4. Cenários de erro

### AC-005-16 — Nome duplicado
```gherkin
Dado uma categoria chamada "Desenvolvimento"
Quando eu envio POST /api/v1/categories com o nome "Desenvolvimento"
Então recebo 409 Conflict com o código DEVTIME-2601
E a mensagem indica que já existe uma categoria com este nome
E nenhuma categoria é criada
```

### AC-005-17 — Exclusão de categoria de sistema
```gherkin
Dado a categoria de sistema "Desenvolvimento" sem nenhum work log vinculado
Quando eu envio DELETE /api/v1/categories/{id}
Então recebo 409 Conflict com o código DEVTIME-2602
E a mensagem orienta a inativar a categoria em vez de excluí-la
E a categoria permanece intacta
```

### AC-005-18 — Exclusão com vínculos sem substituta
```gherkin
Dado uma categoria não sistêmica com 12 work logs vinculados
Quando eu envio DELETE /api/v1/categories/{id} sem replacementCategoryId
Então recebo 409 Conflict com o código DEVTIME-2603
E a resposta informa que existem 12 registros vinculados
E nenhum work log é alterado
E a categoria permanece intacta
```

### AC-005-19 — Substituta igual à excluída
```gherkin
Dado uma categoria com work logs vinculados
Quando eu envio DELETE /api/v1/categories/{id}?replacementCategoryId={mesmoId}
Então recebo 422 Unprocessable Entity com o código DEVTIME-2605
E nenhum registro é migrado
```

### AC-005-20 — Substituta inativa
```gherkin
Dado uma categoria com work logs vinculados
E uma categoria substituta com active igual a falso
Quando eu envio DELETE informando essa substituta
Então recebo 422 Unprocessable Entity com o código DEVTIME-2605
E nenhum registro é migrado
```

### AC-005-21 — Cor fora do formato hexadecimal
```gherkin
Quando eu envio POST /api/v1/categories com cor "vermelho"
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
E a mensagem indica que a cor é inválida
```

### AC-005-22 — Conflito de `version`
```gherkin
Dado uma categoria com version igual a 3
Quando eu envio PUT /api/v1/categories/{id} com version igual a 2
Então recebo 409 Conflict com o código DEVTIME-2004
E a mensagem orienta a recarregar o registro
E nenhuma alteração é persistida
```

### AC-005-23 — Reordenação com lista incompleta
```gherkin
Dado 10 categorias no tenant
Quando eu envio PATCH /api/v1/categories/reorder com apenas 8 ids
Então recebo 422 Unprocessable Entity
E o sortOrder de nenhuma categoria é alterado
```

### AC-005-24 — Uso de categoria inativa em work log
```gherkin
Dado uma categoria com active igual a falso
Quando eu tento criar um work log informando explicitamente essa categoria
Então recebo 422 Unprocessable Entity com o código DEVTIME-2104
E a mensagem indica categoria inválida ou inativa
E nenhum work log é criado
```

### AC-005-25 — `MEMBER` tenta criar categoria
```gherkin
Dado que estou autenticado com o papel MEMBER
Quando eu envio POST /api/v1/categories
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica CATEGORY_MANAGE
Mas eu consigo listar as categorias normalmente com GET /api/v1/categories
```

---

## 5. Cenários extremos

### AC-005-26 — Nome diferindo apenas na caixa
```gherkin
Dado uma categoria chamada "Desenvolvimento"
Quando eu envio POST /api/v1/categories com o nome "DESENVOLVIMENTO"
Então recebo 409 Conflict com o código DEVTIME-2601
E nenhuma categoria é criada
```

### AC-005-27 — Nomes diferindo por acento são distintos
```gherkin
Dado uma categoria chamada "Análise"
Quando eu envio POST /api/v1/categories com o nome "Analise"
Então recebo 201 Created
E as duas categorias coexistem no tenant
```

### AC-005-28 — Nome liberado após exclusão
```gherkin
Dado uma categoria "Consultoria" que foi excluída logicamente
Quando eu envio POST /api/v1/categories com o nome "Consultoria"
Então recebo 201 Created
E uma nova categoria é criada com um id distinto
E a categoria anterior permanece excluída na base
```

### AC-005-29 — Inativar todas as categorias
```gherkin
Dado que todas as categorias do tenant estão ativas
Quando eu inativo todas elas, inclusive as de sistema
Então cada inativação retorna 200 OK
E ao inativar a última categoria ativa a interface exibe um alerta
Mas a operação não é bloqueada
E uma tentativa posterior de criar work log retorna 422 DEVTIME-2104
```

### AC-005-30 — Exclusão com 100.000 vínculos
```gherkin
Dado uma categoria com 100.000 work logs vinculados
Quando eu envio DELETE informando uma substituta válida
Então recebo 200 OK com migratedWorkLogs igual a 100000
E a operação é executada como atualização em lote no banco
E nenhuma entidade de work log é carregada em memória
E a operação conclui em menos de 5 segundos
```

### AC-005-31 — Work log mantém categoria inativada
```gherkin
Dado um work log vinculado a uma categoria que foi inativada depois
Quando eu consulto o work log
Então a categoria original é exibida normalmente
Quando eu edito apenas a descrição desse work log
Então a categoria permanece a original
E nenhum erro de categoria inativa é retornado
```

### AC-005-32 — Cadeia pula categoria excluída do ticket
```gherkin
Dado um ticket cujo defaultCategoryId aponta para uma categoria excluída
E um contrato com defaultCategoryId apontando para uma categoria ativa
Quando a categoria padrão é resolvida para esse ticket
Então a categoria retornada é a do contrato
E nenhum erro é lançado
```

### AC-005-33 — Seed executado duas vezes
```gherkin
Dado um tenant que já possui as 9 categorias padrão
Quando o processo de seed é executado novamente para esse tenant
Então nenhuma categoria adicional é criada
E o tenant continua com exatamente 9 categorias de sistema
```

### AC-005-34 — Relatório histórico exibe categoria excluída
```gherkin
Dado um período fechado com work logs de uma categoria posteriormente excluída
Quando eu gero o relatório desse período
Então o nome da categoria vigente à época é exibido nas linhas correspondentes
E nenhuma linha aparece sem classificação
```

---

## 6. Cenários de segurança

### AC-005-35 — Categoria de outro tenant retorna 404
```gherkin
Dado uma categoria pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu envio GET, PUT ou DELETE em /api/v1/categories/{idDoTenantB}
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
E nenhuma informação sobre a existência do recurso é revelada
```

### AC-005-36 — Reordenação com id de outro tenant
```gherkin
Dado 10 categorias no tenant A
Quando eu envio PATCH /api/v1/categories/reorder incluindo um id do tenant B
Então recebo 422 Unprocessable Entity
E o sortOrder de nenhuma categoria do tenant A é alterado
E nenhuma categoria do tenant B é alterada
```

### AC-005-37 — Migração para categoria de outro tenant
```gherkin
Dado uma categoria do tenant A com work logs vinculados
Quando eu envio DELETE informando como substituta uma categoria do tenant B
Então recebo 422 Unprocessable Entity com o código DEVTIME-2605
E nenhum work log é migrado
E a categoria do tenant A permanece intacta
```

### AC-005-38 — `isSystem` enviado no payload é ignorado
```gherkin
Dado uma categoria criada manualmente com isSystem igual a falso
Quando eu envio PUT /api/v1/categories/{id} incluindo isSystem igual a verdadeiro
Então o campo é ignorado
E isSystem permanece falso
E a categoria continua podendo ser excluída
```

### AC-005-39 — `tenantId` da requisição é ignorado
```gherkin
Dado que estou autenticado no tenant A
Quando eu envio POST /api/v1/categories incluindo tenantId do tenant B no corpo
Então a categoria é criada no tenant A
E nenhuma categoria é criada no tenant B
```

---

## 7. Cenários de concorrência

### AC-005-40 — Duas categorias com o mesmo nome simultaneamente
```gherkin
Dado duas requisições simultâneas de criação com o nome "Consultoria"
Quando ambas são processadas em paralelo
Então exatamente uma recebe 201 Created
E a outra recebe 409 Conflict com o código DEVTIME-2601
E existe exatamente uma categoria "Consultoria" no tenant
E a rejeição ocorre pela constraint do banco, não apenas pela verificação prévia
```

### AC-005-41 — Reordenações simultâneas
```gherkin
Dado 10 categorias no tenant
Quando duas requisições de reordenação com ordens diferentes são processadas em paralelo
Então ambas retornam 200 OK ou uma retorna 409 por conflito de versão
E a ordem final corresponde integralmente a uma das duas requisições
E nunca resulta em uma mistura parcial das duas ordens
E nenhum sortOrder fica duplicado ou com lacuna
```

### AC-005-42 — Exclusão e criação de work log simultâneas
```gherkin
Dado uma categoria ativa sem vínculos
Quando uma requisição exclui a categoria e outra cria um work log com ela, em paralelo
Então ou o work log é criado e a exclusão falha com DEVTIME-2603
Ou a exclusão conclui e a criação do work log falha com DEVTIME-2104
E nunca existe um work log apontando para uma categoria excluída
```

### AC-005-43 — Falha no seed reverte a criação do tenant
```gherkin
Dado que a criação de categorias falhará por erro de banco
Quando um novo tenant é criado
Então a criação do tenant é revertida integralmente
E nenhum tenant é persistido
E nenhuma categoria órfã permanece na base
E nunca existe um tenant sem categorias
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-501 | AC-005-01, AC-005-33, AC-005-43 | ✅ |
| RN-502 | AC-005-02, AC-005-16, AC-005-26, AC-005-27, AC-005-28, AC-005-40 | ✅ |
| RN-503 | AC-005-04, AC-005-17 | ✅ |
| RN-504 | AC-005-05, AC-005-31 | ✅ |
| RN-505 | AC-005-08, AC-005-18, AC-005-19, AC-005-20, AC-005-30, AC-005-42 | ✅ |
| RN-104 | AC-005-11, AC-005-12, AC-005-13, AC-005-14, AC-005-24, AC-005-32 | ✅ |
| RN-112 | AC-005-15 | ✅ |
| RN-003 | AC-005-07, AC-005-28 | ✅ |
| RN-004 | AC-005-03, AC-005-22 | ✅ |
| RN-011 | AC-005-38 | ✅ |
| RN-001 | AC-005-39 | ✅ |
| RN-002 | AC-005-35 | ✅ |
| RN-006 | AC-005-01, AC-005-02, AC-005-03, AC-005-05, AC-005-08, AC-005-09 | ✅ |
| INV-CAT-01 | AC-005-26, AC-005-28, AC-005-40 | ✅ |
| INV-CAT-02 | AC-005-01, AC-005-14, AC-005-43 | ✅ |
| INV-CAT-03 | AC-005-17 | ✅ |
| INV-CAT-04 | AC-005-08, AC-005-42 | ✅ |
| INV-CAT-05 | AC-005-38 | ✅ |
| §7 permissions | AC-005-25 | ✅ |
| §8.1 users.md | AC-005-10 | ✅ |
| §8.4 users.md | AC-005-09, AC-005-23, AC-005-36, AC-005-41 | ✅ |
| SG-02 | AC-005-36 | ✅ |
| SG-03 | AC-005-37 | ✅ |
| SG-05 | AC-005-38 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. Toda categoria exigida pelo template (feliz, erro, extremo, segurança, concorrência) possui ao menos três cenários.
