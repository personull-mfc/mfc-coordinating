package com.mfc.coordinating.requests.enums;

public enum RequestsStates {
	WRITING,			// 요청전
	NONERESPONSE,		// 요청(미응답)
	RESPONSEACCEPT,		// 요청수락(응답)
	RESPONSEREJECT,		// 요청거절(응답)
	TRADE_CREATED,		// 거래 생성
	PROPOSALREJECT,		// 거래 거절
	CONFIRMED,			// 거래 확정
	COORDINATE_RECEIVED, // 코디 받은 상태
	CLOSED				// 마감(코디완료)
}
