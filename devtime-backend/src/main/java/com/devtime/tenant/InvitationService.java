package com.devtime.tenant;

import com.devtime.tenant.dto.MemberRequests.InvitationRequest;
import com.devtime.tenant.dto.MemberResponses.MemberInvitationResponse;
import java.util.List;
import java.util.UUID;

/**
 * Emissão, reenvio e revogação de convites (RN-457, spec 002 §22.2).
 *
 * <p>Separado de {@link MembershipService} porque resolve um problema distinto: o convite lida com
 * um endereço de e-mail que pode ainda não ter conta, enquanto a gestão de membros lida com
 * vínculos já existentes. O <b>aceite</b> não está aqui — é público e pertence a {@code
 * 001-authentication} (§4).
 */
public interface InvitationService {

    /**
     * users.md §7.2: convida um e-mail para a organização.
     *
     * <p>CE-U-01: se já existir conta com o endereço, o convite apenas a vincula; nenhuma conta
     * nova é criada. CX-06: um vínculo anterior {@code REMOVED} não impede o convite — gera um
     * novo, preservando o histórico do vínculo antigo.
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2459} quando já existe
     *     vínculo ativo ou convite pendente, {@code DEVTIME-1104} quando um {@code ADMIN} tenta
     *     conceder {@code OWNER}
     */
    MemberInvitationResponse invite(InvitationRequest request);

    /** RN-457: emite novo token e invalida o anterior. */
    MemberInvitationResponse resend(UUID membershipId);

    /** §4.3 de state-machines.md: {@code INVITED → REMOVED} por revogação. */
    void revoke(UUID membershipId);

    /** users.md §4: convites ainda pendentes na organização. */
    List<MemberInvitationResponse> listPending();

    /**
     * RN-457: {@code INVITED → REMOVED} após 7 dias, executado pelo {@code ExpiredInvitationJob}.
     */
    int expirePending();
}
