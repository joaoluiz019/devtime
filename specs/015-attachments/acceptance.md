# 015 — Attachments · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-015-XX`, estável e imutável |
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
| AC-015-01 | Feliz | Upload em ticket | §6.1 |
| AC-015-02 | Feliz | Upload em comentário | INV-ATT-01 |
| AC-015-03 | Feliz | Download após verificação limpa | RN-803 |
| AC-015-04 | Feliz | Sanitização do nome | RN-804 |
| AC-015-05 | Feliz | Deduplicação por checksum | RN-805 |
| AC-015-06 | Feliz | Exclusão pelo autor | OWN-07 |
| AC-015-07 | Feliz | Exclusão do último referenciador remove o binário | RN-805 |
| AC-015-08 | Feliz | Indicador de quota | RN-801 |
| AC-015-09 | Erro | Arquivo acima de 10 MB | RN-801 |
| AC-015-10 | Erro | Quota do tenant excedida | RN-801 |
| AC-015-11 | Erro | Tipo fora da allowlist | RN-802 |
| AC-015-12 | Erro | Assinatura divergente do tipo declarado | RN-802 |
| AC-015-13 | Erro | Download durante a verificação | RN-803 |
| AC-015-14 | Erro | Download de arquivo infectado | RN-803 |
| AC-015-15 | Erro | Download após falha de verificação | §6.3 |
| AC-015-16 | Erro | Limite de anexos no ticket | RN-806 |
| AC-015-17 | Erro | Limite de anexos no comentário | RN-806 |
| AC-015-18 | Erro | Nenhum alvo ou dois alvos | INV-ATT-01 |
| AC-015-19 | Erro | `VIEWER` tenta enviar | §7 permissions |
| AC-015-20 | Extremo | Arquivo de exatamente 10 MB | CX-01 |
| AC-015-21 | Extremo | Arquivo de 0 byte | CX-02 |
| AC-015-22 | Extremo | Executável renomeado | CX-03 |
| AC-015-23 | Extremo | ZIP renomeado para `.docx` | CX-05 |
| AC-015-24 | Extremo | Texto com bytes nulos | CX-07 |
| AC-015-25 | Extremo | Nome com *path traversal* | CX-08 |
| AC-015-26 | Extremo | Nome com 300 caracteres | CX-09 |
| AC-015-27 | Extremo | Exclusão de um de dois referenciadores | CX-12 |
| AC-015-28 | Extremo | Arquivo idêntico a um infectado | CX-14 |
| AC-015-29 | Extremo | Três falhas de verificação | CX-16 |
| AC-015-30 | Extremo | Limites por alvo são independentes | CX-19 |
| AC-015-31 | Extremo | Verificador indisponível | CX-20 |
| AC-015-32 | Segurança | **Arquivo EICAR é detectado** | RN-803, SG-02 |
| AC-015-33 | Segurança | EICAR dentro de ZIP | SG-03 |
| AC-015-34 | Segurança | Nenhuma liberação manual existe | §6.3, SG-09 |
| AC-015-35 | Segurança | `storageKey` não deriva do nome | SG-05 |
| AC-015-36 | Segurança | Deduplicação não atravessa tenants | SG-07 |
| AC-015-37 | Segurança | `storageKey` e checksum não expostos | §23 |
| AC-015-38 | Segurança | Storage privado | CP-16 |
| AC-015-39 | Segurança | Anexo de outro tenant retorna 404 | RN-002 |
| AC-015-40 | Segurança | Nenhuma rota de atualização | RN-011 |
| AC-015-41 | Segurança | Download é auditado | §18 |
| AC-015-42 | Segurança | Nome nunca aparece em log | §19.1 |
| AC-015-43 | Concorrência | Upload concorrente no limite do alvo | RN-806 |
| AC-015-44 | Concorrência | Uploads concorrentes do mesmo conteúdo | RN-805 |
| AC-015-45 | Concorrência | Exclusão e download simultâneos | INV-ATT-05 |

---

## 3. Cenários felizes

### AC-015-01 — Upload em ticket
```gherkin
Dado um ticket existente no meu tenant
E que estou autenticado com a permissão ATTACHMENT_UPLOAD
Quando eu envio um PNG válido de 2 MB para /api/v1/tickets/{id}/attachments
Então recebo 201 Created
E o anexo é criado com scanStatus igual a PENDING
E o download permanece bloqueado
E uma verificação antivírus é enfileirada
E um AuditLog com action "ATTACHMENT_UPLOADED" foi gravado
```

