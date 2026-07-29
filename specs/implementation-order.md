# Ordem Oficial de Implementação — DevTime

## 1. Objetivo

Definir a **ordem canônica** de implementação das funcionalidades do DevTime, com dependências explícitas, prioridade, alocação em sprint, complexidade e estimativa. Nenhuma funcionalidade pode iniciar com uma dependência fora de `DONE`.

Este documento é a autoridade sobre **sequência**. `docs/07-backlog/mvp.md` é a autoridade sobre **escopo** e `docs/07-backlog/epics.md` sobre **agrupamento de valor**. Em caso de divergência de sequência, este documento prevalece; em caso de divergência de escopo, `mvp.md` prevalece.

---

## 2. Como ler as colunas

| Coluna | Significado |
|---|---|
| **Nº** | Identificador estável da pasta em `specs/`. **Não** é a ordem de execução |
| **Ordem** | Posição oficial na fila de implementação. Esta é a sequência a seguir |
| **Nome** | Funcionalidade |
| **Dependências** | Features que precisam estar `DONE` antes do início. `F0` = fundação técnica (sprint S1) |
| **Prioridade** | `P0` bloqueante do MVP · `P1` importante · `P2` cortável (§11.1 de `mvp.md`) |
| **Sprint** | Sprint de `docs/07-backlog/mvp.md` §6 |
| **Complexidade** | `Baixa` · `Média` · `Alta` · `Crítica` — risco técnico, não volume |
| **Estimativa** | Pontos de story agregados + dias-agente sequenciais estimados |

**Sobre complexidade `Crítica`:** significa que um erro na funcionalidade **destrói a confiança no produto** ou **causa falha de segurança**, não que seja difícil. Features críticas exigem duas aprovações em PR (PR-04) e testes escritos antes do código.

---

## 3. Pré-requisito: F0 — Fundação técnica (Sprint S1)

Não é uma feature de `specs/`, mas **bloqueia todas elas**. Corresponde a EP-01 e EP-03.

| Entregável | Referência | Critério de saída |
|---|---|---|
| Projeto Spring Boot 3 / Java 21 com estrutura de pacotes | `backend.md` §5 | ArchUnit verifica AR-01 a AR-09 |
| `BaseEntity`, `AuditListener`, UUIDv7, soft delete | `backend.md` §7.3 | Toda entidade herda e é auditada |
| `TenantContext`, `TenantContextFilter`, `TenantAwareInterceptor` | `backend.md` §7 | Teste prova isolamento entre dois tenants |
| Flyway com migration inicial | `database.md` | Migration do zero em banco limpo |
| `GlobalExceptionHandler` + `ErrorCode` + Problem Details | `backend.md` §12 | Toda exceção vira RFC 7807 |
| Projeto Angular standalone + Signals + PrimeNG + interceptors | `frontend.md` §5, §7.2 | Nenhum `NgModule`; `OnPush` em todos |
| Pipeline CI com todos os gates | `architecture.md` §11 | Gate falha quando deve falhar |
| Docker Compose com PostgreSQL 16 | `coding-guidelines.md` RE-03 | Ambiente sobe em até 3 comandos |

> **Regra F0-01:** nenhuma feature de `specs/` inicia antes de o teste de isolamento entre tenants estar verde. Isolamento retroativo é a classe de retrabalho mais cara do projeto (RP-05).

---

## 4. Ordem oficial

| Ordem | Nº | Nome | Dependências | Prioridade | Sprint | Complexidade | Estimativa |
|:--:|:--:|---|---|:--:|:--:|---|---|
| 1 | 001 | Authentication | F0 | P0 | S2 | Alta | 34 pts · 8 dias |
| 2 | 002 | Users & Tenant | 001 | P0 | S2 · S11 (auditoria) | Média | 26 pts · 6 dias |
| 3 | 005 | Categories | 002 | P0 | S3 | Baixa | 8 pts · 2 dias |
| 4 | 003 | Clients | 002 | P0 | S3 | Baixa | 13 pts · 3 dias |
| 5 | 004 | Contracts & Periods | 003, 005 | P0 | S3 · S4 | **Crítica** | 42 pts · 10 dias |
| 6 | 006 | Tags | 002 | P1 | S4 | Baixa | 5 pts · 1 dia |
| 7 | 007 | Tickets | 004, 005, 006 | P0 | S4 | Média | 29 pts · 7 dias |
| 8 | 008 | Work Logs | 007, 004, 005 | P0 | S5 | **Crítica** | 40 pts · 10 dias |
| 9 | 009 | Timer | 008 | P0 | S6 | **Crítica** | 34 pts · 8 dias |
| 10 | 011 | Bank Hours | 008, 004 | P0 | S7 · S10 | **Crítica** | 45 pts · 11 dias |
| 11 | 013 | Notifications | 011, 009, 007 | P1 | S8 | Média | 21 pts · 5 dias |
| 12 | 010 | Dashboard | 011, 008, 004 | P1 | S8 | Média | 21 pts · 5 dias |
| 13 | 012 | Reports & Export | 011, 008, 003 | P0 | S9 | Alta | 34 pts · 8 dias |
| 14 | 014 | Comments | 007, 002 | P2 | S11 | Baixa | 13 pts · 3 dias |
| 15 | 015 | Attachments | 014, 007 | P2 | S11 | Alta | 21 pts · 5 dias |

