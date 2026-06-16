# LoreMind

**English** · [Français](README.fr.md)

> A self-hostable web app for game masters who want to centralize their world, campaigns and characters — with a context-aware AI assistant.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Documentation](https://img.shields.io/badge/docs-loremind--docs-green)](https://loremind-docs.igmlcreation.fr/en/)
[![Demo](https://img.shields.io/badge/demo-online-orange)](https://loremind-demo.igmlcreation.fr/)
[![Patreon](https://img.shields.io/badge/Patreon-support-red)](https://www.patreon.com/c/IGMLCreation)
[![Discord](https://img.shields.io/badge/Discord-join-5865F2)](https://discord.gg/cPpFzCjEzQ)

## See LoreMind in action

[![LoreMind overview](https://img.youtube.com/vi/llJkmlotbB8/maxresdefault.jpg)](https://www.youtube.com/watch?v=llJkmlotbB8)

![Dashboard](https://raw.githubusercontent.com/IGMLcreation/loremind-docs/main/static/img/screenshots/dashboard.png)

## What it does

LoreMind brings together what a game master usually scatters across several tools. The app is built around three core modules, augmented by an AI assistant that draws on all of your content.

### Lore

Build your world with a tree of templated pages: locations, factions, NPCs, events, organizations... Each page type follows a configurable template, which keeps things consistent and makes navigating rich worlds easy.

### Game System

Store the rules of your game system (D&D, Nimble, homebrew...) and define the matching character sheet templates. Indexed rules can be injected into the AI's context for answers that stay true to your system.

### Campaign

Structure your campaigns as Arcs → Chapters → Scenes, with a clear split between GM-only and player-facing content. Manage PCs and NPCs through dynamic sheets based on your chosen game system's templates.

### AI Assistant

A context-aware assistant that pulls from your Lore, rules and campaigns to answer your questions, suggest consistent content, or improvise around an unexpected situation at the table.

The AI runs **locally via [Ollama](https://ollama.com/)** or via **[1min.ai](https://1min.ai/)**. More engines will be supported in the future.

## Documentation

The full documentation (installation, configuration, getting started) lives at **[loremind-docs.igmlcreation.fr/en](https://loremind-docs.igmlcreation.fr/en/)**.

## Live demo

A demo instance is available at **[loremind-demo.igmlcreation.fr](https://loremind-demo.igmlcreation.fr/)**.

A few limitations to be aware of:
- 10 concurrent users maximum (isolated instances)
- Sessions limited to 20 minutes before reset
- The AI part is not included in the demo (requires Ollama or 1min.ai server-side)

## Support the project

LoreMind is **and will remain free when self-hosted**. Development moves faster with your support:

- **[Patreon](https://www.patreon.com/c/IGMLCreation)** — early access to features, roadmap voting, exclusive devlogs
- **[Discord](https://discord.gg/cPpFzCjEzQ)** — announcements, support, user feedback

## License

LoreMind is distributed under the **[GNU AGPL v3](LICENSE)** license.

In practice:
- You can use it for free, host it, modify it, and redistribute it.
- If you modify the code and expose the modified app over a network (even as a private SaaS), you must make your changes public under the same license.
- The worlds (Lore) and campaigns you create with LoreMind **belong entirely to you** — the license only covers the application's code.
