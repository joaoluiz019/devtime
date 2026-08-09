import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';
import { formatDocument } from '../../../shared/utils/document';
import { ClientListItem } from '../data/client.model';

/**
 * Cartão de cliente — `dt-client-card` (T-003-20).
 *
 * LS-06: em `xs` a tabela vira cartões com os campos mais relevantes. Aqui são nome, documento,
 * situação e contratos ativos — os quatro que respondem "é este o cliente e ele está em operação?".
 */
@Component({
  selector: 'dt-client-card',
  imports: [RouterLink, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="dt-client-card" [routerLink]="['/clients', client().id]">
      <span
        class="dt-client-card__color"
        aria-hidden="true"
        [style.background-color]="client().color"
      ></span>

      <span class="dt-client-card__body">
        <span class="dt-client-card__name">{{ client().name }}</span>
        @if (document() !== null) {
          <span class="dt-client-card__document">{{ document() }}</span>
        }
        <span class="dt-client-card__contracts">{{ contractsLabel() }}</span>
      </span>

      <!-- DS-05: a situação é texto com selo, nunca só cor. -->
      <p-tag
        [value]="statusLabel()"
        [severity]="client().status === 'ACTIVE' ? 'success' : 'warn'"
      />
    </a>
  `,
  styles: `
    .dt-client-card {
      display: flex;
      align-items: center;
      gap: var(--dt-space-3);
      padding: var(--dt-space-3);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background-color: var(--dt-surface-card);
      color: var(--dt-text-primary);
      text-decoration: none;
    }

    .dt-client-card:hover {
      border-color: var(--dt-color-primary);
    }

    .dt-client-card__color {
      width: 4px;
      align-self: stretch;
      border-radius: var(--dt-radius-full);
    }

    .dt-client-card__body {
      display: flex;
      flex: 1;
      flex-direction: column;
    }

    .dt-client-card__name {
      font-size: var(--dt-text-sm);
      font-weight: 600;
    }

    .dt-client-card__document,
    .dt-client-card__contracts {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class ClientCardComponent {
  readonly client = input.required<ClientListItem>();

  protected readonly document = computed(() => {
    const client = this.client();
    if (client.documentNumber === undefined || client.documentType === undefined) {
      return null;
    }
    return formatDocument(client.documentType, client.documentNumber);
  });

  protected readonly statusLabel = computed(() =>
    this.client().status === 'ACTIVE'
      ? $localize`:@@client.status.active:Ativo`
      : $localize`:@@client.status.inactive:Inativo`,
  );

  protected readonly contractsLabel = computed(() => {
    const count = this.client().activeContractsCount;
    return count === 1
      ? $localize`:@@client.contracts.one:1 contrato ativo`
      : $localize`:@@client.contracts.many:${count}:count: contratos ativos`;
  });
}
