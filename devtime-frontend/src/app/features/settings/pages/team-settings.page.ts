import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { TagModule } from 'primeng/tag';
import { Role } from '../../../core/auth/auth.model';
import { AuthStore } from '../../../core/auth/auth.store';
import { messageForCode } from '../../../core/error/error-messages';
import { InviteMemberDialogComponent } from '../components/invite-member-dialog.component';
import { MemberRowComponent } from '../components/member-row.component';
import { RemoveMemberDialogComponent } from '../components/remove-member-dialog.component';
import { MemberStore } from '../data/member.store';
import { ROLE_LABELS } from '../../../shared/models/role.model';
import { InvitationRequest, Member } from '../data/member.model';

/**
 * Equipe — P32, layout L9 (T-002-37).
 *
 * Duas listas, e não uma: membros com vínculo aceito e convites pendentes. O convite é um estado
 * diferente de "pessoa com acesso" — ele expira, é reenviado e é revogado —, e misturá-lo à equipe
 * faria a organização parecer maior do que é para quem confere quem tem acesso.
 *
 * Nenhuma ação é oferecida por dedução da tela: `availableActions` chega calculado pelo servidor
 * (ME-06). A única regra antecipada é RN-455, e apenas para **explicar** — o último proprietário
 * aparece com o motivo à vista, porque um tenant sem proprietário é irrecuperável.
 */
