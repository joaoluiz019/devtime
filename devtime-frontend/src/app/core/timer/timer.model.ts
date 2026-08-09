/**
 * Modelos do cronômetro, espelhando `TimerResponses` do backend (FR-061, AP-02).
 *
 * Vivem em `core` porque o cronômetro é um componente **global** do layout (§21.1 de
 * `specs/009-timer/spec.md`): ele existe em toda tela autenticada, e um modelo dentro de uma feature
 * obrigaria o shell a importar de `features`.
 */

/** §4.5 de `state-machines.md`. */
export type TimerStatus = 'RUNNING' | 'PAUSED' | 'ABANDONED' | 'COMPLETED' | 'DISCARDED';

/** ME-06: o que o estado atual permite, calculado pelo servidor. */
export type TimerTransition = 'PAUSE' | 'RESUME' | 'STOP' | 'DISCARD' | 'UPDATE' | 'RECOVER';

export interface TimerTicket {
  readonly id: string;
  readonly key: string;
  readonly title: string;
}

export interface TimerCategory {
  readonly id: string;
  readonly name: string;
  readonly color: string | null;
}

/**
 * Estado do cronômetro.
 *
 * **`elapsedSeconds` não existe no contrato**, e é deliberado (§20, §21.3): o servidor devolve o
 * estado, não o relógio. O tempo decorrido é derivado de `startedAt`, `lastResumedAt` e
 * `accumulatedActiveSeconds` no cliente — consultar o servidor a cada segundo geraria 3.600
 * requisições por hora por pessoa ativa.
 */
export interface Timer {
  readonly id: string;
  readonly status: TimerStatus;
  readonly ticket: TimerTicket;
  readonly category: TimerCategory | null;
  readonly startedAt: string;
  readonly lastResumedAt: string | null;
  readonly accumulatedActiveSeconds: number;
  readonly pausedMinutes: number;
  readonly billable: boolean;
  readonly description: string | null;
  readonly stoppedAt: string | null;
  readonly workLogId: string | null;
  readonly availableTransitions: readonly TimerTransition[];
  readonly version: number;
}

export interface TimerStartRequest {
  readonly ticketId: string;
  readonly categoryId?: string;
  readonly description?: string;
  readonly billable?: boolean;
}

/** RN-161: `startedAt` está ausente — alterá-lo seria reescrever quando o trabalho começou. */
export interface TimerUpdateRequest {
  readonly ticketId?: string;
  readonly categoryId?: string;
  readonly description?: string;
  readonly billable?: boolean;
}

/** RN-158: a descrição é obrigatória no encerramento, não no início. */
export interface TimerStopRequest {
  readonly description: string;
}

export interface TimerRecoverRequest {
  readonly endedAt: string;
  readonly description?: string;
}

/** Aviso herdado de `008` (RN-232): o excedente é do registro de horas, não do cronômetro. */
export interface WorkLogWarning {
  readonly code: string;
  readonly message: string;
}

/** Resposta de encerramento: o registro gerado e o saldo já atualizado. */
export interface TimerStopResult {
  readonly timer: Timer;
  readonly workLog: {
    readonly id: string;
    readonly netMinutes: number;
    readonly durationLabel?: string;
  };
  readonly balance: { readonly remainingMinutes: number; readonly overageMinutes: number } | null;
  readonly warnings: readonly WorkLogWarning[];
}

/** §19.1: sem descrição e sem histórico de pausas — a visão da equipe não expõe ritmo de trabalho. */
export interface ActiveTimer {
  readonly id: string;
  readonly userId: string;
  readonly userName: string;
  readonly ticketKey: string;
  readonly status: TimerStatus;
  readonly startedAt: string;
}

/** RN-165: recuperável por sete dias; depois disso o descarte é definitivo. */
export interface AbandonedTimer {
  readonly id: string;
  readonly ticket: TimerTicket;
  readonly startedAt: string;
  readonly grossElapsedSeconds: number;
  readonly recoverableUntil: string;
}

/** RN-163: acima disto a barra exibe o aviso de duração. */
export const LONG_RUNNING_SECONDS = 8 * 60 * 60;

/**
 * Tempo ativo decorrido, em segundos.
 *
 * RN-151 / TB-01: o valor é **derivado do estado do servidor**; o contador local apenas anima os
 * segundos entre uma ressincronização e outra. Pausado, o acumulado é a resposta inteira — o tempo
 * congela, e somar o intervalo desde `lastResumedAt` contaria a pausa como trabalho.
 */
export function elapsedSeconds(timer: Timer, now: number): number {
  if (timer.status !== 'RUNNING') {
    return timer.accumulatedActiveSeconds;
  }
  const since = timer.lastResumedAt ?? timer.startedAt;
  const running = Math.max(0, Math.floor((now - Date.parse(since)) / 1000));
  return timer.accumulatedActiveSeconds + running;
}
