# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) (RE-04).
Versionamento conforme [Semantic Versioning](https://semver.org/lang/pt-BR/); antes do lançamento a
versão permanece `0.x.y` (VR-04).

## [Não publicado]

### Adicionado

**Sprint S1 — Fundação técnica (F0)** · `specs/implementation-order.md` §3

Backend

- Projeto Spring Boot 3 com Java 21, pacote `com.devtime`, organizado por feature (ADR-027).
- `BaseEntity` e `TenantScopedEntity` com UUIDv7 gerado na aplicação, auditoria, exclusão lógica e
  concorrência otimista (ART-010, ART-050 a ART-052).
- `TenantContext`, `TenantContextFilter` e `TenantAwareInterceptor`: o `tenant_id` vem exclusivamente
  da claim `tid` do JWT e o filtro Hibernate é ativado em toda abertura de sessão (ART-021, ART-022).
- Anotação `@CrossTenant` como marcador de revisão para as exceções previstas em `backend.md` §7.4.
- Spring Security com JWT `HS256`, allowlist pública exaustiva, CORS por ambiente e os cabeçalhos de
  segurança de `security.md` §8.2.
- Enums `Role` e `Permission` com a matriz Papel × Permissão de `permissions.md` §7 e
  `DevTimePermissionEvaluator` para `@PreAuthorize`.
- Tratamento global de exceções em RFC 7807 com códigos `DEVTIME-XXXX`, mapeamento de constraint por
  nome e `traceId` em toda resposta (ADR-017).
- Log estruturado em JSON com máscara obrigatória de dados sensíveis (ART-084).
- `TenantClock`, `DateRange`, `DomainEventPublisher`, `PageResponse` e `PageRequestFactory`.
- OpenAPI 3.1 gerado a partir do código (ART-076).
- Migrations Flyway `V001`–`V007`: extensões, `tenants`, `users`, `memberships`, `refresh_tokens`,
  `audit_logs` particionada e `shedlock`.
- Entidades `Tenant`, `User`, `Membership`, `RefreshToken` e `AuditLog` com seus repositórios. Nenhum
  serviço nem endpoint de negócio.

Frontend

- Projeto Angular standalone com Signals, `OnPush` obrigatório, PrimeNG e PrimeFlex.
- Tokens de design `--dt-*` e tema claro/escuro conforme `design-system.md` §5 a §7 e §15.
- `AuthStore`, `TokenStorage` com access token apenas em memória e `AuthService` com fila única de
  refresh (FR-066, FR-068).
- Guards `authGuard`, `tenantSelectedGuard`, `guestGuard` e `permissionGuard`.
- Interceptors na ordem obrigatória de `frontend.md` §7.2: loading, auth, tenant, retry e error.
- Shell da aplicação (L2), layout de autenticação (L1), tela de login (P01), páginas de acesso negado
  e de recurso não encontrado.
- `DurationPipe` (`HH:MM`) e diretiva `dtHasPermission`.
- i18n com `@angular/localize` e mapa de mensagens por código `DEVTIME-XXXX`.

Infraestrutura

- Dockerfile multi-stage do backend e do frontend, `infra/docker-compose.yml` com PostgreSQL 16 e
  `.env.example` sem segredos.

Testes

- ArchUnit cobrindo `AR-01` a `AR-09` e as regras de persistência `BR-020` a `BR-035`.
- Suíte de isolamento entre dois tenants (critério de saída F0-01).
- Testes de `JwtService` (incluindo rejeição de `alg=none`), da matriz de permissões, da máscara de
  log, do contrato RFC 7807 e das migrations a partir de banco limpo.

Documentação

- `README.md` com o ambiente em 3 comandos (RE-03) e este `CHANGELOG.md` (RE-04).
- `ai/coding-guidelines.md` §5: árvore do repositório corrigida para `devtime-backend/` e
  `devtime-frontend/`, conforme ADR-022.

### Corrigido

- `JwtService` derivava a chave de assinatura tentando decodificar Base64 antes de recorrer a UTF-8.
  Decodificadores Base64 descartam caracteres inválidos em silêncio, então um segredo forte contendo
  `-` ou `!` produzia uma chave muito mais curta que o esperado — fraqueza criptográfica invisível na
  configuração. Passa a usar um único formato: bytes UTF-8.
- `SensitiveDataMasker` mascarava apenas o esquema `Bearer` do cabeçalho `Authorization` e deixava o
  token exposto na sequência, violando ART-084.

### Pendências desta sprint

- Suíte de integração (Testcontainers) **não executada**: o engine Linux do Docker não inicializou no
  ambiente de desenvolvimento. Os testes estão escritos; a execução é pré-requisito da regra F0-01.
- Pipeline CI com os gates de `architecture.md` §11 fora do escopo acordado para a sprint.
- Angular 21.2.19 em vez da última versão estável (22.x) exigida por ART-090: o Angular 22 requer
  Node ≥ 22.22.3 e o ambiente tem 22.19.0.
- `CA-09` de `database.md` (impossibilidade de `UPDATE` em `audit_logs`) não verificável: a topologia
  de papéis do banco não está especificada.
