-- ============================================================================
-- V4 : instantané des stats Foundry sur les ennemis (affichage en prep).
-- ============================================================================
-- A l'import d'un monstre Foundry, on stocke un snapshot APLATI (cle->valeur) de
-- ses stats systeme, juste pour l'affichage cote LoreMind. JSON en TEXT (meme
-- mecanique que field_values). Nullable, figé (non synchronise avec Foundry).

alter table enemies add column foundry_stats TEXT;
