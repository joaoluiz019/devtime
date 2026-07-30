# Regras de Frontend — DevTime

## 1. Objetivo

Estabelecer as regras obrigatórias de implementação do frontend Angular, em formato verificável e acionável por agentes de IA. Cada regra traz o que é proibido e o que é correto.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Regras de implementação de componentes, stores e serviços | Arquitetura de frontend (`03-architecture/frontend.md`) |
| Padrões obrigatórios e proibições | Tokens visuais (`05-ui/design-system.md`) |
| Templates de referência | Especificação de telas (`05-ui/pages.md`) |
| Checklist de implementação de tela | Convenções transversais (`coding-guidelines.md`) |

## 3. Definições

| Termo | Definição |
|---|---|
| **Componente de apresentação** | Sem dependência de serviço; recebe `input()` e emite `output()`. |
| **Componente conectado** | Injeta store; orquestra dados. |
| **Store** | Serviço que encapsula estado com Signals. |
| **Estado de servidor** | Dados carregados por requisição. |
| **Estado de UI** | Situação local da interface. |

---

## 4. Índice de regras

| Faixa | Área | Quantidade |
|---|---|:--:|
| `FR-001`–`FR-019` | Estrutura e organização | 11 |
| `FR-020`–`FR-039` | Componentes | 15 |
| `FR-040`–`FR-059` | Estado e Signals | 13 |
| `FR-060`–`FR-079` | Comunicação com a API | 12 |
| `FR-080`–`FR-099` | Roteamento e navegação | 10 |
| `FR-100`–`FR-119` | Formulários | 13 |
| `FR-120`–`FR-139` | Estilo e design system | 12 |
| `FR-140`–`FR-159` | Acessibilidade | 12 |
| `FR-160`–`FR-179` | Desempenho | 11 |
| `FR-180`–`FR-199` | Testes | 10 |

---

## 5. Estrutura e organização — `FR-001` a `FR-019`

| ID | Regra | Verificação |
|---|---|---|
| FR-001 | 100% dos componentes são standalone; `NgModule` é proibido | Lint |
| FR-002 | A organização é por feature, com subpastas `data`, `pages` e `components` | Revisão |
| FR-003 | `core` é provido uma única vez em `app.config.ts` | Revisão |
| FR-004 | `shared` não depende de `features` | Lint |
| FR-005 | Uma feature não importa de outra feature | Lint |
| FR-006 | Toda feature é carregada por lazy loading | Revisão |
| FR-007 | Arquivo nomeado `kebab-case.tipo.ts` | Lint |
| FR-008 | Seletor de componente usa prefixo `dt-` | Lint |
| FR-009 | Um componente por arquivo | Lint |
| FR-010 | Nenhum arquivo com mais de 300 linhas | Revisão |
| FR-011 | `barrel files` (`index.ts`) são proibidos — prejudicam o tree-shaking | Lint |

---

## 6. Componentes — `FR-020` a `FR-039`

| ID | Regra | Verificação |
|---|---|---|
| FR-020 | `ChangeDetectionStrategy.OnPush` é obrigatório em todos | Lint |
| FR-021 | Entradas usam `input()`; `@Input()` decorado é proibido | Lint |
| FR-022 | Saídas usam `output()`; `@Output()` decorado é proibido | Lint |
| FR-023 | Entrada obrigatória usa `input.required()` | Revisão |
| FR-024 | Nenhum componente injeta `HttpClient` diretamente | Lint |
| FR-025 | Componente de `shared/` nunca injeta store ou serviço de dados | Lint |
| FR-026 | Nenhuma lógica complexa em template; usar `computed` | Revisão |
| FR-027 | Template com mais de 60 linhas vai para arquivo separado | Revisão |
| FR-028 | Membro usado apenas no template é `protected` | Revisão |
| FR-029 | Nenhum texto fixo em template; sempre i18n | Lint |
| FR-030 | Nenhuma chamada de método no template dentro de binding de valor | Lint |
| FR-031 | Toda iteração declara `track` | Lint |
| FR-032 | Nenhum uso de `any` | Lint |
| FR-033 | Nenhum uso de `innerHTML` com conteúdo de usuário | Lint |
| FR-034 | Componente não manipula o DOM diretamente; usar diretiva ou binding | Revisão |

**Template de componente de apresentação:**

