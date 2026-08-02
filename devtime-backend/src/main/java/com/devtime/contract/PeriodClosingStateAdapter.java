package com.devtime.contract;

import com.devtime.tenant.MemberRemovalPorts.PeriodClosingStateSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link PeriodClosingStateSource} (CX-12 de {@code specs/002-users}).
 *
 * <p>A guarda do cancelamento pertence a {@code 002}, mas o estado do período pertence a {@code
 * 004}: cancelar a organização no meio de um fechamento deixaria um período travado em {@code
 * CLOSING} sem quem o destrave — nem o usuário, que perdeu o acesso, nem o {@code StuckClosingJob},
 * que não roda em tenant cancelado.
 */
@Component
@RequiredArgsConstructor
public class PeriodClosingStateAdapter implements PeriodClosingStateSource {

    private final ContractPeriodRepository repository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasPeriodInClosing() {
        return repository.existsClosingInTenant();
    }
}
