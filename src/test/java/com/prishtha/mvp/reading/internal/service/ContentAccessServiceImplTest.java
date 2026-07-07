package com.prishtha.mvp.reading.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prishtha.mvp.reading.api.contract.AccessLevel;
import com.prishtha.mvp.reading.api.dto.response.ContentAccessResponseDto;
import com.prishtha.mvp.reading.internal.repository.ContentAccessRepository;
import com.prishtha.mvp.studio.api.contract.WritingContentService;
import com.prishtha.mvp.studio.api.dto.response.WritingContentResponseDto;
import org.junit.jupiter.api.Test;

class ContentAccessServiceImplTest {

    private final WritingContentService writingContentService = mock(WritingContentService.class);
    private final ContentAccessRepository contentAccessRepository = mock(ContentAccessRepository.class);
    private final ContentAccessServiceImpl service =
            new ContentAccessServiceImpl(writingContentService, contentAccessRepository);

    @Test
    void freePostIsFullAccessEvenForGuest() {
        when(writingContentService.getPublishedContentBySlug("free-post"))
                .thenReturn(content(1L, "FREE", "{\"body\":\"full\"}", null));

        ContentAccessResponseDto result = service.getContent("free-post", null);

        assertThat(result.getAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(result.getBody()).isEqualTo("{\"body\":\"full\"}");
    }

    @Test
    void lockedPostWithoutUnlockIsPreviewOnly() {
        when(writingContentService.getPublishedContentBySlug("locked-post"))
                .thenReturn(content(2L, "LOCKED", "{\"body\":\"full\"}", "{\"body\":\"teaser\"}"));
        when(contentAccessRepository.existsByReaderIdAndWritingId(10L, 2L)).thenReturn(false);

        ContentAccessResponseDto result = service.getContent("locked-post", 10L);

        assertThat(result.getAccessLevel()).isEqualTo(AccessLevel.PREVIEW);
        assertThat(result.getBody()).isEqualTo("{\"body\":\"teaser\"}");
    }

    @Test
    void lockedPostWithUnlockIsFullAccess() {
        when(writingContentService.getPublishedContentBySlug("locked-post"))
                .thenReturn(content(2L, "LOCKED", "{\"body\":\"full\"}", "{\"body\":\"teaser\"}"));
        when(contentAccessRepository.existsByReaderIdAndWritingId(10L, 2L)).thenReturn(true);

        ContentAccessResponseDto result = service.getContent("locked-post", 10L);

        assertThat(result.getAccessLevel()).isEqualTo(AccessLevel.FULL);
        assertThat(result.getBody()).isEqualTo("{\"body\":\"full\"}");
    }

    @Test
    void lockedPostWithoutExplicitPreviewFallsBackToTruncatedBody() {
        String longBody = "x".repeat(600);
        when(writingContentService.getPublishedContentBySlug("locked-post"))
                .thenReturn(content(2L, "LOCKED", longBody, null));
        when(contentAccessRepository.existsByReaderIdAndWritingId(null, 2L)).thenReturn(false);

        ContentAccessResponseDto result = service.getContent("locked-post", null);

        assertThat(result.getAccessLevel()).isEqualTo(AccessLevel.PREVIEW);
        assertThat(result.getBody()).hasSize(500);
    }

    private WritingContentResponseDto content(Long id, String priceType, String bodyJson, String previewJson) {
        return WritingContentResponseDto.builder()
                .writingId(id)
                .slug("slug")
                .priceType(priceType)
                .bodyJson(bodyJson)
                .previewJson(previewJson)
                .build();
    }
}
