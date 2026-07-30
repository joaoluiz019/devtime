import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

/**
 * Página de acesso negado — P35 de `05-ui/pages.md`.
 *
 * A mensagem oferece a ação de solicitar acesso ao proprietário, conforme o mapa de mensagens de erro
 * do design system (§9.1, `DEVTIME-1101`). Nunca revela qual recurso existe do outro lado (ART-024).
 */
@Component({
  selector: 'dt-forbidden-page',
  imports: [RouterLink, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-empty">
      <i class="pi pi-lock dt-empty__icon" aria-hidden="true"></i>
      <h1 class="dt-empty__title" i18n="@@forbidden.title">Acesso negado</h1>
      <p class="dt-empty__text" i18n="@@forbidden.text">
        Você não tem permissão para esta área. Solicite acesso ao proprietário da organização.
      </p>
      <p-button
        routerLink="/"
        i18n-label="@@forbidden.action"
        label="Ir para o dashboard"
        severity="secondary"
      />
    </section>
  `,
  styleUrl: './empty-page.scss',
})
export class ForbiddenPage {}
