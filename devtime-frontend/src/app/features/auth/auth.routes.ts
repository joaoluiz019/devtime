import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '../../core/auth/auth.guard';
import { AuthLayoutComponent } from './auth-layout.component';

/**
 * Rotas de autenticação, fora do shell (layouts.md §4, L1).
 *
 * Dois grupos sob o mesmo layout, com guards diferentes. As telas públicas usam `guestGuard`, que
 * devolve ao produto quem já tem sessão. `select-tenant` **não** pode ficar nesse grupo: quem chega
 * lá tem sessão de pré-seleção e seria mandado à raiz, que redireciona de volta à seleção — um laço.
 * Ela usa `authGuard`, que exige sessão e ainda tenta restaurá-la pelo cookie no recarregamento.
 *
 * O aceite de convite (P07) não fica em nenhum dos dois grupos: `guestGuard` mandaria à raiz quem já
 * tem sessão — descartando o convite recém-aberto —, e `authGuard` exigiria login de quem foi
 * convidado justamente para criar a conta. §5.12 prevê os três casos na mesma URL.
 */
const authRoutes: Routes = [
  {
    path: '',
    component: AuthLayoutComponent,
    canActivate: [guestGuard],
    children: [
      { path: '', redirectTo: 'login', pathMatch: 'full' },
      {
        path: 'login',
        title: $localize`:@@login.title:Entrar`,
        // FR-080: toda rota de feature usa loadComponent ou loadChildren.
        loadComponent: () => import('./login.page').then((module) => module.LoginPage),
      },
      {
        path: 'register',
        title: $localize`:@@register.title:Criar conta`,
        loadComponent: () => import('./register.page').then((module) => module.RegisterPage),
      },
      {
        path: 'verify',
        title: $localize`:@@verify.title:Verificação de e-mail`,
        loadComponent: () => import('./verify-email.page').then((module) => module.VerifyEmailPage),
      },
      {
        path: 'forgot-password',
        title: $localize`:@@forgot.title:Recuperar acesso`,
        loadComponent: () =>
          import('./forgot-password.page').then((module) => module.ForgotPasswordPage),
      },
      {
        path: 'reset-password',
        title: $localize`:@@reset.title:Definir nova senha`,
        loadComponent: () =>
          import('./reset-password.page').then((module) => module.ResetPasswordPage),
      },
    ],
  },
  {
    path: '',
    component: AuthLayoutComponent,
    children: [
      {
        path: 'invitation/:token',
        title: $localize`:@@invitation.title.short:Aceitar convite`,
        loadComponent: () =>
          import('./accept-invitation.page').then((module) => module.AcceptInvitationPage),
      },
    ],
  },
  {
    path: '',
    component: AuthLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'select-tenant',
        title: $localize`:@@selectTenant.title:Escolha a organização`,
        loadComponent: () =>
          import('./select-tenant.page').then((module) => module.SelectTenantPage),
      },
    ],
  },
];

export default authRoutes;
