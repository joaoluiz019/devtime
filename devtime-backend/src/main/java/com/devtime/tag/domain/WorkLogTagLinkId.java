package com.devtime.tag.domain;

import java.io.Serializable;
import java.util.UUID;

/**
 * Chave primária composta de {@link WorkLogTagLink} (índice {@code pk_work_log_tags}).
 *
 * @param workLogId registro de horas rotulado
 * @param tagId etiqueta aplicada
 */
public record WorkLogTagLinkId(UUID workLogId, UUID tagId) implements Serializable {}
