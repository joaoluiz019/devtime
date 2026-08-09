import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { AuthStore } from '../auth/auth.store';
import { messageForCode } from '../error/error-messages';
import { TimerStore } from './timer.store';

/**
 * Início rápido do cronômetro — `dt-timer-quick-start` (§7 de `components.md`).
 *
 * Fica onde o trabalho é escolhido: no card e no detalhe do ticket. Obrigar a pessoa a abrir um
 * formulário para começar a contar seria atrito na operação mais frequente do produto — e a
 * categoria, que é o único campo que faltaria, tem padrão no contrato e é editável durante a
 * execução (RN-161).
 *
 * RN-166: com outro cronômetro rodando, a troca é oferecida e acontece de forma **atômica** no
 * servidor. Iniciar sem avisar encerraria silenciosamente o trabalho anterior; recusar obrigaria a
 * pessoa a ir até a barra, parar e voltar.
 */
@Component({
  selector: 'dt-timer-quick-start',
  imports: [ButtonModule, DialogModule, TooltipModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (canUse()) {
      <p-button
        [icon]="isCurrent() ? 'pi pi-stopwatch' : 'pi pi-play'"
        [label]="label()"
        severity="secondary"
        [text]="compact()"
        [outlined]="!compact()"
        [disabled]="store.busy() || isCurrent()"
        [pTooltip]="tooltip()"
        (onClick)="start()"
      />

      <p-dialog
        [visible]="switchVisible()"
        (visibleChange)="switchVisible.set($event)"
        [modal]="true"
        [style]="{ width: '30rem' }"
        [header]="switchTitle"
      >
        <div class="dt-quick-start__switch">
          <p i18n="@@timer.switch.text">
            Já existe um cronômetro em {{ currentTicketKey() }}. Ele será encerrado e o novo começa
            em seguida — as duas coisas acontecem juntas: se o encerramento falhar, nada muda.
          </p>
          <p class="dt-quick-start__hint" i18n="@@timer.switch.description">
            O tempo do cronômetro atual vira um registro de horas com a descrição que ele já tiver.
          </p>

          <div class="dt-quick-start__actions">
            <p-button
              type="button"
              i18n-label="@@action.cancel"
              label="Cancelar"
              severity="secondary"
              [text]="true"
              (onClick)="switchVisible.set(false)"
            />
            <p-button
              type="button"
              i18n-label="@@timer.switch.submit"
              label="Trocar de tarefa"
              [loading]="store.busy()"
              (onClick)="start(true)"
            />
          </div>
        </div>
      </p-dialog>
    }
  `,
  styles: `
    .dt-quick-start__switch {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-3);
      font-size: var(--dt-text-sm);
    }

    .dt-quick-start__switch p {
      margin: 0;
    }

    .dt-quick-start__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-quick-start__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--dt-space-2);
    }
  `,
})
export class TimerQuickStartComponent {
  private readonly authStore = inject(AuthStore);
  private readonly messages = inject(MessageService);

  protected readonly store = inject(TimerStore);

  readonly ticketId = input.required<string>();
  readonly ticketKey = input('');
  /** Em listas o botão é só o ícone; no detalhe do ticket ele tem rótulo. */
  readonly compact = input(false);

  protected readonly switchVisible = signal(false);

  protected readonly switchTitle = $localize`:@@timer.switch.title:Trocar de tarefa`;

  protected readonly canUse = computed(() => this.authStore.hasPermission('TIMER_USE'));

  protected readonly isCurrent = computed(
    () => this.store.isActive() && this.store.current()?.ticket.id === this.ticketId(),
  );

  protected readonly currentTicketKey = computed(() => this.store.current()?.ticket.key ?? '');

  protected readonly label = computed(() => {
    if (this.compact()) {
      return '';
    }
    return this.isCurrent()
      ? $localize`:@@timer.quickStart.running:Contando agora`
      : $localize`:@@timer.quickStart:Iniciar cronômetro`;
  });

  protected readonly tooltip = computed(() =>
    this.isCurrent()
      ? $localize`:@@timer.quickStart.running:Contando agora`
      : $localize`:@@timer.quickStart:Iniciar cronômetro`,
  );

  protected async start(stopCurrent = false): Promise<void> {
    if (this.store.isActive() && !stopCurrent) {
      this.switchVisible.set(true);
      return;
    }
    const started = await this.store.start({ ticketId: this.ticketId() }, stopCurrent);
    this.switchVisible.set(false);
    if (started) {
      return;
    }
    const problem = this.store.error();
    if (problem !== null) {
      this.messages.add({
        severity: 'error',
        summary: messageForCode(problem.code, problem.detail),
      });
      this.store.clearError();
    }
  }
}
