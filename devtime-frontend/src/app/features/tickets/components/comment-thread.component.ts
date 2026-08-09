import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TextareaModule } from 'primeng/textarea';
import { MarkdownViewComponent } from '../../../shared/components/markdown/markdown-view.component';
import { COMMENT_BODY_MAX, SystemCommentTrigger, TicketComment } from '../data/comment.model';

/**
 * Conversa do ticket — `dt-comment-thread` (tickets.md §10.1, T-014).
 *
 * Comentários de sistema (RN-815) aparecem na mesma linha do tempo das pessoas, mas visualmente
 * distintos: eles registram o que **aconteceu** com o ticket, não o que alguém disse. Separá-los em
 * outra aba quebraria a leitura cronológica, que é o motivo de a conversa existir.
 *
 * Editar e excluir aparecem a partir de `canEdit`/`canDelete` do servidor. A janela de 24h de
 * RN-812 não é recalculada aqui — a tela ofereceria o botão até o relógio local achar que expirou, e
 * o servidor recusaria com `DEVTIME-2706` antes ou depois disso.
 */
@Component({
  selector: 'dt-comment-thread',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    FormsModule,
    ButtonModule,
    MessageModule,
    SkeletonModule,
    TextareaModule,
    MarkdownViewComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="dt-comments">
      <h2 class="dt-comments__title">
        <span i18n="@@comments.title">Conversa</span>
        <span class="dt-comments__count">{{ total() }}</span>
      </h2>

      @if (canComment()) {
        <form class="dt-comments__composer" (ngSubmit)="submit()">
          <label class="dt-comments__label" for="comment-body" i18n="@@comments.new">
            Escrever um comentário
          </label>
          <textarea
            id="comment-body"
            pTextarea
            rows="3"
            [maxlength]="maxLength"
            [ngModel]="draft()"
            [ngModelOptions]="{ standalone: true }"
            (ngModelChange)="draft.set($event)"
          ></textarea>
          <div class="dt-comments__composer-actions">
            <small class="dt-comments__hint" i18n="@@comments.mentionHint">
              Use @ para mencionar alguém da equipe.
            </small>
            <p-button
              type="submit"
              i18n-label="@@comments.submit"
              label="Comentar"
              icon="pi pi-send"
              [disabled]="draft().trim() === ''"
              [loading]="saving()"
            />
          </div>
        </form>
      }

      @if (loading() && comments().length === 0) {
        <p-skeleton height="8rem" />
      } @else if (comments().length === 0) {
        <p class="dt-comments__empty" i18n="@@comments.empty">
          Nenhum comentário ainda. O histórico de mudanças do ticket também aparece aqui.
        </p>
      } @else {
        <ul class="dt-comments__list" role="list">
          @for (comment of comments(); track comment.id) {
            <li class="dt-comments__item">
              <ng-container
                *ngTemplateOutlet="entry; context: { $implicit: comment, reply: false }"
              />

              @if (comment.replies.length > 0) {
                <ul class="dt-comments__replies" role="list">
                  @for (reply of comment.replies; track reply.id) {
                    <li>
                      <ng-container
                        *ngTemplateOutlet="entry; context: { $implicit: reply, reply: true }"
                      />
                    </li>
                  }
                </ul>
              }

              @if (canComment() && replyingTo() === comment.id) {
                <form class="dt-comments__reply-form" (ngSubmit)="submitReply(comment.id)">
                  <textarea
                    pTextarea
                    rows="2"
                    [maxlength]="maxLength"
                    i18n-aria-label="@@comments.reply"
                    aria-label="Responder"
                    [ngModel]="replyDraft()"
                    [ngModelOptions]="{ standalone: true }"
                    (ngModelChange)="replyDraft.set($event)"
                  ></textarea>
                  <div class="dt-comments__composer-actions">
                    <p-button
                      type="button"
                      i18n-label="@@action.cancel"
                      label="Cancelar"
                      severity="secondary"
                      [text]="true"
                      (onClick)="cancelReply()"
                    />
                    <p-button
                      type="submit"
                      i18n-label="@@comments.reply.submit"
                      label="Enviar resposta"
                      [disabled]="replyDraft().trim() === ''"
                      [loading]="saving()"
                    />
                  </div>
                </form>
              }
            </li>
          }
        </ul>

        @if (hasMore()) {
          <p-button
            i18n-label="@@comments.loadMore"
            label="Carregar comentários anteriores"
            severity="secondary"
            [text]="true"
            [loading]="loading()"
            (onClick)="loadMore.emit()"
          />
        }
      }
    </section>

    <ng-template #entry let-comment let-reply="reply">
      @if (comment.isSystem) {
        <!-- RN-815: o que aconteceu com o ticket, não o que alguém disse. -->
        <p class="dt-comments__system">
          <i class="pi pi-info-circle" aria-hidden="true"></i>
          <span>{{ systemLabel(comment.systemTrigger) }}</span>
          <span class="dt-comments__meta">{{ comment.createdAt | date: 'short' }}</span>
        </p>
      } @else {
        <article class="dt-comments__entry" [class.dt-comments__entry--reply]="reply">
          <header class="dt-comments__entry-header">
            <strong>{{ comment.author?.name ?? removedAuthor }}</strong>
            <span class="dt-comments__meta">{{ comment.createdAt | date: 'short' }}</span>
            @if (comment.editedAt !== null) {
              <span class="dt-comments__meta" i18n="@@comments.edited">editado</span>
            }
          </header>

          @if (editingId() === comment.id) {
            <form class="dt-comments__edit" (ngSubmit)="submitEdit(comment)">
              <textarea
                pTextarea
                rows="3"
                [maxlength]="maxLength"
                i18n-aria-label="@@comments.edit"
                aria-label="Editar comentário"
                [ngModel]="editDraft()"
                [ngModelOptions]="{ standalone: true }"
                (ngModelChange)="editDraft.set($event)"
              ></textarea>
              <div class="dt-comments__composer-actions">
                <p-button
                  type="button"
                  i18n-label="@@action.cancel"
                  label="Cancelar"
                  severity="secondary"
                  [text]="true"
                  (onClick)="cancelEdit()"
                />
                <p-button
                  type="submit"
                  i18n-label="@@action.save"
                  label="Salvar"
                  [disabled]="editDraft().trim() === ''"
                  [loading]="saving()"
                />
              </div>
            </form>
          } @else {
            <dt-markdown-view [source]="comment.body" />
          }

          <footer class="dt-comments__entry-actions">
            @if (canComment() && !reply) {
              <p-button
                i18n-label="@@comments.reply"
                label="Responder"
                severity="secondary"
                [text]="true"
                (onClick)="startReply(comment.id)"
              />
            }
            @if (comment.canEdit && editingId() !== comment.id) {
              <p-button
                i18n-label="@@action.edit"
                label="Editar"
                severity="secondary"
                [text]="true"
                (onClick)="startEdit(comment)"
              />
            }
            @if (comment.canDelete) {
              <p-button
                i18n-label="@@action.delete"
                label="Excluir"
                severity="danger"
                [text]="true"
                (onClick)="removed.emit(comment.id)"
              />
            }
          </footer>
        </article>
      }
    </ng-template>
  `,
  styleUrl: './comment-thread.component.scss',
})
export class CommentThreadComponent {
  readonly comments = input.required<readonly TicketComment[]>();
  readonly total = input(0);
  readonly hasMore = input(false);
  readonly loading = input(false);
  readonly saving = input(false);
  readonly canComment = input(true);

  readonly created = output<{ body: string; parentCommentId?: string }>();
  readonly edited = output<{ comment: TicketComment; body: string }>();
  readonly removed = output<string>();
  readonly loadMore = output<void>();

  protected readonly maxLength = COMMENT_BODY_MAX;
  protected readonly removedAuthor = $localize`:@@user.removed:Usuário Removido`;

  protected readonly draft = signal('');
  protected readonly replyDraft = signal('');
  protected readonly editDraft = signal('');
  protected readonly replyingTo = signal<string | null>(null);
  protected readonly editingId = signal<string | null>(null);

  protected readonly systemLabels = computed<Readonly<Record<SystemCommentTrigger, string>>>(
    () => ({
      STATUS_CHANGED: $localize`:@@comments.system.status:A situação do ticket mudou.`,
      ASSIGNEE_CHANGED: $localize`:@@comments.system.assignee:O responsável pelo ticket mudou.`,
      CONTRACT_MOVED: $localize`:@@comments.system.contract:O ticket foi movido de contrato.`,
    }),
  );

  protected systemLabel(trigger: SystemCommentTrigger | null): string {
    return trigger === null
      ? $localize`:@@comments.system.generic:Registro automático do ticket.`
      : this.systemLabels()[trigger];
  }

  protected submit(): void {
    const body = this.draft().trim();
    if (body === '') {
      return;
    }
    this.created.emit({ body });
    this.draft.set('');
  }

  protected startReply(id: string): void {
    this.replyingTo.set(id);
    this.replyDraft.set('');
  }

  protected cancelReply(): void {
    this.replyingTo.set(null);
    this.replyDraft.set('');
  }

  protected submitReply(parentCommentId: string): void {
    const body = this.replyDraft().trim();
    if (body === '') {
      return;
    }
    this.created.emit({ body, parentCommentId });
    this.cancelReply();
  }

  protected startEdit(comment: TicketComment): void {
    this.editingId.set(comment.id);
    this.editDraft.set(comment.body);
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
    this.editDraft.set('');
  }

  protected submitEdit(comment: TicketComment): void {
    const body = this.editDraft().trim();
    if (body === '') {
      return;
    }
    this.edited.emit({ comment, body });
    this.cancelEdit();
  }
}
