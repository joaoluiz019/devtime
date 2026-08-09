import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ElapsedTimePipe } from '../../shared/pipes/elapsed-time.pipe';
import { messageForCode } from '../error/error-messages';
import { TimerDiscardDialogComponent } from './timer-discard-dialog.component';
import { LONG_RUNNING_SECONDS } from './timer.model';
import { TimerStore } from './timer.store';
import { TimerStopDialogComponent } from './timer-stop-dialog.component';

/**
 * Barra global do cronômetro — `dt-timer-bar` (§6.2 de `layouts.md`, §6.3 de `components.md`).
 *
 * §21.2 de `specs/009-timer/spec.md` a chama de `dt-timer-widget`; `layouts.md` e `components.md` a
 * chamam de `dt-timer-bar`. Vale o nome dos dois documentos de interface, que é também o que a
 * seção de layout desenha.
 *
 * Fica **acima** da barra superior e ocupa toda a largura: quando há cronômetro rodando, essa é a
 * informação mais importante da tela (ID-01). Sem cronômetro, não é renderizada — reservar 48px
 * para algo ausente deslocaria todo o layout.
 *
 * TB-06: o tempo decorrido vai para o título da aba. Quem trabalha com o navegador minimizado
 * precisa saber que o cronômetro está correndo sem voltar à aplicação — é assim que se evita o
 * cronômetro esquecido de sexta-feira.
 */
