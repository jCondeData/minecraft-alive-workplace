package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Reg;
import io.github.jcondedata.aliveworkplace.block.BuildersBenchBlock;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	/** Workstation for the Builder profession. Unemployed villagers next to one become builders. */
	public static final BuildersBenchBlock BUILDERS_BENCH = Reg.block("builders_bench", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

	/** Browse the server's blueprints, see their materials, take copies and upload your own files. */
	public static final BlueprintTableBlock BLUEPRINT_TABLE = Reg.block("blueprint_table", BlueprintTableBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE));

	/** Workstation for the Miner profession; chests near it are where the miner drops off what it digs. */
	public static final BuildersBenchBlock MINERS_BENCH = Reg.block("miners_bench", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE));

	/** Workstation for the Lumberjack profession: trees within 16 blocks get cut, logs go into the chests nearby. */
	public static final BuildersBenchBlock CHOPPING_BLOCK = Reg.block("chopping_block", BuildersBenchBlock::new, BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.WOOD)
			.instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
			.strength(2.0f)
			.sound(net.minecraft.world.level.block.SoundType.WOOD)
			.ignitedByLava());

	/** A player's mailbox: mail for them arrives here, and they post parcels from it. */
	public static final io.github.jcondedata.aliveworkplace.mail.MailboxBlock MAILBOX = Reg.block("mailbox", io.github.jcondedata.aliveworkplace.mail.MailboxBlock::new, BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLUE)
			.strength(1.5f)
			.sound(net.minecraft.world.level.block.SoundType.LANTERN)
			.noOcclusion());

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity> MAILBOX_ENTITY =
		Reg.blockEntity("mailbox", io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity::new, MAILBOX);

	public static final net.minecraft.world.inventory.MenuType<io.github.jcondedata.aliveworkplace.mail.MailboxMenu> MAILBOX_MENU =
		Registry.register(BuiltInRegistries.MENU, AliveWorkplace.id("mailbox"),
			io.github.jcondedata.aliveworkplace.platform.Platform.get().menuWithData(
				(id, inventory, pos) -> new io.github.jcondedata.aliveworkplace.mail.MailboxMenu(id, inventory, pos),
				net.minecraft.core.BlockPos.STREAM_CODEC));

	/** Workstation for the Postman profession: they collect and deliver mail within 64 blocks of it. */
	public static final BuildersBenchBlock POSTAL_DESK = Reg.block("postal_desk", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARTOGRAPHY_TABLE));

	/** Workstation for the Guard profession: a weapon rack; guards take their gear from the chests near it. */
	public static final BuildersBenchBlock GUARD_POST = Reg.block("guard_post", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE));

	/** Workstation for the Nurse profession: a counter with bandages and tonics. */
	public static final BuildersBenchBlock NURSE_STATION = Reg.block("nurse_station", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_PLANKS));

	/** Workstation for the Shopkeeper profession, and the shop's price list. */
	public static final io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock SHOP_COUNTER = Reg.block("shop_counter", io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS));

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity> SHOP_COUNTER_ENTITY =
		Reg.blockEntity("shop_counter", io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity::new, SHOP_COUNTER);

	/** A stop on the travel network, and the Ferryman's workstation. */
	/** A cradle near a bed makes a nursery village (ROADMAP 30.12). */
	public static final io.github.jcondedata.aliveworkplace.hall.CradleBlock CRADLE = Reg.block("cradle", io.github.jcondedata.aliveworkplace.hall.CradleBlock::new,
		BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(1.0f).noOcclusion());
	public static final io.github.jcondedata.aliveworkplace.travel.TravelPostBlock TRAVEL_POST = Reg.block("travel_post", io.github.jcondedata.aliveworkplace.travel.TravelPostBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Bard profession: they play the music discs from the chests nearby. */
	public static final BuildersBenchBlock MUSIC_STAND = Reg.block("music_stand", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.NOTE_BLOCK));

	/** Workstation for Pokémon Trainers (they only battle with Cobblemon installed). */
	public static final BuildersBenchBlock TRAINING_POST = Reg.block("training_post", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.TARGET));

	/** Workstation for the village's Trainer Leader. */
	public static final BuildersBenchBlock LEADERS_PODIUM = Reg.block("leaders_podium", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_ANDESITE));

	/** Workstation for Move Tutors (they only teach with Cobblemon installed). */
	public static final BuildersBenchBlock TUTORS_DESK = Reg.block("tutors_desk", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF));

	/** Workstation for Pokémon Traders (they only trade with Cobblemon installed). */
	public static final BuildersBenchBlock TRADE_BOARD = Reg.block("trade_board", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Orchard Keeper: ripe fruit within 16 blocks gets picked and stored in the chests nearby. */
	public static final BuildersBenchBlock FRUIT_BASKET = Reg.block("fruit_basket", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));

	/** Workstation for the Beekeeper: the beehives within 16 blocks are harvested, kept in flowers and filled with bees. */
	public static final BuildersBenchBlock APIARY = Reg.block("apiary", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));

	/** Workstation for the Florist: flowers grown in the garden round it, empty flower pots in the village filled. */
	public static final BuildersBenchBlock FLOWER_STAND = Reg.block("flower_stand", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Rancher: horses tamed, saddled and bred round it; with Cobblemon, pastured Pokémon groomed. */
	public static final BuildersBenchBlock FEED_TROUGH = Reg.block("feed_trough", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Ball Smith: Poké Balls from the apricorns and metals in the chests nearby (with Cobblemon). */
	public static final BuildersBenchBlock BALL_WORKBENCH = Reg.block("ball_workbench", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMITHING_TABLE));

	/** Workstation for the Porter: the chests near it are the village's storehouse. Whoever places it owns it. */
	public static final io.github.jcondedata.aliveworkplace.store.StorehouseBlock STOREHOUSE = Reg.block("storehouse", io.github.jcondedata.aliveworkplace.store.StorehouseBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity> STOREHOUSE_ENTITY =
		Reg.blockEntity("storehouse", io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity::new, STOREHOUSE);

	/** Workstation for the Sifter: gravel, sand and dirt shaken through it for what's hidden in them. */
	public static final BuildersBenchBlock SIEVE = Reg.block("sieve", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Tinkerer: redstone and iron parts for the builders, with a little forge for ore. */
	public static final BuildersBenchBlock TINKERS_BENCH = Reg.block("tinkers_bench", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS));

	/** Workstation for the Netherworker: expeditions through the Nether portal nearby start here. */
	public static final BuildersBenchBlock NETHER_BRAZIER = Reg.block("nether_brazier", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_BLACKSTONE).lightLevel(state -> 10));

	/** Anything put in goes to the storehouse with the next porter. */
	public static final io.github.jcondedata.aliveworkplace.store.DropBoxBlock DROP_BOX = Reg.block("drop_box", io.github.jcondedata.aliveworkplace.store.DropBoxBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL));

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity> DROP_BOX_ENTITY =
		Reg.blockEntity("drop_box", io.github.jcondedata.aliveworkplace.store.DropBoxBlockEntity::new, DROP_BOX);

	/** Workstation for the Composter: the village's scraps become bone meal. */
	public static final BuildersBenchBlock COMPOST_BIN = Reg.block("compost_bin", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.COMPOSTER));

	/** Workstation for the Scholar: the village's research is done here (a Village Hall keeps it). */
	public static final BuildersBenchBlock SCHOLARS_DESK = Reg.block("scholars_desk", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF));

	/** Workstation for the Undertaker: the graves nearby are tended, and the villagers in them brought back. */
	public static final BuildersBenchBlock UNDERTAKERS_TABLE = Reg.block("undertakers_table", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS));

	/** Where a villager lies: left where they died (see {@code grave/Graves}). */
	public static final io.github.jcondedata.aliveworkplace.grave.GraveBlock GRAVE = Reg.block("grave", io.github.jcondedata.aliveworkplace.grave.GraveBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion().strength(1.5f, 6f));

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity> GRAVE_ENTITY =
		Reg.blockEntity("grave", io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity::new, GRAVE);

	/** Workstation for the Innkeeper: travellers come to stay at the inn, and can be hired. */
	public static final BuildersBenchBlock INN_COUNTER = Reg.block("inn_counter", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** Workstation for the Teacher: the village's children come here for lessons. */
	public static final BuildersBenchBlock TEACHERS_DESK = Reg.block("teachers_desk", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS));

	/** The Village Hall: the village at a glance (right-click), and the village's name. */
	public static final io.github.jcondedata.aliveworkplace.hall.VillageHallBlock VILLAGE_HALL = Reg.block("village_hall", io.github.jcondedata.aliveworkplace.hall.VillageHallBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF));

	public static final net.minecraft.world.level.block.entity.BlockEntityType<io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity> VILLAGE_HALL_ENTITY =
		Reg.blockEntity("village_hall", io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity::new, VILLAGE_HALL);

	/** Workstation for the Carpenter: they make what the builders nearby are waiting for, here. */
	public static final BuildersBenchBlock CARPENTERS_BENCH = Reg.block("carpenters_bench", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE));

	/** Workstation for the Chef: they cook here, from the chests nearby and the village's storehouse. */
	public static final BuildersBenchBlock KITCHEN_STOVE = Reg.block("kitchen_stove", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOKER).lightLevel(state -> 7));

	/** Workstation for the Fossil Scientist (with Cobblemon): fossils handed to them are revived here. */
	public static final BuildersBenchBlock FOSSIL_LAB = Reg.block("fossil_lab", BuildersBenchBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).lightLevel(state -> 4));

	/** Guards spar with the ones near their Guard Post between fights, for experience. */
	public static final io.github.jcondedata.aliveworkplace.guard.TrainingDummyBlock TRAINING_DUMMY = Reg.block("training_dummy", io.github.jcondedata.aliveworkplace.guard.TrainingDummyBlock::new, BlockBehaviour.Properties.of()
			.mapColor(net.minecraft.world.level.material.MapColor.COLOR_YELLOW)
			.strength(1.0f)
			.sound(net.minecraft.world.level.block.SoundType.WOOL)
			.noOcclusion()
			.ignitedByLava());

	public static void init() {
	}

	private ModBlocks() {
	}
}
