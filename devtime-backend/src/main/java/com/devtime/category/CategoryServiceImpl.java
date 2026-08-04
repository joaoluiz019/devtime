package com.devtime.category;

import com.devtime.audit.AuditService;
import com.devtime.category.domain.Category;
import com.devtime.category.domain.CategoryExceptions;
import com.devtime.category.domain.DefaultCategoryCatalog;
import com.devtime.category.domain.DefaultCategoryCatalog.DefaultCategory;
import com.devtime.category.dto.CategoryRequests.CategoryCreateRequest;
import com.devtime.category.dto.CategoryRequests.CategoryReorderRequest;
import com.devtime.category.dto.CategoryRequests.CategoryUpdateRequest;
import com.devtime.category.dto.CategoryResponses.CategoryDeletionResponse;
import com.devtime.category.dto.CategoryResponses.CategoryResponse;
import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.EntityNotFoundException;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de catálogo de categorias (spec 005 §6).
 *
 * <p>BR-121: a classe declara {@code readOnly = true} e cada operação de escrita sobrescreve. A
 * ordem das validações em {@link #delete} segue exatamente a §6.1 da spec — a ordem é normativa
 * (BR-062), não uma preferência de implementação.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository repository;
    private final CategoryHistoryRepository historyRepository;
    private final CategoryWorkLogSource workLogSource;
    private final AuditService auditService;
    private final CategoryMapper mapper;
    private final CategoryNameUniquenessValidator nameUniquenessValidator;
    private final SystemCategoryGuard systemCategoryGuard;
    private final CategoryReplacementValidator replacementValidator;
    private final DefaultCategoryResolver defaultCategoryResolver;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    private static final String DEFAULT_COLOR = "#6366F1";

    @Override
    @PreAuthorize("hasPermission(null, 'CATEGORY_VIEW')")
    public List<CategoryResponse> list(Boolean active, String search) {
        return mapper.toResponses(
                repository.findAllOrdered().stream()
                        .filter(category -> active == null || category.isActive() == active)
                        .filter(category -> matchesSearch(category, search))
                        .toList());
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CATEGORY_VIEW')")
    public List<CategoryResponse> listActive() {
        return mapper.toResponses(repository.findActiveOrdered());
    }

    /**
     * Catálogo histórico (ver {@link CategoryService#getAllForReport()}).
     *
     * <p>Sem {@code @PreAuthorize}: quem chama são {@code 010} e {@code 012}, que já verificaram
     * {@code DASHBOARD_VIEW}/{@code REPORT_VIEW_*} e cujo consumidor é um rótulo de linha, não uma
     * lista de opções. Exigir {@code CATEGORY_VIEW} aqui faria um relatório falhar para quem tem
     * permissão de relatório e não de catálogo.
     */
    @Override
    public List<CategoryResponse> getAllForReport() {
        return historyRepository.findAllIncludingDeleted().stream()
                .map(
                        category ->
                                new CategoryResponse(
                                        category.getId(),
                                        category.getName(),
                                        category.getDescription(),
                                        category.getColor(),
                                        category.getIcon(),
                                        category.isBillableByDefault(),
                                        category.isActive(),
                                        category.getSortOrder(),
                                        category.isSystem(),
                                        category.getVersion() == null ? 0L : category.getVersion()))
                .toList();
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CATEGORY_VIEW')")
    public CategoryResponse getById(UUID id) {
        return mapper.toResponse(require(id));
    }

    /**
     * RN-104 (ver {@link CategoryService#resolveForWorkLog}).
     *
     * <p>Delega a {@link DefaultCategoryResolver}, que já implementa a cadeia e o desempate
     * determinístico. O que este método acrescenta é a fronteira: a entidade fica dentro da feature
     * e o consumidor recebe DTO (AR-02).
     */
    @Override
    @PreAuthorize("hasPermission(null, 'CATEGORY_VIEW')")
    public java.util.Optional<CategoryResponse> resolveForWorkLog(
            UUID ticketCategoryId, UUID contractCategoryId, UUID userCategoryId) {
        return defaultCategoryResolver
                .resolveDefault(ticketCategoryId, contractCategoryId, userCategoryId)
                .map(mapper::toResponse);
    }

    @Override
    @PreAuthorize("hasPermission(null, 'CATEGORY_VIEW')")
    public CategoryResponse requireActive(UUID id) {
        return mapper.toResponse(
                repository
                        .findActiveById(id)
                        // ART-024: inexistente, de outro tenant ou inativa produzem a mesma
                        // resposta.
                        .orElseThrow(() -> EntityNotFoundException.of(Category.class, id)));
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CATEGORY_MANAGE')")
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();
        nameUniquenessValidator.assertUnique(name, null); // RN-502

        Category category = new Category();
        category.setName(name);
        category.setDescription(request.description());
        category.setColor(request.color() == null ? DEFAULT_COLOR : request.color());
        category.setIcon(request.icon());
        category.setBillableByDefault(
                request.billableByDefault() == null || request.billableByDefault());
        category.setActive(true);
        category.setSortOrder(request.sortOrder() == null ? nextSortOrder() : request.sortOrder());
        // INV-CAT-05: apenas o seed cria categoria de sistema.
        category.setSystem(false);

        Category saved = repository.save(category);
        log.info("categoria criada categoryId={} name={}", saved.getId(), saved.getName());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CATEGORY_MANAGE')")
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        Category category = require(id);
        assertVersion(category, request.version()); // RN-004

        String name = request.name().trim();
        // CX-04: a proteção de categoria de sistema não dispensa a unicidade ao renomear.
        nameUniquenessValidator.assertUnique(name, id); // RN-502

        category.setName(name);
        category.setDescription(request.description());
        if (request.color() != null) {
            category.setColor(request.color());
        }
        category.setIcon(request.icon());
        category.setBillableByDefault(request.billableByDefault());
        // RN-504: inativar não afeta os work logs existentes; a categoria só deixa de ser
        // oferecida.
        category.setActive(request.active());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        return mapper.toResponse(category);
    }

    /**
     * Exclusão lógica na ordem normativa da §6.1 da spec.
     *
     * <p>A ordem é normativa e não preferência: o passo 3 (sistema) precede o 4 (vínculos) para não
     * fazer o usuário escolher uma substituta que seria descartada, e o 5 (validar a substituta)
     * precede o 6 (migrar) porque migrar para uma categoria inválida corromperia os registros de
     * forma difícil de reverter.
     *
     * <p>Migrar e excluir na mesma transação é o que sustenta INV-CAT-04: um work log sem categoria
     * válida não chega a existir nem por um instante observável.
     */
    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CATEGORY_MANAGE')")
    public CategoryDeletionResponse delete(UUID id, UUID replacementCategoryId) {
        Category category = require(id); // passo 2
        systemCategoryGuard.assertDeletable(category); // passo 3 — RN-503

        long linkedWorkLogs = workLogSource.countByCategory(id);
        if (linkedWorkLogs > 0 && replacementCategoryId == null) {
            // passo 4 — RN-505: com vínculos, a substituta é obrigatória.
            throw CategoryExceptions.replacementRequired(id, linkedWorkLogs);
        }

        UUID migratedTo = null;
        long migratedWorkLogs = 0L;
        if (replacementCategoryId != null) {
            // passo 5 — users.md §8.3: valida antes de qualquer efeito (BR-072).
            migratedTo = replacementValidator.require(replacementCategoryId, id).getId();
            // passo 6 — RN-505.
            migratedWorkLogs = workLogSource.reassignCategory(id, migratedTo);
        }

        // passo 7 — RN-003: exclusão lógica.
        repository.softDelete(id, clock.now(), tenantContext.currentUserId().orElse(null));
        auditService.record(
                "CATEGORY_DELETED",
                "Category",
                id,
                Map.of("name", category.getName(), "workLogsCount", linkedWorkLogs),
                Map.of(
                        "deletedAt",
                        clock.now().toString(),
                        "migratedTo",
                        migratedTo == null ? "" : migratedTo.toString(),
                        "migratedWorkLogs",
                        migratedWorkLogs));
        log.info(
                "categoria excluída categoryId={} migratedTo={} migratedWorkLogs={}",
                id,
                migratedTo,
                migratedWorkLogs);
        return new CategoryDeletionResponse(migratedWorkLogs, migratedTo);
    }

    @Override
    @Transactional
    @PreAuthorize("hasPermission(null, 'CATEGORY_MANAGE')")
    public List<CategoryResponse> reorder(CategoryReorderRequest request) {
        List<Category> all = repository.findAllOrdered();
        assertReorderCoversTenant(request.orderedIds(), all);

        for (int position = 0; position < request.orderedIds().size(); position++) {
            UUID categoryId = request.orderedIds().get(position);
            Category category =
                    all.stream()
                            .filter(candidate -> candidate.getId().equals(categoryId))
                            .findFirst()
                            .orElseThrow(
                                    () -> EntityNotFoundException.of(Category.class, categoryId));
            category.setSortOrder(position);
        }
        // A atomicidade vem da transação: ou toda a ordem é aplicada, ou nenhuma (CX-11, CX-12).
        return mapper.toResponses(repository.findAllOrdered());
    }

    /**
     * RN-501.
     *
     * <p>Única escrita da feature sem {@code @PreAuthorize} (BR-065). O seed é ação de sistema
     * executada dentro da criação do tenant, antes de existir qualquer membership — exigir {@code
     * CATEGORY_MANAGE} aqui tornaria impossível criar um tenant. A proteção é a idempotência: em um
     * tenant já semeado a operação não tem efeito.
     */
    @Override
    @Transactional
    public int seedDefaults() {
        if (repository.countAll() > 0) {
            // CX-14: idempotente. Reexecutar o seed não duplica o catálogo.
            return 0;
        }
        int sortOrder = 0;
        for (DefaultCategory definition : DefaultCategoryCatalog.entries()) {
            Category category = new Category();
            category.setName(definition.name());
            category.setColor(definition.color());
            category.setIcon(definition.icon());
            category.setBillableByDefault(definition.billableByDefault());
            category.setActive(true);
            category.setSortOrder(sortOrder++);
            category.setSystem(true); // RN-501
            repository.save(category);
        }
        log.info("seed de categorias concluído quantidade={}", sortOrder);
        return sortOrder;
    }

    private Category require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> EntityNotFoundException.of(Category.class, id));
    }

    private void assertVersion(Category category, long expected) {
        if (category.getVersion() != null && category.getVersion() != expected) {
            throw BusinessRuleException.versionConflict("Category", expected); // RN-004
        }
    }

    private void assertReorderCoversTenant(List<UUID> orderedIds, List<Category> all) {
        Set<UUID> distinct = new HashSet<>(orderedIds);
        if (distinct.size() != orderedIds.size()) {
            throw CategoryExceptions.invalidReorder("identificadores duplicados"); // CX-12
        }
        Set<UUID> existing =
                all.stream().map(Category::getId).collect(java.util.stream.Collectors.toSet());
        if (!distinct.equals(existing)) {
            // CX-10/CX-11: lista incompleta ou com id de outro tenant não altera ordem alguma.
            throw CategoryExceptions.invalidReorder(
                    "a lista deve conter exatamente as categorias do tenant");
        }
    }

    private int nextSortOrder() {
        return repository.findAllOrdered().stream()
                        .mapToInt(Category::getSortOrder)
                        .max()
                        .orElse(-1)
                + 1;
    }

    /**
     * Busca textual sem acento e sem diferenciar caixa.
     *
     * <p>Aplicada em memória porque o catálogo tem dezenas de entradas por tenant, não milhares: um
     * índice GIN aqui otimizaria uma consulta que já é integralmente resolvida por um índice de
     * tenant (CG-10).
     */
    private boolean matchesSearch(Category category, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        return unaccented(category.getName()).contains(unaccented(search));
    }

    private String unaccented(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
