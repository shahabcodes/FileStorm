package com.shahaabapps.filestorm

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.shahaabapps.filestorm.data.Prefs

class FileStormApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.shahaabapps.filestorm.data.FolderStyles.init(this)
        com.shahaabapps.filestorm.data.jobs.JobStore.init(this)
        com.shahaabapps.filestorm.data.Favorites.init(this)
        com.shahaabapps.filestorm.data.FolderLocks.init(this)
        com.shahaabapps.filestorm.data.StorageAnalyzer.init(this)
        com.shahaabapps.filestorm.data.DashboardPrefs.init(this)
        com.shahaabapps.filestorm.data.StorageInsights.init(this)
        com.shahaabapps.filestorm.data.audio.AudioPlayer.init(this)
        com.shahaabapps.filestorm.data.vault.VaultPrefs.init(this)
        com.shahaabapps.filestorm.data.vault.VaultLog.init(this)
        com.shahaabapps.filestorm.data.vault.VaultMedia.init(this)
        com.shahaabapps.filestorm.data.FolderViews.init(this)
        com.shahaabapps.filestorm.data.IconManager.reconcile(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
