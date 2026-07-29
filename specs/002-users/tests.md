# 002 — Users & Tenant · Plano de Testes

## 1. Convenções

| Campo | Regra |
|---|---|
| **ID** | `TS-002-XX`, estável e imutável |
| **Objetivo** | O que o teste prova |
| **Pré-condição** | Estado necessário antes da execução |
| **Passos** | Ações numeradas e determinísticas |
| **Resultado esperado** | Verificação objetiva |

**ART-101:** o `@DisplayName` inicia com o identificador da regra — exemplo: `RN-455: rejeita a remoção do último OWNER ativo`.

## 2. Estratégia

| Tipo | Escopo | Ferramenta | Meta |
|---|---|---|---|
| Unitário | `TenantSettingsValidator`, `TimezoneValidator`, `LastOwnerGuard`, `SelfRoleChangeGuard`, `AuditLogWriter` | JUnit 5 + AssertJ | ≥ 95% |
| Integração | Service + Repository + PostgreSQL | Testcontainers | Todos os fluxos das §7 e §8 |
| API | Controller + serialização + segurança | `@WebMvcTest` | Os 17 endpoints |
| Isolamento | Tenancy | Suíte dedicada | Todos os endpoints |
| Frontend | Stores, formulário de `settings`, trilha | Jest + Testing Library + MSW | ≥ 90% em stores |
| E2E | Configuração, equipe, auditoria, exportação | Playwright | Jornadas principais |
| Performance | Auditoria e exportação | k6 | Metas da §20 |
| Segurança | Imutabilidade, isolamento, upload | JUnit + scripts | Vetores da §19 |
| Regressão | Suíte completa | CI | 100% verde |

---

## 3. Testes unitários

### TS-002-01 — Validação de `tenant.settings` (§6.2)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que toda chave valida faixa e que a validação cruzada é aplicada |
| **Passos** | Validar cada chave nos limites inferior−1, inferior, meio, superior e superior+1 |
| **Resultado esperado** | Aceitação apenas dentro da faixa; `DEVTIME-2000` fora dela |

| Chave | Rejeita | Aceita |
|---|---|---|
| `workDayMinutes` | 59, 1441, 0, negativo | 60, 480, 1440 |
| `workDays` | `[]`, `[0]`, `[8]`, duplicados | `[1,2,3,4,5]`, `[1..7]` |
| `timerLongRunningMinutes` | 29, 1441 | 30, 480, 1440 |
| `timerAutoAbandonMinutes` | valor ≤ `timerLongRunning`, 2881 | 960, 2880 |
| `retroactiveLimitDays` | −1, 366 | 0, 30, 365 |
| `roundingMinutes` | 7, 20, −5 | 0, 5, 6, 10, 15, 30 |
| `notificationThresholds` | `[0]`, `[201]`, 6 valores | `[50,80,100]`, `[100]` |

### TS-002-02 — Normalização de `notificationThresholds`
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ordenação e remoção de duplicatas |
| **Passos** | Enviar `[100,50,80,50]`, `[80]`, `[50,50,50]` |
| **Resultado esperado** | `[50,80,100]`, `[80]`, `[50]` |

### TS-002-03 — `TimezoneValidator` (INV-TEN-03)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas IDs IANA resolvíveis são aceitos |
| **Passos** | Validar `America/Sao_Paulo`, `UTC`, `America/Nao_Existe`, `GMT-3`, string vazia |
| **Resultado esperado** | Os dois primeiros aceitos; os demais rejeitados com `DEVTIME-2000` |

### TS-002-04 — `LastOwnerGuard` (RN-455)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a contagem correta de OWNERs ativos |
| **Passos** | Cenários: 1 OWNER ativo; 2 OWNERs ativos; 1 ativo e 1 suspenso; 1 ativo e 1 removido; 1 ativo e 1 convidado |
| **Resultado esperado** | Apenas OWNERs `ACTIVE` contam. Com contagem 1, toda operação sobre esse OWNER é bloqueada |

### TS-002-05 — `SelfRoleChangeGuard` (RN-456)
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum papel altera o próprio |
| **Passos** | Tentar auto-alteração como OWNER, ADMIN, MANAGER e MEMBER |
| **Resultado esperado** | `DEVTIME-2456` em todos os casos |

### TS-002-06 — `AuditLogWriter` — diff de campos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que apenas campos alterados entram em `beforeState`/`afterState` |
| **Passos** | Alterar 1 campo de 12; alterar 3; não alterar nenhum |
| **Resultado esperado** | 1 chave; 3 chaves; **nenhum** `AuditLog` gerado quando nada muda |

---

## 4. Testes de integração

