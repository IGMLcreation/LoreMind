-- Nombre de joueurs attendus à la table.
-- Le champ était saisi à la création côté front (défaut 4) mais jamais persisté :
-- le backend (domaine/entité/DTO) ne le portait pas, d'où un affichage "0 joueurs".
-- Défaut 4 (valeur par défaut du formulaire) pour les campagnes existantes.
ALTER TABLE campaigns ADD COLUMN player_count INTEGER NOT NULL DEFAULT 4;
