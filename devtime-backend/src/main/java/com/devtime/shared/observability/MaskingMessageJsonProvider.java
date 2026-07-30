package com.devtime.shared.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import net.logstash.logback.composite.loggingevent.MessageJsonProvider;

/**
 * Provider do campo {@code message} do log JSON, com máscara aplicada (ART-084).
 *
 * <p>Atua no appender, e não no ponto de chamada, para que a máscara valha para <b>todo</b> log —
 * incluindo mensagens de bibliotecas de terceiros, que não conhecem as regras de security.md §9.2 e
 * são justamente onde um dado sensível aparece sem que ninguém tenha escrito o log.
 */
public class MaskingMessageJsonProvider extends MessageJsonProvider {

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        generator.writeStringField(
                getFieldName(), SensitiveDataMasker.mask(event.getFormattedMessage()));
    }
}
