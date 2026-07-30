import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

bootstrapApplication(AppComponent, appConfig).catch((error: unknown) => {
  // Falha de bootstrap ocorre antes de existir GlobalErrorHandler; o console é a única saída.
  console.error('Falha ao inicializar a aplicação', error);
});
