import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Report, ReportAddress } from '../data/report.model';

/**
 * Prévia do cabeçalho que sairá no PDF — `dt-report-header-preview` (T-012-26).
 *
 * O cabeçalho é o que torna o arquivo um documento: emissor, destinatário, recorte e o `issueId` de
 * RN-703. Mostrá-lo antes da exportação evita o ciclo de gerar o PDF, descobrir que o CNPJ da
 * organização está em branco e voltar às configurações — com o cliente já esperando o anexo.
 *
 * O `issueId` aparece em texto discreto: ele não interessa a quem lê, e interessa muito a quem
 * precisa rastrear qual emissão gerou o arquivo que chegou ao cliente.
 */
@Component({
  selector: 'dt-report-header-preview',
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-report-header">
      <div class="dt-report-header__party">
        @if (report().issuer.logoUrl !== null) {
          <img
            class="dt-report-header__logo"
            [src]="report().issuer.logoUrl"
            alt=""
            aria-hidden="true"
          />
        }
        <strong>{{ report().issuer.legalName ?? report().issuer.name }}</strong>
        @if (report().issuer.documentNumber !== null) {
          <span>{{ report().issuer.documentNumber }}</span>
        }
        @if (issuerAddress() !== null) {
          <span>{{ issuerAddress() }}</span>
        }
        @if (report().issuer.email !== null) {
          <span>{{ report().issuer.email }}</span>
        }
      </div>

      @if (client(); as clientRef) {
        <div class="dt-report-header__party">
          <span class="dt-report-header__party-label" i18n="@@report.header.client">Cliente</span>
          <strong>{{ clientRef.legalName ?? clientRef.name }}</strong>
          @if (clientRef.documentNumber !== null) {
            <span>{{ clientRef.documentNumber }}</span>
          }
        </div>
      }

      <div class="dt-report-header__meta">
        <span>{{ scope() }}</span>
        <span class="dt-report-header__issue" i18n="@@report.header.issue">
          Emissão {{ report().issueId }}
        </span>
      </div>
    </div>
  `,
  styles: `
    .dt-report-header {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: var(--dt-space-4);
      padding: var(--dt-space-4);
      border: 1px solid var(--dt-border);
      border-radius: var(--dt-radius-md);
      background: var(--dt-surface-card);
      font-size: var(--dt-text-sm);
    }

    .dt-report-header__party,
    .dt-report-header__meta {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-report-header__meta {
      text-align: end;
    }

    .dt-report-header__logo {
      max-height: 2.5rem;
      max-width: 10rem;
      object-fit: contain;
    }

    .dt-report-header__party-label {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }

    .dt-report-header__issue {
      color: var(--dt-text-secondary);
      font-family: var(--dt-font-mono);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class ReportHeaderPreviewComponent {
  readonly report = input.required<Report>();

  protected readonly client = computed(() => {
    const report = this.report();
    switch (report.reportType) {
      case 'CONTRACT_PERIOD':
      case 'CLIENT_SUMMARY':
      case 'TICKET_DETAIL':
        return report.client;
      default:
        // §7.2: a folha de horas sem recorte por cliente não tem destinatário, e inventar um seria
        // afirmar que o documento se destina a quem ele não se destina.
        return null;
    }
  });

  /** O recorte, na forma em que ele aparece no topo do PDF. */
  protected readonly scope = computed(() => {
    const report = this.report();
    switch (report.reportType) {
      case 'CONTRACT_PERIOD':
        return `${report.contract.code} · ${report.period.label}`;
      case 'TICKET_DETAIL':
        return `${report.ticket.key} · ${report.ticket.title}`;
      case 'CLIENT_SUMMARY':
      case 'TIMESHEET':
      case 'PRODUCTIVITY':
        return `${report.range.from} — ${report.range.to}`;
    }
  });

  protected readonly issuerAddress = computed(() => formatAddress(this.report().issuer.address));
}

function formatAddress(address: ReportAddress | null): string | null {
  if (address === null) {
    return null;
  }
  const parts = [
    [address.street, address.number].filter(Boolean).join(', '),
    address.district,
    [address.city, address.state].filter(Boolean).join('/'),
    address.postalCode,
  ].filter((part) => part !== null && part !== '');
  return parts.length === 0 ? null : parts.join(' · ');
}
