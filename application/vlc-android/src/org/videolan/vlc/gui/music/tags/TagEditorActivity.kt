package org.videolan.vlc.gui.music.tags

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.util.isExternalStorageManager
import org.videolan.tools.BitmapCache
import org.videolan.vlc.R
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Edits tags inside the audio files. Either a single track ([EXTRA_MEDIA_ID]) or every track of an
 * album ([EXTRA_ALBUM_ID]); album mode only offers the fields shared by the whole album.
 */
class TagEditorActivity : AppCompatActivity() {

    private class Target(val media: MediaWrapper, val file: File)

    private var targets by mutableStateOf<List<Target>?>(null)
    private var loadError by mutableStateOf<String?>(null)
    private var initial by mutableStateOf(TagEdit())
    private var coverUri by mutableStateOf<String?>(null)
    private var saving by mutableStateOf(false)
    private val albumMode get() = intent.hasExtra(EXTRA_ALBUM_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Content() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() = lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) { resolveTargets() }
        loadError = result.second
        val list = result.first
        targets = list
        if (!list.isNullOrEmpty()) {
            withContext(Dispatchers.IO) { readInitial(list) }.let { initial = it.first; coverUri = it.second }
        }
    }

    private fun resolveTargets(): Pair<List<Target>?, String?> {
        val ml = Medialibrary.getInstance()
        val medias: List<MediaWrapper> = if (albumMode) {
            ml.getAlbum(intent.getLongExtra(EXTRA_ALBUM_ID, 0L))?.tracks?.toList().orEmpty()
        } else listOfNotNull(ml.getMedia(intent.getLongExtra(EXTRA_MEDIA_ID, 0L)))
        if (medias.isEmpty()) return null to getString(R.string.tag_editor_not_found)
        val list = medias.map { m ->
            val uri = m.uri
            if (uri.scheme != "file" || uri.path == null) return null to getString(R.string.tag_editor_read_only)
            Target(m, File(uri.path!!))
        }
        if (!isExternalStorageManager()) return null to getString(R.string.tag_editor_needs_permission)
        return list to null
    }

    private fun readInitial(list: List<Target>): Pair<TagEdit, String?> {
        fun props(t: Target) = android.os.ParcelFileDescriptor.open(t.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {
            TagLib.getMetadata(it.detachFd(), false)?.propertyMap.orEmpty()
        }
        val first = props(list.first())
        val cover = list.first().media.artworkMrl
        fun shared(key: String): String {
            val v = first.first(key)
            return if (list.size == 1 || list.all { props(it).first(key) == v }) v else ""
        }
        return TagEdit(
            title = first.first("TITLE"), artist = first.first("ARTIST"),
            album = shared("ALBUM"), albumArtist = shared("ALBUMARTIST"), genre = shared("GENRE"),
            year = yearFromDate(shared("DATE")), trackNumber = first.first("TRACKNUMBER"), discNumber = first.first("DISCNUMBER"),
        ) to cover
    }

    private fun save(edit: TagEdit) {
        val list = targets ?: return
        saving = true
        lifecycleScope.launch {
            val failure = withContext(Dispatchers.IO) {
                var problem: String? = null
                for (t in list) {
                    val result = TagWriter.write(t.file, edit)
                    if (result is TagResult.Failure) { problem = "${t.file.name}: ${result.reason}"; break }
                }
                problem
            }
            if (failure != null) {
                saving = false
                Toast.makeText(this@TagEditorActivity, failure, Toast.LENGTH_LONG).show()
                // Files already written stay edited; refresh so the library matches them.
            }
            withContext(Dispatchers.IO) {
                val ml = Medialibrary.getInstance()
                list.mapNotNull { it.file.parent }.distinct().forEach { ml.reload(it) }
                BitmapCache.clear()
            }
            if (failure == null) {
                Toast.makeText(this@TagEditorActivity, R.string.tag_editor_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (albumMode) R.string.tag_editor_album_title else R.string.tag_editor_title)) }) }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    val list = targets
                    when {
                        loadError != null -> Problem(loadError!!)
                        list == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                        else -> Form(list.size)
                    }
                }
            }
        }
    }

    @Composable
    private fun Problem(message: String) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message)
            if (message == getString(R.string.tag_editor_needs_permission)) {
                Button(onClick = {
                    startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                }) { Text(stringResource(R.string.tag_editor_grant_access)) }
            }
        }
    }

    @Composable
    private fun Form(count: Int) {
        var title by remember(initial) { mutableStateOf(initial.title.orEmpty()) }
        var artist by remember(initial) { mutableStateOf(initial.artist.orEmpty()) }
        var album by remember(initial) { mutableStateOf(initial.album.orEmpty()) }
        var albumArtist by remember(initial) { mutableStateOf(initial.albumArtist.orEmpty()) }
        var genre by remember(initial) { mutableStateOf(initial.genre.orEmpty()) }
        var year by remember(initial) { mutableStateOf(initial.year.orEmpty()) }
        var track by remember(initial) { mutableStateOf(initial.trackNumber.orEmpty()) }
        var disc by remember(initial) { mutableStateOf(initial.discNumber.orEmpty()) }
        var cover by remember { mutableStateOf<CoverEdit>(CoverEdit.Keep) }
        var pickedPreview by remember { mutableStateOf<Bitmap?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) lifecycleScope.launch {
                val data = withContext(Dispatchers.IO) { loadCover(uri) }
                if (data != null) { cover = CoverEdit.Replace(data, "image/jpeg"); pickedPreview = BitmapFactory.decodeByteArray(data, 0, data.size) }
                else Toast.makeText(this@TagEditorActivity, R.string.tag_editor_bad_image, Toast.LENGTH_SHORT).show()
            }
        }
        // Only fields the user actually changed are written, so an album edit never overwrites per-track titles.
        fun changed(now: String, was: String?) = if (now != was.orEmpty()) now else null
        fun build() = TagEdit(
            title = if (albumMode) null else changed(title, initial.title),
            artist = if (albumMode) null else changed(artist, initial.artist),
            album = changed(album, initial.album), albumArtist = changed(albumArtist, initial.albumArtist),
            genre = changed(genre, initial.genre), year = changed(year, initial.year),
            trackNumber = if (albumMode) null else changed(track, initial.trackNumber),
            discNumber = if (albumMode) null else changed(disc, initial.discNumber),
            cover = cover,
        )
        val problem = build().validate()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (albumMode) Text(stringResource(R.string.tag_editor_album_hint, count), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val model: Any? = when (cover) {
                    CoverEdit.Remove -> null
                    else -> pickedPreview ?: coverUri?.let { Uri.parse(it) }
                }
                AsyncImage(model, null, Modifier.size(96.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.tag_editor_change_cover)) }
                    TextButton(onClick = { cover = CoverEdit.Remove; pickedPreview = null }) { Text(stringResource(R.string.tag_editor_remove_cover)) }
                }
            }
            if (!albumMode) {
                Field(R.string.tag_title, title) { title = it }
                Field(R.string.tag_artist, artist) { artist = it }
            }
            Field(R.string.tag_album, album) { album = it }
            Field(R.string.tag_album_artist, albumArtist) { albumArtist = it }
            Field(R.string.tag_genre, genre) { genre = it }
            Field(R.string.tag_year, year) { year = it }
            if (!albumMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(R.string.tag_track, track, Modifier.weight(1f)) { track = it }
                    Field(R.string.tag_disc, disc, Modifier.weight(1f)) { disc = it }
                }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { finish() }) { Text(stringResource(android.R.string.cancel)) }
                Button(enabled = !saving && problem == null, onClick = { save(build()) }) {
                    Text(stringResource(R.string.tag_editor_save))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun Field(label: Int, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
        OutlinedTextField(value, onChange, modifier, label = { Text(stringResource(label)) }, singleLine = true)
    }

    /** Decodes the picked image and re-encodes it as a JPEG of at most 1200 px so embedded covers stay small. */
    private fun loadCover(uri: Uri): ByteArray? = try {
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val scale = 1200f / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    } catch (e: Exception) { null }

    companion object {
        private const val EXTRA_MEDIA_ID = "media_id"
        private const val EXTRA_ALBUM_ID = "album_id"

        fun editTrack(context: Context, mediaId: Long) =
            context.startActivity(Intent(context, TagEditorActivity::class.java).putExtra(EXTRA_MEDIA_ID, mediaId))

        fun editAlbum(context: Context, albumId: Long) =
            context.startActivity(Intent(context, TagEditorActivity::class.java).putExtra(EXTRA_ALBUM_ID, albumId))
    }
}
