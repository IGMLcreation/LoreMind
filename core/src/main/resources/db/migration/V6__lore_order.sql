-- ============================================================================
-- V6 : colonne `order` sur les pages et dossiers de lore (réordonnancement
-- manuel par glisser-déposer, comme arcs/chapitres/scènes).
-- ============================================================================
-- `order` est un mot-clé SQL -> colonne quotée (cohérent avec arc/chapter/scene).
-- Défaut 0 : les lignes existantes prennent 0 (ordre indéfini jusqu'au 1er drag).

alter table pages add column "order" integer not null default 0;
alter table lore_nodes add column "order" integer not null default 0;
