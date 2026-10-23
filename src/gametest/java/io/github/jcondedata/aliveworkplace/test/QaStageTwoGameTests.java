package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA stage 2 (2026-10-23): the config matrix. Expectations come from docs/wiki/config-switches.md ("each value is
 * handed to the system that uses it"; the file is read at start-up; an expansion's switches stay off while its gate is
 * closed) and each switch's own comment in the config file: a switch named after a feature turns that feature on, and
 * off stops it. The feature tests already prove each system's own flag stops it; these prove the switch in the file
 * reaches that flag, that no two switches drive the same thing, and that the mod's core job still works with every
 * switch off at once.
 */
public class QaStageTwoGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String GAMETEST = "fabric-api.gametest";
	/** Read from the file at server start only (its comment: "keep every questbook chapter file of ours as it is on start"). */
	private static final List<String> READ_AT_START = List.of("questbookKeepEdits");

	/** The switches a player can change: those on the settings screen (an expansion's are hidden while its gate is closed). */
	private static List<String> liveSwitches() {
		return WorkplaceConfig.optionNames().stream().filter(WorkplaceConfig::isSwitch).toList();
	}

	/** Every public static boolean of the systems the config hands its values to. */
	private static Map<String, Boolean> flags() {
		Map<String, Boolean> flags = new TreeMap<>();
		for (Class<?> type : systems()) {
			for (Field f : type.getDeclaredFields()) {
				int mods = f.getModifiers();
				if (f.getType() == boolean.class && Modifier.isStatic(mods) && Modifier.isPublic(mods) && !Modifier.isFinal(mods)) {
					try {
						flags.put(type.getSimpleName() + "." + f.getName(), f.getBoolean(null));
					} catch (IllegalAccessException e) {
						throw new GameTestAssertException("can't read " + f + ": " + e);
					}
				}
			}
		}
		return flags;
	}

	private static List<Class<?>> systemsCache;

	/** The mod's classes the config class refers to: the systems its values are handed to. */
	private static List<Class<?>> systems() {
		if (systemsCache != null) {
			return systemsCache;
		}
		String bytes;
		try (InputStream in = WorkplaceConfig.class.getResourceAsStream("WorkplaceConfig.class")) {
			if (in == null) {
				throw new GameTestAssertException("WorkplaceConfig.class not readable");
			}
			bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		} catch (IOException e) {
			throw new GameTestAssertException("WorkplaceConfig.class: " + e);
		}
		TreeSet<String> names = new TreeSet<>();
		Matcher m = Pattern.compile("io/github/jcondedata/aliveworkplace/[A-Za-z0-9_/$]+").matcher(bytes);
		while (m.find()) {
			names.add(m.group().replace('/', '.'));
		}
		List<Class<?>> types = new ArrayList<>();
		for (String name : names) {
			if (name.equals(WorkplaceConfig.class.getName()) || name.startsWith(WorkplaceConfig.class.getName() + "$")) {
				continue;
			}
			try {
				types.add(Class.forName(name, true, WorkplaceConfig.class.getClassLoader()));
			} catch (ClassNotFoundException | LinkageError e) {
				// a descriptor fragment, not a class name
			}
		}
		systemsCache = types;
		return types;
	}

	private static Map<String, String> diff(Map<String, Boolean> before, Map<String, Boolean> after) {
		Map<String, String> changed = new TreeMap<>();
		for (Map.Entry<String, Boolean> e : after.entrySet()) {
			Boolean old = before.get(e.getKey());
			if (old != null && !old.equals(e.getValue())) {
				changed.put(e.getKey(), old + "->" + e.getValue());
			}
		}
		return changed;
	}

	/** Applies {@code config} as a real server would (the GameTest-only overrides lifted while it's applied) and reads the flags. */
	private static Map<String, Boolean> appliedAsOnAServer(WorkplaceConfig config) {
		String was = System.getProperty(GAMETEST);
		System.clearProperty(GAMETEST);
		try {
			config.apply();
			return flags();
		} finally {
			if (was != null) {
				System.setProperty(GAMETEST, was);
			}
		}
	}

	/**
	 * Each live switch, flipped from its default in an otherwise default config, changes what the game does: at least
	 * one system's flag follows it, in the switch's direction (off turns things off, on turns them on), no other switch
	 * changes exactly the same flags, and the defaults applied again give back exactly the default state.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa2_switches"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa2_switches")
	public void everyLiveSwitchReachesItsSystem(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		List<String> switches = liveSwitches();
		WorkplaceConfig defaults = new WorkplaceConfig();
		try {
			Map<String, Boolean> base = appliedAsOnAServer(new WorkplaceConfig());
			helper.assertTrue(base.size() > 50, "only " + base.size() + " flags found in " + systems().size() + " systems");
			Map<Map<String, String>, String> seen = new HashMap<>();
			for (String key : switches) {
				boolean def = defaults.getBoolean(key);
				WorkplaceConfig flipped = new WorkplaceConfig();
				flipped.setBoolean(key, !def);
				Map<String, String> changed = diff(base, appliedAsOnAServer(flipped));
				if (READ_AT_START.contains(key)) {
					continue;
				}
				if (changed.isEmpty()) {
					problems.add(key + " " + (def ? "off" : "on") + " changed nothing");
					continue;
				}
				String expected = def ? "true->false" : "false->true";
				changed.forEach((flag, how) -> {
					if (!how.equals(expected)) {
						problems.add(key + " " + (def ? "off" : "on") + " turned " + flag + " " + how);
					}
				});
				String other = seen.put(changed, key);
				if (other != null) {
					problems.add(key + " and " + other + " drive exactly the same flags " + changed.keySet());
				}
				helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[test] " + key + " -> " + changed));
			}
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[test] " + switches.size() + " live switches each reach their own system"));
			Map<String, String> after = diff(base, appliedAsOnAServer(new WorkplaceConfig()));
			if (!after.isEmpty()) {
				problems.add("the defaults applied again left " + after);
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(switches.size() >= 30, "only " + switches.size() + " live switches: " + switches);
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The expansion gate as the released jar has it (every expansion closed, docs/wiki/config-switches.md): a switch of
	 * a closed expansion is off whatever the file says, so turning it on or off changes nothing, while the switches
	 * outside any expansion still each reach their system.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa2_gates"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa2_gates")
	public void aClosedExpansionsSwitchesChangeNothing(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		boolean open = io.github.jcondedata.aliveworkplace.Expansions.openForTests;
		int hidden = 0;
		int live = 0;
		io.github.jcondedata.aliveworkplace.Expansions.openForTests = false;
		try {
			List<String> switches = liveSwitches();
			WorkplaceConfig defaults = new WorkplaceConfig();
			Map<String, Boolean> base = appliedAsOnAServer(new WorkplaceConfig());
			for (Field f : WorkplaceConfig.class.getFields()) {
				if (f.getType() != boolean.class || Modifier.isStatic(f.getModifiers()) || READ_AT_START.contains(f.getName())) {
					continue;
				}
				WorkplaceConfig flipped = new WorkplaceConfig();
				flipped.setBoolean(f.getName(), !defaults.getBoolean(f.getName()));
				Map<String, String> changed = diff(base, appliedAsOnAServer(flipped));
				if (switches.contains(f.getName())) {
					live++;
					if (changed.isEmpty()) {
						problems.add(f.getName() + " (no expansion) changed nothing with the gates closed");
					}
				} else {
					hidden++;
					if (!changed.isEmpty()) {
						problems.add(f.getName() + " (its expansion closed) changed " + changed);
					}
				}
			}
		} finally {
			io.github.jcondedata.aliveworkplace.Expansions.openForTests = open;
			new WorkplaceConfig().apply();
		}
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"[test] gates closed: " + hidden + " expansion switches change nothing, " + live + " others each reach their system"));
		helper.assertTrue(hidden >= 40 && live >= 20, "expected many of both: " + hidden + " behind gates, " + live + " live");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * Every live switch off at once (a server owner who wants a plain village): the file reads back all off, applying
	 * it doesn't throw, and the Builder, which has no switch, still builds the test hut from a chest. The defaults
	 * come back when it's done.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa2_all_off"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa2_all_off")
	public void aBuilderStillBuildsWithEverySwitchOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		WorkplaceConfig off = new WorkplaceConfig();
		for (String key : liveSwitches()) {
			off.setBoolean(key, false);
		}
		// The file round trip too: what the server would read back from that file.
		WorkplaceConfig read = WorkplaceConfig.parse(new com.google.gson.Gson().toJson(off));
		for (String key : liveSwitches()) {
			helper.assertTrue(!read.getBoolean(key), key + " came back on from a file that says off");
		}
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		read.apply();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		chest.setItem(3, new ItemStack(Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "blueprint not found: " + TEST_HUT);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building with every switch off: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong with every switch off, e.g. " + wrong.stream().limit(3).map(helper::relativePos).toList());
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "planks left in the chest: " + chest.countItem(Items.OAK_PLANKS));
			helper.assertTrue(!ModAttachments.BUILDER_JOB.has(villager), "the builder still has a job");
		});
	}
}
