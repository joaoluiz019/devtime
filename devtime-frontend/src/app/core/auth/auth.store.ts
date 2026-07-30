import { computed, inject, Injectable, signal } from '@angular/core';
import { ProblemDetail } from '../error/problem-detail.model';
import {
  AuthenticatedTenant,
  AuthenticatedUser,
  AuthSessionResponse,
  Role,
  TenantOption,
} from './auth.model';
import { TokenStorage } from './token.storage';

/**
 * Estado global de sessão (frontend.md §6.1, ST-01 a ST-06).
 *
 * ST-01: os Signals de escrita são privados e a exposição é sempre `asReadonly()` — um Signal de
 * escrita público permitiria a qualquer componente alterar a sessão. ST-02: todo dado derivado é
 * `computed`, nunca recalculado no template.
 *
 * FR-045 / ST-06: nenhuma regra de negócio vive aqui. As permissões chegam prontas do servidor; o
 * store apenas as guarda e responde consultas.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly tokenStorage = inject(TokenStorage);

  private readonly _user = signal<AuthenticatedUser | null>(null);
  private readonly _tenant = signal<AuthenticatedTenant | null>(null);
  private readonly _role = signal<Role | null>(null);
  private readonly _permissions = signal<ReadonlySet<string>>(new Set());
  private readonly _availableTenants = signal<readonly TenantOption[]>([]);
  private readonly _tenantSelectionRequired = signal(false);

  // ST-03: todo store expõe loading e error.
  private readonly _loading = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  readonly user = this._user.asReadonly();
  readonly tenant = this._tenant.asReadonly();
  readonly role = this._role.asReadonly();
  readonly permissions = this._permissions.asReadonly();
  readonly availableTenants = this._availableTenants.asReadonly();
  readonly tenantSelectionRequired = this._tenantSelectionRequired.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly isAuthenticated = computed(() => this._user() !== null);

  readonly hasTenantSelected = computed(() => this._tenant() !== null && this._role() !== null);

  /** Nome exibido na barra superior: o apelido quando existe, senão o nome completo. */
  readonly displayName = computed(() => {
    const user = this._user();
    if (user === null) {
      return '';
    }
    return user.displayName ?? user.fullName;
  });

  /** Iniciais para o avatar substituto quando a imagem não carrega (§17 do design system). */
  readonly initials = computed(() => {
    const name = this.displayName().trim();
    if (name === '') {
      return '';
    }
    const parts = name.split(/\s+/);
    const first = parts[0]?.charAt(0) ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.charAt(0) ?? '') : '';
    return (first + last).toUpperCase();
  });

  /** Aplica a resposta de login, de refresh ou de seleção de tenant. */
  applySession(session: AuthSessionResponse): void {
    this.tokenStorage.set(session.accessToken);
    this._user.set(session.user);
    this._tenant.set(session.tenant ?? null);
    this._role.set(session.role ?? null);
    this._permissions.set(new Set(session.permissions ?? []));
    this._availableTenants.set(session.tenants ?? []);
    this._tenantSelectionRequired.set(session.tenantSelectionRequired);
    this._error.set(null);
  }

  /**
   * Encerra a sessão local.
   *
   * CE-F-04: trocar de organização também limpa os stores de feature. A limpeza dos stores de feature
   * não é responsabilidade deste método — ele é `core` e não pode conhecer `features` (FR-02/FR-03).
   * Quem orquestra é o serviço de troca de tenant, na feature de autenticação.
   */
  clearSession(): void {
    this.tokenStorage.clear();
    this._user.set(null);
    this._tenant.set(null);
    this._role.set(null);
    this._permissions.set(new Set());
    this._availableTenants.set([]);
    this._tenantSelectionRequired.set(false);
  }

  setLoading(loading: boolean): void {
    this._loading.set(loading);
  }

  setError(error: ProblemDetail | null): void {
    this._error.set(error);
  }

  /**
   * Consulta de permissão para ocultar ações na interface.
   *
   * IMP-06 / FR-083: isto é **apenas ergonomia**. A decisão real de autorização é sempre do backend;
   * um `true` aqui não concede nada, e um `false` apenas evita oferecer uma ação que falharia.
   */
  hasPermission(permission: string): boolean {
    return this._permissions().has(permission);
  }

  hasAnyPermission(permissions: readonly string[]): boolean {
    return permissions.some((permission) => this.hasPermission(permission));
  }
}
