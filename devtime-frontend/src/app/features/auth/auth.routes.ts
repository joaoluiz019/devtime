import { Routes } from '@angular/router';
import { guestGuard } from '../../core/auth/auth.guard';
import { AuthLayoutComponent } from './auth-layout.component';

/**
 * Rotas de autenticação, fora do shell (layouts.md §4, L1).
 *
 * As demais telas do fluxo — registro (P02), verificação de e-mail (P03), recuperação de senha
 * (P04/P05), seleção de organização (P06) e aceite de convite (P07) — pertencem à feature 001
 * (T-001-48 a T-001-52). Declará-las aqui apontando para componentes inexistentes quebraria o build.
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
    ],
  },
];

export default authRoutes;
