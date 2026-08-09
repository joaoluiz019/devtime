import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TextareaModule } from 'primeng/textarea';
import { firstValueFrom } from 'rxjs';
import { messageForCode } from '../../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../../core/error/problem-detail.model';
import { TimerApi } from '../../../core/timer/timer.api';
import { AbandonedTimer } from '../../../core/timer/timer.model';
import { TimerStore } from '../../../core/timer/timer.store';
import { ElapsedTimePipe } from '../../../shared/pipes/elapsed-time.pipe';

/**
 * Cronômetros abandonados — RN-164 e RN-165.
 *
 * O sistema marca como abandonado em vez de encerrar sozinho, e a diferença é o ponto desta tela:
 * encerrar com um horário arbitrário registraria horas que ninguém trabalhou. **Quem informa o
 * término é a pessoa**, e por isso o campo de data e hora é obrigatório aqui.
 *
 * O prazo de sete dias fica visível em cada linha: depois dele o cronômetro é descartado de vez, e
 * saber disso é o que faz alguém tratar a fila hoje em vez de na semana que vem.
 */
@Component({
  selector: 'dt-abandoned-timers-page',
  imports: [
    DatePipe,
    FormsModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SkeletonModule,
    TextareaModule,
    ElapsedTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="dt-abandoned__header">
      <h1 class="dt-abandoned__title" i18n="@@timers.abandoned.title">Cronômetros abandonados</h1>
      <p class="dt-abandoned__subtitle" i18n="@@timers.abandoned.subtitle">
        Ficaram rodando sem atividade. Informe quando o trabalho terminou para gerar o registro de
        horas — o sistema não inventa esse horário.
      </p>
    </header>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (loading()) {
      <p-skeleton height="8rem" />
    } @else if (timers().length === 0) {
      <p class="dt-abandoned__empty" i18n="@@timers.abandoned.empty">
        Nenhum cronômetro abandonado.
      </p>
    } @else {
      <ul class="dt-abandoned__list" role="list">
        @for (timer of timers(); track timer.id) {
          <li class="dt-abandoned__item">
            <div class="dt-abandoned__identity">
              <strong>{{ timer.ticket.key }}</strong>
              <span>{{ timer.ticket.title }}</span>
              <small class="dt-abandoned__meta">
                <span i18n="@@timers.abandoned.startedAt">
                  Iniciado em {{ timer.startedAt | date: 'short' }}
                </span>
                ·
                <span i18n="@@timers.abandoned.gross">
                  {{ timer.grossElapsedSeconds | elapsedTime }} decorridos
                </span>
                ·
                <span i18n="@@timers.abandoned.deadline">
                  recuperável até {{ timer.recoverableUntil }}
                </span>
              </small>
            </div>

            <div class="dt-abandoned__form">
              <label [for]="'ended-' + timer.id" i18n="@@timers.abandoned.endedAt">
                Terminou em
              </label>
              <input
                [id]="'ended-' + timer.id"
                type="datetime-local"
                class="dt-abandoned__input"
                [ngModel]="endedAt()[timer.id] ?? ''"
                (change)="setEndedAt(timer.id, $any($event.target).value)"
              />

              <label [for]="'description-' + timer.id" i18n="@@timers.abandoned.description">
                O que foi feito
              </label>
              <textarea
                [id]="'description-' + timer.id"
                pTextarea
                rows="2"
                maxlength="2000"
                [ngModel]="description()[timer.id] ?? ''"
                (change)="setDescription(timer.id, $any($event.target).value)"
              ></textarea>

              <p-button
                i18n-label="@@timers.abandoned.recover"
                label="Registrar horas"
                icon="pi pi-check"
                [loading]="saving() === timer.id"
                (onClick)="recover(timer)"
              />
            </div>
          </li>
        }
      </ul>
    }
  `,
  styleUrl: './abandoned-timers.page.scss',
})
export class AbandonedTimersPage {
  private readonly api = inject(TimerApi);
  private readonly timerStore = inject(TimerStore);
  private readonly messages = inject(MessageService);

  private readonly _timers = signal<readonly AbandonedTimer[]>([]);
  private readonly _loading = signal(true);
  private readonly _saving = signal<string | null>(null);
  private readonly _error = signal<ProblemDetail | null>(null);
  private readonly _endedAt = signal<Record<string, string>>({});
  private readonly _description = signal<Record<string, string>>({});

  protected readonly timers = this._timers.asReadonly();
  protected readonly loading = this._loading.asReadonly();
  protected readonly saving = this._saving.asReadonly();
  protected readonly endedAt = this._endedAt.asReadonly();
  protected readonly description = this._description.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    void this.load();
  }

  protected setEndedAt(id: string, value: string): void {
    this._endedAt.update((current) => ({ ...current, [id]: value }));
  }

  protected setDescription(id: string, value: string): void {
    this._description.update((current) => ({ ...current, [id]: value }));
  }

  /**
   * Recupera o cronômetro gerando o registro de horas.
   *
   * O horário local do campo é convertido para instante antes de viajar: o backend trabalha em UTC,
   * e mandar `2026-08-08T17:30` sem fuso deixaria o servidor adivinhar — errando por três horas em
   * qualquer máquina brasileira.
   */
  protected async recover(timer: AbandonedTimer): Promise<void> {
    const local = this._endedAt()[timer.id] ?? '';
    if (local === '') {
      this.messages.add({
        severity: 'warn',
        summary: $localize`:@@timers.abandoned.endedAt.required:Informe quando o trabalho terminou.`,
      });
      return;
    }

    this._saving.set(timer.id);
    this._error.set(null);
    try {
      const description = this._description()[timer.id] ?? '';
      await firstValueFrom(
        this.api.recover(timer.id, {
          endedAt: new Date(local).toISOString(),
          description: description.trim() === '' ? undefined : description.trim(),
        }),
      );
      this.messages.add({
        severity: 'success',
        summary: $localize`:@@timers.abandoned.recovered:Horas registradas`,
      });
      await this.load();
      await this.timerStore.refresh();
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : null);
    } finally {
      this._saving.set(null);
    }
  }

  private async load(): Promise<void> {
    this._loading.set(true);
    try {
      this._timers.set(await firstValueFrom(this.api.abandoned()));
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : null);
    } finally {
      this._loading.set(false);
    }
  }
}
