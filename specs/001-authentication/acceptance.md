# 001 — Authentication · Critérios de Aceite

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `AC-001-XX`, estável e imutável |
| **Formato** | Gherkin: `Dado` / `Quando` / `Então` / `E` / `Mas` |
| **Categoria** | Feliz · Erro · Extremo · Segurança · Concorrência |
| **Regra** | `RN-XXX` ou invariante verificada |

**Regras de escrita:** um cenário verifica **um** comportamento; `Então` descreve resultado observável (resposta HTTP, estado persistido, evento emitido), nunca implementação; todo cenário de erro declara o código `DEVTIME-XXXX` e o status HTTP.

## 2. Índice

| ID | Categoria | Cenário | Regra |
|---|---|---|---|
| AC-001-01 | Feliz | Cadastro cria conta, organização e categorias | RN-452, RN-501, INV-TEN-02 |
| AC-001-02 | Feliz | Verificação de e-mail ativa a conta | §4.2 SM |
| AC-001-03 | Feliz | Login com um único tenant entrega sessão pronta | — |
| AC-001-04 | Feliz | Login com múltiplos tenants exige seleção | CE-P-11 |
| AC-001-05 | Feliz | Seleção de organização completa a sessão | RN-459 |
| AC-001-06 | Feliz | Refresh rotaciona o token | ART-080 |
| AC-001-07 | Feliz | Logout revoga a sessão corrente | — |
| AC-001-08 | Feliz | Recuperação de senha redefine o acesso | RN-461 |
| AC-001-09 | Feliz | Alteração de senha preserva a sessão corrente | RN-454 |
| AC-001-10 | Feliz | Aceite de convite ativa o membership | RN-457 |
| AC-001-11 | Feliz | `GET /auth/me` retorna papel e permissões | — |
| AC-001-12 | Erro | E-mail já cadastrado | RN-452 |
| AC-001-13 | Erro | Senha fora da política | RN-451 |
| AC-001-14 | Erro | Credenciais inválidas (`DEVTIME-1001`) | AU-01 |
| AC-001-15 | Erro | Login sem e-mail verificado (`DEVTIME-1008`) | §4.2 SM |
| AC-001-16 | Erro | Conta bloqueada após 5 falhas | RN-453 |
| AC-001-17 | Erro | Token de verificação expirado | §4.2 SM |
| AC-001-18 | Erro | Token de redefinição reutilizado | RN-461 |
| AC-001-19 | Erro | Convite expirado | RN-457 |
| AC-001-20 | Erro | Membership suspenso ao selecionar organização | RN-459 |
| AC-001-21 | Erro | Requisição sem tenant selecionado | CE-P-11 |
| AC-001-22 | Erro | Escrita em tenant suspenso | RN-007 |
| AC-001-23 | Erro | Termos não aceitos | — |
| AC-001-24 | Extremo | E-mail com maiúsculas e espaços é normalizado | CX-01 |
| AC-001-25 | Extremo | Colisão de slug resolve com sufixo | CX-03 |
| AC-001-26 | Extremo | Token de verificação usado duas vezes — idempotente | CX-04 |
| AC-001-27 | Extremo | Redefinição desbloqueia conta bloqueada | CX-07 |
| AC-001-28 | Extremo | Falha no envio de e-mail não desfaz o cadastro | CX-12 |
| AC-001-29 | Extremo | Tenant suspenso aparece na seleção em modo leitura | CX-08 |
| AC-001-30 | Extremo | Aceite de convite não troca o tenant da sessão corrente | CX-09 |
| AC-001-31 | Segurança | Reuso de refresh revoga a cadeia | RN-005 |
| AC-001-32 | Segurança | "Esqueci a senha" não revela existência de conta | SG-02 |
| AC-001-33 | Segurança | Login não distingue e-mail inexistente de senha errada | SG-03 |
| AC-001-34 | Segurança | Recurso de outro tenant retorna 404 | RN-002, ART-024 |
| AC-001-35 | Segurança | `tenantId` do corpo é ignorado | ART-021 |
| AC-001-36 | Segurança | Token com assinatura adulterada é rejeitado | SG-11 |
| AC-001-37 | Segurança | Nenhuma resposta contém `passwordHash` | INV-USR-02 |
| AC-001-38 | Segurança | Revogar sessão de outro usuário retorna 404 | OWN-09 |
| AC-001-39 | Segurança | Rate limit no cadastro | ART-073 |
| AC-001-40 | Concorrência | Dois cadastros simultâneos com o mesmo e-mail | CX-02 |
| AC-001-41 | Concorrência | Refresh simultâneo em três abas | CX-05 |
| AC-001-42 | Concorrência | Refresh legítimo e reuso disparados juntos | RN-005 |
| AC-001-43 | Concorrência | Login concorrente na 5ª tentativa | RN-453 |