**Total MVP:** 386 pontos · ~92 dias-agente sequenciais.

> **Divergência intencional entre `Nº` e `Ordem`:** `011-bank-hours` (ordem 10) precede `010-dashboard` (ordem 12) porque o dashboard **consome** o cálculo de saldo. Construir o dashboard antes exigiria dados falsos e testes que não provam nada. A numeração de pasta permanece pela ordem de leitura conceitual; a fila de execução respeita as dependências reais.

---

## 5. Grafo de dependências

```mermaid
flowchart TD
    F0["F0 · Fundação técnica<br/>tenancy · auditoria · CI"]
    F0 --> A001["001 Authentication<br/>P0 · Alta"]
    A001 --> A002["002 Users & Tenant<br/>P0 · Média"]
    A002 --> A005["005 Categories<br/>P0 · Baixa"]
    A002 --> A003["003 Clients<br/>P0 · Baixa"]
    A002 --> A006["006 Tags<br/>P1 · Baixa"]
    A003 --> A004["004 Contracts & Periods<br/>P0 · CRÍTICA"]
    A005 --> A004
    A004 --> A007["007 Tickets<br/>P0 · Média"]
    A005 --> A007
    A006 --> A007
    A007 --> A008["008 Work Logs<br/>P0 · CRÍTICA"]
    A004 --> A008
    A008 --> A009["009 Timer<br/>P0 · CRÍTICA"]
    A008 --> A011["011 Bank Hours<br/>P0 · CRÍTICA"]
    A004 --> A011
    A011 --> A013["013 Notifications<br/>P1 · Média"]
    A009 --> A013
    A007 --> A013
    A011 --> A010["010 Dashboard<br/>P1 · Média"]
    A008 --> A010
    A011 --> A012["012 Reports & Export<br/>P0 · Alta"]
    A008 --> A012
    A003 --> A012
    A007 --> A014["014 Comments<br/>P2 · Baixa"]
    A002 --> A014
    A014 --> A015["015 Attachments<br/>P2 · Alta"]

    style A004 stroke:#c00,stroke-width:3px
    style A008 stroke:#c00,stroke-width:3px
    style A009 stroke:#c00,stroke-width:3px
    style A011 stroke:#c00,stroke-width:3px
```

**Verificação de aciclicidade:** o grafo é um DAG. A ordenação topológica da §4 é uma linearização válida. Nenhuma feature depende de outra de ordem superior.

---

## 6. Justificativa da ordem

| Decisão | Motivo | Alternativa rejeitada |
|---|---|---|
| `001` antes de tudo | Sem sessão autenticada não existe `TenantContext`; sem `TenantContext` nenhuma consulta é segura (ART-021) | Implementar CRUDs com tenant fixo e "plugar" auth depois — produz testes que não provam isolamento |
| `005 Categories` antes de `003 Clients` | Categoria é dependência de `WorkLog` (RN-104) e o seed de 9 categorias (RN-501) ocorre na criação do tenant, dentro de `002` | Deixar categorias para o fim — obrigaria a stub em `008` |
| `004` antes de `007` | Ticket exige contrato (RN-301) e sua `key` deriva de `contract.code` (RN-302) | — |
| `007` antes de `008` | Todo work log exige ticket (RN-101). Não existe registro avulso | Permitir work log sem ticket "temporariamente" — viola a regra mais estruturante do produto |
| `008` antes de `009` | O timer é apenas uma forma de capturar um work log; ele **reusa** integralmente as validações RN-102 a RN-120 (RN-159) | Implementar o timer com validações próprias — cria dois caminhos divergentes de regra |
| `011` antes de `010`, `012` e `013` | Saldo é a fonte dos números do dashboard, dos relatórios e dos limiares de alerta (RN-602) | Calcular saldo no dashboard — duplica a fórmula canônica |
| `013` depois de `011` | Alertas de consumo dependem de `consumedMinutes` e dos limiares por período (RN-603) | — |
| `012` depois de `011` | Relatório de período fechado é servido do snapshot (RN-701), produzido pelo fechamento em `011` | — |
| `015` depois de `014` | Anexo pode pertencer a comentário (INV-ATT-01), e o caminho de comentário é o mais simples para validar o upload | — |
| `014`/`015` por último | São `P2` e encabeçam a ordem de corte (§11.1 de `mvp.md`, itens 10) | — |

