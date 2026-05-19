package com.loremind.infrastructure.web.dto.licensing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loremind.application.licensing.ChannelSwitcherService;

/**
 * Etat du canal courant + dernier resultat de switch.
 *
 * <p>{@code currentChannel} : detecte au demarrage de Core a partir du prefixe
 * d'image. {@code switcherAvailable} : indique si le sidecar de switch est
 * monte (V0.9+) ou si on est sur une vieille install qui doit encore passer
 * par les instructions manuelles.
 *
 * <p>{@code lastSwitch} : null tant qu'aucun switch n'a ete tente sur cette
 * instance. Sinon, contient le resultat du dernier appel (en cours / succes /
 * erreur), utilise par l'UI pour suivre la progression apres clic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelStatusDTO(
        String currentChannel,
        boolean switcherAvailable,
        ChannelSwitcherService.SwitchResult lastSwitch) {

    public static ChannelStatusDTO from(ChannelSwitcherService service) {
        return new ChannelStatusDTO(
                service.getCurrentChannel().name().toLowerCase(),
                service.isSwitcherAvailable(),
                service.getLastResult());
    }
}
