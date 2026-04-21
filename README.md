# LoreMind

Application web d'aide aux Maîtres de Jeu (JDR) pour centraliser la gestion de l'univers (Lore) et le suivi des campagnes, avec un moteur IA intégré pour générer du contenu structuré.

## Fonctionnalités

- Gestion centralisée du Lore : Lieux, Factions, PNJ, et tous les éléments de votre univers
- Suivi de campagnes : Sessions, actions des joueurs, chronologie
- Moteur IA intégré : Génération automatique de contenu (PNJ, Villes, Quêtes) à partir de templates
- Export vers FoundryVTT : Transfert structuré des données vers votre VTT préféré (en développement)

## Captures d'écran

### Page d'accueil
![Accueil](docs/maquettes/général/Accueil.png)

### Recherche
![Recherche](docs/maquettes/général/Ecran de recherche.png)

## Stack Technologique

LoreMind utilise une architecture distribuée pour séparer les responsabilités :

- **Frontend** : Angular (Interface utilisateur, affichage du lore, formulaires de templates)
- **Backend Core** : Java (Spring Boot) - Orchestration, persistance, export VTT
- **Backend IA** : Python - Traitement des LLM et génération de contenu
- **Base de données** : PostgreSQL avec JSONB pour les templates flexibles

## Architecture

### Backend Java (Domain-Driven Design & Hexagonal)

Le Backend Core respecte strictement :
- **Domain-Driven Design (DDD)** : Séparation en Bounded Contexts autonomes
- **Architecture Hexagonale (Ports et Adaptateurs)** : Domaine pur sans dépendances techniques

#### Bounded Contexts
- **LoreContext** : Gestion de l'encyclopédie de l'univers
- **CampaignContext** : Suivi des sessions et chronologie
- **GenerationContext** : Gestion des requêtes IA et templates

#### Couches
- **Domaine (Core)** : Entités métier pures et interfaces (Ports)
- **Application** : Orchestration des flux (Use Cases)
- **Infrastructure** : Implémentation technique (Adapters)

## Installation

Pour installer LoreMind chez vous (Docker requis), suivez le guide **[INSTALL.md](INSTALL.md)** — 3 étapes, 5 minutes chrono :

1. Télécharger `docker-compose.yml` + `.env.example` depuis la [dernière release](https://git.igmlcreation.fr/ietm64/LoreMindMJ/releases)
2. Renommer `.env.example` → `.env` et changer `POSTGRES_PASSWORD`
3. `docker compose up -d` → ouvrir http://localhost:8081

Mise à jour : `docker compose pull && docker compose up -d`.

## Développement (contributeurs)

Pour builder les images localement depuis les sources :

```bash
git clone https://git.igmlcreation.fr/ietm64/LoreMindMJ.git
cd LoreMindMJ
# Créer un docker-compose.override.yml local (voir docs de contrib)
docker compose up -d --build
```

## License

[À définir]
