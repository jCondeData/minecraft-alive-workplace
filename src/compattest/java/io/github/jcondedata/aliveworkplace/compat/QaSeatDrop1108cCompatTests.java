package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA lane-c 2026-11-08, B957, B1024 and B1028 (written from the bugs' text and docs/wiki/furniture-and-storage.md, not
 * from the code): a villager whose chair is broken or taken on the way picks another at once, and a villager who no
 * longer goes to a seat stops walking to it. The corners the fixes' own tests leave out: the broken chair was the only
 * one (he must not walk on and stand where it was, nor sit on air), the chair swapped for a furniture block that isn't a
 * seat (a table), a chair broken and put back (a broken chair isn't one he gave up on, so he sits on the new one), and a
 * dropped seat leaves alone a walk the villager was set on somewhere else.
 */
public class QaSeatDrop1108cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(20, 2, 15);
	private static final BlockPos START = CHAIR.west(6);

	private static Block block(GameTestHelper helper, String id) {
		Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
		helper.assertTrue(b != Blocks.AIR, "setup: " + id + " isn't loaded");
		return b;
	}

	private static Block oakChair(GameTestHelper helper) {
		Block chair = block(helper, "handcrafted:oak_chair");
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		return chair;
	}

	private static Runnable clock(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	private static void setTime(ServerLevel level, long time) {
		long day = level.getDayTime() / 24000;
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= time ? day : day + 1) * 24000 + time);
	}

	private static BlockPos seatPos(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat.blockPosition() : null;
	}

	private static boolean walkTargetIs(Villager v, BlockPos pos) {
		return v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).filter(w -> w.getTarget().currentBlockPosition().equals(pos)).isPresent();
	}

	/** Holds {@code v} at {@code spot} with no walk while he has no seat in mind: a stroll can't take him off before he picks. */
	private static void hold(Villager v, Vec3 spot) {
		v.getNavigation().stop();
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.teleportTo(spot.x, spot.y, spot.z);
	}

	/** The chair, a villager 6 blocks west of it, supper time and the clock put back afterwards. */
	private static Villager setUp(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		Villager v = helper.spawn(EntityType.VILLAGER, START);
		Runnable restore = clock(helper.getLevel());
		PartnerShowsCompatTests.after(helper, restore);
		setTime(helper.getLevel(), FurnitureSeats.SUPPER + 300);
		return v;
	}

	/**
	 * The only chair round is broken while the villager walks to it: within 2 seconds he no longer has it in mind nor walks
	 * to where it stood, and for the next 5 seconds nobody sits on air there.
	 */
	//$ gametest_ticks_batch HUGE_AREA '500' '"qa1108c_seat_only_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 500, batch = "qa1108c_seat_only_broken")
	public void aVillagerWhoseOnlyChairIsBrokenStopsWalkingWhereItStood(GameTestHelper helper) {
		replacedOnTheWay(helper, Blocks.AIR, "broken");
	}

	/**
	 * The chair is swapped for a Handcrafted table (furniture, but not a seat) while the villager walks to it: he lets it
	 * go as if it were broken and doesn't sit on the table.
	 */
	//$ gametest_ticks_batch HUGE_AREA '500' '"qa1108c_seat_swapped_table"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 500, batch = "qa1108c_seat_swapped_table")
	public void aChairSwappedForATableOnTheWayIsLetGo(GameTestHelper helper) {
		Block table = block(helper, "handcrafted:oak_table");
		helper.assertFalse(table.defaultBlockState().is(FurnitureSeats.SEATS), "setup: Handcrafted's oak table is a villager seat");
		replacedOnTheWay(helper, table, "swapped for a table");
	}

	private static void replacedOnTheWay(GameTestHelper helper, Block with, String what) {
		ServerLevel level = helper.getLevel();
		Villager v = setUp(helper);
		Vec3 spot = v.position();
		BlockPos chair = helper.absolutePos(CHAIR);
		long[] at = {-1};
		helper.onEachTick(() -> {
			if (at[0] < 0) {
				if (chair.equals(FurnitureSeats.walkingTo(v)) && !v.isPassenger()) {
					at[0] = helper.getTick();
					helper.setBlock(CHAIR, with);
				} else if (FurnitureSeats.walkingTo(v) == null && !v.isPassenger()) {
					hold(v, spot);
				}
				return;
			}
			long since = helper.getTick() - at[0];
			if (v.isPassenger()) {
				helper.fail("the villager sat down " + since + " ticks after his only chair was " + what + " (at " + seatPos(v) + "); "
					+ FurnitureSeats.describe(level, v));
			}
			if (!level.getEntitiesOfClass(FurnitureSeat.class, new AABB(chair).inflate(1)).isEmpty()) {
				helper.fail("a seat stands where the chair was " + what + ", " + since + " ticks after");
			}
			if (since > 40 && chair.equals(FurnitureSeats.walkingTo(v))) {
				helper.fail("the villager still has the chair in mind " + since + " ticks after it was " + what + "; "
					+ FurnitureSeats.describe(level, v));
			}
			if (since > 40 && walkTargetIs(v, chair)) {
				helper.fail("the villager still walks to where the chair stood " + since + " ticks after it was " + what + "; "
					+ FurnitureSeats.describe(level, v));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(helper.getTick() - at[0] >= 140, "watching for 140 ticks after the chair was " + what);
			v.discard();
		});
	}

	/**
	 * The chair is broken while the villager walks to it and a new chair is put in its place 3 seconds later: a broken
	 * chair isn't one he couldn't reach, so he doesn't leave the spot alone for a minute; he picks the new chair and sits.
	 */
	//$ gametest_ticks_batch HUGE_AREA '700' '"qa1108c_seat_put_back"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 700, batch = "qa1108c_seat_put_back")
	public void aChairBrokenAndPutBackIsSatOnAfterAll(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager v = setUp(helper);
		Vec3 spot = v.position();
		BlockPos chair = helper.absolutePos(CHAIR);
		Block oak = oakChair(helper);
		int[] step = {0};
		long[] at = {-1, -1};
		helper.onEachTick(() -> {
			if (step[0] == 0 && chair.equals(FurnitureSeats.walkingTo(v)) && !v.isPassenger()) {
				step[0] = 1;
				at[0] = helper.getTick();
				helper.setBlock(CHAIR, Blocks.AIR);
				helper.runAfterDelay(60, () -> {
					helper.setBlock(CHAIR, oak);
					step[0] = 2;
					at[1] = helper.getTick();
				});
			} else if (step[0] != 1 && FurnitureSeats.walkingTo(v) == null && !v.isPassenger()) {
				hold(v, spot); // within reach of the chair till he picks it, at the start and once it is back
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(step[0] >= 2, "waiting for the chair to be put back");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair put back " + (helper.getTick() - at[1])
				+ " ticks ago (the first was broken " + (at[1] - at[0]) + " ticks before that); " + FurnitureSeats.describe(level, v));
			v.discard();
		});
	}

	/**
	 * A villager walking to a chair is sent somewhere else (another walk, 12 blocks north) and then the furnishings switch
	 * goes off: he forgets the chair, but the other walk is his and stays (only a walk to the dropped seat is stopped).
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qa1108c_seat_other_walk"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qa1108c_seat_other_walk")
	public void aDroppedSeatLeavesAWalkElsewhereAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Villager v = setUp(helper);
		boolean wasOn = Furnishings.ENABLED;
		helper.assertTrue(wasOn, "setup: furnishings are off before the test");
		PartnerShowsCompatTests.after(helper, () -> Furnishings.ENABLED = wasOn);
		Vec3 spot = v.position();
		BlockPos chair = helper.absolutePos(CHAIR);
		BlockPos elsewhere = helper.absolutePos(START.north(12));
		long[] at = {-1, -1};
		String[] seen = {null};
		helper.onEachTick(() -> {
			if (at[0] < 0) {
				if (chair.equals(FurnitureSeats.walkingTo(v)) && !v.isPassenger()) {
					at[0] = helper.getTick();
					v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(elsewhere, 0.5f, 1));
					Furnishings.ENABLED = false;
				} else if (FurnitureSeats.walkingTo(v) == null && !v.isPassenger()) {
					hold(v, spot);
				}
			} else if (at[1] < 0 && FurnitureSeats.walkingTo(v) == null) {
				at[1] = helper.getTick();
				seen[0] = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString())
					.orElse("none");
				Furnishings.ENABLED = wasOn;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(at[1] >= 0, "the villager still has the chair in mind " + (helper.getTick() - at[0])
				+ " ticks after the switch went off; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(elsewhere.toShortString().equals(seen[0]), "when the villager let the chair go (" + (at[1] - at[0])
				+ " ticks after the switch went off) his walk to " + elsewhere.toShortString() + " was stopped too: walk target " + seen[0]);
			helper.assertFalse(v.isPassenger(), "the villager sat down with the switch off");
			v.discard();
		});
	}
}
