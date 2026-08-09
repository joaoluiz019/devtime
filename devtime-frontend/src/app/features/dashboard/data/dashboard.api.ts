import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Dashboard, DashboardPeriodType } from './dashboard.model';

/** Transporte HTTP do painel (T-010-14). FR-060 a FR-064: só HTTP. */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/dashboard`;

  load(period: DashboardPeriodType, from?: string, to?: string): Observable<Dashboard> {
    let params = new HttpParams().set('period', period);
    // O intervalo personalizado exige as duas pontas; o servidor recusa incompleto (`DEVTIME-2000`).
    if (period === 'CUSTOM' && from !== undefined && to !== undefined) {
      params = params.set('from', from).set('to', to);
    }
    return this.http.get<Dashboard>(this.base, { params });
  }
}
