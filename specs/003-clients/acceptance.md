# 003 — Clients · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-003-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-003-01 | Feliz | Cadastro com CNPJ válido | RN-402 |
| AC-003-02 | Feliz | Cadastro sem documento | CX-06 |
| AC-003-03 | Feliz | Edição de cliente | RN-004 |
| AC-003-04 | Feliz | Adição de contato primário | RN-406 |
| AC-003-05 | Feliz | Inativação sem contratos ativos | RN-405 |
| AC-003-06 | Feliz | Reativação | §4.4 SM |
| AC-003-07 | Feliz | Exclusão sem contratos | RN-003 |
| AC-003-08 | Feliz | Resumo consolidado do cliente | §8 clients.md |
| AC-003-09 | Feliz | Busca sem acento e sem caixa | §4.4 authentication.md |
| AC-003-10 | Erro | CPF inválido | RN-402 |
| AC-003-11 | Erro | Nome duplicado | RN-404 |
| AC-003-12 | Erro | Documento duplicado | RN-403 |
| AC-003-13 | Erro | Exclusão com contrato ativo | RN-401 |
| AC-003-14 | Erro | Contrato para cliente inativo | RN-405 |
| AC-003-15 | Erro | Conflito de `version` | RN-004 |
| AC-003-16 | Erro | `size` acima de 100 | RN-012 |
| AC-003-17 | Erro | MEMBER tenta criar cliente | §7 permissions |
| AC-003-18 | Extremo | Documento com máscara | CX-03 |
| AC-003-19 | Extremo | CPF com dígitos repetidos | CX-04 |
| AC-003-20 | Extremo | Nome liberado após exclusão | CX-02 |
| AC-003-21 | Extremo | Nome duplicado diferindo só na caixa | CX-01 |
| AC-003-22 | Extremo | Troca de contato primário | CX-08 |
| AC-003-23 | Extremo | Exclusão do contato primário | CX-09 |
| AC-003-24 | Extremo | Inativação com 10 contratos ativos | CX-10 |
| AC-003-25 | Extremo | CNPJ de filial com raiz já cadastrada | CX-05 |
| AC-003-26 | Segurança | Cliente de outro tenant retorna 404 | RN-002 |
| AC-003-27 | Segurança | MEMBER não enxerga cliente sem vínculo | nota ² |
| AC-003-28 | Segurança | Escopo aplicado também na contagem | IMP-02 |
| AC-003-29 | Segurança | Busca não é vetor de injeção | RP-04 |
| AC-003-30 | Segurança | MEMBER não vê valores monetários no resumo | `CONTRACT_VIEW_FINANCIAL` |
| AC-003-31 | Concorrência | Dois clientes com o mesmo nome simultaneamente | RN-404 |
| AC-003-32 | Concorrência | Duas marcações de contato primário | INV-CON-01 |
| AC-003-33 | Concorrência | Exclusão e ativação de contrato simultâneas | RN-401 |

---

## 3. Cenários felizes

### AC-003-01 — Cadastro com CNPJ válido
```gherkin
Dado que estou autenticado com a permissão CLIENT_CREATE
Quando eu envio POST /api/v1/clients com nome "Acme Software",
      documentType "CNPJ" e documentNumber "12.345.678/0001-95" válido
Então recebo 201 Created com o header Location
E o cliente é criado com status "ACTIVE"
E documentNumber é persistido como "12345678000195", apenas com dígitos
E activeContractsCount é 0
E o campo color é preenchido com um valor hexadecimal derivado do nome
E um AuditLog com action "CLIENT_CREATED" foi gravado
```

### AC-003-02 — Cadastro sem documento
```gherkin
Quando eu envio POST /api/v1/clients com apenas o nome "Beta Consultoria"
Então recebo 201 Created
E documentNumber é nulo
E nenhuma validação de dígito verificador é aplicada
E é possível cadastrar um segundo cliente também sem documento
```

### AC-003-03 — Edição de cliente
```gherkin
Dado um cliente com version igual a 2
Quando eu envio PATCH /api/v1/clients/{id} alterando o e-mail e a razão social, com version igual a 2
Então recebo 200 OK
E a version passa a 3
E um AuditLog com action "CLIENT_UPDATED" registra apenas os dois campos alterados
```

