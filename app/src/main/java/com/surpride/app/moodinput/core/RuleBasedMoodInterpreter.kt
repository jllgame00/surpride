package com.surpride.app.moodinput.core

import com.surpride.app.experience.core.*
import com.surpride.app.experience.core.Companion as DriveCompanion
import java.time.Instant
import java.time.ZoneId

/** Local, deterministic Korean rules. Never sends, stores, logs, or returns the utterance. */
class RuleBasedMoodInterpreter {
    fun extract(text: String, nowMillis: Long): MoodInterpretation {
        if (text.length > 1000) return MoodInterpretation(PreferencePatch(), setOf(InterpretationIssue.INPUT_TOO_LONG))
        if (text.isBlank()) return MoodInterpretation(PreferencePatch(), setOf(InterpretationIssue.EMPTY_INPUT))
        val compact = text.replace(Regex("\\s+"), "").replace(Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")) {
            "${it.groupValues[1]}시${it.groupValues[2]}분"
        }
        val issues = linkedSetOf<InterpretationIssue>()
        val categoryMatches = categoryWords.flatMap { (category, pattern) ->
            Regex(pattern).findAll(compact).map { Mention(category, it.range.first, it.range.last + 1) }.toList()
        }.sortedBy { it.start }
        val included = linkedSetOf<PlaceCategory>()
        val excluded = linkedSetOf<PlaceCategory>()
        val removed = linkedSetOf<PlaceCategory>()
        val tails = categoryMatches.mapIndexed { index, mention ->
            compact.substring(mention.end, categoryMatches.getOrNull(index + 1)?.start ?: compact.length)
        }
        // A terminal negation applies to a finite conjunction chain: "바다와 야경은 빼줘".
        val modes = tails.map { tail -> when {
            Regex("^(?:은|는|을|를)?보다는").containsMatchIn(tail) -> 2
            isNegated(tail) -> 1
            else -> 0
        } }.toMutableList()
        for (index in categoryMatches.size - 2 downTo 0) {
            if (Regex("(?:와|과|하고|및|이랑|랑|,|·|그리고)").matches(tails[index]) && modes[index + 1] != 0)
                modes[index] = modes[index + 1]
        }
        categoryMatches.forEachIndexed { index, mention ->
            val tail = tails[index]
            when (modes[index]) {
                2 -> removed += mention.category
                1 -> excluded += mention.category
                else -> included += mention.category
            }
            val next = categoryMatches.getOrNull(index + 1)
            if (next != null && next.category != mention.category && Regex("^(?:이)?(?:나|또는|혹은|아니면)").containsMatchIn(tail))
                issues += InterpretationIssue.ALTERNATIVE_CONDITIONS
        }
        if ((included intersect excluded).isNotEmpty() || (included intersect removed).isNotEmpty()) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS

        val moods = moodWords.filter { (_, words) -> words.any { word ->
            val match = Regex(word).find(compact) ?: return@any false
            val negative = isNegated(compact.substring(match.range.last + 1)) || compact.substring(0, match.range.first).endsWith("안")
            if (negative) issues += InterpretationIssue.UNSUPPORTED_CONDITION
            !negative
        } }.keys
        if (moods.size > 1) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS

        fun <T> positiveMentions(patterns: Map<T, String>): Set<T> = patterns.filter { (_, pattern) ->
            val matches = Regex(pattern).findAll(compact).toList()
            val negated = matches.any { isNegated(compact.substring(it.range.last + 1)) || compact.substring(0, it.range.first).endsWith("안") }
            if (negated) issues += InterpretationIssue.UNSUPPORTED_CONDITION
            matches.isNotEmpty() && !negated
        }.keys
        val companions = positiveMentions(companionWords)
        if (companions.size > 1) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS
        val scopes = positiveMentions(scopeWords)
        if (scopes.size > 1) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS

        // Clock minutes belong to the deadline, never to the available-duration field.
        val durationText = compact.replace(deadlineRegex, "")
        val unsupportedClock = Regex("(?:$number)시(?!간)|\\d+:\\d+").containsMatchIn(durationText)
        if (unsupportedClock) issues += InterpretationIssue.AMBIGUOUS_TIME
        val durations = durationRegex.findAll(durationText).toList()
        val invalidDurationNumber = Regex("[+-]\\d+(?:\\.\\d*)?(?:시간|분)|(?:\\d+\\.\\d*|\\.\\d+)(?:시간|분)").containsMatchIn(durationText)
        if (invalidDurationNumber) issues += InterpretationIssue.INVALID_TIME
        val minuteValues = durations.mapNotNull { match ->
            val hoursText = match.groups[1]?.value
            val minutesText = match.groups[3]?.value ?: match.groups[4]?.value
            val hours = if (hoursText == null) 0 else koreanNumber(hoursText)
            val minutes = if (minutesText == null) 0 else koreanNumber(minutesText)
            if (hours == null || minutes == null) { issues += InterpretationIssue.INVALID_TIME; null }
            else (hours.toLong() * 60 + minutes + if (match.groups[2]?.value == "반" || hoursText == "반") 30 else 0)
        }
        if (minuteValues.size > 1) issues += InterpretationIssue.AMBIGUOUS_TIME
        val unparsedTime = durationText.replace(durationRegex, "").replace(Regex("시간(?:은|는|을|를)"), "")
        if (unparsedTime.contains("시간") || Regex("\\d+:\\d+").containsMatchIn(compact)) issues += InterpretationIssue.AMBIGUOUS_TIME
        val available = if (invalidDurationNumber || unsupportedClock) null else minuteValues.singleOrNull()?.let {
            if (it !in 1..10080) { issues += InterpretationIssue.INVALID_TIME; null } else it.toInt()
        }
        if (Regex("\\d+\\.\\d+(?:시간|분)|시간반반|몇시간|몇분").containsMatchIn(compact)) issues += InterpretationIssue.AMBIGUOUS_TIME

        val deadlineMatches = deadlineRegex.findAll(compact).toList()
        var deadline: Long? = null
        if (deadlineMatches.size > 1) issues += InterpretationIssue.AMBIGUOUS_TIME
        if (deadlineMatches.size == 1) {
            val match = deadlineMatches.single()
            var hour = koreanNumber(match.groupValues[2])
            val minute = match.groups[3]?.value?.let(::koreanNumber) ?: if (match.groups[4] != null) 30 else 0
            val period = match.groupValues[1]
            if (hour == null || minute !in 0..59 || hour !in 0..23 || (period.isNotEmpty() && hour !in 1..12)) issues += InterpretationIssue.INVALID_TIME
            else if ((period.isEmpty() && hour < 13) || (period == "밤" && hour == 12)) issues += InterpretationIssue.AMBIGUOUS_TIME
            else {
                if (period in setOf("오후", "저녁", "밤")) hour = hour % 12 + 12
                if (period in setOf("오전", "아침")) hour %= 12
                try {
                    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of("Asia/Seoul"))
                    val date = now.toLocalDate().plusDays(if (compact.contains("내일")) 1 else 0)
                    val value = date.atTime(hour, minute).atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
                    if (value <= nowMillis) issues += InterpretationIssue.AMBIGUOUS_TIME else deadline = value
                } catch (_: RuntimeException) { issues += InterpretationIssue.INVALID_TIME }
            }
        }
        if (Regex("(?:\\d+월\\d+일|모레|다음주|이번주|자정|정오)").containsMatchIn(compact)) issues += InterpretationIssue.AMBIGUOUS_TIME
        if (compact.contains("오늘") && compact.contains("내일")) issues += InterpretationIssue.AMBIGUOUS_TIME

