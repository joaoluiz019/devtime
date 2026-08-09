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

/** Situação da organização (`state-machines.md` §4.1). */
export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'CANCELLED';

/**
 * Tenant disponível para seleção quando o usuário pertence a mais de um.
 *
 * CX-08: organizações suspensas chegam **marcadas** por `status`, não omitidas — por isso o campo
 * existe aqui e a tela precisa desenhá-lo.
 */
export interface TenantOption {
  readonly id: string;
  readonly name: string;
  readonly slug: string;
  readonly role: Role;
  readonly logoUrl: string | null;
  readonly status: TenantStatus;
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

/**
 * Cadastro de conta com organização própria (`AuthRequests.RegisterRequest`).
 *
 * `tenantName` e `timezone` são opcionais no contrato: o backend usa o nome do titular e
 * `America/Sao_Paulo` como padrão (entities.md §6.1). O cliente envia o que o usuário preencheu, sem
 * reproduzir o padrão aqui — duplicá-lo criaria uma segunda fonte de verdade.
 */
export interface RegisterRequest {
  readonly email: string;
  readonly password: string;
  readonly fullName: string;
  readonly tenantName?: string;
  readonly timezone?: string;
  readonly acceptedTerms: boolean;
}

/** `POST /auth/register` → `201`. Sem token: o login exige verificação (CP-08). */
export interface RegisterResponse {
  readonly userId: string;
  readonly tenantId: string;
  readonly email: string;
  readonly status: string;
  readonly verificationEmailSent: boolean;
}

export interface VerifyEmailRequest {
  readonly token: string;
}

export interface ResendVerificationRequest {
  readonly email: string;
}

export interface ForgotPasswordRequest {
  readonly email: string;
}

export interface ResetPasswordRequest {
  readonly token: string;
  readonly newPassword: string;
}

export interface SelectTenantRequest {
  readonly tenantId: string;
}

/**
 * Convite consultado antes do aceite (`GET /auth/invitations/{token}`, §5.12).
 *
 * `userExists` responde à pergunta que a tela faz — "peço senha ou peço cadastro?". É `true` quando
 * a conta **já pode autenticar**, e não quando existe linha em `users`: o convite cria a conta em
 * `PENDING_ACTIVATION` antes de a pessoa definir senha.
 */
export interface InvitationPreview {
  readonly tenantName: string;
  readonly tenantLogoUrl: string | null;
  readonly invitedByName: string;
  readonly role: Role;
  readonly email: string;
  readonly userExists: boolean;
  readonly expiresAt: string;
}

/**
 * Corpo do aceite (`AcceptInvitationRequest`).
 *
 * Os três casos de §5.12 se distinguem pelo que vai aqui: nada (já autenticado), só a senha (conta
 * existente) ou nome e senha (conta nova). Nenhum campo é obrigatório no tipo porque o servidor é
 * quem decide qual combinação o token exige.
 */
export interface AcceptInvitationRequest {
  readonly fullName?: string;
  readonly password?: string;
}

/** Resposta genérica de operação aceita (`AuthResponses.MessageResponse`). */
export interface MessageResponse {
  readonly message: string;
}
