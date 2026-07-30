/**
 * Ambiente de produção.
 *
 * AP-05 / FR-064: toda URL parte de `/api/v1` e o host vem do ambiente. Em produção o frontend é
 * servido pelo mesmo domínio da API, então a base é relativa — o que também é pré-requisito do
 * cookie `SameSite=Strict` do refresh token (security.md §5.4).
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
} as const;
