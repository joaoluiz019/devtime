/** Tipos das telas de configuração, espelhando os DTOs do backend (AP-02 / FR-061). */
export type ThemePreference = 'LIGHT' | 'DARK' | 'SYSTEM';

export type DashboardPeriodPreference = 'CURRENT_PERIOD' | 'LAST_7_DAYS' | 'LAST_30_DAYS';

export interface UserPreferences {
  readonly theme: ThemePreference;
  readonly defaultCategoryId?: string;
  readonly dashboardPeriod: DashboardPeriodPreference;
  readonly emailNotifications: boolean;
  readonly mutedNotificationTypes: readonly string[];
  readonly timerReminderEnabled: boolean;
}

export interface UserProfile {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly displayName?: string;
  readonly avatarUrl?: string;
  readonly timezone: string;
  readonly locale: string;
  readonly preferences: UserPreferences;
  readonly version: number;
}

export interface UserProfileUpdateRequest {
  readonly fullName?: string;
  readonly displayName?: string;
  readonly timezone?: string;
  readonly locale?: string;
}

/** `PATCH /users/me/preferences`: campos ausentes permanecem como estão. */
export interface UserPreferencesUpdateRequest {
  readonly theme?: ThemePreference;
  readonly defaultCategoryId?: string;
  readonly dashboardPeriod?: DashboardPeriodPreference;
  readonly emailNotifications?: boolean;
  readonly mutedNotificationTypes?: readonly string[];
  readonly timerReminderEnabled?: boolean;
}

export interface TenantSettings {
  readonly workDayMinutes: number;
  readonly workDays: readonly number[];
  readonly defaultRolloverPolicy: string;
  readonly defaultOveragePolicy: string;
  readonly timerLongRunningMinutes: number;
  readonly timerAutoAbandonMinutes: number;
  readonly allowFutureWorkLogs: boolean;
  readonly retroactiveLimitDays: number;
  /** RN-113: passo do arredondamento de horas; `0` desliga. */
  readonly roundingMinutes: number;
  readonly notificationThresholds: readonly number[];
}

export interface Tenant {
  readonly id: string;
  readonly name: string;
  readonly slug: string;
  readonly legalName?: string;
  readonly documentNumber?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly timezone: string;
  readonly locale: string;
  readonly currency: string;
  readonly logoUrl?: string;
  readonly status: string;
  readonly planCode?: string;
  readonly settings: TenantSettings;
  readonly version: number;
}

export interface TenantUpdateRequest {
  readonly name?: string;
  readonly legalName?: string;
  readonly documentNumber?: string;
  readonly email?: string;
  readonly phone?: string;
  readonly timezone?: string;
  readonly locale?: string;
  readonly currency?: string;
  readonly version: number;
}

export interface TenantSettingsRequest extends Partial<
  Omit<TenantSettings, 'workDays' | 'notificationThresholds'>
> {
  readonly workDays?: readonly number[];
  readonly notificationThresholds?: readonly number[];
  readonly version: number;
}

export interface Category {
  readonly id: string;
  readonly name: string;
  readonly description?: string;
  readonly color: string;
  readonly icon?: string;
  readonly billableByDefault: boolean;
  readonly active: boolean;
  readonly sortOrder: number;
  /** RN-503: categoria de sistema é inativável e renomeável, nunca excluível. */
  readonly isSystem: boolean;
  readonly version: number;
}

export interface CategoryCreateRequest {
  readonly name: string;
  readonly description?: string;
  readonly color?: string;
  readonly billableByDefault?: boolean;
}

export interface CategoryUpdateRequest {
  readonly name: string;
  readonly description?: string;
  readonly color?: string;
  readonly billableByDefault: boolean;
  readonly active: boolean;
  readonly version: number;
}

/** RN-505: a exclusão migra os registros para a categoria substituta. */
export interface CategoryDeletionResult {
  readonly migratedWorkLogs: number;
  readonly migratedTo?: string;
}

export interface Tag {
  readonly id: string;
  readonly name: string;
  readonly color: string;
  readonly usageCount: number;
  readonly version: number;
}

export interface TagDeleteResult {
  readonly removedLinks: number;
}

export interface AuditActor {
  readonly id?: string;
  readonly name: string;
  readonly type: string;
}

export interface AuditChange {
  readonly field: string;
  readonly before?: string;
  readonly after?: string;
}

export interface AuditLog {
  readonly id: string;
  readonly occurredAt: string;
  readonly actor?: AuditActor;
  readonly action: string;
  readonly entityType: string;
  readonly entityId?: string;
  readonly changes: readonly AuditChange[];
  readonly metadata?: Record<string, unknown>;
}

export interface NotificationTypeOption {
  readonly type: string;
  readonly label: string;
  readonly severity: string;
  readonly canMute: boolean;
}

export interface NotificationPreferences {
  readonly emailNotifications: boolean;
  readonly mutedNotificationTypes: readonly string[];
  readonly availableTypes: readonly NotificationTypeOption[];
}
