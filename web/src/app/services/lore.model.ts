// Interface TypeScript pour LoreDTO (correspond au DTO Java)
export interface Lore {
  id?: string;
  name: string;
  description: string;
  nodeCount?: number;
  pageCount?: number;
}

// Interface pour la création de Lore (sans id)
export interface LoreCreate {
  name: string;
  description: string;
}

export interface LoreNode {
  id?: string;
  name: string;
  /** Clé d'icône lucide-angular (ex: "users", "map-pin"). */
  icon?: string | null;
  /** ID du dossier parent (null = racine). */
  parentId?: string | null;
  loreId: string;
  /** Champs historiques non encore persistés côté backend — gardés pour compat de l'UI. */
  type?: string;
  description?: string;
  address?: string;
}

export interface LoreNodeCreate {
  name: string;
  icon: string;
  description: string;
  address: string;
  parentId?: string | null;
  loreId: string;
}
