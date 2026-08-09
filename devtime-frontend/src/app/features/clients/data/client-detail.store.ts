import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  isProblemDetail,
  ProblemDetail,
  UNEXPECTED_PROBLEM,
} from '../../../core/error/problem-detail.model';
import { ClientApi } from './client.api';
import {
  Client,
  ClientDeactivationResult,
  ClientSummary,
  Contact,
  ContactRequest,
} from './client.model';

/**
 * Estado do cliente exibido em P11 (T-003-16 / T-003-22).
 *
 * FR-045 / ST-06: nenhuma regra de negócio aqui. Quais ações são possíveis é `client.availableActions`,
 * que o servidor calcula; o store apenas guarda e reexpõe.
 */
@Injectable()
export class ClientDetailStore {
  private readonly api = inject(ClientApi);

  private readonly _client = signal<Client | null>(null);
  private readonly _summary = signal<ClientSummary | null>(null);
  private readonly _loading = signal(false);
  private readonly _saving = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly client = this._client.asReadonly();
  readonly summary = this._summary.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly saving = this._saving.asReadonly();
  readonly error = this._error.asReadonly();

  readonly contacts = computed<readonly Contact[]>(() => this._client()?.contacts ?? []);

  /** RN-406 / INV-CON-01: no máximo um primário — o servidor garante; a tela apenas o destaca. */
  readonly primaryContact = computed(
    () => this.contacts().find((contact) => contact.isPrimary) ?? null,
  );

  readonly canEdit = computed(() => this.allows('UPDATE'));
  readonly canDelete = computed(() => this.allows('DELETE'));
  readonly canDeactivate = computed(() => this.allows('DEACTIVATE'));
  readonly canActivate = computed(() => this.allows('ACTIVATE'));

  /**
   * RN-407: inativar um cliente com contratos ativos exige confirmação explícita.
   *
   * A tela usa isto para decidir se o diálogo precisa declarar o impacto antes de confirmar.
   */
  readonly hasActiveContracts = computed(() => (this._client()?.activeContractsCount ?? 0) > 0);

  private allows(action: string): boolean {
    return this._client()?.availableActions.includes(action) ?? false;
  }

  async load(id: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      this._client.set(await firstValueFrom(this.api.getById(id)));
      await this.loadSummary(id);
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      this._client.set(null);
    } finally {
      this._loading.set(false);
    }
  }

  /**
   * O resumo é acessório: sua falha não derruba a tela.
   *
   * Ele depende de contratos e de saldo; um erro ali não pode impedir a leitura do cadastro, que é o
   * conteúdo principal de P11.
   */
  private async loadSummary(id: string): Promise<void> {
    try {
      this._summary.set(await firstValueFrom(this.api.summary(id)));
    } catch {
      this._summary.set(null);
    }
  }

  /**
   * Inativa o cliente (RN-405, RN-407).
   *
   * Devolve o impacto declarado pelo servidor em vez de recarregar em silêncio: RN-407 diz que
   * nenhum contrato é alterado, e é essa frase que a tela precisa mostrar a quem acabou de inativar.
   */
  async deactivate(
    confirmActiveContracts: boolean,
    reason?: string,
  ): Promise<ClientDeactivationResult | null> {
    const client = this._client();
    if (client === null) {
      return null;
    }
    return this.run(async () => {
      const result = await firstValueFrom(
        this.api.deactivate(client.id, { confirmActiveContracts, reason }),
      );
      await this.load(client.id);
      return result;
    });
  }

  async activate(): Promise<boolean> {
    const client = this._client();
    if (client === null) {
      return false;
    }
    const updated = await this.run(() => firstValueFrom(this.api.activate(client.id)));
    if (updated !== null) {
      this._client.set(updated);
    }
    return updated !== null;
  }

  async addContact(request: ContactRequest): Promise<boolean> {
    return this.withReload((client) => firstValueFrom(this.api.createContact(client.id, request)));
  }

  async updateContact(contactId: string, request: ContactRequest): Promise<boolean> {
    return this.withReload((client) =>
      firstValueFrom(this.api.updateContact(client.id, contactId, request)),
    );
  }

  async removeContact(contactId: string): Promise<boolean> {
    return this.withReload((client) =>
      firstValueFrom(this.api.deleteContact(client.id, contactId)),
    );
  }

  /**
   * RN-401: exclusão com contrato ativo responde `409 DEVTIME-2401`.
   *
   * O erro é guardado no store para a tela sugerir a inativação, que é a saída real de quem tentou
   * excluir um cliente em operação (FA-09).
   */
  async delete(): Promise<boolean> {
    const client = this._client();
    if (client === null) {
      return false;
    }
    const done = await this.run(() => firstValueFrom(this.api.delete(client.id)));
    return done !== null;
  }

  /**
   * O contato mudou no servidor; a lista vem do cliente, então o cliente é recarregado.
   *
   * Aplicar a alteração localmente exigiria reproduzir RN-406 no cliente: promover um contato a
   * primário rebaixa o anterior, e essa transição pertence ao `PrimaryContactPolicy` do backend.
   */
  private async withReload(operation: (client: Client) => Promise<unknown>): Promise<boolean> {
    const client = this._client();
    if (client === null) {
      return false;
    }
    const done = await this.run(async () => {
      await operation(client);
      await this.load(client.id);
      return true;
    });
    return done !== null;
  }

  private async run<T>(operation: () => Promise<T>): Promise<T | null> {
    this._saving.set(true);
    this._error.set(null);
    try {
      return await operation();
    } catch (error: unknown) {
      this._error.set(isProblemDetail(error) ? error : UNEXPECTED_PROBLEM);
      return null;
    } finally {
      this._saving.set(false);
    }
  }
}
