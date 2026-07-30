import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Layout de autenticação — L1 de `layouts.md` §5.
 *
 * Cartão de 400px centralizado, sem imagem decorativa (DS-01: densidade sóbria). Fica fora do shell:
 * quem não tem sessão não deve ver barra lateral nem barra superior.
 */
@Component({
  selector: 'dt-auth-layout',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-auth">
      <div class="dt-auth__brand">
        <i class="pi pi-stopwatch" aria-hidden="true"></i>
        <span i18n="@@brand.name">DevTime</span>
      </div>

      <main class="dt-auth__card">
        <router-outlet />
      </main>

      <footer class="dt-auth__footer">
        <span i18n="@@auth.footer.terms">Termos</span>
        <span aria-hidden="true">·</span>
        <span i18n="@@auth.footer.privacy">Privacidade</span>
        <span aria-hidden="true">·</span>
        <span i18n="@@auth.footer.support">Suporte</span>
      </footer>
    </div>
  `,
  styleUrl: './auth-layout.component.scss',
})
export class AuthLayoutComponent {}
