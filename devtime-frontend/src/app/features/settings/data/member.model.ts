import { Role } from '../../../core/auth/auth.model';

/**
 * Modelos de equipe, espelhando `MemberResponses` do backend (FR-061, AP-02).
 *
 * `Role` vem de `core/auth`: é o mesmo papel que a sessão carrega, e um segundo tipo idêntico
 * divergiria na primeira vez que um papel fosse acrescentado de um lado só.
 */

/** Situação do vínculo (`MembershipState`). */
export type MembershipState = 'INVITED' | 'ACTIVE' | 'SUSPENDED' | 'REMOVED';

/**
 * Ações que o **requisitante** pode executar sobre um membro (ME-06).
 *
 * Vêm calculadas pelo servidor a partir do papel de ambos. A tela não recalcula a matriz de
 * `permissions.md`: reproduzi-la aqui ofereceria botões que resultariam em `DEVTIME-1104` ou
 * `DEVTIME-2456`, e divergiria na primeira mudança da nota ¹.
 */
export type MemberAction =
  | 'CHANGE_ROLE'
  | 'SUSPEND'
  | 'REACTIVATE'
  | 'REMOVE'
  | 'RESEND_INVITATION';

export interface MemberUser {
  readonly id: string;
  readonly fullName: string;
  readonly displayName: string | null;
  readonly email: string | null;
  readonly avatarUrl: string | null;
}

/** Espelha `MemberResponse`. O bloco `stats` de users.md §7.1 não é emitido pelo backend. */
export interface Member {
  readonly id: string;
  readonly user: MemberUser;
  readonly role: Role;
  readonly status: MembershipState;
  readonly invitedAt: string | null;
  readonly acceptedAt: string | null;
  readonly availableActions: readonly MemberAction[];
  /** RN-004: acompanha a alteração de papel e provoca `DEVTIME-2004` quando desatualizado. */
  readonly version: number;
}

/** Espelha `MemberInvitationResponse` (users.md §7.2). */
export interface MemberInvitation {
  readonly id: string;
  readonly email: string;
  readonly role: Role;
  readonly status: MembershipState;
  readonly invitedAt: string;
  /** RN-457: sete dias após a emissão; o reenvio recomeça a contagem. */
  readonly expiresAt: string;
}

export interface InvitationRequest {
  readonly email: string;
  readonly role: Role;
  readonly message?: string;
}

export interface RoleUpdateRequest {
  readonly role: Role;
  readonly version: number;
}

/**
 * Efeitos da remoção (users.md §7.4).
 *
 * Existe para tornar RN-458 visível: sem estes números, quem remove um membro não tem como saber
 * que as horas registradas continuam nos relatórios e no saldo dos contratos.
 */
export interface MemberRemoval {
  readonly status: MembershipState;
  readonly workLogsPreserved: number;
  readonly ticketsReassigned: number;
  readonly reassignedTo: string | null;
  readonly activeTimerDiscarded: boolean;
}

export const MEMBERSHIP_STATE_LABELS: Readonly<Record<MembershipState, string>> = {
  INVITED: $localize`:@@member.status.invited:Convidado`,
  ACTIVE: $localize`:@@member.status.active:Ativo`,
  SUSPENDED: $localize`:@@member.status.suspended:Suspenso`,
  REMOVED: $localize`:@@member.status.removed:Removido`,
};
