package com.devtime.client;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Cor determinística derivada do nome do cliente (entities.md §6.4, spec 003 §22.3).
 *
 * <p>A cor identifica o cliente em gráficos e cartões. Ser <b>determinística</b> importa mais que
 * ser bonita: o mesmo cliente precisa aparecer sempre com a mesma cor entre sessões, dispositivos e
 * relatórios exportados — uma cor aleatória tornaria a leitura comparativa impossível.
 *
 * <p>A paleta é fixa e vem do design system; derivar um valor hexadecimal livre do hash produziria
 * cores com contraste imprevisível sobre o fundo, quebrando o critério de acessibilidade.
 */
@Component
public class ClientColorGenerator {

    private static final List<String> PALETTE =
            List.of(
                    "#6366F1", "#EF4444", "#F59E0B", "#10B981", "#8B5CF6", "#06B6D4", "#64748B",
                    "#0EA5E9", "#EC4899", "#84CC16");

    public String generate(String clientName) {
        String normalized = clientName == null ? "" : clientName.trim().toLowerCase(Locale.ROOT);
        int hash = 0;
        for (byte character : normalized.getBytes(StandardCharsets.UTF_8)) {
            // Hash polinomial simples: estável entre JVMs, diferente de String.hashCode(), cuja
            // estabilidade entre versões não é contratual.
            hash = 31 * hash + character;
        }
        return PALETTE.get(Math.floorMod(hash, PALETTE.size()));
    }
}
