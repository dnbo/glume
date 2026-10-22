package com.android.glume

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.android.glume.di.appModule
import com.android.glume.ui.app.ImageStats
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GlumeApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GlumeApp)
            modules(appModule)
        }
    }

    /** Bounded caches per CLAUDE.md: ~22 % of app memory, 512 MB on disk. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.22).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(IMAGE_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .eventListener(ImageStats)
            .build()

    companion object {
        const val IMAGE_CACHE_DIR = "image_cache"
    }
}
