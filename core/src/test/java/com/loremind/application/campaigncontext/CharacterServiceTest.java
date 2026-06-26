package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CharacterService.
 * Mock du port CharacterRepository.
 */
@ExtendWith(MockitoExtension.class)
public class CharacterServiceTest {

    @Mock
    private CharacterRepository characterRepository;

    @InjectMocks
    private CharacterService service;

    private static CharacterService.CharacterData data(Integer order, String playthroughId) {
        return new CharacterService.CharacterData(
                "Aragorn", "portrait-1", "header-1",
                Map.of("Classe", "Rôdeur"),
                Map.of("Galerie", List.of("img-1")),
                Map.of("Stats", Map.of("FOR", "16")),
                playthroughId, order);
    }

    // --- createCharacter ---

    @Test
    void testCreateCharacter_WithExplicitOrder() {
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));

        Character result = service.createCharacter(data(4, "pt-1"));

        assertEquals("Aragorn", result.getName());
        assertEquals(4, result.getOrder());
        assertEquals("Rôdeur", result.getValues().get("Classe"));
        assertEquals("pt-1", result.getPlaythroughId());
        verify(characterRepository, never()).findByPlaythroughId(anyString());
    }

    @Test
    void testCreateCharacter_ComputesNextOrderWhenNull() {
        when(characterRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                Character.builder().order(1).build(),
                Character.builder().order(4).build()));
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));

        Character result = service.createCharacter(data(null, "pt-1"));

        assertEquals(5, result.getOrder()); // max(1,4)+1
    }

    @Test
    void testCreateCharacter_NextOrderZeroWhenNoExisting() {
        when(characterRepository.findByPlaythroughId("pt-1")).thenReturn(List.of());
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));

        Character result = service.createCharacter(data(null, "pt-1"));

        assertEquals(0, result.getOrder());
    }

    @Test
    void testCreateCharacter_NullMapsBecomeEmpty() {
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));
        CharacterService.CharacterData d = new CharacterService.CharacterData(
                "Bob", null, null, null, null, null, "pt-1", 0);

        Character result = service.createCharacter(d);

        assertNotNull(result.getValues());
        assertTrue(result.getValues().isEmpty());
        assertNotNull(result.getImageValues());
        assertTrue(result.getImageValues().isEmpty());
        assertNotNull(result.getKeyValueValues());
        assertTrue(result.getKeyValueValues().isEmpty());
    }

    // --- read ---

    @Test
    void testGetCharacterById_Found() {
        Character c = Character.builder().id("c-1").name("Aragorn").build();
        when(characterRepository.findById("c-1")).thenReturn(Optional.of(c));

        Optional<Character> result = service.getCharacterById("c-1");

        assertTrue(result.isPresent());
        assertEquals("Aragorn", result.get().getName());
    }

    @Test
    void testGetCharactersByPlaythroughId() {
        when(characterRepository.findByPlaythroughId("pt-1"))
                .thenReturn(List.of(Character.builder().id("c-1").build()));

        List<Character> result = service.getCharactersByPlaythroughId("pt-1");

        assertEquals(1, result.size());
    }

    // --- updateCharacter ---

    @Test
    void testUpdateCharacter_Success() {
        Character existing = Character.builder().id("c-1").name("Old").order(2).playthroughId("pt-1").build();
        when(characterRepository.findById("c-1")).thenReturn(Optional.of(existing));
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));

        Character result = service.updateCharacter("c-1", data(null, "pt-2"));

        assertEquals("Aragorn", result.getName());
        assertEquals("Rôdeur", result.getValues().get("Classe"));
        // order null -> conserve l'ordre existant.
        assertEquals(2, result.getOrder());
        // playthroughId immuable : inchangé malgré data.playthroughId="pt-2".
        assertEquals("pt-1", result.getPlaythroughId());
    }

    @Test
    void testUpdateCharacter_AppliesOrderWhenProvided() {
        Character existing = Character.builder().id("c-1").name("Old").order(2).build();
        when(characterRepository.findById("c-1")).thenReturn(Optional.of(existing));
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> inv.getArgument(0));

        Character result = service.updateCharacter("c-1", data(8, "pt-1"));

        assertEquals(8, result.getOrder());
    }

    @Test
    void testUpdateCharacter_NotFound() {
        when(characterRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateCharacter("missing", data(1, "pt-1")));
        assertEquals("Character non trouvé avec l'ID: missing", ex.getMessage());
        verify(characterRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void testDeleteCharacter() {
        service.deleteCharacter("c-1");
        verify(characterRepository).deleteById("c-1");
    }

    // --- searchCharacters ---

    @Test
    void testSearchCharacters_BlankReturnsEmpty() {
        assertTrue(service.searchCharacters(null).isEmpty());
        assertTrue(service.searchCharacters("  ").isEmpty());
        verify(characterRepository, never()).searchByName(anyString());
    }

    @Test
    void testSearchCharacters_TrimsAndDelegates() {
        when(characterRepository.searchByName("ara")).thenReturn(List.of(Character.builder().id("c-1").build()));

        List<Character> result = service.searchCharacters("  ara  ");

        assertEquals(1, result.size());
        verify(characterRepository).searchByName("ara");
    }
}
