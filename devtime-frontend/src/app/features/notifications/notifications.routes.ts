import { Routes } from '@angular/router';
import { permissionGuard } from '../../core/auth/auth.guard';

/** Rotas de notificações (P25). */
const notificationRoutes: Routes = [
  {
    path: '',
    title: $localize`:@@notifications.title:Notificações`,
    canActivate: [permissionGuard(['NOTIFICATION_VIEW'])],
    loadComponent: () =>
      import('./pages/notification-list.page').then((module) => module.NotificationListPage),
  },
];

export default notificationRoutes;
