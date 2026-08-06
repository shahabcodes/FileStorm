package com.shahabcodes.filestorm

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.shahabcodes.filestorm.data.Prefs

class FileStormApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.shahabcodes.filestorm.data.FolderStyles.init(this)
        com.shahabcodes.filestorm.data.jobs.JobStore.init(this)
        com.shahabcodes.filestorm.data.Favorites.init(this)
        com.shahabcodes.filestorm.data.FolderLocks.init(this)
        com.shahabcodes.filestorm.data.StorageAnalyzer.init(this)
        com.shahabcodes.filestorm.data.AppStorageAnalyzer.init(this)
        com.shahabcodes.filestorm.data.DashboardPrefs.init(this)
        com.shahabcodes.filestorm.data.StorageInsights.init(this)
        com.shahabcodes.filestorm.data.audio.AudioPlayer.init(this)
        com.shahabcodes.filestorm.data.FolderViews.init(this)
        com.shahabcodes.filestorm.data.IconManager.reconcile(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
