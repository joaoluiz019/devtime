import { DurationPipe } from './duration.pipe';

/**
 * Formatação de duração (ART-035, I18-03, CA-08 do design system).
 *
 * FR-188 exige cobertura acima de 90% em pipes. Este é o pipe mais exposto do produto: aparece em toda
 * listagem de horas, e um erro de formatação aqui é lido pelo usuário como erro de cálculo.
 */
describe('DurationPipe', () => {
  const pipe = new DurationPipe();

  it('ART-035: formata minutos em HH:MM, nunca em decimal', () => {
    expect(pipe.transform(450)).toBe('07:30');
    expect(pipe.transform(90)).toBe('01:30');
    expect(pipe.transform(60)).toBe('01:00');
    expect(pipe.transform(5)).toBe('00:05');
    expect(pipe.transform(0)).toBe('00:00');
  });

  it('§10 do design system: duração negativa é exibida com sinal, como excedente', () => {
    expect(pipe.transform(-140)).toBe('-02:20');
    expect(pipe.transform(-1)).toBe('-00:01');
  });

  it('CE-D-01: duração acima de 999 horas é exibida integralmente, nunca convertida para dias', () => {
    expect(pipe.transform(61470)).toBe('1024:30');
    expect(pipe.transform(41280)).toBe('688:00');
  });

  it('o formato signed prefixa valores positivos, para uso em saldo', () => {
    expect(pipe.transform(140, 'signed')).toBe('+02:20');
    expect(pipe.transform(-140, 'signed')).toBe('-02:20');
    expect(pipe.transform(0, 'signed')).toBe('00:00');
  });

  it('CE-D-05: valor ausente exibe travessão em vez de um número possivelmente errado', () => {
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(undefined)).toBe('—');
    expect(pipe.transform(Number.NaN)).toBe('—');
  });

  it('BR-144: fração de minuto é truncada, nunca arredondada para cima', () => {
    // Truncar garante que o sistema nunca exiba tempo não trabalhado (RN-010, PR-03).
    expect(pipe.transform(90.9)).toBe('01:30');
    expect(pipe.transform(-90.9)).toBe('-01:30');
  });
});
