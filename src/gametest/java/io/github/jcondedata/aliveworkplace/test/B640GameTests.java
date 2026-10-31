package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B640 (the owner's screenshot, 2026-10-27): the Terrace's bay windows looked broken, their glass panes standing as thin
 * strips with gaps beside them. The bays had nothing at their sides (air, and a door hood's stair bracket), so each
 * pair of panes joined only each other and stopped at the middle of its outer blocks: one block of glass in a two-block
 * opening, open at both ends. The bays now have a stone pier on each side for the glass to join. Here a builder puts
 * the Terrace up: every front pane joins its neighbours on both sides, and every pane, fence and wall in the build is
 * joined as the game itself would join it (nothing kept from the drawing's own connections); and the same with the
 * build saved and loaded back while its front windows are going in.
 */
public class B640GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** The Terrace's front glass (blueprint positions): the two bays (z 0) and the two dormers (z 1). */
	private static final List<BlockPos> FRONT = new ArrayList<>();

	static {
		for (int x : new int[] {4, 5, 7, 8}) {
			FRONT.add(new BlockPos(x, 2, 0));
			FRONT.add(new BlockPos(x, 3, 0));
		}
		for (int x : new int[] {3, 4, 8, 9}) {
			FRONT.add(new BlockPos(x, 10, 1));
			FRONT.add(new BlockPos(x, 11, 1));
		}
	}

	/** A builder puts the Terrace up from bare ground: the front windows are whole panes, joined as the game joins them. */
	//$ gametest_ticks_batch AREA '60000' '"b640TerracePanes"'
	@GameTest(template = AREA, timeoutTicks = 60000, batch = "b640TerracePanes")
	public void b640TheTerracesFrontPanesJoinTheirNeighbours(GameTestHelper helper) {
		Build b = start(helper);
		helper.succeedWhen(() -> assertJoined(helper, b));
	}

	/** As above, the build saved and loaded back (site and builder) once some of the front glass is in and some isn't. */
	//$ gametest_ticks_batch AREA '60000' '"b640TerracePanesReload"'
	@GameTest(template = AREA, timeoutTicks = 60000, batch = "b640TerracePanesReload")
	public void b640TheFrontPanesJoinAfterASaveAndReloadMidWindows(GameTestHelper helper) {
		Build b = start(helper);
		ServerLevel level = helper.getLevel();
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (reloaded[0] || BuildSiteManager.get(level).get(b.id()) == null) {
				return;
			}
			long in = FRONT.stream().filter(p -> level.getBlockState(b.origin().offset(p)).is(Blocks.GLASS_PANE)).count();
			if (in >= 1 && in < FRONT.size()) {
				reloaded[0] = true;
				b.builder()[0] = reload(helper, level, b.builder()[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(b.id()) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while the front glass was going in");
			assertJoined(helper, b);
		});
	}

	/** The drawing a Terrace built before B640 was built from, as the mod ships it for Refresh to the latest look. */
	private static final String BEFORE = "11b48d517";

	/**
	 * A Terrace already standing as the drawing before B640 drew it is offered Refresh to the latest look from that
	 * drawing, and the refresh would put the new piers in (blocks), so the owner's Terraces get whole windows too.
	 */
	//$ gametest_ticks_batch AREA '100' '"b640OldTerraceRefresh"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b640OldTerraceRefresh")
	public void b640AnOldTerraceIsOfferedTheNewBays(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		io.github.jcondedata.aliveworkplace.blueprint.Blueprint old = io.github.jcondedata.aliveworkplace.build.Refreshes.olderDrawing(level.getServer(),
			StarterBlueprints.TERRACE.id(), BEFORE).orElseThrow(() -> new IllegalStateException("the Terrace's drawing before B640 isn't shipped"));
		for (java.util.Map.Entry<BlockPos, BlockState> e : io.github.jcondedata.aliveworkplace.build.BuildPlan.create(old, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.TERRACE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildSiteManager.Finished terrace = BuildSiteManager.get(level).finishedIn(level).stream().filter(f -> f.placement().equals(placement))
			.findFirst().orElseThrow();
		var change = io.github.jcondedata.aliveworkplace.build.Refreshes.change(level, terrace);
		helper.assertTrue(change.isPresent(), "an old Terrace isn't offered Refresh to the latest look");
		helper.assertTrue(BEFORE.equals(change.get().from()), "refreshed from " + change.get().from() + ", not the drawing before B640");
		int blocks = change.get().added()[io.github.jcondedata.aliveworkplace.build.Refreshes.BLOCK];
		helper.assertTrue(blocks >= 12, "the refresh puts in " + blocks + " blocks, fewer than the bays' piers");
		helper.succeed();
	}

	private record Build(UUID id, BlockPos origin, BoundingBox box, Villager[] builder) {
	}

	private static Build start(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, StarterBlueprints.TERRACE.id(), placement);
		helper.assertTrue(site != null && site.plan(level) != null, "the Terrace didn't start");
		return new Build(site.id(), helper.absolutePos(ORIGIN), BlueprintOutline.bounds(placement, StarterBlueprints.TERRACE.size()), new Villager[] {villager});
	}

	private static void assertJoined(GameTestHelper helper, Build b) {
		ServerLevel level = helper.getLevel();
		BuildSite site = BuildSiteManager.get(level).get(b.id());
		helper.assertTrue(site == null, site == null ? "" : "still building: " + site.stage() + " " + site.status() + " step " + site.cursor() + ", "
			+ site.placed() + " placed, " + site.detail() + ", builder at " + b.builder()[0].blockPosition().subtract(b.origin()).toShortString()
			+ (b.builder()[0].isAlive() ? "" : " (dead)"));
		for (BlockPos rel : FRONT) {
			BlockPos at = b.origin().offset(rel);
			BlockState pane = level.getBlockState(at);
			helper.assertTrue(pane.is(Blocks.GLASS_PANE), "no glass pane at " + rel.toShortString() + " of the Terrace: " + pane);
			helper.assertTrue(pane.getValue(CrossCollisionBlock.WEST) && pane.getValue(CrossCollisionBlock.EAST),
				"B640: the Terrace's front pane at " + rel.toShortString() + " doesn't join both sides (west "
					+ pane.getValue(CrossCollisionBlock.WEST) + " " + level.getBlockState(at.west()).getBlock() + ", east "
					+ pane.getValue(CrossCollisionBlock.EAST) + " " + level.getBlockState(at.east()).getBlock() + ")");
		}
		int checked = 0;
		for (BlockPos p : BlockPos.betweenClosed(b.box().minX(), b.box().minY(), b.box().minZ(), b.box().maxX(), b.box().maxY(), b.box().maxZ())) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock) {
				checked++;
				BlockState vanilla = Block.updateFromNeighbourShapes(state, level, p);
				helper.assertTrue(vanilla == state, "B640: " + state + " at " + p.subtract(b.origin()).toShortString()
					+ " of the Terrace isn't joined as the game joins it: " + vanilla);
			}
		}
		helper.assertTrue(checked >= FRONT.size(), "only " + checked + " panes, fences and walls in the Terrace");	}

	/**
	 * As a save and a load of the world would: every build site from its save (the manager replaced, so each site's plan
	 * is worked out again from the ground as it stands), and the builder from its save. Returns the loaded builder.
	 */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
