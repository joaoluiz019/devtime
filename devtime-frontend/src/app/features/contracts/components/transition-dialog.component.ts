import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';
import { ContractTransitionRequest } from '../data/contract.model';

/** Transições que exigem confirmação com contexto. */
export type TransitionKind = 'SUSPEND' | 'END' | 'CANCEL';

/** Mínimo do backend para justificativa de transição (`DEVTIME-2215`). */
const REASON_MIN_LENGTH = 10;

/**
 * Confirmação de suspensão, encerramento e cancelamento — `dt-transition-dialog` (T-004-19).
 *
 * Cada transição muda o que acontece com as horas, e o diálogo diz qual é a consequência **antes**
 * do clique:
 * — suspender mantém o período aberto e para de gerar os seguintes;
 * — encerrar trunca o período corrente na data escolhida (RN-214);
 * — cancelar é terminal e trunca hoje.
 *
 * Suspender e cancelar exigem justificativa de ao menos 10 caracteres (`DEVTIME-2215`), verificada
 * aqui para que a recusa não venha do servidor depois de a pessoa fechar o diálogo.
 */
@Component({
  selector: 'dt-transition-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DatePickerModule,
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
      [style]="{ width: '34rem' }"
      [header]="title()"
    >
      <form class="dt-transition" [formGroup]="form" (ngSubmit)="confirm()">
        <p-message severity="warn" styleClass="w-full">
          <span>{{ consequence() }}</span>
        </p-message>

        @if (kind() === 'END') {
          <div class="dt-transition__field">
            <label for="transition-end-date" i18n="@@contract.transition.endDate">
              Data de encerramento
            </label>
            <p-datepicker
              inputId="transition-end-date"
              formControlName="endDate"
              [dateFormat]="'dd/mm/yy'"
              [showIcon]="true"
            />
            <small class="dt-transition__hint" i18n="@@contract.transition.endDate.hint">
              O período corrente é truncado nesta data e nenhum posterior é gerado.
            </small>
          </div>
        }

        @if (requiresReason()) {
          <div class="dt-transition__field">
            <label for="transition-reason" i18n="@@contract.transition.reason"
              >Justificativa *</label
            >
            <textarea
              id="transition-reason"
              pTextarea
              rows="3"
              formControlName="reason"
              maxlength="1000"
              aria-required="true"
              [attr.aria-invalid]="reasonInvalid()"
            ></textarea>
            @if (reasonInvalid()) {
              <small class="dt-transition__error" i18n="@@contract.transition.reason.invalid">
                A justificativa precisa ter ao menos 10 caracteres.
              </small>
            }
          </div>
        }

        <div class="dt-transition__actions">
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
            [label]="confirmLabel()"
            [severity]="kind() === 'SUSPEND' ? 'warn' : 'danger'"
            [loading]="saving()"
          />
        </div>
      </form>
    </p-dialog>
  `,
  styles: `
    .dt-transition {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
    }

    .dt-transition__field {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-transition__field textarea {
      width: 100%;
    }

    .dt-transition__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-transition__error {
      color: var(--dt-color-danger);
      font-size: var(--dt-text-xs);
    }

    .dt-transition__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class TransitionDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly kind = input.required<TransitionKind>();
  readonly saving = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<ContractTransitionRequest>();

  protected readonly form = this.formBuilder.group({
    reason: this.formBuilder.control(''),
    endDate: this.formBuilder.control<Date | null>(null),
  });

  private readonly _submitted = signal(false);

  /** RN-214 permite encerrar sem data (o backend usa hoje); suspender e cancelar exigem motivo. */
  protected readonly requiresReason = computed(() => this.kind() !== 'END');

  protected readonly title = computed(() => {
    switch (this.kind()) {
      case 'SUSPEND':
        return $localize`:@@contract.suspend.title:Suspender contrato`;
      case 'END':
        return $localize`:@@contract.end.title:Encerrar contrato`;
      default:
        return $localize`:@@contract.cancel.title:Cancelar contrato`;
    }
  });

  /**
   * O rótulo de confirmação repete o objeto da ação.
   *
   * O botão do cabeçalho já se chama "Suspender"; um segundo botão com o mesmo nome dentro do
   * diálogo é indistinguível para quem navega por leitor de tela, que ouve os dois fora de contexto.
   */
  protected readonly confirmLabel = computed(() => {
    switch (this.kind()) {
      case 'SUSPEND':
        return $localize`:@@contract.suspend.confirm:Suspender contrato`;
      case 'END':
        return $localize`:@@contract.end.confirm:Encerrar contrato`;
      default:
        return $localize`:@@contract.cancel.action:Cancelar contrato`;
    }
  });

  protected readonly consequence = computed(() => {
    switch (this.kind()) {
      case 'SUSPEND':
        return $localize`:@@contract.suspend.consequence:O período aberto continua aberto e as horas já lançadas permanecem. Nenhum período novo é gerado enquanto o contrato estiver suspenso.`;
      case 'END':
        return $localize`:@@contract.end.consequence:O período corrente é truncado na data de encerramento e nenhum período posterior é gerado. As horas registradas são preservadas.`;
      default:
        return $localize`:@@contract.cancel.consequence:O cancelamento é definitivo e não pode ser desfeito. O período corrente é truncado hoje; os registros de horas são preservados para histórico.`;
    }
  });

  protected reasonInvalid(): boolean {
    if (!this.requiresReason()) {
      return false;
    }
    return this._submitted() && this.form.controls.reason.value.trim().length < REASON_MIN_LENGTH;
  }

  protected confirm(): void {
    this._submitted.set(true);
    if (this.reasonInvalid()) {
      document.getElementById('transition-reason')?.focus();
      return;
    }

    const value = this.form.getRawValue();
    this.confirmed.emit({
      reason: value.reason.trim() === '' ? undefined : value.reason.trim(),
      // O backend espera `LocalDate`: só a parte da data, sem fuso, para não deslocar o dia.
      endDate: value.endDate === null ? undefined : toIsoDate(value.endDate),
    });
  }
}

function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