```typescript
@Component({
  selector: 'dt-balance-bar',
  standalone: true,
  imports: [ProgressBarModule, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-balance-bar" [attr.aria-label]="ariaLabel()">
      <p-progressBar
        [value]="displayRate()"
        [styleClass]="severityClass()"
        role="progressbar"
        [attr.aria-valuenow]="consumptionRate()"
        aria-valuemin="0"
        aria-valuemax="100" />
      <div class="flex justify-content-between">
        <span>{{ consumedMinutes() | duration }}</span>
        <span [class.dt-text-danger]="isOverage()">
          {{ remainingMinutes() | duration: 'signed' }}
        </span>
      </div>
    </div>
  `
})
export class BalanceBarComponent {
  readonly availableMinutes = input.required<number>();
  readonly consumedMinutes  = input.required<number>();

  protected readonly remainingMinutes = computed(() =>
    this.availableMinutes() - this.consumedMinutes());

  protected readonly consumptionRate = computed(() =>
    this.availableMinutes() > 0
      ? (this.consumedMinutes() / this.availableMinutes()) * 100
      : 0);

  protected readonly displayRate = computed(() =>
    Math.min(this.consumptionRate(), 100));

  protected readonly isOverage = computed(() => this.remainingMinutes() < 0);

  protected readonly severityClass = computed(() => {
    const rate = this.consumptionRate();
    if (rate >= 100) return 'dt-progress-critical';
    if (rate >= 80)  return 'dt-progress-warning';
    if (rate >= 50)  return 'dt-progress-info';
    return 'dt-progress-ok';
  });
}
```

---

## 7. Estado e Signals — `FR-040` a `FR-059`

| ID | Regra | Verificação |
|---|---|---|
| FR-040 | Estado usa Signals; `BehaviorSubject` para estado de UI é proibido | Lint |
| FR-041 | Signal de escrita é privado; a exposição usa `asReadonly()` | Revisão |
| FR-042 | Todo dado derivado usa `computed`, nunca é recalculado no template | Revisão |
| FR-043 | Todo store expõe `loading` e `error` | Revisão |
| FR-044 | Store nunca formata dado para exibição — isso é papel de pipes | Revisão |
| FR-045 | Nenhuma regra de negócio no frontend além de validação de formulário e formatação | Revisão |
| FR-046 | Filtro, paginação e ordenação vivem na URL, não no store | Revisão |
| FR-047 | RxJS é usado apenas para fluxos assíncronos e eventos, nunca para estado | Revisão |
| FR-048 | Toda subscription usa `takeUntilDestroyed()` | Lint |
| FR-049 | Nenhum `subscribe` aninhado | Lint |
| FR-050 | `effect()` nunca altera estado que dispara o próprio efeito | Revisão |
| FR-051 | Trocar de organização limpa todos os stores de feature | Teste |
| FR-052 | Nenhum estado de negócio persiste em `localStorage` | Revisão |

**Exemplo de FR-041 e FR-042:**

```typescript
// ❌ Proibido — signal público de escrita e cálculo no template
readonly contracts = signal<Contract[]>([]);
// template: {{ contracts().filter(c => c.status === 'ACTIVE').length }}

// ✅ Correto
private readonly _contracts = signal<Contract[]>([]);
readonly contracts = this._contracts.asReadonly();
readonly activeCount = computed(() =>
  this._contracts().filter(c => c.status === 'ACTIVE').length);
