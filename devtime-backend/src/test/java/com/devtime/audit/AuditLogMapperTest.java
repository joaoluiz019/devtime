package com.devtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.audit.domain.ActorType;
import com.devtime.audit.domain.AuditLog;
import com.devtime.audit.dto.AuditLogResponses.AuditChangeResponse;
import com.devtime.user.dto.UserSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T-002-44: forma da trilha exposta (users.md §10.1). */
class AuditLogMapperTest {

    private final AuditLogMapper mapper = new AuditLogMapper(new ObjectMapper());

    private AuditLog entry(String before, String after, String metadata, UUID actorId) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOccurredAt(Instant.parse("2026-08-02T12:00:00Z"));
        log.setTenantId(UUID.randomUUID());
        log.setActorId(actorId);
        log.setActorType(actorId == null ? ActorType.SYSTEM : ActorType.USER);
        log.setAction("MEMBERSHIP_ROLE_CHANGED");
        log.setEntityType("MEMBERSHIP");
        log.setEntityId(UUID.randomUUID());
        log.setBeforeState(before);
        log.setAfterState(after);
        log.setMetadata(metadata);
        return log;
    }

    @Test
    @DisplayName("changes[] traz apenas os campos que mudaram, com antes e depois")
    void changesContainOnlyDifferences() {
        var response =
                mapper.toResponse(
                        entry(
                                "{\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}",
                                "{\"role\":\"MEMBER\",\"status\":\"ACTIVE\"}",
                                "{}",
                                null),
                        Map.of());

        assertThat(response.changes())
                .containsExactly(new AuditChangeResponse("role", "ADMIN", "MEMBER"));
    }

    @Test
    @DisplayName("Campo presente apenas em um dos estados aparece com o outro lado nulo")
    void addedAndRemovedFieldsAppear() {
        var response =
                mapper.toResponse(
                        entry("{\"phone\":\"41999\"}", "{\"email\":\"x@y.z\"}", "{}", null),
                        Map.of());

        assertThat(response.changes())
                .containsExactlyInAnyOrder(
                        new AuditChangeResponse("phone", "41999", null),
                        new AuditChangeResponse("email", null, "x@y.z"));
    }

    @Test
    @DisplayName("§19.1: o IP é devolvido mascarado, nunca em claro")
    void ipIsMasked() {
        var response =
                mapper.toResponse(
                        entry(
                                "{}",
                                "{}",
                                "{\"ipAddress\":\"200.152.34.42\",\"traceId\":\"abc\"}",
                                null),
                        Map.of());

        assertThat(response.metadata()).containsEntry("ipAddress", "200.***.***.42");
        assertThat(response.metadata()).containsEntry("traceId", "abc");
    }

    @Test
    @DisplayName("RN-458: autor sem conta resolvida aparece como Usuário Removido")
    void unknownActorFallsBackToRemovedName() {
        UUID actorId = UUID.randomUUID();
        var response = mapper.toResponse(entry("{}", "{}", "{}", actorId), Map.of());

        assertThat(response.actor().id()).isEqualTo(actorId);
        assertThat(response.actor().name()).isEqualTo(UserSummary.REMOVED_USER_NAME);
    }

    @Test
    @DisplayName("CE-S-06: ação de sistema não possui autor")
    void systemActionHasNoActor() {
        var response = mapper.toResponse(entry("{}", "{}", "{}", null), Map.of());

        assertThat(response.actor().id()).isNull();
        assertThat(response.actor().type()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    @DisplayName("JSON ilegível não impede a leitura das demais entradas")
    void malformedJsonDegrades() {
        var response = mapper.toResponse(entry("{quebrado", "{}", "{}", null), Map.of());

        assertThat(response.changes()).isEmpty();
    }
}
