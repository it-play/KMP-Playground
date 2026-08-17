package com.amond.kmpbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.amond.kmpbook.audio.DesktopBackgroundMusicPlayer
import com.amond.kmpbook.domain.data.DesktopInstrumentPackParser
import com.amond.kmpbook.domain.data.InstrumentCatalogSnapshot
import com.amond.kmpbook.domain.data.InstrumentPack
import com.amond.kmpbook.presentation.settings.AudioSettings
import com.amond.kmpbook.presentation.settings.DesktopAudioSettingsPersistence
import com.amond.kmpbook.presentation.simulator.SimulatorViewModel
import com.amond.kmpbook.ui.components.LoadingFinancialFact
import com.amond.kmpbook.ui.screens.opening.OpeningScreen
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketDesignSystem
import com.amond.kmpbook.ui.theme.MarketLayout
import com.amond.kmpbook.ui.theme.MarketSimulatorTheme
import com.amond.kmpbook.ui.theme.MarketType
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import kmpbook.composeapp.generated.resources.Res
import kmpbook.composeapp.generated.resources.app_icon_market_ledger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

private const val BASE_INSTRUMENT_CATALOG_PATH: String =
    "files/instruments/market_instrument_catalog_v6.json"
private const val BASE_INSTRUMENT_SOURCE_ID: String = "builtin:base"
private const val BACKGROUND_MUSIC_RECOVERY_DELAY_MILLIS: Long = 3_000L
private const val BACKGROUND_MUSIC_RECOVERY_RESET_MILLIS: Long = 60_000L
private const val MAX_BACKGROUND_MUSIC_RECOVERY_ATTEMPTS: Int = 3

private fun backgroundMusicErrorMessage(error: Throwable): String =
    error.message?.take(300)?.takeIf(String::isNotBlank)
        ?: "배경음악 재생 장치를 준비하지 못했습니다."

private suspend fun loadBaseInstrumentCatalog(): InstrumentCatalogSnapshot {
    val bytes = withContext(Dispatchers.IO) {
        Res.readBytes(BASE_INSTRUMENT_CATALOG_PATH)
    }
    return withContext(Dispatchers.Default) {
        val pack = DesktopInstrumentPackParser.parse(
            bytes = bytes,
            sourceId = BASE_INSTRUMENT_SOURCE_ID,
            maxInstruments = InstrumentPack.MAX_INSTRUMENTS,
        )
        InstrumentCatalogSnapshot.fromPacks(listOf(pack))
    }
}

