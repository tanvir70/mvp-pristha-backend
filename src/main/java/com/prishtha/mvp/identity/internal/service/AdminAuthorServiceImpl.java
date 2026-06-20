package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AdminAuthorService;
import com.prishtha.mvp.identity.api.dto.response.AuthorProfileResponseDto;
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
class AdminAuthorServiceImpl implements AdminAuthorService {

    private final AuthorProfileRepository authorProfileRepository;
    private final UserRepository userRepository;

    @Override
    public AuthorProfileResponseDto deactivateAuthor(Long authorProfileId, Long adminUserId) {
        ensureAdmin(adminUserId);
        AuthorProfile authorProfile = findAuthorProfile(authorProfileId);
        authorProfile.setActive(false);
        return toDto(authorProfileRepository.save(authorProfile));
    }

    @Override
    public AuthorProfileResponseDto activateAuthor(Long authorProfileId, Long adminUserId) {
        ensureAdmin(adminUserId);
        AuthorProfile authorProfile = findAuthorProfile(authorProfileId);
        authorProfile.setActive(true);
        return toDto(authorProfileRepository.save(authorProfile));
    }

    private AuthorProfile findAuthorProfile(Long authorProfileId) {
        return authorProfileRepository.findById(authorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Author profile not found"));
    }

    private void ensureAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Only admins can perform this action");
        }
    }

    private AuthorProfileResponseDto toDto(AuthorProfile authorProfile) {
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
