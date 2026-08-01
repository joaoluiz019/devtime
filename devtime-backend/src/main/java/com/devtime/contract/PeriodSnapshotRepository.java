package com.devtime.contract;

import com.devtime.contract.domain.PeriodSnapshot;
import com.devtime.shared.persistence.SoftDeleteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistência de {@link PeriodSnapshot} (spec 011 §25).
 *
 * <p>Somente inserção e leitura. Não existe método de atualização nem de remoção (INV-SNP-01): o
 * snapshot é a âncora da imutabilidade dos relatórios, e um método capaz de alterá-lo tornaria a
 * garantia uma questão de disciplina.
 */
@Repository
public interface PeriodSnapshotRepository extends SoftDeleteRepository<PeriodSnapshot> {

    /**
     * Snapshot mais recente do período.
     *
     * <p>CX-18: um período reaberto e refechado tem <b>mais de um</b>. O mais recente é o que vale
     * para o relatório; os anteriores permanecem, versionados por {@code snapshotAt}, porque cada
     * um documenta o que foi entregue ao cliente naquele fechamento.
     */
    @Query(
            """
            SELECT s FROM PeriodSnapshot s
             WHERE s.contractPeriodId = :periodId
             ORDER BY s.snapshotAt DESC
             LIMIT 1
            """)
    Optional<PeriodSnapshot> findLatestByPeriod(@Param("periodId") UUID periodId);

    /** Todos os snapshots do período, do mais recente ao mais antigo. */
    @Query(
            """
            SELECT s FROM PeriodSnapshot s
             WHERE s.contractPeriodId = :periodId
             ORDER BY s.snapshotAt DESC
            """)
    List<PeriodSnapshot> findAllByPeriod(@Param("periodId") UUID periodId);
}
