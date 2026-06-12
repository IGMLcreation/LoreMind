package com.loremind.domain.gamesystemcontext.ports;

import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;

import java.util.function.Consumer;

/**
 * Port de sortie : extrait et structure les règles d'un PDF en sections.
 * <p>
 * L'implémentation (adapter) délègue au Brain Python (extraction texte + OCR +
 * structuration LLM). Le domaine ne connaît ni HTTP, ni le Brain, ni le LLM.
 */
public interface RulesPdfImporter {

    /**
     * @param pdfBytes contenu binaire du PDF de règles.
     * @param filename nom d'origine (diagnostic/logs ; peut être null).
     * @return la proposition de sections (non persistée).
     * @throws RulesImportException si l'extraction ou la structuration échoue.
     */
    RulesImportResult importRules(byte[] pdfBytes, String filename);

    /**
     * Variante streamée : l'import peut durer plusieurs minutes, on remonte
     * l'avancement au fil de l'eau. Les callbacks sont invoqués depuis le thread
     * d'exécution de l'adapter (synchrone jusqu'à {@code onDone}/{@code onError}).
     *
     * @param onProgress  invoqué à chaque étape (extraction, puis par morceau).
     * @param onHeartbeat invoqué périodiquement pendant un appel LLM long (aucune
     *                    avancée à afficher, mais le canal SSE vers le navigateur
     *                    doit rester actif — sinon un proxy intermédiaire le coupe).
     * @param onStatus    invoqué avec un message lisible quand quelque chose se
     *                    passe pendant l'attente (fournisseur saturé → retry,
     *                    morceau re-découpé, morceau ignoré…) — affiché par l'UI
     *                    pour que l'utilisateur n'ait pas à lire les logs.
     * @param onDone      invoqué une fois avec le résultat final.
     * @param onError     invoqué si l'extraction/structuration échoue.
     */
    void importRulesStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<RulesImportProgress> onProgress,
            Runnable onHeartbeat,
            Consumer<String> onStatus,
            Consumer<RulesImportResult> onDone,
            Consumer<Throwable> onError);
}
