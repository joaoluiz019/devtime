import {
  calculateWorkLog,
  combine,
  isPausedValid,
  resolveEnd,
  roundDown,
} from './work-log-calculator';

function at(hour: number, minute = 0, day = 10): Date {
  return new Date(2026, 6, day, hour, minute);
}

/**
 * Espelho de RN-110 a RN-113 (T-008-27).
 *
 * A tabela abaixo é a mesma que o backend usa. Se os dois lados divergirem, a tela mostra uma
 * duração enquanto a fatura registra outra — e a divergência só aparece na cobrança.
 */
describe('calculateWorkLog', () => {
  it('RN-110: o bruto trunca os segundos, não arredonda', () => {
    const start = new Date(2026, 6, 10, 9, 0, 0);
    const end = new Date(2026, 6, 10, 9, 59, 59);

    expect(
      calculateWorkLog({ startedAt: start, endedAt: end, pausedMinutes: 0, billable: true }),
    ).toMatchObject({ grossMinutes: 59, netMinutes: 59 });
  });

  it('RN-111: o líquido desconta a pausa', () => {
    const result = calculateWorkLog({
      startedAt: at(9),
      endedAt: at(12),
      pausedMinutes: 30,
      billable: true,
    });

    expect(result.grossMinutes).toBe(180);
    expect(result.netMinutesBeforeRounding).toBe(150);
    expect(result.netMinutes).toBe(150);
  });

  it('RN-112: registro não faturável tem faturável zero, mas mantém o líquido', () => {
    const result = calculateWorkLog({
      startedAt: at(9),
      endedAt: at(11),
      pausedMinutes: 0,
      billable: false,
    });

    expect(result.netMinutes).toBe(120);
    expect(result.billableMinutes).toBe(0);
  });

  it.each([
    [150, 15, 150],
    [151, 15, 150],
    [164, 15, 150],
    [165, 15, 165],
    [7, 15, 0],
    [151, 0, 151],
  ])('RN-113: %s minutos com passo %s viram %s', (net, rounding, expected) => {
    expect(roundDown(net, rounding)).toBe(expected);
  });

  it('RN-113: o arredondamento é sempre para baixo, nunca para o mais próximo', () => {
    const result = calculateWorkLog({
      startedAt: at(9),
      endedAt: at(9, 59),
      pausedMinutes: 0,
      billable: true,
      roundingMinutes: 30,
    });

    // 59 minutos viram 30, não 60: cobrar o minuto não trabalhado é o erro caro.
    expect(result.netMinutes).toBe(30);
    expect(result.netMinutesBeforeRounding).toBe(59);
  });

  it('RN-114: fim antes ou igual ao início não produz duração', () => {
    expect(
      calculateWorkLog({ startedAt: at(10), endedAt: at(9), pausedMinutes: 0, billable: true }),
    ).toMatchObject({ grossMinutes: 0, netMinutes: 0 });
  });

  it('pausa maior que o bruto não gera líquido negativo', () => {
    const result = calculateWorkLog({
      startedAt: at(9),
      endedAt: at(10),
      pausedMinutes: 90,
      billable: true,
    });

    expect(result.netMinutes).toBe(0);
  });
});

describe('isPausedValid', () => {
  it.each([
    [60, 0, true],
    [60, 59, true],
    [60, 60, false],
    [60, -1, false],
  ])('RN-116: bruto %s com pausa %s é válido=%s', (gross, paused, expected) => {
    expect(isPausedValid(gross, paused)).toBe(expected);
  });
});

describe('resolveEnd', () => {
  it('empurra para o dia seguinte quando a sessão atravessa a meia-noite', () => {
    const workDate = at(0, 0, 10);
    const start = combine(workDate, at(23, 0));
    const end = resolveEnd(workDate, start, at(1, 0));

    expect(end.getDate()).toBe(11);
    expect(end.getHours()).toBe(1);
  });

  it('mantém o mesmo dia quando o fim é depois do início', () => {
    const workDate = at(0, 0, 10);
    const start = combine(workDate, at(9, 0));
    const end = resolveEnd(workDate, start, at(18, 0));

    expect(end.getDate()).toBe(10);
  });
});
