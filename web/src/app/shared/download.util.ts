/** Déclenche le téléchargement d'un fichier reçu en Blob (lien temporaire). */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Nom de fichier sûr (alphanumérique, tirets, soulignés), comme côté serveur. */
export function safeFileName(name: string | null | undefined, fallback: string): string {
  let slug = (name || '').trim().replace(/[^a-zA-Z0-9-_]+/g, '_');
  // Au plus un « _ » de bord après la fusion ci-dessus : retiré sans regex ancrée.
  if (slug.startsWith('_')) slug = slug.slice(1);
  if (slug.endsWith('_')) slug = slug.slice(0, -1);
  return slug || fallback;
}
