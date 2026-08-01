package com.devtime.shared.mail;

import com.devtime.shared.config.DevTimeProperties;
import com.resend.Resend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Cliente do Resend como bean.
 *
 * <p>A instância existe apenas quando o provedor está selecionado: criá-la sempre exigiria a chave
 * de API em ambientes que não enviam e-mail algum. Sendo bean, o adapter recebe o cliente por
 * construtor e pode ser testado sem rede (BR-203).
 */
@Configuration
@Profile({"staging", "prod"})
@ConditionalOnProperty(name = "devtime.mail.provider", havingValue = "resend")
public class ResendClientConfig {

    @Bean
    public Resend resendClient(DevTimeProperties properties) {
        // A validação de MailProps garante que a chave não é nula aqui (CF-03).
        return new Resend(properties.mail().resendApiKey());
    }
}
