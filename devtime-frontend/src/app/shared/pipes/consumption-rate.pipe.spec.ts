import { ConsumptionRatePipe } from './consumption-rate.pipe';

/** Percentual com 1 casa decimal e vírgula como separador (§10 do design system, I18-02). */
describe('ConsumptionRatePipe', () => {
  const pipe = new ConsumptionRatePipe();

  it.each([
    [0, '0,0%'],
    [41, '41,0%'],
    [83.65, '83,7%'],
    [83.64, '83,6%'],
    [105.07, '105,1%'],
    [1024.5, '1024,5%'],
  ])('formata %s como %s', (rate, expected) => {
    expect(pipe.transform(rate)).toBe(expected);
  });

  it('CE-D-05: valor ausente vira travessão, nunca um número possivelmente errado', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform(Number.NaN)).toBe('—');
  });

  it('arredonda HALF_UP, como o servidor', () => {
    // `toFixed` sozinho devolveria 83,6 por causa da representação binária de 83.65.
    expect(pipe.transform(83.65)).toBe('83,7%');
    expect(pipe.transform(0.05)).toBe('0,1%');
  });
});
