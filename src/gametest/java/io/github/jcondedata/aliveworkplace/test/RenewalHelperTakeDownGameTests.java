package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B789: a renewal's take-down the Steward gives to a builder who is lending a hand on another builder's build. The
 * city time-lapse ended with the old house's take-down at STARTING, nothing taken down, its builder standing about:
 * with two builds open and three builders, the third is nearly always a helper, and a helper's own build waited behind
 * the build he was helping for as long as that one stayed open. His own build comes first, as when a player hands him
 * a blueprint: he leaves the crew (what he carries goes back to the lead's chests) and takes the old house down.
 */
public class RenewalHelperTakeDownGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The other build (5x4x5), south of the Homes zone and clear of the old house. */
	private static final BlockPos HUT_AT = new BlockPos(22, 2, 24);
	/** The lead's chest, by the village's Blueprint Table (RenewalGameTests.village). */
	private static final BlockPos LEAD_CHEST = new BlockPos(3, 2, 25);
	/** The second builder's table: nearer the hall than the lead's, so the Steward picks him first among equals. */
	private static final BlockPos MATE_TABLE = new BlockPos(2, 2, 18);

	/** The village with the lead on the hut, the mate helping there, and the renewal's proposal on the desk. */
	private record Scene(RenewalGameTests.Village v, Villager mate, BuildSite hut, StewardDesk.Proposal proposal) {
	}

	private static Scene scene(GameTestHelper helper, boolean stocked) {
		RenewalGameTests.Village v = RenewalGameTests.village(helper, "", true);
		ServerLevel level = v.level();
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer()));
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		OldHouses.House house = RenewalGameTests.measure(helper, v, RenewalGameTests.beds(level, old).get(0));
		StewardDesk.Proposal proposal = RenewalGameTests.propose(helper, v, house);
		BuildSite hut = Builders.start(level, v.builder(), v.owner(), HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_AT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = hut.plan(level);
		helper.assertTrue(plan != null, "setup: no test hut blueprint");
		if (stocked) {
			Container chest = helper.getBlockEntity(LEAD_CHEST);
			int slot = 0;
			for (Map.Entry<Item, Integer> need : plan.materials().entrySet()) {
				chest.setItem(slot++, new ItemStack(need.getKey(), Math.min(64, need.getValue())));
			}
		}
		helper.setBlock(MATE_TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager mate = helper.spawn(EntityType.VILLAGER, MATE_TABLE.east());
		Builders.employ(level, mate, helper.absolutePos(MATE_TABLE));
		BuildSite joined = Builders.recruit(level, mate);
		helper.assertTrue(joined == hut, "setup: the second builder didn't join the hut as its helper: " + joined);
		return new Scene(v, mate, hut, proposal);
	}

	/** The old house is down and its plot handed to the rebuild; else what the take-down and the mate are at. */
	private static void assertTakenDown(GameTestHelper helper, Scene s, UUID takeDown) {
		ServerLevel level = s.v().level();
		List<Renewals.Renewal> active = Renewals.active(level, s.v().hall());
		BuildSite now = active.isEmpty() ? null : BuildSiteManager.get(level).get(active.get(0).site());
		BuildSite down = BuildSiteManager.get(level).get(takeDown);
		String state = down == null ? "gone" : down.status() + ", queued " + down.isQueued() + ", " + down.placed() + " placed, stage " + down.stage();
		helper.assertTrue(now != null && !now.isDeconstruction(),
			"the old house isn't taken down and handed to the rebuild yet: take-down " + state + "; its builder at "
				+ helper.relativePos(s.mate().blockPosition()) + (Builders.isHelping(s.mate()) ? ", still helping" : ", not helping")
				+ ", renewals under way " + active.size());
	}

	private static BuildSite approve(GameTestHelper helper, Scene s) {
		ServerLevel level = s.v().level();
		StewardDesk.Outcome outcome = StewardDesk.approve(level, s.v().hall(), null, s.proposal().id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED || outcome == StewardDesk.Outcome.QUEUED, "the renewal wasn't approved: " + outcome);
		BuildSite down = RenewalGameTests.site(helper, s.v());
		helper.assertTrue(down.isDeconstruction(), "setup: the renewal's first site isn't the take-down");
		helper.assertTrue(s.mate().getUUID().equals(down.builder()), "setup: the take-down isn't the helper's: " + down.builder());
		return down;
	}

	/**
	 * The build he was helping never ends (its lead waits for materials nobody brings): the helper's take-down is his own
	 * build and comes first. It starts at once, he is off the crew, and the hut is still its lead's.
	 */
	//$ gametest_ticks_batch AREA '6000' '"renewHelperTakeDownFirst"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "renewHelperTakeDownFirst")
	public void aHelperGivenATakeDownLeavesTheCrewAndTakesTheOldHouseDown(GameTestHelper helper) {
		Scene s = scene(helper, false);
		ServerLevel level = s.v().level();
		BuildSite down = approve(helper, s);
		UUID id = down.id();
		UUID hut = s.hut().id();
		helper.succeedWhen(() -> {
			assertTakenDown(helper, s, id);
			BuildSite other = BuildSiteManager.get(level).get(hut);
			helper.assertTrue(other != null && s.v().builder().getUUID().equals(other.builder()), "the hut he was helping with isn't its lead's any more: " + other);
			helper.assertTrue(!Builders.isHelping(s.mate()), "he is helping again with the rebuild his to do");
		});
	}

	/** The build he was helping is finished by its crew or without him: either way the old house comes down and the hut stands. */
	//$ gametest_ticks_batch AREA '8000' '"renewHelperTakeDownBothDone"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "renewHelperTakeDownBothDone")
	public void theHelpedBuildAndTheHelpersTakeDownBothGetDone(GameTestHelper helper) {
		Scene s = scene(helper, true);
		ServerLevel level = s.v().level();
		BuildSite down = approve(helper, s);
		UUID id = down.id();
		UUID hut = s.hut().id();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(hut) == null, "the hut isn't finished yet: " + s.hut().status() + ", " + s.hut().placed() + " placed");
			assertTakenDown(helper, s, id);
		});
	}

	/**
	 * A world saved with a helper's own build waiting behind the build he helps (as every version before this fix left
	 * it: the site queued, his job still the other builder's hut): he takes it up as soon as he is back at work.
	 */
	//$ gametest_ticks_batch AREA '6000' '"renewHelperTakeDownSaved"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "renewHelperTakeDownSaved")
	public void aSavedHelperWithAQueuedTakeDownTakesItUp(GameTestHelper helper) {
		Scene s = scene(helper, false);
		ServerLevel level = s.v().level();
		BuildSite down = approve(helper, s);
		UUID id = down.id();
		// as the old code left it, whatever approving does today
		if (!Builders.isHelping(s.mate())) {
			down.setQueued(true);
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.set(s.mate(),
				io.github.jcondedata.aliveworkplace.build.BuilderJob.helping(s.hut()));
			s.hut().seen(s.mate().getUUID(), level.getGameTime());
		}
		helper.assertTrue(down.isQueued() && Builders.isHelping(s.mate()), "setup: not the saved state (queued " + down.isQueued() + ")");
		helper.succeedWhen(() -> assertTakenDown(helper, s, id));
	}
}
