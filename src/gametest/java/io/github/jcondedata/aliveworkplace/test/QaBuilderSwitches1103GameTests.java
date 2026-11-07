package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-11-03), from the spec, not the code: the Builder's switches flipped while a job runs. docs/wiki/scaffolding.md
 * ("Off: those blocks are skipped as before (scaffolds already up still come down into the chests)"), the config's
 * furnishedBuilds ("Off: every build is the vanilla build"), builderRepairs ("Idle builders repair ... Off: ..."), and
 * blueprintUnlocks ("Off: every build is unlocked, as before"). The gaps no test turned: scaffolding switched off and the
 * server restarted mid-build, switched off and back on mid-build, its request on the board after it goes off; furnished
 * builds switched ON while a vanilla build goes up (with and without a restart); builderRepairs switched off while a
 * repair runs; and blueprintUnlocks off then on again (what was unlocked or handed out stays, and the refusal reads).
 */
public class QaBuilderSwitches1103GameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");

	// --- builderScaffolding ------------------------------------------------------------------------------------------

	private record Pillar(ServerLevel level, Villager builder, UUID id, Container chest) {
		BuildSite live() {
			return BuildSiteManager.get(level).get(id);
		}
	}

	/** The pillar up a big area with {@code scaffolding} in the chest, the switch on; everything changed is put back. */
	private static Pillar pillar(GameTestHelper helper, int scaffolding) {
		Leftovers.clear(helper);
		boolean was = Scaffolds.ENABLED;
		Scaffolds.ENABLED = true;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos chestAt = new BlockPos(3, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		if (scaffolding > 0) {
			chest.setItem(2, new ItemStack(Items.SCAFFOLDING, scaffolding));
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(11, 2, 11)), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the pillar didn't start");
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		return new Pillar(level, villager, id, chest);
	}

	/** The server's build sites saved and loaded again, as a restart does. */
	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	/** The pillar is finished, nothing stands, and all {@code in} scaffolding is back (chest, bag or on the ground). */
	private static void assertCleanEnd(GameTestHelper helper, Pillar p, int in, String when) {
		BuildSite live = p.live();
		helper.assertTrue(live == null, when + ": the pillar never ended: " + (live == null ? "" : live.stage() + ", " + live.status()
			+ ", " + live.scaffolds().size() + " scaffold(s) recorded"));
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 17, 21)))) {
			if (p.level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
				throw new GameTestAssertException(when + ": scaffolding left standing at " + helper.relativePos(pos));
			}
		}
		int back = p.chest.countItem(Items.SCAFFOLDING) + QaScaffoldingGameTests.bagCount(p.builder) + QaScaffoldingGameTests.loose(helper, Items.SCAFFOLDING);
		helper.assertValueEqual(back, in, when + ": scaffolding back in the chest, bag and on the ground");
	}

	/**
	 * builderScaffolding off while scaffolds stand, then the server restarts before they are down (wiki: "scaffolds already
	 * up still come down into the chests"; builder invariant: restartable at any tick): the pillar still ends, nothing is
	 * left standing, all 64 come back.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1103ScaffoldOffRestart"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldOffRestart")
	public void scaffoldingOffThenARestartMidBuildLeavesNoneStanding(GameTestHelper helper) {
		Pillar p = pillar(helper, 64);
		String[] when = {null};
		helper.onEachTick(() -> {
			BuildSite live = p.live();
			if (when[0] == null && live != null && live.scaffolds().size() >= 3) {
				Scaffolds.ENABLED = false;
				restart(p.level);
				when[0] = "off and restarted with " + live.scaffolds().size() + " scaffold(s) up at tick " + helper.getTick();
				LOGGER.info("qa1103 scaffold: {}", when[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(when[0] != null, "no scaffolding went up before the switch");
			assertCleanEnd(helper, p, 64, when[0]);
		});
	}

	/**
	 * builderScaffolding off with scaffolds up, then on again 20 ticks later (a player flipping the screen's toggle):
	 * the pillar ends, nothing is left standing, all 64 come back.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1103ScaffoldOffOn"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldOffOn")
	public void scaffoldingOffThenOnAgainMidBuildLeavesNoneStanding(GameTestHelper helper) {
		Pillar p = pillar(helper, 64);
		long[] offAt = {-1};
		boolean[] onAgain = {false};
		helper.onEachTick(() -> {
			BuildSite live = p.live();
			if (offAt[0] < 0 && live != null && live.scaffolds().size() >= 3) {
				Scaffolds.ENABLED = false;
				offAt[0] = helper.getTick();
			} else if (offAt[0] >= 0 && !onAgain[0] && helper.getTick() >= offAt[0] + 20) {
				Scaffolds.ENABLED = true;
				onAgain[0] = true;
				LOGGER.info("qa1103 scaffold: off at {}, on again at {} with {} recorded", offAt[0], helper.getTick(), live == null ? -1 : live.scaffolds().size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(onAgain[0], "the switch never went off and on again (off at " + offAt[0] + ")");
			assertCleanEnd(helper, p, 64, "off at tick " + offAt[0] + " and on again");
		});
	}

	/**
	 * Too little scaffolding (3) puts it on the site's missing list (the Storehouse board); with builderScaffolding then
	 * switched off ("Off: those blocks are skipped as before"), the builder stops asking for scaffolding within 200 ticks
	 * while the build goes on, and the build ends with the 3 back and nothing standing.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qa1103ScaffoldOffAsk"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qa1103ScaffoldOffAsk")
	public void scaffoldingOffStopsAskingForScaffolding(GameTestHelper helper) {
		Pillar p = pillar(helper, 3);
		long[] offAt = {-1};
		String[] stale = {null};
		helper.onEachTick(() -> {
			BuildSite live = p.live();
			if (live == null) {
				return;
			}
			int asked = live.missing().getOrDefault(Items.SCAFFOLDING, 0);
			if (offAt[0] < 0 && asked > 0) {
				Scaffolds.ENABLED = false;
				offAt[0] = helper.getTick();
				LOGGER.info("qa1103 scaffold: {} asked for, switched off at {}", asked, offAt[0]);
			} else if (offAt[0] >= 0 && stale[0] == null && asked > 0 && helper.getTick() >= offAt[0] + 200) {
				stale[0] = "still asking for " + asked + " scaffolding " + (helper.getTick() - offAt[0]) + " ticks after builderScaffolding went off ("
					+ live.stage() + ", " + live.status() + ")";
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(offAt[0] >= 0, "scaffolding was never asked for with 3 in the chest");
			helper.assertTrue(stale[0] == null, String.valueOf(stale[0]));
			assertCleanEnd(helper, p, 3, "asked for, then off");
		});
	}

	// --- furnishedBuilds ---------------------------------------------------------------------------------------------

	private record Nook(ServerLevel level, Villager builder, BuildSite site, BlueprintData.Placement placement, BlockPos chairAt, BuildPlan plan) {
	}

	/** The chair nook begun with furnishedBuilds OFF (the test mod first in the order): a vanilla build. */
	private static Nook vanillaNook(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		boolean enabled = Furnishings.ENABLED;
		Leftovers.after(helper, () -> {
			Furnishings.MODS = mods;
			Furnishings.ENABLED = enabled;
		});
		Furnishings.ENABLED = false;
		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		helper.assertTrue(site != null, "the nook didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertTrue(site.furniture().isVanilla(), "the setup: with the switch off the nook chose " + site.furniture());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Nook(level, builder, site, placement, placement.origin().offset(QaFurnishedGameTests.CHAIR), plan);
	}

	/** Turns furnishedBuilds ON the first tick the vanilla nook has placed something and its chair is still empty. */
	private static void switchedOn(GameTestHelper helper, boolean restart) {
		Nook n = vanillaNook(helper);
		QaFurnishedGameTests.stock(helper, n.plan);
		BlockState plannedChair = QaFurnishedGameTests.stepStates(n.plan).get(n.chairAt);
		helper.assertTrue(plannedChair != null, "the vanilla nook plans no chair at " + n.chairAt);
		String[] when = {null};
		helper.onEachTick(() -> {
			if (when[0] != null) {
				return;
			}
			BuildSite live = BuildSiteManager.get(n.level).get(n.site.id());
			if (live == null) {
				when[0] = "missed: finished before anything could change";
				return;
			}
			if (live.placed() == 0) {
				return;
			}
			if (!n.level.getBlockState(n.chairAt).isAir()) {
				when[0] = "missed: the chair went down with the first blocks";
				return;
			}
			Furnishings.ENABLED = true;
			if (restart) {
				restart(n.level);
			}
			when[0] = "switched on at " + live.stage() + ", " + live.placed() + " placed" + (restart ? ", then restarted" : "");
			LOGGER.info("qa1103 furnished: {}", when[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(when[0] != null && !when[0].startsWith("missed"), String.valueOf(when[0]));
			BuildSite live = BuildSiteManager.get(n.level).get(n.site.id());
			helper.assertTrue(live == null, when[0] + ": still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
			helper.assertTrue(n.site.skipped() == 0, when[0] + ": " + n.site.skipped() + " steps skipped");
			for (Map.Entry<BlockPos, BlockState> e : QaFurnishedGameTests.stepStates(n.plan).entrySet()) {
				BlockState there = n.level.getBlockState(e.getKey());
				helper.assertTrue(there.getBlock() == e.getValue().getBlock(), when[0] + ": " + e.getKey() + " is " + there + ", planned " + e.getValue());
			}
			BlockState chair = n.level.getBlockState(n.chairAt);
			helper.assertTrue(chair.equals(plannedChair), when[0] + ": the vanilla nook's chair is " + chair + ", planned " + plannedChair);
			helper.assertTrue(BuildSiteManager.get(n.level).furniture(n.placement).isVanilla(),
				when[0] + ": the finished vanilla nook records " + BuildSiteManager.get(n.level).furniture(n.placement));
		});
	}

	/** furnishedBuilds turned ON while a vanilla build goes up: a config change never changes a build going up. */
	//$ gametest_ticks_batch AREA '3600' '"qa1103FurnishedOnMid"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1103FurnishedOnMid")
	public void furnishedOnMidVanillaBuildStaysVanilla(GameTestHelper helper) {
		switchedOn(helper, false);
	}

	/** furnishedBuilds turned ON and the server restarted while a vanilla build goes up: still the vanilla build. */
	//$ gametest_ticks_batch AREA '3600' '"qa1103FurnishedOnMidRestart"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1103FurnishedOnMidRestart")
	public void furnishedOnAndARestartMidVanillaBuildStaysVanilla(GameTestHelper helper) {
		switchedOn(helper, true);
	}

	// --- builderRepairs ----------------------------------------------------------------------------------------------

	private static final BlockPos HALL_AT = new BlockPos(14, 2, 14);
	private static final BlockPos WALL_AT = new BlockPos(6, 2, 6);

	/**
	 * builderRepairs turned off while a repair (repairAsk off) is under way, the builder slow (build delay 40): the
	 * builder is not left stuck on the job, and no plank is lost (the hole is mended, or the plank is still in hand).
	 */
	//$ gametest_ticks_batch BIG '2400' '"qa1103RepairsOffMid"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qa1103RepairsOffMid")
	@SuppressWarnings("removal")
	public void builderRepairsOffMidRepairEndsTheJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		VillageHalls.RADIUS = 16;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getServer().getPlayerList().remove(owner);
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(40, level.getServer());
		helper.setBlock(HALL_AT, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(WALL_AT.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1103_wall_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(WALL_AT).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(WALL_AT), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(WALL_AT.offset(1, 0, 0), Blocks.AIR);
		helper.setBlock(WALL_AT.offset(2, 0, 0), Blocks.AIR);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WALL_AT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		boolean[] off = {false};
		helper.runAfterDelay(2, () -> {
			if (!ModAttachments.BUILDER_JOB.has(builder)) {
				Upkeep.look(level, builder); // (the builder's own round may have begun it already)
			}
			BuildSite repair = Builders.activeSite(level, builder);
			helper.assertTrue(repair != null && repair.isRepair(), "the setup: no repair of the two-plank hole started: " + repair);
			helper.assertTrue(repair.placed() == 0, "the setup: the repair placed " + repair.placed() + " before the switch");
			Upkeep.ENABLED = false;
			off[0] = true;
			LOGGER.info("qa1103 repairs: builderRepairs off with the repair just begun");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "not switched off yet");
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "the builder is still on the repair after builderRepairs went off");
			helper.assertTrue(Builders.activeSite(level, builder) == null, "a site is still open for the builder");
			int mended = 0;
			for (int x = 1; x <= 2; x++) {
				mended += helper.getBlockState(WALL_AT.offset(x, 0, 0)).is(Blocks.OAK_PLANKS) ? 1 : 0;
			}
			int held = chest.countItem(Items.OAK_PLANKS) + builder.getInventory().countItem(Items.OAK_PLANKS)
				+ QaScaffoldingGameTests.loose(helper, Items.OAK_PLANKS);
			LOGGER.info("qa1103 repairs: job ended with {} of 2 mended and {} planks held", mended, held);
			helper.assertValueEqual(mended + held, 8, "planks in the wall's hole (" + mended + "), the chest, the builder and on the ground");
		});
	}

	// --- blueprintUnlocks --------------------------------------------------------------------------------------------

	private static final BlockPos U_HALL = new BlockPos(1, 2, 1);
	private static final BlockPos U_TABLE = new BlockPos(5, 2, 5);

	private static void unlocksSetUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int radius = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = true;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = radius;
		});
		helper.setBlock(U_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(U_TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
	}

	@SuppressWarnings("removal")
	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(U_TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static ItemStack blueprintOf(ServerPlayer player, ResourceLocation id) {
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT) && BlueprintItem.data(s).map(d -> d.structure().equals(id)).orElse(false)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * blueprintUnlocks on, Smithy II unlocked; switched off, the locked Market Stall is taken from the Table; switched on
	 * again: Smithy II and the starting set are still unlocked (nothing taken away), and the Market Stall blueprint the
	 * player holds is still taken by a builder.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1103UnlocksOffOn"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103UnlocksOffOn")
	public void unlocksOffThenOnTakesNothingAway(GameTestHelper helper) {
		unlocksSetUp(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(U_HALL);
			BlockPos table = helper.absolutePos(U_TABLE);
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(level, table)), "the Table isn't in the test's village");
			helper.assertTrue(BlueprintUnlocks.unlock(level, hall, StarterBlueprints.SMITHY_2.id(), List.of()), "Smithy II didn't unlock");
			Set<ResourceLocation> before = new HashSet<>(BlueprintUnlocks.unlocked(level, hall));
			ServerPlayer player = playerAtTable(helper);
			try {
				player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
				BlueprintUnlocks.ENABLED = false;
				helper.assertTrue(TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "the Market Stall was refused with the switch off");
				BlueprintUnlocks.ENABLED = true;
				Set<ResourceLocation> after = BlueprintUnlocks.unlocked(level, hall);
				Set<ResourceLocation> lost = new HashSet<>(before);
				lost.removeAll(after);
				helper.assertTrue(lost.isEmpty(), "switched off and on again, the village lost its unlocks " + lost);
				helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.SMITHY_2.id()) == BlueprintUnlocks.State.UNLOCKED,
					"Smithy II is locked again after the switch went off and on");
				helper.assertTrue(TableServer.take(player, table, StarterBlueprints.SMITHY_2.id()), "the Table refused Smithy II, unlocked before the switch went off");
				ItemStack stall = blueprintOf(player, StarterBlueprints.MARKET_STALL.id());
				helper.assertFalse(stall.isEmpty(), "the Market Stall blueprint handed out with the switch off is gone from the player's inventory");
				BlueprintData data = BlueprintItem.data(stall).orElseThrow().withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(),
					helper.absolutePos(new BlockPos(2, 2, 9)), Rotation.NONE, Mirror.NONE)));
				stall.set(ModComponents.BLUEPRINT, data);
				helper.setBlock(new BlockPos(9, 2, 1), ModBlocks.BUILDERS_BENCH);
				Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2));
				Builders.employ(level, builder, helper.absolutePos(new BlockPos(9, 2, 1)));
				ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(player.getUUID(), player.getGameProfile().getName()));
				Builders.assign(player, builder, stall);
				BuildSite site = Builders.activeSite(level, builder);
				if (site != null) {
					BuildSiteManager.get(level).remove(site.id());
				}
				helper.assertTrue(site != null && site.structure().equals(StarterBlueprints.MARKET_STALL.id()),
					"a builder refused the Market Stall blueprint handed out while the switch was off: " + site);
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}

	/**
	 * Switched on again, a build that is still locked is refused at the Table with the sentence the player reads: the
	 * build's English name and how to get it, no raw key, and no Blank Blueprint used.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1103UnlocksOnAgainRefusal"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103UnlocksOnAgainRefusal")
	public void unlocksOnAgainRefusesALockedBuildInPlainWords(GameTestHelper helper) {
		unlocksSetUp(helper);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(U_HALL);
			BlockPos table = helper.absolutePos(U_TABLE);
			ServerPlayer player = playerAtTable(helper);
			try {
				player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
				BlueprintUnlocks.ENABLED = false;
				helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL.id()) == BlueprintUnlocks.State.UNLOCKED,
					"the Market Stall is locked with the switch off");
				BlueprintUnlocks.ENABLED = true;
				helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.MARKET_STALL.id()) == BlueprintUnlocks.State.LOCKED,
					"the Market Stall isn't locked once the switch is on again (it was never unlocked)");
				helper.assertFalse(TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "the Table handed out the locked Market Stall");
				helper.assertValueEqual(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT), 2, "Blank Blueprints after a refusal");
				String hint = BlueprintUnlocks.hint(StarterBlueprints.MARKET_STALL.id());
				String text = Component.translatable("message.aliveworkplace.table.locked", Blueprints.displayName(StarterBlueprints.MARKET_STALL.id()),
					Component.translatable(hint)).getString();
				LOGGER.info("qa1103 unlocks: the refusal reads \"{}\"", text);
				helper.assertFalse(text.contains("aliveworkplace.") || text.contains("%"), "the refusal shows a raw key or placeholder: " + text);
				helper.assertTrue(text.contains("Market Stall"), "the refusal doesn't name the Market Stall: " + text);
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
			helper.succeed();
		});
	}
}
