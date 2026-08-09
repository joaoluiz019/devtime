import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';

/**
 * Rota de relatórios (P24).
 *
 * OWN-08: `REPORT_VIEW_OWN` basta para entrar. Os tipos que exigem `REPORT_VIEW_ANY` são
 * desabilitados e explicados dentro da tela — barrar a página inteira faria `MEMBER` perder também
 * a folha de horas dos próprios registros, que ele tem direito de emitir.
 */
const reportRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@reports.title:Relatórios`,
    canActivate: [permissionGuard(['REPORT_VIEW_OWN', 'REPORT_VIEW_ANY'])],
    loadComponent: () => import('./pages/reports.page').then((module) => module.ReportsPage),
  },
];

export default reportRoutes;
