package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA, lane D (2026-11-08), from 31.13's spec: "the hideout is the bandit camp ... with his own banner on a spruce pole by
 * the fire" and "the captain at the chief's spot, the band round the fire". Mutation of Bounties left alive: the pole's
 * search stopping at ring 2 (rings 0-2 blocked, no pole at all) and a band member put half a block off his spot (no test
 * blocked the near ground or looked where a man stands). The pole is placed by the package-private {@code banner}, which
 * the camp build calls with the template's pole spot; here it's called directly (by reflection) on ground we shape.
 */
public class QaBountyCamp1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos POLE = new BlockPos(15, 2, 15);
	private static final BlockPos FIRE = new BlockPos(15, 2, 12);
	private static final BlockPos CHIEF = new BlockPos(15, 2, 17);

	/** A bounty on a Warlord (a band of five, no mount), not posted: only what the camp needs. */
	private static Bounties.Bounty warlord(GameTestHelper helper, BlockPos spot) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("warlord");
		helper.assertTrue(Bounties.quirks().containsKey(id), "the warlord quirk didn't load: " + Bounties.quirks().keySet());
		return new Bounties.Bounty(UUID.randomUUID(), helper.absolutePos(new BlockPos(1, 2, 1)), 2, 3, id,
			new ArcState.ArcPlace("hideout", spot, List.of(Bounties.HIDEOUT.toString()), 0), Chronicle.day(level), 15, false);
	}

	private static void banner(GameTestHelper helper, Bounties.Bounty b, BlockPos at) {
		try {
			Method m = Bounties.class.getDeclaredMethod("banner", ServerLevel.class, Bounties.Bounty.class, BlockPos.class);
			m.setAccessible(true);
			m.invoke(null, helper.getLevel(), b, at);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Bounties.banner(ServerLevel, Bounty, BlockPos) not callable: " + e, e);
		}
	}

	/** Stone from y = 2 to 5 over every column within {@code ring} (Chebyshev) of the pole spot: no open ground there. */
	private static void block(GameTestHelper helper, int ring) {
		for (int dx = -ring; dx <= ring; dx++) {
			for (int dz = -ring; dz <= ring; dz++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(POLE.getX() + dx, y, POLE.getZ() + dz), Blocks.STONE);
				}
			}
		}
	}

	/** Every spruce fence within 6 of the pole spot (relative positions). */
	private static List<BlockPos> fences(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				for (int y = 1; y <= 8; y++) {
					BlockPos p = new BlockPos(POLE.getX() + dx, y, POLE.getZ() + dz);
					if (helper.getBlockState(p).is(Blocks.SPRUCE_FENCE)) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	/** With rings 0 to 2 round the pole's spot all solid, the pole and his banner stand on the open ground of ring 3. */
	//$ gametest_ticks_batch AREA '20' '"qa_bounty_pole_1108d"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qa_bounty_pole_1108d")
	public void withRingsZeroToTwoBlockedThePoleStandsInRingThree(GameTestHelper helper) {
		Leftovers.clear(helper);
		block(helper, 2);
		Bounties.Bounty b = warlord(helper, helper.absolutePos(POLE));
		banner(helper, b, helper.absolutePos(POLE));
		List<BlockPos> fences = fences(helper);
		helper.assertTrue(fences.size() == 1, "one pole expected with rings 0-2 blocked and ring 3 open; poles at " + fences);
		BlockPos pole = fences.get(0);
		int ring = Math.max(Math.abs(pole.getX() - POLE.getX()), Math.abs(pole.getZ() - POLE.getZ()));
		helper.assertTrue(ring == 3, "the pole stands in ring " + ring + " (at " + pole + "), not ring 3");
		helper.assertTrue(pole.getY() == 2, "the pole isn't on the ground (y = 2): " + pole);
		helper.assertTrue(helper.getBlockState(pole.above()).is(BlockTags.BANNERS), "no banner on the pole: " + helper.getBlockState(pole.above()));
		helper.succeed();
	}

	/** With rings 0 to 3 all solid, no pole is put up farther out (ring 4 is open but not "by the fire"). */
	//$ gametest_ticks_batch AREA '20' '"qa_bounty_pole_far_1108d"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qa_bounty_pole_far_1108d")
	public void withRingsZeroToThreeBlockedNoPoleGoesFartherOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		block(helper, 3);
		Bounties.Bounty b = warlord(helper, helper.absolutePos(POLE));
		banner(helper, b, helper.absolutePos(POLE));
		helper.assertTrue(fences(helper).isEmpty(), "a pole went up beyond ring 3: " + fences(helper));
		helper.succeed();
	}

	/**
	 * The captain at the chief's spot and his five men round the fire each stand in the middle of a block (x and z at .5,
	 * feet on the block's floor), not on its edge or corner, and on open ground within 3 of the fire.
	 */
	//$ gametest_ticks_batch AREA '20' '"qa_bounty_band_spot_1108d"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qa_bounty_band_spot_1108d")
	public void theCaptainAndHisBandStandCenteredOnTheirBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = warlord(helper, helper.absolutePos(CHIEF));
		Leftovers.after(helper, () -> level.getEntitiesOfClass(Mob.class, new AABB(helper.absolutePos(FIRE)).inflate(16),
			m -> m.getTags().contains(Bounties.TAG)).forEach(Mob::discard));
		helper.assertTrue(Bounties.raise(level, b, helper.absolutePos(CHIEF), helper.absolutePos(FIRE)), "he didn't stand");
		helper.assertTrue(b.band.size() == 5, "a Warlord's band of 5: " + b.band.size());
		List<UUID> all = new ArrayList<>(b.band);
		all.add(0, b.captain);
		BlockPos fire = helper.absolutePos(FIRE);
		for (UUID u : all) {
			Mob mob = level.getEntity(u) instanceof Mob m ? m : null;
			helper.assertTrue(mob != null && mob.isAlive(), "a man of the band isn't in the world: " + u);
			String who = (u.equals(b.captain) ? "the captain " : "bandit ") + mob.getType().getDescriptionId() + " at " + mob.position();
			double fx = mob.getX() - Math.floor(mob.getX());
			double fz = mob.getZ() - Math.floor(mob.getZ());
			helper.assertTrue(Math.abs(fx - 0.5) < 1e-6 && Math.abs(fz - 0.5) < 1e-6, who + " isn't centered on his block (x, z fractions " + fx + ", " + fz + ")");
			helper.assertTrue(mob.getY() == Math.floor(mob.getY()), who + " isn't standing on a block's floor");
			BlockPos at = mob.blockPosition();
			helper.assertTrue(level.getBlockState(at).getCollisionShape(level, at).isEmpty(), who + " stands inside " + level.getBlockState(at));
			if (!u.equals(b.captain)) {
				int d = Math.max(Math.abs(at.getX() - fire.getX()), Math.abs(at.getZ() - fire.getZ()));
				helper.assertTrue(d <= 3, who + " is " + d + " blocks from the fire, not round it");
			}
		}
		helper.succeed();
	}
}
