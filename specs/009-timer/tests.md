# 009 — Timer · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-009-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-160: preserva o timer quando a validação de sobreposição falha`.

> **SQ-02 — Ordem inegociável.** Complexidade **Crítica**. As suítes `TS-009-01` (preservação do timer) e `TS-009-02` (unicidade sob concorrência) são escritas, revisadas e **aprovadas** antes de qualquer linha de `TimerStateMachine` ou `TimerService.stop`.
>
> `TS-009-01` é a suíte mais importante: RN-160 quebra silenciosamente se o `stop` alterar o estado antes de chamar a validação. Escrita depois, ela refletiria a ordem que o código já usa; escrita antes, ela **define** essa ordem.
>
> **SQ-03:** duas aprovações obrigatórias no PR.

**Relógio:** todo teste temporal injeta um `Clock` fixo. Os limiares de 8h, 16h e 7 dias são inverificáveis com relógio real.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `TimerStateMachine`, `TimerElapsedCalculator`, `TimerPausePolicy`, `TimerToWorkLogAssembler`, `AbandonedTimerPolicy` | JUnit 5 + AssertJ | ≥ 95% |
| **Preservação** | RN-160 em cada regra de work log | JUnit parametrizado | Todas as regras de `008` |
| Integração | Service + Repository + constraints + PostgreSQL | Testcontainers | Ciclo de vida completo |
| **Concorrência** | RN-150, encerramento duplo, pausa concorrente | JUnit + `CountDownLatch` | Sem violação de invariante |
| Temporal | Limiares de 8h, 16h e 7 dias | JUnit + `Clock` fixo | 3 limiares |
| API | Controllers + serialização + permissões | `@WebMvcTest` | Os 11 endpoints |
| Isolamento | Tenancy + `@CrossTenant` justificado | Suíte dedicada | Todos os endpoints |
| Frontend | Store, cálculo local, multi-aba, painel de erro | Jest + Testing Library + MSW | ≥ 90% em store |
| E2E | Ciclo completo, multi-aba, reinício | Playwright | Jornada completa |
| Performance | `GET /current`, job de monitoramento | k6 | Metas da §20 |
| Segurança | Isolamento, campos forjados, privacidade das pausas | JUnit + scripts | Vetores da §19 |
| Regressão | Preservação e unicidade | CI | 100% verde |

---

## 3. Testes de preservação (RN-160)

### TS-009-01 — Timer preservado em toda falha de validação
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-160 para **cada** regra de work log que pode falhar no encerramento |
| **Pré-condição** | Um cenário de falha preparado para cada regra: RN-102, RN-103, RN-105, RN-114, RN-115, RN-116, RN-117, RN-118, RN-119, RN-120, RN-121, RN-231, RN-306 |
| **Passos** | Para cada regra: 1. Criar timer válido. 2. Preparar a condição de falha. 3. Encerrar. 4. Verificar o estado do timer, das pausas e do banco |
| **Resultado esperado** | Em **todos** os casos: erro específico da regra; timer com status **inalterado** (`RUNNING` permanece `RUNNING`, `PAUSED` permanece `PAUSED`); nenhuma pausa fechada indevidamente; `stoppedAt` e `workLogId` nulos; nenhum work log criado; a resposta contém o objeto `timer` |

> Esta é a suíte que sustenta PV-03 e mitiga RP-02. Cada linha corresponde a uma forma de o usuário perder horas trabalhadas.

### TS-009-02 — Ordem de validação preserva o estado (§6.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a descrição é validada **antes** de fechar a pausa |
| **Passos** | 1. Timer `PAUSED` com pausa aberta. 2. Encerrar **sem** descrição. 3. Inspecionar a `TimerPause` |
| **Resultado esperado** | `422 DEVTIME-2105`; a pausa permanece **aberta**; `accumulatedActiveSeconds` inalterado. Inverter a ordem exigiria desfazer o fechamento da pausa — o defeito que a §6.1 previne |

