/**
 * Tipos da sessão autenticada, espelhando `docs/04-api/authentication.md` §5.3.
 *
 * AP-02 / FR-061: os tipos refletem exatamente os DTOs do backend, sem transformação (AP-03).
 */

/** Papéis de `permissions.md` §5. */
export type Role = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MEMBER' | 'VIEWER' | 'CLIENT_PORTAL';

export interface AuthenticatedUser {
  readonly id: string;
  readonly fullName: string;
  readonly displayName: string | null;
  readonly email: string;
  readonly avatarUrl: string | null;
}

export interface AuthenticatedTenant {
  readonly id: string;
  readonly name: string;
  readonly slug: string;
  readonly timezone: string;
  readonly currency: string;
  readonly logoUrl: string | null;
}

/** Tenant disponível para seleção quando o usuário pertence a mais de um. */
export interface TenantOption {
  readonly id: string;
  readonly name: string;
  readonly role: Role;
  readonly logoUrl: string | null;
}

/**
 * Resposta de login e de refresh (a estrutura é idêntica nos dois).
 *
 * `permissions` vem do servidor e **não** é derivada no cliente: TK-03 estabelece que as permissões
 * são resolvidas do papel a cada requisição no backend. Reproduzir a matriz de `permissions.md` §7 no
 * frontend criaria uma segunda fonte de verdade que divergiria na primeira alteração de papel.
 */
export interface AuthSessionResponse {
  readonly accessToken: string;
  readonly tokenType: 'Bearer';
  readonly expiresIn: number;
  readonly tenantSelectionRequired: boolean;
  readonly user: AuthenticatedUser;
  readonly tenant?: AuthenticatedTenant;
  readonly role?: Role;
  readonly permissions?: readonly string[];
  readonly tenants?: readonly TenantOption[];
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}
