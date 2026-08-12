import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { MarkdownViewComponent } from '../../../shared/components/markdown/markdown-view.component';
import {
  TicketPriorityBadgeComponent,
  TicketStatusBadgeComponent,
  ticketStatusLabel,
} from '../../../shared/components/ticket-badges/ticket-badges.component';
import { ContractLookupApi } from '../../../shared/data/contract-lookup.api';
import { MemberLookupApi, MemberOption } from '../../../shared/data/member-lookup.api';
import { TimerQuickStartComponent } from '../../../core/timer/timer-quick-start.component';
import { AttachmentListComponent } from '../components/attachment-list.component';
import { CommentThreadComponent } from '../components/comment-thread.component';
import { Attachment } from '../data/attachment.model';
import { AttachmentStore } from '../data/attachment.store';
import { TicketComment } from '../data/comment.model';
import { CommentStore } from '../data/comment.store';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { BlockReasonDialogComponent } from '../components/block-reason-dialog.component';
import {
  MoveContractDialogComponent,
  MoveContractOption,
} from '../components/move-contract-dialog.component';
import { TicketProgressComponent } from '../components/ticket-progress.component';
import { TicketTimelineComponent } from '../components/ticket-timeline.component';
import { TicketDetailStore } from '../data/ticket-detail.store';
import { TicketStatus } from '../data/ticket.model';

/**
 * Detalhe do ticket — P19, layout L6 (T-007-31, T-007-32).
 *
 * ME-06 / nota ⁴ de `permissions.md`: as transições vêm de `availableTransitions`, já filtradas pelo
 * servidor conforme o papel; as ações de edição e exclusão dependem também de permissão e de o
 * ticket ser próprio, o que o backend resolve — a tela apenas não oferece o que não veio.
 *
 * RN-306: contrato que não aceita horas é anunciado no topo. Descobrir isso só ao tentar registrar
 * tempo é descobrir tarde.
 */
