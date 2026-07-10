package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.identity.api.dto.request.AuthorProfileUpdateRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
import com.prishtha.mvp.identity.api.dto.response.PublicAuthorProfileResponseDto;
import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthorProfileServiceImpl implements AuthorProfileService {

    private final AuthorProfileRepository authorProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthorProfileResponseDto getMyAuthorProfile(Long requesterUserId) {
        ensureAuthor(requesterUserId);

        AuthorProfile authorProfile = authorProfileRepository.findByUser_Id(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));
        return toAuthorProfileDto(authorProfile);
    }

    @Override
    public AuthorProfileResponseDto updateMyAuthorProfile(
            Long requesterUserId, AuthorProfileUpdateRequestDto requestDto) {
        ensureAuthor(requesterUserId);

        AuthorProfile authorProfile = authorProfileRepository.findByUser_Id(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));

        if (requestDto.getPenName() != null && !requestDto.getPenName().isBlank()) {
            authorProfileRepository.findByPenName(requestDto.getPenName().trim())
                    .filter(existing -> !existing.getId().equals(authorProfile.getId()))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Pen name already in use");
                    });
            authorProfile.setPenName(requestDto.getPenName().trim());
        } else if (requestDto.getPenName() != null) {
            authorProfile.setPenName(null);
        }

        if (requestDto.getBiography() != null) {
            authorProfile.setBiography(requestDto.getBiography());
        }
        if (requestDto.getPayoutMfsNumber() != null) {
            authorProfile.setPayoutMfsNumber(requestDto.getPayoutMfsNumber());
        }
        if (requestDto.getPayoutMfsProvider() != null) {
            authorProfile.setPayoutMfsProvider(requestDto.getPayoutMfsProvider());
        }

        return toAuthorProfileDto(authorProfileRepository.save(authorProfile));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicAuthorProfileResponseDto getPublicAuthorProfile(Long authorProfileId) {
        AuthorProfile authorProfile = authorProfileRepository.findById(authorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));

        if (!authorProfile.isActive()) {
            throw new IllegalArgumentException("Author profile is inactive");
        }

        String displayName = authorProfile.getPenName();
        if (displayName == null || displayName.isBlank()) {
            displayName = authorProfile.getUser().getFullName();
        }

        return PublicAuthorProfileResponseDto.builder()
                .id(authorProfile.getId())
                .userId(authorProfile.getUser().getId())
                .displayName(displayName)
                .biography(authorProfile.getBiography())
                .avatarUrl(authorProfile.getUser().getAvatarUrl())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureAuthorIsActive(Long authorProfileId) {
        AuthorProfile authorProfile = authorProfileRepository.findById(authorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));
        if (!authorProfile.isActive()) {
            throw new IllegalArgumentException("Author account is deactivated");
        }
    }

    private void ensureAuthor(Long userId) {
        if (!authorProfileRepository.existsByUser_Id(userId)) {
            throw new IllegalArgumentException("Only authors can perform this action");
        }
    }

    private AuthorProfileResponseDto toAuthorProfileDto(AuthorProfile authorProfile) {
        return AuthorProfileResponseDto.builder()
                .id(authorProfile.getId())
                .userId(authorProfile.getUser().getId())
                .penName(authorProfile.getPenName())
                .biography(authorProfile.getBiography())
                .payoutMfsNumber(authorProfile.getPayoutMfsNumber())
                .payoutMfsProvider(authorProfile.getPayoutMfsProvider())
                .active(authorProfile.isActive())
                .build();
    }
}
