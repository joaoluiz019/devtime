# 005 — Categories · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-005-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-503: rejeita exclusão de categoria de sistema`.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `DefaultCategoryResolver`, `SystemCategoryGuard`, `CategoryReorderPolicy`, `CategoryReplacementValidator`, `DefaultCategoryCatalog` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Seed, exclusão com migração, reordenação |
| API | Controller + serialização + permissões | `@WebMvcTest` | Os 5 endpoints |
| Isolamento | Tenancy nos 5 endpoints | Suíte dedicada | Todos |
| Frontend | Store, seletores, diálogos, reordenação | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Gestão da taxonomia e efeito no registro de horas | Playwright | Jornada completa |
| Performance | Migração em massa, resolução da cadeia, listagem | k6 + JMH | Metas da §20 |
| Segurança | Isolamento, `isSystem`, ids externos | JUnit + scripts | Vetores da §19 |
| Regressão | Suíte completa | CI | 100% verde |

**Nota sobre acoplamento com `008`:** os testes que envolvem `work_logs` (`TS-005-09`, `TS-005-10`, `TS-005-19`) dependem da tabela criada em `008-worklogs`. Enquanto `008` não existir, eles rodam contra uma tabela mínima criada por *fixture* de teste, e são **reexecutados** ao final de `008` como regressão (`TS-005-24`). Declarar essa dependência é obrigatório: um teste de migração que nunca tocou a tabela real não prova nada.

---

## 3. Testes unitários

### TS-005-01 — Catálogo de seed é fiel à documentação (RN-501)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `DefaultCategoryCatalog` reproduz exatamente a tabela §6.10 de `entities.md` |
| **Pré-condição** | Nenhuma |
| **Passos** | 1. Ler o catálogo. 2. Comparar nome, cor, ícone e `billableByDefault` das 9 entradas com os valores normativos |
| **Resultado esperado** | 9 entradas; igualdade exata nos 4 atributos de cada uma; "Interno (não faturável)" é a única com `billableByDefault = false`; ordem estável, definindo `sortOrder` de 0 a 8 |

### TS-005-02 — Cadeia de resolução nas 4 origens (RN-104)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a precedência da §6.2 |
| **Pré-condição** | Categorias ativas distintas para ticket, contrato e usuário |
| **Passos** | Resolver com: (a) as 3 origens preenchidas; (b) sem a do ticket; (c) sem ticket e contrato; (d) nenhuma preenchida |
| **Resultado esperado** | (a) categoria do ticket; (b) do contrato; (c) do usuário; (d) a primeira ativa por `sortOrder`. Nenhum caso retorna vazio |

### TS-005-03 — Cadeia pula origens inativas e excluídas (CX-15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a origem inválida é **pulada**, não rejeitada |
| **Passos** | 1. Ticket aponta para categoria inativa, contrato para ativa. 2. Ticket aponta para categoria excluída. 3. Todas as três origens inativas |
| **Resultado esperado** | (1) e (2) retornam a do contrato; (3) retorna a primeira ativa. Nenhuma exceção é lançada em nenhum caso |

### TS-005-04 — Determinismo da resolução
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o desempate por `sortOrder` e depois `name` elimina ambiguidade |
| **Pré-condição** | Duas categorias ativas com o **mesmo** `sortOrder` |
| **Passos** | Resolver 1.000 vezes sem nenhuma origem definida, em ordens de carregamento variadas |
| **Resultado esperado** | Sempre a mesma categoria. Sem o desempate por `name`, o resultado dependeria da ordem de retorno do banco — que não é garantida |

### TS-005-05 — `SystemCategoryGuard` (RN-503)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a proteção da categoria de sistema |
| **Passos** | 1. Excluir `isSystem = true` sem vínculos. 2. Com vínculos. 3. Renomear. 4. Inativar. 5. Excluir `isSystem = false` |
| **Resultado esperado** | (1) e (2) `DEVTIME-2602` — a verificação de sistema precede a de vínculos (§6.1); (3) e (4) permitidos; (5) segue o fluxo normal |

