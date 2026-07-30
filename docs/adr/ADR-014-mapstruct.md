# ADR-014 — MapStruct para mapeamento entidade ↔ DTO em tempo de compilação

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-061` (parte de conversão). Depende de [ADR-013](ADR-013-dto.md).

## Data

2026-07-29

## Contexto

A decisão de fronteira ([ADR-013](ADR-013-dto.md)) cria a necessidade de conversão entre entidade JPA e DTO em todas as features. Com ~15 entidades expostas e 3 a 4 DTOs cada, o volume de mapeamento é significativo e repetitivo.

O mapeamento é código de baixo valor intelectual e alto risco: um campo esquecido não gera erro de compilação em nenhuma abordagem manual ou reflexiva — apenas um `null` silencioso na resposta ou, pior, na persistência.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Nenhum campo interno pode ser mapeado por acidente (`tenantId`, `deletedAt`) | DT-05/DT-07 de [ADR-013](ADR-013-dto.md) |
| R-02 | DTOs são `record` imutáveis | DT-02 |
| R-03 | O mapeamento não pode conter regra de negócio | `ART-062` |
| R-04 | Performance: o mapeamento está no caminho quente de toda requisição | AQ-01 |
| R-05 | O código gerado deve ser legível por humanos e por agentes de IA | `docs/ai/` |

## Decisão

| # | Regra |
|---|---|
| MS-01 | A conversão entidade ↔ DTO usa **MapStruct 1.6.x**, com geração de código em **tempo de compilação**. |
| MS-02 | Existe um mapper por feature: `<Entidade>Mapper`, interface anotada com `@Mapper(componentModel = "spring")`, no pacote da feature. |
| MS-03 | A política de campo não mapeado é **`ReportingPolicy.ERROR`**: um campo do destino sem origem correspondente **falha a compilação**. |
| MS-04 | Campos deliberadamente não mapeados são declarados com `@Mapping(target = "...", ignore = true)`, tornando a omissão **explícita e revisável**. |
| MS-05 | O mapper **não** contém regra de negócio (R-03). Transformações permitidas: renomear campo, formatar valor, achatar objeto aninhado, converter tipo. |
| MS-06 | Cálculo, decisão condicional de negócio e acesso a repositório são **proibidos** no mapper. Se necessário, o valor é calculado no serviço e passado como `@Context` ou parâmetro. |
| MS-07 | O mapeamento de `*Request` → entidade **nunca** define `id`, `tenantId`, campos de auditoria nem `version`; todos são `ignore = true` explícito (R-01). |
| MS-08 | Atualização parcial usa `@MappingTarget` com `NullValuePropertyMappingStrategy.IGNORE`, coerente com a semântica de `PATCH` (DT-13). |
| MS-09 | O código gerado é versionado no diretório de build, **não** no repositório; é inspecionável durante a revisão quando necessário. |
| MS-10 | Mappers são testados: todo mapper tem teste que verifica o mapeamento completo de todos os campos, inclusive os ignorados. |
| MS-11 | Não há mapeamento entre DTOs de features diferentes dentro de um mapper; a composição usa o mapper público da outra feature, declarado em `uses` (`ART-065`). |

## Motivação

**Por que geração em tempo de compilação (MS-01):** é o que transforma o principal risco (campo esquecido) em **erro de compilação** (MS-03), em vez de bug silencioso em produção. Nenhuma abordagem reflexiva ou manual oferece essa garantia.

**Por que `ReportingPolicy.ERROR` (MS-03):** esta é a regra mais importante do ADR. Sem ela, o MapStruct apenas emite um aviso, e avisos são ignorados. Com ela, adicionar um campo ao DTO de resposta **quebra o build** até que alguém decida conscientemente de onde ele vem — que é exatamente o momento em que a decisão deve ser tomada.

**Por que ignorar explicitamente (MS-04/MS-07):** `ignore = true` é documentação executável. Ao ler o mapper, o revisor (humano ou agente) vê que `tenantId` **não** vem do request — e vê isso no código, não em uma convenção não escrita. É a diferença entre "esqueceram" e "decidiram".

**Por que sem regra de negócio (MS-05/MS-06):** um mapper com lógica vira uma camada de domínio escondida, invisível ao teste de serviço e ao agente que lê a spec. Além disso, mappers são frequentemente reutilizados entre casos de uso; uma regra dentro deles se aplicaria a contextos para os quais não foi pensada.

**Por que não reflexão (comparação com A1):** além do custo de runtime em R-04, o mapeamento por reflexão baseado em nomes de campo cria um acoplamento **invisível**: renomear um campo da entidade quebra o mapeamento silenciosamente, sem erro de compilação e sem falha de teste, a menos que exista um teste específico para aquele campo. Sob R-01, isso é inaceitável — o campo silenciosamente não mapeado pode ser justamente o que separa dados de tenants.

## Alternativas consideradas

### A1 — ModelMapper / Dozer (mapeamento por reflexão em runtime)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Zero código de mapeamento para casos simples; configuração mínima; mapeia por convenção de nome. |
| **Contras** | Erro apenas em runtime; renomeação de campo quebra silenciosamente; custo de reflexão em todo mapeamento (R-04); comportamento "mágico" difícil de depurar; mapeamento implícito pode copiar campos que **não deveriam** ser copiados — violando R-01 diretamente. |
| **Por que foi descartada** | O mapeamento implícito é uma falha de segurança em potencial: um campo novo na entidade com nome coincidente seria automaticamente exposto ou atribuído. A decisão adotada exige o oposto — mapeamento explícito e verificado. |

### A2 — Mapeamento manual (métodos estáticos ou construtores)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Explícito, sem dependência, sem geração; totalmente sob controle; fácil de depurar. |
| **Contras** | Volume alto de código repetitivo (~15 entidades × 3 DTOs); campo novo esquecido não gera erro algum; propenso a copiar-colar com erro entre features; revisão de código vira conferência campo a campo. |
| **Por que foi descartada** | O modo de falha (campo esquecido em silêncio) é exatamente o que se quer eliminar, e a revisão humana não é confiável para detectá-lo em escala. MS-03 resolve isso automaticamente. |

### A3 — Construtor do DTO recebendo a entidade

| Aspecto | Avaliação |
|---|---|
| **Prós** | Sem classe extra; conversão próxima ao DTO; sem dependência. |
| **Contras** | Acopla o DTO à entidade — invertendo a direção da dependência que [ADR-013](ADR-013-dto.md) estabelece; o DTO deixa de ser um contrato puro; impossível para o sentido inverso (request → entidade); dificulta o uso de projeções (DT-11). |
| **Por que foi descartada** | Contamina o contrato com conhecimento de persistência, anulando parte do benefício da fronteira. |

### A4 — Records canônicos com projeção direta do JPQL

| Aspecto | Avaliação |
|---|---|
| **Prós** | A consulta produz o DTO diretamente (`SELECT new com.devtime...Response(...)`); sem mapeamento; máxima eficiência. |
| **Contras** | Funciona apenas para leitura; acopla o DTO ao JPQL (renomear campo do DTO quebra a query em runtime, não em compilação); inviável para grafos; não serve para escrita. |
| **Por que foi descartada como padrão** | Permanece **complementar** para listagens via projeção (DT-11), mas não substitui o mapeamento geral, especialmente no sentido request → entidade. |

### A5 — Bibliotecas alternativas de geração (JMapper, Selma, Orika)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Também geram código; algumas com sintaxe alternativa. |
| **Contras** | Comunidades menores; manutenção irregular; integração com Spring e com Lombok menos testada; menos material canônico (afeta R-05). |
| **Por que foram descartadas** | MapStruct é o padrão de fato no ecossistema Spring, com melhor suporte a `record`, melhor integração com Lombok e vastamente mais material — decisivo para geração assistida por IA. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Campo não mapeado **quebra o build** (MS-03) — o principal risco é eliminado. |
| C+02 | Código gerado é Java simples, legível e depurável (R-05). |
| C+03 | Zero reflexão em runtime: performance equivalente a mapeamento manual (R-04). |
| C+04 | `ignore = true` documenta decisões de não-mapeamento (MS-04). |
| C+05 | Suporte nativo a `record` e a construtores imutáveis (R-02). |
| C+06 | Integração natural com Spring por injeção (`componentModel = "spring"`). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Dependência de processador de anotações no build. | Padrão maduro; falha afeta apenas o build, nunca o runtime. |
| C-02 | Erros de compilação do MapStruct podem ser crípticos. | Mitigado por RK-02 e por exemplos canônicos em `backend-rules.md`. |
| C-03 | Ordem dos processadores importa (MapStruct depois do Lombok). | Configurada uma vez no `pom.xml`; falha é imediata e óbvia. |
| C-04 | Mais uma abstração a aprender. | A superfície usada é pequena e padronizada. |
| C-05 | Mappers podem virar depósito de lógica se MS-05/MS-06 não forem aplicadas. | Verificado por revisão e por ArchUnit. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Mapeamentos com lógica condicional complexa ficam ilegíveis; nesses casos, um método `default` explícito na interface é preferível. |
| L-02 | Não resolve N+1: mapear uma entidade com associação preguiçosa a dispara. A solução é a projeção (DT-11), não o mapper. |
| L-03 | O código gerado não é versionado (MS-09), o que dificulta a revisão do resultado exato em PR. |

### Custos

| Item | Custo |
|---|---|
| Dependência | MapStruct 1.6.x (Apache 2.0) |
| Build | Alguns segundos de processamento de anotações |
| Runtime | Zero em relação a mapeamento manual |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Simplicidade do build** (sem processador de anotações) | Verificação em tempo de compilação | Erro no build é infinitamente preferível a bug silencioso em produção. |
| **Conveniência** do mapeamento automático por convenção (A1) | Mapeamento explícito e auditável | Mapeamento implícito pode expor ou atribuir campo indevido. |
| **Controle total** do mapeamento manual | Volume de código e consistência | O controle manual é ilusório em escala; MS-03 dá mais garantia que revisão humana. |
| **Visibilidade do código gerado** no repositório | Repositório limpo | Inspecionável no diretório de build quando necessário. |
| **Flexibilidade** de colocar lógica no mapper | Clareza sobre onde a regra vive | Regra escondida em mapper é a pior localização possível. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `<feature>/mapper` | `<Entidade>Mapper` por feature (MS-02). |
| `<feature>/controller` | Usa o mapper para converter entidade/projeção → resposta. |
| `<feature>/service` | Usa o mapper para converter request → entidade; retorna entidades ou projeções. |
| `shared/mapper` | Conversores comuns (ex.: minutos → `HH:MM`) declarados em `uses`. |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/backend.md` §9 | Padrão de mapeamento |
| `docs/ai/backend-rules.md` | `BR-100` a `BR-119` |
| `docs/ai/review-checklist.md` | Verificação de `ignore` explícito |

