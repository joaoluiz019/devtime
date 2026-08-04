package com.devtime.tag;

import com.devtime.shared.maintenance.DenormalizationReconciler;
import com.devtime.tag.domain.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * CX-13 / INV-TAG-04: recalcula {@code usageCount} pela contagem real de vínculos (spec 006 §22.4).
 *
 * <p>{@code usageCount} é incrementado na transação do vínculo porque a listagem ordenada por uso é
 * o caminho quente da feature. O preço é este job: um incremento perdido deixaria a etiqueta
 * eternamente com a contagem errada, e o filtro {@code minUsage} passaria a mentir.
 *
 * <p>As duas tabelas de junção entram na conta — {@code ticket_tags} e {@code work_log_tags} —,
 * porque as duas incrementam o mesmo contador.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TagUsageReconciler implements DenormalizationReconciler {

    private final TagRepository repository;
    private final TicketTagRepository ticketTagRepository;
    private final WorkLogTagRepository workLogTagRepository;

    @Override
    public String target() {
        return "tag.usageCount";
    }

    @Override
    @Transactional
    public int reconcile() {
        Map<UUID, Long> real = countsByTag();
        List<Tag> tags = repository.findAll();

        int corrected = 0;
        for (Tag tag : tags) {
            int expected = real.getOrDefault(tag.getId(), 0L).intValue();
            if (tag.getUsageCount() != expected) {
                // §28: o nome da etiqueta nunca entra em log; o identificador basta.
                log.warn(
                        "usageCount divergente tagId={} persistido={} real={}",
                        tag.getId(),
                        tag.getUsageCount(),
                        expected);
                tag.setUsageCount(expected);
                corrected++;
            }
        }
        return corrected;
    }

    /** Uma consulta por tabela de junção, nunca uma por etiqueta (CP-12). */
    private Map<UUID, Long> countsByTag() {
        Map<UUID, Long> counts =
                ticketTagRepository.countLinksByTag().stream()
                        .collect(
                                Collectors.toMap(
                                        TagLinkCount::tagId, TagLinkCount::links, Long::sum));
        workLogTagRepository
                .countLinksByTag()
                .forEach(count -> counts.merge(count.tagId(), count.links(), Long::sum));
        return counts;
    }
}
