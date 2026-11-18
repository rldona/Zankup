package com.zankup.android.activity

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.ActivityDetail
import com.zankup.android.api.model.ActivityEdit
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ListRow
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.color
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.R.string as UiStrings
import com.zankup.android.ui.family
import com.zankup.android.ui.sportName
import com.zankup.android.ui.sportOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Editing an activity (OS-1135): name, description, sport, gear (or none), a strength workout's type and
 * its photos; "Delete activity" asks first. Only what changed is sent.
 */
@Composable
internal fun EditSheet(a: ActivityDetail, model: ActivityModel, onDone: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(a.name.orEmpty()) }
    var description by remember { mutableStateOf(a.description.orEmpty()) }
    var sport by remember { mutableStateOf(sportOf(a.sport)) }
    var gearId by remember { mutableStateOf(a.gear?.id ?: 0) }
    var workout by remember { mutableStateOf(a.workoutType) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf(false) }
    val gear by model.gear.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { model.gear.refresh() }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { error = !model.addPhoto(uri) }
        }

    ZankupSheet(onDismiss = onDone) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.edit), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, {
                name = it
            }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, {
                description = it
            }, label = { Text(stringResource(R.string.description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.sport), style = MaterialTheme.typography.titleSmall)
            ChipPicker(Sport.entries, sport, { stringResource(sportName(it)) }, { sport = it })
            Text(stringResource(R.string.gear), style = MaterialTheme.typography.titleSmall)
            val options = listOf(0) + gear?.data.orEmpty().filter { !it.retired || it.id == a.gear?.id }.map { it.id }
            ChipPicker(options, gearId, { id ->
                gear?.data?.firstOrNull { it.id == id }?.name
                    ?: stringResource(R.string.no_gear)
            }, { gearId = it })
            if (sport == Sport.STRENGTH) {
                Text(stringResource(R.string.workout_type), style = MaterialTheme.typography.titleSmall)
                val types = ActivityEdit.WorkoutType.entries
                ChipPicker(
                    types.map {
                        it.value
                    },
                    workout ?: "",
                    { workoutName(it) },
                    {
                        workout =
                            it
                    }
                )
            }
            Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleSmall)
            a.photoList.forEach { photo ->
                ListRow(photo.caption ?: "#${photo.id}", icon = ZIcon.Delete, onClick = { removing = photo.id })
            }
            OutlinedButton(onClick = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text(stringResource(R.string.add_photo))
            }
            if (error) Text(stringResource(R.string.save_failed), color = ZankupTheme.colors.formNegative)
            SheetButtons(onCancel = onDone) {
                Button(onClick = {
                    val edit =
                        ActivityEdit(
                            name = name.trim().takeIf { it != a.name.orEmpty() },
                            description = description.takeIf { it != a.description.orEmpty() },
                            sport = ActivityEdit.Sport.entries.firstOrNull { it.value == sport.key }?.takeIf {
                                sport !=
                                    sportOf(a.sport)
                            },
                            gearId = gearId.takeIf { it != (a.gear?.id ?: 0) },
                            workoutType = ActivityEdit.WorkoutType.entries.firstOrNull { it.value == workout }?.takeIf {
                                workout !=
                                    a.workoutType
                            }
                        )
                    scope.launch {
                        if (model.save(edit) is ApiResult.Ok) onDone() else error = true
                    }
                }) { Text(stringResource(R.string.save)) }
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text(stringResource(R.string.delete_activity), color = ZankupTheme.colors.formNegative)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_activity)) },
            text = { Text(stringResource(R.string.delete_activity_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (model.delete()) {
                            onDone()
                            onDeleted()
                        } else {
                            error = true
                        }
                        confirmDelete = false
                    }
                }) { Text(stringResource(R.string.delete), color = ZankupTheme.colors.formNegative) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    removing?.let { photoId ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_photo)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        model.removePhoto(photoId)
                        removing = null
                    }
                }) { Text(stringResource(R.string.delete), color = ZankupTheme.colors.formNegative) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** Sharing (OS-1135): a link to the activity on the web app, valid a week, through Android's share sheet. */
@Composable
internal fun ShareSheet(a: ActivityDetail, model: ActivityModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val title = a.name ?: stringResource(sportName(sportOf(a.sport)))
    val chooser = stringResource(R.string.share)
    ZankupSheet(onDismiss = onDone) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.share), style = MaterialTheme.typography.titleLarge)
            val image = rememberShareImage(a, model, onFailed = { failed = true }, onDone = onDone)
            ListRow(
                stringResource(R.string.share_image),
                subtitle = stringResource(R.string.share_image_note),
                icon = ZIcon.Image,
                onClick = image
            )
            ListRow(
                stringResource(R.string.share_link),
                subtitle = stringResource(R.string.share_link_note),
                icon = ZIcon.Link,
                onClick = {
                    scope.launch {
                        val link = model.shareLink()
                        if (link == null) {
                            failed = true
                        } else {
                            val send = Intent(
                                Intent.ACTION_SEND
                            ).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$title\n$link")
                            context.startActivity(Intent.createChooser(send, chooser))
                            onDone()
                        }
                    }
                }
            )
            if (failed) Text(stringResource(R.string.share_failed), color = ZankupTheme.colors.formNegative)
        }
    }
}

