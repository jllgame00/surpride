package com.surpride.navhostpoc

import com.surpride.navhostpoc.guidance.RouteProgress
import com.surpride.navhostpoc.returntrip.ReturnRisk
import com.surpride.navhostpoc.ownership.OwnershipState
import com.surpride.navhostpoc.tracking.MatchStatus
import java.util.Locale
import kotlin.math.ceil

/** Driver-facing presentation only. No coordinates, callbacks or platform ownership operations. */
internal data class NavigationFooterInput(
    val destinationName:String?=null,
    val revealed:Boolean=false,
    val active:Boolean=false,
    val returning:Boolean=false,
    val returnCompleted:Boolean=false,
    val arrived:Boolean=false,
    val arrivalCandidate:Boolean=false,
    val working:Boolean=false,
    val rerouting:Boolean=false,
    val match:MatchStatus?=null,
    val progress:RouteProgress?=null,
    val road:String?=null,
    val voiceEnabled:Boolean=false,
    val returnRisk:ReturnRisk?=null,
    val result:String="",
    val ownerState:OwnershipState?=null
)

internal object NavigationFooter {
    /** Priority order is deliberate: whenever one readable row fits, it is the status. */
    fun present(input:NavigationFooterInput):List<String> {
        val stopped=!input.active && (input.result.startsWith("USER_STOPPED") || input.result.startsWith("GUIDANCE_STOPPED"))
        val status=when {
            input.result.startsWith("SESSION_RESTORE_UNAVAILABLE") || input.result.startsWith("SESSION_ENDED_OR_CHANGED") || input.result.startsWith("SESSION_CHANGED")->"저장된 경로를 복원할 수 없어요 · SURPRIDE에서 다시 준비해 주세요"
            input.result.startsWith("SESSION_CACHE_UNAVAILABLE")->"경로 정보를 저장하지 못했어요 · 다시 준비해 주세요"
            input.result.startsWith("RETURN_PREPARATION_INTERRUPTED")->"복귀 준비가 중단됐어요 · 정차 후 복귀 준비를 다시 선택해 주세요"
            input.ownerState==OwnershipState.ERROR->"안내 연결 오류 · 정차 후 다시 준비해 주세요"
            input.ownerState==OwnershipState.REQUESTED->"안내 연결을 확인하고 있어요"
            input.working->if(input.returning) "복귀 경로를 준비하고 있어요" else "경로를 준비하고 있어요"
            input.rerouting->"경로를 다시 찾고 있어요"
            input.result.startsWith("REROUTE_FAILED")->"경로를 다시 찾지 못했어요 · 정차 후 재시도"
            input.result.startsWith("RETURN_FAILED")->"복귀 경로를 준비하지 못했어요"
            input.result.startsWith("RETRY_FAILED")->"경로를 준비하지 못했어요 · 다시 시도해 주세요"
            input.result.startsWith("TRIP_FAILED")->"안내가 중단됐어요 · 정차 후 다시 준비해 주세요"
            input.returnCompleted->"출발지에 도착했어요"
            input.arrived->if(input.revealed) input.destinationName?:"목적지를 공개했어요" else "안전한 정차를 확인했어요"
            stopped->"안내를 종료했어요"
            input.arrivalCandidate->"도착이 가까워요 · 정차 후 도착 확인"
            input.active && input.match!=MatchStatus.ON_ROUTE->"위치를 확인하고 있어요 · 회전 안내 대기"
            input.result.startsWith("POSITION_UNTRUSTED")->"위치 확인이 필요해 안내를 중단했어요"
            input.result.startsWith("EXISTING_NAVIGATION_BLOCKED")->"다른 내비게이션을 종료한 뒤 시작해 주세요"
            input.result.startsWith("PRECISE_LOCATION_REQUIRED")->"정확한 위치 권한이 필요해요"
            input.result.startsWith("ROUTE_EXPIRED")->"경로가 만료됐어요 · 다시 준비해 주세요"
            input.result.startsWith("WAYPOINT_")->"경유지 안내는 아직 지원하지 않아요"
            input.result.startsWith("NEW_ROUTE_NOT_MATCHED")->"경로와 위치가 맞지 않아요 · 다시 확인해 주세요"
            input.result.startsWith("RETURN_ORIGIN_UNKNOWN")->"출발지 정보가 없어 복귀를 준비할 수 없어요"
            input.result.startsWith("REQUEST_COOLDOWN")->"잠시 후 경로 준비를 다시 선택해 주세요"
            input.result.startsWith("START_APPROVAL_EXPIRED")->"시작 확인 시간이 지났어요 · 다시 선택해 주세요"
            input.result.startsWith("FRESH_ON_ROUTE_GPS_REQUIRED")->"경로 위의 현재 위치를 확인하고 있어요"
            input.result.startsWith("MAIN_APP_RESULT_UNAVAILABLE")->"결과 화면을 열지 못했어요"
            input.result.startsWith("ARRIVAL_NOT_CONFIRMED")->"아직 도착을 확인할 수 없어요"
            input.returnRisk==ReturnRisk.AT_RISK->"복귀 시간을 확인해 주세요 · 교통에 따라 달라져요"
            input.returning->if(input.active) "출발지로 돌아가는 중" else "복귀 경로 준비 · 안내 시작 전"
            input.revealed->"공개 · ${input.destinationName?:"목적지"}"
            else->"목적지는 아직 비밀입니다"
        }
        val trusted=input.match==MatchStatus.ON_ROUTE && !stopped && input.ownerState!=OwnershipState.ERROR &&
            input.ownerState!=OwnershipState.REQUESTED && !input.working && !input.rerouting &&
            !input.result.startsWith("TRIP_FAILED")
        val progress=input.progress?.takeIf {trusted && it.remainingMeters.isFinite() && it.remainingMeters>=0 && it.remainingSeconds>=0}
        val estimate=when {
            input.arrived->"안내 종료 · 정차 확인 완료"
            progress!=null->"약 ${ceil(progress.remainingSeconds/60.0).toInt()}분 · ${String.format(Locale.KOREA,"%.1f",progress.remainingMeters/1000)} km 남음 · 추정"
            else->"남은 시간·거리 확인 중"
        }
        val road=if(trusted) input.road?.takeIf {it.isNotBlank()} else null
        val detail=when {
            input.returnCompleted->"안내가 끝났어요 · 안전하게 마무리해 주세요"
            input.arrived->"평가 또는 복귀를 직접 선택해 주세요"
            input.working->"준비 후 안내는 직접 시작해 주세요"
            input.rerouting->"새 경로 확인 전까지 회전 안내를 기다려 주세요"
            road!=null->"$road · 음성 ${if(input.voiceEnabled) "켜짐" else "꺼짐"}"
            input.active->"안전하게 위치를 다시 확인해 주세요"
            else->"정차 후 안내를 시작할 수 있어요"
        }
        return listOf(status,estimate,detail)
    }
}
