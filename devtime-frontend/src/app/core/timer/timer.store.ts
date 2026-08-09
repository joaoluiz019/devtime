import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import { isProblemDetail, ProblemDetail, UNEXPECTED_PROBLEM } from '../error/problem-detail.model';
import { TimerApi } from './timer.api';
import {
  elapsedSeconds,
  Timer,
  TimerStartRequest,
  TimerStopResult,
  TimerUpdateRequest,
} from './timer.model';

/** TB-02: ressincroniza a cada 60s. Nunca por segundo — o relógio é local (RN-151). */
const RESYNC_INTERVAL_MS = 60_000;

/** Frequência da animação do contador; não gera requisição alguma. */
const TICK_INTERVAL_MS = 1_000;

/** Canal de sincronização entre abas do mesmo navegador (§21.3). */
const CHANNEL_NAME = 'devtime-timer';

/**
 * Estado do cronômetro (T-009, §21.3).
 *
 * Vive em `core` e em `root` porque o cronômetro é global: um store provido na rota morreria a cada
 * navegação e o contador reiniciaria a cada clique no menu.
 *
 * **O servidor é a fonte da verdade do estado; o relógio é local.** `now` avança de segundo em
 * segundo apenas para animar o número, e a ressincronização de 60s — mais o retorno de foco da aba —
 * corrige qualquer deriva. É por isso que hibernar a máquina não estraga a contagem: ao voltar, o
 * tempo é recalculado a partir de `startedAt`, e não de um contador que ficou parado.
 *
 * Entre abas, `BroadcastChannel` propaga a ação local imediatamente (§21.3): pausar numa aba e ver a
 * outra continuar contando faria o usuário duvidar de qual delas está certa.
 */
@Injectable({ providedIn: 'root' })
export class TimerStore {
  private readonly api = inject(TimerApi);
  private readonly authStore = inject(AuthStore);

  private readonly _current = signal<Timer | null>(null);
  private readonly _now = signal(Date.now());
  private readonly _loading = signal(false);
  private readonly _busy = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly current = this._current.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly busy = this._busy.asReadonly();

  /**
   * Erro da última ação.
   *
   * RN-160 / TB-05: uma falha de encerramento **não** limpa o cronômetro. O erro e o cronômetro
   * coexistem na tela, porque o trabalho continua registrado e o usuário precisa corrigir o motivo,
   * não recomeçar.
   */
  readonly error = this._error.asReadonly();

  readonly isActive = computed(() => {
    const timer = this._current();
    return timer !== null && (timer.status === 'RUNNING' || timer.status === 'PAUSED');
  });

  readonly status = computed(() => this._current()?.status ?? null);

  readonly elapsed = computed(() => {
    const timer = this._current();
    return timer === null ? 0 : elapsedSeconds(timer, this._now());
  });

  private ticker: ReturnType<typeof setInterval> | null = null;
  private resyncTimer: ReturnType<typeof setInterval> | null = null;
  private channel: BroadcastChannel | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.teardown());
  }

  /**
   * Liga o cronômetro global.
   *
   * Chamado pelo shell: é o único ponto em que o ciclo de vida coincide com o da sessão autenticada.
   * `VIEWER` não registra horas e não recebe nada disso — pedir o estado para quem não pode iniciar
   * um cronômetro gastaria uma requisição por carregamento de tela.
   */
  async connect(): Promise<void> {
    if (!this.authStore.hasTenantSelected() || !this.authStore.hasPermission('TIMER_USE')) {
      return;
    }
    await this.refresh();
    this.startTicker();
    this.startResync();
    this.openChannel();
    this.listenVisibility();
  }

  disconnect(): void {
    this.teardown();
    this._current.set(null);
  }

  async refresh(): Promise<void> {
    this._loading.set(true);
    try {
      this._current.set(await firstValueFrom(this.api.current()));
      this._now.set(Date.now());
    } catch {
      // TB-02 / CE-CO-02: falha de rede não apaga o cronômetro da tela. O tempo continua correndo
      // localmente e a próxima ressincronização corrige — zerar aqui sugeriria trabalho perdido.
    } finally {
      this._loading.set(false);
    }
  }

  async start(request: TimerStartRequest, stopCurrent = false): Promise<boolean> {
    return this.mutate(async () => {
      this._current.set(await firstValueFrom(this.api.start(request, stopCurrent)));
    });
  }

  async update(request: TimerUpdateRequest): Promise<boolean> {
    return this.mutate(async () => {
      this._current.set(await firstValueFrom(this.api.update(request)));
    });
  }

  async pause(): Promise<boolean> {
    return this.mutate(async () => {
      this._current.set(await firstValueFrom(this.api.pause()));
    });
  }

  async resume(): Promise<boolean> {
    return this.mutate(async () => {
      this._current.set(await firstValueFrom(this.api.resume()));
    });
  }

  /**
   * Encerra e gera o registro de horas.
   *
   * Devolve o resultado — e não apenas sucesso — porque a tela precisa dizer **quanto** foi
   * registrado e o que aconteceu com o saldo. Em falha, devolve `null` e o cronômetro permanece
   * exatamente como estava (RN-160).
   */
  async stop(description: string): Promise<TimerStopResult | null> {
    this._busy.set(true);
    this._error.set(null);
    try {
      const result = await firstValueFrom(this.api.stop({ description }));
      this._current.set(null);
      this.broadcast();
      return result;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return null;
    } finally {
      this._busy.set(false);
    }
  }

  /** RN-162: irreversível e sem gerar registro. A confirmação pertence à tela. */
  async discard(): Promise<boolean> {
    return this.mutate(async () => {
      await firstValueFrom(this.api.discard());
      this._current.set(null);
    });
  }

  clearError(): void {
    this._error.set(null);
  }

  private async mutate(operation: () => Promise<void>): Promise<boolean> {
    this._busy.set(true);
    this._error.set(null);
    try {
      await operation();
      this._now.set(Date.now());
      this.broadcast();
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._busy.set(false);
    }
  }

  private startTicker(): void {
    if (this.ticker !== null) {
      return;
    }
    this.ticker = setInterval(() => this._now.set(Date.now()), TICK_INTERVAL_MS);
  }

  private startResync(): void {
    if (this.resyncTimer !== null) {
      return;
    }
    this.resyncTimer = setInterval(() => void this.refresh(), RESYNC_INTERVAL_MS);
  }

  /**
   * Abre o canal entre abas.
   *
   * A mensagem não carrega estado: ela apenas diz "algo mudou". Enviar o cronômetro inteiro
   * transformaria uma aba na fonte da verdade da outra, e as duas divergiriam da única fonte que
   * importa — o servidor.
   */
  private openChannel(): void {
    if (this.channel !== null || typeof BroadcastChannel === 'undefined') {
      return;
    }
    this.channel = new BroadcastChannel(CHANNEL_NAME);
    this.channel.onmessage = () => void this.refresh();
  }

  private broadcast(): void {
    this.channel?.postMessage('changed');
  }

  /** TB-02: ao voltar o foco da aba, o estado é confirmado com o servidor. */
  private listenVisibility(): void {
    if (typeof document === 'undefined') {
      return;
    }
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  private readonly onVisibilityChange = (): void => {
    if (document.visibilityState === 'visible') {
      void this.refresh();
    }
  };

  private teardown(): void {
    if (this.ticker !== null) {
      clearInterval(this.ticker);
      this.ticker = null;
    }
    if (this.resyncTimer !== null) {
      clearInterval(this.resyncTimer);
      this.resyncTimer = null;
    }
    this.channel?.close();
    this.channel = null;
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
  }
}
