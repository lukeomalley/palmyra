package xyz.lukeomalley.palmyra

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.util.Locale

data class AppEntry(
    val label: String,
    val packageName: String,
    val glyph: Int = 0,
)

object AppCatalog {
    val favorites =
        listOf(
            AppEntry("YouTube TV", "com.google.android.youtube.tvunplugged", R.drawable.ic_youtubetv),
            AppEntry("Jellyfin", "org.jellyfin.androidtv", R.drawable.ic_jellyfin),
            AppEntry("Netflix", "com.netflix.ninja", R.drawable.ic_netflix),
            AppEntry("Amazon Prime", "com.amazon.amazonvideo.livingroom", R.drawable.ic_primevideo),
            AppEntry("YouTube", "com.google.android.youtube.tv", R.drawable.ic_youtube),
            AppEntry("Hulu", "com.hulu.livingroomplus", R.drawable.ic_hulu),
            AppEntry("HBO Max", "com.wbd.stream", R.drawable.ic_hbo),
            AppEntry("Apple TV", "com.apple.atve.androidtv.appletv", R.drawable.ic_appletv),
            AppEntry("Twitch", "tv.twitch.android.app", R.drawable.ic_twitch),
            AppEntry("Spotify", "com.spotify.tv.android", R.drawable.ic_spotify),
            AppEntry("Play Store", "com.android.vending", R.drawable.ic_playstore),
        )

    fun installed(available: (String) -> Boolean) = favorites.filter { available(it.packageName) }

    fun installed(pm: PackageManager): List<AppEntry> {
        val packages = activities(pm).mapTo(mutableSetOf()) { it.activityInfo.packageName }
        return installed { it in packages }
    }

    fun launchIntent(
        pm: PackageManager,
        pkg: String,
    ): Intent? = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    fun all(pm: PackageManager): List<AppEntry> =
        activities(pm)
            .filter { it.activityInfo.packageName != "xyz.lukeomalley.palmyra" }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }

    @Suppress("DEPRECATION")
    private fun activities(pm: PackageManager): List<ResolveInfo> {
        val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val normal = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(leanback, 0) + pm.queryIntentActivities(normal, 0)
    }
}
