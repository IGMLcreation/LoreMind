-- Niveau 2 (2d) : positions persistées des nœuds dans la vue graphe d'un chapitre.
-- Null = nœud pas encore positionné → le layout automatique (BFS) le place à l'ouverture.
-- Compatible Postgres et H2 (MODE=PostgreSQL).
alter table scenes add column graph_x double precision;
alter table scenes add column graph_y double precision;
