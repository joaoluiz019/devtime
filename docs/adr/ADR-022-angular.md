# ADR-022 — Angular como framework de frontend

## Status

**Aceito** em 2026-07-29.
Fundamenta `ART-090`. Complementado por [ADR-023](ADR-023-standalone-components.md), [ADR-024](ADR-024-signals.md) e [ADR-025](ADR-025-primeng.md).

## Data

2026-07-29

## Contexto

O DevTime Web é uma SPA de aplicação de negócio: formulários densos, tabelas com filtro e paginação, dashboards, máquinas de estado visíveis ao usuário e um cronômetro que precisa sobreviver a recarga de página e troca de aba.

Restrições:

| # | Restrição | Origem |
|---|---|---|
| R-01 | Angular é requisito do projeto | `docs/03-architecture/frontend.md` §4 |
| R-02 | A SPA é servida como estáticos; nenhum estado de negócio no cliente | `architecture.md` §5, `ART-080` |
| R-03 | Toda string visível passa por i18n | `ART-095` |
| R-04 | Acessibilidade é gate de build nas telas principais | RNF-042, gate `G-08` |
| R-05 | Implementação majoritária por agentes de IA | `docs/ai/` |
| R-06 | O access token vive apenas em memória | JW-10 de [ADR-008](ADR-008-jwt.md) |

O tipo de aplicação é decisivo: não é um site de conteúdo (SEO e renderização no servidor não são requisitos), é uma ferramenta autenticada usada diariamente por profissionais.

## Decisão

| # | Regra |
|---|---|
| NG-01 | O frontend usa **Angular na última versão estável**, atualizado conforme o ciclo de releases do framework. |
| NG-02 | A aplicação é uma **SPA servida como arquivos estáticos**. Não há SSR nem SSG no MVP. |
| NG-03 | **100% standalone components**; `NgModule` é proibido ([ADR-023](ADR-023-standalone-components.md), `ART-090`). |
| NG-04 | Estado reativo com **Signals** ([ADR-024](ADR-024-signals.md), `ART-091`). |
| NG-05 | `ChangeDetectionStrategy.OnPush` obrigatório em todos os componentes (`ART-092`). |
| NG-06 | **Reactive Forms tipados**; *template-driven forms* são proibidos. |
| NG-07 | `HttpClient` com `provideHttpClient(withFetch())`; nenhum componente o utiliza diretamente (`ART-094`). |
| NG-08 | Internacionalização com `@angular/localize` (R-03). |
| NG-09 | Roteamento com carregamento sob demanda por feature, com guards de autenticação e de permissão. |
| NG-10 | TypeScript em modo `strict`, com `strictTemplates` habilitado. |
| NG-11 | Testes com **Jest + Testing Library**; E2E com **Playwright**. |
| NG-12 | A atualização de versão maior do Angular é planejada e executada com `ng update`, dentro de uma janela por ciclo de release. |

## Motivação

**Por que Angular, tecnicamente (além de R-01):**

| Característica | Valor para este produto |
|---|---|
| Framework completo e opinativo | Roteamento, HTTP, formulários, i18n e testes vêm da mesma fonte, versionados juntos. Não há decisão de biblioteca por área, o que reduz drasticamente a variabilidade de código gerado por agentes (R-05). |
| Reactive Forms tipados | O produto é dominado por formulários com validação complexa; tipagem forte sobre o valor do formulário elimina uma classe inteira de erro. |
| Injeção de dependências nativa | `AuthStore`, `TimerStore` e serviços de API são injetáveis e substituíveis em teste sem ferramenta adicional. |
| Interceptors HTTP | A cadeia de autenticação, tratamento de erro e refresh de token ([ADR-009](ADR-009-refresh-token.md)) é implementada em um ponto, de forma transparente aos componentes. |
| CLI e `ng update` | Atualização de versão maior com *schematics* automáticos; a migração é assistida, não manual. |
| Estrutura imposta | Convenção forte de nomes e organização — determinante para R-05, porque reduz o espaço de decisão do agente. |

**Por que SPA sem SSR (NG-02):** a aplicação inteira está atrás de autenticação; não há conteúdo indexável. SSR adicionaria um servidor Node ao runtime — mais um contêiner, mais uma classe de falha, mais complexidade de deploy — para melhorar uma métrica (primeira renderização) que importa pouco em uma ferramenta de uso diário, aberta e mantida aberta. Além disso, SSR interage mal com R-06: o access token em memória não existe no servidor, o que exigiria tratamento especial da primeira renderização.

**Por que a estrutura opinativa importa mais aqui do que em outros projetos (R-05):** quando o código é produzido majoritariamente por agentes, a **variabilidade** é o principal inimigo da manutenibilidade. Um framework que impõe uma forma de fazer cada coisa produz código homogêneo; um ecossistema de bibliotecas avulsas produz cinco padrões diferentes para o mesmo problema, dependendo do exemplo que o agente encontrou.

