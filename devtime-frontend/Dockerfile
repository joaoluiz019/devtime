# Imagem do frontend (ADR-020).
#
# A SPA é conteúdo estático servido por nginx. Manter Node na imagem final acrescentaria um runtime
# completo sem nada para executar.

# ── Estágio 1 — build ─────────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /build

# `npm ci` exige o lockfile e instala exatamente as versões fixadas (DP-04) — `npm install` poderia
# resolver versões diferentes das verificadas em teste.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ── Estágio 2 — execução ──────────────────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

# O caminho inclui o locale: com `localize: true`, o Angular emite um diretório por locale dentro de
# `browser/`. Copiar `browser/` inteiro deixaria o conteúdo em `/usr/share/nginx/html/pt-BR/` e o
# nginx serviria sua própria página padrão na raiz.
#
# O locale é explícito porque I18-06 define pt-BR como único idioma agora. Ao adicionar en-US e es-ES
# em F6, isto passa a exigir uma estratégia de roteamento por locale no nginx — e falhar aqui de
# forma visível é melhor que servir silenciosamente o idioma errado.
COPY --from=build /build/dist/devtime-frontend/browser/pt-BR /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
