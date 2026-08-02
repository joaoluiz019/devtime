# Backlog Futuro — DevTime

## 1. Objetivo

Especificar as funcionalidades pós-MVP (fases F5 a F8): equipe e permissões, aprovação e custos, planos e cobrança, portal do cliente, inteligência artificial, API pública e integrações. Cada item traz motivação, escopo, regras, riscos e pré-condições para entrar em desenvolvimento.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Funcionalidades das fases F5 a F8 | Escopo do MVP (`mvp.md`) |
| Pré-condições e critérios de entrada | Sequenciamento em sprints |
| Regras de negócio preliminares | Especificação executável (feita na fase correspondente) |
| Itens rejeitados e sua justificativa | Estratégia comercial |

## 3. Definições

| Termo | Definição |
|---|---|
| **Pré-condição** | Situação que deve ser verdadeira para o item entrar em desenvolvimento. |
| **Gatilho de priorização** | Sinal objetivo que indica que a funcionalidade se tornou necessária. |
| **Item rejeitado** | Funcionalidade avaliada e descartada, com justificativa registrada. |
| **Especificação preliminar** | Nível de detalhe suficiente para decidir, insuficiente para implementar. |

---

## 4. Visão geral

| Fase | Épicos | Objetivo | Segmento atendido |
|:--:|---|---|---|
| F5 | EP-16, EP-17 | Colaboração em equipe | Micro software house (Camila) |
| F6 | EP-18, EP-19 | Monetização e autoatendimento | Todos |
| F7 | EP-20 | Inteligência artificial assistiva | Todos |
| F8 | EP-21, EP-22 | Ecossistema e integrações | Usuários avançados |

```mermaid
flowchart LR
    MVP["MVP — F0 a F4<br/>Freelancer solo"] --> F5["F5 — Colaboração<br/>Equipe e permissões"]
    F5 --> F6["F6 — SaaS comercial<br/>Planos e portal"]
    F6 --> F7["F7 — IA<br/>Assistência inteligente"]
    F7 --> F8["F8 — Ecossistema<br/>API e integrações"]
```

---

## 5. EP-16 — Equipe e Permissões (F5)

### 5.1 Motivação

O produto já nasce multi-tenant (ART-001) e com RBAC completo (`permissions.md`). O que falta é a **superfície de gestão**: convidar, atribuir papéis, suspender e remover membros. Adiantar isso para o MVP aumentaria a superfície de teste sem entregar valor ao segmento primário.

### 5.2 Escopo

| Feature | Descrição | Regras |
|---|---|---|
| FT-057 | Convite por e-mail com papel definido | Expira em 7 dias; reenvio invalida o anterior (RN-457) |
| FT-058 | Gestão de papéis e suspensão | Sempre ao menos um `OWNER` ativo (RN-455); ninguém altera o próprio papel (RN-456) |
| FT-059 | Remoção preservando histórico | Registros preservados; tickets reatribuídos; cronômetro descartado (RN-458, RN-460) |
| FT-060 | Escopo de dados por membro | `MEMBER` vê apenas os próprios registros (§9 de `permissions.md`) |

### 5.3 Novidades de escopo

| Item | Descrição | Justificativa |
|---|---|---|
| Compartilhamento explícito de contrato | Tabela `contract_members` concedendo acesso a um `MEMBER` sobre contratos específicos | A definição implícita de "contrato vinculado" não cobre o caso de um novo membro que ainda não registrou horas |
| Visão consolidada de equipe | Horas por membro por contrato | JTBD-06 de Camila |
| Reatribuição em lote | Ao remover membro com muitos tickets | Evita 50 operações manuais |

### 5.4 Pré-condições

| # | Condição |
|---|---|
| PC-01 | MVP lançado e estável por 30 dias |
| PC-02 | Ao menos 5 usuários solicitando adicionar membros |
| PC-03 | Suíte de permissões cobrindo 100% da matriz atual |

### 5.5 Riscos

| Risco | Mitigação |
|---|---|
| Regressão de isolamento ao introduzir escopo por membro | Suíte de isolamento estendida antes da implementação |
| `MEMBER` sentindo-se vigiado | IDG-01 a IDG-05 são invioláveis: sem ranking, sem comparação, sem visão lateral de horas |
| Complexidade de reatribuição na remoção | Definir o destino no momento da remoção, com prévia do impacto |

---

## 6. EP-17 — Aprovação e Custos (F5)

