package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1101-1538): adversarial checks of B341 (the Guild Charter from the off hand), B343 (the Gray Cough healer
 * takes what is given instead of the story swallowing the click) and B240's invariant (no feature turns a chest's stack
 * of one item into another).
 *
 * <p>Expectations from the bug entries and the wiki, not the code: guilds.md "sneak-right-click a Master villager with
 * it"; B341 "the off hand ... exactly as from the main hand"; B343 "a Gift, tonic, Remedy or Guild Charter in hand (or the
 * off hand, the main one empty) is given; other items (a sword, a map) still talk"; B240 "Expected: never changes items".
 */
public class QaBuilderBugsVerifyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String TALL = "aliveworkplace_test:tall_area";

	/**
	 * A player's main hand made empty: a new player is handed the guide book a few ticks after joining, into the
	 * selected slot, so an "empty main hand" click must clear it right before.
	 */
	private static void emptyMainHand(ServerPlayer p) {
		p.getInventory().clearOrCountMatchingItems(st -> st.is(ModItems.GUIDE_BOOK), -1, p.inventoryMenu.getCraftSlots());
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	// --- B341: the charter's refusal from the off hand -----------------------------------------------------------

	/**
	 * B341, the refusal side: a charter in the off hand (main hand empty), sneak-clicked on a Journeyman builder, is
	 * refused exactly as from the main hand: the same "isn't a Master" line with her name, the charter kept, nobody
	 * chartered, no trade screen. Afterwards an empty-handed sneak-click still gets the builder's status line.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVerifyCharterRefusedOff"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVerifyCharterRefusedOff")
	public void offHandCharterOnANonMasterIsRefusedLikeTheMainHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaStageSixGameTests.guildVillage(helper);
		Villager jo = QaStageSixGameTests.guildVillager(helper, new BlockPos(6, 2, 6), "Jo", ModVillagers.BUILDER, 3);
		helper.runAfterDelay(5, () -> {
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qavr"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);

			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));
			QaStageSixGameTests.clickVillager(p, jo, c.seen());
			List<String> mainKeys = QaStageFourGameTests.keys(c.seen());
			helper.assertTrue(mainKeys.contains("message.aliveworkplace.guild.not_master"), "main hand: not refused, saw " + mainKeys);
			helper.assertTrue(p.getMainHandItem().getCount() == 3, "main hand: a refused charter was used");
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.closeContainer();

			helper.runAfterDelay(3, () -> {
				c.seen().clear();
				emptyMainHand(p);
				p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));
				QaStageSixGameTests.clickVillager(p, jo, c.seen());
				Component refused = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.guild.not_master");
				helper.assertTrue(refused != null, "off hand: not told Jo isn't a Master, saw " + QaStageFourGameTests.keys(c.seen()));
				QaStageFourGameTests.assertRendered(helper, refused, "Jo");
				helper.assertTrue(QaStageFourGameTests.keys(c.seen()).equals(mainKeys),
					"off hand answered differently from the main hand: " + QaStageFourGameTests.keys(c.seen()) + " vs " + mainKeys);
				helper.assertTrue(p.getOffhandItem().is(ModItems.GUILD_CHARTER) && p.getOffhandItem().getCount() == 3,
					"off hand: a refused charter was used: " + p.getOffhandItem());
				helper.assertTrue(ModAttachments.GUILD_MASTER.get(jo) == null, "off hand: Jo leads a guild");
				helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a trade screen opened on the refusal");
			});
			helper.runAfterDelay(6, () -> {
				c.seen().clear();
				emptyMainHand(p);
				p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				QaStageSixGameTests.clickVillager(p, jo, c.seen());
				helper.assertTrue(QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.status.no_bench") != null,
					"empty hands after the charter: no status line, saw " + QaStageFourGameTests.keys(c.seen()));
				helper.succeed();
			});
		});
	}

	/**
	 * B341: the off hand's charter on a Master is used once per charter: a second sneak-click on the same new Guild
	 * Master doesn't use another charter or tell of a second charter.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVerifyCharterTwiceOff"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVerifyCharterTwiceOff")
	public void offHandCharterIsUsedOnceOnAMaster(GameTestHelper helper) {
		VillageHallBlockEntity hall = QaStageSixGameTests.guildVillage(helper);
		Villager dara = QaStageSixGameTests.guildVillager(helper, new BlockPos(8, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			QaStageSixGameTests.ready(hall, VillageRanks.Rank.VILLAGE);
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qavt"));
			ServerPlayer p = c.player();
			p.setShiftKeyDown(true);
			emptyMainHand(p);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.GUILD_CHARTER, 3));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara) != null, "off hand: Dara isn't chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			Component chartered = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.guild.chartered");
			helper.assertTrue(chartered != null, "off hand: not told Dara was chartered, saw " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, chartered, "Dara");
			helper.assertTrue(p.getOffhandItem().getCount() == 2, "off hand: charters left " + p.getOffhandItem());
			helper.runAfterDelay(3, () -> {
				c.seen().clear();
				p.closeContainer();
				emptyMainHand(p);
				QaStageSixGameTests.clickVillager(p, dara, c.seen());
				helper.assertTrue(p.getOffhandItem().getCount() == 2, "off hand: a second charter was used on a Guild Master: " + p.getOffhandItem()
					+ ", saw " + QaStageFourGameTests.keys(c.seen()));
				helper.assertTrue(QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.guild.chartered") == null, "chartered twice");
				helper.succeed();
			});
		});
	}

	// --- B343: the Gray Cough healer --------------------------------------------------------------------------

	private static final BlockPos SICK_HALL = new BlockPos(15, 2, 15);

	private record Story(ServerLevel level, ArcState state, Villager nurse) {
	}

	/** The Gray Cough under way by a hall of eight villagers with a seasoned healer, as QaStageSixGameTests sets it up. */
	private static Story sickness(GameTestHelper helper, String nurseName) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		BlockPos hall = helper.absolutePos(SICK_HALL);
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		SicknessStory.LOCATOR = (l, from, herb) -> {
			String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(herb).getPath();
			return switch (path) {
				case "sea_lavender" -> from.offset(200, 0, 0);
				case "mirewort" -> from.offset(400, 0, 0);
				case "glowcap" -> from.offset(600, 0, 0);
				default -> null;
			};
		};
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			SicknessStory.LOCATOR = locator;
		});
		helper.setBlock(SICK_HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(11 + (i % 4) * 3, 2, 12 + (i / 4) * 3));
			v.setNoAi(true);
			v.setCustomName(Component.literal("Villager " + (i + 1)));
		}
		BlockPos station = new BlockPos(20, 2, 20);
		helper.setBlock(station, Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(21, 2, 20));
		nurse.setNoAi(true);
		nurse.setCustomName(Component.literal(nurseName));
		Jobs.employ(level, nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		nurse.setVillagerXp(70);
		level.setDayTime(Math.floorDiv(level.getDayTime(), VillageNeeds.DAY) * VillageNeeds.DAY + VillageNeeds.DAY + 1000);
		ArcState s = Arcs.start(level, hall, Arcs.find("the_sickness").orElseThrow());
		helper.assertTrue(s != null, "the arc didn't start");
		return new Story(level, s, nurse);
	}

	private static boolean anyKeyStarts(List<Component> seen, String prefix) {
		return QaStageFourGameTests.keys(seen).stream().anyMatch(k -> k.startsWith(prefix));
	}

	/**
	 * B343 from the off hand (main hand empty), and a Remedy specifically: mid-story, a tonic in the off hand is offered
	 * (the healer's "no use" line, the tonic kept) and a Remedy in the off hand is given (a Remedy line, one used, the
	 * bottle back), neither telling the story (no maps, chapter 0); a Remedy from the main hand is given the same way.
	 * Then a sword in the main hand still gets the story's talk (three maps) and stays in hand.
	 */
	//$ gametest_batch TALL '"qaVerifyHealerOffHand"'
	@GameTest(template = TALL, batch = "qaVerifyHealerOffHand")
	public void healerTakesOffHandTonicAndRemediesMidStory(GameTestHelper helper) {
		Story story = sickness(helper, "Nurse Cal");
		ArcState s = story.state();
		Villager nurse = story.nurse();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qavh"));
		ServerPlayer p = c.player();

		emptyMainHand(p);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.BUILDERS_TEA));
		QaStageSixGameTests.clickVillager(p, nurse, c.seen());
		helper.assertTrue(QaStageFourGameTests.keys(c.seen()).contains("message.aliveworkplace.tonic.no_use"),
			"off hand: the tonic wasn't offered, saw " + QaStageFourGameTests.keys(c.seen()));
		helper.assertTrue(p.getOffhandItem().is(ModItems.BUILDERS_TEA), "off hand: the tonic went: " + p.getOffhandItem());
		helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 0 && s.chapter == 0, "off hand: the tonic's click told the story");
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand tonic: a trade screen opened");

		helper.runAfterDelay(3, () -> {
			c.seen().clear();
			emptyMainHand(p);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.REMEDY, 2));
			QaStageSixGameTests.clickVillager(p, nurse, c.seen());
			helper.assertTrue(anyKeyStarts(c.seen(), "message.aliveworkplace.remedy."), "off hand: the Remedy wasn't given, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getOffhandItem().is(ModItems.REMEDY) && p.getOffhandItem().getCount() == 1, "off hand: remedies left " + p.getOffhandItem());
			helper.assertTrue(p.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "off hand: no bottle back");
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 0 && s.chapter == 0, "off hand: the Remedy's click told the story");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand Remedy: a trade screen opened");
		});
		helper.runAfterDelay(6, () -> {
			c.seen().clear();
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY, 2));
			QaStageSixGameTests.clickVillager(p, nurse, c.seen());
			helper.assertTrue(anyKeyStarts(c.seen(), "message.aliveworkplace.remedy."), "main hand: the Remedy wasn't offered, saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 0 && s.chapter == 0, "main hand: the Remedy's click told the story");
		});
		helper.runAfterDelay(9, () -> {
			c.seen().clear();
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			QaStageSixGameTests.clickVillager(p, nurse, c.seen());
			helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 3 && s.chapter == 1, "a sword didn't get the story's talk: chapter " + s.chapter
				+ ", maps " + p.getInventory().countItem(Items.FILLED_MAP) + ", saw " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(p.getMainHandItem().is(Items.IRON_SWORD) && p.getMainHandItem().getCount() == 1, "the sword went: " + p.getMainHandItem());
			helper.assertTrue(nurse.getMainHandItem().isEmpty() || !nurse.getMainHandItem().is(Items.IRON_SWORD), "the healer took the sword");
			helper.assertTrue(p.containerMenu == p.inventoryMenu, "sword: a trade screen opened");
			helper.succeed();
		});
	}

	/** B343: an empty map in hand isn't a gift: mid-story it still gets the story's talk (three filled maps), and the map stays in hand. */
	//$ gametest_batch TALL '"qaVerifyHealerMap"'
	@GameTest(template = TALL, batch = "qaVerifyHealerMap")
	public void healerStillTellsTheStoryToAPlayerHoldingAMap(GameTestHelper helper) {
		Story story = sickness(helper, "Nurse Dot");
		ArcState s = story.state();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qavm"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MAP));
		QaStageSixGameTests.clickVillager(p, story.nurse(), c.seen());
		helper.assertTrue(p.getInventory().countItem(Items.FILLED_MAP) == 3 && s.chapter == 1, "a map in hand didn't get the story's talk: chapter " + s.chapter
			+ ", maps " + p.getInventory().countItem(Items.FILLED_MAP) + ", saw " + QaStageFourGameTests.keys(c.seen()));
		helper.assertTrue(p.getInventory().countItem(Items.MAP) == 1, "the empty map went: " + p.getInventory().countItem(Items.MAP));
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "map: a trade screen opened");
		helper.succeed();
	}

	// --- B240: no stack turns into another item ---------------------------------------------------------------

	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ItemStack[] BYSTANDERS = {new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.WHEAT, 64), new ItemStack(Items.RAW_IRON, 64)};

	private static void fillBystanders(Container chest) {
		for (int i = 0; i < BYSTANDERS.length; i++) {
			chest.setItem(i, BYSTANDERS[i].copy());
		}
	}

	/**
	 * The chest still holds exactly 64 iron ingots, 64 wheat and 64 raw iron in its first three slots; any other slot is
	 * empty or holds one of {@code added} (what the worker may store there, e.g. a farmer's harvest).
	 */
	private static void assertBystanders(GameTestHelper helper, Container chest, String who, Item... added) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack now = chest.getItem(i);
			if (i < BYSTANDERS.length) {
				Item want = BYSTANDERS[i].getItem();
				helper.assertTrue(now.is(want) && now.getCount() == 64, who + ": slot " + i + " was 64 " + want + ", now " + now);
			} else {
				helper.assertTrue(now.isEmpty() || java.util.Arrays.stream(added).anyMatch(now::is), who + ": slot " + i + " was empty, now " + now);
			}
		}
	}

	/**
	 * B240: a builder finishing the test hut from the chest by her bench never changes the items of the chest beside it
	 * (64 iron ingots, 64 wheat, 64 raw iron): every stack keeps its item and count. No Create here.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaVerifyB240Builder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaVerifyB240Builder")
	public void builderFinishingABuildChangesNoNearbyStack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos store = new BlockPos(2, 2, 4);
		BlockPos bystander = new BlockPos(0, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(store, Blocks.CHEST);
		helper.setBlock(bystander, Blocks.CHEST);
		Container storeChest = helper.getBlockEntity(store);
		storeChest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		storeChest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		storeChest.setItem(2, new ItemStack(Items.OAK_DOOR));
		storeChest.setItem(3, new ItemStack(Items.TORCH));
		storeChest.setItem(4, new ItemStack(Items.IRON_INGOT, 64));
		Container other = helper.getBlockEntity(bystander);
		fillBystanders(other);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the builder didn't take the test hut");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " missing=" + site.missing());
			assertBystanders(helper, other, "the chest beside the store");
			helper.assertTrue(storeChest.countItem(Items.IRON_INGOT) == 64, "the store's iron ingots: " + storeChest.countItem(Items.IRON_INGOT));
		});
	}

	/**
	 * B240: a farmer harvesting a field into the chest by the composter never changes the items of the chest beside it
	 * (64 iron ingots, 64 wheat, 64 raw iron), nor the iron ingots and raw iron already in their own chest.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaVerifyB240Farmer"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaVerifyB240Farmer")
	public void farmerHarvestingChangesNoNearbyStack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos water = new BlockPos(10, 1, 10);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 12))) {
			if (p.equals(water)) {
				helper.setBlock(p, Blocks.WATER);
			} else {
				helper.setBlock(p, Blocks.FARMLAND);
				helper.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			}
		}
		helper.setDayTime(2000);
		BlockPos composter = new BlockPos(2, 2, 2);
		BlockPos own = new BlockPos(2, 2, 4);
		BlockPos bystander = new BlockPos(0, 2, 4);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(own, Blocks.CHEST);
		helper.setBlock(bystander, Blocks.CHEST);
		Container ownChest = helper.getBlockEntity(own);
		ownChest.setItem(0, new ItemStack(Items.IRON_INGOT, 64));
		ownChest.setItem(1, new ItemStack(Items.RAW_IRON, 64));
		Container other = helper.getBlockEntity(bystander);
		fillBystanders(other);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(12, 1, 12))));
		helper.succeedWhen(() -> {
			int harvested = ModAttachments.FARM_HARVESTED.getOrElse(villager, 0);
			helper.assertTrue(harvested >= 24, "only " + harvested + " harvested");
			helper.assertTrue(ownChest.countItem(Items.WHEAT) + other.countItem(Items.WHEAT) >= 64 + 20, "the harvest isn't in the chests yet");
			assertBystanders(helper, other, "the chest beside the farmer's", Items.WHEAT, Items.WHEAT_SEEDS, Items.BONE_MEAL);
			helper.assertTrue(ownChest.getItem(0).is(Items.IRON_INGOT) && ownChest.getItem(0).getCount() == 64, "the farmer's chest's iron ingots: " + ownChest.getItem(0));
			helper.assertTrue(ownChest.getItem(1).is(Items.RAW_IRON) && ownChest.getItem(1).getCount() == 64, "the farmer's chest's raw iron: " + ownChest.getItem(1));
		});
	}
}
