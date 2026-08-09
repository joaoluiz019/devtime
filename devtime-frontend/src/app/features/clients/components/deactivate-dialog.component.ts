import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';

/**
 * Confirmação de inativação de cliente — `dt-deactivate-dialog` (T-003-21).
 *
 * RN-405: o cliente inativo deixa de aceitar **novos** contratos. RN-407: os contratos em vigor
 * **continuam operando** — e é isso que a caixa de confirmação precisa dizer com todas as letras. A
 * leitura natural de "inativar cliente" é "parar tudo"; sem esta frase, quem confirma acredita ter
 * interrompido faturamento que segue correndo.
 */
@Component({
  selector: 'dt-deactivate-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    MessageModule,
    TextareaModule,
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
      <div class="dt-deactivate">
        <p class="dt-deactivate__text" i18n="@@client.deactivate.text">
          O cliente deixa de aceitar novos contratos. Ele continua visível e pode ser reativado a
          qualquer momento.
        </p>

        @if (activeContracts() > 0) {
          <!-- RN-407: o impacto é declarado antes da confirmação, não depois. -->
          <p-message severity="warn" styleClass="w-full">
            <span i18n="@@client.deactivate.impact">
              Este cliente tem {{ activeContracts() }} contrato(s) ativo(s). Eles continuam operando
              normalmente: horas seguem sendo registradas e os períodos seguem fechando.
            </span>
          </p-message>
        }

        <form class="dt-deactivate__form" [formGroup]="form" (ngSubmit)="confirm()">
          @if (activeContracts() > 0) {
            <div class="dt-deactivate__checkbox">
              <p-checkbox
                inputId="deactivate-confirm"
                formControlName="confirmActiveContracts"
                [binary]="true"
                aria-required="true"
              />
              <label for="deactivate-confirm" i18n="@@client.deactivate.confirm">
                Entendi que os contratos ativos continuam operando.
              </label>
            </div>
            @if (confirmationMissing()) {
              <small class="dt-deactivate__error" i18n="@@client.deactivate.confirmRequired">
                Confirme para continuar.
              </small>
            }
          }

          <div class="dt-deactivate__field">
            <label for="deactivate-reason" i18n="@@client.deactivate.reason">
              Motivo (opcional)
            </label>
            <textarea
              id="deactivate-reason"
              pTextarea
              rows="3"
              formControlName="reason"
              maxlength="500"
            ></textarea>
          </div>

          <div class="dt-deactivate__actions">
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
              i18n-label="@@client.deactivate.submit"
              label="Inativar cliente"
              severity="warn"
              [loading]="saving()"
            />
          </div>
        </form>
      </div>
    </p-dialog>
  `,
  styles: `
    .dt-deactivate,
    .dt-deactivate__form {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-deactivate__text {
      margin: 0;
      font-size: var(--dt-text-sm);
    }

    .dt-deactivate__checkbox {
      display: flex;
      align-items: flex-start;
      gap: var(--dt-space-2);
      font-size: var(--dt-text-sm);
    }

    .dt-deactivate__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-deactivate__field textarea {
      width: 100%;
    }

    .dt-deactivate__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-deactivate__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class DeactivateDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly activeContracts = input(0);
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<{ confirmActiveContracts: boolean; reason?: string }>();

  protected readonly title = $localize`:@@client.deactivate.title:Inativar cliente`;

  protected readonly form = this.formBuilder.group({
    confirmActiveContracts: this.formBuilder.control(false),
    reason: this.formBuilder.control('', [Validators.maxLength(500)]),
  });

  private readonly _submitted = signal(false);

  /**
   * A confirmação só é exigida quando há contratos ativos.
   *
   * Exibi-la sempre transformaria a caixa em ritual: quem inativa um cliente sem contrato nenhum não
   * tem impacto a compreender, e um aviso constante deixa de ser lido quando passa a importar.
   */
  protected confirmationMissing(): boolean {
    return (
      this._submitted() &&
      this.activeContracts() > 0 &&
      !this.form.controls.confirmActiveContracts.value
    );
  }

  protected confirm(): void {
    this._submitted.set(true);
    if (this.confirmationMissing() || this.form.invalid) {
      return;
    }
    const value = this.form.getRawValue();
    this.confirmed.emit({
      confirmActiveContracts: value.confirmActiveContracts,
      reason: value.reason === '' ? undefined : value.reason,
    });
  }
}
