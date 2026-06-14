package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.CampaignImportProgress;
import com.loremind.domain.campaigncontext.CampaignImportProposal;
import com.loremind.domain.campaigncontext.ports.CampaignImportException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires PURS (JUnit 5, sans Spring ni réseau) pour {@link BrainCampaignImportClient}.
 * <p>
 * NB : contrairement à la consigne initiale, ce client est entièrement WebClient + SSE
 * (POST /import/campaign/stream) — il n'y a PAS de RestTemplate ni d'appel one-shot.
 * On injecte donc un WebClient.Builder dont l'ExchangeFunction renvoie un corps SSE
 * canned (ou échoue), ce qui couvre {@code handleEvent} et tous les helpers de parsing.
 */
class BrainCampaignImportClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Construit un client dont le WebClient renvoie le corps SSE fourni. */
    private BrainCampaignImportClient clientReturning(String sseBody) {
        ExchangeFunction ef = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                        .body(sseBody)
                        .build());
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainCampaignImportClient(builder, MAPPER, "http://brain", 30);
    }

    /** Construit un client dont le transport échoue immédiatement (Mono.error). */
    private BrainCampaignImportClient clientFailingWith(Throwable boom) {
        ExchangeFunction ef = req -> Mono.error(boom);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainCampaignImportClient(builder, MAPPER, "http://brain", 30);
    }

    /** Collecteur mutable réunissant tous les callbacks de l'import streamé. */
    private static final class Collector {
        final List<CampaignImportProgress> progresses = new ArrayList<>();
        final AtomicInteger heartbeats = new AtomicInteger(0);
        final List<String> statuses = new ArrayList<>();
        final AtomicReference<CampaignImportProposal> done = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        void invoke(BrainCampaignImportClient client) {
            invoke(client, "campaign.pdf");
        }

        void invoke(BrainCampaignImportClient client, String filename) {
            client.importCampaignStreaming(
                    new byte[]{1, 2, 3},
                    filename,
                    progresses::add,
                    heartbeats::incrementAndGet,
                    statuses::add,
                    done::set,
                    error::set);
        }
    }

    // ---------- flux nominal : start -> progress -> done --------------------

    @Test
    void streame_start_progress_done_construit_la_proposition() {
        // SSE déclenchant start (page/ocr counts), progress (compteurs), puis done (arbre complet).
        String sse =
                "event:start\ndata:{\"total\":5,\"page_count\":12,\"ocr_page_count\":3}\n\n" +
                "event:progress\ndata:{\"current\":2,\"total\":5,\"arc_count\":1,\"chapter_count\":2,\"scene_count\":4,\"npc_count\":6}\n\n" +
                "event:done\ndata:" + doneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        // start : current=0, total=5, pageCount=12, ocrPageCount=3, reste 0.
        assertEquals(2, c.progresses.size());
        CampaignImportProgress start = c.progresses.get(0);
        assertEquals(0, start.current());
        assertEquals(5, start.total());
        assertEquals(12, start.pageCount());
        assertEquals(3, start.ocrPageCount());

        // progress : compteurs propagés + pageCount/ocr mémorisés depuis start.
        CampaignImportProgress prog = c.progresses.get(1);
        assertEquals(2, prog.current());
        assertEquals(5, prog.total());
        assertEquals(12, prog.pageCount());
        assertEquals(3, prog.ocrPageCount());
        assertEquals(1, prog.arcCount());
        assertEquals(2, prog.chapterCount());
        assertEquals(4, prog.sceneCount());
        assertEquals(6, prog.npcCount());

        // done : arbre désérialisé (arcs/chapters/scenes/rooms + npcs).
        CampaignImportProposal proposal = c.done.get();
        assertNotNull(proposal);
        assertEquals(1, proposal.arcs().size());
        var arc = proposal.arcs().get(0);
        assertEquals("Acte I", arc.name());
        assertEquals("Mise en place", arc.description());
        assertEquals("LINEAR", arc.type());
        assertEquals(1, arc.chapters().size());
        var chapter = arc.chapters().get(0);
        assertEquals("Chapitre 1", chapter.name());
        assertEquals(1, chapter.scenes().size());
        var scene = chapter.scenes().get(0);
        assertEquals("L'auberge", scene.name());
        assertEquals("Lisez ceci", scene.playerNarration());
        assertEquals("Secret MJ", scene.gmNotes());
        assertEquals(1, scene.rooms().size());
        var room = scene.rooms().get(0);
        assertEquals("Cave", room.name());
        assertEquals("2 gobelins", room.enemies());
        assertEquals("50 po", room.loot());
        assertEquals(1, proposal.npcs().size());
        assertEquals("Thorin", proposal.npcs().get(0).name());
        assertEquals("Nain bougon", proposal.npcs().get(0).description());

        assertNull(c.error.get(), "aucune erreur sur un flux terminé par done");
    }

    private static String doneJson() {
        return "{"
                + "\"arcs\":[{"
                + "  \"name\":\"Acte I\",\"description\":\"Mise en place\",\"type\":\"LINEAR\","
                + "  \"chapters\":[{"
                + "    \"name\":\"Chapitre 1\",\"description\":\"intro\","
                + "    \"scenes\":[{"
                + "      \"name\":\"L'auberge\",\"description\":\"tendue\","
                + "      \"player_narration\":\"Lisez ceci\",\"gm_notes\":\"Secret MJ\","
                + "      \"rooms\":[{\"name\":\"Cave\",\"description\":\"sombre\",\"enemies\":\"2 gobelins\",\"loot\":\"50 po\"}]"
                + "    }]"
                + "  }]"
                + "}],"
                + "\"npcs\":[{\"name\":\"Thorin\",\"description\":\"Nain bougon\"}]"
                + "}";
    }

    // ---------- events simples : heartbeat / status / chunk_failed / extracting

    @Test
    void event_heartbeat_propage_le_keepalive() {
        String sse =
                "event:heartbeat\ndata:\n\n" +
                "event:heartbeat\ndata:\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals(2, c.heartbeats.get());
        assertNotNull(c.done.get());
    }

    @Test
    void event_status_relaie_le_message_lisible() {
        String sse =
                "event:status\ndata:{\"message\":\"Fournisseur saturé, nouvelle tentative\"}\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals(1, c.statuses.size());
        assertEquals("Fournisseur saturé, nouvelle tentative", c.statuses.get(0));
    }

    @Test
    void event_status_sans_champ_message_relaie_data_brut() {
        // readMessage : pas de champ "message" -> renvoie la data brute.
        String sse =
                "event:status\ndata:texte-brut\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("texte-brut", c.statuses.get(0));
    }

    @Test
    void event_chunk_failed_compose_un_status_avec_compteurs_et_message() {
        String sse =
                "event:chunk_failed\ndata:{\"current\":3,\"total\":10,\"message\":\"timeout LLM\"}\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("Morceau 3/10 ignoré : timeout LLM", c.statuses.get(0));
    }

    @Test
    void event_chunk_failed_sans_message_termine_par_un_point() {
        // Branche msg.isEmpty() -> suffixe "." au lieu de " : <msg>".
        String sse =
                "event:chunk_failed\ndata:{\"current\":1,\"total\":4}\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("Morceau 1/4 ignoré.", c.statuses.get(0));
    }

    @Test
    void event_chunk_failed_avec_json_invalide_donne_zero_zero() {
        // data non-JSON -> readJson renvoie null -> current/total à 0, suffixe ".".
        String sse =
                "event:chunk_failed\ndata:pas-du-json\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("Morceau 0/0 ignoré.", c.statuses.get(0));
    }

    @Test
    void event_extracting_emet_un_progress_neutre() {
        String sse =
                "event:extracting\ndata:\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals(1, c.progresses.size());
        CampaignImportProgress p = c.progresses.get(0);
        assertEquals(0, p.current());
        assertEquals(0, p.total());
        assertEquals(0, p.pageCount());
        assertEquals(0, p.npcCount());
    }

    private static String emptyDoneJson() {
        return "{\"arcs\":[],\"npcs\":[]}";
    }

    // ---------- event error (terminal) -------------------------------------

    @Test
    void event_error_appelle_onError_et_n_appelle_pas_onDone() {
        String sse =
                "event:start\ndata:{\"total\":2,\"page_count\":1,\"ocr_page_count\":0}\n\n" +
                "event:error\ndata:{\"message\":\"PDF illisible\"}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.error.get());
        assertInstanceOf(CampaignImportException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("PDF illisible"));
        assertNull(c.done.get(), "onDone non appelé après un error terminal");
    }

    @Test
    void event_error_sans_message_relaie_data_brut() {
        String sse = "event:error\ndata:erreur-brute\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.error.get());
        assertTrue(c.error.get().getMessage().contains("erreur-brute"));
    }

    // ---------- branches de robustesse du parsing --------------------------

    @Test
    void event_inconnu_avec_data_non_json_est_ignore() {
        // event non géré + data non-JSON -> readJson null -> return sans effet ;
        // flux clos sans done -> branche d'interruption (onError).
        String sse = "event:mystere\ndata:pas-du-json\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertTrue(c.progresses.isEmpty());
        assertTrue(c.statuses.isEmpty());
        assertNull(c.done.get());
        assertNotNull(c.error.get(), "flux interrompu sans done/error -> onError");
    }

    @Test
    void start_avec_champs_absents_utilise_les_valeurs_par_defaut() {
        // JSON valide mais sans page_count/ocr/total -> path().asInt() == 0.
        String sse =
                "event:start\ndata:{}\n\n" +
                "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        CampaignImportProgress p = c.progresses.get(0);
        assertEquals(0, p.total());
        assertEquals(0, p.pageCount());
        assertEquals(0, p.ocrPageCount());
    }

    @Test
    void done_avec_arbre_vide_donne_une_proposition_vide() {
        // Couvre toArcs/toNpcs sur des tableaux vides + text() sur champs absents.
        String sse = "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.done.get());
        assertTrue(c.done.get().arcs().isEmpty());
        assertTrue(c.done.get().npcs().isEmpty());
    }

    @Test
    void done_avec_arc_sans_chapitres_et_npc_sans_description() {
        // toChapters sur noeud absent (path -> MissingNode, non-array) -> liste vide ;
        // text() sur "description" absent -> "".
        String sse = "event:done\ndata:{"
                + "\"arcs\":[{\"name\":\"Solo\"}],"
                + "\"npcs\":[{\"name\":\"Anon\"}]"
                + "}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        var proposal = c.done.get();
        assertNotNull(proposal);
        var arc = proposal.arcs().get(0);
        assertEquals("Solo", arc.name());
        assertEquals("", arc.description());
        assertEquals("", arc.type());
        assertTrue(arc.chapters().isEmpty());
        assertEquals("Anon", proposal.npcs().get(0).name());
        assertEquals("", proposal.npcs().get(0).description());
    }

    @Test
    void done_avec_champ_explicitement_null_donne_chaine_vide() {
        // text() : valeur JSON null -> "" (branche v.isNull()).
        String sse = "event:done\ndata:{"
                + "\"arcs\":[{\"name\":null,\"description\":\"d\"}],"
                + "\"npcs\":[]"
                + "}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertEquals("", c.done.get().arcs().get(0).name());
        assertEquals("d", c.done.get().arcs().get(0).description());
    }

    // ---------- fin de flux sans terminaison -------------------------------

    @Test
    void flux_clos_sans_done_ni_error_appelle_onError() {
        // terminated reste false -> branche "Le flux d'import s'est interrompu...".
        String sse = "event:progress\ndata:{\"current\":1,\"total\":3}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNull(c.done.get());
        assertNotNull(c.error.get());
        assertInstanceOf(CampaignImportException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("interrompu"));
    }

    // ---------- erreur de transport ----------------------------------------

    @Test
    void erreur_transport_traduite_en_CampaignImportException() {
        // Mono.error -> blockLast lève -> branche catch, expose type + message de la cause.
        Collector c = new Collector();
        c.invoke(clientFailingWith(new RuntimeException("connexion coupée")));

        assertNotNull(c.error.get());
        assertInstanceOf(CampaignImportException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("streaming d'import"));
        assertTrue(c.error.get().getMessage().contains("connexion coupée"));
        assertNull(c.done.get());
    }

    @Test
    void erreur_transport_sans_message_reste_geree() {
        // Cause sans message -> branche getMessage() == null (pas de " — ...").
        Collector c = new Collector();
        c.invoke(clientFailingWith(new RuntimeException()));

        assertNotNull(c.error.get());
        assertInstanceOf(CampaignImportException.class, c.error.get());
        assertTrue(c.error.get().getMessage().contains("streaming d'import"));
    }

    @Test
    void erreur_transport_apres_event_error_ne_double_pas_le_callback() {
        // error terminal puis le flux se clôt : terminated[0]==true -> pas de second onError.
        // (vérifie que le callback n'est appelé qu'une fois via le message attendu.)
        String sse = "event:error\ndata:{\"message\":\"stop\"}\n\n";
        Collector c = new Collector();
        c.invoke(clientReturning(sse));

        assertNotNull(c.error.get());
        assertTrue(c.error.get().getMessage().contains("stop"));
        assertFalse(c.error.get().getMessage().contains("interrompu"));
    }

    // ---------- nom de fichier ---------------------------------------------

    @Test
    void filename_null_ou_blanc_est_accepte() {
        // Couvre la branche de repli "campaign.pdf" dans filePart + part().filename().
        String sse = "event:done\ndata:" + emptyDoneJson() + "\n\n";
        Collector c1 = new Collector();
        c1.invoke(clientReturning(sse), null);
        assertNotNull(c1.done.get());

        Collector c2 = new Collector();
        c2.invoke(clientReturning(sse), "   ");
        assertNotNull(c2.done.get());

        Collector c3 = new Collector();
        c3.invoke(clientReturning(sse), "mon-livre.pdf");
        assertNotNull(c3.done.get());
    }
}
