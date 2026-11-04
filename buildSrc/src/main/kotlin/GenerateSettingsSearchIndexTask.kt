import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateSettingsSearchIndexTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val navGraph: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val screenSources: ConfigurableFileCollection

    @get:Internal
    abstract val sourceRoot: DirectoryProperty

    @get:Input
    abstract val settingsPathPrefix: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val (source, result) = SettingsSearchIndexGenerator.generate(
            navGraph = navGraph.get().asFile,
            screenSources = screenSources.files.filter { it.extension == "kt" },
            sourceRoot = sourceRoot.get().asFile,
            settingsPathPrefix = settingsPathPrefix.get(),
            packageName = packageName.get()
        )
        check(result.unreachable.isEmpty()) {
            "Settings rows that search cannot reach from any route:\n" +
                result.unreachable.joinToString("\n")
        }
        check(result.entries.isNotEmpty()) { "The settings search index is empty" }
        val target = outputDir.get().asFile
            .resolve(packageName.get().replace('.', '/'))
            .resolve("screens/settings/search/GeneratedSettingsSearchIndex.kt")
        target.parentFile.mkdirs()
        target.writeText(source)
        logger.lifecycle("Settings search index: ${result.entries.size} entries")
    }
}