---

## 3. Cenários felizes

### AC-001-01 — Cadastro cria conta, organização e categorias
```gherkin
Dado que o e-mail "rafael@exemplo.com" não está cadastrado
Quando eu envio POST /api/v1/auth/register com e-mail "rafael@exemplo.com",
      senha "SenhaForte123", nome "Rafael Mendes", organização "Rafael Mendes Dev"
      e aceite dos termos igual a verdadeiro
Então recebo 201 Created
E a resposta contém "userId", "tenantId" e "status" igual a "PENDING_ACTIVATION"
E existe um Tenant com status "ACTIVE" e slug "rafael-mendes-dev"
E existe um User com status "PENDING_ACTIVATION"
E existe um Membership com papel "OWNER" e status "ACTIVE"
E existem exatamente 9 categorias com isSystem igual a verdadeiro no tenant criado
E um AuditLog com action "USER_REGISTERED" foi gravado
E a resposta não contém nenhum token de acesso
```

### AC-001-02 — Verificação de e-mail ativa a conta
```gherkin
Dado um usuário com status "PENDING_ACTIVATION" e um token de verificação válido
Quando eu envio POST /api/v1/auth/verify-email com esse token
Então recebo 200 OK
E o usuário passa a ter status "ACTIVE"
E o campo emailVerifiedAt está preenchido
E todos os memberships "INVITED" desse usuário passam a "ACTIVE"
E o token é marcado como consumido
```

### AC-001-03 — Login com um único tenant entrega sessão pronta
```gherkin
Dado um usuário "ACTIVE" com exatamente um Membership "ACTIVE"
Quando eu envio POST /api/v1/auth/login com credenciais corretas
Então recebo 200 OK
E a resposta contém accessToken, expiresIn igual a 900 e tenantSelectionRequired igual a falso
E a resposta contém os dados do tenant, o papel e a lista de permissões
E o access token possui a claim "tid" preenchida
E um cookie de refresh HttpOnly, Secure e SameSite=Strict foi definido
E o campo failedLoginAttempts do usuário é 0
E lastLoginAt foi atualizado
```

### AC-001-04 — Login com múltiplos tenants exige seleção
```gherkin
Dado um usuário "ACTIVE" com dois Memberships "ACTIVE" em tenants distintos
Quando eu envio POST /api/v1/auth/login com credenciais corretas
Então recebo 200 OK
E tenantSelectionRequired é verdadeiro
E a resposta contém a lista "tenants" com dois itens, cada um com id, nome e papel
E o access token NÃO possui a claim "tid"
E uma requisição a GET /api/v1/clients com esse token retorna 401 DEVTIME-1002
```

### AC-001-05 — Seleção de organização completa a sessão
```gherkin
Dado que possuo um access token de pré-seleção
E que sou membro "ACTIVE" do tenant "Acme Software"
Quando eu envio POST /api/v1/auth/select-tenant com o id desse tenant
Então recebo 200 OK
E o novo access token possui a claim "tid" igual ao tenant selecionado
E a resposta contém o papel e as permissões correspondentes a esse tenant
E um AuditLog com action "TENANT_SELECTED" foi gravado
```

### AC-001-06 — Refresh rotaciona o token
```gherkin
Dado que possuo um cookie de refresh válido
Quando eu envio POST /api/v1/auth/refresh
Então recebo 200 OK com um novo accessToken
E um novo cookie de refresh é definido, diferente do anterior
E o refresh token anterior fica marcado como rotacionado, com replacedById preenchido
E o refresh token anterior não pode mais ser usado para renovar
```

### AC-001-07 — Logout revoga a sessão corrente
```gherkin
Dado que possuo uma sessão ativa
Quando eu envio POST /api/v1/auth/logout
Então recebo 204 No Content
E o refresh token da sessão fica com revokedAt preenchido
E o cookie de refresh é removido
E uma nova tentativa de refresh com esse token retorna 401 DEVTIME-1004
```

