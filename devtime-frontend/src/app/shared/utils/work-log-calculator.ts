/**
 * Cálculo de duração de um registro de horas — espelho de RN-110 a RN-113 (T-008-27).
 *
 * O servidor é a autoridade: ele recalcula tudo e recusa o que não bate. Este cálculo existe para a
 * tela mostrar o resultado **enquanto** a pessoa digita — sem ele, só se descobre quanto tempo foi
 * lançado depois de salvar, e um erro de fuso ou de pausa vira uma correção posterior.
 *
 * A tabela normativa é a mesma dos dois lados, e é isso que o teste cruzado verifica.
 */
export interface WorkLogCalculation {
  /** RN-110: minutos completos entre início e fim, truncando os segundos. */
  readonly grossMinutes: number;
  readonly pausedMinutes: number;
  /** RN-111: bruto menos pausa, antes do arredondamento. */
  readonly netMinutesBeforeRounding: number;
  /** RN-113: arredondado **para baixo** ao múltiplo configurado. */
  readonly netMinutes: number;
  /** RN-112: zero quando não faturável. */
  readonly billableMinutes: number;
}

export interface WorkLogCalculationInput {
  readonly startedAt: Date | null;
  readonly endedAt: Date | null;
  readonly pausedMinutes: number;
  readonly billable: boolean;
  /** Configuração do tenant; `0` desliga o arredondamento. */
  readonly roundingMinutes?: number;
}

export const EMPTY_CALCULATION: WorkLogCalculation = {
  grossMinutes: 0,
  pausedMinutes: 0,
  netMinutesBeforeRounding: 0,
  netMinutes: 0,
  billableMinutes: 0,
};

/** RN-103: um registro não pode passar de 24 horas. */
export const MAX_GROSS_MINUTES = 1440;

export function calculateWorkLog(input: WorkLogCalculationInput): WorkLogCalculation {
  const { startedAt, endedAt } = input;
  if (startedAt === null || endedAt === null || endedAt.getTime() <= startedAt.getTime()) {
    // RN-114: fim precisa ser depois do início. Sem isso não há duração a exibir — e mostrar um
    // negativo ou um zero calculado sugeriria que o valor é válido.
    return EMPTY_CALCULATION;
  }

  const grossMinutes = Math.floor((endedAt.getTime() - startedAt.getTime()) / 60000);
  const pausedMinutes = Math.max(0, Math.trunc(input.pausedMinutes));
  const netMinutesBeforeRounding = Math.max(0, grossMinutes - pausedMinutes);
  const netMinutes = roundDown(netMinutesBeforeRounding, input.roundingMinutes ?? 0);

  return {
    grossMinutes,
    pausedMinutes,
    netMinutesBeforeRounding,
    netMinutes,
    billableMinutes: input.billable ? netMinutes : 0,
  };
}

/**
 * RN-113: arredondamento **sempre para baixo**.
 *
 * Arredondar para o mais próximo cobraria minutos não trabalhados; é a diferença entre uma política
 * conservadora e uma fatura contestável.
 */
export function roundDown(minutes: number, roundingMinutes: number): number {
  if (roundingMinutes <= 0) {
    return minutes;
  }
  return Math.floor(minutes / roundingMinutes) * roundingMinutes;
}

/** RN-116: a pausa precisa ser menor que o bruto; igual significaria um registro de zero minuto. */
export function isPausedValid(grossMinutes: number, pausedMinutes: number): boolean {
  return pausedMinutes >= 0 && (grossMinutes === 0 || pausedMinutes < grossMinutes);
}

/**
 * Sessão que atravessa a meia-noite (T-008-28).
 *
 * Quem começa às 23h e termina à 1h informa dois horários em que o segundo é "menor" que o primeiro.
 * Interpretar isso como erro obrigaria a pessoa a informar a data do fim, que ela não tem motivo para
 * pensar; o fim é empurrado para o dia seguinte, que é a única leitura possível de um intervalo de
 * menos de 24 horas.
 */
export function resolveEnd(workDate: Date, start: Date, end: Date): Date {
  const resolved = new Date(
    workDate.getFullYear(),
    workDate.getMonth(),
    workDate.getDate(),
    end.getHours(),
    end.getMinutes(),
  );
  if (resolved.getTime() <= start.getTime()) {
    resolved.setDate(resolved.getDate() + 1);
  }
  return resolved;
}

/** Combina a data de trabalho com um horário digitado, no fuso local do navegador. */
export function combine(workDate: Date, time: Date): Date {
  return new Date(
    workDate.getFullYear(),
    workDate.getMonth(),
    workDate.getDate(),
    time.getHours(),
    time.getMinutes(),
  );
}