@Component({
  selector: 'dt-ticket-detail-page',
  imports: [
    FormsModule,
    RouterLink,
    BlockReasonDialogComponent,
    ButtonModule,
    DurationPipe,
    MarkdownViewComponent,
    MessageModule,
    MoveContractDialogComponent,
    SelectModule,
    SkeletonModule,
    TagModule,
    TicketPriorityBadgeComponent,
    TicketProgressComponent,
    TicketStatusBadgeComponent,
    TicketTimelineComponent,
    TimerQuickStartComponent,
    AttachmentListComponent,
    CommentThreadComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TicketDetailStore, CommentStore, AttachmentStore],
  template: `
    <nav
      class="dt-ticket__breadcrumb"
      i18n-aria-label="@@breadcrumb.label"
      aria-label="Trilha de navegação"
    >
      <a routerLink="/tickets" i18n="@@tickets.title">Tickets</a>
      @if (store.ticket(); as ticket) {
        <span aria-hidden="true">/</span>
        <span>{{ ticket.key }}</span>
      }
    </nav>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (moveNotice() !== null) {
        <p-message severity="info" [text]="moveNotice()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (store.loading() && store.ticket() === null) {
      <p-skeleton height="14rem" />
    } @else if (store.ticket(); as ticket) {
      <header class="dt-ticket__header">
        <div>
          <p class="dt-ticket__key">{{ ticket.key }}</p>
          <h1 class="dt-ticket__title">{{ ticket.title }}</h1>
          <div class="dt-ticket__badges">
            <dt-ticket-status-badge [status]="ticket.status" />
            <dt-ticket-priority-badge [priority]="ticket.priority" />
            @for (tag of ticket.tags; track tag.id) {
              <p-tag [value]="tag.name" severity="secondary" />
            }
          </div>
        </div>

        <div class="dt-ticket__actions">
          <!-- O início do cronômetro fica onde o trabalho é escolhido, não em outra tela. -->
          <dt-timer-quick-start [ticketId]="ticket.id" [ticketKey]="ticket.key" />

          @if (canUpdate()) {
            <p-button
              i18n-label="@@action.edit"
              label="Editar"
              icon="pi pi-pencil"
              [routerLink]="['/tickets', ticket.id, 'edit']"
            />
          }
          @if (canUpdate() && moveOptions().length > 0) {
            <p-button
              i18n-label="@@ticket.move.action"
              label="Mover de contrato"
              severity="secondary"
              [outlined]="true"
              (onClick)="moveDialogOpen.set(true)"
            />
          }
          @for (target of store.availableTransitions(); track target) {
            <p-button
              [label]="transitionLabel(target)"
              severity="secondary"
              [outlined]="true"
              [loading]="store.saving()"
              (onClick)="transition(target)"
            />
          }
        </div>
      </header>

      @if (!store.acceptsWorkLogs()) {
        <!-- RN-306: sem isto, a pessoa só descobre ao tentar lançar horas. -->
        <p-message severity="warn" styleClass="w-full mb-3">
          <span i18n="@@ticket.contractClosed">
            O contrato {{ ticket.contract.code }} não aceita novos registros de horas.
          </span>
        </p-message>
      }

      @if (ticket.status === 'BLOCKED' && ticket.blockReason) {
        <p-message severity="error" styleClass="w-full mb-3">
          <span i18n="@@ticket.blockedReason">Bloqueado: {{ ticket.blockReason }}</span>
        </p-message>
      }

      <div class="dt-ticket__grid">
        <section class="dt-ticket__main">
          @if (ticket.description) {
            <dt-markdown-view [source]="ticket.description" />
          } @else {
            <p class="dt-ticket__empty" i18n="@@ticket.noDescription">Sem descrição.</p>
          }

          <dt-ticket-timeline
            [events]="store.events()"
            [hasMore]="store.hasMore()"
            [loading]="store.loadingActivity()"
            (loadMore)="loadMore()"
          />

          <dt-attachment-list
            [attachments]="attachments.attachments()"
            [maxCount]="attachments.maxCount()"
            [quota]="attachments.quota()"
            [quotaWarning]="attachments.quotaWarning()"
            [canUpload]="canAttach()"
            [uploading]="attachments.uploading()"
            [full]="attachments.isFull()"
            [localError]="attachments.localError()"
            (selected)="uploadAttachment($event)"
            (downloaded)="downloadAttachment($event)"
            (removed)="removeAttachment($event)"
          />

          <dt-comment-thread
            [comments]="comments.comments()"
            [total]="comments.total()"
            [hasMore]="comments.hasMore()"
            [loading]="comments.loading()"
            [saving]="comments.saving()"
            [canComment]="canComment()"
            (created)="createComment($event)"
            (edited)="editComment($event)"
            (removed)="removeComment($event)"
            (loadMore)="loadMoreComments()"
          />
        </section>

        <aside class="dt-ticket__aside">
          <dl class="dt-ticket__info">
            <dt i18n="@@ticket.contract">Contrato</dt>
            <dd>
              <a [routerLink]="['/contracts', ticket.contract.id]">{{ ticket.contract.code }}</a>
            </dd>
            <dt i18n="@@ticket.client">Cliente</dt>
            <dd>
              <a [routerLink]="['/clients', ticket.client.id]">{{ ticket.client.name }}</a>
            </dd>
            <dt i18n="@@ticket.reporter">Relator</dt>
            <dd>{{ ticket.reporter?.name ?? '—' }}</dd>
            @if (ticket.dueDate) {
              <dt i18n="@@ticket.dueDate">Prazo</dt>
              <dd>{{ ticket.dueDate }}</dd>
            }
            <dt i18n="@@ticket.billable">Faturável</dt>
            <dd>{{ ticket.billableMinutes | duration }}</dd>
          </dl>

          <div class="dt-ticket__assignee">
            <label for="ticket-assignee" i18n="@@ticket.assignee">Responsável</label>
            <p-select
              inputId="ticket-assignee"
              [options]="assignees()"
              optionLabel="name"
              optionValue="id"
              [ngModel]="ticket.assignee?.id ?? null"
              [showClear]="true"
              [disabled]="!canUpdate() || store.saving()"
              i18n-placeholder="@@ticket.unassigned"
              placeholder="Sem responsável"
              (onChange)="assign($event.value)"
            />
          </div>

          <dt-ticket-progress
            [spentMinutes]="ticket.spentMinutes"
            [estimatedMinutes]="ticket.estimatedMinutes"
            [isOverEstimate]="ticket.isOverEstimate"
          />
        </aside>
      </div>

      <dt-block-reason-dialog
        [visible]="blockDialogOpen()"
        [saving]="store.saving()"
        (visibleChange)="blockDialogOpen.set($event)"
        (confirmed)="confirmBlock($event)"
      />

      <dt-move-contract-dialog
        [visible]="moveDialogOpen()"
        [currentKey]="ticket.key"
        [options]="moveOptions()"
        [saving]="store.saving()"
        (visibleChange)="moveDialogOpen.set($event)"
        (confirmed)="confirmMove($event)"
      />
    }
  `,
  styleUrl: './ticket-detail.page.scss',
})
export class TicketDetailPage {
  private readonly authStore = inject(AuthStore);
  private readonly memberLookup = inject(MemberLookupApi);
  private readonly contractLookup = inject(ContractLookupApi);
  private readonly router = inject(Router);

  protected readonly store = inject(TicketDetailStore);
  protected readonly comments = inject(CommentStore);
  protected readonly attachments = inject(AttachmentStore);

  readonly id = input.required<string>();

  protected readonly blockDialogOpen = signal(false);
  protected readonly moveDialogOpen = signal(false);

  private readonly _assignees = signal<readonly MemberOption[]>([]);
  private readonly _moveNotice = signal<string | null>(null);
  private readonly _moveOptions = signal<readonly MoveContractOption[]>([]);

