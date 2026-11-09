package com.surpride.app.realdata

import com.surpride.app.experience.core.*

/** Source facts: name/type/representative point. Mood weights are SURPRIDE editorial hypotheses.
 * Points are NOT verified vehicle entrances. No parking/hours/accessibility claims are inferred. */
object SeoulPilotCatalog {
    const val SOURCE="https://data.seoul.go.kr/dataList/OA-22579/S/1/datasetView.do"
    val places=listOf(
        RealPlace("seoul-night-3","용양봉저정 공원",GeoPoint(126.958588093172,37.5118003827004),"공원/광장",
            setOf(PlaceCategory.NATURE,PlaceCategory.NIGHT),mapOf(Mood.HEALING to 5,Mood.COMFORT to 4),SOURCE),
        RealPlace("seoul-night-15","사육신공원",GeoPoint(126.949411135441,37.5134579901091),"공원/광장",
            setOf(PlaceCategory.NATURE,PlaceCategory.NIGHT),mapOf(Mood.REFLECTION to 5,Mood.COMFORT to 5),SOURCE),
        RealPlace("seoul-night-13","이촌한강공원",GeoPoint(126.970850893942,37.517820588578),"공공시설",
            setOf(PlaceCategory.WATER,PlaceCategory.NATURE,PlaceCategory.NIGHT),mapOf(Mood.HEALING to 5,Mood.ENERGY to 4),SOURCE),
        RealPlace("seoul-night-21","광화문광장(광화문)",GeoPoint(126.976787992565,37.5716228232824),"공원/광장",
            setOf(PlaceCategory.NIGHT),mapOf(Mood.EXCITEMENT to 5,Mood.ENERGY to 5),SOURCE),
        RealPlace("seoul-night-22","하늘공원(월드컵공원내)",GeoPoint(126.886318397942,37.5690794796181),"공원/광장",
            setOf(PlaceCategory.NATURE,PlaceCategory.NIGHT),mapOf(Mood.ADVENTURE to 5,Mood.REFLECTION to 4),SOURCE)
    )
    /** Public catalog point, not current device position and not a verified car departure point. */
    val developmentOrigin=places[3].point
}
