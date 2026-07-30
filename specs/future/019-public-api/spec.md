# 019 — Public API & Webhooks *(pós-MVP)*

| Campo | Valor |
|---|---|
| **Feature** | 019 |
| **Épico** | EP-21 (API Pública e Webhooks) · EP-22 (Integrações) |
| **Fase** | F8 |
| **Depende de** | `017-permissions` |
| **Status** | `FUTURE` — não implementável (FU-01) |

> **Nenhum enunciado desta spec é normativo.** As regras da §6 são candidatas e não existem em `docs/02-domain/business-rules.md`. Implementá-las viola SP-01 e IA-01.

## 1. Objetivo

Expor o DevTime como plataforma: chaves de API com escopos, webhooks de eventos de domínio e integrações com ferramentas externas de gestão de tarefas.

## 2. Por que está fora do MVP

**Sem demanda validada.** Nenhuma persona de `docs/01-product/personas.md` pede integração. O freelancer que registra horas quer registrar horas — não conectar sistemas.

Há um segundo motivo, mais importante: **API pública é um contrato permanente**. Diferentemente de uma tela, que pode ser redesenhada, um endpoint público consumido por terceiros não pode mudar de forma. Publicar antes de o modelo de domínio estar estável significaria congelar decisões que o beta ainda pode mudar.

`docs/00-overview/roadmap.md` coloca esta feature em **F8**, a última fase — depois de equipe, permissões, cobrança e IA. A ordem não é acidental: cada uma dessas fases pode alterar o modelo, e a API deve refletir o modelo maduro.

## 3. Fronteira preservada hoje

| Item | Onde já existe | Estado no MVP |
|---|---|---|
| **API já é REST versionada em `/api/v1`** | `04-api/*` | Toda a superfície interna já tem contrato documentado |
| **OpenAPI gerado e sincronizado** | ART-111, DoD de cada feature | Contrato publicado e verificado por teste |
| **Problem Details (RFC 7807)** em toda resposta de erro | §12 `backend.md` | Implementado; formato de erro já é o de uma API pública |
| **Códigos de erro estáveis `DEVTIME-XXXX`** | §17 `business-rules.md` | Catálogo completo e versionado |
| `actorType` com `API_KEY` | §6.20 `entities.md` | **Já no enum** de `AuditLog`, sem uso |
| CE-P-08: job de sistema com `actorType = SYSTEM`, respeitando o tenant | §12 `permissions.md` | Padrão já estabelecido para ator não humano |
| Modelo de evolução para escopos de API | §14 `permissions.md` | Registra: *"chaves de API com escopos mapeiam para o mesmo catálogo de permissões"* |
| `Ticket.externalRef` | §6.12 `entities.md` | **Persistido**, sem uso — reservado para integração |
| Eventos de domínio publicados após o commit | §15 de cada spec | São exatamente os eventos que virariam webhooks |
| Paginação, filtro e ordenação na URL | ART-073, RN-012 | Padrão consistente em todos os endpoints |
| Toda listagem paginada com `size` máximo 100 | RN-012 | Proteção contra exaustão já normativa |

> **Consequência:** a API pública é, em grande parte, **abrir o que já existe**. Contrato versionado, erros padronizados, paginação consistente e eventos de domínio já estão no lugar. O que falta é autenticação por chave, escopos, limite de taxa e o compromisso de estabilidade.

## 4. O que não pode ser quebrado

**Esta é a única seção com efeito vinculante hoje.**