### TS-005-06 — `CategoryReorderPolicy` (§8.4 `users.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar completude, unicidade e atomicidade |
| **Passos** | 1. Lista completa. 2. Lista faltando um id. 3. Lista com id duplicado. 4. Lista com id inexistente. 5. Lista com id de outro tenant. 6. Lista vazia |
| **Resultado esperado** | (1) aplica; (2) a (6) `422` e **nenhum** `sortOrder` alterado — validação integral antes de qualquer escrita |

### TS-005-07 — `CategoryReplacementValidator` (§8.3 `users.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar todas as rejeições de substituta |
| **Passos** | Substituta: (a) válida e ativa; (b) igual à excluída; (c) inativa; (d) inexistente; (e) excluída; (f) de outro tenant |
| **Resultado esperado** | (a) aceita; (b) a (f) `DEVTIME-2605`, sem migrar nenhum registro |

---

## 4. Testes de integração

### TS-005-08 — Seed transacional (RN-501, INV-CAT-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar atomicidade entre criação do tenant e seed |
| **Passos** | 1. Criar tenant e conferir as 9 categorias. 2. Injetar falha no `CategorySeedService` e criar outro tenant. 3. Verificar o banco. 4. Executar o seed novamente para um tenant já semeado |
| **Resultado esperado** | (1) 9 categorias com `isSystem = true` e `sortOrder` 0–8; (3) **rollback total** — nenhum tenant e nenhuma categoria órfã; (4) idempotente, nenhuma duplicata (protegido por INV-CAT-01) |

### TS-005-09 — Exclusão com migração (RN-505)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a reatribuição correta e a contagem informada |
| **Pré-condição** | Categoria com 1.000 work logs vinculados; substituta ativa |
| **Passos** | 1. Excluir sem substituta. 2. Excluir com substituta válida. 3. Conferir os work logs. 4. Inspecionar o SQL emitido |
| **Resultado esperado** | (1) `409 DEVTIME-2603` informando 1.000 registros, nada alterado; (2) `200` com `migratedWorkLogs = 1000`; (3) todos apontam para a substituta; (4) um único `UPDATE` em lote, **nenhum** `SELECT` carregando entidades |

### TS-005-10 — Inativação não afeta registros (RN-504)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a diferença entre inativar e excluir |
| **Pré-condição** | Categoria ativa com 40 work logs |
| **Passos** | 1. Inativar. 2. Consultar os 40 work logs. 3. Consultar o seletor de categorias ativas. 4. Editar a descrição de um work log sem tocar na categoria |
| **Resultado esperado** | (2) os 40 intactos, ainda vinculados; (3) a categoria ausente; (4) edição bem-sucedida, categoria preservada, **sem** `DEVTIME-2104` |

### TS-005-11 — Unicidade de nome (RN-502, INV-CAT-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o índice único parcial e seu comportamento com caixa, acento e excluídos |
| **Passos** | 1. Criar "Consultoria". 2. Criar "CONSULTORIA". 3. Criar "Consultória". 4. Excluir a primeira. 5. Recriar "Consultoria" |
| **Resultado esperado** | (2) `409 DEVTIME-2601`; (3) `201` — acentos diferenciam (CX-02); (5) `201` — o índice parcial ignora excluídos |

### TS-005-12 — Imutabilidade de `isSystem` (RN-011, INV-CAT-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que RN-503 não pode ser contornada |
| **Passos** | 1. `PUT` com `isSystem = true` em categoria criada manualmente. 2. `PUT` com `isSystem = false` em categoria de sistema. 3. Tentar excluir cada uma |
| **Resultado esperado** | O campo é ignorado nos dois casos; (3) a manual é excluída, a de sistema retorna `DEVTIME-2602`. Nenhum caminho permite alterar `isSystem` |

### TS-005-13 — Reordenação atômica
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a ordem final é consistente |
| **Pré-condição** | 10 categorias com `sortOrder` 0–9 |
| **Passos** | 1. Reordenar invertendo. 2. Conferir a listagem. 3. Reordenar com lista inválida. 4. Conferir novamente |
| **Resultado esperado** | (2) ordem exatamente invertida, `sortOrder` de 0 a 9 sem lacuna nem duplicata; (4) ordem inalterada após a falha |

