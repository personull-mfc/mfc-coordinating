package com.mfc.coordinating.requests.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.mfc.coordinating.requests.domain.RequestHistory;

public interface RequestHistoryRepository extends MongoRepository<RequestHistory, Long> {

	Page<RequestHistory> findByPartnerId(String partnerId, Pageable pageable);

	Page<RequestHistory> findByUserId(String userId, Pageable pageable);

}