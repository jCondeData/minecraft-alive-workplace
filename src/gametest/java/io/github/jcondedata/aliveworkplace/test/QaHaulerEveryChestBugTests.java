package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (29.15, HaulerGolems.choose): a Hauler Golem carries the village workers' goods to the Storehouse, so every
 * worker's chest with a trip's worth of goods is emptied into it in time. Suspected: {@code choose} lists the stashes
 * from one "context" villager's side (a Golem Smith, else the first villager found), and {@code Village.mates} leaves
 * that villager out, so the context worker's own chest is never hauled.
 *
 * <p>An open, reachable Storehouse with an empty chest; three lumberjacks, each chest more than
 * {@code SupplyContainers.RADIUS} (8) from the other workstations and the Storehouse, so every chest is one worker's
 * own; 64 logs (a trip's worth: {@code PorterWork.MIN_LOAD} is 16) in each. Whichever lumberjack is found first, if
 * its chest is left out, that chest keeps its logs and the test fails every run; the message names it.
 */
public class QaHaulerEveryChestBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 4);
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};
	private static final BlockPos SMITH_TABLE = new BlockPos(6, 2, 12);
	private static final int TOTAL = 64 * 3;

	/** No Golem Smith: every lumberjack's chest is emptied into the Storehouse's chest. */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerEveryChest"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerEveryChest")
	public void aHaulerEmptiesEveryLumberjacksChestIntoTheStorehouse(GameTestHelper helper) {
		Villager[] jacks = village(helper);
		IronGolem golem = hauler(helper);
		helper.succeedWhen(() -> assertAllHauled(helper, golem, jacks, "no Golem Smith"));
	}

	/** Control: with a settled Golem Smith by the Storehouse, every lumberjack's chest is emptied into it too. */
	//$ gametest_ticks_batch HUGE '4800' '"qaHaulerEveryChestSmith"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaHaulerEveryChestSmith")
	public void aHaulerWithAGolemSmithByTheStorehouseEmptiesEveryLumberjacksChest(GameTestHelper helper) {
		Villager[] jacks = village(helper);
		Villager smith = golemSmith(helper);
		helper.assertTrue(GolemSmith.isSmith(smith), "the smith isn't a settled Golem Smith");
		IronGolem golem = hauler(helper);
		String setUp = "a Golem Smith by the Storehouse (a " + smith.getVillagerData().getProfession() + ", Village.takesPart: "
			+ io.github.jcondedata.aliveworkplace.work.Village.takesPart(smith) + ")";
		helper.succeedWhen(() -> assertAllHauled(helper, golem, jacks, setUp));
	}

	/** The open Storehouse with its empty chest and three lumberjacks, 64 logs in each one's chest; no Drop Box. */
	private static Villager[] village(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ServerLevel level = helper.getLevel();
		// workers share their chests across the village (off by default in GameTests)
		int radius = io.github.jcondedata.aliveworkplace.work.Village.RADIUS;
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 48;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager[] jacks = new Villager[CHESTS.length];
		for (int i = 0; i < CHESTS.length; i++) {
			BlockPos block = CHESTS[i].north();
			helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
			helper.setBlock(CHESTS[i], Blocks.CHEST);
			((Container) helper.getBlockEntity(CHESTS[i])).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
			jack.setNoAi(true);
			Jobs.employ(level, jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
			jacks[i] = jack;
		}
		return jacks;
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
		return smith;
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 2, 14)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/** Every lumberjack's chest is empty and all {@link #TOTAL} logs are in the Storehouse's chest; else names who kept theirs. */
	private static void assertAllHauled(GameTestHelper helper, IronGolem golem, Villager[] jacks, String setUp) {
		StringBuilder kept = new StringBuilder();
		for (int i = 0; i < CHESTS.length; i++) {
			int left = logs(helper, CHESTS[i]);
			if (left > 0) {
				kept.append(kept.isEmpty() ? "" : "; ").append("lumberjack ").append(i).append("'s chest at ").append(CHESTS[i])
					.append(" kept ").append(left).append(" logs");
			}
		}
		int stored = logs(helper, STORE_CHEST);
		helper.assertTrue(kept.isEmpty(), setUp + ": not every worker's chest was hauled (" + kept + "); Storehouse chest has " + stored
			+ ", golem carries " + carried(golem) + ", first villager found by the Storehouse: lumberjack " + firstFound(helper, jacks));
		helper.assertTrue(carried(golem) == 0 && stored == TOTAL, setUp + ": the lumberjacks' chests are empty but the Storehouse chest has "
			+ stored + " of " + TOTAL + " logs and the golem carries " + carried(golem));
		LOGGER.info("qa hauler every chest ({}): all {} logs in the Storehouse", setUp, TOTAL);
	}

	/** Which lumberjack (index, or -1) is first in a search round the Storehouse like HaulerGolems.choose's (for the message). */
	private static int firstFound(GameTestHelper helper, Villager[] jacks) {
		Villager first = helper.getLevel().getEntitiesOfClass(Villager.class,
			new net.minecraft.world.phys.AABB(helper.absolutePos(STOREHOUSE)).inflate(io.github.jcondedata.aliveworkplace.work.Village.RADIUS),
			v -> v.isAlive() && io.github.jcondedata.aliveworkplace.work.Village.takesPart(v)).stream().findFirst().orElse(null);
		for (int i = 0; i < jacks.length; i++) {
			if (jacks[i] == first) {
				return i;
			}
		}
		return -1;
	}

	private static int logs(GameTestHelper helper, BlockPos chest) {
		return ((Container) helper.getBlockEntity(chest)).countItem(Items.OAK_LOG);
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}
}
