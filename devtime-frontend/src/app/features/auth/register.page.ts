import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';
import { passwordPolicyValidator } from '../../shared/utils/password-policy';

/**
 * Cadastro — P02 de `05-ui/pages.md`, layout L1, `POST /auth/register` (T-001-48).
 *
 * A conta nasce `PENDING_ACTIVATION` e a resposta **não** traz token (CP-08). Por isso a tela não
 * navega para o dashboard ao concluir: ela troca para o estado "verifique seu e-mail", com a ação de
 * reenvio à mão. Redirecionar para o login aqui produziria um `403 DEVTIME-1008` imediato, que o
 * usuário leria como falha do cadastro que acabou de dar certo.
 */
@Component({
  selector: 'dt-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    MessageModule,
    PasswordModule,
    PasswordStrengthComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (registeredEmail() === null) {
      <h1 class="dt-auth-form__title" i18n="@@register.title">Criar conta</h1>
      <p class="dt-auth-form__subtitle" i18n="@@register.subtitle">
        Sua organização é criada junto com a conta. Você poderá convidar sua equipe depois.
      </p>

      <!-- A11Y-05 / FR-145: o erro é anunciado por leitor de tela. -->
      <div aria-live="polite">
        @if (errorMessage() !== null) {
          <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
        }
      </div>

      <form class="dt-auth-form__form" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-auth-form__field">
          <label for="register-full-name" i18n="@@register.fullName">Nome completo</label>
          <input
            id="register-full-name"
            type="text"
            pInputText
            formControlName="fullName"
            autocomplete="name"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('fullName')"
            [attr.aria-describedby]="isInvalid('fullName') ? 'register-full-name-error' : null"
          />
          @if (isInvalid('fullName')) {
            <small
              id="register-full-name-error"
              class="dt-auth-form__error"
              i18n="@@register.fullName.invalid"
            >
              Informe seu nome completo, com ao menos 2 caracteres.
            </small>
          }
        </div>

        <div class="dt-auth-form__field">
          <label for="register-email" i18n="@@register.email">E-mail</label>
          <input
            id="register-email"
            type="email"
            pInputText
            formControlName="email"
            autocomplete="email"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('email')"
            [attr.aria-describedby]="isInvalid('email') ? 'register-email-error' : null"
          />
          @if (isInvalid('email')) {
            <small id="register-email-error" class="dt-auth-form__error">
              @if (serverError('email') !== null) {
                {{ serverError('email') }}
              } @else {
                <ng-container i18n="@@register.email.invalid"
                  >Informe um e-mail válido.</ng-container
                >
              }
            </small>
          }
        </div>

        <div class="dt-auth-form__field">
          <label for="register-password" i18n="@@register.password">Senha</label>
          <p-password
            inputId="register-password"
            formControlName="password"
            [feedback]="false"
            [toggleMask]="true"
            styleClass="w-full"
            inputStyleClass="w-full"
            autocomplete="new-password"
          />
          <!-- A força substitui a mensagem de erro genérica: ela diz o que falta, não só que falhou. -->
          <dt-password-strength [password]="password()" />
        </div>

        <div class="dt-auth-form__field">
          <label for="register-tenant-name" i18n="@@register.tenantName">
            Nome da organização (opcional)
          </label>
          <input
            id="register-tenant-name"
            type="text"
            pInputText
            formControlName="tenantName"
            autocomplete="organization"
            [attr.aria-invalid]="isInvalid('tenantName')"
            aria-describedby="register-tenant-name-hint"
          />
          <small
            id="register-tenant-name-hint"
            class="dt-auth-form__hint"
            i18n="@@register.tenantName.hint"
          >
            Em branco, usamos seu nome.
          </small>
          @if (isInvalid('tenantName')) {
            <small class="dt-auth-form__error" i18n="@@register.tenantName.invalid">
              O nome da organização precisa ter entre 2 e 120 caracteres.
            </small>
          }
        </div>

        <div class="dt-auth-form__checkbox">
          <p-checkbox
            inputId="register-terms"
            formControlName="acceptedTerms"
            [binary]="true"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('acceptedTerms')"
          />
          <label for="register-terms" i18n="@@register.terms">
            Li e aceito os termos de uso e a política de privacidade.
          </label>
        </div>
        @if (isInvalid('acceptedTerms')) {
          <small class="dt-auth-form__error" i18n="@@register.terms.required">
            É necessário aceitar os termos para continuar.
          </small>
        }

        <p-button
          type="submit"
          i18n-label="@@register.submit"
          label="Criar conta"
          styleClass="w-full"
          [loading]="submitting()"
        />
      </form>

      <nav class="dt-auth-form__links">
        <a routerLink="/auth/login" i18n="@@register.toLogin">Já tenho uma conta</a>
      </nav>
    } @else {
      <div class="dt-auth-form__status">
        <i
          class="pi pi-envelope dt-auth-form__status-icon dt-auth-form__status-icon--success"
          aria-hidden="true"
        ></i>
        <h1 class="dt-auth-form__title" i18n="@@register.sent.title">Confirme seu e-mail</h1>
        <p class="dt-auth-form__subtitle" i18n="@@register.sent.text">
          Enviamos um link de verificação para {{ registeredEmail() }}. O link vale por 7 dias.
        </p>

        <div aria-live="polite">
          @if (resendMessage() !== null) {
            <p-message severity="info" [text]="resendMessage()!" styleClass="w-full mb-3" />
          }
        </div>

        <p-button
          i18n-label="@@register.sent.resend"
          label="Reenviar e-mail"
          severity="secondary"
          [outlined]="true"
          [loading]="resending()"
          (onClick)="resend()"
        />
        <a routerLink="/auth/login" i18n="@@register.toLogin">Já tenho uma conta</a>
      </div>
    }
  `,
  styleUrl: './auth-form.scss',
})
export class RegisterPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);

  /** FM-01 / FR-100: Reactive Forms tipados; FR-101: builder não-nulável. */
  protected readonly form = this.formBuilder.group({
    fullName: this.formBuilder.control('', [Validators.required, Validators.minLength(2)]),
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    password: this.formBuilder.control('', [Validators.required, passwordPolicyValidator()]),
    // Opcional no contrato: em branco, o backend usa o nome do titular (entities.md §6.1).
    tenantName: this.formBuilder.control('', [Validators.minLength(2), Validators.maxLength(120)]),
    acceptedTerms: this.formBuilder.control(false, [Validators.requiredTrue]),
  });

  private readonly _submitting = signal(false);
  private readonly _submitted = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _registeredEmail = signal<string | null>(null);
  private readonly _resending = signal(false);
  private readonly _resendMessage = signal<string | null>(null);

  protected readonly submitting = this._submitting.asReadonly();
  protected readonly resending = this._resending.asReadonly();
  protected readonly resendMessage = this._resendMessage.asReadonly();
  protected readonly registeredEmail = this._registeredEmail.asReadonly();

  /** Valor corrente da senha, para a barra de força. `toSignal` cancela a inscrição sozinho. */
  protected readonly password = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  /** Mensagem que o servidor atribuiu ao campo, quando houver (FM-06). */
  protected serverError(field: keyof typeof this.form.controls): string | null {
    const error: unknown = this.form.controls[field].errors?.['server'];
    return typeof error === 'string' ? error : null;
  }

  /** FM-04 / FR-104: o botão nunca é desabilitado por formulário inválido. */
  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    if (this.form.invalid) {
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    this._submitting.set(true);
    try {
      await firstValueFrom(
        this.authService.register({
          email: value.email,
          password: value.password,
          fullName: value.fullName,
          // Campo vazio não viaja: o contrato distingue ausente de string em branco só no default.
          tenantName: value.tenantName.trim() === '' ? undefined : value.tenantName.trim(),
          timezone: this.detectTimezone(),
          acceptedTerms: value.acceptedTerms,
        }),
      );
      this._registeredEmail.set(value.email);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
        this.applyFieldErrors(error);
      }
    } finally {
      this._submitting.set(false);
    }
  }

  /** SG-01: a resposta é idêntica com e sem conta correspondente — a tela reflete isso. */
  protected async resend(): Promise<void> {
    const email = this._registeredEmail();
    if (email === null) {
      return;
    }
    this._resending.set(true);
    try {
      const response = await firstValueFrom(this.authService.resendVerification({ email }));
      this._resendMessage.set(response.message);
    } catch {
      this._resendMessage.set(
        $localize`:@@register.sent.resendFailed:Não foi possível reenviar agora. Tente novamente em instantes.`,
      );
    } finally {
      this._resending.set(false);
    }
  }

  /**
   * FR-070 / FM-06: erro de campo do servidor vai para o campo, nunca para toast.
   *
   * `DEVTIME-2452` (e-mail em uso) chega sem `errors[]` por ser conflito de recurso, e ainda assim é
   * um erro **daquele campo**: sem isto, a pessoa lê "e-mail já está em uso" no topo e não sabe qual
   * dos dois endereços da tela é o problema.
   */
  private applyFieldErrors(problem: ProblemDetail): void {
    if (problem.code === 'DEVTIME-2452') {
      this.form.controls.email.setErrors({
        server: $localize`:@@register.email.taken:Este e-mail já está em uso.`,
      });
      return;
    }
    for (const fieldError of problem.errors ?? []) {
      const control = this.form.get(fieldError.field);
      control?.setErrors({ server: fieldError.message });
    }
  }

  /**
   * Fuso do navegador, validado como ID IANA pelo backend (INV-TEN-03).
   *
   * Enviar o detectado evita perguntar algo que o navegador já sabe. Se a API de internacionalização
   * não estiver disponível, o campo é omitido e o backend aplica o padrão.
   */
  private detectTimezone(): string | undefined {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return timezone === undefined || timezone === '' ? undefined : timezone;
  }

  /** FR-105: o foco vai para o primeiro campo inválido. */
  private focusFirstInvalidField(): void {
    const order: readonly [keyof typeof this.form.controls, string][] = [
      ['fullName', 'register-full-name'],
      ['email', 'register-email'],
      ['password', 'register-password'],
      ['tenantName', 'register-tenant-name'],
      ['acceptedTerms', 'register-terms'],
    ];
    const first = order.find(([field]) => this.form.controls[field].invalid);
    if (first !== undefined) {
      document.getElementById(first[1])?.focus();
    }
  }
}
