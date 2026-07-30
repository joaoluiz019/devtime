# ADR-031 — Conventional Commits com rastreabilidade obrigatória a `RN-`, `US-` e `ADR-`

## Status

**Aceito** em 2026-07-29.
Formaliza §10 de `docs/ai/coding-guidelines.md`.

## Data

2026-07-29

## Contexto

O histórico de commits é a única documentação que **sempre** existe e **nunca** fica desatualizada em relação ao que aconteceu. Em um projeto onde a maior parte do código é gerada por agentes de IA, ele cumpre três papéis adicionais:

| # | Papel | Detalhe |
|---|---|---|
| PP-01 | Rastreabilidade | Ligar cada linha de código à regra de negócio, à user story ou ao ADR que a motivou (`ART-003` aplicado ao processo) |
| PP-02 | Geração de changelog | Produzir notas de versão sem trabalho manual ([ADR-033](ADR-033-versioning.md)) |
| PP-03 | Arqueologia | Responder "por que este código existe?" seis meses depois, quando o autor foi um agente sem memória |

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Formato de commit já definido em `coding-guidelines.md` §10 | `CO-01` a `CO-06` |
| R-02 | Toda mudança de comportamento atualiza a documentação no mesmo PR | `ART-111` |
| R-03 | Toda regra de negócio tem identificador `RN-XXX` | `ART-112` |
| R-04 | Versionamento semântico com changelog por versão | `VR-03` de `coding-guidelines.md` |
| R-05 | Commit fora do formato é bloqueado | §"Casos de erro" de `coding-guidelines.md` |

## Decisão

| # | Regra |
|---|---|
| CC-01 | Toda mensagem de commit segue **Conventional Commits 1.0.0**: `<tipo>(<escopo>): <descrição>`. |
| CC-02 | Tipos permitidos: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `style`, `build`, `ci`, `chore`, `revert`. Nenhum outro. |
| CC-03 | Escopos permitidos são um conjunto **fechado**, correspondente às features e áreas transversais: `auth`, `tenant`, `client`, `contract`, `period`, `ticket`, `worklog`, `timer`, `category`, `tag`, `report`, `notification`, `attachment`, `audit`, `ui`, `infra`, `docs`. |
| CC-04 | A descrição é imperativa, em minúsculas, sem ponto final, com no máximo 72 caracteres (CO-01). |
| CC-05 | Um commit resolve **um** problema (CO-02). |
| CC-06 | **Rastreabilidade obrigatória no rodapé:** todo commit `feat` ou `fix` referencia ao menos um identificador — `Refs: US-081`, `Regra: RN-102`, `Closes: #142` ou `Refs: ADR-014`. |
| CC-07 | Mudança incompatível usa `!` após o escopo **e** `BREAKING CHANGE:` no rodapé (CO-05). |
| CC-08 | Nenhum commit quebra o build (CO-06). |
| CC-09 | O formato é **verificado automaticamente**: hook local (rápido) e verificação no pipeline (autoritativa). Commit fora do formato bloqueia o merge (R-05). |
| CC-10 | O corpo explica o **porquê**, nunca o *o quê* — o diff já mostra o que mudou (CO-03). |
| CC-11 | O merge na branch principal usa *squash* com mensagem convencional, de modo que o histórico da principal seja limpo e navegável ([ADR-032](ADR-032-git-flow.md)). |
| CC-12 | O `CHANGELOG.md` é **gerado** a partir dos commits, não escrito à mão (PP-02, R-04). |
| CC-13 | Commits gerados por agentes de IA seguem exatamente as mesmas regras; não há exceção por autoria. |

**Exemplo canônico:**

```
feat(worklog): impede sobreposição de sessões no mesmo usuário

A validação ocorre na camada de serviço porque depende de consulta
ao banco, conforme VL-05 de ADR-015.

Refs: US-081
Regra: RN-102
```

## Motivação

**Por que um formato estruturado:** mensagens livres produzem histórico não pesquisável e não processável. "ajustes", "correções" e "wip" são o resultado previsível da ausência de convenção. Um formato estruturado permite filtrar por tipo, por escopo e por área, e é a base para PP-02.

