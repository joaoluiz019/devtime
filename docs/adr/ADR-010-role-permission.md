# ADR-010 — RBAC com papéis fixos e permissões derivadas em tempo de execução

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-082`. Complementa [ADR-008](ADR-008-jwt.md) (TK-03) e [ADR-001](ADR-001-multi-tenant.md).

## Data

2026-07-29

## Contexto

O DevTime precisa autorizar operações considerando três eixos simultâneos:

| Eixo | Pergunta | Exemplo |
|---|---|---|
| **Papel** | O papel permite a ação? | `MEMBER` pode criar work log? |
| **Tenant** | O recurso pertence ao tenant da sessão? | O contrato é deste tenant? |
| **Ownership / escopo** | O recurso pertence ao usuário, ou ele tem vínculo com o dado? | `MEMBER` só edita o próprio work log |

Restrições e características do domínio:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Autorização em duas camadas obrigatórias: papel **e** pertencimento ao tenant | `ART-082` |
| R-02 | Um usuário tem papéis **diferentes** em tenants diferentes | `entities.md` (`memberships`) |
| R-03 | Rebaixamento de papel deve ter efeito quase imediato | IMP-04 |
| R-04 | O escopo de dados de `MEMBER` é assimétrico: vê todos os tickets, mas apenas os próprios work logs | `permissions.md` §7 nota ² |
| R-05 | Permissões granulares por usuário são funcionalidade planejada para F5, não para o MVP | `roadmap.md`, `specs/future/017-permissions` |
| R-06 | Acesso a recurso de outro tenant retorna `404`, nunca `403` | `ART-024` |

## Decisão

| # | Regra |
|---|---|
| RB-01 | O modelo é **RBAC com papéis fixos**: `OWNER`, `ADMIN`, `MANAGER`, `MEMBER`, `VIEWER`. O conjunto de papéis é fechado e só muda por ADR. |
| RB-02 | O papel é atribuído ao **`Membership`** (par usuário × tenant), nunca ao usuário. |
| RB-03 | As permissões são **atômicas**, nomeadas `RECURSO_AÇÃO` (ex.: `WORKLOG_CREATE`), e a matriz papel × permissão é **normativa** em `docs/02-domain/permissions.md` §7. |
| RB-04 | A permissão é **derivada do papel a cada requisição** (`RolePermissions.of(role)`), nunca lida do token (TK-03 de [ADR-008](ADR-008-jwt.md)). |
| RB-05 | A verificação de permissão ocorre na **camada de serviço**, via `@PreAuthorize` (AZ-01). O Controller não decide autorização. |
| RB-06 | O **escopo de dados** é aplicado na **consulta** (predicado SQL), nunca por filtragem em memória (AZ-04, IMP-02). |
| RB-07 | O **ownership** é verificado no serviço, **após** a verificação de permissão (AZ-03). |
| RB-08 | A ordem de verificação é fixa e obrigatória: token → tenant selecionado → tenant ativo → membership ativo → permissão → pertencimento ao tenant → ownership → guarda de estado (`permissions.md` §4.1). Alterá-la é falha de segurança. |
| RB-09 | Toda negação é registrada em log estruturado (AZ-05) e as negações de segurança relevantes em `audit_logs`. |
| RB-10 | **Negado por padrão** (`ART-085`): endpoint sem declaração explícita de permissão é inacessível. |
| RB-11 | Nenhum usuário altera o próprio papel (OWN-06, RN-456), mesmo sendo `OWNER`. |
| RB-12 | Todo tenant possui **ao menos um** `OWNER` ativo; a operação que violaria essa invariante é rejeitada. |
| RB-13 | Permissões granulares por usuário e papéis customizados são **fora de escopo do MVP** (R-05); a introdução exigirá ADR próprio, preservando a matriz atual como conjunto padrão. |
| RB-14 | O frontend usa as permissões apenas para **ocultar ou desabilitar** elementos. A decisão de autorização é sempre do servidor. |

```mermaid
flowchart TD
    A[Requisição autenticada] --> B{Tenant ativo?}
    B -->|Não| E1["403 DEVTIME-1201/1202"]
    B -->|Sim| C{Membership ativo?}
    C -->|Não| E2["403 DEVTIME-1102"]
    C -->|Sim| D{"RolePermissions.of(role)<br/>contém a permissão? (RB-04)"}
    D -->|Não| E3["403 DEVTIME-1101"]
    D -->|Sim| F{Recurso é do tenant?}
    F -->|Não| E4["404 DEVTIME-2002 — ART-024"]
    F -->|Sim| G{Ownership satisfeito? (RB-07)}
    G -->|Não| E5["403 DEVTIME-1103"]
    G -->|Sim| H[Executa com escopo de dados na query - RB-06]
