/**
 * Tipos de cliente, espelhando `ClientRequests` e `ClientResponses` do backend.
 *
 * AP-02 / FR-061: sem transformação. Campos opcionais no DTO chegam ausentes do JSON (§4.1 da API),
 * por isso são declarados com `?` e não como `| null` — a diferença importa ao montar o `PUT`, que
 * substitui o recurso inteiro.
 */
export type ClientStatus = 'ACTIVE' | 'INACTIVE';

export type DocumentType = 'CPF' | 'CNPJ' | 'OTHER';

export interface Address {
  readonly street?: string;
  readonly number?: string;
  readonly complement?: string;
  readonly district?: string;
  readonly city?: string;
  readonly state?: string;
  readonly postalCode?: string;
  readonly country?: string;
}

export interface Contact {
  readonly id: string;
  readonly name: string;
  readonly email?: string;
  readonly phone?: string;
  readonly role?: string;
  readonly isPrimary: boolean;
  readonly receivesReports: boolean;
  readonly version: number;
}

export interface ContactRequest {
  readonly name: string;
  readonly email?: string;
  readonly phone?: string;
  readonly role?: string;
  readonly isPrimary?: boolean;
  readonly receivesReports?: boolean;
}

export interface Client {
  readonly id: string;
  readonly name: string;
  readonly legalName?: string;
  readonly documentType?: DocumentType;
  readonly documentNumber?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly website?: string;
  readonly address?: Address;
  readonly notes?: string;
  readonly color: string;
  readonly status: ClientStatus;
  readonly activeContractsCount: number;
  readonly contacts: readonly Contact[];
  readonly createdAt: string;
  readonly updatedAt: string;
  /** RN-004: devolvido ao servidor no `PUT`; conflito responde `409 DEVTIME-2004`. */
  readonly version: number;
  /**
   * Ações que o servidor declara possíveis para este registro, dado estado e papel.
   *
   * ME-06 / DT-02: a interface **oculta** o que não está aqui. A lista vem pronta do backend em vez
   * de ser deduzida do status no cliente, porque a dedução duplicaria a máquina de estados e passaria
   * a divergir dela na primeira regra nova.
   */
  readonly availableActions: readonly string[];
}

export interface ClientListItem {
  readonly id: string;
  readonly name: string;
  readonly legalName?: string;
  readonly documentType?: DocumentType;
  readonly documentNumber?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly color: string;
  readonly status: ClientStatus;
  readonly activeContractsCount: number;
  readonly createdAt: string;
}

export interface ClientCreateRequest {
  readonly name: string;
  readonly legalName?: string;
  readonly documentType?: DocumentType;
  readonly documentNumber?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly website?: string;
  readonly address?: Address;
  readonly notes?: string;
  readonly color?: string;
  readonly contacts?: readonly ContactRequest[];
}

/** O `PUT` substitui o recurso: campo omitido é campo apagado. `version` é obrigatória (RN-004). */
export interface ClientUpdateRequest extends Omit<ClientCreateRequest, 'contacts'> {
  readonly version: number;
}

export interface DeactivateClientRequest {
  /** RN-407: obrigatório quando há contratos ativos; sem ele o servidor responde `DEVTIME-2407`. */
  readonly confirmActiveContracts?: boolean;
  readonly reason?: string;
}

export interface DeactivationImpact {
  readonly activeContractsUnaffected: number;
  readonly message: string;
}

export interface ClientDeactivationResult {
  readonly status: ClientStatus;
  readonly impact: DeactivationImpact;
}

/** Filtros de P10 que vivem na URL (LS-03). */
export interface ClientListQuery {
  readonly search?: string;
  readonly status?: ClientStatus;
  readonly hasActiveContracts?: boolean;
  readonly documentNumber?: string;
  readonly page: number;
  readonly size: number;
  readonly sort: string;
}

/**
 * Resumo consolidado de consumo do cliente (`GET /clients/{id}/summary`, clients.md §8).
 *
 * O endpoint é servido pela feature de contratos no backend, mas o caminho pertence a `/clients` e o
 * consumidor é P11 — declará-lo aqui evita que a tela de clientes importe de outra feature (FR-03).
 */
export interface ClientSummaryTotals {
  readonly contractedMinutes: number;
  readonly consumedMinutes: number;
  readonly nonBillableMinutes: number;
  readonly remainingMinutes: number;
  readonly overageMinutes: number;
}

export interface ClientSummaryByContract {
  readonly contractId: string;
  readonly code: string;
  readonly name: string;
  readonly minutes: number;
}

export interface ClientSummary {
  readonly clientId: string;
  readonly currency: string;
  readonly totals: ClientSummaryTotals;
  readonly byContract: readonly ClientSummaryByContract[];
}
