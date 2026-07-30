# 015 — Attachments · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-015-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-802: rejeita executável renomeado para .pdf`.

> **Uma suíte escrita antes do código:** `TS-015-01` (assinaturas binárias). Os casos **negativos** são o ponto: cada tipo declarado com a assinatura de outro. Uma implementação que apenas verifica se a assinatura está na allowlist — sem **cruzá-la** com o `contentType` declarado — passa em todos os casos positivos e falha em todos os negativos. Escrita depois, a suíte poderia ser modelada sobre a implementação errada.
>
> **`TS-015-13` (EICAR) é o gatilho de acionamento do risco crítico** desta feature (§9 de `implementation-order.md`). Sua ausência ou falha **bloqueia a entrega** (DoD-02).

**Pré-requisito de ambiente:** o verificador antivírus precisa estar operacional no ambiente de teste (`T-015-02`). Sem ele, `TS-015-13` a `TS-015-16` não podem ser executados, e RN-803 não é verificável.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| **Assinatura binária** | Os 9 tipos, positivos e negativos | JUnit + `@ParameterizedTest` | **≥ 95%** |
| **Antivírus** | EICAR isolado e em ZIP | JUnit + verificador real | Detecção obrigatória |
| Unitário | `FileNameSanitizer`, `StorageKeyGenerator`, `ChecksumCalculator`, `AttachmentLimitPolicy`, `DownloadGuard` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + storage + PostgreSQL | Testcontainers + storage local | Upload, verificação, exclusão |
| **Storage** | Presença e ausência do binário | Acesso direto ao storage | INV-ATT-05, INV-ATT-06 |
| Concorrência | Limites e deduplicação | JUnit + `CountDownLatch` | Sem violação |
| **Memória** | Uploads concorrentes de 10 MB | JUnit + medição de heap | Sem esgotamento |
| API | Controllers + *multipart* + permissões | `@WebMvcTest` | Os 6 endpoints |
| Isolamento | Tenancy + deduplicação entre tenants | Suíte dedicada | Todos os endpoints |
| Frontend | Store, upload, estados de verificação | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Anexar, aguardar, baixar, excluir | Playwright | Jornada completa |
| Segurança | Burla de tipo, *traversal*, storage, rotas | JUnit + inspeção + scripts | Vetores da §19 |
| Regressão | Assinaturas e EICAR | CI | 100% verde |

---

## 3. Testes de assinatura binária

### TS-015-01 — Os 9 tipos, positivos e negativos (RN-802, INV-ATT-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que `MagicNumberValidator` **cruza** a assinatura com o `contentType` declarado |
| **Pré-condição** | `magic-number-cases.csv`: para cada um dos 9 tipos, um arquivo válido e um arquivo de outro tipo declarado como ele |
| **Passos** | Para cada linha, validar e comparar com o resultado esperado |
| **Resultado esperado** | 9 casos positivos aceitos; **todos** os casos negativos rejeitados com `DEVTIME-2702`. Um PDF declarado como `image/png` é rejeitado, mesmo que a assinatura de PDF esteja na allowlist |

### TS-015-02 — Executável renomeado (SG-01, CX-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a defesa contra o vetor mais comum |
| **Passos** | Renomear um executável para cada extensão da allowlist e enviar com o `contentType` correspondente |
| **Resultado esperado** | `415 DEVTIME-2702` em **todos** os casos; nenhum binário gravado em nenhuma tentativa |

### TS-015-03 — Heurística de texto (CX-07, §6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a defesa da categoria mais fraca da allowlist |
| **Passos** | Enviar como `text/plain`: (a) texto UTF-8 válido; (b) arquivo com bytes nulos; (c) executável; (d) UTF-8 inválido; (e) CSV legítimo como `text/csv` |
| **Resultado esperado** | (a) e (e) aceitos; (b), (c) e (d) rejeitados com `DEVTIME-2702`. A heurística examina os primeiros 8 KB |

