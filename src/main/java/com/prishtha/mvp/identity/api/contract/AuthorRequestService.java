package com.prishtha.mvp.identity.api.contract;

import com.prishtha.mvp.identity.api.dto.request.AuthorRequestRejectRequestDto;
import com.prishtha.mvp.identity.api.dto.request.AuthorRequestSubmitRequestDto;
import com.prishtha.mvp.identity.api.dto.response.AuthorRequestResponseDto;
import java.util.List;

public interface AuthorRequestService {

    AuthorRequestResponseDto submitRequest(Long requesterUserId, AuthorRequestSubmitRequestDto requestDto);

    AuthorRequestResponseDto getMyLatestRequest(Long requesterUserId);

    List<AuthorRequestResponseDto> getRequestsByStatus(String status);

    AuthorRequestResponseDto approveRequest(Long requestId, Long adminUserId);

    AuthorRequestResponseDto rejectRequest(
            Long requestId, Long adminUserId, AuthorRequestRejectRequestDto requestDto);
}
