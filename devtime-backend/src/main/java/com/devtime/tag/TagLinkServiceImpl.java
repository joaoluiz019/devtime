package com.devtime.tag;

import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tag.domain.Tag;
import com.devtime.tag.domain.TicketTagLink;
import com.devtime.tag.dto.TagRequests.TagLinkRequest;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vínculo de etiquetas com tickets (ver {@link TagLinkService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TagLinkServiceImpl implements TagLinkService {

    private final TicketTagRepository ticketTagRepository;
    private final TagRepository tagRepository;
    private final TagMapper mapper;
    private final TagLinkPolicy linkPolicy;
    private final TagUsageCounter usageCounter;
    private final TagService tagService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @Transactional
    public List<TagOptionResponse> replaceTicketTags(UUID ticketId, Collection<UUID> tagIds) {
        Set<UUID> desired = tagIds == null ? Set.of() : new LinkedHashSet<>(tagIds);
        linkPolicy.assertWithinLimit(ticketId, desired.size()); // RN-313, INV-TAG-01

        List<Tag> resolved = assertAllExistInTenant(desired);
        Set<UUID> current = Set.copyOf(ticketTagRepository.findTagIdsByTicketId(ticketId));

        Set<UUID> toRemove =
                current.stream().filter(id -> !desired.contains(id)).collect(Collectors.toSet());
        List<UUID> toAdd = desired.stream().filter(id -> !current.contains(id)).toList();

        if (!toRemove.isEmpty()) {
            ticketTagRepository.deleteByTicketIdAndTagIdIn(ticketId, toRemove);
            usageCounter.decrement(toRemove); // INV-TAG-04
        }
        if (!toAdd.isEmpty()) {
            // CX-10: o que já estava vinculado não é reinserido, então usageCount não infla.
            toAdd.forEach(tagId -> ticketTagRepository.save(newLink(ticketId, tagId)));
            usageCounter.increment(toAdd);
        }

        // §28 da spec: apenas identificadores em log; o nome da etiqueta é texto livre.
        if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
            log.info(
                    "etiquetas do ticket atualizadas ticketId={} adicionadas={} removidas={}",
                    ticketId,
                    toAdd.size(),
                    toRemove.size());
        }
        return orderedOptions(resolved, desired);
    }

    @Override
    @Transactional
    public List<UUID> resolveTagIds(TagLinkRequest request) {
        if (request == null) {
            return List.of();
        }
        Set<UUID> resolved = new LinkedHashSet<>();
        if (request.tagIds() != null) {
            resolved.addAll(request.tagIds());
        }
        if (request.tagNames() != null) {
            // E-07: criação implícita, idempotente por normalização (RN-506, RN-507).
            request.tagNames().forEach(name -> resolved.add(tagService.resolveOrCreate(name)));
        }
        return List.copyOf(resolved);
    }

    @Override
    public List<TagOptionResponse> findByTicketId(UUID ticketId) {
        List<UUID> tagIds = ticketTagRepository.findTagIdsByTicketId(ticketId);
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return mapper.toOptions(tagRepository.findAllByIdIn(tagIds));
    }

    @Override
    public Map<UUID, List<TagOptionResponse>> findByTicketIds(Collection<UUID> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Map.of();
        }
        List<TicketTagLink> links = ticketTagRepository.findByTicketIdIn(ticketIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<UUID, TagOptionResponse> byTagId =
                tagRepository
                        .findAllByIdIn(
                                links.stream()
                                        .map(TicketTagLink::getTagId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Tag::getId, mapper::toOption));

        Map<UUID, List<TagOptionResponse>> byTicket = new HashMap<>();
        for (TicketTagLink link : links) {
            TagOptionResponse option = byTagId.get(link.getTagId());
            if (option != null) {
                byTicket.computeIfAbsent(link.getTicketId(), key -> new ArrayList<>()).add(option);
            }
        }
        byTicket.values()
                .forEach(
                        options ->
                                options.sort(
                                        java.util.Comparator.comparing(TagOptionResponse::name)));
        return byTicket;
    }

    @Override
    @Transactional
    public void unlinkAllFromTicket(UUID ticketId) {
        List<UUID> tagIds = ticketTagRepository.findTagIdsByTicketId(ticketId);
        if (tagIds.isEmpty()) {
            return;
        }
        ticketTagRepository.deleteByTicketId(ticketId);
        usageCounter.decrement(tagIds); // INV-TAG-04
    }

    @Override
    public List<UUID> ticketIdsWithAllTags(Collection<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> distinct = Set.copyOf(tagIds);
        return ticketTagRepository.findTicketIdsWithAllTags(distinct, distinct.size());
    }

    /**
     * SG-02: uma etiqueta de outro tenant não pode ser vinculada a um ticket deste.
     *
     * <p>O filtro de tenant já restringe a consulta; o que esta verificação acrescenta é
     * transformar "sumiu do resultado" em {@code 404} explícito, em vez de vincular silenciosamente
     * menos etiquetas do que o usuário pediu.
     */
    private List<Tag> assertAllExistInTenant(Set<UUID> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        List<Tag> found = tagRepository.findAllByIdIn(tagIds);
        if (found.size() != tagIds.size()) {
            Set<UUID> existing = found.stream().map(Tag::getId).collect(Collectors.toSet());
            UUID missing =
                    tagIds.stream().filter(id -> !existing.contains(id)).findFirst().orElseThrow();
            throw EntityNotFoundException.of(Tag.class, missing); // INV-TAG-05
        }
        return found;
    }

    private List<TagOptionResponse> orderedOptions(List<Tag> tags, Set<UUID> desiredOrder) {
        Map<UUID, TagOptionResponse> byId =
                tags.stream().collect(Collectors.toMap(Tag::getId, mapper::toOption));
        return desiredOrder.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private TicketTagLink newLink(UUID ticketId, UUID tagId) {
        TicketTagLink link = new TicketTagLink();
        link.setTicketId(ticketId);
        link.setTagId(tagId);
        // O AuditListener não alcança esta entidade: ela não estende BaseEntity (ver
        // TicketTagLink).
        link.setTenantId(tenantContext.requireTenantId()); // ART-021: nunca da requisição
        link.setCreatedAt(clock.now());
        link.setCreatedBy(tenantContext.currentUserId().orElse(null));
        return link;
    }
}
