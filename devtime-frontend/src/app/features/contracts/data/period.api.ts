import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PeriodBalance } from '../../../shared/models/balance.model';
import {
  Adjustment,
  AdjustmentRequest,
  ClosePeriodRequest,
  ClosePeriodResult,
  ContractHeader,
  ContractPeriod,
  PeriodSnapshot,
  PeriodStatement,
  ReopenPeriodRequest,
  ReopenPeriodResult,
} from './period.model';

/**
 * Transporte HTTP do banco de horas (T-011-14, §21.3 de `specs/011-bank-hours/spec.md`).
 *
 * FR-060 a FR-064: uma classe `*Api` por feature, responsável **apenas** por HTTP. Nenhuma
 * transformação de dado (FR-062) e nenhum tratamento de erro (FR-063) — os interceptors registrados
 * em `app.config.ts` cuidam de sessão, tenant, retry e tradução de `ProblemDetail`.
 *
 * Toda URL parte de `/api/v1`, com o host vindo do ambiente (FR-064). Os identificadores são UUIDs
 * gerados pelo servidor e entram no caminho já codificados (FR-065).
 */
@Injectable({ providedIn: 'root' })
export class PeriodApi {
  private readonly http = inject(HttpClient);
  private readonly periods = `${environment.apiBaseUrl}/contract-periods`;
  private readonly contracts = `${environment.apiBaseUrl}/contracts`;

  balance(periodId: string): Observable<PeriodBalance> {
    return this.http.get<PeriodBalance>(`${this.periods}/${encodeURIComponent(periodId)}`);
  }

  statement(periodId: string): Observable<PeriodStatement> {
    return this.http.get<PeriodStatement>(
      `${this.periods}/${encodeURIComponent(periodId)}/statement`,
    );
  }

  adjustments(periodId: string): Observable<readonly Adjustment[]> {
    return this.http.get<readonly Adjustment[]>(
      `${this.periods}/${encodeURIComponent(periodId)}/adjustments`,
    );
  }

  applyAdjustment(periodId: string, request: AdjustmentRequest): Observable<Adjustment> {
    return this.http.post<Adjustment>(
      `${this.periods}/${encodeURIComponent(periodId)}/adjustments`,
      request,
    );
  }

  close(periodId: string, request: ClosePeriodRequest): Observable<ClosePeriodResult> {
    return this.http.post<ClosePeriodResult>(
      `${this.periods}/${encodeURIComponent(periodId)}/close`,
      request,
    );
  }

  reopen(periodId: string, request: ReopenPeriodRequest): Observable<ReopenPeriodResult> {
    return this.http.post<ReopenPeriodResult>(
      `${this.periods}/${encodeURIComponent(periodId)}/reopen`,
      request,
    );
  }

  /** RN-701: o período fechado é servido do snapshot, não do cálculo ao vivo. */
  snapshot(periodId: string): Observable<PeriodSnapshot> {
    return this.http.get<PeriodSnapshot>(
      `${this.periods}/${encodeURIComponent(periodId)}/snapshot`,
    );
  }

  periodsOfContract(contractId: string): Observable<readonly ContractPeriod[]> {
    return this.http.get<readonly ContractPeriod[]>(
      `${this.contracts}/${encodeURIComponent(contractId)}/periods`,
    );
  }

  /** Cabeçalho do contrato para a trilha de navegação de P16 (DT-01). */
  contract(contractId: string): Observable<ContractHeader> {
    return this.http.get<ContractHeader>(`${this.contracts}/${encodeURIComponent(contractId)}`);
  }
}