### TS-005-14 — Estatística de uso sob demanda
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a agregação não ocorre por padrão |
| **Passos** | 1. `GET /categories` e inspecionar o SQL. 2. `GET /categories?includeUsage=true` e inspecionar |
| **Resultado esperado** | (1) nenhuma consulta a `work_logs`; campo `usage` ausente; (2) agregação executada, `workLogsCount` e `totalMinutes` corretos |

### TS-005-15 — `getAllForReport` inclui inativas e excluídas (OB-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento deliberadamente distinto de RN-003 |
| **Pré-condição** | Uma categoria ativa, uma inativa e uma excluída, todas com work logs |
| **Passos** | 1. Chamar `getAllForReport`. 2. Chamar a listagem padrão |
| **Resultado esperado** | (1) as três presentes; (2) apenas a ativa e a inativa. Nenhuma linha de relatório fica sem classificação |

---

## 5. Testes de API

### TS-005-16 — Contrato dos 5 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `Location` no `201`; a exclusão retorna `200` **com corpo** (`migratedWorkLogs`, `migratedTo`), nunca `204`; erros em RFC 7807 com `code`; OpenAPI bate com o real |

### TS-005-17 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel (`OWNER`, `ADMIN`, `MANAGER`, `MEMBER`, `VIEWER`), executar |
| **Resultado esperado** | Os 5 papéis listam (`CATEGORY_VIEW`); apenas `OWNER`, `ADMIN` e `MANAGER` gerenciam; `MEMBER` e `VIEWER` recebem `403 DEVTIME-1101` com `requiredPermission = CATEGORY_MANAGE` |

### TS-005-18 — `active` não muda por `PUT`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a separação entre atualização e inativação |
| **Passos** | `PUT /categories/{id}` com `active = false` no corpo |
| **Resultado esperado** | O campo é ignorado; `active` permanece; a inativação só ocorre pelo endpoint dedicado |

---

## 6. Testes de frontend

### TS-005-19 — `dt-category-picker` filtra inativas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-09 |
| **Passos** | Renderizar com 9 ativas e 3 inativas; inativar uma pela store |
| **Resultado esperado** | Somente as 9 aparecem; após a inativação a lista cai para 8 sem recarregar a página |

### TS-005-20 — `CategoryStore` em escopo `root`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o carregamento único e a invalidação após escrita |
| **Passos** | 1. Navegar por P30, P23 e P19. 2. Criar uma categoria. 3. Navegar novamente |
| **Resultado esperado** | (1) uma única requisição `GET /categories` em toda a sessão; (3) uma nova requisição após a escrita, refletindo a categoria criada |

### TS-005-21 — Reordenação acessível por teclado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 de acessibilidade |
| **Passos** | Reordenar usando apenas teclado; verificar anúncios de leitor de tela |
| **Resultado esperado** | Reordenação completa sem mouse; posição anunciada a cada movimento; zero violações do axe-core em P30 |

### TS-005-22 — Diálogo de exclusão com substituta
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a orientação ao usuário |
| **Passos** | 1. Excluir categoria sem vínculos. 2. Com vínculos. 3. Categoria de sistema |
| **Resultado esperado** | (1) confirmação simples; (2) exibe a contagem e exige substituta, listando apenas categorias **ativas e distintas**; (3) mensagem orientando a inativar, sem oferecer exclusão |

---

## 7. Testes E2E

### TS-005-23 — Taxonomia e efeito no registro de horas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o ciclo completo do ponto de vista do usuário |
| **Passos** | 1. Abrir P30 em tenant novo e conferir as 9 categorias. 2. Criar "Consultoria". 3. Registrar horas com ela em P23. 4. Inativá-la. 5. Conferir que o registro anterior permanece. 6. Excluí-la migrando para outra. 7. Conferir o registro reclassificado |
| **Resultado esperado** | Cada etapa reflete o estado correto na UI; o registro do passo (3) sobrevive a (4) e é reclassificado em (6); nenhuma tela exibe dado desatualizado |

