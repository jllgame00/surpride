package com.surpride.navhostpoc.voice

internal data class KoreanVoiceCandidate(val id:String,val language:String,val networkRequired:Boolean,val installed:Boolean,val quality:Int)
internal data class KoreanVoiceSelection(val voiceId:String?,val networkRequired:Boolean?)

/** Engine metadata only; absent metadata never implies offline capability. No installation requests. */
internal object KoreanVoicePolicy {
    fun select(voices:List<KoreanVoiceCandidate>?,koreanAvailable:Boolean):KoreanVoiceSelection? {
        if(!koreanAvailable) return null
        val candidate=voices?.filter {it.language=="ko" && it.installed}
            ?.sortedWith(compareBy<KoreanVoiceCandidate> {it.networkRequired}.thenByDescending {it.quality}.thenBy {it.id})?.firstOrNull()
        return if(candidate!=null) KoreanVoiceSelection(candidate.id,candidate.networkRequired)
            else KoreanVoiceSelection(null,null)
    }
}