| # | Decisão a preservar | Consequência de quebrar |
|---|---|---|
| FR-01 | **Prefixo `/api/v1` em todos os endpoints** | Sem versão no caminho, publicar a v2 obrigaria a quebrar consumidores da v1 |
| FR-02 | **Códigos `DEVTIME-XXXX` estáveis e nunca reutilizados** | Um código reaproveitado com outro significado quebraria a lógica de tratamento de erro de todo consumidor |
| FR-03 | **Problem Details em toda resposta de erro** (EH-01) | Formato de erro inconsistente é o defeito mais citado em APIs públicas |
| FR-04 | **Nenhuma entidade JPA em assinatura de controller** (IA-09) | Expor a entidade acopla o contrato público ao modelo interno; qualquer refatoração viraria mudança de contrato |
| FR-05 | **DTOs de Request e Response são tipos distintos** | Reusar o mesmo tipo faz campos somente-leitura aparecerem como escrita no contrato |
| FR-06 | **Verificação por permissão atômica, nunca por papel** (FR-01 de `017`) | Escopos de chave mapeiam para permissões; verificar papel tornaria impossível conceder escopo a uma chave |
| FR-07 | **`actorType` com `API_KEY` no enum de `AuditLog`** | Toda ação por API precisa ser distinguível de ação humana na trilha; adicionar o valor depois exigiria migration de enum |
| FR-08 | **`@PreAuthorize` no service** (IMP-01) | A API pública é outro chamador dos mesmos serviços; proteção só no controller a deixaria desprotegida |
| FR-09 | **`Ticket.externalRef` persistido** | É o campo de correlação com sistema externo; removê-lo por ser código morto custaria migration |
| FR-10 | **Eventos de domínio publicados após o commit** (TX-06) | Webhook é entrega externa; publicar dentro da transação faria falha de rede reverter operação de negócio |
| FR-11 | **RN-012: `size` máximo de 100** | É a proteção contra exaustão que uma API pública mais precisa |
| FR-12 | **OpenAPI sincronizado no mesmo PR** (ART-111) | Um contrato público divergente da implementação é pior que ausência de contrato |
| FR-13 | **Filtro, paginação e ordenação na URL, não em corpo** | `GET` com corpo é incompatível com cache, com log de acesso e com boa parte dos clientes HTTP |
| FR-14 | **Toda operação de escrita é idempotente ou explicitamente não é** | Consumidor de API repete requisição por timeout de rede; operações sem garantia produzem duplicata |

## 5. Dependências

| Feature | Tipo | O que consome |
|---|---|---|
| `017-permissions` | **Bloqueante** | Escopos de chave mapeiam para o catálogo de permissões atômicas |
| `016-teams` | Bloqueante | Chave pertence a um tenant e atua com um conjunto de permissões |
| `013-notifications` | Bloqueante | Webhook é um **canal adicional** da mesma infraestrutura (OB-09 daquela spec) |
| `007-tickets` | Bloqueante | `externalRef` para correlação em integrações |
| `012-reports` | Bloqueante | `ReportExecution.filters` torna exportações reproduzíveis por API (OB-08 daquela spec) |
| Ferramentas externas | **Externo** | Jira, GitHub, Slack — decisão de `integrations.md` |

## 6. Regras preliminares — **candidatas, não normativas**

### 6.1 Chaves de API (EP-21)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-9xx?` | Chave pertence a um tenant, possui nome, escopos e data de expiração | Escopos são permissões atômicas existentes |
| `RN-9xx?` | O valor da chave é exibido **uma única vez**, na criação; apenas o hash é persistido | Mesmo padrão de `RefreshToken.tokenHash` (§6.19 `entities.md`) |
| `RN-9xx?` | Chave nunca recebe escopo além do que o criador possui | Impede escalonamento por chave |
| `RN-9xx?` | Toda ação por chave registra `actorType = API_KEY` e a identificação da chave | FR-07 |
| `RN-9xx?` | Revogação é imediata e irreversível | Sem janela de graça |
| `RN-9xx?` | Limite de taxa por chave, com resposta `429` e cabeçalhos de limite | Código novo necessário |
| `RN-9xx?` | Chave não acessa endpoints de autenticação nem de cobrança | Superfície mínima |

### 6.2 Webhooks (EP-21)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-9xx?` | Assinatura por segredo compartilhado em cabeçalho, permitindo verificar a origem | Padrão de mercado |
| `RN-9xx?` | Entrega com novas tentativas em backoff exponencial | Reusa o padrão de RN-610 |
| `RN-9xx?` | Cada entrega possui identificador único; o consumidor deduplica | Análogo ao `dedupeKey` de RN-601 |
| `RN-9xx?` | Endpoint que falha consistentemente é desativado, notificando o tenant | Evita fila infinita |
| `RN-9xx?` | Payload contém identificadores e o tipo do evento, **não** o dado completo | Reduz vazamento; o consumidor busca pela API |
| `RN-9xx?` | Eventos disponíveis são os já publicados internamente (§10.3 de `future.md`) | Nenhum evento novo é criado |