**Por que escopo fechado (CC-03):** escopo livre produziria `worklog`, `work-log`, `worklogs` e `WorkLog` no mesmo repositório, inutilizando o agrupamento. O conjunto fechado corresponde às features de [ADR-027](ADR-027-folder-structure.md), o que cria uma terceira correspondência: pasta ↔ spec ↔ escopo de commit.

**Por que rastreabilidade obrigatória (CC-06) — a regra mais valiosa:** este projeto exige que toda regra de negócio venha de `docs/02-domain/business-rules.md` (`SP-01`, `P-10`) e que toda `RN-XXX` tenha teste (`ART-101`). O rodapé fecha o circuito: dado um trecho de código, `git blame` leva ao commit, que leva à `RN-XXX`, que leva ao documento normativo e ao teste. Sem isso, a rastreabilidade existiria apenas na direção documento → código, e a pergunta mais frequente na manutenção ("por que esta linha existe?") ficaria sem resposta.

**Por que o corpo explica o porquê (CC-10):** o diff mostra o que mudou; nenhuma ferramenta mostra a razão. Em um projeto com autoria majoritariamente automatizada, a razão é justamente o que se perde — o agente não estará disponível para explicar depois.

**Por que verificação automatizada (CC-09):** convenção não verificada é seguida por algumas semanas. Hook local dá feedback imediato; verificação no pipeline é a autoridade, porque hooks locais podem ser contornados.

**Por que squash no merge (CC-11):** o histórico de trabalho de uma branch contém commits intermediários ("corrige teste", "ajusta lint") que não têm valor histórico. O squash produz um commit por unidade de mudança na principal, o que torna o changelog e a arqueologia úteis. Isso é coerente com branches curtas ([ADR-032](ADR-032-git-flow.md)).

**Por que sem exceção para agentes (CC-13):** commits de agentes são a maioria. Excetuá-los esvaziaria a decisão.

## Alternativas consideradas

### A1 — Mensagens de commit livres

| Aspecto | Avaliação |
|---|---|
| **Prós** | Nenhuma restrição; escrita mais rápida; sem ferramenta de verificação. |
| **Contras** | Histórico não pesquisável nem agrupável; changelog manual; sem rastreabilidade; degradação previsível da qualidade das mensagens. |
| **Por que foi descartada** | Elimina PP-01, PP-02 e PP-03 simultaneamente. |

### A2 — Formato próprio (ex.: `[WORKLOG] descrição`)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Adaptado exatamente às necessidades do projeto; simples. |
| **Contras** | Nenhuma ferramenta pronta para verificar, gerar changelog ou calcular versão; toda automação seria construída internamente; contribuidores externos precisariam aprender um formato inédito. |
| **Por que foi descartada** | Conventional Commits é um padrão com ecossistema maduro de ferramentas. Um formato próprio custaria a automação sem ganho. |

### A3 — Conventional Commits **sem** rastreabilidade obrigatória no rodapé

| Aspecto | Avaliação |
|---|---|
| **Prós** | Menos atrito por commit; formato ainda estruturado; changelog ainda gerável. |
| **Contras** | Perde PP-01: não há caminho de código → regra; a pergunta "por que esta validação existe?" volta a depender de memória. |
| **Por que foi descartada** | A rastreabilidade é o principal valor da decisão neste projeto, dado o modelo de execução e o requisito de `ART-003`. O custo é uma linha por commit. |

### A4 — Rastreabilidade apenas no título do PR, não nos commits

| Aspecto | Avaliação |
|---|---|
| **Prós** | Menos atrito; um único lugar para referenciar; PR já contém a descrição completa. |
| **Contras** | `git blame` aponta para o commit, não para o PR; recuperar a referência exigiria consultar a plataforma, o que depende de o GitHub estar disponível e de o PR não ter sido apagado; o histórico Git deixaria de ser autossuficiente. |
| **Por que foi descartada** | O histórico Git deve ser autossuficiente: ele é o artefato que sobrevive a mudanças de plataforma. Com CC-11 (squash), a mensagem do PR **vira** a mensagem do commit, o que reduz o atrito quase a zero. |

### A5 — Gitmoji ou convenções baseadas em emoji

