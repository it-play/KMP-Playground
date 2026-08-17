package com.amond.kmpbook.modding.api.runtime

/**
 * Entry point for executable code that has already passed the host's signature and policy checks.
 *
 * Implementations run in the host JVM and are not sandboxed. The desktop host discovers exactly one
 * provider from a verified, content-addressed JAR through [java.util.ServiceLoader].
 */
interface ExecutableGameMod : AutoCloseable {
    val id: String
    val version: String
    val apiVersion: Int

    fun attach(context: ModGameContext): ModConsoleContribution?

    fun detach() = Unit

    override fun close() = Unit
}
