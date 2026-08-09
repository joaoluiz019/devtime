import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PeriodStatus } from '../models/balance.model';

/** Período de contrato como aparece num seletor. */
export interface PeriodOption {
  readonly id: string;
  readonly label: string;
  readonly sequence: number;
  readonly startDate: string;
  readonly endDate: string;
  readonly status: PeriodStatus;
}

interface ContractPeriodResponse {
  readonly id: string;
  readonly sequence: number;
  readonly label: string;
  readonly startDate: string;
  readonly endDate: string;
  readonly status: PeriodStatus;
}

/**
 * Períodos de um contrato para seletores de outras features (FR-03).
 *
 * A tela de relatórios precisa escolher um período, mas **não pode importar** de
 * `features/contracts` — o compartilhamento entre features passa por `shared` (FR-004).
 *
 * `SCHEDULED` fica de fora: um período que ainda não começou não tem registro algum, e pedir o
 * relatório dele devolveria `DEVTIME-3002`. Os mais recentes vêm primeiro porque é o período atual
 * ou o recém-fechado que se emite — nunca o primeiro do contrato.
 */
@Injectable({ providedIn: 'root' })
export class PeriodLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/contracts`;

  byContract(contractId: string): Observable<readonly PeriodOption[]> {
    return this.http
      .get<
        readonly ContractPeriodResponse[]
      >(`${this.base}/${encodeURIComponent(contractId)}/periods`)
      .pipe(
        map((periods) =>
          periods
            .filter((period) => period.status !== 'SCHEDULED')
            .map((period) => ({
              id: period.id,
              label: period.label,
              sequence: period.sequence,
              startDate: period.startDate,
              endDate: period.endDate,
              status: period.status,
            }))
            .sort((left, right) => right.sequence - left.sequence),
        ),
      );
  }
}