### AC-001-08 — Recuperação de senha redefine o acesso
```gherkin
Dado um usuário "ACTIVE" com o e-mail "rafael@exemplo.com"
Quando eu envio POST /api/v1/auth/forgot-password com esse e-mail
Então recebo 202 Accepted
E um e-mail com token de validade de 1 hora é enviado
Quando eu envio POST /api/v1/auth/reset-password com esse token e a senha "NovaSenha456"
Então recebo 200 OK
E passwordChangedAt é atualizado
E todos os refresh tokens do usuário são revogados
E consigo autenticar com a nova senha
E não consigo autenticar com a senha anterior
```

### AC-001-09 — Alteração de senha preserva a sessão corrente
```gherkin
Dado que possuo duas sessões ativas, A e B
E que estou autenticado pela sessão A
Quando eu envio POST /api/v1/auth/change-password com a senha atual correta e a nova senha "OutraSenha789"
Então recebo 204 No Content
E o refresh token da sessão A continua válido
E o refresh token da sessão B fica revogado
E passwordChangedAt é atualizado
```

### AC-001-10 — Aceite de convite ativa o membership
```gherkin
Dado um convite válido para o tenant "Acme Software" com papel "MEMBER"
E que o e-mail do convite pertence a um usuário já cadastrado e "ACTIVE"
Quando eu envio GET /api/v1/auth/invitations/{token}
Então recebo 200 OK com o nome do tenant, o papel e userExists igual a verdadeiro
Quando eu envio POST /api/v1/auth/invitations/{token}/accept
Então recebo 200 OK
E o Membership passa de "INVITED" para "ACTIVE" com acceptedAt preenchido
E o token de convite é consumido
```

### AC-001-11 — `GET /auth/me` retorna papel e permissões
```gherkin
Dado que estou autenticado com tenant selecionado e papel "MEMBER"
Quando eu envio GET /api/v1/auth/me
Então recebo 200 OK
E a resposta contém os dados do usuário, do tenant, o papel "MEMBER" e a lista de permissões
E a lista de permissões corresponde exatamente à coluna MEMBER da matriz de permissions.md §7
E a resposta não contém passwordHash
```

---

## 4. Cenários de erro

### AC-001-12 — E-mail já cadastrado
```gherkin
Dado que o e-mail "rafael@exemplo.com" já pertence a um usuário não excluído
Quando eu envio POST /api/v1/auth/register com esse e-mail
Então recebo 409 Conflict com o código "DEVTIME-2452"
E nenhum Tenant, User ou Membership é criado
E a mensagem não revela o status nem o nome do usuário existente
```

### AC-001-13 — Senha fora da política
```gherkin
Dado que estou na tela de cadastro
Quando eu envio POST /api/v1/auth/register com a senha "senha123"
Então recebo 422 Unprocessable Entity com o código "DEVTIME-2451"
E a resposta indica os requisitos não atendidos, sem ecoar a senha informada
```

### AC-001-14 — Credenciais inválidas
```gherkin
Dado um usuário "ACTIVE" com senha correta "SenhaForte123"
Quando eu envio POST /api/v1/auth/login com a senha "SenhaErrada999"
Então recebo 401 Unauthorized com o código "DEVTIME-1001"
E failedLoginAttempts é incrementado em 1
E um AuditLog com action "USER_LOGIN_FAILED" é gravado, sem a senha tentada
```

### AC-001-15 — Login sem e-mail verificado
```gherkin
Dado um usuário com status "PENDING_ACTIVATION"
Quando eu envio POST /api/v1/auth/login com credenciais corretas
Então recebo 403 Forbidden com o código "DEVTIME-1008"
E nenhum access token é emitido
E a resposta indica a ação disponível de reenvio da verificação
```

### AC-001-16 — Conta bloqueada após 5 falhas
```gherkin
Dado um usuário "ACTIVE" com 4 tentativas de login falhas nos últimos 15 minutos
Quando eu envio POST /api/v1/auth/login com senha incorreta pela quinta vez
Então recebo 423 Locked com o código "DEVTIME-1006"
E o usuário passa a ter status "LOCKED"
E lockedUntil é igual a agora mais 30 minutos
E um e-mail de alerta de segurança é enviado
Quando eu envio POST /api/v1/auth/login com a senha CORRETA antes de lockedUntil
Então recebo 423 Locked com o código "DEVTIME-1006"
```

### AC-001-17 — Token de verificação expirado
```gherkin
Dado um token de verificação emitido há 8 dias
Quando eu envio POST /api/v1/auth/verify-email com esse token
Então recebo 410 Gone com o código "DEVTIME-1009"
E o usuário permanece com status "PENDING_ACTIVATION"
E a resposta oferece a ação de reenvio
```

