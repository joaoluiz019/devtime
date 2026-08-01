package com.devtime.worklog;

import com.devtime.worklog.dto.WorkLogRequests.WorkLogValidateRequest;
import com.devtime.worklog.dto.WorkLogResponses.WorkLogValidateResponse;

/**
 * Validação prévia sem persistir (FA-01, CE-17).
 *
 * <p>Existe separada de {@link WorkLogService} por uma diferença de contrato, não de conveniência:
 * a criação <b>interrompe no primeiro erro</b>, na ordem normativa da §6.1, porque essa ordem
 * decide qual problema o usuário vê. A validação prévia faz o oposto — <b>relata tudo o que
 * encontrou</b>, para que a correção seja uma só. Misturar as duas responsabilidades no mesmo
 * método exigiria um parâmetro de modo, e o caminho de criação passaria a ter dois comportamentos.
 *
 * <p>CP-19: <b>nada é persistido</b>. Nem work log, nem consumo, nem etiqueta criada
 * implicitamente.
 */
public interface WorkLogValidationService {

    /**
     * @return o que seria calculado, os conflitos encontrados, os avisos aplicáveis e a prévia do
     *     saldo resultante — sem gravar coisa alguma
     */
    WorkLogValidateResponse validate(WorkLogValidateRequest request);
}
