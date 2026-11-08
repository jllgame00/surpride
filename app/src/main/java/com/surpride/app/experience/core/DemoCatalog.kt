package com.surpride.app.experience.core

/** Fictional places and synthetic durations, not verified POIs, travel times, opening hours or parking. */
object DemoCatalog {
    const val VERSION = 1
    private fun estimates(camping: Boolean = false): Map<TravelScope, JourneyEstimate> = buildMap {
        if (!camping) put(TravelScope.LOCAL, JourneyEstimate(4,14,4,4))
        put(TravelScope.NEARBY, JourneyEstimate(12,25,12,8))
        put(TravelScope.DAY, JourneyEstimate(40,60,40,15))
        put(TravelScope.OVERNIGHT, JourneyEstimate(240,960,240,60))
    }
    private fun place(id: String, title: String, mood: Mood, categories: Set<PlaceCategory>, camping: Boolean = false) = DestinationCandidate(
        id, "가상 샘플 · $title", "분위기와 도착 경험을 시연하기 위한 가상의 장소입니다. 실제 주소·영업시간·주차 정보는 없습니다.",
        Mood.entries.associateWith {if (it == mood) 5 else 2}, categories, Companion.entries.toSet(), estimates(camping),
        weather = if (camping) setOf(Weather.CLEAR) else setOf(Weather.CLEAR, Weather.RAIN)
    )
    val candidates = listOf(
        place("demo-water", "고요한 물가", Mood.HEALING, setOf(PlaceCategory.WATER,PlaceCategory.NATURE,PlaceCategory.CHILD,PlaceCategory.PET)),
        place("demo-night", "불빛 전망대", Mood.EXCITEMENT, setOf(PlaceCategory.NIGHT,PlaceCategory.PET)),
        place("demo-rhythm", "바람의 광장", Mood.ENERGY, setOf(PlaceCategory.NIGHT,PlaceCategory.CHILD,PlaceCategory.PET)),
        place("demo-forest", "숲의 여백", Mood.REFLECTION, setOf(PlaceCategory.NATURE,PlaceCategory.PET)),
        place("demo-rest", "작은 쉼터", Mood.COMFORT, setOf(PlaceCategory.NATURE,PlaceCategory.CHILD,PlaceCategory.PET)),
        place("demo-camp", "별빛 캠프", Mood.ADVENTURE, setOf(PlaceCategory.CAMPING,PlaceCategory.NATURE,PlaceCategory.CHILD,PlaceCategory.PET), true),
        place("demo-adventure", "낯선 산책길", Mood.ADVENTURE, setOf(PlaceCategory.NATURE,PlaceCategory.CHILD,PlaceCategory.PET))
    )
}
