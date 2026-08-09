import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  AppNotification,
  MarkAllReadResult,
  NotificationListQuery,
  NotificationReadResult,
  UnreadCount,
} from './notification.model';

/**
 * Transporte HTTP das notificações (T-013-x).
 *
 * A ordenação é fixa em `createdAt,desc` no servidor: uma central ordenada por outro critério
 * esconderia o alerta mais recente, que é justamente o que se procura ao abri-la.
 */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/notifications`;

  list(query: NotificationListQuery): Observable<PageResponse<AppNotification>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.read !== undefined) {
      params = params.set('read', query.read);
    }
    if (query.type !== undefined) {
      params = params.set('type', query.type);
    }
    if (query.severity !== undefined) {
      params = params.set('severity', query.severity);
    }
    return this.http.get<PageResponse<AppNotification>>(this.base, { params });
  }

  /** Endpoint leve, consultado ao carregar qualquer tela; recai sobre índice parcial. */
  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${this.base}/unread-count`);
  }

  markRead(id: string): Observable<NotificationReadResult> {
    return this.http.post<NotificationReadResult>(
      `${this.base}/${encodeURIComponent(id)}/read`,
      null,
    );
  }

  markUnread(id: string): Observable<NotificationReadResult> {
    return this.http.post<NotificationReadResult>(
      `${this.base}/${encodeURIComponent(id)}/unread`,
      null,
    );
  }

  markAllRead(): Observable<MarkAllReadResult> {
    return this.http.post<MarkAllReadResult>(`${this.base}/read-all`, null);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(id)}`);
  }
}
