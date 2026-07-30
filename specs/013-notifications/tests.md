# 013 — Notifications · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-013-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-601: ignora silenciosamente a criação com dedupeKey existente`.

> **Uma suíte escrita antes do código:** `TS-013-01` (concorrência da deduplicação). O modo de falha é a implementação "verificar e inserir", que tem janela de corrida e **passa em qualquer teste sequencial**. Escrita antes, a suíte força a estratégia correta: inserir tratando a violação do índice único como sucesso.

**Relógio:** todo teste de janela temporal injeta um `Clock` fixo. Os limiares de 3 dias, 15 dias e 90 dias são inverificáveis com relógio real.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| **Concorrência** | Deduplicação sob avaliações simultâneas | JUnit + `CountDownLatch` | Sem duplicata |
| Unitário | `DedupeKeyBuilder`, `RecipientResolver`, `ConsumptionAlertPolicy`, `EmailDispatchPolicy`, `NotificationTemplateRenderer` | JUnit 5 + AssertJ + `@ParameterizedTest` | ≥ 95% |
| Integração | Service + consumidores de evento + PostgreSQL | Testcontainers | Os 10 eventos |
| Temporal | Lembretes de 3 e 15 dias, purga de 90 dias | JUnit + `Clock` fixo | 3 janelas |
| **Coerência entre features** | Severidade × `010-dashboard` | JUnit | Mesmos limiares |
| API | Controllers + serialização + escopo | `@WebMvcTest` | Os 9 endpoints |
| Fluxo | SSE: isolamento, reconexão, degradação | JUnit + Jest | Todos os cenários |
| Isolamento | Tenancy + escopo por destinatário | Suíte dedicada | Todos os endpoints |
| Frontend | Store, sino, reconexão, preferências | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Central, leitura, preferências | Playwright | Jornada completa |
| Performance | Contagem de não lidas, listagem | k6 | Metas da §20 |
| Segurança | Escopo, `dedupeKey`, conteúdo do e-mail | JUnit + inspeção | Vetores da §19 |
| Regressão | Deduplicação e coerência | CI | 100% verde |

---

## 3. Testes de deduplicação

### TS-013-01 — Concorrência da deduplicação (RN-601, INV-NOT-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a criação é idempotente **sob concorrência** |
| **Pré-condição** | Período com consumo em 82%; 100 threads sincronizadas por `CountDownLatch` |
| **Passos** | 100 avaliações simultâneas do mesmo limiar para o mesmo destinatário |
| **Resultado esperado** | Exatamente **uma** notificação por destinatário; nenhuma exceção propagada ao chamador; a rejeição ocorre pela constraint, não apenas por verificação prévia. **Este teste falha contra uma implementação "verificar e inserir"** — é o seu propósito |

### TS-013-02 — Repetição sequencial (CX-01)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a idempotência no caso comum |
| **Passos** | Avaliar o mesmo limiar 100 vezes em sequência |
| **Resultado esperado** | Uma notificação; 99 tentativas ignoradas silenciosamente; nenhum erro; métrica `notification.deduped` incrementada 99 vezes |

### TS-013-03 — `DedupeKeyBuilder` (RN-603, §6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que as 11 chaves são montadas exatamente como na matriz |
| **Pré-condição** | `dedupe-key-cases.csv` com os 11 tipos e as chaves esperadas |
| **Passos** | Para cada tipo, montar a chave e comparar |
| **Resultado esperado** | Igualdade exata nas 11, incluindo `CONTRACT_USAGE:{periodId}:{threshold}`, `TICKET_ASSIGNED:{ticketId}:{assigneeId}` e `TICKET_COMMENT:{commentId}:{userId}` |

### TS-013-04 — Sequência de oscilação (§6.3, CE-11)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o comportamento central da feature |
| **Passos** | Reproduzir os 7 momentos da §6.3: 45% → 82% → 70% → 85% → 105% → 95% → 102% |
| **Resultado esperado** | Momento 2: cria `:50` e `:80`; momento 4: **nenhuma nova**; momento 5: cria `:100` e `OVERAGE`; momentos 3, 6 e 7: **nada acontece** e nenhuma notificação é removida. Total final: 4 notificações por destinatário |

### TS-013-05 — Unicidade por destinatário, não global (CX-09)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a chave é única **por destinatário** |
| **Pré-condição** | Tenant com dois `OWNER` e um `ADMIN` |
| **Passos** | Gerar um alerta de consumo |
| **Resultado esperado** | Três notificações, todas com o **mesmo** `dedupeKey`, uma por destinatário; nenhuma violação de constraint |