### TS-009-03 — Sugestão de correção por tipo de erro
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a acionabilidade da falha |
| **Passos** | Falhar o encerramento por sobreposição, saldo e contrato encerrado |
| **Resultado esperado** | Cada erro traz `suggestion` **específica**: ajustar o horário de início; marcar como não faturável ou pedir ajuste; mover o ticket. Nenhuma mensagem genérica |

---

## 4. Testes unitários

### TS-009-04 — Exemplo normativo de cálculo (§6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o cálculo canônico |
| **Pré-condição** | `Clock` fixo reproduzindo a sequência da §6.2 |
| **Passos** | Iniciar 09:00, pausar 10:30, retomar 11:00, encerrar 12:15:40 |
| **Resultado esperado** | `accumulatedActiveSeconds = 9.940`; `pausedMinutes = 30`; `grossMinutes = 195`; **`netMinutes = 165`**. O valor 165 vem de `gross − paused`, **não** de `accumulated` (que daria 165,67 → 166 se arredondado) |

### TS-009-05 — Valor canônico é `gross − paused` (CP-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a decisão de OB-02 |
| **Passos** | Construir 20 sequências de pausa e retomada em que `accumulated/60` diverge de `gross − paused` |
| **Resultado esperado** | Em **todos** os casos o work log usa `gross − paused`; a divergência máxima observada é de 1 minuto, conforme a nota de consistência de `business-rules.md` |

### TS-009-06 — Máquina de estados (§4.8)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar todas as transições e proibições |
| **Passos** | Para cada par (origem, destino), tentar a transição |
| **Resultado esperado** | Transições válidas executam; `COMPLETED → *` e `DISCARDED → *` retornam `409 DEVTIME-2010`; `pause` em `PAUSED` retorna `DEVTIME-2153`; `resume` em `RUNNING` retorna `DEVTIME-2155`. **Nenhuma** transição para `DISCARDED` por falha de validação (CP-02) |

### TS-009-07 — `TimerPausePolicy` (RN-154, RN-156, INV-TMR-02/03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a consistência das pausas |
| **Passos** | 1. Pausar e verificar a pausa aberta. 2. Retomar e verificar o fechamento. 3. Executar 50 ciclos. 4. Verificar `pausedMinutes` |
| **Resultado esperado** | `RUNNING` nunca tem pausa aberta (INV-TMR-03); `PAUSED` sempre tem exatamente uma (INV-TMR-02); após 50 ciclos, `pausedMinutes` é a soma exata e nenhuma pausa permanece aberta |

### TS-009-08 — `AbandonedTimerPolicy` (RN-165)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a janela de 7 dias |
| **Pré-condição** | `Clock` fixo |
| **Passos** | Recuperar timers abandonados há 1, 6, 7, 7 dias e 1 minuto, e 8 dias |
| **Resultado esperado** | Até 7 dias completos permitido; além disso `DEVTIME-2165` |

---

## 5. Testes de integração

### TS-009-09 — Ciclo de vida completo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o caminho feliz de ponta a ponta |
| **Passos** | Iniciar → pausar → retomar → editar → encerrar |
| **Resultado esperado** | Cada transição persiste corretamente; o work log gerado tem `source = TIMER` e `timerId` preenchido; `workLogId` no timer; auditoria em cada passo |

### TS-009-10 — Encerramento delega a `008` (RN-159, CP-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que **não** existe validação duplicada |
| **Passos** | 1. Inspecionar o código de `TimerService.stop` por chamadas de validação. 2. Alterar temporariamente uma regra em `008` e verificar o efeito no encerramento |
| **Resultado esperado** | Nenhuma validação de work log implementada nesta feature; a alteração em `008` reflete **imediatamente** no encerramento. Este teste é o que impede os dois caminhos de divergirem (`TS-008-19` é o seu par em `008`) |

### TS-009-11 — Troca de tarefa atômica (RN-166)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a atomicidade |
| **Passos** | 1. `?stopCurrent=true` com encerramento válido. 2. Com encerramento que falha por sobreposição. 3. Verificar o estado após cada uma |
| **Resultado esperado** | (1) o antigo `COMPLETED` com work log e o novo `RUNNING`; (2) **nada acontece** — o antigo permanece ativo, nenhum novo é criado, nenhum work log gerado |

