/**
 * Logique pure de l'écran d'appairage (testée sans Angular).
 */

/** Uniquement un chemin interne : jamais d'URL absolue (pas de redirection vers un autre site). */
export function safeReturnUrl(value: string | null): string {
  return value && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')
    && !value.startsWith('/pair') ? value : '/';
}

/** Nom proposé par défaut pour l'appareil (clé i18n), d'après le navigateur. */
export function guessDeviceKey(userAgent: string): string {
  const ua = userAgent.toLowerCase();
  if (ua.includes('ipad') || (ua.includes('android') && !ua.includes('mobile')) || ua.includes('tablet')) {
    return 'remoteAccess.pair.defaultTablet';
  }
  if (ua.includes('iphone') || ua.includes('android')) return 'remoteAccess.pair.defaultPhone';
  return 'remoteAccess.pair.defaultDevice';
}

/** Ne garde que les chiffres, 6 au plus (saisie du code ou paramètre d'URL du QR code). */
export function normalizeCode(value: string | null | undefined): string {
  return (value ?? '').replace(/\D/g, '').slice(0, 6);
}
