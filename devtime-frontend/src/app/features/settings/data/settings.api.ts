import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { PageResponse } from '../../../shared/models/page.model';
import {
  AuditLog,
  Category,
  CategoryCreateRequest,
  CategoryDeletionResult,
  CategoryUpdateRequest,
  NotificationPreferences,
  Tag,
  TagDeleteResult,
  Tenant,
  TenantSettingsRequest,
  TenantUpdateRequest,
  UserPreferencesUpdateRequest,
  UserProfile,
  UserProfileUpdateRequest,
} from './settings.model';

/**
 * Transporte HTTP das telas de configuração.
 *
 * Uma classe para as seis áreas porque todas pertencem à mesma feature de tela e nenhuma tem mais de
 * quatro operações; seis serviços de três métodos seriam recorte por origem de dado, não por uso.
 *
 * FR-060 a FR-064: só HTTP, sem transformação nem tratamento de erro.
 */
@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  // ── Perfil (P26) ────────────────────────────────────────────────────────────────────────────
  profile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}/users/me`);
  }

  updateProfile(request: UserProfileUpdateRequest): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/users/me`, request);
  }

  // ── Preferências (P27) ──────────────────────────────────────────────────────────────────────
  updatePreferences(request: UserPreferencesUpdateRequest): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/users/me/preferences`, request);
  }

  // ── Notificações (P28) ──────────────────────────────────────────────────────────────────────
  notificationPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.base}/notifications/preferences`);
  }

  updateNotificationPreferences(request: {
    emailNotifications?: boolean;
    mutedNotificationTypes?: readonly string[];
  }): Observable<NotificationPreferences> {
    return this.http.patch<NotificationPreferences>(
      `${this.base}/notifications/preferences`,
      request,
    );
  }

  // ── Organização (P29) ───────────────────────────────────────────────────────────────────────
  tenant(): Observable<Tenant> {
    return this.http.get<Tenant>(`${this.base}/tenant`);
  }

  updateTenant(request: TenantUpdateRequest): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.base}/tenant`, request);
  }

  updateTenantSettings(request: TenantSettingsRequest): Observable<Tenant> {
    return this.http.patch<Tenant>(`${this.base}/tenant/settings`, request);
  }

  // ── Categorias (P30) ────────────────────────────────────────────────────────────────────────
  categories(includeInactive = true): Observable<readonly Category[]> {
    return this.http.get<readonly Category[]>(`${this.base}/categories`, {
      params: new HttpParams().set('active', !includeInactive),
    });
  }

  createCategory(request: CategoryCreateRequest): Observable<Category> {
    return this.http.post<Category>(`${this.base}/categories`, request);
  }

  updateCategory(id: string, request: CategoryUpdateRequest): Observable<Category> {
    return this.http.put<Category>(`${this.base}/categories/${encodeURIComponent(id)}`, request);
  }

  /** RN-505: com registros vinculados, a substituta é obrigatória e recebe as horas. */
  deleteCategory(id: string, replacementCategoryId?: string): Observable<CategoryDeletionResult> {
    let params = new HttpParams();
    if (replacementCategoryId !== undefined) {
      params = params.set('replacementCategoryId', replacementCategoryId);
    }
    return this.http.delete<CategoryDeletionResult>(
      `${this.base}/categories/${encodeURIComponent(id)}`,
      { params },
    );
  }

  // ── Etiquetas (P31) ─────────────────────────────────────────────────────────────────────────
  tags(): Observable<readonly Tag[]> {
    return this.http.get<readonly Tag[]>(`${this.base}/tags`);
  }

  createTag(request: { name: string; color?: string }): Observable<Tag> {
    return this.http.post<Tag>(`${this.base}/tags`, request);
  }

  updateTag(
    id: string,
    request: { name?: string; color?: string; version: number },
  ): Observable<Tag> {
    return this.http.patch<Tag>(`${this.base}/tags/${encodeURIComponent(id)}`, request);
  }

  deleteTag(id: string): Observable<TagDeleteResult> {
    return this.http.delete<TagDeleteResult>(`${this.base}/tags/${encodeURIComponent(id)}`);
  }

  // ── Auditoria (P33) ─────────────────────────────────────────────────────────────────────────
  /**
   * Trilha de auditoria.
   *
   * Sem intervalo, o servidor aplica os últimos 30 dias; acima de 90 dias responde `DEVTIME-3001`.
   * A tela informa o intervalo explicitamente para que o usuário saiba o que está vendo.
   */
  auditLogs(query: {
    entityType?: string;
    action?: string;
    occurredFrom?: string;
    occurredTo?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<AuditLog>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const [key, value] of Object.entries(query)) {
      if (key !== 'page' && key !== 'size' && typeof value === 'string' && value !== '') {
        params = params.set(key, value);
      }
    }
    return this.http.get<PageResponse<AuditLog>>(`${this.base}/audit-logs`, { params });
  }
}
