package com.surpride.app.moodinput

import com.surpride.app.experience.core.DrivePreferences
import com.surpride.app.experience.core.PlaceCategory
import com.surpride.app.moodinput.core.InterpretationIssue
import com.surpride.app.moodinput.core.MoodInterpretation

/** Finite preference-only copy: no destination, coordinates, utterance or provider payload input. */
internal object MoodInputPresentation {
    fun extraction(result:MoodInterpretation?):String {
        if(result==null) return "문장을 해석하면 바뀐 조건만 여기에 보여 드려요"
        if(!result.canApply) return result.issues.joinToString("\n") {issue(it)}
            .ifBlank {"알아볼 수 있는 조건이 없어요. 기분과 시간을 다시 입력해 주세요"}
        val patch=result.patch
        val changes=buildList {
            patch.mood?.let {add("기분 ${it.label}")}
            patch.availableMinutes?.let {add("사용 시간 ${it}분")}
            patch.returnToStart?.let {add(if(it) "출발지로 돌아오는 왕복" else "돌아오지 않는 편도")}
            patch.companion?.let {add("동행 ${it.label}")}
            patch.scope?.let {add("이동 범위 ${it.label}")}
            patch.categories?.takeIf {it.isNotEmpty()}?.let {add("선호 ${categories(it)}")}
            patch.excludedCategories?.takeIf {it.isNotEmpty()}?.let {add("제외 ${categories(it)}")}
            patch.removedCategories?.takeIf {it.isNotEmpty()}?.let {add("선호 해제 ${categories(it)}")}
            patch.returnByEpochMillis?.let {add("귀가 희망 시각 변경 · 오른쪽 조건에서 확인")}
            patch.randomness?.let {add("우연 정도 ${randomness(it)}")}
        }
        return "이번 문장에서 정리한 조건\n"+changes.joinToString("\n")+
            "\n말하지 않은 조건은 현재 선택을 유지해요"
    }

    fun summary(p:DrivePreferences):String=buildString {
        append("${p.mood.label} · ${p.companion.label}\n")
        append("${p.scope.label}\n")
        append("${p.availableMinutes?.let {"사용 시간 ${it}분"}?:"사용 시간 미지정"} · ${if(p.returnToStart) "왕복" else "편도"}\n")
        append("선호 ${categories(p.categories).ifEmpty {"특별한 조건 없음"}}\n")
        append("제외 ${categories(p.excludedCategories).ifEmpty {"없음"}}\n")
        append("우연 정도 ${randomness(p.randomness)}")
    }

    fun categoryLabel(category:PlaceCategory,p:DrivePreferences):String=category.label+" · "+when(category) {
        in p.categories->"선호"
        in p.excludedCategories->"제외"
        else->"미지정"
    }

    fun readyToApply(allowed:Boolean,dirty:Boolean,result:MoodInterpretation?,working:Boolean,
        speechBusy:Boolean,budgetMinutes:Int?):Boolean = allowed && !dirty && result?.canApply!=false &&
        !working && !speechBusy && budgetMinutes?.let {it>0}==true

    fun issue(value:InterpretationIssue):String=when(value) {
        InterpretationIssue.EMPTY_INPUT->"기분이나 사용 시간을 먼저 입력해 주세요"
        InterpretationIssue.INPUT_TOO_LONG->"문장이 길어요. 필요한 조건만 짧게 알려 주세요"
        InterpretationIssue.UNKNOWN_INPUT->"조건을 알아보지 못했어요. 기분과 시간을 다시 알려 주세요"
        InterpretationIssue.CONTRADICTORY_CONDITIONS->"서로 다른 조건이 함께 있어요. 원하는 조건을 하나로 정해 주세요"
        InterpretationIssue.AMBIGUOUS_TIME->"사용 시간이 분명하지 않아요. 몇 분인지 알려 주세요"
        InterpretationIssue.INVALID_TIME->"사용할 수 있는 시간이나 귀가 시각을 다시 확인해 주세요"
        InterpretationIssue.INVALID_RANDOMNESS->"우연 정도를 선택 항목에서 정해 주세요"
        InterpretationIssue.UNSUPPORTED_CONDITION->"아직 해석할 수 없는 조건이 있어요. 선택 항목에서 정해 주세요"
        InterpretationIssue.UNSUPPORTED_LOCATION->"장소 이름으로 직접 지정할 수 없어요. 원하는 풍경을 선택해 주세요"
        InterpretationIssue.ALTERNATIVE_CONDITIONS->"여러 조건 중 원하는 항목을 직접 선택해 주세요"
    }

    private fun categories(values:Set<PlaceCategory>)=PlaceCategory.entries.filter {it in values}.joinToString {it.label}
    private fun randomness(value:Int)=when(value) {0->"취향 중심";40->"균형";100->"새로운 발견";else->"${value}%"}
}
