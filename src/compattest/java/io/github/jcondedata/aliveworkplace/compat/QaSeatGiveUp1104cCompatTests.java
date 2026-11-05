package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA lane-c 2026-11-04, B945 (written from the spec in docs/wiki/furniture-and-storage.md, not from the code): a villager
 * who can't reach their seat in 30 seconds (600 ticks) gives up on it and leaves it to the others for a minute (1200
 * ticks). Giving up on one seat isn't giving up on sitting, the minute ends, and while it lasts the seat is truly free.
 */
public class QaSeatGiveUp1104cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(6, 2, 6);
	/** The spec's give-up time and skip minute, in ticks (the constants themselves are package-private). */
	private static final int GIVE_UP = 600;
	private static final int SKIP = 1200;

	private static Block oakChair(GameTestHelper helper) {
		Block chair = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_chair"));
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

	/** A 3x3 glass pen, 3 tall with a roof, round {@code centre} (relative); {@code block} air opens it. */
	private static void pen(GameTestHelper helper, BlockPos centre, Block block) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y <= 2; y++) {
					if (dx != 0 || dz != 0 || y == 2) {
						helper.setBlock(centre.offset(dx, y, dz), block);
					}
				}
			}
		}
	}

	/**
	 * The nearest chair is walled in by a glass ring two blocks out (nobody can get within sitting reach of it); a second
	 * chair a block farther away is open. The villager picks the walled one first (it's nearest), gives up on it, and then
	 * sits on the open one: giving up on one seat isn't giving up on sitting down.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1300' '"qa1104c_seat_second_choice"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1300, batch = "qa1104c_seat_second_choice")
	public void aVillagerWhoGaveUpOnAWalledInChairSitsOnAnOpenOneFarther(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Block chair = oakChair(helper);
		helper.setBlock(CHAIR, chair.defaultBlockState());
		BlockPos walled = helper.absolutePos(CHAIR);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					for (int y = 0; y <= 2; y++) {
						helper.setBlock(CHAIR.offset(dx, y, dz), Blocks.GLASS);
					}
				}
			}
		}
		BlockPos openRel = CHAIR.south(9);
		helper.setBlock(openRel, chair.defaultBlockState());
		BlockPos open = helper.absolutePos(openRel);
		// 4 blocks from the walled chair, 5 from the open one: the walled one is nearest.
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south(4));
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 100);
		boolean[] pickedWalled = {false};
		long[] pickedAt = {-1};
		helper.onEachTick(() -> {
			if (!pickedWalled[0] && !v.isPassenger() && !FurnitureSeats.free(level, walled, null)) {
				pickedWalled[0] = true;
				pickedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pickedWalled[0], "setup: the villager never set off for the nearest (walled-in) chair; seated at " + seatPos(v));
			helper.assertTrue(v.isPassenger(), "the villager hasn't sat on the open chair " + (helper.getTick() - pickedAt[0])
				+ " ticks after setting off for the walled-in one (give-up is " + GIVE_UP + "); open chair free: "
				+ FurnitureSeats.free(level, open, v) + ", walled chair free: " + FurnitureSeats.free(level, walled, v));
			helper.assertTrue(open.equals(seatPos(v)), "the villager sat at " + seatPos(v) + ", not on the open chair " + open.toShortString());
			v.discard();
			restore.run();
		});
	}

	/**
	 * A villager penned in glass 4 blocks from a chair gives up on it. Supper is moved back to its start so it lasts
	 * longer than the minute; once the minute is over the pen is opened, and he sits on that chair after all: leaving it
	 * to the others is for a minute, not for good.
	 */
	//$ gametest_ticks_batch HUGE_AREA '2600' '"qa1104c_seat_skip_ends"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2600, batch = "qa1104c_seat_skip_ends")
	public void aVillagerSitsOnTheChairHeGaveUpOnOnceTheMinuteIsOverAndHeCanReachIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		BlockPos chair = helper.absolutePos(CHAIR);
		BlockPos penAt = CHAIR.west(4);
		pen(helper, penAt, Blocks.GLASS);
		Villager v = helper.spawn(EntityType.VILLAGER, penAt);
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		int[] step = {0};
		long[] at = {-1, -1, -1};
		helper.onEachTick(() -> {
			boolean free = FurnitureSeats.free(level, chair, null);
			if (step[0] == 0 && !free && !v.isPassenger()) {
				step[0] = 1;
				at[0] = helper.getTick();
			} else if (step[0] == 1 && free && !v.isPassenger()) {
				step[0] = 2;
				at[1] = helper.getTick();
				setTime(level, FurnitureSeats.SUPPER); // a whole supper ahead, longer than the minute
				helper.runAfterDelay(SKIP + 40, () -> {
					pen(helper, penAt, Blocks.AIR);
					at[2] = helper.getTick();
					step[0] = 3;
				});
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the penned villager never set off for the chair");
			helper.assertTrue(step[0] >= 2, "the penned villager never let go of the chair he can't reach (it never counted as free again) " + (helper.getTick() - at[0]) + " ticks after setting off");
			helper.assertTrue(step[0] >= 3, "waiting for the minute to pass (" + (helper.getTick() - at[1]) + " ticks since giving up)");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - at[2])
				+ " ticks after the pen was opened, " + (helper.getTick() - at[1]) + " ticks after giving up on it (sitting time: "
				+ FurnitureSeats.sittingTime(level) + ", seated at " + seatPos(v) + ")");
			v.discard();
			restore.run();
		});
	}

	/**
	 * A penned villager gives up on a chair. For the whole minute after that nobody counts as walking to it, and a villager
	 * who turns up beside it halfway through the minute sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1800' '"qa1104c_seat_left_free"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1800, batch = "qa1104c_seat_left_free")
	public void aChairGivenUpOnStaysFreeForTheMinuteAndALaterVillagerSitsOnIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		BlockPos chair = helper.absolutePos(CHAIR);
		BlockPos penAt = CHAIR.west(4);
		pen(helper, penAt, Blocks.GLASS);
		Villager penned = helper.spawn(EntityType.VILLAGER, penAt);
		Runnable restore = clock(level);
		setTime(level, FurnitureSeats.SUPPER + 300);
		int[] step = {0};
		long[] at = {-1, -1};
		Villager[] later = {null};
		String[] broken = {null};
		helper.onEachTick(() -> {
			boolean free = FurnitureSeats.free(level, chair, null);
			if (step[0] == 0 && !free && !penned.isPassenger()) {
				step[0] = 1;
				at[0] = helper.getTick();
			} else if (step[0] == 1 && free) {
				step[0] = 2;
				at[1] = helper.getTick();
				setTime(level, FurnitureSeats.SUPPER); // plenty of supper left
			} else if (step[0] == 2) {
				long since = helper.getTick() - at[1];
				if (!free && broken[0] == null) {
					broken[0] = "the chair counted as taken again " + since + " ticks after the penned villager gave up on it (skip is " + SKIP + ")";
				}
				if (since >= SKIP / 2) {
					step[0] = 3;
					later[0] = helper.spawn(EntityType.VILLAGER, CHAIR.south());
					at[1] = helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the penned villager never set off for the chair");
			helper.assertTrue(step[0] >= 2, "the penned villager never let go of the chair he can't reach (it never counted as free again) " + (helper.getTick() - at[0]) + " ticks after setting off");
			helper.assertTrue(broken[0] == null, broken[0]);
			helper.assertTrue(step[0] >= 3, "waiting for the middle of the minute");
			helper.assertFalse(penned.isPassenger(), "setup: the penned villager reached the chair");
			helper.assertTrue(chair.equals(seatPos(later[0])), "the villager who came beside the chair halfway through the minute hasn't sat on it "
				+ (helper.getTick() - at[1]) + " ticks later (seated at " + seatPos(later[0]) + ", free: " + FurnitureSeats.free(level, chair, later[0]) + ")");
			penned.discard();
			later[0].discard();
			restore.run();
		});
	}
}
