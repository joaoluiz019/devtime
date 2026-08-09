import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MessageModule } from 'primeng/message';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { BalancePreview, WorkLogWarning } from '../data/work-log.model';

/**
 * Efeito do lançamento sobre o saldo — `dt-balance-preview` (T-008-30).
 *
 * Mostra o saldo **antes e depois** do registro que está sendo digitado. É o único momento em que
 * ainda dá para dividir a sessão, marcar como não faturável ou avisar o cliente: descobrir o estouro
 * depois de gravar transforma uma decisão em uma correção.
 *
 * Os avisos vêm do servidor (RN-231 em política `WARN`); a política `BLOCK` recusa antes, com erro.
 */
@Component({
  selector: 'dt-balance-preview',
  imports: [DurationPipe, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (preview(); as balance) {
      <section class="dt-balance-preview">
        <h3 class="dt-balance-preview__title" i18n="@@workLog.balance.title">
          Efeito no saldo do período
        </h3>

        <dl class="dt-balance-preview__grid">
          <dt i18n="@@workLog.balance.available">Disponível</dt>
          <dd>{{ balance.availableMinutes | duration }}</dd>
          <dt i18n="@@workLog.balance.before">Consumido antes</dt>
          <dd>{{ balance.consumedBeforeMinutes | duration }}</dd>
          <dt i18n="@@workLog.balance.after">Consumido depois</dt>
          <dd>{{ balance.consumedAfterMinutes | duration }}</dd>
          <dt i18n="@@workLog.balance.remaining">Saldo restante</dt>
          <dd [class.dt-balance-preview__negative]="negative()">
            {{ balance.remainingAfterMinutes | duration }}
          </dd>
        </dl>

        @if (negative()) {
          <!-- DS-05: o estouro é dito, não só pintado. -->
          <p class="dt-balance-preview__over" i18n="@@workLog.balance.over">
            Este registro ultrapassa o saldo contratado do período.
          </p>
        }
      </section>
    }

    @for (warning of warnings(); track warning.code) {
      <p-message severity="warn" [text]="warning.message" styleClass="w-full mt-2" />
    }
  `,
  styles: `
    .dt-balance-preview {
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    .dt-balance-preview__title {
      margin: 0 0 var(--dt-space-2);
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-balance-preview__grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: var(--dt-space-1) var(--dt-space-3);
      margin: 0;
      font-size: var(--dt-text-sm);
    }

    .dt-balance-preview__grid dt {
      color: var(--dt-text-secondary);
    }

    .dt-balance-preview__grid dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
    }

    .dt-balance-preview__negative,
    .dt-balance-preview__over {
      color: var(--dt-color-danger);
    }

    .dt-balance-preview__over {
      margin: var(--dt-space-2) 0 0;
      font-size: var(--dt-text-xs);
    }
  `,
})
export class BalancePreviewComponent {
  readonly preview = input.required<BalancePreview | undefined>();
  readonly warnings = input<readonly WorkLogWarning[]>([]);

  protected readonly negative = computed(() => (this.preview()?.remainingAfterMinutes ?? 0) < 0);
}
