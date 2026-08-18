package com.amond.kmpbook.launcher.presentation

import com.amond.kmpbook.launcher.application.PreparedLaunch
import com.amond.kmpbook.launcher.application.ProgressUpdate
import com.amond.kmpbook.launcher.foundation.LauncherException
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

internal class LauncherFrame(
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onClosed: () -> Unit,
) : JFrame("Market Ledger 2040 Launcher") {
    private val titleLabel = JLabel("MARKET LEDGER 2040", SwingConstants.CENTER)
    private val statusLabel = JLabel("설치 환경을 준비하는 중입니다.", SwingConstants.CENTER)
    private val detailLabel = JLabel(" ", SwingConstants.CENTER)
    private val progressBar = JProgressBar(0, 1000)
    private val retryButton = JButton("다시 시도")
    private val playButton = JButton("게임 실행")

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(560, 300)
        preferredSize = Dimension(640, 340)
        background = BACKGROUND

        titleLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 27)
        titleLabel.foreground = ACCENT
        statusLabel.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
        statusLabel.foreground = FOREGROUND
        detailLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        detailLabel.foreground = MUTED

        progressBar.isStringPainted = false
        progressBar.foreground = ACCENT
        progressBar.background = PANEL
        progressBar.isIndeterminate = true

        retryButton.isVisible = false
        retryButton.addActionListener { onRetry() }
        playButton.isVisible = false
        playButton.addActionListener { onPlay() }

        val textPanel = JPanel(GridLayout(3, 1, 0, 8)).apply {
            background = BACKGROUND
            add(titleLabel)
            add(statusLabel)
            add(detailLabel)
        }
        val buttonPanel = JPanel().apply {
            background = BACKGROUND
            add(retryButton)
            add(playButton)
        }
        val content = JPanel(BorderLayout(16, 22)).apply {
            background = BACKGROUND
            border = BorderFactory.createEmptyBorder(34, 42, 30, 42)
            add(textPanel, BorderLayout.CENTER)
            add(progressBar, BorderLayout.NORTH)
            add(buttonPanel, BorderLayout.SOUTH)
        }
        contentPane = content
        pack()
        setLocationRelativeTo(null)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) = onClosed()
        })
    }

    fun showWorking(update: ProgressUpdate) = onEdt {
        statusLabel.text = update.message
        detailLabel.text = " "
        progressBar.isVisible = true
        progressBar.isIndeterminate = update.fraction == null
        update.fraction?.let { progressBar.value = (it.coerceIn(0.0, 1.0) * 1000).toInt() }
        retryButton.isVisible = false
        playButton.isVisible = false
    }

    fun showReady(prepared: PreparedLaunch) = onEdt {
        statusLabel.text = "Market Ledger 2040 ${prepared.installation.record.document.feed.version} 준비 완료"
        detailLabel.text = prepared.warning ?: "게임 본체와 디버그 모드 번들의 검증이 완료되었습니다."
        progressBar.isIndeterminate = false
        progressBar.value = 1000
        retryButton.isVisible = prepared.warning != null
        playButton.isVisible = true
        playButton.requestFocusInWindow()
    }

    fun showError(error: LauncherException) = onEdt {
        statusLabel.text = "설치 또는 업데이트를 완료하지 못했습니다."
        detailLabel.text = "${error.message} [${error.diagnosticCode}]"
        progressBar.isVisible = false
        retryButton.isVisible = true
        playButton.isVisible = false
    }

    fun showLaunchError(error: LauncherException) = onEdt {
        detailLabel.text = "${error.message} [${error.diagnosticCode}]"
        playButton.isEnabled = true
    }

    fun setPlayEnabled(enabled: Boolean) = onEdt { playButton.isEnabled = enabled }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private companion object {
        val BACKGROUND = Color(18, 22, 30)
        val PANEL = Color(34, 41, 53)
        val FOREGROUND = Color(235, 239, 244)
        val MUTED = Color(166, 176, 190)
        val ACCENT = Color(53, 199, 142)
    }
}
