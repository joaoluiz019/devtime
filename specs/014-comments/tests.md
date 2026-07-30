# 014 — Comments · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-014-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-813: não notifica menção a membro suspenso`.

> **Uma suíte escrita antes do código:** `TS-014-01` (tabela de menções). Três das seis linhas normativas são casos que uma implementação ingênua erra: membro suspenso deve ficar como texto e não falhar; menção repetida deve gerar uma notificação e não duas; auto-menção deve ser registrada **sem** notificar. Escrita depois, a suíte refletiria o comportamento implementado.

**Relógio:** os testes da janela de 24 horas injetam um `Clock` fixo. Um teste que cria o comentário e o edita imediatamente não exercita a regra.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `MentionExtractor`, `CommentThreadPolicy`, `CommentEditPolicy`, `SystemCommentTemplates` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | CRUD, hierarquia, sistema |
| Temporal | Janela de 24 horas | JUnit + `Clock` fixo | 4 pontos |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Os 4 endpoints |
| Isolamento | Tenancy | Suíte dedicada | Todos os endpoints |
| Frontend | Store, editor, autocompletar, sanitização | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Comentar, responder, editar, moderar | Playwright | Jornada completa |
| Performance | Listagem com respostas, autocompletar | k6 | Metas da §20 |
| Segurança | XSS, campos forjados, autocompletar | JUnit + scripts | Vetores da §19 |
| Regressão | Menções e sanitização | CI | 100% verde |

---

## 3. Testes unitários

### TS-014-01 — Tabela normativa de menções (RN-813, §6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `MentionExtractor` reproduz **exatamente** as 6 linhas da §6.2 |
| **Pré-condição** | `mention-cases.csv` com corpo, membros ativos e resultado esperado |
| **Passos** | Para cada linha, extrair as menções e comparar `mentionedUserIds` e os notificados |
| **Resultado esperado** | `@ana` ativa → notificada; `@bruno` suspenso → **texto, sem notificação**; `@carlos` inexistente → nada; `email@dominio.com` → **nenhuma menção**; `@ana @ana` → **uma** notificação; auto-menção → registrada **sem** notificação. Igualdade exata nas 6 |

### TS-014-02 — Resolução em uma consulta (CP-13)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia em lote |
| **Passos** | Comentário com 20 menções distintas; inspecionar o SQL emitido |
| **Resultado esperado** | **Uma** consulta de resolução para os 20 identificadores; nunca 20 consultas |

### TS-014-03 — `CommentThreadPolicy` (RN-814, INV-CMT-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a normalização na escrita |
| **Passos** | (a) sem pai; (b) pai é raiz; (c) pai é resposta; (d) pai de outro ticket; (e) pai inexistente; (f) pai excluído |
| **Resultado esperado** | (a) raiz; (b) vinculado ao pai; (c) vinculado à **raiz do pai**, não ao pai; (d) e (e) `DEVTIME-2706`; (f) `DEVTIME-2706` |

### TS-014-04 — `CommentEditPolicy` — janela e ownership (RN-812, §6.3)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a matriz completa da §6.3 |
| **Pré-condição** | `Clock` fixo |
| **Passos** | Para cada combinação de (autor, `ADMIN`, terceiro) × (< 24h, ≥ 24h) × (usuário, sistema): tentar editar e excluir |
| **Resultado esperado** | Conforme a §6.3: autor edita e exclui < 24h; `ADMIN` **apenas exclui**, em qualquer momento; terceiro nada; sistema nada, por ninguém. **`ADMIN` editando retorna `403` em todos os casos** |

### TS-014-05 — Limite exato da janela (CX-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o limite é estritamente menor que 24h |
| **Passos** | Editar comentário criado há 1h, 23h59, 24h exatas e 25h |
| **Resultado esperado** | 1h e 23h59 permitidos; 24h exatas e 25h rejeitados com `DEVTIME-1101` |

### TS-014-06 — Validação de corpo (RN-811)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os limites e o aparo |
| **Passos** | Corpos de 0, 1, 10.000 e 10.001 caracteres; só espaços; só saltos de linha; espaços nas bordas de um corpo válido |
| **Resultado esperado** | 1 e 10.000 aceitos; 0 e 10.001 rejeitados com `DEVTIME-2705`; só espaços e só saltos rejeitados; bordas aparadas antes da validação |

