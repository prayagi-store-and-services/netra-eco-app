package com.prayagi.netraeco

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoriesTest {
    @Test fun playerIsMediaNotUtility() =
        assertEquals(listOf(AppCategory.MEDIA), AppCategories.classify("Netra Player", "Video and MP3 player", "A simple video and MP3 player for Android. Plays video and music files stored on your phone."))
    @Test fun hubIsSafety() =
        assertEquals(listOf(AppCategory.SAFETY), AppCategories.classify("Netra Hub", "Security application", "Unified Android safety and emergency dashboard."))
    @Test fun batteryAppIsDevice() =
        assertEquals(listOf(AppCategory.DEVICE), AppCategories.classify("Netra Battery Sentinel Pro", "Battery and device health app", "Truthful battery, charging and hardware monitoring for Android."))
    @Test fun appWithSeveralFeaturesIsInSeveralGroups() =
        assertEquals(listOf(AppCategory.MEDIA, AppCategory.SAFETY), AppCategories.classify("X", "Video player", "Also shows a privacy report"))
    @Test fun declaredGroupIsAdded() =
        assertEquals(listOf(AppCategory.GAMES, AppCategory.SAFETY), AppCategories.classify("Netra KBC", "Game", "An adaptive reasoning quiz", listOf("safety")))
    @Test fun nothingMatchedFallsBackToUtility() =
        assertEquals(listOf(AppCategory.UTILITY), AppCategories.classify("Foo", "", "Does a thing"))
}
