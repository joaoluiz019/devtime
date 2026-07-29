# 001 — Authentication · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-001-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` de todo teste de regra inicia com o identificador da regra — exemplo: `RN-453: bloqueia a conta após 5 falhas em 15 minutos`. Isso permite extrair automaticamente a cobertura de regras a partir do relatório de testes.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `PasswordPolicyValidator`, `SlugGenerator`, `EmailNormalizer`, `JwtService`, `OpaqueTokenGenerator` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + Repository + PostgreSQL real | Testcontainers | Todo fluxo da §7 e §8 do spec |
| API | Controller + serialização + segurança | `@WebMvcTest` + `@SpringBootTest` | Todos os 17 endpoints |
| Isolamento | Tenancy | Suíte dedicada | Todos os endpoints |
| Frontend | Store, interceptor, guards, páginas | Jest + Testing Library + MSW | ≥ 90% em store e interceptor |
| E2E | Jornada completa | Playwright | Cadastro → verificação → login → seleção |
| Performance | Login, refresh, validação de JWT | Gatling / k6 | Metas da §20 do spec |
| Segurança | Enumeração, timing, token forjado, rate limit | JUnit + scripts | Todos os vetores da §19 |
| Regressão | Suíte completa a cada PR | CI | 100% verde |

---

## 3. Testes unitários

### TS-001-01 — Política de senha (RN-451)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas senhas conformes são aceitas |
| **Pré-condição** | `PasswordPolicyValidator` instanciado com a lista de senhas comuns carregada |
| **Passos** | 1. Validar cada entrada da tabela abaixo |
| **Resultado esperado** | Aceitação ou rejeição conforme a tabela, com `DEVTIME-2451` nas rejeições |

| Entrada | Resultado | Motivo |
|---|:--:|---|
| `SenhaForte123` | ✅ | Atende a tudo |
| `senhaforte123` | ❌ | Sem maiúscula |
| `SENHAFORTE123` | ❌ | Sem minúscula |
| `SenhaForteAbc` | ❌ | Sem dígito |
| `Senha12` | ❌ | Menos de 10 caracteres |
| `Password123` | ❌ | Consta na lista de senhas comuns |
| `Ab1` + 7 espaços | ❌ | Espaços não contam como complexidade |
| `Sênhã Fôrte123` | ✅ | Acentos e espaço internos são permitidos |
| String de 129 caracteres | ❌ | Excede o máximo |

### TS-001-02 — Normalização de e-mail (RN-452)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a normalização é aplicada antes de qualquer verificação |
| **Passos** | Normalizar `"  Rafael@Exemplo.COM  "`, `"a@b.com"`, `"A@B.COM"` |
| **Resultado esperado** | `rafael@exemplo.com`, `a@b.com`, `a@b.com`. A parte local **não** é alterada além da caixa |

### TS-001-03 — Geração de slug (INV-TEN-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar aderência ao regex e resolução de colisão |
| **Passos** | Gerar slug para `"Rafael Mendes Dev"`, `"Açaí & Cia"`, `"---"`, nome de 200 caracteres, e para um nome cujo slug já existe |
| **Resultado esperado** | `rafael-mendes-dev`; acentos removidos e `&` descartado; nome sem caracteres válidos gera fallback determinístico; truncamento em 60; colisão gera sufixo `-2`, `-3` |

### TS-001-04 — Emissão e validação de JWT
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que as claims são corretas e que tokens inválidos são rejeitados |
| **Passos** | 1. Emitir token com tenant. 2. Emitir token de pré-seleção. 3. Validar token expirado. 4. Validar token com payload adulterado. 5. Validar token com `alg=none`. 6. Validar token assinado com outro segredo |
| **Resultado esperado** | (1) contém `sub`, `tid`, `role`, `perms`, `exp`, `iat`, `jti`; (2) sem `tid`; (3)–(6) rejeitados com `DEVTIME-1001` |

### TS-001-05 — Token opaco e hash
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o valor bruto nunca é persistido |
| **Passos** | 1. Gerar 10.000 tokens. 2. Verificar entropia e ausência de colisão. 3. Persistir e inspecionar a coluna |
| **Resultado esperado** | 256 bits de entropia; zero colisões; a coluna contém apenas o SHA-256 de 64 caracteres hexadecimais |

