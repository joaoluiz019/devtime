# Imagem do backend (ADR-020).
#
# Multi-stage: o estágio de build carrega Maven e o JDK completo; a imagem final carrega apenas o JRE.
# Sem essa separação, a imagem de produção levaria o compilador, as fontes e o cache do Maven — mais
# superfície de ataque e centenas de megabytes sem função em execução.

# ── Estágio 1 — build ─────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# As dependências são resolvidas antes de copiar o código-fonte para que a camada de dependências
# seja reaproveitada entre builds: mudar uma classe não deve invalidar o download do Maven.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

# Os testes não rodam aqui: a suíte de integração exige Testcontainers, que precisaria do socket do
# Docker dentro do build. Os gates de teste pertencem ao pipeline (architecture.md §11).
RUN mvn -B -DskipTests clean package

# ── Estágio 2 — execução ──────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# Usuário sem privilégios: um processo comprometido rodando como root herdaria o controle do
# contêiner inteiro.
RUN addgroup -S devtime && adduser -S devtime -G devtime

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R devtime:devtime /app

USER devtime

# Porta padrão. `EXPOSE` é documentação: quem decide onde a aplicação escuta é `DEVTIME_SERVER_PORT`
# (server.port), e o orquestrador precisa concordar com esse valor, não com este.
EXPOSE 8080

# DP-05: a readiness só responde OK após a validação do schema, então é ela que o health check observa.
#
# A porta vem de `DEVTIME_SERVER_PORT`, com o mesmo default de application.yml, em vez de ficar fixa
# aqui. Fixa, o health check consultava 8080 enquanto a aplicação subia na porta configurada, e o
# resultado era uma aplicação **saudável** declarada morta: Flyway validado, contexto iniciado,
# `Started DevTimeApplication` no log, e mesmo assim três tentativas recusadas e rollback do deploy.
#
# O `${...}` é expandido pelo shell na execução, não no build — o CMD abaixo está na forma shell
# justamente para isso, e por isso acompanha uma mudança de porta sem exigir rebuild da imagem.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${DEVTIME_SERVER_PORT:-8080}/actuator/health/readiness" || exit 1

# MaxRAMPercentage em vez de -Xmx fixo: o limite de memória do contêiner é definido no orquestrador,
# e um valor fixo aqui divergiria dele silenciosamente.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