| Spec dependente | Relação |
|---|---|
| Todas as specs | Seção "Mappers" lista os artefatos a criar (SP-09) |

| ADR relacionado | Relação |
|---|---|
| [ADR-013](ADR-013-dto.md) | Origem da necessidade |
| [ADR-004](ADR-004-java21.md) | Suporte a `record` |
| [ADR-018](ADR-018-auditing.md) | Campos de auditoria sempre ignorados na entrada |

## Impacto no banco

Não se aplica, porque o mapeamento ocorre inteiramente em memória. Efeito indireto (L-02): mapear uma entidade com associações preguiçosas dispara consultas adicionais — motivo pelo qual listagens usam projeção (DT-11) e não a entidade completa.

## Impacto na API

Não se aplica ao contrato, porque o contrato é definido pelos DTOs ([ADR-013](ADR-013-dto.md)). Efeito indireto: MS-03 garante que todo campo do contrato tenha origem definida, eliminando campos que sempre retornariam `null` por esquecimento — uma classe de bug de contrato.

## Impacto no Frontend

Não se aplica, porque o mapeamento é interno ao backend. Efeito indireto positivo: a garantia de MS-03 reduz a incidência de campos nulos inesperados nas respostas, o que diminui o tratamento defensivo necessário no cliente.

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Build | Processador de anotações configurado no `maven-compiler-plugin`, com Lombok **antes** de MapStruct. |
| CI | Compilação já cobre a verificação de MS-03; nenhum passo adicional. |
| Runtime | Nenhum: o mapper é uma classe Java comum. |
| Imagem | Impacto desprezível no tamanho do artefato. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | MS-07 é um controle de segurança: impedir que `tenantId` venha do request é a materialização de MT-04 no nível de mapeamento. |
| S-02 | MS-03 impede que um campo novo da entidade seja exposto por acidente — o mapeamento nunca é implícito. |
| S-03 | O código gerado é inspecionável, o que permite auditar exatamente o que é copiado. |
| S-04 | **Multi-tenant:** o mapeamento `*Request` → entidade **nunca** define `tenantId`; ele é preenchido pelo listener JPA a partir do `TenantContext` (MT-06). Um mapper que o defina é violação bloqueante. |
| S-05 | **LGPD:** o mapeamento de resposta controla exatamente quais campos pessoais são expostos, implementando minimização de forma verificável. |
| S-06 | **Auditoria:** campos de auditoria são sempre `ignore` na entrada e preenchidos pelo listener ([ADR-018](ADR-018-auditing.md)). |

