-- Battlemaps multiples par scène (variantes Jour/Nuit, étages…) :
-- la paire de colonnes { battlemap_media_file_id, battlemap_data_file_id } devient
-- une LISTE JSON [{label, mediaFileId, dataFileId}] dans une colonne TEXT
-- (même pattern que branches/rooms). La carte existante est reprise comme
-- première entrée, libellé vide.
-- Compatible H2 (MODE=PostgreSQL) et PostgreSQL : concaténation || et CASE.

ALTER TABLE scenes ADD COLUMN battlemaps TEXT;

UPDATE scenes SET battlemaps =
    '[{"label":"","mediaFileId":' ||
    CASE WHEN battlemap_media_file_id IS NULL THEN 'null'
         ELSE '"' || battlemap_media_file_id || '"' END ||
    ',"dataFileId":' ||
    CASE WHEN battlemap_data_file_id IS NULL THEN 'null'
         ELSE '"' || battlemap_data_file_id || '"' END ||
    '}]'
WHERE battlemap_media_file_id IS NOT NULL OR battlemap_data_file_id IS NOT NULL;

UPDATE scenes SET battlemaps = '[]' WHERE battlemaps IS NULL;

ALTER TABLE scenes DROP COLUMN battlemap_media_file_id;
ALTER TABLE scenes DROP COLUMN battlemap_data_file_id;
