# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) (RE-04).
Versionamento conforme [Semantic Versioning](https://semver.org/lang/pt-BR/); antes do lançamento a
versão permanece `0.x.y` (VR-04).

## [Não publicado]

### Adicionado

**Sprint S4 — Etiquetas, Tickets e Comentários (backend)** · `specs/implementation-order.md` §4

Escopo acordado: backend de `006-tags`, `007-tickets` e `014-comments`, com testes e documentação.
O frontend das três features não foi solicitado e permanece fora — ver "Pendências desta sprint".

Etiquetas (`006`)

- Migrations `V009` (`tags`) e `V017` (`ticket_tags`), com índice único parcial sobre
  `(tenant_id, name)` (INV-TAG-02) e índice parcial de órfãs sobre `usage_count = 0`.
- `TagNormalizer` reproduzindo integralmente a tabela normativa da §6.1 da spec (RN-506):
  minúsculas, bordas aparadas, espaços internos colapsados e hifenizados. **Acentos preservados** e
  caracteres especiais não filtrados — `refatoração` e `refatoracao` coexistem por decisão (CX-02).
- Unicidade verificada sobre o nome **normalizado** (RN-507), com o índice parcial como barreira
  para a corrida entre criações simultâneas.
- `TagLinkService` com substituição atômica do conjunto de etiquetas do ticket, limite de 10 sobre o
  conjunto resultante (RN-313) e `usageCount` ajustado por `UPDATE ... SET x = x + ?` na mesma
  transação do vínculo (INV-TAG-04).
- Exclusão removendo os vínculos em lote e informando as contagens desvinculadas (§9.3 de
  `users.md`); sugestões de limpeza que **apenas sugerem** (RN-508).

Tickets (`007`)

- Migration `V014` com índice único `(contract_id, number)` (INV-TCK-01), os `CHECK` de INV-TCK-05,
  de INV-TCK-04 (`DONE` exige `completedAt`) e do motivo de impedimento.
- `TicketNumberGenerator` com lock consultivo de transação por contrato
  (`pg_advisory_xact_lock`): a serialização é obtida sem travar linhas, o que importa quando o
  contrato ainda não tem nenhum ticket — cenário em que `SELECT ... FOR UPDATE` sobre `tickets` não
  travaria nada e deixaria a primeira dupla de criações em corrida (RN-302, CP-03).
- `TicketKeyBuilder` reproduzindo a tabela normativa de chaves da §6.2 e a decomposição inversa,
  usada pela busca por chave legível.
- `TicketStateMachine` com as 49 células da matriz de `state-machines.md` §4.7 e
  `availableTransitions` por estado **e** permissão (ME-06).
- RN-310 exata: `startedAt` apenas na 1ª entrada em `IN_PROGRESS`, `completedAt` limpo em **toda**
  saída de `DONE`. `ActiveTimerGuard`, `BlockReasonValidator`, `AssigneeValidator`,
  `ContractMoveGuard` e `TicketDeletionGuard` como pontos únicos de RN-311, §4.7, RN-304, RN-305 e
  RN-307.
- Movimentação de contrato preservando `number` e chave (RN-011, CP-06); exclusão restrita com
  mensagem que aponta o cancelamento como caminho (RN-307, RN-314).
- `TicketTotalsService.applyWorkLogDelta` por incremento, nunca por reagregação (RN-308, CP-12), e
  `reopenOnWorkLog` aplicando RN-312 sem reversão na exclusão do work log (CX-06).
- Quadro servido por **uma** consulta agrupada com limite de 50 cartões por coluna (CP-14) e linha
  do tempo paginada por cursor, unindo auditoria e comentários por inversão de dependência.

Comentários (`014`)

- Migration `V022` com `CHECK (length(btrim(body)) BETWEEN 1 AND 10000)`, FK autorreferente e os
  quatro índices da §13.4 da spec.
- Hierarquia de um nível normalizada **na escrita** (RN-814): responder a uma resposta vincula à
  raiz, mantendo a árvore plana por construção.
- `MentionExtractor` resolvendo menções em duas consultas em lote, independentemente da quantidade,
  filtrando por membros ativos (RN-813). Menção não resolvida permanece como texto, sem erro;
  padrão de e-mail não é menção.
- `CommentEditPolicy` com janela estritamente menor que 24h (CX-09), `canEdit`/`canDelete`
  calculados no servidor e a distinção de §6.3: `ADMIN`/`OWNER` **excluem, mas não editam** —
  `COMMENT_UPDATE_ANY` não existe no catálogo de permissões.
- `SystemCommentListener` fecha a dívida OB-06 de `007`: os três gatilhos de RN-815 geram
  comentário de sistema **dentro** da transação da transição, sem ciclo entre as features — `007`
  publica o evento e não conhece `014`.

Transversal

- 16 códigos de erro de domínio registrados em `ErrorCode` (`DEVTIME-2104`, `23xx`, `2604`, `27xx`).
- `AuditService` ganha leitura (`findByEntity`) e contexto adicional em `metadata`, exigido por §18
  de `specs/007-tickets` para o `blockReason` e o `workLogId` que disparou a reabertura.
- Interfaces públicas mínimas de `002-users` publicadas por esta sprint: `MembershipService`
  (RN-304, RN-813) e `UserService` (exibição e menções). Escopo deliberadamente estreito — o ciclo
  de vida de usuário e membership continua pertencendo a `002`.
- OpenAPI descrevendo as 15 rotas novas, com os códigos de erro por resposta.

**Sprint S3 — Categorias, Clientes e Contratos (backend)** · `specs/implementation-order.md` §4

Escopo acordado: backend de `005-categories`, `003-clients` e `004-contracts` no recorte S3 (CRUD e
prévia de períodos). O frontend das três features e as dependências `001-authentication` e
`002-users` permanecem fora — ver "Pendências desta sprint".

Categorias (`005`)

- Migration `V008`, entidade `Category` e catálogo das 9 categorias de sistema de `entities.md`
  §6.10, com índice único parcial sobre `(tenant_id, lower(name))` (RN-502, INV-CAT-01).
- `CategoryService` com CRUD, inativação, reordenação atômica e exclusão na ordem normativa da §6.1
  da spec; `SystemCategoryGuard` (RN-503) e `CategoryReplacementValidator` (`DEVTIME-2605`).
- `CategorySeedService` exposto por `seedDefaults()`, idempotente (RN-501, CX-14).
- `DefaultCategoryResolver` com a cadeia ticket → contrato → usuário → primeira ativa (RN-104), que
  pula origens inativas e é determinística.

Clientes (`003`)

- Migrations `V010` e `V011`, entidades `Client` e `Contact` com o VO `Address` embutido.
- `DocumentValidator` de CPF e CNPJ por dígitos verificadores, rejeitando sequências repetidas
  (RN-402, CX-04), e `DocumentNormalizer` removendo máscara antes de validar e comparar (CX-03).
- CRUD com unicidade de nome e documento por tenant (RN-403, RN-404), busca sem acento e sem caixa,
  paginação, cor determinística derivada do nome, inativação com confirmação (RN-407) e exclusão
  restrita por contratos ativos (RN-401).
- `ContactService` com `PrimaryContactPolicy` (RN-406) e limite de 20 contatos por cliente.

Contratos (`004`)

- Migrations `V012` e `V013`, incluindo a constraint `EXCLUDE USING gist` de INV-PER-02, o índice
  único parcial de período `OPEN` (INV-PER-07) e as constraints de coerência de tipo
  (INV-CTR-02/03/04).
- `PeriodGenerator` e `ProrationCalculator` reproduzindo a tabela normativa de geração e o exemplo
  de rateio de RN-217 (1.703 minutos), com aritmética inteira — sem ponto flutuante em nenhum passo.
- `ContractStateMachine` com a matriz completa de `state-machines.md` §4.5 e `availableTransitions`.
- CRUD com código sequencial `CT-0001` por tenant, prévia de períodos sem persistência, ativação
  gerando o 1º período `OPEN` na mesma transação (RN-209, INV-CTR-06), suspensão, retomada com
  geração dos períodos faltantes (CE-ME-09), encerramento e cancelamento com truncamento (RN-214),
  exclusão restrita a `DRAFT` (RN-205) e histórico de períodos.
- `ContractChangeGuards` aplicando RN-207 e RN-208.

Transversal

- `AuditService` gravando a trilha na mesma transação da alteração (RN-006), com ator de sistema
  para a geração de períodos.
- 44 códigos de erro de domínio registrados em `ErrorCode` (`DEVTIME-22xx`, `24xx` e `26xx`).
- OpenAPI descrevendo as 21 rotas da sprint, com códigos de erro por resposta.

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

### Corrigido — Sprint S4

- **Escopo de dados de `MEMBER` sobre clientes.** A S3 deixou o escopo fechado por padrão porque
  `tickets` e `work_logs` não existiam ("as subconsultas `EXISTS` entram com `007`/`008`"). Com
  `tickets`, a metade que depende deles foi implementada: um contrato é visível ao membro quando ele
  é relator ou responsável de algum ticket nele, e um cliente é visível quando possui contrato
  visível (§9 de `permissions.md`). O grafo de features permanece acíclico por inversão — `client`
  declara `MemberScopeSource`, `contract` declara `MemberContractLinkSource`, e cada uma é
  implementada pela feature que possui a informação.
- **Ticket ilegível para `MEMBER`.** Como consequência do item acima, um `MEMBER` não conseguia
  abrir ticket algum: a resposta embute contrato e cliente, e o escopo fechado devolvia `404`.
  `ClientService.getRefById` e `ContractService.getRefById` passam a servir a identificação embutida
  sem aplicar o escopo de carteira — comportamento previsto e aceito em OB-04 de `specs/007`
  (`MEMBER` enxerga todos os tickets do tenant). Listar e detalhar clientes segue restrito.
- **Acoplamento entre features na fronteira do contrato.** `ContractResponse` expõe o enum
  `ContractStatus`, e consumi-lo em `007` violaria AR-02. `ContractRefResponse` devolve a situação
  como texto e `acceptsWorkLogs` já decidido por quem é dono de RN-306. Pelo mesmo motivo,
  `TicketStatusChangedEvent` carrega o nome da situação, não o enum.
- **Parâmetro opcional de texto em JPQL.** `(:param IS NULL OR ...)` com um `String` nulo faz
  Hibernate 6 enviar o parâmetro sem tipo, e o PostgreSQL recusa a comparação (`operator does not
  exist: character varying ~~ bytea`). As consultas afetadas passam a usar `cast(... as String)` e a
  receber o padrão de `LIKE` já montado.

### Conflitos de documentação — Sprint S4

Encontrados **antes** da implementação e resolvidos pela hierarquia de IA-11 (`project-constitution`
> `02-domain/` > `03-architecture/` > `04-api/` > `specs/`), conforme CE-G-02 manda: seguir a
hierarquia **e** reportar. Nenhum deles exigiu inventar regra de negócio.

| # | Conflito | Documentos | Resolução |
|:--:|---|---|---|
| C-01 | **Chave do ticket ao mover de contrato.** `04-api/tickets.md` §8.3 determinava um **novo** `number` e uma **nova** `key` no contrato de destino, com `previousKey` e aviso de quebra de referência. `entities.md` §6.12 marca ambos como imutáveis (🔒), RN-011 proíbe alterá-los, e `specs/007` §6.2, CX-04, CP-06, OB-01 e CA-12 exigem que permaneçam | `02-domain/entities.md`, `business-rules.md` × `04-api/tickets.md` | 02-domain prevalece: a chave **não** muda. `04-api/tickets.md` §8.3 foi corrigida neste PR (ART-111) |
| C-02 | **`key` persistida ou derivada.** `entities.md` §6.12 marca 📐 (campo derivado, não persistido) e `database.md` §7.7 não declara a coluna; `specs/007` §13.2 e §13.4 a descrevem persistida, com índice único `uq_tickets_tenant_key` | `02-domain/`, `03-architecture/` × `specs/007` | Derivada. `V014` não cria a coluna; a busca por chave decompõe o valor e resolve por `uq_tickets_contract_number` |
| C-03 | **`DEVTIME-2706` com dois significados.** `specs/014` §12 o atribui a `parentCommentId` inválido (422); `04-api/tickets.md` §10.2 e §13 o atribuem à janela de edição expirada (409) | `04-api/tickets.md` × `specs/014` | 04-api prevalece. Origem inválida responde `DEVTIME-2002`/404 (ART-024) e a decisão está documentada em `tickets.md` §10.2 |
| C-04 | **Numeração das migrations.** `specs/006` pede `V017`/`V018`, `specs/007` pede `V019`–`V021` e `specs/014` pede `V037`; `database.md` §8.1 aloca `V009`, `V014`, `V017` e `V022` | `03-architecture/database.md` × `specs/` | `database.md` prevalece, como já ocorrera em `V008` na S3 |
| C-05 | **Contagem de células da matriz de ticket.** `specs/007/tasks.md` T-007-08 fala em "22 válidas e 27 proibidas"; a matriz de `state-machines.md` §4.7 tem **19** válidas (a soma da spec não fecha 49 sob nenhuma contagem) | `02-domain/state-machines.md` × `specs/007` | A matriz prevalece. O teste cobre as 49 células e afirma 19 válidas |
| C-06 | **FK de `assignee_id`.** `specs/007` §17.3 aponta para `memberships.user_id`, que não é único isoladamente — um usuário participa de vários tenants — e portanto não pode ser alvo de FK; `database.md` §7.7 aponta para `users` | `03-architecture/database.md` × `specs/007` | FK para `users`; a validação de membership `ACTIVE` (RN-304) é da aplicação |
| C-07 | **`DEVTIME-9002` com dois significados.** `ADR-017` e `ADR-045` o atribuem a *rate limit* (`429`) e o código já o implementa assim; `state-machines.md` §7 e `specs/006`/`007` §17.3 o atribuem a estado inconsistente / violação de `CHECK` | `02-domain/state-machines.md` × `docs/adr/` + código | **Não resolvido nesta sprint** e sem impacto no escopo entregue: as violações de `CHECK` continuam caindo em `DEVTIME-9001` pelo comportamento de fechar-por-padrão do `ConstraintViolationMapper`. Exige decisão do Tech Lead: renumerar o código de *rate limit* (mudança de contrato público) ou emendar `state-machines.md` §7 |

### Verificado — Sprint S4

- Backend: **545 testes verdes** (unitários + integração com Testcontainers em PostgreSQL 16).
  Cobertura global de linhas 89,9% (gate 80%) e 92,8% em services, validators, policies e guards
  (meta ART-100: 90%).
- **Duas suítes escritas antes do código** (SQ-02): a tabela normativa de normalização de etiquetas
  (§6.1 de `specs/006`) e a matriz 7×7 do ticket, célula a célula, com aceitação **e** rejeição. A
  matriz esperada é transcrita do documento, não importada da implementação — uma suíte que
  consultasse a própria máquina provaria apenas que ela é consistente consigo mesma.
- **Atomicidade da sequência de `number` comprovada**: 100 criações simultâneas no mesmo contrato
  produzem 100 números distintos e consecutivos, sem lacuna (CA-02 de `specs/007`).
- Janela de 24h dos comentários verificada com `Clock` controlado em 0h, 1h, 23h, 23h59, 24h, 25h e
  30 dias — 24h exatas já está fora (CX-09).
- Isolamento entre tenants verde nos endpoints novos, por id **e** por chave legível; recurso de
  outro tenant responde `404` (ART-024).
- Migrations `V009`, `V014`, `V017` e `V022` aplicam do zero; `ddl-auto=validate` aprovou o
  mapeamento. Verificado por consulta ao catálogo que `tickets` **não** possui coluna `key` e que o
  índice `uq_tickets_contract_number` é parcial.

### Pendências — Sprint S4

Reportadas antes do início e confirmadas na entrega.

- **Frontend das três features** (T-006-10 a 14, T-007-23 a 32, T-014-10 a 15): não solicitado nesta
  sprint. SQ-09 exige que uma feature entregue backend **e** frontend; enquanto o frontend não
  existir, `006`, `007` e `014` permanecem `BACKEND_DONE`, não `DONE`.
- **Dependências não implementadas.** `001-authentication` e `002-users` continuam ausentes. Esta
  sprint publicou apenas as interfaces mínimas que RN-304 e RN-813 exigem (`MembershipService`,
  `UserService`); cadastro, convite, papel e ciclo de vida seguem pertencendo a `002`.
- **`ActiveTimerGuard` não bloqueia nada** enquanto `009-timer` não existir: sem a tabela `timers`,
  nenhum cronômetro pode existir. RN-311 tem ponto único de aplicação e testes que já passam por ele.
- **`TicketWorkLogGate` deriva a existência de horas de `spentMinutes`** enquanto `008` não publica
  `WorkLogService`. A derivação é exata (RN-115 exige `netMinutes > 0`), e a substituição pela
  contagem real é de uma linha.
- **Work logs na linha do tempo e escopo de horas de `MEMBER`** (IMP-02) entram com `008`, junto da
  fonte de eventos correspondente.
- **`work_log_tags` e `TagLinkService.linkToWorkLog`** entram com `008` (CE-O-03); `V017` cria apenas
  `ticket_tags`.
- **`DenormalizationReconcileJob`** continua inexistente — é compartilhado com `003`, `004` e `011` e
  também não foi construído na S3. Os pontos de reconciliação de `usageCount` e dos totais do ticket
  ficam para quando o job existir, e não foram deixados como código sem chamador (CG-09).
- **`TagCleanupSuggestionJob`** não implementado: registrar o instante exato em que `usageCount`
  chegou a zero exigiria um campo que `entities.md` §6.11 não define. As sugestões de RN-508 são
  calculadas ao vivo sobre `updatedAt`, que é o que o índice documentado em §13.4 sustenta.
- **`TagService.getAllForReport`** e **`CommentService.existsForComment`** só ganham consumidor em
  `012` e `015`; o segundo foi publicado, o primeiro não, para não deixar consulta sem chamador.
- **Busca sem acento** em tickets não é oferecida: exigiria a extensão `unaccent`, que não consta das
  instaladas em `V001`. A busca é sem diferenciar caixa, sobre título e descrição.
- **`DEVTIME-9002`** permanece com dois significados na documentação (C-07 abaixo).

### Verificado — Sprint S3

- Backend: **319 testes verdes** (unitários + integração com Testcontainers em PostgreSQL 16).
  Cobertura global de 88,6% (gate 80%) e 91,6% em services e validators (meta 90%).
- Suítes temporais de `004` escritas **antes** do gerador (regra SQ-02): os 5 cenários normativos de
  geração, 1.120 combinações de `startDate` × `billingDay` verificando contiguidade (INV-PER-03) e a
  matriz completa de transições, célula a célula, incluindo as proibidas.
- Constraints estruturais provadas por `INSERT` direto, contornando a aplicação: sobreposição de
  períodos, segundo período `OPEN` e sequência duplicada são rejeitados pelo banco.
- Isolamento entre tenants verificado nas três features; recurso de outro tenant responde `404`.
- Migrations `V008`, `V010`–`V013` aplicam do zero; `ddl-auto=validate` aprovou o mapeamento.

### Pendências — Sprint S3

Reportadas antes do início e confirmadas na entrega.

- **Dependências não implementadas.** `001-authentication` e `002-users` não existem: não há endpoint
  de login, e o gatilho de criação de tenant que deve chamar `CategoryService.seedDefaults()`
  (RN-501) pertence a `002`. A sprint foi executada sobre a fundação F0 por decisão explícita.
- **Frontend das três features** (T-005-12 a 17, T-003-15 a 22, T-004-32 a 46) fora do escopo
  acordado.
- **Jobs de S4** de `004`: `GeneratePeriodsJob` (RN-213), `OpenScheduledPeriodsJob`,
  `AutoEndContractsJob` e `ContractEndingReminderJob`.
- **Guarda de cronômetro ativo** em `suspend` e `end` (`DEVTIME-2212`): depende de `009-timer`.
- **Migração de work logs na exclusão de categoria** (RN-505) e estatística de uso: dependem de
  `008-worklogs`.
- **Escopo de dados de `MEMBER`** sobre clientes: aplicado na consulta, porém fechado por padrão —
  a definição de "cliente vinculado" depende de `work_logs` e `tickets`. As subconsultas `EXISTS`
  entram com `007`/`008`.

### Corrigido — Sprint S1

Defeitos encontrados pelos próprios gates da sprint de fundação, antes de qualquer feature depender
deles.

- **Isolamento entre tenants em `findById`.** O `@Filter` de Hibernate não é aplicado a
  `EntityManager.find()`, que é o que o `findById` padrão de Spring Data usa — então
  `repository.findById()` retornava registros de outro tenant, violando ART-022 e ART-024.
  `SoftDeleteRepository` passa a sobrescrever `findById` com JPQL, onde o filtro volta a valer. Regra
  ArchUnit adicional proíbe `getReferenceById`, que carrega um proxy e não pode ser filtrado.
- **Chave de assinatura do JWT.** `JwtService` tentava decodificar Base64 antes de recorrer a UTF-8.
  Decodificadores Base64 descartam caracteres inválidos em silêncio, então um segredo forte contendo
  `-` ou `!` produzia uma chave muito mais curta que o esperado — fraqueza criptográfica invisível na
  configuração. Passa a usar um único formato: bytes UTF-8.
- **Máscara de log.** `SensitiveDataMasker` mascarava apenas o esquema `Bearer` do cabeçalho
  `Authorization` e deixava o token exposto na sequência, violando ART-084.
- **Formato das respostas de erro.** `spring.mvc.problemdetails.enabled=true` fazia o Spring Boot
  registrar seu próprio handler, que respondia antes do `GlobalExceptionHandler` e produzia corpo RFC
  7807 sem as extensões obrigatórias `code`, `traceId` e `errors[]` (ART-072). Desabilitado, e o
  `GlobalExceptionHandler` passa a declarar precedência máxima.
- **Tipos `CHAR` no mapeamento JPA.** `tenants.currency`, `memberships.cost_currency` e
  `address_country` são `CHAR` no schema (database.md §4.2, ART-041), mas o padrão de Hibernate para
  `String` é `varchar` — `ddl-auto=validate` recusava a inicialização. Declarado
  `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Configuração de log que escondia falhas de inicialização.** Os appenders estavam declarados dentro
  de blocos `<springProfile>` que listavam perfis; sem perfil ativo nenhum bloco casava e o contexto
  ficava sem appender, descartando a mensagem de por que a aplicação não subia. O bloco padrão passa a
  usar a negação dos demais.
- **Build não reprodutível entre plataformas.** Arquivos em CRLF no Windows faziam o mesmo
  `spotless:check` falhar em Linux (imagem Docker e pipeline). Adicionado `.gitattributes` com
  `eol=lf` e fixado `<lineEndings>UNIX</lineEndings>` no Spotless.
- **Imagem do frontend servia a página padrão do nginx.** Com `localize: true`, o Angular emite a
  saída em subdiretório por locale (`browser/pt-BR/`); o `COPY` apontava um nível acima.
- **Cabeçalhos de segurança perdidos em rotas estáticas.** `add_header` em bloco `location` substitui
  os herdados do `server` em vez de somar, então `/index.html` e os assets respondiam sem
  `X-Content-Type-Options`, `X-Frame-Options` e `Referrer-Policy`. Extraídos para
  `security-headers.conf`, incluído em cada bloco.
- **Mensagens de validação de configuração.** O texto padrão do Bean Validation não indicava de onde o
  valor deveria vir; `DEVTIME_JWT_SECRET` agora explica o que definir e como gerar (ER-04).

### Verificado — Sprint S1

- Backend: 124 testes verdes (88 unitários + 36 de integração com Testcontainers), Spotless e gate de
  cobertura JaCoCo de 80% atendidos.
- **Regra F0-01 cumprida:** suíte de isolamento entre dois tenants verde.
- Migrations `V001`–`V007` aplicadas do zero em PostgreSQL 16 limpo; `ddl-auto=validate` aprovou o
  mapeamento; `audit_logs` criada particionada com 12 partições; extensões `pgcrypto`, `btree_gist` e
  `pg_trgm` instaladas.
- Frontend: 36 testes verdes, lint limpo, build de produção em 165,72 kB gzip (limite FR-167: 500 kB).
- Ambiente completo no Docker Compose: PostgreSQL, backend e frontend saudáveis; endpoint protegido
  responde `401` (ART-085), health check público, todos os cabeçalhos de §8.2 presentes, roteamento da
  SPA resolvendo `/dashboard` no index (FR-089).

### Pendências — Sprint S1

- Pipeline CI com os gates de `architecture.md` §11 fora do escopo acordado para a sprint.
- Angular 21.2.19 em vez da última versão estável (22.x) exigida por ART-090: o Angular 22 requer
  Node ≥ 22.22.3 e o ambiente tem 22.19.0.
- `CA-09` de `database.md` (impossibilidade de `UPDATE` em `audit_logs`) não verificável: a topologia
  de papéis do banco não está especificada.
