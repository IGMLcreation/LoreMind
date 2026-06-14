package com.loremind.infrastructure.ai;

import com.loremind.domain.conversationcontext.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs (JUnit 5, sans Spring, sans réseau) de
 * {@link BrainConversationTitleClient}.
 *
 * Principe : on injecte un WebClient.Builder préconfiguré avec une
 * ExchangeFunction mock qui renvoie des réponses canned -> aucun appel réseau.
 */
class BrainConversationTitleClientTest {

    private static final String FALLBACK = "Nouvelle conversation";

    /** Construit un client dont le WebClient répond avec la réponse fournie. */
    private BrainConversationTitleClient clientReturning(ClientResponse response) {
        ExchangeFunction ef = req -> Mono.just(response);
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainConversationTitleClient(builder, "http://brain");
    }

    /** Construit un client dont le WebClient émet une erreur transport. */
    private BrainConversationTitleClient clientErroring() {
        ExchangeFunction ef = req -> Mono.error(new RuntimeException("boom"));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
        return new BrainConversationTitleClient(builder, "http://brain");
    }

    private ClientResponse jsonOk(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private ConversationMessage msg(String role, String content) {
        return ConversationMessage.builder().role(role).content(content).build();
    }

    @Test
    void liste_null_renvoie_fallback() {
        // Pas besoin d'appel réseau : court-circuit sur entrée null.
        BrainConversationTitleClient client = clientErroring();
        assertThat(client.generate(null)).isEqualTo(FALLBACK);
    }

    @Test
    void liste_vide_renvoie_fallback() {
        BrainConversationTitleClient client = clientErroring();
        assertThat(client.generate(List.of())).isEqualTo(FALLBACK);
    }

    @Test
    void reponse_ok_avec_titre_renvoie_le_titre() {
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"title\":\"Mon titre\"}"));
        String result = client.generate(List.of(msg("user", "Salut")));
        assertThat(result).isEqualTo("Mon titre");
    }

    @Test
    void titre_avec_espaces_est_trimme() {
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"title\":\"  Espacé  \"}"));
        String result = client.generate(List.of(msg("user", "Bonjour")));
        assertThat(result).isEqualTo("Espacé");
    }

    @Test
    void contenu_message_null_traite_sans_npe() {
        // content null -> mappé en "" dans le payload, ne doit pas lever.
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"title\":\"Ok\"}"));
        String result = client.generate(List.of(msg("assistant", null)));
        assertThat(result).isEqualTo("Ok");
    }

    @Test
    void titre_absent_renvoie_fallback() {
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"autre\":\"x\"}"));
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void titre_vide_renvoie_fallback() {
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"title\":\"\"}"));
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void titre_blanc_renvoie_fallback() {
        BrainConversationTitleClient client = clientReturning(jsonOk("{\"title\":\"   \"}"));
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void corps_json_vide_renvoie_fallback() {
        // Map décodée non null mais sans clé "title".
        BrainConversationTitleClient client = clientReturning(jsonOk("{}"));
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void erreur_transport_renvoie_fallback() {
        BrainConversationTitleClient client = clientErroring();
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }

    @Test
    void reponse_500_renvoie_fallback() {
        ClientResponse err = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"down\"}")
                .build();
        BrainConversationTitleClient client = clientReturning(err);
        String result = client.generate(List.of(msg("user", "Hello")));
        assertThat(result).isEqualTo(FALLBACK);
    }
}