---

## 4. Testes de integração

### TS-001-06 — Atomicidade do cadastro
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que tenant, usuário, membership e categorias são criados em uma transação |
| **Pré-condição** | Banco limpo |
| **Passos** | 1. Executar `register`. 2. Contar registros. 3. Repetir forçando falha no seed de categorias |
| **Resultado esperado** | Sucesso: 1 tenant, 1 user, 1 membership OWNER `ACTIVE`, 9 categorias `isSystem`. Falha no seed: **nenhum** registro persiste — rollback total |

### TS-001-07 — Ordem de verificação do login (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a ordem das 7 verificações é respeitada |
| **Passos** | Executar login para: usuário bloqueado com senha correta; usuário bloqueado com senha errada; usuário não verificado com senha correta; usuário sem membership ativo |
| **Resultado esperado** | Bloqueado (ambos) → `423 DEVTIME-1006`; não verificado → `403 DEVTIME-1004`; sem membership → `403 DEVTIME-1102`. Em nenhum caso um token é emitido |

### TS-001-08 — Bloqueio e desbloqueio (RN-453)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o bloqueio por 5 falhas e o desbloqueio automático |
| **Passos** | 1. Falhar 4 vezes. 2. Autenticar com sucesso. 3. Verificar contador. 4. Falhar 5 vezes. 5. Avançar o relógio em 31 min. 6. Autenticar |
| **Resultado esperado** | (3) contador = 0; (4) `LOCKED` com `lockedUntil = now+30min`; (6) login bem-sucedido e contador = 0 |

### TS-001-09 — Rotação e detecção de reuso (RN-005)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a rotação e a revogação em cadeia |
| **Passos** | 1. Login → R1. 2. Refresh → R2. 3. Refresh → R3. 4. Apresentar R1 |
| **Resultado esperado** | (2) e (3) sucesso, com `replacedById` encadeado; (4) `401 DEVTIME-1005`, R1, R2 e R3 revogados, `AuditLog` de severidade crítica e métrica incrementada |

### TS-001-10 — Revogado não é reuso (CX-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que logout não é confundido com roubo |
| **Passos** | 1. Login → R1. 2. Logout. 3. Apresentar R1 |
| **Resultado esperado** | `401 DEVTIME-1001` (não `1005`); nenhuma revogação em cadeia; nenhum evento de segurança |

### TS-001-11 — Troca de senha e sessões (RN-454)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas a sessão corrente sobrevive |
| **Passos** | 1. Criar 3 sessões. 2. Alterar a senha pela sessão A. 3. Tentar refresh em A, B e C |
| **Resultado esperado** | A funciona; B e C retornam `401`; `passwordChangedAt` atualizado |

### TS-001-12 — Ciclo de verificação de e-mail
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar uso único, expiração e ativação de memberships |
| **Passos** | 1. Verificar com token válido. 2. Verificar novamente. 3. Verificar com token de 8 dias. 4. Verificar usuário com membership `INVITED` |
| **Resultado esperado** | (1) `200` e `ACTIVE`; (2) e (3) `410 DEVTIME-1007`; (4) membership passa a `ACTIVE` |

### TS-001-13 — Seleção de tenant (RN-459)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as regras de seleção |
| **Passos** | Selecionar: tenant válido; tenant com membership `SUSPENDED`; tenant do qual não sou membro; tenant `SUSPENDED` com membership `ACTIVE` |
| **Resultado esperado** | `200`; `403 DEVTIME-1102`; `404 DEVTIME-2002`; `200` com escrita bloqueada por `DEVTIME-1201` |

### TS-001-14 — Aceite de convite (RN-457)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar aceite, expiração e invalidação por reenvio |
| **Passos** | 1. Aceitar convite válido. 2. Aceitar convite de 8 dias. 3. Reenviar e aceitar com o token antigo. 4. Aceitar com usuário inexistente |
| **Resultado esperado** | (1) `ACTIVE` com `acceptedAt`; (2) e (3) `410 DEVTIME-2457`; (4) fluxo de cadastro com e-mail pré-preenchido e imutável |

