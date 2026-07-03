-- Réconciliation « arc HUB = quêtes » pour les données migrées par V10.
-- V10 a créé, pour chaque chapitre d'arc HUB, une quête JUMELLE avec le MÊME id
-- (continuité d'id) — mais AVANT l'existence de quests.arc_id (V18) : ces quêtes
-- sont donc restées « transverses » et n'apparaissaient pas sous leur arc HUB.
-- On les rattache ici à l'arc de leur chapitre jumeau. Idempotent (arc_id is null),
-- portable H2 (MODE=PostgreSQL) + Postgres.
update quests
set arc_id = (
    select c.arc_id
    from chapters c
    join arcs a on c.arc_id = a.id
    where c.id = quests.id and a.type = 'HUB'
)
where arc_id is null
  and exists (
      select 1
      from chapters c
      join arcs a on c.arc_id = a.id
      where c.id = quests.id and a.type = 'HUB'
  );