  protected readonly assignees = computed(() => [...this._assignees()]);
  protected readonly moveNotice = this._moveNotice.asReadonly();

  /**
   * RN-305: só contratos do **mesmo cliente** são destino possível.
   *
   * O contrato atual é retirado da lista: mover para onde já se está não é uma operação.
   */
  protected readonly moveOptions = computed(() => [...this._moveOptions()]);

  /**
   * `TICKET_UPDATE` não existe no catálogo do servidor — lá a permissão é dividida em
   * `TICKET_UPDATE_ANY` e `TICKET_UPDATE_OWN` (`permissions.md` §7). O nome inexistente fazia esta
   * verificação devolver **falso para todos os papéis, inclusive OWNER**: a edição do ticket ficava
   * bloqueada na interface, sem nenhuma requisição sair — o tipo de falha que não aparece em log
   * algum, porque nada chega ao backend.
   */
  protected readonly canUpdate = computed(() =>
    this.authStore.hasAnyPermission(['TICKET_UPDATE_ANY', 'TICKET_UPDATE_OWN']),
  );

  protected readonly canComment = computed(() => this.authStore.hasPermission('COMMENT_CREATE'));

  protected readonly canAttach = computed(() => this.authStore.hasPermission('ATTACHMENT_UPLOAD'));

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    effect(() => {
      const id = this.id();
      void this.store.load(id);
      // Conversa e anexos pertencem ao ticket aberto: trocar de ticket recarrega os três juntos.
      void this.comments.load(id);
      void this.attachments.load(id);
    });
    void this.loadAssignees();

    effect(() => {
      const clientId = this.store.ticket()?.client.id;
      if (clientId !== undefined) {
        void this.loadMoveOptions(clientId, this.store.ticket()?.contract.id);
      }
    });
  }

  private async loadMoveOptions(clientId: string, currentContractId?: string): Promise<void> {
    try {
      const contracts = await firstValueFrom(this.contractLookup.search(clientId));
      this._moveOptions.set(
        contracts
          .filter((contract) => contract.id !== currentContractId)
          .map((contract) => ({ id: contract.id, code: contract.code, name: contract.name })),
      );
    } catch {
      this._moveOptions.set([]);
    }
  }

  protected async confirmMove(event: {
    targetContractId: string;
    confirmed: boolean;
  }): Promise<void> {
    const result = await this.store.moveContract(event.targetContractId, event.confirmed);
    if (result !== null) {
      this.moveDialogOpen.set(false);
      // INV-TKT-01: o aviso do servidor explica que a chave não mudou.
      this._moveNotice.set(
        result.notice ??
          $localize`:@@ticket.move.done:Ticket movido. A chave ${result.key}:key: permanece a mesma.`,
      );
    }
  }

  /** RN-304: apenas memberships ativos podem receber atribuição. */
  private async loadAssignees(): Promise<void> {
    try {
      this._assignees.set(await firstValueFrom(this.memberLookup.search()));
    } catch {
      this._assignees.set([]);
    }
  }

  protected transitionLabel(status: TicketStatus): string {
    return $localize`:@@ticket.moveTo:Mover para ${ticketStatusLabel(status)}:status:`;
  }

  protected async transition(target: TicketStatus): Promise<void> {
    // RN-308: bloquear exige motivo, pedido antes da chamada.
    if (target === 'BLOCKED') {
      this.blockDialogOpen.set(true);
      return;
    }
    await this.store.transition(target);
  }

  protected async confirmBlock(reason: string): Promise<void> {
    if (await this.store.transition('BLOCKED', reason)) {
      this.blockDialogOpen.set(false);
    }
  }

  protected async assign(assigneeId: string | null): Promise<void> {
    await this.store.assign(assigneeId);
  }

  protected async loadMore(): Promise<void> {
    await this.store.loadActivity();
  }

  protected async remove(): Promise<void> {
    if (await this.store.delete()) {
      await this.router.navigate(['/tickets']);
    }
  }

  protected async createComment(event: { body: string; parentCommentId?: string }): Promise<void> {
    await this.comments.create(this.id(), event.body, event.parentCommentId);
  }

  protected async editComment(event: { comment: TicketComment; body: string }): Promise<void> {
    await this.comments.update(this.id(), event.comment, event.body);
  }

  protected async removeComment(commentId: string): Promise<void> {
    await this.comments.remove(this.id(), commentId);
  }

  protected async loadMoreComments(): Promise<void> {
    await this.comments.loadMore(this.id());
  }

  protected async uploadAttachment(file: File): Promise<void> {
    await this.attachments.upload(this.id(), file);
  }

  protected async downloadAttachment(attachment: Attachment): Promise<void> {
    await this.attachments.download(attachment);
  }

  protected async removeAttachment(attachmentId: string): Promise<void> {
    await this.attachments.remove(this.id(), attachmentId);
  }
}
