package com.devtime.auth;

import com.devtime.auth.domain.AuthExceptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Política de senha (RN-451, PW-02, PW-03, T-001-17).
 *
 * <p>Requisitos: no mínimo 10 e no máximo 128 caracteres, com maiúscula, minúscula e dígito, fora
 * da lista de senhas comuns.
 *
 * <p>O comprimento mínimo é medido sobre os caracteres <b>não brancos</b>. Sem isso, {@code "Ab1"}
 * seguido de sete espaços satisfaria a contagem sem acrescentar entropia alguma (TS-001-01). O
 * máximo, ao contrário, considera a senha inteira: é um limite de entrada, não de complexidade.
 *
 * <p>A lista de senhas comuns vem de {@code classpath:security/common-passwords.txt}. Mantê-la em
 * recurso, e não em código, permite substituí-la por uma lista maior sem recompilar — a comparação
 * é por conjunto em memória, cujo custo não depende do tamanho do arquivo.
 */
@Component
@Slf4j
public class PasswordPolicyValidator {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 128;

    private static final String COMMON_PASSWORDS_RESOURCE = "security/common-passwords.txt";

    private final Set<String> commonPasswords;

    public PasswordPolicyValidator() {
        this.commonPasswords = loadCommonPasswords();
    }

    /**
     * @throws AuthExceptions.PasswordPolicyViolationException {@code DEVTIME-2451} quando a senha
     *     não atende a algum requisito. A exceção carrega os requisitos violados, nunca a senha
     *     informada (PW-08, AC-001-13)
     */
    public void validate(String password) {
        Set<String> violations = new HashSet<>();
        String candidate = password == null ? "" : password;
        String significant = candidate.replaceAll("\\s", "");

        if (significant.length() < MIN_LENGTH) {
            violations.add("MIN_LENGTH");
        }
        if (candidate.length() > MAX_LENGTH) {
            violations.add("MAX_LENGTH");
        }
        if (candidate.chars().noneMatch(Character::isUpperCase)) {
            violations.add("UPPERCASE");
        }
        if (candidate.chars().noneMatch(Character::isLowerCase)) {
            violations.add("LOWERCASE");
        }
        if (candidate.chars().noneMatch(Character::isDigit)) {
            violations.add("DIGIT");
        }
        if (commonPasswords.contains(candidate.toLowerCase(Locale.ROOT))) {
            violations.add("COMMON_PASSWORD");
        }

        if (!violations.isEmpty()) {
            throw AuthExceptions.passwordPolicyViolated(violations); // RN-451
        }
    }

    private Set<String> loadCommonPasswords() {
        ClassPathResource resource = new ClassPathResource(COMMON_PASSWORDS_RESOURCE);
        try (InputStream input = resource.getInputStream();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            Set<String> loaded = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.strip().toLowerCase(Locale.ROOT);
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    loaded.add(entry);
                }
            }
            log.info("lista de senhas comuns carregada quantidade={}", loaded.size());
            return Set.copyOf(loaded);
        } catch (IOException e) {
            // Falha na inicialização, não degradação silenciosa: aceitar senhas comuns por não
            // conseguir ler o arquivo enfraqueceria RN-451 sem que ninguém percebesse (CF-03).
            throw new IllegalStateException(
                    "Não foi possível carregar " + COMMON_PASSWORDS_RESOURCE, e);
        }
    }
}
