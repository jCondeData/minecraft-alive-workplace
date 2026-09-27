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

	/** Workstation for the Miner profession; chests near it are where the miner drops off what it digs. */
	public static final BuildersBenchBlock MINERS_BENCH = register(
		"miners_bench", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE))
	);

	/** Workstation for the Lumberjack profession: trees within 16 blocks get cut, logs go into the chests nearby. */
	public static final BuildersBenchBlock CHOPPING_BLOCK = register(
		"chopping_block", new BuildersBenchBlock(BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.WOOD)
			.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
			.strength(2.0f)
			.sound(net.minecraft.world.level.block.SoundType.WOOD)
			.ignitedByLava())
	);

	/** A player's mailbox: mail for them arrives here, and they post parcels from it. */
	public static final io.github.jcondedata.aliveworkplace.mail.MailboxBlock MAILBOX = register(
		"mailbox", new io.github.jcondedata.aliveworkplace.mail.MailboxBlock(BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
			.strength(1.5f)
			.sound(net.minecraft.world.level.block.SoundType.LANTERN)
			.noOcclusion())
	);

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity> MAILBOX_ENTITY =
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, AliveWorkplace.id("mailbox"),
			net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity::new, MAILBOX).build(null));

	public static final net.minecraft.world.inventory.MenuType<io.github.jcondedata.aliveworkplace.mail.MailboxMenu> MAILBOX_MENU =
		Registry.register(BuiltInRegistries.MENU, AliveWorkplace.id("mailbox"),
			new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
				(id, inventory, pos) -> new io.github.jcondedata.aliveworkplace.mail.MailboxMenu(id, inventory, pos),
				net.minecraft.core.BlockPos.STREAM_CODEC));

	/** Workstation for the Postman profession: they collect and deliver mail within 64 blocks of it. */
	public static final BuildersBenchBlock POSTAL_DESK = register(
		"postal_desk", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE))
	);

	/** Workstation for the Guard profession: a weapon rack; guards take their gear from the chests near it. */
	public static final BuildersBenchBlock GUARD_POST = register(
		"guard_post", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE))
	);

	/** Workstation for the Nurse profession: a counter with bandages and tonics. */
	public static final BuildersBenchBlock NURSE_STATION = register(
		"nurse_station", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_PLANKS))
	);

	/** Workstation for the Shopkeeper profession, and the shop's price list. */
	public static final io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock SHOP_COUNTER = register(
		"shop_counter", new io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS))
	);

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity> SHOP_COUNTER_ENTITY =
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, AliveWorkplace.id("shop_counter"),
			net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity::new, SHOP_COUNTER).build(null));

	/** A stop on the travel network, and the Ferryman's workstation. */
	public static final io.github.jcondedata.aliveworkplace.travel.TravelPostBlock TRAVEL_POST = register(
		"travel_post", new io.github.jcondedata.aliveworkplace.travel.TravelPostBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS))
	);

	/** Workstation for the Bard profession: they play the music discs from the chests nearby. */
	public static final BuildersBenchBlock MUSIC_STAND = register(
		"music_stand", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NOTE_BLOCK))
	);

	/** Workstation for Pokémon Trainers (they only battle with Cobblemon installed). */
	public static final BuildersBenchBlock TRAINING_POST = register(
		"training_post", new BuildersBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.TARGET))
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