### TS-015-04 — Contêineres ZIP e Office (CX-05, §6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a verificação do manifesto interno |
| **Passos** | (a) `.docx` legítimo como tipo Word; (b) ZIP comum como tipo Word; (c) ZIP comum como `application/zip`; (d) `.xlsx` legítimo; (e) `.docx` renomeado para `.xlsx` com o tipo de Excel |
| **Resultado esperado** | (a), (c) e (d) aceitos; (b) e (e) rejeitados. Um ZIP declarado como ZIP é aceito — e é o vetor coberto pelo antivírus (CX-06) |

### TS-015-05 — Arquivo vazio (CX-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a rejeição por ausência de assinatura |
| **Passos** | Enviar arquivo de 0 byte declarando cada tipo da allowlist |
| **Resultado esperado** | `415 DEVTIME-2702` em todos; adicionalmente, o `CHECK (size_bytes > 0)` impede a persistência |

---

## 4. Testes unitários

### TS-015-06 — `FileNameSanitizer` (RN-804, INV-ATT-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a sanitização e a preservação do original |
| **Passos** | Sanitizar: `../../etc/passwd`; `..\..\windows\system32`; nome com bytes de controle; nome com 300 caracteres; nome com emoji; nome com apenas extensão |
| **Resultado esperado** | Nenhuma sequência de travessia no resultado; caracteres de controle removidos; truncado a 255 **preservando a extensão**; emoji preservado; `originalFileName` sempre igual ao enviado |

### TS-015-07 — `StorageKeyGenerator` opaco (SG-05, CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a chave não é influenciada pela entrada |
| **Passos** | Gerar chaves para 100 nomes distintos, incluindo os de `TS-015-06`; inspecionar cada chave |
| **Resultado esperado** | Nenhuma chave contém qualquer parte de qualquer nome; todas seguem o mesmo formato opaco; duas chamadas para o mesmo nome produzem chaves **diferentes** — a chave identifica o objeto, não o arquivo |

### TS-015-08 — `ChecksumCalculator` em fluxo (§20.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a leitura em fluxo |
| **Passos** | Calcular o checksum de um arquivo de 10 MB medindo o consumo de heap; comparar com o SHA-256 de referência |
| **Resultado esperado** | Valor correto; consumo de heap **constante**, não proporcional ao tamanho do arquivo |

### TS-015-09 — `AttachmentLimitPolicy` (RN-806, CX-19)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar os limites independentes por alvo |
| **Passos** | (a) 20 e 21 anexos em ticket; (b) 5 e 6 em comentário; (c) 20 no ticket e 5 no comentário do mesmo ticket; (d) 20 no ticket após excluir um dos 20 |
| **Resultado esperado** | (a) 21º rejeitado; (b) 6º rejeitado; (c) ambos permitidos — 25 anexos no total; (d) permitido, pois a contagem ignora excluídos |

### TS-015-10 — `DownloadGuard` (RN-803, INV-ATT-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a guarda em cada estado |
| **Passos** | Tentar baixar com `scanStatus` em `PENDING`, `CLEAN`, `INFECTED` e `FAILED` |
| **Resultado esperado** | `CLEAN` libera; `PENDING` e `FAILED` retornam `409 DEVTIME-2703`; `INFECTED` retorna `403 DEVTIME-2703`. A guarda está no **service**, verificada também por chamada interna |

---

## 5. Testes de integração

### TS-015-11 — Ordem de aplicação da §6.1
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a sequência e a ausência de efeitos colaterais em falha |
| **Passos** | Payloads violando: permissão; dois alvos; alvo inexistente; limite; tamanho; tipo; assinatura |
| **Resultado esperado** | `403`, `422 DEVTIME-2000`, `404`, `422 DEVTIME-2704`, `413 DEVTIME-2701`, `415 DEVTIME-2702`, `415 DEVTIME-2702` — na ordem de precedência. Em **todos** os casos de falha, nenhum binário é gravado no storage |

### TS-015-12 — Tamanho antes de leitura de conteúdo (§6.1, CA-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de ordem |
| **Passos** | Enviar arquivo de 50 MB instrumentando as leituras de conteúdo |
| **Resultado esperado** | Rejeição por tamanho **sem nenhuma** leitura de assinatura e sem gravação no storage. Inverter a ordem permitiria exaustão de recursos por upload grande |