### TS-009-12 — Descarte (RN-162)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a irreversibilidade e a auditoria |
| **Passos** | 1. Descartar sem `confirm`. 2. Com `confirm`. 3. Tentar operar o descartado. 4. Verificar a auditoria |
| **Resultado esperado** | (1) `422`, nada muda; (2) `DISCARDED`, `workLogId` nulo; (3) `DEVTIME-2010`; (4) `AuditLog` registra o **tempo descartado** em segundos |

### TS-009-13 — Recuperação de abandonado (RN-165)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a recuperação aplica todas as validações |
| **Passos** | Recuperar com `endedAt`: (a) válido; (b) gerando 30h; (c) sobreposto; (d) em período fechado; (e) anterior ao `startedAt` |
| **Resultado esperado** | (a) `COMPLETED` com work log; (b) `DEVTIME-2103`; (c) `DEVTIME-2102`; (d) `DEVTIME-2121` (CE-ME-04); (e) `DEVTIME-2114`. Em todas as falhas o timer permanece `ABANDONED` e recuperável |

### TS-009-14 — Encerramento forçado (OWN-05)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a notificação obrigatória |
| **Passos** | 1. `ADMIN` força o encerramento do timer de X. 2. Verificar o work log, a notificação e a auditoria. 3. `MANAGER` tenta o mesmo |
| **Resultado esperado** | (2) work log com `userId` de **X**, não do `ADMIN`; notificação a X informando quem encerrou; auditoria registra ambos; (3) `403 DEVTIME-1101` |

### TS-009-15 — Descarte por remoção de membro (RN-460)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o efeito cruzado |
| **Passos** | 1. Membro com timer `RUNNING`. 2. Remover o membro. 3. Repetir com timer `PAUSED` |
| **Resultado esperado** | Timer `DISCARDED` em ambos os casos (CE-ME-06); nenhum work log; `OWNER` notificado; tempo apenas em auditoria |

### TS-009-16 — Guardas consumidas por outras features
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `hasActiveForTicket` e `hasActiveForPeriod` |
| **Passos** | Com timer `RUNNING`, `PAUSED`, `COMPLETED`, `DISCARDED` e `ABANDONED`: tentar `DONE` no ticket (RN-311) e fechar o período (RN-240) |
| **Resultado esperado** | `RUNNING` e `PAUSED` bloqueiam ambos (CE-ME-01); `COMPLETED`, `DISCARDED` e `ABANDONED` não bloqueiam |

---

## 6. Testes temporais

### TS-009-17 — Limiar de timer longo (RN-163)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a notificação única |
| **Pré-condição** | `Clock` fixo; `timerLongRunningMinutes = 480` |
| **Passos** | 1. Job com 7h59. 2. Com 8h. 3. Job novamente com 9h, 10h e 12h |
| **Resultado esperado** | (1) nenhuma notificação; (2) uma notificação e `longRunningNotifiedAt` preenchido; (3) **nenhuma** notificação adicional |

### TS-009-18 — Limiar de abandono (RN-164)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a marcação e a ausência de work log |
| **Passos** | 1. Job com 15h59. 2. Com 16h em timer `RUNNING`. 3. Com 16h em timer `PAUSED` com apenas 2h ativas |
| **Resultado esperado** | (1) permanece ativo; (2) e (3) `ABANDONED`; **nenhum** work log gerado em nenhum caso; notificação com ação de recuperar. (3) confirma que o critério é `now − startedAt`, não tempo ativo (CX-07) |

### TS-009-19 — Expiração de abandonados
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o job de limpeza |
| **Passos** | Executar `AbandonedTimerCleanupJob` com abandonados de 6, 7 e 8 dias |
| **Resultado esperado** | Apenas o de 8 dias vai a `DISCARDED`; os demais permanecem recuperáveis; o job é idempotente |

### TS-009-20 — Idempotência dos jobs
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a segurança de reexecução |
| **Passos** | 1. Executar `TimerMonitorJob` duas vezes seguidas. 2. Em duas instâncias simultâneas. 3. Verificar o `@SchedulerLock` |
| **Resultado esperado** | Nenhuma notificação duplicada; nenhum abandono duplicado; a segunda instância não adquire o lock |

