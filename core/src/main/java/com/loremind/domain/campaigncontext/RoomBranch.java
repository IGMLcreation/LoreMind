package com.loremind.domain.campaigncontext;

/**
 * Sortie d'une pièce vers une autre pièce de la même Scene explorable.
 * Équivalent inter-pièces de {@link SceneBranch}.
 *
 * <p>Record Java immuable, sérialisé via Jackson dans la liste JSONB
 * {@code rooms} de la Scene.</p>
 *
 * <p>Règle métier : {@code targetRoomId} doit pointer vers une Room de la
 * MÊME Scene (validation côté service).</p>
 *
 * @param label         Libellé visible (« Porte nord », « Trappe au sol »).
 * @param targetRoomId  ID stable de la Room de destination (UUID Room.id).
 * @param condition     Condition optionnelle (« si les PJ ont la clé en argent »).
 */
public record RoomBranch(String label, String targetRoomId, String condition) {

    public static RoomBranch of(String label, String targetRoomId) {
        return new RoomBranch(label, targetRoomId, null);
    }
}
