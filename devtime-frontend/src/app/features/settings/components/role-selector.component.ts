import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { Role } from '../../../core/auth/auth.model';
import {
  ASSIGNABLE_ROLES,
  ROLE_DESCRIPTIONS,
  ROLE_LABELS,
} from '../../../shared/models/role.model';

/**
 * Seleção de papel — `dt-role-selector` (T-002-36).
 *
 * A descrição de cada papel fica visível, não em tooltip: quem escolhe está decidindo o alcance de
 * outra pessoa sobre dados de clientes e valores, e "Gestor" e "Administrador" não se distinguem
 * pelo nome.
 *
 * Nota ¹ de `permissions.md`: `ADMIN` não concede `OWNER`. O papel some da lista quando
 * `allowOwner` é falso — oferecê-lo produziria `DEVTIME-1104` depois do formulário preenchido.
 */
@Component({
  selector: 'dt-role-selector',
  imports: [FormsModule, SelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="dt-role-selector__label" [for]="inputId()">{{ label() }}</label>
    <p-select
      [inputId]="inputId()"
      [options]="options()"
      [ngModel]="current()"
      optionLabel="label"
      optionValue="value"
      [disabled]="disabled()"
      [ariaLabel]="label()"
      (onChange)="changed.emit($event.value)"
    >
      <ng-template #item let-option>
        <div class="dt-role-selector__option">
          <span class="dt-role-selector__option-label">{{ option.label }}</span>
          <small class="dt-role-selector__option-description">{{ option.description }}</small>
        </div>
      </ng-template>
    </p-select>
    <small class="dt-role-selector__hint">{{ description() }}</small>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
      font-size: var(--dt-text-sm);
    }

    .dt-role-selector__option {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-role-selector__option-description,
    .dt-role-selector__hint {
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }
  `,
})
export class RoleSelectorComponent {
  readonly current = input.required<Role>();
  readonly allowOwner = input(true);
  readonly disabled = input(false);
  readonly inputId = input('member-role');
  readonly label = input($localize`:@@member.role:Papel`);

  readonly changed = output<Role>();

  protected readonly options = computed(() =>
    ASSIGNABLE_ROLES.filter((role) => role !== 'OWNER' || this.allowOwner()).map((role) => ({
      value: role,
      label: ROLE_LABELS[role],
      description: ROLE_DESCRIPTIONS[role],
    })),
  );

  protected readonly description = computed(() => ROLE_DESCRIPTIONS[this.current()]);
}