## Performance

| # | Consideração |
|---|---|
| P-01 | Código gerado é atribuição direta de campos: mesma performance de mapeamento manual. |
| P-02 | Zero reflexão, zero introspecção em runtime — diferença mensurável em relação a ModelMapper sob carga. |
| P-03 | Uma alocação por objeto mapeado; irrelevante frente ao custo de I/O. |
| P-04 | Mapear coleções grandes tem custo linear; listagens são paginadas (`ART-073`), limitando o tamanho. |
| P-05 | L-02 é a única armadilha de performance; endereçada por projeções. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | O número de mappers cresce linearmente com o número de features, sem interdependência. |
| E-02 | MS-11 preserva as fronteiras entre features, mantendo aberta a extração futura de módulos. |
| E-03 | O tempo de build cresce com o número de mappers, mas permanece na casa de segundos. |
| E-04 | Nenhum estado compartilhado: mappers são *stateless* e thread-safe. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | `ReportingPolicy` configurada como `WARN` em vez de `ERROR`, anulando a garantia | Média | Alto | **Alta** |
| RK-02 | Erro de compilação críptico bloqueando o desenvolvimento | Média | Baixo | Baixa |
| RK-03 | Lógica de negócio migrando para o mapper | Média | Médio | Média |
| RK-04 | Mapper definindo `tenantId` ou campo de auditoria | Baixa | Crítico | **Alta** |
| RK-05 | Ordem incorreta dos processadores de anotação quebrando o build | Baixa | Baixo | Baixa |
| RK-06 | Mapeamento de entidade com associação preguiçosa causando N+1 | Média | Médio | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | Política definida globalmente na configuração do compilador; teste que introduz um campo não mapeado e verifica que a compilação falha (executado uma vez, documentado) | Configuração revisada |
| RK-02 | Exemplos canônicos em `backend-rules.md`; mensagens de erro do MapStruct documentadas nos "erros comuns" (§17 daquele documento) | Documentação |
| RK-03 | MS-05/MS-06; regra ArchUnit proibindo injeção de `*Repository` e `*Service` em classes `*Mapper` | ArchUnit |
| RK-04 | MS-07 com `ignore` explícito; teste que verifica que o `tenantId` da entidade criada vem do contexto, não do request | Teste de tenancy |
| RK-05 | Ordem fixada no `pom.xml` com comentário explicativo; falha é imediata na primeira compilação | Build |
| RK-06 | Uso de projeção em listagens (DT-11); teste de contagem de queries (DA-05) | Gate de N+1 |

## Referências

| Fonte | Uso |
|---|---|
| [MapStruct — Reference Guide](https://mapstruct.org/documentation/stable/reference/html/) | Referência da ferramenta |
| [MapStruct — Unmapped target policies](https://mapstruct.org/documentation/stable/reference/html/#configuration-options) | Base de MS-03 |
| [MapStruct — Mapping with records](https://mapstruct.org/documentation/stable/reference/html/#mapping-with-constructors) | Suporte a `record` |
| [Baeldung — MapStruct vs ModelMapper](https://www.baeldung.com/java-performance-mapping-frameworks) | Comparação de performance (A1) |
| `docs/03-architecture/backend.md` §9 | Padrão adotado |
