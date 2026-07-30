import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeStore } from './core/theme/theme.store';

/**
 * Raiz da aplicação.
 *
 * Não contém layout: o shell (L2) e o layout de autenticação (L1) são escolhidos por rota, e colocar
 * qualquer estrutura aqui a imporia às duas.
 *
 * O {@link ThemeStore} é injetado para ser instanciado no bootstrap: o atributo `data-theme` do elemento
 * raiz precisa existir antes da primeira renderização, senão a tela pisca no tema errado.
 */
@Component({
  selector: 'dt-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<router-outlet />',
})
export class AppComponent {
  private readonly themeStore = inject(ThemeStore);

  constructor() {
    // Leitura explícita para que o effect do store seja registrado no bootstrap.
    this.themeStore.preference();
  }
}
