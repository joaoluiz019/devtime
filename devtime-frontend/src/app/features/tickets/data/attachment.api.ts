import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Attachment, AttachmentList, AttachmentQuota } from './attachment.model';

/**
 * Transporte HTTP dos anexos (tickets.md §11).
 *
 * O download passa pelo `HttpClient` porque o endpoint exige `Authorization: Bearer`, e o token vive
 * em memória (§5.4 de `security.md`) — apontar o navegador para a URL não enviaria credencial
 * alguma. O servidor responde `302` para uma URL assinada e o próprio XHR segue o redirecionamento;
 * o `Location` nunca fica legível para o JavaScript, então o que volta é o binário.
 */
@Injectable({ providedIn: 'root' })
export class AttachmentApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  byTicket(ticketId: string): Observable<AttachmentList> {
    return this.http.get<AttachmentList>(
      `${this.base}/tickets/${encodeURIComponent(ticketId)}/attachments`,
    );
  }

  byComment(commentId: string): Observable<AttachmentList> {
    return this.http.get<AttachmentList>(
      `${this.base}/comments/${encodeURIComponent(commentId)}/attachments`,
    );
  }

  /**
   * Envia o arquivo.
   *
   * `multipart/form-data` sem `Content-Type` explícito: o navegador precisa compor o cabeçalho com
   * o `boundary`, e defini-lo à mão produz um corpo que o servidor não consegue separar.
   */
  upload(ticketId: string, file: File): Observable<Attachment> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Attachment>(
      `${this.base}/tickets/${encodeURIComponent(ticketId)}/attachments`,
      form,
    );
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/attachments/${encodeURIComponent(id)}`);
  }

  quota(): Observable<AttachmentQuota> {
    return this.http.get<AttachmentQuota>(`${this.base}/attachments/quota`);
  }

  download(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/attachments/${encodeURIComponent(id)}/download`, {
      responseType: 'blob',
    });
  }
}
