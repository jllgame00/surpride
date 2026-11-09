package com.surpride.navhostpoc.voice

import org.junit.Assert.*
import org.junit.Test

class KoreanVoicePolicyTest {
    @Test fun installedOfflineKoreanIsPreferredOverHigherQualityNetworkVoice() {
        val result=KoreanVoicePolicy.select(listOf(
            KoreanVoiceCandidate("network","ko",true,true,500),
            KoreanVoiceCandidate("offline","ko",false,true,100)),true)
        assertEquals("offline",result?.voiceId);assertEquals(false,result?.networkRequired)
    }
    @Test fun installedNetworkKoreanIsAllowedWhenNoInstalledOfflineVoiceExists() {
        val result=KoreanVoicePolicy.select(listOf(
            KoreanVoiceCandidate("missing-offline","ko",false,false,500),
            KoreanVoiceCandidate("installed-network","ko",true,true,100)),true)
        assertEquals("installed-network",result?.voiceId);assertEquals(true,result?.networkRequired)
    }
    @Test fun successfulKoreanLanguageSupportAllowsDefaultVoiceWithUnknownNetworkMetadata() {
        for(voices in listOf(null,emptyList<KoreanVoiceCandidate>())) {
            val result=KoreanVoicePolicy.select(voices,true)
            assertNotNull(result);assertNull(result?.voiceId);assertNull(result?.networkRequired)
        }
    }
    @Test fun unavailableKoreanAndNonKoreanOrUninstalledVoicesCannotPretendReady() {
        assertNull(KoreanVoicePolicy.select(null,false))
        assertNull(KoreanVoicePolicy.select(listOf(
            KoreanVoiceCandidate("english","en",false,true,500),
            KoreanVoiceCandidate("uninstalled","ko",false,false,500)),false))
    }
}
