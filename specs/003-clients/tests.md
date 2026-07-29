# 003 — Clients · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-003-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-402: rejeita CPF com dígito verificador inválido`.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `DocumentValidator`, `DocumentNormalizer`, `ClientColorGenerator`, `PrimaryContactPolicy` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + Repository + PostgreSQL | Testcontainers | Fluxos das §7 e §8 |
| API | Controller + serialização + permissões | `@WebMvcTest` | Os 12 endpoints |
| Isolamento | Tenancy e escopo de `MEMBER` | Suíte dedicada + inspeção de SQL | Todos os endpoints |
| Frontend | Store, máscara, formulário, diálogos | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Cadastro, contatos, inativação, exclusão | Playwright | Jornada completa |
| Performance | Listagem com busca e resumo | k6 | Metas da §20 |
| Segurança | Isolamento, injeção, log | JUnit + scripts | Vetores da §19 |
| Regressão | Suíte completa | CI | 100% verde |

---

## 3. Testes unitários

### TS-003-01 — Validação de CPF (RN-402)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a correção do algoritmo de dígitos verificadores |
| **Pré-condição** | Conjunto de 100 CPFs válidos e 100 inválidos, gerados e verificados independentemente |
| **Passos** | 1. Validar cada CPF válido. 2. Validar cada inválido. 3. Validar as 10 sequências repetidas. 4. Validar com 10 e com 12 dígitos |
| **Resultado esperado** | 100% dos válidos aceitos; 100% dos inválidos rejeitados; sequências repetidas rejeitadas; tamanhos incorretos rejeitados. Todos com `DEVTIME-2402` |

### TS-003-02 — Validação de CNPJ (RN-402)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Idem para CNPJ |
| **Passos** | 100 válidos, 100 inválidos, 10 sequências repetidas, tamanhos 13 e 15 |
| **Resultado esperado** | Mesmo critério; CNPJ de filial com raiz repetida é válido |

### TS-003-03 — Normalização de documento (CX-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar remoção de máscara antes de validar e persistir |
| **Passos** | Normalizar `123.456.789-09`, `12.345.678/0001-95`, `  12345678909  `, `123-456` |
| **Resultado esperado** | Apenas dígitos em todos os casos; espaços removidos; nenhum caractere não numérico persiste |

### TS-003-04 — Cor determinística
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar estabilidade entre execuções |
| **Passos** | Gerar a cor para o mesmo nome 1.000 vezes, em execuções e JVMs distintas |
| **Resultado esperado** | Sempre o mesmo valor; formato `#RRGGBB`; nomes diferentes produzem boa distribuição na paleta |

### TS-003-05 — `PrimaryContactPolicy` (RN-406)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a desmarcação automática |
| **Passos** | 1. Marcar A. 2. Marcar B. 3. Marcar C. 4. Desmarcar C |
| **Resultado esperado** | Após cada passo, no máximo um primário; após (4), nenhum primário e nenhuma promoção automática |

---

## 4. Testes de integração

### TS-003-06 — Unicidade de nome (RN-404)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-CLI-02 e o comportamento com excluídos |
| **Passos** | 1. Criar "Acme Software". 2. Criar "ACME SOFTWARE". 3. Criar "Açme Software". 4. Excluir o primeiro. 5. Recriar "Acme Software" |
| **Resultado esperado** | (2) `409 DEVTIME-2404`; (3) `201` — acentos diferenciam; (5) `201` — o índice parcial ignora excluídos |

### TS-003-07 — Unicidade de documento (RN-403)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-CLI-01 com índice parcial |
| **Passos** | 1. Criar com documento X. 2. Criar com X mascarado. 3. Criar dois clientes sem documento. 4. Excluir o primeiro e recriar com X |
| **Resultado esperado** | (2) `409 DEVTIME-2403`; (3) ambos aceitos — a unicidade não se aplica a nulo; (4) `201` |