### AC-001-18 — Token de redefinição reutilizado
```gherkin
Dado um token de redefinição já consumido com sucesso
Quando eu envio POST /api/v1/auth/reset-password novamente com o mesmo token
Então recebo 410 Gone com o código "DEVTIME-1007"
E a senha do usuário não é alterada
```

### AC-001-19 — Convite expirado
```gherkin
Dado um convite emitido há 8 dias
Quando eu envio GET /api/v1/auth/invitations/{token}
Então recebo 410 Gone com o código "DEVTIME-2457"
E o Membership permanece com status "INVITED"
```

### AC-001-20 — Membership suspenso ao selecionar organização
```gherkin
Dado que possuo um token de pré-seleção
E que meu Membership no tenant "Acme Software" está "SUSPENDED"
Quando eu envio POST /api/v1/auth/select-tenant com o id desse tenant
Então recebo 403 Forbidden com o código "DEVTIME-1102"
E nenhum token com a claim "tid" desse tenant é emitido
```

### AC-001-21 — Requisição sem tenant selecionado
```gherkin
Dado que possuo um access token de pré-seleção, sem a claim "tid"
Quando eu envio GET /api/v1/contracts
Então recebo 401 Unauthorized com o código "DEVTIME-1002"
Mas GET /api/v1/auth/tenants retorna 200 OK
E POST /api/v1/auth/select-tenant é acessível
```

### AC-001-22 — Escrita em tenant suspenso
```gherkin
Dado que estou autenticado em um tenant com status "SUSPENDED"
Quando eu envio POST /api/v1/clients com dados válidos
Então recebo 403 Forbidden com o código "DEVTIME-1201"
Mas GET /api/v1/clients retorna 200 OK
```

### AC-001-23 — Termos não aceitos
```gherkin
Quando eu envio POST /api/v1/auth/register com acceptedTerms igual a falso
Então recebo 400 Bad Request
E a resposta indica o campo "acceptedTerms" em errors[]
E nenhuma conta é criada
```

---

## 5. Cenários extremos

### AC-001-24 — E-mail com maiúsculas e espaços é normalizado
```gherkin
Dado que o e-mail "rafael@exemplo.com" já está cadastrado
Quando eu envio POST /api/v1/auth/register com o e-mail "  Rafael@Exemplo.COM  "
Então recebo 409 Conflict com o código "DEVTIME-2452"
E o e-mail foi normalizado para minúsculas e sem espaços antes da verificação
```

### AC-001-25 — Colisão de slug resolve com sufixo
```gherkin
Dado que já existe um tenant com o slug "acme-software"
Quando eu cadastro uma nova conta com a organização "Acme Software"
Então recebo 201 Created
E o novo tenant possui o slug "acme-software-2"
E o cadastro não falha por causa do slug
```

### AC-001-26 — Token de verificação usado duas vezes
```gherkin
Dado um token de verificação já consumido com sucesso
Quando eu envio POST /api/v1/auth/verify-email com o mesmo token
Então recebo 200 OK
E o usuário permanece "ACTIVE"
E emailVerifiedAt não é alterado
Mas dado um token substituído por reenvio (RN-457)
Quando eu envio POST /api/v1/auth/verify-email com o token antigo
Então recebo 410 Gone com o código "DEVTIME-1009"
```

> **Ajustado.** A versão anterior exigia `410` no segundo uso, contrariando §5.6 e CA-08 de
> `docs/04-api/authentication.md`, que exigem idempotência — clientes de e-mail com pré-visualização
> consomem o link antes do usuário. A divergência foi reportada e resolvida em favor de `docs/`, que
> é a fonte normativa. O caso que continua devolvendo `410` é o do token substituído por reenvio,
> semanticamente distinto e agora explícito no cenário.

### AC-001-27 — Redefinição desbloqueia conta bloqueada
```gherkin
Dado um usuário com status "LOCKED" e lockedUntil no futuro
E um token de redefinição válido emitido antes do bloqueio
Quando eu envio POST /api/v1/auth/reset-password com esse token e uma senha válida
Então recebo 200 OK
E o usuário passa a ter status "ACTIVE"
E failedLoginAttempts é 0
E lockedUntil é nulo
E consigo autenticar imediatamente com a nova senha
```

