import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { WorkLogConflict } from '../data/work-log.model';

/**
 * Sobreposição de sessões — `dt-overlap-warning` (T-008-30).
 *
 * RN-102: a mesma pessoa não pode ter duas sessões no mesmo intervalo. O aviso **aponta o registro
 * conflitante com link**: sem ele, a pessoa sabe que existe um conflito mas não onde, e a única saída
 * é procurar na lista de horas por tentativa.
 */
@Component({
  selector: 'dt-overlap-warning',
  imports: [RouterLink, MessageModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (conflicts().length > 0) {
      <p-message severity="error" styleClass="w-full">
        <div class="dt-overlap">
          <span i18n="@@workLog.overlap.title">
            Este intervalo se sobrepõe a registros já existentes:
          </span>
          <ul class="dt-overlap__list">
            @for (conflict of conflicts(); track conflict.id) {
              <li>
                <a [routerLink]="['/work-logs', conflict.id, 'edit']">{{ conflict.ticketKey }}</a>
                <span>{{ conflict.workDate }}</span>
                <span>{{ time(conflict.startedAt) }} — {{ time(conflict.endedAt) }}</span>
              </li>
            }
          </ul>
        </div>
      </p-message>
    }
  `,
  styles: `
    .dt-overlap {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-overlap__list {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin: 0;
      padding-left: var(--dt-space-4);
      font-size: var(--dt-text-xs);
    }

    .dt-overlap__list li {
      display: flex;
      gap: var(--dt-space-2);
    }
  `,
})
export class OverlapWarningComponent {
  readonly conflicts = input.required<readonly WorkLogConflict[]>();

  /** Só o horário: a data já aparece ao lado, e repeti-la ocuparia a linha sem informar. */
  protected time(instant: string): string {
    const date = new Date(instant);
    return `${`${date.getHours()}`.padStart(2, '0')}:${`${date.getMinutes()}`.padStart(2, '0')}`;
  }
}
