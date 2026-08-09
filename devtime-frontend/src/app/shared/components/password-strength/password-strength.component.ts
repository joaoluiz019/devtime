import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { evaluatePassword, satisfiedCount } from '../../utils/password-policy';

/**
 * Indicador de força de senha — `dt-password-strength` (T-001-48).
 *
 * DS-05: a força **nunca** é comunicada só por cor. A barra é acompanhada do rótulo e da lista de
 * requisitos com marcação textual, porque quem não distingue vermelho de verde precisa da mesma
 * informação.
 *
 * A lista é a de RN-451 verificável no cliente. Ela é informativa: a recusa definitiva vem do
 * servidor (`422 DEVTIME-2451`), que também conhece a lista de senhas comuns.
 */
@Component({
  selector: 'dt-password-strength',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dt-password-strength">
      <!-- A11Y: a barra é decorativa; o estado real é lido na lista de requisitos abaixo. -->
      <div class="dt-password-strength__track" aria-hidden="true">
        <div
          class="dt-password-strength__bar"
          [class]="'dt-password-strength__bar--' + level()"
          [style.width.%]="percentage()"
        ></div>
      </div>

      <p class="dt-password-strength__label">
        <span i18n="@@password.strength.label">Força da senha:</span>
        <strong>{{ levelLabel() }}</strong>
      </p>

      <ul class="dt-password-strength__list">
        @for (requirement of requirements(); track requirement.id) {
          <li
            class="dt-password-strength__item"
            [class.dt-password-strength__item--ok]="requirement.satisfied"
          >
            <i
              class="pi"
              [class.pi-check]="requirement.satisfied"
              [class.pi-circle]="!requirement.satisfied"
              aria-hidden="true"
            ></i>
            <span>{{ requirement.label }}</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: `
    .dt-password-strength {
      display: flex;
      flex-direction: column;
      gap: var(--dt-space-1);
    }

    .dt-password-strength__track {
      height: 4px;
      border-radius: var(--dt-radius-full);
      background-color: var(--dt-border);
      overflow: hidden;
    }

    .dt-password-strength__bar {
      height: 100%;
      transition: width 150ms ease-out;
    }

    .dt-password-strength__bar--weak {
      background-color: var(--dt-color-danger);
    }

    .dt-password-strength__bar--medium {
      background-color: var(--dt-color-warning);
    }

    .dt-password-strength__bar--strong {
      background-color: var(--dt-color-success);
    }

    .dt-password-strength__label {
      margin: 0;
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-password-strength__label strong {
      margin-left: var(--dt-space-1);
      color: var(--dt-text-primary);
    }

    .dt-password-strength__list {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .dt-password-strength__item {
      display: flex;
      align-items: center;
      gap: var(--dt-space-1);
      color: var(--dt-text-secondary);
      font-size: var(--dt-text-xs);
    }

    .dt-password-strength__item--ok {
      color: var(--dt-color-success);
    }
  `,
})
export class PasswordStrengthComponent {
  readonly password = input.required<string>();

  protected readonly requirements = computed(() => evaluatePassword(this.password()));

  private readonly satisfied = computed(() => satisfiedCount(this.password()));

  protected readonly percentage = computed(() => (this.satisfied() / 4) * 100);

  /**
   * Só é `strong` com os quatro requisitos cumpridos.
   *
   * Chamar de "forte" uma senha que o servidor recusará seria mentir para o usuário no momento em
   * que ele decide qual senha usar.
   */
  protected readonly level = computed<'weak' | 'medium' | 'strong'>(() => {
    const count = this.satisfied();
    if (count === 4) {
      return 'strong';
    }
    return count >= 2 ? 'medium' : 'weak';
  });

  protected readonly levelLabel = computed(() => {
    switch (this.level()) {
      case 'strong':
        return $localize`:@@password.strength.strong:forte`;
      case 'medium':
        return $localize`:@@password.strength.medium:média`;
      default:
        return $localize`:@@password.strength.weak:fraca`;
    }
  });
}
