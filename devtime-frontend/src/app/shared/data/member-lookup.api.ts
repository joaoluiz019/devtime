import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/page.model';

/** Pessoa como aparece num seletor de responsável. */
export interface MemberOption {
  readonly id: string;
  readonly name: string;
  readonly email: string;
  readonly avatarUrl: string | null;
}

interface MemberResponse {
  readonly user: {
    readonly id: string;
    readonly fullName: string;
    readonly displayName: string | null;
    readonly email: string;
    readonly avatarUrl: string | null;
  };
}

/**
 * Membros ativos para seletores de outras features (FR-03).
 *
 * RN-304: `assigneeId` precisa ser membership `ACTIVE`. O filtro é aplicado na origem — oferecer
 * alguém suspenso produziria `422` no salvamento, depois de o formulário inteiro estar preenchido.
 *
 * O `id` devolvido é o do **usuário**, não o do vínculo: é ele que os contratos de ticket e de
 * registro de horas esperam em `assigneeId`.
 */
@Injectable({ providedIn: 'root' })
export class MemberLookupApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/members`;

  search(term?: string): Observable<readonly MemberOption[]> {
    let params = new HttpParams().set('status', 'ACTIVE').set('size', 100);
    if (term !== undefined && term !== '') {
      params = params.set('search', term);
    }
    return this.http.get<PageResponse<MemberResponse>>(this.base, { params }).pipe(
      map((page) =>
        page.content.map((member) => ({
          id: member.user.id,
          name: member.user.displayName ?? member.user.fullName,
          email: member.user.email,
          avatarUrl: member.user.avatarUrl,
        })),
      ),
    );
  }
}
