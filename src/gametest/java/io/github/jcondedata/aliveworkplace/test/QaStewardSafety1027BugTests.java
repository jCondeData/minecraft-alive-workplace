package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (2026-10-27) for ROADMAP 27.19: spec-level tests that fail against the mod as it is (bug candidates, not run in CI
 * until fixed). The village is {@link QaOldHousesGameTests#village}'s; each test runs alone in its batch.
 */
public class QaStewardSafety1027BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * "A block a player puts in the way after the build started stays": building blocks a player places in a hall's area
	 * (stripped logs, oak wood, stripped wood: kinds that never generate in the wild) are a player's block the Steward's site must leave,
	 * like the cobblestone the existing test uses. The site decides with {@link StewardSafety#playersBlock}
	 * (BuilderWork: PLAYER_BLOCK, else BREAK).
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027BugNaturalKinds"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027BugNaturalKinds")
	public void aPlayersStrippedLogOrWoodIsAPlayersBlock(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		// (natural is judged by kind, ROADMAP Notes 2026-10-05; these kinds never grow in the wild, so they are always a player's)
		Block[] kinds = {Blocks.COBBLESTONE, Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.OAK_WOOD, Blocks.STRIPPED_OAK_WOOD};
		List<String> cleared = new ArrayList<>();
		for (int i = 0; i < kinds.length; i++) {
			BlockPos at = helper.absolutePos(new BlockPos(10 + 2 * i, 2, 12));
			helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), at, kinds[i]), "setup: " + kinds[i] + " wasn't placed");
			BlockState state = v.level().getBlockState(at);
			helper.assertTrue(state.is(kinds[i]), "setup: placing put " + state);
			helper.assertTrue(PlayerBuilt.get(v.level()).marked(at), "setup: " + kinds[i] + " didn't mark its section");
			if (!StewardSafety.playersBlock(v.level(), at, state)) {
				cleared.add(state.getBlock().getName().getString());
			}
		}
		helper.assertTrue(cleared.isEmpty(), "blocks a player placed in a marked section would be cleared by the Steward's site as natural: " + cleared);
		helper.succeed();
	}

	/**
	 * Never in Keep Clear, at any hall radius the config allows (16 to 160): at radius 32 a plan cell is 2×2 blocks, and a
	 * Keep Clear cell inside a 9×9 plan, touching none of the points checked every 4 blocks, must still refuse it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027BugKeepClearSmall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027BugKeepClearSmall")
	public void oneKeepClearCellInsideAPlanRefusesItAtRadius32(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 32;
		VillageHalls.forgetNearest();
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		helper.assertTrue(CityPlan.cellSize() == 2, "setup: cell size " + CityPlan.cellSize());
		QaStewardSafetyOldHouses1027GameTests.keepClearCellInsideAPlan(helper, v);
		helper.succeed();
	}

	/**
	 * "A block a player places ... marks its 16×16×16 section": a bed placed with its foot on a section's south edge puts
	 * its head in the next section, and that block is the player's too, so its section is marked.
	 */
	//$ gametest_ticks_batch AREA '40' '"qa1027BugBedHead"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qa1027BugBedHead")
	public void aBedsHeadAcrossASectionEdgeMarksItsSection(GameTestHelper helper) {
		QaOldHousesGameTests.Village v = QaOldHousesGameTests.village(helper);
		int base = helper.absolutePos(BlockPos.ZERO).getZ();
		int relZ = 8 + Math.floorMod(15 - (base + 8), 16);
		BlockPos foot = helper.absolutePos(new BlockPos(16, 2, relZ));
		helper.assertTrue((foot.getZ() & 15) == 15, "setup: not a section's south edge");
		v.owner().setYRot(0); // facing south: the head goes to z + 1
		v.owner().setYHeadRot(0);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), foot, Blocks.RED_BED), "setup: the bed wasn't placed");
		BlockState footState = v.level().getBlockState(foot);
		BlockPos head = foot.south();
		BlockState headState = v.level().getBlockState(head);
		helper.assertTrue(footState.is(Blocks.RED_BED) && footState.getValue(BedBlock.PART) == BedPart.FOOT
			&& headState.is(Blocks.RED_BED) && headState.getValue(BedBlock.PART) == BedPart.HEAD, "setup: the bed is " + footState + " / " + headState);
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(foot), "the bed's foot didn't mark its section");
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(head), "the bed's head, a block the player placed, left its section " + head.toShortString()
			+ " unmarked: a plan there is " + StewardSafety.check(v.level(), v.hall(),
				new net.minecraft.world.level.levelgen.structure.BoundingBox(head.getX() - 1, head.getY(), head.getZ(), head.getX() + 1, head.getY() + 2, head.getZ() + 3), false)
				.map(Enum::name).orElse("allowed"));
		helper.succeed();
	}
	// ---- a running Steward site (StewardSafetyGameTests' village and Well) ----------------------------------------------

	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/qa1027_well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private record Steward(ServerLevel level, BlockPos hall, ServerPlayer owner, VillageHallBlockEntity entity) {
	}

	private static Steward stewardVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.stewardSites(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Steward(level, hall, owner, entity);
	}

	private static BuildSite runningWell(GameTestHelper helper, Steward v) {
		ServerLevel level = v.level();
		level.setDayTime(StewardWishes.day(level) * 24000L + 1000L);
		GameRules rules = level.getGameRules();
		boolean free = rules.getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = rules.getInt(ModGameRules.BUILD_DELAY);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		Plots.Verdict verdict = Plots.check(level, v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		StewardWishes.Wish wish = new StewardWishes.Wish(RULE, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(level, v.hall(), wish, plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(p.isPresent(), "setup: not proposed");
		helper.assertTrue(StewardDesk.approve(level, v.hall(), null, p.get().id()).ok(), "setup: the Steward didn't start the Well");
		List<BuildSite> open = StewardDesk.openSites(level, v.hall());
		helper.assertTrue(open.size() == 1, "setup: " + open.size() + " sites");
		return open.get(0);
	}

	/**
	 * The Done when, with a log: a stripped oak log a player places where the running Well wants a block is still there
	 * when the Well is done (as the existing test's cobblestone is), and counted as skipped.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1027BugRunningLog"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1027BugRunningLog")
	public void aStrippedLogAPlayerPlacesInARunningSiteStays(GameTestHelper helper) {
		Steward v = stewardVillage(helper);
		BuildSite site = runningWell(helper, v);
		BoundingBox box = site.plan(v.level()).bounds();
		Map<BlockPos, Boolean> solid = new HashMap<>();
		for (var step : site.plan(v.level()).steps(BuildPlan.Stage.STRUCTURE)) {
			solid.put(step.pos(), !step.state().isAir());
		}
		BlockPos spot = null;
		for (int y = box.minY(); y <= box.maxY() && spot == null; y++) {
			for (int x = box.minX(); x <= box.maxX() && spot == null; x++) {
				for (int z = box.minZ(); z <= box.maxZ() && spot == null; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (v.level().getBlockState(p).isAir() && v.level().getBlockState(p.below()).isSolid() && solid.getOrDefault(p, false)) {
						spot = p;
					}
				}
			}
		}
		helper.assertTrue(spot != null, "setup: no open spot where the well wants a block");
		BlockPos at = spot;
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), at, Blocks.STRIPPED_OAK_LOG), "setup: not placed at " + at);
		helper.assertTrue(v.level().getBlockState(at).is(Blocks.STRIPPED_OAK_LOG), "setup: placing put " + v.level().getBlockState(at));
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(at), "setup: the log didn't mark its section");
		String[] lost = {null};
		helper.onEachTick(() -> {
			if (lost[0] == null && !v.level().getBlockState(at).is(Blocks.STRIPPED_OAK_LOG)) {
				lost[0] = "stage " + site.stage() + ", now " + v.level().getBlockState(at);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lost[0] == null, "the player's stripped oak log at " + at.toShortString() + " (in the Well's box " + box + ")" + " was taken by the Steward's site (" + lost[0] + ")");
			helper.assertTrue(site.isDone() || BuildSiteManager.get(v.level()).get(site.id()) == null, "still building: " + site.stage());
			helper.assertTrue(site.playerBlocks() > 0 && site.skipped() > 0, "the player's log wasn't counted as skipped: " + site.playerBlocks() + "/" + site.skipped());
		});
	}
}