---

## 7. Testes de concorrência

### TS-009-21 — Cem inícios simultâneos (RN-150, SG-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o índice único garante INV-TMR-01 |
| **Pré-condição** | Nenhum timer ativo; 100 threads sincronizadas |
| **Passos** | 100 inícios simultâneos para o mesmo usuário |
| **Resultado esperado** | Exatamente **um** `201`; 99 `409 DEVTIME-2150`; um único timer ativo na base; a rejeição ocorre também pela constraint, não só pela verificação prévia |

### TS-009-22 — Unicidade entre tenants (CE-13, SG-04)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o índice **não** inclui `tenant_id` |
| **Pré-condição** | Usuário participando dos tenants A e B |
| **Passos** | 1. Iniciar timer no tenant A. 2. Trocar para B. 3. Tentar iniciar. 4. Tentar simultaneamente em A e B |
| **Resultado esperado** | (3) `409 DEVTIME-2150` informando o tenant do timer ativo; (4) apenas um criado. **Este teste falha se `tenant_id` estiver no índice** — é o seu propósito (CP-07) |

### TS-009-23 — Encerramento duplo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas um work log é gerado |
| **Passos** | Dois encerramentos simultâneos do mesmo timer |
| **Resultado esperado** | Um `201` com work log; outro `409 DEVTIME-2010`; **exatamente um** work log criado |

### TS-009-24 — Pausa e encerramento simultâneos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-TMR-02 e INV-TMR-03 sob corrida |
| **Passos** | Uma requisição pausa enquanto outra encerra |
| **Resultado esperado** | Uma aplicada; se o encerramento vencer, nenhuma pausa aberta permanece; se a pausa vencer, exatamente uma aberta; **nunca** um timer `COMPLETED` com pausa aberta |

---

## 8. Testes de API

### TS-009-25 — Contrato dos 11 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o contrato HTTP da §14 |
| **Passos** | Exercitar cada rota com payload válido e inválido |
| **Resultado esperado** | Status conforme a §14; `GET /current` retorna `204` sem timer ativo; `availableTransitions[]` presente; erros em RFC 7807 |

### TS-009-26 — Resposta de erro carrega o timer (CP-15)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `TimerStopErrorResponse` |
| **Passos** | Falhar o encerramento e inspecionar o corpo |
| **Resultado esperado** | O corpo contém `code`, `detail`, `suggestion` **e** o objeto `timer` completo com status atual — permitindo à UI manter o cronômetro visível sem segunda requisição |

### TS-009-27 — Matriz de permissões
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar cada célula aplicável (IMP-07) |
| **Passos** | Para cada operação × cada papel |
| **Resultado esperado** | `TIMER_USE`: `OWNER`, `ADMIN`, `MANAGER`, `MEMBER`; `VIEWER` recebe `403` (CE-P-06). `TIMER_VIEW_ANY`: `OWNER`, `ADMIN`, `MANAGER`. `TIMER_STOP_ANY`: **apenas** `OWNER` e `ADMIN` — `MANAGER` recebe `403` (OWN-05) |

---

## 9. Testes de frontend

### TS-009-28 — Nenhuma requisição por segundo (CP-08)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o cálculo local |
| **Passos** | Manter um timer visível por 5 minutos, contando as requisições |
| **Resultado esperado** | No máximo 5 requisições (*polling* de 60 s), não 300; o tempo exibido avança a cada segundo, calculado localmente a partir de `startedAt`, `lastResumedAt` e `accumulatedActiveSeconds` |

### TS-009-29 — Sincronização entre abas
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a estratégia da §21.3 |
| **Passos** | 1. Duas abas com timer `RUNNING`. 2. Pausar na aba A. 3. Observar a aba B. 4. Ocultar e reexibir a aba B. 5. Encerrar em outro dispositivo simulado |
| **Resultado esperado** | (3) B reflete a pausa imediatamente via `BroadcastChannel`; (4) revalidação em `visibilitychange`; (5) B converge em até 60 s pelo *polling* |

