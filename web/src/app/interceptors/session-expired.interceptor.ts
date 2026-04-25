import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Detecte la perte de session demo (orchestrateur) via les codes 401/502 sur
 * les appels /api/*, affiche un overlay puis force un rechargement de la page.
 * Le reload renvoie l'utilisateur sur la page "Preparation" pour creer une
 * nouvelle session sans qu'il ait a faire Ctrl+Shift+R.
 *
 * Cet interceptor est inerte en mode normal (non-demo) : si le backend natif
 * renvoie un 401 legitime, ca declenche aussi le reload, ce qui est sans
 * consequence puisqu'aucun flux d'auth utilisateur n'existe encore cote app.
 */

// Module-level flag : evite de declencher overlay + reload plusieurs fois si
// plusieurs appels echouent en parallele juste apres l'expiration.
let alreadyTriggered = false;

export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((err) => {
      const isApiCall = req.url.includes('/api/');
      const isSessionLoss =
        err instanceof HttpErrorResponse && (err.status === 401 || err.status === 502);

      if (isApiCall && isSessionLoss && !alreadyTriggered) {
        alreadyTriggered = true;
        showExpiredOverlay();
        setTimeout(() => window.location.reload(), 2500);
      }
      return throwError(() => err);
    })
  );
};

function showExpiredOverlay(): void {
  const overlay = document.createElement('div');
  overlay.setAttribute('data-session-expired', 'true');
  overlay.style.cssText = [
    'position:fixed', 'inset:0',
    'background:rgba(26,22,37,0.96)',
    'color:#e4def5',
    'display:flex', 'flex-direction:column',
    'align-items:center', 'justify-content:center',
    'gap:1rem',
    'font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif',
    'z-index:99999',
    'text-align:center', 'padding:2rem',
  ].join(';');
  overlay.innerHTML = `
    <div style="font-size:1.5rem;color:#b794f4;">✦ Votre session démo a expiré</div>
    <div style="color:#aaa0c5;max-width:420px;line-height:1.5;">
      Une nouvelle session va être préparée automatiquement.<br>
      Vos données précédentes ne sont pas conservées.
    </div>
    <div style="
      width:32px;height:32px;margin-top:0.5rem;
      border:3px solid rgba(183,148,244,0.2);
      border-top-color:#b794f4;border-radius:50%;
      animation:sex-spin 1s linear infinite;
    "></div>
    <style>@keyframes sex-spin{to{transform:rotate(360deg)}}</style>
  `;
  document.body.appendChild(overlay);
}
