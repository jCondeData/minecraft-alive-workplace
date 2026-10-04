// The build of one Minecraft version node (versions/<mc>/), shared by all of them. Stonecutter writes the sources for
// each node from src/ (see stonecutter.gradle.kts); per-node values come from stonecutter.properties.toml. Branch on
// what a node declares there or on sc.current.parsed, never on a hard-coded version.
plugins {
    // fabric-loom-remap (obfuscated Minecraft, up to 1.21.11) or fabric-loom (26.1+), with loomx.loom_version.
    id("dev.kikugie.loom-back-compat")
    // Uploads the jar to CurseForge (ROADMAP 26.3). Without a token it only dry-runs: `./gradlew publishMods`.
    id("me.modmuss50.mod-publish-plugin")
}

val modId = property("mod.id").toString()
val testModId = "${modId}_test"
val modVersion = property("mod.version").toString()
val mc = sc.current.parsed

// DO NOT set `group`: Stonecutter uses it for the nodes' identity.
version = "$modVersion+${sc.current.version}"
base.archivesName = property("mod.archives").toString()

val requiredJava: JavaVersion = when {
    mc >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

/** A list from stonecutter.properties.toml (this node's section first), or empty. */
fun tomlList(table: String, key: String): List<String> =
    sc.properties.rawOrNull(table, key)?.asList().orEmpty().map { it.toString() }

/** deps.compat.<modid> = "<maven coordinate>": the optional integrations this node compiles against. */
val compatMods: Map<String, String> = extra.properties
    .filterKeys { it.startsWith("deps.compat.") }
    .map { (k, v) -> k.removePrefix("deps.compat.") to v.toString() }.toMap()
val compatCompile: List<String> = extra.properties
    .filterKeys { it.startsWith("deps.compat_compile.") }
    .values.flatMap { it.toString().split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
/**
 * -Pcobblemon18=true runs the compat suite, the screenshot harness and the showcase with Cobblemon 1.8 instead of the
 * pack's 1.7.3 (the mod still compiles against 1.7.3). tests.cobblemon18 lists "old -> new" swaps of runtime mods;
 * "old -> " leaves a mod out of that run (each with its reason in stonecutter.properties.toml).
 */
val cobblemon18 = findProperty("cobblemon18")?.toString() == "true"
val cobblemon18Swaps: Map<String, String> = tomlList("tests", "cobblemon18").associate { line ->
    val (from, to) = line.split("->", limit = 2).map { it.trim() } + listOf("")
    from to to
}
fun forCobblemon(mods: List<String>): List<String> =
    if (!cobblemon18) mods else mods.mapNotNull { mod -> cobblemon18Swaps[mod]?.ifEmpty { null } ?: mod.takeUnless { it in cobblemon18Swaps } }
/** The mods the compat test suite runs with (only nodes that declare tests.compat_mods have that suite). */
val compatTestMods: List<String> = forCobblemon(tomlList("tests", "compat_mods"))
/** Whether this node has the screenshot harness (src/devclient). */
val screenshots = findProperty("tests.screenshots")?.toString() == "true"

// Kotlin-written mods (Cobblemon) need Loom's Kotlin-metadata remapping to run in dev on obfuscated versions, or they
// crash with ClassNotFoundException: net.minecraft.class_…; Loom only does that with the Kotlin plugin applied. We write
// no Kotlin, and kotlin.stdlib.default.dependency=false keeps the stdlib out of the mod.
if (compatTestMods.isNotEmpty() && mc < "26.1") {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}

repositories {
    // Other mods, only for the integrations (compile-only) and the compatibility tests (never bundled, never required).
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

loom {
    // Client-only code lives in src/client; the compiler then keeps the server side from touching it.
    splitEnvironmentSourceSets()
    // Loom reads the mod id from here while configuring (before Stonecutter has written the node's resources).
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
}

val main: SourceSet = sourceSets["main"]
val client: SourceSet = sourceSets["client"]

// Dev-only screenshot harness (tools/screenshots/README.md). Never packaged in the mod jar.
val devclient: SourceSet? = if (screenshots) sourceSets.create("devclient") {
    compileClasspath += main.compileClasspath + main.output + client.compileClasspath + client.output
    runtimeClasspath += main.runtimeClasspath + main.output + client.runtimeClasspath + client.output
} else null

// Compatibility tests: in-game tests that run with the pack's other mods installed. `runCompatGameTest`; part of `build`.
val compattest: SourceSet? = if (compatTestMods.isNotEmpty()) sourceSets.create("compattest") {
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath + main.output
} else null

compattest?.let { loom.createRemapConfigurations(it) }
devclient?.let { loom.createRemapConfigurations(it) }

loom {
    mods {
        register(modId) {
            sourceSet(main)
            sourceSet(client)
        }
        devclient?.let { set -> register("${modId}_devclient") { sourceSet(set) } }
        compattest?.let { set -> register("${modId}_compat") { sourceSet(set) } }
    }

    runs {
        if (devclient != null) {
            // Opens the "shots" world, stages a scene, saves screenshots to run/screenshots/screenshots/ and quits.
            // Needs a display: use xvfb-run on a server (tools/screenshots/run.sh does).
            register("screenshots") {
                client()
                name = "Screenshot Client"
                source(devclient)
                runDir = "run/screenshots"
                // GUI_SCALE=3 or 4 (tools/screenshots/run.sh): a 1920x1080 window, big enough for those scales
                val big = (findProperty("guiScale")?.toString()?.toIntOrNull() ?: 2) >= 3
                programArgs("--quickPlaySingleplayer", "shots", "--width", if (big) "1920" else "960", "--height", if (big) "1080" else "540")
                vmArg("-Daliveworkplace.shots=true")
                // ./gradlew runScreenshots -Pscene=table   (the scenes are listed in CLAUDE.md)
                vmArg("-Daliveworkplace.scene=${findProperty("scene") ?: "builders"}")
                // SCENE=village: make workshops common so every generated village shows one
                vmArg("-Daliveworkplace.workshopWeight=${findProperty("workshopWeight") ?: 3}")
                // ... and HOUSE_WEIGHT the other staffed houses (trainer's house, guard house, clinic, post office)
                findProperty("houseWeight")?.let { vmArg("-Daliveworkplace.houseWeight=$it") }
                vmArg("-Daliveworkplace.debug=${findProperty("builderDebug") ?: "false"}")
            }
        }
        if (compattest != null) {
            register("compatGameTest") {
                server()
                name = "Compat Game Test"
                source(compattest)
                runDir = "build/run/compatGameTest"
                vmArg("-Dfabric-api.gametest")
                vmArg("-Dfabric-api.gametest.report-file=${layout.buildDirectory.get().asFile}/junit-compat.xml")
                ideConfigGenerated(false)
            }
        }
    }
}

fabricApi {
    // A "gametest" source set and a `runGameTest` task that boots a headless server, runs every @GameTest and fails
    // the build if one fails. CI runs it (through `build`).
    configureTests {
        createSourceSet = true
        modId = testModId
        enableGameTests = true
        enableClientGameTests = false
        eula = true
    }
}

// A JUnit report of the game tests, by test name, for tools/modtest/results.py (the baseline, flaky tests).
loom.runs.matching { it.name == "gameTest" }.configureEach {
    vmArg("-Dfabric-api.gametest.report-file=${layout.buildDirectory.get().asFile}/junit.xml")
}

/** Unpacks the jars nested in mods (META-INF/jars, recursively) into build/compat-nested: Loom drops them in dev. */
fun nestedJars(mods: List<String>): List<File> {
    val out = layout.buildDirectory.dir("compat-nested").get().asFile
    val resolved = configurations.detachedConfiguration(*mods.map { dependencies.create(it) }.toTypedArray())
    resolved.isTransitive = false
    val jars = mutableListOf<File>()
    fun unpack(mod: File) {
        zipTree(mod).matching { include("META-INF/jars/*.jar") }.forEach { nested ->
            // owo-sentinel only warns that owo-lib is missing, and refuses to load next to it.
            if (nested.name.startsWith("owo-sentinel")) return@forEach
            // C2ME's native maths needs Java 25, and its density function compiler needs that: on Java 21 the loader
            // leaves both nested modules out of a real install (the pack test's mod list); flattened here, they would
            // stop the game (25.4).
            if (nested.name.startsWith("c2me-fabric-opts-natives-math") || nested.name.startsWith("c2me-fabric-opts-dfc")) return@forEach
            val target = File(out, nested.name)
            if (!target.exists()) {
                out.mkdirs()
                target.writeBytes(nested.readBytes())
            }
            jars += target
            // Nested jars can nest jars of their own (Cobblemon → Fabric Language Kotlin → the Kotlin libraries).
            unpack(target)
        }
    }
    resolved.resolve().forEach { unpack(it) }
    return jars
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings on obfuscated versions; nothing on 26.1+.
    loomx.applyMojangMappings()
    // mod* configurations on every node; loom-back-compat turns them into the plain ones on 26.1+.
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Optional integrations: compiled against for compat/<modid>, never a runtime dependency.
    compatMods.values.forEach { modCompileOnly(it) }
    compatCompile.forEach { compileOnly(it) }

    // Only loaded by runCompatGameTest.
    if (compattest != null) {
        compatTestMods.forEach { "modCompattestRuntimeOnly"(it) }
        // Loom drops the libraries these mods bundle inside themselves, so add those back.
        "modCompattestRuntimeOnly"(files(nestedJars(compatTestMods)))
    }

    if (devclient != null) {
        // Screenshots of Cobblemon features (SCENE=tutor): ./gradlew runScreenshots -Pcobblemon=true
        // SCENE=battle: Mega Showdown too (-Pmega=true), for a Master trainer's Mega Evolution.
        // PERF_STACK=true (-PperfStack=true): the pack's performance mods too (ROADMAP 25.4).
        listOf("cobblemon" to "screenshot_cobblemon", "mega" to "screenshot_mega", "perfStack" to "screenshot_perf").forEach { (flag, key) ->
            if (findProperty(flag) == "true") {
                val mods = forCobblemon(tomlList("tests", key))
                mods.forEach { "modDevclientRuntimeOnly"(it) }
                "modDevclientRuntimeOnly"(files(nestedJars(mods)))
            }
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    // Each node compiles and runs (game tests, clients) with its own Java, whatever JDK Gradle runs on.
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

/** Values substituted into fabric.mod.json. */
val resourceProps: Map<String, String> = mapOf(
    "version" to modVersion,
    "minecraft" to property("mod.mc_compat").toString(),
    "java" to requiredJava.majorVersion,
)

/**
 * Per-version resource files, for when a data format changes between Minecraft versions: name the variants
 * `<name>.mc<version>.<ext>` with the version's dots as underscores (`thing.mc1_21.json`, `thing.mc1_21_2.json`). Each
 * node ships the variant with the highest version up to its own, renamed to `<name>.<ext>`, and leaves the others out.
 * Maps resource path → target path ("" = leave out).
 */
fun resourceVariants(sourceSet: String): Map<String, String> {
    val root = rootProject.file("src/$sourceSet/resources")
    if (!root.isDirectory) return emptyMap()
    val pattern = Regex("""^(.+)\.mc(\d+(?:_\d+)*)\.([A-Za-z0-9]+)$""")
    data class Variant(val path: String, val target: String, val since: String)
    return root.walkTopDown().filter { it.isFile }.mapNotNull { f ->
        val rel = f.relativeTo(root).invariantSeparatorsPath
        pattern.matchEntire(rel)?.let { m -> Variant(rel, "${m.groupValues[1]}.${m.groupValues[3]}", m.groupValues[2].replace('_', '.')) }
    }.groupBy { it.target }.flatMap { (_, group) ->
        val chosen = group.filter { mc >= it.since }.maxByOrNull { v ->
            v.since.split('.').map(String::toInt).let { p -> (0 until 4).fold(0L) { acc, i -> acc * 1000 + p.getOrElse(i) { 0 } } }
        }
        group.map { it.path to (if (it == chosen) it.target else "") }
    }.toMap()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
        options.encoding = "UTF-8"
    }

    withType<ProcessResources>().configureEach {
        val props = resourceProps
        props.forEach { (k, v) -> inputs.property(k, v) }
        filesMatching("fabric.mod.json") { expand(props) }
        val variants = resourceVariants(if (name == "processResources") "main" else
            name.removePrefix("process").removeSuffix("Resources").replaceFirstChar { it.lowercase() })
        inputs.property("resourceVariants", variants.toString())
        eachFile {
            val target = variants[path] ?: return@eachFile
            if (target.isEmpty()) exclude() else path = target
        }
    }

    named<Jar>("jar") {
        val archives = base.archivesName.get()
        inputs.property("archives", archives)
        from(rootProject.file("LICENSE")) { rename { "${it}_$archives" } }
    }

    // -PbuilderDebug=true on runGameTest logs builder decisions (see BuilderWork.DEBUG)
    matching { it.name == "runGameTest" }.configureEach {
        if (findProperty("builderDebug") == "true") {
            (this as JavaExec).jvmArgs("-Daliveworkplace.debug=true")
        }
    }

    // The compat test server needs an accepted EULA in its run directory, like runGameTest; `build` runs it.
    matching { it.name == "runCompatGameTest" }.configureEach {
        val dir = layout.buildDirectory.dir("run/compatGameTest").get().asFile
        doFirst {
            dir.mkdirs()
            File(dir, "eula.txt").writeText("eula=true\n")
        }
    }
    if (compattest != null) {
        named("build") { dependsOn("runCompatGameTest") }
    }

    /**
     * Layer rules (CLAUDE.md, "Layers"). Every package outside platform/fabric/, compat/ and mixin/ is "core": it may use
     * Minecraft, the JDK, the libraries Minecraft ships and our own code, but reaches the mod loader only through
     * platform/ (Platform), other mods only through compat/ (extension points), and keeps version switches (//? guards)
     * in mc/ and platform/. This keeps new Minecraft versions, Fabric API changes and other mods' updates to one small
     * place each. The same rules hold on every node, so the check reads the checked-in sources.
     */
    val checkLayers by registering {
        group = "verification"
        description = "Fails if a feature package uses loader or other-mod code directly, or contains a version switch"
        val root = rootProject.file("src/main/java/io/github/jcondedata/aliveworkplace")
        val sources = rootProject.fileTree(root) { include("**/*.java") }
        inputs.files(sources)
        val report = layout.buildDirectory.file("checkLayers.txt")
        outputs.file(report)
        doLast {
            val allowed = listOf("java.", "javax.", "net.minecraft.", "com.mojang.", "com.google.gson.", "com.google.common.",
                "org.jetbrains.annotations.", "org.slf4j.", "org.joml.", "it.unimi.dsi.fastutil.", "io.github.jcondedata.aliveworkplace.")
            val problems = mutableListOf<String>()
            sources.files.sorted().forEach { f ->
                val rel = f.relativeTo(root).invariantSeparatorsPath
                if (rel.startsWith("platform/fabric/") || rel.startsWith("compat/") || rel.startsWith("mixin/")) return@forEach
                val guardsAllowed = rel.startsWith("mc/") || rel.startsWith("platform/")
                var inComment = false
                f.readLines().forEachIndexed { i, line ->
                    val t = line.trim()
                    val where = "$rel:${i + 1}"
                    if (t.startsWith("//?") || t.startsWith("/*?")) {
                        if (!guardsAllowed) problems += "$where: version switch outside mc/ or platform/ (write an adapter in mc/)"
                        return@forEachIndexed
                    }
                    if (inComment) {
                        if ("*/" in t) inComment = false
                        return@forEachIndexed
                    }
                    if (t.startsWith("/*")) {
                        inComment = "*/" !in t
                        return@forEachIndexed
                    }
                    if (t.startsWith("//") || t.startsWith("*")) return@forEachIndexed
                    val code = t.replace(Regex(""""(?:[^"\\]|\\.)*""""), "\"\"").replace(Regex("//.*$"), "")
                    Regex("""^import\s+(?:static\s+)?([\w.]+)""").find(code)?.let { m ->
                        val name = m.groupValues[1]
                        if (allowed.none { name.startsWith(it) }) {
                            problems += "$where: imports $name (only Minecraft, the JDK and our own code; other mods go through compat/)"
                        }
                    }
                    if ("net.fabricmc." in code) problems += "$where: uses Fabric directly (go through platform/Platform)"
                    if (Regex("""\.aliveworkplace\.compat\.""").containsMatchIn(code)) {
                        problems += "$where: uses compat/ directly (add an extension point; compat/Compat fills it in)"
                    }
                    if (Regex("""\.aliveworkplace\.platform\.fabric\.""").containsMatchIn(code)) {
                        problems += "$where: uses platform/fabric/ directly (go through platform/Platform)"
                    }
                    if (Regex("""\b(get|set|has|remove)Attached\w*\(""").containsMatchIn(code)) {
                        problems += "$where: Fabric data attachment method (use platform/Attachment)"
                    }
                }
            }
            val out = report.get().asFile
            out.parentFile.mkdirs()
            out.writeText(problems.joinToString("\n") + "\n")
            if (problems.isNotEmpty()) {
                throw GradleException("Layer rule violations (see CLAUDE.md, \"Layers\"):\n" + problems.joinToString("\n"))
            }
        }
    }
    named("check") { dependsOn(checkLayers) }

    /** Writes the compile classpath for the minecraft-mod-engineer skill's api.py (per-node API lookups). */
    register("apiClasspath") {
        group = "help"
        description = "Writes build/api-classpath.txt (main + client + gametest compile classpath)"
        val cp = files(
            configurations.named("compileClasspath"),
            configurations.named("clientCompileClasspath"),
            configurations.named("gametestCompileClasspath"),
        )
        val out = layout.buildDirectory.file("api-classpath.txt")
        inputs.files(cp)
        outputs.file(out)
        doLast { out.get().asFile.writeText(cp.files.joinToString("\n") { it.absolutePath }) }
    }
}

/**
 * CurseForge uploads (`./gradlew publishMods`; owner's decision 2026-10-03: CurseForge and GitHub, no Modrinth). Without
 * CURSEFORGE_TOKEN it is a dry run: what it would upload lands in versions/<mc>/build/publishMods/. The GitHub
 * Release itself is made by CI (build.yml) when a new version lands on main. The project id goes in
 * gradle.properties as publish.curseforge once the owner has made the project.
 */
publishMods {
    file = loomx.modJar.flatMap { it.archiveFile }
    displayName = "${property("mod.name")} $modVersion for Minecraft ${sc.current.version}"
    version = project.version.toString()
    // This version's section of CHANGELOG.md (or Unreleased, before the release commit moves it).
    changelog = providers.fileContents(rootProject.layout.projectDirectory.file("CHANGELOG.md")).asText.map { text ->
        val sections = text.split(Regex("(?m)^## ")).drop(1)
        val mine = sections.firstOrNull { it.startsWith("$modVersion ") || it.startsWith("$modVersion\n") }
            ?: sections.firstOrNull { it.startsWith("Unreleased") } ?: ""
        mine.substringAfter("\n").trim()
    }
    type = if (modVersion.startsWith("0.")) BETA else STABLE
    modLoaders.add("fabric")
    dryRun = providers.environmentVariable("CURSEFORGE_TOKEN").orNull == null

    curseforge {
        projectId = findProperty("publish.curseforge")?.toString() ?: "000000"
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(tomlList("mod", "mc_releases").ifEmpty { listOf(sc.current.version) })
        javaVersions.add(requiredJava)
        client = true
        server = true
        requires("fabric-api")
        optional("cobblemon", "modmenu")
    }
}