### TS-009-30 — Painel de erro mantém o cronômetro visível
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a materialização de RN-160 na UI |
| **Passos** | Falhar o encerramento por sobreposição |
| **Resultado esperado** | `dt-timer-error-panel` exibe o erro **e** a sugestão; o cronômetro continua visível e contando; nenhuma tela sugere que o tempo foi perdido; ações de "corrigir" e "tentar novamente" disponíveis |

### TS-009-31 — Diálogo de descarte exibe o tempo
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a fricção deliberada de RN-162 |
| **Passos** | Abrir o diálogo com 2h47 decorridas |
| **Resultado esperado** | O tempo a ser descartado é exibido de forma proeminente; a confirmação é explícita; cancelar não altera nada |

### TS-009-32 — Cronômetro oculto para `VIEWER`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar `hasPermission` no componente global |
| **Passos** | Renderizar o layout como `VIEWER` |
| **Resultado esperado** | `dt-timer-widget` ausente; a API também recusa (IMP-06) |

---

## 10. Testes E2E

### TS-009-33 — Ciclo completo com interrupções
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a resiliência prometida por RN-151 e RN-167 |
| **Passos** | 1. Iniciar. 2. Fechar o navegador. 3. Reabrir após 10 min. 4. Pausar. 5. Reiniciar o backend. 6. Retomar. 7. Encerrar |
| **Resultado esperado** | O tempo decorrido está correto em cada retomada; nenhum minuto perdido; o work log final reflete a sessão completa |

### TS-009-34 — Falha de encerramento e recuperação pelo usuário
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a jornada de RN-160 do ponto de vista humano |
| **Passos** | 1. Timer em ticket de contrato sem saldo (`BLOCK`). 2. Tentar encerrar. 3. Ler a sugestão. 4. Marcar como não faturável. 5. Encerrar novamente |
| **Resultado esperado** | (2) erro com cronômetro visível; (5) sucesso; **nenhum** minuto perdido entre as tentativas |

---

## 11. Testes de performance

### TS-009-35 — `GET /timers/current` sob carga
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta do endpoint mais chamado |
| **Pré-condição** | 10.000 timers históricos; 500 ativos |
| **Passos** | 10.000 chamadas medindo p95 |
| **Resultado esperado** | p95 < 100 ms; index scan sobre `uq_timers_active_user`; `204` para usuários sem timer é igualmente rápido |

### TS-009-36 — `TimerMonitorJob` em escala
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o job cabe na janela de 15 min |
| **Pré-condição** | 10.000 timers ativos distribuídos em 1.000 tenants |
| **Passos** | Executar medindo duração e memória |
| **Resultado esperado** | Conclusão em poucos segundos; processamento em lote; `TenantContext` trocado por iteração e **limpo** ao final de cada uma (CE-P-08) |

---

## 12. Testes de segurança

### TS-009-37 — Isolamento entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-024 |
| **Passos** | Acessar timer do tenant B autenticado no tenant A, por todos os endpoints com id |
| **Resultado esperado** | `404 DEVTIME-2002`, nunca `403` |

### TS-009-38 — `@CrossTenant` restrita e justificada (RS-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que a exceção de tenancy é mínima |
| **Passos** | 1. Inspecionar todas as consultas da feature. 2. Verificar que apenas `findActiveByUser` é `@CrossTenant`. 3. Verificar que ela retorna apenas o timer do **próprio** usuário |
| **Resultado esperado** | Exatamente uma consulta `@CrossTenant`, com justificativa em comentário (ART-023); ela nunca retorna timer de outro usuário, mesmo cross-tenant |

### TS-009-39 — Campos de sistema forjados (SG-05, SG-06)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que o tempo não é manipulável |
| **Passos** | Enviar `startedAt`, `lastResumedAt`, `accumulatedActiveSeconds`, `pausedMinutes`, `userId`, `stoppedAt` e `workLogId` em `POST` e `PATCH` |
| **Resultado esperado** | Todos ignorados; valores sempre do servidor. `startedAt` forjado é o vetor mais grave — permitiria inflar horas faturáveis |

