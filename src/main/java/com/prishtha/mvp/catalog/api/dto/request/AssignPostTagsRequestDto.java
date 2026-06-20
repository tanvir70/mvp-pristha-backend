package com.prishtha.mvp.catalog.api.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignPostTagsRequestDto {
    private List<String> tagSlugs;
}
