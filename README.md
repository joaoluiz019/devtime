# DevTime

Controle de horas multi-tenant para freelancers e pequenas equipes de desenvolvimento.

> **Documentação é a fonte de verdade** (ART-110). Código que diverge de [`docs/`](docs/) é bug do
> código ou bug da documentação — nunca uma exceção não documentada. Antes de escrever código, leia
> [`ai/project-constitution.md`](ai/project-constitution.md).

## Estado atual

Sprint **S1 — Fundação técnica (F0)** concluída. Nenhuma funcionalidade de negócio implementada: a
fila de features começa em [`specs/001-authentication`](specs/001-authentication/) e a ordem canônica
está em [`specs/implementation-order.md`](specs/implementation-order.md).

## Subir o ambiente

Pré-requisitos: Docker, e — para desenvolvimento fora de contêiner — JDK 21 e Node 22.

```bash
cp .env.example .env
```

Preencha em `.env` os dois valores obrigatórios (`DEVTIME_DB_PASSWORD` e `DEVTIME_JWT_SECRET`). O
segredo JWT precisa ter no mínimo 32 caracteres (TK-01 exige 256 bits) — a aplicação recusa iniciar
com um valor menor:

```bash
openssl rand -base64 48
```

```bash
docker compose -f infra/docker-compose.yml --env-file .env up --build
```

| Serviço | Endereço |
|---|---|
| Frontend | http://localhost:4200 |
| API | http://localhost:8080/api/v1 |
| OpenAPI (perfis `local` e `staging`) | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/actuator/health |

> O Swagger UI **exige autenticação**: ele não consta na allowlist pública de
> [`security.md`](docs/03-architecture/security.md) §7.1, e acrescentá-lo exigiria ADR.

## Desenvolvimento

### Backend

```bash
cd devtime-backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

| Comando | O que faz |
|---|---|
| `./mvnw test` | Suíte completa. Os testes de integração exigem Docker (Testcontainers) |
| `./mvnw test -DexcludedGroups=integration` | Apenas os testes que não precisam de Docker |
| `./mvnw verify` | Testes + cobertura mínima de 80% (ART-100) |
| `./mvnw spotless:apply` | Formata o código (Google Java Format, estilo AOSP) |

### Frontend

```bash
cd devtime-frontend && npm install && npm start
```

O dev-server usa [`proxy.conf.json`](devtime-frontend/proxy.conf.json) para encaminhar `/api` ao
backend em `localhost:8080`. A base da API permanece relativa de propósito: apontar direto para o
backend tornaria a requisição cross-origin e o cookie `SameSite=Strict` do refresh token deixaria de
ser enviado apenas em desenvolvimento.

| Comando | O que faz |
|---|---|
| `npm test` | Suíte Jest + Testing Library |
| `npm run test:coverage` | Cobertura |
| `npm run lint` | ESLint + Angular ESLint |
| `npm run format` | Prettier |
| `npm run build` | Build de produção |

## Estrutura

| Diretório | Conteúdo |
|---|---|
| [`ai/`](ai/) | Normas para agentes: constituição, diretrizes, regras por camada, DoD, checklist |
| [`docs/`](docs/) | Fonte de verdade: produto, domínio, arquitetura, API, UI, testes, backlog, ADRs |
| [`specs/`](specs/) | Especificação, tarefas, aceite e testes de cada feature |
| `devtime-backend/` | Spring Boot 3 · Java 21 · pacote `com.devtime` |
| `devtime-frontend/` | Angular standalone · Signals · PrimeNG |
| [`infra/`](infra/) | Docker Compose e scripts de ambiente |

## Decisões que valem conhecer antes de contribuir

| # | Decisão | Onde está |
|---|---|---|
| Multi-tenant desde o primeiro commit | Todo dado carrega `tenant_id`; o valor vem **só** do JWT | ART-001, ART-021 |
| Nenhum dado é destruído | Toda exclusão é lógica (`deleted_at`) | ART-004, ART-051 |
| Duração em minutos inteiros | Ponto flutuante para duração ou dinheiro bloqueia o PR | ART-034, P-04, P-05 |
| Identificadores UUIDv7 gerados na aplicação | O banco nunca gera ID | ART-010, ART-011 |
| Erros em RFC 7807 com código estável | `DEVTIME-XXXX` é o identificador programático | ART-072, ADR-017 |
| Recurso de outro tenant retorna `404` | Nunca `403` — `403` confirmaria a existência | ART-024 |

## Contribuindo

Commits seguem [Conventional Commits](ai/coding-guidelines.md#10-commits). Antes de abrir PR, percorra
[`ai/review-checklist.md`](ai/review-checklist.md) e
[`ai/definition-of-done.md`](ai/definition-of-done.md).
