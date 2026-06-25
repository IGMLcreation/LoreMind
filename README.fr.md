# DM Loremind

[English](README.md) · **Français**

> Application web auto-hébergeable pour MJ qui veulent centraliser leur univers, leurs campagnes et leurs personnages — avec un assistant IA contextuel.

[![Licence: AGPL v3](https://img.shields.io/badge/Licence-AGPL%20v3-blue.svg)](LICENSE)
[![Documentation](https://img.shields.io/badge/docs-loremind--docs-green)](https://loremind-docs.igmlcreation.fr/)
[![Démo](https://img.shields.io/badge/d%C3%A9mo-en%20ligne-orange)](https://loremind-demo.igmlcreation.fr/)
[![Patreon](https://img.shields.io/badge/Patreon-soutenir-red)](https://www.patreon.com/c/IGMLCreation)
[![Discord](https://img.shields.io/badge/Discord-rejoindre-5865F2)](https://discord.gg/cPpFzCjEzQ)

## Découvrir DM Loremind en vidéo

[![Présentation DM Loremind](https://img.youtube.com/vi/llJkmlotbB8/maxresdefault.jpg)](https://www.youtube.com/watch?v=llJkmlotbB8)

![Tableau de bord](https://raw.githubusercontent.com/IGMLcreation/loremind-docs/main/static/img/screenshots/dashboard.png)

## Ce que ça fait

DM Loremind regroupe ce qu'un MJ utilise habituellement éparpillé entre plusieurs outils. L'application s'articule autour de trois modules principaux, augmentés par un assistant IA qui exploite tout votre contenu.

### Lore

Construire votre univers avec une arborescence de pages templatées : lieux, factions, PNJ, événements, organisations... Chaque type de page suit un template configurable, ce qui garantit la cohérence et facilite la navigation dans des univers riches.

### Système de JDR

Stocker les règles de votre système de jeu (D&D, Nimble, créations maison...) et définir les modèles de fiches de personnages associés. Les règles indexées peuvent être injectées dans le contexte de l'IA pour des réponses fidèles à votre système.

### Campagne

Structurer vos campagnes en Arcs → Chapitres → Scènes avec séparation claire du contenu MJ et du contenu joueurs. Gérer les PJ et PNJ via des fiches dynamiques basées sur les templates du système de JDR retenu.

### Assistant IA

Un assistant contextuel qui pioche dans votre Lore, vos règles et vos campagnes pour répondre à vos questions, suggérer du contenu cohérent, ou rebondir sur une situation improvisée en table.

L'IA s'exécute **en local via [Ollama](https://ollama.com/)** — vos données ne quittent jamais votre machine — ou dans le **cloud** avec votre propre clé API via [1min.ai](https://1min.ai/), [Mistral](https://mistral.ai/), [Google AI Studio (Gemini)](https://aistudio.google.com/) ou [OpenRouter](https://openrouter.ai/).

## Démarrage rapide

**Bureau (le plus simple)** — récupérez le dernier installeur sur la [page Releases](https://github.com/IGMLcreation/LoreMind/releases) : Windows `.msi` ou Linux `.AppImage`, puis lancez-le. Vos données restent en local.

**Auto-hébergement Docker** — sous Linux :

```bash
curl -fsSL https://raw.githubusercontent.com/IGMLcreation/LoreMind/main/installers/install.sh | bash
```

Sous Windows, suivez le [guide d'installation](https://loremind-docs.igmlcreation.fr/).

## Documentation

Toute la documentation (installation, configuration, prise en main) est sur **[loremind-docs.igmlcreation.fr](https://loremind-docs.igmlcreation.fr/)**.

## Démo en ligne

Une instance de démonstration est disponible sur **[loremind-demo.igmlcreation.fr](https://loremind-demo.igmlcreation.fr/)**.

Quelques limites à connaître :
- 10 utilisateurs maximum simultanés (instances isolées)
- Session limitée à 20 minutes avant réinitialisation
- Fonctions IA non incluses dans la démo (elles nécessitent un fournisseur d'IA — Ollama en local ou une clé API cloud — configuré côté serveur)

## Soutenir le projet

DM Loremind est **et restera gratuit en auto-hébergement**. Le développement avance plus vite avec votre soutien :

- **[Patreon](https://www.patreon.com/c/IGMLCreation)** — accès anticipé aux features, vote sur la roadmap, devlogs exclusifs
- **[Discord](https://discord.gg/cPpFzCjEzQ)** — annonces, support, retours utilisateurs

## Licence

DM Loremind est distribué sous licence **[GNU AGPL v3](LICENSE)**.

En pratique :
- Vous pouvez l'utiliser gratuitement, l'héberger, la modifier, la redistribuer.
- Si vous modifiez le code et que vous exposez l'application modifiée sur un réseau (même en SaaS privé), vous devez rendre vos modifications publiques sous la même licence.
- Les univers (Lore) et campagnes que vous créez avec DM Loremind **vous appartiennent entièrement** — la licence ne couvre que le code de l'application.
