package com.amond.kmpbook.launcher.presentation

import com.amond.kmpbook.launcher.application.PreparedLaunch
import com.amond.kmpbook.launcher.application.ProgressUpdate
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

internal class LauncherFrame(
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onClosed: () -> Unit,
) : JFrame("Market Ledger 2040") {
    private val statusLabel = JLabel()
    private val versionLabel = JLabel()
    private val detailPanel = JPanel(BorderLayout())
    private val detailText = JTextArea()
    private val progressBar = LauncherProgressBar()
    private val retryButton = LauncherButton("다시 시도", isPrimary = false)
    private val playButton = LauncherButton("실행", isPrimary = true)
    private val latestProgress = AtomicReference<ProgressUpdate>()
    private val progressDispatchScheduled = AtomicBoolean(false)

    init {
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        minimumSize = Dimension(760, 420)
        preferredSize = Dimension(788, 444)
        isResizable = false
        javaClass.getResource("/launcher/market-ledger-icon.png")?.let { iconImage = ImageIcon(it).image }

        statusLabel.apply {
            font = LauncherFonts.bold(22f)
            foreground = INK
            alignmentX = LEFT_ALIGNMENT
        }
        versionLabel.apply {
            font = LauncherFonts.regular(12f)
            foreground = MUTED
            alignmentX = LEFT_ALIGNMENT
            isVisible = false
        }
        detailText.apply {
            font = LauncherFonts.regular(13f)
            foreground = INK
            background = PAPER
            isEditable = false
            isFocusable = false
            lineWrap = true
            wrapStyleWord = true
            border = null
            rows = 2
        }
        detailPanel.apply {
            background = PAPER
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIVIDER),
                BorderFactory.createEmptyBorder(12, 14, 12, 14),
            )
            maximumSize = Dimension(Int.MAX_VALUE, 72)
            alignmentX = LEFT_ALIGNMENT
            add(detailText, BorderLayout.CENTER)
            isVisible = false
        }
        progressBar.apply {
            alignmentX = LEFT_ALIGNMENT
            isIndeterminate = true
        }

        retryButton.apply {
            isVisible = false
            addActionListener { onRetry() }
        }
        playButton.apply {
            isVisible = false
            addActionListener { onPlay() }
        }

        val statePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = BACKGROUND
            border = BorderFactory.createEmptyBorder(52, 48, 24, 48)
            add(statusLabel)
            add(Box.createRigidArea(Dimension(0, 12)))
            add(versionLabel)
            add(Box.createRigidArea(Dimension(0, 28)))
            add(progressBar)
            add(Box.createRigidArea(Dimension(0, 22)))
            add(detailPanel)
            add(Box.createVerticalGlue())
        }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0)).apply {
            background = BACKGROUND
            border = BorderFactory.createEmptyBorder(0, 38, 38, 38)
            add(retryButton)
            add(playButton)
        }
        val actionPanel = JPanel(BorderLayout()).apply {
            background = BACKGROUND
            add(statePanel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        contentPane = JPanel(BorderLayout()).apply {
            background = BACKGROUND
            add(LauncherBrandPanel(), BorderLayout.WEST)
            add(actionPanel, BorderLayout.CENTER)
        }
        pack()
        setLocationRelativeTo(null)
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(event: WindowEvent) {
                progressBar.isIndeterminate = false
                onClosed()
            }
        })
    }

    fun showWorking(update: ProgressUpdate) {
        if (SwingUtilities.isEventDispatchThread()) {
            renderWorking(update)
            return
        }
        latestProgress.set(update)
        scheduleProgressRender()
    }

    private fun renderWorking(update: ProgressUpdate) {
        statusLabel.text = update.message
        statusLabel.isVisible = true
        versionLabel.isVisible = false
        hideDetail()
        progressBar.isVisible = true
        progressBar.isIndeterminate = update.fraction == null
        update.fraction?.let { progressBar.value = (it.coerceIn(0.0, 1.0) * 1000).toInt() }
        retryButton.isVisible = false
        playButton.isVisible = false
        refreshLayout()
    }

    private fun scheduleProgressRender() {
        if (!progressDispatchScheduled.compareAndSet(false, true)) return
        SwingUtilities.invokeLater {
            progressDispatchScheduled.set(false)
            latestProgress.getAndSet(null)?.let(::renderWorking)
            if (latestProgress.get() != null) scheduleProgressRender()
        }
    }

    fun showReady(prepared: PreparedLaunch) = onEdt {
        statusLabel.text = if (prepared.warning == null) "실행 준비 완료" else "기존 버전 실행 가능"
        statusLabel.isVisible = true
        versionLabel.text = "v${prepared.installation.record.document.feed.version}"
        versionLabel.isVisible = true
        progressBar.isIndeterminate = false
        progressBar.isVisible = false
        prepared.warning?.let(::showWarning) ?: hideDetail()
        retryButton.isVisible = prepared.warning != null
        playButton.isVisible = true
        playButton.isEnabled = true
        refreshLayout()
        playButton.requestFocusInWindow()
    }

    fun showError() = onEdt {
        statusLabel.text = "설치 실패"
        statusLabel.isVisible = true
        versionLabel.isVisible = false
        progressBar.isIndeterminate = false
        progressBar.isVisible = false
        hideDetail()
        retryButton.isVisible = true
        playButton.isVisible = false
        refreshLayout()
    }

    fun showLaunchError() = onEdt {
        statusLabel.text = "실행 실패"
        statusLabel.isVisible = true
        hideDetail()
        playButton.isEnabled = true
        refreshLayout()
    }

    fun setPlayEnabled(enabled: Boolean) = onEdt { playButton.isEnabled = enabled }

    private fun showWarning(message: String) {
        detailText.text = message
        detailText.caretPosition = 0
        detailText.background = WARNING_BACKGROUND
        detailPanel.background = WARNING_BACKGROUND
        detailPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(WARNING_BORDER),
            BorderFactory.createEmptyBorder(12, 14, 12, 14),
        )
        detailPanel.isVisible = true
    }

    private fun hideDetail() {
        detailText.text = ""
        detailPanel.isVisible = false
    }

    private fun refreshLayout() {
        contentPane.revalidate()
        contentPane.repaint()
    }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private companion object {
        val BACKGROUND = Color(0xED, 0xF1, 0xF4)
        val PAPER = Color(0xFC, 0xFD, 0xFE)
        val INK = Color(0x17, 0x22, 0x2D)
        val MUTED = Color(0x6E, 0x7A, 0x86)
        val DIVIDER = Color(0xD7, 0xDF, 0xE5)
        val WARNING_BACKGROUND = Color(0xFF, 0xF7, 0xE7)
        val WARNING_BORDER = Color(0xDD, 0xAD, 0x4C)
    }
}
