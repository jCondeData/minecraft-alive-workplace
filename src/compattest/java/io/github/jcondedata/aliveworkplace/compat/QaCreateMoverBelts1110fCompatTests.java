package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MAX_STEPS;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.onto;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-10, run 1012), from 45.3's spec and the wiki (moving-buildings.md: "a belt of any length comes
 * across whole, with the items on it, and runs at the new spot"; a canceled move puts everything taken down back up where
 * it was; a save and reload in each stage loses nothing), verifying B1128's fix past its own test: the move called off
 * with the belt packed, a reload with the belt packed, two belts (one five long) in one building, and a sloped belt. Each
 * checks every segment stands, the items on the belt, each segment's Controller naming where its controller now stands,
 * that the belt still takes an item, and that nothing lies loose. Helpers: QaCreateMoverContents1110eCompatTests.
 */
public class QaCreateMoverBelts1110fCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;
	static final String[] PARTS3 = {"start", "middle", "end"};

	/** A belt's segments (relative), the facing and slope they share; the first is the controller. */
	record Belt(List<BlockPos> segments, String facing, String slope) {
		BlockState state(int i) {
			String part = i == 0 ? "start" : i == segments.size() - 1 ? "end" : "middle";
			return with(with(with(block("create:belt").defaultBlockState(), "facing", facing), "slope", slope), "part", part);
		}
	}

	/** Lays {@code belt} with Create's own data: each segment's index, the length, and the controller's world position. */
	static void lay(GameTestHelper helper, Belt belt) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < belt.segments().size(); i++) {
			set(level, helper.absolutePos(belt.segments().get(i)), belt.state(i));
		}
		BlockPos controller = helper.absolutePos(belt.segments().get(0));
		for (int i = 0; i < belt.segments().size(); i++) {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(belt.segments().get(i)));
			helper.assertTrue(be != null, "setup: no belt block entity at " + belt.segments().get(i));
			CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			data.putBoolean("IsController", i == 0);
			data.putInt("Index", i);
			data.putInt("Length", belt.segments().size());
			data.putIntArray("Controller", new int[] {controller.getX(), controller.getY(), controller.getZ()});
			be.loadWithComponents(data, level.registryAccess());
			be.setChanged();
		}
	}

	/** Where a segment's data says its controller stands (Create writes it as an int array or as X/Y/Z), or null. */
	static BlockPos controllerOf(ServerLevel level, BlockPos abs) {
		BlockEntity be = level.getBlockEntity(abs);
		if (be == null) {
			return null;
		}
		Tag tag = be.saveWithoutMetadata(level.registryAccess()).get("Controller");
		if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] p = array.getAsIntArray();
			return new BlockPos(p[0], p[1], p[2]);
		}
		if (tag instanceof CompoundTag xyz && xyz.contains("X")) {
			return new BlockPos(xyz.getInt("X"), xyz.getInt("Y"), xyz.getInt("Z"));
		}
		return null;
	}

	/** Where the block at {@code rel} (in the test) goes at the new spot: the move is a quarter turn clockwise. */
	static BlockPos moved(Setup s, BlockPos rel) {
		return s.before().at(rel.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	/**
	 * What is wrong with {@code belt} standing at {@code at} (each segment's world position, the controller first):
	 * a segment missing, a Controller not naming {@code at[0]}, or not {@code items} of {@code item} on it in all.
	 */
	static List<String> wrong(ServerLevel level, Belt belt, List<BlockPos> at, Item item, int items) {
		List<String> out = new ArrayList<>();
		long on = 0;
		for (int i = 0; i < at.size(); i++) {
			BlockState there = level.getBlockState(at.get(i));
			if (!there.is(block("create:belt"))) {
				out.add("segment " + i + " at " + at.get(i).toShortString() + " is " + there);
				continue;
			}
			BlockPos c = controllerOf(level, at.get(i));
			if (!at.get(0).equals(c)) {
				out.add("segment " + i + " at " + at.get(i).toShortString() + " names its controller at "
					+ (c == null ? "nothing" : c.toShortString()) + ", not " + at.get(0).toShortString());
			}
			on += held(level, at.get(i), item);
		}
		if (on != items) {
			out.add(on + " of the " + items + " " + item + " on it");
		}
		return out;
	}

	static List<BlockPos> abs(GameTestHelper helper, Belt belt) {
		return belt.segments().stream().map(helper::absolutePos).toList();
	}

	static List<BlockPos> movedAll(Setup s, Belt belt) {
		return belt.segments().stream().map(p -> moved(s, p)).toList();
	}

	static final Belt FLAT = new Belt(List.of(new BlockPos(4, 3, 5), new BlockPos(5, 3, 5), new BlockPos(6, 3, 5)), "east", "horizontal");

	/** The flat three-segment belt laid, an iron ingot put on each end (2 on it). */
	static void flatWithIngots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		lay(helper, FLAT);
		helper.runAfterDelay(1, () -> {
			int on = (onto(level, helper.absolutePos(FLAT.segments().get(0)), Items.IRON_INGOT) ? 1 : 0)
				+ (onto(level, helper.absolutePos(FLAT.segments().get(2)), Items.IRON_INGOT) ? 1 : 0);
			helper.assertTrue(on == 2, "setup: the belt took " + on + " of 2 iron ingots");
		});
	}

	/**
	 * The move called off with the house all down (the belt packed): the belt goes back up at its old spot whole, its
	 * two ingots on it, every segment naming its controller there, and it takes an item; nothing at the new spot or loose.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_belt_cancel"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_belt_cancel")
	public void aBeltMoveCanceledWithTheBeltPackedPutsItBackWholeAndRunning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		flatWithIngots(helper);
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_cancel");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage down = site.stage();
				boolean gone = abs(helper, FLAT).stream().allMatch(p -> level.getBlockState(p).isAir());
				MoveSites.get(level).cancel(level, s.from());
				for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
					MoveSites.get(level).step(level, site);
				}
				MoveSite.Stage end = site.stage();
				helper.runAfterDelay(5, () -> {
					List<BlockPos> at = abs(helper, FLAT);
					List<String> wrong = wrong(level, FLAT, at, Items.IRON_INGOT, 2);
					boolean atNew = movedAll(s, FLAT).stream().anyMatch(p -> level.getBlockState(p).is(block("create:belt")));
					boolean takes = level.getBlockState(at.get(1)).is(block("create:belt")) && onto(level, at.get(1), Items.GOLD_INGOT);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && gone, "the house didn't come down first: " + down + ", belt gone " + gone);
						helper.assertTrue(end == MoveSite.Stage.DONE, "the canceled move ended " + end);
						helper.assertTrue(wrong.isEmpty(), "after the canceled move the belt at its old spot: " + wrong);
						helper.assertTrue(!atNew, "a belt segment stands at the new spot after the move was canceled");
						helper.assertTrue(looseNow.isEmpty(), "loose after the canceled move: " + looseNow);
						helper.assertTrue(takes, "the belt put back doesn't take an item on its middle segment");
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Saved and reloaded with the belt packed in the Mover's crates (the house all down): the move finishes with the belt
	 * whole at the new spot, its two ingots once, every Controller remapped there, and it takes an item.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_belt_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_belt_reload")
	public void aMoveReloadedWithTheBeltPackedBringsItAcrossWholeAndRunning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		flatWithIngots(helper);
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_reload");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				MoveSite.Stage down = site.stage();
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				MoveSite again = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
				MoveSite done = work(level, s, x -> false);
				MoveSite.Stage end = done.stage();
				int skipped = done.skipped();
				helper.runAfterDelay(5, () -> {
					List<BlockPos> at = movedAll(s, FLAT);
					List<String> wrong = wrong(level, FLAT, at, Items.IRON_INGOT, 2);
					boolean takes = level.getBlockState(at.get(1)).is(block("create:belt")) && onto(level, at.get(1), Items.GOLD_INGOT);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && again != site, "the reload didn't happen with the house down: " + down);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the reloaded move ended " + end + ", skipped " + skipped);
						helper.assertTrue(wrong.isEmpty(), "after a reload with the belt packed, the belt at the new spot: " + wrong);
						helper.assertTrue(looseNow.isEmpty(), "loose after the reloaded move: " + looseNow);
						helper.assertTrue(takes, "the moved belt doesn't take an item on its middle segment");
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Two belts in one building, a three-segment one running east and a five-segment one running south (an iron ingot on
	 * the first's start, a gold ingot on each end of the second): both come across whole, each segment naming its own
	 * belt's controller at the new spot, with their own items; nothing dropped, nothing skipped.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_belt_two"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_belt_two")
	public void twoBeltsInOneBuildingComeAcrossEachWholeWithItsOwnController(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Belt east = new Belt(List.of(new BlockPos(3, 3, 3), new BlockPos(4, 3, 3), new BlockPos(5, 3, 3)), "east", "horizontal");
		Belt south = new Belt(List.of(new BlockPos(7, 3, 3), new BlockPos(7, 3, 4), new BlockPos(7, 3, 5), new BlockPos(7, 3, 6),
			new BlockPos(7, 3, 7)), "south", "horizontal");
		lay(helper, east);
		lay(helper, south);
		helper.runAfterDelay(1, () -> {
			int on = (onto(level, helper.absolutePos(east.segments().get(0)), Items.IRON_INGOT) ? 1 : 0)
				+ (onto(level, helper.absolutePos(south.segments().get(0)), Items.GOLD_INGOT) ? 1 : 0)
				+ (onto(level, helper.absolutePos(south.segments().get(4)), Items.GOLD_INGOT) ? 1 : 0);
			helper.assertTrue(on == 3, "setup: the belts took " + on + " of 3 ingots");
		});
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_two");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				Map<String, Integer> afterDown = loose(helper);
				int skippedDown = site.skipped();
				work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				helper.runAfterDelay(5, () -> {
					List<String> wrongEast = wrong(level, east, movedAll(s, east), Items.IRON_INGOT, 1);
					List<String> wrongSouth = wrong(level, south, movedAll(s, south), Items.GOLD_INGOT, 2);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(afterDown.isEmpty() && skippedDown == 0, "taking two belts down dropped " + afterDown + " and lost " + skippedDown);
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(wrongEast.isEmpty() && wrongSouth.isEmpty(), "at the new spot, the three-segment belt: "
							+ (wrongEast.isEmpty() ? "right" : wrongEast) + "; the five-segment belt: " + (wrongSouth.isEmpty() ? "right" : wrongSouth));
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * A sloped belt (three segments climbing east, one block up each) with an iron ingot on its foot: it comes across
	 * whole, still climbing (its slope kept, its facing turned with the building), the ingot on it, and takes an item.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110f_belt_slope"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110f_belt_slope")
	public void aSlopedBeltComesAcrossWholeAndStillClimbing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		Belt up = new Belt(List.of(new BlockPos(4, 3, 5), new BlockPos(5, 4, 5), new BlockPos(6, 5, 5)), "east", "upward");
		lay(helper, up);
		helper.runAfterDelay(1, () -> helper.assertTrue(onto(level, helper.absolutePos(up.segments().get(0)), Items.IRON_INGOT),
			"setup: the sloped belt didn't take an iron ingot"));
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_slope");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				helper.runAfterDelay(5, () -> {
					List<BlockPos> at = movedAll(s, up);
					List<String> wrong = wrong(level, up, at, Items.IRON_INGOT, 1);
					List<String> shape = new ArrayList<>();
					for (int i = 0; i < at.size(); i++) {
						BlockState there = level.getBlockState(at.get(i));
						BlockState want = up.state(i).rotate(Rotation.CLOCKWISE_90);
						if (there.is(block("create:belt")) && !there.equals(want)) {
							shape.add("segment " + i + " is " + there + ", not " + want);
						}
					}
					boolean takes = level.getBlockState(at.get(1)).is(block("create:belt")) && onto(level, at.get(1), Items.GOLD_INGOT);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(wrong.isEmpty(), "the sloped belt at the new spot: " + wrong);
						helper.assertTrue(shape.isEmpty(), "the sloped belt's shape at the new spot: " + shape);
						helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
						helper.assertTrue(takes, "the moved sloped belt doesn't take an item on its middle segment");
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Runs {@code step} once a game tick (so Create's belts tick between the Mover's steps, as in a real game) until it
	 * says the job is done or {@code max} ticks pass, then {@code then}.
	 */
	static void eachTick(GameTestHelper helper, java.util.function.BooleanSupplier step, int max, Runnable then) {
		helper.runAfterDelay(1, () -> {
			if (step.getAsBoolean() || max <= 0) {
				then.run();
			} else {
				eachTick(helper, step, max - 1, then);
			}
		});
	}

	/**
	 * The whole move done one Mover step a game tick (Create's belt ticking while it's half down and half up): the belt
	 * comes across whole, its two ingots on it, every Controller remapped, and it takes an item; nothing dropped.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa_e1110f_belt_ticks"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa_e1110f_belt_ticks")
	public void aBeltMovedOneStepATickComesAcrossWholeAndRunning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		flatWithIngots(helper);
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_ticks");
			MoveSite[] site = new MoveSite[1];
			eachTick(helper, () -> {
				if (site[0] == null) {
					site[0] = MoveSites.get(level).work(level, s.hall()).orElseThrow();
				}
				MoverWork.work(level, s.mover(), site[0]);
				return site[0].stage() == MoveSite.Stage.DONE;
			}, 1500, () -> helper.runAfterDelay(5, () -> {
				MoveSite.Stage end = site[0].stage();
				int skipped = site[0].skipped();
				List<BlockPos> at = movedAll(s, FLAT);
				List<String> wrong = wrong(level, FLAT, at, Items.IRON_INGOT, 2);
				boolean takes = level.getBlockState(at.get(1)).is(block("create:belt")) && onto(level, at.get(1), Items.GOLD_INGOT);
				Map<String, Integer> looseNow = loose(helper);
				try {
					helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move, a step a tick, ended " + end + ", skipped " + skipped);
					helper.assertTrue(wrong.isEmpty(), "moved a step a tick, the belt at the new spot: " + wrong);
					helper.assertTrue(looseNow.isEmpty(), "loose after the move: " + looseNow);
					helper.assertTrue(takes, "the moved belt doesn't take an item on its middle segment");
				} finally {
					tidy(helper, s);
				}
				helper.succeed();
			}));
		});
	}

	/**
	 * The move called off with the house all down, then put back one step a game tick (Create's belt ticking between):
	 * the belt stands whole at its old spot again, its two ingots on it, its Controller there, and it takes an item.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa_e1110f_belt_cancel_ticks"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa_e1110f_belt_cancel_ticks")
	public void aBeltMoveCanceledAndPutBackOneStepATickIsWholeAndRunning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		flatWithIngots(helper);
		helper.runAfterDelay(3, () -> {
			Setup s = setupAround(helper, "belt_cancel_ticks");
			MoveSite[] site = new MoveSite[1];
			eachTick(helper, () -> {
				if (site[0] == null) {
					site[0] = MoveSites.get(level).work(level, s.hall()).orElseThrow();
				}
				MoverWork.work(level, s.mover(), site[0]);
				return site[0].stage() == MoveSite.Stage.FOUNDATION;
			}, 1500, () -> {
				MoveSite.Stage down = site[0].stage();
				boolean gone = abs(helper, FLAT).stream().allMatch(p -> level.getBlockState(p).isAir());
				MoveSites.get(level).cancel(level, s.from());
				eachTick(helper, () -> {
					MoveSites.get(level).step(level, site[0]);
					return site[0].stage() == MoveSite.Stage.DONE;
				}, 1500, () -> helper.runAfterDelay(5, () -> {
					MoveSite.Stage end = site[0].stage();
					List<BlockPos> at = abs(helper, FLAT);
					List<String> wrong = wrong(level, FLAT, at, Items.IRON_INGOT, 2);
					boolean takes = level.getBlockState(at.get(1)).is(block("create:belt")) && onto(level, at.get(1), Items.GOLD_INGOT);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(down == MoveSite.Stage.FOUNDATION && gone, "the house didn't come down first: " + down + ", belt gone " + gone);
						helper.assertTrue(end == MoveSite.Stage.DONE, "the canceled move ended " + end);
						helper.assertTrue(wrong.isEmpty(), "put back a step a tick, the belt at its old spot: " + wrong);
						helper.assertTrue(looseNow.isEmpty(), "loose after the canceled move: " + looseNow);
						helper.assertTrue(takes, "the belt put back doesn't take an item on its middle segment");
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				}));
			});
		});
	}
}
