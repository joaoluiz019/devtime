import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { DurationInputComponent } from '../../../shared/components/duration-input/duration-input.component';
import { PeriodBalance } from '../../../shared/models/balance.model';
import { ConsumptionRatePipe } from '../../../shared/pipes/consumption-rate.pipe';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { AdjustmentReason, AdjustmentRequest } from '../data/period.model';

/** Opção do seletor de motivo. */
interface ReasonOption {
  readonly value: AdjustmentReason;
  readonly label: string;
}

/** RN-215: a justificativa tem no mínimo 10 caracteres. */
const MIN_JUSTIFICATION = 10;

/**
 * Diálogo de ajuste manual do saldo, com **prévia do saldo resultante** (T-011-17).
 *
 * **Por que a prévia é obrigatória:** o ajuste é imutável (RN-236). Um erro só se corrige por
 * estorno, que fica registrado para sempre no extrato que o cliente vê. A prévia é a única defesa
 * contra um ajuste digitado errado (§21.2 de `specs/011-bank-hours/spec.md`, risco R-08).
 *
 * **A prévia é exibição, não cálculo canônico (CE-F-05).** Ela soma os minutos ao disponível apenas
 * para mostrar a consequência da mudança. O valor que fica na tela depois de aplicar vem da API — o
 * servidor é sempre a fonte do saldo (FR-045, RP-03).
 */
@Component({
  selector: 'dt-adjustment-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    SelectModule,
    TextareaModule,
    DurationInputComponent,
    DurationPipe,
    ConsumptionRatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './adjustment-dialog.component.html',
  styleUrl: './adjustment-dialog.component.scss',
})
export class AdjustmentDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly balance = input.required<PeriodBalance>();
  /** BT-04: durante o envio o rótulo é mantido e o botão fica desabilitado. */
  readonly submitting = input<boolean>(false);

  readonly apply = output<AdjustmentRequest>();
  readonly cancelled = output<void>();

  /** FR-100/FR-101: Reactive Forms tipado, com `NonNullableFormBuilder` como padrão. */
  protected readonly form = this.formBuilder.group({
    minutes: this.formBuilder.control<number | null>(null, Validators.required),
    reason: this.formBuilder.control<AdjustmentReason>('COURTESY', Validators.required),
    justification: this.formBuilder.control('', [
      Validators.required,
      Validators.minLength(MIN_JUSTIFICATION),
      Validators.maxLength(1000),
    ]),
  });

  /** `p-select` exige um array mutável em `options`; o conteúdo nunca é alterado. */
  protected readonly reasonOptions: ReasonOption[] = [
    { value: 'COURTESY', label: $localize`:@@adjustment.reason.courtesy:Cortesia` },
    { value: 'CORRECTION', label: $localize`:@@adjustment.reason.correction:Correção` },
    {
      value: 'NEGOTIATED_EXTRA',
      label: $localize`:@@adjustment.reason.negotiatedExtra:Extra negociado`,
    },
    { value: 'PENALTY', label: $localize`:@@adjustment.reason.penalty:Penalidade` },
    { value: 'MIGRATION', label: $localize`:@@adjustment.reason.migration:Migração` },
    { value: 'OTHER', label: $localize`:@@adjustment.reason.other:Outro` },
  ];

  protected readonly title = $localize`:@@adjustment.dialog.title:Ajustar saldo do período`;

  /**
   * Minutos informados, como Signal.
   *
   * `valueChanges` não emite o valor inicial; o `initialValue` cobre a primeira renderização. Sem
   * isso a prévia só apareceria depois da primeira tecla — e a prévia precisa existir desde a
   * abertura do diálogo.
   */
  private readonly minutes = toSignal(this.form.controls.minutes.valueChanges, {
    initialValue: null,
  });

  /** FR-042: todo dado derivado é `computed`, nunca recalculado no template (FR-030). */
  protected readonly delta = computed(() => this.minutes() ?? 0);

  protected readonly previewAvailable = computed(
    () => this.balance().availableMinutes + this.delta(),
  );

  protected readonly previewRemaining = computed(
    () => this.balance().remainingMinutes + this.delta(),
  );

  /**
   * Taxa de consumo resultante, exibida só como referência.
   *
   * Com disponível zero ou negativo a taxa não é definida — `BalanceCalculator` trata esse ramo no
   * servidor, e reproduzi-lo aqui duplicaria a fórmula. Devolve `null`, e o template exibe "—"
   * (CE-D-05).
   */
  protected readonly previewRate = computed(() => {
    const available = this.previewAvailable();
    if (available <= 0) {
      return null;
    }
    return (this.balance().consumedMinutes / available) * 100;
  });

  /** RN-237: um ajuste que deixaria o disponível negativo é recusado com `DEVTIME-2237`. */
  protected readonly previewInvalid = computed(() => this.previewAvailable() < 0);

  protected readonly justificationControl = this.form.controls.justification;
  protected readonly minutesControl = this.form.controls.minutes;

  protected onCancel(): void {
    this.form.reset();
    this.cancelled.emit();
  }

  /**
   * FR-104: o botão nunca é desabilitado por formulário inválido — desabilitar esconde **o que** está
   * errado. A tentativa é permitida, os erros aparecem e o foco vai para o primeiro campo inválido
   * (FR-105).
   */
  protected onSubmit(): void {
    if (this.form.invalid || this.delta() === 0) {
      this.form.markAllAsTouched();
      focusFirstInvalid();
      return;
    }
    const value = this.form.getRawValue();
    this.apply.emit({
      minutes: value.minutes ?? 0,
      reason: value.reason,
      justification: value.justification,
    });
    this.form.reset();
  }
}

/** FR-105: o foco vai para o primeiro campo inválido, que é onde o usuário precisa agir. */
function focusFirstInvalid(): void {
  const invalid = document.querySelector<HTMLElement>(
    '.dt-adjustment-dialog [aria-invalid="true"]',
  );
  invalid?.focus();
}
