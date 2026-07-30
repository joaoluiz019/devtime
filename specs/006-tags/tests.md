# 006 — Tags · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-006-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-506: converte espaços internos em hífen`.

> **Suíte de normalização escrita antes do código** (`TS-006-01`). A tabela normativa da §6.1 da spec é o oráculo. Escrever o teste depois produziria uma suíte que confirma o comportamento do normalizador — inclusive seus erros — em vez de verificar a regra. É SQ-02 aplicado por escolha, não por obrigação: a feature é de complexidade baixa, mas um erro de normalização é silencioso e permanece no dado para sempre.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `TagNormalizer`, `TagNameValidator`, `TagLinkPolicy`, `TagUsageCounter` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Unicidade, vínculos, exclusão, job |
| API | Controller + serialização + permissões | `@WebMvcTest` | Os 5 endpoints |
| Isolamento | Tenancy nos 5 endpoints e no vínculo | Suíte dedicada | Todos |
| Frontend | Store, autocompletar, chips, prévia da normalização | Jest + Testing Library + MSW | ≥ 90% em store |
| Contrato cruzado | Normalização do frontend × do backend | Jest + JUnit sobre a mesma tabela | 100% das linhas |
| E2E | Rotular ticket, limpar vocabulário | Playwright | Jornada completa |
| Performance | Autocompletar, exclusão em massa, vínculo | k6 | Metas da §20 |
| Segurança | Isolamento, XSS, log, `usageCount` | JUnit + scripts | Vetores da §19 |
| Regressão | Suíte de normalização | CI | 100% verde |

---

## 3. Testes unitários

### TS-006-01 — Tabela normativa de normalização (RN-506)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `TagNormalizer` reproduz **exatamente** a tabela da §6.1 da spec |
| **Pré-condição** | `tag-normalization-cases.csv` com as linhas normativas |
| **Passos** | Para cada linha, normalizar a entrada e comparar com a saída esperada |
| **Resultado esperado** | Igualdade exata em todas as linhas, incluindo `Code Review` → `code-review`, `REFATORAÇÃO` → `refatoração` (acento preservado), `migracao   v2` → `migracao-v2`, `--` → `--`, e as duas linhas de rejeição por comprimento |

### TS-006-02 — Idempotência da normalização
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CE-02 |
| **Passos** | Aplicar `TagNormalizer` 1.000 vezes sucessivas sobre cada saída da tabela |
| **Resultado esperado** | O resultado da 1ª aplicação é igual ao da 1.000ª. Sem isso, renomear uma tag para o próprio nome poderia alterá-la |

### TS-006-03 — O que a normalização NÃO faz (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as quatro não-transformações declaradas |
| **Passos** | Normalizar: (a) `débito-técnico`; (b) `api/rest`, `v2.1`, `c#`; (c) `bugs`; (d) `Refatoraçao` com erro ortográfico |
| **Resultado esperado** | (a) acentos preservados; (b) caracteres especiais preservados; (c) `bugs` permanece `bugs`, sem singularizar; (d) ortografia inalterada. Cada não-transformação é um teste explícito, não uma ausência de teste |

### TS-006-04 — Ordem dos passos da normalização
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que colapsar espaços precede a hifenização |
| **Passos** | Normalizar `"code    review"` (4 espaços) |
| **Resultado esperado** | `code-review`, **não** `code----review`. Inverter os passos 3 e 4 produziria hífens múltiplos — falha silenciosa que só apareceria com entrada específica |

### TS-006-05 — Validação de comprimento sobre o normalizado (RN-507)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão da §6.2 |
| **Passos** | 1. Nome de 1 caractere. 2. De 2. 3. De 40. 4. De 41. 5. De 58 caracteres com espaços que normaliza para 38. 6. Só espaços |
| **Resultado esperado** | (1), (4) e (6) `DEVTIME-2000`; (2), (3) e (5) aceitos. O caso (5) é o que prova que a validação ocorre **após** a normalização |

### TS-006-06 — `TagLinkPolicy` — limite e idempotência (RN-313, INV-TAG-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limite por alvo e o vínculo idempotente |
| **Passos** | 1. Vincular 10 tags a um ticket. 2. Vincular a 11ª. 3. Vincular uma já vinculada. 4. Desvincular uma e vincular outra. 5. Repetir para work log |
| **Resultado esperado** | (2) `DEVTIME-2313`, nada vinculado; (3) aceito sem erro, sem linha nova e sem incrementar `usageCount`; (4) permitido, contagem permanece 10; (5) mesmo comportamento com INV-TAG-01 |

