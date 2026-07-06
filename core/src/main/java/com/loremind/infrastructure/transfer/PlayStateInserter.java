package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.EntryType;
import com.loremind.infrastructure.persistence.entity.CharacterJpaEntity;
import com.loremind.infrastructure.persistence.entity.ClockJpaEntity;
import com.loremind.infrastructure.persistence.entity.FrontJpaEntity;
import com.loremind.infrastructure.persistence.entity.PlaythroughFlagJpaEntity;
import com.loremind.infrastructure.persistence.entity.PlaythroughJpaEntity;
import com.loremind.infrastructure.persistence.entity.QuestProgressionJpaEntity;
import com.loremind.infrastructure.persistence.entity.SessionEntryJpaEntity;
import com.loremind.infrastructure.persistence.entity.SessionJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CharacterJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ClockJpaRepository;
import com.loremind.infrastructure.persistence.jpa.FrontJpaRepository;
import com.loremind.infrastructure.persistence.jpa.PlaythroughFlagJpaRepository;
import com.loremind.infrastructure.persistence.jpa.PlaythroughJpaRepository;
import com.loremind.infrastructure.persistence.jpa.QuestProgressionJpaRepository;
import com.loremind.infrastructure.persistence.jpa.SessionEntryJpaRepository;
import com.loremind.infrastructure.persistence.jpa.SessionJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1re passe d'import de l'état de jeu (cf. {@link ImportService}) : Playthrough,
 * Session, SessionEntry, PlaythroughFlag, Front, Clock, QuestProgression, Character.
 * Passe APRÈS {@link CampaignContentInserter} : les progressions référencent les
 * quêtes/chapitres importés, et les personnages la Partie importée.
 */
@Component
class PlayStateInserter {

    private final PlaythroughJpaRepository playthroughRepo;
    private final SessionJpaRepository sessionRepo;
    private final SessionEntryJpaRepository sessionEntryRepo;
    private final PlaythroughFlagJpaRepository playthroughFlagRepo;
    private final FrontJpaRepository frontRepo;
    private final ClockJpaRepository clockRepo;
    private final QuestProgressionJpaRepository questProgressionRepo;
    private final CharacterJpaRepository characterRepo;

    PlayStateInserter(PlaythroughJpaRepository playthroughRepo,
                      SessionJpaRepository sessionRepo,
                      SessionEntryJpaRepository sessionEntryRepo,
                      PlaythroughFlagJpaRepository playthroughFlagRepo,
                      FrontJpaRepository frontRepo,
                      ClockJpaRepository clockRepo,
                      QuestProgressionJpaRepository questProgressionRepo,
                      CharacterJpaRepository characterRepo) {
        this.playthroughRepo = playthroughRepo;
        this.sessionRepo = sessionRepo;
        this.sessionEntryRepo = sessionEntryRepo;
        this.playthroughFlagRepo = playthroughFlagRepo;
        this.frontRepo = frontRepo;
        this.clockRepo = clockRepo;
        this.questProgressionRepo = questProgressionRepo;
        this.characterRepo = characterRepo;
    }

    void insert(ContentExport export, ImportIdMaps maps, ImportResult.Builder result) {
        // -- Playthrough (Partie) : campaignId remappe
        for (ContentExport.PlaythroughDto d : nullSafe(export.playthroughs())) {
            PlaythroughJpaEntity e = new PlaythroughJpaEntity();
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setName(d.name());
            e.setDescription(d.description());
            maps.playthroughMap.put(d.id(), playthroughRepo.save(e).getId());
        }
        result.count("playthroughs", maps.playthroughMap.size());

        // -- Session : campaignId (ref faible String) + playthroughId remappes
        for (ContentExport.SessionDto d : nullSafe(export.sessions())) {
            SessionJpaEntity e = new SessionJpaEntity();
            e.setName(d.name());
            e.setCampaignId(IdRemapper.remapStringId(maps.campaignMap, d.campaignId()));
            e.setPlaythroughId(IdRemapper.remapId(maps.playthroughMap, d.playthroughId()));
            e.setStartedAt(parseDateTime(d.startedAt()));
            e.setEndedAt(parseDateTime(d.endedAt()));
            maps.sessionMap.put(d.id(), sessionRepo.save(e).getId());
        }
        result.count("sessions", maps.sessionMap.size());

        // -- SessionEntry : sessionId (ref faible String) remappe
        int sessionEntryCount = 0;
        for (ContentExport.SessionEntryDto d : nullSafe(export.sessionEntries())) {
            SessionEntryJpaEntity e = new SessionEntryJpaEntity();
            e.setSessionId(IdRemapper.remapStringId(maps.sessionMap, d.sessionId()));
            e.setType(parseEntryType(d.type()));
            e.setContent(d.content());
            e.setOccurredAt(parseDateTime(d.occurredAt()));
            sessionEntryRepo.save(e);
            sessionEntryCount++;
        }
        result.count("sessionEntries", sessionEntryCount);

        // -- PlaythroughFlag : playthroughId remappe (la contrainte unique (playthroughId,name)
        //    ne saute pas, le playthroughId etant neuf).
        int flagCount = 0;
        for (ContentExport.PlaythroughFlagDto d : nullSafe(export.playthroughFlags())) {
            PlaythroughFlagJpaEntity e = new PlaythroughFlagJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(maps.playthroughMap, d.playthroughId()));
            e.setName(d.name());
            e.setValue(d.value());
            playthroughFlagRepo.save(e);
            flagCount++;
        }
        result.count("playthroughFlags", flagCount);

