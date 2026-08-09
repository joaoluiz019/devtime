import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TenantOption } from '../../core/auth/auth.model';

/**
 * Lista de organizações do usuário — `dt-tenant-selector` (T-001-51).
 *
 * CX-08: organização suspensa aparece **marcada**, não omitida. Esconder a suspensa faria o usuário
 * concluir que perdeu o acesso, quando o que existe é uma pendência administrativa que ele pode
 * resolver.
 *
 * Cancelada é a única opção não acionável: RN-008 rejeita o acesso, então oferecer o clique seria
 * prometer uma navegação que termina em `403`.
 */
@Component({
  selector: 'dt-tenant-selector',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="dt-tenant-selector" role="list">
      @for (tenant of tenants(); track tenant.id) {
        <li>
          <button
            type="button"
            class="dt-tenant-selector__item"
            [disabled]="tenant.status === 'CANCELLED' || busy()"
            (click)="selected.emit(tenant)"
          >
            <span class="dt-tenant-selector__avatar" aria-hidden="true">
              @if (tenant.logoUrl !== null) {
                <img [src]="tenant.logoUrl" alt="" />
              } @else {
                {{ tenant.name.charAt(0).toUpperCase() }}
              }
            </span>

            <span class="dt-tenant-selector__body">
              <span class="dt-tenant-selector__name">{{ tenant.name }}</span>
              <span class="dt-tenant-selector__role">{{ roleLabel(tenant.role) }}</span>
            </span>

            <!-- DS-05: a situação é texto, nunca só cor. -->
            @if (tenant.status !== 'ACTIVE') {
              <span
                class="dt-tenant-selector__status"
                [class.dt-tenant-selector__status--cancelled]="tenant.status === 'CANCELLED'"
              >
                {{ statusLabel(tenant.status) }}
              </span>
            }
          </button>
        </li>
      }
    </ul>
  `,
  styles: `
    .dt-tenant-selector {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .dt-tenant-selector__item {
      display: flex;
      align-items: center;
      gap: var(--dt-space-3);
      width: 100%;
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
      color: var(--dt-text-primary);
      text-align: left;
      cursor: pointer;
    }

    .dt-tenant-selector__item:hover:not(:disabled) {
      border-color: var(--dt-color-primary);
    }

    .dt-tenant-selector__item:disabled {
      color: var(--dt-text-disabled);
      cursor: not-allowed;
    }

    .dt-tenant-selector__avatar {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 2rem;
      height: 2rem;
      border-radius: var(--dt-radius-full);
      background-color: var(--dt-surface-raised);
      font-weight: 600;
      overflow: hidden;
    }

    .dt-tenant-selector__avatar img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .dt-tenant-selector__body {
      display: flex;
      flex-direction: column;
      flex: 1;
    }

    .dt-tenant-selector__name {
      font-size: var(--dt-text-sm);
      font-weight: 500;
    }

    .dt-tenant-selector__role {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-tenant-selector__status {
      padding: 2px var(--dt-space-2);
      border: 1px solid var(--dt-color-warning);
      border-radius: var(--dt-radius-full);
      color: var(--dt-color-warning);
      font-size: var(--dt-text-xs);
      white-space: nowrap;
    }

    .dt-tenant-selector__status--cancelled {
      border-color: var(--dt-color-danger);
      color: var(--dt-color-danger);
    }
  `,
})
export class TenantSelectorComponent {
  readonly tenants = input.required<readonly TenantOption[]>();

  /** Trava a lista enquanto a seleção está em curso, evitando duas trocas concorrentes. */
  readonly busy = input(false);

  readonly selected = output<TenantOption>();

  protected roleLabel(role: TenantOption['role']): string {
    switch (role) {
      case 'OWNER':
        return $localize`:@@role.owner:Proprietário`;
      case 'ADMIN':
        return $localize`:@@role.admin:Administrador`;
      case 'MANAGER':
        return $localize`:@@role.manager:Gestor`;
      case 'MEMBER':
        return $localize`:@@role.member:Membro`;
      case 'VIEWER':
        return $localize`:@@role.viewer:Visualizador`;
      default:
        return $localize`:@@role.clientPortal:Portal do cliente`;
    }
  }

  protected statusLabel(status: TenantOption['status']): string {
    return status === 'CANCELLED'
      ? $localize`:@@tenant.status.cancelled:Cancelada`
      : $localize`:@@tenant.status.suspended:Suspensa`;
  }
}
