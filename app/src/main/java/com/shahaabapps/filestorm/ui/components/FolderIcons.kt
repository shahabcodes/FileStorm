package com.shahaabapps.filestorm.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Optional glyphs a folder can wear instead of the default folder shape. */
object FolderIcons {

    data class Choice(val key: String, val label: String, val icon: ImageVector)

    val all = listOf(
        Choice("folder", "Default", Icons.Rounded.Folder),
        Choice("star", "Star", Icons.Rounded.Star),
        Choice("heart", "Heart", Icons.Rounded.Favorite),
        Choice("camera", "Camera", Icons.Rounded.CameraAlt),
        Choice("image", "Photos", Icons.Rounded.Image),
        Choice("movie", "Movies", Icons.Rounded.Movie),
        Choice("music", "Music", Icons.Rounded.MusicNote),
        Choice("audio", "Audio", Icons.Rounded.AudioFile),
        Choice("docs", "Documents", Icons.Rounded.Description),
        Choice("download", "Downloads", Icons.Rounded.Download),
        Choice("work", "Work", Icons.Rounded.Work),
        Choice("business", "Business", Icons.Rounded.Business),
        Choice("school", "School", Icons.Rounded.School),
        Choice("code", "Code", Icons.Rounded.Code),
        Choice("art", "Art", Icons.Rounded.Brush),
        Choice("games", "Games", Icons.Rounded.SportsEsports),
        Choice("shopping", "Shopping", Icons.Rounded.ShoppingBag),
        Choice("travel", "Travel", Icons.Rounded.Flight),
        Choice("home", "Home", Icons.Rounded.Home),
        Choice("pets", "Pets", Icons.Rounded.Pets),
        Choice("backup", "Backup", Icons.Rounded.Backup),
        Choice("lock", "Private", Icons.Rounded.Lock),
    )

    fun iconFor(key: String?): ImageVector =
        all.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Folder
}
