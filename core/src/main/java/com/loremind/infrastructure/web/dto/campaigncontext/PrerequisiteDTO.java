package com.loremind.infrastructure.web.dto.campaigncontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO unique pour tous les types de Prerequisite.
 * Le champ {@code kind} discrimine le type (miroir du converter JPA et de l'union TS côté front).
 * Les champs non pertinents pour un kind donné restent null.
 * <p>
 * Valeurs de {@code kind} : "QUEST_COMPLETED" | "SESSION_REACHED" | "FLAG_SET".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrerequisiteDTO {

    /** Discriminant : QUEST_COMPLETED | SESSION_REACHED | FLAG_SET. */
    private String kind;

    /** Pour kind=QUEST_COMPLETED : ID de la quête à terminer. */
    private String questId;

    /** Pour kind=SESSION_REACHED : numéro de session minimum atteint. */
    private Integer minSessionNumber;

    /** Pour kind=FLAG_SET : nom du flag de campagne. */
    private String flagName;
}
