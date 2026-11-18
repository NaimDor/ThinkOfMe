package com.thinkofme.core.data

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.FirebaseApp

/**
 * Usage analytics (Firebase Analytics). Counts what people do, never what they write:
 * parameters are kinds, yes/no flags and counts, never titles, notes, names, links or ids.
 * The same event names are used on iOS (App/UI/Telemetry.swift), see docs/ANALYTICS.md.
 *
 * Does nothing in demo builds (no Firebase config).
 */
object Telemetry {
    fun event(name: String, vararg params: Pair<String, Any>) {
        // Demo builds never initialise Firebase.
        if (runCatching { FirebaseApp.getInstance() }.isFailure) return
        val bundle = Bundle()
        for ((key, value) in params) when (value) {
            is Boolean -> bundle.putLong(key, if (value) 1 else 0)
            is Int -> bundle.putLong(key, value.toLong())
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            else -> bundle.putString(key, value.toString())
        }
        runCatching { Firebase.analytics.logEvent(name, bundle) }
    }

    /**
     * A per-user attribute for slicing reports, e.g. how many people someone has.
     * Counts go in buckets ("0", "1", "2", "3-5", "6-10", "11+"), never exact lists.
     */
    fun count(name: String, value: Int) {
        if (runCatching { FirebaseApp.getInstance() }.isFailure) return
        runCatching { Firebase.analytics.setUserProperty(name, bucket(value)) }
    }

    fun bucket(n: Int): String = when {
        n < 3 -> n.coerceAtLeast(0).toString()
        n <= 5 -> "3-5"
        n <= 10 -> "6-10"
        else -> "11+"
    }

    fun screen(name: String) = event(FirebaseAnalytics.Event.SCREEN_VIEW, FirebaseAnalytics.Param.SCREEN_NAME to name, FirebaseAnalytics.Param.SCREEN_CLASS to name)
}
