import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';

/** Mínimo aceito pelo backend para o motivo do bloqueio (RN-308). */
const REASON_MIN_LENGTH = 10;

/**
 * Motivo do bloqueio — `dt-block-reason-dialog` (T-007-30).
 *
 * RN-308: entrar em `BLOCKED` exige motivo. A exigência não é burocracia: um ticket bloqueado sem
 * motivo registrado vira um item parado que ninguém sabe destravar, e o tempo entre o bloqueio e a
 * pergunta "por que isto está parado?" é o que a regra evita.
 */
@Component({
  selector: 'dt-block-reason-dialog',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, TextareaModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '30rem' }"
      [header]="title"
    >
      <form class="dt-block" [formGroup]="form" (ngSubmit)="confirm()">
        <label for="block-reason" i18n="@@ticket.block.reason">Por que está bloqueado? *</label>
        <textarea
          id="block-reason"
          pTextarea
          rows="3"
          formControlName="reason"
          maxlength="500"
          aria-required="true"
          [attr.aria-invalid]="invalid()"
        ></textarea>
        @if (invalid()) {
          <small class="dt-block__error" i18n="@@ticket.block.reason.invalid">
            Descreva o impedimento com ao menos 10 caracteres.
          </small>
        }

        <div class="dt-block__actions">
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
            i18n-label="@@ticket.block.submit"
            label="Bloquear ticket"
            severity="danger"
            [loading]="saving()"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-block {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
    }

    .dt-block textarea {
      width: 100%;
    }

    .dt-block__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-block__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
      margin-top: var(--dt-space-2);
    }
  `,
})
export class BlockReasonDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<string>();

  protected readonly title = $localize`:@@ticket.block.title:Bloquear ticket`;

  protected readonly form = this.formBuilder.group({
    reason: this.formBuilder.control(''),
  });

  private readonly _submitted = signal(false);

  protected invalid(): boolean {
    return this._submitted() && this.form.controls.reason.value.trim().length < REASON_MIN_LENGTH;
  }

  protected confirm(): void {
    this._submitted.set(true);
    if (this.invalid()) {
      document.getElementById('block-reason')?.focus();
      return;
    }
    this.confirmed.emit(this.form.controls.reason.value.trim());
  }
}
