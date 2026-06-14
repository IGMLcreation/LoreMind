package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.RulesImportProgress;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.infrastructure.web.mapper.GameSystemMapper;
import com.loremind.infrastructure.web.mapper.TemplateFieldMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test UNITAIRE pur (sans Spring) de {@link GameSystemController} dédié au code
 * défensif de déconnexion du navigateur de l'import streamé, INTESTABLE via MockMvc
 * (un envoi SSE après {@code complete()} y remonte en ServletException au lieu
 * d'exécuter la branche). Voir {@link CampaignImportControllerUnitTest} pour le détail
 * de l'approche — même séquence : complete -> send échoue -> {@code clientGone=true}
 * -> les callbacks suivants empruntent les branches {@code ClientGoneException}.
 */
class GameSystemControllerUnitTest {

    private final GameSystemService service = mock(GameSystemService.class);
    private final TaskExecutor inlineExecutor = Runnable::run;
    private final GameSystemController controller = new GameSystemController(
            service, mock(GameSystemMapper.class), mock(TemplateFieldMapper.class),
            inlineExecutor, new ObjectMapper());

    private static RulesImportProgress progress() {
        return new RulesImportProgress(1, 1, 0, 0, List.of());
    }

    @Test
    void importRulesStream_clientDisconnect_exercisesDefensiveBranches() throws Exception {
        doAnswer(inv -> {
            Consumer<RulesImportProgress> onProgress = inv.getArgument(2);
            Runnable onHeartbeat = inv.getArgument(3);
            Consumer<RulesImportResult> onDone = inv.getArgument(5);
            Consumer<Throwable> onError = inv.getArgument(6);

            // 1) Termine le flux (event "done" + complete()).
            onDone.accept(new RulesImportResult(Map.of(), 0, 0));
            // 2) Envoi post-complete : send échoue -> catch -> clientGone=true.
            try { onProgress.accept(progress()); } catch (RuntimeException ignored) { }
            // 3) clientGone=true -> sendImportHeartbeat lève ClientGoneException.
            try { onHeartbeat.run(); } catch (RuntimeException ignored) { }
            // 4) clientGone=true -> sendImportEvent lève ClientGoneException.
            try { onProgress.accept(progress()); } catch (RuntimeException ignored) { }
            // 5) clientGone=true -> callback d'erreur en early-return.
            onError.accept(new RuntimeException("tardif"));
            return null;
        }).when(service).importRulesFromPdfStreaming(any(), any(), any(), any(), any(), any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "regles.pdf", "application/pdf", new byte[]{1, 2, 3});

        SseEmitter emitter = controller.importRulesStream(file);

        assertNotNull(emitter);
        verify(service).importRulesFromPdfStreaming(any(), any(), any(), any(), any(), any(), any());
    }
}
