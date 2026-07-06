package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.itemcatalog.CatalogItem;
import com.loremind.domain.campaigncontext.itemcatalog.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.ItemCatalogRepository;
import com.loremind.infrastructure.persistence.entity.CatalogItemJpaEntity;
import com.loremind.infrastructure.persistence.entity.ItemCatalogJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ItemCatalogJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresItemCatalogRepository implements ItemCatalogRepository {

    private final ItemCatalogJpaRepository jpaRepository;

    public PostgresItemCatalogRepository(ItemCatalogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public ItemCatalog save(ItemCatalog catalog) {
        ItemCatalogJpaEntity entity = (catalog.getId() != null)
                ? jpaRepository.findById(Long.parseLong(catalog.getId())).orElseGet(ItemCatalogJpaEntity::new)
                : new ItemCatalogJpaEntity();

        entity.setName(catalog.getName());
        entity.setDescription(catalog.getDescription());
        entity.setIcon(catalog.getIcon());
        entity.setCampaignId(Long.parseLong(catalog.getCampaignId()));
        entity.setOrder(catalog.getOrder());

        // Remplacement en bloc des objets (orphanRemoval supprime les anciens).
        entity.getItems().clear();
        int position = 0;
        for (CatalogItem it : catalog.getItems()) {
            entity.getItems().add(CatalogItemJpaEntity.builder()
                    .name(it.getName())
                    .price(it.getPrice())
                    .category(it.getCategory())
                    .description(it.getDescription())
                    .position(position++)
                    .catalog(entity)
                    .build());
        }

        return toDomainEntity(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemCatalog> findById(String id) {
        return jpaRepository.findById(Long.parseLong(id)).map(this::toDomainEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemCatalog> findByCampaignId(String campaignId) {
        return jpaRepository.findByCampaignIdOrderByOrderAsc(Long.parseLong(campaignId)).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
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
    public List<ItemCatalog> searchByName(String query) {
        return jpaRepository.findTop20ByNameContainingIgnoreCaseOrderByNameAsc(query).stream()
                .map(this::toDomainEntity)
                .collect(Collectors.toList());
    }

    private ItemCatalog toDomainEntity(ItemCatalogJpaEntity e) {
        List<CatalogItem> items = e.getItems().stream()
                .map(c -> CatalogItem.builder()
                        .name(c.getName())
                        .price(c.getPrice())
                        .category(c.getCategory())
                        .description(c.getDescription())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
        return ItemCatalog.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .description(e.getDescription())
                .icon(e.getIcon())
                .campaignId(e.getCampaignId().toString())
                .order(e.getOrder())
                .items(items)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
