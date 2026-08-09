import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Esqueci a senha — P04, `POST /auth/forgot-password` (T-001-50).
 *
 * SG-02 / PW-07: o servidor responde `202` exista ou não a conta, e **a tela precisa se comportar do
 * mesmo modo**. Por isso o estado de sucesso é aplicado também quando a chamada falha por rate limit
 * ou erro de rede: distinguir os desfechos na interface reintroduziria, no cliente, exatamente a
 * enumeração de e-mails que o backend evita.
 */
@Component({
  selector: 'dt-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink, ButtonModule, InputTextModule, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!sent()) {
      <h1 class="dt-auth-form__title" i18n="@@forgot.title">Recuperar acesso</h1>
      <p class="dt-auth-form__subtitle" i18n="@@forgot.subtitle">
        Informe seu e-mail e enviaremos um link para definir uma nova senha.
      </p>

      <form class="dt-auth-form__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-auth-form__field">
          <label for="forgot-email" i18n="@@forgot.email">E-mail</label>
          <input
            id="forgot-email"
            type="email"
            pInputText
            formControlName="email"
            autocomplete="email"
            aria-required="true"
            [attr.aria-invalid]="isInvalid()"
            [attr.aria-describedby]="isInvalid() ? 'forgot-email-error' : null"
          />
          @if (isInvalid()) {
            <small
              id="forgot-email-error"
              class="dt-auth-form__error"
              i18n="@@forgot.email.invalid"
            >
              Informe um e-mail válido.
            </small>
          }
        </div>

        <p-button
          type="submit"
          i18n-label="@@forgot.submit"
          label="Enviar link"
          styleClass="w-full"
          [loading]="submitting()"
        />
      </form>

      <nav class="dt-auth-form__links">
        <a routerLink="/auth/login" i18n="@@forgot.toLogin">Voltar para entrar</a>
      </nav>
    } @else {
      <div class="dt-auth-form__status" aria-live="polite">
        <i
          class="pi pi-envelope dt-auth-form__status-icon dt-auth-form__status-icon--success"
          aria-hidden="true"
        ></i>
        <h1 class="dt-auth-form__title" i18n="@@forgot.sent.title">Verifique seu e-mail</h1>
        <!-- SG-02: o texto não confirma nem nega a existência da conta. -->
        <p class="dt-auth-form__subtitle" i18n="@@forgot.sent.text">
          Se o e-mail estiver cadastrado, você receberá as instruções em instantes. O link vale por
          1 hora.
        </p>
        <a routerLink="/auth/login" i18n="@@forgot.toLogin">Voltar para entrar</a>
      </div>
    }
  `,
  styleUrl: './auth-form.scss',
})
export class ForgotPasswordPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);

  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
  });

  private readonly _submitting = signal(false);
  private readonly _submitted = signal(false);
  private readonly _sent = signal(false);

  protected readonly submitting = this._submitting.asReadonly();
  protected readonly sent = this._sent.asReadonly();

  protected isInvalid(): boolean {
    const control = this.form.controls.email;
    return control.invalid && (control.touched || this._submitted());
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('forgot-email')?.focus();
      return;
    }

    this._submitting.set(true);
    try {
      await firstValueFrom(this.authService.forgotPassword(this.form.getRawValue()));
    } catch {
      // Silenciado de propósito (SG-02): ver a nota da classe.
    } finally {
      this._submitting.set(false);
      this._sent.set(true);
    }
  }
}