### TS-015-13 — **Arquivo EICAR (SG-02, RN-803, DoD-02)**
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a defesa contra arquivo malicioso — **gatilho de acionamento do risco crítico** |
| **Pré-condição** | Verificador antivírus operacional; arquivo de teste EICAR padrão |
| **Passos** | 1. Enviar EICAR com extensão e `contentType` permitidos. 2. Aguardar a verificação. 3. Tentar baixar. 4. Verificar o storage diretamente. 5. Verificar a notificação, a auditoria e o log |
| **Resultado esperado** | (1) `201` com `PENDING`; (2) `scanStatus = INFECTED`; (3) `403 DEVTIME-2703`; (4) **binário ausente do storage**; (5) notificação a quem enviou, `AuditLog` `ATTACHMENT_SCAN_INFECTED` com a ameaça e o IP do upload, log `ERROR`, métrica `attachment.infected` incrementada |

> **Este teste é obrigatório para a entrega.** Sua falha significa que a feature libera arquivo malicioso para download — a condição que §9 de `implementation-order.md` identifica como gatilho de acionamento do risco crítico.

### TS-015-14 — EICAR dentro de ZIP (SG-03, CX-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a defesa no vetor mais provável |
| **Passos** | Enviar ZIP contendo EICAR, declarado como `application/zip` |
| **Resultado esperado** | Aceito pela allowlist e pela assinatura — o ZIP é legítimo; **detectado como `INFECTED`** pelo antivírus; binário removido; download bloqueado. Confirma que a allowlist sozinha não protege (OB-04) |

### TS-015-15 — Três tentativas de verificação (§4.9, CX-16)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limite e a consequência |
| **Passos** | Verificador falhando sempre; executar o job quatro vezes; tentar baixar |
| **Resultado esperado** | `attemptCount = 3`; `scanStatus = FAILED`; **nenhuma quarta tentativa**; download `409` permanentemente; log `ERROR`; métrica `scan.exhausted` incrementada |

### TS-015-16 — Ausência de liberação manual (§6.3, SG-09, CP-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão mais provável de sofrer pressão (R-03) |
| **Passos** | 1. Enumerar **todas** as rotas expostas. 2. Buscar no código por qualquer caminho que atribua `CLEAN` fora de `ScanService`. 3. Tentar alterar `scanStatus` por qualquer endpoint, como `OWNER` |
| **Resultado esperado** | Nenhuma rota altera `scanStatus`; nenhuma rota de atualização de anexo existe; o único caminho para `CLEAN` é o verificador; `OWNER` não tem exceção |

### TS-015-17 — Deduplicação no tenant (RN-805, §6.4)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o reuso e a restrição de escopo |
| **Passos** | 1. Enviar arquivo X no tenant A. 2. Enviar X novamente no tenant A, em outro ticket. 3. Enviar X no tenant B. 4. Comparar `storageKey` e contar binários no storage |
| **Resultado esperado** | (2) mesma `storageKey`, **nenhum** binário novo, registro em `PENDING`; (3) `storageKey` **distinta** e binário novo — a deduplicação não atravessa tenants (SG-07) |

### TS-015-18 — Remoção do binário por contagem de referências (INV-ATT-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão da §6.4, passos 5 e 6 |
| **Passos** | 1. Dois registros com a mesma `storageKey`. 2. Excluir um. 3. Verificar o storage. 4. Baixar o outro. 5. Excluir o segundo. 6. Verificar o storage |
| **Resultado esperado** | (3) binário **presente**; (4) download bem-sucedido; (6) binário **ausente**, verificado por acesso direto |

### TS-015-19 — Reenvio de conteúdo anteriormente infectado (CX-14)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o `scanStatus` não é herdado |
| **Passos** | 1. Enviar EICAR e aguardar `INFECTED`. 2. Enviar o mesmo arquivo novamente. 3. Observar o estado e o storage |
| **Resultado esperado** | (2) binário gravado **novamente** (o anterior foi removido); registro em `PENDING`; verificado de novo e marcado `INFECTED`. Nenhum estado é herdado por checksum (OB-03) |

