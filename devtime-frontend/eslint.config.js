// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

/**
 * Lint do frontend (frontend-rules.md).
 *
 * As regras abaixo são as que `frontend-rules.md` marca como verificáveis por lint. Regras marcadas
 * como "Revisão" naquele documento não aparecem aqui: forçá-las com heurística produziria falso
 * positivo e levaria a desabilitá-las localmente, que é pior que verificá-las em revisão.
 *
 * Violação é erro, não aviso: §18 de frontend-rules.md determina que estas condições quebrem o build.
 */
module.exports = tseslint.config(
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // FR-008 / convenção do glossário: seletor sempre com prefixo dt-.
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'dt', style: 'kebab-case' },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'dt', style: 'camelCase' },
      ],
      // FR-020 / ART-092: OnPush é obrigatório em todos os componentes.
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      // FR-021 / FR-022: entradas e saídas baseadas em Signals; decoradores são proibidos.
      '@angular-eslint/prefer-signals': 'error',
      '@angular-eslint/no-input-rename': 'error',
      '@angular-eslint/no-output-rename': 'error',
      '@angular-eslint/no-output-native': 'error',
      // FR-001 / ART-090 / P-08: NgModule é proibido.
      '@angular-eslint/prefer-standalone': 'error',
      // FR-032: nenhum uso de any.
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // FR-048: toda subscription usa takeUntilDestroyed; o lint garante o import correto.
      '@typescript-eslint/no-floating-promises': 'off',
      // CG-09: código morto é removido, não comentado.
      'no-unused-private-class-members': 'error',
      // ER-01: nunca capturar exceção sem tratar.
      'no-empty': ['error', { allowEmptyCatch: false }],
      // FR-033: innerHTML com conteúdo de usuário é vetor de XSS.
      'no-restricted-properties': [
        'error',
        {
          property: 'innerHTML',
          message: 'FR-033: innerHTML é proibido; use binding ou uma diretiva dedicada.',
        },
      ],
      // FR-052 / FR-066: nenhum estado de negócio nem token em localStorage.
      'no-restricted-globals': [
        'error',
        {
          name: 'localStorage',
          message:
            'FR-052/FR-066: localStorage é proibido. O access token vive em memória e o refresh em cookie HttpOnly.',
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {
      // FR-031: toda iteração declara track.
      '@angular-eslint/template/prefer-control-flow': 'error',
      // FR-029 / ART-095: nenhum texto fixo em template.
      //
      // `ignoreAttributes` lista atributos que não carregam texto visível ao usuário — configuração
      // de componente, classes CSS, identificadores e semântica ARIA sem conteúdo. Sem essa lista a
      // regra sinalizaria `severity="error"` e `rel="icon"`, e o ruído levaria a desabilitá-la — o
      // que deixaria de proteger o que realmente importa: rótulos, títulos e textos.
      '@angular-eslint/template/i18n': [
        'error',
        {
          checkText: true,
          checkAttributes: true,
          checkId: false,
          ignoreAttributes: [
            'align',
            'appendTo',
            'aria-hidden',
            'aria-invalid',
            'aria-live',
            'aria-required',
            'aria-valuemax',
            'aria-valuemin',
            'aria-valuenow',
            'autocomplete',
            'charset',
            'class',
            'content',
            'formControlName',
            'href',
            'icon',
            'iconPos',
            'id',
            'inputId',
            'inputStyleClass',
            'name',
            'position',
            'rel',
            'role',
            'severity',
            'src',
            'styleClass',
            'tabindex',
            'tooltipPosition',
            'type',
          ],
        },
      ],
      // FR-030 (nenhuma chamada de método em binding de valor) não é verificada por lint:
      // ler um Signal é sintaticamente uma chamada, então a regra sinalizaria todo binding do
      // projeto. O objetivo da regra — não recalcular a cada ciclo de detecção — é atendido
      // estruturalmente por Signals + computed + OnPush, e verificado em revisão.
      // FR-143 / A11Y: todo ícone sem texto possui rótulo acessível.
      '@angular-eslint/template/elements-content': 'error',
      '@angular-eslint/template/label-has-associated-control': 'error',
      '@angular-eslint/template/click-events-have-key-events': 'error',
      '@angular-eslint/template/interactive-supports-focus': 'error',
    },
  },
  {
    // O arquivo de configuração do Jest e o setup rodam em Node, não no navegador.
    files: ['jest.config.js', 'setup-jest.ts'],
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
);
