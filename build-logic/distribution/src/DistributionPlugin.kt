package io.heapy.toolchain.distribution

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.CompilationArtifact
import org.jetbrains.amper.plugins.Configurable
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.time.LocalDateTime
import java.util.Date

@Configurable
public interface DistributionSettings {
    public val applicationName: String
    public val applicationVersion: String get() = "unspecified"
    public val archiveFileName: String
    public val optsEnvironmentVariable: String?
    public val defaultJvmArgs: List<String> get() = emptyList()
}

@TaskAction
public fun installDist(
    @Input applicationJar: CompilationArtifact,
    @Input runtimeClasspath: Classpath,
    applicationName: String,
    applicationVersion: String,
    mainClass: String?,
    optsEnvironmentVariable: String?,
    defaultJvmArgs: List<String>,
    @Output installDirectory: Path,
) {
    val effectiveMainClass = requireNotNull(mainClass) {
        "The distribution plugin requires settings.jvm.mainClass to be configured."
    }

    installDistributionFiles(
        applicationJar = applicationJar.artifact,
        runtimeClasspath = runtimeClasspath.resolvedFiles,
        applicationName = applicationName,
        applicationVersion = applicationVersion,
        mainClass = effectiveMainClass,
        optsEnvironmentVariable = optsEnvironmentVariable,
        defaultJvmArgs = defaultJvmArgs,
        installDirectory = installDirectory,
    )
}

@TaskAction
public fun distTar(
    @Input installDirectory: Path,
    applicationName: String,
    archiveFileName: String,
    @Output archiveFile: Path,
) {
    validateSafeApplicationName(applicationName)
    validateArchiveTarget(archiveFile, archiveFileName)
    createReproducibleTar(
        installDirectory = installDirectory,
        applicationName = applicationName,
        archiveFile = archiveFile,
    )
}

internal data class DistributionFile(
    val source: Path,
    val targetName: String,
    val normalizeJar: Boolean,
)

internal fun installDistributionFiles(
    applicationJar: Path,
    runtimeClasspath: List<Path>,
    applicationName: String,
    applicationVersion: String,
    mainClass: String,
    optsEnvironmentVariable: String?,
    defaultJvmArgs: List<String>,
    installDirectory: Path,
) {
    validateSafeApplicationName(applicationName)
    validateSafeVersion(applicationVersion)
    validateMainClass(mainClass)
    defaultJvmArgs.forEach(::validateJvmArgument)

    val effectiveOptsEnvironmentVariable = optsEnvironmentVariable
        ?: defaultOptsEnvironmentVariable(applicationName)
    validateEnvironmentVariable(effectiveOptsEnvironmentVariable)
    validateInstallTarget(installDirectory, applicationName)

    val files = planDistributionFiles(
        applicationJar = applicationJar,
        runtimeClasspath = runtimeClasspath,
        applicationName = applicationName,
        applicationVersion = applicationVersion,
    )

    deleteOwnedDirectory(installDirectory)

    val binDirectory = installDirectory.resolve("bin")
    val libDirectory = installDirectory.resolve("lib")
    Files.createDirectories(binDirectory)
    Files.createDirectories(libDirectory)

    files.forEach { file ->
        val target = libDirectory.resolve(file.targetName)
        if (file.normalizeJar) {
            writeReproducibleJar(file.source, target)
        } else {
            Files.copy(file.source, target)
        }
        setPosixPermissions(target, FILE_PERMISSIONS)
    }

    val launcher = binDirectory.resolve(applicationName)
    Files.writeString(
        launcher,
        renderUnixLauncher(
            applicationName = applicationName,
            mainClass = mainClass,
            jarNames = files.map(DistributionFile::targetName),
            optsEnvironmentVariable = effectiveOptsEnvironmentVariable,
            defaultJvmArgs = defaultJvmArgs,
        ),
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE,
    )
    setPosixPermissions(launcher, EXECUTABLE_PERMISSIONS)
}