### TS-009-40 — Privacidade das pausas (§19.1)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-12 |
| **Passos** | 1. `GET /timers/active` como `MANAGER`. 2. Inspecionar a resposta. 3. Verificar relatórios de `012` |
| **Resultado esperado** | Nenhum horário de pausa, quantidade de pausas ou descrição exposta; nenhum relatório inclui histórico de pausas |

### TS-009-41 — Ausência de texto livre em log (§28)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CP-16 |
| **Passos** | Executar o ciclo completo capturando os logs |
| **Resultado esperado** | Nenhum log contém `description` nem `reason` de pausa; falha de encerramento registrada em `WARN` com código e `elapsedSeconds` |

---

## 13. Testes de regressão

| ID | Alvo | Gatilho de execução |
|---|---|---|
| TS-009-42 | Preservação (`TS-009-01`) | **Toda** alteração em `TimerService.stop`, em `TimerStateMachine` ou em **qualquer regra de `008`** |
| TS-009-43 | Unicidade (`TS-009-21`, `TS-009-22`) | Toda alteração no índice único ou em `ActiveTimerPolicy` |
| TS-009-44 | Delegação a `008` (`TS-009-10`) | Toda alteração em validação de work log — é o que impede a divergência entre os caminhos |
| TS-009-45 | Limiares temporais (`TS-009-17`, `TS-009-18`) | Toda alteração no job ou nas configurações do tenant |
| TS-009-46 | Cálculo local no front (`TS-009-28`) | Toda alteração em `TimerStore` ou `dt-timer-display` |
| TS-009-47 | Isolamento (`TS-009-37`) | Todo endpoint novo |

**Política:** `TS-009-01` roda integralmente em todo PR que toque esta feature **ou** `008-worklogs`. Uma regra nova em `008` que não fosse coberta aqui criaria um caminho de perda de tempo trabalhado sem nenhum teste falhando.

---

## 14. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-150 | TS-009-21, TS-009-22 | AC-009-13, AC-009-24, AC-009-43 |
| RN-151 / RN-167 | TS-009-28, TS-009-33 | AC-009-10, AC-009-35 |
| RN-152 | TS-009-09 | AC-009-01 |
| RN-153 / RN-155 | TS-009-06 | AC-009-14, AC-009-15 |
| RN-154 / RN-156 | TS-009-07 | AC-009-02, AC-009-03 |
| RN-157 | TS-009-07 | AC-009-25, AC-009-26 |
| RN-158 | TS-009-01, TS-009-02 | AC-009-16 |
| RN-159 | TS-009-09, TS-009-10 | AC-009-04, AC-009-05 |
| **RN-160** | **TS-009-01, TS-009-02, TS-009-03, TS-009-26, TS-009-30, TS-009-34** | AC-009-16 a AC-009-19, AC-009-26, AC-009-31, AC-009-33 |
| RN-161 | TS-009-09 | AC-009-06 |
| RN-162 | TS-009-12, TS-009-31 | AC-009-07, AC-009-20 |
| RN-163 | TS-009-17 | AC-009-27 |
| RN-164 | TS-009-18 | AC-009-28, AC-009-29 |
| RN-165 | TS-009-08, TS-009-13, TS-009-19 | AC-009-09, AC-009-21, AC-009-30, AC-009-31 |
| RN-166 | TS-009-11 | AC-009-08, AC-009-32 |
| RN-460 | TS-009-15 | AC-009-37 |
| RN-311 / RN-240 | TS-009-16 | AC-009-36 |
| RN-002 | TS-009-37 | AC-009-38 |
| INV-TMR-01 | TS-009-21, TS-009-22 | AC-009-13, AC-009-43 |
| INV-TMR-02 / 03 | TS-009-07, TS-009-24 | AC-009-02, AC-009-03, AC-009-45 |
| INV-TMR-04 / 05 | TS-009-09, TS-009-12 | AC-009-04, AC-009-07 |
| §4.8 SM | TS-009-06, TS-009-23 | AC-009-22, AC-009-44 |
| §6.2 (valor canônico) | TS-009-04, TS-009-05 | AC-009-05, AC-009-34 |
| OWN-05 | TS-009-14, TS-009-27 | AC-009-11, AC-009-41 |
| §19.1 | TS-009-40, TS-009-41 | AC-009-42 |
| SG-05 / SG-06 | TS-009-39 | AC-009-39, AC-009-40 |
| RS-06 (`@CrossTenant`) | TS-009-38 | AC-009-24 |

