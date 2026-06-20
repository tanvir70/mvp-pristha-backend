package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthorProfileService;
import com.prishtha.mvp.identity.api.dto.request.AuthorProfileUpdateRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
import com.prishtha.mvp.identity.api.dto.response.PublicAuthorProfileResponseDto;
import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.entity.UserRole;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthorProfileServiceImpl implements AuthorProfileService {

    private final AuthorProfileRepository authorProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AuthorProfileResponseDto getMyAuthorProfile(Long requesterUserId) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        ensureAuthor(requester);

        AuthorProfile authorProfile = authorProfileRepository.findByUser_Id(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));
        return toAuthorProfileDto(authorProfile);
    }

    @Override
    public AuthorProfileResponseDto updateMyAuthorProfile(
            Long requesterUserId, AuthorProfileUpdateRequestDto requestDto) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));
        ensureAuthor(requester);

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
        if (requestDto.getPayoutPhone() != null) {
            authorProfile.setPayoutPhone(requestDto.getPayoutPhone());
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

    private void ensureAuthor(User user) {
        if (user.getRole() != UserRole.AUTHOR) {
            throw new IllegalArgumentException("Only authors can perform this action");
        }
    }

    private AuthorProfileResponseDto toAuthorProfileDto(AuthorProfile authorProfile) {
        return AuthorProfileResponseDto.builder()
                .id(authorProfile.getId())
                .userId(authorProfile.getUser().getId())
                .penName(authorProfile.getPenName())
                .biography(authorProfile.getBiography())
                .payoutPhone(authorProfile.getPayoutPhone())
                .active(authorProfile.isActive())
                .build();
    }
}