---

## 4. Testes de integração

### TS-014-07 — Ordem de aplicação (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a sequência de validações |
| **Passos** | Payloads violando: permissão; ticket inexistente; corpo inválido; pai de outro ticket |
| **Resultado esperado** | `403`, `404`, `422 DEVTIME-2705`, `422 DEVTIME-2706` — nessa ordem de precedência quando múltiplas violações coexistem |

### TS-014-08 — Comentário de sistema transacional (RN-815, CP-16)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a criação está na transação da transição |
| **Passos** | 1. Transicionar um ticket com sucesso e conferir o comentário. 2. Injetar falha após a criação do comentário e antes do commit da transição. 3. Conferir o estado |
| **Resultado esperado** | (1) comentário de sistema criado com `isSystem = true`; (3) **rollback total** — nenhum comentário permanece e o status do ticket não mudou. Nunca existe comentário de sistema sem a transição correspondente |

### TS-014-09 — Os três gatilhos de RN-815
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a cobertura dos gatilhos |
| **Passos** | Provocar mudança de status, mudança de responsável e movimentação de contrato em `007` |
| **Resultado esperado** | Um comentário de sistema por gatilho, com texto de `SystemCommentTemplates`; todos imutáveis; todos na linha do tempo |

### TS-014-10 — Imutabilidade de sistema (INV-CMT-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum caminho altera comentário de sistema |
| **Passos** | Tentar editar e excluir como autor do gatilho, como `ADMIN`, como `OWNER` e por chamada interna de serviço |
| **Resultado esperado** | `403 DEVTIME-1101` em todos os casos; a verificação está no **service**, não apenas no controller |

### TS-014-11 — Exclusão de raiz preserva respostas (CX-08, CP-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de OB-03 |
| **Passos** | 1. Raiz com 3 respostas. 2. Excluir o raiz. 3. Listar. 4. Conferir o banco |
| **Resultado esperado** | Raiz com `deletedAt`; **as 3 respostas permanecem visíveis**; nenhuma exclusão em cascata; a listagem indica o raiz removido |

### TS-014-12 — Edição registra o corpo anterior (§18)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a única fonte do conteúdo original |
| **Passos** | 1. Criar com corpo A. 2. Editar para corpo B. 3. Consultar a auditoria |
| **Resultado esperado** | `AuditLog` `COMMENT_UPDATED` com `beforeState` contendo o corpo A **completo**; sem isso, a edição apagaria irreversivelmente o que foi dito (RS-04) |

### TS-014-13 — Edição reextrai menções (CX-10, CX-26)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento das notificações na edição |
| **Passos** | 1. Comentário mencionando Ana. 2. Editar adicionando Bruno, mantendo Ana. 3. Conferir notificações |
| **Resultado esperado** | `mentionedUserIds` com Ana e Bruno; **Bruno** notificado; **Ana não** renotificada |

### TS-014-14 — Respostas em lote (CP-13, R-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ausência de N+1 |
| **Passos** | Listar ticket com 20 raízes, cada uma com 5 respostas; inspecionar o SQL |
| **Resultado esperado** | Uma consulta para as raízes e **uma** para todas as respostas; nunca 20 consultas de resposta |

### TS-014-15 — Autor removido do tenant (CX-11)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-458 aplicado a comentários |
| **Passos** | 1. Comentários de um membro. 2. Remover o membro. 3. Listar |
| **Resultado esperado** | Comentários preservados; nome exibido como `Usuário Removido`; `authorId` mantido |

---

## 5. Testes de API

### TS-014-16 — Contrato dos 4 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `canEdit` e `canDelete` presentes em toda resposta; erros em RFC 7807; paginação por cursor |

### TS-014-17 — `canEdit` e `canDelete` do servidor (CP-10, §23)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o cliente não reimplementa a regra |
| **Pré-condição** | Comentários em 4 situações: meu < 24h, meu ≥ 24h, de terceiro, de sistema |
| **Passos** | Listar como `MEMBER` e como `ADMIN`; para cada comentário, comparar os flags com o resultado real da operação |
| **Resultado esperado** | Os flags coincidem com o comportamento real em **todos** os 8 pares. Uma divergência significaria a UI habilitando um botão que falha |

