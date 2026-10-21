package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterBenches;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
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
 * ROADMAP 37.15 with the pack's real furniture mods: the Crafter (37.14) makes their furniture. Handcrafted's chairs are
 * crafting-table recipes (an Oak Chair: a slab, two planks and two sticks, making two), so a builder short of them gets
 * them from logs through the whole chain. CobbleFurnies is the one pack furniture mod with a bench of its own (the Furni
 * Crafter, recipe type {@code cobblefurnies:furni_crafting}, shipped as a {@code crafter_benches} file): its pieces are
 * planned only while a Furni Crafter stands in the workshop. A bench file whose mod isn't installed is left out.
 */
public class CrafterFurnitureCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	/**
	 * A builder short of three Handcrafted Oak Chairs, with only oak logs in the chest: the Crafter saws planks, cuts slabs
	 * and sticks and crafts the chairs (by the crafting table's recipe, by id), and the build finishes with them.
	 */
	//$ gametest_ticks_batch AREA '4000' '"crafter_handcrafted_chair"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "crafter_handcrafted_chair")
	public void theCrafterMakesHandcraftedChairsFromLogsForTheBuilder(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Block chair = block("handcrafted:oak_chair");
		helper.assertTrue(chair != Blocks.AIR, "no Handcrafted Oak Chair");
		Crafting.Plan plan = Crafting.chain(level, chair.asItem(), 3, Map.of(Items.OAK_LOG, 3L), t -> true);
		helper.assertTrue(plan != null && plan.count() >= 3 && plan.takes().equals(Map.of(Items.OAK_LOG, 3)),
			"three Oak Chairs from three logs: " + plan);
		List<Item> outs = plan.steps().stream().map(s -> s.out().getItem()).toList();
		helper.assertTrue(outs.get(0) == Items.OAK_PLANKS && outs.contains(Items.OAK_SLAB) && outs.contains(Items.STICK)
			&& outs.get(outs.size() - 1) == chair.asItem(), "the chain should go planks, slabs and sticks, then the chairs: " + outs);
		helper.assertTrue(Crafting.chain(level, chair.asItem(), 3, Map.of(Items.OAK_LOG, 2L), t -> true) == null,
			"three Oak Chairs planned from two logs");

		ResourceLocation id = row(helper, ROW, chair);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 3));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the chairs' build didn't start");
		BuildPlan buildPlan = site.plan(level);
		helper.assertTrue(buildPlan != null, "no plan for the chairs' build");
		Villager crafter = crafter(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.status() + " missing " + site.missing() + "; the crafter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0)
					+ ", chest " + chest.countItem(Items.OAK_LOG) + " logs " + chest.countItem(chair.asItem()) + " chairs");
			helper.assertTrue(buildPlan.unfinished(level).isEmpty(), buildPlan.unfinished(level).size() + " block(s) wrong after the build");
			for (int x = 0; x < 3; x++) {
				helper.assertBlockPresent(chair, ROW.offset(x, 0, 0));
			}
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 3, "the chairs weren't the Crafter's: it made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
		});
	}

	/**
	 * CobbleFurnies' Furni Crafter is the shipped bench: an Oak Chair of CobbleFurnies' (a Furni Crafter recipe, with
	 * counted materials) is planned from a log only while a Furni Crafter stands by the Crafter's Workbench (one plank and
	 * two sticks, so a log's four planks do), and what's short of more is the logs.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFurniCraftersRecipesOnlyWhileItStandsInTheWorkshop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation type = ResourceLocation.parse("cobblefurnies:furni_crafting");
		helper.assertTrue(CrafterBenches.all().containsKey(type) && CrafterBenches.all().get(type).contains(ResourceLocation.parse("cobblefurnies:furnicrafter")),
			"the shipped CobbleFurnies bench wasn't loaded: " + CrafterBenches.all());
		Item chair = item("cobblefurnies:oak_chair");
		BlockPos station = helper.absolutePos(new BlockPos(0, 1, 0));
		helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.CRAFTERS_WORKBENCH);
		helper.assertTrue(Crafting.chain(level, chair, 1, Map.of(Items.OAK_LOG, 1L), CrafterBenches.usableAt(level, station)) == null,
			"a CobbleFurnies chair was planned with no Furni Crafter in the workshop");
		helper.setBlock(new BlockPos(1, 1, 0), block("cobblefurnies:furnicrafter"));
		Crafting.Plan plan = Crafting.chain(level, chair, 1, Map.of(Items.OAK_LOG, 1L), CrafterBenches.usableAt(level, station));
		helper.assertTrue(plan != null && plan.count() == 1 && plan.takes().equals(Map.of(Items.OAK_LOG, 1)),
			"a CobbleFurnies Oak Chair from a log with the Furni Crafter there: " + plan);
		Crafting.Step last = plan.steps().get(plan.steps().size() - 1);
		helper.assertTrue(last.out().is(chair) && last.in().equals(Map.of(Items.OAK_PLANKS, 1, Items.STICK, 2)),
			"the chair's step should take a plank and two sticks: " + last);
		Map<Item, Integer> lacks = Crafting.lacking(level, chair, 4, Map.of(Items.STICK, 8L), CrafterBenches.usableAt(level, station));
		helper.assertTrue(lacks.containsKey(Items.OAK_LOG), "four chairs from sticks alone should be short of oak logs (for the planks): " + lacks);
		helper.setBlock(new BlockPos(1, 1, 0), Blocks.AIR);
		helper.assertTrue(Crafting.chain(level, chair, 1, Map.of(Items.OAK_LOG, 1L), CrafterBenches.usableAt(level, station)) == null,
			"the chair was still planned once the Furni Crafter was taken away");
		helper.succeed();
	}

	/**
	 * A bench file for a mod that isn't installed (the compat suite's {@code not_installed.json}: a recipe type and block
	 * nobody registers) is left out of the benches, so nothing is looked for or planned by it; the installed ones load.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBenchFileForAModThatIsntInstalledIsIgnored(GameTestHelper helper) {
		helper.assertTrue(!BuiltInRegistries.RECIPE_TYPE.containsKey(ResourceLocation.parse("notinstalledmod:bench_crafting")),
			"the absent mod's recipe type is registered");
		helper.assertTrue(!CrafterBenches.all().containsKey(ResourceLocation.parse("notinstalledmod:bench_crafting")),
			"the bench of a mod that isn't installed was loaded: " + CrafterBenches.all());
		helper.assertTrue(CrafterBenches.all().containsKey(ResourceLocation.parse("cobblefurnies:furni_crafting")),
			"the installed CobbleFurnies bench should still load: " + CrafterBenches.all());
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static Villager crafter(GameTestHelper helper) {
		helper.setBlock(WORKBENCH, ModBlocks.CRAFTERS_WORKBENCH);
		Villager crafter = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(helper.getLevel(), crafter, helper.absolutePos(WORKBENCH), ModVillagers.CRAFTERS_WORKBENCH_POI, ModVillagers.CRAFTER);
		return crafter;
	}

	/** A blueprint of three {@code block} in a row, drawn where it's to be built (and taken away again). */
	private static ResourceLocation row(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "crafter_chairs_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}
}