---

## 7. Alocação por sprint

| Sprint | Fase | Features | Marco | Objetivo verificável |
|:--:|:--:|---|---|---|
| S1 | F0 | — (fundação) | **M0** parcial | Isolamento entre tenants comprovado por teste |
| S2 | F0 | `001`, `002` (perfil, tenant) | **M0** | Ciclo completo de conta e sessão |
| S3 | F1 | `005`, `003`, `004` (CRUD + prévia) | — | Cliente e contrato criáveis com prévia de períodos |
| S4 | F1 | `004` (períodos), `006`, `007` | — | Períodos contíguos gerados; tickets funcionais |
| S5 | F1 | `008` | — | Registro manual com todas as validações |
| S6 | F1 | `009` | **M1** | Cronômetro resiliente · início do dogfooding |
| S7 | F2 | `011` (saldo, extrato, ajustes) | — | Banco de horas com extrato explicativo |
| S8 | F2 | `010`, `013` | — | Dashboard p95 < 800 ms e alertas de consumo |
| S9 | F3 | `012` | — | PDF e Excel entregáveis ao cliente |
| S10 | F3 | `011` (fechamento e snapshot) | **M2** | Fechamento atômico · início do beta fechado |
| S11 | F4 | `014`, `015`, `002` (auditoria) | — | Contexto do trabalho e rastreabilidade |
| S12 | F4 | Busca, filtros e estabilização | **M3** | Checklist do MVP 100% verde |

> **`011-bank-hours` ocupa duas sprints não contíguas.** S7 entrega cálculo, extrato e ajustes (EP-08). S10 entrega fechamento, snapshot e reabertura (EP-12). A separação é intencional: o fechamento congela relatórios, e relatórios (`012`, S9) precisam existir para que o congelamento seja verificável de ponta a ponta.

---

## 8. Caminho crítico

```mermaid
gantt
    title Caminho crítico do MVP (features bloqueantes P0)
    dateFormat YYYY-MM-DD
    axisFormat %b
    section Fundação
    F0 Fundação e tenancy         :crit, f0, 2026-08-03, 14d
    001 Authentication            :crit, a1, after f0, 8d
    002 Users e Tenant            :crit, a2, after a1, 6d
    section Núcleo
    005 Categories                :a5, after a2, 2d
    003 Clients                   :crit, a3, after a2, 3d
    004 Contracts e Periods       :crit, a4, after a3, 10d
    006 Tags                      :a6, after a2, 1d
    007 Tickets                   :crit, a7, after a4, 7d
    008 Work Logs                 :crit, a8, after a7, 10d
    009 Timer                     :crit, a9, after a8, 8d
    section Inteligência e entrega
    011 Bank Hours                :crit, a11, after a9, 11d
    013 Notifications             :a13, after a11, 5d
    010 Dashboard                 :a10, after a11, 5d
    012 Reports e Export          :crit, a12, after a11, 8d
    section Produtividade
    014 Comments                  :a14, after a12, 3d
    015 Attachments               :a15, after a14, 5d
```

**Caminho crítico:** `F0 → 001 → 002 → 003 → 004 → 007 → 008 → 009 → 011 → 012`.
Qualquer atraso nessas features atrasa o MVP integralmente. `005`, `006`, `010`, `013`, `014` e `015` possuem folga e podem ser paralelizados por um segundo agente.

### 8.1 Oportunidades de paralelização

