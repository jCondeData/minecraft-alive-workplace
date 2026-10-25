package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Bug tests for 36.7 found by QA (lane-b-1024-1237); B386, fixed.
 */
public class QaRepairCellarBugTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final int GROUND = 6;

	/**
	 * Bug: a builder's "Dig the cellar" trade stays on sale once the cellar is there. Spec 36.7: "the offer isn't there
	 * when the cellar is". The trade is added while the Smithy's cellar is ground; the cellar is then dug (by hand here,
	 * or by the builder after buying it once: the trade has two uses); the builder's trades are opened again, and the
	 * trade is still sold, for 4 emeralds, and its blueprint given back no longer starts a cellar dig (a whole Smithy job
	 * over the standing Smithy instead).
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qa_cellar_stale"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qa_cellar_stale")
	public void digTheCellarTradeGoesOnceTheCellarIsThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < 4 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
		StarterBlueprints.Entry entry = StarterBlueprints.SMITHY;
		BlockPos originRel = new BlockPos(9, GROUND + 1, 9);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(originRel), Rotation.NONE, Mirror.NONE);
		BlockPos file = helper.absolutePos(originRel).below(entry.basement());
		level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		List<BlockPos> open = new ArrayList<>();
		for (Blueprint.Entry e : BlueprintLibrary.get(level, entry.id()).orElseThrow().blocks()) {
			if (e.pos().getY() < 0 && e.state().isAir()) {
				open.add(placement.origin().offset(e.pos()));
			}
		}
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		open.forEach(p -> level.setBlock(p, Blocks.DIRT.defaultBlockState(), flags)); // built before 26.15
		BlockPos bench = new BlockPos(2, GROUND + 1, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, GROUND + 1, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModTrades.builderHomes(builder);
		helper.assertTrue(sellsDig(builder, placement), "precondition: no \"Dig the cellar\" while the cellar is ground");
		open.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), flags)); // the cellar dug
		helper.assertFalse(Cellars.needsDigging(level, entry.id(), placement), "precondition: the dug cellar still needs digging");
		ModTrades.builderHomes(builder); // the player opens the builder's trades again
		helper.assertFalse(sellsDig(builder, placement), "the builder still sells \"Dig the cellar: Smithy\" (4 emeralds) for a Smithy whose cellar is dug");
		helper.succeed();
	}

	private static boolean sellsDig(Villager villager, BlueprintData.Placement placement) {
		return villager.getOffers().stream().anyMatch(o -> !o.isOutOfStock() && o.getResult().has(DataComponents.CUSTOM_NAME) && BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(StarterBlueprints.SMITHY.id()) && d.placement().equals(Optional.of(placement))).orElse(false));
	}
}
