package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA (lane b, 2026-11-16, session lane-b-1116-1837), from the spec, not the code: blueprint-library.md "Builds unlock ...
 * when a builder of the village levels up (3 each time)", "each unlock says in chat ... for a builder's level-up, every
 * player in the village", and "Tables far from any hall" share the server-wide set. Found while checking why
 * QaUpgradeEntry1116b broke six Table tests: its builder leveled up a step outside the village and the level-up's three
 * builds went to the server-wide set. Here the village's own builder leveling up beside the hall, and a builder with no
 * village at all. The village radius is cut to 16 so it fits huge_area; the server-wide set is put back after.
 */
public class QaLevelUpVillage1116bGameTests {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(2, 2, 2);
	static final BlockPos TABLE = new BlockPos(6, 2, 2);

	/** Unlocks on, radius 16, and the server-wide set as it was before the test put back after it. */
	static void setUp(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		ServerLevel level = helper.getLevel();
		Set<ResourceLocation> before = Set.copyOf(BlueprintUnlocks.unlocked(level, null));
		Leftovers.after(helper, () -> wildIds(level.getServer()).retainAll(before));
	}

	@SuppressWarnings("unchecked")
	static Set<ResourceLocation> wildIds(MinecraftServer server) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", MinecraftServer.class);
			get.setAccessible(true);
			Object set = get.invoke(null, server);
			((net.minecraft.world.level.saveddata.SavedData) set).setDirty();
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			return (Set<ResourceLocation>) ids.get(set);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("couldn't reach the server-wide set: " + e);
		}
	}

	/** A Novice builder at {@code at}, not moving (hired with {@link #hireAndLevel}). */
	static Villager builder(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		return v;
	}

	static void hireAndLevel(GameTestHelper helper, Villager v, BlockPos table) {
		ServerLevel level = helper.getLevel();
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		Builders.employ(level, v, helper.absolutePos(table));
		BuilderLevels.addXp(level, v, 30, null);
		helper.assertTrue(v.getVillagerData().getLevel() == 2, "setup: the builder didn't level up: " + v.getVillagerData().getLevel());
	}

	static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/**
	 * The village's builder, hired at its Table and standing 10 blocks east of the hall (in the middle of the area), levels
	 * up: the next three builds unlock in that village (and only there), and every player in the village is told about
	 * each one: one by the Table, one 12 blocks west of the hall (22 from the builder) and one exactly 16 blocks above it
	 * (the village's reach, counted as in it). A player 17 blocks above the hall is outside the village and isn't told.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaLvlVillage1116bNear"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaLvlVillage1116bNear")
	public void aVillageBuildersLevelUpBesideTheHallUnlocksInThatVillage(GameTestHelper helper) {
		setUp(helper);
		BlockPos hallRel = new BlockPos(15, 2, 15);
		BlockPos tableRel = new BlockPos(19, 2, 15);
		QaBlueprintUnlocksGameTests.village(helper, hallRel, tableRel);
		Villager v = builder(helper, new BlockPos(25, 2, 15));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			Set<ResourceLocation> wild = Set.copyOf(BlueprintUnlocks.unlocked(level, null));
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "setup: a level-up would unlock " + next);
			QaStageFiveGameTests.Client byTable = QaBlueprintUnlocksGameTests.client(helper, tableRel);
			QaStageFiveGameTests.Client west = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaLvW"));
			QaStageFiveGameTests.Client edge = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaLvE"));
			QaStageFiveGameTests.Client past = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaLvP"));
			west.player().moveTo(hall.getX() - 12 + 0.5, hall.getY(), hall.getZ() + 0.5, 0f, 0f);
			edge.player().moveTo(hall.getX() + 0.5, hall.getY() + 16, hall.getZ() + 0.5, 0f, 0f);
			past.player().moveTo(hall.getX() + 0.5, hall.getY() + 17, hall.getZ() + 0.5, 0f, 0f);
			helper.assertTrue(edge.player().blockPosition().distSqr(hall) == 256 && past.player().blockPosition().distSqr(hall) == 289,
				"setup: the edge players stand " + edge.player().blockPosition() + " and " + past.player().blockPosition() + ", the hall is " + hall);
			for (QaStageFiveGameTests.Client c : List.of(byTable, west, edge, past)) {
				c.seen().clear();
			}
			hireAndLevel(helper, v, tableRel);
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == BlueprintUnlocks.State.UNLOCKED,
					"the village builder's level-up beside the hall left " + id + " " + BlueprintUnlocks.state(level, hall, id));
				String line = "The village's library has a new blueprint: " + Blueprints.displayName(id).getString();
				helper.assertTrue(texts(byTable.seen()).contains(line), "a player by the Table wasn't told '" + line + "', saw " + texts(byTable.seen()));
				helper.assertTrue(texts(west.seen()).contains(line), "a player 12 blocks west of the hall (22 from the builder) wasn't told '" + line
					+ "', saw " + texts(west.seen()));
				helper.assertTrue(texts(edge.seen()).contains(line), "a player exactly 16 blocks from the hall (the village's reach) wasn't told '" + line
					+ "', saw " + texts(edge.seen()));
				helper.assertFalse(texts(past.seen()).contains(line), "a player 17 blocks from the hall, outside the village, was told '" + line + "'");
			}
			helper.assertTrue(BlueprintUnlocks.unlocked(level, null).equals(wild),
				"the village builder's level-up changed the server-wide set from " + wild + " to " + BlueprintUnlocks.unlocked(level, null));
			LOGGER.info("qa level-up village 1116b: beside the hall, {} unlocked in the village; told at 0, 12 and 16 blocks, not 17", next);
			helper.succeed();
		});
	}

	/**
	 * A builder with no village (no hall in reach of him or of his Table) levels up: the three builds go into the
	 * server-wide set, the one a Table far from every hall reads.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaLvlVillage1116bWild"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaLvlVillage1116bWild")
	public void aBuilderWithNoVillageLevelsUpIntoTheServerWideSet(GameTestHelper helper) {
		setUp(helper);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager v = builder(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			helper.assertTrue(BlueprintUnlocks.village(level, helper.absolutePos(TABLE)) == null, "setup: a hall is in reach of the Table");
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, null);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "setup: a level-up would unlock " + next);
			hireAndLevel(helper, v, TABLE);
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, null, id) == BlueprintUnlocks.State.UNLOCKED,
					"a builder with no village leveled up and " + id + " isn't in the server-wide set: " + BlueprintUnlocks.state(level, null, id));
			}
			LOGGER.info("qa level-up village 1116b: no village, {} went into the server-wide set", next);
			helper.succeed();
		});
	}
}
