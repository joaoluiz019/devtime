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

COPY --from=build /build/dist/devtime-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