### TS-003-08 — Exclusão restrita (RN-401)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-CLI-03 para cada estado de contrato |
| **Passos** | Tentar excluir cliente com contrato em `DRAFT`, `ACTIVE`, `SUSPENDED`, `ENDED` e `CANCELLED` |
| **Resultado esperado** | `ACTIVE` e `SUSPENDED` → `409 DEVTIME-2401` com a lista de contratos; os demais → `204` |

### TS-003-09 — Inativação com contratos (RN-407)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nada é inativado em cascata |
| **Pré-condição** | Cliente com 10 contratos `ACTIVE`, cada um com período aberto |
| **Passos** | 1. Inativar sem confirmação. 2. Inativar com confirmação. 3. Verificar os contratos e os períodos |
| **Resultado esperado** | (1) `409` com os 10 listados; (2) `200`; (3) todos permanecem `ACTIVE` e os períodos continuam sendo gerados |

### TS-003-10 — Soft delete em cascata dos contatos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-003 e a cascata lógica |
| **Passos** | 1. Excluir cliente com 3 contatos. 2. Consultar contatos. 3. Consultar diretamente no banco |
| **Resultado esperado** | Contatos invisíveis nas consultas; presentes fisicamente com `deleted_at` preenchido; nenhuma remoção física |

### TS-003-11 — `activeContractsCount` desnormalizado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a atualização por evento e a convergência do job |
| **Passos** | 1. Ativar 3 contratos. 2. Verificar a contagem. 3. Encerrar 1. 4. Corromper o valor manualmente. 5. Executar o `DenormalizationReconcileJob` |
| **Resultado esperado** | (2) 3; (3) 2; (5) valor restaurado por agregação real; execução repetida do job produz o mesmo resultado |

### TS-003-12 — Resumo consolidado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a agregação e a paginação |
| **Pré-condição** | Cliente com 3 contratos, cada um com período aberto e work logs |
| **Passos** | Consultar o resumo como OWNER e como MEMBER com vínculo |
| **Resultado esperado** | OWNER recebe durações e valores; MEMBER recebe apenas durações; contratos paginados; totais coerentes com a soma dos períodos |

---

## 5. Testes de API

### TS-003-13 — Contrato dos 12 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar aderência a `docs/04-api/clients.md` |
| **Passos** | Executar todos os endpoints e comparar campos, tipos e status |
| **Resultado esperado** | Coincidência total; erros em RFC 7807 com `code` e `traceId`; `Location` presente no `201` |

### TS-003-14 — Listagem retorna projeção
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RP-03 |
| **Passos** | Inspecionar a consulta gerada e o corpo da resposta da listagem |
| **Resultado esperado** | Apenas as colunas de `ClientSummaryProjection` são selecionadas; a entidade completa não é carregada |

### TS-003-15 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CA-02 de `permissions.md` |
| **Passos** | Executar cada endpoint com OWNER, ADMIN, MANAGER, MEMBER e VIEWER |
| **Resultado esperado** | Concessão ou `403 DEVTIME-1101` exatamente conforme a matriz; `requiredPermission` presente na negação |

### TS-003-16 — `status` não alterável por `PATCH`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ME-05 |
| **Passos** | Enviar `PATCH /clients/{id}` com um campo `status` |
| **Resultado esperado** | O campo é ignorado; o status só muda por `/deactivate` e `/reactivate` |

---

## 6. Testes de isolamento e escopo

### TS-003-17 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AQ-03 |
| **Passos** | Autenticado no tenant A, acessar por id direto todos os clientes e contatos do tenant B, em todos os endpoints |
| **Resultado esperado** | `404 DEVTIME-2002` em 100% dos casos; nunca `403`; tempo indistinguível de id inexistente |

