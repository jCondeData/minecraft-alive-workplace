package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA 2026-10-31 for B615 and B616 (36.20's one reach rule, 36.21's Repairs page and Refresh). What a builder was allowed
 * to build he keeps up: placing, upkeep repairs, refreshes and repair asks all measure the reach from the blueprint
 * outline's centre. And the hall's Repairs page (with its Refresh button) and the repair question's leader fallback cover
 * the village's whole claimed area, City Plan zones painted past the hall's radius included.
 *
 * <p>The edge tests are outside any village with the long reach set to 12: a 16-block wall whose centre is 12 off the
 * Table (in reach) and whose origin corner is 20 off; the controls are the wall one block further, centre 13 off (out of
 * reach), with its origin corner only 5 off (which the old corner rule took). Each side of the boundary, for the
 * Refresh (the button's own {@link Refreshes#refresh}, and a waiting one taken up by a free builder after a reload),
 * and for the repair question (asked, saved and reloaded, then agreed to). The zone tests use a hall of radius 12 with a
 * Homes zone painted over the plan's south-east corner, like {@link QaZoneRepairsPageBugTests}; their controls are the
 * same build with nothing painted there (outside every claimed area).
 */
public class QaReachRepairs1031GameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int REACH = 12;
	private static final int LENGTH = 16;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	// ---- the edge of the long reach ------------------------------------------------------------------------------------

	/** Bench x 28, wall x 8..23: centre x 16 (12 off, in reach), origin x 8 (20 off). */
	private static final BlockPos EDGE_BENCH = new BlockPos(28, 2, 15);
	private static final BlockPos EDGE_WALL = new BlockPos(8, 2, 15);
	/** Bench x 2, wall x 7..22: centre x 15 (13 off, out of reach), origin x 7 (5 off). */
	private static final BlockPos PAST_BENCH = new BlockPos(2, 2, 15);
	private static final BlockPos PAST_WALL = new BlockPos(7, 2, 15);

	private record Edge(ServerLevel level, QaStageFiveGameTests.Client owner, Villager[] builder, BlockPos benchRel, ResourceLocation id,
		BlueprintData.Placement placement, double centre, double origin) {
	}

	/**
	 * The wall (latest drawing: 16 planks) recorded finished for an online owner, at a Blueprint Table with planks in its
	 * chest. {@code older}: built from an older drawing with stone bricks at x 2 (so a Refresh has something to do);
	 * otherwise the latest drawing with the plank at x 3 knocked out. {@code withBuilder}: the owner's builder at the Table.
	 */
	private static Edge edge(GameTestHelper helper, BlockPos benchRel, BlockPos wallRel, boolean inReach, boolean older, boolean withBuilder,
		boolean ask, String who) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int reachWas = Builders.MAX_SITE_DISTANCE;
		boolean upkeepWas = Upkeep.ENABLED;
		boolean askWas = Upkeep.ASK;
		boolean protectionWas = VillageProtection.ENABLED;
		Builders.MAX_SITE_DISTANCE = REACH;
		Upkeep.ENABLED = true;
		Upkeep.ASK = ask;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			Builders.MAX_SITE_DISTANCE = reachWas;
			Upkeep.ENABLED = upkeepWas;
			Upkeep.ASK = askWas;
			VillageProtection.ENABLED = protectionWas;
		});
		helper.setDayTime(2000);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(who));

		for (int x = 0; x < LENGTH; x++) {
			helper.setBlock(wallRel.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_reach_repairs_" + who.toLowerCase() + "_" + Long.toHexString(level.getGameTime()));
		Vec3i size = new Vec3i(LENGTH, 1, 1);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(wallRel), size, false, Blocks.STRUCTURE_VOID);
		if (older) {
			List<Blueprint.Entry> blocks = new ArrayList<>();
			for (int x = 0; x < LENGTH; x++) {
				blocks.add(new Blueprint.Entry(new BlockPos(x, 0, 0), (x == 2 ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS).defaultBlockState(), null));
			}
			Refreshes.addHistory(id, "qa_older", new Blueprint(id, size, blocks));
			Leftovers.after(helper, () -> Refreshes.removeHistory(id));
			helper.setBlock(wallRel.offset(2, 0, 0), Blocks.STONE_BRICKS);
		} else {
			helper.setBlock(wallRel.offset(3, 0, 0), Blocks.AIR);
		}

		helper.setBlock(benchRel, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(benchRel.offset(0, 0, 2), Blocks.CHEST);
		Container chest = helper.getBlockEntity(benchRel.offset(0, 0, 2));
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		BlockPos bench = helper.absolutePos(benchRel);
		Villager[] builder = {withBuilder ? hire(helper, benchRel, c.player()) : null};
		helper.assertTrue(Reach.village(level, bench).isEmpty(), "the setup: a village claims the Table; only the long reach should count");

		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(wallRel), Rotation.NONE, Mirror.NONE);
		BoundingBox box = BlueprintOutline.bounds(placement, size);
		double centre = Math.sqrt(box.getCenter().distSqr(bench));
		double origin = Math.sqrt(placement.origin().distSqr(bench));
		if (inReach) {
			helper.assertTrue(centre <= REACH && origin > REACH, "the setup: centre " + centre + ", origin " + origin + " off (want centre within, origin past " + REACH + ")");
		} else {
			helper.assertTrue(centre > REACH && origin <= REACH, "the setup: centre " + centre + ", origin " + origin + " off (want centre past, origin within " + REACH + ")");
		}
		BuildSiteManager.get(level).recordFinished(id, placement, c.player().getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Edge(level, c, builder, benchRel, id, placement, centre, origin);
	}

	private static Villager hire(GameTestHelper helper, BlockPos benchRel, ServerPlayer owner) {
		Villager builder = helper.spawn(EntityType.VILLAGER, benchRel.offset(-1, 0, 1));
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(benchRel));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		return builder;
	}

	/** The build manager saved and loaded again, as on a restart. */
	private static void reload(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static String said(List<Component> seen, String key) {
		Component c = QaStageFourGameTests.find(seen, key);
		return c == null ? null : c.getString();
	}

	/**
	 * B615, the Refresh: the button's own call for a wall accepted at the reach's edge (centre 12 off, origin 20 off) is
	 * started by its builder at once, says so in the owner's words, and the wall is brought to its latest look.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaReachRepairs1031RefreshEdge"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaReachRepairs1031RefreshEdge")
	public void aRefreshOfABuildAtTheReachEdgeIsStartedByItsBuilder(GameTestHelper helper) {
		Edge e = edge(helper, EDGE_BENCH, EDGE_WALL, true, true, true, false, "qrrA");
		helper.runAfterDelay(2, () -> {
			String name = Blueprints.displayName(e.id()).getString();
			e.owner().seen().clear();
			Refreshes.Outcome outcome = Refreshes.refresh(e.owner().player(), e.placement().origin());
			BuildSite site = Refreshes.siteOf(e.level(), e.placement());
			helper.assertTrue(outcome == Refreshes.Outcome.STARTED && site != null && e.builder()[0].getUUID().equals(site.builder()),
				String.format("a Refresh of the wall its builder was allowed to build (centre %.1f blocks off, reach %d; corner %.1f off) didn't go to him: %s, site %s",
					e.centre(), REACH, e.origin(), outcome, site));
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.refresh_answer.started"),
				"A Builder is bringing the " + name + " up to its latest look", "the button's answer");
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.refreshing"),
				e.builder()[0].getDisplayName().getString() + " is bringing the " + name + " up to its latest look", "the builder's line");
			LOGGER.info("qa reach repairs 1031: edge refresh started by the builder, centre {} origin {}", e.centre(), e.origin());
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, EDGE_WALL.offset(2, 0, 0));
			helper.assertTrue(Refreshes.siteOf(e.level(), e.placement()) == null, "still refreshing");
		});
	}

	/**
	 * The control, one block past the edge (centre 13 off, origin only 5 off): placing it is refused with the reach's
	 * sentence, the Refresh button's call finds no builder (it waits) and the builder's round doesn't take it up.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaReachRepairs1031RefreshPast"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaReachRepairs1031RefreshPast")
	public void aBuildJustPastTheReachIsNeitherPlacedNorRefreshed(GameTestHelper helper) {
		Edge e = edge(helper, PAST_BENCH, PAST_WALL, false, true, true, false, "qrrB");
		helper.runAfterDelay(2, () -> {
			ServerPlayer owner = e.owner().player();
			// placing: the player's own click with the blueprint placed there
			ItemStack blueprint = BlueprintItem.create(e.id(), new Vec3i(LENGTH, 1, 1));
			blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(e.placement())));
			owner.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			e.owner().seen().clear();
			QaStageSixGameTests.clickVillager(owner, e.builder()[0], e.owner().seen());
			helper.assertTrue(Builders.activeSite(e.level(), e.builder()[0]) == null, "the wall past the reach was taken by the click");
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.assign.too_far"),
				"That spot is 13 blocks from the builder's Blueprint Table — keep it within " + REACH + ".", "the refusal");
			owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

			// refreshing it: no builder has it in reach
			e.owner().seen().clear();
			Refreshes.Outcome outcome = Refreshes.refresh(owner, e.placement().origin());
			helper.assertTrue(outcome == Refreshes.Outcome.WAITING && Refreshes.siteOf(e.level(), e.placement()) == null,
				String.format("a Refresh of a wall past the reach (centre %.1f off, corner %.1f off) went to the builder: %s", e.centre(), e.origin(), outcome));
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.refresh_answer.waiting"),
				"No Builder is free: the " + Blueprints.displayName(e.id()).getString() + " is refreshed as soon as one is", "the waiting answer");
			Upkeep.look(e.level(), e.builder()[0]);
			helper.assertTrue(Refreshes.siteOf(e.level(), e.placement()) == null && !ModAttachments.BUILDER_JOB.has(e.builder()[0]),
				"the builder's round took up a refresh of a wall past his reach");
			helper.assertBlockPresent(Blocks.STONE_BRICKS, PAST_WALL.offset(2, 0, 0));
			helper.succeed();
		});
	}

	/**
	 * B615 with a restart: a Refresh asked for the edge wall while no builder is hired waits; after a save and reload
	 * the builder hired then takes it up on his round (the reach rule of {@code takeUp}) and finishes it.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qaReachRepairs1031TakeUp"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaReachRepairs1031TakeUp")
	public void aWaitingRefreshAtTheReachEdgeIsTakenUpAfterAReload(GameTestHelper helper) {
		Edge e = edge(helper, EDGE_BENCH, EDGE_WALL, true, true, false, false, "qrrC");
		helper.runAfterDelay(2, () -> {
			Refreshes.Outcome outcome = Refreshes.refresh(e.owner().player(), e.placement().origin());
			helper.assertTrue(outcome == Refreshes.Outcome.WAITING, "with no builder the refresh didn't wait: " + outcome);
			reload(e.level());
			helper.assertTrue(BuildSiteManager.get(e.level()).refreshAsked(e.placement()), "the waiting refresh was lost on reload");
			e.builder()[0] = hire(helper, e.benchRel(), e.owner().player());
			Upkeep.look(e.level(), e.builder()[0]);
			BuildSite site = Refreshes.siteOf(e.level(), e.placement());
			helper.assertTrue(site != null && e.builder()[0].getUUID().equals(site.builder()),
				String.format("after a reload the free builder didn't take up the waiting refresh of the wall he was allowed to build (centre %.1f off, corner %.1f off)",
					e.centre(), e.origin()));
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, EDGE_WALL.offset(2, 0, 0));
			helper.assertTrue(Refreshes.siteOf(e.level(), e.placement()) == null, "still refreshing");
		});
	}

	/**
	 * B615, the repair question: the edge wall with a plank knocked out is asked about in the owner's chat (the sentence
	 * with its real numbers), the question survives a save and reload, and his yes sends his builder to it at once.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaReachRepairs1031AskEdge"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaReachRepairs1031AskEdge")
	public void aRepairAskedAtTheReachEdgeIsAgreedToAfterAReload(GameTestHelper helper) {
		Edge e = edge(helper, EDGE_BENCH, EDGE_WALL, true, false, true, true, "qrrD");
		helper.runAfterDelay(2, () -> {
			String name = Blueprints.displayName(e.id()).getString();
			BlockPos at = e.placement().origin();
			// (his own round may already have asked: the question is looked for in everything the owner was told)
			Upkeep.look(e.level(), e.builder()[0]);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(e.builder()[0]), "a repair started before anyone said yes");
			RepairAsks.Ask ask = BuildSiteManager.get(e.level()).repairAsk(e.placement());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED && ask.missing() == 1,
				String.format("the builder didn't ask about the wall he was allowed to build (centre %.1f off, corner %.1f off): %s", e.centre(), e.origin(), ask));
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.repair_ask.one"),
				e.builder()[0].getDisplayName().getString() + " found 1 block missing from the " + name + " at " + at.getX() + ", " + at.getY() + ", "
					+ at.getZ() + ". Repair it? [Yes] [Not now]", "the question");
			reload(e.level());
			RepairAsks.Ask back = BuildSiteManager.get(e.level()).repairAsk(e.placement());
			helper.assertTrue(back != null && back.state() == RepairAsks.State.ASKED && back.missing() == 1, "the question after a reload: " + back);
			e.owner().seen().clear();
			RepairAsks.Outcome yes = RepairAsks.yes(e.owner().player(), at);
			helper.assertTrue(yes == RepairAsks.Outcome.YES, "the owner's yes: " + yes);
			helper.assertValueEqual(said(e.owner().seen(), "message.aliveworkplace.repair_answer.agreed"),
				"Yes: a builder will repair the " + name + ".", "the yes");
			BuildSite site = RepairAsks.repairOf(e.level(), e.placement());
			helper.assertTrue(site != null && e.builder()[0].getUUID().equals(site.builder()),
				"after the yes no builder set about the wall at the reach's edge (startNow): " + site);
		});
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.OAK_PLANKS, EDGE_WALL.offset(3, 0, 0)));
	}

	/** The control: the wall one block past the edge (centre 13 off, corner 5 off) with a plank out is never asked about. */
	//$ gametest_ticks_batch HUGE '200' '"qaReachRepairs1031AskPast"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaReachRepairs1031AskPast")
	public void aBuildJustPastTheReachIsNotAskedAbout(GameTestHelper helper) {
		Edge e = edge(helper, PAST_BENCH, PAST_WALL, false, false, true, true, "qrrE");
		helper.runAfterDelay(2, () -> {
			Upkeep.look(e.level(), e.builder()[0]);
			helper.assertTrue(BuildSiteManager.get(e.level()).repairAsk(e.placement()) == null
					&& RepairAsks.repairOf(e.level(), e.placement()) == null
					&& !QaStageFourGameTests.saw(e.owner().seen(), "message.aliveworkplace.repair_ask.one"),
				String.format("the builder asked about (or repaired) a wall past his reach (centre %.1f off, corner %.1f off): %s",
					e.centre(), e.origin(), BuildSiteManager.get(e.level()).repairAsk(e.placement())));
			helper.succeed();
		});
	}

	// ---- the village's claimed area ------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos BENCH = HALL.offset(-4, 0, 4);
	private static final BlockPos CHEST = HALL.offset(-4, 0, 6);
	/** Origin 11 east and 13 south of the hall (17 off, radius 12): in the painted corner zone, or outside the village. */
	private static final BlockPos ZONE_WALL = HALL.offset(11, 0, 13);

	private record Zone(ServerLevel level, BlockPos hall, Villager builder, ResourceLocation id, BlueprintData.Placement placement,
		QaStageFiveGameTests.Client leader, @org.jetbrains.annotations.Nullable QaStageFiveGameTests.Client owner) {
	}

	/**
	 * A hall of radius 12 led by an online player, a Homes zone painted over the plan's south-east corner (unless
	 * {@code painted} is false), a builder at a bench inside the radius, and a four-plank wall in the corner recorded
	 * finished. {@code older}: built from an older drawing (stone bricks at x 1) for an online owner who isn't the leader;
	 * otherwise the latest drawing with the plank at x 1 out, for an owner who is offline.
	 */
	private static Zone zone(GameTestHelper helper, boolean painted, boolean older, String who) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean protection = VillageProtection.ENABLED;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = protection;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			VillageHalls.forgetNearest();
		});
		VillageHalls.RADIUS = 12;
		VillageProtection.ENABLED = false;
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		helper.setDayTime(2000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(CityPlan.half() == 16, "the plan's grid reaches " + CityPlan.half() + " blocks from the hall, not 16");
		QaStageFiveGameTests.Client leader = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(who + "L"));
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(leader.player().getUUID(), leader.player().getGameProfile().getName());
		if (painted) {
			BitSet cells = new BitSet();
			for (int dx = 10; dx <= 15; dx++) {
				for (int dz = 12; dz <= 15; dz++) {
					cells.set(CityPlan.cellAt(hall, hall.offset(dx, 0, dz)));
				}
			}
			entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		}

		for (int x = 0; x < 4; x++) {
			helper.setBlock(ZONE_WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_reach_repairs_" + who.toLowerCase() + "_"
			+ Long.toHexString(level.getGameTime()));
		Vec3i size = new Vec3i(4, 1, 1);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ZONE_WALL), size, false, Blocks.STRUCTURE_VOID);
		if (older) {
			List<Blueprint.Entry> blocks = new ArrayList<>();
			for (int x = 0; x < 4; x++) {
				blocks.add(new Blueprint.Entry(new BlockPos(x, 0, 0), (x == 1 ? Blocks.STONE_BRICKS : Blocks.OAK_PLANKS).defaultBlockState(), null));
			}
			Refreshes.addHistory(id, "qa_older", new Blueprint(id, size, blocks));
			Leftovers.after(helper, () -> Refreshes.removeHistory(id));
			level.setBlock(helper.absolutePos(ZONE_WALL.offset(1, 0, 0)), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		} else {
			helper.setBlock(ZONE_WALL.offset(1, 0, 0), Blocks.AIR);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ZONE_WALL), Rotation.NONE, Mirror.NONE);
		BlockPos centre = Builders.spot(level, id, placement);
		helper.assertTrue(Math.sqrt(centre.distSqr(hall)) > VillageHalls.RADIUS && Math.sqrt(placement.origin().distSqr(hall)) > VillageHalls.RADIUS,
			"the setup: the wall isn't past the hall's radius");
		helper.assertTrue(Reach.claims(level, hall, centre) == painted, "the setup: the wall's centre claimed=" + Reach.claims(level, hall, centre) + ", painted=" + painted);

		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.offset(1, 0, 1));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		QaStageFiveGameTests.Client owner = null;
		UUID ownerId;
		String ownerName;
		if (older) {
			owner = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(who + "O"));
			ownerId = owner.player().getUUID();
			ownerName = owner.player().getGameProfile().getName();
		} else {
			ownerId = UUID.nameUUIDFromBytes(("qa-reach-repairs-1031-offline-" + who).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			ownerName = "QaOffline";
		}
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(ownerId, ownerName));
		BuildSiteManager.get(level).recordFinished(id, placement, ownerId);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Zone(level, hall, builder, id, placement, leader, owner);
	}

	/**
	 * B616, the leader fallback: the wall in the painted corner zone, its owner offline, is asked about in the village
	 * leader's chat (the sentence with its real numbers); the leader's yes is taken and the builder repairs it.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaReachRepairs1031Leader"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaReachRepairs1031Leader")
	public void theLeaderIsAskedAboutABuildInAZonePastTheRadiusAndMayAgree(GameTestHelper helper) {
		Zone z = zone(helper, true, false, "qrrF");
		helper.runAfterDelay(2, () -> {
			String name = Blueprints.displayName(z.id()).getString();
			BlockPos at = z.placement().origin();
			Upkeep.look(z.level(), z.builder()); // (his own round may already have asked)
			RepairAsks.Ask ask = BuildSiteManager.get(z.level()).repairAsk(z.placement());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED && ask.missing() == 1, "no question about the zone's wall: " + ask);
			String asked = said(z.leader().seen(), "message.aliveworkplace.repair_ask.one");
			helper.assertTrue(asked != null, "the village's leader wasn't asked about a build in the village's painted zone past the hall's radius "
				+ "(its owner offline); the leader saw " + QaStageFourGameTests.keys(z.leader().seen()));
			helper.assertValueEqual(asked, z.builder().getDisplayName().getString() + " found 1 block missing from the " + name + " at " + at.getX() + ", "
				+ at.getY() + ", " + at.getZ() + ". Repair it? [Yes] [Not now]", "the leader's question");
			z.leader().seen().clear();
			RepairAsks.Outcome yes = RepairAsks.yes(z.leader().player(), at);
			helper.assertTrue(yes == RepairAsks.Outcome.YES, "the leader's yes for a build in the village's zone: " + yes + " "
				+ QaStageFourGameTests.keys(z.leader().seen()));
			helper.assertValueEqual(said(z.leader().seen(), "message.aliveworkplace.repair_answer.agreed"), "Yes: a builder will repair the " + name + ".", "the yes");
			helper.assertTrue(RepairAsks.repairOf(z.level(), z.placement()) != null, "after the leader's yes nobody set about it");
		});
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.OAK_PLANKS, ZONE_WALL.offset(1, 0, 0)));
	}

	/**
	 * The control: the same wall with nothing painted there, outside every claimed area (still in the builder's long
	 * reach, so he asks): not the leader's to be told or to answer, and not on the hall's Repairs page.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaReachRepairs1031LeaderOut"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaReachRepairs1031LeaderOut")
	public void aBuildOutsideEveryClaimedAreaIsNotTheLeadersNorOnThePage(GameTestHelper helper) {
		Zone z = zone(helper, false, false, "qrrG");
		helper.runAfterDelay(2, () -> {
			String name = Blueprints.displayName(z.id()).getString();
			Upkeep.look(z.level(), z.builder()); // (his own round may already have asked)
			RepairAsks.Ask ask = BuildSiteManager.get(z.level()).repairAsk(z.placement());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED, "the setup: the builder didn't ask about the wall in his long reach: " + ask);
			helper.assertFalse(QaStageFourGameTests.saw(z.leader().seen(), "message.aliveworkplace.repair_ask.one"),
				"the leader was asked about a build outside his village");
			helper.assertFalse(RepairAsks.entries(z.level(), z.hall()).stream().anyMatch(en -> en.building().placement().equals(z.placement())),
				"a build outside the village's claimed area is on its Repairs page");
			z.leader().seen().clear();
			RepairAsks.Outcome yes = RepairAsks.yes(z.leader().player(), z.placement().origin());
			helper.assertTrue(yes == RepairAsks.Outcome.NOT_YOURS, "the leader answered for a build outside his village: " + yes);
			helper.assertValueEqual(said(z.leader().seen(), "message.aliveworkplace.repair_answer.not_yours"),
				"The " + name + " isn't yours to answer for: its owner, their friends or the village's leader decide.", "the refusal");
			helper.succeed();
		});
	}

	private static int refreshSlot(ChoiceMenu menu) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(Items.PAINTING) || menu.icon(slot).is(Items.SCAFFOLDING)) {
				return slot;
			}
		}
		return -1;
	}

	private static ChoiceMenu repairsPage(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		int tab = HallPages.slot(RepairsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Repairs page has no tab: " + tab);
		menu.press(tab, player);
		return menu;
	}

	/**
	 * B616, the Refresh button: a wall from an older drawing in the painted corner zone past the hall's radius is on the
	 * Repairs page with its Refresh; the owner's two clicks hand it to the village's builder, who brings it up to date.
	 */
	//$ gametest_ticks_batch HUGE '800' '"qaReachRepairs1031ZoneRefresh"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "qaReachRepairs1031ZoneRefresh")
	public void aZoneBuildPastTheRadiusHasItsRefreshButton(GameTestHelper helper) {
		Zone z = zone(helper, true, true, "qrrH");
		helper.runAfterDelay(2, () -> {
			ServerPlayer owner = z.owner().player();
			helper.assertTrue(Refreshes.entries(z.level(), z.hall()).stream().anyMatch(en -> en.building().placement().equals(z.placement())),
				"Refreshes.entries leaves out a build from an older drawing in the village's painted zone past the hall's radius");
			ChoiceMenu menu = repairsPage(helper, owner, z.hall());
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "the Repairs page shows no Refresh for the build in the painted zone past the radius");
			menu.press(slot, owner);
			helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH)
				&& menu.icon(RepairsPage.REFRESH).getHoverName().getString().equals("Refresh to the latest look"), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
			z.owner().seen().clear();
			menu.press(RepairsPage.REFRESH, owner);
			BuildSite site = Refreshes.siteOf(z.level(), z.placement());
			helper.assertTrue(site != null && z.builder().getUUID().equals(site.builder()), "the button didn't hand it to the village's builder: " + site
				+ " " + QaStageFourGameTests.keys(z.owner().seen()));
			helper.assertValueEqual(said(z.owner().seen(), "message.aliveworkplace.refresh_answer.started"),
				"A Builder is bringing the " + Blueprints.displayName(z.id()).getString() + " up to its latest look", "the button's answer");
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, ZONE_WALL.offset(1, 0, 0));
			helper.assertTrue(Refreshes.siteOf(z.level(), z.placement()) == null, "still refreshing");
		});
	}

	/** The control: the same older wall with nothing painted there (outside every claimed area): no Refresh on the page. */
	//$ gametest_ticks_batch HUGE '200' '"qaReachRepairs1031ZoneRefreshOut"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaReachRepairs1031ZoneRefreshOut")
	public void aBuildOutsideEveryClaimedAreaHasNoRefreshOnThePage(GameTestHelper helper) {
		Zone z = zone(helper, false, true, "qrrI");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Refreshes.change(z.level(), BuildSiteManager.get(z.level()).finishedIn(z.level()).stream()
				.filter(f -> f.placement().equals(z.placement())).findFirst().orElseThrow()).isPresent(), "the setup: the wall has nothing to refresh");
			helper.assertFalse(Refreshes.entries(z.level(), z.hall()).stream().anyMatch(en -> en.building().placement().equals(z.placement())),
				"Refreshes.entries lists a build outside the village's claimed area");
			ChoiceMenu menu = repairsPage(helper, z.owner().player(), z.hall());
			helper.assertTrue(refreshSlot(menu) < 0, "the Repairs page shows a Refresh for a build outside the village: " + menu.icon(Math.max(0, refreshSlot(menu))));
			helper.succeed();
		});
	}
}
