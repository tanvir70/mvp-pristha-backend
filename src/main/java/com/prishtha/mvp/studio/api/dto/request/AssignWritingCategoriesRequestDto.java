package com.prishtha.mvp.studio.api.dto.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignWritingCategoriesRequestDto {
    private List<String> categoryNames;
}