### AC-003-04 — Adição de contato primário
```gherkin
Dado um cliente sem contatos
Quando eu envio POST /api/v1/clients/{id}/contacts com nome "Ana Souza",
      e-mail válido e isPrimary igual a verdadeiro
Então recebo 201 Created
E o contato é criado com isPrimary igual a verdadeiro
E o cliente possui exatamente um contato primário
```

### AC-003-05 — Inativação sem contratos ativos
```gherkin
Dado um cliente "ACTIVE" sem nenhum contrato
Quando eu envio POST /api/v1/clients/{id}/deactivate
Então recebo 200 OK sem exigir confirmação adicional
E o cliente passa a "INACTIVE"
E um AuditLog com action "CLIENT_DEACTIVATED" foi gravado
```

### AC-003-06 — Reativação
```gherkin
Dado um cliente "INACTIVE"
Quando eu envio POST /api/v1/clients/{id}/reactivate
Então recebo 200 OK
E o cliente passa a "ACTIVE"
E passa a ser possível criar contratos para ele
```

### AC-003-07 — Exclusão sem contratos
```gherkin
Dado um cliente sem nenhum contrato e com 3 contatos
Quando eu envio DELETE /api/v1/clients/{id}
Então recebo 204 No Content
E o cliente possui deletedAt e deletedBy preenchidos
E os 3 contatos também são excluídos logicamente
E o cliente não aparece mais em GET /api/v1/clients
E nenhum registro é removido fisicamente do banco
```

### AC-003-08 — Resumo consolidado do cliente
```gherkin
Dado um cliente com 3 contratos ativos, cada um com um período aberto
Quando eu envio GET /api/v1/clients/{id}/summary
Então recebo 200 OK
E a resposta contém a lista dos 3 contratos com seu saldo
E contém o total consumido e o total restante do período corrente
E os contratos vêm paginados
E as durações são apresentadas em minutos inteiros e rótulo HH:MM
```

### AC-003-09 — Busca sem acento e sem caixa
```gherkin
Dado clientes chamados "Açaí Digital", "ACME Software" e "beta"
Quando eu envio GET /api/v1/clients com search igual a "acai"
Então "Açaí Digital" é retornado
Quando eu envio search igual a "ACME"
Então "ACME Software" é retornado
Quando eu envio search igual a "BETA"
Então "beta" é retornado
E o resultado é paginado e usa projeção, não a entidade completa
```

---

## 4. Cenários de erro

### AC-003-10 — CPF inválido
```gherkin
Quando eu envio POST /api/v1/clients com documentType "CPF" e documentNumber "12345678900"
Então recebo 422 Unprocessable Entity com o código "DEVTIME-2402"
E nenhum cliente é criado
E o número informado não aparece em nenhum log
```

### AC-003-11 — Nome duplicado
```gherkin
Dado um cliente não excluído chamado "Acme Software"
Quando eu envio POST /api/v1/clients com o nome "Acme Software"
Então recebo 409 Conflict com o código "DEVTIME-2404"
E nenhum cliente é criado
```

### AC-003-12 — Documento duplicado
```gherkin
Dado um cliente com documentNumber "12345678000195"
Quando eu envio POST /api/v1/clients com outro nome e o mesmo documento
Então recebo 409 Conflict com o código "DEVTIME-2403"
```

### AC-003-13 — Exclusão com contrato ativo
```gherkin
Dado um cliente com um contrato em status "ACTIVE"
Quando eu envio DELETE /api/v1/clients/{id}
Então recebo 409 Conflict com o código "DEVTIME-2401"
E a resposta lista os contratos que impedem a exclusão
E a mensagem sugere inativar o cliente
E o cliente permanece não excluído
E o mesmo ocorre com um contrato em status "SUSPENDED"
```

### AC-003-14 — Contrato para cliente inativo
```gherkin
Dado um cliente com status "INACTIVE"
Quando eu envio POST /api/v1/contracts referenciando esse cliente
Então recebo 422 Unprocessable Entity com o código "DEVTIME-2405"
E nenhum contrato é criado
```

### AC-003-15 — Conflito de `version`
```gherkin
Dado um cliente com version igual a 5
Quando eu envio PATCH /api/v1/clients/{id} com version igual a 4
Então recebo 409 Conflict com o código "DEVTIME-2004"
E nenhum campo é alterado
```

