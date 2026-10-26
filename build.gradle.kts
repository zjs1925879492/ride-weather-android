plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

// Optional build output relocation for Windows JVM workers on non-ASCII workspaces.
providers.environmentVariable("RIDEWEATHER_BUILD_ROOT").orNull?.let { directory ->
    require(directory.all { it.code < 128 }) { "RIDEWEATHER_BUILD_ROOT must be an ASCII path" }
    allprojects {
        val module = path.trim(':').replace(':', '-').ifBlank { "root" }
        layout.buildDirectory.set(file("$directory/$module"))
    }
}