@Component({
  selector: 'dt-timer-bar',
  imports: [
    RouterLink,
    ButtonModule,
    TooltipModule,
    ElapsedTimePipe,
    TimerDiscardDialogComponent,
    TimerStopDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.current(); as timer) {
      @if (visible()) {
        <div
          class="dt-timer-bar"
          [class.dt-timer-bar--paused]="timer.status === 'PAUSED'"
          role="status"
          [attr.aria-label]="ariaLabel()"
        >
          <i
            class="pi pi-stopwatch dt-timer-bar__icon"
            [class.dt-timer-bar__icon--running]="timer.status === 'RUNNING'"
            aria-hidden="true"
          ></i>

          <a class="dt-timer-bar__ticket" [routerLink]="['/tickets', timer.ticket.id]">
            <strong>{{ timer.ticket.key }}</strong>
            <span class="dt-timer-bar__title">{{ timer.ticket.title }}</span>
          </a>

          <span class="dt-timer-bar__elapsed">{{ store.elapsed() | elapsedTime }}</span>

          @if (longRunning()) {
            <!-- RN-163: o aviso é inline, ao lado do número, não um toast que some. -->
            <span class="dt-timer-bar__warning" i18n="@@timer.longRunning">
              Rodando há mais de 8 horas
            </span>
          }

          <span class="dt-timer-bar__actions">
            @if (timer.status === 'RUNNING') {
              <p-button
                icon="pi pi-pause"
                severity="secondary"
                [text]="true"
                [disabled]="store.busy()"
                i18n-ariaLabel="@@timer.pause"
                ariaLabel="Pausar"
                i18n-pTooltip="@@timer.pause"
                pTooltip="Pausar"
                (onClick)="pause()"
              />
            } @else {
              <p-button
                icon="pi pi-play"
                severity="secondary"
                [text]="true"
                [disabled]="store.busy()"
                i18n-ariaLabel="@@timer.resume"
                ariaLabel="Retomar"
                i18n-pTooltip="@@timer.resume"
                pTooltip="Retomar"
                (onClick)="resume()"
              />
            }

            <p-button
              icon="pi pi-stop-circle"
              severity="secondary"
              [text]="true"
              [disabled]="store.busy()"
              i18n-ariaLabel="@@timer.stop"
              ariaLabel="Encerrar"
              i18n-pTooltip="@@timer.stop"
              pTooltip="Encerrar"
              (onClick)="openStop()"
            />

            <p-button
              icon="pi pi-trash"
              severity="secondary"
              [text]="true"
              [disabled]="store.busy()"
              i18n-ariaLabel="@@timer.discard"
              ariaLabel="Descartar"
              i18n-pTooltip="@@timer.discard"
              pTooltip="Descartar"
              (onClick)="discardVisible.set(true)"
            />
          </span>
        </div>
      }

      <dt-timer-stop-dialog
        [visible]="stopVisible()"
        (visibleChange)="onStopVisibleChange($event)"
        [elapsed]="store.elapsed()"
        [ticketLabel]="timer.ticket.key"
        [busy]="store.busy()"
        [errorMessage]="errorMessage()"
        (confirmed)="stop($event)"
      />

      <dt-timer-discard-dialog
        [visible]="discardVisible()"
        (visibleChange)="discardVisible.set($event)"
        [elapsed]="store.elapsed()"
        [busy]="store.busy()"
        (confirmed)="discard()"
      />
    }
  `,
  styleUrl: './timer-bar.component.scss',
})
export class TimerBarComponent {
  private readonly messages = inject(MessageService);

  /** Título definido pela rota, preservado para voltar a ele quando o cronômetro encerra. */
  private plainTitle = typeof document === 'undefined' ? '' : document.title;

  protected readonly store = inject(TimerStore);

  protected readonly stopVisible = signal(false);
  protected readonly discardVisible = signal(false);

  /** `ABANDONED`, `COMPLETED` e `DISCARDED` não são cronômetros em curso: a barra some. */
  protected readonly visible = computed(() => this.store.isActive());

  protected readonly longRunning = computed(() => this.store.elapsed() >= LONG_RUNNING_SECONDS);

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  protected readonly ariaLabel = computed(() => {
    const timer = this.store.current();
    if (timer === null) {
      return '';
    }
    return timer.status === 'PAUSED'
      ? $localize`:@@timer.aria.paused:Cronômetro pausado em ${timer.ticket.key}:ticket:`
      : $localize`:@@timer.aria.running:Cronômetro em execução em ${timer.ticket.key}:ticket:`;
  });

  constructor() {
    // TB-06: o título da aba acompanha o cronômetro. O texto original é restaurado ao encerrar —
    // deixar "01:12:40" no título de uma aba sem cronômetro seria pior do que nunca tê-lo mostrado.
    effect(() => {
      if (typeof document === 'undefined') {
        return;
      }
      const timer = this.store.current();
      if (timer === null || !this.store.isActive()) {
        // Sem cronômetro, o título é o que a rota definiu — e é ele que guardamos para restaurar.
        this.plainTitle = document.title;
        return;
      }
      const elapsed = new ElapsedTimePipe().transform(this.store.elapsed());
      document.title = `${elapsed} · ${this.plainTitle}`;
    });
  }

  protected async pause(): Promise<void> {
    await this.store.pause();
    this.reportFailure();
  }

  protected async resume(): Promise<void> {
    await this.store.resume();
    this.reportFailure();
  }

  protected openStop(): void {
    this.store.clearError();
    this.stopVisible.set(true);
  }

  protected onStopVisibleChange(visible: boolean): void {
    this.stopVisible.set(visible);
    if (!visible) {
      this.store.clearError();
    }
  }

  /**
   * Encerra e informa o que foi registrado.
   *
   * O diálogo só fecha em caso de sucesso: em falha, RN-160 manda manter o cronômetro **e** a tela
   * de correção onde estão.
   */
  protected async stop(description: string): Promise<void> {
    const result = await this.store.stop(description);
    if (result === null) {
      return;
    }
    this.stopVisible.set(false);
    this.messages.add({
      severity: 'success',
      summary: $localize`:@@timer.stopped:Horas registradas`,
      detail:
        result.workLog.durationLabel ??
        $localize`:@@timer.stopped.minutes:${result.workLog.netMinutes}:minutes: minutos`,
    });
    for (const warning of result.warnings) {
      // RN-232: o aviso de excedente é do registro de horas e precisa chegar a quem encerrou.
      this.messages.add({ severity: 'warn', summary: warning.message });
    }
  }

  protected async discard(): Promise<void> {
    if (await this.store.discard()) {
      this.discardVisible.set(false);
      return;
    }
    this.reportFailure();
  }

  private reportFailure(): void {
    const message = this.errorMessage();
    if (message !== null) {
      this.messages.add({ severity: 'error', summary: message });
      this.store.clearError();
    }
  }
}
