package com.matan.vault

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.matan.vault.ui.DetailScreen
import com.matan.vault.ui.EditScreen
import com.matan.vault.ui.Gold
import com.matan.vault.ui.HomeScreen
import com.matan.vault.ui.Navy
import com.matan.vault.ui.NavyDeep
import com.matan.vault.ui.PageViewer
import com.matan.vault.ui.TimelineScreen
import com.matan.vault.ui.VaultTheme
import com.matan.vault.ui.rememberSources

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_DOC_ID = "doc_id"
        private const val RELOCK_AFTER_MS = 60_000L
    }

    private val vm: VaultViewModel by viewModels()
    private val locked = mutableStateOf(true)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // no screenshots, no preview in the recent-apps switcher
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState != null) locked.value = savedInstanceState.getBoolean("locked", true)
        handleIntent(intent)

        setContent {
            VaultTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked.value) LockScreen { authenticate() } else AppNav(vm)
                }
            }
        }
        if (locked.value) authenticate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (!locked.value && stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > RELOCK_AFTER_MS) {
            locked.value = true
            authenticate()
        }
    }

    fun lockNow() {
        locked.value = true
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) vm.pendingShare.value = uris
        val docId = intent.getLongExtra(EXTRA_DOC_ID, -1L)
        if (docId > 0) vm.pendingOpenDoc.value = docId
        intent.action = null
    }

    private fun authenticate() {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(auth)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // no screen lock on the phone: nothing to authenticate against
            locked.value = false
            vm.message.value = "כדאי להגדיר נעילת מסך בטלפון כדי שהכספת תהיה מוגנת"
            return
        }
        val prompt = BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked.value = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("פתיחת הכספת")
                .setSubtitle("טביעת אצבע, פנים או קוד הטלפון")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(NavyDeep).systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Navy),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(52.dp), tint = Gold) }
            Spacer(Modifier.height(24.dp))
            Text("הכספת נעולה", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("המסמכים מוצפנים על המכשיר", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(32.dp))
            Button(onClick = onUnlock, modifier = Modifier.padding(horizontal = 32.dp)) { Text("פתח") }
        }
    }
}

@Composable
private fun AppNav(vm: VaultViewModel) {
    val nav = rememberNavController()
    val documents by vm.documents.collectAsState()
    val covers by vm.covers.collectAsState()
    val edit by vm.edit.collectAsState()
    val pendingShare by vm.pendingShare.collectAsState()
    val pendingOpen by vm.pendingOpenDoc.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val activity = androidx.compose.ui.platform.LocalContext.current as? MainActivity

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val startImport: (List<Uri>) -> Unit = { uris ->
        vm.startNew(uris)
        nav.navigate("edit") { launchSingleTop = true }
    }
    val sources = rememberSources(startImport)

    LaunchedEffect(pendingShare) {
        if (pendingShare.isNotEmpty()) {
            val uris = pendingShare
            vm.pendingShare.value = emptyList()
            startImport(uris)
        }
    }
    LaunchedEffect(pendingOpen) {
        pendingOpen?.let {
            vm.pendingOpenDoc.value = null
            nav.navigate("detail/$it") { launchSingleTop = true }
        }
    }
    LaunchedEffect(message) {
        message?.let {
            vm.message.value = null
            snackbar.showSnackbar(it)
        }
    }

    NavHost(nav, startDestination = "home", modifier = Modifier.systemBarsPadding()) {
        composable("home") {
            HomeScreen(
                documents = documents,
                covers = covers,
                importing = edit?.busy == true,
                snackbar = snackbar,
                onOpen = { nav.navigate("detail/$it") },
                onScan = sources.scan,
                onFiles = sources.pickFiles,
                onTimeline = { nav.navigate("timeline") },
                onLock = { activity?.lockNow() },
            )
        }
        composable("edit") {
            val st = edit
            if (st == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                EditScreen(
                    vm = vm,
                    st = st,
                    owners = documents.map { it.owner.trim() }.filter { it.isNotEmpty() }.distinct(),
                    onDone = { id ->
                        nav.popBackStack()
                        nav.navigate("detail/$id") { launchSingleTop = true }
                    },
                    onCancel = { vm.cancelEdit(); nav.popBackStack() },
                )
            }
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            DetailScreen(
                vm = vm,
                id = id,
                onBack = { nav.popBackStack() },
                onEdit = { vm.startEdit(id); nav.navigate("edit") },
                onViewPage = { nav.navigate("viewer/$id/$it") },
                onRenew = {
                    documents.firstOrNull { it.id == id }?.let { vm.renew(it) }
                    nav.popBackStack()
                    nav.navigate("edit")
                },
            )
        }
        composable(
            "viewer/{id}/{page}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("page") { type = NavType.IntType },
            ),
        ) { entry ->
            PageViewer(
                vm = vm,
                id = entry.arguments?.getLong("id") ?: 0L,
                start = entry.arguments?.getInt("page") ?: 0,
                onBack = { nav.popBackStack() },
            )
        }
        composable("timeline") {
            TimelineScreen(documents, covers, onOpen = { nav.navigate("detail/$it") }, onBack = { nav.popBackStack() })
        }
    }
}
