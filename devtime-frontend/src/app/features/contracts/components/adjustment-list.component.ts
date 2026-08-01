import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { Adjustment, AdjustmentReason } from '../data/period.model';

/**
 * Ajustes aplicados ao período, com ação de estorno (T-011-17).
 *
 * **Não existe editar nem excluir.** RN-236 / INV-ADJ-01: o ajuste é imutável, e a correção é um
 * novo ajuste de sinal contrário (FA-05). Oferecer um botão "editar" que o servidor recusaria com
 * `DEVTIME-2236` seria pior que não oferecer nada.
 *
 * **Autor não é exibido.** `AdjustmentResponse.appliedBy` traz apenas o UUID, e FR-129/CA-09 proíbem
 * exibir identificador técnico na interface. Enquanto a API não devolver o nome, a coluna não
 * existe — um UUID na tela é ruído para o usuário e vazamento de detalhe interno. Lacuna registrada.
 */
@Component({
  selector: 'dt-adjustment-list',
  imports: [ButtonModule, DatePipe, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table class="dt-adjustments">
      <caption class="dt-visually-hidden" i18n="@@adjustments.caption">
        Ajustes aplicados ao período
      </caption>
      <thead>
        <tr>
          <th scope="col" i18n="@@adjustments.column.appliedAt">Aplicado em</th>
          <th scope="col" i18n="@@adjustments.column.reason">Motivo</th>
          <th scope="col" i18n="@@adjustments.column.justification">Justificativa</th>
          <th scope="col" class="dt-adjustments__number" i18n="@@adjustments.column.minutes">
            Horas
          </th>
          @if (canAdjust()) {
            <th scope="col">
              <span class="dt-visually-hidden" i18n="@@adjustments.column.actions">Ações</span>
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @for (adjustment of adjustments(); track adjustment.id) {
          <tr>
            <td>{{ adjustment.appliedAt | date: 'dd/MM/yyyy HH:mm' }}</td>
            <td>{{ reasonLabels()[adjustment.reason] }}</td>
            <td class="dt-adjustments__justification">{{ adjustment.justification }}</td>
            <td
              class="dt-adjustments__number dt-duration"
              [class.dt-severity-critical]="adjustment.minutes < 0"
            >
              {{ adjustment.minutes | duration: 'signed' }}
            </td>
            @if (canAdjust()) {
              <td>
                <p-button
                  severity="secondary"
                  [text]="true"
                  icon="pi pi-replay"
                  [ariaLabel]="reverseLabel()"
                  (onClick)="reverse.emit(adjustment)"
                />
              </td>
            }
          </tr>
        } @empty {
          <tr>
            <td [attr.colspan]="columnCount()" class="dt-adjustments__empty">
              <p class="dt-adjustments__empty-title" i18n="@@adjustments.empty.title">
                Nenhum ajuste neste período
              </p>
              <p i18n="@@adjustments.empty.text">
                Ajustes creditam ou debitam horas fora do registro normal e ficam registrados para
                sempre no extrato.
              </p>
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styleUrl: './adjustment-list.component.scss',
})
export class AdjustmentListComponent {
  readonly adjustments = input.required<readonly Adjustment[]>();
  /** IMP-06: ergonomia. A autorização real de `PERIOD_ADJUST` é sempre do backend. */
  readonly canAdjust = input<boolean>(false);

  readonly reverse = output<Adjustment>();

  protected readonly columnCount = computed(() => (this.canAdjust() ? 5 : 4));

  protected readonly reverseLabel = computed(
    () => $localize`:@@adjustments.reverse:Estornar este ajuste`,
  );

  /** SB-02: rótulos traduzidos, nunca o valor do enum `AdjustmentReason`. */
  protected readonly reasonLabels = computed<Record<AdjustmentReason, string>>(() => ({
    COURTESY: $localize`:@@adjustment.reason.courtesy:Cortesia`,
    CORRECTION: $localize`:@@adjustment.reason.correction:Correção`,
    NEGOTIATED_EXTRA: $localize`:@@adjustment.reason.negotiatedExtra:Extra negociado`,
    PENALTY: $localize`:@@adjustment.reason.penalty:Penalidade`,
    MIGRATION: $localize`:@@adjustment.reason.migration:Migração`,
    OTHER: $localize`:@@adjustment.reason.other:Outro`,
  }));
}
