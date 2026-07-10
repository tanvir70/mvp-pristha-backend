package com.prishtha.mvp.catalog.internal.listener;

import com.prishtha.mvp.catalog.internal.entity.PublishedWriting;
import com.prishtha.mvp.catalog.internal.entity.PublishedWritingTag;
import com.prishtha.mvp.catalog.internal.entity.PublishedWritingTagId;
import com.prishtha.mvp.catalog.internal.repository.PublishedWritingRepository;
import com.prishtha.mvp.catalog.internal.repository.PublishedWritingTagRepository;
import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.studio.api.event.WritingPublishedEvent;
import com.prishtha.mvp.studio.api.event.WritingUnpublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class WritingPublicationEventListener {

    private final PublishedWritingRepository publishedWritingRepository;
    private final PublishedWritingTagRepository publishedWritingTagRepository;
    private final AuthorProfileService authorProfileService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onWritingPublished(WritingPublishedEvent event) {
        String authorPenName = authorProfileService.getPublicAuthorProfile(event.authorId()).getDisplayName();

        PublishedWriting writing = publishedWritingRepository.findById(event.writingId())
                .orElseGet(PublishedWriting::new);
        writing.setId(event.writingId());
        writing.setTenantId(event.tenantId());
        writing.setAuthorId(event.authorId());
        writing.setAuthorPenName(authorPenName);
        writing.setParentId(event.parentId());
        writing.setTitle(event.title());
        writing.setSlug(event.slug());
        writing.setSynopsis(event.synopsis());
        writing.setCoverImageUrl(event.coverImageUrl());
        writing.setPreviewJson(event.previewJson());
        writing.setType(event.type());
        writing.setStatus(event.status());
        writing.setPriceType(event.priceType());
        writing.setPriceAmount(event.priceAmount());
        writing.setPublishedAt(event.publishedAt());

        PublishedWriting saved = publishedWritingRepository.save(writing);

        publishedWritingTagRepository.deleteByIdWritingId(saved.getId());
        for (String tag : event.categoryNames()) {
            PublishedWritingTag publishedWritingTag = new PublishedWritingTag();
            publishedWritingTag.setId(new PublishedWritingTagId(saved.getId(), tag));
            publishedWritingTag.setWriting(saved);
            publishedWritingTagRepository.save(publishedWritingTag);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onWritingUnpublished(WritingUnpublishedEvent event) {
        publishedWritingTagRepository.deleteByIdWritingId(event.writingId());
        if (publishedWritingRepository.existsById(event.writingId())) {
            publishedWritingRepository.deleteById(event.writingId());
        }
    }
}
