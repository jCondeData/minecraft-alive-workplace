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
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1101-2138) of B722 ("a Hauler Golem whose Storehouse is across water doesn't stay stuck in the water with
 * its load; it gets out, takes the load to a Storehouse it can reach and keeps hauling"), written from the bug's text
 * and the fix's promise (a golem at work swims; a plain golem, and one in a fight, keep vanilla's sinking): a hauler
 * reloaded while down in deep water, a plain golem sinking beside a hauler that swims out, a hauler that starts a fight
 * in the water. (Water a block below its banks still traps a hauler, and a hauler going round a lava trench dips into
 * it: QaHaulerWater1101dBugTests on tests/hauler-water-1101.) And B627's side rule ({@link HaulerGolems#sideOf}): a settled
 * Golem Smith sets the side even when another player's adult worker is found first.
 */
public class QaHaulerWater1101dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** Standing height on the raised ground: stone at y 1..4 (the floor is y 1). */
	private static final int TOP = 5;
	private static final Employer OTHER_PLAYER = new Employer(UUID.fromString("6a1e2b3c-0000-4000-8000-000000001101"), "Other");

	// ---- helpers ----

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	private static int logs(GameTestHelper helper, BlockPos chest) {
		return ((Container) helper.getBlockEntity(chest)).countItem(Items.OAK_LOG);
	}

	/** Oak logs lying on the ground anywhere near the test (a load dropped instead of stored). */
	private static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16), e -> e.getItem().is(Items.OAK_LOG))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static IronGolem hauler(GameTestHelper helper, BlockPos near) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(near), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/** Puts {@code golem} at the middle of block {@code pos} (relative), as a push or a fall would. */
	private static void put(GameTestHelper helper, IronGolem golem, BlockPos pos) {
		BlockPos abs = helper.absolutePos(pos);
		golem.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		golem.setDeltaMovement(0, 0, 0);
	}

	/** Raised stone ground over x and z 0..{@code size}: stand height {@link #TOP}. */
	private static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x <= size; x++) {
			for (int z = 0; z <= size; z++) {
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	/**
	 * A pool in the raised ground, x and z {@code from..to}, on a stone bottom, with sheer sides: water at y 1..4 (four
	 * deep, its surface level with the ground round it, as a moat dug and filled to the brim).
	 */
	private static void pool(GameTestHelper helper, int from, int to) {
		for (int x = from; x <= to; x++) {
			for (int z = from; z <= to; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y < TOP; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
				}
			}
		}
	}

	private static IronGolem reloaded(GameTestHelper helper, IronGolem golem) {
		CompoundTag tag = golem.saveWithoutId(new CompoundTag());
		golem.discard();
		IronGolem copy = EntityType.IRON_GOLEM.create(helper.getLevel());
		helper.assertTrue(copy != null, "an iron golem to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	/** Everything a delivered load must show: every log in the chest, none on the golem, none on the ground. */
	private static void delivered(GameTestHelper helper, IronGolem golem, BlockPos chest, int expected, String where) {
		int stored = logs(helper, chest);
		helper.assertTrue(stored == expected, where + ": the Storehouse chest holds " + stored + " of " + expected + " logs; the golem carries "
			+ carried(golem) + " at " + golem.position() + " (in water: " + golem.isInWater() + "), heading for "
			+ HaulerGolems.storehouse(helper.getLevel(), golem));
		helper.assertTrue(carried(golem) == 0, where + ": the golem still carries " + carried(golem) + " after storing " + stored);
		helper.assertTrue(dropped(helper) == 0, where + ": " + dropped(helper) + " logs lie on the ground");
	}

	// ---- B722: water ----

	/**
	 * The save is closed and loaded again while the loaded hauler is down in the deep pool. The loaded golem is at work at
	 * once (it swims), gets out and stores all 64 logs: the load is neither lost nor doubled by the reload.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterReload"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterReload")
	public void aHaulerReloadedWhileInDeepWaterStillStoresItsLoadOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 22);
		pool(helper, 3, 8);
		BlockPos chest = new BlockPos(19, TOP, 18);
		helper.setBlock(new BlockPos(18, TOP, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem golem = hauler(helper, new BlockPos(12, TOP - 1, 5));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		put(helper, golem, new BlockPos(5, 1, 5));
		IronGolem[] now = {golem};
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(golem.isInWater(), "the hauler should be in the pool when the save is closed: at " + golem.position());
			now[0] = reloaded(helper, golem);
			helper.assertTrue(carried(now[0]) == 64, "the reloaded hauler carries " + carried(now[0]) + " of its 64 logs");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(now[0] != golem, "not reloaded yet");
			delivered(helper, now[0], chest, 64, "reloaded in deep water");
			helper.assertTrue(golem.isRemoved(), "the golem from before the reload is still about");
		});
	}

	/**
	 * The swimming must not leak to golems that aren't at work: a plain iron golem dropped into one deep pool sinks to the
	 * bottom and stays under (vanilla), while a loaded hauler dropped into the pool beside it swims out and stores its load.
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qaHaulerWaterPlainSinks"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaHaulerWaterPlainSinks")
	public void aPlainIronGolemStillSinksWhileAHaulerBesideItSwimsOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 24);
		pool(helper, 3, 8);
		pool(helper, 14, 19);
		BlockPos chest = new BlockPos(22, TOP, 4);
		helper.setBlock(new BlockPos(21, TOP, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem plain = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(5, 1, 5));
		Leftovers.after(helper, plain::discard);
		IronGolem golem = hauler(helper, new BlockPos(12, TOP - 1, 12));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		put(helper, golem, new BlockPos(16, 1, 16));
		double bottom = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
		boolean[] plainChecked = {false};
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(GolemSmith.role(plain) == null, "the plain golem has a role: " + GolemSmith.role(plain));
			helper.assertFalse(plain.getNavigation().canFloat(), "a plain iron golem's paths float on water: the swimming leaked to a golem not at work");
			helper.assertTrue(plain.getY() < bottom + 0.5 && plain.isUnderWater(),
				"a plain iron golem in water four deep should sink and stay under (vanilla), but is at y " + plain.getY()
					+ " (pool bottom " + bottom + "), under water " + plain.isUnderWater());
			plainChecked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(plainChecked[0], "the plain golem isn't checked yet");
			helper.assertTrue(plain.isInWater() && plain.getY() < bottom + 0.5, "the plain golem left the bottom of its pool: at " + plain.position());
			delivered(helper, golem, chest, 64, "a hauler beside a plain golem");
		});
	}

	/**
	 * A hauler in deep water that starts a fight stops being at work: from then on its paths no longer float (vanilla's
	 * sinking, as the fix promises for a golem in a fight). Before the fight they do.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaHaulerWaterFight"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaHaulerWaterFight")
	public void aHaulerThatStartsAFightInWaterNoLongerFloats(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ground(helper, 22);
		pool(helper, 3, 8);
		IronGolem golem = hauler(helper, new BlockPos(12, TOP - 1, 12));
		put(helper, golem, new BlockPos(5, 1, 5));
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(18, TOP, 18));
		zombie.setNoAi(true);
		zombie.setInvulnerable(true);
		Leftovers.after(helper, zombie::discard);
		boolean[] fighting = {false};
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(golem.getNavigation().canFloat(), "a hauler at work in water should float its paths (B722) before any fight");
			golem.setTarget(zombie);
			fighting[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fighting[0], "no fight yet");
			helper.assertTrue(golem.getTarget() == zombie, "the golem dropped its target: " + golem.getTarget());
			helper.assertFalse(golem.getNavigation().canFloat(), "a hauler in a fight still floats its paths: the work's swimming didn't reset");
		});
	}

	// ---- B627: whose side (sideOf) ----

	/**
	 * Another player's adult lumberjack stands where the hauler's side is looked up and is found before the settled Golem
	 * Smith; the village's own lumberjack works farther off. The smith sets the side, so the village lumberjack's 64 logs
	 * are hauled to the Storehouse and the other player's 64 are left alone.
	 */
	//$ gametest_ticks_batch HUGE '3600' '"qaHaulerWaterSmithSide"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qaHaulerWaterSmithSide")
	public void aGolemSmithSetsTheSideThoughAnotherPlayersWorkerIsFoundFirst(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		Leftovers.after(helper, () -> Village.RADIUS = radius);
		BlockPos storehouse = new BlockPos(3, 2, 4);
		BlockPos storeChest = new BlockPos(4, 2, 4);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		BlockPos smithTable = new BlockPos(6, 2, 12);
		BlockPos theirs = new BlockPos(12, 2, 27);
		BlockPos ours = new BlockPos(24, 2, 20);
		// the other player's lumberjack first, on the smith's very spot, so the level lists him before the smith
		Villager other = lumberjack(helper, theirs, smithTable.east());
		ModAttachments.BUILDER_EMPLOYER.set(other, OTHER_PLAYER);
		lumberjack(helper, ours, ours.north().east());
		Villager smith = golemSmith(helper, smithTable);
		Villager side = HaulerGolems.sideOf(helper.getLevel(), helper.absolutePos(storehouse));
		helper.assertTrue(side == smith, "the hauler's side should be the Golem Smith's, not "
			+ (side == null ? "nobody" : side == other ? "the other player's lumberjack, found first" : "a " + side.getVillagerData().getProfession()));
		IronGolem golem = hauler(helper, new BlockPos(14, 1, 14));
		helper.succeedWhen(() -> {
			helper.assertTrue(logs(helper, theirs) == 64, "another player's lumberjack's chest was hauled from: " + logs(helper, theirs)
				+ " of 64 left; the Storehouse chest has " + logs(helper, storeChest));
			helper.assertTrue(logs(helper, ours) == 0 && logs(helper, storeChest) == 64 && HaulerGolems.load(golem).isEmpty(),
				"the village lumberjack's chest still has " + logs(helper, ours) + " logs; the Storehouse chest has " + logs(helper, storeChest)
					+ " of 64; the golem carries " + HaulerGolems.load(golem));
		});
	}

	/** A lumberjack with 64 logs in {@code chest}, his chopping block north of it, standing at {@code at}. */
	private static Villager lumberjack(GameTestHelper helper, BlockPos chest, BlockPos at) {
		BlockPos block = chest.north();
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(chest, Blocks.CHEST);
		((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		Villager jack = helper.spawn(EntityType.VILLAGER, at);
		jack.setNoAi(true);
		Jobs.employ(helper.getLevel(), jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		Leftovers.after(helper, jack::discard);
		helper.assertTrue(Village.takesPart(jack), "the lumberjack doesn't take part in the village");
		return jack;
	}

	/** A settled Golem Smith at a smithing table, with only that Legend loaded (as HaulerEveryChestGameTests). */
	private static Villager golemSmith(GameTestHelper helper, BlockPos table) {
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
		helper.setBlock(table, Blocks.SMITHING_TABLE);
		Villager smith = helper.spawn(EntityType.VILLAGER, table.east());
		smith.setNoAi(true);
		Legends.make(level, smith, legend, "test");
		smith.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(table)));
		Leftovers.after(helper, smith::discard);
		helper.assertTrue(GolemSmith.isSmith(smith) && !smith.isBaby() && smith.isAlive(), "the smith isn't a settled adult Golem Smith");
		return smith;
	}
}
