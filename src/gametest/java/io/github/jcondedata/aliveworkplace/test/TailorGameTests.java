package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.tailor.TailorWork;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Tailor (ROADMAP 34.10): picked at a loom with string (the Shepherd back with shears); sews Work Clothes (Novice),
 * Fine Clothes (Apprentice) and Noble Robes (Journeyman) through the luxury workshop engine, from the chest by their
 * loom. The dyed-wool rules: Work Clothes take any mix of wool, Fine Clothes any mix but white, Noble Robes five of one
 * rich colour. The sewing tests drive {@link TailorWork} tick by tick on a NoAI Tailor.
 */
public class TailorGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos LOOM = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final TagKey<Item> DYED_WOOL = TagKey.create(Registries.ITEM, AliveWorkplace.id("dyed_wool"));

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A loom with a chest beside it, and no store nearby but the chest. */
	private static void shop(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 4;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(LOOM, Blocks.LOOM);
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	/** A Tailor of {@code level} at the loom (NoAI), not yet working. */
	private static Villager tailor(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TAILOR).setLevel(level));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(LOOM)));
		return v;
	}

	/** Runs a Tailor's shift on {@code v} every tick, as the WORK activity would. */
	private static TailorWork work(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		TailorWork work = new TailorWork();
		helper.onEachTick(() -> {
			if (!v.isAlive() || v.isRemoved()) {
				return;
			}
			long now = world.getGameTime();
			if (work.getStatus() == Behavior.Status.STOPPED) {
				work.tryStart(world, v, now);
			} else {
				work.tickOrStop(world, v, now);
			}
		});
		return work;
	}

	private static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static VillagerProfession job(Villager villager) {
		return villager.getVillagerData().getProfession();
	}

	private static boolean atTheLoom(GameTestHelper helper, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(LOOM)::equals).isPresent();
	}

	/**
	 * Sneak-right-clicking a jobless villager by a loom with string makes them its Tailor; shears bring the Shepherd back
	 * at the same loom, and string the Tailor again. A plain right-click with string picks nothing.
	 */
	//$ gametest_batch AREA '"tailorPicked"'
	@GameTest(template = AREA, batch = "tailorPicked")
	public void pickedWithStringAtALoom(GameTestHelper helper) {
		shop(helper);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true);
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.STRING), false);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "a plain right-click with string gave " + name(job(villager)));
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.STRING), true);
		helper.assertTrue(job(villager) == ModVillagers.TAILOR, "string at a loom gave " + name(job(villager)) + ", not the Tailor");
		helper.assertTrue(atTheLoom(helper, villager), "the Tailor isn't working at the loom");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.SHEARS), true);
		helper.assertTrue(job(villager) == VillagerProfession.SHEPHERD, "shears gave " + name(job(villager)) + ", not the Shepherd back");
		helper.assertTrue(atTheLoom(helper, villager), "the Shepherd left the loom");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.STRING), true);
		helper.assertTrue(job(villager) == ModVillagers.TAILOR && atTheLoom(helper, villager), "string didn't make the Shepherd a Tailor again");
		helper.assertTrue(Stations.of(ModVillagers.TAILOR).map(s -> s.block() == Blocks.LOOM).orElse(false), "the Tailor's station isn't the loom");
		helper.assertTrue(Stations.of(VillagerProfession.SHEPHERD).map(s -> s.block() == Blocks.LOOM && s.byItself()).orElse(false),
			"a jobless villager no longer takes a loom as a Shepherd");
		helper.assertTrue(Village.takesPart(villager), "porters wouldn't visit a Tailor");
		helper.succeed();
	}

	/**
	 * A Novice sews Work Clothes from 3 wool of any colours together, 2 leather and a string each (the loom clacking),
	 * and leaves the gold alone: no Fine Clothes at their level.
	 */
	//$ gametest_ticks_batch AREA '1600' '"tailorWork"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "tailorWork")
	public void aNoviceSewsWorkClothesFromAnyWool(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 2));
		c.setItem(1, new ItemStack(Items.BROWN_WOOL, 3));
		c.setItem(2, new ItemStack(Items.RED_WOOL, 1));
		c.setItem(3, new ItemStack(Items.LEATHER, 4));
		c.setItem(4, new ItemStack(Items.STRING, 2));
		c.setItem(5, new ItemStack(Items.GOLD_NUGGET, 4));
		int before = TailorWork.stitches;
		Villager v = tailor(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.WORK_CLOTHES) == 2, count(c, ModItems.WORK_CLOTHES) + " work clothes, expected 2");
			int wool = count(c, Items.WHITE_WOOL) + count(c, Items.BROWN_WOOL) + count(c, Items.RED_WOOL);
			helper.assertTrue(wool == 0 && count(c, Items.LEATHER) == 0 && count(c, Items.STRING) == 0,
				wool + " wool, " + count(c, Items.LEATHER) + " leather and " + count(c, Items.STRING) + " string left");
			helper.assertTrue(count(c, ModItems.FINE_CLOTHES) == 0 && count(c, Items.GOLD_NUGGET) == 4, "a Novice sewed Fine Clothes");
			helper.assertTrue(TailorWork.stitches > before, "the loom never clacked");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
		});
	}

	/**
	 * An Apprentice with only white wool sews no Fine Clothes; given dyed wool (two colours together) they sew one, and
	 * the white wool is still there.
	 */
	//$ gametest_ticks_batch AREA '1600' '"tailorFine"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "tailorFine")
	public void fineClothesRefuseWhiteWool(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 8));
		c.setItem(1, new ItemStack(Items.STRING, 1));
		c.setItem(2, new ItemStack(Items.GOLD_NUGGET, 2));
		Villager v = tailor(helper, 2);
		work(helper, v);
		boolean[] dyed = new boolean[1];
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(c, ModItems.FINE_CLOTHES) == 0, "Fine Clothes from white wool");
			helper.assertTrue(count(c, Items.WHITE_WOOL) == 8 && count(c, Items.STRING) == 1 && count(c, Items.GOLD_NUGGET) == 2,
				"the makings were taken: " + count(c, Items.WHITE_WOOL) + " white wool, " + count(c, Items.STRING) + " string, "
					+ count(c, Items.GOLD_NUGGET) + " nuggets");
			c.setItem(3, new ItemStack(Items.YELLOW_WOOL, 3));
			c.setItem(4, new ItemStack(Items.GREEN_WOOL, 1));
			dyed[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dyed[0], "the dyed wool isn't in the chest yet");
			helper.assertTrue(count(c, ModItems.FINE_CLOTHES) == 1, count(c, ModItems.FINE_CLOTHES) + " fine clothes, expected 1");
			helper.assertTrue(count(c, Items.YELLOW_WOOL) == 0 && count(c, Items.GREEN_WOOL) == 0, "dyed wool left over");
			helper.assertTrue(count(c, Items.WHITE_WOOL) == 8, count(c, Items.WHITE_WOOL) + " white wool left of 8: white went into Fine Clothes");
			helper.assertTrue(count(c, Items.STRING) == 0 && count(c, Items.GOLD_NUGGET) == 0, "string or nuggets left");
		});
	}

	/**
	 * A Journeyman with five rich wool of two colours (3 purple, 2 blue) and five green sews no Noble Robes; two more
	 * purple make five of one colour, and one robe is sewn from the purple alone.
	 */
	//$ gametest_ticks_batch AREA '1600' '"tailorNoble"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "tailorNoble")
	public void nobleRobesNeedFiveOfOneRichColour(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.PURPLE_WOOL, 3));
		c.setItem(1, new ItemStack(Items.BLUE_WOOL, 2));
		c.setItem(2, new ItemStack(Items.GREEN_WOOL, 5));
		c.setItem(3, new ItemStack(Items.RABBIT_HIDE, 1));
		c.setItem(4, new ItemStack(Items.GOLD_INGOT, 1));
		Villager v = tailor(helper, 3);
		work(helper, v);
		boolean[] more = new boolean[1];
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(c, ModItems.NOBLE_ROBES) == 0, "Noble Robes from mixed or green wool");
			helper.assertTrue(count(c, Items.PURPLE_WOOL) == 3 && count(c, Items.BLUE_WOOL) == 2 && count(c, Items.GREEN_WOOL) == 5
				&& count(c, Items.RABBIT_HIDE) == 1 && count(c, Items.GOLD_INGOT) == 1, "the makings were taken");
			c.setItem(5, new ItemStack(Items.PURPLE_WOOL, 2));
			more[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(more[0], "the last purple wool isn't in the chest yet");
			helper.assertTrue(count(c, ModItems.NOBLE_ROBES) == 1, count(c, ModItems.NOBLE_ROBES) + " noble robes, expected 1");
			helper.assertTrue(count(c, Items.PURPLE_WOOL) == 0, count(c, Items.PURPLE_WOOL) + " purple wool left");
			helper.assertTrue(count(c, Items.BLUE_WOOL) == 2 && count(c, Items.GREEN_WOOL) == 5, "another colour went into the robe");
			helper.assertTrue(count(c, Items.RABBIT_HIDE) == 0 && count(c, Items.GOLD_INGOT) == 0, "the hide or the gold is left");
		});
	}

	/** Each rich colour makes Noble Robes, five of it: purple, blue, red and black, and no other. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theRichColours(GameTestHelper helper) {
		List<LuxuryRecipes.Recipe> robes = LuxuryRecipes.making(ModItems.NOBLE_ROBES);
		helper.assertTrue(robes.size() == 4, robes.size() + " Noble Robes recipes, expected 4");
		Set<Item> colours = new java.util.HashSet<>();
		for (LuxuryRecipes.Recipe r : robes) {
			LuxuryRecipes.Input wool = r.inputs().get(0);
			helper.assertTrue(r.level() == 3 && wool.count() == 5 && !wool.mix() && wool.item().isPresent(), r.id() + ": " + r);
			colours.add(wool.item().get());
			helper.assertTrue(r.inputs().stream().anyMatch(i -> i.matches(new ItemStack(Items.RABBIT_HIDE)) && i.count() == 1)
				&& r.inputs().stream().anyMatch(i -> i.matches(new ItemStack(Items.GOLD_INGOT)) && i.count() == 1), r.id() + ": no hide or no gold ingot");
		}
		helper.assertTrue(colours.equals(Set.of(Items.PURPLE_WOOL, Items.BLUE_WOOL, Items.RED_WOOL, Items.BLACK_WOOL)), "the rich colours are " + colours);
		helper.succeed();
	}

	/** With nothing to sew, a Novice asks for wool on the requests board. */
	//$ gametest_ticks_batch AREA '600' '"tailorAsks"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "tailorAsks")
	public void asksForWoolWhenTheChestIsEmpty(GameTestHelper helper) {
		shop(helper);
		Villager v = tailor(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			boolean asked = Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.WHITE_WOOL))
				&& r.accepts().test(new ItemStack(Items.ORANGE_WOOL)));
			helper.assertTrue(asked, "no request for wool: " + Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList());
			helper.assertTrue(count(chest(helper), ModItems.WORK_CLOTHES) == 0, "clothes out of nothing");
		});
	}

	/**
	 * Saved and loaded mid-job (the makings already in the bag): the reloaded Tailor puts back or finishes what they had,
	 * and all the wool ends up as Work Clothes.
	 */
	//$ gametest_ticks_batch AREA '2400' '"tailorReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "tailorReload")
	public void aReloadMidJobLosesNothing(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 6));
		c.setItem(1, new ItemStack(Items.LEATHER, 4));
		c.setItem(2, new ItemStack(Items.STRING, 2));
		Villager first = tailor(helper, 1);
		work(helper, first);
		Villager[] copy = new Villager[1];
		TailorWork after = new TailorWork(); // the reloaded Tailor's shift: a new one, as after a restart
		ServerLevel world = helper.getLevel();
		helper.onEachTick(() -> {
			if (copy[0] == null && first.isAlive() && !ModAttachments.BUILDER_BAG.getOrCreate(first).isEmpty()) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager v = EntityType.VILLAGER.create(world);
				v.load(tag);
				v.setUUID(UUID.randomUUID());
				world.addFreshEntity(v);
				v.setNoAi(true);
				copy[0] = v;
			} else if (copy[0] != null && copy[0].isAlive()) {
				long now = world.getGameTime();
				if (after.getStatus() == Behavior.Status.STOPPED) {
					after.tryStart(world, copy[0], now);
				} else {
					after.tickOrStop(world, copy[0], now);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the Tailor never fetched anything");
			helper.assertTrue(job(copy[0]) == ModVillagers.TAILOR, "the job was lost on reload");
			helper.assertTrue(count(c, ModItems.WORK_CLOTHES) == 2 && count(c, Items.WHITE_WOOL) == 0 && count(c, Items.LEATHER) == 0
				&& count(c, Items.STRING) == 0, count(c, ModItems.WORK_CLOTHES) + " work clothes, " + count(c, Items.WHITE_WOOL) + " wool, "
				+ count(c, Items.LEATHER) + " leather, " + count(c, Items.STRING) + " string; expected 2, 0, 0, 0");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy[0]).isEmpty(), "things left in the bag");
		});
	}

	/**
	 * Config {@code tailors} off: string picks no job at a loom (shears still a Shepherd), and a Tailor already hired sews
	 * nothing.
	 */
	//$ gametest_ticks_batch AREA '400' '"tailorOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "tailorOff")
	public void switchedOffSewsNothing(GameTestHelper helper) {
		shop(helper);
		Tailors.ENABLED = false;
		Leftovers.after(helper, () -> Tailors.ENABLED = true);
		ServerPlayer player = player(helper);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		jobless.setNoAi(true);
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.STRING), true);
		helper.assertTrue(job(jobless) == VillagerProfession.NONE, "string gave " + name(job(jobless)) + " with the switch off");
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.SHEARS), true);
		helper.assertTrue(job(jobless) == VillagerProfession.SHEPHERD, "shears gave " + name(job(jobless)) + " with the switch off, not the Shepherd");
		jobless.discard();
		helper.getLevel().getPoiManager().release(helper.absolutePos(LOOM));
		Stations.Station loom = Stations.at(Blocks.LOOM).orElseThrow();
		helper.assertTrue(loom.jobs().stream().filter(Stations::available).map(j -> j.profession().get()).toList().equals(List.of(VillagerProfession.SHEPHERD)),
			"the loom's tooltip still offers the Tailor");
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 6));
		c.setItem(1, new ItemStack(Items.LEATHER, 4));
		c.setItem(2, new ItemStack(Items.STRING, 2));
		work(helper, tailor(helper, 1));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(count(c, ModItems.WORK_CLOTHES) == 0 && count(c, Items.WHITE_WOOL) == 6, "sewed with the switch off");
			helper.succeed();
		});
	}

	/** The luxury files, the recipes and their levels, the dyed-wool tag, the Pokémon that help and the trades. */
	//$ gametest_batch AREA '"tailorData"'
	@GameTest(template = AREA, batch = "tailorData")
	public void theLuxuryFilesRecipesAndTrades(GameTestHelper helper) {
		check(helper, "work_clothes", ModItems.WORK_CLOTHES);
		check(helper, "fine_clothes", ModItems.FINE_CLOTHES);
		check(helper, "noble_robes", ModItems.NOBLE_ROBES);
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.TAILOR, 1).size() == 1 && LuxuryRecipes.forJob(ModVillagers.TAILOR, 2).size() == 2
			&& LuxuryRecipes.forJob(ModVillagers.TAILOR, 3).size() == 6, "recipes by level: " + LuxuryRecipes.forJob(ModVillagers.TAILOR, 5));
		LuxuryRecipes.Recipe work = LuxuryRecipes.all().get(AliveWorkplace.id("work_clothes"));
		helper.assertTrue(work != null && work.level() == 1 && work.inputs().get(0).mix() && work.inputs().get(0).count() == 3
			&& ItemTags.WOOL.equals(work.inputs().get(0).tag().orElse(null)), "the work clothes recipe: " + work);
		LuxuryRecipes.Recipe fine = LuxuryRecipes.all().get(AliveWorkplace.id("fine_clothes"));
		helper.assertTrue(fine != null && fine.level() == 2 && fine.inputs().get(0).mix() && fine.inputs().get(0).count() == 4
			&& DYED_WOOL.equals(fine.inputs().get(0).tag().orElse(null)), "the fine clothes recipe: " + fine);
		helper.assertTrue(!new ItemStack(Items.WHITE_WOOL).is(DYED_WOOL), "white wool counts as dyed");
		int dyed = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (new ItemStack(item).is(ItemTags.WOOL) && new ItemStack(item).is(DYED_WOOL)) {
				dyed++;
			}
		}
		helper.assertTrue(dyed == 15, dyed + " dyed wools, expected the 15 colours but white");
		helper.assertTrue(Partners.types(ModVillagers.TAILOR).equals(Set.of("bug", "normal")), "the Tailor's partners: " + Partners.types(ModVillagers.TAILOR));
		helper.assertTrue(LuxuryRecipes.isMaker(ModVillagers.TAILOR), "the Tailor isn't a luxury maker");
		// Trades at every level: one buys a making (wool, string or leather), one sells the level's clothes.
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		List<Item> sold = List.of(ModItems.WORK_CLOTHES, ModItems.FINE_CLOTHES, ModItems.NOBLE_ROBES, ModItems.FINE_CLOTHES, ModItems.NOBLE_ROBES);
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.TAILOR);
		helper.assertTrue(byLevel != null, "the Tailor has no trades");
		for (int level = 1; level <= 5; level++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(level);
			helper.assertTrue(listings != null && listings.length == 2, "level " + level + ": " + (listings == null ? 0 : listings.length) + " trades, expected 2");
			boolean buys = false;
			boolean sells = false;
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, RandomSource.create(1));
				ItemStack cost = offer.getBaseCostA();
				buys |= offer.getResult().is(Items.EMERALD) && (cost.is(ItemTags.WOOL) || cost.is(Items.STRING) || cost.is(Items.LEATHER));
				sells |= cost.is(Items.EMERALD) && offer.getResult().is(sold.get(level - 1));
			}
			helper.assertTrue(buys && sells, "level " + level + ": buys a making " + buys + ", sells " + sold.get(level - 1) + " " + sells);
		}
		v.discard();
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String id, Item item) {
		Luxuries.Luxury luxury = Luxuries.get(AliveWorkplace.id(id));
		helper.assertTrue(luxury != null && luxury.everyDays() == 8 && luxury.matches(new ItemStack(item)), id + ": " + luxury);
	}

	/** Every new sentence a player reads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		expect(helper, Component.translatable("message.aliveworkplace.tailor.title", 5), "Tailor · 5 made");
		Stations.Station loom = Stations.of(ModVillagers.TAILOR).orElseThrow();
		expect(helper, Component.translatable(Stations.itemKey(loom.jobs().stream()
			.filter(j -> j.profession().get() == ModVillagers.TAILOR).findFirst().orElseThrow())), "String");
		expect(helper, Component.translatable(Stations.itemKey(loom.jobs().stream()
			.filter(j -> j.profession().get() == VillagerProfession.SHEPHERD).findFirst().orElseThrow())), "Shears");
		expect(helper, Component.translatable("entity.minecraft.villager.tailor"), "Tailor");
		expect(helper, Component.translatable("entity.minecraft.zombie_villager.tailor"), "Zombie Tailor");
		expect(helper, Component.translatable("item.aliveworkplace.work_clothes"), "Work Clothes");
		expect(helper, Component.translatable("item.aliveworkplace.fine_clothes"), "Fine Clothes");
		expect(helper, Component.translatable("item.aliveworkplace.noble_robes"), "Noble Robes");
		expect(helper, Component.translatable("aliveworkplace.config.tailors"), "Tailors");
		helper.assertTrue(english(Component.translatable("aliveworkplace.config.tailors.tooltip")).startsWith("Sneak-right-click a villager by a loom with string"),
			"the setting's tooltip");
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, Component text, String want) {
		String got = english(text);
		helper.assertTrue(got.equals(want), "\"" + got + "\", expected \"" + want + "\"");
	}

	/** {@code text} in English, from the mod's own en_us.json (the server has no mod language). */
	private static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		if (!lang.has(contents.getKey())) {
			throw new GameTestAssertException("no English for " + contents.getKey());
		}
		String out = lang.get(contents.getKey()).getAsString();
		for (Object arg : contents.getArgs()) {
			out = out.replaceFirst("%s", String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}
}
