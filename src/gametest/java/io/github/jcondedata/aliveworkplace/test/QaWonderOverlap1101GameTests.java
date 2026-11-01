package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1101-0638), from the specs of B747, B748 and B750 (Wonders against house builds and the Steward's plots):
 * the boundaries from both sides (one shared column refused, one block clear allowed), the Steward's {@link Plots#CLEAR}
 * margin round a waiting Wonder at exactly its width, three Wonders with the going-up one listed last, a house hanging
 * high above a waiting Wonder, a turned Wonder's real ground, the exact sentences players read, and a kept Steward
 * search that must start over when a Wonder is laid out on its plot.
 */
public class QaWonderOverlap1101GameTests {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	/** en_us.json's message.aliveworkplace.assign.overlaps with the folly's name. */
	private static final String HOUSE_REFUSED = "That spot overlaps another build in progress (Test Folly).";
	/** en_us.json's message.aliveworkplace.wonder_plan.over_site with the hut's name. */
	private static final String PLAN_OVER_HUT = "It would stand over the Test Hut, a build in progress";

	// ---- shared helpers ----

	/** Every laid-out part of {@code site} as one footprint (all stages). */
	private static BoundingBox footprint(ServerLevel level, WonderSite site) {
		BoundingBox out = null;
		for (WonderUpkeep.Part part : WonderUpkeep.parts(level, site)) {
			BoundingBox b = BlueprintOutline.bounds(part.placement(), BlueprintLibrary.get(level, part.plain()).orElseThrow().size());
			out = out == null ? b : BoundingBox.encapsulatingBoxes(List.of(out, b)).orElseThrow();
		}
		if (out == null) {
			throw new AssertionError("set-up: the Wonder has no laid-out parts");
		}
		return out;
	}

	private static boolean sharesColumn(BoundingBox a, BoundingBox b) {
		return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
	}

	/** A Wonder begun round {@code hall}, laid out at {@code origin} (absolute), finished if {@code done}. */
	private static WonderSite wonder(GameTestHelper helper, BlockPos hall, BlockPos origin, Rotation turn, boolean done) {
		ServerLevel level = helper.getLevel();
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, turn, Mirror.NONE), origin.offset(-1, 0, -1));
		if (done) {
			manager.finish(site.id(), 2);
		}
		WonderSite now = manager.site(site.id()).orElseThrow();
		helper.assertTrue(done ? now.done() : now.goingUp() && !now.started() && now.parts().isEmpty(),
			"set-up: the Wonder isn't " + (done ? "finished" : "waiting for its goods"));
		return now;
	}

	// ---- the builder's side (B750, B714) ----

	private record Crew(ServerLevel level, BlockPos hall, Villager builder, QaStageFiveGameTests.Client client, Vec3i hut) {
	}

	/** A hall at (24, 2, 24), village protection off, a builder at his table; a survival player to hand him blueprints. */
	private static Crew crew(GameTestHelper helper, BlockPos benchAt, String who) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		BlockPos hallAt = new BlockPos(24, 2, 24);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(who));
		Vec3i size = BlueprintLibrary.get(level, HUT).orElseThrow().size();
		return new Crew(level, hall, builder, c, size);
	}

	/** The player hands the builder a Test Hut placed unturned at {@code origin} (absolute); the site he took, or null. */
	private static BuildSite handHut(GameTestHelper helper, Crew crew, BlockPos origin) {
		ItemStack blueprint = BlueprintItem.create(HUT, crew.hut());
		BlueprintData.Placement at = new BlueprintData.Placement(crew.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(at)));
		crew.client().seen().clear();
		crew.client().player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(crew.client().player(), crew.builder(), crew.client().seen());
		BuildSite taken = Builders.activeSite(crew.level(), crew.builder());
		if (taken != null) {
			Leftovers.after(helper, () -> BuildSiteManager.get(crew.level()).remove(taken.id()));
		}
		return taken;
	}

	private static List<String> said(Crew crew) {
		return crew.client().seen().stream().map(Component::getString).toList();
	}

	private static void assertRefused(GameTestHelper helper, Crew crew, BuildSite taken, String what) {
		helper.assertTrue(taken == null, "the builder took " + what + "; he said " + said(crew));
		helper.assertTrue(said(crew).contains(HOUSE_REFUSED), what + ": expected the player to read '" + HOUSE_REFUSED + "', got " + said(crew));
	}

	/**
	 * B750/B714 boundary from both sides: a Test Hut whose footprint shares exactly one column (its west wall on the
	 * waiting Wonder's east edge) is refused with en_us's sentence; moved one block east, clear of it, the builder takes it.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderOverlapEdge1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderOverlapEdge1101")
	public void qaAHouseOnAWaitingWondersEdgeIsRefusedAndOneBlockClearIsTaken(GameTestHelper helper) {
		Crew crew = crew(helper, new BlockPos(26, 2, 4), "qaWE");
		BoundingBox fp = footprint(crew.level(), wonder(helper, crew.hall(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, false));
		BlockPos touching = new BlockPos(fp.maxX(), fp.minY(), fp.minZ() + 1);
		BuildSite onEdge = handHut(helper, crew, touching);
		assertRefused(helper, crew, onEdge, "a hut sharing one column with the waiting Test Folly (" + fp + ")");
		BuildSite clear = handHut(helper, crew, touching.east());
		helper.assertTrue(clear != null, "a hut one block clear of the waiting Test Folly was refused; he said " + said(crew));
		helper.assertTrue(!sharesColumn(BlueprintOutline.bounds(clear.placement(), crew.hut()), fp), "set-up: the clear hut overlaps the Wonder");
		helper.succeed();
	}

	/**
	 * B750 with three Wonders: two finished (listed first) and the waiting one begun last; the hut covers the corner where
	 * all three meet. Expected: refused, naming the Wonder going up.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderOverlapThree1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderOverlapThree1101")
	public void qaAHouseOverTwoFinishedWondersAndAWaitingOneListedLastIsRefused(GameTestHelper helper) {
		Crew crew = crew(helper, new BlockPos(26, 2, 4), "qaW3");
		ServerLevel level = crew.level();
		BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
		BoundingBox fa = footprint(level, wonder(helper, crew.hall(), a, Rotation.NONE, true));
		int w = fa.getXSpan();
		int d = fa.getZSpan();
		WonderSite b = wonder(helper, crew.hall(), a.offset(w, 0, 0), Rotation.NONE, true);
		WonderSite c = wonder(helper, crew.hall(), a.offset(0, 0, d), Rotation.NONE, false);
		List<WonderSite> listed = WonderManager.get(level).sites();
		helper.assertTrue(listed.get(listed.size() - 1).id().equals(c.id()), "set-up: the waiting Wonder isn't listed last");
		BlockPos corner = new BlockPos(fa.maxX() - 1, fa.minY(), fa.maxZ() - 1);
		BoundingBox hutBox = BlueprintOutline.bounds(new BlueprintData.Placement(Ids.of(level.dimension()), corner, Rotation.NONE, Mirror.NONE), crew.hut());
		helper.assertTrue(sharesColumn(hutBox, fa) && sharesColumn(hutBox, footprint(level, b)) && sharesColumn(hutBox, footprint(level, c)),
			"set-up: the hut " + hutBox + " doesn't cover all three Wonders");
		assertRefused(helper, crew, handHut(helper, crew, corner), "a hut over two finished Test Follies and the waiting one listed last");
		helper.succeed();
	}

	/**
	 * B750's "whenever any Wonder going up lies under the house": a hut hanging 20 blocks above the waiting Wonder's
	 * stage I (same columns, no shared block) is refused all the same, as the Wonder's later stages will rise there.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderOverlapHigh1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderOverlapHigh1101")
	public void qaAHouseHighAboveAWaitingWonderIsRefused(GameTestHelper helper) {
		Crew crew = crew(helper, new BlockPos(26, 2, 4), "qaWH");
		BoundingBox fp = footprint(crew.level(), wonder(helper, crew.hall(), helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, false));
		BlockPos above = new BlockPos(fp.minX() + 2, fp.maxY() + 4, fp.minZ() + 2);
		assertRefused(helper, crew, handHut(helper, crew, above), "a hut hanging above the waiting Test Folly (" + fp + ", hut at y " + above.getY() + ")");
		helper.succeed();
	}

	/**
	 * A Wonder laid out turned a quarter clockwise stands west of its origin: a hut on that turned ground is refused, and
	 * a hut east of the origin (where an unturned folly would stand, clear of the turned one) is taken.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderOverlapTurned1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderOverlapTurned1101")
	public void qaATurnedWaitingWonderHoldsTheGroundItTurnedOnto(GameTestHelper helper) {
		Crew crew = crew(helper, new BlockPos(26, 2, 20), "qaWT");
		BlockPos origin = helper.absolutePos(new BlockPos(13, 2, 2));
		BoundingBox fp = footprint(crew.level(), wonder(helper, crew.hall(), origin, Rotation.CLOCKWISE_90, false));
		helper.assertTrue(fp.maxX() == origin.getX() && fp.minX() < origin.getX() - 4, "set-up: the turned folly isn't west of its origin: " + fp);
		assertRefused(helper, crew, handHut(helper, crew, new BlockPos(fp.minX(), fp.minY(), fp.minZ() + 1)), "a hut on the turned Test Folly's ground (" + fp + ")");
		BuildSite east = handHut(helper, crew, new BlockPos(fp.maxX() + 1, fp.minY(), fp.minZ() + 1));
		helper.assertTrue(east != null, "a hut east of the turned folly (clear of " + fp + ") was refused; he said " + said(crew));
		helper.succeed();
	}

	// ---- the Wonder Plan's side (B748) ----

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static String refusal(ServerLevel level, ItemStack plan, BlockPos at) {
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, Rotation.NONE);
		return WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), placement, size)
			.map(c -> (c.getContents() instanceof TranslatableContents t ? t.getKey() : "") + " (" + c.getString() + ")").orElse("none");
	}

	private static Optional<BlueprintData.Placement> placed(ServerPlayer player) {
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	/** The player right-clicks the ground at {@code centre} with a fresh copy of {@code plan}; where it went, if anywhere. */
	private static Optional<BlueprintData.Placement> lay(ServerPlayer player, ItemStack plan, BlockPos centre) {
		player.setItemInHand(InteractionHand.MAIN_HAND, plan.copy());
		player.setYRot(0); // facing south: the plan's front faces north, unturned (rotationFacing), like refusal(...)
		player.setShiftKeyDown(false);
		BlockPos ground = centre.below();
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
		return placed(player);
	}

	/**
	 * B748's boundary from both sides, through the player's own right-click: a Test Hut being built whose west wall shares
	 * one column with the plan's east edge refuses the plan, saying en_us's sentence with the hut's name; the same hut one
	 * block further east (the first one canceled) leaves the plan free to go down where it was refused.
	 */
	//$ gametest_batch SMALL '"qaWonderOverlapPlanEdge1101"'
	@GameTest(template = SMALL, batch = "qaWonderOverlapPlanEdge1101")
	public void qaAPlanTouchingAHouseBeingBuiltIsRefusedAndOneBlockClearGoesDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		BlockPos hallAt = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(hallAt);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		BlockPos tableAt = new BlockPos(3, 2, 2);
		helper.setBlock(tableAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = villager(helper, new BlockPos(3, 2, 3), ModVillagers.BUILDER, 5);
		Builders.employ(level, builder, helper.absolutePos(tableAt));
		villager(helper, new BlockPos(13, 2, 3), VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(8, 2, 13), VillagerProfession.FARMER, 1);
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlockPos centre = hall.offset(-24, 0, -24).offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "set-up: the open ground isn't free: " + refusal(level, plan, centre));
		BoundingBox planBox = BlueprintOutline.bounds(BlueprintItem.placementAt(level.dimension().location(), size, centre, Rotation.NONE), size);

		BlueprintData.Placement touching = new BlueprintData.Placement(level.dimension().location(), new BlockPos(planBox.maxX(), planBox.minY(), planBox.minZ() + 1),
			Rotation.NONE, Mirror.NONE);
		BuildSite onEdge = Builders.start(level, builder, player.getUUID(), "Owner", HUT, touching);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(onEdge.id()));
		String over = refusal(level, plan, centre);
		helper.assertTrue(over.equals("message.aliveworkplace.wonder_plan.over_site (" + PLAN_OVER_HUT + ")"),
			"a plan sharing one column with the Test Hut being built: expected '" + PLAN_OVER_HUT + "', got " + over);
		Optional<BlueprintData.Placement> refused = lay(player, plan, centre);
		helper.assertTrue(refused.isEmpty(), "the player's right-click laid the plan over the edge of the Test Hut being built at " + refused);

		Builders.cancel(level, onEdge);
		helper.assertTrue(BuildSiteManager.get(level).get(onEdge.id()) == null, "set-up: the first hut wasn't canceled");
		BlueprintData.Placement clear = new BlueprintData.Placement(level.dimension().location(), touching.origin().east(), Rotation.NONE, Mirror.NONE);
		BuildSite beside = Builders.start(level, builder, player.getUUID(), "Owner", HUT, clear);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(beside.id()));
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "a plan one block clear of the Test Hut being built is refused: " + refusal(level, plan, centre));
		Optional<BlueprintData.Placement> down = lay(player, plan, centre);
		helper.assertTrue(down.isPresent(), "the player's right-click didn't lay the plan one block clear of the Test Hut being built");
		helper.assertTrue(down.get().rotation() == Rotation.NONE, "set-up: the plan went down turned " + down.get().rotation());
		BoundingBox downBox = BlueprintOutline.bounds(down.get(), size);
		helper.assertTrue(!sharesColumn(downBox, BlueprintOutline.bounds(clear, BlueprintLibrary.get(level, HUT).orElseThrow().size())),
			"the plan went down over the hut after all: " + downBox);
		helper.succeed();
	}

	// ---- the Steward's plots (B747) ----

	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	/** The Well's spot nearest the hall (as in QaWonderPlots1101BugTests). */
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);

	private static BlockPos plotGround(GameTestHelper helper) {
		Leftovers.clear(helper);
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
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 22; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		return hall;
	}

	private static CityPlan homes(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(HALL);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		return CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
	}

	private static Plots.Verdict atSpot(GameTestHelper helper, CityPlan plan) {
		return Plots.check(helper.getLevel(), helper.absolutePos(HALL), plan, "homes", WELL, helper.absolutePos(SPOT), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	/**
	 * B747's margin ("refused like any build in progress, CLEAR blocks round it") at its boundary: a waiting Wonder whose
	 * east edge leaves exactly {@link Plots#CLEAR} free blocks before the Well's plot lets it fit; one block nearer
	 * (CLEAR - 1 free) it is refused as BUILD; and with the Wonder forgotten it fits again.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWonderOverlapMargin1101"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWonderOverlapMargin1101")
	public void qaTheStewardsMarginRoundAWaitingWonderHoldsAtExactlyClear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = plotGround(helper);
		CityPlan plan = homes(helper);
		helper.runAfterDelay(2, () -> {
			Plots.Verdict open = atSpot(helper, plan);
			helper.assertTrue(open.plot().isPresent(), "set-up: the Well doesn't fit on the open ground: " + open);
			BoundingBox well = open.plot().get().box();
			WonderManager manager = WonderManager.get(level);
			WonderSite site = wonder(helper, hall, helper.absolutePos(new BlockPos(-20, 2, 9)), Rotation.NONE, false);
			BoundingBox fp = footprint(level, site);
			int width = fp.getXSpan();
			// east edge at well.minX - CLEAR - 1: exactly CLEAR free blocks between
			BlockPos far = new BlockPos(well.minX() - Plots.CLEAR - width, site.placement().get().origin().getY(), well.minZ() - 1);
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), far, Rotation.NONE, Mirror.NONE), far.offset(-1, 0, -1));
			BoundingBox atFar = footprint(level, manager.site(site.id()).orElseThrow());
			helper.assertTrue(well.minX() - atFar.maxX() - 1 == Plots.CLEAR, "set-up: the gap is " + (well.minX() - atFar.maxX() - 1));
			Plots.Verdict clear = atSpot(helper, plan);
			helper.assertTrue(clear.plot().isPresent(), "the Well with exactly CLEAR (" + Plots.CLEAR + ") free blocks to the waiting Test Folly is refused: " + clear);

			BlockPos near = far.east();
			manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), near, Rotation.NONE, Mirror.NONE), near.offset(-1, 0, -1));
			Plots.Verdict tooClose = atSpot(helper, plan);
			helper.assertTrue(tooClose.plot().isEmpty() && tooClose.reason().equals(Optional.of(Plots.Reason.BUILD)),
				"the Well with CLEAR - 1 free blocks to the waiting Test Folly: expected refused as BUILD, got " + tooClose);

			manager.remove(site.id());
			helper.assertTrue(atSpot(helper, plan).plot().isPresent(), "with the Wonder forgotten the Well's spot is still refused: " + atSpot(helper, plan));
			helper.succeed();
		});
	}

	/**
	 * The Steward's kept search (Plots.request, the one his planning asks each second): finished with a plot, then a
	 * Wonder is laid out on that plot (waiting for its goods). Expected: the next ask starts a new search, and what it
	 * finds keeps CLEAR blocks off the Wonder (or finds nothing).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWonderOverlapKept1101"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWonderOverlapKept1101")
	public void qaAKeptStewardSearchStartsOverWhenAWonderIsLaidOutOnItsPlot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = plotGround(helper);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setPlan(homes(helper));
		Plots.forget(level, hall);
		Plots.Request request = new Plots.Request(List.of(WELL), "homes");
		Plots.Search first = Plots.request(level, hall, request).orElseThrow();
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(first.done(), "set-up: the first search didn't finish in 120 ticks");
			Plots.Plot found = first.result().orElseThrow(() -> new AssertionError("set-up: the first search found no plot"));
			BoundingBox box = found.box();
			WonderSite site = wonder(helper, hall, new BlockPos(box.minX() - 1, box.minY(), box.minZ() - 1), Rotation.NONE, false);
			BoundingBox fp = footprint(level, site);
			helper.assertTrue(sharesColumn(fp, box), "set-up: the Wonder " + fp + " doesn't cover the plot " + box);
			Plots.Search again = Plots.request(level, hall, request).orElseThrow();
			helper.assertTrue(again != first, "a Wonder was laid out on the plot the Steward's kept search found, but the old search was kept");
			helper.runAfterDelay(120, () -> {
				helper.assertTrue(again.done(), "the new search didn't finish in 120 ticks");
				Optional<Plots.Plot> now = Plots.result(level, hall, request).orElseThrow();
				helper.assertTrue(now.isEmpty() || !sharesColumn(now.get().box(), fp.inflatedBy(Plots.CLEAR)),
					"the new search planned the Well at " + now.map(p -> p.box().toString()).orElse("") + ", within CLEAR of the waiting Test Folly " + fp);
				helper.succeed();
			});
		});
	}
}