fun main() {
    nucleusApplication(backend = NucleusBackend.Tao) {
        var escapeRequest by remember { mutableIntStateOf(0) }
        var debugConsoleToggleRequest by remember { mutableIntStateOf(0) }
        var isDebugConsoleAvailable by remember { mutableStateOf(false) }
        var isGravePressed by remember { mutableStateOf(false) }
        var isEscapePressed by remember { mutableStateOf(false) }
        var isExitBlocked by remember { mutableStateOf(false) }
        var bootstrapAttempt by remember { mutableIntStateOf(0) }
        var bootstrapStage by remember { mutableIntStateOf(0) }
        var bootstrapStatus by remember { mutableStateOf("기본 종목 카탈로그를 읽고 있습니다.") }
        var bootstrapError by remember { mutableStateOf<String?>(null) }
        var baseCatalog by remember { mutableStateOf<InstrumentCatalogSnapshot?>(null) }
        var simulatorViewModel by remember { mutableStateOf<SimulatorViewModel?>(null) }
        var appInitialReady by remember { mutableStateOf(false) }
        var openingComplete by remember { mutableStateOf(false) }
        var hasEnteredSlideshow by remember { mutableStateOf(false) }
        var backgroundMusicAttempt by remember { mutableIntStateOf(0) }
        var isBackgroundMusicReady by remember { mutableStateOf(false) }
        var backgroundMusicError by remember { mutableStateOf<String?>(null) }
        var isBackgroundMusicSkipped by remember { mutableStateOf(false) }
        var backgroundMusicRecoveryAttempts by remember { mutableIntStateOf(0) }
        val audioSettingsPersistence = remember { DesktopAudioSettingsPersistence() }
        var audioSettings by remember { mutableStateOf(AudioSettings()) }
        var areAudioSettingsLoaded by remember { mutableStateOf(false) }
        val effectiveMusicVolume = if (!areAudioSettingsLoaded) {
            null
        } else if (audioSettings.muted) {
            0f
        } else {
            (audioSettings.masterVolume * audioSettings.musicVolume)
                .toFloat()
                .coerceIn(0f, 1f)
        }
        val latestEffectiveMusicVolume by rememberUpdatedState(effectiveMusicVolume)
        val backgroundMusicPlayer = remember { DesktopBackgroundMusicPlayer() }
        val windowState = rememberWindowState(
            width = MarketLayout.defaultWindowWidth,
            height = MarketLayout.defaultWindowHeight,
            position = WindowPosition(Alignment.Center),
        )
        DisposableEffect(backgroundMusicPlayer) {
            onDispose(backgroundMusicPlayer::close)
        }
        DisposableEffect(audioSettingsPersistence) {
            onDispose(audioSettingsPersistence::close)
        }
        LaunchedEffect(audioSettingsPersistence) {
            audioSettings = audioSettingsPersistence.load()
            areAudioSettingsLoaded = true
        }
        LaunchedEffect(audioSettingsPersistence, audioSettings, areAudioSettingsLoaded) {
            if (!areAudioSettingsLoaded) return@LaunchedEffect
            audioSettingsPersistence.scheduleSave(audioSettings)
        }
        LaunchedEffect(backgroundMusicPlayer) {
            backgroundMusicPlayer.playbackErrors.collect { error ->
                isBackgroundMusicReady = false
                backgroundMusicError = error
            }
        }
        LaunchedEffect(
            hasEnteredSlideshow,
            backgroundMusicAttempt,
            areAudioSettingsLoaded,
            isBackgroundMusicSkipped,
        ) {
            if (
                !hasEnteredSlideshow ||
                !areAudioSettingsLoaded ||
                isBackgroundMusicSkipped
            ) {
                return@LaunchedEffect
            }
            isBackgroundMusicReady = false
            backgroundMusicError = null
            try {
                backgroundMusicPlayer.prepare()
                if (isBackgroundMusicSkipped) {
                    backgroundMusicPlayer.reset()
                    return@LaunchedEffect
                }
                val initialVolume = checkNotNull(latestEffectiveMusicVolume) {
                    "오디오 설정이 아직 준비되지 않았습니다."
                }
                backgroundMusicPlayer.startOrUpdateVolume(initialVolume)
                if (isBackgroundMusicSkipped) {
                    backgroundMusicPlayer.reset()
                    return@LaunchedEffect
                }
                isBackgroundMusicReady = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                backgroundMusicError = backgroundMusicErrorMessage(error)
            } catch (error: Exception) {
                backgroundMusicError = backgroundMusicErrorMessage(error)
            }
        }
        LaunchedEffect(backgroundMusicPlayer, isBackgroundMusicSkipped) {
            if (!isBackgroundMusicSkipped) return@LaunchedEffect
            isBackgroundMusicReady = false
            try {
                backgroundMusicPlayer.reset()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: LinkageError) {
                // The user chose to continue without audio, so teardown errors stay non-fatal.
            } catch (_: Exception) {
                // The user chose to continue without audio, so teardown errors stay non-fatal.
            }
        }
        LaunchedEffect(
            isBackgroundMusicReady,
            effectiveMusicVolume,
        ) {
            val volume = effectiveMusicVolume ?: return@LaunchedEffect
            if (!isBackgroundMusicReady) return@LaunchedEffect
            try {
                backgroundMusicPlayer.startOrUpdateVolume(volume)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                isBackgroundMusicReady = false
                backgroundMusicError = backgroundMusicErrorMessage(error)
            } catch (error: Exception) {
                isBackgroundMusicReady = false
                backgroundMusicError = backgroundMusicErrorMessage(error)
            }
        }
        LaunchedEffect(
            openingComplete,
            backgroundMusicError,
            isBackgroundMusicSkipped,
            backgroundMusicRecoveryAttempts,
        ) {
            if (
                !openingComplete ||
                backgroundMusicError == null ||
                isBackgroundMusicSkipped ||
                backgroundMusicRecoveryAttempts >= MAX_BACKGROUND_MUSIC_RECOVERY_ATTEMPTS
            ) {
                return@LaunchedEffect
            }
            delay(BACKGROUND_MUSIC_RECOVERY_DELAY_MILLIS)
            backgroundMusicError = null
            backgroundMusicRecoveryAttempts += 1
            backgroundMusicAttempt += 1
        }
        LaunchedEffect(openingComplete, isBackgroundMusicReady) {
            if (!openingComplete || !isBackgroundMusicReady) return@LaunchedEffect
            delay(BACKGROUND_MUSIC_RECOVERY_RESET_MILLIS)
            backgroundMusicRecoveryAttempts = 0
        }
        DecoratedWindow(
            onCloseRequest = { if (!isExitBlocked) exitApplication() },
            title = "${MarketDesignSystem.NAME} · Stock Simulator",
            icon = painterResource(Res.drawable.app_icon_market_ledger),
            state = windowState,
            minimumSize = DpSize(
                MarketLayout.minimumWindowWidthPx.dp,
                MarketLayout.minimumWindowHeightPx.dp,
            ),
            nativePopupLayers = true,
            onPreviewKeyEvent = { event ->
                val isUnmodifiedGrave = event.key == Key.Grave &&
                    !event.isAltPressed &&
                    !event.isCtrlPressed &&
                    !event.isMetaPressed &&
                    !event.isShiftPressed
                when {
                    event.key == Key.Grave &&
                        isDebugConsoleAvailable &&
                        (isGravePressed || isUnmodifiedGrave) -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> if (!isGravePressed) {
                                isGravePressed = true
                                debugConsoleToggleRequest += 1
                            }
                            KeyEventType.KeyUp -> isGravePressed = false
                        }
                        true
                    }
                    event.key == Key.Escape -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> if (!isEscapePressed) {
                                isEscapePressed = true
                                escapeRequest += 1
                            }
                            KeyEventType.KeyUp -> isEscapePressed = false
                        }
                        true
                    }
                    else -> false
                }
            },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val openingLoadingStatus = when {
                    hasEnteredSlideshow &&
                        !isBackgroundMusicReady &&
                        backgroundMusicError == null ->
                        "배경음악과 재생 장치를 준비하고 있습니다."
                    else -> bootstrapStatus
                }
                val visibleBackgroundMusicError = backgroundMusicError
                    ?.takeUnless { isBackgroundMusicSkipped }
                val openingLoadingError = bootstrapError ?: visibleBackgroundMusicError
                val isBackgroundMusicSettled =
                    isBackgroundMusicReady || isBackgroundMusicSkipped
                val retryLoading = {
                    var retried = false
                    if (bootstrapError != null) {
                        bootstrapAttempt += 1
                        retried = true
                    }
                    if (backgroundMusicError != null) {
                        isBackgroundMusicSkipped = false
                        backgroundMusicRecoveryAttempts = 0
                        backgroundMusicAttempt += 1
                        retried = true
                    }
                    if (!retried) {
                        bootstrapAttempt += 1
                        if (hasEnteredSlideshow) backgroundMusicAttempt += 1
                    }
                }
                LaunchedEffect(bootstrapAttempt) {
                    bootstrapStage = 0
                    bootstrapStatus = "기본 종목 카탈로그를 읽고 있습니다."
                    bootstrapError = null
                    baseCatalog = null
                    simulatorViewModel = null
                    appInitialReady = false
                    try {
                        val catalog = loadBaseInstrumentCatalog()
                        bootstrapStage = 1
                        bootstrapStatus = "시장 엔진과 거래 원장을 준비하고 있습니다."
                        val viewModel = withContext(Dispatchers.Default) {
                            SimulatorViewModel(catalog)
                        }
                        bootstrapStage = 2
                        bootstrapStatus = "화면을 준비하고 있습니다."
                        baseCatalog = catalog
                        simulatorViewModel = viewModel
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        bootstrapError = error.message?.take(300)?.takeIf(String::isNotBlank)
                            ?: "원인을 확인할 수 없는 초기화 오류가 발생했습니다."
                    }
                }

                val readyCatalog = baseCatalog
                val readyViewModel = simulatorViewModel
                if (readyCatalog != null && readyViewModel != null) {
                    App(
                        baseInstrumentCatalog = readyCatalog,
                        viewModel = readyViewModel,
                        audioSettings = audioSettings,
                        areAudioSettingsLoaded = areAudioSettingsLoaded,
                        onAudioSettingsChange = { settings -> audioSettings = settings },
                        onExitRequest = ::exitApplication,
                        onExitBlockedChanged = { blocked -> isExitBlocked = blocked },
                        escapeRequest = escapeRequest,
                        debugConsoleToggleRequest = debugConsoleToggleRequest,
                        onDebugConsoleAvailabilityChanged = { available ->
                            isDebugConsoleAvailable = available
                            if (!available) isGravePressed = false
                        },
                        onInitialLoadingComplete = { appInitialReady = true },
                        isLaunchOverlayVisible = !openingComplete,
                    )
                } else {
                    BootstrapLoadingScreen(
                        stage = bootstrapStage,
                        status = bootstrapStatus,
                        error = bootstrapError,
                        onRetry = retryLoading,
                    )
                }

                if (!openingComplete) {
                    OpeningScreen(
                        isLoadingComplete = readyCatalog != null &&
                            readyViewModel != null &&
                            appInitialReady &&
                            isBackgroundMusicSettled,
                        loadingStatus = openingLoadingStatus,
                        loadingError = openingLoadingError,
                        loadingErrorTitle = if (bootstrapError == null) {
                            "배경음악을 준비하지 못했습니다"
                        } else {
                            "시뮬레이션을 준비하지 못했습니다"
                        },
                        loadingErrorSecondaryActionLabel = if (
                            bootstrapError == null && visibleBackgroundMusicError != null
                        ) {
                            "음악 없이 계속"
                        } else {
                            null
                        },
                        onLoadingErrorSecondaryAction = {
                            isBackgroundMusicSkipped = true
                        },
                        onRetry = retryLoading,
                        onSlideshowEntered = { hasEnteredSlideshow = true },
                        onFinished = { openingComplete = true },
                    )
                }
                if (openingComplete && visibleBackgroundMusicError != null) {
                    BackgroundMusicErrorBanner(
                        error = visibleBackgroundMusicError,
                        recoveryAttempt = backgroundMusicRecoveryAttempts,
                        onRetry = {
                            backgroundMusicRecoveryAttempts = 0
                            isBackgroundMusicSkipped = false
                            backgroundMusicAttempt += 1
                        },
                        onContinueWithoutMusic = {
                            isBackgroundMusicSkipped = true
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundMusicErrorBanner(
    error: String,
    recoveryAttempt: Int,
    onRetry: () -> Unit,
    onContinueWithoutMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(440.dp),
        color = MarketColors.NavyRaised,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "배경음악 재생이 중단되었습니다",
                style = MarketType.label,
                color = Color.White,
            )
            Text(
                text = error,
                style = MarketType.body,
                color = MarketColors.Grey200,
            )
            if (recoveryAttempt > 0) {
                Text(
                    text = if (recoveryAttempt >= MAX_BACKGROUND_MUSIC_RECOVERY_ATTEMPTS) {
                        "자동 복구에 실패했습니다"
                    } else {
                        "자동 복구 $recoveryAttempt/$MAX_BACKGROUND_MUSIC_RECOVERY_ATTEMPTS"
                    },
                    style = MarketType.caption,
                    color = if (recoveryAttempt >= MAX_BACKGROUND_MUSIC_RECOVERY_ATTEMPTS) {
                        MarketColors.Rise
                    } else {
                        MarketColors.SignalLine
                    },
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onContinueWithoutMusic) {
                    Text("음악 없이 계속", style = MarketType.label, color = MarketColors.Grey200)
                }
                TextButton(onClick = onRetry) {
                    Text("다시 시도", style = MarketType.label, color = MarketColors.SignalLine)
                }
            }
        }
    }
}