### TS-015-20 — Quota do tenant (RN-801)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limite e a liberação por exclusão |
| **Passos** | 1. Preencher a quota até 1 GB. 2. Enviar mais um. 3. Excluir um anexo de 10 MB. 4. Enviar novamente. 5. Inspecionar o plano de execução da soma |
| **Resultado esperado** | (2) `413 DEVTIME-2701` informando o consumo; (4) aceito; (5) index-only scan sobre `idx_attachments_quota` |

---

## 6. Testes de concorrência e memória

### TS-015-21 — Limite do alvo sob concorrência (CX-21)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o limite não é burlável por corrida |
| **Pré-condição** | Ticket com 19 anexos |
| **Passos** | Dois uploads simultâneos |
| **Resultado esperado** | Um `201`, outro `422 DEVTIME-2704`; o ticket termina com **exatamente 20**, nunca 21 |

### TS-015-22 — Uploads concorrentes do mesmo conteúdo (CX-44)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a consistência da deduplicação sob corrida |
| **Passos** | Dois uploads simultâneos do mesmo arquivo no mesmo tenant |
| **Resultado esperado** | Ambos `201`; dois registros; ou compartilham a `storageKey`, ou dois binários idênticos são gravados; **nenhum registro fica sem binário correspondente** |

### TS-015-23 — Exclusão e download simultâneos (INV-ATT-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que não há janela inconsistente |
| **Passos** | Uma requisição exclui o último referenciador enquanto outra solicita o download |
| **Resultado esperado** | Ou o download é atendido antes da remoção, ou retorna `404`; **nunca** uma URL assinada apontando para binário já removido; **nunca** binário órfão no storage |

### TS-015-24 — Memória em uploads concorrentes (CP-14, R-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que checksum e assinatura são lidos em fluxo |
| **Pré-condição** | Heap limitado deliberadamente |
| **Passos** | 10 uploads concorrentes de 10 MB cada, medindo o consumo de heap |
| **Resultado esperado** | Consumo **estável**, não proporcional a 100 MB; nenhuma `OutOfMemoryError`. **Este teste falha contra uma implementação que carrega o arquivo em memória** — é o seu propósito |

---

## 7. Testes de API

### TS-015-25 — Contrato dos 6 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com *multipart* válido e inválido |
| **Resultado esperado** | Status conforme a §14; `canDownload` e `canDelete` presentes; `storageKey` e `checksum` **ausentes** de toda resposta; `413` e `415` corretos; erros em RFC 7807 |

### TS-015-26 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel; para exclusão, anexo próprio e de terceiro |
| **Resultado esperado** | `ATTACHMENT_VIEW` para os 5 papéis, incluindo download; `ATTACHMENT_UPLOAD` sem `VIEWER`; `ATTACHMENT_DELETE_OWN` por ownership (OWN-07); `ATTACHMENT_DELETE_ANY` para `OWNER`, `ADMIN` e `MANAGER` |

### TS-015-27 — Exclusividade de alvo (INV-ATT-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a constraint e a validação |
| **Passos** | 1. Sem alvo. 2. Com dois alvos. 3. `INSERT` direto no banco com dois alvos. 4. `INSERT` direto sem alvo |
| **Resultado esperado** | (1) e (2) `422 DEVTIME-2000`; (3) e (4) violação do `CHECK`. A defesa é da aplicação **e** do banco |

---

## 8. Testes de frontend

### TS-015-28 — Estados de verificação explicados (CP-20)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CE-17 |
| **Passos** | Renderizar anexos em `PENDING`, `CLEAN`, `INFECTED` e `FAILED` |
| **Resultado esperado** | Cada estado exibe selo e **explicação**: em verificação (aguarde), disponível, bloqueado por segurança, falha na verificação (reenvie). Nenhum botão desabilitado sem motivo visível |

### TS-015-29 — Validação local e progresso
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ergonomia do upload |
| **Passos** | Arrastar arquivo de 15 MB; arrastar tipo não permitido; arrastar quando o limite do alvo está atingido |
| **Resultado esperado** | Rejeição **antes** do envio nos três casos, com mensagem clara; progresso exibido em upload válido; a decisão final continua sendo do servidor (IMP-06) |

