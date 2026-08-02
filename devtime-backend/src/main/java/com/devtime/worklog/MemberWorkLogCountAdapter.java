package com.devtime.worklog;

import com.devtime.tenant.MemberRemovalPorts.WorkLogCountSource;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação de {@link WorkLogCountSource} (RN-458).
 *
 * <p>Conta o que <b>permanece</b>, não o que foi tocado: a remoção de um membro não altera nenhum
 * registro de horas. O número existe para ser mostrado a quem remove — sem ele, a operação parece
 * apagar o trabalho da pessoa, e o receio de que apague é o que leva organizações a manter membros
 * inativos com acesso.
 */
@Component
@RequiredArgsConstructor
public class MemberWorkLogCountAdapter implements WorkLogCountSource {

    private final WorkLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public long countByUser(UUID userId) {
        return repository.countByUserId(userId);
    }
}