### AC-003-16 — `size` acima de 100
```gherkin
Quando eu envio GET /api/v1/clients com size igual a 250
Então recebo 400 Bad Request com o código "DEVTIME-2006"
```

### AC-003-17 — MEMBER tenta criar cliente
```gherkin
Dado que estou autenticado com papel "MEMBER"
Quando eu envio POST /api/v1/clients com dados válidos
Então recebo 403 Forbidden com o código "DEVTIME-1101"
E requiredPermission é "CLIENT_CREATE"
E a interface não exibe o botão de novo cliente
```

---

## 5. Cenários extremos

### AC-003-18 — Documento com máscara
```gherkin
Quando eu envio POST /api/v1/clients com documentNumber "123.456.789-09" válido
Então recebo 201 Created
E o valor persistido é "12345678909"
Quando eu tento cadastrar outro cliente com "12345678909" sem máscara
Então recebo 409 Conflict com o código "DEVTIME-2403"
```

### AC-003-19 — CPF com dígitos repetidos
```gherkin
Quando eu envio POST /api/v1/clients com documentType "CPF" e documentNumber "11111111111"
Então recebo 422 Unprocessable Entity com o código "DEVTIME-2402"
E o mesmo ocorre para "00000000000" e para o CNPJ "11111111111111"
```

### AC-003-20 — Nome liberado após exclusão
```gherkin
Dado um cliente chamado "Acme Software" que foi excluído logicamente
Quando eu envio POST /api/v1/clients com o nome "Acme Software"
Então recebo 201 Created
E existem dois registros na tabela, um com deletedAt preenchido e outro sem
E apenas o novo aparece nas consultas
```

### AC-003-21 — Nome duplicado diferindo só na caixa
```gherkin
Dado um cliente chamado "Acme Software"
Quando eu envio POST /api/v1/clients com o nome "ACME SOFTWARE"
Então recebo 409 Conflict com o código "DEVTIME-2404"
Mas o nome "Açme Software", com acento, é aceito, pois a unicidade não remove acentos
```

### AC-003-22 — Troca de contato primário
```gherkin
Dado um cliente com o contato A marcado como primário
Quando eu marco o contato B como primário
Então recebo 200 OK
E B passa a ser primário
E A deixa de ser primário na mesma transação
E o cliente continua com exatamente um contato primário
```

### AC-003-23 — Exclusão do contato primário
```gherkin
Dado um cliente com o contato A primário e o contato B secundário
Quando eu envio DELETE do contato A
Então recebo 204 No Content
E o cliente fica sem nenhum contato primário
E nenhum contato é promovido automaticamente
E a interface sinaliza a ausência de contato primário
```

### AC-003-24 — Inativação com 10 contratos ativos
```gherkin
Dado um cliente com 10 contratos em status "ACTIVE"
Quando eu envio POST /api/v1/clients/{id}/deactivate sem confirmação
Então recebo 409 Conflict listando os 10 contratos
Quando eu reenvio com confirmed igual a verdadeiro
Então recebo 200 OK
E o cliente passa a "INACTIVE"
E os 10 contratos permanecem "ACTIVE"
E nenhum período deixa de ser gerado
```

### AC-003-25 — CNPJ de filial com raiz já cadastrada
```gherkin
Dado um cliente com o CNPJ "12345678000195"
Quando eu envio POST /api/v1/clients com o CNPJ "12345678000276", válido e de mesma raiz
Então recebo 201 Created
E ambos os clientes coexistem, pois a unicidade é sobre o número completo
```

---

## 6. Cenários de segurança

### AC-003-26 — Cliente de outro tenant retorna 404
```gherkin
Dado que estou autenticado no tenant A
E que existe um cliente com id X no tenant B
Quando eu envio GET, PATCH ou DELETE em /api/v1/clients/X
Então recebo 404 Not Found com o código "DEVTIME-2002" em todos os casos
E nunca recebo 403
E o cliente do tenant B permanece inalterado
```

