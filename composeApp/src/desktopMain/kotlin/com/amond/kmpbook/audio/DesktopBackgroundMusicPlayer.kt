package com.amond.kmpbook.audio

import dev.nucleusframework.rodio.RodioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopBackgroundMusicPlayer : AutoCloseable {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "market-ledger-background-music").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val closed = AtomicBoolean(false)
    private val mutablePlaybackErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val playbackErrors: SharedFlow<String> = mutablePlaybackErrors.asSharedFlow()

    private var player: RodioPlayer? = null
    private var tracks: List<Path> = emptyList()
    private var currentTrackIndex: Int = 0
    private var currentVolume: Float = 0f
    private var isPrepared: Boolean = false
    private var isPlaying: Boolean = false
    private var monitorJob: Job? = null

    suspend fun prepare() {
        check(!closed.get()) { "배경음악 플레이어가 이미 종료되었습니다." }
        withContext(dispatcher) {
            val resolvedTracks = resolveBackgroundMusicPlaylist()
            val initialTrackIndex = resolvedTracks.indices.random()
            resetPlayer()

            val preparedPlayer = RodioPlayer()
            try {
                preparedPlayer.setVolume(0f)
                resolvedTracks.forEach { track ->
                    prepareTrack(preparedPlayer, track)
                }
                prepareTrack(preparedPlayer, resolvedTracks[initialTrackIndex])
            } catch (error: Throwable) {
                preparedPlayer.close()
                throw error
            }

            player = preparedPlayer
            tracks = resolvedTracks
            currentTrackIndex = initialTrackIndex
            currentVolume = 0f
            isPrepared = true
        }
    }

    suspend fun startOrUpdateVolume(volume: Float) {
        check(!closed.get()) { "배경음악 플레이어가 이미 종료되었습니다." }
        withContext(dispatcher) {
            val preparedPlayer = checkNotNull(player) { "배경음악이 아직 준비되지 않았습니다." }
            check(isPrepared) { "배경음악이 아직 준비되지 않았습니다." }
            currentVolume = volume.coerceIn(0f, 1f)
            preparedPlayer.setVolume(currentVolume)
            if (!isPlaying) {
                preparedPlayer.play()
                isPlaying = true
                startPlaylistMonitor()
            }
        }
    }

    suspend fun reset() {
        if (closed.get()) return
        withContext(dispatcher) {
            resetPlayer()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            runBlocking {
                withContext(dispatcher) {
                    resetPlayer()
                }
            }
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }

    private fun prepareTrack(
        preparedPlayer: RodioPlayer,
        track: Path,
    ) {
        preparedPlayer.playFile(track.toAbsolutePath().toString(), false)
        preparedPlayer.pause()
        if (preparedPlayer.isSeekable()) {
            preparedPlayer.seekToMs(0L)
        }
    }

    private fun startPlaylistMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(PLAYLIST_POLL_MILLIS)
                val activePlayer = player ?: break
                try {
                    if (!isPlaying || !activePlayer.isEmpty()) continue
                    val playbackError = playNextAvailableTrack(activePlayer)
                    if (playbackError != null) throw playbackError
                } catch (error: LinkageError) {
                    publishPlaybackError(error)
                } catch (error: Exception) {
                    publishPlaybackError(error)
                }
            }
        }
    }

    private fun playNextAvailableTrack(activePlayer: RodioPlayer): Exception? {
        var lastError: Exception? = null
        val candidateIndices = tracks.indices
            .filter { index -> tracks.size == 1 || index != currentTrackIndex }
            .shuffled()
        candidateIndices.forEach { candidateIndex ->
            try {
                activePlayer.setVolume(currentVolume)
                activePlayer.playFile(
                    tracks[candidateIndex].toAbsolutePath().toString(),
                    false,
                )
                currentTrackIndex = candidateIndex
                return null
            } catch (error: Exception) {
                lastError = error
            }
        }
        return lastError ?: IllegalStateException("다음 배경음악을 찾지 못했습니다.")
    }

    private fun publishPlaybackError(error: Throwable) {
        isPlaying = false
        mutablePlaybackErrors.tryEmit(
            error.message?.takeIf(String::isNotBlank)
                ?: "다음 배경음악을 재생하지 못했습니다.",
        )
    }

    private fun resetPlayer() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            player?.close()
        } finally {
            player = null
            tracks = emptyList()
            currentTrackIndex = 0
            currentVolume = 0f
            isPrepared = false
            isPlaying = false
        }
    }

    private companion object {
        const val PLAYLIST_POLL_MILLIS: Long = 100L
    }
}