### TS-015-30 — *Polling* limitado
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia da §21.3 |
| **Passos** | Enviar anexo e observar as requisições por 3 minutos |
| **Resultado esperado** | Intervalo de 3 s; interrompido ao sair de `PENDING`; interrompido aos 2 minutos mesmo sem conclusão |

### TS-015-31 — Nome escapado e acessibilidade (SG-13)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escape e AC-01 |
| **Passos** | Anexo com `<script>` no nome; navegar, enviar e excluir por teclado |
| **Resultado esperado** | Nome como texto literal; nenhum script executado; upload e exclusão completos por teclado; zero violações do axe-core |

---

## 9. Testes E2E

### TS-015-32 — Jornada do anexo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário |
| **Passos** | 1. Anexar em ticket em P19. 2. Observar o estado de verificação. 3. Baixar após `CLEAN`. 4. Anexar em comentário. 5. Anexar EICAR. 6. Observar o bloqueio e a explicação. 7. Excluir um anexo |
| **Resultado esperado** | (2) estado comunicado com clareza; (3) download funcional; (6) bloqueio explicado e notificação recebida; (7) exclusão refletida na listagem e na quota |

---

## 10. Testes de segurança

### TS-015-33 — Storage privado (SG-08, DoD-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a configuração de infraestrutura |
| **Passos** | 1. Obter a `storageKey` de um anexo por inspeção do banco. 2. Tentar acessar o objeto **sem** URL assinada. 3. Acessar com URL assinada válida. 4. Acessar com URL assinada expirada |
| **Resultado esperado** | (2) recusado; (3) sucesso; (4) recusado. **Um bucket público torna toda a proteção desta feature irrelevante** |

