import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { messageForCode } from '../../../core/error/error-messages';
import { formatDocument } from '../../../shared/utils/document';
import { ClientSummaryComponent } from '../components/client-summary.component';
import { ContactListComponent } from '../components/contact-list.component';
import { DeactivateDialogComponent } from '../components/deactivate-dialog.component';
import { ClientDetailStore } from '../data/client-detail.store';
import { ContactRequest } from '../data/client.model';

/**
 * Detalhe do cliente — P11, layout L6 (T-003-22).
 *
 * DT-01: cabeçalho com trilha, título, selo de situação e ações. DT-02 / ME-06: ação indisponível por
 * estado ou permissão é **ocultada**, e quem decide é `availableActions`, calculado pelo servidor.
 *
 * FA-09: a exclusão com contrato ativo responde `409 DEVTIME-2401`. A tela responde a isso sugerindo
 * a inativação, que é o que a pessoa realmente queria — o cliente não deve mais receber contratos,
 * sem apagar o histórico de quem já trabalhou para ele.
 */
@Component({
  selector: 'dt-client-detail-page',
  imports: [
    RouterLink,
    ButtonModule,
    ClientSummaryComponent,
    ContactListComponent,
    DeactivateDialogComponent,
    MessageModule,
    SkeletonModule,
    TagModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ClientDetailStore],
  template: `
    <nav
      class="dt-client__breadcrumb"
      aria-label="Trilha de navegação"
      i18n-aria-label="@@breadcrumb.label"
    >
      <a routerLink="/clients" i18n="@@clients.title">Clientes</a>
      @if (store.client(); as client) {
        <span aria-hidden="true">/</span>
        <span>{{ client.name }}</span>
      }
    </nav>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (deletionBlocked()) {
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@client.delete.blocked">
            Este cliente tem contrato ativo e não pode ser excluído. Inative-o para impedir novos
            contratos sem perder o histórico.
          </span>
        </p-message>
      }
      @if (deactivationMessage() !== null) {
        <p-message severity="info" [text]="deactivationMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading() && store.client() === null) {
      <p-skeleton height="12rem" />
    } @else if (store.client(); as client) {
      <header class="dt-client__header">
        <div>
          <h1 class="dt-client__title">
            {{ client.name }}
            <p-tag
              [value]="statusLabel()"
              [severity]="client.status === 'ACTIVE' ? 'success' : 'warn'"
            />
          </h1>
          <p class="dt-client__subtitle">{{ subtitle() }}</p>
        </div>

        <!-- DT-02: nada aqui aparece sem estar nas ações declaradas pelo servidor. -->
        <div class="dt-client__actions">
          @if (store.canEdit()) {
            <p-button
              i18n-label="@@action.edit"
              label="Editar"
              icon="pi pi-pencil"
              [routerLink]="['/clients', client.id, 'edit']"
            />
          }
          @if (store.canDeactivate()) {
            <p-button
              i18n-label="@@client.deactivate.action"
              label="Inativar"
              severity="secondary"
              [outlined]="true"
              (onClick)="openDeactivate()"
            />
          }
          @if (store.canActivate()) {
            <p-button
              i18n-label="@@client.activate.action"
              label="Reativar"
              severity="secondary"
              [outlined]="true"
              [loading]="store.saving()"
              (onClick)="activate()"
            />
          }
          @if (store.canDelete()) {
            <p-button
              i18n-label="@@action.delete"
              label="Excluir"
              severity="danger"
              [text]="true"
              [loading]="store.saving()"
              (onClick)="remove()"
            />
          }
        </div>
      </header>

      <div class="dt-client__grid">
        <section class="dt-client__main">
          <dt-client-summary [summary]="store.summary()" />

          @if (client.activeContractsCount > 0) {
            <p class="dt-client__link">
              <!-- LS-03: o filtro do cliente viaja na URL da lista de contratos. -->
              <a
                [routerLink]="['/contracts']"
                [queryParams]="{ clientId: client.id }"
                i18n="@@client.contracts.link"
              >
                Ver contratos deste cliente
              </a>
            </p>
          }

          <dt-contact-list
            [contacts]="store.contacts()"
            [editable]="store.canEdit()"
            [saving]="store.saving()"
            (created)="addContact($event)"
            (updated)="updateContact($event)"
            (removed)="removeContact($event)"
          />
        </section>

        <!-- DT-04: em telas estreitas o painel desce para o fim do conteúdo. -->
        <aside class="dt-client__aside">
          <h2 class="dt-client__aside-title" i18n="@@client.info.title">Dados do cliente</h2>
          <dl class="dt-client__info">
            @if (client.legalName) {
              <dt i18n="@@client.legalName">Razão social</dt>
              <dd>{{ client.legalName }}</dd>
            }
            @if (document() !== null) {
              <dt i18n="@@client.document">Documento</dt>
              <dd>{{ document() }}</dd>
            }
            @if (client.email) {
              <dt i18n="@@client.email">E-mail</dt>
              <dd>{{ client.email }}</dd>
            }
            @if (client.phone) {
              <dt i18n="@@client.phone">Telefone</dt>
              <dd>{{ client.phone }}</dd>
            }
            @if (client.website) {
              <dt i18n="@@client.website">Site</dt>
              <dd>
                <a [href]="client.website" target="_blank" rel="noopener noreferrer">
                  {{ client.website }}
                </a>
              </dd>
            }
            @if (address() !== null) {
              <dt i18n="@@address.legend">Endereço</dt>
              <dd>{{ address() }}</dd>
            }
            <dt i18n="@@clients.column.contracts">Contratos ativos</dt>
            <dd>{{ client.activeContractsCount }}</dd>
          </dl>

          @if (client.notes) {
            <h2 class="dt-client__aside-title" i18n="@@client.section.notes">Observações</h2>
            <p class="dt-client__notes">{{ client.notes }}</p>
          }
        </aside>
      </div>

      <dt-deactivate-dialog
        [visible]="deactivateOpen()"
        [activeContracts]="client.activeContractsCount"
        [saving]="store.saving()"
        (visibleChange)="deactivateOpen.set($event)"
        (confirmed)="deactivate($event)"
      />
    }
  `,
  styleUrl: './client-detail.page.scss',
})
export class ClientDetailPage {
  private readonly router = inject(Router);

