package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.infrastructure.persistence.entity.QuestProgressionJpaEntity;
import com.loremind.infrastructure.persistence.jpa.QuestProgressionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class PostgresQuestProgressionRepository implements QuestProgressionRepository {

    private final QuestProgressionJpaRepository jpa;

    public PostgresQuestProgressionRepository(QuestProgressionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<QuestProgression> findByPlaythroughId(String playthroughId) {
        return jpa.findByPlaythroughId(Long.parseLong(playthroughId)).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> findCompletedChapterIdsByPlaythroughId(String playthroughId) {
        Set<String> out = new HashSet<>();
        for (QuestProgressionJpaEntity e : jpa.findByPlaythroughId(Long.parseLong(playthroughId))) {
            if (e.getStatus() == ProgressionStatus.COMPLETED) {
                out.add(e.getChapterId().toString());
            }
        }
        return out;
    }

    @Override
    public void setStatus(String playthroughId, String chapterId, ProgressionStatus status) {
        Long pid = Long.parseLong(playthroughId);
        Long cid = Long.parseLong(chapterId);
        // Sémantique : NOT_STARTED = absence de ligne.
        if (status == null || status == ProgressionStatus.NOT_STARTED) {
            jpa.findByPlaythroughIdAndChapterId(pid, cid)
                    .ifPresent(e -> jpa.deleteById(e.getId()));
            return;
        }
        jpa.findByPlaythroughIdAndChapterId(pid, cid).ifPresentOrElse(
                existing -> {
                    existing.setStatus(status);
                    jpa.save(existing);
                },
                () -> jpa.save(QuestProgressionJpaEntity.builder()
                        .playthroughId(pid)
                        .chapterId(cid)
                        .status(status)
                        .build())
        );
    }

    @Override
    public void deleteAllByPlaythroughId(String playthroughId) {
        jpa.deleteByPlaythroughId(Long.parseLong(playthroughId));
    }

    private QuestProgression toDomain(QuestProgressionJpaEntity e) {
        return QuestProgression.builder()
                .id(e.getId().toString())
                .playthroughId(e.getPlaythroughId().toString())
                .chapterId(e.getChapterId().toString())
                .status(e.getStatus())
                .build();
    }
}
