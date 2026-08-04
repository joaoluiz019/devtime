package com.devtime.contract;

import com.devtime.contract.dto.ContractResponses.MaintenanceTarget;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Operações de sistema sobre contratos e períodos (spec 004 §22.4, T-004-29 e T-004-30).
 *
 * <p>Separada de {@link ContractService} porque a natureza é outra: aqui não há usuário, não há
 * permissão a verificar e o alvo não vem de uma requisição. São as três transições que o tempo
 * dispara sozinho — renovar, abrir e encerrar — e que, sem elas, exigiriam que alguém abrisse a
 * tela todo dia para o contrato continuar funcionando.
 *
 * <p>As varreduras devolvem o {@code tenantId} porque percorrem <b>todas</b> as organizações; o
 * contexto é definido pelo job a cada item (BR-049, JB-06). As operações unitárias, essas, já rodam
 * sob o contexto do tenant e são recortadas pelo filtro automático (ART-022).
 *
 * <p>Cada operação é uma transação própria, e não um lote em transação única: JB-04 exige que a
 * falha em um tenant não interrompa os demais, e uma transação abrangendo mil contratos
 * transformaria um erro isolado em nenhuma renovação naquele dia.
 */
public interface ContractMaintenanceService {

    /** RN-213: períodos abertos a {@code ≤ daysAhead} do fim, sem sucessor, em qualquer tenant. */
    List<MaintenanceTarget> findRenewalDue(LocalDate reference, int daysAhead, int limit);

    /** §22.4: períodos {@code SCHEDULED} cujo {@code startDate} já chegou, em qualquer tenant. */
    List<MaintenanceTarget> findScheduledDue(LocalDate reference, int limit);

    /** FA-05: contratos {@code ACTIVE} cuja vigência terminou, em qualquer tenant. */
    List<MaintenanceTarget> findEndDue(LocalDate reference, int limit);

    /** RN-230: períodos abertos com saldo transportado sujeito a expiração, em qualquer tenant. */
    List<MaintenanceTarget> findRolloverExpiryDue(int limit);

    /** CE-ME-02: períodos abertos de contratos encerrados há mais de {@code graceDays} dias. */
    List<MaintenanceTarget> findAutoCloseDue(LocalDate reference, int graceDays, int limit);

    /**
     * RN-230: aplica o débito de expiração de saldo transportado.
     *
     * @return {@code true} quando debitou; {@code false} quando nada expirou ou quando o débito já
     *     havia sido aplicado a este período — a convergência que §22.4 exige do job
     */
    boolean expireRollover(UUID periodId);

    /**
     * CE-ME-02: fecha o período de um contrato encerrado.
     *
     * <p>Delega a {@link PeriodClosingService#close} e não reimplementa nenhum dos sete passos de
     * RN-241: um segundo caminho de fechamento é um segundo lugar onde o snapshot pode divergir.
     *
     * @return {@code true} quando fechou; {@code false} quando o período já não estava aberto ou
     *     quando uma guarda de fechamento o impediu (cronômetro ativo, por exemplo)
     */
    boolean autoClosePeriod(UUID periodId);

    /**
     * RN-213: cria o próximo período como {@code SCHEDULED}.
     *
     * <p>Nasce {@code SCHEDULED} e não {@code OPEN} porque o período seguinte só passa a valer no
     * seu {@code startDate}: abrir antes permitiria registrar horas em um ciclo que ainda não
     * começou, e o saldo do ciclo corrente deixaria de refletir o consumo real.
     *
     * @return {@code true} quando criou; {@code false} quando outra execução já havia criado
     */
    boolean renewPeriod(UUID periodId);

    /**
     * {@code SCHEDULED → OPEN} no {@code startDate}.
     *
     * @return {@code true} quando abriu; {@code false} quando o período já não estava {@code
     *     SCHEDULED}
     */
    boolean openScheduledPeriod(UUID periodId);

    /**
     * FA-05: {@code ACTIVE → ENDED} ao atingir a data de fim.
     *
     * @return {@code true} quando encerrou; {@code false} quando o contrato já não estava {@code
     *     ACTIVE}
     */
    boolean endContract(UUID contractId);
}
