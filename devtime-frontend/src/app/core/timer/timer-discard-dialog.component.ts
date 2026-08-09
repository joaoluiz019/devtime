import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { ElapsedTimePipe } from '../../shared/pipes/elapsed-time.pipe';

/**
 * Descarte do cronômetro — `dt-timer-discard-dialog` (RN-162, TB-04).
 *
 * O tempo que será perdido aparece **em número, grande**, porque esta é a única operação do produto
 * que destrói trabalho já registrado sem gerar nada em troca. "Tem certeza?" sem o valor faz a
 * pessoa confirmar por reflexo; "02:47:13 serão perdidos" faz parar.
 *
 * A confirmação é do usuário, mas não é só dele: o servidor exige `confirm=true` e registra o tempo
 * descartado na auditoria.
 */
@Component({
  selector: 'dt-timer-discard-dialog',
  imports: [ButtonModule, DialogModule, MessageModule, ElapsedTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visibleChange.emit($event)"
      [modal]="true"
      [style]="{ width: '30rem' }"
      [header]="title"
    >
      <div class="dt-timer-discard">
        <p-message severity="error" styleClass="w-full">
          <span i18n="@@timer.discard.warning">
            O tempo abaixo será perdido. Não há registro de horas nem como desfazer.
          </span>
        </p-message>

        <strong class="dt-timer-discard__elapsed">{{ elapsed() | elapsedTime }}</strong>

        <p class="dt-timer-discard__hint" i18n="@@timer.discard.alternative">
          Se o trabalho aconteceu, encerre em vez de descartar: o registro pode ser corrigido
          depois.
        </p>

        <div class="dt-timer-discard__actions">
          <p-button
            type="button"
            i18n-label="@@action.cancel"
            label="Cancelar"
            severity="secondary"
            [text]="true"
            (onClick)="visibleChange.emit(false)"
          />
          <p-button
            type="button"
            i18n-label="@@timer.discard.submit"
            label="Descartar o tempo"
            severity="danger"
            [loading]="busy()"
            (onClick)="confirmed.emit()"
          />
        </div>
      </div>
    </p-dialog>
  `,
  styles: `
    .dt-timer-discard {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--dt-space-3);
      text-align: center;
    }

    .dt-timer-discard__elapsed {
      font-family: var(--dt-font-mono);
      font-size: var(--dt-text-timer);
      line-height: var(--dt-text-timer-line);
    }

    .dt-timer-discard__hint {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-sm);
    }

    .dt-timer-discard__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
      width: 100%;
    }
  `,
})
export class TimerDiscardDialogComponent {
  readonly visible = input.required<boolean>();
  readonly elapsed = input.required<number>();
  readonly busy = input(false);

  readonly visibleChange = output<boolean>();
  readonly confirmed = output<void>();

  protected readonly title = $localize`:@@timer.discard.title:Descartar cronômetro`;
}
