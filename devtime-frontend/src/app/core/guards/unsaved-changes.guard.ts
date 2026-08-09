import { CanDeactivateFn } from '@angular/router';

/**
 * Contrato das telas que podem perder trabalho ao sair (§8 de `frontend.md`, FM-08).
 *
 * A tela responde se há alteração pendente; a confirmação é responsabilidade do guard, para que a
 * pergunta seja a mesma em todo o produto.
 */
export interface HasUnsavedChanges {
  hasUnsavedChanges(): boolean;
}

/**
 * `unsavedChangesGuard` — impede a saída silenciosa de um formulário sujo.
 *
 * Usa `confirm` do navegador de propósito: um diálogo próprio não bloqueia a navegação nativa (voltar,
 * fechar aba), então haveria duas experiências para o mesmo risco. O guard cobre a navegação interna
 * do roteador; a saída da página inteira é coberta por `beforeunload` na própria tela.
 */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }
  return confirm(
    $localize`:@@form.unsavedChanges:Há alterações não salvas nesta tela. Deseja sair mesmo assim?`,
  );
};
