-- Cadrage (pan/zoom) des images dans les blocs IMAGE des pages de lore.
-- Stocké en JSON dans une colonne TEXT (fieldKey -> imageId -> {x, y, scale}).
-- Compatible Postgres (Docker) et H2 en MODE=PostgreSQL (desktop).
alter table pages add column image_framing TEXT;
