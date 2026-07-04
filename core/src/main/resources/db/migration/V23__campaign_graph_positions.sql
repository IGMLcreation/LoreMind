-- Positions personnalisées des nœuds du graphe de campagne (drag & drop).
-- JSON opaque côté back ("<kind>:<id>" -> {x, y}) : pur état de présentation,
-- possédé par le front. Nullable = disposition automatique (force layout).
ALTER TABLE campaigns ADD COLUMN graph_positions TEXT;
