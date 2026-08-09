import {
  TicketPriority,
  TicketStatus,
} from '../../../shared/components/ticket-badges/ticket-badges.component';
import { TagOption } from '../../../shared/data/tag-lookup.api';

export type { TicketPriority, TicketStatus };

export type TicketType = 'FEATURE' | 'BUG' | 'SUPPORT' | 'MEETING' | 'MAINTENANCE' | 'OTHER';

export interface TicketUser {
  readonly id: string;
  readonly name: string;
  readonly handle?: string;
  readonly avatarUrl?: string;
}

export interface TicketContract {
  readonly id: string;
  readonly code: string;
  readonly name: string;
  readonly status: string;
  /** RN-306: contrato encerrado ou cancelado não aceita registro de horas. */
  readonly acceptsWorkLogs: boolean;
}

export interface TicketClient {
  readonly id: string;
  readonly name: string;
  readonly color: string;
}

export interface Ticket {
  readonly id: string;
  readonly number: number;
  /** Chave legível (`CT-0001-42`). INV-TKT-01: imutável, mesmo ao mover de contrato. */
  readonly key: string;
  readonly title: string;
  readonly description?: string;
  readonly type: TicketType;
  readonly status: TicketStatus;
  readonly priority: TicketPriority;
  readonly contract: TicketContract;
  readonly client: TicketClient;
  readonly assignee?: TicketUser;
  readonly reporter?: TicketUser;
  readonly estimatedMinutes?: number;
  readonly spentMinutes: number;
  readonly billableMinutes: number;
  readonly progressRate?: number;
  /** RN-309: verdadeiro quando o gasto ultrapassa a estimativa. */
  readonly isOverEstimate?: boolean;
  readonly blockReason?: string;
  readonly dueDate?: string;
  readonly startedAt?: string;
  readonly completedAt?: string;
  readonly externalRef?: string;
  readonly defaultCategoryId?: string;
  readonly tags: readonly TagOption[];
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
  /** Situações alcançáveis a partir da atual, já filtradas por papel (ME-06). */
  readonly availableTransitions: readonly TicketStatus[];
}

export interface TicketSummary {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly type: TicketType;
  readonly status: TicketStatus;
  readonly priority: TicketPriority;
  readonly contractCode: string;
  readonly assignee?: TicketUser;
  readonly estimatedMinutes?: number;
  readonly spentMinutes: number;
  readonly progressRate?: number;
  readonly isOverEstimate?: boolean;
  readonly tags: readonly TagOption[];
  readonly dueDate?: string;
  readonly updatedAt: string;
}

export interface TicketBoardColumn {
  readonly status: TicketStatus;
  /** Total real da coluna; o quadro traz no máximo 50 cartões por vez. */
  readonly totalCount: number;
  readonly totalSpentMinutes: number;
  readonly tickets: readonly TicketSummary[];
}

export interface TicketBoard {
  readonly columns: readonly TicketBoardColumn[];
}

export interface TicketCreateRequest {
  readonly contractId: string;
  readonly title: string;
  readonly description?: string;
  readonly type?: TicketType;
  readonly priority?: TicketPriority;
  readonly assigneeId?: string;
  readonly estimatedMinutes?: number;
  readonly dueDate?: string;
  readonly defaultCategoryId?: string;
  readonly tagIds?: readonly string[];
  readonly tagNames?: readonly string[];
  readonly externalRef?: string;
}

/** RN-302: o contrato não muda pelo `PUT`; para isso existe `move-contract`. */
export interface TicketUpdateRequest {
  readonly title: string;
  readonly description?: string;
  readonly type: TicketType;
  readonly priority: TicketPriority;
  readonly estimatedMinutes?: number;
  readonly dueDate?: string;
  readonly defaultCategoryId?: string;
  readonly tagIds?: readonly string[];
  readonly externalRef?: string;
  readonly version: number;
}

export interface TicketTransitionRequest {
  readonly targetStatus: TicketStatus;
  /** RN-308: obrigatório ao entrar em `BLOCKED`. */
  readonly blockReason?: string;
  readonly version: number;
}

export interface TicketAssignRequest {
  /** Nulo desatribui: o campo precisa viajar, e não simplesmente sumir do corpo. */
  readonly assigneeId: string | null;
  readonly version: number;
}

export interface TicketMoveContractRequest {
  readonly targetContractId: string;
  readonly confirmed: boolean;
  readonly version: number;
}

export interface TicketMoveContractResult {
  readonly id: string;
  readonly key: string;
  readonly contract: TicketContract;
  readonly notice?: string;
}

export interface TicketActivityEvent {
  readonly type: string;
  readonly occurredAt: string;
  readonly actor?: TicketUser;
  readonly data: Record<string, unknown>;
}

/** Paginação por cursor: a linha do tempo cresce pela ponta mais recente. */
export interface TicketActivity {
  readonly content: readonly TicketActivityEvent[];
  readonly cursor?: string;
  readonly hasMore: boolean;
}

/** Filtros de P17, que vivem na URL (LS-03). O filtro por etiqueta é conjuntivo. */
export interface TicketListQuery {
  readonly contractId?: string;
  readonly clientId?: string;
  readonly status?: readonly TicketStatus[];
  readonly type?: readonly TicketType[];
  readonly priority?: readonly TicketPriority[];
  readonly assigneeId?: string;
  readonly tagIds?: readonly string[];
  readonly search?: string;
  readonly isOverEstimate?: boolean;
  readonly page: number;
  readonly size: number;
  readonly sort: string;
}
