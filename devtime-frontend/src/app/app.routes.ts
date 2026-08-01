import { Routes } from '@angular/router';
import { authGuard, tenantSelectedGuard } from './core/auth/auth.guard';
import { ShellComponent } from './core/layout/shell.component';

/**
 * Rotas raiz (frontend.md §8).
 *
 * FR-081: toda rota autenticada passa por `authGuard` e `tenantSelectedGuard`. FR-089: rota inexistente
 * leva à página de "não encontrado" **dentro do shell**.
 *
 * De `contracts`, apenas P16 (detalhe do período, feature `011`) está declarada. As áreas de clientes,
 * tickets, horas, relatórios e configurações não constam aqui: SQ-07 determina que nenhum frontend de
 * feature inicie antes de o backend correspondente estar `DONE`.
 */
export const routes: Routes = [
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes'),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard, tenantSelectedGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        title: $localize`:@@dashboard.title:Dashboard`,
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((module) => module.DashboardPage),
      },
      {
        path: 'contracts',
        loadChildren: () => import('./features/contracts/contracts.routes'),
      },
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./shared/pages/forbidden.page').then((module) => module.ForbiddenPage),
      },
      {
        path: '**',
        loadComponent: () =>
          import('./shared/pages/not-found.page').then((module) => module.NotFoundPage),
      },
    ],
  },
];
