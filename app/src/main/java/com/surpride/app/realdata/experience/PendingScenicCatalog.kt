package com.surpride.app.realdata.experience

import com.surpride.app.realdata.SeoulPilotCatalog

/** Research citations only. No photo/prose import or claim of an operational scenic-road licence. */
object PendingScenicCatalog {
    data class Record(val waypoint:ScenicWaypoint,val type:String,val coordinateSource:String?,
        val parking:String,val hours:String,val dayNight:String,val drivingViewEvidence:EvidenceStatus,val uncertainty:String)
    val records=listOf(
        Record(ScenicWaypoint("research-bukak","북악스카이웨이·북악팔각정",tags=setOf("nature","night"),
            sceneryEvidence=EvidenceStatus.VERIFIED,access=AccessStatus.RESTRICTED,
            sources=listOf("https://www.ijongno.co.kr/www/125",
                "https://www.ijongno.co.kr/www/139?action=read&action-value=03c466b9a93a003efb3a8145c4ff83cf"),checkedDate="2026-11-09"),
            "전망·드라이브 코스 자료",null,"공사 기간 주차 제한 공지 / 현재 재개 UNKNOWN","UNKNOWN",
            "도시 전망·야경 관련 자료 / 현재 운영 UNKNOWN",EvidenceStatus.INFERRED,
            "차량 진입 좌표·공사 종료·재사용 조건 미확인. 실제 경유 생성에서 제외."),
        Record(ScenicWaypoint("research-haneul-entry","하늘공원·난지천공원 주차 접근 자료",
            representativePoint=SeoulPilotCatalog.places.last().point,tags=setOf("nature","water"),
            sceneryEvidence=EvidenceStatus.VERIFIED,
            sources=listOf("https://parks.seoul.go.kr/park/detailView.do?pIdx=6",
                "https://parks.seoul.go.kr/maps/worldcup/worldcuppark_map_KR.pdf",SeoulPilotCatalog.SOURCE),checkedDate="2026-11-09"),
            "공원 전망 / 자동차 접근 안내 자료",SeoulPilotCatalog.SOURCE,"시설 안내 있음 / 현재 여유·진입 좌표 UNKNOWN","UNKNOWN",
            "공원 전망 자료 / 도로상 조망 UNKNOWN",EvidenceStatus.UNKNOWN,
            "기존 대표점은 주차장 진입점이 아님. 진입 좌표·운영 이용 조건 미확인으로 실제 경유 제외.")
    )
    val routingCandidates get()=records.map {it.waypoint}.filter {it.eligible}
}