| Janela | Agente A (caminho crítico) | Agente B (paralelo) | Condição |
|---|---|---|---|
| S3 | `003 Clients` → `004 Contracts` | `005 Categories`, `006 Tags` | Ambos dependem apenas de `002` |
| S4 | `004` (geração de períodos) | `007 Tickets` (CRUD e quadro) | `007` só integra com `004` ao final |
| S8 | `010 Dashboard` | `013 Notifications` | Ambos consomem `011`, sem se tocarem |
| S11 | `014 Comments` → `015 Attachments` | `002` (auditoria) | Áreas disjuntas |

**Regra de paralelização:** duas features só rodam em paralelo se **não compartilharem migration nem arquivo**. Compartilhamento de migration gera conflito de versão Flyway, cuja resolução é sempre manual e propensa a erro (ART-053: migration é imutável após merge).

---

## 9. Matriz de risco por feature

| Nº | Feature | Risco principal | Prob. | Impacto | Mitigação | Gatilho de acionamento |
|:--:|---|---|:--:|:--:|---|---|
| 001 | Authentication | Corrida de refresh entre abas; cookie bloqueado | Média | Alto | Fila única de refresh no interceptor; teste multi-aba | Falha em `TC-0010`–`TC-0042` |
| 002 | Users & Tenant | Último `OWNER` removido | Baixa | Alto | RN-455 testada em concorrência | Qualquer tenant sem OWNER |
| 003 | Clients | Validação de CPF/CNPJ | Baixa | Baixo | Casos-limite tabelados | — |
| 004 | Contracts & Periods | **Bordas de calendário na geração de períodos (RP-01)** | Alta | Crítico | Suíte temporal escrita **antes** do código | Qualquer falha em `TC-047xT` |
| 005 | Categories | Exclusão com work logs vinculados | Baixa | Médio | RN-505 exige categoria substituta | — |
| 006 | Tags | Normalização inconsistente | Baixa | Baixo | RN-506 com tabela de exemplos | — |
| 007 | Tickets | Sequência de `number` sob concorrência | Média | Médio | Geração atômica no banco | Duas chaves iguais |
| 008 | Work Logs | **Sobreposição não detectada (RN-102)** | Média | Crítico | Índice dedicado; teste de concorrência | Qualquer sobreposição persistida |
| 009 | Timer | **Perda de tempo trabalhado (RP-02)** | Alta | Alto | Estado 100% no servidor; RN-160 preserva o timer | Instabilidade no dogfooding |
| 010 | Dashboard | Desempenho com volume (RP-06) | Média | Médio | Índice coberto; teste de carga desde S8 | p95 > 800 ms |
| 011 | Bank Hours | **Erro de cálculo de saldo (RP-03)** | Média | Crítico | Determinismo testado; reconciliação; snapshot com checksum | Qualquer divergência reportada |
| 012 | Reports | Qualidade visual do PDF (RP-04) | Média | Alto | Spike SP-01 antecipado para S8 | Avaliação externa negativa |
| 013 | Notifications | Alerta duplicado ou ausente | Média | Médio | `dedupeKey` por limiar por período (RN-603) | Dois alertas do mesmo limiar |
| 014 | Comments | Menção a membro inativo | Baixa | Baixo | RN-813 filtra membros ativos | — |
| 015 | Attachments | **Arquivo malicioso liberado** | Baixa | Crítico | Allowlist + magic number + antivírus (RN-802/803) | EICAR liberado para download |

---

## 10. Regras de sequenciamento

| # | Regra |
|---|---|
| SQ-01 | Nenhuma feature inicia com dependência fora de `DONE`. "Quase pronto" não é `DONE` (CE-M-02) |
| SQ-02 | Feature de complexidade `Crítica` exige os testes escritos e revisados **antes** da implementação |
| SQ-03 | Feature de complexidade `Crítica` exige duas aprovações em PR (PR-04) |
| SQ-04 | Duas features em paralelo nunca compartilham migration nem arquivo |
| SQ-05 | Migration de uma feature é criada apenas quando a feature entra em `IN_PROGRESS`, evitando reserva de número |
| SQ-06 | Um endpoint só é exposto quando sua permissão está implementada e testada (CA-02 de `permissions.md`) |
| SQ-07 | Nenhum frontend de feature inicia antes de o backend correspondente estar `DONE` e o contrato OpenAPI publicado |
| SQ-08 | Corte de escopo segue exclusivamente a ordem da §11.1 de `mvp.md`; cortar fora de ordem exige registro de decisão |
| SQ-09 | Toda feature entrega backend, frontend, testes e documentação atualizada — não existe entrega "só backend" |
| SQ-10 | Divergência de saldo reportada em qualquer momento **bloqueia toda a fila** até a causa raiz ser corrigida (§11 de `mvp.md`) |

