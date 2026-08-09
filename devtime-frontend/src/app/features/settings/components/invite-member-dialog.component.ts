import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { Role } from '../../../core/auth/auth.model';
import { InvitationRequest } from '../data/member.model';
import { RoleSelectorComponent } from './role-selector.component';

/**
 * Convite de membro — parte de P32 (FA-06).
 *
 * O papel é escolhido no convite, e não depois: o convidado entra já com o alcance certo, e a
 * alternativa — convidar como `MEMBER` e promover em seguida — deixa uma janela em que a pessoa vê
 * a organização com menos acesso do que deveria e conclui que algo está errado.
 *
 * O convite vale 7 dias (RN-457) e a tela diz isso antes do envio, porque é o que determina se
 * reenviar será necessário.
 */
@Component({
  selector: 'dt-invite-member-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    RoleSelectorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      [header]="title"
    >
      <form class="dt-invite" [formGroup]="form" (ngSubmit)="submit()">
        <div class="dt-invite__field">
          <label for="invite-email" i18n="@@member.invite.email">E-mail</label>
          <input
            id="invite-email"
            type="email"
            pInputText
            formControlName="email"
            autocomplete="email"
            aria-required="true"
            [attr.aria-invalid]="isInvalid('email')"
          />
          @if (isInvalid('email')) {
            <small class="dt-invite__error" i18n="@@member.invite.email.invalid">
              Informe um e-mail válido.
            </small>
          }
        </div>

        <dt-role-selector
          [current]="role()"
          [allowOwner]="allowOwner()"
          inputId="invite-role"
          (changed)="role.set($event)"
        />

        <div class="dt-invite__field">
          <label for="invite-message" i18n="@@member.invite.message">Mensagem (opcional)</label>
          <textarea
            id="invite-message"
            pTextarea
            rows="3"
            formControlName="message"
            maxlength="500"
          ></textarea>
          <small class="dt-invite__hint" i18n="@@member.invite.message.hint">
            Vai no e-mail do convite, que expira em 7 dias.
          </small>
        </div>

        <div class="dt-invite__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="visibleChange.emit(false)"
          />
          <p-button
            type="submit"
            i18n-label="@@member.invite.submit"
            label="Enviar convite"
            icon="pi pi-send"
            [loading]="saving()"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-invite {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-invite__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-invite__field input,
    .dt-invite__field textarea {
      width: 100%;
    }

    .dt-invite__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-invite__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-invite__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class InviteMemberDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  /** Nota ¹: `ADMIN` não concede `OWNER`. */
  readonly allowOwner = input(true);
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly invited = output<InvitationRequest>();

  protected readonly title = $localize`:@@member.invite.title:Convidar membro`;

  protected readonly form = this.formBuilder.group({
    email: this.formBuilder.control('', [Validators.required, Validators.email]),
    message: this.formBuilder.control('', [Validators.maxLength(500)]),
  });

  protected readonly role = signal<Role>('MEMBER');

  private readonly submitted = signal(false);

  protected isInvalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.invalid && (control.touched || this.submitted());
  }

  /** FM-04 / FR-104: o botão nunca é desabilitado por formulário inválido. */
  protected submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      document.getElementById('invite-email')?.focus();
      return;
    }
    const value = this.form.getRawValue();
    this.invited.emit({
      email: value.email.trim(),
      role: this.role(),
      message: value.message.trim() === '' ? undefined : value.message.trim(),
    });
    this.form.reset();
    this.submitted.set(false);
  }
}
