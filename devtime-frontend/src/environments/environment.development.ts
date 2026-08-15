/**
 * Ambiente de desenvolvimento.
 *
 * A base permanece relativa: o `proxy.conf.json` do dev-server encaminha `/api` para a API. Apontar
 * o cliente direto para o domínio do backend faria a requisição ser cross-origin, e aí o cookie
 * `SameSite=Strict` do refresh token deixaria de ser enviado e o HttpClient pararia de mandar o
 * `X-XSRF-TOKEN` — quebrando sessão e escritas apenas em desenvolvimento, o pior lugar para uma
 * divergência de comportamento. O proxy do dev-server e o bloco `/api` do nginx em produção
 * existem pelo mesmo motivo, e precisam ser mantidos em conjunto.
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
} as const;