        val roundTripMentions = Regex("왕복").findAll(compact).toList()
        val negatedRoundTrip = roundTripMentions.any { isNegated(compact.substring(it.range.last + 1)) }
        val noReturn = negatedRoundTrip || Regex("편도|돌아오지않|돌아오지안|복귀안|복귀하지않").containsMatchIn(compact)
        val yesReturn = roundTripMentions.any { !isNegated(compact.substring(it.range.last + 1)) } ||
            Regex("출발지에?돌아|다시돌아|복귀해야|돌아와야|돌아올래").containsMatchIn(compact)
        if (noReturn && yesReturn) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS
        val randomMatches = Regex("(?:랜덤|무작위|랜덤성)(?:정도|은|는|을|를|성|:|=)*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))").findAll(compact).toList()
        if (randomMatches.size > 1) issues += InterpretationIssue.CONTRADICTORY_CONDITIONS
        val randomness = randomMatches.singleOrNull()?.groupValues?.get(1)?.toIntOrNull()?.let {
            if (it !in 0..100) { issues += InterpretationIssue.INVALID_RANDOMNESS; null } else it
        }
        if (randomMatches.isNotEmpty() && randomness == null) issues += InterpretationIssue.INVALID_RANDOMNESS

        if (Regex("사람(?:이|은|도)?(?:없|적|많|안)|인파|붐비|북적|한산|혼잡|대기줄|조용한곳|조용한장소|진단|치료|우울증").containsMatchIn(compact)) issues += InterpretationIssue.UNSUPPORTED_CONDITION
        if (Regex("위도|경도|북위|동경|좌표|latitude|longitude|\\d+\\.\\d+[,/]\\d+\\.\\d+|(?:서울|부산|제주|강릉|속초|대전|대구|인천|광주|울산)|[가-힣]+(?:역|해변|공원)(?:으로|로|에|까지)|\\d+(?:km|킬로미터)", RegexOption.IGNORE_CASE).containsMatchIn(compact)) issues += InterpretationIssue.UNSUPPORTED_LOCATION

