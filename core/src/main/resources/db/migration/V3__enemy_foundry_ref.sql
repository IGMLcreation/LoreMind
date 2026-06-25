-- ============================================================================
-- V3 : reference Foundry sur les ennemis (import depuis un compendium).
-- ============================================================================
-- Un ennemi importe d'un compendium Foundry porte l'UUID de l'acteur source
-- (ex. Compendium.nimble.monsters.Actor.abc123). Permet, a l'export Foundry, de
-- poser un token du VRAI acteur (stats natives), sans recopier les stats.
-- Nullable : un ennemi fait main n'a pas de reference.

alter table enemies add column foundry_ref varchar(512);
