import '@angular/localize/init';
import '@testing-library/jest-dom';
import { setupZonelessTestEnv } from 'jest-preset-angular/setup-env/zoneless';

/**
 * O ambiente de teste é zoneless para espelhar a aplicação, que usa
 * `provideZonelessChangeDetection()`. Testar com Zone.js enquanto a produção roda sem ela deixaria
 * passar exatamente os casos em que a detecção de mudança depende da zona.
 */
setupZonelessTestEnv();

/**
 * FR-185 / BR-205: nenhum teste usa relógio real.
 *
 * Um instante fixo transforma asserções sobre expiração de token e cálculo de data em igualdades
 * exatas, eliminando a classe de teste instável que falha na virada de dia.
 */
export const FIXED_NOW = new Date('2026-07-29T14:32:10.000Z');

beforeEach(() => {
  jest.useFakeTimers({ now: FIXED_NOW, doNotFake: ['nextTick', 'queueMicrotask'] });
});

afterEach(() => {
  jest.useRealTimers();
  jest.clearAllMocks();
});
