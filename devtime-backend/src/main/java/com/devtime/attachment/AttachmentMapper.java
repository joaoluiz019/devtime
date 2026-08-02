package com.devtime.attachment;

import com.devtime.attachment.domain.Attachment;
import com.devtime.attachment.dto.AttachmentResponses.AttachmentResponse;
import com.devtime.user.dto.UserSummary;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Conversão de {@link Attachment} para DTO (ADR-014, BR-104).
 *
 * <p>Omite {@code storageKey} e {@code checksumSha256} (CP-07) — a omissão é o ponto: uma resposta
 * montada por reflexão a partir da entidade os incluiria por padrão, e um campo sensível exposto
 * por omissão é o modo de falha que BR-108 existe para impedir.
 *
 * <p>BR-105: nenhum acesso a banco. Quem envia e as permissões calculadas chegam <b>resolvidos</b>
 * pelo serviço, em consulta em lote — resolvê-los aqui produziria N+1 numa lista de 20 anexos.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AttachmentMapper {

    /**
     * Monta a resposta.
     *
     * <p>Escrito como {@code default} pelo mesmo motivo de {@code CommentMapper}: nenhum dos
     * parâmetros derivados vem da entidade — quem enviou pertence a {@code 002-users}, e {@code
     * canDownload}/{@code canDelete} são decisões sobre o requisitante corrente.
     */
    default AttachmentResponse toResponse(
            Attachment attachment, UserSummary uploadedBy, boolean canDelete) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicketId(),
                attachment.getCommentId(),
                attachment.getFileName(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getScanStatus(),
                uploadedBy,
                attachment.getCreatedAt(),
                // RN-803 decidida no servidor (§23).
                attachment.isDownloadable(),
                canDelete);
    }
}
