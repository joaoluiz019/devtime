import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { ReopenPeriodRequest } from '../data/period.model';

/** RN-242: a justificativa da reabertura tem no mínimo 10 caracteres. */
const MIN_REASON = 10;

/**
 * Diálogo de reabertura do período (T-011-32).
 *
 * **Por que a justificativa é obrigatória:** a reabertura altera um relatório **já entregue** ao
 * cliente. Sem o motivo registrado, a operação é indefensável em disputa contratual (RN-242).
 *
 * O aviso sobre o relatório emitido não é decorativo: quem reabre precisa saber, antes de confirmar,
 * que pode ter de reenviar o documento ao cliente (§10 de `pages.md`).
 */
@Component({
  selector: 'dt-reopen-dialog',
  imports: [ReactiveFormsModule, ButtonModule, DialogModule, TextareaModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: '30rem' }"
      [header]="title"
      (onHide)="onCancel()"
    >
      <form
        class="dt-reopen-dialog"
        [formGroup]="form"
        (ngSubmit)="onSubmit()"
        (keydown.control.enter)="onSubmit()"
        (keydown.meta.enter)="onSubmit()"
      >
        <!-- Aviso de contexto: banner persistente na área afetada (§9 do design system). -->
        <p class="dt-reopen-dialog__warning" role="alert">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span i18n="@@reopen.warning">
            O relatório deste período já pode ter sido enviado ao cliente. Reabrir muda os números e
            pode exigir o reenvio do documento.
          </span>
        </p>

        <div class="dt-field">
          <label for="reopen-reason">
            <span i18n="@@reopen.field.reason">Motivo da reabertura</span>
            <span class="dt-field__required" aria-hidden="true">*</span>
          </label>
          <textarea
            id="reopen-reason"
            pTextarea
            rows="3"
            formControlName="reason"
            aria-required="true"
            aria-describedby="reopen-reason-help"
            [attr.aria-invalid]="reasonControl.touched && reasonControl.invalid"
          ></textarea>
          <p id="reopen-reason-help" class="dt-field__help" i18n="@@reopen.field.reason.help">
            Fica registrado em auditoria. Mínimo de 10 caracteres.
          </p>
          @if (reasonControl.touched && reasonControl.invalid) {
            <p class="dt-field__error" role="alert" aria-live="polite" i18n="@@reopen.error.reason">
              O motivo precisa ter ao menos 10 caracteres.
            </p>
          }
        </div>

        <footer class="dt-reopen-dialog__actions">
          <p-button
            type="button"
            severity="secondary"
            [text]="true"
            i18n-label="@@action.cancel"
            label="Cancelar"
            (onClick)="onCancel()"
          />
          <p-button
            type="submit"
            i18n-label="@@reopen.action.confirm"
            label="Reabrir período"
            [loading]="submitting()"
            [disabled]="submitting()"
          />
        </footer>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-reopen-dialog {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-4);
    }

    .dt-reopen-dialog__warning {
      display: flex;
      gap: var(--dt-space-2);
      margin: 0;
      padding: var(--dt-space-3);
      border-left: 3px solid var(--dt-color-warning);
      border-radius: var(--dt-radius-sm);
      background-color: var(--dt-surface-page);
      color: var(--dt-text-primary);
      font-size: var(--dt-text-sm);
      line-height: var(--dt-text-sm-line);
    }

    .dt-reopen-dialog__warning i {
      color: var(--dt-color-warning);
    }

    .dt-reopen-dialog__actions {
      display: flex;
      gap: var(--dt-space-2);
      justify-content: flex-end;
    }
  `,
})
export class ReopenDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly submitting = input<boolean>(false);

  /** `confirmed` e não `confirm`: `confirm` é evento nativo do DOM (`no-output-native`). */
  readonly confirmed = output<ReopenPeriodRequest>();
  readonly cancelled = output<void>();

  protected readonly form = this.formBuilder.group({
    reason: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(MIN_REASON),
      Validators.maxLength(1000),
    ]),
  });

  protected readonly reasonControl = this.form.controls.reason;

  protected readonly title = $localize`:@@reopen.dialog.title:Reabrir período fechado`;

  protected onCancel(): void {
    this.form.reset();
    this.cancelled.emit();
  }

  /** FR-104: a tentativa é permitida e os erros aparecem; o botão não é desabilitado por invalidez. */
  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.confirmed.emit(this.form.getRawValue());
    this.form.reset();
  }
}
