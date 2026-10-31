import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream

// The build of one Minecraft version node (versions/<mc>/), shared by all of them. Stonecutter writes the sources for
// each node from src/ (see stonecutter.gradle.kts); per-node values come from stonecutter.properties.toml. Branch on
// what a node declares there or on sc.current.parsed, never on a hard-coded version.
plugins {
    // fabric-loom-remap (obfuscated Minecraft, up to 1.21.11) or fabric-loom (26.1+), with loomx.loom_version.
    id("dev.kikugie.loom-back-compat")
    // Uploads the jar to CurseForge (ROADMAP 26.3). Only dry-runs unless -Ppublish.live=true and a token: `./gradlew publishMods`.
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

// The GameTest watchdog (B114: fails a run whose server thread stops ticking, with every thread's stack) and the
// mock-player sweep (B210: without it the compat suite kept every test's mock player, about 120 by batch 266, and ran
// out of a 2 GB heap at batch megaSwitchOff) run in the compat tests too: their files, copied from the gametest
// sources into the compattest source set.
compattest?.let { set ->
    val watchdogSource = tasks.register<Sync>("compatWatchdogSource") {
        from(rootProject.file("src/gametest/java")) {
            include("**/test/GameTestWatchdog.java")
            include("**/test/MockPlayerSweep.java")
        }
        into(layout.buildDirectory.dir("generated/compatWatchdog"))
    }
    set.java.srcDir(watchdogSource)
}
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
                // ... and POKEMON_HOUSE_WEIGHT the five Pokémon jobs' houses (ROADMAP 28.15, SCENE=village)
                findProperty("pokemonHouseWeight")?.let { vmArg("-Daliveworkplace.pokemonHouseWeight=$it") }
                vmArg("-Daliveworkplace.debug=${findProperty("builderDebug") ?: "false"}")
                // The furniture scenes load Cobblemon and the pack's building mods (Chipped, Supplementaries, Rechiseled...):
                // their block states outgrow the default heap (a quarter of RAM, 4 GB on GitHub's runners), and the
                // integrated server ran out of memory saving chunks a minute in (every furniture scene, 2026-10-18).
                // -PshotsHeap=N to override.
                (findProperty("shotsHeap") ?: if (findProperty("furniture") == "true") "6g" else null)?.let { vmArg("-Xmx$it") }
            }
        }
        if (compattest != null) {
            register("compatGameTest") {
                server()
                name = "Compat Game Test"
                source(compattest)
                runDir = "build/run/compatGameTest"
                // The pack's block states keep ~1.5 GB live: the JVM's default heap (a quarter of RAM) thrashes in full GC
                // in the 7 GB dev container, and 3 GB didn't fit next to Gradle (B55). -PcompatHeap=4G to override.
                vmArg("-Xmx${findProperty("compatHeap") ?: "2560m"}")
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

/** A jar's Fabric mod id and version (from its fabric.mod.json), or null for a plain library. */
fun modIdOf(bytes: ByteArray): Pair<String, String>? {
    ZipInputStream(bytes.inputStream()).use { zin ->
        while (true) {
            val entry = zin.nextEntry ?: return null
            if (entry.name == "fabric.mod.json") {
                @Suppress("UNCHECKED_CAST")
                val json = groovy.json.JsonSlurper().parseText(zin.readBytes().toString(Charsets.UTF_8)) as Map<String, Any?>
                return json["id"].toString() to json["version"].toString()
            }
        }
    }
}

/** Every mod nested in a jar, at any depth, with the newest version of each. */
fun nestedModVersions(bytes: ByteArray, into: MutableMap<String, String> = mutableMapOf()): MutableMap<String, String> {
    ZipInputStream(bytes.inputStream()).use { zin ->
        while (true) {
            val entry = zin.nextEntry ?: break
            if (!entry.name.startsWith("META-INF/jars/") || !entry.name.endsWith(".jar")) continue
            val inner = zin.readBytes()
            modIdOf(inner)?.let { (id, version) ->
                if (into[id]?.let { compareVersions(it, version) >= 0 } != true) into[id] = version
            }
            nestedModVersions(inner, into)
        }
    }
    return into
}

/** Compares two mod versions the way semantic versions compare ("3.1.0-beta.90" after "3.1.0-beta.47"; build metadata after "+" ignored). */
fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.substringBefore('+').split('.', '-', '_')
    val x = parts(a)
    val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val p = x.getOrNull(i) ?: return -1
        val q = y.getOrNull(i) ?: return 1
        val c = if (p.toIntOrNull() != null && q.toIntOrNull() != null) p.toInt().compareTo(q.toInt()) else p.compareTo(q)
        if (c != 0) return c
    }
    return 0
}

