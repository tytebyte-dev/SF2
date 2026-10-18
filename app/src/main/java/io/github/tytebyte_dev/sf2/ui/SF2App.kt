package io.github.tytebyte_dev.sf2.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

enum class Access { NONE, PARTIAL, FULL }

object Perms {
    fun request(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(ctx: Context): Access {
        fun ok(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 -> when {
                ok(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
                Build.VERSION.SDK_INT >= 34 && ok(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
                else -> Access.NONE
            }
            else -> if (ok(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.FULL else Access.NONE
        }
    }
}

@Composable
fun SF2App(vm: AppViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var access by remember { mutableStateOf(Perms.access(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = Perms.access(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = Perms.access(ctx)
    }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        vm.onTrashResult(res.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.trashRequests.collect { trashLauncher.launch(it) } }

    BackHandler(enabled = state.screen != Screen.Home) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
        when (val s = state.screen) {
            Screen.Home -> HomeScreen(
                access = access,
                sensitivity = state.sensitivity,
                onSensitivity = vm::setSensitivity,
                onRequestAccess = { permLauncher.launch(Perms.request()) },
                onOpenSettings = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)),
                    )
                },
                onScan = vm::startScan,
            )
            Screen.Scanning -> ScanningScreen(state.progressDone, state.progressTotal, onCancel = vm::cancelScan)
            Screen.Results -> ResultsScreen(
                state = state,
                onBack = vm::back,
                onSensitivity = vm::setSensitivity,
                onOpen = vm::openGroup,
                onResetDismissed = vm::resetDismissed,
            )
            is Screen.Group -> {
                val g = state.groups.firstOrNull { it.key == s.key }
                if (g == null) {
                    LaunchedEffect(s.key) { vm.back() }
                } else {
                    GroupScreen(
                        group = g,
                        selected = state.selected,
                        onBack = vm::back,
                        onToggle = { vm.toggleSelect(g.key, it) },
                        onOpenViewer = { vm.openViewer(g.key, it) },
                        onSuggest = { vm.suggestSelection(g.key) },
                        onClear = { vm.clearSelection(g.key) },
                        onDismiss = { vm.dismissGroup(g.key) },
                        onTrash = { vm.requestTrash(g.key) },
                    )
                }
            }
            is Screen.Viewer -> {
                val g = state.groups.firstOrNull { it.key == s.key }
                if (g == null) {
                    LaunchedEffect(s.key) { vm.back() }
                } else {
                    ViewerScreen(
                        group = g,
                        index = s.index,
                        selected = state.selected,
                        onIndex = { vm.openViewer(g.key, it) },
                        onToggle = { vm.toggleSelect(g.key, it) },
                        onBack = vm::back,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        )
    }
    }
}
