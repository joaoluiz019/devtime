package com.devtime.tenant.dto;

import com.devtime.shared.persistence.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Entradas de organização (users.md §6). */
public final class TenantRequests {

    private TenantRequests() {}

    /**
     * Atualização parcial dos dados da organização (users.md §6.1).
     *
     * <p>O {@code slug} está <b>ausente</b> do record, e não apenas ignorado no serviço: RN-011 o
     * torna imutável (🔒 em entities.md §6.1), e um campo presente no contrato convidaria o cliente
     * a enviá-lo para depois receber {@code DEVTIME-2003}.
     *
     * @param version RN-004: precisa corresponder ao estado atual
     */
    @Schema(name = "TenantUpdateRequest")
    public record TenantUpdateRequest(
            @Size(min = 2, max = 120) String name,
            @Size(max = 200) String legalName,
            @Pattern(regexp = "\\d{0,20}", message = "Documento inválido") String documentNumber,
            @Email @Size(max = 255) String email,
            @Size(max = 20) String phone,
            @Size(max = 60) String timezone,
            @Size(max = 10) String locale,
            @Pattern(regexp = "[A-Z]{3}", message = "Moeda inválida") String currency,
            @Size(max = 500) String logoUrl,
            Address address,
            @NotNull Long version) {}

    /**
     * Atualização parcial das 10 chaves operacionais (users.md §6.2, entities.md §6.1.1).
     *
     * <p>BR-103: a validação cruzada entre os dois limiares de cronômetro é um {@code @AssertTrue}
     * no próprio record — ela não depende de estado persistido quando ambos vêm na requisição. As
     * faixas individuais e o caso em que apenas um dos dois é enviado ficam no {@code
     * TenantSettingsValidator}, que enxerga o valor efetivo.
     *
     * @param version RN-004
     */
    @Schema(name = "TenantSettingsRequest")
    public record TenantSettingsRequest(
            Integer workDayMinutes,
            List<Integer> workDays,
            String defaultRolloverPolicy,
            String defaultOveragePolicy,
            Integer timerLongRunningMinutes,
            Integer timerAutoAbandonMinutes,
            Boolean allowFutureWorkLogs,
            Integer retroactiveLimitDays,
            Integer roundingMinutes,
            List<Integer> notificationThresholds,
            @NotNull Long version) {

        @AssertTrue(message = "O limiar de abandono deve ser maior que o de alerta")
        @Schema(hidden = true)
        public boolean isTimerThresholdOrderValid() {
            if (timerLongRunningMinutes == null || timerAutoAbandonMinutes == null) {
                return true; // Comparação com o valor efetivo fica no validador de negócio.
            }
            return timerAutoAbandonMinutes > timerLongRunningMinutes;
        }
    }

    /**
     * Cancelamento da organização (users.md §6.3).
     *
     * <p>SG-04: exige senha <b>e</b> a digitação da confirmação. Uma sessão sequestrada consegue a
     * primeira; a segunda obriga uma ação deliberada de quem está diante da tela.
     *
     * @param confirmation precisa ser exatamente {@code CANCELAR}
     */
    @Schema(name = "TenantCancelRequest")
    public record TenantCancelRequest(
            @NotBlank String password,
            @Size(max = 500) String reason,
            @NotBlank String confirmation) {

        /** Palavra exigida por users.md §6.3. */
        public static final String EXPECTED_CONFIRMATION = "CANCELAR";
    }
}
