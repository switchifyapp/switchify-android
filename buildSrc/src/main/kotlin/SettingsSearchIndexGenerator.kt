import java.io.File

object SettingsSearchIndexGenerator {

    data class Entry(
        val route: String,
        val tab: Int?,
        val screenTitle: String?,
        val sectionTitle: String?,
        val title: String,
        val summary: String?,
        val explanation: String?
    )

    data class Result(val entries: List<Entry>, val unreachable: List<String>)

    private data class FunctionDef(val name: String, val path: String, val params: String, val body: String)

    private val rowComponentSeeds = setOf("PreferenceComponentBase", "PanelListRow", "Picker")

    private data class Context(
        val route: String,
        val tab: Int?,
        val screenTitle: String?,
        val sectionTitle: String?,
        val visited: Set<String>
    )

    private data class Call(val name: String, val args: String, val lambda: String?, val end: Int)

    private val callStart = Regex("""\b([A-Za-z_]\w*)\s*\(""")
    private val tabBranch = Regex("""\b(\d+)\s*->\s*([A-Z]\w*)\s*\(""")
    private val stringRef = Regex("""\bR\.string\.(\w+)""")
    private val titleRef = Regex("""\btitleResId\s*=\s*R\.string\.(\w+)""")
    private val functionStart = Regex("""\bfun\s+(?:<[^>]*>\s+)?(?:[\w.]+\.)?(\w+)\s*\(""")
    private val composableRoute =
        Regex("""composable\(\s*(?:NavigationRoute\.(\w+)\.name|"\$\{NavigationRoute\.(\w+)\.name}[^"]*")""")

    fun generate(
        navGraph: File,
        screenSources: List<File>,
        sourceRoot: File,
        settingsPathPrefix: String,
        packageName: String
    ): Pair<String, Result> {
        val files = screenSources.associate { file ->
            file.relativeTo(sourceRoot).invariantSeparatorsPath to stripComments(file.readText())
        }
        val functions = files.flatMap { (path, text) -> parseFunctions(path, text) }
            .groupBy { it.name }
        val rowComponents = rowComponents(functions)
        val reached = mutableSetOf<Pair<String, String>>()
        val entries = LinkedHashMap<Triple<String, Int?, String>, Entry>()
        val navGraphText = stripComments(navGraph.readText())

        composableRoute.findAll(navGraphText).forEach { match ->
            val route = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val hasArgs = match.groupValues[1].isEmpty()
            val call = parseCall(navGraphText, match.range.first) ?: return@forEach
            val body = call.lambda ?: return@forEach
            val roots = callStart.findAll(body)
                .map { it.groupValues[1] }
                .flatMap { functions[it].orEmpty() }
                .toList()
            val searchable = !hasArgs && roots.any { it.path.startsWith(settingsPathPrefix) }
            val sink: (Entry) -> Unit = { entry ->
                if (searchable && entry.screenTitle != null) {
                    entries.putIfAbsent(Triple(entry.route, entry.tab, entry.title), entry)
                }
            }
            roots.forEach { root ->
                walk(
                    root.body,
                    root.path,
                    Context(route, null, null, null, setOf(root.name)),
                    functions,
                    rowComponents,
                    reached,
                    sink
                )
            }
        }

        val unreachable = files.filterKeys { it.startsWith(settingsPathPrefix) }
            .flatMap { (path, text) ->
                titleRef.findAll(text).map { path to it.groupValues[1] }
            }
            .filterNot { it in reached }
            .map { (path, name) -> "$path: R.string.$name" }
            .distinct()

        val result = Result(entries.values.toList(), unreachable)
        return render(packageName, result.entries) to result
    }

