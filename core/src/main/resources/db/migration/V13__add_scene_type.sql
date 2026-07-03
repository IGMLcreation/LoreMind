-- Niveau 2 (graphe de nœuds typés) : la Scène devient un nœud narratif typé.
-- `scene_type` est une métadonnée (GENERIC | LOCATION | ENCOUNTER | NPC | EVENT |
-- REVELATION) qui n'altère pas le comportement existant des scènes. Les liens typés
-- (SceneBranch.kind) sont portés par le JSON de la colonne `branches` (champ additif,
-- pas de migration). Compatible Postgres et H2 (MODE=PostgreSQL).
alter table scenes add column scene_type varchar(32) not null default 'GENERIC';
