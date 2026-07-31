package com.devtime.tenant;

import com.devtime.tenant.domain.Membership;
import com.devtime.tenant.domain.MembershipStatus;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta de membership ativo (ver {@link MembershipService}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository repository;

    @Override
    public boolean isActiveMember(UUID userId) {
        if (userId == null) {
            return false;
        }
        return repository
                .findByUserId(userId)
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
    }

    @Override
    public Set<UUID> activeMemberIds() {
        return repository.findByStatus(MembershipStatus.ACTIVE).stream()
                .map(Membership::getUserId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
