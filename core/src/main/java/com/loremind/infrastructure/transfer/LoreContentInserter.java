package com.loremind.infrastructure.transfer;

import com.loremind.infrastructure.persistence.entity.GameSystemJpaEntity;
import com.loremind.infrastructure.persistence.entity.LoreJpaEntity;
import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import com.loremind.infrastructure.persistence.entity.PageJpaEntity;
import com.loremind.infrastructure.persistence.entity.TemplateJpaEntity;
import com.loremind.infrastructure.persistence.jpa.GameSystemJpaRepository;
import com.loremind.infrastructure.persistence.jpa.LoreJpaRepository;
import com.loremind.infrastructure.persistence.jpa.LoreNodeJpaRepository;
import com.loremind.infrastructure.persistence.jpa.PageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.TemplateJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1re passe d'import du référentiel (cf. {@link ImportService}) : GameSystem, Lore,
 * LoreNode, Template, Page. Alimente les maps de remapping correspondantes de
 * {@link ImportIdMaps} ; les références résolues plus tard (parentId, defaultNodeId,
 * relatedPageIds) sont notées pour la 2e passe.
 */
@Component
class LoreContentInserter {

    private final GameSystemJpaRepository gameSystemRepo;
    private final LoreJpaRepository loreRepo;
    private final LoreNodeJpaRepository loreNodeRepo;
    private final TemplateJpaRepository templateRepo;
    private final PageJpaRepository pageRepo;

    LoreContentInserter(GameSystemJpaRepository gameSystemRepo,
                        LoreJpaRepository loreRepo,
                        LoreNodeJpaRepository loreNodeRepo,
                        TemplateJpaRepository templateRepo,
                        PageJpaRepository pageRepo) {
        this.gameSystemRepo = gameSystemRepo;
        this.loreRepo = loreRepo;
        this.loreNodeRepo = loreNodeRepo;
        this.templateRepo = templateRepo;
        this.pageRepo = pageRepo;
    }

    void insert(ContentExport export, ImportIdMaps maps, ImportResult.Builder result) {
        // -- GameSystem
        for (ContentExport.GameSystemDto d : nullSafe(export.gameSystems())) {
            GameSystemJpaEntity e = new GameSystemJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setRulesMarkdown(d.rulesMarkdown());
            e.setCharacterTemplate(d.characterTemplate());
            e.setNpcTemplate(d.npcTemplate());
            e.setEnemyTemplate(d.enemyTemplate());
            e.setFoundryActorType(d.foundryActorType());
            e.setAuthor(d.author());
            e.setPublic(d.isPublic());
            maps.gameSystemMap.put(d.id(), gameSystemRepo.save(e).getId());
        }
        result.count("gameSystems", maps.gameSystemMap.size());

        // -- Lore
        for (ContentExport.LoreDto d : nullSafe(export.lores())) {
            LoreJpaEntity e = new LoreJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setNodeCount(d.nodeCount());
            e.setPageCount(d.pageCount());
            maps.loreMap.put(d.id(), loreRepo.save(e).getId());
        }
        result.count("lores", maps.loreMap.size());

        // -- LoreNode (parentId remappe en 2e passe)
        for (ContentExport.LoreNodeDto d : nullSafe(export.loreNodes())) {
            LoreNodeJpaEntity e = new LoreNodeJpaEntity();
            e.setName(d.name());
            e.setIcon(d.icon());
            e.setParentId(d.parentId()); // remappe en 2e passe
            e.setLoreId(IdRemapper.remapId(maps.loreMap, d.loreId()));
            LoreNodeJpaEntity saved = loreNodeRepo.save(e);
            maps.loreNodeMap.put(d.id(), saved.getId());
            if (d.parentId() != null) maps.loreNodesToFix.add(saved);
        }
        result.count("loreNodes", maps.loreNodeMap.size());

        // -- Template (defaultNodeId remappe en 2e passe)
        for (ContentExport.TemplateDto d : nullSafe(export.templates())) {
            TemplateJpaEntity e = new TemplateJpaEntity();
            e.setLoreId(IdRemapper.remapId(maps.loreMap, d.loreId()));
            e.setName(d.name());
            e.setDescription(d.description());
            e.setDefaultNodeId(d.defaultNodeId()); // remappe en 2e passe
            e.setFields(d.fields());
            maps.templateMap.put(d.id(), templateRepo.save(e).getId());
            if (d.defaultNodeId() != null) maps.templatesWithDefaultNode.add(d);
        }
        result.count("templates", maps.templateMap.size());

        // -- Page (relatedPageIds remappe en 2e passe)
        for (ContentExport.PageDto d : nullSafe(export.pages())) {
            PageJpaEntity e = new PageJpaEntity();
            e.setLoreId(IdRemapper.remapId(maps.loreMap, d.loreId()));
            e.setNodeId(IdRemapper.remapId(maps.loreNodeMap, d.nodeId()));
            e.setTemplateId(IdRemapper.remapId(maps.templateMap, d.templateId()));
            e.setTitle(d.title());
            e.setValues(d.values());
            e.setImageValues(IdRemapper.remapImageValues(maps.imageMap, d.imageValues()));
            e.setImageFraming(IdRemapper.remapImageFraming(maps.imageMap, d.imageFraming()));
            e.setKeyValueValues(d.keyValueValues());
            e.setTableValues(d.tableValues());
            e.setNotes(d.notes());
            e.setTags(d.tags());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe en 2e passe
            maps.pageMap.put(d.id(), pageRepo.save(e).getId());
        }
        result.count("pages", maps.pageMap.size());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