### 6.1 Escopo

| Feature | Descrição |
|---|---|
| FT-061 | Fluxo de aprovação de horas antes do fechamento |
| FT-062 | Custo interno por membro e cálculo de margem |
| FT-063 | Relatório de rentabilidade por contrato e cliente |

### 6.2 Regras preliminares

| ID | Regra |
|---|---|
| RN-F-001 | A aprovação é **opcional por tenant**, desativada por padrão. Um freelancer solo nunca deve encontrar essa etapa |
| RN-F-002 | Registro com `approvalStatus = PENDING` consome saldo normalmente; a aprovação afeta apenas o fechamento |
| RN-F-003 | O fechamento com aprovação ativa exige que todos os registros estejam aprovados ou rejeitados |
| RN-F-004 | Registro rejeitado não entra no relatório e devolve o saldo, com justificativa obrigatória |
| RN-F-005 | Apenas `MANAGER`, `ADMIN` e `OWNER` aprovam; ninguém aprova o próprio registro |
| RN-F-006 | O custo interno é visível apenas para `OWNER` e `ADMIN` |
| RN-F-007 | A margem é calculada como `(valor de venda − custo) / valor de venda`; nunca exibida a `MEMBER` |

**Justificativa de RN-F-002:** se a aprovação bloqueasse o consumo, o saldo exibido durante o mês seria irreal — contrariando PV-02 (saldo sempre correto).

### 6.3 Gatilho de priorização

Três ou mais tenants com 5+ membros solicitando controle de aprovação.

---

## 7. EP-18 — Planos e Cobrança (F6)

### 7.1 Escopo

| Feature | Descrição |
|---|---|
| FT-064 | Definição de planos e limites |
| FT-065 | Assinatura recorrente e gestão de pagamento |
| FT-066 | Aplicação de limites por plano |
| FT-067 | Onboarding self-service com período de teste |

### 7.2 Estrutura preliminar de planos

| Plano | Membros | Clientes | Contratos ativos | Armazenamento | Retenção de histórico | Relatórios |
|---|:--:|:--:|:--:|:--:|:--:|---|
| Free | 1 | 3 | 3 | 100 MB | 6 meses | PDF simples |
| Pro | 1 | Ilimitado | Ilimitado | 5 GB | Ilimitada | Completos |
| Team | 10 | Ilimitado | Ilimitado | 20 GB | Ilimitada | Completos + margem |
| Business | Ilimitado | Ilimitado | Ilimitado | 100 GB | Ilimitada | Completos + API |

### 7.3 Regras preliminares

| ID | Regra |
|---|---|
| RN-F-010 | O DevTime **nunca** armazena dados de cartão; a captura ocorre no provedor |
| RN-F-011 | Falha de cobrança suspende o tenant apenas após 3 tentativas e 7 dias de carência |
| RN-F-012 | Tenant suspenso mantém leitura e exportação por 30 dias (RN-007) |
| RN-F-013 | Downgrade **nunca** exclui dados; apenas bloqueia a criação de novos itens acima do limite |
| RN-F-014 | Limite atingido retorna erro específico (`DEVTIME-1300`) com orientação de upgrade |
| RN-F-015 | Período de teste de 14 dias no plano Pro, sem exigir cartão |
| RN-F-016 | O cancelamento preserva os dados por 30 dias com exportação disponível |

**Justificativa de RN-F-013:** excluir dados por downgrade seria destruir o registro histórico de horas faturadas — inaceitável (ART-004) e potencialmente ilegal do ponto de vista fiscal.

### 7.4 Pré-condições

| # | Condição |
|---|---|
| PC-01 | ADR de gateway de pagamento aprovada, com prova de conceito (spike SP-04) |
| PC-02 | Ao menos 50 tenants ativos |
| PC-03 | Retenção de 3 meses acima de 50% |
| PC-04 | Disposição a pagar validada no beta (critério L-12 de `mvp.md`) |

---

## 8. EP-19 — Portal do Cliente (F6)

### 8.1 Escopo

Acesso somente leitura do cliente final aos próprios contratos.

| Feature | Descrição |
|---|---|
| FT-068 | Convite de contato do cliente com acesso restrito |
| FT-069 | Visão de saldo e consumo do próprio contrato |
| FT-070 | Download de relatórios de períodos fechados |

### 8.2 Regras preliminares

