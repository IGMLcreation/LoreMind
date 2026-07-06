package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.quest.ProgressionStatus;
import com.loremind.domain.playcontext.QuestProgression;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.infrastructure.persistence.entity.QuestProgressionJpaEntity;
import com.loremind.infrastructure.persistence.jpa.QuestProgressionJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                .toList();
    }

    @Override
    public Set<String> findCompletedQuestIdsByPlaythroughId(String playthroughId) {
        Set<String> out = new HashSet<>();
        for (QuestProgressionJpaEntity e : jpa.findByPlaythroughId(Long.parseLong(playthroughId))) {
            if (e.getStatus() == ProgressionStatus.COMPLETED) {
                out.add(e.getQuestId().toString());
            }
        }
        return out;
    }

    @Override
    public void setStatus(String playthroughId, String questId, ProgressionStatus status) {
        Long pid = Long.parseLong(playthroughId);
        Long qid = Long.parseLong(questId);
        // Sémantique : NOT_STARTED = absence de ligne.
        if (status == null || status == ProgressionStatus.NOT_STARTED) {
            jpa.findByPlaythroughIdAndQuestId(pid, qid)
                    .ifPresent(e -> jpa.deleteById(e.getId()));
            return;
        }
        jpa.findByPlaythroughIdAndQuestId(pid, qid).ifPresentOrElse(
                existing -> {
                    existing.setStatus(status);
                    jpa.save(existing);
                },
                () -> jpa.save(QuestProgressionJpaEntity.builder()
                        .playthroughId(pid)
                        .questId(qid)
                        .status(status)
                        .build())
        );
    }

    @Override
    public void deleteAllByPlaythroughId(String playthroughId) {
        jpa.deleteByPlaythroughId(Long.parseLong(playthroughId));
    }

    @Override
    public void deleteByQuestId(String questId) {
        jpa.deleteByQuestId(Long.parseLong(questId));
    }

    private QuestProgression toDomain(QuestProgressionJpaEntity e) {
        return QuestProgression.builder()
                .id(e.getId().toString())
                .playthroughId(e.getPlaythroughId().toString())
                .questId(e.getQuestId().toString())
                .status(e.getStatus())
                .build();
    }
}
