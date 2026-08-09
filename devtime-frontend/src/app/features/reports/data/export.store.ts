import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { ReportApi } from './report.api';
import { ExportExecution, ExportRequest, ExportResponse, isExportPending } from './report.model';

/** §21.3: intervalo do único polling do produto. */
const POLL_INTERVAL_MS = 3_000;

/** Limite de 5 minutos: passado isso, a notificação de `013` é o caminho, não o polling. */
const POLL_LIMIT_MS = 300_000;

/**
 * Estado das exportações em P24 (T-012-21, T-012-28).
 *
 * O polling existe porque o usuário está **parado esperando um arquivo**, e é limitado a 5 minutos
 * porque depois disso ele já saiu da tela — a conclusão chega por notificação (`013`). Um polling
 * sem limite continuaria batendo no servidor por uma aba esquecida aberta.
 */
@Injectable()
export class ExportStore {
  private readonly api = inject(ReportApi);

  private readonly _executions = signal<readonly ExportExecution[]>([]);
  private readonly _polling = signal(false);
  private readonly _requesting = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly executions = this._executions.asReadonly();
  readonly polling = this._polling.asReadonly();
  readonly requesting = this._requesting.asReadonly();
  readonly error = this._error.asReadonly();

  readonly pending = computed(() =>
    this._executions().filter((execution) => isExportPending(execution.status)),
  );

  private timer: ReturnType<typeof setTimeout> | null = null;
  private pollingStartedAt = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  async load(): Promise<void> {
    this._error.set(null);
    try {
      const page = await firstValueFrom(this.api.listExports());
      this._executions.set(page.content);
      this.syncPolling();
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
    }
  }

  /**
   * Solicita a exportação e devolve a resposta para que a tela decida o que dizer.
   *
   * No modo síncrono (`200`) o arquivo já existe e o download começa aqui mesmo. No assíncrono
   * (`202`) só resta acompanhar — e é por isso que a resposta volta para quem chamou: a mensagem
   * "baixando" e a mensagem "avisaremos quando ficar pronto" não podem ser a mesma.
   */
  async request(request: ExportRequest): Promise<ExportResponse | null> {
    this._requesting.set(true);
    this._error.set(null);
    try {
      const response = await firstValueFrom(this.api.requestExport(request, crypto.randomUUID()));
      if (response.status === 'COMPLETED' && response.downloadUrl !== null) {
        openSignedUrl(response.downloadUrl);
      }
      await this.load();
      return response;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return null;
    } finally {
      this._requesting.set(false);
    }
  }

  /**
   * Baixa uma exportação já concluída.
   *
   * FA-13: uma URL assinada expirada não regera o arquivo — o servidor apenas assina de novo. Por
   * isso o botão continua disponível enquanto a exportação não estiver `EXPIRED`.
   */
  async download(execution: ExportExecution): Promise<boolean> {
    this._error.set(null);
    try {
      const blob = await firstValueFrom(this.api.downloadExport(execution.id));
      saveBlob(blob, execution.fileName ?? `relatorio.${execution.format.toLowerCase()}`);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    }
  }

  /** §11.1: só `QUEUED` é cancelável; o botão é ocultado nos demais estados. */
  async cancel(id: string): Promise<boolean> {
    this._error.set(null);
    try {
      await firstValueFrom(this.api.cancelExport(id));
      await this.load();
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    }
  }

  /**
   * Liga ou desliga o polling conforme ainda exista exportação em andamento.
   *
   * Chamado a cada carga: uma exportação que terminou é o sinal para parar, e ficar perguntando por
   * algo já concluído seria trabalho puro de servidor.
   */
  private syncPolling(): void {
    if (this.pending().length === 0) {
      this.stopPolling();
      return;
    }
    if (this.timer !== null) {
      return;
    }
    this.pollingStartedAt = Date.now();
    this._polling.set(true);
    this.scheduleTick();
  }

  private scheduleTick(): void {
    this.timer = setTimeout(() => {
      this.timer = null;
      if (Date.now() - this.pollingStartedAt >= POLL_LIMIT_MS) {
        this.stopPolling();
        return;
      }
      void this.tick();
    }, POLL_INTERVAL_MS);
  }

  private async tick(): Promise<void> {
    const pending = this.pending();
    try {
      const refreshed = await Promise.all(
        pending.map((execution) => firstValueFrom(this.api.getExport(execution.id))),
      );
      const byId = new Map(refreshed.map((execution) => [execution.id, execution]));
      this._executions.update((executions) =>
        executions.map((execution) => byId.get(execution.id) ?? execution),
      );
    } catch {
      // O polling é acessório: uma falha de rede aqui não vira erro na tela, porque o estado
      // exibido continua sendo o último conhecido e a notificação de `013` ainda chega.
    }
    if (this.pending().length === 0) {
      this.stopPolling();
      return;
    }
    this.scheduleTick();
  }

  private stopPolling(): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this._polling.set(false);
  }
}

/** A URL assinada expira em 15 minutos (RN-712); abri-la numa aba é o caminho mais curto. */
function openSignedUrl(url: string): void {
  window.open(url, '_blank', 'noopener');
}

function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}