### AC-015-02 — Upload em comentário
```gherkin
Dado um comentário existente em um ticket
Quando eu envio um PDF válido para /api/v1/comments/{id}/attachments
Então recebo 201 Created
E o anexo possui commentId preenchido e ticketId nulo
E exatamente um dos dois campos de alvo está preenchido
```

### AC-015-03 — Download após verificação limpa
```gherkin
Dado um anexo cuja verificação concluiu com scanStatus igual a CLEAN
Quando eu envio GET /api/v1/attachments/{id}/download
Então eu sou direcionado para uma URL assinada com expiração
E o download é concluído com sucesso
E o binário não passou pela aplicação
E um AuditLog com action "ATTACHMENT_DOWNLOADED" registra quem baixou
```

### AC-015-04 — Sanitização do nome
```gherkin
Quando eu envio um arquivo cujo nome original é "relatório final (v2).pdf"
Então recebo 201 Created
E o fileName persistido está sanitizado, sem caracteres de controle
E o originalFileName preserva o nome enviado
E a extensão é mantida
```

### AC-015-05 — Deduplicação por checksum
```gherkin
Dado um anexo já enviado no meu tenant, com um checksum conhecido
Quando eu envio o mesmo arquivo, byte a byte idêntico, em outro ticket
Então recebo 201 Created
E um segundo registro de anexo é criado, com id distinto
E ambos os registros compartilham a mesma storageKey
E o binário não foi gravado novamente no storage
E o novo registro nasce com scanStatus igual a PENDING
```

### AC-015-06 — Exclusão pelo autor
```gherkin
Dado um anexo que eu enviei
Quando eu envio DELETE /api/v1/attachments/{id}
Então recebo 204 No Content
E o registro recebe deletedAt preenchido
E ele deixa de aparecer na listagem
```

### AC-015-07 — Exclusão do último referenciador remove o binário
```gherkin
Dado um anexo cuja storageKey é referenciada por apenas um registro não excluído
Quando eu excluo esse registro
Então recebo 204 No Content
E o binário é removido do object storage
E uma tentativa de acesso direto à storageKey falha
```

### AC-015-08 — Indicador de quota
```gherkin
Dado um tenant com 400 MB de anexos não excluídos
Quando eu consulto a listagem de anexos
Então o consumo de quota é informado
E o limite de 1 GB é informado
E o percentual é calculado corretamente
```

---

## 4. Cenários de erro

### AC-015-09 — Arquivo acima de 10 MB
```gherkin
Quando eu envio um arquivo de 11 MB
Então recebo 413 Payload Too Large com o código DEVTIME-2701
E a rejeição ocorre antes de qualquer leitura do conteúdo do arquivo
E nenhum binário é gravado no storage
```

### AC-015-10 — Quota do tenant excedida
```gherkin
Dado um tenant que já consumiu 1 GB em anexos não excluídos
Quando eu envio qualquer arquivo válido
Então recebo 413 Payload Too Large com o código DEVTIME-2701
E a mensagem informa o consumo atual e o limite
```

### AC-015-11 — Tipo fora da allowlist
```gherkin
Quando eu envio um arquivo declarando contentType "application/x-msdownload"
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702
E nenhum binário é gravado
```

### AC-015-12 — Assinatura divergente do tipo declarado
```gherkin
Quando eu envio um arquivo cujo conteúdo é um PDF, declarando contentType "image/png"
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702
E a rejeição decorre da verificação de assinatura binária, não da allowlist
E nenhum binário é gravado
```

### AC-015-13 — Download durante a verificação
```gherkin
Dado um anexo com scanStatus igual a PENDING
Quando eu tento baixá-lo
Então recebo 409 Conflict com o código DEVTIME-2703
E a interface explica que o arquivo está em verificação de segurança
```

### AC-015-14 — Download de arquivo infectado
```gherkin
Dado um anexo com scanStatus igual a INFECTED
Quando eu tento baixá-lo
Então recebo 403 Forbidden com o código DEVTIME-2703
E o binário já havia sido removido do storage
E a interface explica que o arquivo foi bloqueado por segurança
```

### AC-015-15 — Download após falha de verificação
```gherkin
Dado um anexo com scanStatus igual a FAILED após três tentativas
Quando eu tento baixá-lo
Então recebo 409 Conflict com o código DEVTIME-2703
E nenhuma opção de liberação é oferecida
E a interface orienta a reenviar o arquivo
```

