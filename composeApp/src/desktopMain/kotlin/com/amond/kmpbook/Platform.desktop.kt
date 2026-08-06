package com.amond.kmpbook

class DesktopPlatform : Platform {
    override val name: String =
        "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
}

actual fun getPlatform(): Platform = DesktopPlatform()
