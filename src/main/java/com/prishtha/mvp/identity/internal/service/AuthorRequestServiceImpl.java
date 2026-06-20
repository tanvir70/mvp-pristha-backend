package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.AuthorRequestService;
import com.prishtha.mvp.identity.api.dto.request.AuthorRequestRejectRequestDto;
import com.prishtha.mvp.identity.api.dto.request.AuthorRequestSubmitRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorRequestResponseDto;
import com.prishtha.mvp.identity.internal.entity.AuthorProfile;
import com.prishtha.mvp.identity.internal.entity.AuthorRequest;
import com.prishtha.mvp.identity.internal.entity.AuthorRequestStatus;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.entity.UserRole;
import com.prishtha.mvp.identity.internal.entity.UserStatus;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import com.prishtha.mvp.identity.internal.repository.AuthorRequestRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class AuthorRequestServiceImpl implements AuthorRequestService {

    private final AuthorRequestRepository authorRequestRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final UserRepository userRepository;

    @Override
    public AuthorRequestResponseDto submitRequest(Long requesterUserId, AuthorRequestSubmitRequestDto requestDto) {
        User requester = userRepository.findById(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("Requester user not found"));

        if (requester.getStatus() != UserStatus.VERIFIED) {
            throw new IllegalArgumentException("Only verified users can request author role");
        }

        if (requester.getRole() == UserRole.AUTHOR) {
            throw new IllegalArgumentException("User is already an author");
        }

        if (authorRequestRepository.existsByUser_IdAndStatus(requesterUserId, AuthorRequestStatus.PENDING)) {
            throw new IllegalArgumentException("A pending author request already exists");
        }

        AuthorRequest request = new AuthorRequest();
        request.setUser(requester);
        request.setRequestedPenName(requestDto.getRequestedPenName());
        request.setMotivation(requestDto.getMotivation());
        request.setSampleWritingUrl(requestDto.getSampleWritingUrl());
        request.setStatus(AuthorRequestStatus.PENDING);

        return toDto(authorRequestRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorRequestResponseDto getMyLatestRequest(Long requesterUserId) {
        AuthorRequest request = authorRequestRepository.findFirstByUser_IdOrderByCreatedAtDesc(requesterUserId)
                .orElseThrow(() -> new IllegalArgumentException("No author request found for user"));
        return toDto(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthorRequestResponseDto> getRequestsByStatus(String status) {
        AuthorRequestStatus mappedStatus = AuthorRequestStatus.valueOf(status.toUpperCase());
        return authorRequestRepository.findByStatusOrderByCreatedAtAsc(mappedStatus).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AuthorRequestResponseDto approveRequest(Long requestId, Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        ensureAdmin(admin);

        AuthorRequest request = authorRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Author request not found"));

        if (request.getStatus() != AuthorRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be approved");
        }

        User requester = request.getUser();
        if (authorProfileRepository.existsByUser_Id(requester.getId())) {
            throw new IllegalArgumentException("Author profile already exists for this user");
        }
        requester.setRole(UserRole.AUTHOR);

        AuthorProfile profile = new AuthorProfile();
        profile.setUser(requester);
        profile.setPenName(request.getRequestedPenName());
        authorProfileRepository.save(profile);

        request.setStatus(AuthorRequestStatus.APPROVED);
        request.setReviewedBy(admin);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(null);

        userRepository.save(requester);
        return toDto(authorRequestRepository.save(request));
    }

    @Override
    public AuthorRequestResponseDto rejectRequest(
            Long requestId, Long adminUserId, AuthorRequestRejectRequestDto requestDto) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        ensureAdmin(admin);

        AuthorRequest request = authorRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Author request not found"));

        if (request.getStatus() != AuthorRequestStatus.PENDING) {
            throw new IllegalArgumentException("Only pending requests can be rejected");
        }

        request.setStatus(AuthorRequestStatus.REJECTED);
        request.setReviewedBy(admin);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(requestDto == null ? null : requestDto.getReviewNote());

        return toDto(authorRequestRepository.save(request));
    }

    private void ensureAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("Only admins can perform this action");
        }
    }

    private AuthorRequestResponseDto toDto(AuthorRequest request) {
        return AuthorRequestResponseDto.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .requestedPenName(request.getRequestedPenName())
                .motivation(request.getMotivation())
                .sampleWritingUrl(request.getSampleWritingUrl())
                .status(request.getStatus().name())
                .reviewedBy(request.getReviewedBy() == null ? null : request.getReviewedBy().getId())
                .reviewNote(request.getReviewNote())
                .reviewedAt(request.getReviewedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
