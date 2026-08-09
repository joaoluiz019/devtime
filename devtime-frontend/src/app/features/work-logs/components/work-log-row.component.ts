import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { WorkLogSummary } from '../data/work-log.model';

/**
 * Linha de registro de horas — `dt-work-log-row` (T-008-33).
 *
 * RN-121: registro de período fechado não aceita edição nem exclusão. As ações **somem** e um selo
 * explica o motivo — um botão desabilitado sem explicação faz parecer defeito.
 *
 * OB-05: o rótulo de duração vem pronto do servidor (`durationLabel`), que já aplicou o
 * arredondamento configurado. Recalcular aqui abriria espaço para divergência com a fatura.
 */
@Component({
  selector: 'dt-work-log-row',
  imports: [RouterLink, ButtonModule, TagModule, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="dt-work-log-row">
      <div class="dt-work-log-row__when">
        <span class="dt-work-log-row__date">{{ entry().workDate }}</span>
        <span class="dt-work-log-row__time">{{ range() }}</span>
      </div>

      <div class="dt-work-log-row__what">
        <a class="dt-work-log-row__ticket" [routerLink]="['/tickets', entry().ticketId]">
          {{ entry().ticketKey }}
        </a>
        @if (entry().categoryName) {
          <span class="dt-work-log-row__category">{{ entry().categoryName }}</span>
        }
        @if (entry().source === 'TIMER') {
          <span class="dt-work-log-row__source" i18n="@@workLog.source.timer">cronômetro</span>
        }
      </div>

      <div class="dt-work-log-row__amount">
        <strong>{{ entry().durationLabel }}</strong>
        @if (!entry().billable) {
          <p-tag i18n-value="@@workLog.nonBillable" value="Não faturável" severity="secondary" />
        }
      </div>

      <div class="dt-work-log-row__actions">
        @if (locked()) {
          <!-- RN-121: o período foi fechado; o registro virou histórico. -->
          <p-tag
            i18n-value="@@workLog.locked"
            value="Período fechado"
            severity="warn"
            i18n-pTooltip="@@workLog.locked.hint"
            pTooltip="Registros de períodos fechados não podem ser alterados."
          />
        } @else {
          <p-button
            icon="pi pi-pencil"
            severity="secondary"
            [text]="true"
            i18n-ariaLabel="@@workLog.edit"
            ariaLabel="Editar registro"
            [routerLink]="['/work-logs', entry().id, 'edit']"
          />
          <p-button
            icon="pi pi-trash"
            severity="danger"
            [text]="true"
            i18n-ariaLabel="@@workLog.delete"
            ariaLabel="Excluir registro"
            (onClick)="removed.emit(entry().id)"
          />
        }
      </div>
    </article>
  `,
  styles: `
    .dt-work-log-row {
      display: grid;
      grid-template-columns: auto 1fr auto auto;
      gap: var(--dt-space-3);
      align-items: center;
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
    }

    @media (max-width: 640px) {
      .dt-work-log-row {
        grid-template-columns: 1fr auto;
      }
    }

    .dt-work-log-row__when {
      display: flex;
      flex-direction: column;
    }

    .dt-work-log-row__date {
      font-size: var(--dt-text-sm);
      font-variant-numeric: tabular-nums;
    }

    .dt-work-log-row__time,
    .dt-work-log-row__category,
    .dt-work-log-row__source {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-work-log-row__what {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .dt-work-log-row__ticket {
      color: var(--dt-color-primary);
      font-family: var(--dt-font-mono, monospace);
      font-size: var(--dt-text-xs);
    }

    .dt-work-log-row__amount {
      display: flex;
      align-items: center;
      gap: var(--dt-space-2);
      font-variant-numeric: tabular-nums;
    }

    .dt-work-log-row__actions {
      display: flex;
      gap: var(--dt-space-1);
    }
  `,
})
export class WorkLogRowComponent {
  readonly entry = input.required<WorkLogSummary>();

  readonly removed = output<string>();

  protected readonly locked = computed(() => this.entry().lockedAt !== undefined);

  protected readonly range = computed(() => {
    const entry = this.entry();
    return `${formatTime(entry.startedAt)} — ${formatTime(entry.endedAt)}`;
  });
}

function formatTime(instant: string): string {
  const date = new Date(instant);
  return `${`${date.getHours()}`.padStart(2, '0')}:${`${date.getMinutes()}`.padStart(2, '0')}`;
}