    private fun rowComponents(functions: Map<String, List<FunctionDef>>): Set<String> {
        val rows = rowComponentSeeds.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            functions.values.flatten().forEach { def ->
                if (def.name in rows || "titleResId" !in def.params) return@forEach
                val callsRow = callStart.findAll(def.body).any { it.groupValues[1] in rows }
                if (callsRow) {
                    rows += def.name
                    changed = true
                }
            }
        }
        return rows
    }

    private fun walk(
        text: String,
        path: String,
        context: Context,
        functions: Map<String, List<FunctionDef>>,
        rowComponents: Set<String>,
        reached: MutableSet<Pair<String, String>>,
        sink: (Entry) -> Unit
    ) {
        val tabs = tabBranch.findAll(text).associate { it.groupValues[2] to it.groupValues[1].toInt() }
        var cursor = 0
        while (cursor < text.length) {
            val match = callStart.find(text, cursor) ?: return
            val call = parseCall(text, match.range.first)
            if (call == null) {
                cursor = match.range.last + 1
                continue
            }
            cursor = call.end
            val titles = resourceRefs(call.args, "titleResId")
            titles.forEach { reached += path to it }
            val next: Context = when {
                call.name == "Section" && titles.isNotEmpty() ->
                    context.copy(sectionTitle = titles.first())
                call.name == "BaseView" && titles.isNotEmpty() ->
                    context.copy(screenTitle = titles.first())
                call.name in rowComponents && titles.isNotEmpty() -> {
                    val summaries = resourceRefs(call.args, "summaryResId")
                    val explanations = resourceRefs(call.args, "explanationResId")
                    titles.forEachIndexed { index, title ->
                        sink(
                            Entry(
                                route = context.route,
                                tab = context.tab,
                                screenTitle = context.screenTitle,
                                sectionTitle = context.sectionTitle,
                                title = title,
                                summary = summaries.getOrNull(index) ?: summaries.firstOrNull(),
                                explanation = explanations.getOrNull(index) ?: explanations.firstOrNull()
                            )
                        )
                    }
                    context
                }
                else -> context
            }
            val callee = functions[call.name].orEmpty()
            if (callee.isNotEmpty() && call.name !in context.visited) {
                val calleeContext = next.copy(
                    tab = tabs[call.name] ?: next.tab,
                    visited = next.visited + call.name
                )
                callee.forEach { def ->
                    walk(def.body, def.path, calleeContext, functions, rowComponents, reached, sink)
                }
            }
            walk(call.args, path, next, functions, rowComponents, reached, sink)
            call.lambda?.let { walk(it, path, next, functions, rowComponents, reached, sink) }
        }
    }

    private fun resourceRefs(args: String, argName: String): List<String> =
        splitTopLevel(args)
            .mapNotNull { arg ->
                val trimmed = arg.trim()
                val name = trimmed.substringBefore('=').trim()
                if (name == argName && '=' in trimmed) trimmed.substringAfter('=') else null
            }
            .flatMap { value -> stringRef.findAll(value).map { it.groupValues[1] } }

    private fun parseFunctions(path: String, text: String): List<FunctionDef> =
        functionStart.findAll(text).mapNotNull { match ->
            val parenStart = match.range.last
            val parenEnd = matchDelimiter(text, parenStart, '(', ')') ?: return@mapNotNull null
            val braceStart = findBodyStart(text, parenEnd + 1) ?: return@mapNotNull null
            val braceEnd = matchDelimiter(text, braceStart, '{', '}') ?: return@mapNotNull null
            FunctionDef(
                match.groupValues[1],
                path,
                text.substring(parenStart + 1, parenEnd),
                text.substring(braceStart + 1, braceEnd)
            )
        }.toList()

    private fun findBodyStart(text: String, from: Int): Int? {
        var index = from
        var depth = 0
        while (index < text.length) {
            when (text[index]) {
                '<' -> depth++
                '>' -> depth--
                '{' -> if (depth == 0) return index
                '=' -> if (depth == 0) return null
                ';' -> return null
                '\n' -> if (depth == 0 && text.substring(from, index).isBlank().not() &&
                    text.substring(from, index).trimEnd().last() !in ":,>"
                ) {
                    return null
                }
            }
            index++
        }
        return null
    }

    private fun parseCall(text: String, start: Int): Call? {
        val match = callStart.find(text, start)?.takeIf { it.range.first == start } ?: return null
        val name = match.groupValues[1]
        val parenStart = match.range.last
        val parenEnd = matchDelimiter(text, parenStart, '(', ')') ?: return null
        var index = parenEnd + 1
        while (index < text.length && text[index].isWhitespace()) index++
        return if (index < text.length && text[index] == '{') {
            val braceEnd = matchDelimiter(text, index, '{', '}') ?: return null
            Call(name, text.substring(parenStart + 1, parenEnd), text.substring(index + 1, braceEnd), braceEnd + 1)
        } else {
            Call(name, text.substring(parenStart + 1, parenEnd), null, parenEnd + 1)
        }
    }

    private fun matchDelimiter(text: String, start: Int, open: Char, close: Char): Int? {
        var depth = 0
        var index = start
        while (index < text.length) {
            when (text[index]) {
                '"' -> index = skipString(text, index)
                '\'' -> index = skipChar(text, index)
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun splitTopLevel(args: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var last = 0
        var index = 0
        while (index < args.length) {
            when (args[index]) {
                '"' -> index = skipString(args, index)
                '\'' -> index = skipChar(args, index)
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) {
                    parts += args.substring(last, index)
                    last = index + 1
                }
            }
            index++
        }
        parts += args.substring(last)
        return parts
    }

    private fun skipString(text: String, start: Int): Int {
        if (text.startsWith("\"\"\"", start)) {
            val end = text.indexOf("\"\"\"", start + 3)
            return if (end == -1) text.length - 1 else end + 2
        }
        var index = start + 1
        var templateDepth = 0
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index++
                '$' -> if (text.getOrNull(index + 1) == '{') {
                    templateDepth++
                    index++
                }
                '}' -> if (templateDepth > 0) templateDepth--
                '"' -> if (templateDepth == 0) return index
            }
            index++
        }
        return text.length - 1
    }

    private fun skipChar(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length && text[index] != '\'') {
            if (text[index] == '\\') index++
            index++
        }
        return index
    }

    private fun stripComments(text: String): String {
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("//", index) -> {
                    while (index < text.length && text[index] != '\n') index++
                }
                text.startsWith("/*", index) -> {
                    val end = text.indexOf("*/", index + 2)
                    index = if (end == -1) text.length else end + 2
                }
                text[index] == '"' -> {
                    val end = skipString(text, index)
                    out.append(text, index, minOf(end + 1, text.length))
                    index = end + 1
                }
                text[index] == '\'' -> {
                    val end = skipChar(text, index)
                    out.append(text, index, minOf(end + 1, text.length))
                    index = end + 1
                }
                else -> {
                    out.append(text[index])
                    index++
                }
            }
        }
        return out.toString()
    }

    private fun render(packageName: String, entries: List<Entry>): String = buildString {
        appendLine("package $packageName.screens.settings.search")
        appendLine()
        appendLine("import $packageName.R")
        appendLine()
        appendLine("object GeneratedSettingsSearchIndex {")
        appendLine("    val entries: List<SettingsSearchEntry> = listOf(")
        entries.forEachIndexed { index, entry ->
            appendLine("        SettingsSearchEntry(")
            appendLine("            route = \"${entry.route}\",")
            appendLine("            tab = ${entry.tab},")
            appendLine("            screenTitleResId = ${resource(entry.screenTitle)},")
            appendLine("            sectionTitleResId = ${resource(entry.sectionTitle)},")
            appendLine("            titleResId = ${resource(entry.title)},")
            appendLine("            summaryResId = ${resource(entry.summary)},")
            appendLine("            explanationResId = ${resource(entry.explanation)}")
            appendLine("        )" + if (index < entries.lastIndex) "," else "")
        }
        appendLine("    )")
        appendLine("}")
    }

    private fun resource(name: String?): String = if (name == null) "null" else "R.string.$name"
}