---

## 4. Testes de integração

### TS-006-07 — Unicidade do nome normalizado (RN-507, INV-TAG-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o índice único parcial e o comportamento com equivalências |
| **Passos** | 1. Criar `Code Review`. 2. Criar `code-review`. 3. Criar `  CODE   REVIEW  `. 4. Criar `refatoração` e `refatoracao`. 5. Excluir a primeira e recriá-la |
| **Resultado esperado** | (2) e (3) `409 DEVTIME-2604` — todas normalizam para o mesmo valor; (4) ambas aceitas (CX-02); (5) `201`, pois o índice parcial ignora excluídos |

### TS-006-08 — `usageCount` transacional (INV-TAG-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a atualização dentro da transação do vínculo |
| **Passos** | 1. Vincular a 3 tickets e conferir. 2. Desvincular 1. 3. Excluir um ticket que tinha a tag. 4. Corromper o valor manualmente. 5. Executar o `DenormalizationReconcileJob` |
| **Resultado esperado** | (1) 3; (2) 2; (3) decrementado para 1; (5) valor restaurado por agregação real; execução repetida do job produz o mesmo resultado |

### TS-006-09 — Exclusão com remoção de vínculos (§9.3 `users.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a remoção em lote e as contagens |
| **Pré-condição** | Tag vinculada a 500 tickets e 3.000 work logs |
| **Passos** | 1. Excluir. 2. Conferir a resposta. 3. Conferir as tabelas de vínculo. 4. Conferir os tickets e work logs. 5. Inspecionar o SQL |
| **Resultado esperado** | (2) `unlinkedFromTickets = 500`, `unlinkedFromWorkLogs = 3000`; (3) nenhuma linha remanescente; (4) tickets e work logs íntegros; (5) dois `DELETE` em lote, **nenhum** `SELECT` carregando entidades |

### TS-006-10 — Tag excluída não é vinculável nem recupera vínculos (INV-TAG-05, CX-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a fronteira do soft delete |
| **Passos** | 1. Excluir tag com 10 vínculos. 2. Tentar vinculá-la. 3. Recriar com o mesmo nome. 4. Conferir vínculos e `usageCount` |
| **Resultado esperado** | (2) `404 DEVTIME-2002`; (3) `201` com id distinto; (4) `usageCount = 0` e nenhum vínculo anterior recuperado |

### TS-006-11 — `resolveOrCreate` idempotente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a interface pública consumida por `007` e `008` |
| **Passos** | 1. Chamar com `Code Review` (inexistente). 2. Chamar com `code-review`. 3. Chamar com `  CODE REVIEW  `. 4. Chamar com nome inválido |
| **Resultado esperado** | (1) cria e retorna; (2) e (3) retornam a **mesma** tag, sem criar; (4) `DEVTIME-2000`. Nenhuma chamada gera duplicata |

### TS-006-12 — Sugestões de limpeza com `Clock` fixo (RN-508)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limiar exato e a ausência de exclusão automática |
| **Pré-condição** | `Clock` fixo; tags órfãs há 30, 89, 90, 91 e 200 dias |
| **Passos** | 1. Executar o job e consultar as sugestões. 2. Vincular a de 91 dias a um ticket. 3. Consultar novamente. 4. Verificar se alguma tag foi excluída |
| **Resultado esperado** | (1) apenas as de 91 e 200 dias; a de 90 **não** entra (limiar é estritamente maior); (3) a de 91 sai da lista imediatamente; (4) **nenhuma** exclusão automática ocorreu |

### TS-006-13 — Renomeação preserva vínculos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FA-03 |
| **Pré-condição** | Tag com 8 vínculos em tickets e 20 em work logs |
| **Passos** | 1. Renomear para um nome válido. 2. Conferir vínculos e `usageCount`. 3. Renomear para o nome de outra tag existente |
| **Resultado esperado** | (2) 28 vínculos intactos, `usageCount = 28`; (3) `409 DEVTIME-2604`, nome inalterado |