/**
 * Sharing the image (OS-919), as the React Native app: the shared view of the activity drawn on the
 * 1080 × 1350 card in the theme's colours, saved in the cache and handed to Android's share sheet.
 * Nothing leaves the phone until the person picks where to send it.
 */
@Composable
private fun rememberShareImage(
    a: ActivityDetail,
    model: ActivityModel,
    onFailed: () -> Unit,
    onDone: () -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val formats = com.zankup.android.ui.LocalFormats.current
    val colors = ZankupTheme.colors
    val sport = sportOf(a.sport)
    val fallback = stringResource(sportName(sport))
    val chooser = stringResource(R.string.share_image)
    val labels = listOf(
        stringResource(com.zankup.android.ui.R.string.figure_distance),
        stringResource(com.zankup.android.ui.R.string.figure_time),
        stringResource(if (sport.usesPace) UiStrings.figure_pace else UiStrings.figure_speed),
        stringResource(R.string.share_ascent)
    )
    val colours = CardColours(
        colors.bg.toArgb(),
        colors.fg.toArgb(),
        colors.muted.toArgb(),
        sport.family.color.toArgb()
    )
    return {
        scope.launch {
            val shared = model.shared()
            if (shared == null) {
                onFailed()
                return@launch
            }
            val moving = shared.movingS ?: shared.durationS
            val distance = shared.distanceM?.takeIf { it > 0 }
            val figures = listOfNotNull(
                distance?.let { CardFigure(labels[0], formats.distance(it)) },
                CardFigure(labels[1], formats.duration(moving)),
                distance?.takeIf { moving > 0 }?.let { CardFigure(labels[2], formats.speedFor(sport, it, moving)) },
                shared.ascentM?.takeIf { it > 0 }?.let { CardFigure(labels[3], formats.elevation(it)) }
            )
            val day = runCatching {
                java.time.LocalDate.parse(shared.day).format(
                    java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
                        .withLocale(formats.locale)
                )
            }.getOrDefault(shared.day)
            val route = shared.route.orEmpty().map { stretch ->
                stretch.mapNotNull { point ->
                    val lat = point.getOrNull(0)?.jsonPrimitive?.doubleOrNull
                    val lon = point.getOrNull(1)?.jsonPrimitive?.doubleOrNull
                    if (lat != null && lon != null) lat to lon else null
                }
            }
            val file = model.shareImage(CardContent(shared.name ?: fallback, day, figures, route), colours)
            if (file == null) {
                onFailed()
                return@launch
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, chooser))
            onDone()
        }
    }
}
