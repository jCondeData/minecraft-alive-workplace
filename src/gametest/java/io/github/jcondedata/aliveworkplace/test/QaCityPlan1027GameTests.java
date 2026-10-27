package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.cells;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.otherHalls;
import static io.github.jcondedata.aliveworkplace.test.QaCityPlanGameTests.sent;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1027-0038) for ROADMAP 27.2, 27.3 and 27.18, from the spec, beyond the existing CityPlan and Wall tests:
 * the raid window of 7 days from both sides; a Town replacing its approved palisade with stone, approved only by someone
 * who may change the plan; zone names and styles at their limits sent as real packets, with the sentence the player
 * reads; and a stranger's line, mode and undo packets in someone else's village refused through the network handler.
 */
public class QaCityPlan1027GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	/** Raided today wants a wall; raided 7 or 8 days ago (outside "the last 7 days") or never doesn't, and gets no proposal. */
	//$ gametest_ticks_batch HUGE '40' '"qa1027RaidWindow"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1027RaidWindow")
	public void theRaidWindowIsSevenDaysFromBothSides(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12)));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			long today = Chronicle.day(level);
			helper.assertTrue(!Walls.wanted(level, v.hall()), "setup: a village never raided wants a wall");
			for (long ago : new long[] {8, 7}) {
				v.entity().setLastRaidDay(today - ago);
				helper.assertTrue(!Walls.wanted(level, v.hall()), "raided " + ago + " days ago (not in the last 7) and it still wants a wall");
				Walls.propose(level, v.hall(), v.steward());
				helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 0, "wall proposals after a raid " + ago + " days ago");
			}
			v.entity().setLastRaidDay(today);
			helper.assertTrue(Walls.wanted(level, v.hall()), "raided today and it doesn't want a wall");
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 1, "wall proposals after a raid today");
			StewardDesk.Proposal proposal = StewardDesk.of(level, v.hall()).proposals().get(0);
			helper.assertTrue(proposal.wall().map(k -> k.name().equals("palisade")).orElse(false), "a Hamlet's wall proposal is " + proposal.wallKit());
			Component why = Component.translatable(proposal.why(), proposal.numbers().toArray());
			helper.assertValueEqual(why.getString(), "The village was raided in the last 7 days, or bandits camp nearby", "the proposal's reason");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * "A Town replaces its palisade with stone": with the palisade approved, a Village gets no new proposal, a Town gets a
	 * Stone one. A stranger's approval is refused and changes nothing; the owner's sets the stone wall going.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1027TownStone"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1027TownStone")
	public void aTownReplacesItsApprovedPalisadeWithStone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper, new BlockPos(14, 2, 14));
		v.entity().setPlan(CityPlan.EMPTY.withWall(square(12).approvedWith("palisade")));
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			v.entity().setLastRaidDay(Chronicle.day(level));
			v.entity().setRank(VillageRanks.Rank.VILLAGE);
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 0, "proposals for a Village whose palisade is approved");
			helper.assertTrue(v.entity().plan().wall().map(w -> w.approved() && w.kit().equals("palisade")).orElse(false),
				"a Village's approved palisade changed: " + v.entity().plan().wall());

			v.entity().setRank(VillageRanks.Rank.TOWN);
			Walls.propose(level, v.hall(), v.steward());
			List<StewardDesk.Proposal> proposals = StewardDesk.of(level, v.hall()).proposals();
			helper.assertValueEqual(proposals.size(), 1, "proposals for a Town with only a palisade");
			StewardDesk.Proposal stone = proposals.get(0);
			helper.assertTrue(stone.wall().map(k -> k.name().equals("stone")).orElse(false), "a Town's wall proposal is " + stone.wallKit());
			helper.assertTrue(v.entity().plan().wall().map(w -> w.points().equals(square(12).points())).orElse(false),
				"the drawn wall line moved: " + v.entity().plan().wall());

			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			CityPlan before = v.entity().plan();
			helper.assertTrue(StewardDesk.approve(level, v.hall(), stranger, stone.id()) == StewardDesk.Outcome.NOT_ALLOWED,
				"a stranger approved the stone wall");
			helper.assertTrue(v.entity().plan().equals(before) && StewardDesk.of(level, v.hall()).proposals().size() == 1,
				"a stranger's approval changed the plan or took the proposal: " + v.entity().plan().wall());

			StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), v.owner(), stone.id());
			helper.assertTrue(outcome.ok(), "the owner's approval of the stone wall: " + outcome);
			helper.assertTrue(v.entity().plan().wall().map(w -> w.approved() && w.kit().equals("stone")).orElse(false),
				"after approval the wall is " + v.entity().plan().wall());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 0, "proposals left after approving the wall");
			// the next morning: stone approved, nothing new
			Walls.propose(level, v.hall(), v.steward());
			helper.assertValueEqual(StewardDesk.of(level, v.hall()).proposals().size(), 0, "proposals the morning after the stone wall was approved");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * Zone names and styles at the limits, sent through the network handler: a 1- and a 32-letter name go, 33 letters
	 * don't; every listed style goes, on a new zone and on an edit; an unlisted one (or a listed one in other case) is
	 * refused with the bad_zone sentence, and the plan is unchanged.
	 */
	//$ gametest_ticks_batch BIG '20' '"qa1027ZoneLimits"'
	@GameTest(template = BIG, timeoutTicks = 20, batch = "qa1027ZoneLimits")
	public void zoneNamesAndStylesAtTheirLimitsThroughThePacket(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());

		send(owner, CityPlans.Edit.addZone(hall, "homes", "A", ""));
		helper.assertValueEqual(entity.plan().zones().size(), 1, "zones after a one-letter name");
		String max = "N".repeat(CityPlans.MAX_NAME);
		send(owner, CityPlans.Edit.addZone(hall, "workshops", max, ""));
		helper.assertTrue(entity.plan().zones().size() == 2 && entity.plan().zones().get(1).name().equals(max), "a 32-letter name: " + entity.plan().zones());
		helper.assertTrue(seen.stream().noneMatch(c -> "message.aliveworkplace.city_plan.bad_zone".equals(QaStageFourGameTests.key(c))),
			"good names were told off: " + QaStageFourGameTests.keys(seen));

		CityPlan before = entity.plan();
		send(owner, CityPlans.Edit.addZone(hall, "homes", max + "N", ""));
		send(owner, CityPlans.Edit.editZone(hall, 0, "homes", max + "N", "", false));
		helper.assertTrue(entity.plan().equals(before), "a 33-letter name went on: " + entity.plan().zones());
		Component bad = QaStageFourGameTests.find(seen, "message.aliveworkplace.city_plan.bad_zone");
		helper.assertTrue(bad != null, "the player wasn't told why: " + QaStageFourGameTests.keys(seen));
		helper.assertValueEqual(bad.getString(), "That zone can't go on the plan: its name must be 1 to 32 letters and its style one the plan lists",
			"the bad zone sentence");

		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		helper.assertTrue(!styles.isEmpty(), "setup: no styles");
		for (BlueprintStyles.Style style : styles) {
			send(owner, CityPlans.Edit.editZone(hall, 0, "homes", "A", style.name(), true));
			CityPlan.Zone z = entity.plan().zones().get(0);
			helper.assertTrue(z.style().equals(style.name()) && z.renew(), "editing to the listed style " + style.name() + " gave " + z);
		}
		String last = styles.get(styles.size() - 1).name();
		send(owner, CityPlans.Edit.addZone(hall, "gardens", "G", last));
		helper.assertTrue(entity.plan().zones().size() == 3 && entity.plan().zones().get(2).style().equals(last), "a new zone in " + last + ": " + entity.plan().zones());

		before = entity.plan();
		String upper = last.toUpperCase(java.util.Locale.ROOT);
		List<String> unlisted = new ArrayList<>(List.of("no_such_style_1027", " " + last, last + " "));
		if (!upper.equals(last) && BlueprintStyles.get(upper).isEmpty()) {
			unlisted.add(upper);
		}
		for (String style : unlisted) {
			send(owner, CityPlans.Edit.editZone(hall, 0, "homes", "A", style, true));
			send(owner, CityPlans.Edit.addZone(hall, "homes", "B", style));
		}
		helper.assertTrue(entity.plan().equals(before), "an unlisted style went on: " + entity.plan().zones());
		helper.succeed();
	}

	/**
	 * In someone else's village, a stranger's wall line, taking the wall off, removing a road, the Steward's mode and
	 * undo, all through the network handler, change nothing and leave the undo steps alone; each tells him who may.
	 */
	//$ gametest_ticks_batch BIG '20' '"qa1027StrangerLines"'
	@GameTest(template = BIG, timeoutTicks = 20, batch = "qa1027StrangerLines")
	public void aStrangersLineModeAndUndoPacketsAreRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper, HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = entity.getBlockPos();
		List<Component> ownerSeen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, ownerSeen, GameType.SURVIVAL);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		send(owner, CityPlans.Edit.addZone(hall, "homes", "Homes", ""));
		send(owner, CityPlans.Edit.paint(hall, 0, cells(CityPlan.cellAt(hall, hall.east(6)))));
		send(owner, CityPlans.Edit.addRoad(hall, List.of(p(-10, 3), p(10, 3)), 3, ""));
		send(owner, CityPlans.Edit.wall(hall, List.of(p(-20, -20), p(20, -20), p(20, 20), p(-20, 20)), true));
		helper.assertTrue(entity.plan().roads().size() == 1 && entity.plan().wall().isPresent() && entity.plan().zones().get(0).cells().cardinality() == 1,
			"setup: the owner's plan " + entity.plan());
		CityPlan before = entity.plan();
		int steps = entity.planUndoSteps();
		CityPlan.Mode other = before.mode() == CityPlan.Mode.RUN ? CityPlan.Mode.REST : CityPlan.Mode.RUN;

		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		List<CityPlans.Edit> edits = List.of(
			CityPlans.Edit.wall(hall, List.of(p(-5, -5), p(5, -5)), false),
			CityPlans.Edit.wall(hall, List.of(), true),
			CityPlans.Edit.removeRoad(hall, 0),
			CityPlans.Edit.mode(hall, other),
			CityPlans.Edit.undo(hall),
			CityPlans.Edit.erase(hall, cells(CityPlan.cellAt(hall, hall.east(6)))),
			CityPlans.Edit.removeZone(hall, 0));
		for (CityPlans.Edit edit : edits) {
			int told = seen.size();
			send(stranger, edit);
			helper.assertTrue(entity.plan().equals(before), "a stranger's " + edit.op() + " changed the plan: " + entity.plan());
			helper.assertTrue(entity.planUndoSteps() == steps, "a stranger's " + edit.op() + " changed the undo steps to " + entity.planUndoSteps());
			helper.assertTrue(seen.subList(told, seen.size()).stream().anyMatch(c -> "message.aliveworkplace.city_plan.denied".equals(QaStageFourGameTests.key(c))),
				"the stranger wasn't told after " + edit.op() + ": " + QaStageFourGameTests.keys(seen));
		}
		helper.assertTrue(owner.getUUID().equals(entity.owner()), "the hall's owner changed to " + entity.ownerName());
		// the owner's undo still takes back his own last stroke (the wall line)
		send(owner, CityPlans.Edit.undo(hall));
		helper.assertTrue(entity.plan().wall().isEmpty() && entity.plan().roads().size() == 1, "the owner's undo after the stranger's tries: " + entity.plan());
		helper.succeed();
	}

	/** The edit packet as the server receives it: every field, at negative coordinates, the grid's corner cells and a non-ASCII name, comes off the wire as sent. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qa1027Wire"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qa1027Wire")
	public void theEditPacketSurvivesTheWireWithEveryField(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = new BlockPos(-30001, -60, -12345);
		java.util.BitSet bits = cells(0, 31, 32 * 31, 1023);
		List<BlockPos> points = List.of(p(-64, -64), p(63, 63), p(0, -7));
		CityPlans.Edit edit = new CityPlans.Edit(hall, CityPlans.Op.PAINT, 3, "gardens", "Ü-park 'east'", "x", true, bits, CityPlan.Mode.RUN,
			points, 5, true);
		CityPlans.Edit back = sent(level, edit);
		helper.assertTrue(back.hall().equals(hall) && back.op() == CityPlans.Op.PAINT && back.zone() == 3 && back.kind().equals("gardens")
			&& back.name().equals("Ü-park 'east'") && back.style().equals("x") && back.renew() && back.cells().equals(bits)
			&& back.mode() == CityPlan.Mode.RUN && back.points().equals(points) && back.width() == 5 && back.closed(),
			"the packet came back as " + back);
		helper.succeed();
	}

	private static void send(ServerPlayer player, CityPlans.Edit edit) {
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(edit));
	}

	private static CityPlan.Wall square(int half) {
		return new CityPlan.Wall(List.of(p(-half, -half), p(half, -half), p(half, half), p(-half, half)), true);
	}

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, Villager steward) {
	}

	/** A hall on flat grass with its owner, a builder at his bench, a seasoned Steward appointed (as QaWallGameTests). */
	private static Village village(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 80, level.getMinBuildHeight(), a.getZ() - 80, a.getX() + 110, level.getMaxBuildHeight(), a.getZ() + 110);
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
				for (int y = 2; y < 16; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		otherHalls(helper, hallAt);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = hallAt.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.south(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.south());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.SPRUCE_FENCE, 64));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(hall, entity, owner, steward);
	}
}