### TS-001-15 — E-mail pós-commit (TX-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o envio não participa da transação |
| **Passos** | 1. Cadastrar com o provedor de e-mail indisponível. 2. Verificar o banco. 3. Verificar o log |
| **Resultado esperado** | `201` com `verificationEmailSent = false`; todos os registros persistidos; log `WARN` sem o corpo do e-mail |

---

## 5. Testes de API

### TS-001-16 — Contrato de todos os endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar aderência a `docs/04-api/authentication.md` |
| **Passos** | Executar os 17 endpoints e comparar o corpo com o contrato documentado |
| **Resultado esperado** | Campos, tipos e status coincidem; toda resposta de erro segue RFC 7807 com `code`, `traceId` e `instance` |

### TS-001-17 — Ausência de campos sensíveis
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-USR-02 e CP-02 |
| **Passos** | Serializar todos os DTOs de resposta e varrer as chaves recursivamente |
| **Resultado esperado** | Nenhuma ocorrência de `passwordHash`, `password`, `tokenHash` ou valor bruto de refresh token |

### TS-001-18 — Validação de formato (`400`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a camada 1 de validação |
| **Passos** | Enviar cada endpoint com campo ausente, tipo errado, tamanho excedido e `acceptedTerms = false` |
| **Resultado esperado** | `400` com `errors[]` por campo, sem eco de valor sensível |

### TS-001-19 — Cookie de refresh
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os atributos de segurança do cookie |
| **Passos** | Inspecionar o `Set-Cookie` após login, refresh e logout |
| **Resultado esperado** | `HttpOnly`, `Secure`, `SameSite=Strict`, path restrito a `/api/v1/auth`, `Max-Age` de 30 dias; removido no logout |

### TS-001-20 — Rate limit
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os limites e a impossibilidade de contorno |
| **Passos** | 1. 6 cadastros do mesmo IP em 1h. 2. 11 logins em 1 min. 3. Repetir variando `X-Forwarded-For` e `X-Real-IP` |
| **Resultado esperado** | `429` com `Retry-After` a partir do excedente; headers de cliente não contornam o limite |

---

## 6. Testes de isolamento entre tenants

### TS-001-21 — Isolamento por endpoint
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AQ-03 |
| **Pré-condição** | Tenants A e B com dados equivalentes |
| **Passos** | Autenticado em A, acessar por id direto todos os recursos de B, em todos os endpoints da aplicação |
| **Resultado esperado** | `404 DEVTIME-2002` em 100% dos casos; nunca `403`; diferença de tempo de resposta inferior a 50 ms em relação a um id inexistente |

### TS-001-22 — `tenantId` do corpo é ignorado (ART-021)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-04 |
| **Passos** | Enviar `tenantId` de outro tenant no corpo, na query e no header `X-Tenant-Id` em operações de escrita |
| **Resultado esperado** | O recurso é criado no tenant do JWT; nenhum dado é gravado no outro tenant |

### TS-001-23 — `@CrossTenant` exaustivo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas os três métodos justificados ignoram o filtro |
| **Passos** | Varredura ArchUnit por todos os métodos anotados com `@CrossTenant` |
| **Resultado esperado** | Exatamente `UserRepository.findByEmail`, `MembershipRepository.findActiveByUserId` e `RefreshTokenRepository.findByTokenHash`, cada um com `reason` preenchido |

---

## 7. Testes de frontend

### TS-001-24 — Fila de refresh (CX-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que requisições concorrentes disparam um único refresh |
| **Pré-condição** | MSW com `/auth/refresh` instrumentado; access token expirado |
| **Passos** | 1. Disparar 5 requisições autenticadas simultâneas. 2. Contar chamadas a `/auth/refresh` |
| **Resultado esperado** | Exatamente 1 chamada; as 5 requisições são reenviadas e concluídas com sucesso |

### TS-001-25 — Falha de refresh limpa a sessão
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento de FA-05 |
| **Passos** | 1. Configurar `/auth/refresh` para retornar `401 DEVTIME-1005`. 2. Disparar uma requisição autenticada |
| **Resultado esperado** | `AuthStore` limpo; redirecionamento para `/auth/login` com `returnUrl`; toast informando encerramento por segurança |