---

## 4. Testes unitários

### TS-013-06 — `ConsumptionAlertPolicy` com limiares do contrato (CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que os limiares vêm do contrato |
| **Passos** | Com `[50,80,100]`: consumo em 30%, 60%, 85%, 105%. Com `[70,90]`: 75%, 92%. Com `[100]`: 99%, 100% |
| **Resultado esperado** | Chaves derivadas dos limiares configurados; com `[70,90]` **nenhuma** chave `:50` ou `:80`; com `[100]`, apenas `:100` e o excedente (CX-06) |

### TS-013-07 — Excedente e salto múltiplo (RN-604, CX-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a criação simultânea de vários limiares |
| **Passos** | Consumo saltando de 0% a 105% em uma única avaliação |
| **Resultado esperado** | Quatro notificações: `:50`, `:80`, `:100` e `CONTRACT_OVERAGE`; nenhuma omitida por ter sido ultrapassada junto |

### TS-013-08 — Contrato `HOURLY_OPEN` (CX-04, CE-10)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ausência de avaliação |
| **Passos** | Registrar 600 minutos faturáveis em contrato `HOURLY_OPEN` |
| **Resultado esperado** | Nenhuma notificação de consumo nem de excedente; nenhuma avaliação executada |

### TS-013-09 — `RecipientResolver` (RN-607)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a resolução por tipo |
| **Pré-condição** | Tenant com um de cada papel, mais um `ADMIN` suspenso |
| **Passos** | Resolver destinatários para: consumo, período, timer, ticket, comentário, exportação |
| **Resultado esperado** | Consumo e período → `OWNER` e `ADMIN` **ativos** (o suspenso é excluído); timer → dono; ticket → responsável; comentário → responsável + mencionados **ativos**; exportação → solicitante |

### TS-013-10 — `EmailDispatchPolicy` (RN-608)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as duas condições de supressão |
| **Passos** | (a) `emailNotifications = true`, tipo não silenciado; (b) tipo silenciado; (c) `emailNotifications = false`; (d) ambos |
| **Resultado esperado** | (a) e-mail enviado; (b), (c) e (d) suprimido. **Em todos os quatro casos a in-app é criada** |

### TS-013-11 — `NotificationTemplateRenderer` (§19.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ausência de dado sensível |
| **Passos** | Gerar título e corpo para os 11 tipos, em contexto com descrições de work log e valores monetários disponíveis |
| **Resultado esperado** | Nenhum corpo contém descrição de registro nem valor monetário; todos contêm link para o sistema; `body` dentro de 500 caracteres |

---

## 5. Testes de integração

### TS-013-12 — Os dez eventos consumidos (§15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a reação a cada evento |
| **Passos** | Publicar cada um dos 10 eventos e verificar a notificação gerada |
| **Resultado esperado** | Tipo, severidade, destinatário e `dedupeKey` conforme a §6.1 em todos os casos |

### TS-013-13 — Eventos consumidos após o commit (CP-16, TX-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que falha de notificação não reverte a origem |
| **Passos** | 1. Injetar falha em `NotificationService`. 2. Fechar um período. 3. Verificar o período e a notificação |
| **Resultado esperado** | O fechamento **conclui** normalmente; nenhuma notificação criada; o erro é registrado. Uma falha de provedor de e-mail não pode desfazer um fechamento de período |

### TS-013-14 — In-app antes do e-mail (§6.2, INV-NOT-02)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ordem estrutural |
| **Passos** | 1. Provedor de e-mail indisponível. 2. Gerar notificação. 3. Consultar a central |
| **Resultado esperado** | Notificação presente e legível na central; e-mail pendente na fila; nenhuma parte revertida |

### TS-013-15 — Três tentativas de e-mail (RN-610, CX-13)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limite de tentativas |
| **Passos** | Provedor falhando sempre; executar `EmailRetryJob` quatro vezes |
| **Resultado esperado** | Três tentativas com backoff crescente; **nenhuma quarta**; in-app intacta; log `ERROR`; métrica `email.exhausted` incrementada |

### TS-013-16 — Escopo por destinatário (§16)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que ninguém acessa notificação de terceiro |
| **Passos** | Como cada um dos 5 papéis, tentar ler, marcar e excluir notificação de outro usuário |
| **Resultado esperado** | `404 DEVTIME-2002` em **todos** os casos, incluindo `OWNER`; a notificação não aparece na listagem de nenhum outro usuário |

