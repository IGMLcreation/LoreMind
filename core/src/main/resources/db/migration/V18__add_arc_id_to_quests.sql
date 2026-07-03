-- Rattachement d'une quête à un arc HUB (arc Linéaire = chapitres, arc Hub = quêtes).
-- Nullable : arcId null = quête TRANSVERSE (liste « Quêtes »). Pas de FK (agrégat Quest
-- indépendant, cohérent avec campaign_id). Portable H2 (MODE=PostgreSQL) + Postgres.
alter table quests add column arc_id bigint;

create index ix_quests_arc on quests (arc_id);
