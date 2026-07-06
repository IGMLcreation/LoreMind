package com.loremind.infrastructure.transfer;

import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import com.loremind.infrastructure.transfer.dto.ContentExport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * État partagé entre les phases d'un import (cf. {@link ImportService}) : maps de
 * remapping {@code oldId → newId} par type, alimentées par les inserters (1re passe)
 * et consommées par {@link ImportReferenceRemapper} (2e passe), plus les entités dont
 * une référence n'a pas pu être résolue à l'insertion.
 */
final class ImportIdMaps {

    // Images/fichiers : remappés dès la 1re phase (importés AVANT les inserters), pour
    // que toute réf d'image (portrait, illustration, imageValues, plan de salle) ou de
    // fichier (battlemap) porte le NOUVEL id dans la base cible. Sans ça, un import
    // inter-machines laisse des refs pendantes ou pointant l'image d'un autre id.
    final Map<Long, Long> imageMap = new HashMap<>();
    final Map<Long, Long> storedFileMap = new HashMap<>();

    final Map<Long, Long> gameSystemMap = new HashMap<>();
    final Map<Long, Long> loreMap = new HashMap<>();
    final Map<Long, Long> loreNodeMap = new HashMap<>();
    final Map<Long, Long> templateMap = new HashMap<>();
    final Map<Long, Long> pageMap = new HashMap<>();
    final Map<Long, Long> campaignMap = new HashMap<>();
    final Map<Long, Long> arcMap = new HashMap<>();
    final Map<Long, Long> chapterMap = new HashMap<>();
    final Map<Long, Long> npcMap = new HashMap<>();
    final Map<Long, Long> enemyMap = new HashMap<>();
    final Map<Long, Long> characterMap = new HashMap<>();
    final Map<Long, Long> sceneMap = new HashMap<>();
    final Map<Long, Long> questMap = new HashMap<>();
    final Map<Long, Long> playthroughMap = new HashMap<>();
    final Map<Long, Long> sessionMap = new HashMap<>();
    final Map<Long, Long> clockMap = new HashMap<>();
    final Map<Long, Long> frontMap = new HashMap<>();

    /** LoreNodes sauvés avec leur parentId d'origine, à remapper en 2e passe. */
    final List<LoreNodeJpaEntity> loreNodesToFix = new ArrayList<>();

    /** Templates du bundle portant un defaultNodeId, à remapper en 2e passe. */
    final List<ContentExport.TemplateDto> templatesWithDefaultNode = new ArrayList<>();
}
