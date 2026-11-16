package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * B1322: a build ended (canceled, its site removed) while one of its scaffold columns stood in an unloaded chunk left
 * that column standing for good: {@code Scaffolds.dismantle} skipped it and struck it off the site. Now it is
 * remembered by the dimension's sites (saved) and taken down once its chunk loads, into the chests by the bench like
 * the rest. The column is put 4096 blocks off so its chunk really unloads.
 */
public class B1322GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int FAR = 4096;
	private static final int COLUMN = 3;
	private static final int SETTLE = 20;

	/** Canceled with a column in an unloaded chunk: it comes down once the chunk loads, into the chests by the bench. */
	//$ gametest_ticks_batch BIG '2000' '"b1322Cancel"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b1322Cancel")
	public void canceledColumnInAnUnloadedChunkComesDownWhenItLoads(GameTestHelper helper) {
		farColumn(helper, false);
	}

	/** The same with the world saved and loaded again between the cancel and the chunk loading. */
	//$ gametest_ticks_batch BIG '2000' '"b1322Reload"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "b1322Reload")
	public void leftColumnSurvivesASaveAndComesDownWhenItsChunkLoads(GameTestHelper helper) {
		farColumn(helper, true);
	}

	/** All the scaffolding in loaded chunks: taken down at once into the chests, nothing left waiting (as before). */
	//$ gametest_ticks_batch BIG '400' '"b1322Loaded"'
	@GameTest(template = BIG, timeoutTicks = 400, batch = "b1322Loaded")
	public void loadedScaffoldingComesDownAtOnceAsBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BuildSite site = start(helper);
		List<BlockPos> column = column(level, helper.absolutePos(new BlockPos(6, 2, 2)), site);
		Builders.cancel(level, site);
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the site wasn't canceled");
		for (BlockPos p : column) {
			helper.assertFalse(level.getBlockState(p).is(Blocks.SCAFFOLDING), "the scaffold at " + p + " is still standing after the cancel");
			helper.assertFalse(BuildSiteManager.get(level).leftScaffolds().containsKey(p), "the loaded scaffold at " + p + " was left waiting for its chunk");
		}
		helper.assertValueEqual(scaffoldingIn(helper), COLUMN, "scaffolding in the chest by the bench");
		helper.succeed();
	}

	private static BuildSite start(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + PILLAR + " didn't start");
		}
		return site;
	}

	/** A column of scaffolding on stone at {@code foot}, recorded on the site as its builder would. */
	private static List<BlockPos> column(ServerLevel level, BlockPos foot, BuildSite site) {
		level.setBlockAndUpdate(foot.below(), Blocks.STONE.defaultBlockState());
		List<BlockPos> column = new ArrayList<>();
		for (int i = 0; i < COLUMN; i++) {
			BlockPos p = foot.above(i);
			level.setBlockAndUpdate(p, Blocks.SCAFFOLDING.defaultBlockState());
			site.addScaffold(p);
			column.add(p);
		}
		return column;
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

	private static void farColumn(GameTestHelper helper, boolean reload) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BuildSite site = start(helper);
		BlockPos foot = helper.absolutePos(new BlockPos(FAR, 2, reload ? 6 : 2));
		List<BlockPos> column = column(level, foot, site);
		BlockPos near = helper.absolutePos(new BlockPos(6, 2, 2));
		level.setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(near, Blocks.SCAFFOLDING.defaultBlockState());
		site.addScaffold(near);
		int[] phase = {0}; // 0 waiting for the far chunk to unload, 1 canceled and loaded again
		int[] ticks = {0};
		int[] inChest = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (phase[0] != 0 || level.isLoaded(foot)) {
				return;
			}
			Builders.cancel(level, site);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the site wasn't canceled");
			helper.assertFalse(level.getBlockState(near).is(Blocks.SCAFFOLDING), "the scaffold by the bench is still standing after the cancel");
			inChest[0] = scaffoldingIn(helper); // (the builder may have taken the one by the bench down itself already)
			for (BlockPos p : column) {
				helper.assertTrue(BuildSiteManager.get(level).leftScaffolds().containsKey(p), "the unloaded scaffold at " + p + " wasn't remembered");
			}
			if (reload) { // the world saved and loaded again: what is left waiting survives
				HolderLookup.Provider registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME,
					BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				for (BlockPos p : column) {
					helper.assertTrue(BuildSiteManager.get(level).leftScaffolds().containsKey(p), "the scaffold left at " + p + " was lost in the save");
				}
			}
			level.getChunk(foot); // a player walks over: the chunk loads again
			phase[0] = 1;
			ticks[0] = 0;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 1, "the far chunk didn't unload yet (" + ticks[0] + " ticks)");
			helper.assertTrue(ticks[0] >= SETTLE, "settling: " + ticks[0]);
			level.getChunk(foot);
			for (BlockPos p : column) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					throw new GameTestAssertException("the scaffold at " + p + " is still standing " + ticks[0] + " ticks after its chunk loaded again");
				}
				helper.assertFalse(BuildSiteManager.get(level).leftScaffolds().containsKey(p), "the scaffold at " + p + " is still waiting after it came down");
			}
			helper.assertValueEqual(scaffoldingIn(helper), inChest[0] + COLUMN, "scaffolding in the chest by the bench once the far column came down");
		});
	}
}