### TS-015-34 — Isolamento entre tenants (SG-06, SG-15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-024 |
| **Passos** | Para cada um dos 6 endpoints, acessar anexo ou alvo do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002` em todos, nunca `403` |

### TS-015-35 — Campos internos não expostos (§23, CP-07)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ausência de canal de inferência |
| **Passos** | Consultar anexos por todos os endpoints; inspecionar as respostas |
| **Resultado esperado** | `storageKey` e `checksumSha256` **ausentes** em todas. `checksum` exposto permitiria verificar se um arquivo específico existe no tenant sem tê-lo |

### TS-015-36 — Ausência de nome em log (§19.1, CP-19)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de LGPD |
| **Passos** | Enviar, baixar, excluir e falhar uploads capturando os logs |
| **Resultado esperado** | Nenhum log contém `fileName` nem `originalFileName`; presentes apenas ids, `contentType`, tamanho e traceId |

### TS-015-37 — Bomba de descompressão (SG-14)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a aplicação não descomprime |
| **Passos** | Enviar um ZIP com razão de compressão extrema, declarado como `application/zip` |
| **Resultado esperado** | A aplicação **não** descomprime em nenhum momento; o arquivo segue para o antivírus; nenhum consumo anômalo de CPU ou memória na aplicação |

---

## 11. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-015-38 | Assinaturas (`TS-015-01` a `TS-015-05`) | **Toda** alteração em `MagicNumberValidator`, na allowlist ou em RN-802 |
| TS-015-39 | **EICAR (`TS-015-13`, `TS-015-14`)** | **Toda** alteração em `ScanService`, no verificador, na configuração de storage ou em qualquer caminho de download |
| TS-015-40 | Ausência de liberação manual (`TS-015-16`) | Todo endpoint novo nesta feature; toda alteração em rotas |
| TS-015-41 | Deduplicação e remoção (`TS-015-17`, `TS-015-18`) | Toda alteração em `DeduplicationPolicy` ou na exclusão |
| TS-015-42 | Memória (`TS-015-24`) | Toda alteração em `ChecksumCalculator` ou no caminho de upload |
| TS-015-43 | Storage privado (`TS-015-33`) | **Toda** alteração de configuração de infraestrutura do storage |
| TS-015-44 | Isolamento (`TS-015-34`) | Todo endpoint novo |

**Política:** `TS-015-13`, `TS-015-14`, `TS-015-16` e `TS-015-33` rodam em **todo** PR que toque esta feature, sem amostragem. São os quatro testes cuja falha significa que o sistema distribui arquivo malicioso ou expõe conteúdo sem controle.

**Regra adicional:** `TS-015-43` roda em toda alteração de infraestrutura, mesmo sem mudança de código. Um bucket que se torna público por alteração de política de acesso é falha crítica que nenhum teste de aplicação detecta.

---

## 12. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-801 | TS-015-05, TS-015-12, TS-015-20 | AC-015-09, AC-015-10, AC-015-20 |
| RN-802 | TS-015-01 a TS-015-05 | AC-015-11, AC-015-12, AC-015-21 a AC-015-24 |
| **RN-803** | **TS-015-10, TS-015-13, TS-015-14, TS-015-15, TS-015-16** | AC-015-03, AC-015-13 a AC-015-15, AC-015-32, AC-015-33 |
| RN-804 | TS-015-06 | AC-015-04, AC-015-25, AC-015-26 |
| RN-805 | TS-015-17, TS-015-18, TS-015-19, TS-015-22 | AC-015-05, AC-015-07, AC-015-27, AC-015-28, AC-015-36 |
| RN-806 | TS-015-09, TS-015-21 | AC-015-16, AC-015-17, AC-015-30, AC-015-43 |
| RN-003 | TS-015-18 | AC-015-06 |
| RN-011 | TS-015-16, TS-015-25 | AC-015-40 |
| RN-002 | TS-015-34 | AC-015-39 |
| RN-006 | TS-015-13 | AC-015-01, AC-015-41 |
| INV-ATT-01 | TS-015-27 | AC-015-02, AC-015-18 |
| INV-ATT-02 | TS-015-10 | AC-015-13 a AC-015-15 |
| INV-ATT-03 | TS-015-01 a TS-015-04 | AC-015-12, AC-015-22 a AC-015-24 |
| INV-ATT-04 | TS-015-06, TS-015-07 | AC-015-25, AC-015-26 |
| INV-ATT-05 | TS-015-18, TS-015-23 | AC-015-07, AC-015-27, AC-015-45 |
| INV-ATT-06 | TS-015-13 | AC-015-14, AC-015-32 |
| §6.1 ordem | TS-015-11, TS-015-12 | AC-015-09, AC-015-11 |
| §6.2 allowlist | TS-015-01 a TS-015-05 | AC-015-21 a AC-015-24 |
| §6.3 estados | TS-015-10, TS-015-15, TS-015-16 | AC-015-15, AC-015-29, AC-015-34 |
| §6.4 deduplicação | TS-015-17 | AC-015-05, AC-015-36 |
| §4.9 SM | TS-015-13, TS-015-15 | AC-015-29, AC-015-31, AC-015-32 |
| §7 permissions | TS-015-26 | AC-015-19 |
| §19.1 LGPD | TS-015-33, TS-015-36 | AC-015-38, AC-015-42 |
| §23 DTOs | TS-015-35 | AC-015-37 |
| SG-01 | TS-015-02 | AC-015-22 |
| **SG-02 / SG-03** | **TS-015-13, TS-015-14** | AC-015-32, AC-015-33 |
| SG-05 | TS-015-07 | AC-015-35 |
| SG-07 | TS-015-17 | AC-015-36 |
| SG-08 | TS-015-33 | AC-015-38 |
| SG-09 | TS-015-16 | AC-015-34 |
| SG-13 | TS-015-31 | — |
| SG-14 | TS-015-37 | — |
| CP-14 | TS-015-24 | — |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha. RN-803 e SG-02 possuem cinco e duas suítes respectivamente, proporcional ao seu peso no risco crítico da feature.

---

## 13. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `magic-number-cases.csv` | Os 9 tipos com arquivo válido e arquivo de outro tipo declarado como ele | `TS-015-01` — oráculo das assinaturas |
| **`eicar.txt`** | Arquivo de teste antivírus padrão EICAR | `TS-015-13`, `TS-015-19` |
| **`eicar.zip`** | EICAR dentro de um ZIP legítimo | `TS-015-14` |
| `filename-cases.csv` | Nomes com travessia, controle, 300 caracteres, emoji, só extensão | `TS-015-06`, `TS-015-07` |
| `fixture-valid-samples` | Um arquivo válido por tipo da allowlist | `TS-015-01`, `TS-015-04` |
| `fixture-renamed-executable` | Executável renomeado para cada extensão da allowlist | `TS-015-02` |
| `fixture-text-variants` | UTF-8 válido, com bytes nulos, UTF-8 inválido, executável como texto | `TS-015-03` |
| `fixture-file-10mb` | Arquivo válido de exatamente 10.485.760 bytes | `TS-015-08`, `TS-015-20`, `TS-015-24` |
| `fixture-ticket-19-attachments` | Ticket com 19 anexos | `TS-015-21` |
| `fixture-tenant-quota-full` | Tenant com 1 GB consumido | `TS-015-20` |
| `fixture-zip-bomb` | ZIP com razão de compressão extrema | `TS-015-37` |
| `fixture-tenant-b` | Segundo tenant para deduplicação e isolamento | `TS-015-17`, `TS-015-34` |

**Regras de fixture:**
- **`eicar.txt` e `eicar.zip` são obrigatórios no repositório de teste.** Alguns antivírus de estação de trabalho os detectam e removem; o repositório precisa documentar isso e o ambiente de CI precisa ser configurado para preservá-los. Sem essas duas fixtures, o risco crítico da feature não é verificável.
- `magic-number-cases.csv` deve conter, para cada tipo, ao menos **um caso negativo** — um arquivo de outro tipo declarado como ele. É o que torna `TS-015-01` capaz de detectar uma implementação que não cruza assinatura com `contentType`.
- `fixture-renamed-executable` usa um executável real, não um arquivo com bytes arbitrários: a assinatura de executável precisa ser genuína para o teste ser significativo.

---

## 14. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-015-01` foi escrita **antes** de `MagicNumberValidator`, com casos negativos |
| CC-02 | Os 9 tipos passam com casos positivos **e** negativos |
| CC-03 | Executável renomeado rejeitado em **todas** as extensões da allowlist |
| CC-04 | Heurística de texto rejeita bytes nulos e UTF-8 inválido |
| CC-05 | Manifesto interno distingue Office legítimo de ZIP renomeado |
| CC-06 | **`TS-015-13` (EICAR) verde**, com binário removido do storage e download `403` |
| CC-07 | **`TS-015-14` (EICAR em ZIP) verde** |
| CC-08 | `TS-015-16` prova que **nenhum** caminho de liberação manual existe |
| CC-09 | Três tentativas e nenhuma quarta |
| CC-10 | `storageKey` comprovadamente sem nenhuma parte do nome |
| CC-11 | Nome sanitizado com extensão preservada no truncamento |
| CC-12 | Deduplicação restrita ao tenant, provada com dois tenants |
| CC-13 | Remoção do binário provada por **acesso direto ao storage** |
| CC-14 | Reenvio de conteúdo infectado não herda `scanStatus` |
| CC-15 | Tamanho rejeitado antes de qualquer leitura de conteúdo |
| CC-16 | 10 uploads concorrentes de 10 MB com heap estável |
| CC-17 | Limite do alvo não burlável por concorrência |
| CC-18 | **Storage privado provado por acesso anônimo recusado** |
| CC-19 | `storageKey` e `checksum` ausentes de toda resposta |
| CC-20 | Nenhum log contém nome de arquivo |
| CC-21 | Aplicação **não** descomprime ZIP |
| CC-22 | Cobertura ≥ 95% em `MagicNumberValidator`; ≥ 90% em services e validators |
| CC-23 | Os 6 endpoints passam na suíte de isolamento com `404` |
| CC-24 | Estados de verificação explicados na UI, sem botão desabilitado sem motivo |
| CC-25 | Verificador antivírus operacional em **todos** os ambientes, incluindo teste |