/**
 * Unpacks the jars nested in mods (META-INF/jars, recursively) into build/compat-nested: Loom drops them in dev.
 * [local] are the local mod jars the same run loads from its mods/ folder (localModsInto): a library they nest at the
 * same or a newer version isn't flattened here, because a flattened jar is a root mod and the loader would take it over
 * theirs. Sophisticated Core nests Porting Lib 3.1.0-beta.47, which Create's port can't run on (it needs beta.90).
 */
fun nestedJars(mods: List<String>, local: List<String> = emptyList()): List<File> {
    val out = layout.buildDirectory.dir("compat-nested").get().asFile
    val resolved = configurations.detachedConfiguration(*mods.map { dependencies.create(it) }.toTypedArray())
    resolved.isTransitive = false
    val localNested = mutableMapOf<String, String>()
    local.forEach { nestedModVersions(rootProject.file(it).readBytes(), localNested) }
    val jars = mutableListOf<File>()
    fun unpack(mod: File) {
        zipTree(mod).matching { include("META-INF/jars/*.jar") }.forEach { nested ->
            // owo-sentinel only warns that owo-lib is missing, and refuses to load next to it.
            if (nested.name.startsWith("owo-sentinel")) return@forEach
            // C2ME's native maths needs Java 25, and its density function compiler needs that: on Java 21 the loader
            // leaves both nested modules out of a real install (the pack test's mod list); flattened here, they would
            // stop the game (25.4).
            if (nested.name.startsWith("c2me-fabric-opts-natives-math") || nested.name.startsWith("c2me-fabric-opts-dfc")) return@forEach
            if (localNested.isNotEmpty()) {
                val id = modIdOf(nested.readBytes())
                if (id != null && localNested[id.first]?.let { compareVersions(it, id.second) >= 0 } == true) return@forEach
            }
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

/** Every local mod jar a run may load (tests.compat_files, tests.screenshot_create): the files localModsInto manages. */
val localModFiles: List<String> = (tomlList("tests", "compat_files") + tomlList("tests", "screenshot_create")).distinct()

/**
 * Puts local mod jars (files in the repo, such as the owner's Create port in libs/) into a run's mods/ folder, and takes
 * out the managed ones that run doesn't ask for. Not on the classpath like the maven mods above: Fabric Loader remaps a
 * mods/ jar itself and picks one version of each library it nests, as in a real install. Create nests three versions of
 * Porting Lib's core and two of Flywheel, which flattening (nestedJars) would load side by side. Each copy:
 * - leaves out its own GameTests (the "fabric-gametest" entrypoints of the jar and the jars it nests: Create's own
 *   suite and Porting Lib's test need their own test structures, and are theirs to run), so our suites hold only our tests;
 * - keeps vanilla's methods in Porting Lib's ItemStackMixin (devIntrinsics below). It soft-implements set, remove and
 *   applyComponents with @Intrinsic(displace = true): harmless in a real install, where vanilla's methods are
 *   method_57379 and so on, but in our Mojang-named dev runtime the names meet, so vanilla's methods are displaced, and
 *   owo-lib's injection into applyComponents (owo is in the compat runtime for Mega Showdown) finds nothing to inject
 *   into and stops the game. With displace = false the vanilla method, which has the same name and signature in dev,
 *   is kept as the interface's method, which is what the soft implementation does in a real install.
 */
fun localModsInto(runDir: File, wanted: List<String>) {
    val mods = File(runDir, "mods")
    mods.mkdirs()
    localModFiles.filter { it !in wanted }.forEach { File(mods, File(it).name).delete() }
    wanted.forEach { path ->
        val source = rootProject.file(path)
        val target = File(mods, source.name)
        if (target.exists() && target.lastModified() >= maxOf(source.lastModified(), buildFile.lastModified())) return@forEach
        target.writeBytes(rewriteJar(source.readBytes()))
    }
}

/** Mixin classes whose @Intrinsic(displace = true) methods keep vanilla's in dev runs (localModsInto): nested jar prefix to class. */
val devIntrinsics = mapOf("META-INF/jars/base-" to "io/github/fabricators_of_create/porting_lib/mixin/common/ItemStackMixin.class")

/** A class with every @Intrinsic(displace = true) turned into displace = false. */
fun keepIntrinsicTargets(bytes: ByteArray): ByteArray {
    val api = org.objectweb.asm.Opcodes.ASM9
    val reader = org.objectweb.asm.ClassReader(bytes)
    val writer = org.objectweb.asm.ClassWriter(0)
    reader.accept(object : org.objectweb.asm.ClassVisitor(api, writer) {
        override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor {
            val method = super.visitMethod(access, name, descriptor, signature, exceptions)
            return object : org.objectweb.asm.MethodVisitor(api, method) {
                override fun visitAnnotation(desc: String?, visible: Boolean): org.objectweb.asm.AnnotationVisitor? {
                    val annotation = super.visitAnnotation(desc, visible)
                    if (desc != "Lorg/spongepowered/asm/mixin/Intrinsic;" || annotation == null) return annotation
                    return object : org.objectweb.asm.AnnotationVisitor(api, annotation) {
                        override fun visit(key: String?, value: Any?) = super.visit(key, if (key == "displace") false else value)
                    }
                }
            }
        }
    }, 0)
    return writer.toByteArray()
}

@Suppress("UNCHECKED_CAST")
fun rewriteJar(bytes: ByteArray, intrinsics: String? = null): ByteArray {
    val entries = linkedMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zin ->
        while (true) {
            val entry = zin.nextEntry ?: break
            if (!entry.isDirectory) entries[entry.name] = zin.readBytes()
        }
    }
    entries["fabric.mod.json"]?.let { b ->
        val json = groovy.json.JsonSlurper().parseText(b.toString(Charsets.UTF_8)) as MutableMap<String, Any?>
        if ((json["entrypoints"] as? MutableMap<String, Any?>)?.remove("fabric-gametest") != null) {
            entries["fabric.mod.json"] = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)).toByteArray()
        }
    }
    if (intrinsics != null) {
        entries[intrinsics]?.let { entries[intrinsics] = keepIntrinsicTargets(it) }
    }
    for (name in entries.keys.toList()) {
        if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar")) continue
        val target = devIntrinsics.entries.firstOrNull { name.startsWith(it.key) }?.value
        entries[name] = rewriteJar(entries.getValue(name), intrinsics = target)
    }
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, data) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(data)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
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
    // Local mod jars compat/<mod>/ compiles against (tests.compat_compile_files: Create, 39.7a), never loaded by the mod.
    // Their nested libraries (Create's Ponder and Porting Lib) too: Create's classes extend theirs.
    tomlList("tests", "compat_compile_files").forEach { path ->
        val jar = rootProject.file(path)
        modCompileOnly(files(jar))
        val out = layout.buildDirectory.dir("compat-compile-nested").get().asFile
        zipTree(jar).matching { include("META-INF/jars/*.jar") }.forEach { nested ->
            val target = File(out, nested.name)
            if (!target.exists()) {
                out.mkdirs()
                target.writeBytes(nested.readBytes())
            }
            modCompileOnly(files(target))
        }
    }

    // Only loaded by runCompatGameTest.
    if (compattest != null) {
        compatTestMods.forEach { "modCompattestRuntimeOnly"(it) }
        // Loom drops the libraries these mods bundle inside themselves, so add those back.
        "modCompattestRuntimeOnly"(files(nestedJars(compatTestMods, tomlList("tests", "compat_files"))))
    }

    if (devclient != null) {
        // Screenshots of Cobblemon features (SCENE=tutor): ./gradlew runScreenshots -Pcobblemon=true
        // SCENE=battle: Mega Showdown too (-Pmega=true), for a Master trainer's Mega Evolution.
        // PERF_STACK=true (-PperfStack=true): the pack's performance mods too (ROADMAP 25.4).
        // FURNITURE=true (-Pfurniture=true): the pack's furniture mods (ROADMAP 37.4, scene furnished_home),
        // Sophisticated Storage (37.9) and Tom's Simple Storage (37.10), both in scene modded_storehouse.
        // WAYSTONES=true (-Pwaystones=true): Waystones and Balm (ROADMAP 41.1, scene village_waystone).
        listOf("cobblemon" to "screenshot_cobblemon", "mega" to "screenshot_mega", "perfStack" to "screenshot_perf",
            "furniture" to "screenshot_furniture", "waystones" to "screenshot_waystones").forEach { (flag, key) ->
            if (findProperty(flag) == "true") {
                val mods = forCobblemon(tomlList("tests", key))
                mods.forEach { "modDevclientRuntimeOnly"(it) }
                val local = if (findProperty("create") == "true") tomlList("tests", "screenshot_create") else emptyList()
                "modDevclientRuntimeOnly"(files(nestedJars(mods, local)))
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

/**
 * Values substituted into fabric.mod.json. The mod's version there (what Mod Menu and crash reports show) is the jar's:
 * `<mod.version>+<minecraft>`, e.g. 1.0.0+1.21.1. Release tags stay v<mod.version> (build.yml).
 */
val resourceProps: Map<String, String> = mapOf(
    "version" to "$modVersion+${sc.current.version}",
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
        // String joins compiled as plain StringBuilder code, not invokedynamic: each indy join is linked the first time it
        // runs, and on the pack that first link stalled a tick by 80 to 110 ms (B103: a day's report line, a farmer's
        // first look at a field). Same strings, no runtime linking.
        options.compilerArgs.add("-XDstringConcat=inline")
    }

    withType<ProcessResources>().configureEach {
        val props = resourceProps
        props.forEach { (k, v) -> inputs.property(k, v) }
        // Only a fabric.mod.json with ${...} placeholders is expanded: Groovy's template engine refuses a file over
        // 64K characters, and the GameTest one (no placeholders, one line per test class) grew past that.
        filesMatching("fabric.mod.json") { if (file.readText().contains("\${")) expand(props) }
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

    // A GameTest run never hangs silently (B114). The watchdog (GameTestWatchdog) halts the server with every thread's
    // stack when no tick finishes for 300 s (-PgametestStallSeconds=N); running out of heap ends the run at once instead
    // of leaving a chunk waited on forever; and Gradle stops a run past -PgametestTimeoutMinutes (120: the full suite
    // takes about 15 min locally, 7 on CI).
    matching { it.name == "runGameTest" || it.name == "runCompatGameTest" }.configureEach {
        (this as JavaExec).jvmArgs("-XX:+ExitOnOutOfMemoryError")
        findProperty("gametestStallSeconds")?.let { jvmArgs("-Daliveworkplace.gametestStallSeconds=$it") }
        timeout.set(Duration.ofMinutes(findProperty("gametestTimeoutMinutes")?.toString()?.toLong() ?: 120L))
    }

    // The compat test server needs an accepted EULA in its run directory, like runGameTest; `build` runs it.
    matching { it.name == "runCompatGameTest" }.configureEach {
        val dir = layout.buildDirectory.dir("run/compatGameTest").get().asFile
        val wanted = tomlList("tests", "compat_files")
        doFirst {
            dir.mkdirs()
            File(dir, "eula.txt").writeText("eula=true\n")
            localModsInto(dir, wanted)
        }
    }
    // -Pcreate=true: the screenshot client loads the owner's Create port (tests.screenshot_create); without it, not.
    matching { it.name == "runScreenshots" }.configureEach {
        val dir = file("run/screenshots")
        val wanted = if (findProperty("create") == "true") tomlList("tests", "screenshot_create") else emptyList()
        doFirst { localModsInto(dir, wanted) }
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

    /**
     * Refresh to the latest look keeps working across a redraw (ROADMAP 36.21, B661/B662): tools/blueprints/history.py
     * --check fails when a blueprint redrawn since the latest release (newest v* tag) doesn't ship that release's drawing
     * in blueprint_history, so a house players built from it couldn't be refreshed. Fix: run
     * `python3 tools/blueprints/history.py` and commit what it writes. About a second; passes when no tag is fetched.
     */
    val checkHistory by registering(Exec::class) {
        group = "verification"
        description = "Fails if a blueprint redrawn since the last release doesn't ship that release's drawing in blueprint_history"
        workingDir = rootProject.projectDir
        commandLine("python3", "tools/blueprints/history.py", "--check")
    }
    named("check") { dependsOn(checkHistory) }

    /**
     * The wiki stays true (ROADMAP 22.9): tools/modtest/wikicheck.py fails when a page in docs/wiki/ names a config key,
     * id, lang key, test or scene that no longer exists, or a ticked player-visible roadmap item has no page. It reads
     * only checked-in files and takes under a second, so it always runs. Needs python3 (GitHub's runners have it).
     */
    val checkWiki by registering(Exec::class) {
        group = "verification"
        description = "Fails if docs/wiki names something that no longer exists, or a ticked player-visible item has no page"
        workingDir = rootProject.projectDir
        commandLine("python3", "tools/modtest/wikicheck.py")
    }
    named("check") { dependsOn(checkWiki) }

    /**
     * The player guide stays a guide for players (ROADMAP 22.10): tools/modtest/guidecheck.py fails when a page in
     * docs/guide/ has a roadmap id, "ROADMAP", "GameTest" or a path under src/, a broken link, or a picture of a showcase
     * scene or still that doesn't exist. Under a second, checked-in files only, so it always runs.
     */
    val checkGuide by registering(Exec::class) {
        group = "verification"
        description = "Fails if a docs/guide page has backend words (roadmap ids, ROADMAP, GameTest, src/) or a broken link or picture"
        workingDir = rootProject.projectDir
        commandLine("python3", "tools/modtest/guidecheck.py")
    }
    named("check") { dependsOn(checkGuide) }

    /**
     * American English in every sentence a player reads (owner, 2026-10-26; ROADMAP 36.19): langcheck.py --spelling fails
     * on a British spelling (colour, Jeweller, cancelled, storey ...) in an en_us.json value, the questbook, docs/guide/
     * or docs/wiki/; keys, ids and code spans are ignored. The word list and its allowlist are in tools/modtest/spelling.py,
     * and `spelling.py --fix` rewrites what it finds. Under a second, checked-in files only, so it always runs.
     */
    val checkSpelling by registering(Exec::class) {
        group = "verification"
        description = "Fails on a British spelling in an en_us.json value, the questbook, docs/guide or docs/wiki"
        workingDir = rootProject.projectDir
        commandLine("python3", "tools/modtest/langcheck.py", "--spelling")
    }
    named("check") { dependsOn(checkSpelling) }

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
 * CurseForge uploads (`./gradlew publishMods`; owner's decision 2026-10-03: CurseForge and GitHub, no Modrinth). It is
 * always a dry run (what it would upload lands in versions/<mc>/build/publishMods/) unless `-Ppublish.live=true` is
 * passed AND CURSEFORGE_TOKEN is set: store-page publishing waits for the owner (ROADMAP 26.1). The GitHub Release
 * itself is made by CI (build.yml) when a new version lands on main. The project id comes from CURSEFORGE_PROJECT_ID
 * or gradle.properties' publish.curseforge once the owner has made the project.
 */
val publishLive = findProperty("publish.live")?.toString() == "true" &&
    providers.environmentVariable("CURSEFORGE_TOKEN").orNull != null

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
    dryRun = !publishLive

    curseforge {
        projectId = providers.environmentVariable("CURSEFORGE_PROJECT_ID").orNull
            ?: findProperty("publish.curseforge")?.toString() ?: "000000"
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(tomlList("mod", "mc_releases").ifEmpty { listOf(sc.current.version) })
        javaVersions.add(requiredJava)
        client = true
        server = true
        requires("fabric-api")
        optional("cobblemon", "modmenu")
    }
}