  protected readonly store = inject(ClientDetailStore);

  readonly id = input.required<string>();

  protected readonly deactivateOpen = signal(false);

  private readonly _deactivationMessage = signal<string | null>(null);

  protected readonly deactivationMessage = this._deactivationMessage.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    if (problem === null || problem.code === 'DEVTIME-2401') {
      // `2401` tem tratamento próprio abaixo, com a saída sugerida.
      return null;
    }
    return messageForCode(problem.code, problem.detail);
  });

  /** FA-09: exclusão barrada por contrato ativo (RN-401). */
  protected readonly deletionBlocked = computed(() => this.store.error()?.code === 'DEVTIME-2401');

  protected readonly statusLabel = computed(() =>
    this.store.client()?.status === 'ACTIVE'
      ? $localize`:@@client.status.active:Ativo`
      : $localize`:@@client.status.inactive:Inativo`,
  );

  protected readonly subtitle = computed(() => {
    const count = this.store.client()?.activeContractsCount ?? 0;
    return count === 1
      ? $localize`:@@client.contracts.one:1 contrato ativo`
      : $localize`:@@client.contracts.many:${count}:count: contratos ativos`;
  });

  protected readonly document = computed(() => {
    const client = this.store.client();
    if (client?.documentNumber === undefined || client.documentType === undefined) {
      return null;
    }
    return formatDocument(client.documentType, client.documentNumber);
  });

  protected readonly address = computed(() => {
    const address = this.store.client()?.address;
    if (address === undefined) {
      return null;
    }
    const parts = [
      [address.street, address.number].filter(Boolean).join(', '),
      address.complement,
      address.district,
      [address.city, address.state].filter(Boolean).join(' - '),
      address.postalCode,
    ].filter((part) => part !== undefined && part !== '');
    return parts.length === 0 ? null : parts.join(' · ');
  });

  constructor() {
    // O identificador chega por `input()` do roteador e só está disponível depois da criação do
    // componente; um `effect` também recarrega quando a rota muda de cliente sem recriar a tela.
    effect(() => {
      void this.store.load(this.id());
    });
  }

  protected openDeactivate(): void {
    this._deactivationMessage.set(null);
    this.deactivateOpen.set(true);
  }

  protected async deactivate(event: {
    confirmActiveContracts: boolean;
    reason?: string;
  }): Promise<void> {
    const result = await this.store.deactivate(event.confirmActiveContracts, event.reason);
    if (result !== null) {
      this.deactivateOpen.set(false);
      // RN-407: a mensagem vem do servidor, que sabe quantos contratos seguem operando.
      this._deactivationMessage.set(result.impact.message);
    }
  }

  protected async activate(): Promise<void> {
    this._deactivationMessage.set(null);
    await this.store.activate();
  }

  protected async remove(): Promise<void> {
    if (await this.store.delete()) {
      await this.router.navigate(['/clients']);
    }
  }

  protected async addContact(request: ContactRequest): Promise<void> {
    await this.store.addContact(request);
  }

  protected async updateContact(event: { id: string; request: ContactRequest }): Promise<void> {
    await this.store.updateContact(event.id, event.request);
  }

  protected async removeContact(contactId: string): Promise<void> {
    await this.store.removeContact(contactId);
  }
}
