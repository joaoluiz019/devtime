import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

/**
 * Página de recurso não encontrado.
 *
 * FR-089: rota inexistente leva a esta página **dentro do shell** — o usuário permanece navegável em
 * vez de cair numa tela isolada e ter que usar o botão voltar.
 */
@Component({
  selector: 'dt-not-found-page',
  imports: [RouterLink, ButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-empty">
      <i class="pi pi-exclamation-triangle dt-empty__icon" aria-hidden="true"></i>
      <h1 class="dt-empty__title" i18n="@@notFound.title">Página não encontrada</h1>
      <p class="dt-empty__text" i18n="@@notFound.text">
        O endereço acessado não existe ou foi movido.
      </p>
      <p-button
        routerLink="/"
        i18n-label="@@notFound.action"
        label="Ir para o dashboard"
        severity="secondary"
      />
    </section>
  `,
  styleUrl: './empty-page.scss',
})
export class NotFoundPage {}
