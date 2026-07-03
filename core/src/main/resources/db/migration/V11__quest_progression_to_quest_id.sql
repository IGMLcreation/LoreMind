
    -- Niveau 1, Phase 2b : quest_progression passe de chapter_id à quest_id.
    -- Continuité d'id (quest id == chapter id pour les quêtes migrées) => simple copie,
    -- aucune réécriture de valeur. Contraintes nommées => DROP portable H2 / Postgres.

    alter table quest_progression add column quest_id bigint;

    update quest_progression set quest_id = chapter_id;

    alter table quest_progression alter column quest_id set not null;

    alter table quest_progression drop constraint uk_quest_progression_unique;

    alter table quest_progression add constraint uk_quest_progression_quest unique (playthrough_id, quest_id);

    alter table quest_progression drop column chapter_id;