**Critério de completude:** toda `RN-XXX` da §6 da spec possui ao menos uma linha. RN-160 possui **seis** suítes distintas, proporcional ao seu peso no risco RP-02.

---

## 15. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `timer-preservation-cases.csv` | Uma linha por regra de work log que pode falhar no encerramento, com o cenário de falha e o erro esperado | `TS-009-01` — oráculo de RN-160 |
| `timer-normative-sequence.csv` | A sequência da §6.2: 09:00 → 10:30 → 11:00 → 12:15:40 | `TS-009-04` |
| `timer-transition-matrix.csv` | Pares (origem, destino) com resultado esperado | `TS-009-06` |
| `fixture-timer-running` | Timer `RUNNING` com 2h decorridas | Base da maioria dos testes |
| `fixture-timer-paused` | Timer `PAUSED` com pausa aberta | `TS-009-02`, `TS-009-07` |
| `fixture-timer-abandoned-3d` | Abandonado há 3 dias | `TS-009-13` |
| `fixture-timer-abandoned-8d` | Abandonado há 8 dias | `TS-009-08`, `TS-009-19` |
| `fixture-user-two-tenants` | Usuário participando de dois tenants | `TS-009-22` |
| `fixture-contract-block-no-balance` | Contrato `BLOCK` sem saldo | `TS-009-01`, `TS-009-34` |
| `fixture-overlapping-worklog` | Work log que conflita com o intervalo do timer | `TS-009-01` |
| `fixture-period-closed` | Período fechado abrangendo o timer abandonado | `TS-009-13` |
| `fixture-clock-thresholds` | `Clock` fixo em 7h59, 8h, 15h59, 16h, 7 e 8 dias | `TS-009-17` a `TS-009-19` |
| `fixture-10k-active-timers` | 10.000 timers ativos em 1.000 tenants | `TS-009-36` |

**Regra de fixture:** `timer-preservation-cases.csv` deve ser **atualizado sempre** que uma regra nova entrar em `008-worklogs`. Uma regra de work log sem linha correspondente aqui é um caminho de perda de tempo trabalhado sem teste — registrado como gatilho de `TS-009-42`.

---

## 16. Critérios de conclusão

| # | Critério |
|---|---|
| CC-01 | `TS-009-01` e `TS-009-21` foram escritas e **revisadas** antes da implementação (SQ-02) |
| CC-02 | RN-160 provada para **todas** as regras de work log que podem falhar |
| CC-03 | A descrição é validada antes de fechar a pausa, comprovado por `TS-009-02` |
| CC-04 | Toda falha de encerramento traz sugestão específica |
| CC-05 | O work log usa `gross − paused`, provado em 20 sequências divergentes |
| CC-06 | 100 inícios simultâneos produzem exatamente um timer |
| CC-07 | O índice único comprovadamente **não** inclui `tenant_id` |
| CC-08 | Nenhuma validação de work log é reimplementada nesta feature |
| CC-09 | Limiares de 8h, 16h e 7 dias verificados com `Clock` fixo |
| CC-10 | Timer `PAUSED` bloqueia `DONE` do ticket e fechamento do período |
| CC-11 | O frontend não faz mais de uma requisição por minuto |
| CC-12 | Abas sincronizadas pelos três mecanismos da §21.3 |
| CC-13 | Cobertura ≥ 95% em `TimerStateMachine` e políticas; ≥ 90% em services |
| CC-14 | Os 11 endpoints passam na suíte de isolamento com `404` |
| CC-15 | Exatamente uma consulta `@CrossTenant`, justificada |
| CC-16 | Nenhum log contém `description` nem `reason` de pausa |
| CC-17 | Métricas de RP-02 (`timer.discarded.minutes`, `timer.abandoned.expired`) coletadas **antes** do início do dogfooding |
