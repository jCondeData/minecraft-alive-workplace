package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	public static final BlueprintItem BLUEPRINT = Registry.register(
		BuiltInRegistries.ITEM, AliveWorkplace.id("blueprint"), new BlueprintItem(new Item.Properties().stacksTo(1))
	);

	/** Paper + blue dye. Turned into a real blueprint at a Blueprint Table. */
	public static final Item BLANK_BLUEPRINT = Registry.register(
		BuiltInRegistries.ITEM, AliveWorkplace.id("blank_blueprint"), new Item(new Item.Properties())
	);

	/** Marks out a quarry for a Miner. */
	public static final io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem QUARRY_MARKER = Registry.register(
		BuiltInRegistries.ITEM, AliveWorkplace.id("quarry_marker"),
		new io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem(new Item.Properties().stacksTo(1))
	);

	/** Marks out a field for a Farmer. */
	public static final io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem FIELD_MARKER = Registry.register(
		BuiltInRegistries.ITEM, AliveWorkplace.id("field_marker"),
		new io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem(new Item.Properties().stacksTo(1))
	);

	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		AliveWorkplace.id("main"),
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.aliveworkplace"))
			.icon(() -> new ItemStack(ModBlocks.BUILDERS_BENCH))
			.displayItems((params, output) -> {
				output.accept(ModBlocks.BUILDERS_BENCH);
				output.accept(ModBlocks.BLUEPRINT_TABLE);
				output.accept(BLANK_BLUEPRINT);
				output.accept(ModBlocks.MINERS_BENCH);
				output.accept(QUARRY_MARKER);
				output.accept(ModBlocks.CHOPPING_BLOCK);
				output.accept(FIELD_MARKER);
				output.accept(ModBlocks.POSTAL_DESK);
				output.accept(ModBlocks.MAILBOX);
				output.accept(ModBlocks.GUARD_POST);
				output.accept(ModBlocks.NURSE_STATION);
				for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
					output.accept(BlueprintItem.create(entry.id(), entry.size()));
				}
			})
			.build()
	);

	public static void init() {
	}

	private ModItems() {
	}
}