internal fun planDistributionFiles(
    applicationJar: Path,
    runtimeClasspath: List<Path>,
    applicationName: String,
    applicationVersion: String,
): List<DistributionFile> {
    val normalizedApplicationJar = applicationJar.toAbsolutePath().normalize()
    require(Files.isRegularFile(normalizedApplicationJar)) {
        "Application JAR does not exist or is not a regular file: $normalizedApplicationJar"
    }

    val applicationTargetName = "$applicationName-$applicationVersion.jar"
    val dependencies = runtimeClasspath
        .asSequence()
        .map { it.toAbsolutePath().normalize() }
        .filterNot { it == normalizedApplicationJar }
        .distinct()
        .map { source ->
            require(Files.isRegularFile(source)) {
                "Runtime classpath entry does not exist or is not a regular file: $source"
            }
            require(source.fileName.toString().endsWith(".jar")) {
                "Runtime classpath entry is not a JAR: $source"
            }
            DistributionFile(
                source = source,
                targetName = distributionJarName(source.fileName.toString()),
                normalizeJar = source.fileName.toString().endsWith("-jvm.jar"),
            )
        }
        .sortedWith(compareBy(DistributionFile::targetName, { it.source.toString() }))
        .toList()

    val files = listOf(
        DistributionFile(
            source = normalizedApplicationJar,
            targetName = applicationTargetName,
            normalizeJar = true,
        ),
    ) + dependencies

    val conflicts = files
        .groupBy(DistributionFile::targetName)
        .filterValues { it.size > 1 }
    require(conflicts.isEmpty()) {
        conflicts.entries.joinToString(
            prefix = "Cannot create distribution because multiple JARs map to the same file name:\n",
            separator = "\n",
        ) { (targetName, sources) ->
            "  $targetName <- ${sources.joinToString { it.source.toString() }}"
        }
    }

    return files
}

internal fun writeReproducibleJar(
    source: Path,
    target: Path,
) {
    val targetParent = requireNotNull(target.toAbsolutePath().normalize().parent) {
        "JAR target must have a parent directory: $target"
    }
    Files.createDirectories(targetParent)
    val temporaryJar = Files.createTempFile(targetParent, ".${target.fileName}.", ".tmp")

    try {
        ZipFile.builder()
            .setPath(source)
            .get()
            .use { input ->
                val entries = input.entries
                    .asSequence()
                    .sortedBy(ZipArchiveEntry::getName)
                    .toList()
                val duplicateNames = entries
                    .groupingBy(ZipArchiveEntry::getName)
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                require(duplicateNames.isEmpty()) {
                    "Cannot normalize JAR with duplicate entries: " +
                        duplicateNames.sorted().joinToString()
                }

                ZipArchiveOutputStream(temporaryJar).use { output ->
                    output.setEncoding("UTF-8")
                    output.setFallbackToUTF8(true)
                    output.setUseLanguageEncodingFlag(true)
                    output.setUseZip64(Zip64Mode.AsNeeded)

                    entries.forEach { original ->
                        val normalized = ZipArchiveEntry(original.name).apply {
                            method = original.method
                            size = original.size
                            compressedSize = original.compressedSize
                            crc = original.crc
                            internalAttributes = original.internalAttributes
                            externalAttributes = original.externalAttributes
                            setTimeLocal(FIXED_ZIP_TIMESTAMP)
                        }
                        input.getRawInputStream(original).use { rawInput ->
                            output.addRawArchiveEntry(normalized, rawInput)
                        }
                    }
                }
            }

        try {
            Files.move(
                temporaryJar,
                target,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryJar, target)
        }
    } finally {
        Files.deleteIfExists(temporaryJar)
    }
}

internal fun distributionJarName(fileName: String): String =
    if (fileName.endsWith("-jvm.jar")) {
        fileName.removeSuffix("-jvm.jar") + ".jar"
    } else {
        fileName
    }