### TS-014-18 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel; para edição, em comentário próprio e de terceiro |
| **Resultado esperado** | `COMMENT_VIEW` para os 5 papéis; `COMMENT_CREATE` sem `VIEWER`; `COMMENT_DELETE_ANY` para `OWNER`, `ADMIN` e `MANAGER`; **nenhum** papel edita comentário de terceiro |

### TS-014-19 — Campos forjados (SG-07, SG-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que campos de sistema são inalteráveis |
| **Passos** | Enviar `authorId`, `isSystem`, `mentionedUserIds`, `editedAt` e `parentCommentId` (na edição) |
| **Resultado esperado** | Todos ignorados; `authorId` do token; `isSystem = false`; menções derivadas do corpo; `parentCommentId` imutável na edição |

---

## 6. Testes de frontend

### TS-014-20 — Autocompletar de menção (SG-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o filtro e a mitigação de enumeração |
| **Passos** | 1. Digitar `an` com um membro ativo `ana` e um suspenso `andre`. 2. Contar as requisições ao digitar 10 caracteres. 3. Verificar o limite de sugestões |
| **Resultado esperado** | (1) apenas `ana` sugerida; nenhuma resposta distingue suspenso de inexistente; (2) no máximo 2 requisições por debounce de 250 ms; (3) no máximo 10 sugestões |

### TS-014-21 — Sanitização reutilizada de `007` (CP-12, SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não há allowlist duplicada |
| **Passos** | 1. Inspecionar as importações de `dt-comment-editor`. 2. Renderizar corpos com `<script>`, `<iframe>`, `javascript:` em link e atributos de evento |
| **Resultado esperado** | (1) `dt-markdown-editor` e `dt-markdown-view` importados de `007`, sem reimplementação; (2) todos os payloads neutralizados com o mesmo comportamento de `007` |

### TS-014-22 — Ações conforme o servidor
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `canEditComment` |
| **Passos** | Renderizar comentários nas 4 situações, como `MEMBER` e como `ADMIN` |
| **Resultado esperado** | Botão de edição habilitado apenas quando `canEdit` é verdadeiro; `ADMIN` vê excluir mas **não** editar em comentário de terceiro; comentário de sistema sem nenhuma ação |

### TS-014-23 — Comentário de sistema visualmente distinto
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a distinção imediata |
| **Passos** | Renderizar um fio com comentários de usuário e de sistema misturados |
| **Resultado esperado** | `dt-system-comment` com estilo distinto; nenhuma ação disponível; o usuário distingue imediatamente o que o sistema registrou |

### TS-014-24 — Raiz excluído com respostas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `dt-comment-deleted` |
| **Passos** | Renderizar um fio cujo raiz foi excluído e possui 3 respostas |
| **Resultado esperado** | Marcador de comentário removido no lugar do raiz; as 3 respostas visíveis e aninhadas; nenhuma resposta aparece órfã |

### TS-014-25 — Contador de caracteres e acessibilidade
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ergonomia do limite e AC-01 |
| **Passos** | Digitar até 10.000 caracteres; navegar e enviar por teclado; verificar leitor de tela |
| **Resultado esperado** | Contador visível ao aproximar do limite; envio bloqueado ao exceder; navegação e envio completos por teclado; zero violações do axe-core |

---

## 7. Testes E2E

### TS-014-26 — Jornada da conversa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário |
| **Passos** | 1. Comentar em P19 mencionando um colega. 2. Conferir a notificação do colega. 3. Responder ao próprio comentário. 4. Responder à resposta. 5. Editar. 6. Como `ADMIN`, tentar editar e depois excluir. 7. Provocar mudança de status e conferir o comentário de sistema |
| **Resultado esperado** | (4) vinculada à raiz; (6) edição bloqueada, exclusão bem-sucedida; (7) comentário de sistema visualmente distinto e sem ações |

---

## 8. Testes de performance

### TS-014-27 — Listagem com respostas (§20)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta e a paginação por cursor |
| **Pré-condição** | Ticket com 500 comentários |
| **Passos** | Percorrer todas as páginas medindo cada uma |
| **Resultado esperado** | p95 < 250 ms; tempo **constante** entre a primeira e a última página; nenhum `OFFSET` no SQL |

