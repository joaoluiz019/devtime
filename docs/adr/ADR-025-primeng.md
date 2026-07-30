# ADR-025 — PrimeNG e PrimeFlex como biblioteca de componentes e utilitários de layout

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-093`. Depende de [ADR-022](ADR-022-angular.md).

## Data

2026-07-29

## Contexto

O DevTime Web é uma aplicação de gestão: tabelas com filtro, ordenação, seleção e paginação no servidor; formulários densos; seletores de data com fuso; máscaras; diálogos; menus; notificações; upload de arquivo; árvores e listas virtualizadas.

Construir esses componentes internamente consumiria a maior parte do orçamento do MVP e produziria componentes com pior acessibilidade e menos casos de borda tratados do que uma biblioteca madura.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Componentes de UI usam PrimeNG; layout usa PrimeFlex; CSS customizado é exceção justificada | `ART-093` |
| R-02 | Acessibilidade é gate de build nas telas principais | RNF-042, gate `G-08` |
| R-03 | Toda string visível passa por i18n | `ART-095` |
| R-04 | Componentes standalone; `NgModule` proibido em código próprio | [ADR-023](ADR-023-standalone-components.md) SC-08 |
| R-05 | Design system com tokens `--dt-*` | `docs/05-ui/design-system.md` |
| R-06 | Implementação majoritária por agentes de IA | `docs/ai/` |

## Decisão

| # | Regra |
|---|---|
| PN-01 | Os componentes de UI vêm do **PrimeNG**, na versão compatível com a versão do Angular adotada. |
| PN-02 | O layout usa **PrimeFlex** (utilitários de grid, espaçamento e flexbox). |
| PN-03 | Os gráficos usam **Chart.js via `p-chart`** ([ADR-026](ADR-026-chartjs.md)). |
| PN-04 | CSS customizado é **exceção justificada** (`ART-093`): antes de escrevê-lo, verifica-se se PrimeFlex ou uma propriedade de tema do PrimeNG resolvem. |
| PN-05 | A identidade visual é aplicada por **tema customizado com tokens de design** (`--dt-*`), nunca por sobrescrita de seletores internos do PrimeNG. |
| PN-06 | Componentes do PrimeNG **não** são usados diretamente em telas quando houver regra de apresentação do produto: nesses casos, são encapsulados em um componente `dt-*` da pasta `shared` (ex.: `dt-duration-input`, `dt-tenant-aware-table`). |
| PN-07 | Todo texto exibido por componente PrimeNG (rótulos de paginação, mensagens vazias, botões de diálogo) passa por i18n (R-03). |
| PN-08 | Tabelas usam **paginação no servidor** (`lazy`), coerente com `ART-073`. Carregar tudo no cliente e paginar localmente é proibido. |
| PN-09 | A acessibilidade dos componentes é **verificada**, não presumida: axe-core roda nos testes de componente das telas principais (R-02). |
| PN-10 | Atualizações de versão maior do PrimeNG acompanham a atualização do Angular, dentro da mesma janela planejada (NG-12). |
| PN-11 | Componentes do PrimeNG são importados individualmente no `imports` do componente standalone que os usa (R-04, SC-09). |

## Motivação

**Por que uma biblioteca de componentes, e não componentes próprios:** uma tabela com ordenação, filtro, seleção, paginação no servidor, redimensionamento de coluna, exportação e acessibilidade completa é um projeto de semanas. O produto tem várias telas assim. Construir internamente consumiria o orçamento do MVP em infraestrutura de UI em vez de em domínio.

**Por que PrimeNG especificamente:**

| Característica | Valor |
|---|---|
| Cobertura de componentes de negócio | Tabela avançada, seletor de data com fuso, máscaras, árvore, upload, diálogo, menu — praticamente tudo que o produto precisa, de uma única fonte |
| Alinhamento com Angular | Biblioteca nativa (não wrapper), acompanhando o ciclo de releases |
| Tematização por tokens | Compatível com R-05: a identidade é aplicada por variáveis, sem tocar seletores internos |
| PrimeFlex integrado | Utilitários de layout consistentes com os componentes, evitando misturar dois sistemas de espaçamento |
| Chart.js integrado (`p-chart`) | Gráficos sem integrar uma segunda biblioteca manualmente |
| Volume de material | Ampla documentação e exemplos, o que melhora a geração por agentes (R-06) |

**Por que encapsular em componentes `dt-*` (PN-06):** as durações do produto são formatadas em `HH:MM` (`ART-035`) e armazenadas em minutos inteiros (`ART-034`). Usar um `p-inputNumber` cru em cada tela replicaria a conversão em dezenas de lugares. Um `dt-duration-input` concentra a regra de apresentação em um ponto testável. O critério é objetivo: **existe regra de apresentação do produto? Encapsula. É só um botão? Usa direto.**

**Por que tema por tokens e não sobrescrita de seletores (PN-05):** sobrescrever classes internas do PrimeNG cria acoplamento com a implementação da biblioteca, e cada atualização de versão quebra estilos de forma silenciosa. Tokens são a API pública de tematização e sobrevivem a atualizações.

**Por que paginação no servidor (PN-08):** carregar todos os work logs de um tenant no cliente violaria `ART-073` e AQ-01, e não escalaria com 100k registros. A tabela precisa operar em modo `lazy` desde a primeira tela — retrofit é caro.

**Por que verificar acessibilidade (PN-09):** bibliotecas maduras têm boa acessibilidade **de base**, mas o uso incorreto (rótulo ausente, contraste insuficiente no tema customizado, ordem de foco quebrada) a destrói. R-02 exige verificação automatizada, não confiança.

## Alternativas consideradas

### A1 — Angular Material

| Aspecto | Avaliação |
|---|---|
| **Prós** | Mantido pela própria equipe do Angular; acessibilidade excelente (CDK); qualidade e consistência altas; integração perfeita com o framework. |
| **Contras** | Cobertura menor de componentes de aplicação de negócio: a tabela do Material é substancialmente mais básica que a do PrimeNG (sem filtro por coluna, sem redimensionamento, sem exportação prontos), exigindo construção sobre o CDK; Material Design impõe uma identidade visual forte, mais difícil de adaptar a uma identidade própria; sem utilitários de layout equivalentes ao PrimeFlex. |
| **Por que foi descartada** | O esforço para elevar a tabela do Material ao nível exigido pelas telas de work logs, tickets e relatórios recriaria parte do que o PrimeNG já entrega. O CDK, no entanto, permanece disponível como recurso pontual, e é a alternativa preferencial caso o PrimeNG seja substituído no futuro. |

### A2 — Componentes próprios sobre Angular CDK

| Aspecto | Avaliação |
|---|---|
| **Prós** | Controle total sobre marcação, estilo e comportamento; identidade visual exatamente como desejada; sem dependência de versão de biblioteca de UI; bundle mínimo. |
| **Contras** | Semanas de desenvolvimento antes da primeira tela funcional; acessibilidade e casos de borda por nossa conta; manutenção permanente; alta variabilidade quando gerado por agentes (R-06). |
| **Por que foi descartada** | Investir o orçamento do MVP em infraestrutura de UI em vez de domínio é a troca errada para um produto cujo diferencial é a regra de negócio. |

### A3 — Tailwind CSS + biblioteca headless (Headless UI, Radix, Spartan)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Liberdade visual total; componentes acessíveis sem estilo imposto; bundle enxuto; tendência atual do mercado. |
| **Contras** | Componentes complexos (tabela avançada com filtro e paginação no servidor) ainda precisam ser construídos; o ecossistema headless para Angular é significativamente menos maduro que para React; classes utilitárias no template aumentam a variabilidade de código gerado (R-06); dois sistemas a aprender (utilitários + headless). |
| **Por que foi descartada** | A imaturidade do ecossistema headless em Angular deixaria a tabela — o componente mais crítico do produto — sem solução pronta. |

### A4 — Bootstrap / ng-bootstrap

| Aspecto | Avaliação |
|---|---|
| **Prós** | Amplamente conhecido; grid maduro; visual neutro e fácil de customizar. |
| **Contras** | Cobertura de componentes de negócio limitada (sem tabela avançada, sem seletor de data completo); orientado a sites, não a aplicações de gestão; exigiria complementar com outras bibliotecas. |
| **Por que foi descartada** | Cobertura insuficiente para o tipo de aplicação. |

### A5 — Combinar múltiplas bibliotecas (a melhor de cada categoria)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Melhor componente disponível para cada necessidade. |
| **Contras** | Múltiplos sistemas de estilo conflitantes; múltiplos ciclos de atualização; bundle inflado; inconsistência visual e de comportamento; cada biblioteca com sua abordagem de acessibilidade e i18n. |
| **Por que foi descartada** | Consistência de UI é requisito de produto. Uma fonte única de componentes é o que a garante. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Telas complexas construídas em horas, não semanas. |
| C+02 | Consistência visual e comportamental por construção. |
| C+03 | Acessibilidade de base fornecida pela biblioteca (verificada por PN-09). |
| C+04 | PrimeFlex evita um segundo sistema de layout. |
| C+05 | `p-chart` entrega gráficos sem integração manual ([ADR-026](ADR-026-chartjs.md)). |
| C+06 | Ampla base de exemplos, favorecendo geração por agentes (R-06). |
| C+07 | Tematização por tokens preserva a identidade através de atualizações (PN-05). |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Dependência externa relevante, com ciclo de versões próprio. | Alinhado ao ciclo do Angular (PN-10). |
| C-02 | Bundle maior que o de componentes próprios. | Importação individual (PN-11) e *tree shaking* limitam o impacto. |
| C-03 | Personalização visual profunda é mais difícil que com headless. | A identidade do produto é funcional, não visualmente disruptiva. |
| C-04 | Comportamentos padrão da biblioteca nem sempre coincidem com o desejado. | Encapsulamento em `dt-*` (PN-06) resolve nos casos que importam. |
| C-05 | Atualizações de versão maior podem quebrar estilos e APIs. | PN-05 reduz o risco; suíte E2E detecta regressões. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | A acessibilidade da biblioteca é boa, mas não perfeita; PN-09 é obrigatório. |
| L-02 | Alguns componentes trazem comportamentos difíceis de alterar sem recorrer a CSS de sobrescrita. |
| L-03 | O idioma padrão dos componentes é inglês; a tradução é responsabilidade nossa (PN-07). |

### Custos

| Item | Custo |
|---|---|
| Licença | Zero para o PrimeNG open source (MIT) |
| Bundle | Dezenas de KB por componente importado |
| Manutenção | Uma atualização de versão maior por ciclo do Angular |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Controle total** sobre marcação e estilo | Velocidade de entrega e cobertura de componentes | O diferencial do produto é o domínio, não a UI. |
| **Bundle mínimo** | Funcionalidade pronta e acessível | Aplicação autenticada de uso diário tolera bundle maior. |
| **Liberdade visual** (headless + utilitários) | Consistência e maturidade em Angular | O ecossistema headless em Angular não cobre a tabela avançada. |
| **Independência de biblioteca** | Orçamento do MVP | Encapsulamento em `dt-*` limita o raio de acoplamento. |
| **Qualidade de acessibilidade** do Angular Material | Cobertura de componentes de negócio | Compensada por verificação automatizada (PN-09). |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `shared/ui` | Componentes `dt-*` que encapsulam PrimeNG onde há regra de apresentação (PN-06). |
| `styles/` | Tema customizado com tokens `--dt-*` (PN-05). |
| `features/*` | Importam componentes do PrimeNG individualmente (PN-11). |
| Testes | axe-core nos testes de componente das telas principais (PN-09). |

| Documento dependente | Relação |
|---|---|
| `docs/ai/project-constitution.md` | ART-093 |
| `docs/05-ui/design-system.md` | Tokens e tema |
| `docs/05-ui/components.md` | Catálogo de componentes `dt-*` |
| `docs/03-architecture/frontend.md` §4 | Stack |
| `docs/ai/frontend-rules.md` | `FR-120` a `FR-139` |

| Spec dependente | Relação |
|---|---|
| Todas as specs com UI | Seção "Componentes Frontend" |

| ADR relacionado | Relação |
|---|---|
| [ADR-022](ADR-022-angular.md) | Framework |
| [ADR-023](ADR-023-standalone-components.md) | Importação individual (SC-08, PN-11) |
| [ADR-026](ADR-026-chartjs.md) | `p-chart` |
| [ADR-011](ADR-011-rest-api.md) | Paginação no servidor (PN-08) |

## Impacto no banco

Não se aplica, porque a biblioteca de UI não toca persistência. Efeito indireto: PN-08 (paginação no servidor) determina que toda listagem seja consultada com `LIMIT`/`OFFSET` e ordenação indexada, o que exige índices de suporte no banco.

## Impacto na API

| Item | Impacto |
|---|---|
| Paginação | A tabela em modo `lazy` consome o envelope padrão de paginação (AP-07 de [ADR-011](ADR-011-rest-api.md)). |
| Ordenação | Os parâmetros `sort` enviados pela tabela precisam corresponder a campos ordenáveis suportados e indexados. |
| Filtros | Filtros de coluna mapeiam para parâmetros nomeados (AP-08); a tabela **não** pode gerar filtros arbitrários. |

## Impacto no Frontend

Este ADR **é** uma decisão de frontend:

| Item | Regra |
|---|---|
| Componentes | PrimeNG, importados individualmente |
| Layout | PrimeFlex; CSS customizado é exceção |
| Tema | Tokens `--dt-*`; sem sobrescrita de seletores internos |
| Encapsulamento | `dt-*` quando houver regra de apresentação |
| Tabelas | Modo `lazy` obrigatório |
| i18n | Todos os textos dos componentes traduzidos |
| Acessibilidade | Verificada por axe-core |

## Impacto na Infraestrutura

Não se aplica diretamente. Efeito indireto: o tamanho dos artefatos estáticos aumenta, o que é absorvido por compressão e cache de longo prazo em assets com hash ([ADR-020](ADR-020-docker.md)).

## Segurança

| # | Consideração |
|---|---|
| S-01 | Componentes que renderizam HTML (editores, `p-tooltip` com HTML) exigem cuidado: conteúdo do usuário **nunca** é renderizado como HTML sem sanitização (OWASP A03). |
| S-02 | Dependências do PrimeNG são verificadas no pipeline; CVE `HIGH`/`CRITICAL` bloqueia (`ART-103`). |
| S-03 | O componente de upload valida tipo e tamanho no cliente, mas a validação **autoritativa** é do servidor ([ADR-038](ADR-038-file-storage.md)). |
| S-04 | Nenhum componente armazena dado em `localStorage` por conta própria; configurações de tabela persistidas pelo componente são desabilitadas quando contiverem dado do tenant. |
| S-05 | **Multi-tenant:** componentes não conhecem tenant; o isolamento é do servidor. A troca de tenant limpa o estado das tabelas (SG-11 de [ADR-024](ADR-024-signals.md)). |
| S-06 | **LGPD:** exportação de tabela no cliente é desabilitada quando os dados forem sensíveis; a exportação oficial passa pelo servidor, com auditoria ([ADR-036](ADR-036-report-generation.md)). |
| S-07 | **Auditoria:** ações da UI são auditadas no servidor; o componente não registra nada. |

## Performance

| # | Consideração |
|---|---|
| P-01 | PN-08 evita carregar grandes volumes no cliente. |
| P-02 | Listas muito longas usam virtualização (`virtualScroll`). |
| P-03 | Importação individual (PN-11) mantém o *tree shaking* eficaz. |
| P-04 | Componentes pesados (editor, gráficos) são carregados sob demanda pela rota. |
| P-05 | O tema é CSS estático, sem custo de runtime. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | A biblioteca cobre as necessidades previstas até F8 sem substituição. |
| E-02 | Novos componentes são importações adicionais, sem alteração estrutural. |
| E-03 | O encapsulamento em `dt-*` (PN-06) limita o custo de uma eventual substituição da biblioteca aos componentes encapsulados. |
| E-04 | Tabelas com centenas de milhares de registros funcionam por paginação no servidor. |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Atualização de versão maior quebrar estilos e APIs | Média | Médio | Média |
| RK-02 | Sobrescrita de seletores internos criando acoplamento frágil | **Alta** | Médio | Alta |
| RK-03 | Tabela usada sem modo `lazy`, carregando tudo no cliente | Média | Alto | Alta |
| RK-04 | Regressão de acessibilidade no tema customizado (contraste) | Média | Médio | Média |
| RK-05 | Texto de componente não traduzido chegando à produção | Média | Baixo | Baixa |
| RK-06 | Bundle crescer além do orçamento | Média | Médio | Média |
| RK-07 | Dependência da biblioteca dificultar substituição futura | Baixa | Alto | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | PN-10 (janela planejada junto com Angular); suíte E2E com verificação visual das telas principais | Playwright |
| RK-02 | PN-05; revisão bloqueia CSS que referencie classes internas do PrimeNG; lint de seletores proibidos | `review-checklist.md` |
| RK-03 | PN-08; teste que verifica a chamada com parâmetros de paginação; teste de carga com volume realista | Teste de integração |
| RK-04 | axe-core nos testes de componente (PN-09), incluindo verificação de contraste | Gate `G-08` |
| RK-05 | `ART-095`; lint que detecta texto fixo em template; revisão | Lint |
| RK-06 | Orçamento de bundle no build, com falha ao exceder | Build |
| RK-07 | PN-06 concentra o acoplamento nos componentes `dt-*`; o CDK do Angular é a alternativa mapeada (A1) | Revisão de arquitetura |

## Referências

| Fonte | Uso |
|---|---|
| [PrimeNG — Documentation](https://primeng.org/) | Referência da biblioteca |
| [PrimeNG — Theming](https://primeng.org/theming) | PN-05 |
| [PrimeFlex](https://primeflex.org/) | PN-02 |
| [Angular Material / CDK](https://material.angular.io/) | Alternativa A1 e recurso pontual |
| [WCAG 2.1](https://www.w3.org/TR/WCAG21/) | Critério de PN-09 |
| [axe-core](https://github.com/dequelabs/axe-core) | Verificação automatizada |
| `docs/05-ui/design-system.md` | Tokens `--dt-*` |
