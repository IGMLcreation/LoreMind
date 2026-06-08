/** Reflet de l'ItemCatalogDTO côté Core. Un catalogue d'objets (boutique, butin…). */

export interface CatalogItem {
  name: string;
  price?: string;
  category?: string;
  description?: string;
}

export interface ItemCatalog {
  id?: string;
  name: string;
  description?: string;
  icon?: string;
  campaignId: string;
  order?: number;
  items: CatalogItem[];
}

export interface ItemCatalogCreate {
  name: string;
  description?: string;
  icon?: string;
  campaignId: string;
  items: CatalogItem[];
}