---

## 5. Testes de API

### TS-006-14 — Contrato dos 5 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; a criação retorna o nome **já normalizado** (§9.2); a exclusão retorna `200` com corpo, nunca `204`; erros em RFC 7807 com `code`; OpenAPI bate com o real |

### TS-006-15 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel, executar |
| **Resultado esperado** | Os 5 papéis listam (`TAG_VIEW`); `OWNER`, `ADMIN`, `MANAGER` e **`MEMBER`** gerenciam; apenas `VIEWER` recebe `403 DEVTIME-1101` com `requiredPermission = TAG_MANAGE`. A presença de `MEMBER` é o ponto do teste — a assimetria com `CATEGORY_MANAGE` é intencional (§16) |

### TS-006-16 — Ordenação e filtro (§9.1 `users.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ordenação padrão e `minUsage` |
| **Passos** | 1. Listar sem parâmetros. 2. Com `minUsage=5`. 3. Com `search`. 4. Com duas tags de mesmo `usageCount` |
| **Resultado esperado** | (1) `usageCount` decrescente; (2) apenas as ≥ 5; (3) comparação sobre o termo normalizado; (4) desempate estável por nome |

---

## 6. Testes de frontend

### TS-006-17 — Contrato cruzado de normalização (FM-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `tagNormalizePipe` e `TagNormalizer` produzem resultados idênticos |
| **Pré-condição** | O **mesmo** `tag-normalization-cases.csv` consumido pelas duas suítes |
| **Passos** | Executar a tabela normativa no Jest e no JUnit, comparando linha a linha |
| **Resultado esperado** | 100% das linhas com resultado idêntico. Divergência aqui significa prévia mentindo ao usuário — o defeito mais provável desta feature (R-01) |

### TS-006-18 — Prévia da normalização em tempo real
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de UX da §21.2 |
| **Passos** | Digitar `Code Review` e observar a prévia; salvar e comparar |
| **Resultado esperado** | A prévia exibe `code-review` durante a digitação; o nome salvo coincide exatamente com o previsto |

### TS-006-19 — Autocompletar com debounce e limite
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a mitigação de R-04 |
| **Passos** | 1. Digitar 10 caracteres rapidamente. 2. Contar as requisições. 3. Verificar o número de sugestões com 100 tags correspondentes |
| **Resultado esperado** | (2) no máximo 2 requisições, por debounce de 250 ms — não 10; (3) no máximo 20 sugestões |

### TS-006-20 — Limite de 10 no componente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ergonomia do limite |
| **Passos** | Vincular 10 tags em `dt-tag-input` e tentar a 11ª |
| **Resultado esperado** | O campo desabilita a adição ao atingir 10 e exibe a razão; se a requisição ocorrer mesmo assim, o erro `DEVTIME-2313` é exibido de forma legível. O bloqueio no cliente é ergonomia; a decisão continua no servidor (IMP-06) |

### TS-006-21 — Acessibilidade dos chips
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 |
| **Passos** | Navegar, adicionar e remover tags usando apenas teclado; verificar leitor de tela |
| **Resultado esperado** | Todas as operações possíveis sem mouse; remoção anunciada; zero violações do axe-core em P31 e no campo de tags de P19 |

---

## 7. Testes E2E

### TS-006-22 — Rotular ticket e limpar vocabulário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o ciclo completo do ponto de vista do usuário |
| **Passos** | 1. Abrir P19 e digitar uma tag inexistente. 2. Confirmar a criação implícita. 3. Digitar novamente e conferir a sugestão. 4. Vincular até 10 e tentar a 11ª. 5. Abrir P31 e conferir `usageCount`. 6. Excluir uma tag e conferir o ticket |
| **Resultado esperado** | Cada etapa reflete o estado correto na UI; a tag criada em (2) aparece como sugestão em (3); o ticket em (6) permanece íntegro sem o rótulo |

---

## 8. Testes de performance

### TS-006-23 — Autocompletar no caminho quente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta da §20 |
| **Pré-condição** | Tenant com 5.000 tags |
| **Passos** | 1.000 consultas de autocompletar com prefixos variados, medindo p95 |
| **Resultado esperado** | p95 < 100 ms; plano de execução usa `idx_tags_tenant_usage`; no máximo 20 linhas retornadas |