### TS-002-07 — RN-455 por todos os caminhos
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar que nenhum caminho deixa o tenant sem OWNER |
| **Pré-condição** | Tenant com 1 OWNER ativo |
| **Passos** | 1. Remover o OWNER. 2. Suspender o OWNER. 3. Rebaixar o OWNER. 4. Desativar o usuário do OWNER |
| **Resultado esperado** | `409 DEVTIME-2455` em todos; o tenant permanece com 1 OWNER ativo |

### TS-002-08 — RN-455 sob concorrência
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o lock pessimista na contagem |
| **Pré-condição** | Tenant com 2 OWNERs ativos |
| **Passos** | Disparar simultaneamente o rebaixamento de ambos, 50 vezes |
| **Resultado esperado** | Em todas as execuções, exatamente um sucede e um recebe `409`; o tenant nunca fica sem OWNER |

### TS-002-09 — Remoção de membro preserva registros
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-458 e RN-460 |
| **Pré-condição** | Membro com 200 work logs, 5 tickets abertos, 1 timer `RUNNING`, 10 comentários |
| **Passos** | 1. Remover o membro. 2. Contar work logs e comentários. 3. Verificar o timer. 4. Verificar os tickets. 5. Consultar o saldo do período |
| **Resultado esperado** | 200 work logs e 10 comentários intactos; timer `DISCARDED`; 5 tickets reatribuídos ao OWNER; saldo inalterado |

### TS-002-10 — Alteração de `settings` não recalcula nada
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CX-07, CX-08 e ART-005 |
| **Pré-condição** | 50 work logs com `roundingMinutes = 0` e fuso `America/Sao_Paulo` |
| **Passos** | 1. Registrar os valores de `netMinutes` e `workDate`. 2. Alterar `roundingMinutes` para 15 e o fuso para `America/Manaus`. 3. Recomparar |
| **Resultado esperado** | Todos os 50 registros idênticos; saldo idêntico; um novo registro respeita as novas configurações |

### TS-002-11 — Auditoria de todas as ações da §18
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-006 |
| **Passos** | Executar cada uma das 10 ações auditadas e consultar `audit_logs` |
| **Resultado esperado** | Um registro por ação, com `action` correto, diff correto, `actorId`, `occurredAt` e `traceId`; gravado na mesma transação |

### TS-002-12 — Auditoria em rollback
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar atomicidade da auditoria |
| **Passos** | Forçar falha após a alteração e antes do commit |
| **Resultado esperado** | Nem a alteração nem o `AuditLog` persistem |

### TS-002-13 — Convite: emissão, reenvio e expiração
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-457 e CX-05/CX-06 |
| **Passos** | 1. Convidar e-mail novo. 2. Convidar membro ativo. 3. Convidar e-mail com membership `REMOVED`. 4. Reenviar. 5. Aceitar com o token antigo |
| **Resultado esperado** | (1) `201`; (2) `409 DEVTIME-2001`; (3) `201` com novo membership, preservando o anterior; (4) novo token; (5) `410 DEVTIME-2457` |

### TS-002-14 — Cancelamento do tenant
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo e as guardas |
| **Passos** | 1. Cancelar com senha errada. 2. Com nome errado. 3. Com período em `CLOSING`. 4. Com tudo correto |
| **Resultado esperado** | (1) `401`; (2) `400`; (3) `409 DEVTIME-2010`; (4) `202`, `CANCELLED`, tokens revogados, purga agendada |

### TS-002-15 — Exportação completa
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AQ-12 e o comportamento assíncrono |
| **Pré-condição** | Tenant com dados em todas as entidades |
| **Passos** | 1. Solicitar exportação. 2. Aguardar a conclusão. 3. Abrir o arquivo. 4. Varrer por campos sensíveis |
| **Resultado esperado** | `202` imediato; arquivo completo em formato aberto; sem `passwordHash` nem hash de token; `AuditLog` gravado |

### TS-002-16 — Invalidação de tokens ao alterar papel
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar IMP-04 |
| **Passos** | 1. Autenticar como MANAGER. 2. Alterar o papel para MEMBER. 3. Usar o access token anterior. 4. Fazer refresh |
| **Resultado esperado** | (3) `401`; (4) novo token com papel `MEMBER` e permissões correspondentes |

---

## 5. Testes de API

### TS-002-17 — Contrato dos 17 endpoints
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar aderência a `docs/04-api/users.md` |
| **Passos** | Executar todos os endpoints e comparar com o contrato |
| **Resultado esperado** | Campos, tipos e status coincidem; erros em RFC 7807 |

### TS-002-18 — Ausência de campos sensíveis
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-USR-02 |
| **Passos** | Varrer recursivamente todas as respostas |
| **Resultado esperado** | Nenhuma ocorrência de `passwordHash`, `password` ou `tokenHash`; IP sempre mascarado |