| ID | Regra |
|---|---|
| RN-F-020 | O portal é **opt-in por contrato**, não por tenant |
| RN-F-021 | O prestador escolhe o nível de detalhe: apenas totais, por ticket, ou detalhamento completo |
| RN-F-022 | O cliente **nunca** vê outros clientes, outros contratos, valores de custo ou dados de membros |
| RN-F-023 | O cliente vê apenas períodos **fechados**; períodos abertos permanecem invisíveis |
| RN-F-024 | O acesso do cliente é revogável a qualquer momento |
| RN-F-025 | Toda visualização do cliente é registrada em auditoria |

**Justificativa de RN-F-023:** expor um período aberto criaria expectativa sobre números que ainda podem mudar, gerando questionamentos sobre variações naturais do mês em curso — o oposto do valor de D-03.

**Justificativa de RN-F-021:** o controle permanece com quem presta o serviço (conflito CF-04 de `personas.md`). Alguns prestadores não querem expor a granularidade completa do próprio trabalho.

---

## 9. EP-20 — Inteligência Artificial (F7)

### 9.1 Princípio inviolável

> **A IA assiste, nunca decide (PR-07).** Nenhuma saída de IA altera dado de negócio sem confirmação humana explícita.

### 9.2 Capacidades

| Feature | Capacidade | Entrada | Saída | Guardrail |
|---|---|---|---|---|
| FT-071 | Resumo de período | Descrições e categorias dos registros | Texto executivo | Rascunho editável; nunca enviado sem revisão |
| FT-072 | Geração de tickets | Texto livre do usuário | Lista estruturada | Confirmação individual antes de criar |
| FT-073 | Estimativa de horas | Título, descrição e histórico de tickets similares | Faixa com intervalo de confiança | Exibida como sugestão; nunca preenche automaticamente |
| FT-074 | Detecção de inconsistências | Registros do período | Lista de apontamentos | Apenas sinaliza; nunca altera |

### 9.3 Detecção de inconsistências — catálogo

| Tipo | Descrição | Severidade |
|---|---|---|
| Lacuna de tempo | Intervalo longo sem registro em dia com atividade | Informação |
| Descrição genérica | "Ajustes", "Desenvolvimento", "Trabalho" sem contexto | Aviso |
| Descrição duplicada | Mesma descrição em muitos registros | Informação |
| Pico anômalo | Dia com volume muito acima da média pessoal | Aviso |
| Sessão suspeita | Duração exata e repetida (ex.: sempre 8:00 cravadas) | Aviso |
| Categoria incoerente | Descrição sugere categoria diferente da escolhida | Informação |
| Ticket sem progresso | Muitas horas sem mudança de status | Informação |

### 9.4 Regras preliminares

| ID | Regra |
|---|---|
| RN-F-030 | Nenhum dado é enviado a provedor de IA sem consentimento explícito por tenant, configurável e revogável |
| RN-F-031 | Dados de cliente (nome, documento, valores) são **removidos** antes do envio; apenas descrições de trabalho são enviadas |
| RN-F-032 | Orçamento mensal por tenant; ao esgotar, a funcionalidade fica indisponível com aviso claro |
| RN-F-033 | Respostas são cacheadas por hash da entrada |
| RN-F-034 | Toda saída de IA é visualmente sinalizada como gerada por IA |
| RN-F-035 | Falha do provedor degrada a funcionalidade, nunca o sistema |
| RN-F-036 | Nenhuma detecção de inconsistência gera notificação automática ao gestor sobre um membro específico |

**Justificativa de RN-F-036:** transformar a detecção em vigilância automatizada destruiria a confiança dos executores (persona Diego) e violaria NO-05. Os apontamentos são apresentados ao **próprio autor** dos registros e, de forma agregada e não nominal, ao gestor.

### 9.5 Pré-condições

| # | Condição |
|---|---|
| PC-01 | Base histórica de ao menos 6 meses e 100.000 registros |
| PC-02 | ADR de provedor aprovada, com custo por tenant projetado (spike SP-05) |
| PC-03 | Margem do plano suporta o custo de IA |
| PC-04 | Política de privacidade atualizada e consentimento implementado |

---

## 10. EP-21 — API Pública e Webhooks (F8)

### 10.1 Escopo

| Feature | Descrição |
|---|---|
| FT-075 | Chaves de API por tenant com escopos |
| FT-076 | Documentação pública e ambiente de testes |
| FT-077 | Webhooks assinados com entrega garantida |

