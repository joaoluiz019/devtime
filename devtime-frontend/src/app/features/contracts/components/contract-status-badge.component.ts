import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { ContractStatus } from '../data/contract.model';

/**
 * Selo de estado do contrato — `dt-status-badge` (T-004-17).
 *
 * DS-05: estado é **texto**, nunca só cor. Um contrato suspenso e um encerrado são coisas
 * diferentes para quem lança horas, e distingui-los por matiz exclui quem não vê a diferença.
 */
@Component({
  selector: 'dt-contract-status-badge',
  imports: [TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="label()" [severity]="severity()" [icon]="icon()" />`,
})
export class ContractStatusBadgeComponent {
  readonly status = input.required<ContractStatus>();

  protected readonly label = computed(() => {
    switch (this.status()) {
      case 'DRAFT':
        return $localize`:@@contract.status.draft:Rascunho`;
      case 'ACTIVE':
        return $localize`:@@contract.status.active:Ativo`;
      case 'SUSPENDED':
        return $localize`:@@contract.status.suspended:Suspenso`;
      case 'ENDED':
        return $localize`:@@contract.status.ended:Encerrado`;
      default:
        return $localize`:@@contract.status.cancelled:Cancelado`;
    }
  });

  protected readonly severity = computed(() => {
    switch (this.status()) {
      case 'ACTIVE':
        return 'success';
      case 'SUSPENDED':
        return 'warn';
      case 'DRAFT':
        return 'info';
      default:
        return 'secondary';
    }
  });

  protected readonly icon = computed(() => {
    switch (this.status()) {
      case 'ACTIVE':
        return 'pi pi-check-circle';
      case 'SUSPENDED':
        return 'pi pi-pause-circle';
      case 'DRAFT':
        return 'pi pi-pencil';
      default:
        return 'pi pi-ban';
    }
  });
}