| Aspecto | Avaliação |
|---|---|
| **Prós** | Visualmente rápido de escanear; popular em alguns ecossistemas. |
| **Contras** | Não processável por ferramentas de changelog padrão; ambíguo (vários emojis para o mesmo conceito); problemas de codificação em terminais e ferramentas. |
| **Por que foi descartada** | Prejudica a automação, que é o objetivo principal. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Rastreabilidade bidirecional completa: código ↔ commit ↔ regra ↔ documento ↔ teste. |
| C+02 | `CHANGELOG.md` gerado automaticamente (CC-12). |
| C+03 | Cálculo automático do incremento de versão semântica ([ADR-033](ADR-033-versioning.md)). |
| C+04 | Histórico pesquisável por tipo e escopo. |
| C+05 | Mudanças incompatíveis explicitamente marcadas (CC-07). |
| C+06 | Histórico da principal limpo e navegável (CC-11). |
| C+07 | O "porquê" é preservado mesmo quando o autor foi um agente (CC-10). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Atrito por commit (formato + rodapé). | Poucos segundos; automatizável por template de commit. |
| C-02 | Ferramenta de verificação a configurar e manter. | Configuração única, de baixa manutenção. |
| C-03 | Escopo fechado exige atualização quando surge feature nova. | Uma linha de configuração por feature; força coerência com [ADR-027](ADR-027-folder-structure.md). |
| C-04 | Squash perde os commits intermediários da branch. | Deliberado (CC-11): eles não têm valor histórico. |
| C-05 | Commits rejeitados por formato interrompem o fluxo. | Hook local dá feedback antes do push. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | O formato não garante que a mensagem seja **boa**, apenas que seja bem formada. |
| L-02 | A referência pode apontar para um identificador inexistente se não houver verificação cruzada. |
| L-03 | O changelog gerado reflete os commits; commits mal descritos produzem changelog ruim. |

### Custos

| Item | Custo |
|---|---|
| Ferramenta | Verificador de commit (gratuito) |
| Implementação | ~2 horas |
| Por commit | Poucos segundos |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Liberdade** de escrita | Automação e pesquisa | Histórico livre degrada previsivelmente. |
| **Velocidade** por commit | Rastreabilidade permanente (CC-06) | Segundos agora contra horas de arqueologia depois. |
| **Formato sob medida** | Ecossistema de ferramentas | Padrão de indústria traz changelog e versionamento prontos. |
| **Histórico completo** da branch | Histórico limpo da principal | Commits intermediários são ruído em `git log`. |
| **Flexibilidade** de escopo | Agrupamento confiável | Escopo livre inutiliza o agrupamento. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| Repositório | Configuração do verificador de commits e template de mensagem. |
| CI | Etapa de verificação de formato ([ADR-030](ADR-030-github-actions.md)). |
| Release | Geração de `CHANGELOG.md` e cálculo de versão ([ADR-033](ADR-033-versioning.md)). |

| Documento dependente | Relação |
|---|---|
| `docs/ai/coding-guidelines.md` §10 | Formato e regras `CO-01` a `CO-06` |
| `docs/ai/review-checklist.md` | Verificação de rastreabilidade |
| `docs/ai/definition-of-done.md` | Commit rastreável como critério |

| Spec dependente | Relação |
|---|---|
| Todas as specs | `tasks.md` fornece os `US-XXX` referenciados |

| ADR relacionado | Relação |
|---|---|
| [ADR-032](ADR-032-git-flow.md) | Fluxo de branches e squash |
| [ADR-033](ADR-033-versioning.md) | Versão e changelog derivados |
| [ADR-030](ADR-030-github-actions.md) | Verificação no pipeline |
| [ADR-027](ADR-027-folder-structure.md) | Escopos correspondem às features |

## Impacto no banco

Não se aplica, porque a convenção de commits é de processo. Efeito indireto: commits que introduzem migration usam escopo da feature correspondente e referenciam a regra que motivou a mudança de schema, o que facilita entender a origem de cada alteração estrutural.

## Impacto na API

