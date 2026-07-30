# ADR-027 — Organização de código por feature (vertical slice), não por camada técnica

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-063`, `ART-065`.
Substitui o identificador legado `ADR-003` de `docs/03-architecture/architecture.md` §6.

## Data

2026-07-29

## Contexto

O DevTime é um monólito modular. "Modular" só é verdade se as fronteiras entre módulos forem **físicas e verificáveis**; caso contrário, é apenas um monólito com boas intenções.

A organização de pastas determina:

| # | O que determina |
|---|---|
| DT-01 | Onde o desenvolvedor (e o agente) procura e cria código |
| DT-02 | Qual dependência é possível expressar sem esforço |
| DT-03 | O que pode ser extraído no futuro sem reescrita |
| DT-04 | Quanto contexto é necessário para entender uma funcionalidade |

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Organização por feature, não por camada | `ART-063` |
| R-02 | Comunicação entre features por interface pública ou evento | `ART-065` |
| R-03 | Extração futura de módulos na ordem: relatórios → notificações → IA | `architecture.md` §13 |
| R-04 | Um agente deve implementar uma feature com contexto fechado | OB-01 de `specs/README.md` |
| R-05 | Regras de dependência verificadas automaticamente | CA-02 de `architecture.md` §16 |

## Decisão

| # | Regra |
|---|---|
| FS-01 | O código é organizado **por feature (vertical slice)**. A feature é a dimensão primária; a camada é a secundária, **dentro** dela (`ART-063`). |
| FS-02 | Estrutura do backend: `com.devtime.<feature>` contendo `controller`, `service`, `repository`, `mapper`, `dto`, `domain` e, quando necessário, subfeatures. |
| FS-03 | `com.devtime.shared` contém a infraestrutura transversal (`tenancy`, `security`, `persistence`, `error`, `time`, `event`, `observability`) e **não depende de nenhuma feature**. |
| FS-04 | Uma feature pode depender de `shared` e da **interface pública** de outra feature. Nunca do `Repository` nem de entidade interna de outra feature (`ART-065`). |
| FS-05 | A "interface pública" de uma feature é o conjunto explícito de: interface de serviço, DTOs de resposta e eventos de domínio publicados. Tudo o mais é interno. |
| FS-06 | Subfeatures são permitidas quando um subdomínio tem coesão própria (ex.: `contract/period`), seguindo a mesma estrutura interna. |
| FS-07 | O frontend espelha a organização: `features/<feature>/` com `components`, `store`, `api`, `models` e `routes`; `core/` para transversal; `shared/` para reutilizável. |
| FS-08 | Uma feature de frontend **não** importa de outra feature de frontend; o compartilhamento passa por `shared/` ou `core/`. |
| FS-09 | Todas as regras de dependência são **verificadas automaticamente**: ArchUnit no backend, lint de fronteira no frontend (R-05). |
| FS-10 | O nome da pasta da feature coincide com o nome usado em `specs/NNN-<feature>/` e em `docs/`, criando rastreabilidade direta entre documento e código. |
| FS-11 | Não existe pasta `utils` genérica. Utilitários pertencem a `shared/<domínio-do-utilitário>` (ex.: `shared/time`), com propósito nomeado. |

### Regras de dependência (verificadas por ArchUnit)

| De | Pode depender de |
|---|---|
| Qualquer feature | `shared/*` |
| `worklog` | interface pública de `ticket`, `contract`, `category` |
| `contract` | interface pública de `client` |
| `report` | interfaces públicas de leitura de todas as features |
| `shared` | **nada** de feature |
| Feature A | **nunca** de `Repository` ou entidade interna da feature B |

```
com.devtime
├── shared/            # transversal — não depende de feature (FS-03)
│   ├── tenancy/ · security/ · persistence/ · error/ · time/ · event/ · observability/
├── tenant/ · user/ · auth/ · client/
├── contract/
│   ├── ContractController.java · ContractService.java · ContractRepository.java
│   ├── domain/ · dto/ · mapper/
│   └── period/        # subfeature (FS-06)
├── ticket/ · worklog/ · timer/ · category/
└── report/ · notification/ · audit/
```

## Motivação

**Por que feature e não camada:**

1. **Contexto fechado (R-04).** Implementar "registro de horas" exige tocar controller, service, repository, DTO, mapper e entidade de work log. Organizados por camada, isso significa navegar por seis diretórios distintos, cada um contendo também código de todas as outras features. Organizados por feature, é uma pasta.
2. **A fronteira vira física (DT-02).** Em `controllers/`, `services/`, `repositories/`, nada impede `WorkLogService` de injetar `ContractRepository` — não há fronteira a violar, porque não há fronteira. Com pacotes por feature, essa dependência atravessa uma fronteira nomeada, o que a torna **detectável** (FS-09) e discutível em revisão.
3. **Extração futura (R-03/DT-03).** Extrair `report` como serviço independente é mover uma pasta e substituir chamadas de interface por chamadas remotas. Com organização por camada, seria necessário caçar as classes de relatório em seis diretórios e descobrir, uma a uma, suas dependências ocultas.
4. **Alinhamento com `specs/` (FS-10).** As specs são organizadas por funcionalidade. Quando as pastas de código têm o mesmo nome, a rastreabilidade documento → código é direta e o agente sabe exatamente onde criar cada artefato.
5. **Escala de leitura.** Em `services/` com 40 arquivos, encontrar o relevante exige ler nomes. Em `worklog/`, tudo é relevante.

**Por que `shared` não depende de feature (FS-03):** se `shared` dependesse de uma feature, toda feature dependeria transitivamente dela, e o grafo se tornaria cíclico. `shared` é a base sobre a qual as features se apoiam; a seta aponta em um único sentido.

**Por que interface pública explícita (FS-05):** sem uma definição precisa, "interface pública" vira "qualquer classe `public`", e a restrição perde sentido. Definir o conjunto (serviço, DTO de resposta, evento) torna a regra verificável.

**Por que proibir `utils` (FS-11):** uma pasta `utils` acumula funções sem dono, é importada por todos e vira acoplamento global. `shared/time` comunica propósito e delimita escopo.

**Por que verificar automaticamente (FS-09):** regras de dependência não sobrevivem a seis meses de disciplina humana, especialmente com múltiplos agentes gerando código. ArchUnit as transforma em falha de build.

## Alternativas consideradas

### A1 — Organização por camada técnica

| Aspecto | Avaliação |
|---|---|
| **Prós** | Padrão histórico amplamente conhecido; fácil localizar "todos os controllers"; simetria evidente; muito material de referência. |
| **Contras** | Nenhuma fronteira de módulo; entender uma feature exige navegar por N diretórios; extração futura impossível sem arqueologia; diretórios crescem indefinidamente; nenhuma regra de dependência verificável. |
| **Por que foi descartada** | Falha em R-01, R-03, R-04 e R-05 simultaneamente. É a organização que torna "monólito modular" uma expressão vazia. |

### A2 — Arquitetura hexagonal com pastas por camada de hexágono (`domain/`, `application/`, `infrastructure/`)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Explicita a regra de dependência (infraestrutura → aplicação → domínio); domínio isolado de framework; verificável. |
| **Contras** | Ainda é organização por camada, apenas com outros nomes: uma feature continua espalhada por três diretórios de topo; combinada com feature, produz `domain/worklog`, `application/worklog`, `infrastructure/worklog` — o pior dos dois mundos em termos de navegação. |
| **Por que foi descartada** | O modelo hexagonal completo já foi descartado em [ADR-016](ADR-016-controller-service-repository.md) A2. Sua organização de pastas herda o problema de dispersão. |

### A3 — Módulos Maven separados (um artefato por feature)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Fronteira imposta pelo compilador, não por teste; impossível violar a dependência; prepara literalmente a extração. |
| **Contras** | Build significativamente mais lento; refatoração entre módulos é custosa; ciclo de desenvolvimento pior; complexidade de configuração desproporcional para ~12 features; mudanças que atravessam features (comuns no MVP, quando o domínio ainda está sendo descoberto) tornam-se caras. |
| **Por que foi descartada para o MVP** | O domínio ainda está em descoberta (contexto de `architecture.md` §6): fronteiras erradas cristalizadas em módulos Maven seriam caras de corrigir. ArchUnit entrega ~90% da garantia com ~5% do custo. A opção permanece disponível quando as fronteiras estabilizarem. |

### A4 — Spring Modulith

| Aspecto | Avaliação |
|---|---|
| **Prós** | Verificação de fronteiras nativa; documentação de módulos gerada; eventos entre módulos com garantias; caminho oficial do Spring para monólito modular. |
| **Contras** | Dependência adicional; convenções próprias a aprender; sobreposição parcial com o que ArchUnit já entrega; ecossistema ainda em amadurecimento na época da decisão. |
| **Por que foi descartada para o MVP** | ArchUnit já cobre a verificação de dependências, e a publicação de eventos já está abstraída. A adoção seria reavaliada por ADR próprio, provavelmente junto com a introdução de mensageria em F6 ([ADR-042](ADR-042-rabbitmq.md)). |

### A5 — Estrutura plana (todas as classes em poucos pacotes)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Nenhuma decisão de organização; simples no início. |
| **Contras** | Insustentável além de algumas dezenas de classes; nenhuma fronteira; nenhuma navegabilidade. |
| **Por que foi descartada** | Inviável na escala prevista. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Contexto fechado por feature (R-04): o agente lê uma pasta. |
| C+02 | Fronteiras físicas e verificáveis (FS-09). |
| C+03 | Extração futura de módulos preservada (R-03). |
| C+04 | Rastreabilidade direta entre `specs/`, `docs/` e código (FS-10). |
| C+05 | Diretórios permanecem em tamanho legível conforme o sistema cresce. |
| C+06 | Frontend e backend com a mesma organização mental (FS-07). |
| C+07 | Dependência indevida entre features falha o build, não a revisão. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Menos familiar que a organização por camada. | Padronizada em `architecture.md` §6 e verificada; a curva é de horas. |
| C-02 | Decidir a qual feature um código pertence nem sempre é óbvio. | Critério: a feature que **possui** a entidade principal; casos ambíguos vão para `shared` com justificativa. |
| C-03 | Alguma duplicação entre features (DTOs semelhantes). | Duplicação é preferível a acoplamento entre features (A5 de [ADR-013](ADR-013-dto.md)). |
| C-04 | Comunicação entre features é mais verbosa. | É o atrito desejado: torna a dependência visível. |
| C-05 | Refatoração de fronteira (mover código entre features) exige atenção. | Ocorre poucas vezes e é justamente quando a discussão arquitetural é útil. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | A fronteira é verificada por teste, não pelo compilador (consequência de rejeitar A3). |
| L-02 | Não há encapsulamento real: uma classe `public` de outra feature é tecnicamente acessível; a proibição é verificada, não impedida. |
| L-03 | Features muito pequenas geram pastas com poucos arquivos, o que pode parecer sobre-estruturação. |

### Custos

| Item | Custo |
|---|---|
| Implementação | Zero: é a estrutura inicial |
| Verificação | Suíte ArchUnit (~20 s no pipeline) |
| Aprendizado | Baixo, com a estrutura documentada |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Familiaridade** da organização por camada | Fronteiras reais e contexto fechado | Familiaridade não compensa a impossibilidade de modularizar. |
| **Garantia do compilador** (módulos Maven) | Velocidade de build e refatoração barata | Domínio em descoberta não deve ter fronteiras cristalizadas cedo. |
| **Ausência de duplicação** entre features | Baixo acoplamento | Acoplamento é a dívida mais cara de um monólito. |
| **Simplicidade** de acesso direto entre features | Modularidade verificável | Sem a restrição, o monólito modular deixa de ser modular. |
| **Recursos do Spring Modulith** | Menos dependências | ArchUnit cobre o essencial; reavaliável em F6. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| Todo o backend | Estrutura de pacotes. |
| Todo o frontend | Estrutura de pastas (FS-07). |
| Testes | Suíte ArchUnit codificando as regras de dependência. |
| `shared/*` | Definido como base sem dependência de feature. |

| Documento dependente | Relação |
|---|---|
| `docs/ai/project-constitution.md` | ART-063, ART-065 |
| `docs/03-architecture/architecture.md` §6, §7 | Estrutura e regras de dependência |
| `docs/03-architecture/backend.md` §5, §5.1 | Pacotes e ArchUnit |
| `docs/03-architecture/frontend.md` §5, §5.1 | Pastas e fronteiras |
| `docs/06-testing/strategy.md` §6.4 | Testes de arquitetura |

| Spec dependente | Relação |
|---|---|
| Todas as specs | O nome da pasta corresponde ao da spec (FS-10) |

| ADR relacionado | Relação |
|---|---|
| [ADR-016](ADR-016-controller-service-repository.md) | Camadas dentro da feature |
| [ADR-013](ADR-013-dto.md) | DTOs no pacote da feature (DT-03) |
| [ADR-014](ADR-014-mapstruct.md) | Mappers por feature (MS-11) |
| [ADR-023](ADR-023-standalone-components.md) | Agrupamento por pasta, não por módulo |
| [ADR-042](ADR-042-rabbitmq.md) | Extração futura depende desta fronteira |

## Impacto no banco

Não se aplica diretamente, porque a organização de código não determina o schema. Efeito indireto relevante: FS-04 significa que uma feature **não** consulta tabelas de outra diretamente pelo Repository alheio; se precisar do dado, chama a interface pública. Isso mantém a propriedade dos dados alinhada à propriedade do código — pré-requisito de uma extração futura.

## Impacto na API

Não se aplica ao contrato. Efeito indireto: os controllers de uma feature expõem os endpoints daquele recurso, o que produz naturalmente uma correspondência entre a estrutura da API (`/work-logs`) e a estrutura do código (`worklog/`), facilitando localizar o código a partir do endpoint.

## Impacto no Frontend

| Item | Impacto |
|---|---|
| Estrutura | `features/<feature>/` com `components`, `store`, `api`, `models`, `routes` (FS-07). |
| Transversal | `core/` (interceptors, guards, stores globais) e `shared/` (componentes `dt-*`, pipes, diretivas). |
| Fronteira | Feature não importa de feature (FS-08), verificado por lint. |
| Rotas | Cada feature declara suas rotas, carregadas sob demanda ([ADR-023](ADR-023-standalone-components.md) SC-05). |
| Correspondência | O nome da pasta do frontend coincide com o do backend e com o da spec (FS-10). |

## Impacto na Infraestrutura

Não se aplica no MVP, porque o artefato continua único ([ADR-020](ADR-020-docker.md)). Efeito futuro: FS-01 e FS-04 são pré-requisitos da extração de módulos prevista em `architecture.md` §13 — que **seria** uma mudança de infraestrutura (novos artefatos, novos deploys, comunicação em rede).

## Segurança

| # | Consideração |
|---|---|
| S-01 | FS-04 limita o raio de alcance de um bug: uma feature comprometida não acessa dados de outra diretamente. |
| S-02 | `shared/tenancy` e `shared/security` concentram os controles críticos em pacotes identificáveis, com revisão dirigida. |
| S-03 | A verificação automatizada (FS-09) impede que uma dependência indevida seja introduzida silenciosamente. |
| S-04 | A correspondência entre spec e pasta (FS-10) facilita auditar se todas as regras de segurança de uma feature foram implementadas. |
| S-05 | **Multi-tenant:** o isolamento vive em `shared/tenancy` e se aplica a todas as features uniformemente; nenhuma feature implementa tenancy própria. |
| S-06 | **LGPD:** a propriedade clara do dado por feature facilita mapear onde dado pessoal é tratado. |
| S-07 | **Auditoria:** a feature `audit` é um módulo próprio, consumido por evento, o que evita que cada feature reimplemente a trilha. |

## Performance

| # | Consideração |
|---|---|
| P-01 | Nenhum impacto em runtime: a organização de pacotes não afeta a execução. |
| P-02 | FS-04 evita joins acidentais entre domínios distintos, que são fonte comum de consulta cara. |
| P-03 | A comunicação entre features por interface pode gerar consultas adicionais em vez de um join único — trade-off consciente de C-04. Onde o custo for relevante, a spec define uma consulta de leitura dedicada em `report` (CL-11 de [ADR-016](ADR-016-controller-service-repository.md)). |
| P-04 | A suíte ArchUnit adiciona ~20 s ao pipeline. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Adicionar feature é adicionar pasta, sem tocar as existentes. |
| E-02 | A extração de módulos (R-03) permanece viável enquanto FS-04 for respeitada — este é o principal ativo de escalabilidade da decisão. |
| E-03 | Diretórios não crescem indefinidamente, ao contrário de `services/`. |
| E-04 | O grafo de dependências permanece plano e auditável. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Feature acessando Repository ou entidade de outra feature | **Alta** | Alto | **Alta** |
| RK-02 | `shared` acumulando código que pertence a uma feature | Alta | Médio | Alta |
| RK-03 | Surgimento de pasta `utils` genérica | Média | Médio | Média |
| RK-04 | Dependência circular entre features | Média | Alto | Alta |
| RK-05 | Feature errada escolhida para um código ambíguo | Média | Baixo | Baixa |
| RK-06 | Regras de ArchUnit desabilitadas por gerarem atrito | Baixa | Alto | Média |
| RK-07 | Divergência entre nomes de pasta e de spec (FS-10) | Baixa | Baixo | Baixa |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Regra ArchUnit explícita; gate `G-05` bloqueia o build | ArchUnit |
| RK-02 | Regra: entrar em `shared` exige uso por **duas ou mais** features e justificativa em PR; ArchUnit verifica que `shared` não depende de feature | ArchUnit + revisão |
| RK-03 | FS-11 explícita; lint/ArchUnit proíbe pacote chamado `util`/`utils`/`helpers` | ArchUnit |
| RK-04 | ArchUnit verifica ausência de ciclos entre pacotes de feature | ArchUnit |
| RK-05 | Critério documentado (a feature que possui a entidade principal); mover código entre features é barato no MVP | Revisão |
| RK-06 | Desabilitar regra de arquitetura exige ADR substituto; a configuração do gate é protegida | Processo |
| RK-07 | Teste que compara a lista de pastas de feature com a lista de specs | Teste de conformidade |

## Referências

| Fonte | Uso |
|---|---|
| [Simon Brown — Package by component](https://www.codingthearchitecture.com/2015/03/08/package_by_component_and_architecturally_aligned_testing.html) | Fundamento de FS-01 |
| [Jimmy Bogard — Vertical Slice Architecture](https://www.jimmybogard.com/vertical-slice-architecture/) | Modelo adotado |
| [ArchUnit — User Guide](https://www.archunit.org/userguide/html/000_Index.html) | FS-09 |
| [Spring Modulith](https://docs.spring.io/spring-modulith/reference/) | Alternativa A4 |
| [Eric Evans — Bounded Context](https://martinfowler.com/bliki/BoundedContext.html) | Fronteira de feature |
| `docs/03-architecture/architecture.md` §6, §7 | Estrutura e regras de dependência |
