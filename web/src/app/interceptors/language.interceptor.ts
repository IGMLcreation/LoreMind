import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { LanguageService } from '../services/language.service';

/**
 * Ajoute l'entête `X-User-Language` (langue choisie dans l'UI : `fr`/`en`) à
 * toutes les requêtes HttpClient. Le Core la relaie au Brain, qui rédige alors
 * ses réponses IA dans cette langue.
 *
 * NB : les appels SSE en `fetch()` (chat, imports, notebooks) ne passent PAS par
 * les intercepteurs Angular — ils ajoutent l'entête manuellement de leur côté.
 */
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  const language = inject(LanguageService);
  return next(
    req.clone({ setHeaders: { 'X-User-Language': language.current } })
  );
};
