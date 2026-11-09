package com.surpride.app.realdata.experience

import com.surpride.app.realdata.GeoPoint
import com.surpride.app.realdata.RouteOptions
import java.time.LocalDate

enum class EvidenceStatus { VERIFIED, INFERRED, UNKNOWN }
enum class AccessStatus { ALLOWED, RESTRICTED, UNKNOWN }
data class ScenicWaypoint(
    val id: String, val privateName: String, val representativePoint: GeoPoint? = null,
    val entryPoint: GeoPoint? = null, val tags: Set<String> = emptySet(),
    val sceneryEvidence: EvidenceStatus = EvidenceStatus.UNKNOWN,
    val access: AccessStatus = AccessStatus.UNKNOWN,
    val entryEvidence: EvidenceStatus = EvidenceStatus.UNKNOWN,
    val licenseVerified: Boolean = false, val sources: List<String> = emptyList(), val checkedDate: String = ""
) {
    val eligible get() = entryPoint != null && sceneryEvidence == EvidenceStatus.VERIFIED &&
        access == AccessStatus.ALLOWED && entryEvidence == EvidenceStatus.VERIFIED && licenseVerified &&
        sources.isNotEmpty() && sources.all { it.startsWith("https://") } &&
        runCatching { LocalDate.parse(checkedDate) }.isSuccess
    override fun toString() = "ScenicWaypoint(REDACTED)"
}

/** Initial mood hypotheses, not measured enjoyment. Safety is always a hard constraint. */
data class ExperienceWeights(val destination: Double, val route: Double, val time: Double,
    val repeat: Double, val detour: Double, val scenery: Double)
data class ExperienceAssessment(val destinationScore: Int, val routeScore: Double, val combinedScore: Double,
    val timeScore: Double, val repeatScore: Double?, val detourScore: Double?, val sceneryScore: Double?)
enum class ComparisonType { PRIORITY, SCENIC }
enum class ComparisonStatus { ELIGIBLE, NO_ROUTE, TIME_EXCEEDED, DUPLICATE, EXCESSIVE_DETOUR, STALE_DATA, INVALID_RESPONSE, RANGE_EXCEEDED }
/** Labels are fixed generic text; identities, coordinates and geometry never enter this projection. */
data class RouteComparison(val label: String, val type: ComparisonType, val totalSeconds: Long?,
    val distanceMeters: Long?, val status: ComparisonStatus)
class ExperienceSelection(val selectedOptions: RouteOptions, val weights: ExperienceWeights,
    val assessment: ExperienceAssessment, val comparisons: List<RouteComparison>, val requestCount: Int,
    val publicReason: String) {
    override fun toString() = "ExperienceSelection(requestCount=$requestCount, comparisons=${comparisons.size})"
}
