package com.devtime.comment;

import com.devtime.tenant.MembershipService;
import com.devtime.user.UserService;
import com.devtime.user.dto.UserSummary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Extração e resolução de menções {@code @} (RN-813, §6.2 da spec 014).
 *
 * <p>Cinco passos: localizar padrões {@code @identificador}, resolver cada um contra os memberships
 * do tenant, filtrar por {@code ACTIVE}, persistir os identificadores resolvidos e deixar as
 * menções não resolvidas como texto.
 *
 * <p><b>Menção inválida não é erro.</b> {@code @joao} pode ser texto legítimo, não menção. Rejeitar
 * o comentário porque um {@code @} não corresponde a membro ativo transformaria escrita livre em
 * formulário (§6.1, passo 6).
 *
 * <p><b>Padrão de e-mail não é menção.</b> O regex exige que o {@code @} não seja precedido por
 * caractere de palavra, então {@code email@dominio.com} não dispara nada (CX-16).
 *
 * <p><b>Resolução em lote.</b> Duas consultas no total, independentemente do número de menções: uma
 * para os membros ativos e outra para os identificadores de exibição. Uma consulta por menção faria
 * um comentário com 20 menções custar 20 idas ao banco (§20 da spec).
 */
@Component
@RequiredArgsConstructor
public class MentionExtractor {

    /**
     * {@code @} não precedido por caractere de palavra, seguido de letras, dígitos, ponto,
     * sublinhado ou hífen.
     *
     * <p>{@code \p{L}} cobre acentuação: {@code @joão} é uma menção tão legítima quanto {@code
     * @joao}.
     */
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@])@([\\p{L}\\p{N}._-]{2,60})");

    private final MembershipService membershipService;
    private final UserService userService;

    /**
     * Resolve as menções do corpo.
     *
     * @return identificadores dos membros <b>ativos</b> mencionados, sem duplicata e na ordem de
     *     aparição; vazio quando nenhuma menção é resolvível (CX-05)
     */
    public List<UUID> extract(String body) {
        Set<String> handles = handlesIn(body);
        if (handles.isEmpty()) {
            return List.of();
        }
        Set<UUID> activeMembers = membershipService.activeMemberIds(); // RN-813
        return userService.findByHandles(handles).stream()
                .map(UserSummary::id)
                .filter(activeMembers::contains)
                .distinct()
                .toList();
    }

    private Set<String> handlesIn(String body) {
        if (body == null || body.isBlank()) {
            return Set.of();
        }
        Set<String> handles = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find()) {
            handles.add(matcher.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        return handles;
    }
}
