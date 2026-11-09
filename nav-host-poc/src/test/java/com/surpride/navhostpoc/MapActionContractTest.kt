package com.surpride.navhostpoc

import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class MapActionContractTest {
    @Test fun revealMapControlUsesIconWithoutForbiddenTitle() {
        val context=RuntimeEnvironment.getApplication()
        val icon=CarIcon.Builder(IconCompat.createWithResource(context,R.drawable.ic_reveal)).build()
        val strip=ActionStrip.Builder().addAction(Action.Builder().setIcon(icon).setOnClickListener {}.build()).build()
        val primary=ActionStrip.Builder().addAction(Action.Builder().setTitle("안내 종료").setOnClickListener {}.build()).build()
        assertNotNull(NavigationTemplate.Builder().setActionStrip(primary).setMapActionStrip(strip).build())
        assertNotNull(strip.actions.single().icon)
        assertTrue(strip.actions.single().title?.toString().isNullOrEmpty())
    }
}
