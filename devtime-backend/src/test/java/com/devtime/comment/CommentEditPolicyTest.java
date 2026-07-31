package com.devtime.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.comment.domain.Comment;
import com.devtime.comment.domain.SystemCommentTrigger;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.security.Permission;
import com.devtime.shared.security.Role;
import com.devtime.shared.security.RolePermissions;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.tenancy.TenantSession;
import com.devtime.shared.time.TenantClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Janela de edição de comentário (RN-812, T-014-16).
 *
 * <p>Teste unitário com {@code Clock} controlado, e não de integração: a regra é uma comparação de
 * instantes, e verificá-la com um relógio fixo exigiria criar comentários com {@code createdAt}
 * forjado — que é justamente o que os builders de teste evitam (BR-207). Aqui o relógio anda e o
 * dado permanece real.
 */
class CommentEditPolicyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-29T14:32:10Z");
    private static final UUID AUTHOR_ID = UUID.fromString("019fb000-0000-7000-8000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("019fb000-0000-7000-8000-000000000002");
    private static final UUID TENANT_ID = UUID.fromString("019fb000-0000-7000-8000-0000000000ff");

    private final TenantContext tenantContext = new TenantContext();

    @AfterEach
    void clearContext() {
        tenantContext.clear();
    }

    @ParameterizedTest(name = "{0}h após a criação: editável = {1}")
    @CsvSource({"0, true", "1, true", "23, true", "24, false", "25, false", "720, false"})
    @DisplayName("RN-812/CX-09: a janela é estritamente menor que 24h — 24h exatas já está fora")
    void editWindowShouldBeStrictlyUnderTwentyFourHours(long hoursElapsed, boolean editable) {
        CommentEditPolicy policy = policyAt(CREATED_AT.plus(Duration.ofHours(hoursElapsed)));
        signIn(AUTHOR_ID, Role.MEMBER);
        Comment comment = userComment();

        assertThat(policy.canEdit(comment)).isEqualTo(editable);
        if (editable) {
            policy.assertEditable(comment);
        } else {
            assertThatThrownBy(() -> policy.assertEditable(comment))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(
                            failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                    .isEqualTo("DEVTIME-2706");
        }
    }

    @Test
    @DisplayName("RN-812: 23h59 ainda edita; um minuto depois, não")
    void windowShouldCloseExactlyAtTwentyFourHours() {
        signIn(AUTHOR_ID, Role.MEMBER);
        Comment comment = userComment();

        assertThat(policyAt(CREATED_AT.plus(Duration.ofHours(23).plusMinutes(59))).canEdit(comment))
                .isTrue();
        assertThat(policyAt(CREATED_AT.plus(Duration.ofHours(24))).canEdit(comment)).isFalse();
    }

    @Test
    @DisplayName("§6.3/CX-12: ADMIN não edita comentário de terceiro — DEVTIME-1103")
    void adminShouldNotEditOthersComment() {
        CommentEditPolicy policy = policyAt(CREATED_AT.plusSeconds(60));
        signIn(OTHER_ID, Role.ADMIN);
        Comment comment = userComment();

        assertThat(policy.canEdit(comment)).isFalse();
        assertThatThrownBy(() -> policy.assertEditable(comment))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-1103");
    }

    @Test
    @DisplayName("RN-812/FA-09: ADMIN modera fora da janela — exclui o que não pode editar")
    void adminShouldModerateOutsideWindow() {
        CommentEditPolicy policy = policyAt(CREATED_AT.plus(Duration.ofDays(30)));
        signIn(OTHER_ID, Role.ADMIN);
        Comment comment = userComment();

        assertThat(policy.canDelete(comment)).isTrue();
        policy.assertDeletable(comment);
    }

    @Test
    @DisplayName("RN-812: o autor perde a exclusão junto com a edição, ao fim da janela")
    void authorShouldLoseDeleteWithTheWindow() {
        CommentEditPolicy policy = policyAt(CREATED_AT.plus(Duration.ofHours(25)));
        signIn(AUTHOR_ID, Role.MEMBER);
        Comment comment = userComment();

        assertThat(policy.canDelete(comment)).isFalse();
        assertThatThrownBy(() -> policy.assertDeletable(comment))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2706");
    }

    @Test
    @DisplayName(
            "RN-815/INV-CMT-03: comentário de sistema é imutável e inexcluível por qualquer papel")
    void systemCommentShouldBeImmutable() {
        CommentEditPolicy policy = policyAt(CREATED_AT.plusSeconds(1));
        signIn(AUTHOR_ID, Role.OWNER);
        Comment comment = systemComment();

        assertThat(policy.canEdit(comment)).isFalse();
        assertThat(policy.canDelete(comment)).isFalse();
        assertThatThrownBy(() -> policy.assertEditable(comment))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2707");
        assertThatThrownBy(() -> policy.assertDeletable(comment))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(failure -> ((BusinessRuleException) failure).getErrorCode().getCode())
                .isEqualTo("DEVTIME-2707");
    }

    @Test
    @DisplayName("MEMBER não modera: sem COMMENT_DELETE_ANY, comentário de terceiro é intocável")
    void memberShouldNotModerate() {
        CommentEditPolicy policy = policyAt(CREATED_AT.plusSeconds(60));
        signIn(OTHER_ID, Role.MEMBER);
        Comment comment = userComment();

        assertThat(RolePermissions.of(Role.MEMBER)).doesNotContain(Permission.COMMENT_DELETE_ANY);
        assertThat(policy.canDelete(comment)).isFalse();
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    private CommentEditPolicy policyAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new CommentEditPolicy(tenantContext, new TenantClock(clock, tenantContext));
    }

    private void signIn(UUID userId, Role role) {
        tenantContext.set(
                new TenantSession(
                        userId,
                        TENANT_ID,
                        UUID.randomUUID(),
                        role,
                        RolePermissions.of(role),
                        "America/Sao_Paulo"));
    }

    private Comment userComment() {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setAuthorId(AUTHOR_ID);
        comment.setBody("Comentário do autor");
        comment.setCreatedAt(CREATED_AT);
        comment.setSystem(false);
        return comment;
    }

    private Comment systemComment() {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setBody("Situação alterada de BACKLOG para IN_PROGRESS.");
        comment.setCreatedAt(CREATED_AT);
        comment.setSystem(true);
        comment.setSystemTrigger(SystemCommentTrigger.STATUS_CHANGED);
        return comment;
    }
}