internal fun defaultOptsEnvironmentVariable(applicationName: String): String {
    validateSafeApplicationName(applicationName)
    return applicationName
        .uppercase()
        .map { character ->
            if (character.isLetterOrDigit()) character else '_'
        }
        .joinToString(separator = "") + "_OPTS"
}

internal fun renderUnixLauncher(
    applicationName: String,
    mainClass: String,
    jarNames: List<String>,
    optsEnvironmentVariable: String,
    defaultJvmArgs: List<String>,
): String {
    validateSafeApplicationName(applicationName)
    validateMainClass(mainClass)
    validateEnvironmentVariable(optsEnvironmentVariable)
    require(jarNames.isNotEmpty()) {
        "A distribution launcher requires at least one JAR."
    }
    jarNames.forEach(::validateJarName)
    defaultJvmArgs.forEach(::validateJvmArgument)

    val classpath = jarNames.joinToString(separator = ":") { jarName ->
        "\$APP_HOME/lib/$jarName"
    }
    val defaultOptions = shellSingleQuote(
        defaultJvmArgs.joinToString(separator = " ") { argument ->
            xargsEscape(argument)
        },
    )

    return buildString {
        appendLine("#!/bin/sh")
        appendLine()
        appendLine("#")
        appendLine("# Copyright © 2015 the original Gradle authors.")
        appendLine("#")
        appendLine("# Licensed under the Apache License, Version 2.0 (the \"License\");")
        appendLine("# you may not use this file except in compliance with the License.")
        appendLine("# You may obtain a copy of the License at")
        appendLine("#")
        appendLine("#      https://www.apache.org/licenses/LICENSE-2.0")
        appendLine("#")
        appendLine("# Unless required by applicable law or agreed to in writing, software")
        appendLine("# distributed under the License is distributed on an \"AS IS\" BASIS,")
        appendLine("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.")
        appendLine("# See the License for the specific language governing permissions and")
        appendLine("# limitations under the License.")
        appendLine("#")
        appendLine("# SPDX-License-Identifier: Apache-2.0")
        appendLine("#")
        appendLine("# Adapted from Gradle's POSIX start-script template:")
        appendLine("# https://github.com/gradle/gradle/blob/3d91ce3b8caaf77ad09f381f43615b715b53f72c/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt")
        appendLine()
        appendLine("app_path=\$0")
        appendLine("while")
        appendLine("    APP_HOME=\${app_path%\"\${app_path##*/}\"}")
        appendLine("    [ -h \"\$app_path\" ]")
        appendLine("do")
        appendLine("    link=\$(ls -ld \"\$app_path\")")
        appendLine("    link=\${link#*' -> '}")
        appendLine("    case \$link in")
        appendLine("      /*) app_path=\$link ;;")
        appendLine("      *)  app_path=\$APP_HOME\$link ;;")
        appendLine("    esac")
        appendLine("done")
        appendLine("APP_HOME=\$(cd -P \"\${APP_HOME:-./}..\" >/dev/null && printf '%s\\n' \"\$PWD\") || exit 1")
        appendLine()
        appendLine("die () {")
        appendLine("    printf '\\n%s\\n\\n' \"\$*\" >&2")
        appendLine("    exit 1")
        appendLine("}")
        appendLine()
        appendLine("if [ -n \"\${JAVA_HOME:-}\" ]; then")
        appendLine("    JAVACMD=\$JAVA_HOME/bin/java")
        appendLine("    [ -x \"\$JAVACMD\" ] || die \"ERROR: JAVA_HOME does not contain an executable bin/java: \$JAVA_HOME\"")
        appendLine("else")
        appendLine("    JAVACMD=java")
        appendLine("    command -v java >/dev/null 2>&1 || die \"ERROR: JAVA_HOME is not set and java is not available on PATH.\"")
        appendLine("fi")
        appendLine()
        appendLine("CLASSPATH=\"$classpath\"")
        appendLine("DEFAULT_JVM_OPTS=$defaultOptions")
        appendLine("JAVA_OPTS_VALUE=\${JAVA_OPTS:-}")
        appendLine("APP_OPTS_VALUE=\${$optsEnvironmentVariable:-}")
        appendLine()
        appendLine("set -- -classpath \"\$CLASSPATH\" $mainClass \"\$@\"")
        appendLine()
        appendLine("command -v xargs >/dev/null 2>&1 || die \"ERROR: xargs is required to parse JVM options.\"")
        appendLine("eval \"set -- \$(")
        appendLine("    printf '%s\\n' \"\$DEFAULT_JVM_OPTS \$JAVA_OPTS_VALUE \$APP_OPTS_VALUE\" |")
        appendLine("        xargs -n1 |")
        appendLine("        sed ' s~[^-[:alnum:]+,./:=@_]~\\\\&~g; ' |")
        appendLine("        tr '\\n' ' '")
        appendLine(")\" '\"\$@\"'")
        appendLine()
        appendLine("exec \"\$JAVACMD\" \"\$@\"")
    }
}

