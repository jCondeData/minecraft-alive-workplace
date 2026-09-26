package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.block.BuildersBenchBlock;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	/** Workstation for the Builder profession. Unemployed villagers next to one become builders. */
	public static final BuildersBenchBlock BUILDERS_BENCH = register(
		"builders_bench", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE))
	);

	/** Browse the server's blueprints, see their materials, take copies and upload your own files. */
	public static final BlueprintTableBlock BLUEPRINT_TABLE = register(
		"blueprint_table", new BlueprintTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE))
	);

	private static <T extends Block> T register(String name, T block) {
		Registry.register(BuiltInRegistries.BLOCK, AliveWorkplace.id(name), block);
		Registry.register(BuiltInRegistries.ITEM, AliveWorkplace.id(name), new BlockItem(block, new Item.Properties()));
		return block;
	}

	public static void init() {
	}

	private ModBlocks() {
	}
}