### TS-013-17 — Marcar todas como lidas isolado por usuário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o escopo da operação em lote |
| **Pré-condição** | Dois usuários com notificações não lidas |
| **Passos** | Usuário A executa `read-all`; verificar ambos |
| **Resultado esperado** | Todas as de A lidas; **nenhuma** de B alterada |

### TS-013-18 — Ausência de rota de criação (§14, CP-12)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que notificações só nascem de eventos |
| **Passos** | Enumerar as rotas expostas; tentar `POST /api/v1/notifications` |
| **Resultado esperado** | Nenhuma rota de criação; a tentativa retorna `404` ou `405` |

---

## 6. Testes temporais

### TS-013-19 — Lembrete de fechamento iminente (RN-605)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a janela de 3 dias |
| **Pré-condição** | `Clock` fixo; períodos com `endDate` a 4, 3 e 2 dias |
| **Passos** | Executar o job |
| **Resultado esperado** | Notificação apenas para o de 3 dias; reexecução no mesmo dia não duplica (idempotência por `dedupeKey`) |

### TS-013-20 — Lembrete de contrato terminando (RN-606)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a janela de 15 dias |
| **Passos** | Contratos com `endDate` a 16, 15 e 14 dias; executar o job |
| **Resultado esperado** | Notificação apenas para o de 15 dias; severidade `WARNING` |

### TS-013-21 — Purga por leitura (RN-609, CX-16, CX-17)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o limiar e a proteção das não lidas |
| **Pré-condição** | Notificações lidas há 89, 90 e 91 dias; uma não lida há 2 anos |
| **Passos** | Executar `NotificationCleanupJob` |
| **Resultado esperado** | Apenas a de 91 dias é removida; a de 90 **permanece** (limiar estritamente maior); a **não lida nunca é removida**, independentemente da idade |

### TS-013-22 — Idempotência dos jobs (CX-24)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a idempotência vem do `dedupeKey` |
| **Passos** | Executar cada um dos 4 jobs duas vezes seguidas e em duas instâncias simultâneas |
| **Resultado esperado** | Nenhum efeito duplicado; `@SchedulerLock` impede a segunda instância; nenhum job mantém controle próprio de "já executei hoje" |

---

## 7. Teste de coerência entre features

### TS-013-23 — Severidade coerente com `010-dashboard` (R-03, CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a tela e o e-mail não divergem |
| **Pré-condição** | Contratos com limiares `[50,80,100]` e `[70,90]` |
| **Passos** | Para cada contrato e cada faixa de consumo: 1. Consultar a `severity` do cartão em `GET /dashboard`. 2. Verificar qual limiar gerou notificação e com qual severidade. 3. Comparar |
| **Resultado esperado** | A severidade exibida no dashboard corresponde ao limiar notificado, em **todas** as combinações. Divergência significa um cliente recebendo alerta que a tela não mostra — e o usuário sem saber em qual confiar |

---

## 8. Testes de fluxo (SSE)

### TS-013-24 — Isolamento por destinatário (SG-03, R-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o fluxo é por usuário, não por tenant |
| **Pré-condição** | Dois usuários do mesmo tenant, ambos conectados |
| **Passos** | Criar notificação para o primeiro; observar os dois fluxos |
| **Resultado esperado** | Apenas o primeiro recebe o evento; o segundo não recebe nada; nenhum dado da notificação alheia é transmitido |

### TS-013-25 — Reconexão sem perda (ST-05, INV-NOT-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o fluxo nunca é o único canal |
| **Passos** | 1. Conectar. 2. Derrubar a conexão. 3. Criar três notificações. 4. Reconectar. 5. Verificar a central |
| **Resultado esperado** | Ao reconectar, o cliente **recarrega** histórico e contagem; as três notificações aparecem; nenhuma perdida. O cliente **não** assume que nada aconteceu durante a queda |

### TS-013-26 — Degradação sem fluxo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o funcionamento sem SSE |
| **Passos** | Bloquear o endpoint de fluxo; navegar pela aplicação |
| **Resultado esperado** | A contagem é atualizada em cada navegação; a central funciona integralmente; nenhuma funcionalidade é perdida, apenas a latência aumenta |