### TS-001-26 — Guards
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o roteamento por estado de sessão |
| **Passos** | Navegar para rota protegida sem sessão; com sessão de pré-seleção; com sessão completa; para rota pública já autenticado; para rota sem permissão |
| **Resultado esperado** | `/auth/login?returnUrl=`; `/auth/select-tenant`; acesso liberado; redirecionamento para `/dashboard`; `/forbidden` |

### TS-001-27 — Restauração de sessão após recarga
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar OB-03 |
| **Passos** | 1. Autenticar. 2. Recarregar a página. 3. Observar o estado inicial |
| **Resultado esperado** | Estado de carregamento (nunca a tela de login piscando); refresh silencioso; sessão restaurada com o mesmo tenant |

### TS-001-28 — Troca de organização limpa os stores (CE-F-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que dados do tenant anterior não vazam para a UI |
| **Passos** | 1. Carregar listas no tenant A. 2. Trocar para o tenant B. 3. Inspecionar os stores |
| **Resultado esperado** | Todos os stores de feature são reinicializados; nenhum dado de A permanece em memória ou visível |

### TS-001-29 — Acessibilidade de P01–P07
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 a AC-10 de `frontend.md` |
| **Passos** | Executar axe-core em cada tela e percorrer os formulários apenas por teclado |
| **Resultado esperado** | Zero violações; todo campo com `<label>`; foco visível; erros anunciados por `aria-live` |

---

## 8. Testes E2E

### TS-001-30 — Jornada de onboarding
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo principal §7 de ponta a ponta |
| **Pré-condição** | Ambiente com captura de e-mail (Mailpit ou equivalente) |
| **Passos** | 1. Cadastrar em P02. 2. Abrir o e-mail e clicar no link. 3. Verificar em P03. 4. Autenticar em P01. 5. Chegar ao dashboard |
| **Resultado esperado** | Fluxo completo sem erro; tenant com 9 categorias; sessão ativa |

### TS-001-31 — Jornada multi-tenant
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FA-01, AC-001-04 e AC-001-05 |
| **Passos** | 1. Autenticar com usuário de 2 tenants. 2. Selecionar em P06. 3. Trocar de organização pela topbar |
| **Resultado esperado** | P06 exibida; após a seleção, dados do tenant correto; após a troca, stores limpos e dados do novo tenant |

### TS-001-32 — Jornada de recuperação de senha
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-001-08 e AC-001-18 |
| **Passos** | 1. Solicitar em P04. 2. Abrir o e-mail. 3. Redefinir em P05. 4. Autenticar. 5. Tentar reusar o link |
| **Resultado esperado** | Redefinição bem-sucedida; login com a nova senha; reuso exibe "Link expirado" com ação de nova solicitação |

---

## 9. Testes de performance

### TS-001-33 — Latência de login
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta p95 < 400 ms |
| **Pré-condição** | 10.000 usuários; BCrypt custo 12 |
| **Passos** | 200 logins concorrentes por 5 minutos |
| **Resultado esperado** | p95 < 400 ms; p99 < 800 ms; zero erro `5xx` |

### TS-001-34 — Latência de refresh e de validação de JWT
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a autorização não consulta o banco |
| **Passos** | 1. 1.000 refreshes concorrentes. 2. 5.000 requisições autenticadas com contagem de queries |
| **Resultado esperado** | Refresh p95 < 100 ms; validação de JWT sem nenhuma query; overhead < 2 ms por requisição |

---

## 10. Testes de segurança

### TS-001-35 — Timing de enumeração
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-01, SG-02 e SG-03 |
| **Passos** | 1.000 requisições de login com e-mail existente e senha errada, e 1.000 com e-mail inexistente; idem para `forgot-password` e `register` |
| **Resultado esperado** | Diferença de mediana inferior a 50 ms; corpos idênticos exceto `traceId` |

### TS-001-36 — Token forjado e adulterado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-11 |
| **Passos** | Enviar token com `alg=none`; com `role` alterado; assinado com outro segredo; expirado; sem `sub`; com `tid` de outro tenant |
| **Resultado esperado** | `401 DEVTIME-1001` em todos os casos; o `tid` adulterado **não** concede acesso, pois a assinatura falha |

