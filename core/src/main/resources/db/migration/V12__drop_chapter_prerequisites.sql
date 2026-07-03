-- Niveau 1 : le Chapitre devient une donnée de SCÉNARIO pure. Les prérequis (conditions
-- de déblocage) vivent désormais uniquement sur la table quests ; la colonne des chapitres
-- est morte (la migration V10 a déjà recopié les chapitres « quête » HUB vers quests).
-- Compatible Postgres et H2 (MODE=PostgreSQL). Pas de FK ni d'index sur cette colonne.
alter table chapters drop column prerequisites;