### TS-013-27 — Payload mínimo do evento (§23)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de `StreamEventDto` |
| **Passos** | Inspecionar o evento publicado no fluxo |
| **Resultado esperado** | Contém `id`, `type`, `severity`, `title` e `unreadCount`; **não** contém `body`, `payload` nem `dedupeKey` |

---

## 9. Testes de API

### TS-013-28 — Contrato dos 9 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `dedupeKey` **ausente** de toda resposta (CP-11); `availableTypes` presente nas preferências; erros em RFC 7807 |

### TS-013-29 — Preferências (§9 `notifications.md`)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a validação e o efeito |
| **Passos** | 1. Silenciar tipos válidos. 2. Enviar tipo inexistente. 3. Desligar e-mail globalmente. 4. Gerar notificações de tipos silenciados e não silenciados |
| **Resultado esperado** | (2) `422 DEVTIME-2000` sem alterar nada; (4) e-mail suprimido conforme a preferência, in-app criada em todos os casos |

---

## 10. Testes de frontend

### TS-013-30 — Sino global com contagem
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o componente global |
| **Passos** | Navegar entre telas; receber notificação pelo fluxo; marcar como lida |
| **Resultado esperado** | Contagem visível em todas as telas; atualizada pelo fluxo quando disponível; decrementada ao marcar como lida; anunciada por leitor de tela |

### TS-013-31 — Reconexão do cliente
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia da §21.3 |
| **Passos** | Derrubar a rede, aguardar, restabelecer |
| **Resultado esperado** | Reconexão com backoff; ao reconectar, **recarrega** contagem e listagem; após falhas repetidas, degrada para atualização por navegação |

### TS-013-32 — Preferências a partir de `availableTypes`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a ausência de lista fixa no cliente |
| **Passos** | Renderizar P28; adicionar um tipo novo no backend |
| **Resultado esperado** | A lista vem de `availableTypes`; um tipo novo aparece sem alteração no frontend |

### TS-013-33 — Escape de `title` e `body`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-09 |
| **Passos** | Notificação com `<script>` no título e no corpo; renderizar na central e no sino |
| **Resultado esperado** | Texto literal; nenhum script executado |

---

## 11. Testes E2E

### TS-013-34 — Jornada da central
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo do usuário |
| **Passos** | 1. Provocar um alerta de consumo. 2. Ver o sino atualizar. 3. Abrir P25. 4. Clicar e navegar à origem. 5. Marcar todas como lidas. 6. Excluir uma. 7. Silenciar o tipo em P28. 8. Provocar outro alerta do mesmo tipo |
| **Resultado esperado** | Cada etapa reflete o estado correto; (8) a in-app chega e nenhum e-mail é enviado |

---

## 12. Testes de performance

### TS-013-35 — Contagem de não lidas (§20.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta do endpoint mais chamado |
| **Pré-condição** | Usuário com 5.000 notificações, 3 não lidas |
| **Passos** | 10.000 chamadas medindo p95; inspecionar o plano de execução |
| **Resultado esperado** | **p95 < 50 ms**; index-only scan sobre `idx_notifications_unread` **parcial**; o índice contém 3 entradas, não 5.000 |

### TS-013-36 — Listagem e leitura em lote
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar as metas de listagem |
| **Passos** | Listar com 5.000 notificações; `read-all` com 5.000 não lidas |
| **Resultado esperado** | Listagem p95 < 200 ms com paginação; `read-all` < 500 ms em `UPDATE` em lote |

### TS-013-37 — Rajada de avaliações
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-07 |
| **Passos** | 1.000 avaliações do mesmo limiar em sequência rápida |
| **Resultado esperado** | Uma notificação criada; 999 rejeições resolvidas em microssegundos pelo índice; nenhum acúmulo de linhas; nenhuma degradação |

---

## 13. Testes de segurança

### TS-013-38 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-024 |
| **Passos** | Para cada um dos 9 endpoints, acessar notificação do tenant B autenticado no tenant A |
| **Resultado esperado** | `404 DEVTIME-2002`, nunca `403` |

### TS-013-39 — Conteúdo do e-mail (§19.1, R-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-15 |
| **Passos** | Gerar e-mail para os 11 tipos, em contexto com descrições e valores disponíveis; inspecionar o corpo renderizado |
| **Resultado esperado** | Nenhuma descrição de work log; nenhum valor monetário; nenhum dado além de nome de contrato, percentual e link. Este teste é o verificável de DoD-08 |

