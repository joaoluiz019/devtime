import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';

/** Contrato de destino oferecido no seletor. */
export interface MoveContractOption {
  readonly id: string;
  readonly code: string;
  readonly name: string;
}

/**
 * Mudança de contrato do ticket — `dt-move-contract-dialog` (T-007-30).
 *
 * RN-305: só é possível **sem horas registradas** e apenas para outro contrato do **mesmo cliente**.
 * O seletor recebe somente contratos elegíveis; a recusa final é do servidor.
 *
 * INV-TKT-01: a chave do ticket **não muda**. Ela guarda o prefixo do contrato de origem para sempre,
 * e quem move sem saber disso conclui depois que a operação falhou — por isso o aviso é parte do
 * diálogo, e não uma mensagem posterior.
 */
@Component({
  selector: 'dt-move-contract-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    MessageModule,
    SelectModule,
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
      <form class="dt-move" [formGroup]="form" (ngSubmit)="confirm()">
        <p-message severity="warn" styleClass="w-full">
          <span i18n="@@ticket.move.keyNotice">
            A chave do ticket ({{ currentKey() }}) não muda: ela continua com o prefixo do contrato
            atual, mesmo depois da transferência.
          </span>
        </p-message>

        <div class="dt-move__field">
          <label for="move-contract" i18n="@@ticket.move.target">Contrato de destino *</label>
          <p-select
            inputId="move-contract"
            [options]="options()"
            optionLabel="name"
            optionValue="id"
            formControlName="targetContractId"
            i18n-placeholder="@@ticket.move.placeholder"
            placeholder="Selecione o contrato"
          />
          @if (missingTarget()) {
            <small class="dt-move__error" i18n="@@ticket.move.required">
              Escolha o contrato de destino.
            </small>
          }
          <small class="dt-move__hint" i18n="@@ticket.move.sameClient">
            Apenas contratos do mesmo cliente aparecem: mover entre clientes não é permitido.
          </small>
        </div>

        <div class="dt-move__checkbox">
          <p-checkbox inputId="move-confirm" formControlName="confirmed" [binary]="true" />
          <label for="move-confirm" i18n="@@ticket.move.confirm">
            Entendi que a chave permanece a mesma.
          </label>
        </div>
        @if (missingConfirmation()) {
          <small class="dt-move__error" i18n="@@ticket.move.confirmRequired">
            Confirme para continuar.
          </small>
        }

        <div class="dt-move__actions">
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
            i18n-label="@@ticket.move.submit"
            label="Mover ticket"
            [loading]="saving()"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-move {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
      font-size: var(--dt-text-sm);
    }

    .dt-move__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-move__checkbox {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
    }

    .dt-move__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-move__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-move__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class MoveContractDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly currentKey = input.required<string>();
  readonly options = input.required<MoveContractOption[]>();
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<{ targetContractId: string; confirmed: boolean }>();

  protected readonly title = $localize`:@@ticket.move.title:Mover para outro contrato`;

  protected readonly form = this.formBuilder.group({
    targetContractId: this.formBuilder.control(''),
    confirmed: this.formBuilder.control(false),
  });

  private readonly _submitted = signal(false);

  protected missingTarget(): boolean {
    return this._submitted() && this.form.controls.targetContractId.value === '';
  }

  protected missingConfirmation(): boolean {
    return this._submitted() && !this.form.controls.confirmed.value;
  }

  protected confirm(): void {
    this._submitted.set(true);
    if (this.missingTarget() || this.missingConfirmation()) {
      return;
    }
    this.confirmed.emit({
      targetContractId: this.form.controls.targetContractId.value,
      confirmed: true,
    });
  }
}