## Alternativas consideradas

### A1 — React (com Vite ou Next.js)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Maior ecossistema e comunidade; mais material disponível; muito flexível; excelente desempenho; grande oferta de bibliotecas de UI. |
| **Contras** | Não é framework, é biblioteca de UI: roteamento, formulários, HTTP, estado e i18n exigem escolher e integrar bibliotecas separadas, cada uma com ciclo de vida próprio; ausência de padrão canônico gera alta variabilidade de código (crítico sob R-05); formulários com validação complexa exigem biblioteca adicional; sem injeção de dependências nativa. |
| **Por que foi descartada** | Além de R-01, a flexibilidade que é vantagem em times grandes com padrões próprios é desvantagem em produção assistida por IA: cada decisão não tomada pelo framework vira uma decisão que o agente toma sozinho, de forma inconsistente. |

### A2 — Vue 3

| Aspecto | Avaliação |
|---|---|
| **Prós** | Curva de aprendizado suave; reatividade excelente e conceitualmente próxima a Signals; Composition API expressiva; bom desempenho. |
| **Contras** | Ecossistema menor que Angular e React em componentes de aplicação de negócio; menos material canônico em português e em geral; formulários e validação exigem bibliotecas externas; menor adoção corporativa no contexto de aplicações de gestão. |
| **Por que foi descartada** | Além de R-01, o ecossistema de componentes de negócio (tabelas avançadas, seletores de data com fuso, editores) é significativamente mais fraco que o do Angular com PrimeNG ([ADR-025](ADR-025-primeng.md)). |

### A3 — Svelte / SvelteKit

| Aspecto | Avaliação |
|---|---|
| **Prós** | Menor bundle; compilação elimina runtime; reatividade muito elegante; ótimo desempenho. |
| **Contras** | Ecossistema de componentes de negócio limitado; comunidade menor; muito menos material canônico (impacto direto em R-05); menor maturidade em aplicações corporativas de grande porte. |
| **Por que foi descartada** | O ganho de desempenho não é o gargalo do produto (o gargalo é o banco, AQ-01), e a escassez de material afeta diretamente o modelo de produção. |

### A4 — Angular com SSR (Angular Universal)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Primeira renderização mais rápida; melhor para SEO; melhor desempenho percebido em conexões lentas. |
| **Contras** | Servidor Node adicional em runtime; complexidade de deploy e de estado; incompatibilidade natural com token em memória (R-06); nenhum benefício de SEO em aplicação autenticada. |
| **Por que foi descartada** | Custo operacional real em troca de benefício inexistente para o perfil de uso. |

### A5 — Aplicação renderizada no servidor (Thymeleaf, HTMX)

| Aspecto | Avaliação |
|---|---|
| **Prós** | Uma única aplicação e um único deploy; sem duplicação de modelos; muito menos JavaScript. |
| **Contras** | Interatividade rica (cronômetro em tempo real, dashboards, atualização otimista) fica difícil; a API deixaria de ser o único contrato, prejudicando F8; acopla frontend e backend no mesmo artefato; experiência inferior para uso diário intensivo. |
| **Por que foi descartada** | O cronômetro com atualização otimista (AQ-02: feedback percebido < 200 ms) e o dashboard interativo são centrais no produto e pedem uma SPA. |

## Consequências

### Positivas

| # | Consequência |
|---|---|
| C+01 | Um único framework cobre roteamento, HTTP, formulários, i18n e testes. |
| C+02 | Código homogêneo e previsível — determinante sob R-05. |
| C+03 | Interceptors centralizam autenticação, refresh e tratamento de erro. |
| C+04 | Reactive Forms tipados eliminam erros de nome e de tipo em formulários. |
| C+05 | Deploy simples: arquivos estáticos servidos por Nginx ([ADR-020](ADR-020-docker.md)). |
| C+06 | `ng update` torna a atualização de versão maior assistida. |
| C+07 | TypeScript `strict` com `strictTemplates` detecta erro em template, não em runtime. |

### Negativas

| # | Consequência | Aceita porque |
|---|---|---|
| C-01 | Bundle maior que Svelte ou Vue. | Aplicação autenticada de uso diário; carregamento sob demanda e cache mitigam. |
| C-02 | Curva de aprendizado mais íngreme. | Compensada por documentação oficial extensa e por padrões fixados em `docs/ai/frontend-rules.md`. |
| C-03 | Ciclo de releases frequente exige manutenção contínua. | Planejada em NG-12; `ng update` reduz o custo. |
| C-04 | Framework opinativo restringe soluções não convencionais. | É o efeito desejado (R-05). |
| C-05 | Sem SSR, a primeira renderização é mais lenta. | Irrelevante para o perfil de uso; mitigado por *lazy loading*. |

