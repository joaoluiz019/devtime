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
# O entrypoint da imagem oficial expande os templates deste diretório em /etc/nginx/conf.d/ na
# subida, trocando `default.conf.template` por `default.conf`.
COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.conf /etc/nginx/security-headers.conf

# Sem o filtro, o envsubst do entrypoint substituiria **todas** as variáveis do ambiente — inclusive
# `$host`, `$uri` e `$remote_addr`, que são do nginx e não do shell. Como elas não existem no
# ambiente, virariam string vazia e a configuração deixaria de funcionar de formas silenciosas.
# Restringindo ao prefixo do projeto, apenas os dois parâmetros abaixo são expandidos.
ENV NGINX_ENVSUBST_FILTER="^DEVTIME_"

# Destino do proxy de /api. O padrão é o ambiente local do compose, onde o backend é um serviço na
# mesma rede; o deploy sobrescreve os dois valores com o domínio público da API. O `Host` vai
# separado porque o proxy do Coolify roteia por ele, e o cabeçalho não aceita esquema nem porta.
ENV DEVTIME_API_ORIGIN="http://backend:8080"
ENV DEVTIME_API_HOST="backend"

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
