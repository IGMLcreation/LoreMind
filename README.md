# LoreMind

![Tableau de bord](https://raw.githubusercontent.com/IGMLcreation/loremind-docs/main/static/img/screenshots/dashboard.png)

Loremind est une application web angular auto-hébergable afin de venir en aide aux Maîtres de jeu qui souhaitent centraliser leur univers et leurs campagnes.
Cette dernière intègre un moteur IA qui va ingérer le contenu du lore et de la campagne afin de pouvoir répondre à des questions précises sur l'univers ou la campagne, mais également proposer des idées de création dans le contexte de la campagne et du lore.
Pour le moment seul Ollama est supporté pour la partie locale, il y-a également une intégration pour 1min.ai. Plus tard, d'autres moteurs seront supportés.

## Documentation

La documentation complète est accessible sur le site [loremind-docs](https://loremind-docs.igmlcreation.fr/)

Pour l'installation, consultez le guide dans cette dernière .

## Fonctionnalités

- Gestion centralisée du Lore : Lieux, Factions, PNJ, et tous les éléments de votre univers
- Suivi de campagnes : Sessions, actions des joueurs, chronologie
- Moteur IA intégré : Génération automatique de contenu (PNJ, Villes, Quêtes) à partir de templates

## Démo

Une démo est disponible sur le site [loremind-demo](https://loremind-demo.igmlcreation.fr/)

!! Attention, la démo est uniquement accessible à 10 personnes à la fois (instances personnalisées). Cette limite est mise en place pour éviter l'overhead sur les ressources serveur.

Cette dernière est utilisable 20 minutes maximum par session avant d'être réinitialiser.
Vous comprendrez également qu'elle ne contient pas de démo pour la partie IA, pour laquelle il faut configurer un serveur Ollama (et qui ferait donc exploser le serveur) ou utiliser 1min.ai.

## License

LoreMind est distribué sous licence **[GNU AGPL v3](LICENSE)**.

En pratique :
- Tu peux l'utiliser gratuitement, l'héberger où tu veux, le modifier, le redistribuer.
- Si tu modifies le code et que tu exposes l'application modifiée sur un réseau (même en SaaS privé), tu dois rendre tes modifications publiques sous la même licence.
- Les univers (Lore) et campagnes que tu crées avec LoreMind **t'appartiennent entièrement** — la licence ne couvre que le code de l'application.
