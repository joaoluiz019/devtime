/**
 * Ambiente de desenvolvimento.
 *
 * A base permanece relativa: o `proxy.conf.json` do dev-server encaminha `/api` para
 * `http://localhost:8080`. Apontar direto para o backend faria a requisição ser cross-origin e o
 * cookie `SameSite=Strict` do refresh token deixaria de ser enviado, quebrando a sessão apenas em
 * desenvolvimento — o pior lugar para uma divergência de comportamento.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
} as const;