### TS-006-24 — Exclusão em massa (CX-12)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta de exclusão |
| **Pré-condição** | Tag com 3.500 vínculos |
| **Passos** | Excluir, medindo duração e memória |
| **Resultado esperado** | Conclusão em menos de 2 s; memória constante; leituras concorrentes não bloqueadas |

### TS-006-25 — Contenção no vínculo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento sob concorrência em tag popular |
| **Passos** | 50 vínculos simultâneos da mesma tag a tickets distintos |
| **Resultado esperado** | Todas as 50 linhas criadas; `usageCount` final igual a 50, ou restaurado pelo reconciliador; **nunca** negativo (protegido por `CHECK`) |

---

## 9. Testes de segurança

### TS-006-26 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-021 e ART-024 |
| **Passos** | Para cada um dos 5 endpoints, acessar recurso do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em todos, nunca `403` |

### TS-006-27 — Vínculo cruzado entre tenants (SG-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a validação dos dois lados do vínculo |
| **Passos** | 1. Tag do tenant A + ticket do tenant B. 2. Tag do tenant B + ticket do tenant A |
| **Resultado esperado** | Ambos `404 DEVTIME-2002`; nenhum vínculo criado; nenhum `usageCount` alterado em nenhum dos tenants |

### TS-006-28 — XSS por nome de tag (SG-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape em todas as saídas |
| **Passos** | Criar tag com `<script>alert(1)</script>` e `"><img src=x onerror=alert(1)>`; renderizar em P19, P31, exportação CSV e PDF de relatório |
| **Resultado esperado** | Texto literal em todas as saídas; nenhum script executado; CSV sem fórmula injetável (prefixo neutralizado); PDF sem marcação interpretável |

### TS-006-29 — `usageCount` imutável por API (SG-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o desnormalizado não é manipulável |
| **Passos** | Enviar `usageCount` em `POST` e em `PATCH` |
| **Resultado esperado** | Campo ignorado em ambos; valor real preservado |

### TS-006-30 — Nome de tag ausente dos logs (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-10, decorrente da análise de LGPD da §19.1 |
| **Passos** | Criar, renomear, vincular, desvincular e excluir tags capturando todos os logs |
| **Resultado esperado** | Nenhum log contém nome de tag; apenas ids, contagens e traceId. A auditoria (dado do tenant) preserva o nome; o log de aplicação, não |

---

## 10. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-006-31 | Tabela normativa de normalização (`TS-006-01`, `TS-006-17`) | **Toda** alteração em `TagNormalizer` ou em `tagNormalizePipe`, dos dois lados |
| TS-006-32 | Limite de 10 (`TS-006-06`) | Toda alteração em `007` ou `008` que toque o vínculo de tags |
| TS-006-33 | `usageCount` (`TS-006-08`, `TS-006-25`) | Toda alteração no reconciliador ou nos eventos de vínculo |
| TS-006-34 | Vínculos com work log (`TS-006-09`) | **Reexecutado ao final de `008`**, contra a tabela `work_log_tags` real |
| TS-006-35 | Isolamento (`TS-006-26`, `TS-006-27`) | Todo endpoint novo |
| TS-006-36 | XSS (`TS-006-28`) | Toda alteração em renderização de tag, especialmente no gerador de PDF de `012` |

**Política:** `TS-006-31` roda nas duas linguagens em todo PR que toque a normalização. Uma divergência entre frontend e backend não quebra nada de imediato — apenas faz a prévia mentir, e o usuário descobre depois de salvar.

---

