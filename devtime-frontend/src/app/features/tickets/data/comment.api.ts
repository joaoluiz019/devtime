import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  CommentCreateRequest,
  CommentThread,
  CommentUpdateRequest,
  TicketComment,
} from './comment.model';

/**
 * Transporte HTTP da conversa (tickets.md §10.1).
 *
 * As rotas são duas de propósito: a conversa pertence ao ticket (`/tickets/{id}/comments`) e a
 * manutenção pertence ao comentário (`/comments/{id}`). Um comentário editado não muda de ticket, e
 * a rota reflete isso.
 */
@Injectable({ providedIn: 'root' })
export class CommentApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Paginação por cursor: `cursor` é o instante da raiz mais antiga já recebida. */
  list(ticketId: string, cursor?: string, size = 20): Observable<CommentThread> {
    let params = new HttpParams().set('size', size);
    if (cursor !== undefined) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<CommentThread>(
      `${this.base}/tickets/${encodeURIComponent(ticketId)}/comments`,
      { params },
    );
  }

  create(ticketId: string, request: CommentCreateRequest): Observable<TicketComment> {
    return this.http.post<TicketComment>(
      `${this.base}/tickets/${encodeURIComponent(ticketId)}/comments`,
      request,
    );
  }

  update(id: string, request: CommentUpdateRequest): Observable<TicketComment> {
    return this.http.patch<TicketComment>(
      `${this.base}/comments/${encodeURIComponent(id)}`,
      request,
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/comments/${encodeURIComponent(id)}`);
  }
}
