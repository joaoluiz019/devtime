import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';

/** Estados da tela (T-001-49): em verificação, verificado, link inválido/expirado. */
type VerificationState = 'verifying' | 'verified' | 'failed' | 'missingToken';

/**
 * Verificação de e-mail — P03, `POST /auth/verify-email` (T-001-49).
 *
 * O token chega em `?token=` no link do e-mail e a verificação dispara sozinha: pedir um clique a
 * quem já clicou no e-mail é um passo sem função.
 *
 * O endpoint emite sessão, então o sucesso leva direto ao destino autenticado — a pessoa acabou de
 * provar posse do endereço e não há o que reautenticar.
 */
@Component({
  selector: 'dt-verify-email-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    MessageModule,
    ProgressSpinnerModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-auth-form__status" aria-live="polite">
      @switch (state()) {
        @case ('verifying') {
          <p-progress-spinner
            styleClass="w-3rem h-3rem"
            i18n-ariaLabel="@@verify.loading"
            ariaLabel="Verificando seu e-mail"
          />
          <h1 class="dt-auth-form__title" i18n="@@verify.verifying.title">
            Verificando seu e-mail
          </h1>
        }
        @case ('verified') {
          <i
            class="pi pi-check-circle dt-auth-form__status-icon dt-auth-form__status-icon--success"
            aria-hidden="true"
          ></i>
          <h1 class="dt-auth-form__title" i18n="@@verify.verified.title">E-mail verificado</h1>
          <p class="dt-auth-form__subtitle" i18n="@@verify.verified.text">
            Sua conta está ativa. Você já pode começar a usar o DevTime.
          </p>
          <p-button
            i18n-label="@@verify.verified.continue"
            label="Continuar"
            (onClick)="continueToApp()"
          />
        }
        @default {
          <i
            class="pi pi-times-circle dt-auth-form__status-icon dt-auth-form__status-icon--danger"
            aria-hidden="true"
          ></i>
          <h1 class="dt-auth-form__title" i18n="@@verify.failed.title">
            Não foi possível verificar
          </h1>
          @if (errorMessage() !== null) {
            <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
          } @else {
            <p class="dt-auth-form__subtitle" i18n="@@verify.missingToken.text">
              O link não contém um código de verificação. Informe seu e-mail para receber um novo.
            </p>
          }

          <!-- RN-457: o reenvio invalida o token anterior; é a saída de todo link expirado. -->
          <form class="dt-auth-form__form" [formGroup]="form" (ngSubmit)="resend()">
            <div class="dt-auth-form__field">
              <label for="verify-email" i18n="@@verify.email">E-mail cadastrado</label>
              <input
                id="verify-email"
                type="email"
                pInputText
                formControlName="email"
                autocomplete="email"
                aria-required="true"
                [attr.aria-invalid]="emailInvalid()"
              />
              @if (emailInvalid()) {
                <small class="dt-auth-form__error" i18n="@@verify.email.invalid">
                  Informe um e-mail válido.
                </small>
              }
            </div>
            <p-button
              type="submit"
              i18n-label="@@verify.resend"
              label="Reenviar verificação"
              styleClass="w-full"
              [loading]="resending()"
            />
          </form>

          <div aria-live="polite">
            @if (resendMessage() !== null) {
              <p-message severity="info" [text]="resendMessage()!" styleClass="w-full" />
            }
          </div>

          <a routerLink="/auth/login" i18n="@@verify.toLogin">Voltar para entrar</a>
        }
      }
    </div>
  `,
  styleUrl: './auth-form.scss',
})
export class VerifyEmailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
  });

  private readonly _state = signal<VerificationState>('verifying');
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _resending = signal(false);
  private readonly _resendMessage = signal<string | null>(null);
  private readonly _submitted = signal(false);

  protected readonly state = this._state.asReadonly();
  protected readonly resending = this._resending.asReadonly();
  protected readonly resendMessage = this._resendMessage.asReadonly();

  protected errorMessage(): string | null {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  }

  protected emailInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.touched || this._submitted());
  }

  async ngOnInit(): Promise<void> {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (token === null || token.trim() === '') {
      this._state.set('missingToken');
      return;
    }
    await this.verify(token);
  }

  private async verify(token: string): Promise<void> {
    try {
      await firstValueFrom(this.authService.verifyEmail(token));
      this._state.set('verified');
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
      this._state.set('failed');
    }
  }

  /**
   * O destino é a raiz, não o dashboard.
   *
   * Quem tem uma organização só chega ao dashboard; quem tem várias cai na seleção pelo
   * `tenantSelectedGuard`. Mandar todo mundo para `/dashboard` faria o segundo grupo ver um redirect
   * imediato para outra tela.
   */
  protected async continueToApp(): Promise<void> {
    await this.router.navigate(['/']);
  }

  protected async resend(): Promise<void> {
    this._submitted.set(true);
    this._resendMessage.set(null);
    if (this.form.invalid) {
      document.getElementById('verify-email')?.focus();
      return;
    }

    this._resending.set(true);
    try {
      const response = await firstValueFrom(
        this.authService.resendVerification(this.form.getRawValue()),
      );
      // SG-01: a mensagem do servidor é deliberadamente indiferente à existência da conta.
      this._resendMessage.set(response.message);
    } catch {
      this._resendMessage.set(
        $localize`:@@verify.resendFailed:Não foi possível reenviar agora. Tente novamente em instantes.`,
      );
    } finally {
      this._resending.set(false);
    }
  }
}