### 6.3 Integrações (EP-22)

| ID candidato | Enunciado candidato | Observação |
|---|---|---|
| `RN-9xx?` | Ticket externo é correlacionado por `externalRef`, sem duplicar o conteúdo | FR-09 |
| `RN-9xx?` | Sincronização é **unidirecional** para dentro, na primeira versão | Bidirecional exige resolução de conflito |
| `RN-9xx?` | Falha de integração nunca bloqueia operação de negócio | Coerente com TX-06 |

> Nenhum destes IDs existe. A faixa `RN-900`+ precisará ser criada em `business-rules.md` §3.2, provavelmente compartilhada com `018`.

## 7. Impacto nas features existentes

| Feature | O que muda | Aditivo? |
|---|---|---|
| `001-authentication` | Novo mecanismo de autenticação por chave, paralelo ao JWT | ✔ — caminho separado |
| `002-users` | Tela de gestão de chaves e de webhooks | ✔ |
| `007-tickets` | `externalRef` passa a ter uso; possível importação de tickets | ✔ |
| `012-reports` | Exportação solicitável por API; webhook de "relatório pronto" | ✔ |
| `013-notifications` | Ganha canal `WEBHOOK` ao lado de in-app e e-mail | ✔ — OB-09 daquela spec |
| Todas as features | Endpoints passam a ter consumidor externo; **contrato torna-se permanente** | ⚠ — mudança de compromisso, não de código |

> **O impacto mais significativo não é técnico.** Nenhuma feature precisa ser reescrita. O que muda é que o contrato de cada endpoint deixa de ser interno e passa a ser um compromisso com terceiros — e alterá-lo passa a exigir versionamento em vez de um PR.

## 8. Riscos de antecipação

| # | Risco de construir antes do tempo |
|---|---|
| RA-01 | **Contrato prematuro é permanente.** Publicar antes de o modelo estabilizar congela decisões que F5, F6 e F7 podem mudar. Um endpoint público de work log publicado antes de `017` teria que ganhar `approvalStatus` como mudança de contrato |
| RA-02 | **API pública amplia drasticamente a superfície de ataque.** Chave de longa duração é credencial mais exposta que sessão curta; vazá-la é mais fácil e o impacto é maior |
| RA-03 | **Limite de taxa sem dados de uso será errado.** Sem conhecer o padrão real, qualquer limite é arbitrário — e apertá-lo depois quebra integrações existentes |
| RA-04 | **Webhooks introduzem dependência de disponibilidade de terceiros.** Uma fila de entrega crescendo por endpoint indisponível é problema operacional novo |
| RA-05 | **Integração bidirecional exige resolução de conflito.** Um ticket alterado nos dois lados simultaneamente é um problema de convergência que nenhuma feature do MVP enfrenta |
| RA-06 | Suporte a integrador é qualitativamente diferente de suporte a usuário final: exige documentação, ambiente de teste e canal próprio |

## 9. Gatilho de priorização

| # | Sinal objetivo |
|---|---|
| GP-01 | **Demanda explícita e repetida** por integração, de ao menos 3 tenants distintos |
| GP-02 | Um tenant relatar que **deixou de adotar** o produto por ausência de integração com sua ferramenta atual |
| GP-03 | Fases F5, F6 e F7 concluídas — o modelo de domínio estabilizado |
| GP-04 | Base instalada suficiente para justificar o custo de manter um contrato público |

> **GP-03 é pré-condição, não gatilho.** Mesmo com demanda comprovada, publicar a API antes de `017-permissions` significaria expor endpoints cujos escopos ainda não existem, e antes de `018-subscriptions` significaria não ter como limitar o uso por plano.
>
> **A ordem F8 é deliberada.** Esta é a última feature do roadmap porque é a única cujo custo de erro é permanente: uma tela ruim se refaz, um contrato público quebrado se paga em confiança de integradores.
