package br.com.helpdev.velocimetroalerta.ads

internal fun canShowActivitiesEntryInterstitial(
    screenResumed: Boolean,
    hasShownInterstitial: Boolean,
    entryFlowActive: Boolean,
    adAvailable: Boolean
): Boolean = screenResumed && !hasShownInterstitial && entryFlowActive && adAvailable
