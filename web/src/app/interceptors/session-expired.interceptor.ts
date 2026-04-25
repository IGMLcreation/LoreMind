import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ConfigService } from '../services/config.service';

/**
 * Detecte la perte de session demo (orchestrateur) via les codes 401/502 sur
 * les appels /api/*, affiche un overlay puis force un rechargement de la page.
 * Le reload renvoie l'utilisateur sur la page "Preparation" pour creer une
 * nouvelle session sans qu'il ait a faire Ctrl+Shift+R.
 *
 * Strictement inerte hors mode demo : sans cette garde, un 401 du backend
 * natif (par ex. HTTP Basic sur /api/settings avant authentification) ou un
 * 502 transitoire au boot (Brain pas encore pret) declencherait a tort
 * l'overlay "session demo expiree" sur les installs self-hosted.
 */

// Module-level flag : evite de declencher overlay + reload plusieurs fois si
// plusieurs appels echouent en parallele juste apres l'expiration.
let alreadyTriggered = false;

export const sessionExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const config = inject(ConfigService);

  return next(req).pipe(
    catchError((err) => {
      // Garde stricte : l'overlay et le reload n'ont de sens qu'en mode demo,
      // ou l'orchestrateur drop les sessions sans prevenir le client.
      if (!config.demoMode) {
        return throwError(() => err);
      }

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