### 10.2 Regras preliminares

| ID | Regra |
|---|---|
| RN-F-040 | Escopos derivam do **mesmo catálogo de permissões** do RBAC; não existe modelo paralelo |
| RN-F-041 | A chave é exibida uma única vez na criação; apenas o hash é persistido |
| RN-F-042 | Rate limit próprio por chave, independente do limite de usuário |
| RN-F-043 | `actorType = API_KEY` em toda auditoria gerada pela API |
| RN-F-044 | Política de depreciação de 12 meses para qualquer mudança incompatível |
| RN-F-045 | Webhooks usam padrão outbox: o evento é persistido na mesma transação do dado |
| RN-F-046 | Payload assinado com HMAC-SHA256; segredo por endpoint |
| RN-F-047 | 5 tentativas em até 24h; após esgotar, o endpoint é desabilitado e o tenant notificado |
| RN-F-048 | Destinos em faixas de IP privadas são bloqueados (proteção SSRF) |

### 10.3 Eventos de webhook

| Evento | Payload |
|---|---|
| `work_log.created` / `updated` / `deleted` | Registro e saldo resultante |
| `contract_period.threshold_crossed` | Contrato, período, limiar |
| `contract_period.closed` | Resumo do fechamento |
| `ticket.created` / `status_changed` | Ticket |
| `contract.status_changed` | Contrato |

---

## 11. EP-22 — Integrações (F8)

| Integração | Capacidades | Regra crítica |
|---|---|---|
| **GitHub / GitLab** | Vincular commit e PR a ticket; criar ticket a partir de issue; mover status ao mesclar | **Nunca** cria registro de horas automaticamente (GH-03) |
| **Jira** | Importar issue; sincronizar status bidirecionalmente | O DevTime é fonte de verdade das **horas**; o Jira, dos **status** (JR-02) |
| **Slack** | Comandos de cronômetro; consulta de saldo; notificações em canal | Toda ação passa pelas mesmas regras da API (SL-02) |
| **Calendário (Google/Outlook)** | Sugerir registro a partir de eventos com participantes | Sempre como sugestão; nunca cria registro automaticamente |

**Justificativa de GH-03 e da regra do calendário:** inferir horas trabalhadas a partir de commits ou de eventos de agenda violaria PR-03 (nunca inferir tempo a favor do prestador) e produziria dados não confiáveis para faturamento — exatamente o oposto do valor central do produto.

---

## 12. Itens rejeitados

| Item | Motivo da rejeição | Reavaliar se |
|---|---|---|
| Captura de screenshots | Contraria NO-05 e destrói a confiança do executor | Nunca |
| Monitoramento de teclado e mouse | Idem | Nunca |
| Rastreamento de localização | Idem; sem valor para o modelo de negócio | Nunca |
| Ranking de produtividade entre membros | IDG-02; time tracking punitivo destrói a adoção | Nunca |
| Registro automático de horas por atividade do computador | Viola PR-03; dados não faturáveis com segurança | Nunca |
| Emissão de nota fiscal | NO-01; complexidade regulatória por município | Se 30%+ dos usuários pedirem e houver parceiro de integração |
| Gestão de projetos com Gantt e sprints | NO-03; dilui o diferencial | Nunca |
| App mobile nativo | NO-07; web responsiva atende | Se o uso móvel ultrapassar 30% das sessões |
| Ponto eletrônico conforme legislação trabalhista | NO-04; exigências jurídicas fora do modelo | Nunca |
| CRM (funil, oportunidades) | NO-06; cliente existe como contraparte contratual | Nunca |
| Chat interno | Slack e ferramentas existentes atendem | Nunca |
| Divisão automática de sessão na virada do dia | RN-108 decidiu o contrário deliberadamente | Se o feedback indicar confusão recorrente |
| Arredondamento para cima | PR-03; cobraria tempo não trabalhado | Nunca |
| Timer que continua sozinho após inatividade | Registraria tempo não trabalhado | Nunca |
| **Liberação manual de anexo `FAILED`** | OB-02 de `specs/015-attachments`: converteria três camadas de defesa em uma caixa de diálogo, e quem clica em "liberar mesmo assim" não tem como avaliar o risco | Somente por alteração de RN-803 em `02-domain/business-rules.md` **antes** do código, com trilha de auditoria e restrição de papel |
| **Versionamento de anexo na aplicação** | RS-09 e §4 de `specs/015-attachments`: um novo anexo substitui na prática, e RN-011 torna todos os campos imutáveis. Não confundir com o versionamento **de objeto no storage** (SG-03 de `integrations.md`), que está implementado | Se surgir demanda de histórico de revisões de documento; exige revisar RN-011 e CP-13 |

