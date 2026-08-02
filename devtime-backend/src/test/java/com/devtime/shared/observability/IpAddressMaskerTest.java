package com.devtime.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Máscara de IP na listagem de sessões (§9.2 de security.md, §5.11 de authentication.md). */
class IpAddressMaskerTest {

    @Test
    @DisplayName("§5.11: IPv4 preserva apenas o primeiro e o último octeto")
    void shouldMaskMiddleOctetsOfIpv4() {
        assertThat(IpAddressMasker.mask("200.152.34.42")).isEqualTo("200.***.***.42");
    }

    @Test
    @DisplayName("§9.2: IPv6 aplica o mesmo critério aos grupos")
    void shouldMaskMiddleGroupsOfIpv6() {
        assertThat(IpAddressMasker.mask("2001:db8:85a3:0:0:8a2e:370:7334"))
                .startsWith("2001:")
                .endsWith(":7334")
                .contains("***");
    }

    @Test
    @DisplayName("Valor fora do formato esperado é totalmente ocultado")
    void unknownFormatMustBeFullyRedacted() {
        assertThat(IpAddressMasker.mask("desconhecido"))
                .as(
                        "mascarar parcialmente algo cuja estrutura não se conhece pode preservar"
                                + " justamente a parte identificadora")
                .isEqualTo("***");
    }

    @Test
    @DisplayName("Ausência de IP permanece ausente")
    void nullMustRemainNull() {
        assertThat(IpAddressMasker.mask(null)).isNull();
        assertThat(IpAddressMasker.mask("  ")).isNull();
    }
}
