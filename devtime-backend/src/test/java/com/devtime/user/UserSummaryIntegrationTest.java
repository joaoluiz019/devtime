package com.devtime.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.FeatureTestSupport;
import com.devtime.user.dto.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resumos de usuário consumidos por {@code 007} e {@code 014} (users.md §6.5).
 *
 * <p>É a interface que preenche todo nome exibido no produto — responsável de ticket, autor de
 * comentário, menção. O caso que importa e não tinha teste é o do identificador
 * <b>desconhecido</b>: a menção a alguém removido precisa continuar renderizando, com o rótulo de
 * RN-458, em vez de derrubar a tela inteira em que aparece.
 */
class UserSummaryIntegrationTest extends FeatureTestSupport {

    @Autowired private UserService userService;

    @Test
    @DisplayName("§6.5: o resumo do usuário existente traz nome e identificador")
    void summaryOfExistingUser() {
        UserSummary resumo = asOwnerOfA(() -> userService.summaryOf(userAId));

        assertThat(resumo.id()).isEqualTo(userAId);
        assertThat(resumo.name()).isNotBlank();
    }

    @Test
    @DisplayName("RN-458: identificador desconhecido devolve o rótulo, e não uma exceção")
    void unknownUserFallsBackToRemovedLabel() {
        UUID inexistente = UUID.randomUUID();

        UserSummary resumo = asOwnerOfA(() -> userService.summaryOf(inexistente));

        assertThat(resumo.name())
                .as("uma menção a quem saiu não pode derrubar a linha do tempo do ticket")
                .isEqualTo(UserSummary.REMOVED_USER_NAME);
    }

    @Test
    @DisplayName(
            "QY-03: os resumos vêm em lote, e o identificador inexistente fica de fora do mapa")
    void summariesAreFetchedInBatch() {
        UUID inexistente = UUID.randomUUID();

        Map<UUID, UserSummary> resumos =
                asOwnerOfA(() -> userService.findSummaries(List.of(userAId, userBId, inexistente)));

        assertThat(resumos).containsKeys(userAId, userBId);
        assertThat(resumos)
                .as(
                        "o mapa não inventa entrada: a ausência é a informação, e quem exibe a"
                                + " resolve com o rótulo de RN-458 — como faz summaryOf")
                .doesNotContainKey(inexistente);
    }

    @Test
    @DisplayName("CE-08: coleção vazia não consulta o banco e devolve mapa vazio")
    void emptyCollectionReturnsEmptyMap() {
        assertThat(asOwnerOfA(() -> userService.findSummaries(List.of()))).isEmpty();
    }

    @Test
    @DisplayName("RN-813: a busca por identificador de menção ignora o que não existe")
    void handlesResolveOnlyExistingAccounts() {
        List<UserSummary> encontrados =
                asOwnerOfA(() -> userService.findByHandles(List.of("ninguem-com-este-handle")));

        assertThat(encontrados).as("mencionar alguém inexistente não cria destinatário").isEmpty();
    }
}
