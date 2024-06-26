package com.mfc.coordinating.reviews.application;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfc.coordinating.common.exception.BaseException;
import com.mfc.coordinating.common.response.BaseResponseStatus;
import com.mfc.coordinating.reviews.dto.request.ReviewsRequest;
import com.mfc.coordinating.reviews.entity.ReviewImage;
import com.mfc.coordinating.reviews.entity.Reviews;
import com.mfc.coordinating.reviews.infrastructure.ReviewsRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ReviewsServiceImpl implements ReviewsService {
	private final ReviewsRepository reviewsRepository;
	private final ReviewEventProducer reviewEventProducer;

	@Override
	@Transactional
	public void createReview(ReviewsRequest request) {
		Reviews review = buildReviewFromRequest(request);
		reviewsRepository.save(review);
		publishReviewEvent(request.getPartnerId(), request.getRating());
	}

	@Override
	@Transactional
	public void updateReview(Long reviewId, String comment, List<String> reviewImageUrls) {
		Reviews review = getReviewById(reviewId);
		review.updateComment(comment);
		review.updateReviewImage(convertToReviewImages(reviewImageUrls));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Reviews> getReviewsByPartner(String partnerId, int page, int pageSize) {
		return reviewsRepository.findByPartnerId(partnerId, PageRequest.of(page, pageSize));
	}

	@Override
	@Transactional(readOnly = true)
	public Reviews getReviewById(Long reviewId) {
		return reviewsRepository.findById(reviewId)
			.orElseThrow(() -> new BaseException(BaseResponseStatus.REVIEW_NOT_FOUND));
	}

	@Override
	@Transactional
	public void deleteReview(Long reviewId) {
		Reviews review = getReviewById(reviewId);
		reviewsRepository.delete(review);
	}

	private Reviews buildReviewFromRequest(ReviewsRequest request) {
		Reviews review = Reviews.builder()
			.requestId(request.getRequestId())
			.userId(request.getUserId())
			.partnerId(request.getPartnerId())
			.rating(request.getRating())
			.comment(request.getComment())
			.build();

		List<ReviewImage> reviewImages = convertToReviewImages(request.getReviewImage());
		review.updateReviewImage(reviewImages);

		return review;
	}

	private List<ReviewImage> convertToReviewImages(List<String> imageUrls) {
		return imageUrls.stream()
			.map(ReviewImage::new)
			.collect(Collectors.toList());
	}

	private void publishReviewEvent(String partnerId, Short rating) {
		reviewEventProducer.publishCreateReviewEvent(partnerId, rating);
	}
}