import { Directive, effect, inject, input, TemplateRef, ViewContainerRef } from '@angular/core';
import { AuthStore } from '../../core/auth/auth.store';

/**
 * Oculta o conteúdo quando o papel não possui a permissão (SB-01, CA-07 de permissions.md).
 *
 * IMP-06 / FR-083: isto é **apenas ergonomia**. A autorização real é sempre do backend. A diretiva
 * remove o elemento do DOM em vez de desabilitá-lo porque SB-01 determina ocultar, não desabilitar: um
 * botão desabilitado informa que a ação existe e sugere que falta apenas um clique.
 *
 * Uso: `<button *dtHasPermission="'CLIENT_CREATE'">…</button>`
 */
@Directive({ selector: '[dtHasPermission]' })
export class HasPermissionDirective {
  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly authStore = inject(AuthStore);

  /** Permissão exigida, ou lista da qual basta uma (OWN-08 admite equivalência entre `*_OWN`/`*_ANY`). */
  readonly dtHasPermission = input.required<string | readonly string[]>();

  constructor() {
    effect(() => {
      const required = this.dtHasPermission();
      const permissions = typeof required === 'string' ? [required] : required;
      const allowed = this.authStore.hasAnyPermission(permissions);

      this.viewContainer.clear();
      if (allowed) {
        this.viewContainer.createEmbeddedView(this.templateRef);
      }
    });
  }
}
