/**
 * Gère la pile de retours partagée par les écrans de création imbriqués
 * (page-create ↔ template-create ↔ node-create).
 *
 * La pile est encodée dans le query-param `returnTo` sous forme de chaîne
 * séparée par des virgules, ex : `"template-create,page-create"`. Chaque
 * écran dépile le premier élément pour savoir où revenir, et propage le
 * reste comme nouveau `returnTo`.
 */
export interface PoppedReturn {
  /** Nom de l'écran vers lequel revenir, ou null si la pile est vide. */
  next: string | null;
  /** Reste de la pile à transmettre à l'écran de retour, ou null si vide. */
  rest: string | null;
}

export function popReturnTo(raw: string | null | undefined): PoppedReturn {
  const parts = (raw ?? '').split(',').map(s => s.trim()).filter(Boolean);
  const next = parts.shift() ?? null;
  const rest = parts.length ? parts.join(',') : null;
  return { next, rest };
}
