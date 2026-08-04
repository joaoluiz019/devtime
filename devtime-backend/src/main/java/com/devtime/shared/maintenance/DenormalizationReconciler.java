package com.devtime.shared.maintenance;

/**
 * Reconciliador de um campo desnormalizado (specs 003 §22.4, 006, 007, 008 e 011).
 *
 * <p>Cinco features mantêm contadores e totais por <b>incremento transacional</b>, e não por
 * reagregação: {@code activeContractsCount}, {@code usageCount}, {@code spentMinutes}, {@code
 * billableMinutes} e {@code consumedMinutes}. O incremento é a decisão certa — reagregar no caminho
 * quente custaria linear no volume —, mas ele tem um modo de falha próprio: um incremento perdido
 * não se corrige sozinho, e o número errado sobrevive indefinidamente.
 *
 * <p>Todas as specs prevêem <b>um</b> job noturno compartilhado ({@code 0 0 2 * * *}) em vez de
 * cinco: a varredura é a mesma operação em cinco tabelas, e cinco agendamentos disputariam a mesma
 * janela de baixa atividade. Cada feature registra o seu reconciliador; quem orquestra é {@link
 * DenormalizationReconcileJob}.
 *
 * <p><b>Convergente por construção</b> (BR-185): o reconciliador recalcula do zero a partir da
 * fonte da verdade e escreve o resultado. Reexecutar não produz efeito diferente, porque ele não
 * opera sobre um delta acumulado.
 */
public interface DenormalizationReconciler {

    /** Nome curto do que é reconciliado, para o log da execução. Ex.: {@code tag.usageCount}. */
    String target();

    /**
     * Recalcula e corrige, percorrendo <b>todos</b> os tenants.
     *
     * <p>Roda sem sessão, e é isso que desliga o filtro de tenant do Hibernate — o mesmo mecanismo
     * das demais varreduras de plataforma (BR-049). Nenhum identificador vem de requisição: os
     * alvos saem da própria consulta.
     *
     * @return quantidade de registros que <b>estavam divergentes</b> e foram corrigidos; zero é o
     *     resultado esperado e qualquer valor acima disso é alerta operacional
     */
    int reconcile();
}
