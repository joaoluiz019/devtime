import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { TicketApi } from './ticket.api';
import {
  Ticket,
  TicketActivityEvent,
  TicketMoveContractResult,
  TicketStatus,
} from './ticket.model';

/**
 * Estado do ticket exibido em P19 (T-007-31).
 *
 * FR-045: nenhuma regra de negócio. As transições possíveis vêm de `availableTransitions`, que o
 * servidor calcula pela máquina de estados e pelo papel.
 */
@Injectable()
export class TicketDetailStore {
  private readonly api = inject(TicketApi);

  private readonly _ticket = signal<Ticket | null>(null);
  private readonly _events = signal<readonly TicketActivityEvent[]>([]);
  private readonly _cursor = signal<string | undefined>(undefined);
  private readonly _hasMore = signal(false);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _loadingActivity = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly ticket = this._ticket.asReadonly();
  readonly events = this._events.asReadonly();
  readonly hasMore = this._hasMore.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly loadingActivity = this._loadingActivity.asReadonly();
  readonly error = this._error.asReadonly();

  readonly availableTransitions = computed<readonly TicketStatus[]>(
    () => this._ticket()?.availableTransitions ?? [],
  );

  /** RN-309: o selo de estouro depende do servidor, que compara gasto com estimativa. */
  readonly isOverEstimate = computed(() => this._ticket()?.isOverEstimate === true);

  /** RN-306: contrato encerrado não aceita horas — a tela avisa antes de alguém tentar. */
  readonly acceptsWorkLogs = computed(() => this._ticket()?.contract.acceptsWorkLogs ?? false);

  async load(id: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._ticket.set(await firstValueFrom(this.api.getById(id)));
      await this.loadActivity(true);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._ticket.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  /**
   * Linha do tempo paginada por cursor.
   *
   * A auditoria de um ticket antigo tem centenas de eventos; carregá-los de uma vez violaria FR-161.
   * O cursor é o instante do último evento recebido — não um número de página —, então eventos novos
   * durante a leitura não deslocam a janela nem produzem repetição.
   */
  async loadActivity(reset = false): Promise<void> {
    const ticket = this._ticket();
    if (ticket === null) {
      return;
    }
    this._loadingActivity.set(true);
    try {
      const activity = await firstValueFrom(
        this.api.activity(ticket.id, reset ? undefined : this._cursor()),
      );
      this._events.set(reset ? activity.content : [...this._events(), ...activity.content]);
      this._cursor.set(activity.cursor);
      this._hasMore.set(activity.hasMore);
    } catch {
      // A linha do tempo é acessória: sua falha não pode esconder o ticket.
      if (reset) {
        this._events.set([]);
        this._hasMore.set(false);
      }
    } finally {
      this._loadingActivity.set(false);
    }
  }

  /** RN-308: `BLOCKED` exige motivo; o servidor recusa sem ele. */
  async transition(targetStatus: TicketStatus, blockReason?: string): Promise<boolean> {
    return this.run(async (ticket) => {
      const updated = await firstValueFrom(
        this.api.transition(ticket.id, { targetStatus, blockReason, version: ticket.version }),
      );
      this._ticket.set(updated);
      // A transição gera evento de auditoria: a linha do tempo precisa refletir o que acabou de ser
      // feito, senão parece que nada aconteceu.
      await this.loadActivity(true);
    });
  }

  async assign(assigneeId: string | null): Promise<boolean> {
    return this.run(async (ticket) => {
      const updated = await firstValueFrom(
        this.api.assign(ticket.id, { assigneeId, version: ticket.version }),
      );
      this._ticket.set(updated);
      await this.loadActivity(true);
    });
  }

  /**
   * RN-305 / INV-TKT-01: mover de contrato **não** muda a chave.
   *
   * O aviso vem do servidor (`notice`) e é repassado à tela: a chave permanece com o prefixo do
   * contrato antigo, e quem não é avisado conclui que a mudança não valeu.
   */
  async moveContract(
    targetContractId: string,
    confirmed: boolean,
  ): Promise<TicketMoveContractResult | null> {
    const ticket = this._ticket();
    if (ticket === null) {
      return null;
    }
    this._saving.set(true);
    this._error.set(null);
    try {
      const result = await firstValueFrom(
        this.api.moveContract(ticket.id, {
          targetContractId,
          confirmed,
          version: ticket.version,
        }),
      );
      await this.load(ticket.id);
      return result;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return null;
    } finally {
      this._saving.set(false);
    }
  }

  /** RN-307: ticket com horas registradas não é excluído; o caminho é cancelar. */
  async delete(): Promise<boolean> {
    return this.run((ticket) => firstValueFrom(this.api.delete(ticket.id)));
  }

  private async run(operation: (ticket: Ticket) => Promise<unknown>): Promise<boolean> {
    const ticket = this._ticket();
    if (ticket === null) {
      return false;
    }
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation(ticket);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._saving.set(false);
    }
  }
}
