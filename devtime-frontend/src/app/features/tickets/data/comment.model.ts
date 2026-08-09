/**
 * Modelos da conversa do ticket, espelhando `CommentResponses` (FR-061, AP-02).
 *
 * `canEdit` e `canDelete` chegam **calculados pelo servidor**. A janela de 24h de RN-812 não é
 * reimplementada aqui: duas definições de "ainda dá para editar" divergiriam no primeiro ajuste de
 * fuso ou de relógio, e a tela ofereceria um botão que o servidor recusa.
 */

/** RN-815: o que produziu um comentário de sistema. */
export type SystemCommentTrigger = 'STATUS_CHANGED' | 'ASSIGNEE_CHANGED' | 'CONTRACT_MOVED';

/** `UserSummary`: existe para exibir um nome ao lado do comentário, e nada além disso. */
export interface CommentAuthor {
  readonly id: string;
  /** RN-458: `Usuário Removido` quando o membro saiu; o vínculo histórico é preservado. */
  readonly name: string;
  readonly handle: string | null;
  readonly avatarUrl: string | null;
}

export interface TicketComment {
  readonly id: string;
  readonly ticketId: string;
  readonly body: string;
  readonly author: CommentAuthor | null;
  readonly parentCommentId: string | null;
  readonly mentionedUsers: readonly CommentAuthor[];
  readonly isSystem: boolean;
  readonly systemTrigger: SystemCommentTrigger | null;
  readonly createdAt: string;
  readonly editedAt: string | null;
  readonly canEdit: boolean;
  readonly canDelete: boolean;
  readonly version: number;
  /** Hierarquia de um nível (RN-814): uma resposta nunca traz respostas. */
  readonly replies: readonly TicketComment[];
}

export interface CommentThread {
  readonly content: readonly TicketComment[];
  readonly cursor: string | null;
  readonly hasMore: boolean;
  readonly totalComments: number;
}

export interface CommentCreateRequest {
  readonly body: string;
  readonly parentCommentId?: string;
}

export interface CommentUpdateRequest {
  readonly body: string;
  readonly version: number;
}

export const COMMENT_BODY_MAX = 10_000;
