package com.prishtha.mvp.identity.api.dto.request;

import com.prishtha.mvp.identity.internal.enums.MfsProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorProfileUpdateRequestDto {
    private String penName;
    private String biography;
    private String payoutMfsNumber;
    private MfsProvider payoutMfsProvider;
}
