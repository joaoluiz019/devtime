/** Ação sugerida pela notificação: rótulo e rota interna. */
export interface NotificationAction {
  readonly label: string;
  readonly route: string;
}

export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface AppNotification {
  readonly id: string;
  readonly type: string;
  readonly severity: NotificationSeverity;
  readonly title: string;
  readonly body: string;
  readonly payload?: Record<string, unknown>;
  readonly entityType?: string;
  readonly entityId?: string;
  readonly action?: NotificationAction;
  /** Nulo enquanto não lida; é o que o índice parcial do backend indexa. */
  readonly readAt?: string;
  readonly emailSentAt?: string;
  readonly createdAt: string;
}

export interface UnreadCount {
  readonly unreadCount: number;
  readonly bySeverity: Record<string, number>;
}

export interface NotificationReadResult {
  readonly id: string;
  readonly readAt?: string;
  readonly unreadCount: number;
}

export interface MarkAllReadResult {
  readonly markedCount: number;
  readonly unreadCount: number;
}

/** Evento do fluxo SSE: o mínimo para avisar sem recarregar a lista. */
export interface NotificationStreamEvent {
  readonly id: string;
  readonly type: string;
  readonly severity: NotificationSeverity;
  readonly title: string;
  readonly unreadCount: number;
}

/** Filtros de P25, na URL (LS-03). */
export interface NotificationListQuery {
  readonly read?: boolean;
  readonly type?: string;
  readonly severity?: NotificationSeverity;
  readonly page: number;
  readonly size: number;
}
