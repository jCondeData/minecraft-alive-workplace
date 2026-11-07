package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-02, lane-b-1102-2137's tester) of a Builder whose PLAYER changes their mind mid-job, every step through the
 * player's own entry points (a placed blueprint handed over by a click, /workplace cancel, a click on the ground to move
 * the blueprint, the Scan Tool's clicks, a survival player mining blocks, a player emptying and refilling the chests).
 * Expectations come from the spec, not the code:
 * <ul>
 * <li>docs/guide/command-cancel.md: "Canceled the build of Fountain." What's built stays, and the blueprint is still
 * placed there: hand it back to the builder to carry on, or click the ground to move it; docs/guide/stewards-desk.md:
 * cancel, "placed blocks stay, the builder's materials go back to the store"; builder.md decisions: "cancel with
 * materials returned" (23.8);</li>
 * <li>docs/guide/builder.md: "Busy builders queue up to five more blueprints and build them in order";</li>
 * <li>docs/wiki/builder.md: "If the chests run short, the Builder waits and tells you what is missing" (en_us
 * message.aliveworkplace.waiting); "If one laid earlier is gone and its spot is open (a player ... broke it mid-build)
 * the Builder goes back ... and lays what's missing" (B508);</li>
 * <li>docs/guide/scan-tool.md: "The Scan Tool turns it into a blueprint" (what stands in the box, as it stands);</li>
 * <li>CLAUDE.md: all progress lives in the saved site; ROADMAP 23.1 "no item is duplicated or lost".</li>
 * </ul>
 * Layout: QaBuilderTerrain1102GameTests.start's (a Builder's Bench, a chest and four barrels, the hut's list plus EXTRA of
 * each, free materials off), but the build is started by a player handing the builder a placed blueprint, so the player
 * owns it and can cancel it.
 */