### AC-001-28 — Falha no envio de e-mail não desfaz o cadastro
```gherkin
Dado que o provedor de e-mail está indisponível
Quando eu envio POST /api/v1/auth/register com dados válidos
Então recebo 201 Created
E verificationEmailSent é falso
E o Tenant, o User, o Membership e as 9 categorias existem no banco
E a falha de envio é registrada em log com nível WARN
E a ação de reenvio está disponível
```

### AC-001-29 — Tenant suspenso aparece na seleção em modo leitura
```gherkin
Dado que sou membro "ACTIVE" de dois tenants, um "ACTIVE" e outro "SUSPENDED"
Quando eu envio GET /api/v1/auth/tenants
Então recebo 200 OK com os dois tenants
E o tenant suspenso está marcado com status "SUSPENDED"
Quando eu seleciono o tenant suspenso
Então recebo 200 OK com um token válido
E operações de leitura são permitidas
E operações de escrita retornam 403 DEVTIME-1201
```

### AC-001-30 — Aceite de convite não troca o tenant da sessão corrente
```gherkin
Dado que estou autenticado no tenant "Rafael Mendes Dev"
E que possuo um convite válido para o tenant "Acme Software"
Quando eu aceito o convite
Então recebo 200 OK
E o Membership em "Acme Software" passa a "ACTIVE"
Mas minha sessão corrente continua com a claim "tid" de "Rafael Mendes Dev"
E o tenant "Acme Software" passa a aparecer em GET /api/v1/auth/tenants
```

---

## 6. Cenários de segurança

### AC-001-31 — Reuso de refresh revoga a cadeia
```gherkin
Dado um refresh token R1 que já foi usado e rotacionado para R2
Quando eu envio POST /api/v1/auth/refresh apresentando R1
Então recebo 401 Unauthorized com o código "DEVTIME-1005"
E R1, R2 e todos os tokens da mesma cadeia ficam revogados
E um AuditLog com action "SECURITY_TOKEN_REUSE_DETECTED" é gravado
E a métrica auth.token.reuse_detected é incrementada
E uma tentativa subsequente de refresh com R2 retorna 401
```

### AC-001-32 — "Esqueci a senha" não revela existência de conta
```gherkin
Quando eu envio POST /api/v1/auth/forgot-password com o e-mail "existe@exemplo.com"
Então recebo 202 Accepted
Quando eu envio POST /api/v1/auth/forgot-password com o e-mail "naoexiste@exemplo.com"
Então recebo 202 Accepted
E as duas respostas possuem corpo idêntico, exceto o traceId
E a diferença entre os tempos de resposta é inferior a 50 milissegundos
```

### AC-001-33 — Login não distingue e-mail inexistente de senha errada
```gherkin
Quando eu envio POST /api/v1/auth/login com um e-mail inexistente
Então recebo 401 Unauthorized com o código "DEVTIME-1003"
Quando eu envio POST /api/v1/auth/login com um e-mail existente e senha incorreta
Então recebo 401 Unauthorized com o código "DEVTIME-1003"
E as duas respostas possuem corpo idêntico, exceto o traceId
E a diferença entre os tempos de resposta é inferior a 50 milissegundos
```

### AC-001-34 — Recurso de outro tenant retorna 404
```gherkin
Dado que estou autenticado no tenant A
E que existe um cliente com id X pertencente ao tenant B
Quando eu envio GET /api/v1/clients/X
Então recebo 404 Not Found com o código "DEVTIME-2002"
E não recebo 403
E o tempo de resposta é indistinguível do de um id inexistente
E um log de nível ERROR registra a tentativa cross-tenant
```

### AC-001-35 — `tenantId` do corpo é ignorado
```gherkin
Dado que estou autenticado no tenant A
Quando eu envio POST /api/v1/clients com um campo "tenantId" apontando para o tenant B
Então o cliente é criado no tenant A
E o campo "tenantId" do corpo é completamente ignorado
E nenhum dado é gravado no tenant B
```

### AC-001-36 — Token com assinatura adulterada é rejeitado
```gherkin
Dado um access token válido cujo payload foi alterado para role "OWNER"
Quando eu envio qualquer requisição autenticada com esse token
Então recebo 401 Unauthorized com o código "DEVTIME-1001"
E o mesmo ocorre para um token com o cabeçalho "alg" igual a "none"
```

### AC-001-37 — Nenhuma resposta contém `passwordHash`
```gherkin
Quando eu executo todos os endpoints da feature 001 com sucesso
Então nenhuma resposta contém os campos passwordHash, password ou tokenHash
E nenhuma resposta contém o valor bruto de um refresh token no corpo
```

