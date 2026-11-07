package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Pathfinder;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (qa-lane-c-1031-1553): verifies six landed village fixes from their specs, on the sides their own tests left out.
 * B707 every job_level line any data file asks for, a real count, another mod's trade; B710 a Hero's real purchase
 * landing the owner's whole price, a cured (gossip) shopkeeper, the owner buying, and the vanilla villager next door
 * still discounted; B711 a villager tagged by hand as the part; B673 arrival at a Stronghold and Trial Chambers; B674
 * zero, rations split over two chests, and the real sneak-right-click; B691 a post broken, a second player, a post
 * placed by the player and 0 known.
 */
public class QaVillage1031VerifyGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B707: job_level requirement lines -------------------------------------------------------------------------

	private static void jobLevels(JsonElement json, List<JsonObject> out) {
		if (json.isJsonObject()) {
			JsonObject o = json.getAsJsonObject();
			if (o.has("type") && o.get("type").isJsonPrimitive() && "job_level".equals(o.get("type").getAsString())) {
				out.add(o);
			}
			o.entrySet().forEach(e -> jobLevels(e.getValue(), out));
		} else if (json.isJsonArray()) {
			for (JsonElement e : (JsonArray) json) {
				jobLevels(e, out);
			}
		}
	}

	/**
	 * B707: every job_level condition in every one of the mod's data files (Wonders, Legends, quests, arcs and the rest)
	 * reads its trade's real plural ("Fishermen at Master or above: 0 of 1"), never "Fishermans", a raw key or the
	 * fallback meant for another mod's trades.
	 */
	//$ gametest_ticks_batch BIG '40' '"qaVerify1031JobLevelFiles"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaVerify1031JobLevelFiles")
	public void everyJobLevelLineInTheDataFilesReadsItsPlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(9, 2, 9), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		List<JsonObject> found = new ArrayList<>();
		List<String> files = new ArrayList<>();
		for (String folder : List.of("wonders", "legends", "quests", "arcs", "edicts", "guilds", "heart_events", "research_trees", "disasters",
			"realm_research", "raider_cultures")) {
			Map<ResourceLocation, Resource> res = helper.getLevel().getServer().getResourceManager().listResources(folder,
				id -> id.getPath().endsWith(".json") && AliveWorkplace.MOD_ID.equals(id.getNamespace()));
			for (Map.Entry<ResourceLocation, Resource> e : res.entrySet()) {
				int before = found.size();
				try (Reader r = e.getValue().openAsReader()) {
					jobLevels(JsonParser.parseReader(r), found);
				} catch (Exception ex) {
					throw new AssertionError("couldn't read " + e.getKey() + ": " + ex);
				}
				if (found.size() > before) {
					files.add(e.getKey().getPath());
				}
			}
		}
		helper.assertTrue(found.size() >= 10, "only " + found.size() + " job_level conditions found in the data files: " + files);
		List<String> bad = new ArrayList<>();
		for (JsonObject o : found) {
			ResourceLocation job = ResourceLocation.parse(o.get("job").getAsString());
			String line = Conditions.parse(o).progress(helper.getLevel(), hall).line().getString();
			Component plural = Words.professionPlural(job);
			if (plural == null || !line.startsWith(plural.getString() + " at ") || line.contains("mans ") || line.contains("aliveworkplace.")
				|| line.contains("entity.minecraft") || line.contains("%") || line.startsWith("Villagers working as")) {
				bad.add(job + " -> " + line);
			}
		}
		AliveWorkplace.LOG.info("QA B707: {} job_level lines in {} files checked", found.size(), files.size());
		helper.assertTrue(bad.isEmpty(), "job_level lines that don't read their trade's plural: " + bad);
		helper.succeed();
	}

	/**
	 * B707 with real villagers: two Master fishermen and an Apprentice by the hall, the Lighthouse's own condition (one
	 * Master fisherman) and a two-Master one read "Fishermen at Master or above: 2 of 1" and ": 2 of 2"; the Apprentice
	 * doesn't count toward a Master line, and counts toward an Apprentice one (3 of 3).
	 */
	//$ gametest_ticks_batch BIG '40' '"qaVerify1031JobLevelCount"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaVerify1031JobLevelCount")
	public void jobLevelLineCountsRealFishermen(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(9, 2, 9), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		int[] levels = {5, 5, 2};
		for (int i = 0; i < levels.length; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6 + i * 2, 2, 6));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FISHERMAN).setLevel(levels[i]));
		}
		String one = line(helper, hall, "minecraft:fisherman", 5, 1);
		String two = line(helper, hall, "minecraft:fisherman", 5, 2);
		String apprentice = line(helper, hall, "minecraft:fisherman", 2, 3);
		helper.assertTrue(one.equals("Fishermen at Master or above: 2 of 1"), "the Lighthouse's line reads: " + one);
		helper.assertTrue(two.equals("Fishermen at Master or above: 2 of 2"), "a two-Master line reads: " + two);
		helper.assertTrue(apprentice.equals("Fishermen at Apprentice or above: 3 of 3"), "an Apprentice line reads: " + apprentice);
		helper.succeed();
	}

	/**
	 * B707's fallback: a trade from another mod (no plural in the lang) never gets an "s" stuck on; it reads "Villagers
	 * working as ... at Journeyman or above: 0 of 3". And every job the fix's sentence can name of ours ends in a real
	 * plural: the -man trades end in "men".
	 */
	//$ gametest_ticks_batch BIG '40' '"qaVerify1031JobLevelOther"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qaVerify1031JobLevelOther")
	public void anotherModsTradeReadsVillagersWorkingAs(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(9, 2, 9), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		String other = line(helper, hall, "othermod:glassblower", 3, 3);
		helper.assertTrue(other.startsWith("Villagers working as ") && other.endsWith(" at Journeyman or above: 0 of 3") && !other.contains("glassblowers"),
			"another mod's trade reads: " + other);
		for (String job : List.of("minecraft:fisherman", "aliveworkplace:postman", "aliveworkplace:ferryman")) {
			String l = line(helper, hall, job, 1, 2);
			helper.assertTrue(l.split(" ")[0].endsWith("men") && l.endsWith("at Novice or above: 0 of 2"), "the line for " + job + " reads: " + l);
		}
		helper.succeed();
	}

	private static String line(GameTestHelper helper, BlockPos hall, String job, int level, int count) {
		return Conditions.parse(JsonParser.parseString("{\"type\": \"job_level\", \"job\": \"" + job + "\", \"level\": " + level + ", \"count\": " + count + "}")
			.getAsJsonObject()).progress(helper.getLevel(), hall).line().getString();
	}

	// ---- B710: a player's shop asks the owner's price -------------------------------------------------------------

	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static int emeralds(Container c) {
		return c.countItem(Items.EMERALD);
	}

	/**
	 * B710 through a real purchase: a Hero of the Village buys the owner's 20-emerald diamond block on the trade screen;
	 * with 19 emeralds nothing is sold, with 20 the sale goes through and the owner's chest gets all 20 (not 14).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVerify1031HeroBuys"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVerify1031HeroBuys")
	public void aHeroBuysAndTheOwnersChestGetsTheWholePrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 20));
		Container chest = helper.getBlockEntity(CHEST);
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVHero"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 1));
		p.setShiftKeyDown(Money.cobbleDollars());

		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 19));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		p.closeContainer();
		int boughtShort = p.getInventory().countItem(Items.DIAMOND_BLOCK);
		helper.assertTrue(boughtShort == 0 && emeralds(chest) == 0, "with 19 emeralds a Hero bought " + boughtShort + " diamond blocks; the chest has "
			+ emeralds(chest) + " emeralds");
		p.getInventory().clearContent();

		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open the second time: " + p.containerMenu);
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 20));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		p.closeContainer();
		int bought = p.getInventory().countItem(Items.DIAMOND_BLOCK);
		int left = p.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(bought == 1, "a Hero with 20 emeralds bought " + bought + " diamond blocks");
		helper.assertTrue(emeralds(chest) == 20 && left == 0, "the owner's chest got " + emeralds(chest) + " emeralds for a 20-emerald sale; the Hero kept " + left);
		helper.succeed();
	}

	/**
	 * B710's other vanilla discount: a buyer who cured the shopkeeper (vanilla's big "major positive" gossip) and holds
	 * Hero of the Village is still asked the owner's 20; and so is the shop's owner buying from their own shop.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVerify1031GossipOwner"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVerify1031GossipOwner")
	public void aCuredShopkeeperAndTheOwnerStillAskTheOwnersPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		ShopCounterBlockEntity counter = QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 20));
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVCured"));
		ServerPlayer p = buyer.player();
		keeper.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, 20);
		keeper.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 25);
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 0));
		p.setShiftKeyDown(Money.cobbleDollars());
		helper.assertTrue(keeper.getPlayerReputation(p) > 0, "setup: the cure gossip didn't take: " + keeper.getPlayerReputation(p));
		int cured = askedEmeralds(helper, p, keeper, buyer);
		counter.setOwner(p.getUUID(), p.getName().getString());
		int owner = askedEmeralds(helper, p, keeper, buyer);
		helper.assertTrue(cured == 20, "a Hero who cured the shopkeeper is asked " + cured + " emeralds for the owner's 20");
		helper.assertTrue(owner == 20, "the owner, a Hero with cure gossip, is asked " + owner + " emeralds at their own 20-emerald shop");
		helper.succeed();
	}

	private static int askedEmeralds(GameTestHelper helper, ServerPlayer p, Villager v, QaStageFiveGameTests.Client buyer) {
		QaStageFourGameTests.pressOn(p, v, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open for " + v.getName().getString() + ": " + p.containerMenu);
		MerchantOffer offer = ((MerchantMenu) p.containerMenu).getOffers().get(0);
		ItemStack cost = offer.getCostA();
		int n = cost.is(Items.EMERALD) ? cost.getCount() : -1;
		p.closeContainer();
		return n;
	}

	/**
	 * B710's fix must stay on the shopkeeper: a vanilla farmer standing next to the shop still gives the same Hero of the
	 * Village his discount on every offer (vanilla behaviour), while the shopkeeper asks the owner's 20.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaVerify1031HeroNeighbour"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVerify1031HeroNeighbour")
	public void theVanillaVillagerNextDoorStillGivesTheHeroDiscount(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 20));
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
		farmer.getOffers().clear(); // fixed trades (vanilla's are random): 10 emeralds for bread, 20 wheat for an emerald
		farmer.getOffers().add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
		farmer.getOffers().add(new MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD, 1), 16, 2, 0.05f));
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVNeighbour"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 0));
		p.setShiftKeyDown(false);
		QaStageFourGameTests.pressOn(p, farmer, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the farmer's trade screen didn't open: " + p.containerMenu);
		List<String> full = new ArrayList<>();
		int priced = 0;
		for (MerchantOffer o : ((MerchantMenu) p.containerMenu).getOffers()) {
			if (o.getBaseCostA().getCount() > 1) { // (a 1-item cost can't go lower: vanilla asks at least 1)
				priced++;
				if (o.getCostA().getCount() >= o.getBaseCostA().getCount()) {
					full.add(o.getBaseCostA() + " asked " + o.getCostA());
				}
			}
		}
		helper.assertTrue(priced == 2, "setup: the farmer's trade screen shows " + priced + " of his 2 trades");
		p.closeContainer();
		p.setShiftKeyDown(Money.cobbleDollars());
		int shop = askedEmeralds(helper, p, keeper, buyer);
		helper.assertTrue(full.isEmpty(), "the vanilla farmer next to the shop gave the Hero no discount on " + full.size() + " of " + priced + " offers: " + full);
		helper.assertTrue(shop == 20, "the shopkeeper asked the Hero " + shop + " emeralds for the owner's 20");
		helper.succeed();
	}

	// ---- B711: story arc talk counts only the cast villager -------------------------------------------------------

	/**
	 * B711: a villager of the same village who carries the part's tag (given by hand, as with /tag) but wasn't the one
	 * the arc cast doesn't finish the talk step; the cast elder still does.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaVerify1031ArcTagged"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaVerify1031ArcTagged")
	public void aVillagerTaggedByHandIsNoStandInForTheCastElder(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		VillageHalls.RADIUS = 8;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 50;
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(5, 2, 5);
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.stopAll(level, hall);
		});
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		Villager ilse = helper.spawn(EntityType.VILLAGER, at.east(2));
		ilse.setCustomName(Component.literal("Ilse"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(hall.getX() + 3.5, hall.getY(), hall.getZ() + 0.5);
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
		Arcs.Arc arc = Arcs.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk")).orElse(null);
		helper.assertTrue(arc != null, "the test pack's talk arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && ilse.getTags().contains(ArcEffects.roleTag("elder")), "Ilse wasn't cast as the elder");
		Villager tagged = helper.spawn(EntityType.VILLAGER, at.west(2));
		tagged.setCustomName(Component.literal("Hans"));
		tagged.addTag(ArcEffects.roleTag("elder"));
		for (String t : ilse.getTags()) {
			tagged.addTag(t); // every tag Ilse has, the arc's own too: only the arc's saved cast tells them apart
		}
		ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, tagged);
		List<Quest> open = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
		helper.assertTrue(Arcs.running(level, hall) == s && open.size() == 1, "talking to Hans (tagged by hand, not cast) moved the arc on");
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, ilse) == InteractionResult.SUCCESS
			&& Arcs.running(level, hall) == null, "talking to Ilse, the cast elder, didn't finish the arc");
		helper.succeed();
	}

	// ---- B673, B674: the Pathfinder's sentences --------------------------------------------------------------------

	private static final BlockPos PF_HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation PATHFINDER = AliveWorkplace.id("pathfinder");

	private static void pfSetUp(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		LegendRecord stale = LegendRecord.get(level);
		stale.entries().stream().filter(e -> e.id().equals(PATHFINDER)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(PF_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(PF_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(PF_HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend pathfinder = Legends.get(PATHFINDER).orElseThrow(() -> new AssertionError("pathfinder.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(PATHFINDER, pathfinder);
			Legends.setForTest(map);
			body.accept(pathfinder);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	private static Villager pathfinder(GameTestHelper helper, Legend legend, BlockPos at, BlockPos table, int bread) {
		helper.setBlock(table, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(table.east(), Blocks.CHEST);
		if (bread > 0) {
			((Container) helper.getBlockEntity(table.east())).setItem(0, new ItemStack(Items.BREAD, bread));
		}
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(table)));
		return v;
	}

	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name, BlockPos at) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos a = helper.absolutePos(at);
		player.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static List<String> legendChronicle(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(PF_HALL);
		return hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	private static void arrive(GameTestHelper helper, String kind, String name, String expected) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, name, new BlockPos(22, 2, 24));
		pfSetUp(helper, player);
		ServerLevel level = helper.getLevel();
		BlockPos target = helper.absolutePos(new BlockPos(26, 2, 26));
		Villager[] v = new Villager[1];
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 24), new BlockPos(20, 2, 20), 8);
			helper.assertTrue(Pathfinder.start(level, player, v[0], kind, target, null), "the Pathfinder didn't set out for " + kind);
		});
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(Pathfinder.state(v[0]).arrived(), "never arrived at the " + kind))
			.thenExecute(() -> {
				List<String> lines = legendChronicle(helper);
				String led = lines.stream().filter(l -> l.contains(" led ")).findFirst().orElse("(no expedition line: " + lines + ")");
				helper.assertTrue(led.contains(expected) && !led.contains(" an Stronghold") && !led.contains(" an Trial"), "arrived at the " + kind + ", the chronicle reads \"" + led + "\" (expected \"" + expected + "\")");
			})
			.thenSucceed();
	}

	/** B673's other side on arrival: led to a Stronghold, the chronicle keeps "to a Stronghold," (never "an Stronghold"). */
	//$ gametest_ticks_batch HUGE '600' '"qaVerify1031PathfinderStronghold"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaVerify1031PathfinderStronghold")
	public void ledToAStrongholdKeepsAInTheChronicle(GameTestHelper helper) {
		arrive(helper, "stronghold", "qaV-stronghold", " to a Stronghold,");
	}

	/**
	 * B673: the third place the Pathfinder knows: arriving at the Trial Chambers the line never reads "an Trial
	 * Chambers" (a consonant keeps "a").
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaVerify1031PathfinderTrial"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaVerify1031PathfinderTrial")
	public void ledToTheTrialChambersNeverReadsAn(GameTestHelper helper) {
		arrive(helper, "trial_chambers", "qaV-trial", " Trial Chambers, ");
	}

	private static String food(List<Component> seen) {
		return seen.stream().map(Component::getString).filter(s -> s.contains("food")).findFirst().orElse("(nothing about food: " + seen + ")");
	}

	/**
	 * B674 through the player's own entry point (a sneak-right-click with an empty hand): no food says "(there are 0)",
	 * one cooked beef "(there is 1)", a bread in each of two chests by the table "(there are 2)".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVerify1031PathfinderFood"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVerify1031PathfinderFood")
	public void aSneakRightClickCountsTheRationsInEveryChest(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen, "qaV-asker", new BlockPos(22, 2, 22));
		pfSetUp(helper, player);
		Villager[] v = new Villager[1];
		String[] said = new String[3];
		BlockPos table = new BlockPos(20, 2, 18);
		staged(helper, legend -> {
			v[0] = pathfinder(helper, legend, new BlockPos(23, 2, 22), table, 0);
			v[0].setNoAi(true);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setShiftKeyDown(true);
			seen.clear();
			QaStageFourGameTests.pressOn(player, v[0], seen);
			said[0] = food(seen);
			((Container) helper.getBlockEntity(table.east())).setItem(0, new ItemStack(Items.COOKED_BEEF, 1));
			seen.clear();
			QaStageFourGameTests.pressOn(player, v[0], seen);
			said[1] = food(seen);
			((Container) helper.getBlockEntity(table.east())).setItem(0, new ItemStack(Items.BREAD, 1));
			helper.setBlock(table.west(), Blocks.CHEST);
			((Container) helper.getBlockEntity(table.west())).setItem(0, new ItemStack(Items.BREAD, 1));
			seen.clear();
			QaStageFourGameTests.pressOn(player, v[0], seen);
			said[2] = food(seen);
		});
		helper.assertTrue(said[0].endsWith("for the road (there are 0)"), "with no food: \"" + said[0] + "\"");
		helper.assertTrue(said[1].endsWith("for the road (there is 1)"), "with one cooked beef: \"" + said[1] + "\"");
		helper.assertTrue(said[2].endsWith("for the road (there are 2)"), "with a bread in each of two chests: \"" + said[2] + "\"");
		helper.succeed();
	}

	// ---- B691: Travel Post "1 other post" ---------------------------------------------------------------------------

	private static String said(QaStageFiveGameTests.Client client, String start) {
		String line = client.seen().stream().map(Component::getString).filter(s -> s.startsWith(start)).findFirst().orElse("<nothing>");
		client.seen().clear();
		return line;
	}

	private static void click(GameTestHelper helper, ServerPlayer p, BlockPos post) {
		BlockPos at = helper.absolutePos(post);
		p.moveTo(Vec3.atCenterOf(at.south()));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.gameMode.useItemOn(p, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}

	/**
	 * B691 down the count: a player who knows three posts reads "2 other posts"; once one is broken, clicking a post says
	 * "1 other post"; with a second broken, "0 other posts". A second player who only finds one post reads "0 other
	 * posts", whatever the first player knows.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVerify1031TravelBroken"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVerify1031TravelBroken")
	public void aBrokenPostCountsDownToOneOtherPost(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos[] posts = {new BlockPos(5, 2, 5), new BlockPos(15, 2, 5), new BlockPos(25, 2, 5)};
		String[] names = {"Ash", "Birch", "Cove"};
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		for (int i = 0; i < posts.length; i++) {
			helper.setBlock(posts[i], ModBlocks.TRAVEL_POST);
			network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(posts[i])), names[i]);
		}
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVPosts"));
		ServerPlayer p = client.player();
		for (BlockPos post : posts) {
			click(helper, p, post);
		}
		client.seen().clear();
		click(helper, p, posts[0]);
		String three = said(client, "Travel Post \"Ash\"");
		helper.getLevel().destroyBlock(helper.absolutePos(posts[2]), false);
		click(helper, p, posts[0]);
		String two = said(client, "Travel Post \"Ash\"");
		helper.getLevel().destroyBlock(helper.absolutePos(posts[1]), false);
		click(helper, p, posts[0]);
		String one = said(client, "Travel Post \"Ash\"");

		QaStageFiveGameTests.Client other = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVPosts2"));
		click(helper, other.player(), posts[0]);
		String stranger = said(other, "Found the Travel Post \"Ash\"");

		helper.assertTrue(three.equals("Travel Post \"Ash\" · you know 2 other posts"), "knowing three posts: " + three);
		helper.assertTrue(two.equals("Travel Post \"Ash\" · you know 1 other post"), "after one post was broken: " + two);
		helper.assertTrue(one.equals("Travel Post \"Ash\" · you know 0 other posts"), "after two posts were broken: " + one);
		helper.assertTrue(stranger.endsWith("You know 0 other posts."), "a second player finding their first post: " + stranger);
		helper.succeed();
	}

	/**
	 * B691 from a post the player placed themselves (placing it counts as knowing it): finding the next post reads "You
	 * know 1 other post." in the singular.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVerify1031TravelPlaced"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVerify1031TravelPlaced")
	public void aPlacedPostThenAFoundOneSaysOneOtherPost(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos found = new BlockPos(20, 2, 20);
		helper.setBlock(found, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(found)), "Elm");
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaVPlacer"));
		ServerPlayer p = client.player();
		ItemStack post = new ItemStack(ModBlocks.TRAVEL_POST.asItem());
		post.set(DataComponents.CUSTOM_NAME, Component.literal("Dune"));
		BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
		p.moveTo(Vec3.atCenterOf(floor.above().south(2)));
		p.setItemInHand(InteractionHand.MAIN_HAND, post);
		p.gameMode.useItemOn(p, helper.getLevel(), post, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
		helper.assertTrue(helper.getLevel().getBlockState(floor.above()).is(ModBlocks.TRAVEL_POST), "setup: the Travel Post wasn't placed");
		client.seen().clear();
		click(helper, p, found);
		String line = said(client, "Found the Travel Post \"Elm\"");
		helper.assertTrue(line.endsWith("You know 1 other post."), "finding a post after placing one: " + line);
		helper.getLevel().destroyBlock(floor.above(), false);
		helper.succeed();
	}
}
