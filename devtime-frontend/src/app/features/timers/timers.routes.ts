import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';

/**
 * Rotas do cronômetro.
 *
 * O cronômetro em si **não tem tela**: ele é a barra global do layout, operável de qualquer lugar
 * (§21.1 de `specs/009-timer/spec.md`). A única rota é a fila de abandonados, que é trabalho de
 * manutenção e não pertence a nenhuma tela de operação.
 */
const timerRoutes: Routes = [
  {
    path: 'abandoned',
    title: $localize`:@@timers.abandoned.title:Cronômetros abandonados`,
    canActivate: [permissionGuard(['TIMER_USE'])],
    loadComponent: () =>
      import('./pages/abandoned-timers.page').then((module) => module.AbandonedTimersPage),
  },
  { path: '', redirectTo: 'abandoned', pathMatch: 'full' },
];

export default timerRoutes;
