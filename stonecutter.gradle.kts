plugins {
    id("dev.kikugie.stonecutter")
    // Only applied on nodes whose compat tests run Kotlin-written mods (Cobblemon): see build.gradle.kts.
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
}

// The node the checked-in sources (and your IDE) currently target. Switch with
// `./gradlew "Set active project to <version>"`; switch back to the vcsVersion (settings.gradle.kts) before committing.
stonecutter active "1.21.1"

// Optional integrations (compat/<modid>/). Each id becomes a constant for `//? if <id> {`, true on nodes whose section in
// stonecutter.properties.toml declares deps.compat.<id>. The whole of compat/cobblemon/ and compat/cobbledollars/ is
// wrapped in such a guard, so a node without the mod never compiles them.
val integrations = listOf("cobblemon", "cobbledollars")

stonecutter parameters {
    val mc = current.parsed

    // GameTest annotations: `//$ gametest…` above every @GameTest writes it for the node. The arguments are the test
    // area (a structure id or a constant), the timeout in ticks and the batch; arguments that aren't plain names go in
    // single quotes (`//$ gametest_ticks_batch AREA '400' '"bard"'`). Up to 1.21.4 it's vanilla's annotation, exactly
    // as the tests were written. From 1.21.5 it's Fabric's @GameTest (porting.md) with only a timeout; our custom test
    // areas and batches have no equivalent there yet and get their new form when a 26.x node is added (ROADMAP,
    // Milestone 19 phase 3).
    val modern = mc >= "1.21.5"
    swaps["gametest"] = if (modern) "@GameTest" else "@GameTest(template = $1)"
    swaps["gametest_ticks"] = if (modern) "@GameTest(maxTicks = $2)" else "@GameTest(template = $1, timeoutTicks = $2)"
    swaps["gametest_batch"] = if (modern) "@GameTest" else "@GameTest(template = $1, batch = $2)"
    swaps["gametest_ticks_batch"] = if (modern) "@GameTest(maxTicks = $2)" else "@GameTest(template = $1, timeoutTicks = $2, batch = $3)"

    for (modId in integrations) {
        constants[modId] = node.project.findProperty("deps.compat.$modId") != null
    }

    // `//? if fapi: >=0.140` checks the node's Fabric API version instead of Minecraft's.
    dependencies["fapi"] = node.project.property("deps.fabric_api").toString().substringBefore('+')
}
