package com.devtime.user;

import com.devtime.user.dto.UserProfileRequests.UserPreferencesRequest;
import com.devtime.user.dto.UserProfileRequests.UserProfileUpdateRequest;
import com.devtime.user.dto.UserProfileResponses.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Perfil do usuário autenticado (users.md §5).
 *
 * <p>Nenhuma rota recebe identificador de usuário: o alvo é sempre o titular da sessão. Um {@code
 * /users/{id}} de escrita exigiria verificação de ownership em cada método, e a ausência da rota
 * elimina a classe inteira de erro.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "Perfil", description = "Dados pessoais, preferências e avatar (users.md §5)")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(summary = "Perfil do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Perfil com preferências normalizadas")
    public UserProfileResponse current() {
        return userProfileService.current();
    }

    @PatchMapping
    @Operation(
            summary = "Atualiza o perfil",
            description = "Atualização parcial. O e-mail não é alterável no MVP (RS-01).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil atualizado"),
        @ApiResponse(responseCode = "400", description = "DEVTIME-2000 — fuso horário inválido")
    })
    public UserProfileResponse updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return userProfileService.updateProfile(request);
    }

    @PatchMapping("/preferences")
    @Operation(
            summary = "Atualiza as preferências",
            description = "Mesclada sobre as existentes; chaves ausentes preservam o valor atual.")
    @ApiResponse(responseCode = "200", description = "Preferências atualizadas")
    public UserProfileResponse updatePreferences(
            @Valid @RequestBody UserPreferencesRequest request) {
        return userProfileService.updatePreferences(request);
    }

    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envia o avatar",
            description = "PNG, JPEG ou WebP, até 2 MB, com verificação de assinatura binária.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Avatar atualizado"),
        @ApiResponse(responseCode = "413", description = "DEVTIME-2701 — acima de 2 MB"),
        @ApiResponse(responseCode = "415", description = "DEVTIME-2702 — tipo não permitido")
    })
    public UserProfileResponse uploadAvatar(@RequestPart("file") MultipartFile file) {
        return userProfileService.uploadAvatar(
                file.getSize(), file.getContentType(), () -> openStream(file));
    }

    @DeleteMapping("/avatar")
    @Operation(summary = "Remove o avatar")
    @ApiResponse(responseCode = "204", description = "Avatar removido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar() {
        userProfileService.removeAvatar();
    }

    /**
     * {@code MultipartFile} devolve um fluxo novo a cada chamada, o que permite validar a
     * assinatura e gravar o conteúdo sem reter o arquivo em memória.
     */
    private InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
