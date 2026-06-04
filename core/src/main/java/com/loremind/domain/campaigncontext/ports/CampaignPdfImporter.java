package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;

import java.util.function.Consumer;

/**
 * Port de sortie : extrait et structure un PDF de campagne en arbre
 * arc → chapitre → scène. L'implémentation délègue au Brain Python.
 */
public interface CampaignPdfImporter {

    /**
     * Variante streamée : l'import peut durer plusieurs minutes, on remonte
     * l'avancement au fil de l'eau, puis la proposition finale.
     *
     * @param onProgress invoqué à chaque étape (extraction, puis par morceau).
     * @param onDone     invoqué une fois avec l'arbre proposé (non persisté).
     * @param onError    invoqué si l'extraction/structuration échoue.
     */
    void importCampaignStreaming(
            byte[] pdfBytes,
            String filename,
            Consumer<CampaignImportProgress> onProgress,
            Consumer<CampaignImportProposal> onDone,
            Consumer<Throwable> onError);
}
