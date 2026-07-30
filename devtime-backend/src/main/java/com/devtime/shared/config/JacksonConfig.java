package com.devtime.shared.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Serialização JSON (ART-033, ART-075). */
@Configuration
public class JacksonConfig {

    /**
     * ART-033: instantes trafegam em ISO-8601 com offset, nunca como epoch numérico.
     *
     * <p>Serializar como número perderia o offset e obrigaria cada cliente a assumir um fuso — o
     * erro exato que ART-030 a ART-033 existem para impedir.
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder ->
                builder.modules(new JavaTimeModule())
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