        val patch = PreferencePatch(
            mood = moods.singleOrNull(), scope = scopes.singleOrNull(), categories = included.takeIf { it.isNotEmpty() },
            excludedCategories = excluded.takeIf { it.isNotEmpty() }, removedCategories = removed.takeIf { it.isNotEmpty() },
            companion = companions.singleOrNull(), availableMinutes = available, returnByEpochMillis = deadline,
            returnToStart = when { noReturn && !yesReturn -> false; yesReturn && !noReturn -> true; else -> null }, randomness = randomness
        )
        if (patch.isEmpty() && issues.isEmpty()) issues += InterpretationIssue.UNKNOWN_INPUT
        return MoodInterpretation(patch, issues)
    }

    private data class Mention(val category: PlaceCategory, val start: Int, val end: Int)
    private fun isNegated(tail: String): Boolean = Regex("^(?:은|는|을|를|이|가|도)?(?:말고|빼|제외|싫|아닌|아니고|아니라|지않|지는않|안해|안하|안할|하지않|하지안|안가|가지않|원하지않|보고싶지않|보기싫|안보고)").containsMatchIn(tail)

    private fun koreanNumber(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        nativeNumbers[text]?.let { return it }
        if (text == "반") return 0
        if (text.contains('십')) {
            val parts = text.split('십')
            if (parts.size != 2) return null
            val tens = if (parts[0].isEmpty()) 1 else nativeNumbers[parts[0]] ?: return null
            val ones = if (parts[1].isEmpty()) 0 else nativeNumbers[parts[1]] ?: return null
            return tens * 10 + ones
        }
        return null
    }

    private companion object {
        val nativeNumbers = mapOf("영" to 0, "일" to 1, "이" to 2, "삼" to 3, "사" to 4, "오" to 5, "육" to 6, "칠" to 7, "팔" to 8, "구" to 9,
            "한" to 1, "하나" to 1, "두" to 2, "둘" to 2, "세" to 3, "셋" to 3, "네" to 4, "넷" to 4, "다섯" to 5, "여섯" to 6,
            "일곱" to 7, "여덟" to 8, "아홉" to 9, "열" to 10, "열한" to 11, "열두" to 12, "스무" to 20, "서른" to 30)
        const val number = "(?:\\d+|열두|열한|여덟|일곱|아홉|다섯|여섯|스무|서른|하나|둘|셋|넷|[영일이삼사오육칠팔구십한두세네열]+|반)"
        val durationRegex = Regex("($number)시간(반)?(?:($number)분)?|($number)분")
        val deadlineRegex = Regex("(오전|오후|아침|저녁|밤)?($number)시(?!간)(?:($number)분|(반))?(?:까지|에는|에복귀|에돌아)")
        val categoryWords = mapOf(PlaceCategory.WATER to "바다|해안|호수|강가|강변|강(?=을|은|과|와|나|$)", PlaceCategory.NIGHT to "야경|밤풍경",
            PlaceCategory.NATURE to "숲|자연|수목원", PlaceCategory.CAMPING to "캠핑|야영", PlaceCategory.CHILD to "아이|어린이|자녀",
            PlaceCategory.PET to "반려동물|반려견|강아지|댕댕이|애견")
        val moodWords = mapOf(Mood.HEALING to listOf("힐링", "휴식", "쉬고", "편안", "답답해서", "바람쐬고"), Mood.EXCITEMENT to listOf("설렘", "설레", "두근", "새로운풍경"),
            Mood.ENERGY to listOf("신나", "신남", "활기", "에너지", "기분전환"), Mood.REFLECTION to listOf("생각정리", "머리정리", "생각을정리", "사색"),
            Mood.COMFORT to listOf("위로", "다독", "지친마음"), Mood.ADVENTURE to listOf("모험", "탐험", "도전", "낯선"))
        val companionWords = mapOf(DriveCompanion.SOLO to "혼자|나홀로", DriveCompanion.PARTNER to "연인|애인|커플|둘이", DriveCompanion.FRIENDS to "친구", DriveCompanion.FAMILY to "가족|부모님")
        val scopeWords = mapOf(TravelScope.LOCAL to "동네|근처", TravelScope.NEARBY to "근교", TravelScope.DAY to "당일", TravelScope.OVERNIGHT to "숙박|[1-7]박|하룻밤|일박")
    }
}
