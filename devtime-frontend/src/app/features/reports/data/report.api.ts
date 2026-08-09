import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  ClientSummaryReport,
  ContractPeriodReport,
  ExportExecution,
  ExportRequest,
  ExportResponse,
  ProductivityReport,
  Report,
  ReportCriteria,
  ReportFilters,
  TicketDetailReport,
  TimesheetReport,
} from './report.model';

/**
 * Transporte HTTP dos dez endpoints de relatório e exportação (T-012-21).
 *
 * FR-062: nenhuma transformação de dado. `generate` escolhe a rota pelo tipo porque cada tipo tem o
 * seu próprio recorte na URL — mas devolve a união `Report`, de modo que store e visualizador
 * tratem os cinco pelo mesmo caminho.
 */
@Injectable({ providedIn: 'root' })
export class ReportApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/reports`;
  private readonly exportsBase = `${environment.apiBaseUrl}/reports/exports`;

  /**
   * Emite o relatório do recorte pedido.
   *
   * Os identificadores ausentes são recusados aqui, e não pelo servidor: um `undefined` na URL
   * viraria a string `"undefined"` e produziria um `404` que descreveria mal o problema.
   */
  generate(criteria: ReportCriteria): Observable<Report> {
    const params = this.filterParams(criteria.filters);
    switch (criteria.reportType) {
      case 'CONTRACT_PERIOD':
        return this.contractPeriod(required(criteria.contractPeriodId, 'contractPeriodId'), params);
      case 'CLIENT_SUMMARY':
        return this.clientSummary(required(criteria.clientId, 'clientId'), params);
      case 'TIMESHEET':
        return this.timesheet(params);
      case 'TICKET_DETAIL':
        return this.ticketDetail(required(criteria.ticketId, 'ticketId'), params);
      case 'PRODUCTIVITY':
        return this.productivity(params);
    }
  }

  contractPeriod(periodId: string, params: HttpParams): Observable<ContractPeriodReport> {
    return this.http.get<ContractPeriodReport>(
      `${this.base}/contract-period/${encodeURIComponent(periodId)}`,
      { params },
    );
  }

  clientSummary(clientId: string, params: HttpParams): Observable<ClientSummaryReport> {
    return this.http.get<ClientSummaryReport>(
      `${this.base}/client-summary/${encodeURIComponent(clientId)}`,
      { params },
    );
  }

  timesheet(params: HttpParams): Observable<TimesheetReport> {
    return this.http.get<TimesheetReport>(`${this.base}/timesheet`, { params });
  }

  ticketDetail(ticketId: string, params: HttpParams): Observable<TicketDetailReport> {
    return this.http.get<TicketDetailReport>(
      `${this.base}/ticket-detail/${encodeURIComponent(ticketId)}`,
      { params },
    );
  }

  productivity(params: HttpParams): Observable<ProductivityReport> {
    return this.http.get<ProductivityReport>(`${this.base}/productivity`, { params });
  }

  /**
   * Solicita a exportação (§8.1).
   *
   * `Idempotency-Key` faz duas requisições idênticas devolverem a **mesma** exportação (CE-R-12) —
   * o que importa quando alguém clica duas vezes no botão de um relatório de 40.000 linhas.
   */
  requestExport(request: ExportRequest, idempotencyKey: string): Observable<ExportResponse> {
    return this.http.post<ExportResponse>(this.exportsBase, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  listExports(page = 0, size = 10): Observable<PageResponse<ExportExecution>> {
    return this.http.get<PageResponse<ExportExecution>>(this.exportsBase, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  getExport(id: string): Observable<ExportExecution> {
    return this.http.get<ExportExecution>(`${this.exportsBase}/${encodeURIComponent(id)}`);
  }

  /**
   * Baixa o arquivo de uma exportação concluída.
   *
   * O endpoint responde `302` para uma URL assinada, e o navegador segue o redirecionamento dentro
   * do próprio XHR — o `Location` nunca fica legível para o JavaScript. Por isso o retorno é o
   * binário: pedir a URL para abrir numa aba exigiria um endpoint que o backend não expõe.
   */
  downloadExport(id: string): Observable<Blob> {
    return this.http.get(`${this.exportsBase}/${encodeURIComponent(id)}/download`, {
      responseType: 'blob',
    });
  }

  cancelExport(id: string): Observable<void> {
    return this.http.delete<void>(`${this.exportsBase}/${encodeURIComponent(id)}`);
  }

  /**
   * Converte os filtros em `HttpParams`.
   *
   * Campos ausentes ficam fora da query em vez de irem vazios: `groupBy=` seria um valor inválido
   * de enum, e `includeFinancial=` apagaria o default de §6. As listas são repetidas com `append`,
   * que é o formato que o Spring liga a `List<UUID>`.
   */
  filterParams(filters: ReportFilters): HttpParams {
    let params = new HttpParams();
    const scalars: readonly [string, string | boolean | undefined][] = [
      ['groupBy', filters.groupBy],
      ['from', filters.from],
      ['to', filters.to],
      ['includeNonBillable', filters.includeNonBillable],
      ['includeFinancial', filters.includeFinancial],
      ['includeUserColumn', filters.includeUserColumn],
      ['billable', filters.billable],
    ];
    for (const [key, value] of scalars) {
      if (value !== undefined) {
        params = params.set(key, value);
      }
    }

    const lists: readonly [string, readonly string[] | undefined][] = [
      ['contractIds', filters.contractIds],
      ['clientIds', filters.clientIds],
      ['categoryIds', filters.categoryIds],
      ['tagIds', filters.tagIds],
      ['userIds', filters.userIds],
    ];
    for (const [key, values] of lists) {
      for (const value of values ?? []) {
        params = params.append(key, value);
      }
    }
    return params;
  }
}

function required(value: string | undefined, name: string): string {
  if (value === undefined || value === '') {
    throw new Error(`Relatório sem ${name}`);
  }
  return value;
}