---

## 8. Testes de performance

### TS-005-24 — Migração em massa (CX-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta da §20 |
| **Pré-condição** | Categoria com 100.000 work logs |
| **Passos** | Executar a exclusão com substituta, medindo duração e memória |
| **Resultado esperado** | Conclusão em menos de 5 s; consumo de memória constante (sem carregar entidades); leituras concorrentes não bloqueadas durante o `UPDATE` |

### TS-005-25 — Resolução da cadeia no caminho quente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a §6.2 não pesa na criação de work log |
| **Passos** | 10.000 resoluções consecutivas, medindo p95 |
| **Resultado esperado** | p95 < 10 ms; a lista de categorias é resolvida de cache por requisição, sem uma consulta por origem da cadeia |

### TS-005-26 — Listagem sem e com estatística
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o custo de `includeUsage` |
| **Pré-condição** | Tenant com 500.000 work logs |
| **Passos** | Medir p95 de `GET /categories` e de `GET /categories?includeUsage=true` |
| **Resultado esperado** | Sem `includeUsage`: p95 < 100 ms; com: p95 < 400 ms. A diferença justifica o parâmetro ser desligado por padrão |

---

## 9. Testes de segurança

### TS-005-27 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 |
| **Passos** | Para cada um dos 5 endpoints, acessar recurso do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em todos, nunca `403`; nenhum vazamento por contagem, mensagem ou tempo de resposta |

### TS-005-28 — `tenantId` da requisição é ignorado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-001 |
| **Passos** | Enviar `tenantId` de outro tenant no corpo e em header customizado |
| **Resultado esperado** | Valor ignorado; a categoria é criada no tenant do token |

### TS-005-29 — Ids externos em operações compostas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-02 e SG-03 |
| **Passos** | 1. Reordenar incluindo id do tenant B. 2. Excluir informando substituta do tenant B |
| **Resultado esperado** | Ambas `422`; nenhuma escrita em nenhum dos dois tenants; a validação ocorre antes de qualquer alteração |

### TS-005-30 — Ausência de dado sensível em log
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a §28 |
| **Passos** | Executar o fluxo completo capturando os logs |
| **Resultado esperado** | Exclusão com migração registrada em `WARN` com `migratedWorkLogs`; nenhuma ordem completa de reordenação logada; nenhuma stack trace ou nome de tabela em resposta de erro |

---

## 10. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-005-31 | Fidelidade do catálogo (`TS-005-01`) | Toda alteração em `DefaultCategoryCatalog` ou na tabela §6.10 de `entities.md` |
| TS-005-32 | Atomicidade do seed (`TS-005-08`) | Toda alteração no fluxo de criação de tenant, em `002` ou aqui |
| TS-005-33 | Migração em lote (`TS-005-09`, `TS-005-24`) | **Reexecutado ao final de `008`**, contra a tabela `work_logs` real |
| TS-005-34 | Cadeia de resolução (`TS-005-02`, `TS-005-03`) | Toda alteração em `008`, `009` ou nos campos `defaultCategoryId` de `004`/`007` |
| TS-005-35 | Isolamento (`TS-005-27`) | Todo endpoint novo |

**Política:** `TS-005-33` é o único teste desta feature que **não pode** ser considerado concluído antes de `008-worklogs`. Marcá-lo como verde contra uma tabela de fixture seria declarar provado algo que não foi exercitado.

---