### Limitações

| # | Limitação |
|---|---|
| L-01 | Sem SEO (consequência de NG-02); aceitável porque a aplicação é autenticada. |
| L-02 | Sem funcionamento offline no MVP; PWA é evolução possível, por ADR próprio. |
| L-03 | Atualizações de versão maior são eventos planejados, não contínuos. |

### Custos

| Item | Custo |
|---|---|
| Licença | Zero (MIT) |
| Build | Alguns minutos em produção |
| Manutenção | Uma atualização de versão maior por ciclo de release |

## Trade-offs

| Sacrificado | Em favor de | Justificativa da troca |
|---|---|---|
| **Flexibilidade** de escolher biblioteca por área (React) | Homogeneidade e previsibilidade | Variabilidade é o principal risco de manutenção em produção assistida por IA. |
| **Tamanho do bundle** (Svelte/Vue) | Ecossistema de componentes de negócio | O gargalo de desempenho do produto é o banco, não o bundle. |
| **SEO e primeira renderização** (SSR) | Simplicidade operacional | Aplicação autenticada não tem conteúdo indexável. |
| **Simplicidade** de uma aplicação renderizada no servidor | Interatividade e contrato de API único | Cronômetro e dashboard exigem SPA; a API única viabiliza F8. |
| **Estabilidade de versão** (ciclo mais lento) | Recursos modernos (Signals, standalone) | `ng update` torna a atualização gerenciável. |

## Impacto na arquitetura

| Módulo | Impacto |
|---|---|
| `devtime-frontend/` | Aplicação inteira. |
| `core/` | Interceptors, guards, stores globais (`AuthStore`, `TimerStore`). |
| `features/` | Uma pasta por feature, espelhando a organização do backend ([ADR-027](ADR-027-folder-structure.md)). |
| `shared/` | Componentes e utilitários reutilizáveis. |

| Documento dependente | Relação |
|---|---|
| `docs/03-architecture/frontend.md` | Documento inteiro |
| `docs/ai/frontend-rules.md` | `FR-001` a `FR-199` |
| `docs/05-ui/*` | Telas, componentes e design system |
| `docs/ai/project-constitution.md` §4.10 | ART-090 a ART-095 |

| Spec dependente | Relação |
|---|---|
| Todas as specs | Seção "Componentes Frontend" |

| ADR relacionado | Relação |
|---|---|
| [ADR-023](ADR-023-standalone-components.md) | Standalone |
| [ADR-024](ADR-024-signals.md) | Estado |
| [ADR-025](ADR-025-primeng.md) | Componentes de UI |
| [ADR-026](ADR-026-chartjs.md) | Gráficos |
| [ADR-011](ADR-011-rest-api.md) | Contrato consumido |
| [ADR-008](ADR-008-jwt.md) / [ADR-009](ADR-009-refresh-token.md) | Autenticação no cliente |

## Impacto no banco

Não se aplica, porque o frontend não acessa o banco. Efeito indireto: `ART-094` proíbe componente chamar `HttpClient` diretamente, o que garante que todo acesso a dado passe por um serviço de feature — mantendo o backend como única porta de acesso aos dados.

## Impacto na API

Não se aplica ao contrato, que é definido por [ADR-011](ADR-011-rest-api.md). Dois efeitos indiretos:

| Efeito | Descrição |
|---|---|
| CORS | A SPA em origem distinta exige CORS com `allowCredentials = true` e origens explícitas (`security.md` §8.3). |
| Endpoints de agregação | Telas compostas podem motivar endpoints dedicados (C-02 de [ADR-011](ADR-011-rest-api.md)) para atender AQ-01. |

## Impacto no Frontend

Este ADR **é** a decisão de frontend. Regras consolidadas:

| Item | Regra |
|---|---|
| Componentes | Standalone, `OnPush` |
| Estado | Signals; RxJS restrito a fluxos assíncronos |
| Formulários | Reactive Forms tipados |
| HTTP | Apenas em serviços de feature |
| i18n | `@angular/localize`, sem texto fixo em template |
| Rotas | Carregamento sob demanda, com guards |
| Testes | Jest + Testing Library; Playwright para E2E |
| TypeScript | `strict` e `strictTemplates` |

## Impacto na Infraestrutura

| Item | Impacto |
|---|---|
| Build | Executado no pipeline; artefato estático. |
| Servidor | Nginx em imagem própria ([ADR-020](ADR-020-docker.md)). |
| Configuração | URL da API definida em tempo de execução, não no build — a mesma imagem serve staging e produção. |
| Cache | Assets com hash em cache longo; `index.html` com `no-cache`. |
| Headers | CSP, HSTS e demais headers configurados no Nginx (`security.md` §8.2). |
| CDN | Opcional; a aplicação é totalmente estática. |