@Component({
  selector: 'dt-team-settings-page',
  imports: [
    DatePipe,
    ButtonModule,
    MessageModule,
    SkeletonModule,
    TagModule,
    InviteMemberDialogComponent,
    MemberRowComponent,
    RemoveMemberDialogComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [MemberStore],
  template: `
    <h2 class="dt-setting__title" i18n="@@settings.team">Equipe</h2>
    <p class="dt-setting__subtitle" i18n="@@settings.team.subtitle">
      Quem tem acesso a esta organização e com qual alcance.
    </p>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
      @if (store.lastRemoval(); as removal) {
        <!-- RN-458 tornada visível: os números provam que nada do trabalho foi perdido. -->
        <p-message severity="success" styleClass="w-full mb-3">
          <span i18n="@@member.removed.impact">
            Membro removido. {{ removal.workLogsPreserved }} registro(s) de horas preservado(s) e
            {{ removal.ticketsReassigned }} ticket(s) reatribuído(s).
          </span>
        </p-message>
      }
    </div>

    @if (store.loading()) {
      <p-skeleton height="10rem" />
    } @else {
      <ul class="dt-team__list" role="list">
        @for (member of store.activeMembers(); track member.id) {
          <dt-member-row
            [member]="member"
            [lastOwner]="isLastOwner()(member)"
            [isSelf]="member.user.id === currentUserId()"
            [allowOwner]="canGrantOwner()"
            [saving]="store.saving()"
            (roleChanged)="changeRole(member, $event)"
            (suspended)="suspend(member)"
            (reactivated)="reactivate(member)"
            (removed)="openRemove(member)"
          />
        }
      </ul>

      @if (canInvite()) {
        <p-button
          i18n-label="@@member.invite.open"
          label="Convidar membro"
          icon="pi pi-user-plus"
          severity="secondary"
          [outlined]="true"
          (onClick)="inviteVisible.set(true)"
        />
      }

      <section class="dt-team__invitations">
        <h3 class="dt-team__section-title" i18n="@@member.invitations.title">Convites pendentes</h3>

        @if (store.invitations().length === 0) {
          <p class="dt-setting__hint" i18n="@@member.invitations.empty">
            Nenhum convite aguardando aceite.
          </p>
        } @else {
          <ul class="dt-team__list" role="list">
            @for (invitation of store.invitations(); track invitation.id) {
              <li class="dt-team__invitation">
                <span class="dt-team__invitation-identity">
                  <span class="dt-team__invitation-email">{{ invitation.email }}</span>
                  <small class="dt-team__invitation-meta">
                    {{ roleName(invitation.role) }} ·
                    <span i18n="@@member.invitation.expires">
                      expira em {{ invitation.expiresAt | date: 'short' }}
                    </span>
                  </small>
                </span>

                <p-tag severity="info" i18n-value="@@member.status.invited" value="Convidado" />

                @if (canInvite()) {
                  <span class="dt-team__invitation-actions">
                    <p-button
                      i18n-label="@@member.invitation.resend"
                      label="Reenviar"
                      icon="pi pi-refresh"
                      severity="secondary"
                      [text]="true"
                      [disabled]="store.saving()"
                      i18n-pTooltip="@@member.invitation.resend.tooltip"
                      pTooltip="Emite um novo link e invalida o anterior."
                      (onClick)="resend(invitation.id)"
                    />
                    <p-button
                      i18n-label="@@member.invitation.revoke"
                      label="Revogar"
                      icon="pi pi-times"
                      severity="danger"
                      [text]="true"
                      [disabled]="store.saving()"
                      (onClick)="revoke(invitation.id)"
                    />
                  </span>
                }
              </li>
            }
          </ul>
        }
      </section>
    }

    <dt-invite-member-dialog
      [visible]="inviteVisible()"
      (visibleChange)="inviteVisible.set($event)"
      [allowOwner]="canGrantOwner()"
      [saving]="store.saving()"
      (invited)="invite($event)"
    />

    <dt-remove-member-dialog
      [visible]="removeTarget() !== null"
      (visibleChange)="onRemoveVisibleChange($event)"
      [member]="removeTarget()"
      [members]="store.activeMembers()"
      [saving]="store.saving()"
      (confirmed)="remove($event)"
    />
  `,
  styleUrls: ['./settings-form.scss', './team-settings.page.scss'],
})
export class TeamSettingsPage {
  private readonly authStore = inject(AuthStore);

  protected readonly store = inject(MemberStore);

  protected readonly inviteVisible = signal(false);
  protected readonly removeTarget = signal<Member | null>(null);

  protected readonly isLastOwner = this.store.isLastOwner;

  protected readonly currentUserId = computed(() => this.authStore.user()?.id ?? null);

  protected readonly canInvite = computed(() => this.authStore.hasPermission('MEMBER_INVITE'));

  /** Nota ¹ de `permissions.md`: só um `OWNER` concede `OWNER`. */
  protected readonly canGrantOwner = computed(() => this.authStore.role() === 'OWNER');

  protected readonly errorMessage = computed(() => {
    const problem = this.store.error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  constructor() {
    void this.store.load();
  }

  protected roleName(role: Role): string {
    return ROLE_LABELS[role];
  }

  protected async invite(request: InvitationRequest): Promise<void> {
    if (await this.store.invite(request)) {
      this.inviteVisible.set(false);
    }
  }

  protected async resend(id: string): Promise<void> {
    await this.store.resendInvitation(id);
  }

  protected async revoke(id: string): Promise<void> {
    await this.store.revokeInvitation(id);
  }

  protected async changeRole(member: Member, role: Role): Promise<void> {
    await this.store.changeRole(member, role);
  }

  protected async suspend(member: Member): Promise<void> {
    await this.store.suspend(member.id);
  }

  protected async reactivate(member: Member): Promise<void> {
    await this.store.reactivate(member.id);
  }

  protected openRemove(member: Member): void {
    this.removeTarget.set(member);
  }

  protected onRemoveVisibleChange(visible: boolean): void {
    if (!visible) {
      this.removeTarget.set(null);
    }
  }

  protected async remove(reassignTicketsTo: string | undefined): Promise<void> {
    const target = this.removeTarget();
    if (target === null) {
      return;
    }
    if (await this.store.remove(target.id, reassignTicketsTo)) {
      this.removeTarget.set(null);
    }
  }
}