internal fun createReproducibleTar(
    installDirectory: Path,
    applicationName: String,
    archiveFile: Path,
) {
    validateInstallTarget(installDirectory, applicationName)
    require(Files.isDirectory(installDirectory, LinkOption.NOFOLLOW_LINKS)) {
        "Installed distribution does not exist: $installDirectory"
    }

    val archiveParent = requireNotNull(archiveFile.toAbsolutePath().normalize().parent) {
        "Archive must have a parent directory: $archiveFile"
    }
    Files.createDirectories(archiveParent)
    val temporaryArchive = Files.createTempFile(archiveParent, ".${archiveFile.fileName}.", ".tmp")

    try {
        TarArchiveOutputStream(Files.newOutputStream(temporaryArchive)).use { output ->
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_ERROR)
            output.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_ERROR)
            output.setAddPaxHeadersForNonAsciiNames(false)

            val paths = Files.walk(installDirectory)
                .use { stream ->
                    stream
                        .sorted(compareBy { installDirectory.relativize(it).toString().replace('\\', '/') })
                        .toList()
                }

            paths.forEach { path ->
                require(!Files.isSymbolicLink(path)) {
                    "Symbolic links are not supported in distributions: $path"
                }
                val isDirectory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                require(isDirectory || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    "Distribution entry is not a regular file or directory: $path"
                }

                val relativeName = installDirectory
                    .relativize(path)
                    .toString()
                    .replace('\\', '/')
                val entryName = buildString {
                    append(applicationName)
                    append('/')
                    if (relativeName.isNotEmpty()) {
                        append(relativeName)
                        if (isDirectory) append('/')
                    }
                }
                val entry = TarArchiveEntry(entryName).apply {
                    mode = if (isDirectory || relativeName == "bin/$applicationName") {
                        EXECUTABLE_MODE
                    } else {
                        FILE_MODE
                    }
                    userId = 0
                    groupId = 0
                    userName = ""
                    groupName = ""
                    modTime = Date(FIXED_TIMESTAMP_MILLIS)
                    size = if (isDirectory) 0 else Files.size(path)
                }

                output.putArchiveEntry(entry)
                if (!isDirectory) {
                    Files.newInputStream(path).use { input ->
                        input.copyTo(output)
                    }
                }
                output.closeArchiveEntry()
            }
            output.finish()
        }

        try {
            Files.move(
                temporaryArchive,
                archiveFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryArchive,
                archiveFile,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        setPosixPermissions(archiveFile, FILE_PERMISSIONS)
    } finally {
        Files.deleteIfExists(temporaryArchive)
    }
}

