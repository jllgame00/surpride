package com.surpride.app.experience.core

enum class Mood(val label: String, val note: String) {
    HEALING("힐링", "잠시 속도를 늦추고"), EXCITEMENT("설렘", "새로운 장면을 만나고"),
    ENERGY("신남", "기분 좋은 리듬을 타고"), REFLECTION("생각정리", "마음에 여백을 만들고"),
    COMFORT("위로", "오늘의 나를 다독이고"), ADVENTURE("모험", "익숙함에서 한 걸음 더")
}
enum class TravelScope(val label: String, val note: String, val defaultMinutes: Int) {
    LOCAL("동네 한 바퀴", "약 30분", 30), NEARBY("근교 나들이", "약 1시간", 60),
    DAY("당일치기", "약 2~3시간", 180), OVERNIGHT("국내 여행", "1박 이상", 2880)
}
enum class PlaceCategory(val label: String) {
    WATER("바다 · 호수 · 강"), NIGHT("야경"), NATURE("자연 · 숲"), CAMPING("캠핑"), CHILD("아이 동반"), PET("반려동물 동반")
}
enum class Companion(val label: String) { SOLO("혼자"), PARTNER("연인"), FRIENDS("친구"), FAMILY("가족") }
enum class Season { SPRING, SUMMER, AUTUMN, WINTER }
enum class Weather { CLEAR, RAIN, STORM }
enum class EstimateOrigin { DEMO, VERIFIED_ROUTE }
data class DrivePreferences(
    val mood: Mood = Mood.HEALING,
    val scope: TravelScope = TravelScope.LOCAL,
    val categories: Set<PlaceCategory> = emptySet(),
    val companion: Companion = Companion.SOLO,
    val availableMinutes: Int? = 60,
    val returnByEpochMillis: Long? = null,
    val returnToStart: Boolean = true,
    val randomness: Int = 40,
    val excludedCategories: Set<PlaceCategory> = emptySet()
)
data class JourneyEstimate(
    val outboundMinutes: Int, val stayMinutes: Int, val returnMinutes: Int, val bufferMinutes: Int,
    val origin: EstimateOrigin = EstimateOrigin.DEMO,
    val verifiedDistanceKm: Double? = null
) {
    fun total(returnToStart: Boolean): Int = outboundMinutes + stayMinutes + bufferMinutes + if (returnToStart) returnMinutes else 0
    fun valid(): Boolean {
        val legs=listOf(outboundMinutes,stayMinutes,returnMinutes,bufferMinutes)
        return legs.all {it>=0} && outboundMinutes>0 && legs.sumOf {it.toLong()}<=Int.MAX_VALUE
    }
}
/** No GPS/road geometry in the sample catalog. Real providers must supply verified estimates explicitly. */
data class DestinationCandidate(
    val id: String, val name: String, val description: String,
    val moods: Map<Mood, Int>, val categories: Set<PlaceCategory>, val companions: Set<Companion>,
    val estimates: Map<TravelScope, JourneyEstimate>,
    val seasons: Set<Season> = Season.entries.toSet(),
    val weather: Set<Weather> = setOf(Weather.CLEAR, Weather.RAIN),
    val sample: Boolean = true
)
data class VehicleContext(val verifiedRangeKm: Double? = null)
data class RecommendationContext(
    val nowMillis: Long, val recentIds: Set<String> = emptySet(),
    val season: Season? = null, val weather: Weather? = null,
    val vehicle: VehicleContext = VehicleContext()
)
enum class PlanWarning { DEMO_ESTIMATES, RANGE_UNVERIFIED, DISTANCE_UNVERIFIED, WEATHER_UNKNOWN, SEASON_UNKNOWN }
data class RecommendationScore(val mood: Int, val place: Int, val companion: Int, val season: Int, val weather: Int, val variation: Int) {
    val total get() = mood + place + companion + season + weather + variation
}
sealed interface RecommendationResult {
    data class Success(val candidate: DestinationCandidate, val estimate: JourneyEstimate, val totalMinutes: Int,
        val score: RecommendationScore, val warnings: Set<PlanWarning>) : RecommendationResult
    data class Failure(val reason: String) : RecommendationResult
}
enum class TripPhase { SETUP, PLANNING, READY, DEMO_DRIVING, ARRIVED, REVEALED, COMPLETED }
enum class ExperiencePanel { HOME, TRIP, MAP }
data class DemoVisit(val tripId: String, val candidateId: String, val mood: Mood, val rating: Int, val completedAtMillis: Long, val simulatedArrival: Boolean)
data class MysteryTripState(
    val phase: TripPhase = TripPhase.SETUP, val panel: ExperiencePanel = ExperiencePanel.HOME,
    val preferences: DrivePreferences = DrivePreferences(), val setupStep: Int = 0,
    val plan: RecommendationResult.Success? = null, val error: String? = null,
    val seed: Long = 0, val tripId: String = "", val progress: Int = 0, val arrived: Boolean = false,
    val hintVisible: Boolean = false, val rating: Int? = null, val history: List<DemoVisit> = emptyList()
)
data class RevealedDestination(val name: String, val description: String, val sample: Boolean)
data class TripPresentation(val phase: TripPhase, val simulation: Boolean, val totalMinutes: Int?,
    val preparation: String, val reason: String, val destination: RevealedDestination?)
object ParkedInteractionPolicy {
    fun allows(requiresDistractionOptimization: Boolean?) = requiresDistractionOptimization == false
    fun runIfAllowed(current: () -> Boolean?, action: () -> Unit): Boolean {
        if(!allows(current())) return false
        action();return true
    }
}
