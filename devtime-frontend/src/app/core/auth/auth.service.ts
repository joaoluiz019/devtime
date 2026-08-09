import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom, Observable, share, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AcceptInvitationRequest,
  AuthSessionResponse,
  ForgotPasswordRequest,
  LoginRequest,
  MessageResponse,
  RegisterRequest,
  RegisterResponse,
  InvitationPreview,
  ResendVerificationRequest,
  ResetPasswordRequest,
  SelectTenantRequest,
  TenantOption,
} from './auth.model';
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

  /**
   * Cadastro (P02).
   *
   * Não aplica a sessão: o backend responde `201` **sem** token porque a conta nasce
   * `PENDING_ACTIVATION` e só o `verify-email` emite sessão (CP-08).
   */
  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.base}/register`, request);
  }

  /**
   * Verificação de e-mail (P03).
   *
   * Emite sessão como o login, então o resultado é aplicado ao store — o usuário sai do link do
   * e-mail já autenticado. É idempotente no backend (CA-08): pré-visualizações de cliente de e-mail
   * consomem o link antes da pessoa clicar.
   */
  verifyEmail(token: string): Observable<AuthSessionResponse> {
    return this.http
      .post<AuthSessionResponse>(`${this.base}/verify-email`, { token }, { withCredentials: true })
      .pipe(tap((session) => this.store.applySession(session)));
  }

  /** Reenvio da verificação. SG-01: a resposta é a mesma com e sem conta correspondente. */
  resendVerification(request: ResendVerificationRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/resend-verification`, request);
  }

  /** PW-07 / SG-02: sempre `202`, exista ou não a conta. A tela não pode revelar a diferença. */
  forgotPassword(request: ForgotPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(`${this.base}/forgot-password`, request);
  }

  /**
   * Redefinição por token (P05).
   *
   * O backend revoga todas as sessões e limpa o cookie de refresh (CE-AU-05); o store local é
   * limpo junto para não manter usuário e permissões de uma sessão que já não existe no servidor.
   */
  resetPassword(request: ResetPasswordRequest): Observable<MessageResponse> {
    return this.http
      .post<MessageResponse>(`${this.base}/reset-password`, request, { withCredentials: true })
      .pipe(tap(() => this.store.clearSession()));
  }

  /** Organizações do usuário autenticado (P06). CX-08: as suspensas vêm marcadas, não omitidas. */
  listTenants(): Observable<readonly TenantOption[]> {
    return this.http.get<TenantOption[]>(`${this.base}/tenants`);
  }

  /**
   * Seleciona — ou troca — a organização da sessão (RN-459).
   *
   * A resposta traz um token com o claim `tid` e as permissões do papel naquela organização, então
   * é aplicada ao store como qualquer outra sessão.
   */
  selectTenant(request: SelectTenantRequest): Observable<AuthSessionResponse> {
    return this.http
      .post<AuthSessionResponse>(`${this.base}/select-tenant`, request, { withCredentials: true })
      .pipe(tap((session) => this.store.applySession(session)));
  }

  /**
   * Consulta o convite antes do aceite (P07).
   *
   * Público: a tela precisa exibir organização e papel antes de existir sessão. O que protege o
   * endpoint é a imprevisibilidade do token, não a autenticação.
   */
  peekInvitation(token: string): Observable<InvitationPreview> {
    return this.http.get<InvitationPreview>(
      `${this.base}/invitations/${encodeURIComponent(token)}`,
    );
  }

  /**
   * Aceita o convite (P07).
   *
   * A resposta tem duas formas (§5.12): com sessão prévia, apenas confirmação — CX-09 preserva a
   * organização corrente e **nenhum** cookie novo é emitido; sem sessão, a estrutura de sessão, que
   * é aplicada ao store como qualquer login. Distinguir pela presença de `accessToken` evita que o
   * aceite de quem já estava logado derrube a sessão que a pessoa está usando agora.
   */
  acceptInvitation(
    token: string,
    request: AcceptInvitationRequest,
  ): Observable<AuthSessionResponse | MessageResponse> {
    return this.http
      .post<
        AuthSessionResponse | MessageResponse
      >(`${this.base}/invitations/${encodeURIComponent(token)}/accept`, request, { withCredentials: true })
      .pipe(
        tap((response) => {
          if (isSessionResponse(response)) {
            this.store.applySession(response);
          }
        }),
      );
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

/**
 * A resposta do aceite é sessão ou apenas confirmação (§5.12).
 *
 * O discriminante é `accessToken`: `MessageResponse` não o possui, e é exatamente esse o caso em
 * que aplicar a resposta ao store trocaria a organização corrente do convidado.
 */
function isSessionResponse(
  response: AuthSessionResponse | MessageResponse,
): response is AuthSessionResponse {
  return typeof (response as Partial<AuthSessionResponse>).accessToken === 'string';
}
