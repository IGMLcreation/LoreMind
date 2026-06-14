package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.campaigncontext.CampaignImportService;
import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test UNITAIRE pur (sans Spring) de {@link CampaignImportController} dédié au code
 * défensif de déconnexion du navigateur, INTESTABLE via MockMvc : là-bas, un envoi
 * SSE après {@code complete()} met l'emitter dans l'état « already completed » qui
 * remonte en {@code ServletException} (échec de test) au lieu d'exécuter la branche.
 * <p>
 * En instanciant le controller à la main (exécuteur en ligne, vrai ObjectMapper,
 * service mocké), on pilote directement la séquence : envoi initial → {@code complete()}
 * → un envoi ultérieur échoue et bascule {@code clientGone=true} → les callbacks
 * suivants empruntent alors les branches {@code ClientGoneException} / early-return.
 */
class CampaignImportControllerUnitTest {

    private final CampaignImportService service = mock(CampaignImportService.class);
    /** Exécuteur synchrone : la tâche d'import tourne dans le thread du test. */
    private final TaskExecutor inlineExecutor = Runnable::run;
    private final CampaignImportController controller =
            new CampaignImportController(service, inlineExecutor, new ObjectMapper());

    private static CampaignImportProgress progress() {
        return new CampaignImportProgress(1, 1, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void importStream_clientDisconnect_exercisesDefensiveBranches() throws Exception {
        // Scénario de déconnexion : on termine le flux puis on continue d'émettre.
        // Le 1er envoi post-complete échoue -> clientGone=true ; les callbacks suivants
        // court-circuitent (ClientGoneException sur sendEvent/sendHeartbeat, early-return
        // sur le callback d'erreur). Chaque étape est isolée car ClientGoneException
        // (privée) se propage hors des callbacks — comme en prod où elle remonte au
        // pipeline amont pour stopper le Brain.
        doAnswer(inv -> {
            Consumer<CampaignImportProgress> onProgress = inv.getArgument(2);
            Runnable onHeartbeat = inv.getArgument(3);
            Consumer<CampaignImportProposal> onDone = inv.getArgument(5);
            Consumer<Throwable> onError = inv.getArgument(6);

            // 1) Termine proprement le flux (event "done" + complete()).
            onDone.accept(new CampaignImportProposal(List.of(), List.of()));
            // 2) Envoi post-complete : send échoue -> catch -> clientGone=true.
            try { onProgress.accept(progress()); } catch (RuntimeException ignored) { }
            // 3) clientGone=true -> sendHeartbeat lève ClientGoneException (branche garde).
            try { onHeartbeat.run(); } catch (RuntimeException ignored) { }
            // 4) clientGone=true -> sendEvent lève ClientGoneException (branche garde).
            try { onProgress.accept(progress()); } catch (RuntimeException ignored) { }
            // 5) clientGone=true -> le callback d'erreur prend l'early-return (pas d'envoi).
            onError.accept(new RuntimeException("tardif"));
            return null;
        }).when(service).importStructureStreaming(any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "campagne.pdf", "application/pdf", new byte[]{1, 2, 3});

        SseEmitter emitter = controller.importStream("camp-1", file);

        assertNotNull(emitter);
        verify(service).importStructureStreaming(any(), any(), any(), any(), any(), any(), any());
    }
}
