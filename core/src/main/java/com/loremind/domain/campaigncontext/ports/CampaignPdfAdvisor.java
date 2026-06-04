package com.loremind.domain.campaigncontext.ports;

import java.util.function.Consumer;

/**
 * Port de sortie : produit des CONSEILS d'adaptation d'un PDF à une campagne
 * existante, streamés token par token. Délègue au Brain (LLM + extraction PDF).
 * <p>
 * Contrairement à {@link CampaignPdfImporter} (qui structure pour créer), ici la
 * sortie est du texte libre (markdown) : l'utilisateur applique à la main.
 */
public interface CampaignPdfAdvisor {

    /**
     * @param pdfBytes contenu du PDF à adapter.
     * @param filename nom d'origine (diagnostic ; peut être null).
     * @param brief    description de la campagne existante (structure + PNJ + lore).
     * @param messagesJson JSON de l'échange conversationnel ([{role, content}, …]) ;
     *                     "[]" au 1er tour. Permet à l'utilisateur de répondre/corriger.
     * @param onToken  invoqué à chaque fragment de texte généré.
     * @param onComplete invoqué à la fin normale du flux.
     * @param onError  invoqué en cas d'échec (PDF illisible, Brain/LLM en erreur).
     */
    void adviseStreaming(
            byte[] pdfBytes,
            String filename,
            String brief,
            String messagesJson,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError);
}
