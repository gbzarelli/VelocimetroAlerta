package br.com.helpdev.velocimetroalerta.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitiesInterstitialPolicyTest {
    @Test
    fun entryInterstitialWaitsUntilItIsLoadedOnTheInitialListScreen() {
        assertTrue(
            canShowActivitiesEntryInterstitial(
                screenResumed = true,
                hasShownInterstitial = false,
                entryFlowActive = true,
                adAvailable = true
            )
        )
    }

    @Test
    fun entryInterstitialIsSkippedAfterNavigatingToAnActivityDetail() {
        assertFalse(
            canShowActivitiesEntryInterstitial(
                screenResumed = true,
                hasShownInterstitial = false,
                entryFlowActive = false,
                adAvailable = true
            )
        )
    }

    @Test
    fun entryInterstitialDoesNotShowTwiceOrWhileTheListIsPaused() {
        assertFalse(
            canShowActivitiesEntryInterstitial(
                screenResumed = false,
                hasShownInterstitial = false,
                entryFlowActive = true,
                adAvailable = true
            )
        )
        assertFalse(
            canShowActivitiesEntryInterstitial(
                screenResumed = true,
                hasShownInterstitial = true,
                entryFlowActive = true,
                adAvailable = true
            )
        )
    }
}
