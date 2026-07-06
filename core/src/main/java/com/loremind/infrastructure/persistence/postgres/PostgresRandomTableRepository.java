package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.RandomTableRepository;
import com.loremind.infrastructure.persistence.entity.RandomTableEntryJpaEntity;
import com.loremind.infrastructure.persistence.entity.RandomTableJpaEntity;
import com.loremind.infrastructure.persistence.jpa.RandomTableJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresRandomTableRepository implements RandomTableRepository {

    private final RandomTableJpaRepository jpaRepository;

    public PostgresRandomTableRepository(RandomTableJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public RandomTable save(RandomTable table) {
        // Création OU mise à jour : on charge l'entité gérée si elle existe afin que
        // le remplacement des entrées (clear + add) déclenche bien orphanRemoval.
        RandomTableJpaEntity entity = (table.getId() != null)
                ? jpaRepository.findById(Long.parseLong(table.getId())).orElseGet(RandomTableJpaEntity::new)
                : new RandomTableJpaEntity();

        entity.setName(table.getName());
        entity.setDescription(table.getDescription());
        entity.setDiceFormula(table.getDiceFormula());
        entity.setIcon(table.getIcon());
        entity.setCampaignId(Long.parseLong(table.getCampaignId()));
        entity.setOrder(table.getOrder());

        // Remplacement en bloc des entrées (les anciennes sont supprimées via orphanRemoval).
        entity.getEntries().clear();
        int position = 0;
        for (RandomTableEntry e : table.getEntries()) {
            entity.getEntries().add(RandomTableEntryJpaEntity.builder()
                    .minRoll(e.getMinRoll())
                    .maxRoll(e.getMaxRoll())
                    .label(e.getLabel())
                    .detail(e.getDetail())
                    .position(position++)
                    .randomTable(entity)
                    .build());
        }

        RandomTableJpaEntity saved = jpaRepository.save(entity);
        return toDomainEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RandomTable> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RandomTable> findByCampaignId(String campaignId) {
        return jpaRepository.findByCampaignIdOrderByOrderAsc(Long.parseLong(campaignId)).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(Long.parseLong(id));
    }

    @Override
    public boolean existsById(String id) {
        return jpaRepository.existsById(Long.parseLong(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RandomTable> searchByName(String query) {
        return jpaRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(query).stream()
                .map(this::toDomainEntity)
                .toList();
    }

    private RandomTable toDomainEntity(RandomTableJpaEntity e) {
        List<RandomTableEntry> entries = e.getEntries().stream()
                .map(c -> RandomTableEntry.builder()
                        .minRoll(c.getMinRoll())
                        .maxRoll(c.getMaxRoll())
                        .label(c.getLabel())
                        .detail(c.getDetail())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        return RandomTable.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .description(e.getDescription())
                .diceFormula(e.getDiceFormula())
                .icon(e.getIcon())
                .campaignId(e.getCampaignId().toString())
                .order(e.getOrder())
                .entries(entries)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
