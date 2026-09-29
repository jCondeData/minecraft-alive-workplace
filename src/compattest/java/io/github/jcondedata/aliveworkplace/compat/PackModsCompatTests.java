package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The building and storage mods of the Cobbleverse pack (1.7.42): Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar Concrete (Cozyhome can't be remapped for a dev environment). */
public class PackModsCompatTests implements FabricGameTest {
	static final Set<String> BUILDING_MODS = Set.of("handcrafted", "beautify", "cobblefurnies", "carved_wood", "moarconcrete");

	/** Every block these mods have an item for can be built, and costs something. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPackBlockHasACost(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int checked = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!BUILDING_MODS.contains(id.getNamespace()) || block.asItem() == Items.AIR) {
				continue;
			}
			BlockState state = MaterialRules.forPlacement(block.defaultBlockState());
			if (MaterialRules.isSecondaryHalf(state)) {
				continue;
			}
			checked++;
			if (MaterialRules.classify(state) == MaterialRules.Kind.SKIP || MaterialRules.requirements(state, null).isEmpty()) {
				problems.add(id + " (" + MaterialRules.classify(state) + ")");
			}
		}
		helper.assertTrue(checked > 100, "only " + checked + " blocks from the pack's building mods: are they installed?");
		helper.assertTrue(problems.isEmpty(), problems.size() + " of " + checked + " blocks can't be built: "
			+ String.join(", ", problems.subList(0, Math.min(40, problems.size()))));
		helper.succeed();
	}

	/**
	 * Sophisticated Storage barrels are supply chests; a Tom's Storage connector next to a chest doesn't
	 * make its contents count twice.
	 */
	//$ gametest 'CompatGameTests.AREA'
	@GameTest(template = CompatGameTests.AREA)
	public void packStorageWorksAsSupplyChests(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.core.BlockPos bench = helper.absolutePos(CompatGameTests.BENCH);
		helper.setBlock(CompatGameTests.BENCH, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH);
		net.minecraft.core.BlockPos barrel = new net.minecraft.core.BlockPos(4, 2, 2);
		helper.setBlock(barrel, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("sophisticatedstorage:barrel")));
		var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, helper.absolutePos(barrel), null);
		helper.assertTrue(storage != null, "a Sophisticated Storage barrel has no item storage");
		long inserted;
		try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
			inserted = storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.OAK_PLANKS), 10, tx);
			tx.commit();
		}
		helper.assertTrue(inserted == 10, "put " + inserted + " planks in the barrel (" + storage.getClass().getName() + ")");
		net.minecraft.core.BlockPos chest = new net.minecraft.core.BlockPos(2, 2, 6);
		helper.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST);
		((net.minecraft.world.Container) helper.getBlockEntity(chest)).setItem(0, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE, 5));
		net.minecraft.core.BlockPos connector = new net.minecraft.core.BlockPos(2, 2, 7);
		helper.setBlock(connector, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("toms_storage:inventory_connector")));

		var supplies = io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(level, bench, null);
		helper.assertTrue(supplies.contains(helper.absolutePos(barrel)) && supplies.contains(helper.absolutePos(chest)), "supplies: " + supplies);
		helper.assertFalse(supplies.contains(helper.absolutePos(connector)), "the storage connector counts as a chest");
		long planks = io.github.jcondedata.aliveworkplace.build.SupplyContainers.count(level, supplies, Items.OAK_PLANKS);
		helper.assertTrue(planks == 10, planks + " planks counted in the barrel");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.SupplyContainers.count(level, supplies, Items.COBBLESTONE) == 5,
			"cobblestone counted " + io.github.jcondedata.aliveworkplace.build.SupplyContainers.count(level, supplies, Items.COBBLESTONE) + " times");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.SupplyContainers.extract(level, supplies, Items.OAK_PLANKS, 4) == 4
			&& io.github.jcondedata.aliveworkplace.build.SupplyContainers.count(level, supplies, Items.OAK_PLANKS) == 6, "taking from the barrel");
		helper.succeed();
	}

	/**
	 * A sample of five blocks from each of the pack's building mods (furniture, lamps, carved planks,
	 * concrete...), spread over the floor: a builder builds all of it from the chest, nothing skipped.
	 */
	//$ gametest_ticks 'CompatGameTests.AREA' '4000'
	@GameTest(template = CompatGameTests.AREA, timeoutTicks = 4000)
	public void buildsASampleOfEachPackMod(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		java.util.Map<net.minecraft.core.BlockPos, BlockState> design = new java.util.HashMap<>();
		java.util.Map<net.minecraft.world.item.Item, Integer> materials = new java.util.LinkedHashMap<>();
		List<String> picked = new ArrayList<>();
		int spot = 0;
		for (String mod : new java.util.TreeSet<>(BUILDING_MODS)) {
			List<Block> candidates = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
				BlockState state = block.defaultBlockState();
				if (id.getNamespace().equals(mod) && block.asItem() != Items.AIR && !state.isAir()
					&& !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
					&& !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
					&& MaterialRules.classify(state) != MaterialRules.Kind.SKIP) {
					candidates.add(block);
				}
			}
			candidates.sort(java.util.Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()));
			int taken = 0;
			for (int i = 0; i < candidates.size() && taken < 5; i += Math.max(1, candidates.size() / 7)) {
				BlockState state = MaterialRules.forPlacement(candidates.get(i).defaultBlockState());
				net.minecraft.core.BlockPos rel = new net.minecraft.core.BlockPos((spot % 5) * 2, 0, (spot / 5) * 2);
				if (!state.canSurvive(level, helper.absolutePos(CompatGameTests.ORIGIN.offset(rel)))) {
					continue; // needs a wall or a ceiling
				}
				design.put(rel, state);
				for (MaterialRules.Requirement r : MaterialRules.requirements(state, null)) {
					materials.merge(r.item(), r.count(), Integer::sum);
				}
				picked.add(BuiltInRegistries.BLOCK.getKey(candidates.get(i)).toString());
				spot++;
				taken++;
			}
		}
		helper.assertTrue(design.size() >= 20, "only " + design.size() + " sample blocks: " + picked);
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "pack_sample", design, java.util.Map.of());
		List<net.minecraft.world.item.ItemStack> chest = new ArrayList<>();
		materials.forEach((item, count) -> chest.add(new net.minecraft.world.item.ItemStack(item, count)));
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, chest.toArray(net.minecraft.world.item.ItemStack[]::new));
		helper.succeedWhen(() -> CompatGameTests.assertBuilt(helper, s));
	}
}
