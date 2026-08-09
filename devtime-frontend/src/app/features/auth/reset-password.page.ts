import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';
import { passwordPolicyValidator } from '../../shared/utils/password-policy';

/**
 * Redefinir senha — P05, `POST /auth/reset-password` (T-001-50).
 *
 * RN-461: o token vale 1 hora e é de uso único; `410 DEVTIME-1007` é o desfecho esperado de um link
 * antigo, e a tela oferece o caminho de pedir outro em vez de deixar a pessoa sem saída.
 *
 * CE-AU-05: a redefinição revoga todas as sessões. Depois dela o usuário **precisa** entrar de novo —
 * é por isso que o sucesso leva ao login, e não ao dashboard.
 */
@Component({
  selector: 'dt-reset-password-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    MessageModule,
    PasswordModule,
    PasswordStrengthComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (done()) {
      <div class="dt-auth-form__status" aria-live="polite">
        <i
          class="pi pi-check-circle dt-auth-form__status-icon dt-auth-form__status-icon--success"
          aria-hidden="true"
        ></i>
        <h1 class="dt-auth-form__title" i18n="@@reset.done.title">Senha redefinida</h1>
        <!-- CE-AU-05: todas as sessões foram encerradas; dizer isso evita a impressão de erro. -->
        <p class="dt-auth-form__subtitle" i18n="@@reset.done.text">
          Por segurança, encerramos as sessões abertas. Entre novamente com a nova senha.
        </p>
        <a routerLink="/auth/login" i18n="@@reset.toLogin">Ir para entrar</a>
      </div>
    } @else if (!hasToken()) {
      <div class="dt-auth-form__status">
        <i
          class="pi pi-times-circle dt-auth-form__status-icon dt-auth-form__status-icon--danger"
          aria-hidden="true"
        ></i>
        <h1 class="dt-auth-form__title" i18n="@@reset.noToken.title">Link inválido</h1>
        <p class="dt-auth-form__subtitle" i18n="@@reset.noToken.text">
          Este link não contém um código de redefinição. Solicite um novo.
        </p>
        <a routerLink="/auth/forgot-password" i18n="@@reset.requestNew">Solicitar novo link</a>
      </div>
    } @else {
      <h1 class="dt-auth-form__title" i18n="@@reset.title">Definir nova senha</h1>
      <p class="dt-auth-form__subtitle" i18n="@@reset.subtitle">
        Escolha uma senha que você ainda não usa em outro serviço.
      </p>

      <div aria-live="polite">
        @if (errorMessage() !== null) {
          <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
        }
      </div>

      <form class="dt-auth-form__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-auth-form__field">
          <label for="reset-password" i18n="@@reset.newPassword">Nova senha</label>
          <p-password
            inputId="reset-password"
            formControlName="newPassword"
            [feedback]="false"
            [toggleMask]="true"
            styleClass="w-full"
            inputStyleClass="w-full"
            autocomplete="new-password"
          />
          <dt-password-strength [password]="password()" />
        </div>

        <div class="dt-auth-form__field">
          <label for="reset-confirmation" i18n="@@reset.confirmation">Repita a nova senha</label>
          <p-password
            inputId="reset-confirmation"
            formControlName="confirmation"
            [feedback]="false"
            [toggleMask]="true"
            styleClass="w-full"
            inputStyleClass="w-full"
            autocomplete="new-password"
          />
          @if (mismatch()) {
            <small class="dt-auth-form__error" i18n="@@reset.mismatch">
              As senhas não conferem.
            </small>
          }
        </div>

        <p-button
          type="submit"
          i18n-label="@@reset.submit"
          label="Redefinir senha"
          styleClass="w-full"
          [loading]="submitting()"
        />
      </form>

      <nav class="dt-auth-form__links">
        @if (expired()) {
          <a routerLink="/auth/forgot-password" i18n="@@reset.requestNew">Solicitar novo link</a>
        }
        <a routerLink="/auth/login" i18n="@@reset.toLogin">Ir para entrar</a>
      </nav>
    }
  `,
  styleUrl: './auth-form.scss',
})
export class ResetPasswordPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  /**
   * A confirmação é validada no grupo, não no campo.
   *
   * A igualdade é uma relação entre dois controles; declará-la em um deles faria o erro sumir quando
   * o **outro** fosse editado.
   */
  protected readonly form = this.formBuilder.group(
    {
      newPassword: this.formBuilder.control('', [Validators.required, passwordPolicyValidator()]),
      confirmation: this.formBuilder.control('', [Validators.required]),
    },
    { validators: [matchPasswords] },
  );

  private readonly _submitting = signal(false);
  private readonly _submitted = signal(false);
  private readonly _done = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly submitting = this._submitting.asReadonly();
  protected readonly done = this._done.asReadonly();

  protected readonly password = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: '',
  });

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /** RN-461: `410 DEVTIME-1007` é link expirado ou já usado — o único caminho é pedir outro. */
  protected readonly expired = computed(() => this._error()?.code === 'DEVTIME-1007');

  protected hasToken(): boolean {
    return this.token.trim() !== '';
  }

  protected mismatch(): boolean {
    return (
      this.form.hasError('mismatch') &&
      (this.form.controls.confirmation.touched || this._submitted())
    );
  }

  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    if (this.form.invalid) {
      document.getElementById('reset-password')?.focus();
      return;
    }

    this._submitting.set(true);
    try {
      await firstValueFrom(
        this.authService.resetPassword({
          token: this.token,
          newPassword: this.form.getRawValue().newPassword,
        }),
      );
      this._done.set(true);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._submitting.set(false);
    }
  }
}

/** Validador de grupo: as duas senhas precisam coincidir. */
function matchPasswords(group: AbstractControl): ValidationErrors | null {
  const password = group.get('newPassword')?.value;
  const confirmation = group.get('confirmation')?.value;
  if (confirmation === '' || password === confirmation) {
    return null;
  }
  return { mismatch: true };
}