---

## 11. Features pós-MVP (`future/`)

Não entram na fila. Estão especificadas apenas na **fronteira**: o que precisa ser preservado hoje para que sejam viáveis amanhã.

| Nº | Nome | Fase | Épico | Depende de | Por que está fora do MVP |
|:--:|---|:--:|---|---|---|
| 016 | Teams (equipe e convites) | F5 | EP-16 | 002 | A persona primária opera sozinha |
| 017 | Permissions (granulares por contrato) | F5 | EP-16/EP-17 | 016 | RBAC fixo cobre o MVP |
| 018 | Subscriptions (planos e cobrança) | F6 | EP-18 | 016 | Validar valor antes de monetizar |
| 019 | Public API (chaves, escopos, webhooks) | F8 | — | 017 | Sem demanda validada |
| 020 | AI (sugestões e automações) | F7 | — | 008, 011, 012 | Exige base histórica de dados |

---

## 12. Rastreamento de progresso

| Nº | Feature | Spec | Tasks | Aceite | Testes | Status |
|:--:|---|:--:|:--:|:--:|:--:|---|
| 001 | Authentication | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 002 | Users & Tenant | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 003 | Clients | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 004 | Contracts & Periods | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 005 | Categories | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 006 | Tags | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 007 | Tickets | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 008 | Work Logs | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 009 | Timer | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 010 | Dashboard | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 011 | Bank Hours | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 012 | Reports & Export | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 013 | Notifications | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 014 | Comments | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |
| 015 | Attachments | ✅ | ✅ | ✅ | ✅ | `SPEC_APPROVED` |

> Atualizar a coluna **Status** é obrigatório no PR que conclui a feature (MN-03).

---

## 13. Casos especiais de sequenciamento

| # | Caso | Tratamento |
|---|---|---|
| CE-O-01 | Feature bloqueada por lacuna de documentação | Congelar a feature, seguir para a próxima **sem dependência** da bloqueada, reportar a lacuna |
| CE-O-02 | Dependência concluída mas com bug conhecido | A dependente não inicia. Corrigir primeiro — bug em base propaga para tudo acima |
| CE-O-03 | Duas features precisam da mesma migration | A de menor ordem cria a migration; a de maior ordem cria uma migration incremental |
| CE-O-04 | Feature `P2` atrasando o caminho crítico | Cortar imediatamente conforme §11.1 de `mvp.md` |
| CE-O-05 | Descoberta de dependência não prevista | Atualizar este documento **antes** de implementar; revalidar aciclicidade do grafo |
| CE-O-06 | Beta exige antecipação de feature de `future/` | Só antecipa se 3+ participantes pedirem e a ordem técnica não quebrar (CE-M-01) |
| CE-O-07 | Agente conclui feature antes do previsto | Puxa a próxima da fila cujas dependências estejam `DONE`, nunca uma fora de ordem por conveniência |

---

## 14. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Toda feature de `specs/` consta na tabela da §4 |
| CA-02 | O grafo de dependências é acíclico e a §4 é uma ordenação topológica válida |
| CA-03 | Toda dependência declarada existe como feature ou como F0 |
| CA-04 | Toda feature possui prioridade, sprint, complexidade e estimativa preenchidas |
| CA-05 | Toda feature `Crítica` possui risco, mitigação e gatilho na §9 |
| CA-06 | A alocação por sprint é consistente com §6 de `docs/07-backlog/mvp.md` |
| CA-07 | O caminho crítico está identificado e coerente com o grafo |

## 15. Dependências e impactos

| Documento | Relação |
|---|---|
| `docs/07-backlog/mvp.md` | Fornece sprints, marcos e ordem de corte |
| `docs/07-backlog/epics.md` | Fornece o agrupamento de valor e as estimativas por épico |
| `docs/07-backlog/stories.md` | Fornece as stories agregadas nas tarefas |
| `docs/00-overview/roadmap.md` | Define as fases F0–F8 |
| `specs/*/tasks.md` | Detalha cada feature em tarefas |

**Impacto:** alterar a ordem exige revalidar a aciclicidade do grafo, o caminho crítico, a alocação por sprint e as oportunidades de paralelização.
