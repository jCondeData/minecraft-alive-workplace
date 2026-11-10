package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

/**
 * QA, lane e (2026-11-10), ROADMAP 46.6 the Glassblower with the pack's mods (Create, Chipped, Rechiseled, Supplementaries,
 * the furniture and storage mods): their recipes for glass things don't take his list from him or make him plan from their
 * makings, a builder's panes and red stained panes are still made from sand, and looking up the maker of every modded glass
 * thing throws nothing.
 */
public class QaGlassblower1110eCompatTests {
	private static final String AREA = "aliveworkplace_compat:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	/** With every pack mod loaded the vanilla glass things are still his, and every mod's glass thing has a maker or none, without a crash. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePacksGlassLeavesHisListAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (Item item : List.of(Items.GLASS, Items.GLASS_PANE, Items.RED_STAINED_GLASS, Items.RED_STAINED_GLASS_PANE, Items.TINTED_GLASS, Items.GLASS_BOTTLE)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.GLASSBLOWER, item + " is made by " + CraftJobs.maker(level, item) + " with the pack's mods");
		}
		List<String> modded = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
			if (!id.getNamespace().equals("minecraft") && id.getPath().contains("glass")) {
				CraftJobs.maker(level, item); // must not throw
				modded.add(id.toString());
			}
		}
		helper.assertTrue(!modded.isEmpty(), "no modded glass in the pack: the test checked nothing");
		helper.succeed();
	}

	/** A glass pane for a builder from six sand and a coal with the pack's mods loaded: fired, then cut into panes. */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassPackPanes"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassPackPanes")
	public void withThePackPanesAreStillMadeFromSand(GameTestHelper helper) {
		prepare(helper);
		BuildSite site = asker(helper, blueprint(helper, Blocks.GLASS_PANE, "packpanes"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		BuildPlan plan = site.plan(helper.getLevel());
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, site, plan);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SAND) == 0 && chest.countItem(Items.COAL) == 0,
				"the panes weren't made from the sand and coal: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(chest.countItem(Items.GLASS_PANE) == 15, "the rest of the panes in the chest: " + chest.countItem(Items.GLASS_PANE));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** A red stained glass pane from sand, a coal and a red dye with the pack's mods loaded: glass, stained glass, panes. */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassPackStainedPanes"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassPackStainedPanes")
	public void withThePackRedPanesAreMadeFromSandAndADye(GameTestHelper helper) {
		prepare(helper);
		BuildSite site = asker(helper, blueprint(helper, Blocks.RED_STAINED_GLASS_PANE, "packred"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.RED_DYE, 1));
		BuildPlan plan = site.plan(helper.getLevel());
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, site, plan);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SAND) == 0 && chest.countItem(Items.RED_DYE) == 0,
				"the red panes weren't made from the makings: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.RED_DYE) + " dye left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	private static ResourceLocation blueprint(GameTestHelper helper, Block block, String what) {
		helper.setBlock(ROW, block);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qa_glass_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}

	private static BuildSite asker(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return site;
	}

	private static Villager glassblower(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		return v;
	}

	private static void assertBuilt(GameTestHelper helper, BuildSite site, BuildPlan plan) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: status=" + site.status() + " missing=" + site.missing());
		List<BlockPos> unfinished = plan.unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}
}
