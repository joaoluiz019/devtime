package com.devtime.category;

import java.util.UUID;

/**
 * Vínculo entre categoria e registros de horas (RN-505, passos 4 e 6 da §6.1 da spec 005).
 *
 * <p>A dependência é invertida pelo mesmo motivo de {@code PeriodWorkLogSource}: {@code worklog}
 * consome {@code CategoryService} (RN-104, RN-112), então {@code category} não pode chamá-lo de
 * volta sem fechar o ciclo que AR-09 e BR-008 proíbem. Quem declara a interface é quem precisa do
 * dado; quem a implementa é quem o possui.
 *
 * <p>Sem implementação registrada a contagem é zero e a migração não afeta linha alguma — que é
 * exatamente o comportamento correto num tenant sem a feature de horas.
 */
public interface CategoryWorkLogSource {

    /** Passo 4: registros vinculados, que decidem se a substituta é obrigatória. */
    long countByCategory(UUID categoryId);

    /**
     * Passo 6: migra os registros da categoria excluída para a substituta.
     *
     * <p>Inclui os registros de período fechado. Eles estão travados contra <b>edição pelo
     * usuário</b> (RN-241), não contra a manutenção do catálogo, e o número do fechamento não muda:
     * a categoria não entra em nenhuma fórmula de saldo. O relatório do período fechado também não
     * muda, porque é servido do snapshot, onde o nome vigente à época já está congelado (RN-701).
     *
     * @return quantidade migrada, publicada na resposta da exclusão e na auditoria
     */
    long reassignCategory(UUID fromCategoryId, UUID toCategoryId);
}