```

---

## 8. Comunicação com a API — `FR-060` a `FR-079`

| ID | Regra | Verificação |
|---|---|---|
| FR-060 | Uma classe `*Api` por feature, responsável apenas por HTTP | Revisão |
| FR-061 | Tipos de request e response espelham exatamente os DTOs do backend | Revisão |
| FR-062 | Nenhuma transformação de dado na camada API | Revisão |
| FR-063 | Nenhum tratamento de erro na camada API — os interceptors cuidam | Revisão |
| FR-064 | Toda URL parte de `/api/v1`; o host vem do ambiente | Revisão |
| FR-065 | Nenhuma URL construída por concatenação de string sem codificação | Lint |
| FR-066 | O access token vive apenas em memória, nunca em `localStorage` | Revisão |
| FR-067 | O refresh token nunca é acessado por JavaScript (cookie `HttpOnly`) | Revisão |
| FR-068 | Refreshes concorrentes são enfileirados, nunca disparados em paralelo | Teste |
| FR-069 | Nenhum retry automático em operação não idempotente | Revisão |
| FR-070 | Erro `422` é mapeado para os campos do formulário, nunca exibido em toast | Revisão |
| FR-071 | Todo código `DEVTIME-XXXX` possui mensagem localizada mapeada | Teste |

---

## 9. Roteamento e navegação — `FR-080` a `FR-099`

| ID | Regra | Verificação |
|---|---|---|
| FR-080 | Toda rota de feature usa `loadChildren` ou `loadComponent` | Revisão |
| FR-081 | Toda rota autenticada passa por `authGuard` e `tenantSelectedGuard` | Revisão |
| FR-082 | Rota com permissão específica declara `permissionGuard` | Revisão |
| FR-083 | Guards são ergonomia; a autorização real é sempre do backend | Revisão |
| FR-084 | Filtro, página e ordenação são refletidos em query params | Teste |
| FR-085 | Aba ativa é refletida na URL | Revisão |
| FR-086 | Item selecionado em layout lista+detalhe é refletido na URL | Revisão |
| FR-087 | Formulário sujo dispara `unsavedChangesGuard` | Teste |
| FR-088 | A posição de rolagem é restaurada ao navegar de volta | Configuração |
| FR-089 | Rota inexistente leva à página de "não encontrado" dentro do shell | Teste |

---

## 10. Formulários — `FR-100` a `FR-119`

| ID | Regra | Verificação |
|---|---|---|
| FR-100 | Reactive Forms tipados; template-driven é proibido | Lint |
| FR-101 | `NonNullableFormBuilder` é o padrão | Revisão |
| FR-102 | A validação do cliente espelha a do servidor, mas nunca a substitui | Revisão |
| FR-103 | Erro de campo é exibido abaixo do campo, nunca em toast | Revisão |
| FR-104 | O botão de submissão é desabilitado apenas durante o envio, nunca por formulário inválido | Revisão |
| FR-105 | Ao submeter com erros, o foco vai para o primeiro campo inválido | Teste |
| FR-106 | Erro `422` do servidor é mapeado para os campos via `errors[]` | Teste |
| FR-107 | Todo campo possui `<label>` associado por `for`/`id` | Lint |
| FR-108 | Campo obrigatório é marcado visualmente e por `aria-required` | Lint |
| FR-109 | Formulário longo mantém a barra de ações fixa no rodapé | Revisão |
| FR-110 | `Ctrl/Cmd + Enter` submete o formulário | Teste |
| FR-111 | Rascunho é preservado em `sessionStorage` antes de redirecionamento por sessão expirada | Teste |
| FR-112 | Nenhum campo de duração aceita apenas um formato — usar `dt-duration-input` | Revisão |

**Justificativa de FR-104:** desabilitar o botão em formulário inválido esconde do usuário **o que** está errado. O comportamento correto é permitir a tentativa, exibir os erros e mover o foco para o primeiro campo inválido.

---

## 11. Estilo e design system — `FR-120` a `FR-139`

| ID | Regra | Verificação |
|---|---|---|
| FR-120 | Toda cor, espaçamento e tamanho vem de token `--dt-*` | Lint |
| FR-121 | Nenhum valor de cor literal em componente | Lint |
| FR-122 | Nenhum `px` fixo fora dos tokens de dimensão | Revisão |
| FR-123 | Layout usa PrimeFlex; CSS customizado é exceção justificada | Revisão |
| FR-124 | Preferir o componente PrimeNG existente a criar um customizado | Revisão |
| FR-125 | Componente customizado exige justificativa do que o PrimeNG não atende | Revisão |
| FR-126 | Cores de severidade nunca são usadas com outro significado | Revisão |
| FR-127 | Nenhuma informação transmitida apenas por cor | Revisão |
| FR-128 | Duração é sempre exibida em `HH:MM` com fonte monoespaçada | Revisão |
| FR-129 | Nenhum identificador técnico é exibido | Revisão |
| FR-130 | Todo componente é validado nos temas claro e escuro | Revisão |
| FR-131 | `!important` é proibido, salvo sobrescrita justificada de biblioteca | Lint |

---

## 12. Acessibilidade — `FR-140` a `FR-159`

| ID | Regra | Verificação |
|---|---|---|
| FR-140 | Zero violações do axe-core | Teste |
| FR-141 | Todo elemento interativo é alcançável por teclado | Teste |
| FR-142 | Foco visível nunca é suprimido | Lint |
| FR-143 | Todo ícone sem texto possui `aria-label` | Lint |
| FR-144 | Todo campo possui rótulo associado | Lint |
| FR-145 | Erros são anunciados por `aria-live="polite"` | Revisão |
| FR-146 | Diálogo prende o foco e o devolve ao fechar | Teste |
| FR-147 | Hierarquia de cabeçalhos sem saltos | Teste |
| FR-148 | Regiões de marco presentes em todas as páginas | Teste |
| FR-149 | `prefers-reduced-motion` é respeitado | Revisão |
| FR-150 | Alvo tocável tem no mínimo 44×44px | Revisão |
| FR-151 | Tabela possui cabeçalhos associados por `scope` | Teste |

---

## 13. Desempenho — `FR-160` a `FR-179`

| ID | Regra | Verificação |
|---|---|---|
| FR-160 | Bloco pesado usa `@defer` | Revisão |
| FR-161 | Lista com mais de 100 itens usa virtual scroll | Revisão |
| FR-162 | Toda listagem é paginada no servidor | Revisão |
| FR-163 | Campo de busca usa debounce de 300ms | Revisão |
| FR-164 | Requisição pendente é cancelada ao destruir o componente | Lint |
| FR-165 | Ações frequentes usam atualização otimista | Revisão |
| FR-166 | Imagens usam `loading="lazy"` com dimensões definidas | Lint |
| FR-167 | Bundle inicial abaixo de 500 KB gzip | Pipeline |
| FR-168 | Nenhuma dependência pesada importada integralmente | Revisão |
| FR-169 | Nenhum cálculo pesado em `computed` acessado a cada renderização | Revisão |
| FR-170 | Carregamento exibe esqueleto após 200ms, nunca tela em branco | Revisão |

---

## 14. Testes — `FR-180` a `FR-199`

| ID | Regra | Verificação |
|---|---|---|
| FR-180 | Testes consultam por papel, rótulo ou texto — nunca por seletor de CSS | Revisão |
| FR-181 | Todo componente de `shared/` possui teste | Pipeline |
| FR-182 | Toda página principal possui teste de integração com API simulada | Pipeline |
| FR-183 | Todo teste de acessibilidade usa axe-core | Pipeline |
| FR-184 | Nenhum teste depende de rede real | Revisão |
| FR-185 | Nenhum teste usa relógio real; usar temporizadores falsos | Revisão |
| FR-186 | Testes do cronômetro cobrem recarga, hibernação, reconexão e duas abas | Pipeline |
| FR-187 | Nenhum teste usa `setTimeout` para sincronização | Lint |
| FR-188 | Cobertura de pipes, validators e utils acima de 90% | Pipeline |

**Exemplo de FR-180:**

```typescript
// ❌ Proibido — acoplado à implementação
const button = fixture.debugElement.query(By.css('.p-button-primary'));

