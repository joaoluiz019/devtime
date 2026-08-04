package com.devtime.report.render;

import java.util.List;

/**
 * Colunas do detalhamento, compartilhadas por CSV e XLSX (§9.2 e §9.3 de reports.md).
 *
 * <p>§9.3 define o CSV como "uma única tabela <b>equivalente à aba Detalhamento</b>". Uma segunda
 * lista de colunas para o CSV divergiria da primeira na primeira alteração, e o cliente que abrisse
 * os dois arquivos veria conjuntos diferentes de colunas para o mesmo relatório.
 *
 * <p>Os rótulos estão em português porque são <b>conteúdo do arquivo entregue ao cliente final</b>,
 * não texto de interface: NM-03 reserva o inglês ao código e o português ao que o usuário lê.
 */
public final class ReportColumns {

    private ReportColumns() {}

    /** As 13 colunas sempre presentes, na ordem de §9.2. */
    public static final List<String> BASE =
            List.of(
                    "Data",
                    "Dia da semana",
                    "Início",
                    "Fim",
                    "Ticket",
                    "Título do ticket",
                    "Categoria",
                    "Usuário",
                    "Descrição",
                    "Duração",
                    "Horas decimais",
                    "Faturável",
                    "Tags");

    /** Coluna 14 de §9.2 — presente apenas com permissão financeira (CP-08, SG-07). */
    public static final String VALUE = "Valor";

    /** Índice, base zero, da coluna de horas decimais — a somável de XLS-02/XLS-03. */
    public static final int DECIMAL_HOURS_INDEX = 10;

    public static List<String> headers(boolean includeValue) {
        if (!includeValue) {
            return BASE;
        }
        List<String> headers = new java.util.ArrayList<>(BASE);
        headers.add(VALUE);
        return List.copyOf(headers);
    }
}
