import { formatDuration, parseDuration } from './duration.parser';

/**
 * CA-05 de `components.md`: `dt-duration-input` aceita **todos** os formatos da tabela §6.2.
 *
 * Cada linha da tabela vira um caso. Um formato que deixa de funcionar é atrito direto no campo mais
 * usado do produto (RF-111, PR-01).
 */
describe('parseDuration', () => {
  it.each([
    ['90', 90],
    ['1:30', 90],
    ['1h30', 90],
    ['1h30m', 90],
    ['1,5h', 90],
    ['1.5h', 90],
    ['90m', 90],
    ['2h', 120],
  ])('interpreta %s como %s minutos', (input, expected) => {
    expect(parseDuration(input)).toBe(expected);
  });

  it('aceita valor negativo, usado em débito de ajuste de saldo', () => {
    expect(parseDuration('-1:30')).toBe(-90);
    expect(parseDuration('-90')).toBe(-90);
  });

  it('DI-02: entrada inválida devolve null em vez de um número inventado', () => {
    // `1h75` é erro de digitação; aceitá-lo produziria 2h15 sem que o usuário percebesse.
    expect(parseDuration('1h75')).toBeNull();
    expect(parseDuration('abc')).toBeNull();
    expect(parseDuration('1:99')).toBeNull();
    expect(parseDuration('')).toBeNull();
  });

  it('ART-034: o resultado é sempre inteiro em minutos', () => {
    expect(parseDuration('1,51h')).toBe(91);
    expect(Number.isInteger(parseDuration('0,3h'))).toBe(true);
  });
});

describe('formatDuration', () => {
  it.each([
    [90, '01:30'],
    [0, '00:00'],
    [-140, '-02:20'],
    // CE-D-01: durações longas são exibidas integralmente, nunca convertidas para dias.
    [61470, '1024:30'],
  ])('formata %s minutos como %s', (minutes, expected) => {
    expect(formatDuration(minutes)).toBe(expected);
  });
});
