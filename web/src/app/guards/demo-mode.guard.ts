import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ConfigService } from '../services/config.service';

/**
 * Bloque l'acces aux routes sensibles quand demoMode est actif et redirige
 * vers la home. Defense UX ; le verrou serveur reste la source de verite.
 */
export const hiddenInDemoGuard: CanActivateFn = () => {
  const config = inject(ConfigService);
  const router = inject(Router);
  if (config.demoMode) {
    router.navigate(['/']);
    return false;
  }
  return true;
};
