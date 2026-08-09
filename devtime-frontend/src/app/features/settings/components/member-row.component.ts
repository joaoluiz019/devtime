import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { Role } from '../../../core/auth/auth.model';
import { ROLE_LABELS } from '../../../shared/models/role.model';
import { Member, MEMBERSHIP_STATE_LABELS, MemberAction } from '../data/member.model';
import { RoleSelectorComponent } from './role-selector.component';

/**
 * Linha de membro — `dt-member-row` (T-002-36).
 *
 * As ações vêm de `availableActions`, calculado pelo servidor a partir do papel de quem olha e do
 * papel do alvo (ME-06). A tela **não** recalcula a matriz: reproduzi-la aqui ofereceria botões que
 * resultariam em `DEVTIME-1104` (`ADMIN` sobre `OWNER`) ou `DEVTIME-2456` (próprio papel), e
 * divergiria na primeira mudança da nota ¹ de `permissions.md`.
 *
 * RN-455 é a única regra antecipada na tela: o último proprietário ativo aparece com as ações
 * bloqueadas e **com o motivo à vista**. É a única situação irrecuperável do produto — um tenant
 * sem proprietário não tem quem o conserte —, e descobri-la pelo erro depois do clique não ensina
 * nada a quem já estava com o dedo no botão.
 */
@Component({
  selector: 'dt-member-row',
  imports: [ButtonModule, TagModule, TooltipModule, RoleSelectorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <li class="dt-member-row">
      <div class="dt-member-row__identity">
        @if (member().user.avatarUrl !== null) {
          <img class="dt-member-row__avatar" [src]="member().user.avatarUrl" alt="" />
        } @else {
          <span class="dt-member-row__avatar dt-member-row__avatar--initials" aria-hidden="true">
            {{ initials() }}
          </span>
        }
        <span class="dt-member-row__names">
          <span class="dt-member-row__name">
            {{ member().user.displayName ?? member().user.fullName }}
            @if (isSelf()) {
              <span class="dt-member-row__self" i18n="@@member.self">você</span>
            }
          </span>
          <small class="dt-member-row__email">{{ member().user.email ?? '—' }}</small>
        </span>
      </div>

      <div class="dt-member-row__role">
        @if (canChangeRole()) {
          <dt-role-selector
            [current]="member().role"
            [allowOwner]="allowOwner()"
            [inputId]="'member-role-' + member().id"
            [label]="roleLabel"
            [disabled]="saving()"
            (changed)="roleChanged.emit($event)"
          />
        } @else {
          <span class="dt-member-row__role-static">{{ roleName() }}</span>
        }
      </div>

      <p-tag [severity]="statusSeverity()" [value]="statusLabel()" />

      <div class="dt-member-row__actions">
        @if (lastOwner()) {
          <!-- RN-455: o motivo fica visível, não escondido atrás de um botão desabilitado. -->
          <small class="dt-member-row__blocked" i18n="@@member.lastOwner">
            Último proprietário: a organização precisa de um.
          </small>
        } @else {
          @if (can('SUSPEND')) {
            <p-button
              i18n-label="@@member.suspend"
              label="Suspender"
              icon="pi pi-pause"
              severity="secondary"
              [text]="true"
              [disabled]="saving()"
              i18n-pTooltip="@@member.suspend.tooltip"
              pTooltip="O acesso é bloqueado e o cronômetro ativo é descartado. As horas registradas permanecem."
              (onClick)="suspended.emit()"
            />
          }
          @if (can('REACTIVATE')) {
            <p-button
              i18n-label="@@member.reactivate"
              label="Reativar"
              icon="pi pi-play"
              severity="secondary"
              [text]="true"
              [disabled]="saving()"
              (onClick)="reactivated.emit()"
            />
          }
          @if (can('REMOVE')) {
            <p-button
              i18n-label="@@member.remove"
              label="Remover"
              icon="pi pi-trash"
              severity="danger"
              [text]="true"
              [disabled]="saving()"
              (onClick)="removed.emit()"
            />
          }
        }
      </div>
    </li>
  `,
  styleUrl: './member-row.component.scss',
})
export class MemberRowComponent {
  readonly member = input.required<Member>();
  /** RN-455 avaliada pelo store, que enxerga a lista inteira. */
  readonly lastOwner = input(false);
  readonly isSelf = input(false);
  readonly allowOwner = input(true);
  readonly saving = input(false);

  readonly roleChanged = output<Role>();
  readonly suspended = output<void>();
  readonly reactivated = output<void>();
  readonly removed = output<void>();

  protected readonly roleLabel = $localize`:@@member.role:Papel`;

  protected readonly roleName = computed(() => ROLE_LABELS[this.member().role]);

  protected readonly statusLabel = computed(() => MEMBERSHIP_STATE_LABELS[this.member().status]);

  protected readonly canChangeRole = computed(() => this.can('CHANGE_ROLE') && !this.lastOwner());

  protected readonly initials = computed(() => {
    const name = this.member().user.displayName ?? this.member().user.fullName;
    const parts = name.trim().split(/\s+/).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join('');
  });

  protected statusSeverity(): 'success' | 'warn' | 'info' | 'secondary' {
    switch (this.member().status) {
      case 'ACTIVE':
        return 'success';
      case 'SUSPENDED':
        return 'warn';
      case 'INVITED':
        return 'info';
      case 'REMOVED':
        return 'secondary';
    }
  }

  protected can(action: MemberAction): boolean {
    return this.member().availableActions.includes(action);
  }
}
