package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.api.contract.IdentityService;
import com.prishtha.mvp.identity.api.dto.request.UserSignUpRequestDto;
import com.prishtha.mvp.identity.api.dto.response.UserBasicInfoResponseDto;
import com.prishtha.mvp.identity.internal.entity.User;
import com.prishtha.mvp.identity.internal.enums.UserStatus;
import com.prishtha.mvp.identity.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        return mapToResponseDto(savedUser);
    }

    @Override
    public UserBasicInfoResponseDto verifyOtp(String phone, String code) {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"123456".equals(code)) {
            throw new IllegalArgumentException("Invalid OTP code");
        }

        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        return mapToResponseDto(savedUser);
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