### TS-003-18 — Escopo de `MEMBER` com inspeção de SQL
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-02 e a nota ² |
| **Pré-condição** | 50 clientes, 3 com vínculo do `MEMBER` |
| **Passos** | 1. Listar como MEMBER e capturar o SQL. 2. Verificar `totalElements`. 3. Acessar cliente sem vínculo por id |
| **Resultado esperado** | O SQL contém a restrição `EXISTS`; `totalElements = 3`; acesso direto retorna `404`. Nenhuma filtragem ocorre em memória |

### TS-003-19 — Escopo evolui com o vínculo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a definição operacional de vínculo |
| **Passos** | 1. MEMBER sem vínculo lista. 2. Atribuir um ticket ao MEMBER. 3. Listar novamente. 4. Registrar work log em outro contrato. 5. Listar novamente |
| **Resultado esperado** | (1) vazio; (3) o cliente do ticket aparece; (5) o segundo cliente também aparece |

---

## 7. Testes de frontend

### TS-003-20 — Máscara dinâmica de documento
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `dt-document-input` |
| **Passos** | Alternar entre CPF, CNPJ e OTHER, digitando valores |
| **Resultado esperado** | Máscara muda com o tipo; validação local espelha RN-402; `OTHER` não valida dígitos; o valor enviado à API não contém máscara |

### TS-003-21 — Erros `422` mapeados por campo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FM-06 |
| **Passos** | Submeter com documento inválido e com nome duplicado |
| **Resultado esperado** | Mensagem abaixo do campo correspondente, nunca em toast (FM-03); o botão de envio não é desabilitado por formulário inválido (FM-04) |

### TS-003-22 — Diálogo de inativação
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-407 na UI |
| **Passos** | Inativar cliente sem contratos e cliente com 10 contratos |
| **Resultado esperado** | Sem contratos, ação direta; com contratos, diálogo listando os 10 e exigindo confirmação explícita |

### TS-003-23 — Filtros na URL
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CA-06 de `frontend.md` |
| **Passos** | Aplicar busca, filtro de status e página; copiar a URL; abrir em nova aba; recarregar |
| **Resultado esperado** | Estado preservado em todos os casos |

### TS-003-24 — Acessibilidade de P10–P12
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 a AC-10 de `frontend.md` |
| **Passos** | axe-core em cada tela; navegação apenas por teclado |
| **Resultado esperado** | Zero violações; todo campo com `<label>`; foco visível; erros anunciados por `aria-live` |

---

## 8. Testes E2E

### TS-003-25 — Jornada de cadastro e contatos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo principal §7 |
| **Passos** | 1. Criar cliente com CNPJ em P12. 2. Adicionar 2 contatos, marcando um como primário. 3. Trocar o primário. 4. Editar o cliente |
| **Resultado esperado** | Todas as etapas concluídas; sempre um único primário; navegação para P11 após a criação, com a ação de criar contrato oferecida |

### TS-003-26 — Jornada de inativação e exclusão
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FA-05 a FA-09 |
| **Passos** | 1. Tentar excluir cliente com contrato ativo. 2. Inativar com confirmação. 3. Reativar. 4. Encerrar os contratos. 5. Excluir |
| **Resultado esperado** | (1) erro claro com a lista e a sugestão; (2)–(5) concluídos; cliente desaparece da lista após a exclusão |

---

## 9. Testes de performance

### TS-003-27 — Listagem com busca
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta p95 < 300 ms |
| **Pré-condição** | 10.000 clientes no tenant |
| **Passos** | 100 buscas concorrentes com termos variados |
| **Resultado esperado** | p95 < 300 ms; o plano de execução usa o índice GIN |

### TS-003-28 — Resumo com muitos contratos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CX-14 |
| **Pré-condição** | Cliente com 500 contratos históricos e 20 ativos |
| **Passos** | Consultar o resumo |
| **Resultado esperado** | p95 < 1 s; apenas o período corrente é agregado; contratos paginados; nenhuma consulta N+1 |

---

## 10. Testes de segurança

