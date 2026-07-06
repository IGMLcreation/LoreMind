package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.shared.ReorderSupport;
import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.RandomTableGenerator;
import com.loremind.domain.campaigncontext.ports.RandomTableRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service d'application pour les tables aléatoires (campagne).
 */
@Service
public class RandomTableService {

    private final RandomTableRepository repository;
    private final RandomTableGenerator generator;
    private final CampaignContextFormatter campaignContextFormatter;

    public RandomTableService(
            RandomTableRepository repository,
            RandomTableGenerator generator,
            CampaignContextFormatter campaignContextFormatter) {
        this.repository = repository;
        this.generator = generator;
        this.campaignContextFormatter = campaignContextFormatter;
    }

    public record TableData(
            String name,
            String description,
            String diceFormula,
            String icon,
            List<RandomTableEntry> entries,
            String campaignId,
            Integer order
    ) {}

    public RandomTable createTable(TableData data) {
        int order = data.order() != null ? data.order() : nextOrderFor(data.campaignId());
        RandomTable table = RandomTable.builder()
                .name(data.name())
                .description(data.description())
                .diceFormula(data.diceFormula())
                .icon(data.icon())
                .entries(copyEntries(data.entries()))
                .campaignId(data.campaignId())
                .order(order)
                .build();
        return repository.save(table);
    }

    public Optional<RandomTable> getTableById(String id) {
        return repository.findById(id);
    }

    public List<RandomTable> getTablesByCampaignId(String campaignId) {
        return repository.findByCampaignId(campaignId);
    }

    public RandomTable updateTable(String id, TableData data) {
        RandomTable existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Table aléatoire introuvable: " + id));
        existing.setName(data.name());
        existing.setDescription(data.description());
        existing.setDiceFormula(data.diceFormula());
        existing.setIcon(data.icon());
        existing.setEntries(copyEntries(data.entries()));
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return repository.save(existing);
    }

    public void deleteTable(String id) {
        repository.deleteById(id);
    }

    /** Réordonne les tables aléatoires d'une campagne : {@code order} = position. */
    @org.springframework.transaction.annotation.Transactional
    public void reorderTables(List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                repository::findById,
                RandomTable::setOrder,
                repository::save);
    }

    public List<RandomTable> searchTables(String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.searchByName(query.trim());
    }

    /** Génère une PROPOSITION de table (non persistée) via l'IA, contextualisée campagne. */
    public RandomTable generateProposal(String campaignId, String description, String diceFormula) {
        String formula = (diceFormula == null || diceFormula.isBlank()) ? "1d20" : diceFormula;
        RandomTableGenerator.GeneratedTable g = generator.generate(
                description, formula, campaignContextFormatter.format(campaignId));
        return RandomTable.builder()
                .name(g.name())
                .description(g.description())
                .diceFormula(formula)
                .campaignId(campaignId)
                .entries(g.entries() != null ? new ArrayList<>(g.entries()) : new ArrayList<>())
                .build();
    }

    /** Brode un court récit IA sur un résultat tiré (pour la partie). */
    public String improviseRoll(String campaignId, String tableName, String resultLabel, String resultDetail) {
        return generator.improvise(tableName, resultLabel, resultDetail, campaignContextFormatter.format(campaignId));
    }

    private static List<RandomTableEntry> copyEntries(List<RandomTableEntry> entries) {
        return entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    private int nextOrderFor(String campaignId) {
        return repository.findByCampaignId(campaignId).stream()
                .mapToInt(RandomTable::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
