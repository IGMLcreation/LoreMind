import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { PreloadAllModules, provideRouter, withPreloading } from '@angular/router';
import { routes } from './app/app.routes';
import { provideHttpClient } from '@angular/common/http';

// withPreloading(PreloadAllModules) : une fois l'app initiale rendue, Angular
// telecharge en arriere-plan tous les chunks lazy-loades. Consequence : la
// premiere visite d'une route ne declenche plus de download runtime, elle
// ouvre instantanement. Cout : un peu plus de bande passante au demarrage
// (acceptable pour une app interne ou toutes les routes seront visitees).
bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes, withPreloading(PreloadAllModules)),
    provideHttpClient(),
  ],
}).catch((err: Error) => console.error(err));