### TS-014-28 — Autocompletar e criação
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as metas de escrita |
| **Passos** | 1.000 sugestões de menção e 1.000 criações com menções |
| **Resultado esperado** | Autocompletar p95 < 100 ms; criação p95 < 200 ms; resolução de menções < 50 ms |

---

## 9. Testes de segurança

### TS-014-29 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-024 |
| **Passos** | Para cada um dos 4 endpoints, acessar comentário ou ticket do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002`, nunca `403` |

### TS-014-30 — Janela burlada por chamada interna (SG-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-01 |
| **Passos** | Chamar `CommentService.update` diretamente, sem passar pelo controller, em comentário de 30h |
| **Resultado esperado** | `DEVTIME-1101`. A guarda está no service; um job ou outra feature que chamasse o serviço também seria bloqueado |

### TS-014-31 — XSS em todas as saídas (SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape onde o comentário aparece |
| **Passos** | Comentário com payloads; renderizar na central, na linha do tempo de `007` e em qualquer exportação que o inclua |
| **Resultado esperado** | Texto literal em todas as saídas; nenhuma tag fora da allowlist |

### TS-014-32 — Ausência de `body` em log (§28, CP-15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de §19.1 |
| **Passos** | Criar, editar, excluir e falhar comentários capturando os logs |
| **Resultado esperado** | Nenhum log contém `body`, antes ou depois da edição; presentes apenas ids, `ticketKey`, contagens e traceId |

---

## 10. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-014-33 | Tabela de menções (`TS-014-01`) | **Toda** alteração em `MentionExtractor` ou em RN-813 |
| TS-014-34 | Sanitização (`TS-014-21`, `TS-014-31`) | **Toda** alteração em `dt-markdown-editor` ou `dt-markdown-view` de `007` — a mudança lá afeta esta feature |
| TS-014-35 | Comentário de sistema transacional (`TS-014-08`) | Toda alteração em `TicketStateMachine` ou nos gatilhos de RN-815 |
| TS-014-36 | Janela de 24h (`TS-014-04`, `TS-014-05`) | Toda alteração em `CommentEditPolicy` ou em RN-812 |
| TS-014-37 | Respostas em lote (`TS-014-14`) | Toda alteração em `CommentRepository` ou na listagem |
| TS-014-38 | Isolamento (`TS-014-29`) | Todo endpoint novo |

**Política:** `TS-014-34` roda em todo PR que toque `007-tickets`, não apenas esta feature. A sanitização é **compartilhada** (CP-12), e uma alteração na allowlist de `007` altera o comportamento aqui sem que nenhum arquivo desta feature seja tocado — exatamente o cenário de R-08.

---

## 11. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-811 | TS-014-06 | AC-014-01, AC-014-10, AC-014-11, AC-014-17, AC-014-18 |
| RN-812 | TS-014-04, TS-014-05, TS-014-30 | AC-014-04 a AC-014-06, AC-014-12, AC-014-13, AC-014-25 |
| RN-813 | TS-014-01, TS-014-02, TS-014-13, TS-014-20 | AC-014-02, AC-014-20 a AC-014-24, AC-014-26 |
| RN-814 | TS-014-03 | AC-014-03, AC-014-19 |
| RN-815 | TS-014-08, TS-014-09, TS-014-10 | AC-014-07, AC-014-14, AC-014-36 |
| RN-003 | TS-014-11 | AC-014-05, AC-014-27 |
| RN-004 | TS-014-16 | AC-014-37 |
| RN-011 | TS-014-19 | AC-014-32, AC-014-33 |
| RN-012 | TS-014-27 | AC-014-29 |
| RN-002 | TS-014-29 | AC-014-30 |
| RN-006 | TS-014-12 | AC-014-01, AC-014-04 |
| RN-458 | TS-014-15 | AC-014-28 |
| INV-CMT-01 | TS-014-03 | AC-014-19 |
| INV-CMT-02 | TS-014-03 | AC-014-15 |
| INV-CMT-03 | TS-014-10 | AC-014-14 |
| INV-CMT-04 | TS-014-01 | AC-014-20, AC-014-21 |
| INV-CMT-05 | TS-014-19 | AC-014-32, AC-014-33 |
| §6.1 ordem | TS-014-07 | — |
| §6.2 menções | TS-014-01 | AC-014-02, AC-014-20 a AC-014-24 |
| §6.3 janela e moderação | TS-014-04 | AC-014-04, AC-014-06, AC-014-13 |
| §7 permissions | TS-014-18 | AC-014-16 |
| §18 auditoria | TS-014-12 | — |
| §23 flags do servidor | TS-014-17, TS-014-22 | AC-014-09 |
| §28 logs | TS-014-32 | AC-014-35 |
| CX-08 | TS-014-11, TS-014-24 | AC-014-27, AC-014-38 |
| SG-03 | TS-014-30 | — |
| SG-05 | TS-014-21, TS-014-31 | AC-014-31 |
| SG-06 | TS-014-20 | AC-014-34 |
| SG-07 / SG-08 | TS-014-19 | AC-014-32, AC-014-33 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 12. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `mention-cases.csv` | As 6 linhas normativas da §6.2 | `TS-014-01` — oráculo das menções |
| `comment-edit-matrix.csv` | (papel × idade × `isSystem`) × resultado esperado | `TS-014-04` |
| `fixture-tenant-members` | Membro ativo `ana`, suspenso `andre`, removido `bruno` | `TS-014-01`, `TS-014-20` |
| `fixture-thread-two-levels` | Raiz A com resposta B, para testar resposta a resposta | `TS-014-03` |
| `fixture-root-with-replies` | Raiz com 3 respostas | `TS-014-11`, `TS-014-24` |
| `fixture-ticket-500-comments` | Ticket com 500 comentários, 20 raízes com 5 respostas cada | `TS-014-14`, `TS-014-27` |
| `fixture-comment-20-mentions` | Comentário com 20 menções distintas | `TS-014-02` |
| `fixture-clock-edit-window` | `Clock` fixo em 1h, 23h59, 24h e 25h após a criação | `TS-014-04`, `TS-014-05` |
| `fixture-xss-payloads` | Payloads de XSS — **o mesmo arquivo usado por `007`** | `TS-014-21`, `TS-014-31` |
| `fixture-tenant-b` | Segundo tenant com comentários espelhados | `TS-014-29` |

**Regras de fixture:**
- `fixture-xss-payloads` é **compartilhada com `007-tickets`**. É o que garante que as duas features são testadas contra o mesmo conjunto de ataques — coerente com a decisão de reutilizar a sanitização (CP-12).
- `fixture-clock-edit-window` é obrigatória: um teste que cria e edita imediatamente não exercita a janela de 24h.

---

## 13. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-014-01` foi escrita **antes** de `MentionExtractor` |
| CC-02 | As 6 linhas da tabela de menções passam com igualdade exata |
| CC-03 | Resolução de menções em **uma** consulta, comprovado em SQL |
| CC-04 | Resposta a resposta comprovadamente vinculada à raiz |
| CC-05 | A matriz da §6.3 passa em todas as combinações |
| CC-06 | `ADMIN` recebe `403` ao editar comentário de terceiro, em todos os caminhos |
| CC-07 | O limite da janela é estritamente menor que 24h, provado com `Clock` fixo |
| CC-08 | Comentário de sistema comprovadamente na transação da transição, com rollback |
| CC-09 | Comentário de sistema imutável por todos os caminhos, incluindo chamada interna |
| CC-10 | Exclusão de raiz preserva as respostas |
| CC-11 | Auditoria de edição contém o corpo anterior completo |
| CC-12 | `canEdit` e `canDelete` coincidem com o comportamento real nos 8 pares |
| CC-13 | Nenhuma consulta N+1 ao carregar respostas |
| CC-14 | Sanitização comprovadamente reutilizada de `007`, sem allowlist duplicada |
| CC-15 | Autocompletar não distingue membro suspenso de inexistente |
| CC-16 | Nenhum log contém `body` |
| CC-17 | Listagem com tempo constante entre a primeira e a última página |
| CC-18 | Cobertura ≥ 90% em services e policies |
| CC-19 | Os 4 endpoints passam na suíte de isolamento com `404` |
| CC-20 | Zero violações do axe-core, com envio por teclado |