### TS-013-40 — Ausência de dado sensível em log (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-18 |
| **Passos** | Gerar, enviar e falhar notificações capturando os logs |
| **Resultado esperado** | Nenhum log contém `title`, `body`, `payload` ou endereço de e-mail em claro; presentes apenas ids, tipo, `dedupeKey` e traceId |

---

## 14. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-013-41 | Deduplicação (`TS-013-01`, `TS-013-04`) | **Toda** alteração em `NotificationService`, `DedupeKeyBuilder` ou no índice único |
| TS-013-42 | Coerência com `010` (`TS-013-23`) | Toda alteração em `notificationThresholds`, em `ConsumptionAlertPolicy` ou em `SeverityCalculator` de `010` |
| TS-013-43 | Independência in-app × e-mail (`TS-013-14`, `TS-013-15`) | Toda alteração em `EmailDispatchService` ou no provedor |
| TS-013-44 | Isolamento do fluxo (`TS-013-24`) | Toda alteração em `NotificationStreamService`, dos dois lados |
| TS-013-45 | Conteúdo do e-mail (`TS-013-39`) | Toda alteração em `NotificationTemplateRenderer` ou em tipo novo |
| TS-013-46 | Eventos após o commit (`TS-013-13`) | Todo consumidor de evento novo |
| TS-013-47 | Isolamento (`TS-013-38`) | Todo endpoint novo |

**Política:** `TS-013-01` roda em todo PR que toque esta feature. `TS-013-23` roda em todo PR que toque esta feature **ou** `010-dashboard` — a coerência entre a severidade da tela e a do e-mail é uma propriedade **entre** as duas features, e nenhuma delas isoladamente a garante.

**Regra adicional:** `TS-013-45` roda quando um tipo novo de notificação é adicionado. Um tipo novo cujo template inclua uma descrição de work log vazaria dado para provedor externo sem que nenhum teste existente falhasse.

---

## 15. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-601 | TS-013-01, TS-013-02, TS-013-05, TS-013-37 | AC-013-19, AC-013-25, AC-013-26, AC-013-31, AC-013-39 |
| RN-602 | TS-013-04, TS-013-06 | AC-013-01, AC-013-20, AC-013-21 |
| RN-603 | TS-013-03 | AC-013-01, AC-013-22, AC-013-26 |
| RN-604 | TS-013-07 | AC-013-02, AC-013-21 |
| RN-605 | TS-013-19 | AC-013-06, AC-013-31 |
| RN-606 | TS-013-20 | AC-013-07 |
| RN-607 | TS-013-09 | AC-013-03, AC-013-04, AC-013-08, AC-013-32 |
| RN-608 | TS-013-10, TS-013-14, TS-013-29 | AC-013-14, AC-013-27, AC-013-33 |
| RN-609 | TS-013-21 | AC-013-28, AC-013-29 |
| RN-610 | TS-013-15 | AC-013-34, AC-013-41 |
| RN-012 | TS-013-28 | AC-013-17 |
| RN-003 | TS-013-28 | AC-013-12 |
| RN-002 | TS-013-38 | AC-013-38 |
| INV-NOT-01 | TS-013-01, TS-013-05 | AC-013-19, AC-013-26, AC-013-39 |
| INV-NOT-02 | TS-013-10, TS-013-14 | AC-013-33, AC-013-34 |
| INV-NOT-03 | TS-013-02, TS-013-04 | AC-013-19, AC-013-20 |
| INV-NOT-04 | TS-013-25, TS-013-26 | AC-013-13, AC-013-40 |
| INV-NOT-05 | TS-013-14, TS-013-15 | AC-013-34, AC-013-41 |
| §6.1 matriz | TS-013-03, TS-013-12 | AC-013-01 a AC-013-08 |
| §6.2 ordem | TS-013-14 | AC-013-33, AC-013-34 |
| §6.3 oscilação | TS-013-04 | AC-013-20, AC-013-24 |
| §14 sem criação | TS-013-18 | AC-013-18 |
| §16 escopo | TS-013-16, TS-013-17 | AC-013-15, AC-013-35 |
| §19.1 conteúdo | TS-013-11, TS-013-39 | AC-013-37 |
| §23 payload do fluxo | TS-013-27 | AC-013-36 |
| CE-10 | TS-013-08 | AC-013-23 |
| CP-05 (coerência) | TS-013-06, TS-013-23 | AC-013-22 |
| CP-11 | TS-013-27, TS-013-28 | AC-013-36 |
| CP-16 / TX-06 | TS-013-13 | — |
| SG-03 | TS-013-24 | AC-013-35 |
| SG-07 | TS-013-37 | AC-013-19 |
| SG-09 | TS-013-33 | — |
| ST-05 | TS-013-25, TS-013-26 | AC-013-13, AC-013-40 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha nesta matriz.

