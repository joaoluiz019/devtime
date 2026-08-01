package com.devtime.tag;

import com.devtime.audit.AuditService;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tag.domain.Tag;
import com.devtime.tag.dto.TagRequests.TagCreateRequest;
import com.devtime.tag.dto.TagRequests.TagUpdateRequest;
import com.devtime.tag.dto.TagResponses.TagCleanupSuggestionResponse;
import com.devtime.tag.dto.TagResponses.TagDeleteResponse;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import com.devtime.tag.dto.TagResponses.TagResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do vocabulário de etiquetas (spec 006 §6).
 *
 * <p>A ordem de {@link #create} segue exatamente a §6.2 da spec: normalizar, validar o comprimento
 * do resultado, verificar a unicidade do <b>normalizado</b> e só então persistir. A ordem é
 * normativa (BR-062).
 *
 * <p>§28 da spec: <b>o nome da etiqueta nunca entra em log</b>. É o único campo de texto livre da
 * feature e nada impede que contenha dado pessoal; o identificador basta para depuração e a
 * auditoria — que é dado do tenant, não log de aplicação — preserva o nome.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TagServiceImpl implements TagService {

    /** users.md §9.2. */
    private static final String DEFAULT_COLOR = "#94A3B8";

    /** §20 da spec: limite do autocompletar aplicado no servidor. */
    private static final int AUTOCOMPLETE_LIMIT = 20;

    /** RN-508: estritamente <b>mais</b> de 90 dias; 89 dias não é sugerida (CX-14). */
    private static final Duration ORPHAN_THRESHOLD = Duration.ofDays(90);

    private final TagRepository repository;
    private final TicketTagRepository ticketTagRepository;
    private final WorkLogTagRepository workLogTagRepository;
    private final TagMapper mapper;
    private final TagNormalizer normalizer;
    private final TagNameValidator nameValidator;
    private final TagUniquenessValidator uniquenessValidator;
    private final AuditService auditService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    @Override
    @PreAuthorize("hasPermission(null, 'TAG_VIEW')")
    public List<TagResponse> search(String search, Integer minUsage) {
        return mapper.toResponses(repository.search(likePattern(search), minUsage));
    }

    @Override
    @PreAuthorize("hasPermission(null, 'TAG_VIEW')")
    public List<TagOptionResponse> autocomplete(String term) {
        return mapper.toOptions(
                repository.searchForAutocomplete(
                        normalizeSearch(term) == null ? "" : normalizeSearch(term),
                        PageRequest.of(0, AUTOCOMPLETE_LIMIT)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TAG_MANAGE')")
    public TagResponse create(TagCreateRequest request) {
        Tag saved = createNormalized(request.name(), request.color());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TAG_MANAGE')")
    public TagResponse update(UUID id, TagUpdateRequest request) {
        Tag tag = require(id);
        assertVersion(tag, request.version()); // RN-004

        String previousName = tag.getName();
        String previousColor = tag.getColor();

        if (request.name() != null) {
            String normalized = normalizer.normalize(request.name());
            nameValidator.assertValid(normalized); // RN-507
            // CX-09: renomear para o nome de outra etiqueta existente é conflito.
            uniquenessValidator.assertUnique(normalized, id); // RN-507
            tag.setName(normalized);
        }
        if (request.color() != null) {
            tag.setColor(request.color());
        }

        // §18 da spec: renomeação e alteração de cor são ações distintas na trilha.
        if (!previousName.equals(tag.getName())) {
            auditService.record(
                    "TAG_RENAMED",
                    "Tag",
                    id,
                    Map.of("name", previousName),
                    Map.of("name", tag.getName()));
        }
        if (!previousColor.equals(tag.getColor())) {
            auditService.record(
                    "TAG_UPDATED",
                    "Tag",
                    id,
                    Map.of("color", previousColor),
                    Map.of("color", tag.getColor()));
        }
        return mapper.toResponse(tag);
    }

    /**
     * Exclusão de etiqueta (§9.3 de users.md).
     *
     * <p>Não existe bloqueio por uso. Diferentemente da categoria (RN-505), excluir uma etiqueta em
     * uso não deixa registro órfão: o ticket continua íntegro e classificado por sua categoria
     * obrigatória. Exigir migração imporia atrito a uma operação de limpeza rotineira.
     *
     * <p>Os vínculos são removidos por {@code DELETE} em lote sobre {@code idx_ticket_tags_tag},
     * nunca carregando entidades — uma etiqueta usada em todo o tenant pode ter milhares (CX-12).
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'TAG_MANAGE')")
    public TagDeleteResponse delete(UUID id) {
        Tag tag = require(id);
        int usageCount = tag.getUsageCount();

        long unlinkedFromTickets = ticketTagRepository.deleteByTagId(id); // INV-TAG-05
        // Dívida CE-O-03 quitada: work_log_tags passou a existir com 008-worklogs (V028).
        long unlinkedFromWorkLogs = workLogTagRepository.deleteByTagId(id);

        repository.softDelete(
                id, clock.now(), tenantContext.currentUserId().orElse(null)); // RN-003

        auditService.record(
                "TAG_DELETED",
                "Tag",
                id,
                Map.of("name", tag.getName(), "usageCount", usageCount),
                Map.of(
                        "deletedAt", clock.now().toString(),
                        "unlinkedFromTickets", unlinkedFromTickets,
                        "unlinkedFromWorkLogs", unlinkedFromWorkLogs));
        log.info(
                "etiqueta excluída tagId={} unlinkedFromTickets={} unlinkedFromWorkLogs={}",
                id,
                unlinkedFromTickets,
                unlinkedFromWorkLogs);
        return new TagDeleteResponse(unlinkedFromTickets, unlinkedFromWorkLogs);
    }

    /**
     * RN-508: sugestões calculadas ao vivo sobre {@code idx_tags_tenant_orphan}.
     *
     * <p>A spec prevê um {@code TagCleanupSuggestionJob} que registraria o instante exato em que
     * {@code usageCount} chegou a zero. Esse instante exigiria uma coluna que {@code entities.md}
     * §6.11 não define, e inventá-la seria decidir modelo de dados — proibido por IA-01. A consulta
     * usa {@code updatedAt}, que é exatamente o que o índice documentado em §13.4 da spec sustenta.
     * A diferença é observável apenas quando a etiqueta é editada depois de ficar órfã, caso em que
     * a sugestão é adiada — erra para o lado conservador, nunca sugerindo indevidamente.
     */
    @Override
    @PreAuthorize("hasPermission(null, 'TAG_MANAGE')")
    public TagCleanupSuggestionResponse cleanupSuggestions() {
        List<Tag> orphans = repository.findOrphansOlderThan(clock.now().minus(ORPHAN_THRESHOLD));
        return new TagCleanupSuggestionResponse(
                mapper.toSuggestions(orphans), (int) ORPHAN_THRESHOLD.toDays());
    }

    /**
     * Criação implícita a partir do nome digitado (E-07).
     *
     * <p>Sem {@code @PreAuthorize} próprio: é chamado de dentro de operações de {@code 007} que já
     * verificaram {@code TICKET_UPDATE_*}, e {@code MEMBER} — o papel que rotula tickets — possui
     * {@code TAG_MANAGE} (§16 da spec). Exigir a permissão aqui seria redundante; omiti-la de uma
     * rota HTTP não seria, e por isso {@link #create} continua declarando-a.
     */
    @Override
    @Transactional
    public UUID resolveOrCreate(String rawName) {
        String normalized = normalizer.normalize(rawName);
        nameValidator.assertValid(normalized); // RN-507
        return repository
                .findByNormalizedName(normalized)
                .map(Tag::getId)
                // FA-01: quando já existe, o vínculo reaproveita a etiqueta em vez de falhar.
                .orElseGet(() -> createNormalized(rawName, null).getId());
    }

    private Tag createNormalized(String rawName, String color) {
        String normalized = normalizer.normalize(rawName); // §6.2 passo 3 — RN-506
        nameValidator.assertValid(normalized); // §6.2 passo 4 — RN-507
        uniquenessValidator.assertUnique(normalized, null); // §6.2 passo 5 — RN-507

        Tag tag = new Tag();
        tag.setName(normalized);
        tag.setColor(color == null ? DEFAULT_COLOR : color);
        tag.setUsageCount(0); // §6.2 passo 6 — nunca vem da requisição
        Tag saved = repository.save(tag);

        // §18 da spec: a auditoria registra o nome bruto digitado além do normalizado. Quando o
        // usuário afirmar ter criado "Code Review" e encontrar "code-review", é o que explica.
        Map<String, Object> afterState = new HashMap<>();
        afterState.put("name", saved.getName());
        afterState.put("rawName", rawName);
        afterState.put("color", saved.getColor());
        auditService.record("TAG_CREATED", "Tag", saved.getId(), Map.of(), afterState);

        log.info("etiqueta criada tagId={}", saved.getId());
        return saved;
    }

    private Tag require(UUID id) {
        // ART-024: inexistente e de outro tenant produzem a mesma resposta.
        return repository.findById(id).orElseThrow(() -> EntityNotFoundException.of(Tag.class, id));
    }

    private void assertVersion(Tag tag, long expected) {
        if (tag.getVersion() != null && tag.getVersion() != expected) {
            throw BusinessRuleException.versionConflict("Tag", expected); // RN-004
        }
    }

    /**
     * O termo de busca passa pela mesma normalização do nome.
     *
     * <p>Sem isso, procurar por {@code "Code Review"} não encontraria {@code code-review} — o
     * usuário digita como fala, mas o dado está normalizado (§23 da spec, {@code TagFilter}).
     */
    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return normalizer.normalize(search);
    }

    /**
     * Padrão de {@code LIKE} já montado; nulo quando não há termo, para o filtro não se aplicar.
     */
    private String likePattern(String search) {
        String normalized = normalizeSearch(search);
        return normalized == null ? null : "%" + normalized + "%";
    }
}
