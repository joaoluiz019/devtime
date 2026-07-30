import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable, share, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionResponse, LoginRequest } from './auth.model';
import { AuthStore } from './auth.store';

/**
 * Operações de sessão contra os endpoints de `/api/v1/auth`.
 *
 * <p>Esta sprint entrega a infraestrutura de sessão — o transporte, a fila de refresh e a aplicação do
 * resultado no store. As regras de negócio da autenticação (bloqueio por tentativas RN-453, rotação com
 * detecção de reuso RN-005, seleção de tenant RN-459) pertencem à feature 001 e **não** estão aqui:
 * implementá-las agora significaria inventar comportamento antes da tela e do fluxo existirem.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(AuthStore);
  private readonly base = `${environment.apiBaseUrl}/auth`;

  /**
   * Refresh em andamento, compartilhado por todas as requisições que receberam 401.
   *
   * FR-068 / CA-08 de `001/tasks.md`: refreshes concorrentes são **enfileirados**, nunca disparados em
   * paralelo. Sem esta fila, três abas expirando juntas fariam três chamadas de rotação; a segunda e a
   * terceira usariam um token já rotacionado, o que RT-04 interpreta como roubo e revoga toda a cadeia
   * — derrubando a sessão legítima do usuário.
   */
  private refreshInFlight: Observable<AuthSessionResponse> | null = null;

  login(request: LoginRequest): Observable<AuthSessionResponse> {
    return this.http
      .post<AuthSessionResponse>(`${this.base}/login`, request, { withCredentials: true })
      .pipe(tap((session) => this.store.applySession(session)));
  }

  /**
   * Renova o access token usando o cookie de refresh.
   *
   * O cookie é enviado automaticamente por `withCredentials`; o token nunca é lido por JavaScript
   * (FR-067).
   */
  refresh(): Observable<AuthSessionResponse> {
    if (this.refreshInFlight !== null) {
      return this.refreshInFlight;
    }
    this.refreshInFlight = this.http
      .post<AuthSessionResponse>(`${this.base}/refresh`, null, { withCredentials: true })
      .pipe(
        tap({
          next: (session) => this.store.applySession(session),
          // A fila é liberada em qualquer desfecho: mantê-la após uma falha faria toda tentativa
          // seguinte reaproveitar um Observable já encerrado, travando a recuperação da sessão.
          finalize: () => (this.refreshInFlight = null),
        }),
        share(),
      );
    return this.refreshInFlight;
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.base}/logout`, null, { withCredentials: true })
      .pipe(tap(() => this.store.clearSession()));
  }

  /**
   * Restaura a sessão ao carregar a aplicação.
   *
   * Consequência de §5.4 de security.md: o access token vive em memória e é perdido no reload. O
   * frontend chama o refresh e recupera a sessão; se o cookie estiver ausente ou inválido, o usuário
   * segue para o login.
   */
  async restoreSession(): Promise<boolean> {
    try {
      await firstValueFrom(this.refresh());
      return true;
    } catch {
      this.store.clearSession();
      return false;
    }
  }
}