---

## 16. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `dedupe-key-cases.csv` | Os 11 tipos da §6.1 com as chaves esperadas | `TS-013-03` — oráculo das chaves |
| `consumption-oscillation.csv` | Os 7 momentos da §6.3 com o resultado esperado | `TS-013-04` |
| `threshold-variants.csv` | Limiares `[50,80,100]`, `[70,90]`, `[100]` × faixas de consumo | `TS-013-06`, `TS-013-23` |
| `recipient-matrix.csv` | Tipo de evento × papéis presentes × destinatários esperados | `TS-013-09` |
| `fixture-tenant-roles` | Tenant com um de cada papel, mais um `ADMIN` suspenso | `TS-013-09`, `TS-013-16` |
| `fixture-two-owners` | Tenant com dois `OWNER` | `TS-013-05` |
| `fixture-contract-hourly-open` | Contrato `HOURLY_OPEN` | `TS-013-08` |
| `fixture-user-5k-notifications` | Usuário com 5.000 notificações, 3 não lidas | `TS-013-35`, `TS-013-36` |
| `fixture-clock-reminders` | `Clock` fixo a 4, 3, 2 dias e 16, 15, 14 dias | `TS-013-19`, `TS-013-20` |
| `fixture-clock-purge` | `Clock` fixo com leituras há 89, 90, 91 dias e uma não lida há 2 anos | `TS-013-21` |
| `fixture-email-provider-failing` | Provedor simulado que falha sempre | `TS-013-14`, `TS-013-15` |
| `fixture-context-with-sensitive` | Contexto com descrições de work log e valores monetários disponíveis | `TS-013-11`, `TS-013-39` |
| `fixture-tenant-b` | Segundo tenant com notificações espelhadas | `TS-013-38` |

**Regras de fixture:**
- `fixture-context-with-sensitive` é essencial: o teste de conteúdo do e-mail só prova algo se o dado sensível **estiver disponível** ao renderizador. Um teste em contexto vazio passaria trivialmente.
- `threshold-variants.csv` é compartilhado com `010-dashboard` (`severity-cases.csv`). É o que torna `TS-013-23` verificável — as duas features consomem a mesma tabela.
- Nenhuma fixture usa data relativa ao momento da execução.

---

## 17. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-013-01` foi escrita **antes** de `insertIgnoringDuplicate` |
| CC-02 | 100 avaliações simultâneas produzem exatamente uma notificação por destinatário |
| CC-03 | Nenhuma verificação de existência precede a inserção |
| CC-04 | As 11 chaves da §6.1 são reproduzidas exatamente |
| CC-05 | A sequência de oscilação da §6.3 produz exatamente 4 notificações |
| CC-06 | Nenhuma notificação é removida quando o consumo cai |
| CC-07 | Limiares vêm do contrato, provado com `[70,90]` e `[100]` |
| CC-08 | Coerência de severidade com `010-dashboard` provada em todas as combinações |
| CC-09 | In-app criada nos quatro cenários de supressão de e-mail |
| CC-10 | Falha de notificação não reverte o fechamento de período |
| CC-11 | Três tentativas de e-mail e nenhuma quarta |
| CC-12 | Fluxo isolado por destinatário, provado com dois conectados |
| CC-13 | Reconexão recarrega histórico e contagem |
| CC-14 | A aplicação funciona integralmente sem SSE |
| CC-15 | Nenhum papel acessa notificação de terceiro, incluindo `OWNER` |
| CC-16 | Nenhuma rota de criação existe |
| CC-17 | Purga remove lidas há mais de 90 dias e **nunca** não lidas |
| CC-18 | Jobs idempotentes pelo `dedupeKey`, sem controle próprio |
| CC-19 | Contagem de não lidas com p95 < 50 ms em 5.000 notificações, por índice parcial |
| CC-20 | Nenhum corpo de e-mail contém descrição ou valor monetário |
| CC-21 | Nenhum log contém conteúdo de notificação ou endereço de e-mail |
| CC-22 | Cobertura ≥ 95% em policies e ≥ 90% em services |
| CC-23 | Os 9 endpoints passam na suíte de isolamento com `404` |
