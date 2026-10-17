-- Règles d'un système de jeu en deux langues : rules_markdown = version française,
-- rules_markdown_en = version anglaise (remplie à l'import d'un PDF par traduction).
ALTER TABLE game_systems ADD COLUMN rules_markdown_en TEXT;
