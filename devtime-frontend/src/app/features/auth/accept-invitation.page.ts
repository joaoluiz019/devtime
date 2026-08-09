import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { firstValueFrom } from 'rxjs';
import { InvitationPreview } from '../../core/auth/auth.model';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';
import { PasswordStrengthComponent } from '../../shared/components/password-strength/password-strength.component';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '../../shared/models/role.model';
import { passwordPolicyValidator } from '../../shared/utils/password-policy';

/**
 * Aceite de convite — P07, layout L1, §5.12 de `authentication.md`.
 *
 * A tela ramifica em três, e a ramificação vem do servidor: `userExists` diz se a conta já pode
 * autenticar. Decidir isso no cliente — por exemplo, tentando o login e vendo falhar — revelaria a
 * existência de contas para quem tivesse um token de convite qualquer.
 *
 * CX-09: quem já está autenticado em outra organização aceita **sem** trocar de sessão. O produto
 * não muda a organização corrente por baixo de quem estava trabalhando; a troca é feita pelo
 * seletor, quando a pessoa quiser.
 *
 * Sem `guestGuard`: os três casos precisam da mesma URL, e mandar o usuário autenticado para a raiz
 * — que é o que aquele guard faz — descartaria o convite que ele acabou de abrir.
 */
@Component({
  selector: 'dt-accept-invitation-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    MessageModule,
    PasswordModule,
    PasswordStrengthComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <p class="dt-auth-form__subtitle" i18n="@@invitation.loading">Carregando o convite…</p>
    } @else if (invitation(); as preview) {
      @if (accepted()) {
        <div class="dt-auth-form__status">
          <i
            class="pi pi-check-circle dt-auth-form__status-icon dt-auth-form__status-icon--success"
            aria-hidden="true"
          ></i>
          <h1 class="dt-auth-form__title" i18n="@@invitation.accepted.title">Convite aceito</h1>
          <p class="dt-auth-form__subtitle">{{ acceptedMessage() }}</p>
          <p-button
            i18n-label="@@invitation.accepted.continue"
            label="Continuar"
            styleClass="w-full"
            (onClick)="continue_()"
          />
        </div>
      } @else {
        <h1 class="dt-auth-form__title" i18n="@@invitation.title">
          Você foi convidado para {{ preview.tenantName }}
        </h1>
        <p class="dt-auth-form__subtitle" i18n="@@invitation.subtitle">
          {{ preview.invitedByName }} convidou {{ preview.email }} para participar como
          {{ roleName() }}.
        </p>
        <p class="dt-auth-form__hint">{{ roleDescription() }}</p>

        <div aria-live="polite">
          @if (errorMessage() !== null) {
            <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
          }

          @if (sessionMismatch()) {
            <!-- O convite é de um endereço, a sessão é de outro: aceitar vincularia a conta errada. -->
            <p-message severity="warn" styleClass="w-full mb-3">
              <span i18n="@@invitation.mismatch">
                Você está autenticado como {{ currentEmail() }}, e o convite é para
                {{ preview.email }}. Aceitar vincula a conta em uso.
              </span>
            </p-message>
          }
        </div>

        @if (isAuthenticated()) {
          <!-- CX-09: nenhuma credencial é pedida, e a organização corrente não muda. -->
          <p class="dt-auth-form__hint" i18n="@@invitation.authenticated">
            Sua organização atual continua aberta. Você poderá alternar entre elas depois.
          </p>
          <p-button
            i18n-label="@@invitation.accept"
            label="Aceitar convite"
            styleClass="w-full"
            [loading]="submitting()"
            (onClick)="accept()"
          />
        } @else {
          <form class="dt-auth-form__form" [formGroup]="form" (ngSubmit)="accept()">
            @if (!preview.userExists) {
              <div class="dt-auth-form__field">
                <label for="invitation-full-name" i18n="@@invitation.fullName">Nome completo</label>
                <input
                  id="invitation-full-name"
                  type="text"
                  pInputText
                  formControlName="fullName"
                  autocomplete="name"
                  aria-required="true"
                  [attr.aria-invalid]="isInvalid('fullName')"
                />
                @if (isInvalid('fullName')) {
                  <small class="dt-auth-form__error" i18n="@@invitation.fullName.invalid">
                    Informe seu nome completo, com ao menos 2 caracteres.
                  </small>
                }
              </div>
            }

            <div class="dt-auth-form__field">
              <label for="invitation-password">
                @if (preview.userExists) {
                  <ng-container i18n="@@invitation.password">Sua senha</ng-container>
                } @else {
                  <ng-container i18n="@@invitation.newPassword">Crie uma senha</ng-container>
                }
              </label>
              <p-password
                inputId="invitation-password"
                formControlName="password"
                [feedback]="false"
                [toggleMask]="true"
                styleClass="w-full"
                inputStyleClass="w-full"
                [autocomplete]="preview.userExists ? 'current-password' : 'new-password'"
              />
              @if (!preview.userExists) {
                <dt-password-strength [password]="password()" />
              }
            </div>

            <p-button
              type="submit"
              i18n-label="@@invitation.accept"
              label="Aceitar convite"
              styleClass="w-full"
              [loading]="submitting()"
            />
          </form>
        }
      }
    } @else {
      <div class="dt-auth-form__status">
        <i
          class="pi pi-times-circle dt-auth-form__status-icon dt-auth-form__status-icon--danger"
          aria-hidden="true"
        ></i>
        <h1 class="dt-auth-form__title" i18n="@@invitation.invalid.title">Convite indisponível</h1>
        <p class="dt-auth-form__subtitle">{{ errorMessage() }}</p>
        <a routerLink="/auth/login" i18n="@@invitation.toLogin">Ir para a entrada</a>
      </div>
    }
  `,
  styleUrl: './auth-form.scss',
})
export class AcceptInvitationPage implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  /** Vem do parâmetro de rota por `withComponentInputBinding` (FR-021). */
  readonly token = input.required<string>();

  protected readonly form = this.formBuilder.group({
    fullName: this.formBuilder.control('', [Validators.minLength(2)]),
    password: this.formBuilder.control('', [Validators.required]),
  });

  private readonly _invitation = signal<InvitationPreview | null>(null);
  private readonly _loading = signal(true);
  private readonly _submitting = signal(false);
  private readonly _submitted = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _acceptedMessage = signal<string | null>(null);

  protected readonly invitation = this._invitation.asReadonly();
  protected readonly loading = this._loading.asReadonly();
  protected readonly submitting = this._submitting.asReadonly();
  protected readonly acceptedMessage = this._acceptedMessage.asReadonly();

  protected readonly accepted = computed(() => this._acceptedMessage() !== null);

  protected readonly isAuthenticated = computed(() => this.authStore.isAuthenticated());

  protected readonly currentEmail = computed(() => this.authStore.user()?.email ?? null);

  protected readonly sessionMismatch = computed(() => {
    const email = this.currentEmail();
    const invited = this._invitation()?.email;
    return email !== null && invited !== undefined && email.toLowerCase() !== invited.toLowerCase();
  });

  protected readonly password = toSignal(this.form.controls.password.valueChanges, {
    initialValue: '',
  });

  protected readonly roleName = computed(() => {
    const role = this._invitation()?.role;
    return role === undefined ? '' : ROLE_LABELS[role];
  });

  protected readonly roleDescription = computed(() => {
    const role = this._invitation()?.role;
    return role === undefined ? '' : ROLE_DESCRIPTIONS[role];
  });

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /**
   * A consulta espera o `ngOnInit`, não o construtor: o parâmetro de rota chega por `setInput`,
   * depois da construção, e ler `token()` antes disso lançaria por entrada obrigatória ausente.
   */
  ngOnInit(): void {
    void this.peek();
  }

  /**
   * Consulta o convite ao abrir a tela.
   *
   * Falhar aqui é estado final, não erro sobre um formulário: token expirado (`DEVTIME-2457`),
   * revogado (`DEVTIME-2458`) ou já aceito (`DEVTIME-2459`) não têm o que tentar de novo, e mostrar
   * um formulário de senha ao lado da mensagem convidaria a insistir.
   */
  private async peek(): Promise<void> {
    try {
      const preview = await firstValueFrom(this.authService.peekInvitation(this.token()));
      this._invitation.set(preview);
      // Conta nova precisa do nome; conta existente não o informa, e exigi-lo recusaria o aceite.
      if (!preview.userExists) {
        this.form.controls.fullName.addValidators(Validators.required);
        this.form.controls.password.addValidators(passwordPolicyValidator());
        this.form.controls.fullName.updateValueAndValidity();
        this.form.controls.password.updateValueAndValidity();
      }
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : null);
    } finally {
      this._loading.set(false);
    }
  }

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this._submitted());
  }

  protected async accept(): Promise<void> {
    this._submitted.set(true);
    this._error.set(null);

    const preview = this._invitation();
    if (preview === null) {
      return;
    }

    // Autenticado, o corpo vai vazio: pedir senha de novo a quem já tem sessão não prova nada.
    const authenticated = this.isAuthenticated();
    if (!authenticated && this.form.invalid) {
      document
        .getElementById(preview.userExists ? 'invitation-password' : 'invitation-full-name')
        ?.focus();
      return;
    }

    const value = this.form.getRawValue();
    this._submitting.set(true);
    try {
      const response = await firstValueFrom(
        this.authService.acceptInvitation(
          this.token(),
          authenticated
            ? {}
            : {
                fullName: preview.userExists ? undefined : value.fullName.trim(),
                password: value.password,
              },
        ),
      );
      this._acceptedMessage.set(
        'message' in response && typeof response.message === 'string'
          ? response.message
          : $localize`:@@invitation.accepted.text:Seu acesso a ${preview.tenantName}:tenant: está ativo.`,
      );
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._submitting.set(false);
    }
  }

  /**
   * Onde a pessoa cai depois do aceite.
   *
   * Quem já tinha sessão volta ao produto na organização em que estava (CX-09). Quem acabou de
   * autenticar recebe a sessão da organização convidada e vai para o dashboard; a seleção de
   * organização só aparece se o servidor a exigir.
   */
  protected async continue_(): Promise<void> {
    await this.router.navigate([this.authStore.hasTenantSelected() ? '/' : '/auth/select-tenant']);
  }
}
