# Backlog de Stories — DevTime

## 1. Objetivo

Catalogar todas as user stories do DevTime com estimativa, prioridade, dependências, tasks técnicas e critérios de aceite resumidos. É o inventário executável do produto, derivado de [`epics.md`](epics.md) e detalhado em [`01-product/user-stories.md`](../01-product/user-stories.md).

## 2. Escopo

| Dentro | Fora |
|---|---|
| Catálogo de stories com estimativa e prioridade | Narrativa completa das stories (`01-product/user-stories.md`) |
| Tasks técnicas por story | Sequenciamento em sprints (`mvp.md`) |
| Definition of Ready e critérios de estimativa | Épicos e features (`epics.md`) |
| Dependências entre stories | Casos de teste (`06-testing/test-cases.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Story point** | Unidade relativa de esforço, considerando complexidade, incerteza e volume. |
| **Definition of Ready (DoR)** | Condições para uma story entrar em execução. |
| **Task** | Trabalho técnico dentro de uma story. |
| **Bloqueador** | Story que impede o início de outra. |
| **Spike** | Investigação com resultado documentado, sem entrega funcional. |

### 3.1 Escala de estimativa

| Pontos | Significado | Referência |
|---|---|---|
| 1 | Trivial | Adicionar um campo opcional a um formulário existente |
| 2 | Simples | CRUD de entidade simples sem regra especial |
| 3 | Moderada | Endpoint com 3–5 validações de negócio |
| 5 | Complexa | Fluxo com máquina de estados ou cálculo |
| 8 | Muito complexa | Fechamento de período, motor de saldo |
| 13 | **Deve ser dividida** | Não entra em sprint sem decomposição |

**Regra:** nenhuma story acima de 8 pontos entra em sprint. Story de 13 pontos é obrigatoriamente decomposta.

### 3.2 Definition of Ready

| # | Condição |
|---|---|
| DoR-01 | A story segue o formato Como/Quero/Para e identifica a persona |
| DoR-02 | Os critérios de aceite estão escritos e são verificáveis |
| DoR-03 | As regras de negócio aplicáveis (`RN-XXX`) estão referenciadas |
| DoR-04 | Os fluxos alternativos e de exceção estão documentados |
| DoR-05 | As dependências estão resolvidas ou planejadas |
| DoR-06 | O contrato de API está especificado, quando aplicável |
| DoR-07 | A tela está especificada em `05-ui/pages.md`, quando aplicável |
| DoR-08 | A story foi estimada pela equipe |
| DoR-09 | Não há pergunta de negócio em aberto |

**Regra:** uma story que não atende a **todos** os itens não entra em sprint. Se DoR-09 falhar, a lacuna é registrada e a documentação é atualizada **antes** da implementação (ART-110).

---

## 4. Resumo do backlog

| Épico | Stories | Pontos | Fase |
|---|:--:|:--:|:--:|
| EP-01 Fundação | 5 | 26 | F0 |
| EP-02 Autenticação | 15 | 47 | F0 |
| EP-03 Multi-tenant | 4 | 21 | F0 |
| EP-04 Clientes | 10 | 24 | F1 |
| EP-05 Contratos | 20 | 71 | F1 |
| EP-06 Tickets e classificação | 20 | 54 | F1 |
| EP-07 Registro de horas e cronômetro | 30 | 98 | F1 |
| EP-08 Banco de horas | 15 | 55 | F2 |
| EP-09 Dashboard | 10 | 31 | F2 |
| EP-10 Notificações | 10 | 34 | F2 |
| EP-11 Relatórios | 15 | 63 | F3 |
| EP-12 Fechamento | 10 | 44 | F3 |
| EP-13 Comentários e anexos | 10 | 33 | F4 |
| EP-14 Busca e filtros | 6 | 17 | F4 |
| EP-15 Auditoria e preferências | 8 | 24 | F4 |
| **Total MVP** | **188** | **642** | **F0–F4** |
| EP-16 a EP-22 (pós-MVP) | 87 | 340 | F5–F8 |

---

## 5. EP-01 — Fundação Técnica

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-001 | Estruturar o monorepo com build de backend e frontend | 5 | P0 | — |
| US-002 | Subir o ambiente completo com Docker Compose | 5 | P0 | US-001 |
| US-003 | Configurar o pipeline com todos os gates de qualidade | 8 | P0 | US-001 |
| US-004 | Estabelecer a base de persistência e migrations | 5 | P0 | US-002 |
| US-005 | Criar o shell Angular com layout, roteamento e tema | 3 | P0 | US-001 |

**Tasks de US-003:**

| # | Task |
|---|---|
| T-01 | Workflow de build e teste |
| T-02 | Testcontainers em CI com reuso de contêiner |
| T-03 | Gate de cobertura (JaCoCo, 80% global / 90% em serviços) |
| T-04 | Gate de vulnerabilidades (OWASP Dependency-Check) |
| T-05 | Gate de arquitetura (ArchUnit) |
| T-06 | Verificação de cobertura de regras `RN-XXX` |
| T-07 | Scanner de segredos |
| T-08 | Publicação de artefatos e relatórios |

---

## 6. EP-03 — Base Multi-tenant

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-006 | Implementar `TenantContext` e filtro automático | 8 | P0 | US-004 |
| US-007 | Preencher `tenant_id` automaticamente na escrita | 3 | P0 | US-006 |
| US-008 | Criar a base reutilizável de testes de isolamento | 5 | P0 | US-006 |
| US-009 | Impedir referência cruzada entre organizações | 5 | P0 | US-007 |

**Tasks de US-006:**

| # | Task |
|---|---|
| T-01 | `TenantContext` com escopo de requisição |
| T-02 | `TenantContextFilter` extraindo do JWT |
| T-03 | `@FilterDef`/`@Filter` na `BaseEntity` |
| T-04 | Interceptor ativando o filtro em toda sessão |
| T-05 | Anotação `@CrossTenant` com justificativa obrigatória |
| T-06 | Regra ArchUnit verificando o uso de `@CrossTenant` |
| T-07 | Exceção em `TenantContext` vazio |

---

## 7. EP-02 — Autenticação e Conta

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-010 | Criar conta | 5 | P0 | US-006 |
| US-011 | Entrar no sistema | 5 | P0 | US-010 |
| US-012 | Verificar e-mail | 3 | P0 | US-010 |
| US-013 | Reenviar verificação | 2 | P1 | US-012 |
| US-015 | Renovar sessão automaticamente | 5 | P0 | US-011 |
| US-016 | Sair do sistema | 2 | P0 | US-011 |
| US-017 | Recuperar senha esquecida | 5 | P0 | US-011 |
| US-018 | Alterar a própria senha | 3 | P1 | US-011 |
| US-019 | Selecionar organização | 3 | P0 | US-011 |
| US-020 | Consultar e revogar sessões ativas | 3 | P2 | US-015 |
| US-021 | Editar o próprio perfil | 3 | P1 | US-011 |
| US-022 | Configurar a organização | 5 | P1 | US-011 |
| US-023 | Bloquear conta após tentativas falhas | 3 | P0 | US-011 |
| US-024 | Exportar todos os dados da organização | 3 | P2 | US-022 |
| US-014 | Aceitar convite | 5 | P1 | US-010 · **F5** |

**Tasks de US-011:**

| # | Task |
|---|---|
| T-01 | Endpoint de autenticação com resposta uniforme |
| T-02 | Emissão de JWT com validação de `iss`, `aud`, `exp` |
| T-03 | Refresh token opaco com hash persistido |
| T-04 | Cookie `HttpOnly` com escopo de path restrito |
| T-05 | Contagem de falhas e bloqueio temporário |
| T-06 | Execução de BCrypt em tempo constante para e-mail inexistente |
| T-07 | Rate limit por IP e e-mail |
| T-08 | Tela de login com estados e acessibilidade |

---

## 8. EP-05 — Gestão de Contratos

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-040 | Cadastrar contrato mensal de horas | 8 | P0 | US-030 |
| US-041 | Visualizar a prévia dos períodos antes de salvar | 5 | P0 | US-040 |
| US-042 | Configurar a política de transporte de saldo | 3 | P0 | US-040 |
| US-043 | Configurar a política de excedente | 3 | P0 | US-040 |
| US-044 | Ativar contrato gerando o primeiro período | 5 | P0 | US-040 |
| US-045 | Gerar períodos automaticamente por job | 8 | P0 | US-044 |
| US-046 | Ratear horas em períodos parciais | 5 | P0 | US-045 |
| US-048 | Listar contratos com indicador de consumo | 5 | P0 | US-044 |
| US-049 | Suspender e retomar contrato | 5 | P1 | US-044 |
| US-050 | Encerrar contrato | 5 | P1 | US-044 |
| US-051 | Cancelar contrato | 3 | P1 | US-044 |
| US-052 | Editar contrato respeitando períodos fechados | 5 | P1 | US-044 |
| US-053 | Duplicar contrato | 2 | P2 | US-040 |
| US-054 | Criar contrato de horas abertas | 3 | P1 | US-040 |
| US-055 | Visualizar o histórico de consumo do contrato | 5 | P1 | US-045 |
| US-056 | Configurar limiares de notificação por contrato | 2 | P2 | US-040 |
| US-057 | Excluir contrato em rascunho | 1 | P2 | US-040 |

**Tasks de US-045 (geração automática de períodos):**

| # | Task |
|---|---|
| T-01 | `PeriodGenerator` com cálculo de ciclos |
| T-02 | Rateio proporcional de período parcial |
| T-03 | Job diário com ShedLock e processamento por tenant |
| T-04 | Transição `SCHEDULED → OPEN` na data |
| T-05 | Truncamento por `endDate` |
| T-06 | Geração de períodos faltantes após retomada |
| T-07 | Constraint de exclusão contra sobreposição |
| T-08 | Suíte de testes temporais (§14 de `test-cases.md`) |

---

## 9. EP-07 — Registro de Horas e Cronômetro

### 9.1 Registro manual

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-081 | Registrar horas manualmente | 8 | P0 | US-060 |
| US-082 | Informar duração em formato flexível | 3 | P0 | US-081 |
| US-083 | Impedir sessões sobrepostas | 5 | P0 | US-081 |
| US-084 | Impedir sessões acima de 24 horas | 2 | P0 | US-081 |
| US-085 | Corrigir um registro | 5 | P0 | US-081 |
| US-086 | Calcular o tempo líquido automaticamente | 3 | P0 | US-081 |
| US-087 | Aplicar arredondamento configurado | 3 | P2 | US-086 |
| US-098 | Excluir um registro devolvendo o saldo | 3 | P0 | US-081 |
| US-099 | Marcar registro como não faturável | 2 | P0 | US-081 |
| US-100 | Duplicar um registro | 2 | P2 | US-081 |
| US-101 | Listar e filtrar registros | 5 | P0 | US-081 |
| US-102 | Registrar horas em nome de outro membro | 3 | P2 | US-081 |
| US-103 | Validar o registro em tempo real antes de salvar | 5 | P1 | US-081 |

### 9.2 Cronômetro

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-080 | Registrar horas com o cronômetro | 8 | P0 | US-081 |
| US-088 | Pausar e retomar o cronômetro | 5 | P0 | US-080 |
| US-089 | Manter o cronômetro visível em todas as telas | 3 | P0 | US-080 |
| US-090 | Recuperar o cronômetro após recarga ou reinício | 5 | P0 | US-080 |
| US-091 | Trocar de tarefa em uma única operação | 5 | P1 | US-080 |
| US-092 | Descartar o cronômetro com confirmação | 2 | P1 | US-080 |
| US-093 | Ser alertado sobre cronômetro longo | 3 | P0 | US-080 |
| US-094 | Recuperar cronômetro abandonado | 5 | P0 | US-093 |
| US-104 | Alterar ticket e categoria durante a execução | 3 | P1 | US-080 |
| US-105 | Encerrar o cronômetro de outro membro | 3 | P2 | US-080 · **F5** |

### 9.3 Calendário

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-095 | Visualizar as horas em calendário semanal | 5 | P1 | US-101 |
| US-096 | Identificar lacunas de tempo não registrado | 5 | P1 | US-095 |
| US-097 | Criar registro a partir de uma lacuna | 3 | P1 | US-096 |

**Tasks de US-080 (cronômetro):**

| # | Task |
|---|---|
| T-01 | Entidade `Timer` e `TimerPause` com índice único parcial |
| T-02 | Máquina de estados com todas as transições e guardas |
| T-03 | Endpoints de início, pausa, retomada, encerramento e descarte |
| T-04 | Geração de work log aplicando todas as validações |
| T-05 | Preservação do cronômetro em falha de validação (RN-160) |
| T-06 | `TimerStore` no frontend derivando do estado do servidor |
| T-07 | Barra global com estados visuais |
| T-08 | Ressincronização por intervalo, foco e reconexão |
| T-09 | Job de detecção de cronômetro longo e abandonado |
| T-10 | Testes de concorrência, resiliência e recuperação |

---

## 10. EP-08 — Banco de Horas

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-110 | Calcular o saldo do período | 8 | P0 | US-081 |
| US-111 | Recalcular o saldo a cada alteração | 5 | P0 | US-110 |
| US-112 | Garantir o determinismo do cálculo | 3 | P0 | US-110 |
| US-047 | Entender de onde vem o meu saldo (extrato) | 8 | P0 | US-110 |
| US-114 | Detalhar o consumo por categoria e ticket | 3 | P1 | US-047 |
| US-115 | Ver a projeção de consumo do período | 5 | P1 | US-110 |
| US-116 | Transportar saldo conforme a política | 8 | P0 | US-110 |
| US-117 | Impedir o transporte de saldo negativo | 2 | P0 | US-116 |
| US-118 | Expirar saldo transportado | 3 | P2 | US-116 |
| US-119 | Bloquear registro que estoura o saldo | 3 | P0 | US-110 |
| US-120 | Aplicar ajuste manual de saldo | 5 | P0 | US-110 |
| US-121 | Exigir justificativa em ajustes | 2 | P0 | US-120 |
| US-122 | Impedir alteração de ajustes | 1 | P0 | US-120 |
| US-123 | Estornar um ajuste | 2 | P1 | US-120 |
| US-124 | Exibir "indisponível" em falha de cálculo | 2 | P0 | US-110 |

**Tasks de US-110 (motor de cálculo):**

| # | Task |
|---|---|
| T-01 | `BalanceCalculator` puro e determinístico |
| T-02 | Índice coberto para agregação de consumo |
| T-03 | Atualização incremental dos campos desnormalizados |
| T-04 | Job noturno de reconciliação |
| T-05 | Cálculo de projeção com dias úteis do tenant |
| T-06 | Testes parametrizados de todas as combinações |
| T-07 | Teste de determinismo com 500 registros |

---

## 11. EP-11 e EP-12 — Relatórios e Fechamento

| ID | Story | Pontos | Prioridade | Depende de |
|---|---|:--:|:--:|---|
| US-145 | Gerar o relatório mensal do cliente | 8 | P0 | US-160 |
| US-146 | Escolher o agrupamento do relatório | 3 | P0 | US-145 |
| US-147 | Exportar em Excel | 5 | P0 | US-145 |
| US-148 | Exportar em PDF com identidade visual | 8 | P0 | US-145 |
| US-149 | Exportar em CSV | 2 | P2 | US-147 |
| US-150 | Gerar relatório consolidado por cliente | 5 | P1 | US-145 |
| US-151 | Gerar timesheet por intervalo livre | 5 | P0 | US-145 |
| US-152 | Gerar detalhamento por ticket | 3 | P1 | US-145 |
| US-153 | Marcar relatórios de períodos abertos como parciais | 3 | P0 | US-145 |
| US-154 | Processar exportações grandes de forma assíncrona | 8 | P1 | US-147 |
| US-155 | Baixar exportação por URL temporária | 3 | P0 | US-154 |
| US-156 | Restringir exportação de `MEMBER` aos próprios dados | 3 | P0 | US-145 |
| US-157 | Registrar toda exportação realizada | 2 | P1 | US-145 |
| US-160 | Fechar o período do contrato | 8 | P0 | US-116 |
| US-161 | Ver o resumo antes de fechar | 5 | P0 | US-160 |
| US-162 | Impedir fechamento com cronômetro ativo | 3 | P0 | US-160 |
| US-163 | Gerar snapshot imutável no fechamento | 8 | P0 | US-160 |
| US-164 | Travar os registros do período fechado | 3 | P0 | US-160 |
| US-165 | Reabrir período com justificativa | 5 | P1 | US-160 |
| US-166 | Impedir reabertura fora de ordem | 3 | P1 | US-165 |
| US-167 | Recuperar período preso em fechamento | 3 | P1 | US-160 |

**Tasks de US-163 (snapshot):**

| # | Task |
|---|---|
| T-01 | `SnapshotBuilder` montando o payload completo |
| T-02 | Serialização canônica e cálculo de SHA-256 |
| T-03 | Versionamento do formato (`schemaVersion`) |
| T-04 | Leitura de relatório a partir do snapshot |
| T-05 | Preservação de snapshots em reaberturas |
| T-06 | Teste de determinismo de regeração após 6 meses |

---

## 12. Demais épicos do MVP — resumo

| Épico | Stories | Destaques |
|---|---|---|
| **EP-04 Clientes** | US-030 a US-039 | CRUD, validação de documento, contatos, consumo consolidado, inativação com aviso |
| **EP-06 Tickets** | US-060 a US-079 | CRUD com chave legível, máquina de estados, quadro, categorias com seed, tags normalizadas |
| **EP-09 Dashboard** | US-125 a US-134 | Cards por criticidade, gráficos, estatísticas, dashboard pessoal para `MEMBER` |
| **EP-10 Notificações** | US-135 a US-144 | Alertas de limiar com deduplicação, central in-app, SSE, e-mail, preferências |
| **EP-13 Comentários e anexos** | US-170 a US-179 | Comentários com menções, anexos com antivírus, linha do tempo do ticket |
| **EP-14 Busca e filtros** | US-180 a US-185 | Busca global, filtros compostos persistidos na URL |
| **EP-15 Auditoria e preferências** | US-186 a US-193 | Trilha consultável, preferências de usuário, configurações do tenant |

---

## 13. Spikes necessários

| ID | Spike | Objetivo | Pontos | Fase |
|---|---|---|:--:|:--:|
| SP-01 | Geração de PDF | Avaliar biblioteca quanto a qualidade, desempenho e determinismo | 3 | F3 |
| SP-02 | SSE em produção | Verificar comportamento atrás de proxy e balanceador | 2 | F2 |
| SP-03 | Particionamento de auditoria | Definir estratégia de partições e arquivamento | 3 | F0 |
| SP-04 | Gateway de pagamento | Prova de conceito e ADR | 5 | F6 |
| SP-05 | Provedor de IA | Avaliar custo, latência e qualidade por capacidade | 5 | F7 |

**Regra:** todo spike produz uma ADR ou uma seção de documentação. Spike sem artefato documentado não é concluído.

---

## 14. Dívidas técnicas conhecidas

| ID | Dívida | Origem | Impacto | Prazo |
|---|---|---|---|---|
| DT-01 | Rate limiting em banco, não em Redis | Simplicidade no MVP | Contenção acima de 1.000 usuários | F6 |
| DT-02 | Eventos síncronos, não em fila | ADR-006 | Latência em cascatas longas | F6 |
| DT-03 | Exportação síncrona até 5.000 linhas | Simplicidade | Requisição longa | F3 (fallback já previsto) |
| DT-04 | Sem cache distribuído | ADR-006 | Recálculo repetido de dashboard | F6 |
| DT-05 | i18n com apenas pt-BR | Mercado inicial | Bloqueia internacionalização | F6 |
| DT-06 | Auditoria sem interface de exportação | Escopo | Consulta apenas por tela | F5 |

**Regra:** dívida técnica é registrada com origem, impacto e prazo. Dívida sem prazo é rejeitada — vira decisão permanente e deve virar ADR.

---

## 15. Critérios de priorização

| Critério | Peso | Descrição |
|---|:--:|---|
| Bloqueia o MVP | 40% | Sem isso não há produto |
| Realiza uma promessa da visão | 25% | `PV-XX` |
| Desbloqueia outras stories | 15% | Número de dependentes |
| Reduz risco crítico | 10% | Segurança, cálculo, perda de dados |
| Esforço | 10% | Menor esforço com igual valor vem antes |

**Regra de desempate:** vence a story que atende à persona primária (Rafael).

---

## 16. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-S-01 | Story estimada em 13 pontos | Decomposta obrigatoriamente antes da sprint |
| CE-S-02 | Story sem critério de aceite verificável | Não atende à DoR; retorna ao refinamento |
| CE-S-03 | Story com pergunta de negócio em aberto | Documentação atualizada primeiro (ART-110) |
| CE-S-04 | Story descoberta durante a implementação | Registrada no backlog; entra na sprint apenas se caber sem remover outra |
| CE-S-05 | Story bloqueada por dependência externa | Marcada como impedida; a equipe segue com stories independentes |
| CE-S-06 | Story cuja estimativa se mostrou muito errada | Reestimada; a causa é registrada na retrospectiva |
| CE-S-07 | Dívida técnica sem prazo | Rejeitada; deve virar ADR ou receber prazo |

## 17. Casos de erro do processo

| Situação | Consequência |
|---|---|
| Story em sprint sem atender à DoR | Removida da sprint |
| Story concluída sem atender à Definition of Done | Reaberta |
| Story implementada divergindo da documentação | Bug; a documentação prevalece (ART-110) |
| Regra de negócio decidida durante a implementação | Bloqueado; documentar primeiro |
| Story sem rastreabilidade a épico e requisito | Rejeitada no refinamento |

## 18. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Toda story possui identificador, estimativa, prioridade e épico |
| CA-02 | Toda story rastreia a um requisito e a uma persona |
| CA-03 | Nenhuma story acima de 8 pontos está em estado pronto |
| CA-04 | Toda dependência entre stories está declarada |
| CA-05 | Todo spike tem objetivo e artefato esperado |
| CA-06 | Toda dívida técnica tem origem, impacto e prazo |

## 19. Dependências e impactos

| Documento | Relação |
|---|---|
| `epics.md` | Agrupa estas stories em valor de negócio |
| `01-product/user-stories.md` | Detalha a narrativa e os fluxos |
| `01-product/requirements.md` | Fornece os critérios de aceite formais |
| `mvp.md` | Sequencia estas stories em sprints |
| `ai/definition-of-done.md` | Define quando uma story está concluída |

**Impacto:** alterar a prioridade de uma story pode invalidar o sequenciamento de sprints e exigir revisão do plano de fase.
