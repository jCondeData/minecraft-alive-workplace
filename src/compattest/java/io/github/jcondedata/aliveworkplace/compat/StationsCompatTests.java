package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 21.1a with Cobblemon installed: the plan's Pokémon jobs start at their shared block with their item (a Ball Smith
 * at a smithing table with an apricorn, a Pokémon Trader at a Shop Counter with a Poké Ball, and since 21.1c a Fossil
 * Scientist at Cobblemon's Fossil Analyzer with a fossil), and the Training Post's own job, the Trainer, takes a jobless
 * villager by itself.
 */
public class StationsCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** Helper y = 1 is the floor here too. */
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	private static Item cobblemon(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == net.minecraft.world.item.Items.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("no cobblemon:" + path);
		}
		return item;
	}

	/** A client's sneak-right-click on {@code villager} holding {@code stack}, through the server's packet handler. */
	private static void sneakClick(ServerPlayer player, Villager villager, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private record Row(Block block, Item item, VillagerProfession job) {
	}

	static Block fossilAnalyzer() {
		Block block = BuiltInRegistries.BLOCK.get(ModVillagers.FOSSIL_ANALYZER_BLOCK);
		if (block == Blocks.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("no " + ModVillagers.FOSSIL_ANALYZER_BLOCK);
		}
		return block;
	}

	/**
	 * The owner's 21.1c: the Fossil Scientist works at Cobblemon's Fossil Analyzer, the block of its revival machine, not
	 * a block of ours. A fossil by a Training Post no longer makes one (it says the job needs a Fossil Analyzer), and a
	 * jobless villager by an analyzer doesn't take it by themselves: only a fossil gives the job.
	 */
	//$ gametest_ticks_batch AREA '400' '"theFossilScientistWorksAtTheFossilAnalyzer"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "theFossilScientistWorksAtTheFossilAnalyzer")
	public void theFossilScientistWorksAtTheFossilAnalyzer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager atPost = helper.spawn(EntityType.VILLAGER, STANDING);
		sneakClick(player, atPost, new ItemStack(cobblemon("dome_fossil")));
		helper.assertTrue(atPost.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a fossil at a Training Post gave " + name(atPost.getVillagerData().getProfession()));
		atPost.discard();
		helper.setBlock(STATION, Blocks.AIR);
		BlockPos analyzer = new BlockPos(12, 2, 12);
		helper.setBlock(analyzer, fossilAnalyzer());
		helper.assertTrue(Stations.at(fossilAnalyzer()).map(s -> !s.byItself() && s.has(ModVillagers.FOSSIL_SCIENTIST)).orElse(false),
			"the Fossil Analyzer isn't the Fossil Scientist's station (only by item)");
		// Scientists who worked at a Fossil Lab or a Training Post before 21.1c (the owner's server) keep working there.
		var pois = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE);
		for (var old : List.of(ModVillagers.FOSSIL_LAB_POI, ModVillagers.TRAINING_POST_POI)) {
			helper.assertTrue(ModVillagers.FOSSIL_SCIENTIST.heldJobSite().test(pois.getHolderOrThrow(old)), "a scientist loses their " + old.location());
		}
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 13));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.NONE && site(jobless).isEmpty(),
				"a jobless villager took the Fossil Analyzer by themselves: " + name(jobless.getVillagerData().getProfession()));
			helper.succeed();
		});
	}

	/** Each Pokémon job of the plan: a jobless villager by the block, sneak-right-clicked with the item, takes it there. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePokemonJobsStartFromTheirBlockAndItem(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		List<Row> rows = List.of(
			new Row(Blocks.SMITHING_TABLE, cobblemon("red_apricorn"), ModVillagers.BALL_SMITH),
			new Row(ModBlocks.SHOP_COUNTER, cobblemon("poke_ball"), ModVillagers.POKEMON_TRADER),
			new Row(fossilAnalyzer(), cobblemon("dome_fossil"), ModVillagers.FOSSIL_SCIENTIST),
			new Row(ModBlocks.TRAINING_POST, net.minecraft.world.item.Items.GOLD_BLOCK, ModVillagers.TRAINER_LEADER),
			new Row(ModBlocks.TRAINING_POST, net.minecraft.world.item.Items.BOOK, ModVillagers.TUTOR));
		for (Row row : rows) {
			helper.setBlock(STATION, row.block());
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			sneakClick(player, villager, new ItemStack(row.item()));
			String what = BuiltInRegistries.ITEM.getKey(row.item()) + " at " + BuiltInRegistries.BLOCK.getKey(row.block());
			helper.assertTrue(villager.getVillagerData().getProfession() == row.job(),
				what + " gave " + name(villager.getVillagerData().getProfession()) + ", not " + name(row.job()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), what + ": works at " + site(villager));
			villager.discard();
			helper.setBlock(STATION, Blocks.AIR);
		}
		helper.succeed();
	}

	/**
	 * A Poké Ball picks two jobs (Trainer at a Training Post, Pokémon Trader at a Shop Counter): the block the villager
	 * works at decides, so a Move Tutor becomes a Trainer at their post, and back with a book.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPokeBallMakesATutorATrainerAtTheirPost(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		helper.setBlock(new BlockPos(6, 2, 5), ModBlocks.SHOP_COUNTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		sneakClick(player, villager, new ItemStack(net.minecraft.world.item.Items.BOOK));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TUTOR, "a book gave " + name(villager.getVillagerData().getProfession()));
		sneakClick(player, villager, new ItemStack(cobblemon("poke_ball")));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TRAINER, "a Poké Ball gave the tutor " + name(villager.getVillagerData().getProfession()));
		helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the trainer works at " + site(villager));
		helper.succeed();
	}

	/** The Training Post's own job takes a jobless villager by itself: a Trainer, not the Leader, Tutor or Scientist. */
	//$ gametest_ticks_batch AREA '1200' '"stationsCompatTrainer"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsCompatTrainer")
	public void aTrainingPostTakesATrainerByItself(GameTestHelper helper) {
		helper.setDayTime(2000);
		for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			e.discard();
		}
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.TRAINER, "by the Training Post: " + name(villager.getVillagerData().getProfession()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the trainer works at " + site(villager));
		});
	}

	/** A test player like GameTestHelper's mock one, who also keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listeningPlayer(GameTestHelper helper, List<String> seen) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-listener"), false);
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
	 * Tester, 21.1c: the player is told in plain words which block the Fossil Scientist needs. A fossil by a Training
	 * Post: "needs a Fossil Analyzer"; by an analyzer: "took the Fossil Scientist job at the Fossil Analyzer"; a second
	 * villager by the same (now worked) analyzer: "needs a free Fossil Analyzer". Never the Training Post or a raw id.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theFossilHandOverMessagesNameTheFossilAnalyzer(GameTestHelper helper) {
		List<String> seen = new java.util.ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.setBlock(STATION, ModBlocks.TRAINING_POST);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		sneakClick(player, first, new ItemStack(cobblemon("dome_fossil")));
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Fossil Scientist job needs a Fossil Analyzer")),
			"a fossil by a Training Post told the player: " + seen);
		seen.clear();
		helper.setBlock(STATION, fossilAnalyzer());
		sneakClick(player, first, new ItemStack(cobblemon("dome_fossil")));
		helper.assertTrue(seen.contains("Villager took the Fossil Scientist job at the Fossil Analyzer."),
			"a fossil by a Fossil Analyzer told the player: " + seen);
		seen.clear();
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 4));
		sneakClick(player, second, new ItemStack(cobblemon("helix_fossil")));
		helper.assertTrue(second.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a second villager took the worked analyzer: " + name(second.getVillagerData().getProfession()));
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Fossil Scientist job needs a free Fossil Analyzer")),
			"a fossil by a worked Fossil Analyzer told the player: " + seen);
		helper.succeed();
	}

	/**
	 * Tester, 21.1c, the owner's live server: a Fossil Analyzer placed while 0.137.0 ran had no workstation record (it
	 * wasn't one then). Where its chunk section already had a record (a bed, a composter: most houses), the game never
	 * looks at the section's blocks again, so the analyzer must get its record when the chunk loads, and a fossil then
	 * makes the villager by it a scientist there. Here: the analyzer without its record, then exactly what loading does.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void anAnalyzerPlacedBeforeTheUpdateBecomesAWorkstation(GameTestHelper helper) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		var poi = level.getPoiManager();
		BlockPos spot = new BlockPos(3, 2, 3);
		BlockPos at = helper.absolutePos(spot);
		BlockPos composter = net.minecraft.core.SectionPos.of(at).equals(net.minecraft.core.SectionPos.of(helper.absolutePos(spot.east())))
			? spot.east() : spot.west();
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(spot, fossilAnalyzer());
		helper.assertTrue(net.minecraft.core.SectionPos.of(at).equals(net.minecraft.core.SectionPos.of(helper.absolutePos(composter))),
			"setup: not one chunk section");
		poi.remove(at); // 0.137.0 kept no record for Cobblemon's analyzer
		helper.assertTrue(poi.getType(at).isEmpty(), "setup: the record is still there");
		var chunk = level.getChunkAt(at);
		poi.checkConsistencyWithBlocks(net.minecraft.core.SectionPos.of(at), chunk.getSection(chunk.getSectionIndex(at.getY()))); // the chunk loads
		helper.assertTrue(poi.getType(at).map(h -> h.is(ModVillagers.FOSSIL_ANALYZER_POI)).orElse(false),
			"an analyzer from before the update is no workstation after its chunk loads: " + poi.getType(at));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		sneakClick(player, villager, new ItemStack(cobblemon("dome_fossil")));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.FOSSIL_SCIENTIST && site(villager).equals(Optional.of(at)),
			"a fossil by the old analyzer gave " + name(villager.getVillagerData().getProfession()) + " at " + site(villager));
		helper.succeed();
	}

	/**
	 * Tester, 21.1c: the analyzer lights up while Cobblemon's machine scans (its "on" state): the scientist keeps it.
	 * Broken, the scientist keeps the job (no crash, nothing else breaks) and takes the next free analyzer by
	 * themselves, as our other workers take a free block of their kind again (21.1a, decision 1).
	 */
	//$ gametest_ticks_batch AREA '900' '"fossilAnalyzerBroken"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "fossilAnalyzerBroken")
	public void aScientistWhoseAnalyzerBreaksTakesTheNextOne(GameTestHelper helper) {
		helper.setDayTime(2000);
		for (var e : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			e.discard();
		}
		helper.setBlock(STATION, fossilAnalyzer());
		BlockPos first = helper.absolutePos(STATION);
		Villager scientist = helper.spawn(EntityType.VILLAGER, STANDING);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		sneakClick(player, scientist, new ItemStack(cobblemon("dome_fossil")));
		helper.assertTrue(scientist.getVillagerData().getProfession() == ModVillagers.FOSSIL_SCIENTIST, "setup: not a scientist");
		var state = helper.getLevel().getBlockState(first);
		var on = state.getBlock().getStateDefinition().getProperty("on");
		helper.assertTrue(on instanceof net.minecraft.world.level.block.state.properties.BooleanProperty, "setup: the analyzer has no 'on' state");
		var lit = (net.minecraft.world.level.block.state.properties.BooleanProperty) on;
		helper.getLevel().setBlockAndUpdate(first, state.setValue(lit, true)); // what the machine does while it scans
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(site(scientist).equals(Optional.of(first)), "a scanning analyzer lost its scientist: " + site(scientist));
			helper.getLevel().destroyBlock(first, true);
			BlockPos next = new BlockPos(9, 2, 9);
			helper.runAfterDelay(100, () -> {
				helper.assertTrue(scientist.getVillagerData().getProfession() == ModVillagers.FOSSIL_SCIENTIST,
					"breaking the analyzer took the job: " + name(scientist.getVillagerData().getProfession()));
				helper.assertTrue(site(scientist).isEmpty(), "the scientist still works at the broken analyzer: " + site(scientist));
				helper.setBlock(next, fossilAnalyzer());
				helper.succeedWhen(() -> helper.assertTrue(site(scientist).equals(Optional.of(helper.absolutePos(next)))
					&& scientist.getVillagerData().getProfession() == ModVillagers.FOSSIL_SCIENTIST,
					"the scientist works at " + site(scientist) + " as " + name(scientist.getVillagerData().getProfession())));
			});
		});
	}
}
