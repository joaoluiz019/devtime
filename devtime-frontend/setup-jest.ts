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
 * `ResizeObserver` não existe no jsdom.
 *
 * Componentes do PrimeNG que se reposicionam — abas, painéis com rolagem — chamam a API no
 * `ngAfterViewInit` e quebrariam qualquer teste que os renderize. A substituição é inerte de
 * propósito: nenhum teste afirma nada sobre redimensionamento, que depende de layout real e não
 * existe em jsdom.
 */
if (!('ResizeObserver' in globalThis)) {
  (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  };
}

/**
 * `matchMedia` não existe no jsdom.
 *
 * O overlay do PrimeNG — usado por `p-select`, `p-multiSelect` e `p-datepicker` — consulta a media
 * query do modo modal ao abrir. Sem esta substituição, qualquer teste que **abra** um seletor falha
 * dentro da renderização do overlay, não na asserção. A resposta é sempre "não corresponde": o
 * comportamento responsivo depende de layout real, que não existe aqui, e nenhum teste afirma nada
 * sobre ele.
 */
if (!('matchMedia' in globalThis)) {
  (globalThis as unknown as { matchMedia: unknown }).matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

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