### AC-003-27 — MEMBER não enxerga cliente sem vínculo
```gherkin
Dado que estou autenticado com papel "MEMBER"
E que existe um cliente com o qual não possuo nenhum vínculo
Quando eu envio GET /api/v1/clients
Então esse cliente não aparece na lista
Quando eu envio GET /api/v1/clients/{id} desse cliente por id direto
Então recebo 404 Not Found com o código "DEVTIME-2002"
Mas um cliente cujo contrato possui work log meu aparece normalmente
```

### AC-003-28 — Escopo aplicado também na contagem
```gherkin
Dado um tenant com 50 clientes, dos quais 3 possuem vínculo com o MEMBER
Quando o MEMBER envia GET /api/v1/clients
Então totalElements é igual a 3, não 50
E o número total de páginas corresponde a 3 elementos
E o SQL executado contém a restrição de escopo, comprovado por inspeção
```

### AC-003-29 — Busca não é vetor de injeção
```gherkin
Quando eu envio GET /api/v1/clients com search igual a "' OR 1=1 --"
Então recebo 200 OK com resultado vazio ou apenas correspondências literais
E nenhum erro de banco é exposto
E a consulta é executada por Specification parametrizada
```

### AC-003-30 — MEMBER não vê valores monetários no resumo
```gherkin
Dado que estou autenticado com papel "MEMBER"
E que possuo vínculo com o cliente consultado
Quando eu envio GET /api/v1/clients/{id}/summary
Então recebo 200 OK
E a resposta não contém estimatedValue, hourlyRate nem qualquer campo monetário
E contém as durações em minutos
Mas um VIEWER, que possui CONTRACT_VIEW_FINANCIAL, recebe os campos monetários
```

---

## 7. Cenários de concorrência

### AC-003-31 — Dois clientes com o mesmo nome simultaneamente
```gherkin
Quando duas requisições de criação com o nome "Novo Cliente" chegam simultaneamente
Então exatamente uma recebe 201 Created
E a outra recebe 409 Conflict com o código "DEVTIME-2404"
E existe exatamente um cliente com esse nome
E a violação de constraint do banco foi traduzida para o código de negócio
```

### AC-003-32 — Duas marcações de contato primário
```gherkin
Dado um cliente com os contatos A, B e C, nenhum primário
Quando as marcações de B e C como primário chegam simultaneamente
Então exatamente uma sucede
E o cliente possui exatamente um contato primário
E a outra requisição recebe 409, respeitando INV-CON-01
```

### AC-003-33 — Exclusão e ativação de contrato simultâneas
```gherkin
Dado um cliente com um contrato em "DRAFT"
Quando a exclusão do cliente e a ativação do contrato chegam simultaneamente
Então ou o cliente é excluído e a ativação falha com 404,
      ou o contrato é ativado e a exclusão falha com 409 DEVTIME-2401
E em nenhum caso existe um contrato ativo apontando para um cliente excluído
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-002 | AC-003-26, AC-003-27 | ✅ |
| RN-003 | AC-003-07, AC-003-20 | ✅ |
| RN-004 | AC-003-15 | ✅ |
| RN-012 | AC-003-16 | ✅ |
| RN-401 | AC-003-13, AC-003-33 | ✅ |
| RN-402 | AC-003-01, AC-003-10, AC-003-18, AC-003-19 | ✅ |
| RN-403 | AC-003-12, AC-003-18, AC-003-25 | ✅ |
| RN-404 | AC-003-11, AC-003-21, AC-003-31 | ✅ |
| RN-405 | AC-003-06, AC-003-14 | ✅ |
| RN-406 | AC-003-04, AC-003-22, AC-003-32 | ✅ |
| RN-407 | AC-003-24 | ✅ |
| INV-CLI-01 | AC-003-12, AC-003-25 | ✅ |
| INV-CLI-02 | AC-003-11, AC-003-20, AC-003-21 | ✅ |
| INV-CLI-03 | AC-003-13 | ✅ |
| INV-CLI-04 | AC-003-14 | ✅ |
| INV-CON-01 | AC-003-22, AC-003-32 | ✅ |
| IMP-02 | AC-003-28 | ✅ |
| nota ² permissions | AC-003-27, AC-003-28, AC-003-30 | ✅ |
| RP-04 | AC-003-29 | ✅ |
| ART-024 | AC-003-26, AC-003-27 | ✅ |