## 11. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-501 | TS-005-01, TS-005-08 | AC-005-01, AC-005-33, AC-005-43 |
| RN-502 | TS-005-11 | AC-005-16, AC-005-26, AC-005-27, AC-005-28, AC-005-40 |
| RN-503 | TS-005-05, TS-005-12 | AC-005-04, AC-005-17 |
| RN-504 | TS-005-10 | AC-005-05, AC-005-31 |
| RN-505 | TS-005-07, TS-005-09, TS-005-24 | AC-005-08, AC-005-18, AC-005-19, AC-005-20, AC-005-30 |
| RN-104 | TS-005-02, TS-005-03, TS-005-04, TS-005-25 | AC-005-11 a AC-005-14, AC-005-24, AC-005-32 |
| RN-112 | TS-005-10 | AC-005-15 |
| RN-003 | TS-005-11 | AC-005-07, AC-005-28 |
| RN-004 | TS-005-16 | AC-005-03, AC-005-22 |
| RN-011 | TS-005-12 | AC-005-38 |
| RN-012 | TS-005-16 | — |
| RN-001 | TS-005-28 | AC-005-39 |
| RN-002 | TS-005-27 | AC-005-35 |
| RN-006 | TS-005-08, TS-005-09, TS-005-13 | AC-005-01 a AC-005-09 |
| INV-CAT-01 | TS-005-11 | AC-005-26, AC-005-28, AC-005-40 |
| INV-CAT-02 | TS-005-08 | AC-005-01, AC-005-14, AC-005-43 |
| INV-CAT-03 | TS-005-05 | AC-005-17 |
| INV-CAT-04 | TS-005-09 | AC-005-08, AC-005-42 |
| INV-CAT-05 | TS-005-12 | AC-005-38 |
| §8.1 users.md | TS-005-14, TS-005-26 | AC-005-10 |
| §8.3 users.md | TS-005-07 | AC-005-19, AC-005-20, AC-005-37 |
| §8.4 users.md | TS-005-06, TS-005-13 | AC-005-09, AC-005-23, AC-005-36, AC-005-41 |
| §7 permissions | TS-005-17 | AC-005-25 |
| SG-02 / SG-03 | TS-005-29 | AC-005-36, AC-005-37 |
| OB-04 | TS-005-15 | AC-005-34 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 12. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `category-seed-expected.csv` | As 9 categorias normativas com nome, cor, ícone e `billableByDefault` | `TS-005-01` — oráculo do catálogo |
| `category-resolution-chain.csv` | Combinações das 4 origens × estado (ativa, inativa, excluída, ausente) | `TS-005-02`, `TS-005-03` |
| `fixture-tenant-fresh` | Tenant recém-criado, apenas com o seed | Base da maioria dos testes |
| `fixture-category-with-1k-logs` | Categoria não sistêmica com 1.000 work logs | `TS-005-09` |
| `fixture-category-with-100k-logs` | Categoria com 100.000 work logs | `TS-005-24` |
| `fixture-category-inactive` | Categoria inativa com 40 work logs | `TS-005-10` |
| `fixture-category-deleted` | Categoria excluída com work logs históricos | `TS-005-15` |
| `fixture-tenant-b` | Segundo tenant com taxonomia espelhada | `TS-005-27`, `TS-005-28`, `TS-005-29` |
| `fixture-same-sort-order` | Duas categorias ativas com `sortOrder` idêntico | `TS-005-04` |

**Regra de fixture:** `fixture-category-with-100k-logs` é gerada por `COPY` em massa, não por inserções individuais — construí-la registro a registro levaria mais tempo que o próprio teste.

---

## 13. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | O catálogo de seed é idêntico à tabela §6.10 de `entities.md`, verificado por teste |
| CC-02 | A atomicidade do seed é provada com injeção de falha |
| CC-03 | A cadeia de resolução é determinística em 1.000 repetições |
| CC-04 | A migração de 100.000 registros conclui em < 5 s com memória constante |
| CC-05 | A inspeção de SQL confirma `UPDATE` em lote, sem carregar entidades |
| CC-06 | Nenhuma consulta a `work_logs` ocorre na listagem sem `includeUsage` |
| CC-07 | Cobertura ≥ 90% em services e validators |
| CC-08 | Os 5 endpoints passam na suíte de isolamento com `404` |
| CC-09 | Zero violações do axe-core em P30, com reordenação por teclado |
| CC-10 | `TS-005-33` reexecutado e verde após a conclusão de `008-worklogs` |
