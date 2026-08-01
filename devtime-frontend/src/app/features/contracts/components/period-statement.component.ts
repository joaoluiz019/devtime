import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { StatementEntry, StatementEntryType } from '../data/period.model';

/**
 * Extrato do período: work logs e ajustes em ordem cronológica, com saldo acumulado (T-011-18).
 *
 * MV-02, o momento de verdade da persona: é aqui que o cliente confere de onde veio cada minuto.
 * Um número que não pode ser auditado não é defensável em uma cobrança.
 *
 * **Contrato de entrada.** A §21.2 da spec descreve a entrada como `periodId`. Recebo os lançamentos
 * já carregados: um componente declarado *Presentational* que recebesse um id precisaria buscar os
 * dados, o que exigiria injetar o store e o tornaria conectado. Quem carrega é `StatementStore`, na
 * página.
 */
@Component({
  selector: 'dt-period-statement',
  imports: [ButtonModule, DurationPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- BS-04 / A11Y-12: tabela com cabeçalhos associados por scope. -->
    <table class="dt-statement">
      <caption class="dt-visually-hidden" i18n="@@statement.caption">
        Extrato de lançamentos do período
      </caption>
      <thead>
        <tr>
          <th scope="col" i18n="@@statement.column.date">Data</th>
          <th scope="col" i18n="@@statement.column.type">Tipo</th>
          <th scope="col" i18n="@@statement.column.description">Descrição</th>
          <th scope="col" class="dt-statement__number" i18n="@@statement.column.minutes">
            Movimento
          </th>
          <th scope="col" class="dt-statement__number" i18n="@@statement.column.running">
            Saldo acumulado
          </th>
        </tr>
      </thead>
      <tbody>
        @for (entry of entries(); track $index) {
          <tr>
            <td>{{ entry.date }}</td>
            <td>{{ typeLabels()[entry.type] }}</td>
            <td class="dt-statement__description" [title]="entry.description">
              {{ entry.description }}
            </td>
            <td
              class="dt-statement__number dt-duration"
              [class.dt-severity-critical]="entry.minutes < 0"
            >
              {{ entry.minutes | duration: 'signed' }}
            </td>
            <td class="dt-statement__number dt-duration">
              {{ entry.runningBalanceMinutes | duration }}
            </td>
          </tr>
        } @empty {
          <!-- DS-08 / CA-06: todo estado vazio tem título, texto e — quando existe — ação. -->
          <tr>
            <td colspan="5" class="dt-statement__empty">
              <p class="dt-statement__empty-title" i18n="@@statement.empty.title">
                Nenhuma hora registrada
              </p>
              <p i18n="@@statement.empty.text">
                Inicie o cronômetro ou lance um registro manual para ver os lançamentos aqui.
              </p>
            </td>
          </tr>
        }
      </tbody>
    </table>

    @if (hasMore()) {
      <p-button
        severity="secondary"
        [outlined]="true"
        [label]="moreLabel()"
        icon="pi pi-angle-down"
        (onClick)="loadMore.emit()"
      />
    }
  `,
  styleUrl: './period-statement.component.scss',
})
export class PeriodStatementComponent {
  readonly entries = input.required<readonly StatementEntry[]>();
  readonly total = input.required<number>();
  readonly hasMore = input<boolean>(false);

  readonly loadMore = output<void>();

  /** SB-02: rótulo traduzido, nunca o valor do enum. */
  protected readonly typeLabels = computed<Record<StatementEntryType, string>>(() => ({
    CONTRACTED: $localize`:@@statement.type.contracted:Contratado`,
    CARRIED_IN: $localize`:@@statement.type.carriedIn:Transportado`,
    ADJUSTMENT: $localize`:@@statement.type.adjustment:Ajuste`,
    WORK_LOG: $localize`:@@statement.type.workLog:Registro de horas`,
  }));

  protected readonly moreLabel = computed(
    () =>
      $localize`:@@statement.loadMore:Ver mais (${this.entries().length}:shown: de ${this.total()}:total:)`,
  );
}