### TS-002-19 — Matriz de permissões desta feature
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CA-02 de `permissions.md` |
| **Passos** | Para cada endpoint, executar com OWNER, ADMIN, MANAGER, MEMBER e VIEWER |
| **Resultado esperado** | Concessão ou `403 DEVTIME-1101` exatamente conforme a matriz §7; `requiredPermission` presente na negação |

### TS-002-20 — Paginação da auditoria
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-012 e o default de período |
| **Passos** | 1. Consultar sem período. 2. Com `size = 500`. 3. Com `size = 100`. 4. Com intervalo de 5 anos |
| **Resultado esperado** | (1) últimos 30 dias, com o período aplicado indicado; (2) `400 DEVTIME-2006`; (3) `200`; (4) paginado, sem varredura total |

---

## 6. Testes de isolamento

### TS-002-21 — Isolamento por endpoint
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AQ-03 |
| **Passos** | Autenticado no tenant A, acessar por id direto membros, auditoria e configurações do tenant B |
| **Resultado esperado** | `404 DEVTIME-2002` em 100% dos casos; nunca `403` |

### TS-002-22 — Auditoria não vaza entre tenants
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o filtro automático em tabela particionada |
| **Passos** | Consultar `/audit-logs` com dados de dois tenants na mesma partição |
| **Resultado esperado** | Apenas registros do tenant da sessão; contagem e paginação também isoladas |

---

## 7. Testes de frontend

### TS-002-23 — Formulário de `settings` com avisos de impacto
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar OB-03 |
| **Passos** | Alterar `roundingMinutes` e `timezone` no formulário |
| **Resultado esperado** | Aviso explícito de que a alteração vale apenas para registros futuros, antes de salvar |

### TS-002-24 — Ações de membro condicionadas ao papel
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CA-07 de `permissions.md` |
| **Passos** | Renderizar P32 como OWNER, ADMIN, MANAGER e MEMBER |
| **Resultado esperado** | OWNER vê todas as ações; ADMIN não vê ações sobre OWNER; MANAGER e MEMBER não veem nenhuma ação de gestão |

### TS-002-25 — Cancelamento com dupla confirmação
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar SG-04 |
| **Passos** | Abrir `dt-danger-zone`, digitar nome errado, digitar nome correto sem senha, e ambos corretos |
| **Resultado esperado** | Botão habilitado apenas com nome exato e senha preenchida |

### TS-002-26 — Filtros da auditoria na URL
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CA-06 de `frontend.md` |
| **Passos** | Aplicar filtros, copiar a URL, abrir em nova aba, recarregar |
| **Resultado esperado** | Filtros e paginação preservados em todos os casos |

### TS-002-27 — Acessibilidade de P26–P33
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar AC-01 a AC-10 de `frontend.md` |
| **Passos** | axe-core em cada tela; navegação apenas por teclado |
| **Resultado esperado** | Zero violações; foco visível; erros anunciados por `aria-live` |

---

## 8. Testes E2E

### TS-002-28 — Jornada de configuração
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar o fluxo principal §7 |
| **Passos** | 1. Abrir P29. 2. Alterar nome, logo, fuso e 3 chaves de `settings`. 3. Salvar. 4. Verificar o reflexo na formatação de datas |
| **Resultado esperado** | Alterações persistidas; formatação atualizada sem recarga; trilha de auditoria com o registro correspondente |

### TS-002-29 — Jornada de equipe
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FA-06 a FA-09 |
| **Passos** | 1. Convidar. 2. Aceitar em outra sessão. 3. Alterar o papel. 4. Suspender. 5. Reativar. 6. Remover |
| **Resultado esperado** | Cada etapa reflete na lista; registros do membro removido continuam nos relatórios |

### TS-002-30 — Jornada de auditoria
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar FA-10 |
| **Passos** | 1. Executar 5 operações auditáveis. 2. Abrir P33. 3. Filtrar por entidade, ator e período |
| **Resultado esperado** | As 5 operações aparecem com antes e depois legíveis; filtros funcionam e são compartilháveis por link |

---

## 9. Testes de performance

### TS-002-31 — Consulta de auditoria com volume
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar a meta p95 < 500 ms |
| **Pré-condição** | 5 milhões de registros distribuídos em 24 partições mensais |
| **Passos** | Consultar 30 dias, 90 dias e por entidade específica |
| **Resultado esperado** | p95 < 500 ms em todos; o plano de execução usa *partition pruning* |

### TS-002-32 — Exportação com volume
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar CX-11 |
| **Pré-condição** | Tenant com 500k work logs |
| **Passos** | Solicitar a exportação e monitorar memória e tempo |
| **Resultado esperado** | `202` imediato; memória abaixo de 512 MB; conclusão em menos de 10 min; arquivo íntegro |

