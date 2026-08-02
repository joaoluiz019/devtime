package com.devtime.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.devtime.attachment.domain.ScanStatus;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * CA-11 / CP-02 / CP-13 / DoD-05 — <b>não existe caminho de liberação manual</b>.
 *
 * <p>A spec exige que isto seja verificado por "inspeção de rotas e código". Um teste é a forma
 * executável dessa inspeção: uma verificação feita por leitura humana passa a ser refeita a cada
 * revisão, e deixa de ser feita quando alguém tem pressa.
 *
 * <p>OB-02 antecipa que esta é a decisão que mais sofrerá pressão: um usuário com arquivo
 * importante inacessível pedirá exceção. Se alguém acrescentar a rota, este teste falha — e a falha
 * aponta para a decisão documentada, não para uma preferência de estilo.
 */
class NoManualReleasePathTest {

    @Test
    @DisplayName("CP-13/RN-011: nenhum controller de anexo expõe PUT ou PATCH")
    void attachmentControllersMustNotExposeUpdateRoutes() {
        Arrays.asList(
                        AttachmentController.class,
                        TicketAttachmentController.class,
                        CommentAttachmentController.class)
                .forEach(
                        controller ->
                                assertThat(declaredMethodsOf(controller))
                                        .as(
                                                "alterar o contentType após a verificação"
                                                        + " permitiria burlar a validação de"
                                                        + " assinatura (%s)",
                                                controller.getSimpleName())
                                        .noneMatch(
                                                method ->
                                                        method.isAnnotationPresent(PutMapping.class)
                                                                || method.isAnnotationPresent(
                                                                        PatchMapping.class)
                                                                || isMappedWith(
                                                                        method, "PUT", "PATCH")));
    }

    @Test
    @DisplayName("CA-11/CP-02: a API de anexo expõe apenas listar, enviar, baixar e excluir")
    void attachmentApiMustExposeOnlyDocumentedOperations() {
        assertThat(declaredMethodsOf(AttachmentController.class))
                .extracting(Method::getName)
                .as("§14: os únicos verbos de manutenção são download, delete e quota")
                .containsExactlyInAnyOrder("download", "delete", "quota");

        assertThat(declaredMethodsOf(AttachmentController.class))
                .filteredOn(method -> method.isAnnotationPresent(DeleteMapping.class))
                .as("a exclusão existe; a liberação manual não")
                .hasSize(1);
    }

    /**
     * Métodos escritos no código-fonte, sem os gerados pela instrumentação.
     *
     * <p>O JaCoCo acrescenta {@code $jacocoInit} às classes instrumentadas. Sem o filtro, este
     * teste passaria com {@code -Djacoco.skip=true} e falharia no pipeline — o pior modo de falha
     * possível para uma verificação de segurança, porque a suspeita recairia sobre o teste.
     */
    private static Method[] declaredMethodsOf(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !method.getName().startsWith("$"))
                .toArray(Method[]::new);
    }

    @Test
    @DisplayName("CP-02: nenhum método público do serviço aceita alterar o scanStatus")
    void serviceApiMustNotAcceptScanStatus() {
        assertThat(AttachmentService.class.getMethods())
                .as(
                        "§6.3: liberar um arquivo não verificado por decisão administrativa"
                                + " converteria três camadas de defesa em uma caixa de diálogo")
                .noneMatch(
                        method ->
                                Arrays.stream(method.getParameterTypes())
                                        .anyMatch(ScanStatus.class::equals));

        assertThat(AttachmentDownloadService.class.getMethods())
                .as("nenhum parâmetro do download permite ignorar a guarda")
                .noneMatch(
                        method ->
                                Arrays.stream(method.getParameterTypes())
                                        .anyMatch(
                                                type ->
                                                        type.equals(boolean.class)
                                                                || type.equals(Boolean.class)
                                                                || type.equals(ScanStatus.class)));
    }

    @Test
    @DisplayName("RN-803/INV-ATT-02: CLEAN é o único estado que libera o download")
    void onlyCleanIsDownloadable() {
        assertThat(Arrays.stream(ScanStatus.values()).filter(ScanStatus::isDownloadable))
                .containsExactly(ScanStatus.CLEAN);
    }

    private boolean isMappedWith(Method method, String... verbs) {
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.method())
                .anyMatch(requestMethod -> Arrays.asList(verbs).contains(requestMethod.name()));
    }
}