@Composable
private fun BootstrapLoadingScreen(
    stage: Int,
    status: String,
    error: String?,
    onRetry: () -> Unit,
) {
    MarketSimulatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MarketColors.Ledger,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(MarketLayout.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(560.dp),
                    color = MarketColors.NavyRaised,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Text(
                            text = "MARKET LEDGER",
                            style = MarketType.caption,
                            color = MarketColors.SignalLine,
                        )
                        Text(
                            text = if (error == null) "시뮬레이션 장부를 여는 중" else "초기화를 완료하지 못했습니다",
                            style = MarketType.heading,
                            color = Color.White,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("카탈로그", "시장 엔진", "화면 준비").forEachIndexed { index, label ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    color = when {
                                        error != null && index == stage -> MarketColors.Rise.copy(alpha = 0.28f)
                                        index <= stage -> MarketColors.SignalLine.copy(alpha = 0.22f)
                                        else -> MarketColors.Navy
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        style = MarketType.caption,
                                        color = if (index <= stage) Color.White else MarketColors.Grey400,
                                    )
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (error == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(30.dp),
                                    color = MarketColors.SignalLine,
                                    strokeWidth = 3.dp,
                                )
                            }
                            Text(
                                text = error ?: status,
                                modifier = Modifier.weight(1f),
                                style = MarketType.body,
                                color = if (error == null) MarketColors.Grey200 else MarketColors.Rise,
                            )
                        }
                        if (error == null) {
                            LoadingFinancialFact(
                                factKey = "bootstrap:$stage",
                                dark = true,
                            )
                        }
                        if (error != null) {
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("다시 시도", style = MarketType.label, color = MarketColors.SignalLine)
                            }
                        }
                    }
                }
            }
        }
    }
}
