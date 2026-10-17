-- Champs d'écriture (mentor) :
-- scène : tactique et moral des adversaires, utilité (ce qu'on perd si on la coupe) ;
-- arc : histoire cachée, accroches ;
-- PNJ : fiche « rôle dans l'histoire » (rôle, motivation, ce qu'il sait, signe distinctif) en JSON.
ALTER TABLE scenes ADD COLUMN tactics TEXT;
ALTER TABLE scenes ADD COLUMN purpose TEXT;
ALTER TABLE arcs ADD COLUMN hidden_truth TEXT;
ALTER TABLE arcs ADD COLUMN hooks TEXT;
ALTER TABLE npcs ADD COLUMN story TEXT;
