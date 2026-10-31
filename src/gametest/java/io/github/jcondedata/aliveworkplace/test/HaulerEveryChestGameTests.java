package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B627: a Hauler Golem hauls from every village worker's chest on its side, the worker its side is taken from
 * included ({@link Village#workerStashes}), and a settled Golem Smith by the Storehouse sets that side even though a
 * Smith's job doesn't take part in the village ({@link HaulerGolems#sideOf}).
 */
public class HaulerEveryChestGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 4);
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};
	private static final BlockPos SMITH_TABLE = new BlockPos(6, 2, 12);
	private static final Employer OTHER_PLAYER = new Employer(UUID.fromString("6a1e2b3c-0000-4000-8000-000000000627"), "Other");

	/** Every worker's stash is listed, each worker's own included; another player's worker and the Storehouse's chest aren't. */
	//$ gametest_batch HUGE '"haulerEveryChestList"'
	@GameTest(template = HUGE, batch = "haulerEveryChestList")
	public void everyVillageWorkersStashIsListedForTheHauler(GameTestHelper helper) {
		Villager[] jacks = village(helper);
		ModAttachments.BUILDER_EMPLOYER.set(jacks[2], OTHER_PLAYER);
		List<Village.Stash> stashes = Village.workerStashes(helper.getLevel(), helper.absolutePos(STOREHOUSE), null, null);
		for (int i = 0; i < 2; i++) {
			BlockPos chest = helper.absolutePos(CHESTS[i]);
			helper.assertTrue(stashes.stream().anyMatch(s -> s.chests().contains(chest)), "lumberjack " + i + "'s chest isn't listed: " + stashes);
		}
		helper.assertFalse(stashes.stream().anyMatch(s -> s.chests().contains(helper.absolutePos(CHESTS[2]))),
			"another player's lumberjack's chest is listed for the village's side: " + stashes);
		helper.assertFalse(stashes.stream().anyMatch(s -> s.job() != ModVillagers.PORTER && s.chests().contains(helper.absolutePos(STORE_CHEST))),
			"the Storehouse's own chest is listed as a worker's: " + stashes);
		List<Village.Stash> theirs = Village.workerStashes(helper.getLevel(), helper.absolutePos(STOREHOUSE), OTHER_PLAYER, null);
		helper.assertTrue(theirs.stream().anyMatch(s -> s.chests().contains(helper.absolutePos(CHESTS[2])))
			&& theirs.stream().noneMatch(s -> s.chests().contains(helper.absolutePos(CHESTS[0]))),
			"the other player's side should list only their lumberjack's chest: " + theirs);
		helper.succeed();
	}

	/** A settled Golem Smith by the Storehouse sets the hauler's side, though lumberjacks stand about too. */
	//$ gametest_batch HUGE '"haulerEveryChestSmithSide"'
	@GameTest(template = HUGE, batch = "haulerEveryChestSmithSide")
	public void aGolemSmithByTheStorehouseSetsTheHaulersSide(GameTestHelper helper) {
		village(helper);
		Villager smith = golemSmith(helper);
		Villager side = HaulerGolems.sideOf(helper.getLevel(), helper.absolutePos(STOREHOUSE));
		helper.assertTrue(side == smith, "the hauler's side should be the Golem Smith's, not " + (side == null ? "nobody"
			: "a " + side.getVillagerData().getProfession()));
		helper.succeed();
	}

	/**
	 * With a Golem Smith of the village's by the Storehouse, the village's lumberjacks' chests are hauled and another
	 * player's lumberjack's is left alone.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"haulerEveryChestSmithHaul"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "haulerEveryChestSmithHaul")
	public void aGolemSmithsHaulerTakesOnlyItsOwnSidesChests(GameTestHelper helper) {
		Villager[] jacks = village(helper);
		ModAttachments.BUILDER_EMPLOYER.set(jacks[2], OTHER_PLAYER);
		golemSmith(helper);
		IronGolem golem = hauler(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(logs(helper, CHESTS[0]) == 0 && logs(helper, CHESTS[1]) == 0, "the village's lumberjacks still have "
				+ logs(helper, CHESTS[0]) + " and " + logs(helper, CHESTS[1]) + " logs; the Storehouse chest has " + logs(helper, STORE_CHEST));
			helper.assertTrue(logs(helper, STORE_CHEST) == 128 && HaulerGolems.load(golem).isEmpty(),
				"the Storehouse chest has " + logs(helper, STORE_CHEST) + " of 128 logs; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(logs(helper, CHESTS[2]) == 64, "another player's lumberjack's chest was hauled from: " + logs(helper, CHESTS[2]) + " left");
		});
	}

	/**
	 * The put-back (B593) after a reload, with no Storehouse or Drop Box and one lumberjack about: the load goes back
	 * into that lumberjack's chest (it was the one its side came from, so before B627 the load stayed on the golem).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"haulerEveryChestPutBack"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "haulerEveryChestPutBack")
	public void aLoadedHaulerWithOnlyOneWorkerPutsTheLoadInThatWorkersChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		wideVillage(helper);
		lumberjack(helper, CHESTS[0], 0);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> helper.assertTrue(logs(helper, CHESTS[0]) == 64 && HaulerGolems.load(golem).isEmpty(),
			"the lumberjack's chest has " + logs(helper, CHESTS[0]) + " of 64 logs; the golem carries " + HaulerGolems.load(golem)));
	}

	/** The open Storehouse with its empty chest and three lumberjacks, 64 logs in each one's chest (as QaHaulerEveryChestBugTests). */
	private static Villager[] village(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		wideVillage(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager[] jacks = new Villager[CHESTS.length];
		for (int i = 0; i < CHESTS.length; i++) {
			jacks[i] = lumberjack(helper, CHESTS[i], 64);
		}
		return jacks;
	}

	/** Workers share their chests across the village (off by default in GameTests). */
	private static void wideVillage(GameTestHelper helper) {
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		Leftovers.after(helper, () -> Village.RADIUS = radius);
	}

	private static Villager lumberjack(GameTestHelper helper, BlockPos chest, int logs) {
		BlockPos block = chest.north();
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(chest, Blocks.CHEST);
		if (logs > 0) {
			((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.OAK_LOG, logs));
		}
		Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
		jack.setNoAi(true);
		Jobs.employ(helper.getLevel(), jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		return jack;
	}

	/** A settled Golem Smith at a smithing table by the Storehouse, with only that Legend loaded (as GolemSmithGameTests). */
	private static Villager golemSmith(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean legends = Legends.ENABLED;
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		Legends.ENABLED = true;
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Legends.ENABLED = legends;
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		Legends.reload(level.getServer().getResourceManager());
		Legend legend = Legends.get(GolemSmith.ID).orElseThrow(() -> new AssertionError("golem_smith.json didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(GolemSmith.ID, legend);
		Legends.setForTest(map);
		helper.setBlock(SMITH_TABLE, Blocks.SMITHING_TABLE);
		Villager smith = helper.spawn(EntityType.VILLAGER, SMITH_TABLE.east());
		smith.setNoAi(true);
		Legends.make(level, smith, legend, "test");
		smith.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(SMITH_TABLE)));
		Leftovers.after(helper, smith::discard);
		helper.assertTrue(GolemSmith.isSmith(smith), "the smith isn't a settled Golem Smith");
		return smith;
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 2, 14)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	private static int logs(GameTestHelper helper, BlockPos chest) {
		return ((Container) helper.getBlockEntity(chest)).countItem(Items.OAK_LOG);
	}
}
