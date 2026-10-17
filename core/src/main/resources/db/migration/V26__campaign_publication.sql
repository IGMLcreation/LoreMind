-- Fiche « Publication » d'une campagne (module à vendre ou à partager) : sous-titre,
-- auteur, page technique, argument, contexte MJ, accroches, crédits, licence,
-- image de couverture… Stockée en JSON (texte libre, évolutif) ; NULL = jamais renseignée.
ALTER TABLE campaigns ADD COLUMN publication TEXT;
