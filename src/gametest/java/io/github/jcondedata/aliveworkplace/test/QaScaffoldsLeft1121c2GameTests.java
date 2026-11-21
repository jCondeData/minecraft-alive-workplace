package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1121-2153), from B1322 and docs/wiki/scaffolding.md ("When the build ends any other way"): a site removed
 * while a scaffold column stands in an unloaded chunk has that column taken down once the chunk loads. What
 * B1322GameTests and QaScaffoldsUnloaded1116bGameTests leave out: the builder dying (one of the wiki's named ways); a
 * cell of the column a player filled with his own block before the build ended (only scaffolding comes down, his block
 * stays; a block put there while the chunk is unloaded can't happen, as the takedown runs on the tick it loads); and
 * free materials on (taken down for nothing: none added to the chest, none dropped). The column is put 4096 blocks
 * off so its chunk really unloads.
 */
public class QaScaffoldsLeft1121c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int FAR = 4096;
	private static final int COLUMN = 4;
	private static final int SETTLE = 40;

	private static void log(String line) {
		AliveWorkplace.LOG.info("[qa-scaffolds-left-1121c2] {}", line);
	}

	private interface Before {
		/** Before the far chunk unloads: change the column (still loaded). */
		void apply(ServerLevel level, List<BlockPos> column, Villager builder);
	}

	private interface End {
		void end(ServerLevel level, BuildSite site, Villager builder);
	}

	private interface Check {
		void check(ServerLevel level, List<BlockPos> column, int chestBefore, int chestAfter);
	}

	private static int scaffoldingIn(GameTestHelper helper) {
		if (!(helper.getBlockEntity(CHEST) instanceof ChestBlockEntity chest)) {
			throw new GameTestAssertException("no chest by the bench");
		}
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(Items.SCAFFOLDING)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static void run(GameTestHelper helper, boolean freeMaterials, Before before, End end, String how, Check check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		var free = level.getGameRules().getRule(ModGameRules.FREE_MATERIALS);
		boolean was = free.get();
		free.set(freeMaterials, level.getServer());
		Leftovers.after(helper, () -> free.set(was, level.getServer()));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + PILLAR + " didn't start");
		}
		UUID id = site.id();
		BlockPos foot = helper.absolutePos(new BlockPos(FAR, 2, 4));
		level.setBlockAndUpdate(foot.below(), Blocks.STONE.defaultBlockState());
		List<BlockPos> column = new ArrayList<>();
		for (int i = 0; i < COLUMN; i++) {
			BlockPos p = foot.above(i);
			level.setBlockAndUpdate(p, Blocks.SCAFFOLDING.defaultBlockState());
			site.addScaffold(p);
			column.add(p);
		}
		before.apply(level, column, villager);
		int[] phase = {0};
		int[] ticks = {0};
		int[] chestBefore = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (phase[0] == 0 && !level.isLoaded(foot)) {
				chestBefore[0] = scaffoldingIn(helper);
				end.end(level, site, villager);
				helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the site wasn't removed when " + how);
				log(how + " after " + ticks[0] + " ticks with the far column unloaded; loading it again");
				level.getChunk(foot);
				phase[0] = 1;
				ticks[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 1, "the far chunk didn't unload yet (" + ticks[0] + " ticks)");
			helper.assertTrue(ticks[0] >= SETTLE, "settling: " + ticks[0]);
			level.getChunk(foot);
			for (BlockPos p : column) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					throw new GameTestAssertException("the scaffold at " + p + " still stands " + ticks[0] + " ticks after its chunk loaded (" + how + ")");
				}
			}
			check.check(level, column, chestBefore[0], scaffoldingIn(helper));
			log(how + ": none standing once the chunk loaded");
		});
	}

	/**
	 * The builder dies (a wiki-named way the site ends) with his column unloaded: it comes down when its chunk loads. He
	 * is held still by the bench (no AI): left to work he walks off towards the far column and unloads with it.
	 */
	//$ gametest_ticks_batch BIG '2000' '"qaScaffoldsLeft1121c2Died"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaScaffoldsLeft1121c2Died")
	public void theBuilderDiesWithAColumnUnloaded(GameTestHelper helper) {
		run(helper, false, (level, column, builder) -> builder.setNoAi(true), (level, site, builder) -> {
			boolean removed = builder.isRemoved();
			float health = builder.getHealth();
			boolean hurt = builder.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
			helper.assertTrue(builder.isDeadOrDying(), "the builder didn't die: removed " + removed + " " + builder.getRemovalReason() + ", health " + health
				+ ", hurt " + hurt + ", invulnerable " + builder.isInvulnerable() + ", at " + builder.blockPosition());
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				// the site ends with its builder, now or on the next tick
				throw new GameTestAssertException("the builder died but his site " + site.structure() + " is still open");
			}
		}, "the builder died", (level, column, before, after) ->
			helper.assertValueEqual(after - before, COLUMN, "scaffolding back in the chest by the bench after the builder died"));
	}

	/** A player's block in one cell of the column: the scaffolding comes down, his block stays. */
	//$ gametest_ticks_batch BIG '2000' '"qaScaffoldsLeft1121c2Player"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaScaffoldsLeft1121c2Player")
	public void aPlayersBlockInTheColumnStays(GameTestHelper helper) {
		BlockPos[] mine = {null};
		run(helper, false, (level, column, builder) -> {
			mine[0] = column.get(1);
			level.setBlockAndUpdate(mine[0], Blocks.OAK_PLANKS.defaultBlockState());
		}, (level, site, builder) -> Builders.cancel(level, site), "canceled", (level, column, before, after) -> {
			helper.assertTrue(level.getBlockState(mine[0]).is(Blocks.OAK_PLANKS), "the player's planks in the column were taken: " + level.getBlockState(mine[0]));
			helper.assertValueEqual(after - before, COLUMN - 1, "scaffolding back in the chest (3 scaffolds, not the planks)");
			boolean planksDropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(mine[0]).inflate(4), e -> e.getItem().is(Items.OAK_PLANKS)).isEmpty();
			helper.assertFalse(planksDropped, "the player's planks were broken and dropped");
		});
	}

	/** Free materials on: the far column comes down for nothing, none added to the chest and none dropped by it. */
	//$ gametest_ticks_batch BIG '2000' '"qaScaffoldsLeft1121c2Free"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaScaffoldsLeft1121c2Free")
	public void freeMaterialsTakeTheColumnDownForNothing(GameTestHelper helper) {
		run(helper, true, (level, column, builder) -> { }, (level, site, builder) -> Builders.cancel(level, site), "canceled with free materials",
			(level, column, before, after) -> {
				helper.assertValueEqual(after, before, "scaffolding in the chest with free materials on");
				boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(column.get(0)).inflate(6), e -> e.getItem().is(Items.SCAFFOLDING)).isEmpty();
				helper.assertFalse(dropped, "scaffolding was dropped by the column with free materials on");
			});
	}
}
