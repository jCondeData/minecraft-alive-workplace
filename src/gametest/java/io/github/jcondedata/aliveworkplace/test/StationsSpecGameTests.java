package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The tester's checks of ROADMAP 21.1a (fewer job blocks), written from its plan, the owner's answers and README "All the
 * jobs at a glance". The player's clicks go through the server's packet handler, as a real client's do, so every
 * interaction handler runs in its real order.
 */
public class StationsSpecGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	/** The 26 job blocks the plan lists as gone. */
	static List<Block> gone() {
		return List.of(ModBlocks.BUILDERS_BENCH, ModBlocks.MINERS_BENCH, ModBlocks.CHOPPING_BLOCK, ModBlocks.FRUIT_BASKET, ModBlocks.APIARY,
			ModBlocks.FLOWER_STAND, ModBlocks.SCHOLARS_DESK, ModBlocks.SIEVE, ModBlocks.TINKERS_BENCH, ModBlocks.COMPOST_BIN, ModBlocks.NETHER_BRAZIER,
			ModBlocks.UNDERTAKERS_TABLE, ModBlocks.INN_COUNTER, ModBlocks.TEACHERS_DESK, ModBlocks.FEED_TROUGH, ModBlocks.CARPENTERS_BENCH,
			ModBlocks.KITCHEN_STOVE, ModBlocks.POSTAL_DESK, ModBlocks.GUARD_POST, ModBlocks.NURSE_STATION, ModBlocks.MUSIC_STAND,
			ModBlocks.LEADERS_PODIUM, ModBlocks.TUTORS_DESK, ModBlocks.BALL_WORKBENCH, ModBlocks.TRADE_BOARD, ModBlocks.FOSSIL_LAB);
	}

	/** The blocks the plan keeps. */
	static List<Block> kept() {
		return List.of(ModBlocks.BLUEPRINT_TABLE, ModBlocks.VILLAGE_HALL, ModBlocks.STOREHOUSE, ModBlocks.SHOP_COUNTER, ModBlocks.TRAVEL_POST,
			ModBlocks.MAILBOX, ModBlocks.TRAINING_POST);
	}

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/**
	 * What a client sends when its player right-clicks {@code villager} holding {@code stack}: first the click at a spot
	 * on the villager (where Fabric's use-entity callbacks run on the server), then, for a plain click, the plain
	 * interaction (vanilla's trading). A sneak-right-click with a job's item is answered by the client's own callback,
	 * so only the first packet goes, as in the game.
	 */
	static void rightClick(ServerPlayer player, Villager villager, ItemStack stack, boolean sneaking) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneaking, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (!sneaking) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
	}

	static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	static VillagerProfession job(Villager villager) {
		return villager.getVillagerData().getProfession();
	}

	/**
	 * README: sneak-right-click a villager standing by the block holding the job's item and they take it there; a plain
	 * right-click with the item doesn't; the block's own job comes back with its item; picking a job doesn't hire.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theSneakRightClickPicksTheJob(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.COMPOSTER);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		BlockPos composter = helper.absolutePos(STATION);

		rightClick(player, villager, new ItemStack(Items.BONE_MEAL), false);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "a plain right-click with bone meal gave " + name(job(villager)));

		rightClick(player, villager, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(job(villager) == ModVillagers.ORCHARD_KEEPER, "sweet berries gave " + name(job(villager)));
		helper.assertTrue(site(villager).equals(Optional.of(composter)), "the orchard keeper works at " + site(villager));
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(villager) == null, "picking a job hired the villager");

		rightClick(player, villager, new ItemStack(Items.WHEAT), true);
		helper.assertTrue(job(villager) == VillagerProfession.FARMER, "wheat gave " + name(job(villager)));
		helper.assertTrue(site(villager).equals(Optional.of(composter)), "the farmer works at " + site(villager));
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(villager) == null, "switching back hired the villager");
		helper.succeed();
	}

	/**
	 * README: "hiring is as before, the vanilla job's item on a villager who already has that job". Each vanilla worker,
	 * sneak-right-clicked with its hiring item, is hired and keeps their job and block. (The shepherd's shears: see the
	 * bug test.)
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void vanillaWorkersAreStillHiredWithTheirItem(GameTestHelper helper) {
		record Hire(Block block, ResourceKey<PoiType> poi, VillagerProfession job, Item item) {
		}
		List<Hire> hires = List.of(
			new Hire(Blocks.BLAST_FURNACE, PoiTypes.ARMORER, VillagerProfession.ARMORER, Items.COAL),
			new Hire(Blocks.BLAST_FURNACE, PoiTypes.ARMORER, VillagerProfession.ARMORER, Items.CHARCOAL),
			new Hire(Blocks.SMITHING_TABLE, PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH, Items.IRON_INGOT),
			new Hire(Blocks.GRINDSTONE, PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH, Items.IRON_INGOT),
			new Hire(Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, VillagerProfession.FLETCHER, Items.FLINT),
			new Hire(Blocks.BREWING_STAND, PoiTypes.CLERIC, VillagerProfession.CLERIC, Items.GLASS_BOTTLE),
			new Hire(Blocks.SMOKER, PoiTypes.BUTCHER, VillagerProfession.BUTCHER, Items.LEAD),
			new Hire(Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN, Items.LAPIS_LAZULI),
			new Hire(Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER, Items.COMPASS));
		ServerPlayer player = player(helper);
		List<String> wrong = new ArrayList<>();
		for (Hire hire : hires) {
			helper.setBlock(STATION, hire.block());
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			Jobs.employ(helper.getLevel(), villager, helper.absolutePos(STATION), hire.poi(), hire.job());
			rightClick(player, villager, new ItemStack(hire.item()), true);
			String what = name(hire.job()) + " with " + BuiltInRegistries.ITEM.getKey(hire.item());
			if (ModAttachments.BUILDER_EMPLOYER.get(villager) == null) {
				wrong.add(what + ": not hired");
			}
			if (job(villager) != hire.job() || !site(villager).equals(Optional.of(helper.absolutePos(STATION)))) {
				wrong.add(what + ": now " + name(job(villager)) + " at " + site(villager));
			}
			villager.discard();
			helper.setBlock(STATION, Blocks.AIR);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		helper.succeed();
	}

	/** The owner's server has several players: someone else's hired worker can't be given another job. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void someoneElsesWorkerKeepsTheirJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		helper.setBlock(STATION, Blocks.BLAST_FURNACE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		Jobs.employ(level, villager, helper.absolutePos(STATION), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(UUID.randomUUID(), "Boss"));
		ServerPlayer stranger = player(helper);
		helper.assertFalse(stranger.hasPermissions(2), "setup: the stranger should not be an operator");
		rightClick(stranger, villager, new ItemStack(Items.IRON_PICKAXE), true);
		helper.assertTrue(job(villager) == VillagerProfession.ARMORER, "a stranger's pickaxe made the Boss's armorer a " + name(job(villager)));
		helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the armorer lost their blast furnace: " + site(villager));
		helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(villager).name().equals("Boss"), "the armorer changed hands");
		helper.succeed();
	}

	/**
	 * A picked job takes a free block: a farmer's composter isn't taken from them for another villager's orchard keeper
	 * job (the player is told a composter is needed instead).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void anotherWorkersBlockIsNotTaken(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.COMPOSTER);
		BlockPos composter = helper.absolutePos(STATION);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		Jobs.employ(helper.getLevel(), farmer, composter, PoiTypes.FARMER, VillagerProfession.FARMER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player(helper), villager, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "the second villager became a " + name(job(villager)) + " at " + site(villager));
		helper.assertTrue(job(farmer) == VillagerProfession.FARMER && site(farmer).equals(Optional.of(composter)),
			"the farmer is now a " + name(job(farmer)) + " at " + site(farmer));
		helper.succeed();
	}

	/** A test player like GameTestHelper's mock one, who also keeps every chat and action-bar line they're shown. */
	static ServerPlayer listeningPlayer(GameTestHelper helper, List<String> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "test-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(net.minecraft.network.chat.Component message, boolean overlay) {
				seen.add(message.getString());
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/**
	 * The player is told what happened, in the words of the plan: with no block of the job's kind near, which block it
	 * needs; with one, who took which job where.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePlayerIsToldWhatHappened(GameTestHelper helper) {
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player, villager, new ItemStack(Items.IRON_AXE), true);
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Lumberjack job needs a Fletching Table")),
			"with no fletching table near, the player saw: " + seen);
		seen.clear();
		helper.setBlock(STATION, Blocks.FLETCHING_TABLE);
		rightClick(player, villager, new ItemStack(Items.IRON_AXE), true);
		helper.assertTrue(seen.contains("Villager took the Lumberjack job at the Fletching Table."),
			"with a fletching table near, the player saw: " + seen);
		helper.succeed();
	}

	/**
	 * A block a job was picked at is that worker's: the next villager handed a job item for the same kind of block is told
	 * one is needed, and doesn't share it (every click goes through the player's real sneak-right-click).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPickedBlockBelongsToItsWorker(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.COMPOSTER);
		BlockPos composter = helper.absolutePos(STATION);
		ServerPlayer player = player(helper);
		Villager florist = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player, florist, new ItemStack(Items.POPPY), true);
		helper.assertTrue(job(florist) == ModVillagers.FLORIST && site(florist).equals(Optional.of(composter)),
			"setup: a poppy gave " + name(job(florist)) + " at " + site(florist));
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		rightClick(player, second, new ItemStack(Items.BONE_MEAL), true);
		helper.assertTrue(job(second) == VillagerProfession.NONE,
			"the florist's composter was shared: the second villager became a " + name(job(second)) + " at " + site(second));
		helper.assertTrue(job(florist) == ModVillagers.FLORIST && site(florist).equals(Optional.of(composter)),
			"the florist is now a " + name(job(florist)) + " at " + site(florist));
		helper.succeed();
	}

	/**
	 * A worker moved to another kind of block lets go of the old one: a farmer handed redstone becomes a tinkerer at the
	 * smithing table, and their composter is free again for the next villager.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aWorkerWhoMovesFreesTheirOldBlock(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(6, 2, 4), Blocks.SMITHING_TABLE);
		BlockPos composter = helper.absolutePos(STATION);
		ServerPlayer player = player(helper);
		Villager worker = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player, worker, new ItemStack(Items.WHEAT), true);
		helper.assertTrue(job(worker) == VillagerProfession.FARMER && site(worker).equals(Optional.of(composter)),
			"setup: wheat gave " + name(job(worker)) + " at " + site(worker));
		rightClick(player, worker, new ItemStack(Items.REDSTONE), true);
		helper.assertTrue(job(worker) == ModVillagers.TINKERER && site(worker).equals(Optional.of(helper.absolutePos(new BlockPos(6, 2, 4)))),
			"redstone gave " + name(job(worker)) + " at " + site(worker));
		Villager next = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		rightClick(player, next, new ItemStack(Items.BONE_MEAL), true);
		helper.assertTrue(job(next) == ModVillagers.COMPOSTER && site(next).equals(Optional.of(composter)),
			"the old composter wasn't freed: bone meal gave the next villager " + name(job(next)) + " at " + site(next));
		helper.succeed();
	}

	/**
	 * README: "stand the villager by the block (within about 4 blocks)". A fletching table 5 blocks away gives no job; one
	 * 4.47 blocks away (4 across, 2 along) does.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aBlockAboutFourBlocksAwayIsNearEnough(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		helper.setBlock(new BlockPos(9, 2, 4), Blocks.FLETCHING_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player, villager, new ItemStack(Items.IRON_AXE), true);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "a table 5 blocks away gave " + name(job(villager)) + " at " + site(villager));
		helper.setBlock(new BlockPos(8, 2, 6), Blocks.FLETCHING_TABLE);
		rightClick(player, villager, new ItemStack(Items.IRON_AXE), true);
		helper.assertTrue(job(villager) == ModVillagers.LUMBERJACK && site(villager).equals(Optional.of(helper.absolutePos(new BlockPos(8, 2, 6)))),
			"a table 4.47 blocks away gave " + name(job(villager)) + " at " + site(villager));
		helper.succeed();
	}

	/**
	 * README: "Where one item fits two blocks (paper, a book...), the block they work at, or else the nearest, decides."
	 * Paper by a mailbox (near) and a lectern (farther) makes a postman; with the lectern nearer, a scholar.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theNearestBlockDecidesWhenAnItemFitsTwo(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		helper.setBlock(new BlockPos(5, 2, 4), ModBlocks.MAILBOX);
		helper.setBlock(new BlockPos(1, 2, 4), Blocks.LECTERN);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player, first, new ItemStack(Items.PAPER), true);
		helper.assertTrue(job(first) == ModVillagers.POSTMAN, "paper next to the mailbox gave " + name(job(first)));
		helper.assertTrue(site(first).equals(Optional.of(helper.absolutePos(new BlockPos(5, 2, 4)))), "the postman works at " + site(first));

		helper.setBlock(new BlockPos(14, 2, 14), ModBlocks.MAILBOX);
		helper.setBlock(new BlockPos(10, 2, 14), Blocks.LECTERN);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(11, 2, 14));
		rightClick(player, second, new ItemStack(Items.PAPER), true);
		helper.assertTrue(job(second) == ModVillagers.SCHOLAR, "paper next to the lectern gave " + name(job(second)));
		helper.assertTrue(site(second).equals(Optional.of(helper.absolutePos(new BlockPos(10, 2, 14)))), "the scholar works at " + site(second));
		helper.succeed();
	}

	/** A job picked with an item survives the villager's chunk being saved and loaded again. */
	//$ gametest_ticks_batch AREA '400' '"stationsSpecReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "stationsSpecReload")
	public void aPickedJobSurvivesSaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.CAULDRON);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		rightClick(player(helper), villager, new ItemStack(Items.GRAVEL), true);
		helper.assertTrue(job(villager) == ModVillagers.SIFTER, "gravel gave " + name(job(villager)));
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		BlockPos cauldron = helper.absolutePos(STATION);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(job(copy) == ModVillagers.SIFTER, "after a reload: " + name(job(copy)));
			helper.assertTrue(site(copy).equals(Optional.of(cauldron)), "after a reload the sifter works at " + site(copy));
			helper.succeed();
		});
	}

	/**
	 * README: "A crafting table, a beehive, a jukebox or a Mailbox never takes a jobless villager by itself" (the crafting
	 * table: StationsGameTests). A bee nest counts as a beehive.
	 */
	//$ gametest_ticks_batch HUGE '1000' '"stationsSpecOnlyByItem"'
	@GameTest(template = HUGE, timeoutTicks = 1000, batch = "stationsSpecOnlyByItem")
	public void aBeehiveJukeboxOrMailboxTakesNobodyByItself(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		List<Block> blocks = List.of(Blocks.BEEHIVE, Blocks.BEE_NEST, Blocks.JUKEBOX, ModBlocks.MAILBOX);
		List<BlockPos> spots = List.of(new BlockPos(3, 2, 3), new BlockPos(25, 2, 3), new BlockPos(3, 2, 25), new BlockPos(25, 2, 25));
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			helper.setBlock(spots.get(i), blocks.get(i));
			villagers.add(helper.spawn(EntityType.VILLAGER, spots.get(i).offset(1, 0, 1)));
		}
		helper.runAfterDelay(900, () -> {
			for (int i = 0; i < blocks.size(); i++) {
				Villager villager = villagers.get(i);
				helper.assertTrue(job(villager) == VillagerProfession.NONE,
					"by the " + BuiltInRegistries.BLOCK.getKey(blocks.get(i)) + ": " + name(job(villager)));
			}
			helper.succeed();
		});
	}

	/**
	 * The owner's answers: "The kept blocks' own job still takes a jobless villager by itself" — the Shop Counter
	 * (Shopkeeper, not the Innkeeper or Pokémon Trader who share it), the Storehouse (Porter) and the Travel Post
	 * (Ferryman). The Training Post's Trainer: CobblemonCompatTests. Which of the jobless villagers takes which block is
	 * the game's choice (a villager may walk to a farther one first), so the check is per block: each is someone's
	 * workstation, for its own job.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"stationsSpecKeptByItself"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "stationsSpecKeptByItself")
	public void theKeptBlocksTakeAJoblessVillagerByThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		List<Block> blocks = List.of(ModBlocks.SHOP_COUNTER, ModBlocks.STOREHOUSE, ModBlocks.TRAVEL_POST);
		List<VillagerProfession> jobs = List.of(ModVillagers.SHOPKEEPER, ModVillagers.PORTER, ModVillagers.FERRYMAN);
		List<BlockPos> spots = List.of(new BlockPos(3, 2, 3), new BlockPos(25, 2, 3), new BlockPos(3, 2, 25));
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			helper.setBlock(spots.get(i), blocks.get(i));
			villagers.add(helper.spawn(EntityType.VILLAGER, spots.get(i).offset(1, 0, 1)));
		}
		helper.succeedWhen(() -> {
			for (int i = 0; i < blocks.size(); i++) {
				BlockPos at = helper.absolutePos(spots.get(i));
				Optional<Villager> worker = villagers.stream().filter(v -> site(v).equals(Optional.of(at))).findFirst();
				helper.assertTrue(worker.isPresent(), "nobody works at the " + BuiltInRegistries.BLOCK.getKey(blocks.get(i)));
				helper.assertTrue(job(worker.get()) == jobs.get(i),
					"at the " + BuiltInRegistries.BLOCK.getKey(blocks.get(i)) + ": a " + name(job(worker.get())) + ", not a " + name(jobs.get(i)));
			}
		});
	}

	/**
	 * The gone blocks leave the creative tab but stay registered, and still drop themselves when broken (old worlds keep
	 * them); the kept blocks are still in the tab and can still be crafted.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCreativeTabAndRecipesMatchThePlan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceKey.create(Registries.CREATIVE_MODE_TAB, AliveWorkplace.id("main")));
		helper.assertTrue(tab != null, "no creative tab aliveworkplace:main");
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
		List<Item> shown = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
		List<String> wrong = new ArrayList<>();
		for (Block block : gone()) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				wrong.add(block + " is no longer registered as ours: " + id);
			}
			if (shown.contains(block.asItem())) {
				wrong.add(id + " is still in the creative tab");
			}
			if (level.getServer().reloadableRegistries().getLootTable(block.getLootTable()) == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
				wrong.add(id + " drops nothing when broken");
			}
		}
		var access = level.registryAccess();
		for (Block block : kept()) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!shown.contains(block.asItem())) {
				wrong.add(id + " is missing from the creative tab");
			}
			if (level.getRecipeManager().getRecipes().stream().noneMatch(r -> r.value().getResultItem(access).is(block.asItem()))) {
				wrong.add(id + " can't be crafted");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		helper.succeed();
	}

	/**
	 * Done when: "no village house or starter build uses a gone block". Every structure the mod ships (village houses,
	 * workshops, the settlers' camp, starter blueprints, research builds), every palette.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noStructureOfOursUsesAGoneBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> goneIds = gone().stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).toString()).toList();
		List<ResourceLocation> ids = level.getStructureManager().listTemplates().filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID))
			.toList();
		helper.assertTrue(ids.size() >= 150, "only " + ids.size() + " structures of ours listed");
		List<String> wrong = new ArrayList<>();
		for (ResourceLocation id : ids) {
			var template = level.getStructureManager().get(id);
			if (template.isEmpty()) {
				wrong.add(id + " doesn't load");
				continue;
			}
			CompoundTag saved = template.get().save(new CompoundTag());
			List<ListTag> palettes = new ArrayList<>();
			if (saved.contains("palette", Tag.TAG_LIST)) {
				palettes.add(saved.getList("palette", Tag.TAG_COMPOUND));
			}
			ListTag many = saved.getList("palettes", Tag.TAG_LIST);
			for (int i = 0; i < many.size(); i++) {
				palettes.add(many.getList(i));
			}
			for (ListTag palette : palettes) {
				for (int i = 0; i < palette.size(); i++) {
					String block = palette.getCompound(i).getString("Name");
					if (goneIds.contains(block)) {
						wrong.add(id + " uses " + block);
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " uses of gone blocks: " + wrong);
		helper.succeed();
	}

	/** Every job the stations give has a name in the game's language (the chat message and the tooltip show it). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStationJobHasAName(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (Stations.Station station : Stations.ALL) {
			for (Stations.Job job : station.jobs()) {
				String shown = Stations.name(job.profession().get()).getString();
				if (shown.startsWith("entity.")) {
					missing.add(shown);
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "untranslated job names: " + missing);
		String chosen = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.job.chosen", "Villager",
			Stations.name(ModVillagers.ORCHARD_KEEPER), Blocks.COMPOSTER.getName()).getString();
		helper.assertTrue(chosen.equals("Villager took the Orchard Keeper job at the Composter."), "the chat message reads: " + chosen);
		String needs = net.minecraft.network.chat.Component.translatable("message.aliveworkplace.job.needs_station",
			Stations.name(ModVillagers.CARPENTER), Blocks.CRAFTING_TABLE.getName()).getString();
		helper.assertTrue(needs.startsWith("The Carpenter job needs a Crafting Table"), "the missing-block message reads: " + needs);
		helper.succeed();
	}
}
