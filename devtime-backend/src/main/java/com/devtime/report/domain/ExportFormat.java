package com.devtime.report.domain;

/**
 * Formatos de exportação (§8.1 de reports.md).
 *
 * <p>O {@code contentType} e a extensão vivem aqui, e não no renderer, porque o nome do arquivo é
 * composto por {@code ExportService} antes de qualquer renderer ser escolhido — a decisão síncrono
 * × assíncrono (RN-706) acontece antes da renderização.
 */
public enum ExportFormat {
    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),

    /**
     * §9.3: UTF-8 <b>com BOM</b>. O {@code charset} declarado no tipo é o que os navegadores
     * respeitam; o BOM é o que faz o Excel em português abrir o arquivo com acentuação correta, e
     * os dois são necessários porque atendem a leitores diferentes.
     */
    CSV("text/csv; charset=UTF-8", "csv");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
