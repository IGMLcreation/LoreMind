import { HttpInterceptorFn } from '@angular/common/http';
import { STORAGE_KEY } from '../services/language.service';

/**
 * Ajoute l'entête `X-User-Language` (langue choisie dans l'UI : `fr`/`en`) à
 * toutes les requêtes HttpClient. Le Core la relaie au Brain, qui rédige alors
 * ses réponses IA dans cette langue.
 *
 * NB : les appels SSE en `fetch()` (chat, imports, notebooks) ne passent PAS par
 * les intercepteurs Angular — ils ajoutent l'entête manuellement de leur côté.
 *
 * IMPORTANT — on lit la langue directement depuis localStorage et NON via
 * LanguageService/TranslateService. Injecter ces services ici créerait une
 * dépendance circulaire fatale au démarrage : provideTranslateService charge la
 * langue de repli (`fr.json`) PENDANT la construction de TranslateService ; cette
 * requête passe par cet intercepteur ; si l'intercepteur injectait LanguageService
 * (qui injecte TranslateService, en cours de construction) → cycle → la requête
 * `fr.json` échoue → ngx-translate marque `fr` comme chargé-mais-vide → toutes les
 * clés s'affichent brutes (l'anglais, n'étant pas la langue de repli, se chargeait
 * après la construction et fonctionnait). localStorage est de toute façon la
 * source de vérité (persistée par LanguageService.use()).
 */
export const languageInterceptor: HttpInterceptorFn = (req, next) => {
  let language = 'fr';
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      language = stored;
    }
  } catch {
    // localStorage indisponible (mode privé strict) : on garde le défaut.
  }
  return next(req.clone({ setHeaders: { 'X-User-Language': language } }));
};
