import { MoneyPipe } from './money.pipe';

/** RN-709 / §21.4 de `specs/012-reports/spec.md`. */
describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  /** O separador do `Intl` é espaço não separável; normalizar evita um teste frágil. */
  function normalize(value: string): string {
    return value.replace(/[\u00a0\u202f]/g, ' ');
  }

  it('formata com a moeda do dado e duas casas', () => {
    expect(normalize(pipe.transform(1234.5, 'BRL'))).toBe('R$ 1.234,50');
  });

  it('CE-R-09: a moeda vem do dado, não de uma configuração de tela', () => {
    expect(normalize(pipe.transform(10, 'USD'))).toContain('US$');
    expect(normalize(pipe.transform(10, 'EUR'))).toContain('€');
  });

  it('arredonda meio para cima, como o backend', () => {
    expect(normalize(pipe.transform(2.425, 'BRL'))).toBe('R$ 2,43');
    expect(normalize(pipe.transform(2.435, 'BRL'))).toBe('R$ 2,44');
  });

  it('arredonda simetricamente em valores negativos', () => {
    expect(normalize(pipe.transform(-2.425, 'BRL'))).toBe('-R$ 2,43');
  });

  /** CP-03 / CP-08: valor ausente é omissão do servidor; o travessão não sugere zero. */
  it('exibe travessão quando não há valor', () => {
    expect(pipe.transform(null, 'BRL')).toBe('—');
    expect(pipe.transform(undefined, 'BRL')).toBe('—');
  });

  it('sem moeda declarada, usa a moeda padrão em vez de falhar', () => {
    expect(normalize(pipe.transform(1, null))).toBe('R$ 1,00');
  });
});