        // -- Front (menaces regroupant des horloges) : playthroughId remappé.
        for (ContentExport.FrontDto d : nullSafe(export.fronts())) {
            FrontJpaEntity e = new FrontJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(maps.playthroughMap, d.playthroughId()));
            e.setName(d.name());
            e.setDescription(d.description());
            e.setOrder(d.order());
            maps.frontMap.put(d.id(), frontRepo.save(e).getId());
        }
        result.count("fronts", maps.frontMap.size());

        // -- Clock (horloges de Partie) : playthroughId + frontId remappés ; triggerRef quête -> 2e passe.
        for (ContentExport.ClockDto d : nullSafe(export.clocks())) {
            ClockJpaEntity e = new ClockJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(maps.playthroughMap, d.playthroughId()));
            e.setName(d.name());
            e.setDescription(d.description());
            e.setSegments(d.segments());
            e.setFilled(d.filled());
            e.setOrder(d.order());
            e.setTriggerType(d.triggerType() != null ? d.triggerType() : ClockTrigger.NONE);
            e.setTriggerRef(d.triggerRef());
            e.setFrontId(IdRemapper.remapId(maps.frontMap, d.frontId()));
            maps.clockMap.put(d.id(), clockRepo.save(e).getId());
        }
        result.count("clocks", maps.clockMap.size());

        // -- QuestProgression : playthroughId + (quest id) remappés (quêtes/chapitres déjà
        //    insérés ; contrainte unique (playthroughId, questId) préservée car playthroughId neuf).
        boolean bundleHasQuests = export.quests() != null;
        int questProgCount = 0;
        for (ContentExport.QuestProgressionDto d : nullSafe(export.questProgressions())) {
            QuestProgressionJpaEntity e = new QuestProgressionJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(maps.playthroughMap, d.playthroughId()));
            Long oldRef = d.chapterId();
            // v2 : oldRef est un quest id -> questMap. v1 : oldRef est un chapter id ; s'il a été
            //   converti en quête, questMap (clé chapter id) le résout, sinon fallback chapterMap.
            Long newQuestId;
            if (bundleHasQuests) {
                newQuestId = IdRemapper.remapId(maps.questMap, oldRef);
            } else if (oldRef != null && maps.questMap.containsKey(oldRef)) {
                newQuestId = maps.questMap.get(oldRef);
            } else {
                newQuestId = IdRemapper.remapId(maps.chapterMap, oldRef);
            }
            e.setQuestId(newQuestId);
            e.setStatus(parseProgressionStatus(d.status()));
            questProgressionRepo.save(e);
            questProgCount++;
        }
        result.count("questProgressions", questProgCount);

        // -- Character : inséré ici (et non avec le contenu de campagne) car il référence la
        //    Partie importée via playthroughId.
        for (ContentExport.CharacterDto d : nullSafe(export.characters())) {
            CharacterJpaEntity e = new CharacterJpaEntity();
            e.setName(d.name());
            e.setPortraitImageId(IdRemapper.remapStringId(maps.imageMap, d.portraitImageId()));
            e.setHeaderImageId(IdRemapper.remapStringId(maps.imageMap, d.headerImageId()));
            e.setValues(d.values());
            e.setImageValues(IdRemapper.remapImageValues(maps.imageMap, d.imageValues()));
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            // playthroughId remappe vers la Partie importee (ou null si le jeu n'etait pas
            // dans l'export -> la map est vide). Evite une reference pendante.
            e.setPlaythroughId(maps.playthroughMap.get(d.playthroughId()));
            e.setOrder(d.order());
            maps.characterMap.put(d.id(), characterRepo.save(e).getId());
        }
        result.count("characters", maps.characterMap.size());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    /** Parse un horodatage ISO LocalDateTime, ou null si absent/illisible. */
    private static java.time.LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDateTime.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** Parse un EntryType, repli sur NOTE si inconnu/absent (jamais d'echec). */
    private static EntryType parseEntryType(String s) {
        if (s == null) return EntryType.NOTE;
        try {
            return EntryType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return EntryType.NOTE;
        }
    }

    /** Parse un ProgressionStatus, repli sur NOT_STARTED si inconnu/absent. */
    private static ProgressionStatus parseProgressionStatus(String s) {
        if (s == null) return ProgressionStatus.NOT_STARTED;
        try {
            return ProgressionStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ProgressionStatus.NOT_STARTED;
        }
    }
}