public class QaBuilderMindChange1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = QaBuilderTerrain1102GameTests.LOGGER;
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int EXTRA = QaBuilderTerrain1102GameTests.EXTRA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos HUT_B = new BlockPos(16, 2, 16);

	/** A player-started run: the run, the player and the player's chat. */
	record Hand(QaBuilderTerrain1102GameTests.Run run, QaStageFiveGameTests.Client client) {
		ServerPlayer player() {
			return client.player();
		}
	}

	// ------------------------------------------------------------------------------------- canceled, then moved

	/**
	 * Mid-STRUCTURE (blocks up, materials in the builder's bag) the player cancels the build with /workplace cancel: the
	 * line says the blueprint is still placed there, and the blueprint comes back to the player. The player clicks the
	 * ground 10 blocks further on to move it, tops the chests up with what the first half used, and hands it to the same
	 * builder. He builds the hut at the new spot to the drawing; the half-built hut is left exactly as it stood at the
	 * cancel (nothing torn down, nothing added); and every material adds up (the bag he carried at the cancel included).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaMindCancelMove"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaMindCancelMove")
	public void aBuildCanceledMidStructureAndMovedIsBuiltAtTheNewSpotLeavingTheHalfAsItWas(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcM");
		QaBuilderTerrain1102GameTests.Run first = h.run();
		ServerPlayer p = h.player();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		Map<BlockPos, BlockState> half = new HashMap<>();
		QaBuilderTerrain1102GameTests.Run[] next = new QaBuilderTerrain1102GameTests.Run[1];
		midStructureWithABag(helper, first, () -> {
			String at = QaBuilderTerrain1102GameTests.state(helper, first) + ", bag " + bag(first.builder()[0], first.plan());
			Map<Item, Integer> before = totals(helper, first, p);
			cancel(helper, h, first.id(), hut);
			snapshot(first, half);
			ItemStack blueprint = returned(helper, p, first);
			// the player moves it: a right-click on the ground, looking south (front to the north, no turn)
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			p.setYRot(0);
			p.setYHeadRot(0);
			h.client().seen().clear();
			QaStageFourGameTests.press(helper, p, HUT_B.below(), Direction.UP);
			BlueprintData.Placement moved = BlueprintItem.data(p.getMainHandItem()).orElseThrow().placement()
				.orElseThrow(() -> new GameTestAssertException("a click on the ground didn't place the canceled blueprint: " + texts(h.client().seen())));
			BoundingBox newBox = BlueprintOutline.bounds(moved, BlueprintLibrary.get(first.level(), TEST_HUT).orElseThrow().size());
			helper.assertFalse(newBox.intersects(first.plan().bounds()), "setup: the moved hut " + newBox + " overlaps the first " + first.plan().bounds());
			// the player tops the chests up with what the first half used (built or in the bag), so the second hut can be whole
			Map<Item, Integer> after = totals(helper, first, p);
			helper.assertTrue(after.equals(before), "the cancel itself made or lost materials: before " + before + ", after " + after);
			for (Map.Entry<Item, Integer> e : first.plan().materials().entrySet()) {
				int used = e.getValue() + EXTRA - QaBuilderTerrain1102GameTests.loose(helper, first, e.getKey());
				topUp(helper, first, e.getKey(), used);
			}
			h.client().seen().clear();
			QaStageSixGameTests.clickVillager(p, first.builder()[0], h.client().seen());
			BuildSite site = Builders.activeSite(first.level(), first.builder()[0]);
			helper.assertTrue(site != null, "the moved blueprint handed back started nothing: " + texts(h.client().seen()));
			helper.assertTrue(site.placement().equals(moved), "the builder builds at " + site.placement() + ", the blueprint was moved to " + moved);
			next[0] = new QaBuilderTerrain1102GameTests.Run(first.level(), first.builder(), site.id(), site.plan(first.level()), 2, first.supplies(), first.died());
			LOGGER.info("qa mind cancel+move: canceled at {}; {} blocks of the half standing; moved to {} and handed back", at, half.size(), moved);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(next[0] != null, "not canceled and moved yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, next[0]);
			assertUnchanged(helper, first, half, "the half-built hut");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, next[0]);
			LOGGER.info("qa mind cancel+move: the moved hut stands as drawn, the half untouched ({} blocks), nothing made or lost", half.size());
		});
	}

	// --------------------------------------------------------------------------------- a second blueprint mid-build

	/**
	 * Mid-STRUCTURE the player hands the busy builder a second placed blueprint (another spot). Guide: busy builders queue
	 * it and build in order. The hand-over says "(number 1 in line)" and takes the blueprint from the hand; the hut under
	 * way keeps its spot and finishes as drawn first (the second site isn't touched while the first is open), then the
	 * second hut is built as drawn; with a second list of materials put in by the player, every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '14000' '"qaMindSecond"'
	@GameTest(template = HUGE, timeoutTicks = 14000, batch = "qaMindSecond")
	public void aSecondBlueprintMidBuildIsLinedUpAndBuiltAfterTheFirst(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcS");
		QaBuilderTerrain1102GameTests.Run first = h.run();
		ServerPlayer p = h.player();
		Map<BlockPos, BlockState> emptyB = new HashMap<>();
		QaBuilderTerrain1102GameTests.Run[] second = new QaBuilderTerrain1102GameTests.Run[1];
		BlueprintData.Placement[] firstAt = {null};
		midStructureWithABag(helper, first, () -> {
			firstAt[0] = first.site().placement();
			ItemStack blueprint = placed(first.level(), helper.absolutePos(HUT_B));
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			h.client().seen().clear();
			QaStageSixGameTests.clickVillager(p, first.builder()[0], h.client().seen());
			List<String> said = texts(h.client().seen());
			List<BuildSite> line = Builders.queue(first.level(), first.builder()[0]);
			helper.assertTrue(line.size() == 1, "the second blueprint wasn't lined up: " + line.size() + " in line; saw " + said);
			helper.assertTrue(said.stream().anyMatch(t -> t.contains("(number 1 in line)")), "lining up the second said " + said);
			helper.assertTrue(p.getMainHandItem().isEmpty(), "the lined-up blueprint stayed in hand: " + p.getMainHandItem());
			BuildSite active = Builders.activeSite(first.level(), first.builder()[0]);
			helper.assertTrue(active != null && active.id().equals(first.id()), "the second blueprint replaced the build under way: active " + (active == null ? "none" : active.placement()));
			BuildSite queued = line.get(0);
			second[0] = new QaBuilderTerrain1102GameTests.Run(first.level(), first.builder(), queued.id(), queued.plan(first.level()), 2, first.supplies(), first.died());
			snapshot(second[0], emptyB);
			for (Map.Entry<Item, Integer> e : first.plan().materials().entrySet()) {
				topUp(helper, first, e.getKey(), e.getValue());
			}
			Leftovers.after(helper, () -> {
				if (BuildSiteManager.get(first.level()).get(queued.id()) != null) {
					BuildSiteManager.get(first.level()).remove(queued.id());
				}
			});
			LOGGER.info("qa mind second: lined up at {}, saw {}", QaBuilderTerrain1102GameTests.state(helper, first), said);
		});
		boolean[] firstDone = {false};
		helper.onEachTick(() -> {
			if (second[0] == null || firstDone[0]) {
				return;
			}
			if (first.site() == null) {
				firstDone[0] = true;
				QaBuilderTerrain1102GameTests.assertFinished(helper, first);
				LOGGER.info("qa mind second: the first hut done at tick {}, the second {}", helper.getTick(), second[0].site() == null ? "gone" : second[0].site().stage());
			} else {
				// while the first is open the second spot stays as it was
				assertUnchanged(helper, second[0], emptyB, "the lined-up hut's spot while the first was under way");
				BuildSite a = Builders.activeSite(first.level(), first.builder()[0]);
				helper.assertTrue(a != null && a.placement().equals(firstAt[0]), "the build under way moved to " + (a == null ? "none" : a.placement()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(firstDone[0], "the first hut isn't done yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, second[0]);
			helper.assertTrue(first.plan().unfinished(first.level()).isEmpty(), "the first hut was undone by the second");
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, first);
			LOGGER.info("qa mind second: both huts stand as drawn, in order, nothing made or lost");
		});
	}

	// ------------------------------------------------------------------------------------- the chests emptied

	/**
	 * The player keeps 30 planks back at the start, and mid-STRUCTURE takes every one of the hut's materials out of the
	 * builder's chests too. Once his bag runs dry
	 * the builder waits and tells the player what is missing ("&lt;builder&gt; is waiting for materials to build Test Hut.
	 * Still needed: ..."), and builds nothing for the next 200 ticks. The player puts everything back: he carries on, the
	 * hut ends as drawn, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaMindEmptied"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaMindEmptied")
	public void chestsEmptiedMidStructureMakeHimWaitAndSaySoThenHeFinishesWhenRefilled(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcE");
		QaBuilderTerrain1102GameTests.Run r = h.run();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		String name = r.builder()[0].getDisplayName().getString();
		Map<Item, Integer> taken = new HashMap<>();
		// 0 building, 1 emptied (waiting for the line), 2 told (watching 200 ticks), 3 refilled
		int[] phase = {0};
		long[] toldAt = {-1};
		int[] unbuilt = {-1};
		String[] line = {null};
		// the player held 30 planks back at the start (so the builder's first trip can't carry the whole list)
		int held = 30;
		for (BlockPos pos : r.supplies()) {
			Container c = helper.getBlockEntity(pos);
			for (int i = 0; i < c.getContainerSize() && held > 0; i++) {
				ItemStack s = c.getItem(i);
				if (s.is(Blocks.OAK_PLANKS.asItem())) {
					int k = Math.min(held, s.getCount());
					s.shrink(k);
					held -= k;
					taken.merge(Blocks.OAK_PLANKS.asItem(), k, Integer::sum);
				}
			}
			c.setChanged();
		}
		helper.assertTrue(held == 0, "setup: fewer than 30 planks stocked");
		midStructureWithABag(helper, r, () -> {
			for (BlockPos pos : r.supplies()) {
				Container c = helper.getBlockEntity(pos);
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (r.plan().materials().containsKey(s.getItem())) {
						taken.merge(s.getItem(), s.getCount(), Integer::sum);
						c.setItem(i, ItemStack.EMPTY);
					}
				}
				c.setChanged();
			}
			h.client().seen().clear();
			phase[0] = 1;
			LOGGER.info("qa mind emptied: took {} at {}, bag {}", taken, QaBuilderTerrain1102GameTests.state(helper, r), bag(r.builder()[0], r.plan()));
		});
		helper.onEachTick(() -> {
			if (phase[0] == 1) {
				for (Component c : h.client().seen()) {
					if ("message.aliveworkplace.waiting".equals(QaStageFourGameTests.key(c))) {
						line[0] = c.getString();
						phase[0] = 2;
						toldAt[0] = helper.getTick();
						unbuilt[0] = r.plan().unfinished(r.level()).size();
						helper.assertTrue(line[0].startsWith(name + " is waiting for materials to build " + hut + ". Still needed: "),
							"the waiting line reads \"" + line[0] + "\"");
						helper.assertTrue(bag(r.builder()[0], r.plan()).isEmpty() || r.site() != null, "told while he still had materials");
						LOGGER.info("qa mind emptied: told \"{}\" with {} unbuilt", line[0], unbuilt[0]);
						break;
					}
				}
			} else if (phase[0] == 2) {
				int now = r.plan().unfinished(r.level()).size();
				helper.assertTrue(now >= unbuilt[0], "he built with empty chests: " + unbuilt[0] + " unbuilt when he said so, then " + now);
				helper.assertTrue(r.site() != null, "the build ended with empty chests: " + QaBuilderTerrain1102GameTests.state(helper, r));
				if (helper.getTick() - toldAt[0] >= 200) {
					helper.assertTrue(r.site().stage() == BuildPlan.Stage.STRUCTURE, "waiting, the build left STRUCTURE: " + r.site().stage());
					taken.forEach((item, n) -> topUp(helper, r, item, n));
					phase[0] = 3;
					LOGGER.info("qa mind emptied: refilled after 200 ticks waiting, {}", QaBuilderTerrain1102GameTests.state(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 3, "not refilled yet (phase " + phase[0] + ", " + QaBuilderTerrain1102GameTests.state(helper, r) + ", saw "
				+ texts(h.client().seen()) + ")");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------ blocks mined in DECORATION

	/**
	 * As DECORATION begins (the walls done) a survival player mines two wall planks the builder laid. builder.md (B508):
	 * a laid block gone with its spot open is gone back for before the build is called done. The hut ends as drawn, the
	 * planks the player mined are the player's (in the inventory or on the ground) and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMindMinedDeco"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMindMinedDeco")
	public void wallPlanksMinedDuringDecorationAreLaidAgainBeforeTheEnd(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcD");
		QaBuilderTerrain1102GameTests.Run r = h.run();
		ServerPlayer p = h.player();
		List<BlockPos> mined = new ArrayList<>();
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (!mined.isEmpty() || s == null || s.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			BoundingBox box = r.plan().bounds();
			for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (mined.size() < 2 && pos.getY() == helper.absolutePos(HUT).getY() + 1 && r.level().getBlockState(pos).is(Blocks.OAK_PLANKS)
					&& (pos.getX() == box.minX() || pos.getX() == box.maxX())) {
					mined.add(pos.immutable());
				}
			}
			helper.assertTrue(mined.size() == 2, "setup: no two wall planks at the start of DECORATION: " + mined);
			for (BlockPos pos : mined) {
				p.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
				helper.assertTrue(p.gameMode.destroyBlock(pos), "the player couldn't mine " + helper.relativePos(pos));
				helper.assertTrue(r.level().getBlockState(pos).isAir(), "mined, " + helper.relativePos(pos) + " is " + r.level().getBlockState(pos));
			}
			LOGGER.info("qa mind mined: mined {} at {}", mined.stream().map(helper::relativePos).toList(), QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!mined.isEmpty(), "not mined yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			for (BlockPos pos : mined) {
				helper.assertTrue(r.level().getBlockState(pos).is(Blocks.OAK_PLANKS), "the mined plank at " + helper.relativePos(pos) + " wasn't laid again");
			}
			// the two the player mined are the player's: in the inventory or on the ground (counted by loose)
			// two more came out of the chests to lay them again; the two mined are on the ground (counted) or with the player
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r,
				Map.of(Blocks.OAK_PLANKS.asItem(), -p.getInventory().countItem(Blocks.OAK_PLANKS.asItem())));
			LOGGER.info("qa mind mined: both planks laid again, player holds {}", p.getInventory().countItem(Blocks.OAK_PLANKS.asItem()));
		});
	}

	// ----------------------------------------------------------------------------- a scan of a half-built hut

	/**
	 * Mid-STRUCTURE a player scans the half-built hut with the Scan Tool (corners: the ground block outside its north-west
	 * foot and a log marker outside its top south-east corner) and saves it. The blueprint holds exactly what stood in the
	 * box at that moment (the half, not the drawing; no villager), the Blank is used up, and the build under way is
	 * not disturbed: it ends as drawn with every material adding up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMindScanHalf"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMindScanHalf")
	public void aScanOfTheHalfBuiltHutHoldsWhatStandsAndTheBuildCarriesOn(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcC");
		QaBuilderTerrain1102GameTests.Run r = h.run();
		ServerPlayer p = h.player();
		boolean[] scanned = {false};
		midStructureWithABag(helper, r, 20, () -> {
			BoundingBox box = r.plan().bounds();
			BlockPos low = new BlockPos(box.minX() - 1, box.minY() - 1, box.minZ() - 1);
			BlockPos high = new BlockPos(box.maxX() + 1, box.maxY(), box.maxZ() + 1);
			r.level().setBlockAndUpdate(high, Blocks.OAK_LOG.defaultBlockState());
			ItemStack held = p.getMainHandItem().copy();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
			p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
			h.client().seen().clear();
			QaStageThreeGameTests.click(helper, p, low.subtract(helper.absolutePos(BlockPos.ZERO)));
			QaStageThreeGameTests.click(helper, p, high.subtract(helper.absolutePos(BlockPos.ZERO)));
			// what stands in the box the moment it's saved
			Map<BlockPos, net.minecraft.world.level.block.Block> standing = new HashMap<>();
			int hutBlocks = 0;
			for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
				BlockState s = r.level().getBlockState(pos);
				if (!s.isAir()) {
					standing.put(pos.subtract(low), s.getBlock());
					if (box.isInside(pos) && pos.getY() > low.getY()) {
						hutBlocks++;
					}
				}
			}
			p.setShiftKeyDown(true);
			QaStageThreeGameTests.clickAir(helper, p);
			p.setShiftKeyDown(false);
			helper.assertTrue(QaStageThreeGameTests.last(h.client().seen()).equals("message.aliveworkplace.scan.saved"), "saving the scan said "
				+ texts(h.client().seen()));
			ItemStack made = p.getInventory().items.stream().filter(s -> s.is(ModItems.BLUEPRINT)).findFirst()
				.orElseThrow(() -> new GameTestAssertException("the scan gave no blueprint"));
			helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the Blank wasn't used up");
			Blueprint bp = BlueprintLibrary.get(r.level(), BlueprintItem.data(made).orElseThrow().structure()).orElseThrow();
			Map<BlockPos, net.minecraft.world.level.block.Block> inBlueprint = new HashMap<>();
			for (Blueprint.Entry e : bp.blocks()) {
				if (!e.state().isAir()) {
					inBlueprint.put(e.pos(), e.state().getBlock());
				}
			}
			int drawn = r.plan().placeableCount();
			helper.assertTrue(hutBlocks >= 20 && hutBlocks < drawn && r.site() != null, "setup: the hut wasn't half-built at the scan: " + hutBlocks + " of " + drawn);
			helper.assertTrue(inBlueprint.equals(standing), "the scan of the half-built hut isn't what stood: " + inBlueprint.size() + " blocks scanned, "
				+ standing.size() + " stood; e.g. scanned-only " + diff(inBlueprint, standing) + ", standing-only " + diff(standing, inBlueprint));
			helper.assertTrue(bp.entities().isEmpty(), "the scan took entities along (the builder?): " + bp.entities().size());
			r.level().setBlockAndUpdate(high, Blocks.AIR.defaultBlockState());
			p.setItemInHand(InteractionHand.MAIN_HAND, held);
			scanned[0] = true;
			LOGGER.info("qa mind scan: scanned {} blocks ({} of the hut, which draws {}) at {}", inBlueprint.size(), hutBlocks, drawn,
				QaBuilderTerrain1102GameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(scanned[0], "not scanned yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, r);
		});
	}

	// -------------------------------------------------------------------------------- canceled, then saved and loaded

	/**
	 * Mid-STRUCTURE the player cancels the build, and the world is saved and loaded at once (sites and builder). The
	 * canceled site doesn't come back: no site at the spot, the builder idle, nothing built or taken down at the half
	 * for the next 400 ticks; the blueprint is in the player's hand once (none dropped or in the chests), still placed;
	 * the builder's bag has gone back to the chests (cancel: "the builder's materials go back to the store") and every
	 * material adds up with what stands.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaMindCancelReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaMindCancelReload")
	public void aCanceledBuildDoesNotComeBackAfterASaveAndLoad(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcR");
		QaBuilderTerrain1102GameTests.Run r = h.run();
		ServerPlayer p = h.player();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		Map<BlockPos, BlockState> half = new HashMap<>();
		Map<Item, Integer> before = new HashMap<>();
		long[] at = {-1};
		midStructureWithABag(helper, r, () -> {
			before.putAll(totals(helper, r, p));
			String was = QaBuilderTerrain1102GameTests.state(helper, r) + ", bag " + bag(r.builder()[0], r.plan());
			cancel(helper, h, r.id(), hut);
			snapshot(r, half);
			r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, r.level(), r.builder()[0]);
			at[0] = helper.getTick();
			LOGGER.info("qa mind cancel+reload: canceled and reloaded at {}", was);
		});
		helper.onEachTick(() -> {
			if (at[0] < 0) {
				return;
			}
			helper.assertTrue(BuildSiteManager.get(r.level()).get(r.id()) == null, "the canceled site came back after the load");
			BlueprintData.Placement spot = new BlueprintData.Placement(r.level().dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
			for (BuildSite s : BuildSiteManager.get(r.level()).all()) {
				helper.assertFalse(s.placement().equals(spot), "a site is open at the canceled spot again: " + s.id());
			}
			helper.assertTrue(Builders.activeSite(r.level(), r.builder()[0]) == null, "the builder took a build up again after the load");
			assertUnchanged(helper, r, half, "the canceled half");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0 && helper.getTick() - at[0] >= 400, "not 400 ticks past the cancel yet");
			int blueprints = 0;
			for (ItemStack s : p.getInventory().items) {
				if (s.is(ModItems.BLUEPRINT)) {
					blueprints += s.getCount();
					helper.assertTrue(BlueprintItem.data(s).orElseThrow().placement().isPresent(), "the blueprint came back not placed");
				}
			}
			int elsewhere = 0;
			for (BlockPos pos : r.supplies()) {
				elsewhere += ((Container) helper.getBlockEntity(pos)).countItem(ModItems.BLUEPRINT);
			}
			for (net.minecraft.world.entity.item.ItemEntity e : r.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(16))) {
				if (e.getItem().is(ModItems.BLUEPRINT)) {
					elsewhere += e.getItem().getCount();
				}
			}
			helper.assertTrue(blueprints == 1 && elsewhere == 0, "blueprints after cancel and load: " + blueprints + " with the player, " + elsewhere
				+ " in the chests or on the ground (wanted the one in the player's hand)");
			Map<Item, Integer> after = totals(helper, r, p);
			helper.assertTrue(after.equals(before), "the cancel and the load made or lost materials: " + before + " then " + after);
			Map<Item, Integer> b = bag(r.builder()[0], r.plan());
			helper.assertTrue(b.isEmpty(), "400 ticks after the cancel the builder still carries the hut's materials " + b
				+ " (cancel: the builder's materials go back to the store)");
			LOGGER.info("qa mind cancel+reload: stayed canceled 400 ticks, blueprint with the player, bag back, totals {}", after);
		});
	}

	// ---------------------------------------------------------------------------- canceled, then handed straight back

	/**
	 * Mid-DECORATION the player cancels, then hands the returned blueprint (still placed) straight back. command-cancel:
	 * "hand it back to the builder to carry on". He carries on at the same spot: the hut ends as drawn, no block built
	 * twice, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaMindCancelBack"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaMindCancelBack")
	public void aBuildCanceledMidDecorationAndHandedBackCarriesOnWithNothingDoubled(GameTestHelper helper) {
		Hand h = startByHand(helper, "qaMcB");
		QaBuilderTerrain1102GameTests.Run first = h.run();
		ServerPlayer p = h.player();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		QaBuilderTerrain1102GameTests.Run[] next = new QaBuilderTerrain1102GameTests.Run[1];
		helper.onEachTick(() -> {
			BuildSite s = first.site();
			if (next[0] != null || s == null || s.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			BlueprintData.Placement spot = s.placement();
			String was = QaBuilderTerrain1102GameTests.state(helper, first);
			cancel(helper, h, first.id(), hut);
			ItemStack blueprint = returned(helper, p, first);
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			h.client().seen().clear();
			QaStageSixGameTests.clickVillager(p, first.builder()[0], h.client().seen());
			BuildSite again = Builders.activeSite(first.level(), first.builder()[0]);
			helper.assertTrue(again != null, "handed back, the blueprint started nothing: " + texts(h.client().seen()));
			helper.assertTrue(again.placement().equals(spot), "handed back, he builds at " + again.placement() + " not " + spot);
			next[0] = new QaBuilderTerrain1102GameTests.Run(first.level(), first.builder(), again.id(), again.plan(first.level()), 2, first.supplies(), first.died());
			LOGGER.info("qa mind cancel+back: canceled at {}, handed back: {}", was, texts(h.client().seen()));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(next[0] != null, "not canceled and handed back yet");
			QaBuilderTerrain1102GameTests.assertFinished(helper, next[0]);
			QaBuilderTerrain1102GameTests.assertNothingMadeOrLost(helper, next[0]);
			LOGGER.info("qa mind cancel+back: finished as drawn, nothing made or lost");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/** QaBuilderTerrain1102GameTests.start's layout, the build handed over by a survival player's click with a blueprint placed at HUT. */
	static Hand startByHand(GameTestHelper helper, String prefix) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		ServerLevel level = helper.getLevel();
		BlockPos chest = new BlockPos(2, 2, 4);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		for (BlockPos pos : supplies) {
			helper.setBlock(pos, pos.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		UUID who = builder.getUUID();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (who.equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(prefix));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, placed(level, helper.absolutePos(HUT)));
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		if (site == null) {
			throw new GameTestAssertException("the hut handed over started nothing: " + texts(c.seen()));
		}
		BuildPlan plan = site.plan(level);
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			int left = n + EXTRA;
			while (left > 0) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		for (int i = 0; i < stock.size(); i++) {
			Container box = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			box.setItem(i % 27, stock.get(i));
		}
		LOGGER.info("qa mind: handed over by {}, materials {}, bounds {}", p.getGameProfile().getName(), plan.materials(), plan.bounds());
		QaBuilderTerrain1102GameTests.Run r = new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, site.id(), plan, 2, supplies, new String[1]);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		return new Hand(r, c);
	}

	/** A test-hut blueprint placed with its origin at absolute {@code origin}, unturned. */
	static ItemStack placed(ServerLevel level, BlockPos origin) {
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, size);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	/** The player types /workplace cancel for the site; the line and the site's end are checked. */
	private static void cancel(GameTestHelper helper, Hand h, UUID id, String hut) {
		QaStageSixGameTests.command(h.client(), "/workplace cancel " + id);
		List<String> said = texts(h.client().seen());
		helper.assertTrue(said.contains("Canceled the build of " + hut + ". Its blueprint is still placed there: hand it back to carry on, or click the ground to move it."),
			"canceling said " + said);
		helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(id) == null, "the canceled site is still open");
	}

	/** The canceled blueprint, back with the player and still placed at the site's spot. */
	private static ItemStack returned(GameTestHelper helper, ServerPlayer p, QaBuilderTerrain1102GameTests.Run r) {
		ItemStack found = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				helper.assertTrue(found.isEmpty(), "more than one blueprint came back");
				found = s.copy();
			}
		}
		helper.assertTrue(!found.isEmpty(), "the canceled blueprint didn't come back to the player");
		BlueprintData data = BlueprintItem.data(found).orElseThrow();
		helper.assertTrue(data.placement().isPresent() && data.placement().get().origin().equals(helper.absolutePos(HUT)),
			"the canceled blueprint came back placed at " + data.placement());
		return found;
	}

	/** Every block in the run's box now. */
	private static void snapshot(QaBuilderTerrain1102GameTests.Run r, Map<BlockPos, BlockState> into) {
		BoundingBox box = r.plan().bounds();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			into.put(pos.immutable(), r.level().getBlockState(pos));
		}
	}

	private static void assertUnchanged(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Map<BlockPos, BlockState> then, String what) {
		List<String> changed = new ArrayList<>();
		then.forEach((pos, state) -> {
			BlockState now = r.level().getBlockState(pos);
			if (now.getBlock() != state.getBlock()) {
				changed.add(helper.relativePos(pos).toShortString() + " " + state.getBlock().getDescriptionId() + "->" + now.getBlock().getDescriptionId());
			}
		});
		helper.assertTrue(changed.isEmpty(), what + " changed at " + changed.size() + " block(s), e.g. " + changed.stream().limit(8).toList());
	}

	/** Every material of the hut: loose (chests, bags, ground), with the player, and standing in the hut's box. */
	private static Map<Item, Integer> totals(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, ServerPlayer p) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : r.plan().materials().keySet()) {
			int standing = QaBuilderTerrain1102GameTests.count(r, s -> s.getBlock().asItem() == item
				&& !(s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
					&& s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
					== net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
			out.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item) + p.getInventory().countItem(item) + standing);
		}
		return out;
	}

	/** The player puts {@code n} of {@code item} into the chest (or the barrels when it's full). */
	private static void topUp(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Item item, int n) {
		int left = n;
		for (BlockPos pos : r.supplies()) {
			Container c = helper.getBlockEntity(pos);
			for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
				ItemStack in = c.getItem(i);
				if (in.isEmpty()) {
					int k = Math.min(left, item.getDefaultMaxStackSize());
					c.setItem(i, new ItemStack(item, k));
					left -= k;
				} else if (in.is(item) && in.getCount() < in.getMaxStackSize()) {
					int k = Math.min(left, in.getMaxStackSize() - in.getCount());
					in.grow(k);
					left -= k;
				}
			}
			c.setChanged();
		}
		helper.assertTrue(left == 0, "no room in the chests for " + n + " " + item);
	}

	/** The hut's materials in the builder's bag. */
	private static Map<Item, Integer> bag(Villager v, BuildPlan plan) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : plan.materials().keySet()) {
			int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			if (n > 0) {
				out.put(item, n);
			}
		}
		return out;
	}

	/** Runs {@code then} once, mid-STRUCTURE: four or more blocks of the hut built since STRUCTURE began, and materials in the bag. */
	private static void midStructureWithABag(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, Runnable then) {
		midStructureWithABag(helper, r, 4, then);
	}

	/** The same, once {@code min} or more blocks of the hut are built since STRUCTURE began. */
	private static void midStructureWithABag(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, int min, Runnable then) {
		int[] atStart = {-1};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (done[0] || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(r.level()).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			if (atStart[0] - unbuilt < min || bag(r.builder()[0], r.plan()).isEmpty()) {
				return;
			}
			done[0] = true;
			then.run();
		});
	}

	private static <V> List<String> diff(Map<BlockPos, V> a, Map<BlockPos, V> b) {
		return a.entrySet().stream().filter(e -> !e.getValue().equals(b.get(e.getKey()))).limit(6)
			.map(e -> e.getKey().toShortString() + "=" + e.getValue()).toList();
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}
}