### AC-001-38 — Revogar sessão de outro usuário retorna 404
```gherkin
Dado que estou autenticado como o usuário A
E que existe uma sessão com id S pertencente ao usuário B
Quando eu envio DELETE /api/v1/auth/sessions/S
Então recebo 404 Not Found com o código "DEVTIME-2002"
E a sessão do usuário B permanece ativa
```

### AC-001-39 — Rate limit no cadastro
```gherkin
Dado que já enviei 5 requisições a POST /api/v1/auth/register a partir do mesmo IP na última hora
Quando eu envio a sexta requisição
Então recebo 429 Too Many Requests
E a resposta contém o header Retry-After
E nenhuma conta é criada
```

---

## 7. Cenários de concorrência

### AC-001-40 — Dois cadastros simultâneos com o mesmo e-mail
```gherkin
Dado que duas requisições de cadastro com o e-mail "novo@exemplo.com" são enviadas simultaneamente
Quando ambas são processadas
Então exatamente uma recebe 201 Created
E a outra recebe 409 Conflict com o código "DEVTIME-2452"
E existe exatamente um User com esse e-mail
E existe exatamente um Tenant criado
E não existe tenant órfão sem OWNER
```

### AC-001-41 — Refresh simultâneo em três abas
```gherkin
Dado que a aplicação está aberta em três abas com o access token expirado
Quando as três abas disparam requisições autenticadas ao mesmo tempo
Então apenas UMA chamada a POST /api/v1/auth/refresh é enviada
E as três requisições originais são reenviadas com o novo token
E as três recebem 200 OK
E nenhuma aba é deslogada
```

### AC-001-42 — Refresh legítimo e reuso disparados juntos
```gherkin
Dado um refresh token R1 válido
Quando duas requisições de refresh apresentando R1 chegam simultaneamente
Então exatamente uma recebe 200 OK com um novo token
E a outra recebe 401 com o código "DEVTIME-1005"
E toda a cadeia é revogada
E o usuário é obrigado a autenticar novamente
```

> **Nota:** este cenário é indistinguível de um roubo de token real. A decisão de revogar a cadeia é intencional (RN-005): o falso positivo custa um login; o falso negativo custa uma sessão comprometida.

### AC-001-43 — Login concorrente na 5ª tentativa
```gherkin
Dado um usuário "ACTIVE" com 4 tentativas falhas nos últimos 15 minutos
Quando três tentativas com senha incorreta chegam simultaneamente
Então o usuário fica com status "LOCKED"
E failedLoginAttempts é maior ou igual a 5
E lockedUntil é definido exatamente uma vez
E exatamente um e-mail de alerta de segurança é enviado
```

---

## 8. Matriz de cobertura de regras

| Regra | Cenários | Coberta |
|---|---|:--:|
| RN-002 | AC-001-34 | ✅ |
| RN-005 | AC-001-31, AC-001-42 | ✅ |
| RN-007 | AC-001-22, AC-001-29 | ✅ |
| RN-008 | Coberto em `002-users` (cancelamento de tenant) | ✅ |
| RN-451 | AC-001-13 | ✅ |
| RN-452 | AC-001-01, AC-001-12, AC-001-24, AC-001-40 | ✅ |
| RN-453 | AC-001-16, AC-001-43 | ✅ |
| RN-454 | AC-001-09 | ✅ |
| RN-455 | AC-001-01 (cria OWNER) · demais em `002-users` | ✅ |
| RN-457 | AC-001-10, AC-001-19 | ✅ |
| RN-459 | AC-001-20, AC-001-29 | ✅ |
| RN-461 | AC-001-08, AC-001-18, AC-001-27 | ✅ |
| RN-501 | AC-001-01 | ✅ |
| INV-USR-02 | AC-001-37 | ✅ |
| INV-USR-04 | AC-001-04 (0 memberships → 403) | ✅ |
| INV-TEN-01 | AC-001-25 | ✅ |
| INV-TEN-02 | AC-001-01, AC-001-40 | ✅ |
| INV-RFT-01 | AC-001-31 | ✅ |
| ART-021 | AC-001-35 | ✅ |
| ART-024 | AC-001-34, AC-001-38 | ✅ |
| ART-073 | AC-001-39 | ✅ |
| CE-P-09 | AC-001-20 | ✅ |
| CE-P-11 | AC-001-04, AC-001-21 | ✅ |
