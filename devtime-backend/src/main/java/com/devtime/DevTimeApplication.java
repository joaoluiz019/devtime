package com.devtime;

import com.devtime.shared.config.DevTimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Ponto de entrada da aplicação DevTime.
 *
 * <p>O agendamento de jobs não é habilitado aqui: JB-07 exige que jobs executem apenas no perfil
 * {@code scheduler}, o que é decidido em {@code SchedulingConfig}.
 */
@SpringBootApplication
@EnableConfigurationProperties(DevTimeProperties.class)
public class DevTimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevTimeApplication.class, args);
    }
}