### AC-015-16 — Limite de anexos no ticket
```gherkin
Dado um ticket com 20 anexos não excluídos
Quando eu tento enviar o 21º
Então recebo 422 Unprocessable Entity com o código DEVTIME-2704
E nenhum binário é gravado
```

### AC-015-17 — Limite de anexos no comentário
```gherkin
Dado um comentário com 5 anexos não excluídos
Quando eu tento enviar o 6º
Então recebo 422 Unprocessable Entity com o código DEVTIME-2704
```

### AC-015-18 — Nenhum alvo ou dois alvos
```gherkin
Quando eu tento criar um anexo sem informar ticket nem comentário
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
Quando eu tento criar informando ambos
Então recebo 422 Unprocessable Entity com o código DEVTIME-2000
```

### AC-015-19 — `VIEWER` tenta enviar
```gherkin
Dado que estou autenticado com o papel VIEWER
Quando eu tento enviar um anexo
Então recebo 403 Forbidden com o código DEVTIME-1101
E o campo requiredPermission indica ATTACHMENT_UPLOAD
Mas eu consigo baixar anexos com scanStatus CLEAN normalmente
```

---

## 5. Cenários extremos

### AC-015-20 — Arquivo de exatamente 10 MB
```gherkin
Quando eu envio um arquivo de exatamente 10.485.760 bytes
Então recebo 201 Created
Quando eu envio um arquivo com um byte a mais
Então recebo 413 com o código DEVTIME-2701
```

### AC-015-21 — Arquivo de 0 byte
```gherkin
Quando eu envio um arquivo vazio
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702
E a rejeição decorre de nenhuma assinatura corresponder ao conteúdo
```

### AC-015-22 — Executável renomeado
```gherkin
Dado um executável do sistema operacional
Quando eu o renomeio para cada extensão da allowlist
E eu o envio declarando o contentType correspondente
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702 em todos os casos
E nenhum binário é gravado em nenhuma tentativa
```

### AC-015-23 — ZIP renomeado para `.docx`
```gherkin
Dado um arquivo ZIP comum, sem estrutura de documento Office
Quando eu o envio declarando o contentType de documento Word
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702
E a rejeição decorre da verificação do manifesto interno
Mas o mesmo arquivo declarado como application/zip é aceito
```

### AC-015-24 — Texto com bytes nulos
```gherkin
Quando eu envio um arquivo com bytes nulos declarando contentType "text/plain"
Então recebo 415 Unsupported Media Type com o código DEVTIME-2702
E a rejeição decorre da heurística de detecção de binário
```

### AC-015-25 — Nome com *path traversal*
```gherkin
Quando eu envio um arquivo cujo nome original é "../../etc/passwd"
Então recebo 201 Created
E o fileName persistido não contém nenhuma sequência de travessia de diretório
E o originalFileName preserva o valor enviado como metadado
E a storageKey não contém nenhuma parte do nome enviado
```

### AC-015-26 — Nome com 300 caracteres
```gherkin
Quando eu envio um arquivo cujo nome possui 300 caracteres
Então recebo 201 Created
E o fileName persistido possui no máximo 255 caracteres
E a extensão original é preservada no final do nome truncado
```

### AC-015-27 — Exclusão de um de dois referenciadores
```gherkin
Dado dois registros de anexo compartilhando a mesma storageKey
Quando eu excluo um deles
Então recebo 204 No Content
E o binário permanece no storage
E o outro registro continua podendo ser baixado normalmente
```

### AC-015-28 — Arquivo idêntico a um infectado
```gherkin
Dado um anexo anterior marcado como INFECTED, cujo binário foi removido
Quando eu envio um arquivo com o mesmo checksum
Então o binário é gravado novamente no storage
E o novo registro nasce com scanStatus igual a PENDING
E ele é verificado novamente
E ele é marcado como INFECTED ao final da verificação
```

### AC-015-29 — Três falhas de verificação
```gherkin
Dado que o verificador falha por erro em todas as tentativas
Quando o job de verificação executa a primeira, a segunda e a terceira tentativa
Então o scanStatus permanece FAILED com attemptCount igual a 3
E nenhuma quarta tentativa ocorre
E o download permanece bloqueado permanentemente
E nenhuma rota nem opção de interface permite liberá-lo
```