private fun validateInstallTarget(
    installDirectory: Path,
    applicationName: String,
) {
    val normalized = installDirectory.toAbsolutePath().normalize()
    require(normalized.fileName?.toString() == applicationName) {
        "Refusing to manage install directory outside the configured application path: $installDirectory"
    }
    require(normalized.parent?.fileName?.toString() == "install") {
        "Install directory must be under build/install: $installDirectory"
    }
    require(normalized.parent?.parent?.fileName?.toString() == "build") {
        "Install directory must be under build/install: $installDirectory"
    }
}

private fun validateArchiveTarget(
    archiveFile: Path,
    archiveFileName: String,
) {
    require(archiveFileName.matches(SAFE_ARCHIVE_NAME)) {
        "Archive file name must be a simple .tar file name: $archiveFileName"
    }
    val normalized = archiveFile.toAbsolutePath().normalize()
    require(normalized.fileName?.toString() == archiveFileName) {
        "Archive output does not match archiveFileName: $archiveFile"
    }
    require(normalized.parent?.fileName?.toString() == "distributions") {
        "Archive output must be under build/distributions: $archiveFile"
    }
    require(normalized.parent?.parent?.fileName?.toString() == "build") {
        "Archive output must be under build/distributions: $archiveFile"
    }
}

private fun deleteOwnedDirectory(directory: Path) {
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return
    if (Files.isSymbolicLink(directory)) {
        Files.delete(directory)
        return
    }
    require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        "Install output exists but is not a directory: $directory"
    }
    Files.walk(directory).use { stream ->
        stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
    }
}

private fun setPosixPermissions(
    path: Path,
    permissions: String,
) {
    if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) != null) {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions))
    }
}

private fun validateSafeApplicationName(applicationName: String) {
    require(applicationName.matches(SAFE_APPLICATION_NAME)) {
        "Application name contains unsupported characters: $applicationName"
    }
}

private fun validateSafeVersion(version: String) {
    require(version.matches(SAFE_VERSION)) {
        "Application version contains unsupported characters: $version"
    }
}

private fun validateEnvironmentVariable(environmentVariable: String) {
    require(environmentVariable.matches(SAFE_ENVIRONMENT_VARIABLE)) {
        "Invalid JVM options environment variable: $environmentVariable"
    }
}

private fun validateMainClass(mainClass: String) {
    require(mainClass.matches(SAFE_MAIN_CLASS)) {
        "Invalid JVM main class: $mainClass"
    }
}

private fun validateJarName(jarName: String) {
    require(jarName.matches(SAFE_JAR_NAME)) {
        "Distribution JAR name contains unsupported characters: $jarName"
    }
}

private fun validateJvmArgument(argument: String) {
    require('\n' !in argument && '\r' !in argument && '\u0000' !in argument) {
        "JVM arguments cannot contain newlines or NUL characters."
    }
}

private fun xargsEscape(value: String): String =
    buildString {
        value.forEach { character ->
            if (character.isLetterOrDigit() || character in XARGS_SAFE_CHARACTERS) {
                append(character)
            } else {
                append('\\')
                append(character)
            }
        }
    }

private fun shellSingleQuote(value: String): String =
    "'" + value.replace("'", "'\"'\"'") + "'"

private val SAFE_APPLICATION_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private val SAFE_VERSION = Regex("[A-Za-z0-9][A-Za-z0-9._+-]*")
private val SAFE_ARCHIVE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.tar")
private val SAFE_ENVIRONMENT_VARIABLE = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val SAFE_MAIN_CLASS = Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
private val SAFE_JAR_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._+-]*\\.jar")

private const val EXECUTABLE_MODE = 0b111101101
private const val FILE_MODE = 0b110100100
private const val EXECUTABLE_PERMISSIONS = "rwxr-xr-x"
private const val FILE_PERMISSIONS = "rw-r--r--"
private const val FIXED_TIMESTAMP_MILLIS = 0L
private const val XARGS_SAFE_CHARACTERS = "-+,./:=@_"
private val FIXED_ZIP_TIMESTAMP: LocalDateTime = LocalDateTime.of(1980, 1, 1, 0, 0)
