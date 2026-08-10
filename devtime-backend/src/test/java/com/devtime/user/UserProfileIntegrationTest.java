package com.devtime.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.support.FeatureTestSupport;
import com.devtime.user.dto.UserProfileRequests.UserPreferencesRequest;
import com.devtime.user.dto.UserProfileRequests.UserProfileUpdateRequest;
import com.devtime.user.dto.UserProfileResponses.UserProfileResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Perfil e preferências da conta (users.md §5, §6.2.1).
 *
 * <p>As preferências são <b>mescladas</b>, não substituídas: a atualização parcial precisa
 * preservar o que não veio no pedido — inclusive chaves que esta versão do sistema não conhece. Sem
 * isso, uma tela que edita apenas o tema apagaria as preferências de notificação de quem a usa, e o
 * efeito só apareceria quando os alertas parassem de chegar.
 */
class UserProfileIntegrationTest extends FeatureTestSupport {

    @Autowired private UserProfileService profileService;

    @Test
    @DisplayName("§5.1: o perfil corrente é o do usuário autenticado, com os padrões aplicados")
    void currentProfileCarriesDefaults() {
        UserProfileResponse perfil = asOwnerOfA(() -> profileService.current());

        assertThat(perfil.id()).isEqualTo(userAId);
        assertThat(perfil.preferences())
                .as("§6.2.1: os padrões são aplicados na leitura, não gravados na criação")
                .isNotNull();
    }

    @Test
    @DisplayName("§5.1: a atualização de perfil altera apenas os campos enviados")
    void updateProfileChangesOnlyWhatWasSent() {
        UserProfileResponse antes = asOwnerOfA(() -> profileService.current());

        UserProfileResponse depois =
                asOwnerOfA(
                        () ->
                                profileService.updateProfile(
                                        new UserProfileUpdateRequest(
                                                "Nome Alterado", null, null, null)));

        assertThat(depois.fullName()).isEqualTo("Nome Alterado");
        assertThat(depois.email())
                .as("o e-mail não é alterável por este caminho")
                .isEqualTo(antes.email());
    }

    @Test
    @DisplayName("BR-103 / §5.2: as preferências são mescladas, e o que não veio permanece")
    void preferencesAreMergedNotReplaced() {
        asOwnerOfA(
                () ->
                        profileService.updatePreferences(
                                new UserPreferencesRequest(
                                        "DARK", null, null, false, List.of(), null)));

        UserProfileResponse depois =
                asOwnerOfA(
                        () ->
                                profileService.updatePreferences(
                                        new UserPreferencesRequest(
                                                null, null, null, null, null, true)));

        assertThat(depois.preferences().theme())
                .as("editar o lembrete de cronômetro não pode apagar o tema escolhido")
                .isEqualTo("DARK");
        assertThat(depois.preferences().timerReminderEnabled()).isTrue();
    }

    @Test
    @DisplayName("BR-103: a lista fechada de §6.2.1 é verificada pelo próprio pedido")
    void unsupportedPreferenceValueIsRejected() {
        // A verificação vive no record, por `@AssertTrue`, e é aplicada por Bean Validation na
        // fronteira HTTP. Chamar o serviço direto não a exercita — afirmar aqui que o serviço lança
        // criaria um teste que passa por engano no dia em que a anotação for removida.
        assertThat(
                        new UserPreferencesRequest("ROXO_NEON", null, null, null, null, null)
                                .isThemeSupported())
                .as("a lista de temas é fechada; aceitar valor livre quebraria a interface")
                .isFalse();
        assertThat(
                        new UserPreferencesRequest("DARK", null, null, null, null, null)
                                .isThemeSupported())
                .isTrue();
    }

    @Test
    @DisplayName("§5.3: remover avatar de quem não tem é operação sem efeito, não erro")
    void removingAbsentAvatarIsNoOp() {
        asOwnerOfA(
                () -> {
                    profileService.removeAvatar();
                    return null;
                });

        assertThat(asOwnerOfA(() -> profileService.current()).avatarUrl()).isNull();
    }
}
