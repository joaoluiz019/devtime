package com.devtime.contract.domain;

import com.devtime.shared.persistence.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Cópia congelada do relatório de um período fechado (entities.md §6.9, ART-005).
 *
 * <p>É a âncora da imutabilidade: depois que o período fecha e o relatório vai ao cliente, aquele
 * número não muda — nem por edição de work log, nem por alteração de contrato, nem por recálculo.
 * RN-701 serve relatórios de período fechado exclusivamente daqui, o que também os torna imunes a
 * qualquer degradação de {@code work_logs}: um relatório de dois anos atrás responde na mesma
 * velocidade que o do mês passado.
 *
 * <p><b>INV-SNP-01: o snapshot é imutável e a reabertura não o apaga.</b> Um refechamento gera um
 * <b>segundo</b> snapshot, versionado por {@code snapshotAt} (CX-18) — é por isso que a unicidade
 * em V020 é {@code (contract_period_id, snapshot_at)} e não apenas o período.
 *
 * <p>O {@code checksum} SHA-256 existe para detectar adulteração direta no banco (SG-05). Quando
 * diverge, o job de integridade <b>alerta e não corrige</b> (CX-21): reescrever o snapshot para
 * "consertar" o checksum destruiria a única prova de que algo foi alterado.
 */
@Entity
@Table(name = "period_snapshots")
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = TenantScopedEntity.TENANT_CONDITION)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
public class PeriodSnapshot extends TenantScopedEntity {

    @Column(name = "contract_period_id", nullable = false, updatable = false)
    private UUID contractPeriodId;

    @Column(name = "snapshot_at", nullable = false, updatable = false)
    private Instant snapshotAt;

    /** 🔒 JSON canônico com tenant, cliente, contrato, período, totais, work logs e ajustes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    /** 🔒 SHA-256 hexadecimal do {@code payload} exatamente como persistido. */
    @Column(name = "checksum", nullable = false, updatable = false, length = 64)
    private String checksum;

    @Column(name = "schema_version", nullable = false, updatable = false)
    private int schemaVersion;
}