---

## 10. Testes de segurança

### TS-002-33 — Imutabilidade da auditoria
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar INV-AUD-01 |
| **Passos** | 1. Varrer todas as rotas por `PUT`/`PATCH`/`DELETE` em `audit-logs`. 2. Executar `UPDATE` e `DELETE` diretos com o usuário da aplicação. 3. Inspecionar a entidade |
| **Resultado esperado** | Nenhuma rota; operações rejeitadas pelo banco; entidade sem `updatedAt` nem `deletedAt` |

### TS-002-34 — Upload de avatar
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar RN-801 e RN-802 |
| **Passos** | Enviar SVG, executável renomeado, arquivo de 12 MB, PNG válido, EICAR |
| **Resultado esperado** | `415`, `415`, `413`, `200`, bloqueado pela verificação antivírus |

### TS-002-35 — Vazamento em log
| Campo | Conteúdo |
|---|---|
| **Objetivo** | Provar ART-084 |
| **Passos** | Executar a suíte capturando logs e varrer por e-mail, documento completo e hash |
| **Resultado esperado** | Zero ocorrências |

---

## 11. Testes de regressão

| ID | Objetivo | Gatilho |
|---|---|---|
| TS-002-36 | Suíte completa a cada PR que toque `tenant`, `user`, `membership` ou `audit` | Todo PR |
| TS-002-37 | RN-455 e RN-456 executadas a cada PR que toque autorização | Todo PR de segurança |
| TS-002-38 | Teste de imutabilidade da auditoria a cada migration | Toda migration |
| TS-002-39 | Teste de que nenhuma feature nova lê `tenant.settings` fora de `TenantSettingsService` (ArchUnit) | Todo PR |

---

## 12. Matriz de rastreabilidade

| Regra | Testes | Cenários de aceite |
|---|---|---|
| RN-004 | TS-002-17 | AC-002-18, AC-002-39 |
| RN-006 | TS-002-11, TS-002-12 | AC-002-01, AC-002-04, AC-002-06 |
| RN-007 | TS-002-19 | AC-002-19 |
| RN-008 | TS-002-14 | AC-002-10 |
| RN-011 | TS-002-17 | AC-002-15 |
| RN-012 | TS-002-20 | AC-002-21 |
| RN-455 | TS-002-04, TS-002-07, TS-002-08 | AC-002-11, AC-002-12, AC-002-37 |
| RN-456 | TS-002-05 | AC-002-13 |
| RN-457 | TS-002-13 | AC-002-05, AC-002-25 |
| RN-458 | TS-002-09 | AC-002-07, AC-002-38 |
| RN-460 | TS-002-09 | AC-002-07, AC-002-24 |
| RN-801/802 | TS-002-34 | AC-002-35 |
| INV-AUD-01 | TS-002-33 | AC-002-31 |
| INV-MEM-02 | TS-002-07, TS-002-08 | AC-002-11, AC-002-37 |
| INV-TEN-03 | TS-002-03 | AC-002-23 |
| INV-USR-02 | TS-002-18 | — |
| IMP-04 | TS-002-16 | AC-002-33 |
| ART-005 | TS-002-10 | AC-002-22, AC-002-23 |
| ART-024 | TS-002-21, TS-002-22 | AC-002-32 |
| ART-084 | TS-002-35 | — |
| AQ-12 | TS-002-15, TS-002-32 | AC-002-09, AC-002-27 |

---

## 13. Dados de teste

| Fixture | Conteúdo | Uso |
|---|---|---|
| `tenant-single-owner` | Tenant com exatamente 1 OWNER ativo | RN-455 |
| `tenant-two-owners` | Tenant com 2 OWNERs ativos | Concorrência RN-455 |
| `tenant-full-roles` | Um membro de cada papel | Matriz de permissões |
| `tenant-suspended` | Tenant `SUSPENDED` | RN-007 |
| `tenant-with-closing-period` | Período em `CLOSING` | CX-12 |
| `tenant-large` | 500k work logs, 5M registros de auditoria | Performance |
| `member-with-history` | 200 work logs, 5 tickets, 1 timer, 10 comentários | RN-458, RN-460 |
| `member-removed` | Membership `REMOVED` | CX-06 |
| `settings-boundary` | Conjunto com todos os valores nos limites | TS-002-01 |
| `avatar-samples` | PNG válido, SVG, executável renomeado, 12 MB, EICAR | TS-002-34 |

**Regra:** fixtures são criadas por builders de teste, nunca por SQL bruto — SQL contorna as invariantes de aplicação e produz estado que o sistema real jamais geraria.
