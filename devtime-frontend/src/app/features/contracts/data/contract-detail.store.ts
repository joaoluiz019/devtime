import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { ContractApi } from './contract.api';
import {
  Contract,
  ContractHistory,
  ContractPeriod,
  ContractTransitionRequest,
} from './contract.model';

/** Transições que a tela oferece, todas vindas de `availableActions` (ME-06). */
export type ContractAction =
  | 'UPDATE'
  | 'DELETE'
  | 'ACTIVATE_OR_RESUME'
  | 'SUSPEND'
  | 'END'
  | 'CANCEL';

/**
 * Estado do contrato exibido em P14 (T-004-22).
 *
 * FR-045: nenhuma regra de negócio. Quais transições existem é `availableActions`, calculado pelo
 * servidor a partir da máquina de estados e das permissões — reproduzir `state-machines.md` §4.5
 * aqui criaria uma segunda máquina, que divergiria da primeira.
 */
@Injectable()
export class ContractDetailStore {
  private readonly api = inject(ContractApi);

  private readonly _contract = signal<Contract | null>(null);
  private readonly _periods = signal<readonly ContractPeriod[]>([]);
  private readonly _history = signal<ContractHistory | null>(null);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly contract = this._contract.asReadonly();
  readonly periods = this._periods.asReadonly();
  readonly history = this._history.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();

  readonly status = computed(() => this._contract()?.status ?? null);

  /** ME-04: estado terminal não aceita escrita; a tela oculta as ações e explica por quê. */
  readonly isTerminal = computed(() => {
    const status = this.status();
    return status === 'ENDED' || status === 'CANCELLED';
  });

  readonly canUpdate = computed(() => this.allows('UPDATE'));
  readonly canDelete = computed(() => this.allows('DELETE'));
  readonly canSuspend = computed(() => this.allows('SUSPEND'));
  readonly canEnd = computed(() => this.allows('END'));
  readonly canCancel = computed(() => this.allows('CANCEL'));

  /**
   * A mesma ação do servidor cobre ativar (`DRAFT`) e retomar (`SUSPENDED`).
   *
   * O destino da transição é `ACTIVE` nos dois casos; o que muda é o significado para quem lê.
   */
  readonly canActivate = computed(
    () => this.allows('ACTIVATE_OR_RESUME') && this.status() === 'DRAFT',
  );
  readonly canResume = computed(
    () => this.allows('ACTIVATE_OR_RESUME') && this.status() === 'SUSPENDED',
  );

  private allows(action: ContractAction): boolean {
    return this._contract()?.availableActions.includes(action) ?? false;
  }

  async load(id: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._contract.set(await firstValueFrom(this.api.getById(id)));
      // Períodos e histórico só existem depois da ativação (RN-209): pedi-los para um `DRAFT`
      // gastaria duas requisições para receber listas vazias.
      if (this._contract()?.status !== 'DRAFT') {
        await Promise.all([this.loadPeriods(id), this.loadHistory(id)]);
      } else {
        this._periods.set([]);
        this._history.set(null);
      }
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._contract.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  private async loadPeriods(id: string): Promise<void> {
    this._periods.set(await firstValueFrom(this.api.periods(id)));
  }

  private async loadHistory(id: string): Promise<void> {
    this._history.set(await firstValueFrom(this.api.history(id)));
  }

  /** RN-209: a ativação gera o primeiro período na mesma transação. */
  async activate(): Promise<boolean> {
    return this.transition((id) => firstValueFrom(this.api.activate(id)));
  }

  /** CE-ME-09: a retomada gera os períodos faltantes, preservando a contiguidade. */
  async resume(): Promise<boolean> {
    return this.transition((id) => firstValueFrom(this.api.resume(id)));
  }

  async suspend(request: ContractTransitionRequest): Promise<boolean> {
    return this.transition((id) => firstValueFrom(this.api.suspend(id, request)));
  }

  /** RN-214: encerrar trunca o período corrente na data informada. */
  async end(request: ContractTransitionRequest): Promise<boolean> {
    return this.transition((id) => firstValueFrom(this.api.end(id, request)));
  }

  async cancel(request: ContractTransitionRequest): Promise<boolean> {
    return this.transition((id) => firstValueFrom(this.api.cancel(id, request)));
  }

  /** RN-205: exclusão só em `DRAFT`; nos demais estados o caminho é encerrar ou cancelar. */
  async delete(): Promise<boolean> {
    const contract = this._contract();
    if (contract === null) {
      return false;
    }
    this._saving.set(true);
    this._error.set(null);
    try {
      await firstValueFrom(this.api.delete(contract.id));
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._saving.set(false);
    }
  }

  /**
   * Toda transição recarrega o contrato.
   *
   * As respostas de transição trazem só o estado e os períodos afetados; aplicar isso ao contrato em
   * memória exigiria recompor `availableActions` no cliente — exatamente a máquina de estados que
   * FR-045 mantém no servidor.
   */
  private async transition(operation: (id: string) => Promise<unknown>): Promise<boolean> {
    const contract = this._contract();
    if (contract === null) {
      return false;
    }
    this._saving.set(true);
    this._error.set(null);
    try {
      await operation(contract.id);
      await this.load(contract.id);
      return true;
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return false;
    } finally {
      this._saving.set(false);
    }
  }
}