### AC-015-30 — Limites por alvo são independentes
```gherkin
Dado um ticket com 20 anexos e um comentário desse ticket com 5 anexos
Quando eu consulto ambos
Então os 25 anexos existem
E o limite de 20 se aplica ao ticket e o de 5 ao comentário, independentemente
Quando eu tento enviar mais um em qualquer um dos dois
Então recebo 422 com o código DEVTIME-2704
```

### AC-015-31 — Verificador indisponível
```gherkin
Dado que o verificador antivírus está indisponível
Quando eu envio três arquivos válidos
Então os três são aceitos com 201 Created e scanStatus PENDING
E nenhum download é liberado
Quando o verificador é restabelecido
Então os três são verificados e passam a CLEAN
E os downloads são liberados
```

---

## 6. Cenários de segurança

### AC-015-32 — **Arquivo EICAR é detectado**
```gherkin
Dado o arquivo de teste padrão EICAR, com extensão e contentType permitidos
Quando eu o envio como anexo
Então recebo 201 Created com scanStatus igual a PENDING
Quando a verificação antivírus é processada
Então o scanStatus passa a INFECTED
E o binário é removido do object storage
E uma tentativa de download retorna 403 com o código DEVTIME-2703
E quem enviou o arquivo é notificado
E um AuditLog com action "ATTACHMENT_SCAN_INFECTED" registra a ameaça e o endereço de origem do upload
E um log de nível ERROR é emitido
```

### AC-015-33 — EICAR dentro de ZIP
```gherkin
Dado um arquivo ZIP contendo o arquivo de teste EICAR
Quando eu o envio declarando contentType "application/zip"
Então o arquivo é aceito pela allowlist e pela verificação de assinatura
Quando a verificação antivírus é processada
Então o scanStatus passa a INFECTED
E o binário é removido do storage
E o download é bloqueado
```

### AC-015-34 — Nenhuma liberação manual existe
```gherkin
Quando eu inspeciono todas as rotas expostas pela aplicação
Então não existe nenhuma rota que altere scanStatus para CLEAN
E não existe nenhuma rota de atualização de anexo
E nenhuma opção de interface oferece liberar um arquivo não verificado
E nem OWNER nem ADMIN possuem qualquer caminho para isso
```

### AC-015-35 — `storageKey` não deriva do nome
```gherkin
Quando eu envio arquivos com nomes contendo travessia de diretório,
      caracteres especiais e nomes muito longos
Então nenhuma storageKey gerada contém qualquer parte dos nomes enviados
E todas as storageKey são identificadores opacos gerados pelo sistema
```

### AC-015-36 — Deduplicação não atravessa tenants
```gherkin
Dado um arquivo já enviado no tenant A
Quando o mesmo arquivo, byte a byte idêntico, é enviado no tenant B
Então um novo binário é gravado no storage
E as duas storageKey são distintas
E nenhuma informação sobre a existência do arquivo no tenant A é revelada
```

### AC-015-37 — `storageKey` e checksum não expostos
```gherkin
Quando eu consulto qualquer anexo pela API
Então o campo storageKey não está presente na resposta
E o campo checksumSha256 não está presente na resposta
E nenhum endpoint os expõe
```

### AC-015-38 — Storage privado
```gherkin
Dado um anexo com scanStatus CLEAN
Quando eu tento acessar o objeto no storage sem URL assinada
Então o acesso é recusado
E o único caminho de acesso é a URL assinada com expiração
```

### AC-015-39 — Anexo de outro tenant retorna 404
```gherkin
Dado um anexo pertencente ao tenant B
E que estou autenticado no tenant A
Quando eu tento baixá-lo ou excluí-lo por id
Então recebo 404 Not Found com o código DEVTIME-2002
E nunca recebo 403
```

### AC-015-40 — Nenhuma rota de atualização
```gherkin
Quando eu tento alterar contentType, fileName, sizeBytes ou storageKey de um anexo
Então nenhuma rota permite essa operação
E a tentativa retorna 404 ou 405
E alterar o contentType após a verificação seria um caminho para burlar a validação de assinatura
```

### AC-015-41 — Download é auditado
```gherkin
Quando eu baixo um anexo
Então um AuditLog com action "ATTACHMENT_DOWNLOADED" é gravado
E ele registra quem baixou, quando e o endereço de origem
```

