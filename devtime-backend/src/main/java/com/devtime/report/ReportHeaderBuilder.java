package com.devtime.report;

import com.devtime.client.ClientService;
import com.devtime.client.dto.ClientResponses.AddressResponse;
import com.devtime.client.dto.ClientResponses.ClientReportParty;
import com.devtime.report.dto.ReportResponses.ReportAddress;
import com.devtime.report.dto.ReportResponses.ReportClient;
import com.devtime.report.dto.ReportResponses.ReportIssuer;
import com.devtime.report.dto.ReportResponses.ReportUserRef;
import com.devtime.shared.persistence.Address;
import com.devtime.shared.persistence.UuidGenerator;
import com.devtime.shared.tenancy.TenantContext;
import com.devtime.shared.time.TenantClock;
import com.devtime.tenant.TenantService;
import com.devtime.tenant.dto.TenantViews.TenantIssuer;
import com.devtime.user.UserService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cabeçalho de identificação de todo relatório (RN-703, RP-03).
 *
 * <p>Um relatório sem emissor identificado é um documento sem procedência: ele circula por e-mail,
 * é impresso e discutido meses depois, e a pergunta "quem emitiu isto e quando" precisa ter
 * resposta dentro do próprio arquivo.
 *
 * <p>Em período fechado, <b>nada</b> daqui é usado: emissor, cliente e contrato vêm congelados do
 * payload (RN-701, CX-02). Este construtor serve ao caminho ao vivo e ao carimbo de emissão, que é
 * a única parte do documento que legitimamente muda entre duas gerações (RN-708).
 */
@Component
@RequiredArgsConstructor
public class ReportHeaderBuilder {

    /** Prefixo do identificador de emissão. Legível, não técnico (PDF-04). */
    private static final String ISSUE_PREFIX = "EM";

    private static final DateTimeFormatter ISSUE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TenantService tenantService;
    private final ClientService clientService;
    private final UserService userService;
    private final TenantContext tenantContext;
    private final TenantClock clock;

    /**
     * Identificador único de emissão (RN-703, PDF-07).
     *
     * <p>Formato {@code EM-20260801-3F9A2C71}: data no fuso do tenant e os últimos 8 dígitos
     * hexadecimais do identificador de origem. <b>Não é o UUID</b> — PDF-04 proíbe identificador
     * técnico no arquivo —, mas deriva dele, e é isso que permite rastrear o rodapé do PDF até o
     * registro de exportação sem uma coluna nova e sem um segundo gerador de sequência.
     *
     * @param source identificador da {@code ReportExecution} na exportação; na consulta em tela, um
     *     UUIDv7 novo — a tela não produz arquivo, e o carimbo só precisa ser único
     */
    public String issueId(UUID source) {
        UUID origin = source == null ? UuidGenerator.newId() : source;
        // Os 32 bits menos significativos, em hexadecimal maiúsculo de largura fixa. São os que
        // variam entre dois identificadores próximos: em UUIDv7 os bits mais significativos
        // carregam o instante, e dois pedidos do mesmo segundo os compartilhariam.
        String suffix = "%08X".formatted((int) origin.getLeastSignificantBits());
        return "%s-%s-%s".formatted(ISSUE_PREFIX, clock.today().format(ISSUE_DATE), suffix);
    }

    /** Quem pediu o relatório (§6, {@code generatedBy}). */
    public ReportUserRef currentUser() {
        UUID userId = tenantContext.requireUserId();
        return new ReportUserRef(userId, userService.summaryOf(userId).name());
    }

    /** Emissor ao vivo (RN-703). Em período fechado, use o bloco {@code issuer} do payload. */
    public ReportIssuer issuer() {
        TenantIssuer issuer = tenantService.issuer();
        return new ReportIssuer(
                issuer.name(),
                issuer.legalName(),
                issuer.documentNumber(),
                issuer.email(),
                issuer.phone(),
                issuer.logoUrl(),
                toReportAddress(issuer.address()));
    }

    /**
     * Destinatário ao vivo (RN-703).
     *
     * <p>Nulo quando o relatório não tem um cliente único — a folha de horas cruza clientes por
     * definição (§7.2), e escolher um deles para o cabeçalho daria ao documento um destinatário que
     * ele não tem.
     */
    public ReportClient client(UUID clientId) {
        if (clientId == null) {
            return null;
        }
        ClientReportParty party = clientService.getReportParty(clientId);
        return new ReportClient(
                party.name(),
                party.legalName(),
                party.documentNumber(),
                toReportAddress(party.address()));
    }

    /** Idioma do rótulo de data (§6.4). {@code pt-BR} é o default do produto (ART-032). */
    public Locale locale() {
        String tag = tenantService.issuer().locale();
        return tag == null || tag.isBlank()
                ? Locale.forLanguageTag("pt-BR")
                : Locale.forLanguageTag(tag);
    }

    private ReportAddress toReportAddress(Address address) {
        if (address == null) {
            return null;
        }
        return new ReportAddress(
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getDistrict(),
                address.getCity(),
                address.getState(),
                address.getZipCode(),
                address.getCountry());
    }

    private ReportAddress toReportAddress(AddressResponse address) {
        if (address == null) {
            return null;
        }
        return new ReportAddress(
                address.street(),
                address.number(),
                address.complement(),
                address.district(),
                address.city(),
                address.state(),
                address.postalCode(),
                address.country());
    }
}
