package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-08) for the Bandit King's fort (ROADMAP 31.15: "on fit ground 110 to 150 blocks from the hall:
 * the bandit camps' test of the ground widened to the fort's size, nothing a player built in the way"; "four bandits
 * in the yard, a pillager on each watchtower"; "where a man keeps to is written in his tags, so a chunk reload or a
 * restart doesn't let him wander"). A local mutation run of BanditFort (lane-d-1108-0108, 14 mutants against the 16
 * fort and King classes) let 7 survive. These kill the 6 that matter: natural ground with copper ore in it refused,
 * a block a player set (of plain dirt) not refusing the fort, the 32-block height limit between the hall and the fort
 * moved either way, a yard man set a block off his spot, and a man saved with an older tag (no radius) breaking the
 * fort's tick. (The last survivor, the footprint's loaded-chunk corner check moved two blocks in, needs unloaded
 * chunks next to a test area.)
 */
public class QaBanditFortGround1108dGameTests implements FabricGameTest {
	/** 30 blocks tall, grass at y 3 over dirt (as BanditFortGameTests uses it). */
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);

	private static void begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2); // open sky, so the heightmap finds the grass
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	/**
	 * Ore showing in the ground is natural ground: the fort fits with copper ore at the surface in its footprint, and
	 * with iron ore, and with coal ore, each alone. (Kills the last {@code ||} of BanditFort.natural turned into
	 * {@code &&}, which refused a copper ore that isn't also iron ore.)
	 */
	//$ gametest_ticks_batch AREA '40' '"qaFortOre1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaFortOre1108d")
	public void theFortFitsOverCopperIronOrCoalOreInItsGround(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(MIDDLE);
		helper.assertTrue(BanditFort.fits(level, ground), "setup: flat grass doesn't fit the fort");
		BlockPos spot = new BlockPos(20, 3, 20);
		for (Block ore : List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE)) {
			helper.setBlock(spot, ore);
			helper.assertTrue(BanditFort.fits(level, ground), "the fort doesn't fit over natural ground with " + ore.getName().getString() + " showing in it");
		}
		helper.setBlock(spot, Blocks.GRASS_BLOCK);
		helper.succeed();
	}

	/**
	 * A block a player set in the fort's box refuses it even when it is plain dirt (natural to look at): marked as the
	 * player's (27.19), it doesn't fit; the same dirt not marked fits. (Kills BanditFort.fits' player-built check
	 * returning true.)
	 */
	//$ gametest_ticks_batch AREA '40' '"qaFortPlayersDirt1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaFortPlayersDirt1108d")
	public void aPlayersDirtBlockInTheFortsBoxRefusesIt(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(MIDDLE);
		BlockPos dirt = new BlockPos(9, 4, 21);
		helper.setBlock(dirt, Blocks.DIRT);
		helper.assertTrue(BanditFort.fits(level, ground), "control: a dirt block nobody set refuses the fort");
		PlayerBuilt.get(level).mark(helper.absolutePos(dirt));
		helper.assertFalse(BanditFort.fits(level, ground), "the fort fits over a dirt block a player set in its box");
		PlayerBuilt.get(level).forget(net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(dirt), helper.absolutePos(dirt)));
		helper.succeed();
	}

	/**
	 * The fort's ground may be at most 32 blocks above or below the hall: asked for a site at the hall's own column (0
	 * blocks off), ground exactly 32 below the hall is taken, 33 below is not. (Kills the {@code <= 32} turned into
	 * {@code < 32} and into {@code <= 33}. Only the hall-above side: the test world's floor is too near the bottom of the
	 * world for a hall 32 below it, and the limit is the same either way.)
	 */
	//$ gametest_ticks_batch AREA '40' '"qaFortHeight1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaFortHeight1108d")
	public void theFortsGroundIsAtMost32BlocksBelowTheHall(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BlockPos ground = helper.absolutePos(MIDDLE);
		for (int dy : new int[] {-32}) {
			BlockPos site = BanditFort.site(level, ground.below(dy), 0, 0, RandomSource.create(7));
			helper.assertTrue(ground.equals(site), "ground " + -dy + " blocks below the hall (the limit) wasn't taken: " + site);
		}
		for (int dy : new int[] {-33}) {
			BlockPos site = BanditFort.site(level, ground.below(dy), 0, 0, RandomSource.create(7));
			helper.assertTrue(site == null, "ground " + -dy + " blocks below the hall (past 32) was taken: " + site);
		}
		helper.succeed();
	}

	/**
	 * Each stage's men come where the blueprint puts them: the four yard bandits on their four spots in the yard, and a
	 * pillager on each watchtower's top (the blueprint's (2, 6, 2) and (26, 6, 2)). (Kills the man's z set half a block
	 * the wrong way, a block off his spot.)
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFortMenSpots1108d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFortMenSpots1108d")
	public void eachStagesMenStandOnTheirSpots(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BanditFort.Fort fort = BanditFort.at(helper.absolutePos(MIDDLE), Rotation.NONE);
		helper.assertTrue(BanditFort.place(level, fort, 0, false), "stage 1 missing");
		List<BlockPos> yard = List.of(new BlockPos(12, 1, 8), new BlockPos(16, 1, 8), new BlockPos(11, 1, 14), new BlockPos(17, 1, 14));
		List<Mob> men = BanditFort.men(level, fort.yard(), 24);
		helper.assertTrue(men.size() == 4, "stage 1's men: " + men.size());
		for (BlockPos local : yard) {
			BlockPos at = fort.at(local);
			helper.assertTrue(men.stream().anyMatch(m -> m.blockPosition().equals(at)), "no yard bandit on his spot " + local + " (" + at + "); the men at "
				+ men.stream().map(Mob::blockPosition).toList());
		}
		helper.assertTrue(BanditFort.place(level, fort, 1, false), "stage 2 missing");
		List<Mob> all = BanditFort.men(level, fort.yard(), 24);
		for (BlockPos local : List.of(new BlockPos(2, 6, 2), new BlockPos(26, 6, 2))) {
			BlockPos at = fort.at(local);
			helper.assertTrue(all.stream().anyMatch(m -> m instanceof Pillager && m.blockPosition().equals(at)), "no pillager on the watchtower at " + local + " (" + at
				+ "); the men at " + all.stream().map(Mob::blockPosition).toList());
		}
		for (Mob m : all) {
			m.discard();
		}
		helper.succeed();
	}

	/**
	 * A fort man saved with an older home tag that has no radius (just "aliveworkplace_fort_home.&lt;where&gt;") keeps to
	 * the fort's 17 blocks once the fort's tick finds he lost his restriction (a chunk reload); one whose tag says 2 keeps
	 * to 2. (Kills the radius parsed with {@code >= 1} parts, which threw on the old tag and broke the tick.)
	 */
	//$ gametest_ticks_batch AREA '40' '"qaFortOldTag1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaFortOldTag1108d")
	public void aFortManWithAnOlderTagWithNoRadiusKeepsToTheFort(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		BlockPos home = helper.absolutePos(MIDDLE.above());
		Pillager old = man(helper, new BlockPos(14, 4, 14), "aliveworkplace_fort_home." + home.asLong());
		Pillager tower = man(helper, new BlockPos(16, 4, 14), "aliveworkplace_fort_home." + home.asLong() + ".2");
		helper.assertFalse(old.hasRestriction() || tower.hasRestriction(), "setup: restricted before the tick");
		BanditFort.tick(level);
		helper.assertTrue(old.hasRestriction() && old.getRestrictCenter().equals(home) && old.getRestrictRadius() == BanditFort.KEEP,
			"a man with an older tag (no radius) keeps to " + old.getRestrictCenter() + " within " + old.getRestrictRadius() + ", not the fort's " + home + " within " + BanditFort.KEEP);
		helper.assertTrue(tower.hasRestriction() && tower.getRestrictCenter().equals(home) && tower.getRestrictRadius() == 2,
			"a man whose tag says 2 keeps to " + tower.getRestrictCenter() + " within " + tower.getRestrictRadius());
		old.discard();
		tower.discard();
		BanditFort.tick(level); // forgets the two
		helper.succeed();
	}

	private static Pillager man(GameTestHelper helper, BlockPos at, String homeTag) {
		ServerLevel level = helper.getLevel();
		Pillager p = EntityType.PILLAGER.create(level);
		helper.assertTrue(p != null, "setup: no pillager");
		BlockPos abs = helper.absolutePos(at);
		p.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		p.setNoAi(true);
		p.setPersistenceRequired();
		p.addTag(BanditFort.TAG);
		p.addTag(homeTag);
		level.addFreshEntity(p);
		Leftovers.after(helper, p::discard);
		return p;
	}
}
