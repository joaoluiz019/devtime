import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { firstValueFrom } from 'rxjs';
import { TenantOption } from '../../core/auth/auth.model';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { messageForCode } from '../../core/error/error-messages';
import { isProblemDetail, ProblemDetail } from '../../core/error/problem-detail.model';
import { TenantSelectorComponent } from './tenant-selector.component';

/**
 * Seleção de organização — P06, `POST /auth/select-tenant` (T-001-51).
 *
 * O login com mais de uma organização devolve um token de **pré-seleção**, sem o claim `tid`: nada
 * do produto abre até esta escolha. É também a tela de troca de organização, acionada pela barra
 * superior.
 *
 * CE-F-04 (limpar os stores de feature na troca) é atendido pela navegação para a raiz: os stores de
 * feature são providos na rota (`@Injectable()` sem `providedIn`), então trocar de rota os destrói
 * junto com os dados do tenant anterior. Um registro global de stores para limpar à mão seria uma
 * segunda mecânica com o mesmo efeito e uma chance a mais de esquecer um store novo.
 */
@Component({
  selector: 'dt-select-tenant-page',
  imports: [MessageModule, ProgressSpinnerModule, TenantSelectorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 class="dt-auth-form__title" i18n="@@selectTenant.title">Escolha a organização</h1>
    <p class="dt-auth-form__subtitle" i18n="@@selectTenant.subtitle">
      Você participa de mais de uma organização. Selecione com qual deseja trabalhar agora.
    </p>

    <div aria-live="polite">
      @if (errorMessage() !== null) {
        <p-message severity="error" [text]="errorMessage()!" styleClass="w-full mb-3" />
      }
    </div>

    @if (loading()) {
      <p-progress-spinner
        styleClass="w-3rem h-3rem"
        i18n-ariaLabel="@@selectTenant.loading"
        ariaLabel="Carregando organizações"
      />
    } @else if (tenants().length === 0) {
      <!-- INV-USR-04: sem vínculo ativo não há o que selecionar; o texto diz o que fazer. -->
      <p class="dt-auth-form__subtitle" i18n="@@selectTenant.empty">
        Você não possui acesso ativo a nenhuma organização. Peça a um administrador para reativar
        seu acesso.
      </p>
    } @else {
      <dt-tenant-selector [tenants]="tenants()" [busy]="selecting()" (selected)="select($event)" />
    }
  `,
  styleUrl: './auth-form.scss',
})
export class SelectTenantPage implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);
  private readonly router = inject(Router);

  private readonly _tenants = signal<readonly TenantOption[]>([]);
  private readonly _loading = signal(false);
  private readonly _selecting = signal(false);
  private readonly _error = signal<ProblemDetail | null>(null);

  protected readonly tenants = this._tenants.asReadonly();
  protected readonly loading = this._loading.asReadonly();
  protected readonly selecting = this._selecting.asReadonly();

  protected readonly errorMessage = computed(() => {
    const problem = this._error();
    return problem === null ? null : messageForCode(problem.code, problem.detail);
  });

  /**
   * A lista do login é usada quando existe; só então há chamada.
   *
   * O login já devolve `tenants[]` para quem tem mais de uma organização. Buscar de novo repetiria
   * uma requisição cuja resposta acabou de chegar. A chamada permanece para o caminho de **troca**,
   * em que a pessoa entra nesta tela vinda do produto.
   */
  async ngOnInit(): Promise<void> {
    const fromSession = this.authStore.availableTenants();
    if (fromSession.length > 0) {
      this._tenants.set(fromSession);
      return;
    }

    this._loading.set(true);
    try {
      this._tenants.set(await firstValueFrom(this.authService.listTenants()));
    } catch (error: unknown) {
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._loading.set(false);
    }
  }

  protected async select(tenant: TenantOption): Promise<void> {
    this._error.set(null);
    this._selecting.set(true);
    try {
      await firstValueFrom(this.authService.selectTenant({ tenantId: tenant.id }));
      await this.router.navigate(['/']);
    } catch (error: unknown) {
      // RN-459 / RN-008: vínculo revogado (`1102`) e organização cancelada (`1202`) chegam aqui.
      if (isProblemDetail(error)) {
        this._error.set(error);
      }
    } finally {
      this._selecting.set(false);
    }
  }
}
