/**
 * Configuração do Jest (frontend.md §15, ADR-028).
 *
 * Metas de cobertura de FR-188 e §15 de frontend.md: 90% em pipes, validators, utils e computeds de
 * store. O limiar global é menor porque componentes de página são cobertos por testes de integração,
 * cuja contribuição de linhas é desproporcional ao valor de verificação.
 */
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testEnvironment: 'jsdom',
  testMatch: ['<rootDir>/src/**/*.spec.ts'],
  moduleFileExtensions: ['ts', 'html', 'js', 'json', 'mjs'],
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$)'],
  collectCoverageFrom: [
    'src/app/**/*.ts',
    '!src/app/**/*.spec.ts',
    '!src/app/**/*.routes.ts',
    '!src/app/app.config.ts',
  ],
  coverageThreshold: {
    global: {
      lines: 70,
    },
    // FR-188: pipes, validators e utils acima de 90%.
    './src/app/shared/pipes/': {
      lines: 90,
    },
    './src/app/core/': {
      lines: 80,
    },
  },
  // BR-204 / FR-187: nenhum teste depende de ordem nem de setTimeout para sincronização.
  randomize: true,
};
