import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  InvitationRequest,
  Member,
  MemberInvitation,
  MemberRemoval,
  RoleUpdateRequest,
} from './member.model';

/**
 * Transporte HTTP da equipe (users.md §7, T-002-28).
 *
 * Separada de `SettingsApi` porque é a única área de configurações com máquina de estados própria —
 * convite, aceite, suspensão, remoção — e com regras que a tela precisa respeitar (RN-455, RN-456).
 *
 * FR-060 a FR-064: só HTTP, sem transformação nem tratamento de erro.
 */
@Injectable({ providedIn: 'root' })
export class MemberApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/members`;

  list(query: {
    role?: string;
    status?: string;
    search?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<Member>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const key of ['role', 'status', 'search'] as const) {
      const value = query[key];
      if (value !== undefined && value !== '') {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<Member>>(this.base, { params });
  }

  /** Convites em `INVITED`; o vínculo correspondente também aparece na listagem de membros. */
  invitations(): Observable<readonly MemberInvitation[]> {
    return this.http.get<readonly MemberInvitation[]>(`${this.base}/invitations`);
  }

  invite(request: InvitationRequest): Observable<MemberInvitation> {
    return this.http.post<MemberInvitation>(`${this.base}/invitations`, request);
  }

  /** RN-457: emite um novo token e invalida o anterior. Responde `202`. */
  resendInvitation(id: string): Observable<MemberInvitation> {
    return this.http.post<MemberInvitation>(
      `${this.base}/invitations/${encodeURIComponent(id)}/resend`,
      null,
    );
  }

  revokeInvitation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/invitations/${encodeURIComponent(id)}`);
  }

  changeRole(id: string, request: RoleUpdateRequest): Observable<Member> {
    return this.http.patch<Member>(`${this.base}/${encodeURIComponent(id)}/role`, request);
  }

  suspend(id: string): Observable<Member> {
    return this.http.post<Member>(`${this.base}/${encodeURIComponent(id)}/suspend`, null);
  }

  reactivate(id: string): Observable<Member> {
    return this.http.post<Member>(`${this.base}/${encodeURIComponent(id)}/reactivate`, null);
  }

  /**
   * Remove o vínculo (RN-458, RN-460).
   *
   * `reassignTicketsTo` é opcional: omitido, o backend reatribui ao `OWNER` que executou. A escolha
   * existe porque devolver os tickets de quem saiu ao proprietário raramente é onde eles devem
   * ficar.
   */
  remove(id: string, reassignTicketsTo?: string): Observable<MemberRemoval> {
    let params = new HttpParams();
    if (reassignTicketsTo !== undefined) {
      params = params.set('reassignTicketsTo', reassignTicketsTo);
    }
    return this.http.delete<MemberRemoval>(`${this.base}/${encodeURIComponent(id)}`, { params });
  }
}
