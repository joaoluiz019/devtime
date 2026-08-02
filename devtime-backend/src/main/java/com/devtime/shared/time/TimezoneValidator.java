package com.devtime.shared.time;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * INV-TEN-03 / ART-032: o fuso é um identificador IANA resolvível.
 *
 * <p>Vive em {@code shared} porque duas features o exigem — o fuso do tenant (002, {@code PATCH
 * /tenant}) e a preferência pessoal do usuário (002, {@code PATCH /users/me}) — e porque a
 * consequência de aceitar um valor inválido é a mesma nos dois casos: {@code ZoneId.of} passa a
 * lançar em tempo de cálculo, dentro do {@code TenantClock}, muito longe de onde o valor entrou.
 *
 * <p>A verificação é contra {@link ZoneId#getAvailableZoneIds()}, e não contra uma lista própria: a
 * base tzdata muda com as decisões de fuso de cada país, e uma lista fixa envelheceria.
 */
@Component
public class TimezoneValidator {

    /**
     * @throws BusinessRuleException {@code DEVTIME-2000} quando o identificador não é resolvível
     */
    public void validate(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return; // Opcional em ambos os usos; a ausência significa "herda do tenant".
        }
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new InvalidTimezoneException(timezone);
        }
    }

    /** {@code DEVTIME-2000} / 400. */
    public static final class InvalidTimezoneException extends BusinessRuleException {
        private InvalidTimezoneException(String timezone) {
            super(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "timezone", "rejectedValue", timezone),
                    "Fuso horário inválido");
        }
    }
}
