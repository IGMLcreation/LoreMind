-- ============================================================================
-- V5 : type d'acteur Foundry sur le GameSystem (export d'ennemis maison typés).
-- ============================================================================
-- Renseigne par l'import d'une structure d'acteur Foundry. Couple aux foundryPath
-- des champs du template ennemi : a l'export, un ennemi maison devient un acteur de
-- ce type avec system.<foundryPath> = valeur. Nullable.

alter table game_systems add column foundry_actor_type varchar(64);
