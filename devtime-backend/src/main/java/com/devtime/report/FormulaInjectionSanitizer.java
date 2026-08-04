package com.devtime.report;

import org.springframework.stereotype.Component;

/**
 * Neutraliza células que uma planilha interpretaria como fórmula (SG-05, CE-17, CP-17).
 *
 * <p><b>A vítima não é o usuário do sistema — é o cliente dele.</b> Uma descrição de work log com
 * {@code =SUM(...)} ou {@code =cmd|'/c calc'!A1} é texto inofensivo enquanto vive no banco; ela
 * vira execução de código quando o arquivo exportado é aberto no Excel de um terceiro que nunca
 * teve conta no DevTime. É por isso que a neutralização vale para CSV <b>e</b> XLSX, e não só para
 * o formato "de texto": OB-05 registra que a mitigação não está em {@code docs/} e é uma decisão
 * desta spec.
 *
 * <p>O prefixo é uma aspa simples, o mecanismo que Excel, LibreOffice e Google Sheets reconhecem
 * como "trate como texto". A alternativa de <b>remover</b> o caractere inicial foi rejeitada: ela
 * altera o dado do usuário silenciosamente, e uma descrição legítima começando com {@code -} —
 * {@code -2h de retrabalho} — perderia sentido.
 */
@Component
public class FormulaInjectionSanitizer {

    /**
     * Os quatro caracteres de SG-05.
     *
     * <p>{@code +} e {@code -} entram junto com {@code =} e {@code @} porque as três planilhas
     * aceitam os quatro como início de fórmula, ainda que só {@code =} seja o documentado.
     */
    private static final String FORMULA_STARTERS = "=+-@";

    /**
     * Prefixa a célula quando ela começa com caractere de fórmula.
     *
     * <p>Também neutraliza quando o caractere vem depois de espaço, tabulação ou quebra de linha
     * iniciais: as planilhas descartam o espaço em branco à esquerda antes de decidir se a célula é
     * fórmula, então {@code " =SUM(A1)"} executa igual.
     *
     * @return o próprio texto quando não há o que neutralizar; nulo permanece nulo, porque célula
     *     ausente e célula vazia são coisas diferentes no XLSX
     */
    public String sanitize(String cell) {
        if (cell == null || cell.isEmpty()) {
            return cell;
        }
        String trimmed = cell.stripLeading();
        if (trimmed.isEmpty() || FORMULA_STARTERS.indexOf(trimmed.charAt(0)) < 0) {
            return cell;
        }
        // A aspa é prefixada ao texto original, com o espaço em branco preservado: o conteúdo que o
        // usuário digitou continua íntegro, e apenas a interpretação muda.
        return "'" + cell;
    }
}