### TS-003-29 — Injeção na busca
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RP-04 |
| **Passos** | Buscar com `' OR 1=1 --`, `%`, `_`, `\`, e sequências Unicode |
| **Resultado esperado** | Nenhum erro de banco exposto; caracteres tratados como literais; consulta parametrizada |

### TS-003-30 — Documento em log
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-084 |
| **Passos** | Executar a suíte capturando os logs e varrer por padrões de CPF e CNPJ |
| **Resultado esperado** | Nenhum documento completo em log; documento inválido nunca é registrado |

### TS-003-31 — Campos monetários por papel
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `CONTRACT_VIEW_FINANCIAL` |
| **Passos** | Consultar o resumo como OWNER, MANAGER, VIEWER e MEMBER |
| **Resultado esperado** | MEMBER não recebe nenhum campo monetário; os demais recebem; a omissão é do backend, não da UI |

---

## 11. Testes de regressão

| ID | Objetivo | Gatilho |
|---|---|---|
| TS-003-32 | Suíte completa a cada PR que toque `client`, `contract` ou `shared/tenancy` | Todo PR |
| TS-003-33 | Suíte de isolamento e escopo executada a cada endpoint novo | Todo PR com endpoint |
| TS-003-34 | Teste de convergência de `activeContractsCount` após alterações em `004` | PR em `004` |
| TS-003-35 | ArchUnit verificando que nenhuma feature acessa `ClientRepository` | Todo PR |

---

## 12. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-003 | TS-003-10 | AC-003-07, AC-003-20 |
| RN-004 | TS-003-13 | AC-003-15 |
| RN-012 | TS-003-13 | AC-003-16 |
| RN-401 | TS-003-08 | AC-003-13, AC-003-33 |
| RN-402 | TS-003-01, TS-003-02, TS-003-03 | AC-003-01, AC-003-10, AC-003-18, AC-003-19 |
| RN-403 | TS-003-07 | AC-003-12, AC-003-25 |
| RN-404 | TS-003-06 | AC-003-11, AC-003-21, AC-003-31 |
| RN-405 | TS-003-09 | AC-003-14 |
| RN-406 | TS-003-05 | AC-003-04, AC-003-22, AC-003-32 |
| RN-407 | TS-003-09, TS-003-22 | AC-003-24 |
| INV-CLI-01 | TS-003-07 | AC-003-12 |
| INV-CLI-02 | TS-003-06 | AC-003-11, AC-003-21 |
| INV-CLI-03 | TS-003-08 | AC-003-13 |
| INV-CON-01 | TS-003-05 | AC-003-22, AC-003-32 |
| IMP-02 | TS-003-18, TS-003-19 | AC-003-27, AC-003-28 |
| RP-03 | TS-003-14 | — |
| RP-04 | TS-003-29 | AC-003-29 |
| ME-05 | TS-003-16 | — |
| ART-024 | TS-003-17 | AC-003-26 |
| ART-084 | TS-003-30 | — |

---

## 13. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `documents-valid` | 100 CPFs e 100 CNPJs válidos, verificados independentemente | TS-003-01, TS-003-02 |
| `documents-invalid` | 100 CPFs e 100 CNPJs inválidos + sequências repetidas | Idem |
| `client-simple` | Cliente `ACTIVE` sem contratos | Exclusão |
| `client-with-active-contract` | Cliente com 1 contrato `ACTIVE` | RN-401 |
| `client-with-ten-contracts` | Cliente com 10 contratos `ACTIVE` | RN-407 |
| `client-with-contacts` | Cliente com 3 contatos, um primário | RN-406 |
| `client-deleted` | Cliente excluído logicamente | CX-02 |
| `tenant-many-clients` | 10.000 clientes com nomes variados e acentos | Performance e busca |
| `member-scoped` | MEMBER com vínculo em 3 de 50 clientes | Escopo de dados |

**Regra:** fixtures são criadas por builders de teste, nunca por SQL bruto — SQL contorna as invariantes de aplicação e produz estado que o sistema real jamais geraria.
