import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { PreloadAllModules, provideRouter, withPreloading } from '@angular/router';
import { routes } from './app/app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { APP_INITIALIZER, provideZoneChangeDetection } from '@angular/core';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { ConfigService } from './app/services/config.service';
import { LanguageService } from './app/services/language.service';
import { sessionExpiredInterceptor } from './app/interceptors/session-expired.interceptor';

// withPreloading(PreloadAllModules) : une fois l'app initiale rendue, Angular
// telecharge en arriere-plan tous les chunks lazy-loades. Consequence : la
// premiere visite d'une route ne declenche plus de download runtime, elle
// ouvre instantanement. Cout : un peu plus de bande passante au demarrage
// (acceptable pour une app interne ou toutes les routes seront visitees).
bootstrapApplication(AppComponent, {
  providers: [
    provideZoneChangeDetection(),provideRouter(routes, withPreloading(PreloadAllModules)),
    provideHttpClient(withInterceptors([sessionExpiredInterceptor])),
    provideTranslateService({
      loader: provideTranslateHttpLoader({
        prefix: 'assets/i18n/',
        suffix: '.json',
      }),
      fallbackLang: 'fr',
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: (config: ConfigService) => () => config.load(),
      deps: [ConfigService],
      multi: true,
    },
    {
      // Applique la langue (localStorage → navigateur → fr) et précharge le JSON
      // correspondant avant le premier rendu, pour éviter un flash de clés brutes.
      provide: APP_INITIALIZER,
      useFactory: (lang: LanguageService) => () => lang.init(),
      deps: [LanguageService],
      multi: true,
    },
  ],
}).catch((err: Error) => console.error(err));
