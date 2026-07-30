package io.heapy.toolchain.distribution

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.jar.JarInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DistributionPluginTest {
    @Test
    fun `scopes task outputs to the enabled module root`() {
        val pluginConfiguration = Files.readString(
            Path.of("plugin.yaml"),
        )

        assertContains(
            pluginConfiguration,
            "installDirectory: \${module.rootDir}/build/install/" +
                "\${pluginSettings.applicationName}",
        )
        assertContains(
            pluginConfiguration,
            "archiveFile: \${module.rootDir}/build/distributions/" +
                "\${pluginSettings.archiveFileName}",
        )
    }

    @Test
    fun `rejects project-global module-namespaced output paths`() =
        withTempDirectory { root ->
            val applicationName = "shared-app"
            val applicationJar = writeJar(root.resolve("shared-app-jvm.jar"), "application")
            val installDirectory = root.resolve("build/install/first-module/$applicationName")

            val installException = assertFailsWith<IllegalArgumentException> {
                installDistributionFiles(
                    applicationJar = applicationJar,
                    runtimeClasspath = listOf(applicationJar),
                    applicationName = applicationName,
                    applicationVersion = "1.0",
                    mainClass = "example.Main",
                    optsEnvironmentVariable = null,
                    defaultJvmArgs = emptyList(),
                    installDirectory = installDirectory,
                )
            }
            assertEquals(
                "Install directory must be under build/install: $installDirectory",
                installException.message,
            )

            val archiveFile = root.resolve("build/distributions/first-module/shared-app.tar")
            val archiveException = assertFailsWith<IllegalArgumentException> {
                distTar(
                    installDirectory = root.resolve("build/install/$applicationName"),
                    applicationName = applicationName,
                    archiveFileName = "shared-app.tar",
                    archiveFile = archiveFile,
                )
            }
            assertEquals(
                "Archive output must be under build/distributions: $archiveFile",
                archiveException.message,
            )
        }

    @Test
    fun `derives a portable JVM options environment variable`() {
        assertEquals("KOTBUSTA_OPTS", defaultOptsEnvironmentVariable("kotbusta"))
        assertEquals("MY_APP_OPTS", defaultOptsEnvironmentVariable("my-app"))
        assertEquals("APP_2_OPTS", defaultOptsEnvironmentVariable("app.2"))
        assertEquals("_2FA_TOOL_OPTS", defaultOptsEnvironmentVariable("2fa-tool"))
    }

    @Test
    fun `installs digit-leading application with valid derived JVM options variable`() =
        withTempDirectory { root ->
            val applicationJar = writeJar(root.resolve("2fa-tool-jvm.jar"), "application")
            val installDirectory = root.resolve("build/install/2fa-tool")

            installDistributionFiles(
                applicationJar = applicationJar,
                runtimeClasspath = listOf(applicationJar),
                applicationName = "2fa-tool",
                applicationVersion = "1.0",
                mainClass = "example.Main",
                optsEnvironmentVariable = null,
                defaultJvmArgs = emptyList(),
                installDirectory = installDirectory,
            )

            val launcher = installDirectory.resolve("bin/2fa-tool")
            assertContains(Files.readString(launcher), "APP_OPTS_VALUE=\${_2FA_TOOL_OPTS:-}")
        }

    @Test
    fun `installs versioned application and dependency JARs with stable names and permissions`() =
        withTempDirectory { root ->
            val inputs = Files.createDirectories(root.resolve("inputs"))
            val applicationJar = writeJar(inputs.resolve("kotbusta-jvm.jar"), "application")
            val dataopsJar = writeJar(inputs.resolve("dataops-jvm.jar"), "dataops")
            val externalJar = writeFile(inputs.resolve("library-1.0.jar"), "library")
            val installDirectory = root.resolve("build/install/kotbusta")

            installDistributionFiles(
                applicationJar = applicationJar,
                runtimeClasspath = listOf(externalJar, applicationJar, dataopsJar),
                applicationName = "kotbusta",
                applicationVersion = "1.2.3",
                mainClass = "io.heapy.kotbusta.Application",
                optsEnvironmentVariable = null,
                defaultJvmArgs = listOf("--add-modules=jdk.incubator.vector"),
                installDirectory = installDirectory,
            )

            val installedFiles = Files.walk(installDirectory).use { stream ->
                stream
                    .filter(Files::isRegularFile)
                    .map { installDirectory.relativize(it).toString().replace('\\', '/') }
                    .sorted()
                    .toList()
            }
            assertEquals(
                listOf(
                    "bin/kotbusta",
                    "bin/kotbusta.bat",
                    "lib/dataops.jar",
                    "lib/kotbusta-1.2.3.jar",
                    "lib/library-1.0.jar",
                ),
                installedFiles,
            )

            val launcher = installDirectory.resolve("bin/kotbusta")
            assertContains(Files.readString(launcher), "APP_OPTS_VALUE=\${KOTBUSTA_OPTS:-}")

            if (Files.getFileAttributeView(launcher, PosixFileAttributeView::class.java) != null) {
                assertEquals(
                    "rwxr-xr-x",
                    PosixFilePermissions.toString(Files.getPosixFilePermissions(launcher)),
                )
                assertEquals(
                    "rw-r--r--",
                    PosixFilePermissions.toString(
                        Files.getPosixFilePermissions(installDirectory.resolve("lib/dataops.jar")),
                    ),
                )
            }
        }

    @Test
    fun `installs Windows launcher with CRLF and expected command semantics`() =
        withTempDirectory { root ->
            val applicationJar = writeJar(root.resolve("sample-jvm.jar"), "application")
            val dependencyJar = writeFile(root.resolve("dependency-2.0.jar"), "dependency")
            val installDirectory = root.resolve("build/install/sample")

            installDistributionFiles(
                applicationJar = applicationJar,
                runtimeClasspath = listOf(applicationJar, dependencyJar),
                applicationName = "sample",
                applicationVersion = "1.0",
                mainClass = "example.App\$Main",
                optsEnvironmentVariable = "SAMPLE_OPTS",
                defaultJvmArgs = listOf("-Dgreeting=hello world"),
                installDirectory = installDirectory,
            )

            val launcher = Files.readString(installDirectory.resolve("bin/sample.bat"))
            assertTrue(launcher.endsWith("\r\n"))
            val contentWithoutCrlf = launcher.replace("\r\n", "")
            assertFalse('\r' in contentWithoutCrlf)
            assertFalse('\n' in contentWithoutCrlf)
            assertContains(launcher, "set \"APP_HOME=%~dp0..\"")
            assertContains(
                launcher,
                "set \"CLASSPATH=%APP_HOME%\\lib\\sample-1.0.jar;" +
                    "%APP_HOME%\\lib\\dependency-2.0.jar\"",
            )
            assertContains(launcher, "set DEFAULT_JVM_OPTS=\"-Dgreeting=hello world\"")
            assertContains(
                launcher,
                "\"%JAVA_EXE%\" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %SAMPLE_OPTS% " +
                    "-classpath \"%CLASSPATH%\" example.App\$Main %*",
            )
            assertContains(launcher, "ERROR: JAVA_HOME is not set")
            assertContains(launcher, "ERROR: JAVA_HOME does not contain bin\\java.exe")
            assertContains(launcher, "set EXIT_CODE=%ERRORLEVEL%")
            assertContains(launcher, "endlocal & exit /b %EXIT_CODE%")
        }

    @Test
    fun `rejects cmd metacharacters in Windows launcher JVM arguments`() =
        withTempDirectory { root ->
            val applicationJar = writeJar(root.resolve("sample-jvm.jar"), "application")

            val exception = assertFailsWith<IllegalArgumentException> {
                installDistributionFiles(
                    applicationJar = applicationJar,
                    runtimeClasspath = listOf(applicationJar),
                    applicationName = "sample",
                    applicationVersion = "1.0",
                    mainClass = "example.Main",
                    optsEnvironmentVariable = "SAMPLE_OPTS",
                    defaultJvmArgs = listOf("-Dspecial=%&^<>|\""),
                    installDirectory = root.resolve("build/install/sample"),
                )
            }

            assertEquals(
                "Windows launcher JVM arguments cannot contain cmd.exe metacharacters: " +
                    "%, &, ^, <, >, |, or \".",
                exception.message,
            )
        }

    @Test
    fun `fails before touching output when JAR names conflict`() =
        withTempDirectory { root ->
            val applicationJar = writeFile(root.resolve("application.jar"), "application")
            val first = writeFile(root.resolve("first/same-1.0.jar"), "first")
            val second = writeFile(root.resolve("second/same-1.0.jar"), "second")

            val exception = assertFailsWith<IllegalArgumentException> {
                planDistributionFiles(
                    applicationJar = applicationJar,
                    runtimeClasspath = listOf(first, second),
                    applicationName = "sample",
                    applicationVersion = "1.0",
                )
            }

            assertContains(exception.message.orEmpty(), "same-1.0.jar")
            assertContains(exception.message.orEmpty(), first.toString())
            assertContains(exception.message.orEmpty(), second.toString())
        }

    @Test
    fun `renders escaped JVM options and preserves launcher exit code and arguments`() =
        withTempDirectory { root ->
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return@withTempDirectory

            val installDirectory = root.resolve("build/install/sample")
            val binDirectory = Files.createDirectories(installDirectory.resolve("bin"))
            Files.createDirectories(installDirectory.resolve("lib"))
            val launcher = binDirectory.resolve("sample")
            Files.writeString(
                launcher,
                renderUnixLauncher(
                    applicationName = "sample",
                    mainClass = "example.Main",
                    jarNames = listOf("sample-1.0.jar", "dependency-2.0.jar"),
                    optsEnvironmentVariable = "SAMPLE_OPTS",
                    defaultJvmArgs = listOf(
                        "-Ddefault=hello world",
                        "-Dquote=a\"b",
                        "-Dapostrophe=it's",
                        """-Dslash=C:\tmp""",
                    ),
                ),
            )
            makeExecutable(launcher)

            val fakeJavaHome = root.resolve("fake java home")
            val fakeJava = Files.createDirectories(fakeJavaHome.resolve("bin")).resolve("java")
            Files.writeString(
                fakeJava,
                """
                    |#!/bin/sh
                    |for arg do
                    |    printf '<%s>\n' "${'$'}arg"
                    |done
                    |exit 23
                    |
                """.trimMargin(),
            )
            makeExecutable(fakeJava)

            val process = ProcessBuilder(launcher.toString(), "hello world", "plain")
                .redirectErrorStream(true)
                .apply {
                    environment()["JAVA_HOME"] = fakeJavaHome.toString()
                    environment()["JAVA_OPTS"] = "\"-Djava=two words\""
                    environment()["SAMPLE_OPTS"] = "-Dsample=yes"
                }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            assertEquals(23, exitCode)
            assertEquals(
                listOf(
                    "<-Ddefault=hello world>",
                    "<-Dquote=a\"b>",
                    "<-Dapostrophe=it's>",
                    """<-Dslash=C:\tmp>""",
                    "<-Djava=two words>",
                    "<-Dsample=yes>",
                    "<-classpath>",
                    "<${installDirectory.toRealPath()}/lib/sample-1.0.jar:" +
                        "${installDirectory.toRealPath()}/lib/dependency-2.0.jar>",
                    "<example.Main>",
                    "<hello world>",
                    "<plain>",
                ),
                output.lineSequence().filter(String::isNotEmpty).toList(),
            )
        }

    @Test
    fun `preserves dollar sign in nested main class when executing launcher`() =
        withTempDirectory { root ->
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return@withTempDirectory

            val installDirectory = root.resolve("build/install/sample")
            val binDirectory = Files.createDirectories(installDirectory.resolve("bin"))
            Files.createDirectories(installDirectory.resolve("lib"))
            val launcher = binDirectory.resolve("sample")
            Files.writeString(
                launcher,
                renderUnixLauncher(
                    applicationName = "sample",
                    mainClass = "example.App\$Main",
                    jarNames = listOf("sample.jar"),
                    optsEnvironmentVariable = "SAMPLE_OPTS",
                    defaultJvmArgs = emptyList(),
                ),
            )
            makeExecutable(launcher)

            val fakeJavaHome = root.resolve("fake java home")
            val fakeJava = Files.createDirectories(fakeJavaHome.resolve("bin")).resolve("java")
            Files.writeString(
                fakeJava,
                """
                    |#!/bin/sh
                    |for arg do
                    |    printf '<%s>\n' "${'$'}arg"
                    |done
                    |exit 23
                    |
                """.trimMargin(),
            )
            makeExecutable(fakeJava)

            val process = ProcessBuilder(launcher.toString())
                .redirectErrorStream(true)
                .apply {
                    environment()["JAVA_HOME"] = fakeJavaHome.toString()
                    environment().remove("JAVA_OPTS")
                    environment().remove("SAMPLE_OPTS")
                    environment().remove("Main")
                }
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            assertEquals(23, exitCode)
            assertEquals(
                listOf(
                    "<-classpath>",
                    "<${installDirectory.toRealPath()}/lib/sample.jar>",
                    "<example.App\$Main>",
                ),
                output.lineSequence().filter(String::isNotEmpty).toList(),
            )
        }

    @Test
    fun `rejects JVM arguments that cannot be represented safely`() {
        assertFailsWith<IllegalArgumentException> {
            renderUnixLauncher(
                applicationName = "sample",
                mainClass = "example.Main",
                jarNames = listOf("sample.jar"),
                optsEnvironmentVariable = "SAMPLE_OPTS",
                defaultJvmArgs = listOf("line one\nline two"),
            )
        }
        }

    @Test
    fun `normalizes generated JAR order and timestamps without recompressing content`() =
        withTempDirectory { root ->
            val firstSource = writeJar(
                path = root.resolve("first.jar"),
                entries = listOf("z.txt" to "last", "a.txt" to "first"),
                timestampMillis = 123_456_000,
            )
            val secondSource = writeJar(
                path = root.resolve("second.jar"),
                entries = listOf("a.txt" to "first", "z.txt" to "last"),
                timestampMillis = 987_654_000,
            )
            val firstTarget = root.resolve("normalized-first.jar")
            val secondTarget = root.resolve("normalized-second.jar")

            writeReproducibleJar(firstSource, firstTarget)
            writeReproducibleJar(secondSource, secondTarget)

            assertContentEquals(
                Files.readAllBytes(firstTarget),
                Files.readAllBytes(secondTarget),
            )
        }

    @Test
    fun `keeps manifest first when normalizing JAR`() =
        withTempDirectory { root ->
            val source = writeJar(
                path = root.resolve("source.jar"),
                entries = listOf(
                    "A.class" to "class",
                    "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\r\n\r\n",
                ),
                timestampMillis = 123_456_000,
            )
            val target = root.resolve("normalized.jar")

            writeReproducibleJar(source, target)

            JarInputStream(Files.newInputStream(target)).use { input ->
                assertNotNull(input.manifest, "JarInputStream must discover the normalized manifest")
            }
            ZipInputStream(Files.newInputStream(target)).use { input ->
                assertEquals("META-INF/MANIFEST.MF", input.nextEntry?.name)
            }
        }

    @Test
    fun `normalizes manifest-bearing JARs reproducibly`() =
        withTempDirectory { root ->
            val firstSource = writeJar(
                path = root.resolve("first.jar"),
                entries = listOf(
                    "z.txt" to "last",
                    "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\r\n\r\n",
                    "META-INF/" to "",
                    "A.class" to "class",
                ),
                timestampMillis = 123_456_000,
            )
            val secondSource = writeJar(
                path = root.resolve("second.jar"),
                entries = listOf(
                    "A.class" to "class",
                    "META-INF/" to "",
                    "z.txt" to "last",
                    "META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\r\n\r\n",
                ),
                timestampMillis = 987_654_000,
            )
            val firstTarget = root.resolve("normalized-first.jar")
            val secondTarget = root.resolve("normalized-second.jar")

            writeReproducibleJar(firstSource, firstTarget)
            writeReproducibleJar(secondSource, secondTarget)

            assertContentEquals(
                Files.readAllBytes(firstTarget),
                Files.readAllBytes(secondTarget),
            )
            ZipInputStream(Files.newInputStream(firstTarget)).use { input ->
                assertEquals("META-INF/", input.nextEntry?.name)
                assertEquals("META-INF/MANIFEST.MF", input.nextEntry?.name)
            }
        }

    @Test
    fun `moves JAR into place by replacing existing target on atomic and fallback paths`() =
        withTempDirectory { root ->
            listOf(true, false).forEach { atomic ->
                val source = writeFile(root.resolve("source-$atomic.jar"), "new")
                val target = writeFile(root.resolve("target-$atomic.jar"), "old")

                moveJarIntoPlace(source, target, atomic)

                assertEquals("new", Files.readString(target), "atomic=$atomic")
            }
        }

    @Test
    fun `creates byte-for-byte reproducible TAR with fixed metadata`() =
        withTempDirectory { root ->
            val installDirectory = root.resolve("build/install/sample")
            val launcher = writeFile(installDirectory.resolve("bin/sample"), "launcher")
            val windowsLauncher = writeFile(installDirectory.resolve("bin/sample.bat"), "launcher")
            val jar = writeFile(installDirectory.resolve("lib/sample-1.0.jar"), "jar")
            Files.setLastModifiedTime(launcher, FileTime.fromMillis(123_456_789))
            Files.setLastModifiedTime(windowsLauncher, FileTime.fromMillis(456_789_123))
            Files.setLastModifiedTime(jar, FileTime.fromMillis(987_654_321))

            val firstArchive = root.resolve("build/distributions/first.tar")
            val secondArchive = root.resolve("build/distributions/second.tar")
            createReproducibleTar(installDirectory, "sample", firstArchive)
            createReproducibleTar(installDirectory, "sample", secondArchive)

            assertContentEquals(
                Files.readAllBytes(firstArchive),
                Files.readAllBytes(secondArchive),
            )

            val entries = readTarEntries(firstArchive)
            assertEquals(
                listOf(
                    "sample/",
                    "sample/bin/",
                    "sample/bin/sample",
                    "sample/bin/sample.bat",
                    "sample/lib/",
                    "sample/lib/sample-1.0.jar",
                ),
                entries.map(TarEntrySnapshot::name),
            )
            entries.forEach { entry ->
                assertEquals(0L, entry.timestampMillis, entry.name)
                assertEquals(0L, entry.userId, entry.name)
                assertEquals(0L, entry.groupId, entry.name)
                val expectedMode = if (
                    entry.name.endsWith("/") ||
                    entry.name == "sample/bin/sample" ||
                    entry.name == "sample/bin/sample.bat"
                ) {
                    493
                } else {
                    420
                }
                assertEquals(expectedMode, entry.mode, entry.name)
            }
        }

    private fun readTarEntries(archive: Path): List<TarEntrySnapshot> =
        TarArchiveInputStream(Files.newInputStream(archive)).use { input ->
            buildList {
                while (true) {
                    val entry = input.nextEntry ?: break
                    add(
                        TarEntrySnapshot(
                            name = entry.name,
                            timestampMillis = entry.modTime.time,
                            userId = entry.longUserId,
                            groupId = entry.longGroupId,
                            mode = entry.mode,
                        ),
                    )
                }
            }
        }

    private fun makeExecutable(path: Path) {
        val permissions = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
        requireNotNull(permissions) {
            "POSIX file permissions are required for launcher integration tests."
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    private fun writeFile(
        path: Path,
        content: String,
    ): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return path
    }

    private fun writeJar(
        path: Path,
        content: String,
    ): Path = writeJar(
        path = path,
        entries = listOf("content.txt" to content),
        timestampMillis = System.currentTimeMillis(),
    )

    private fun writeJar(
        path: Path,
        entries: List<Pair<String, String>>,
        timestampMillis: Long,
    ): Path {
        Files.createDirectories(path.parent)
        ZipOutputStream(Files.newOutputStream(path)).use { output ->
            entries.forEach { (name, content) ->
                output.putNextEntry(
                    ZipEntry(name).apply {
                        time = timestampMillis
                    },
                )
                output.write(content.toByteArray())
                output.closeEntry()
            }
        }
        return path
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("distribution-plugin-test")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private data class TarEntrySnapshot(
        val name: String,
        val timestampMillis: Long,
        val userId: Long,
        val groupId: Long,
        val mode: Int,
    )
}
