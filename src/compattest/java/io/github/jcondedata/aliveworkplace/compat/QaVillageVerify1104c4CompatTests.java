package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1104-1254) of B676 (two chairs seat two villagers, one each), from the bug and its fix's account of 37.6:
 * "a seat another villager is walking to isn't taken twice", and a villager walking to a chair keeps it until they sit
 * or give up. What the fix's test leaves out: three villagers for two chairs (one stays standing, no chair ever holds
 * two), the chair a villager is walking to broken on the way (they take the other one), and the furniture switch turned
 * off under a seated villager. (A villager penned where it can't reach the chair it picked is
 * QaVillageVerify1104c4CompatBugTests, on tests/village-1104c4.) Each test is alone in its batch and clears the area first.
 */
public class QaVillageVerify1104c4CompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHAIR = new BlockPos(6, 2, 6);

	private static BlockPos chair(GameTestHelper helper, BlockPos at) {
		Block chair = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_chair"));
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		helper.setBlock(at, chair.defaultBlockState());
		return helper.absolutePos(at);
	}

	@org.jetbrains.annotations.Nullable
	private static FurnitureSeat seatOf(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat : null;
	}

	private static List<FurnitureSeat> seats(GameTestHelper helper, BlockPos chair) {
		return helper.getLevel().getEntitiesOfClass(FurnitureSeat.class, new AABB(chair).inflate(1), Entity::isAlive);
	}

	/** Supper, the clock kept running from there; returns what puts the clock back. */
	private static Runnable supper(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		long now = day % 24000;
		long time = FurnitureSeats.SUPPER + 300;
		level.setDayTime((now <= time ? day / 24000 : day / 24000 + 1) * 24000 + time);
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	/**
	 * Three villagers at supper, two chairs: two sit, one on each chair; the third stays standing; on no tick does a
	 * chair hold two seats or two villagers share one.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa1104c4_three_for_two"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa1104c4_three_for_two")
	public void threeVillagersForTwoChairsSeatTwoOnePerChair(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = chair(helper, CHAIR);
		BlockPos b = chair(helper, CHAIR.east(4));
		List<Villager> villagers = List.of(helper.spawn(EntityType.VILLAGER, CHAIR.south()), helper.spawn(EntityType.VILLAGER, CHAIR.east(4).south()),
			helper.spawn(EntityType.VILLAGER, CHAIR.east(2).south(2)));
		Runnable restore = supper(level);
		helper.onEachTick(() -> {
			if (seats(helper, a).size() > 1 || seats(helper, b).size() > 1) {
				helper.fail("a chair holds two seats on tick " + helper.getTick() + ": " + seats(helper, a).size() + ", " + seats(helper, b).size());
			}
			for (FurnitureSeat seat : seats(helper, a).isEmpty() ? seats(helper, b) : seats(helper, a)) {
				if (seat.getPassengers().size() > 1) {
					helper.fail("two villagers share one seat on tick " + helper.getTick());
				}
			}
		});
		helper.succeedWhen(() -> {
			List<FurnitureSeat> taken = villagers.stream().map(QaVillageVerify1104c4CompatTests::seatOf).filter(s -> s != null).toList();
			helper.assertTrue(taken.size() == 2, taken.size() + " of 3 villagers seated (two chairs)");
			helper.assertTrue(!taken.get(0).blockPosition().equals(taken.get(1).blockPosition()), "both seated villagers on one chair");
			helper.assertTrue(List.of(a, b).contains(taken.get(0).blockPosition()) && List.of(a, b).contains(taken.get(1).blockPosition()), "seated off the chairs");
			villagers.forEach(Entity::discard);
			restore.run();
		});
	}

	/**
	 * A villager sets off for the nearer of two chairs; the chair is broken before they reach it. They take the other
	 * chair, and no seat is left where the broken one stood.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa1104c4_target_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa1104c4_target_broken")
	public void aVillagerWhoseChairIsBrokenOnTheWayTakesTheOther(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BlockPos nearRel = new BlockPos(6, 2, 4);
		BlockPos near = chair(helper, nearRel);
		BlockPos far = chair(helper, new BlockPos(13, 2, 10));
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 10));
		Runnable restore = supper(level);
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && !v.isPassenger() && !FurnitureSeats.free(level, near, null)) {
				broken[0] = true; // they're walking to it
				helper.setBlock(nearRel, Blocks.AIR);
			}
			if (!broken[0] && v.isPassenger()) {
				helper.fail("setup: the villager sat down before the chair they walked to could be broken");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the villager never set off for the nearer chair");
			FurnitureSeat seat = seatOf(v);
			helper.assertTrue(seat != null && seat.blockPosition().equals(far), "the villager didn't take the other chair: seated " + (seat != null)
				+ (seat == null ? "" : " at " + seat.blockPosition()));
			helper.assertTrue(seats(helper, near).isEmpty(), "a seat was left where the broken chair stood");
			v.discard();
			restore.run();
		});
	}

	/** The furniture switch turned off under a seated villager at supper: they stand up and the seat goes. */
	//$ gametest_ticks_batch HUGE_AREA '900' '"qa1104c4_switch_off"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa1104c4_switch_off")
	public void aSeatedVillagerStandsUpWhenFurnitureIsSwitchedOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Furnishings.ENABLED;
		BlockPos chair = chair(helper, CHAIR);
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.south());
		Runnable restore = supper(level);
		boolean[] off = {false};
		long[] offAt = {-1};
		// what was seen 100 ticks after the switch went off, when it is put back (so a failure never leaves it off)
		Boolean[] stillSeated = {null};
		Integer[] seatsLeft = {null};
		helper.onEachTick(() -> {
			if (!off[0] && seatOf(v) != null) {
				off[0] = true;
				offAt[0] = helper.getTick();
				Furnishings.ENABLED = false;
			}
			if (off[0] && stillSeated[0] == null && helper.getTick() - offAt[0] >= 100) {
				stillSeated[0] = v.isPassenger();
				seatsLeft[0] = seats(helper, chair).size();
				Furnishings.ENABLED = enabled;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "the villager never sat at supper");
			helper.assertTrue(stillSeated[0] != null, "waiting 100 ticks with the switch off");
			v.discard();
			restore.run();
			helper.assertFalse(stillSeated[0], "the villager still sat 100 ticks after the furniture was switched off");
			helper.assertTrue(seatsLeft[0] == 0, seatsLeft[0] + " seats stayed with the switch off");
		});
	}
}
