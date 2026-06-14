package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.CatalogItem;
import com.loremind.domain.campaigncontext.ItemCatalog;
import com.loremind.infrastructure.web.dto.campaigncontext.CatalogItemDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.ItemCatalogDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ItemCatalogMapper {

    public ItemCatalogDTO toDTO(ItemCatalog c) {
        if (c == null) return null;
        ItemCatalogDTO dto = new ItemCatalogDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setDescription(c.getDescription());
        dto.setIcon(c.getIcon());
        dto.setCampaignId(c.getCampaignId());
        dto.setOrder(c.getOrder());
        dto.setItems(c.getItems().stream().map(this::toItemDTO).collect(Collectors.toList()));
        return dto;
    }

    public List<CatalogItem> toDomainItems(List<CatalogItemDTO> dtos) {
        if (dtos == null) return List.of();
        return dtos.stream().map(this::toDomainItem).collect(Collectors.toList());
    }

    private CatalogItemDTO toItemDTO(CatalogItem i) {
        CatalogItemDTO dto = new CatalogItemDTO();
        dto.setName(i.getName());
        dto.setPrice(i.getPrice());
        dto.setCategory(i.getCategory());
        dto.setDescription(i.getDescription());
        return dto;
    }

    private CatalogItem toDomainItem(CatalogItemDTO dto) {
        return CatalogItem.builder()
                .name(dto.getName())
                .price(dto.getPrice())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .build();
    }
}
