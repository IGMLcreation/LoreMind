import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { REMOTE_PAIRING_REQUIRED } from '../services/remote-access.service';

/**
 * Accès distant (tablette sur le réseau local) : quand le Core répond
 * `401 remote_pairing_required`, l'appareil n'est pas (ou plus) appairé — on bascule
 * sur l'écran d'appairage en retenant la page demandée. Inerte sur le PC et en Docker,
 * où ce code d'erreur n'existe pas.
 */
export const remotePairingInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  return next(req).pipe(
    catchError((err) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && err.error?.error === REMOTE_PAIRING_REQUIRED
          && !router.url.startsWith('/pair')) {
        router.navigate(['/pair'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    })
  );
};
