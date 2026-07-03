-- Mode séance (cockpit) : scène courante épinglée sur la session en cours.
-- Weak ref nullable vers scenes.id (pas de FK — la scène peut être supprimée,
-- l'épingle devient simplement caduque). Portable H2 (MODE=PostgreSQL) + Postgres.
alter table sessions add column current_scene_id bigint;