## 11. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-506 | TS-006-01, TS-006-02, TS-006-03, TS-006-04, TS-006-17 | AC-006-01, AC-006-12, AC-006-21 a AC-006-25 |
| RN-507 | TS-006-05, TS-006-07, TS-006-11 | AC-006-13, AC-006-14, AC-006-15, AC-006-38 |
| RN-508 | TS-006-12 | AC-006-11, AC-006-30, AC-006-31 |
| RN-313 | TS-006-06, TS-006-20 | AC-006-16, AC-006-17, AC-006-28, AC-006-40 |
| RN-003 | TS-006-09, TS-006-10 | AC-006-08, AC-006-26 |
| RN-004 | TS-006-14 | AC-006-19 |
| RN-012 | TS-006-16 | AC-006-09 |
| RN-001 | TS-006-27 | AC-006-34 |
| RN-002 | TS-006-26, TS-006-27 | AC-006-33, AC-006-34 |
| RN-006 | TS-006-09, TS-006-13 | AC-006-01, AC-006-08 |
| INV-TAG-01 | TS-006-06 | AC-006-17, AC-006-32, AC-006-40 |
| INV-TAG-02 | TS-006-07 | AC-006-13, AC-006-26, AC-006-38 |
| INV-TAG-03 | TS-006-01, TS-006-02 | AC-006-01, AC-006-23 |
| INV-TAG-04 | TS-006-08, TS-006-25 | AC-006-04, AC-006-05, AC-006-27, AC-006-39 |
| INV-TAG-05 | TS-006-10 | AC-006-41 |
| §9.1 users.md | TS-006-16, TS-006-23 | AC-006-09, AC-006-10 |
| §9.2 users.md | TS-006-11, TS-006-14, TS-006-19 | AC-006-02, AC-006-03 |
| §9.3 users.md | TS-006-09, TS-006-24 | AC-006-08, AC-006-29 |
| §7 permissions | TS-006-15 | AC-006-20 |
| SG-02 | TS-006-27 | AC-006-34 |
| SG-04 | TS-006-28 | AC-006-35 |
| SG-05 | TS-006-29 | AC-006-36 |
| §28 spec | TS-006-30 | AC-006-37 |
| FM-02 | TS-006-17, TS-006-18 | AC-006-12 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 12. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `tag-normalization-cases.csv` | A tabela normativa da §6.1, incluindo os casos de rejeição | `TS-006-01`, `TS-006-05`, `TS-006-17` — **compartilhada entre Jest e JUnit** |
| `tag-non-transformations.csv` | Casos que a normalização não deve alterar (acentos, especiais, plurais) | `TS-006-03` |
| `fixture-tag-popular` | Tag com 500 vínculos em tickets e 3.000 em work logs | `TS-006-09`, `TS-006-24` |
| `fixture-tag-orphans` | Tags órfãs há 30, 89, 90, 91 e 200 dias | `TS-006-12` |
| `fixture-ticket-10-tags` | Ticket com exatamente 10 tags vinculadas | `TS-006-06`, `TS-006-20` |
| `fixture-tenant-5k-tags` | Tenant com 5.000 tags e `usageCount` distribuído | `TS-006-23` |
| `fixture-tag-xss` | Tags com payloads de XSS e de injeção de fórmula em CSV | `TS-006-28` |
| `fixture-tenant-b` | Segundo tenant com tags e tickets espelhados | `TS-006-26`, `TS-006-27` |
| `fixture-clock-fixed` | `Clock` fixo para os testes de RN-508 | `TS-006-12` |

**Regra de fixture:** `tag-normalization-cases.csv` é um **único arquivo** consumido pelas suítes Java e TypeScript. Duplicá-lo permitiria que os dois lados divergissem sem que nenhum teste falhasse — exatamente o defeito que `TS-006-17` existe para impedir.

---

## 13. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | A suíte de normalização foi escrita e revisada **antes** de `TagNormalizer` |
| CC-02 | A tabela normativa da §6.1 passa integralmente, em Java e em TypeScript, a partir do mesmo arquivo |
| CC-03 | A normalização é idempotente em 1.000 aplicações |
| CC-04 | As quatro não-transformações possuem teste explícito |
| CC-05 | A validação de comprimento ocorre sobre o nome normalizado, provado por CX-05 |
| CC-06 | O limite de 10 é aplicado por alvo e sob concorrência |
| CC-07 | O vínculo é idempotente e não infla `usageCount` |
| CC-08 | A exclusão de 3.500 vínculos conclui em < 2 s, comprovadamente em lote |
| CC-09 | `TagCleanupSuggestionJob` não exclui nenhuma tag, verificado por teste |
| CC-10 | Cobertura ≥ 90% em `TagNormalizer`, services e validators |
| CC-11 | Os 5 endpoints passam na suíte de isolamento com `404` |
| CC-12 | Nenhum log contém nome de tag, verificado por inspeção |
| CC-13 | `TS-006-34` reexecutado e verde após a conclusão de `008-worklogs` |
