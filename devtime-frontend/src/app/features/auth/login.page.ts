import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail } from '../../core/error/problem-detail.model';

/**
 * Tela de login — P01 de `05-ui/pages.md`, layout L1.
 *
 * Entrega a estrutura da tela e o transporte da credencial. As regras de negócio do login — bloqueio
 * após 5 falhas (RN-453), mensagem uniforme para e-mail inexistente (AU-01), tratamento de `423` e de
 * e-mail não verificado — pertencem à feature 001 (T-001-47) e **não** são implementadas aqui: sem os
 * endpoints correspondentes, qualquer tratamento seria comportamento inventado.
 */
@Component({
  selector: 'dt-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="dt-login__title" i18n="@@login.title">Entrar</h1>
    <p class="dt-login__subtitle" i18n="@@login.subtitle">
      Use seu e-mail e senha para acessar o DevTime.
    </p>

    <!-- A11Y-05 / FR-145: o erro é anunciado por leitor de tela. -->
    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      <!-- FA-02: e-mail não verificado sai com a ação de reenvio, não só com o diagnóstico. -->
      @if (unverified()) {
        <p-button
          i18n-label="@@login.resendVerification"
          label="Reenviar e-mail de verificação"
          severity="secondary"
          [outlined]="true"
          [loading]="resending()"
          (onClick)="resendVerification()"
        />
      }
      @if (resendMessage() !== null) {
        <p-message severity="info" [text]="resendMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    <form class="dt-login__form" [formGroup]="form" (ngSubmit)="submit()">
      <div class="dt-login__field">
        <!-- FR-107 / A11Y-04: rótulo associado por for/id. -->
        <label for="login-email" i18n="@@login.email">E-mail</label>
        <input
          id="login-email"
          type="email"
          pInputText
          formControlName="email"
          autocomplete="email"
          aria-required="true"
          [attr.aria-invalid]="isInvalid('email')"
          [attr.aria-describedby]="isInvalid('email') ? 'login-email-error' : null"
        />
        <!-- FR-103 / FM-03: erro de campo abaixo do campo, nunca em toast. -->
        @if (isInvalid('email')) {
          <small id="login-email-error" class="dt-login__error" i18n="@@login.email.invalid">
            Informe um e-mail válido.
          </small>
        }
      </div>

      <div class="dt-login__field">
        <label for="login-password" i18n="@@login.password">Senha</label>
        <p-password
          inputId="login-password"
          formControlName="password"
          [feedback]="false"
          [toggleMask]="true"
          styleClass="w-full"
          inputStyleClass="w-full"
          autocomplete="current-password"
        />
        @if (isInvalid('password')) {
          <small id="login-password-error" class="dt-login__error" i18n="@@login.password.required">
            Informe sua senha.
          </small>
        }
      </div>

      <p-button
        type="submit"
        i18n-label="@@login.submit"
        label="Entrar"
        styleClass="w-full"
        [loading]="submitting()"
      />
    </form>

    <nav class="dt-login__links">
      <a routerLink="/auth/forgot-password" i18n="@@login.forgot">Esqueci minha senha</a>
      <a routerLink="/auth/register" i18n="@@login.toRegister">Criar uma conta</a>
    </nav>
  `,
  styleUrl: './login.page.scss',
})
export class LoginPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  /** FM-01 / FR-100: Reactive Forms tipados; template-driven é proibido. FR-101: builder não-nulável. */
  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    password: this.formBuilder.control('', [Validators.required]),
  });

  private readonly _submitting = signal(false);
  private readonly _submitted = signal(false);
  private readonly _resending = signal(false);
  private readonly _resendMessage = signal<string | null>(null);

  protected readonly submitting = this._submitting.asReadonly();
  protected readonly resending = this._resending.asReadonly();
  protected readonly resendMessage = this._resendMessage.asReadonly();

  /**
   * AU-01: a falha de credencial usa uma mensagem única e genérica.
   *
   * `DEVTIME-1001` cobre dois casos no contrato — token expirado **e** credencial inválida. O texto
   * global fala em sessão expirada, que é o caso comum no restante do produto; aqui, onde a pessoa
   * acabou de digitar e-mail e senha, ele acusaria um problema que não é o dela. A mensagem local
   * também não distingue e-mail inexistente de senha errada, para não permitir enumeração.
   */
  protected readonly errorMessage = computed(() => {
    const problem = this.authStore.error();
    if (problem === null) {
      return null;
    }
    if (problem.code === 'DEVTIME-1001') {
      return $localize`:@@login.invalidCredentials:E-mail ou senha inválidos.`;
    }
    return messageForCode(problem.code, problem.detail);
  });

  /** FA-02: `403 DEVTIME-1008` é conta pendente de verificação. */
  protected readonly unverified = computed(() => this.authStore.error()?.code === 'DEVTIME-1008');

  /**
   * Um campo só exibe erro depois da primeira tentativa de envio ou de ter sido tocado.
   *
   * Exibir erro enquanto o usuário digita o primeiro caractere sinaliza falha antes de haver falha.
   */
  protected isInvalid(field: 'email' | 'password'): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  /**
   * FM-04 / FR-104: o botão não é desabilitado por formulário inválido.
   *
   * Desabilitar esconde do usuário **o que** está errado. O comportamento correto é permitir a
   * tentativa, exibir os erros e mover o foco para o primeiro campo inválido (FR-105).
   */
  protected async submit(): Promise<void> {
    this._submitted.set(true);
    this.authStore.setError(null);
    this._resendMessage.set(null);

    if (this.form.invalid) {
      this.focusFirstInvalidField();
      return;
    }

    this._submitting.set(true);
    try {
      await firstValueFrom(this.authService.login(this.form.getRawValue()));
      await this.router.navigate(['/']);
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this.authStore.setError(error);
      }
    } finally {
      this._submitting.set(false);
    }
  }

  /** Reenvio da verificação a partir do próprio login (FA-02). SG-01: resposta sempre igual. */
  protected async resendVerification(): Promise<void> {
    const email = this.form.controls.email.value;
    this._resending.set(true);
    try {
      const response = await firstValueFrom(this.authService.resendVerification({ email }));
      this._resendMessage.set(response.message);
    } catch {
      this._resendMessage.set(
        $localize`:@@login.resendFailed:Não foi possível reenviar agora. Tente novamente em instantes.`,
      );
    } finally {
      this._resending.set(false);
    }
  }

  /** FR-105: ao submeter com erros, o foco vai para o primeiro campo inválido. */
  private focusFirstInvalidField(): void {
    const field = this.form.controls.email.invalid ? 'login-email' : 'login-password';
    document.getElementById(field)?.focus();
  }
}