### AC-015-42 — Nome nunca aparece em log
```gherkin
Dado que eu envio, baixo e excluo anexos, incluindo casos rejeitados
Quando eu inspeciono os logs de aplicação gerados
Então nenhum log contém fileName nem originalFileName
E os logs contêm apenas identificadores, contentType, tamanho e traceId
```

---

## 7. Cenários de concorrência

### AC-015-43 — Upload concorrente no limite do alvo
```gherkin
Dado um ticket com 19 anexos
Quando duas requisições simultâneas tentam enviar o 20º e o 21º anexo
Então exatamente uma é aceita com 201 Created
E a outra recebe 422 com o código DEVTIME-2704
E o ticket termina com exatamente 20 anexos
E nunca com 21
```

### AC-015-44 — Uploads concorrentes do mesmo conteúdo
```gherkin
Quando duas requisições simultâneas enviam o mesmo arquivo idêntico no mesmo tenant
Então ambas são aceitas com 201 Created
E dois registros distintos são criados
E ou ambos compartilham a mesma storageKey, ou dois binários idênticos são gravados
E nenhum registro fica sem binário correspondente
```

### AC-015-45 — Exclusão e download simultâneos
```gherkin
Dado um anexo CLEAN cuja storageKey é referenciada por apenas um registro
Quando uma requisição o exclui e outra solicita o download, em paralelo
Então ou o download é atendido antes da remoção do binário
Ou a solicitação de download retorna 404 após a exclusão
E nunca resulta em uma URL assinada apontando para um binário já removido
E nunca um binário permanece no storage sem registro que o referencie
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-801 | AC-015-09, AC-015-10, AC-015-20 | ✅ |
| RN-802 | AC-015-11, AC-015-12, AC-015-21 a AC-015-24 | ✅ |
| RN-803 | AC-015-03, AC-015-13 a AC-015-15, AC-015-32, AC-015-33 | ✅ |
| RN-804 | AC-015-04, AC-015-25, AC-015-26 | ✅ |
| RN-805 | AC-015-05, AC-015-07, AC-015-27, AC-015-28, AC-015-36, AC-015-44 | ✅ |
| RN-806 | AC-015-16, AC-015-17, AC-015-30, AC-015-43 | ✅ |
| RN-003 | AC-015-06 | ✅ |
| RN-011 | AC-015-40 | ✅ |
| RN-002 | AC-015-39 | ✅ |
| RN-006 | AC-015-01, AC-015-32, AC-015-41 | ✅ |
| INV-ATT-01 | AC-015-02, AC-015-18 | ✅ |
| INV-ATT-02 | AC-015-13 a AC-015-15 | ✅ |
| INV-ATT-03 | AC-015-12, AC-015-22 a AC-015-24 | ✅ |
| INV-ATT-04 | AC-015-04, AC-015-25, AC-015-26 | ✅ |
| INV-ATT-05 | AC-015-07, AC-015-27, AC-015-45 | ✅ |
| INV-ATT-06 | AC-015-14, AC-015-32 | ✅ |
| §6.1 ordem | AC-015-09, AC-015-11, AC-015-12 | ✅ |
| §6.2 allowlist e assinaturas | AC-015-21 a AC-015-24 | ✅ |
| §6.3 estados | AC-015-15, AC-015-29, AC-015-34 | ✅ |
| §6.4 deduplicação | AC-015-05, AC-015-36 | ✅ |
| §4.9 SM | AC-015-29, AC-015-31, AC-015-32 | ✅ |
| §7 permissions | AC-015-19 | ✅ |
| §18 auditoria | AC-015-41 | ✅ |
| §19.1 LGPD | AC-015-38, AC-015-42 | ✅ |
| §23 DTOs | AC-015-37 | ✅ |
| SG-01 | AC-015-22 | ✅ |
| **SG-02** | **AC-015-32** | ✅ |
| SG-03 | AC-015-33 | ✅ |
| SG-04 / SG-05 | AC-015-25, AC-015-35 | ✅ |
| SG-07 | AC-015-36 | ✅ |
| SG-08 | AC-015-38 | ✅ |
| SG-09 | AC-015-34 | ✅ |
| SG-12 | AC-015-14, AC-015-32 | ✅ |

**Verificação de completude:** toda regra da §6 da spec possui ao menos um cenário. `AC-015-32` (EICAR) é o cenário mais importante da feature — ele é o **gatilho de acionamento** do risco crítico identificado em §9 de `implementation-order.md`, e sua falha bloqueia a entrega.