## Segurança

| # | Consideração |
|---|---|
| S-01 | Angular escapa interpolação por padrão, mitigando XSS (OWASP A03). `innerHTML` com conteúdo do usuário é proibido. |
| S-02 | O access token vive em memória (R-06); persistir é bug bloqueante. |
| S-03 | A CSP restringe origens de script; a aplicação não usa `eval` nem scripts inline. |
| S-04 | Guards de rota são **usabilidade**, não segurança: toda autorização é verificada no servidor (RB-14 de [ADR-010](ADR-010-role-permission.md)). |
| S-05 | Dependências de npm são verificadas no pipeline; CVE `HIGH`/`CRITICAL` bloqueia (`ART-103`). |
| S-06 | **Multi-tenant:** o frontend **nunca** envia `tenantId`; a troca de tenant limpa todo o estado em memória, evitando exibir dados do tenant anterior. |
| S-07 | **LGPD:** nenhum dado pessoal é persistido em `localStorage` ou `IndexedDB`; o estado é de sessão. |
| S-08 | **Auditoria:** as ações do usuário são auditadas no servidor; o frontend não mantém trilha própria. |

## Performance

| # | Consideração |
|---|---|
| P-01 | `OnPush` + Signals reduzem drasticamente as verificações de mudança. |
| P-02 | Carregamento sob demanda por rota mantém o bundle inicial pequeno. |
| P-03 | AQ-02 (feedback < 200 ms no cronômetro) é atendida por atualização otimista com reconciliação. |
| P-04 | AQ-01 (dashboard p95 < 800 ms) depende predominantemente do backend; o frontend evita requisições em cascata. |
| P-05 | Orçamento de tamanho de bundle configurado no build, falhando se excedido. |
| P-06 | Imagens e ícones otimizados; fontes com `display: swap`. |

## Escalabilidade

| # | Consideração |
|---|---|
| E-01 | Estáticos escalam trivialmente (CDN ou múltiplas réplicas de Nginx). |
| E-02 | Não há estado no servidor de frontend. |
| E-03 | O crescimento em número de telas é absorvido por carregamento sob demanda. |
| E-04 | Listas grandes exigem virtualização, disponível em PrimeNG ([ADR-025](ADR-025-primeng.md)). |

## Riscos

| # | Risco | Probabilidade | Impacto | Severidade |
|---|---|---|---|---|
| RK-01 | Atualização de versão maior quebrar funcionalidades | Média | Médio | Média |
| RK-02 | Bundle crescer além do aceitável | Média | Médio | Média |
| RK-03 | Token persistido em `localStorage` por descuido | Média | Alto | **Alta** |
| RK-04 | Lógica de autorização implementada apenas no cliente | Média | Crítico | **Alta** |
| RK-05 | CVE em dependência transitiva de npm | Alta | Médio | Alta |
| RK-06 | Violação de acessibilidade bloqueando o build tardiamente | Média | Baixo | Baixa |
| RK-07 | Estado do tenant anterior sobreviver à troca de tenant | Baixa | Alto | Média |

## Mitigações

| Risco | Mitigação | Verificação |
|---|---|---|
| RK-01 | NG-12 (janela planejada); `ng update` com schematics; suíte E2E como rede de proteção | Playwright |
| RK-02 | Orçamento de bundle no build, com falha ao exceder; auditoria periódica de dependências | Build |
| RK-03 | Regra explícita; teste E2E que verifica ausência de token em `localStorage` e `sessionStorage` após login | Teste E2E |
| RK-04 | Testes de integração chamando endpoints diretamente com cada papel ([ADR-010](ADR-010-role-permission.md) RK-04) | Suíte de autorização |
| RK-05 | Verificação de dependências no pipeline; Dependabot; gate `G-06` | Pipeline |
| RK-06 | axe-core executado nos testes de componente, não apenas no E2E — falha cedo (gate `G-08`) | Pipeline |
| RK-07 | Troca de tenant limpa todos os stores; teste E2E que troca de tenant e verifica ausência de dados anteriores | Teste E2E |

## Referências

| Fonte | Uso |
|---|---|
| [Angular — Documentation](https://angular.dev/) | Referência oficial |
| [Angular — Release practices e suporte](https://angular.dev/reference/releases) | NG-01, NG-12 |
| [Angular — Security](https://angular.dev/best-practices/security) | S-01 |
| [Angular — Typed Forms](https://angular.dev/guide/forms/typed-forms) | NG-06 |
| [Angular — Internationalization](https://angular.dev/guide/i18n) | NG-08 |
| [Testing Library — Angular](https://testing-library.com/docs/angular-testing-library/intro/) | NG-11 |
| `docs/03-architecture/frontend.md` | Arquitetura detalhada |
