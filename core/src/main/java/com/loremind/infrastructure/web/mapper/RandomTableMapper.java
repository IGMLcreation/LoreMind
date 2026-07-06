package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.infrastructure.web.dto.campaigncontext.RandomTableDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.RandomTableEntryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RandomTableMapper {

    public RandomTableDTO toDTO(RandomTable t) {
        if (t == null) return null;
        RandomTableDTO dto = new RandomTableDTO();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setDiceFormula(t.getDiceFormula());
        dto.setIcon(t.getIcon());
        dto.setCampaignId(t.getCampaignId());
        dto.setOrder(t.getOrder());
        dto.setEntries(t.getEntries().stream().map(this::toEntryDTO).collect(Collectors.toList()));
        return dto;
    }

    public List<RandomTableEntry> toDomainEntries(List<RandomTableEntryDTO> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(this::toDomainEntry).collect(Collectors.toList());
    }

    private RandomTableEntryDTO toEntryDTO(RandomTableEntry e) {
        RandomTableEntryDTO dto = new RandomTableEntryDTO();
        dto.setMinRoll(e.getMinRoll());
        dto.setMaxRoll(e.getMaxRoll());
        dto.setLabel(e.getLabel());
        dto.setDetail(e.getDetail());
        return dto;
    }

    private RandomTableEntry toDomainEntry(RandomTableEntryDTO dto) {
        return RandomTableEntry.builder()
                .minRoll(dto.getMinRoll())
                .maxRoll(dto.getMaxRoll())
                .label(dto.getLabel())
                .detail(dto.getDetail())
                .build();
    }
}