> **Sobre a liberação manual de anexo `FAILED` (registrada em S11 por T-015-31).** É a decisão
> que mais provavelmente sofrerá pressão de usuário: alguém com um arquivo importante
> inacessível pedirá exceção. O ponto de OB-02 é que a exceção não é um caso particular — ela é
> um caminho, e um caminho que existe é usado. A alternativa oferecida ao usuário é reenviar o
> arquivo, o que reinicia a verificação e resolve o caso legítimo. Hoje **nenhum** parâmetro,
> papel ou configuração do sistema libera um arquivo não verificado, e existe teste de inspeção
> que falha se alguém acrescentar um.

**Regra:** um item marcado como "Nunca" só pode ser reconsiderado por ADR que emende explicitamente o não-objetivo ou princípio correspondente.

---

## 13. Critérios de entrada para desenvolvimento

Nenhum item deste documento entra em desenvolvimento sem atender a **todos** os critérios abaixo:

| # | Critério |
|---|---|
| CE-01 | A fase anterior está concluída com todos os critérios de saída atendidos |
| CE-02 | As pré-condições específicas do item foram atingidas |
| CE-03 | Existe especificação completa nos documentos de `02-domain/`, `03-architecture/`, `04-api/` e `05-ui/` |
| CE-04 | As regras preliminares `RN-F-XXX` foram convertidas em regras definitivas `RN-XXX` |
| CE-05 | Os riscos foram avaliados e mitigados |
| CE-06 | O item não viola nenhum não-objetivo da visão nem princípio de produto |
| CE-07 | Existe demanda validada, não apenas suposta |

---

## 14. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-F-01 | Usuário pede funcionalidade rejeitada | Explicar a decisão e o não-objetivo correspondente; oferecer a alternativa mais próxima |
| CE-F-02 | Concorrente lança uma funcionalidade rejeitada | Reavaliar **o problema**, não a solução; a rejeição pode continuar correta |
| CE-F-03 | Funcionalidade futura se mostra bloqueante para o MVP | Reavaliar o escopo do MVP com registro da decisão |
| CE-F-04 | Regra preliminar conflita com regra existente | A regra existente prevalece; a preliminar é reescrita |
| CE-F-05 | Fase antecipada por demanda de mercado | Exige que a ordem técnica de `roadmap.md` §5 seja respeitada |
| CE-F-06 | Cliente grande exige funcionalidade fora do roadmap | Avaliar como o item afeta os demais; nunca construir funcionalidade de cliente único no produto principal |

## 15. Casos de erro do processo

| Situação | Consequência |
|---|---|
| Item futuro implementado sem especificação completa | Rejeitado na revisão |
| Regra `RN-F-XXX` implementada sem virar `RN-XXX` | Bloqueado (ART-112) |
| Item rejeitado implementado sem ADR | Revertido |
| Fase iniciada com a anterior incompleta | Retrabalho previsível; bloqueado |

## 16. Critérios de aceite deste documento

| # | Critério |
|---|---|
| CA-01 | Todo item futuro tem motivação, escopo, regras preliminares e pré-condições |
| CA-02 | Todo item rejeitado tem justificativa e condição de reavaliação |
| CA-03 | Nenhuma regra preliminar contradiz uma regra existente |
| CA-04 | Todo item respeita os princípios de produto e não-objetivos da visão |
| CA-05 | Todo item declara seu gatilho de priorização objetivo |

## 17. Dependências e impactos

| Documento | Relação |
|---|---|
| `00-overview/vision.md` | Fornece os não-objetivos que sustentam as rejeições |
| `00-overview/roadmap.md` | Define as fases destes itens |
| `epics.md` | Fornece os épicos EP-16 a EP-22 |
| `mvp.md` | Define o que ficou fora e veio para cá |
| `02-domain/permissions.md` | Fornece o modelo que EP-16 e EP-21 estendem |

**Impacto:** antecipar um item exige revisão do roadmap, verificação da ordem técnica obrigatória e conversão das regras preliminares em definitivas antes de qualquer implementação.
