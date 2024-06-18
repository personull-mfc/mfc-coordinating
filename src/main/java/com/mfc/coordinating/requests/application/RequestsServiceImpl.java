package com.mfc.coordinating.requests.application;

import static com.mfc.coordinating.requests.enums.RequestsStates.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfc.coordinating.common.client.AuthClient;
import com.mfc.coordinating.common.client.AuthInfoResponse;
import com.mfc.coordinating.common.client.MemberClient;
import com.mfc.coordinating.common.client.MemberInfoResponse;
import com.mfc.coordinating.common.exception.BaseException;
import com.mfc.coordinating.common.response.BaseResponseStatus;
import com.mfc.coordinating.requests.domain.Requests;
import com.mfc.coordinating.requests.dto.kafka.CreateChatRoomDto;
import com.mfc.coordinating.requests.dto.req.AuthInfoRequestDto;
import com.mfc.coordinating.requests.dto.req.RequestsCreateReqDto;
import com.mfc.coordinating.requests.dto.req.RequestsUpdateReqDto;
import com.mfc.coordinating.requests.dto.req.UserInfoRequestDto;
import com.mfc.coordinating.requests.dto.res.MyRequestListResponse;
import com.mfc.coordinating.requests.dto.res.RequestsDetailResDto;
import com.mfc.coordinating.requests.dto.res.RequestsListResDto;
import com.mfc.coordinating.requests.enums.RequestsListSortType;
import com.mfc.coordinating.requests.enums.RequestsStates;
import com.mfc.coordinating.requests.infrastructure.RequestsRepository;
import com.mfc.coordinating.requests.mapper.RequestMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RequestsServiceImpl implements RequestsService {
	private final RequestsRepository requestsRepository;
	private final RequestsEventProducer requestsEventProducer;
	private final ObjectMapper objectMapper;
	private final RequestMapper requestMapper;
    private final AuthClient authClient;
	private final MemberClient memberClient;

	@Override
	public void createRequests(RequestsCreateReqDto requestsCreateReqDto, String uuid) {

		AuthInfoResponse auth_response = authClient.getAuthInfo(uuid);
		AuthInfoRequestDto authInfoResponse = auth_response.getResult();

		MemberInfoResponse member_response = memberClient.getMemberInfo(uuid);
		UserInfoRequestDto userInfoResponse = member_response.getResult();

		String userImageUrl = userInfoResponse.getUserImageUrl();
		if (userImageUrl == null) {
			userImageUrl = "";
		}
		String userNickName = userInfoResponse.getUserNickName();
		if (userNickName == null) {
			userNickName = "user";
		}
		Short userGender = authInfoResponse.getUserGender();
		if (userGender == null) {
			userGender = 0;
		}
		LocalDate userBirth = authInfoResponse.getUserBirth();

		int userAge = LocalDate.now().getYear() - userBirth.getYear();

		// Requests 엔티티 생성 및 저장
		Requests requests = getRequests(requestsCreateReqDto, uuid, userImageUrl,
			userNickName, userGender, userAge);

		requestsRepository.save(requests);
	}

	@Override
	public List<MyRequestListResponse> getMyRequestsList(int page, int pageSize, RequestsListSortType sortType,
		String userId) {
		Pageable pageable = getPageable(page, pageSize, sortType);
		Page<Requests> requestsPage = requestsRepository.findByUserId(userId, pageable);

		return requestsPage.getContent()
			.stream()
			.map(requestMapper::toMyRequestListResponse)
			.toList();
	}

	@Override
	public List<RequestsListResDto> getPartnerRequestsList(int page, int pageSize, RequestsListSortType sortType, String partnerId) {
		Pageable pageable = getPageable(page, pageSize, sortType);
		Page<Requests> requestsPage = requestsRepository.findByPartnerId(partnerId, pageable);

		return requestsPage.getContent().stream()
			.map(request -> requestMapper.toRequestsListResDto(request, partnerId))
			.filter(Objects::nonNull)
			.toList();
	}
	@Override
	public List<RequestsListResDto> getUserRequestsList(int page, int pageSize, RequestsListSortType sortType,
		String userId) {
		Pageable pageable = getPageable(page, pageSize, sortType);
		Page<Requests> requestsPage = requestsRepository.findByUserId(userId, pageable);

		return getRequestsListResDtos(requestsPage);
	}

	@Override
	public RequestsDetailResDto getRequestsDetail(String requestId) {
		Requests requests = requestsRepository.findByRequestId(requestId)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		return getRequestsDetailResDto(requests);
	}

	@Transactional
	@Override
	public void updateRequests(RequestsUpdateReqDto dto, String requestId, String uuid) {
		Requests requests = requestsRepository.findByRequestIdAndUserId(requestId, uuid)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		requests.updateRequests(
			dto.getTitle(),
			dto.getDescription(),
			dto.getSituation(),
			dto.getBudget(),
			dto.getBrandIds(),
			dto.getCategoryIds(),
			dto.getReferenceImageUrls(),
			dto.getMyImageUrls()
		);
	}

	@Transactional
	@Override
	public void deleteRequests(String requestId, String uuid) {
		Requests requests = requestsRepository.findByRequestIdAndUserId(requestId, uuid)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		requestsRepository.delete(requests);
	}

	@Transactional
	@Override
	public void updateProposal(String requestId, String partnerId, String uuid, Instant deadline) {
		Requests requests = requestsRepository.findByRequestIdAndUserId(requestId, uuid)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		if (partnerId.equals(uuid)) {
			throw new BaseException(BaseResponseStatus.NON_SELF_MEMBERS);
		}

		requests.addPartnerInfo(partnerId, RequestsStates.NONERESPONSE, deadline);
		requestsRepository.save(requests);

		// Kafka를 통해 파트너에게 알림 이벤트 발행
		//requestsEventProducer.sendPartnerNotificationEvent(requestId, partnerId);
	}

	@Transactional
	@Override
	public void confirmProposal(String requestId, String partnerId, Double price, Instant confirmDate) {
		Requests requests = requestsRepository.findByRequestId(requestId)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		requests.updateConfirmedProposal(partnerId, price, confirmDate);
		requestsRepository.save(requests);
	}

	@Transactional
	@Override
	public void updatePartnerResponse(String requestId, String partnerId, String uuid, RequestsStates status) {
		Requests requests = requestsRepository.findByRequestId(requestId)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.COORDINATING_REQUESTS_NOT_FOUND));

		requests.updatePartnerStatus(partnerId, status);
		requestsRepository.save(requests);

		if(status == RESPONSEACCEPT) {
			List<String> members = new ArrayList<>();
			members.add(partnerId);
			members.add(uuid);

			requestsEventProducer.createChatRoom(
					CreateChatRoomDto.builder()
							.requestId(requestId)
							.members(members)
							.build());
		}
	}

	private Pageable getPageable(int page, int pageSize, RequestsListSortType sortType) {
		Sort sort = switch (sortType) {
			case LATEST -> Sort.by("createdDate").descending();
			case DEADLINE_ASC -> Sort.by("partner.deadline").ascending();
			case DEADLINE_DESC -> Sort.by("partner.deadline").descending();
		};

		return PageRequest.of(page, pageSize, sort);
	}

	private String generateRequestId() {
		return String.valueOf(System.currentTimeMillis());
	}

	private static RequestsDetailResDto getRequestsDetailResDto(Requests requests) {
		return RequestsDetailResDto.builder()
			.requestId(requests.getRequestId())
			.userId(requests.getUserId())
			.title(requests.getTitle())
			.description(requests.getDescription())
			.situation(requests.getSituation())
			.budget(requests.getBudget())
			.userImageUrl(requests.getUserImageUrl())
			.userNickName(requests.getUserNickName())
			.userGender(requests.getUserGender())
			.userAge(requests.getUserAge())
			.createdDate(requests.getCreatedDate())
			.partner(requests.getPartner())
			.brandIds(requests.getBrandIds())
			.categoryIds(requests.getCategoryIds())
			.referenceImageUrls(requests.getReferenceImageUrls())
			.myImageUrls(requests.getMyImageUrls())
			.build();
	}

	private static List<RequestsListResDto> getRequestsListResDtos(Page<Requests> requestsPage) {
		return requestsPage.getContent().stream()
			.flatMap(request -> request.getPartner().stream()
				.map(partner -> RequestsListResDto.builder()
					.requestId(request.getRequestId())
					.userId(request.getUserId())
					.userImageUrl(request.getUserImageUrl())
					.userNickName(request.getUserNickName())
					.userGender(request.getUserGender())
					.userAge(request.getUserAge())
					.title(request.getTitle())
					.createdDate(request.getCreatedDate())
					.partnerId(partner.getPartnerId())
					.status(partner.getStatus())
					.deadline(partner.getDeadline())
					.build()))
			.toList();
	}

	private Requests getRequests(RequestsCreateReqDto requestsCreateReqDto, String uuid, String userImageUrl,
		String userNickName, Short userGender, int userAge) {
		return Requests.builder()
			.requestId(generateRequestId())
			.userId(uuid)
			.title(requestsCreateReqDto.getTitle())
			.description(requestsCreateReqDto.getDescription())
			.situation(requestsCreateReqDto.getSituation())
			.budget(requestsCreateReqDto.getBudget())
			.userImageUrl(userImageUrl)
			.userNickName(userNickName)
			.userGender(userGender)
			.userAge(userAge)
			.brandIds(requestsCreateReqDto.getBrandIds())
			.categoryIds(requestsCreateReqDto.getCategoryIds())
			.referenceImageUrls(requestsCreateReqDto.getReferenceImageUrls())
			.myImageUrls(requestsCreateReqDto.getMyImageUrls())
			.build();
	}

}