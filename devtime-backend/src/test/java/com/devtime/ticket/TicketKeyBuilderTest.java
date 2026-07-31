package com.devtime.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.ticket.TicketKeyBuilder.TicketKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tabela normativa de chaves (RN-302, §6.2 de specs/007-tickets).
 *
 * <p>Suíte escrita <b>antes</b> do construtor (T-007-04). A chave é a única referência do ticket
 * que circula fora do sistema — em e-mail, reunião e nota fiscal —, e um erro nela é comunicado ao
 * cliente antes de ser percebido.
 */
class TicketKeyBuilderTest {

    private final TicketKeyBuilder builder = new TicketKeyBuilder();

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
        "CT-0001, 1, CT-0001-1",
        "CT-0001, 42, CT-0001-42",
        "CT-0002, 1, CT-0002-1",
        "CT-0010, 137, CT-0010-137"
    })
    @DisplayName("RN-302: reproduz a tabela normativa de chaves da §6.2 da spec 007")
    void shouldReproduceNormativeKeyTable(String contractCode, int number, String expected) {
        assertThat(builder.build(contractCode, number)).isEqualTo(expected);
    }

    @Test
    @DisplayName("RN-302: a sequência é por contrato — dois contratos possuem, ambos, o número 1")
    void sequenceShouldBePerContract() {
        assertThat(builder.build("CT-0001", 1)).isNotEqualTo(builder.build("CT-0002", 1));
    }

    @ParameterizedTest(name = "\"{0}\" decompõe em ({1}, {2})")
    @CsvSource({"CT-0001-42, CT-0001, 42", "CT-0010-137, CT-0010, 137", "CT-0001-1, CT-0001, 1"})
    @DisplayName("FA-15: a decomposição corta no último hífen, porque o código do contrato tem um")
    void shouldParseAtLastHyphen(String key, String contractCode, int number) {
        assertThat(builder.parse(key)).contains(new TicketKey(contractCode, number));
    }

    @Test
    @DisplayName("RN-302: build e parse são inversos um do outro")
    void buildAndParseShouldRoundTrip() {
        String key = builder.build("CT-0007", 314);
        assertThat(builder.parse(key)).contains(new TicketKey("CT-0007", 314));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CT-0001-", "-42", "CT0001", "CT-0001-abc", "CT-0001-0"})
    @DisplayName("CX-19: chave malformada é irresolvível, produzindo o mesmo 404 de chave alheia")
    void shouldRejectMalformedKeys(String key) {
        assertThat(builder.parse(key)).isEmpty();
    }

    @Test
    @DisplayName(
            "CX-19: chave estruturalmente válida com contrato inexistente decompõe e falha depois")
    void structurallyValidKeyShouldParseEvenIfContractDoesNotExist() {
        // O construtor não conhece contratos: sua responsabilidade termina na decomposição. Uma
        // chave bem formada de um contrato que não existe produz 404 na resolução, não aqui —
        // rejeitá-la neste ponto exigiria um formato de código que INV-CTR-01 não impõe.
        assertThat(builder.parse("CT-0001--1")).contains(new TicketKey("CT-0001-", 1));
    }

    @Test
    @DisplayName("CX-19: chave nula é irresolvível e não lança")
    void shouldRejectNullKey() {
        assertThat(builder.parse(null)).isEmpty();
    }
}
