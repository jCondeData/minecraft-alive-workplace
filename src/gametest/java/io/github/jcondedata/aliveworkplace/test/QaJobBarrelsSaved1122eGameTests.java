package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-22), B1463 and its regressions B1476 and B1477 from their specs, with the fisherman cap on (26.13,
 * one fisherman allowed, none yet; B1484: a player's game has it on). B1463: a jobless villager's job search skips a
 * barrel within supply reach (8 across, 4 up or down) of a FREE workstation of another job that a jobless villager would
 * take. B1476: the Fisher's Hut II hires its Fisherman at its barrel although its own smoker is in reach
 * (fishers_hut.md: "a jobless villager by it [the barrel] becomes a Fisherman"). B1477: a block no villager takes by
 * himself (jukebox, beehive, bee nest, the mod's mailbox) or a workstation no villager can actually take never stops the
 * fisherman; vanilla takes a workstation from its 4 sides or its top.
 * <p>
 * Two cases where the fix disagrees with these words fail and live in {@link QaJobBarrelsSaved1122eBugTests}.
 * <p>
 * Every test: its own batch, {@code Leftovers.clear} first ({@link QaJobBarrels1122cGameTests#setup}: random jobs and
 * the worker cap off, the day held at morning), a jobless villager beside the barrel.
 */
public class QaJobBarrelsSaved1122eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BARREL = new BlockPos(10, 2, 10);
	static final BlockPos BESIDE = new BlockPos(10, 2, 11);
	/** Ticks a Fisherman must stay one at his barrel after the second reload. */
	private static final int KEEP = 200;

	// ---- helpers -------------------------------------------------------------------------------------------------------

	static Villager jobless(GameTestHelper helper, BlockPos abs) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	private static BlockPos site(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
	}

	private static String where(GameTestHelper helper, Villager v) {
		BlockPos s = site(v);
		return "job " + v.getVillagerData().getProfession() + ", site " + (s == null ? "none" : s.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()
			+ " " + helper.getLevel().getBlockState(s).getBlock().getName().getString()) + ", at "
			+ v.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/** As a save and a load of the world do to a villager: saved, removed and loaded back from its save. */
	private static Villager reload(GameTestHelper helper, Villager old) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		if (!old.save(tag)) {
			throw new GameTestAssertException("(setup) the villager wasn't saved");
		}
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (!(back instanceof Villager v)) {
			throw new GameTestAssertException("the villager didn't load back as a villager: " + back);
		}
		level.addFreshEntity(v);
		return v;
	}

	private static boolean fishermanAt(Villager v, BlockPos barrel) {
		return v.getVillagerData().getProfession() == VillagerProfession.FISHERMAN && barrel.equals(site(v));
	}

	/** Succeeds once {@code v} is a Fisherman whose job site is the barrel at absolute {@code barrel}. */
	static void becomesFishermanAt(GameTestHelper helper, Villager v, BlockPos barrel, String what) {
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), what + ": the villager died");
			helper.assertTrue(fishermanAt(v, barrel), what + ": no Fisherman at the barrel yet: " + where(helper, v));
			v.discard();
		});
	}

	/** Never a Fisherman; succeeds once {@code v} is a Librarian at the lectern at absolute {@code lectern}. */
	static void becomesLibrarianAt(GameTestHelper helper, Villager v, BlockPos lectern, String what) {
		helper.onEachTick(() -> helper.assertTrue(v.getVillagerData().getProfession() != VillagerProfession.FISHERMAN,
			what + ": took the barrel (the free lectern's stock): " + where(helper, v)));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), what + ": the villager died");
			helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && lectern.equals(site(v)),
				what + ": hasn't taken the lectern yet: " + where(helper, v));
			v.discard();
		});
	}

	/** A lone barrel, {@code block} two blocks east of it, the jobless villager beside the barrel: he becomes the Fisherman. */
	private static void barrelBy(GameTestHelper helper, Block block, String what) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(BARREL.offset(2, 0, 0), block);
		helper.assertTrue(helper.getBlockState(BARREL.offset(2, 0, 0)).is(block), "(setup) the " + what + " wasn't placed");
		Villager v = jobless(helper, helper.absolutePos(BESIDE));
		becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a " + what + " 2 blocks from the barrel");
	}

	/** An open cell beside {@code block} (absolute) a villager can stand in. */
	private static BlockPos besideOnFoot(GameTestHelper helper, BlockPos block) {
		ServerLevel level = helper.getLevel();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos c = block.relative(d);
			if (level.getBlockState(c).getCollisionShape(level, c).isEmpty() && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty()
				&& !level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()) {
				return c;
			}
		}
		throw new GameTestAssertException("(setup) no open cell beside " + block.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
	}

	// ---- (a) Fisher's Hut II across two reloads (B1476) ---------------------------------------------------------------

	/**
	 * The shipped Fisher's Hut II placed (its smoker within supply reach of the barrel at template (2, 1, 10)), a jobless
	 * villager beside the barrel. He is saved and loaded back mid-search (on the first tick, still jobless) and still becomes the hut's Fisherman at that barrel; then saved and loaded again, he stays its Fisherman at
	 * that barrel for 10 seconds (never a Butcher at the smoker, never jobless).
	 */
	//$ gametest_ticks_batch HUGE '1800' '"qaJobBarrelsSaved1122eHut2"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qaJobBarrelsSaved1122eHut2")
	public void qaTheFishersHutIIHiresItsFishermanAcrossTwoReloads(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaRebuiltWorkshops1121cLiveGameTests.place(helper, StarterBlueprints.FISHERS_HUT_2);
		BlockPos barrel = origin.offset(2, 1, 10);
		helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL), "(setup) the hut's barrel has moved");
		Villager[] who = {jobless(helper, besideOnFoot(helper, barrel))};
		int[] reloads = {0};
		long[] second = {-1};
		helper.onEachTick(() -> {
			Villager v = who[0];
			helper.assertTrue(v.isAlive(), "the villager died");
			if (reloads[0] == 0) {
				// beside the barrel he takes it within a tick or two of picking it: saved at the first look, still jobless
				helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.NONE, "(setup) took a job before the first reload: "
					+ where(helper, v));
				reloads[0] = 1;
				who[0] = reload(helper, v);
			} else if (reloads[0] == 1 && fishermanAt(v, barrel)) {
				reloads[0] = 2;
				who[0] = reload(helper, v);
				second[0] = level.getGameTime();
			} else if (reloads[0] == 2) {
				helper.assertTrue(fishermanAt(v, barrel), "after the second reload the hut's Fisherman left his barrel: " + where(helper, v)
					+ ", " + (level.getGameTime() - second[0]) + " ticks after it");
			}
		});
		helper.succeedWhen(() -> {
			Villager v = who[0];
			helper.assertTrue(reloads[0] >= 1, "never saved and loaded before taking a job: " + where(helper, v));
			helper.assertTrue(reloads[0] == 2, "fishers_hut_2: no Fisherman at the barrel after the first reload: " + where(helper, v));
			helper.assertTrue(level.getGameTime() - second[0] >= KEEP, "waiting out " + KEEP + " ticks after the second reload");
			helper.assertTrue(fishermanAt(v, barrel), "fishers_hut_2: not its Fisherman " + KEEP + " ticks after the second reload: " + where(helper, v));
			v.discard();
		});
	}

	// ---- (b-d) blocks no villager takes by himself (B1477) --------------------------------------------------------------

	/** A jukebox 2 blocks from a lone barrel (only by an item: a disc): the village still gets its Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eJukebox"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eJukebox")
	public void qaAJukeboxBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		barrelBy(helper, Blocks.JUKEBOX, "jukebox");
	}

	/** A beehive 2 blocks from a lone barrel: the village still gets its Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eBeehive"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eBeehive")
	public void qaABeehiveBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		barrelBy(helper, Blocks.BEEHIVE, "beehive");
	}

	/** A bee nest 2 blocks from a lone barrel (named in B1477 with the beehive): the village still gets its Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eBeeNest"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eBeeNest")
	public void qaABeeNestBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		barrelBy(helper, Blocks.BEE_NEST, "bee nest");
	}

	/** The mod's Mailbox 2 blocks from a lone barrel: the village still gets its Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eMailbox"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eMailbox")
	public void qaAMailboxBesideTheBarrelDoesntStopTheFisherman(GameTestHelper helper) {
		barrelBy(helper, ModBlocks.MAILBOX, "Mailbox");
	}

	// ---- (e) a free, reachable lectern at the edge of supply reach (B1463) -----------------------------------------------

	/** A lone barrel, a free lectern {@code dx} blocks east on flat ground, the villager beside the barrel. */
	private static void lecternAcross(GameTestHelper helper, int dx, boolean stock) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos lectern = BARREL.offset(dx, 0, 0);
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager v = jobless(helper, helper.absolutePos(BESIDE));
		String what = "a free lectern " + dx + " across";
		if (stock) {
			becomesLibrarianAt(helper, v, helper.absolutePos(lectern), what);
		} else {
			becomesFishermanAt(helper, v, helper.absolutePos(BARREL), what);
		}
	}

	/** 8 across, the last block of supply reach: the barrel is the lectern's stock; the villager takes the lectern. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eLectern8"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eLectern8")
	public void qaABarrelEightAcrossFromAFreeLecternIsItsStock(GameTestHelper helper) {
		lecternAcross(helper, 8, true);
	}

	/** 10 across, out of reach: a plain barrel right beside the villager; he becomes the Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eLectern10"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eLectern10")
	public void qaABarrelTenAcrossFromAFreeLecternMakesTheFisherman(GameTestHelper helper) {
		lecternAcross(helper, 10, false);
	}

	/**
	 * The barrel on a stone platform {@code h} blocks above a free lectern on the ground (4 across, 1 along), a staircase
	 * of full blocks down from the platform; the villager on the platform beside the barrel.
	 */
	private static void lecternBelow(GameTestHelper helper, int h, boolean stock) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		int top = 1 + h;
		for (int x = 8; x <= 12; x++) {
			for (int z = 10; z <= 12; z++) {
				for (int y = 2; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		for (int k = 1; k < h; k++) { // step k: one block lower for each block south
			for (int y = 2; y <= top - k; y++) {
				helper.setBlock(new BlockPos(10, y, 12 + k), Blocks.STONE);
			}
		}
		BlockPos barrel = new BlockPos(10, top + 1, 10);
		helper.setBlock(barrel, Blocks.BARREL);
		BlockPos lectern = new BlockPos(14, 2, 11);
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager v = jobless(helper, helper.absolutePos(new BlockPos(10, top + 1, 11)));
		String what = "a free lectern " + h + " below the barrel";
		if (stock) {
			becomesLibrarianAt(helper, v, helper.absolutePos(lectern), what);
		} else {
			becomesFishermanAt(helper, v, helper.absolutePos(barrel), what);
		}
	}

	/** 4 below, the last block of reach: the barrel is the lectern's stock; the villager walks down and takes the lectern. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eLecternDown4"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eLecternDown4")
	public void qaABarrelFourAboveAFreeLecternIsItsStock(GameTestHelper helper) {
		lecternBelow(helper, 4, true);
	}

	/** 6 below, out of reach: the barrel beside the villager makes him the Fisherman. */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eLecternDown6"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eLecternDown6")
	public void qaABarrelSixAboveAFreeLecternMakesTheFisherman(GameTestHelper helper) {
		lecternBelow(helper, 6, false);
	}

	// ---- (f) a lectern only its top can be reached from (B1477) -----------------------------------------------------------

	/**
	 * A lectern 4 blocks from the barrel with a stone block against each of its 4 sides and one on top: no side and no top
	 * to stand at, so no villager can ever take it; the
	 * barrel 4 blocks away is no free workstation's stock and the village gets its Fisherman.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eBoxed"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eBoxed")
	public void qaALecternBoxedInOnEverySideDoesntStopTheFisherman(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		helper.setBlock(BARREL, Blocks.BARREL);
		BlockPos lectern = BARREL.offset(4, 0, 0);
		helper.setBlock(lectern, Blocks.LECTERN);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(lectern.relative(d), Blocks.STONE);
		}
		helper.setBlock(lectern.above(), Blocks.STONE);
		Villager v = jobless(helper, helper.absolutePos(BESIDE));
		becomesFishermanAt(helper, v, helper.absolutePos(BARREL), "a lectern boxed in on 4 sides and the top");
	}

	/**
	 * Control for {@link QaJobBarrelsSaved1122eBugTests#qaALecternReachableOnlyFromItsTopIsTakenAndItsBarrelIsStock} (no barrel): the same walled-in lectern, open on top, a jobless villager beside where the
	 * barrel stood: he hops up, stands on its top and becomes its Librarian. So it is a free workstation a jobless villager
	 * can take, as B1477's status says vanilla takes one from its top.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaJobBarrelsSaved1122eTopControl"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaJobBarrelsSaved1122eTopControl")
	public void qaAJoblessVillagerTakesALecternReachableOnlyFromItsTop(GameTestHelper helper) {
		QaJobBarrels1122cGameTests.setup(helper, true);
		BlockPos lectern = BARREL.offset(4, 0, 0);
		helper.setBlock(lectern, Blocks.LECTERN);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			helper.setBlock(lectern.relative(d), Blocks.STONE);
		}
		Villager v = jobless(helper, helper.absolutePos(BESIDE));
		becomesLibrarianAt(helper, v, helper.absolutePos(lectern), "a lectern walled on 4 sides, open on top, no barrel");
	}
}