```

## Motivação

**Por que RBAC com papéis fixos (RB-01):** o domínio tem cinco perfis nitidamente distintos e estáveis (titular, administrador, gestor de entrega, executor, leitor). Papéis fixos são compreensíveis pelo usuário final sem treinamento, testáveis exaustivamente (5 papéis × N permissões é uma matriz finita e verificável) e legíveis por agentes de IA como uma tabela. Um modelo de permissões arbitrárias por usuário produz combinações que ninguém testa e que geram incidentes de acesso.

**Por que no `Membership` e não no usuário (RB-02):** R-02 é característica estrutural do produto. O mesmo contador é `VIEWER` em cinco tenants; o mesmo desenvolvedor é `OWNER` no próprio e `MEMBER` no do cliente. Vincular o papel ao usuário seria modelagem incorreta e impediria o produto de funcionar.

**Por que derivar em runtime (RB-04):** é a implementação de R-03. Se as permissões estivessem no token, o rebaixamento levaria até 15 minutos para produzir efeito. Derivar do `role` a cada requisição, combinado com a rejeição de tokens anteriores a `roleChangedAt` (JW-09), torna o efeito imediato na requisição seguinte. O custo é uma consulta a um mapa em memória — nenhum I/O.

**Por que escopo na consulta (RB-06):** filtrar em memória significa que o servidor **carregou** dados que o usuário não pode ver. Qualquer falha posterior (log, mensagem de erro, serialização parcial, contagem, paginação) vaza. Além disso, filtrar após carregar quebra a paginação: uma página de 20 registros pode retornar 3 após o filtro. O predicado precisa estar no SQL.

**Por que ownership depois da permissão (RB-07):** a ordem importa para não vazar informação. Se o ownership fosse verificado antes, um usuário sem a permissão saberia, pela diferença de mensagem, se o recurso é ou não dele. Verificar a permissão primeiro produz a mesma resposta para todos que não podem executar a ação.

**Por que a matriz vive em `docs/02-domain/permissions.md` e não neste ADR:** a matriz **muda** com o produto (nova feature adiciona permissões). Um ADR é imutável. Este ADR fixa o **modelo**; o documento de domínio mantém o **conteúdo**. Alterar o modelo exige ADR; adicionar uma permissão a uma feature nova, não.

## Alternativas consideradas

### A1 — ABAC (autorização baseada em atributos) com motor de políticas

| Aspecto | Avaliação |
|---|---|
| **Prós** | Máxima expressividade (política pode considerar qualquer atributo: valor do contrato, horário, status); políticas versionadas separadamente do código; um motor externo (OPA, Cedar) centraliza a decisão. |
| **Contras** | Complexidade muito acima da necessidade; políticas em linguagem própria (Rego) que ninguém da equipe domina; difícil de testar exaustivamente; difícil de explicar ao usuário final ("por que não posso?"); um serviço a mais na infraestrutura ou uma biblioteca a incorporar. |
| **Por que foi descartada** | Os requisitos reais são cobertos por RBAC + ownership + escopo de dados. ABAC resolveria políticas dinâmicas que o produto não tem e provavelmente nunca terá — e, se tiver, será por regra de negócio específica documentada como `RN-XXX`. |

### A2 — Permissões diretas por usuário, sem papéis

| Aspecto | Avaliação |
|---|---|
| **Prós** | Flexibilidade total; cada usuário recebe exatamente o que precisa. |
| **Contras** | Explosão combinatória: com 40 permissões há 2⁴⁰ combinações possíveis, das quais nenhuma é testada; administração inviável (o titular precisaria entender 40 permissões); nenhuma consistência entre tenants; suporte impossível de prestar. |
| **Por que foi descartada** | O usuário-alvo é um freelancer, não um administrador de sistemas. A matriz de papéis fixos é a **funcionalidade**, não uma limitação. |

### A3 — Papéis com permissões customizáveis por tenant

| Aspecto | Avaliação |
|---|---|
| **Prós** | Meio-termo: papéis nomeados, mas com conteúdo ajustável por tenant. |
| **Contras** | A matriz deixa de ser conhecida em tempo de teste — cada tenant tem a sua; suporte precisa perguntar "como está configurado o seu papel X?"; exige tabela de permissões por papel por tenant, consultada a cada requisição (ou cacheada, com invalidação); torna a documentação de permissões descritiva em vez de normativa. |
| **Por que foi descartada para o MVP** | É exatamente a funcionalidade planejada para F5 (`specs/future/017-permissions`). Antecipá-la custaria a testabilidade da matriz e a simplicidade do MVP. RB-13 preserva o caminho: a matriz atual vira o conjunto padrão dos papéis customizáveis. |

### A4 — Permissões embutidas no JWT

| Aspecto | Avaliação |
|---|---|
| **Prós** | Zero consulta na autorização; decisão puramente local; frontend lê diretamente do token. |
| **Contras** | Permissões congeladas por até 15 min (viola R-03); token maior; alteração de papel exige revogação forçada; incentiva o frontend a confiar no token para decidir (viola RB-14). |
| **Por que foi descartada** | Explicitamente rejeitada por TK-03 em `security.md` §5.2. A derivação em runtime custa uma consulta a um `EnumMap` — nada. |

### A5 — Verificação de autorização no Controller

| Aspecto | Avaliação |
|---|---|
| **Prós** | Perto da entrada HTTP; fácil de ver ao ler o endpoint. |
| **Contras** | Um serviço chamado por outro caminho (job, evento, outro serviço) escaparia da verificação; viola `ART-062` (Controller só adapta HTTP); duplicação quando o mesmo serviço é exposto por mais de um endpoint. |
| **Por que foi descartada** | A camada de serviço é a fronteira real do domínio. Autorizar ali garante que **todo** caminho de invocação passe pela verificação. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | A matriz papel × permissão é finita, documentada e testável exaustivamente. |
| C+02 | Rebaixamento de papel tem efeito na requisição seguinte (RB-04 + JW-09). |
| C+03 | O usuário final entende cinco papéis nomeados sem treinamento. |
| C+04 | Autorização centralizada em um único mecanismo (`PermissionEvaluator`), auditável em um lugar. |
| C+05 | O escopo na consulta impede que dado não autorizado seja carregado (RB-06). |
| C+06 | Zero I/O adicional para decidir permissão. |
| C+07 | Caminho de evolução para permissões granulares preservado (RB-13). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Papéis fixos não atendem a necessidades intermediárias ("gestor que também vê faturamento"). | Necessidade real será atendida em F5; até lá, o usuário escolhe o papel mais próximo. |
| C-02 | Adicionar uma permissão exige revisar a matriz para os cinco papéis. | É uma virtude: força a decisão explícita, em vez de omissão silenciosa. |
| C-03 | Ownership e escopo de dados são lógica **por feature**, não centralizável. | Padronizado em `permissions.md` §7 e verificado por teste por feature. |
| C-04 | Autorização na camada de serviço não é visível ao ler apenas o Controller. | Documentada na spec de cada feature e verificada por ArchUnit. |
| C-05 | A assimetria de `MEMBER` (R-04) é contraintuitiva e precisa ser explicada. | Justificada em `permissions.md` §7 e exposta na UI. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Sem delegação temporária de acesso ("acesso por 7 dias"). |
| L-02 | Sem permissão por recurso específico ("este contrato apenas"). O compartilhamento de contrato com `MEMBER` é regra de escopo, não de permissão. |
| L-03 | Sem hierarquia organizacional (equipes, departamentos) — previsto para F5. |

### Custos

| Item | Custo |
|---|---|
| Implementação | ~2 dias (matriz, `PermissionEvaluator`, integração com Spring Security) |
| Teste | Uma suíte por feature verificando os cinco papéis por operação |
| Runtime | Consulta a `EnumMap` — desprezível |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Flexibilidade** de permissões arbitrárias | Testabilidade exaustiva e compreensibilidade | Uma matriz que ninguém testa é uma falha de segurança esperando acontecer. |
| **Expressividade** do ABAC | Simplicidade e previsibilidade | Nenhum requisito atual exige política dinâmica. |
| **Autorização visível no Controller** | Cobertura de todos os caminhos de invocação | O caminho não coberto é justamente o que vaza. |
| **Permissões no token** (zero derivação) | Revogação quase imediata de privilégio | Privilégio congelado por 15 min é risco real; a derivação é gratuita. |
| **Customização por tenant** | Matriz normativa e suporte viável | Adiada para F5 com caminho preservado. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `shared/security` | `Permission` (enum), `Role` (enum), `RolePermissions` (mapa), `DevTimePermissionEvaluator`. |
| `*/service` | `@PreAuthorize` em toda operação de escrita (AZ-01) e nas leituras sensíveis. |
| `*/repository` | Predicados de escopo de dados nas `Specification` (RB-06). |
| `tenant` | Gestão de `Membership`, papel, `roleChangedAt`, invariante RB-12. |
| `audit` | Registro de negações relevantes (RB-09). |

| Documento dependente | Relação |
|---|---|
| `docs/02-domain/permissions.md` | Matriz normativa (RB-03) |
| `docs/03-architecture/security.md` §7 | Implementação técnica |
| `docs/ai/backend-rules.md` | `BR-160` a `BR-179` |
| `docs/05-ui/pages.md` | Elementos ocultos por papel (RB-14) |

| Spec dependente | Relação |
|---|---|
| **Todas** as specs `001`–`015` | Seção obrigatória "Permissões" |
| `specs/future/017-permissions` | Evolução prevista por RB-13 |

| ADR relacionado | Relação |
|---|---|
| [ADR-008](ADR-008-jwt.md) | Claim `role` e TK-03/TK-05 |
| [ADR-001](ADR-001-multi-tenant.md) | Segunda camada de `ART-082` |
| [ADR-044](ADR-044-security.md) | Consolidação (OWASP A01) |
| [ADR-048](ADR-048-feature-flags.md) | Flags **não** substituem permissões |

## Impacto no banco

| Item | Impacto |
|---|---|
| Tabela | `memberships (id, tenant_id, user_id, role, status, role_changed_at, ...)`. |
| Coluna `role` | `VARCHAR(20)` + `CHECK` com os cinco valores (PG-05 de [ADR-006](ADR-006-postgresql.md)); nunca `ENUM` nativo. |
| Índice | `uq_memberships_tenant_user` parcial (`WHERE deleted_at IS NULL`). |
| Invariante RB-12 | Verificada na camada de serviço, não por constraint — depende de contagem condicional que um `CHECK` não expressa. |
| Escopo de dados | Traduzido em predicados adicionais nas consultas de `MEMBER` (RB-06), sustentados por índices existentes. |
| Sem tabela de permissões | O MVP **não** possui tabela `permissions` nem `role_permissions`: a matriz é código, versionada com o código e testada. F5 introduzirá persistência (RB-13). |

## Impacto na API

| Item | Impacto |
|---|---|
| `GET /api/v1/auth/me` | Retorna o papel e a **lista derivada de permissões**, para o frontend controlar a UI (RB-14). |
| `403 DEVTIME-1101` | Permissão insuficiente. |
| `403 DEVTIME-1102` | Membership inativo. |
| `403 DEVTIME-1103` | Violação de ownership. |
| `404 DEVTIME-2002` | Recurso de outro tenant — **antes** de qualquer consideração de papel (R-06). |
| Listagens | O escopo de dados afeta o **conteúdo** e a **contagem total** da paginação; a resposta nunca revela a existência de registros fora do escopo. |
| Documentação | Cada endpoint declara no OpenAPI a permissão exigida ([ADR-012](ADR-012-openapi.md)). |

## Impacto no Frontend

| Item | Impacto |
|---|---|
| Estado | `AuthStore` mantém papel e permissões derivadas, obtidas de `/auth/me`. |
| Diretiva | Diretiva estrutural (`*dtHasPermission`) oculta elementos sem permissão. |
| Guards | Rotas protegidas por guard de permissão; a proteção é de **usabilidade**, não de segurança (RB-14). |
| Erro | `403` exibe mensagem clara de permissão insuficiente, distinta de `404`. |
| Troca de papel | Alteração de papel invalida o token (JW-09); o frontend trata o `401` recarregando a sessão. |
| Regra | **Nunca** decidir autorização apenas no cliente; toda ação oculta também é bloqueada no servidor. |

## Impacto na Infraestrutura

Não se aplica diretamente, porque a autorização é lógica de aplicação e não exige componente de infraestrutura. Efeito indireto: as negações geram volume de log estruturado (RB-09), considerado no dimensionamento de retenção de logs ([ADR-019](ADR-019-logging.md)).

## Segurança

| # | Consideração |
|---|---|
| S-01 | Este é o controle da categoria **A01 — Broken Access Control** do OWASP Top 10, a mais prevalente. |
| S-02 | Negado por padrão (RB-10): endpoint novo sem declaração é inacessível, não permissivo. |
| S-03 | A ordem de verificação (RB-08) evita vazamento de existência de recurso por diferença de código de erro. |
| S-04 | RB-06 impede que dado fora do escopo seja carregado em memória. |
| S-05 | **Multi-tenant:** a verificação de papel é a **segunda** camada; a primeira é o pertencimento ao tenant ([ADR-001](ADR-001-multi-tenant.md)). Ambas obrigatórias (`ART-082`). |
| S-06 | **LGPD:** o escopo de dados limita o acesso a dado pessoal ao mínimo necessário por papel (minimização). |
| S-07 | **Auditoria:** alteração de papel, suspensão e remoção de membership são eventos obrigatórios; negações repetidas geram alerta. |
| S-08 | RB-11 e RB-12 impedem escalonamento de privilégio por autoatribuição e perda de controle do tenant. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Derivação de permissão é consulta a `EnumMap` estático: nanossegundos, sem I/O. |
| P-02 | Verificação de membership ativo exige leitura leve, compartilhada com JW-09 e cacheável por TTL curto. |
| P-03 | Ownership frequentemente exige carregar o recurso — o que a operação faria de qualquer forma. |
| P-04 | O escopo de dados de `MEMBER` adiciona predicados que precisam de índice; consultas de listagem são revisadas por plano de execução. |
| P-05 | `@PreAuthorize` usa SpEL, com custo de microssegundos; expressões complexas são substituídas por chamada a método dedicado. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Sem estado compartilhado: a decisão é local a cada instância. |
| E-02 | Adicionar permissões cresce a matriz linearmente, não combinatoriamente. |
| E-03 | A evolução para permissões customizáveis (F5) exigirá cache com invalidação por evento ([ADR-040](ADR-040-cache-strategy.md)). |
| E-04 | O escopo de dados é o ponto que mais exige atenção de índice conforme o volume cresce. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Endpoint novo sem `@PreAuthorize` ficar acessível a papéis indevidos | **Alta** | Alto | **Alta** |
| RK-02 | Escopo de dados aplicado em memória em vez de na consulta | Média | Alto | Alta |
| RK-03 | Ownership esquecido em operação de edição | Média | Alto | Alta |
| RK-04 | Frontend decidir autorização e o backend não verificar | Média | Crítico | **Alta** |
| RK-05 | Matriz de permissões divergir entre documentação e código | Média | Médio | Média |
| RK-06 | Tenant ficar sem `OWNER` ativo | Baixa | Alto | Média |
| RK-07 | Ordem de verificação alterada, vazando existência de recurso | Baixa | Médio | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | RB-10 (negado por padrão) na configuração do Spring Security; teste ArchUnit exigindo `@PreAuthorize` em todo método público de `*Service` que altere estado; suíte que chama cada endpoint com cada papel | ArchUnit + suíte de papéis |
| RK-02 | Regra AZ-04 explícita; revisão de código; teste que verifica a **contagem** de paginação sob escopo restrito (filtragem em memória quebra a contagem) | Teste de paginação |
| RK-03 | Padrão obrigatório na spec de cada feature; teste por operação com usuário não-proprietário | `acceptance.md` por feature |
| RK-04 | Teste de integração que chama o endpoint diretamente, ignorando a UI, para cada papel sem permissão | Suíte de autorização |
| RK-05 | Teste que compara a matriz de código com a tabela de `permissions.md` §7, falhando na divergência | Teste de conformidade |
| RK-06 | Invariante RB-12 verificada em serviço; teste que tenta remover o último `OWNER` | Teste de invariante |
| RK-07 | Ordem codificada em um único ponto (`PermissionEvaluator` + filtro); teste que verifica os códigos de erro na sequência de `permissions.md` §4.1 | Teste de ordem |

## Referências

| Fonte | Uso |
|---|---|
| [NIST — Role Based Access Control (RBAC)](https://csrc.nist.gov/projects/role-based-access-control) | Modelo de referência |
| [OWASP — Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) | Negado por padrão e verificação no servidor |
| [OWASP Top 10 — A01 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/) | Base de S-01 |
| [Spring Security — Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) | RB-05 |
| [Google Zanzibar](https://research.google/pubs/pub48190/) | Referência de modelo alternativo avaliado (A1) |
| `docs/02-domain/permissions.md` | Matriz normativa e ordem de verificação |
