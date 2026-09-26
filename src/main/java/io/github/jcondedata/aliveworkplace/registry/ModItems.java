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

	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		AliveWorkplace.id("main"),
		FabricItemGroup.builder()
			.title(Component.translatable("itemGroup.aliveworkplace"))
			.icon(() -> new ItemStack(ModBlocks.BUILDERS_BENCH))
			.displayItems((params, output) -> {
				output.accept(ModBlocks.BUILDERS_BENCH);
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
