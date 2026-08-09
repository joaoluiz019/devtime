import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AbandonedTimer,
  ActiveTimer,
  Timer,
  TimerRecoverRequest,
  TimerStartRequest,
  TimerStopRequest,
  TimerStopResult,
  TimerUpdateRequest,
} from './timer.model';

/**
 * Transporte HTTP do cronômetro (T-009, `worklogs.md` §9 a §12).
 *
 * FR-060 a FR-064: só HTTP. A única concessão é em `current`, que converte o `204` do servidor em
 * `null` — sem isso, todo consumidor teria de inspecionar o status para descobrir que "sem corpo"
 * significa "nenhum cronômetro ativo".
 */
@Injectable({ providedIn: 'root' })
export class TimerApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/timers`;

  current(): Observable<Timer | null> {
    return this.http
      .get<Timer>(`${this.base}/current`, { observe: 'response' })
      .pipe(map((response) => response.body));
  }

  /** RN-166: `stopCurrent` encerra o atual e inicia o novo em uma operação atômica. */
  start(request: TimerStartRequest, stopCurrent = false): Observable<Timer> {
    return this.http.post<Timer>(this.base, request, {
      params: new HttpParams().set('stopCurrent', stopCurrent),
    });
  }

  update(request: TimerUpdateRequest): Observable<Timer> {
    return this.http.patch<Timer>(`${this.base}/current`, request);
  }

  /** RN-153: pausar um cronômetro já pausado é erro, não idempotência. */
  pause(): Observable<Timer> {
    return this.http.post<Timer>(`${this.base}/current/pause`, null);
  }

  resume(): Observable<Timer> {
    return this.http.post<Timer>(`${this.base}/current/resume`, null);
  }

  /** RN-159/RN-160: aplica as validações de `008`; em qualquer falha o cronômetro permanece ativo. */
  stop(request: TimerStopRequest): Observable<TimerStopResult> {
    return this.http.post<TimerStopResult>(`${this.base}/current/stop`, request);
  }

  /** RN-162: exige confirmação explícita — destrói trabalho registrado sem contrapartida. */
  discard(): Observable<void> {
    return this.http.delete<void>(`${this.base}/current`, {
      params: new HttpParams().set('confirm', true),
    });
  }

  abandoned(): Observable<readonly AbandonedTimer[]> {
    return this.http.get<readonly AbandonedTimer[]>(`${this.base}/abandoned`);
  }

  recover(id: string, request: TimerRecoverRequest): Observable<TimerStopResult> {
    return this.http.post<TimerStopResult>(
      `${this.base}/${encodeURIComponent(id)}/recover`,
      request,
    );
  }

  /** `TIMER_VIEW_ANY`: cronômetros da equipe, sem descrição nem pausas (§19.1). */
  active(): Observable<readonly ActiveTimer[]> {
    return this.http.get<readonly ActiveTimer[]>(`${this.base}/active`);
  }

  forceStop(id: string, request: TimerStopRequest): Observable<TimerStopResult> {
    return this.http.post<TimerStopResult>(
      `${this.base}/${encodeURIComponent(id)}/force-stop`,
      request,
    );
  }
}
