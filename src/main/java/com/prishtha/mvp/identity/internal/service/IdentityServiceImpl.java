package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.enums.OtpPurpose;
import com.prishtha.mvp.identity.internal.enums.UserStatus;
import com.prishtha.mvp.identity.internal.repository.AuthorProfileRepository;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import com.prishtha.mvp.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final AuthorProfileRepository authorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    @Override
    public UserBasicInfoResponseDto signUp(UserSignUpRequestDto requestDto) {
        if (userRepository.findByPhone(requestDto.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        User user = new User();
        user.setPhone(requestDto.getPhone());
        user.setFullName(requestDto.getFullName());
        user.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        User savedUser = userRepository.save(user);
        otpService.sendOtp(savedUser.getPhone(), OtpPurpose.SIGNUP_VERIFICATION);
        return mapToResponseDto(savedUser);
    }

    @Override
    public UserBasicInfoResponseDto verifyOtp(String phone, String code) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!otpService.verifyOtp(phone, OtpPurpose.SIGNUP_VERIFICATION, code)) {
            throw new IllegalArgumentException("Invalid OTP code");
        }

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        return mapToResponseDto(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserActive(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAuthor(Long userId) {
        return authorProfileRepository.existsByUser_Id(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserBasicInfoResponseDto getUserBasicInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return mapToResponseDto(user);
    }

    private UserBasicInfoResponseDto mapToResponseDto(User user) {
        return UserBasicInfoResponseDto.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus().name())
                .build();
    }
}
