# LoreMind channel switcher

Sidecar qui bascule LoreMind entre les canaux **stable** et **beta** depuis l'UI,
sans manipulation manuelle du `.env` ni de docker-compose.

## Principe

Le switcher est un container minimal (Alpine + docker-cli + bash) qui :

1. Watch un fichier `command.json` dans un volume partagé avec le Core
2. Quand une commande arrive :
   - Valide le canal cible (`stable` | `beta`)
   - Sed la ligne `IMAGE_NAMESPACE` du `.env` du host
   - Lance `docker compose pull` puis `docker compose up -d` sur core/brain/web
3. Écrit son résultat dans `result.json` (le Core remonte ça à l'UI via polling)

## Sécurité

Le switcher a accès au socket Docker et au répertoire compose du host (RW),
donc beaucoup de pouvoir. Pour éviter qu'une compromission du Core devienne
un RCE sur l'hôte :

- Le Core n'a **pas** accès au socket Docker — il dépose une commande dans un
  fichier, point.
- Le switcher **valide strictement** le contenu : `channel` doit valoir exactement
  `stable` ou `beta` (case statement, pas de regex laxiste).
- Aucun port n'est exposé. La communication se fait uniquement via volume
  partagé.

## Architecture

```
┌──────────────┐  ┌──────────────────┐  ┌──────────────┐  ┌────────────┐
│ User clique  │  │ Core             │  │ switcher     │  │ Docker     │
│ "Passer beta"│─▶│ écrit command.json│─▶│ sed .env     │─▶│ daemon     │
│   dans UI    │  │  dans volume     │  │ docker compose│  │ (recreate) │
└──────────────┘  └──────────────────┘  └──────────────┘  └────────────┘
                                                 │
                                                 ▼
                                          ┌─────────────┐
                                          │ result.json │ ◄── Core poll
                                          └─────────────┘
```

## Upgrade pour les installs existantes

Le sidecar est arrivé dans LoreMind 0.9.0. Pour les installs antérieures qui
ne l'ont pas dans leur `docker-compose.yml`, l'utilisateur doit faire une
**dernière** manipulation :

1. Récupérer le nouveau `docker-compose.yml` du repo
2. Lancer `docker compose pull && docker compose up -d`

Après ça, tous les switchs futurs se font depuis l'UI sans intervention CLI.

## Pourquoi le switcher n'est PAS dans `IMAGE_NAMESPACE`

L'image du switcher est codée en dur (`ghcr.io/igmlcreation/loremind-switcher`)
plutôt que d'utiliser `${IMAGE_NAMESPACE}`. Raison : pendant un switch, le
switcher exécute `docker compose up -d`. Si son propre image faisait partie
de `IMAGE_NAMESPACE`, le compose voudrait le recréer en même temps que
core/brain/web — et il se tuerait au milieu de sa propre commande. Race
condition fatale.

Pour la même raison, le `docker compose up -d` dans `switch.sh` cible
explicitement `core brain web --no-deps` — jamais le switcher lui-même.
