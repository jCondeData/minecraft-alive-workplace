package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B308: the Colossus's lantern came out hollow on GitHub. Its glowstone stands in a one-block cell inside a ring of
 * panes, high on the raised hand; a villager that ended up in that cell was boxed in by the panes, and the builder could
 * neither push it out nor find a spot within four blocks to move it to (everything near was air or still to be built),
 * so the glowstone was given up. The test build is that lantern on a stone post: a plate 6 up, a ring of panes round
 * the glowstone over it. Everything stands but the glowstone; a villager is caged where it goes. The builder moves
 * whoever is in the way to a free spot further out, and lays the glowstone.
 */
public class LanternCageGameTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation CAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_lantern_cage");
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 6);
	/** The lower glowstone, in the drawing (1, 6, 1). */
	private static final BlockPos GLOW = ORIGIN.offset(1, 6, 1);

	/** The lantern built but for its glowstone; {@code caged} put in the cell the glowstone goes in. Returns the site. */
	private static BuildSite begin(GameTestHelper helper, boolean builderCaged) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		((Container) helper.getBlockEntity(new BlockPos(2, 2, 4))).setItem(0, new ItemStack(Items.GLOWSTONE, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, CAGE, at);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "the test lantern isn't loaded");
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().is(Blocks.GLOWSTONE)) {
					level.setBlockAndUpdate(s.pos(), s.state()); // all of it stands but the glowstone
				}
			}
		}
		if (builderCaged) {
			// its glowstone in hand already, in the cell it is to lay it in
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
			Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
			BlockPos cell = helper.absolutePos(GLOW);
			builder.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
		} else {
			helper.spawn(EntityType.VILLAGER, GLOW);
		}
		return site;
	}

	private static void lit(GameTestHelper helper, BuildSite site) {
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(GLOW)).is(Blocks.GLOWSTONE), "no glowstone in the lantern (site "
				+ (BuildSiteManager.get(helper.getLevel()).get(site.id()) == null ? "finished" : "at " + site.stage()) + ", " + site.skipped() + " skipped; in the cell: "
				+ helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(GLOW))).size() + " villager(s))");
		});
	}

	/** A villager caged in the lantern where its glowstone goes is moved out, and the glowstone laid. */
	//$ gametest_ticks_batch BIG_AREA '2400' '"lanternCage"'
	@GameTest(template = BIG_AREA, timeoutTicks = 2400, batch = "lanternCage")
	public void aVillagerCagedWhereTheGlowstoneGoesIsMovedOut(GameTestHelper helper) {
		lit(helper, begin(helper, false));
	}

	/** The builder itself caged in the cell it is to lay the glowstone in gets out, and lays it. */
	//$ gametest_ticks_batch BIG_AREA '2400' '"lanternCageBuilder"'
	@GameTest(template = BIG_AREA, timeoutTicks = 2400, batch = "lanternCageBuilder")
	public void aBuilderCagedWhereItsGlowstoneGoesGetsOut(GameTestHelper helper) {
		lit(helper, begin(helper, true));
	}
}