Não se aplica diretamente. Efeito indireto relevante: CC-07 (`BREAKING CHANGE`) é o sinal que identifica mudanças incompatíveis de contrato, alimentando a decisão de versionamento de API ([ADR-043](ADR-043-api-versioning.md)) e o changelog.

## Impacto no Frontend

Não se aplica além da convenção: commits de frontend usam escopo `ui` ou o escopo da feature correspondente, seguindo exatamente as mesmas regras.

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Hook local | Configurado no repositório, executado antes do commit. |
| Pipeline | Verificação autoritativa do formato dos commits do PR. |
| Release | Geração do changelog e cálculo de versão a partir do histórico. |
| Proteção | O verificador é gate; contorná-lo exige alterar o workflow, protegido por revisão. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | Mensagens de commit são públicas no histórico: **nunca** devem conter segredo, credencial, token ou dado pessoal. |
| S-02 | Referências a incidentes de segurança usam o identificador do incidente, não a descrição da vulnerabilidade — evita expor detalhe explorável antes da correção estar publicada. |
| S-03 | CC-06 cria trilha de auditoria de código: toda alteração é rastreável até a motivação. |
| S-04 | O autor de cada commit é registrado, complementando a auditoria de deploy ([ADR-030](ADR-030-github-actions.md) S-09). |
| S-05 | **Multi-tenant:** commits que alterem `shared/tenancy` ou consultas devem referenciar explicitamente a regra, sinalizando revisão dirigida. |
| S-06 | **LGPD:** nenhum dado pessoal em mensagem de commit — o histórico Git é imutável e não pode ser purgado sem reescrita. |
| S-07 | **Auditoria:** o histórico é o registro de quem alterou o quê no código, complementar a `audit_logs`, que registra alterações de **dados**. |

## Performance

Não se aplica, porque a convenção não afeta o comportamento em runtime. Efeito no processo: a verificação de formato leva menos de um segundo no pipeline.

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | A convenção escala com o número de contribuidores, humanos ou agentes. |
| E-02 | O conjunto de escopos cresce com as features; permanece pequeno e gerenciável. |
| E-03 | O changelog gerado cresce com o histórico, organizado por versão. |
| E-04 | A busca por escopo permanece eficiente independentemente do tamanho do histórico. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Rodapé de rastreabilidade omitido (CC-06) | **Alta** | Médio | Alta |
| RK-02 | Referência apontando para identificador inexistente | Média | Médio | Média |
| RK-03 | Descrições genéricas ("ajustes", "correções") passando pelo verificador | **Alta** | Médio | Alta |
| RK-04 | Segredo ou dado pessoal em mensagem de commit | Baixa | Alto | Média |
| RK-05 | Escopo desatualizado em relação às features | Média | Baixo | Baixa |
| RK-06 | `BREAKING CHANGE` omitido em mudança incompatível | Média | Alto | Alta |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Verificador exige rodapé em commits `feat` e `fix`; template de commit pré-preenchido | Pipeline |
| RK-02 | Verificação cruzada: o identificador referenciado deve existir em `business-rules.md`, em `stories.md` ou em `docs/adr/` | Script de conformidade |
| RK-03 | Revisão humana no PR; lista de descrições proibidas no verificador (`ajustes`, `correções`, `wip`, `fix`) | Verificador + revisão |
| RK-04 | Gate `G-07` de detecção de segredo cobre também mensagens; orientação explícita em S-01 | Pipeline |
| RK-05 | Lista de escopos gerada a partir das pastas de feature, falhando se divergir | Teste de conformidade |
| RK-06 | Detecção de quebra de contrato no OpenAPI (OA-11) confrontada com a presença de `BREAKING CHANGE` no histórico | Gate de contrato |

## Referências

| Fonte | Uso |
|---|---|
| [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) | Especificação adotada |
| [Angular Commit Message Guidelines](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#commit) | Origem histórica do formato |
| [Keep a Changelog](https://keepachangelog.com/) | Formato do changelog gerado |
| [Semantic Versioning 2.0.0](https://semver.org/) | Relação entre tipo de commit e incremento |
| [Git — commit message best practices](https://git-scm.com/docs/git-commit) | CC-04, CC-10 |
| `docs/ai/coding-guidelines.md` §10 | Regras `CO-01` a `CO-06` |
