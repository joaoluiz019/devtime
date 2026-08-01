import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { TextareaModule } from 'primeng/textarea';
import { PeriodBalance } from '../../../shared/models/balance.model';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { ClosePeriodRequest } from '../data/period.model';

/**
 * Diálogo de pré-fechamento do período (§10 de `pages.md`).
 *
 * O fechamento é o momento de faturamento e é **irreversível na prática**: congela o relatório,
 * trava os registros e gera o snapshot. Confirmar sem ver os números é o erro mais caro possível
 * nesta tela.
 *
 * **Entrega parcial, declarada.** A §10 de `pages.md` lista seis itens obrigatórios no diálogo.
 * Dois deles — "será transportado" (`carriedOutPreview`) e "registros a travar" — vêm de
 * `ClosePreviewResponse`, previsto em T-011-29 e **não publicado** pelo backend: não existe rota de
 * prévia de fechamento no OpenAPI. Os dois campos aparecem como "—" com aviso, e não como um número
 * calculado no cliente: reproduzir a fórmula de carry-over aqui é exatamente o que RP-03 aponta como
 * origem mais provável de divergência de saldo, e CE-D-05 proíbe exibir um valor possivelmente
 * errado. A lacuna está registrada no relatório da sprint.
 */
@Component({
  selector: 'dt-close-period-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    CheckboxModule,
    DialogModule,
    TextareaModule,
    DurationPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './close-period-dialog.component.html',
  styleUrl: './close-period-dialog.component.scss',
})
export class ClosePeriodDialogComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly visible = input.required<boolean>();
  readonly balance = input.required<PeriodBalance>();
  /** RN-239: antes do `endDate`, o fechamento exige confirmação explícita. */
  readonly early = input<boolean>(false);
  readonly submitting = input<boolean>(false);

  /**
   * A spec nomeia a saída `confirm` (§21.2). Aqui ela é `confirmed`: `confirm` é um evento nativo do
   * DOM, e `@angular-eslint/no-output-native` recusa o nome — a colisão faria o binding do pai
   * disparar também para o evento nativo homônimo.
   */
  readonly confirmed = output<ClosePeriodRequest>();
  readonly cancelled = output<void>();

  protected readonly form = this.formBuilder.group({
    confirmed: this.formBuilder.control(false),
    earlyClosingReason: this.formBuilder.control('', Validators.maxLength(1000)),
  });

  protected readonly confirmedControl = this.form.controls.confirmed;

  protected readonly title = $localize`:@@close.dialog.title:Fechar período`;

  protected readonly isOverage = computed(() => this.balance().remainingMinutes < 0);

  protected readonly balanceDisplay = computed(() =>
    this.isOverage() ? this.balance().overageMinutes : this.balance().remainingMinutes,
  );

  protected readonly balanceLabel = computed(() =>
    this.isOverage()
      ? $localize`:@@balance.overage:Excedente`
      : $localize`:@@balance.remaining:Restante`,
  );

  /** A confirmação só é exigida no fechamento antecipado; nos demais casos o botão segue livre. */
  protected readonly missingConfirmation = computed(
    () => this.early() && !this.confirmedControl.value,
  );

  protected onCancel(): void {
    this.form.reset();
    this.cancelled.emit();
  }

  /** BT-05: ação destrutiva sempre passa por confirmação — aqui, a do fechamento antecipado. */
  protected onConfirm(): void {
    if (this.missingConfirmation()) {
      this.confirmedControl.markAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.confirmed.emit({
      confirmed: value.confirmed,
      earlyClosingReason: value.earlyClosingReason === '' ? null : value.earlyClosingReason,
    });
    this.form.reset();
  }
}