### TS-001-37 — Vazamento em log
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-084 e CP-11 |
| **Passos** | Executar toda a suíte capturando os logs e varrer por padrões de e-mail, senha, hash BCrypt e token |
| **Resultado esperado** | Zero ocorrência de e-mail em claro, senha, `passwordHash` ou valor de token |

### TS-001-38 — Cabeçalhos de segurança
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a configuração de borda |
| **Passos** | Inspecionar os headers das respostas |
| **Resultado esperado** | `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy` e `Referrer-Policy` presentes; nenhum header revela versão de framework |

---

## 11. Testes de regressão

| ID | Objetivo | Gatilho |
|---|---|---|
| TS-001-39 | Suíte completa da feature executada a cada PR que toque `auth`, `user`, `tenant` ou `shared/security` | Todo PR |
| TS-001-40 | Suíte de isolamento executada a cada endpoint novo em qualquer feature | Todo PR com endpoint novo |
| TS-001-41 | Teste de contrato OpenAPI comparando a especificação publicada com a implementação | Todo PR |
| TS-001-42 | ArchUnit verificando `@CrossTenant` exaustivo e as regras AR-01 a AR-09 | Todo PR |

---

## 12. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-002 | TS-001-21 | AC-001-34 |
| RN-005 | TS-001-09, TS-001-10 | AC-001-31, AC-001-42 |
| RN-007 | TS-001-13 | AC-001-22, AC-001-29 |
| RN-451 | TS-001-01 | AC-001-13 |
| RN-452 | TS-001-02, TS-001-06 | AC-001-01, AC-001-12, AC-001-24, AC-001-40 |
| RN-453 | TS-001-08 | AC-001-16, AC-001-43 |
| RN-454 | TS-001-11 | AC-001-09 |
| RN-457 | TS-001-14 | AC-001-10, AC-001-19 |
| RN-459 | TS-001-13 | AC-001-20 |
| RN-461 | TS-001-12 | AC-001-08, AC-001-18, AC-001-27 |
| RN-501 | TS-001-06 | AC-001-01 |
| INV-USR-02 | TS-001-17 | AC-001-37 |
| INV-TEN-01 | TS-001-03 | AC-001-25 |
| INV-TEN-02 | TS-001-06 | AC-001-01, AC-001-40 |
| INV-RFT-01 | TS-001-09 | AC-001-31 |
| ART-021 | TS-001-22 | AC-001-35 |
| ART-023 | TS-001-23 | — |
| ART-024 | TS-001-21 | AC-001-34, AC-001-38 |
| ART-073 | TS-001-20 | AC-001-39 |
| ART-080 | TS-001-04, TS-001-19 | AC-001-06 |
| ART-084 | TS-001-37 | — |
| AQ-03 | TS-001-21, TS-001-35 | AC-001-34 |
| TX-06 | TS-001-15 | AC-001-28 |

---

## 13. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `tenant-alpha` | Tenant `ACTIVE`, fuso `America/Sao_Paulo`, 9 categorias | Base de todos os testes |
| `tenant-beta` | Tenant `ACTIVE` com dados equivalentes | Testes de isolamento |
| `tenant-suspended` | Tenant `SUSPENDED` | RN-007 |
| `user-owner` | `ACTIVE`, OWNER em `tenant-alpha` | Fluxos administrativos |
| `user-member` | `ACTIVE`, MEMBER em `tenant-alpha` | Escopo de dados |
| `user-multi-tenant` | `ACTIVE`, OWNER em alpha e MEMBER em beta | Seleção de tenant |
| `user-pending` | `PENDING_ACTIVATION` | FA-02 |
| `user-locked` | `LOCKED` com `lockedUntil` no futuro | RN-453 |
| `user-suspended-membership` | `ACTIVE` com membership `SUSPENDED` | RN-459 |
| `token-verification-valid` | Emitido há 1 dia | Caminho feliz |
| `token-verification-expired` | Emitido há 8 dias | Expiração |
| `token-reset-consumed` | Já consumido | Uso único |
| `token-invitation-expired` | Emitido há 8 dias | RN-457 |
| `refresh-chain` | R1 → R2 → R3 encadeados | RN-005 |

**Regra:** fixtures são criadas por builders de teste, nunca por SQL bruto — o SQL contorna as invariantes de aplicação e produz estado que o sistema real jamais geraria.
