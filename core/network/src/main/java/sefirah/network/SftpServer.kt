/*
 * Acknowledgment:
 * Most portions of this code are adapted from KDEConnect
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package sefirah.network

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.SessionContext
import org.apache.sshd.common.util.io.PathUtils
import org.apache.sshd.common.util.security.SecurityUtils.SECURITY_PROVIDER_REGISTRARS
import org.apache.sshd.scp.server.ScpCommandFactory
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.server.subsystem.SubsystemFactory
import org.apache.sshd.sftp.server.FileHandle
import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.sftp.server.SftpSubsystemProxy
import sefirah.domain.model.SftpServerInfo
import sefirah.network.util.MediaStoreHelper
import sefirah.network.util.TrustManager
import sefirah.network.util.getDeviceIpAddress
import java.nio.channels.Channel
import java.nio.channels.SeekableByteChannel
import java.nio.file.CopyOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.security.KeyPair
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.inject.Inject

class SftpServer @Inject constructor(
    private val context: Context,
    private val customTrustManager: TrustManager
) {
    private var sshd: SshServer? = null
    private var isRunning = false

    private var serverInfo: SftpServerInfo? = null

    private inner class PfxKeyPairProvider : KeyPairProvider {
        private val keyPair: KeyPair = initializeKeyPair()

        private fun initializeKeyPair(): KeyPair {
            val keyStore = customTrustManager.getKeyStore()
            val alias = keyStore.aliases().nextElement()
            val privateKey = keyStore.getKey(alias, null) as PrivateKey
            val cert = keyStore.getCertificate(alias)
            val publicKey = cert.publicKey

            return KeyPair(publicKey, privateKey)
        }

        override fun loadKeys(session: SessionContext?): Iterable<KeyPair> = listOf(keyPair)
    }

    private inner class SimpleFileSystemFactory : VirtualFileSystemFactory() {
        init {
            defaultHomeDir = Paths.get("/storage/emulated/0/")
        }
    }

    fun initialize() {
        if (sshd!=null) return
        val sshd = ServerBuilder.builder().apply {
            fileSystemFactory(
                if (SUPPORTS_NATIVEFS) {
                    NativeFileSystemFactory()
                } else {
                    return
                }
            )
        }.build()


        sshd.commandFactory = ScpCommandFactory()
        sshd.subsystemFactories =
            listOf<SubsystemFactory>(SftpSubsystemFactory.Builder().apply {
                withFileSystemAccessor(object : SftpFileSystemAccessor {
                    fun notifyMediaStore(path: Path) {
                        kotlin.runCatching {
                            val uri = Uri.parse(path.toUri().toString())
                            MediaStoreHelper.indexFile(context, uri)
                            uri
                        }.fold(
                            onSuccess = { Log.i(TAG, "Notified media store: $path, $it") },
                            onFailure = { Log.w(TAG, "Failed to notify media store: $path", it) }
                        )
                    }

                    override fun openFile(
                        subsystem: SftpSubsystemProxy?,
                        fileHandle: FileHandle?,
                        file: Path?,
                        handle: String?,
                        options: MutableSet<out OpenOption>?,
                        vararg attrs: FileAttribute<*>?
                    ): SeekableByteChannel {
                        return super.openFile(subsystem, fileHandle, file, handle, options, *attrs)
                    }

                    override fun removeFile(
                        subsystem: SftpSubsystemProxy?,
                        path: Path?,
                        isDirectory: Boolean
                    ) {
                        super.removeFile(subsystem, path, isDirectory)
                        path?.let { notifyMediaStore(it) }
                    }

                    override fun copyFile(
                        subsystem: SftpSubsystemProxy?,
                        src: Path?,
                        dst: Path?,
                        opts: MutableCollection<CopyOption>?
                    ) {
                        super.copyFile(subsystem, src, dst, opts)
                        dst?.let { notifyMediaStore(it) }
                    }

                    override fun renameFile(
                        subsystem: SftpSubsystemProxy?,
                        oldPath: Path?,
                        newPath: Path?,
                        opts: MutableCollection<CopyOption>?
                    ) {
                        super.renameFile(subsystem, oldPath, newPath, opts)
                        oldPath?.let { notifyMediaStore(it) }
                        newPath?.let { notifyMediaStore(it) }
                    }

                    override fun createLink(
                        subsystem: SftpSubsystemProxy?,
                        link: Path?,
                        existing: Path?,
                        symLink: Boolean
                    ) {
                        super.createLink(subsystem, link, existing, symLink)
                        link?.let { notifyMediaStore(it) }
                        existing?.let { notifyMediaStore(it) }
                    }

                    override fun closeFile(
                        subsystem: SftpSubsystemProxy?,
                        fileHandle: FileHandle?,
                        file: Path?,
                        handle: String?,
                        channel: Channel?,
                        options: MutableSet<out OpenOption>?
                    ) {
                        super.closeFile(subsystem, fileHandle, file, handle, channel, options)
                        if (options?.contains(StandardOpenOption.WRITE) == true) {
                            file?.let { notifyMediaStore(it) }
                        }
                    }
                })
            }.build())

        this.sshd = sshd
    }

    fun start() : SftpServerInfo? {
        if (isRunning) return serverInfo

        val pwd = regeneratePassword()
        try {
            sshd = SshServer.setUpDefaultServer().apply {
                port = 8668
                keyPairProvider = PfxKeyPairProvider()

                publickeyAuthenticator = PublickeyAuthenticator { _, _, _ -> true }
                passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                    username == USER && password == pwd
                }

                fileSystemFactory = SimpleFileSystemFactory()
                subsystemFactories = listOf(SftpSubsystemFactory())
                start()
            }
            val ipAddress = getDeviceIpAddress()
            isRunning = true

            Log.d(TAG, "SFTP server started: $ipAddress on port 8668, Pass: $pwd")

            serverInfo = ipAddress?.let {
                SftpServerInfo(
                    username = USER,
                    password = pwd,
                    ipAddress = it,
                    port = 8668
                )
            }
            return serverInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SFTP server", e)
            throw e
        }
    }

    fun stop() {
        try {
            if (isRunning){
                sshd?.stop(true)
                isRunning = false
            }
            Log.d(TAG, "SFTP server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop SFTP server", e)
        }
    }

    /**
     * Generate a random password with 12 characters, including uppercase letters, lowercase letters, numbers, and special characters.
     */
    private fun regeneratePassword(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#$%^&*"
        return (1..12).map { allowedChars.random() }.joinToString("")
    }

    companion object {
        private const val TAG = "SftpServer"
        private const val USER = "sun"
        val SUPPORTS_NATIVEFS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        private val PORT_RANGE = 1739..1764

        init {
            System.setProperty(SECURITY_PROVIDER_REGISTRARS, "") // disable BouncyCastle
            System.setProperty(
                "org.apache.sshd.common.io.IoServiceFactoryFactory",
                "org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory"
            )
            // Remove it when SSHD Core is fixed.
            // Android has no user home folder, so we need to set it to something.
            // `System.getProperty("user.home")` is not available on Android,
            // but it exists in SSHD Core's `org.apache.sshd.common.util.io.PathUtils.LazyDefaultUserHomeFolderHolder`.
            PathUtils.setUserHomeFolderResolver { Path.of("/") }
        }
    }
}