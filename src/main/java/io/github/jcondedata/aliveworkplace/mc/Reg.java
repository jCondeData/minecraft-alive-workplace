package io.github.jcondedata.aliveworkplace.mc;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registering our blocks, items and entities under {@code aliveworkplace:<name>}. Changes (porting.md): from 1.21.2 the
 * properties must carry the registry key ({@code setId}) before the block or item is made, and block items need
 * {@code useBlockDescriptionPrefix}; {@code EntityType.Builder.build} takes a {@code ResourceKey}; the
 * {@code BlockEntityType} constructor/builder changes between 1.21.4 and 26.2. So everything is made here, from a
 * factory and its properties.
 */
public final class Reg {
	/** A block, and the item that places it. */
	public static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
		ResourceLocation id = AliveWorkplace.id(name);
		T block = Registry.register(BuiltInRegistries.BLOCK, id, factory.apply(properties));
		Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()));
		return block;
	}

	public static <T extends Item> T item(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		return Registry.register(BuiltInRegistries.ITEM, AliveWorkplace.id(name), factory.apply(properties));
	}

	public static <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, AliveWorkplace.id(name), BlockEntityType.Builder.of(factory, blocks).build(null));
	}

	public static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, AliveWorkplace.id(name), builder.build(name));
	}

	private Reg() {
	}
}