// ✅ Correto — como o usuário enxerga
const button = await screen.findByRole('button', { name: 'Registrar horas' });
await userEvent.click(button);
expect(await screen.findByText('02:30')).toBeVisible();
```

---

## 15. Checklist de implementação de tela

| # | Passo | Verificação |
|---|---|---|
| 1 | Ler a especificação da tela em `05-ui/pages.md` | Estados, layout e permissões definidos? |
| 2 | Identificar layout, componentes e endpoints | Todos existem? |
| 3 | Criar a rota com guards de permissão | FR-081, FR-082 |
| 4 | Criar a classe `*Api` da feature | FR-060 a FR-064 |
| 5 | Criar o store com `loading`, `error` e `computed` | FR-040 a FR-045 |
| 6 | Implementar a página conectada | FR-020 a FR-034 |
| 7 | Implementar os componentes de apresentação | FR-025 |
| 8 | Implementar todos os estados: normal, carregando, vazio, erro | FR-170 |
| 9 | Implementar o comportamento responsivo | Breakpoints de `layouts.md` |
| 10 | Adicionar todas as strings ao arquivo de i18n | FR-029 |
| 11 | Verificar acessibilidade com axe-core | FR-140 |
| 12 | Escrever testes de componente e integração | FR-180 a FR-188 |
| 13 | Verificar nos temas claro e escuro | FR-130 |
| 14 | Verificar navegação apenas por teclado | FR-141 |
| 15 | Percorrer `review-checklist.md` | Todos os itens |

---

## 16. Erros comuns e correções

| Erro | Por que acontece | Correção |
|---|---|---|
| `@Input()` decorado | Hábito de versões anteriores | `input()` (FR-021) |
| Componente sem `OnPush` | Esquecimento | Obrigatório (FR-020) |
| Signal público de escrita | Conveniência | `asReadonly()` (FR-041) |
| Cálculo no template | Parece simples | `computed` (FR-042) |
| Filtro apenas no store | Parece natural | URL (FR-046) |
| Token no `localStorage` | Padrão comum na web | Memória + cookie (FR-066) |
| Erro de validação em toast | Mais visível | Abaixo do campo (FR-103) |
| Botão desabilitado por formulário inválido | Parece proteger | Permitir tentativa e mostrar erros (FR-104) |
| Cor literal no CSS | Rapidez | Token `--dt-*` (FR-120) |
| Texto fixo em template | Rapidez | i18n (FR-029) |
| Teste por classe CSS | Facilidade | Papel e rótulo (FR-180) |
| Contador local do cronômetro como fonte da verdade | Parece natural | Derivar do servidor (§6.3 de `frontend.md`) |

---

## 17. Casos especiais

| # | Caso | Tratamento |
|---|---|---|
| CE-F-01 | Componente PrimeNG não atende ao requisito | Criar customizado com justificativa registrada (FR-125) |
| CE-F-02 | Necessidade de manipular o DOM | Criar diretiva; nunca no componente (FR-034) |
| CE-F-03 | Estado compartilhado entre duas features | Mover para `core`, nunca importar entre features |
| CE-F-04 | Biblioteca exige `NgModule` | Importar o módulo diretamente no componente standalone |
| CE-F-05 | Cálculo que existe no backend precisa ser exibido em tempo real | Reproduzir apenas a **exibição**; o valor canônico é sempre do servidor |
| CE-F-06 | Sessão expira com formulário preenchido | Preservar rascunho em `sessionStorage` (FR-111) |
| CE-F-07 | Lista extensa em relatório | Virtual scroll + paginação no servidor (FR-161, FR-162) |
| CE-F-08 | Componente precisa de dado de outra feature | Consumir pelo store de `core` ou receber por `input()` |

## 18. Casos de erro

| Situação | Consequência |
|---|---|
| `NgModule` no projeto | Build falha |
| Componente sem `OnPush` | Build falha |
| `HttpClient` injetado em componente | Build falha |
| Texto fixo em template | Build falha |
| Uso de `any` | Build falha |
| Violação do axe-core em tela principal | Build falha |
| Bundle acima do limite | Build falha |
| Cor literal em componente | Build falha |
| Teste por seletor CSS | Rejeitado na revisão |

## 19. Critérios de aceite

| # | Critério |
|---|---|
| CA-01 | Nenhum `NgModule` existe no projeto |
| CA-02 | Todos os componentes usam `OnPush` e Signals |
| CA-03 | Nenhum componente injeta `HttpClient` |
| CA-04 | Nenhum texto fixo fora do sistema de i18n |
| CA-05 | Zero violações do axe-core nas telas do MVP |
| CA-06 | Toda listagem preserva filtro e paginação na URL |
| CA-07 | Bundle inicial abaixo de 500 KB gzip |
| CA-08 | O cronômetro exibe o tempo correto em todos os cenários de resiliência |
| CA-09 | Todos os testes consultam por papel, rótulo ou texto |
| CA-10 | Nenhuma cor ou dimensão fora dos tokens |

## 20. Dependências e impactos

| Documento | Relação |
|---|---|
| `project-constitution.md` | ART-090 a ART-095 |
| `03-architecture/frontend.md` | Define a arquitetura implementada |
| `05-ui/design-system.md` | Fonte dos tokens obrigatórios |
| `05-ui/components.md` | Catálogo de componentes |
| `05-ui/pages.md` | Especificação das telas |
| `coding-guidelines.md` | Convenções transversais |

**Impacto:** alterar um padrão de estado ou a camada HTTP afeta todas as features e exige revisão dos testes de integração.
