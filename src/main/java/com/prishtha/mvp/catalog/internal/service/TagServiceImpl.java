package com.prishtha.mvp.catalog.internal.service;

import com.prishtha.mvp.catalog.api.contract.TagService;
import com.prishtha.mvp.catalog.api.dto.response.TagResponseDto;
import com.prishtha.mvp.catalog.internal.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    @Override
    public List<TagResponseDto> getAllTags() {
        return tagRepository.findAll().stream()
                .map(tag -> TagResponseDto.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .build())
                .toList();
    }
}
