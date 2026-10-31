package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for B696's fix from the builder's chest reach (wiki builder.md: a bench's chests are those within 8 blocks "and 4
 * blocks up or down"): what another open build needs stays reserved in a shared chest exactly when that chest is one of
 * its bench's chests. A chest 4 blocks below the other builder's bench is his (its logs kept back from the Carpenter
 * working for this build); 5 below is not. Kills the nightly survivor at SupplyContainers.inReach (&lt;= VERTICAL).
 */
public class QaReservedReach1031GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(3, 2, 4);
	private static final BlockPos ROW_A = new BlockPos(6, 2, 8);
	private static final BlockPos ROW_B = new BlockPos(6, 2, 11);

	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench.below(), Blocks.STONE);
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		helper.setBlock(bench.south().below(), Blocks.STONE);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		v.setNoAi(true); // nobody builds: only what each build still needs matters
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no build of " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A row of {@code length} {@code block}s saved as a blueprint, the spot cleared again. */
	private static ResourceLocation row(GameTestHelper helper, String name, BlockPos origin, int length, net.minecraft.world.level.block.Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1031_reach_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(length, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < length; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	/** What other open builds keep back in the shared chest, for A's build, with B's bench {@code up} blocks above the chest. */
	private static int keptForB(GameTestHelper helper, int up, String name) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation planks = row(helper, name + "_a", ROW_A, 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logs = row(helper, name + "_b", ROW_B, 3, Blocks.SPRUCE_LOG);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager a = builder(helper, BENCH_A);
		BlockPos benchB = new BlockPos(5, CHEST.getY() + up, 2);
		Villager b = builder(helper, benchB);
		BuildSite siteA = start(helper, a, planks, ROW_A);
		start(helper, b, logs, ROW_B);
		Map<Item, Integer> kept = Builders.reservedIn(level, List.of(helper.absolutePos(CHEST)), siteA);
		return kept.getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
	}

	/** B's bench 4 above the shared chest: the chest is one of his, so his 3 spruce logs are kept back. */
	//$ gametest_ticks_batch BIG '40' '"qaReach1031Four"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaReach1031Four")
	public void qaAChestFourBelowAnotherBenchKeepsItsBuildsLogs(GameTestHelper helper) {
		int kept = keptForB(helper, 4, "four");
		helper.assertTrue(kept >= 3, "a chest 4 below B's bench is B's chest, but only " + kept + " of B's 3 spruce logs are kept back from A's Carpenter");
		helper.succeed();
	}

	/** B's bench 5 above the shared chest: not his chest, so nothing in it is kept back for him. */
	//$ gametest_ticks_batch BIG '40' '"qaReach1031Five"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaReach1031Five")
	public void qaAChestFiveBelowAnotherBenchKeepsNothingForIt(GameTestHelper helper) {
		int kept = keptForB(helper, 5, "five");
		helper.assertTrue(kept == 0, "a chest 5 below B's bench isn't B's chest, but " + kept + " spruce logs are kept back for B there");
		helper.succeed();
	}
}
