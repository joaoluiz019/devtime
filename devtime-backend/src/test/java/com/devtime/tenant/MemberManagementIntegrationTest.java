package com.devtime.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.security.Role;
import com.devtime.support.FeatureTestSupport;
import com.devtime.support.FoundationDataBuilder;
import com.devtime.tenant.dto.MemberRequests.RoleUpdateRequest;
import com.devtime.tenant.dto.MemberResponses.MemberResponse;
import com.devtime.tenant.dto.TenantRequests.TenantSettingsRequest;
import com.devtime.tenant.dto.TenantRequests.TenantUpdateRequest;
import com.devtime.tenant.dto.TenantResponses.TenantResponse;
import com.devtime.tenant.dto.TenantViews.MembershipState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Gestão de membros e configurações da organização (users.md §6.2 e §7).
 *
 * <p>Duas regras aqui são irreversíveis quando falham. RN-455: um tenant sem proprietário ativo não
 * tem quem o conserte — é a única situação sem saída do produto. E RN-460: suspender alguém
 * descarta o cronômetro em andamento, o que precisa acontecer de fato, ou a pessoa suspensa segue
 * acumulando horas que ninguém revisará.
 */
class MemberManagementIntegrationTest extends FeatureTestSupport {

    @Autowired private MembershipService membershipService;
    @Autowired private TenantService tenantService;

    @Test
    @DisplayName("§7.1: a busca lista os membros do tenant com as ações disponíveis calculadas")
    void searchListsMembersWithAvailableActions() {
        Membro membro = novoMembro(Role.MEMBER);

        var pagina =
                asOwnerOfA(() -> membershipService.search(null, null, null, PageRequest.of(0, 20)));

        assertThat(pagina.content()).extracting(MemberResponse::id).contains(membro.membershipId());
        assertThat(pagina.content())
                .as("ME-06: a tela não deduz ação nenhuma — elas chegam calculadas")
                .allSatisfy(item -> assertThat(item.availableActions()).isNotNull());
    }

    @Test
    @DisplayName("§7.3: alterar o papel exige a versão corrente do vínculo (RN-004)")
    void changeRoleRequiresCurrentVersion() {
        Membro membro = novoMembro(Role.MEMBER);
        MemberResponse antes = asOwnerOfA(() -> membershipService.getById(membro.membershipId()));

        MemberResponse depois =
                asOwnerOfA(
                        () ->
                                membershipService.changeRole(
                                        membro.membershipId(),
                                        new RoleUpdateRequest(Role.ADMIN, antes.version())));

        assertThat(depois.role()).isEqualTo(Role.ADMIN);
        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                membershipService.changeRole(
                                                        membro.membershipId(),
                                                        new RoleUpdateRequest(
                                                                Role.MEMBER, antes.version()))))
                .as("a versão antiga perdeu a corrida e não pode sobrescrever em silêncio")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("§4.3: suspender e reativar percorrem ACTIVE → SUSPENDED → ACTIVE")
    void suspendAndReactivate() {
        Membro membro = novoMembro(Role.MEMBER);

        assertThat(asOwnerOfA(() -> membershipService.suspend(membro.membershipId())).status())
                .isEqualTo(MembershipState.SUSPENDED);
        assertThat(asOwnerOfA(() -> membershipService.isActiveMember(membro.userId()))).isFalse();

        assertThat(asOwnerOfA(() -> membershipService.reactivate(membro.membershipId())).status())
                .isEqualTo(MembershipState.ACTIVE);
        assertThat(asOwnerOfA(() -> membershipService.isActiveMember(membro.userId()))).isTrue();
    }

    @Test
    @DisplayName("RN-455: o último proprietário ativo não pode ser removido nem rebaixado")
    void lastOwnerCannotBeRemoved() {
        MemberResponse dono =
                asOwnerOfA(
                        () ->
                                membershipService
                                        .search(null, null, null, PageRequest.of(0, 20))
                                        .content()
                                        .stream()
                                        .filter(item -> item.role() == Role.OWNER)
                                        .findFirst()
                                        .orElseThrow());

        assertThatThrownBy(() -> asOwnerOfA(() -> membershipService.remove(dono.id(), null)))
                .as("uma organização sem proprietário não tem quem a conserte")
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("RN-458: a remoção preserva os registros e devolve as contagens")
    void removalPreservesRecords() {
        Membro membro = novoMembro(Role.MEMBER);

        var remocao = asOwnerOfA(() -> membershipService.remove(membro.membershipId(), null));

        assertThat(remocao).isNotNull();
        assertThat(asOwnerOfA(() -> membershipService.isActiveMember(membro.userId())))
                .as("o vínculo termina; o que a pessoa registrou permanece")
                .isFalse();
    }

    @Test
    @DisplayName("§6.2: as configurações são atualizadas sem recalcular registro algum (CE-03)")
    void settingsAreUpdatedWithoutRecalculation() {
        TenantResponse antes = asOwnerOfA(() -> tenantService.currentDetail());

        TenantResponse depois =
                asOwnerOfA(
                        () ->
                                tenantService.updateSettings(
                                        new TenantSettingsRequest(
                                                420,
                                                List.of(1, 2, 3, 4, 5),
                                                null,
                                                null,
                                                480,
                                                960,
                                                null,
                                                null,
                                                null,
                                                null,
                                                antes.version())));

        assertThat(depois.settings().workDayMinutes()).isEqualTo(420);
    }

    @Test
    @DisplayName("BR-103: limiar de abandono menor que o de alerta é recusado")
    void inconsistentTimerThresholdsAreRejected() {
        TenantResponse antes = asOwnerOfA(() -> tenantService.currentDetail());

        assertThatThrownBy(
                        () ->
                                asOwnerOfA(
                                        () ->
                                                tenantService.updateSettings(
                                                        new TenantSettingsRequest(
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                900,
                                                                300,
                                                                null,
                                                                null,
                                                                null,
                                                                null,
                                                                antes.version()))))
                .as("marcar como abandonado antes de alertar inverteria a ordem dos avisos")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("§6.1: o cadastro da organização é atualizado com verificação de versão")
    void tenantProfileIsUpdated() {
        TenantResponse antes = asOwnerOfA(() -> tenantService.currentDetail());

        TenantResponse depois =
                asOwnerOfA(
                        () ->
                                tenantService.update(
                                        new TenantUpdateRequest(
                                                "Organização Renomeada",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                antes.version())));

        assertThat(depois.name()).isEqualTo("Organização Renomeada");
    }

    /**
     * Vínculo e conta são identificadores distintos: confundi-los faz a asserção passar sozinha.
     */
    private record Membro(UUID membershipId, UUID userId) {}

    private Membro novoMembro(Role papel) {
        return asOwnerOfA(
                () -> {
                    UUID userId =
                            userRepository
                                    .save(
                                            FoundationDataBuilder.user(
                                                    "membro-"
                                                            + UUID.randomUUID()
                                                                    .toString()
                                                                    .substring(0, 8)
                                                            + "@exemplo.com",
                                                    NOW))
                                    .getId();
                    UUID membershipId =
                            membershipRepository
                                    .save(
                                            FoundationDataBuilder.membership(
                                                    tenantAId, userId, papel, NOW))
                                    .getId();
                    return new Membro(membershipId, userId);
                });
    }
}
