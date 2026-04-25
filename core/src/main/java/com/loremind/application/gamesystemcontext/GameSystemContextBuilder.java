package com.loremind.application.gamesystemcontext;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.GenerationIntent;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.domain.generationcontext.GameSystemContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Construit un {@link GameSystemContext} à partir d'un gameSystemId et d'un intent.
 * <p>
 * Pipeline :
 *  1. Charge le GameSystem (retourne Optional.empty si introuvable — dégradation gracieuse).
 *  2. Parse le markdown par titres H2 (## Section) → Map<Titre, Contenu>.
 *  3. Filtre les sections selon l'intent via les alias {@link GenerationIntent#getSectionAliases()}.
 *     GENERIC = pas de filtre.
 * <p>
 * Parsing à la volée (pas de cache) : les règles d'un système font
 * typiquement 5-20kB, le coût de parsing est négligeable devant l'appel LLM.
 */
@Service
public class GameSystemContextBuilder {

    /** Matche "## Titre" en début de ligne (multiline). Capture le titre en groupe 1. */
    private static final Pattern H2_HEADER = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");

    private final GameSystemRepository gameSystemRepository;

    public GameSystemContextBuilder(GameSystemRepository gameSystemRepository) {
        this.gameSystemRepository = gameSystemRepository;
    }

    public Optional<GameSystemContext> buildOptional(String gameSystemId, GenerationIntent intent) {
        if (gameSystemId == null || gameSystemId.isBlank()) return Optional.empty();
        return gameSystemRepository.findById(gameSystemId)
                .map(gs -> build(gs, intent));
    }

    private GameSystemContext build(GameSystem gs, GenerationIntent intent) {
        Map<String, String> allSections = parseH2Sections(gs.getRulesMarkdown());
        Map<String, String> filtered = filterByIntent(allSections, intent);
        return new GameSystemContext(gs.getName(), gs.getDescription(), filtered);
    }

    /**
     * Découpe le markdown par titres H2. Préserve l'ordre d'apparition (LinkedHashMap).
     * Le contenu avant le premier H2 est ignoré (préambule libre).
     */
    Map<String, String> parseH2Sections(String markdown) {
        Map<String, String> sections = new LinkedHashMap<>();
        if (markdown == null || markdown.isBlank()) return sections;

        Matcher m = H2_HEADER.matcher(markdown);
        String currentTitle = null;
        int currentContentStart = -1;

        while (m.find()) {
            if (currentTitle != null) {
                sections.put(currentTitle, markdown.substring(currentContentStart, m.start()).strip());
            }
            currentTitle = m.group(1).trim();
            currentContentStart = m.end();
        }
        if (currentTitle != null) {
            sections.put(currentTitle, markdown.substring(currentContentStart).strip());
        }
        return sections;
    }

    private Map<String, String> filterByIntent(Map<String, String> sections, GenerationIntent intent) {
        if (intent.matchesAllSections()) return sections;
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sections.entrySet()) {
            String titleLower = e.getKey().toLowerCase();
            boolean match = intent.getSectionAliases().stream().anyMatch(titleLower::contains);
            if (match) {
                filtered.put(e.getKey(), e.getValue());
            }
        }
        return filtered;
    }
}
