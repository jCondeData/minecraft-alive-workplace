package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.jeweller.Jewellers;
import io.github.jcondedata.aliveworkplace.jeweller.MegaStones;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.trainer.LeaderBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 38.6 with Mega Showdown: the Jeweller cuts a Mega Stone Crystal with gold and emeralds into the Mega Stone of a
 * Pokémon on one of the village's trainers' teams (one of each, only those the store lacks) and Key Stone ore into a Key
 * Stone; a Miner's Key Stones go to the village store, not their own chests; a player who beats every Trainer Leader of a
 * realm gets one Mega Bracelet from the capital's hall, once. Switched off ({@code villageMegas}, or the Jeweller, M34):
 * none of it. Without Mega Showdown: {@code MegaStoneGameTests}.
 */
public class MegaStoneCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CUTTER = new BlockPos(4, 2, 2);
	private static final BlockPos CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos STANDING = new BlockPos(5, 2, 3);
	private static final BlockPos TRAINER = new BlockPos(9, 2, 9);
	/** The village store: a Storehouse in the hall's reach, its chest out of the stonecutter's (8 blocks), and the other way round. */
	private static final BlockPos SHOP_STOREHOUSE = new BlockPos(13, 2, 11);
	private static final BlockPos SHOP_STORE = new BlockPos(15, 2, 11);
	private static final TagKey<Item> STONES = TagKey.create(Registries.ITEM, AliveWorkplace.id("mega_stones"));

	static Item ms(String path) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mega_showdown", path);
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			throw new IllegalStateException("Mega Showdown has no item " + id);
		}
		return BuiltInRegistries.ITEM.get(id);
	}

	static Block msBlock(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mega_showdown", path));
	}

	/**
	 * A Village Hall, the nearest to everything in the test, a stonecutter with the Jeweller's own chest by it (the store's
	 * rings, brooches and circlets already made, so the gold and emeralds go into the Mega Stone) and a Storehouse with a
	 * chest holding {@code stock} (38.6a: the Mega makings count only there). The village
	 * is shrunk to this test's area while it runs (restored when it ends, passed or failed): with the usual 64-block hall
	 * reach, and the store orders' 48, the trainers, storehouses, kitchens, stock orders and Wonders left by earlier
	 * batches beyond {@code clearAround}'s 48-block sweep count as this village's, and the Jeweller cuts for them or
	 * spends the gold on them (the Mega Stone test passed alone but failed in the full compat suite).
	 */
	static Container shop(GameTestHelper helper, ItemStack... stock) {
		DenWardenCompatTests.clearAround(helper);
		int radius = VillageHalls.RADIUS;
		int range = StockOrders.RANGE;
		VillageHalls.RADIUS = 16;
		StockOrders.RANGE = 16;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			StockOrders.RANGE = range;
			VillageHalls.forgetNearest();
			MegaStones.forget();
		});
		VillageHalls.forgetNearest();
		MegaStones.forget();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(SHOP_STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(SHOP_STORE, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		c.setItem(0, new ItemStack(ModItems.AMETHYST_RING, LuxuryWork.KEEP));
		c.setItem(1, new ItemStack(ModItems.EMERALD_BROOCH, LuxuryWork.KEEP));
		c.setItem(2, new ItemStack(ModItems.GOLD_CIRCLET, LuxuryWork.KEEP));
		Container store = store(helper);
		for (int i = 0; i < stock.length; i++) {
			store.setItem(i, stock[i]);
		}
		return c;
	}

	/** The village store's chest (by the Storehouse). */
	static Container store(GameTestHelper helper) {
		return helper.getBlockEntity(SHOP_STORE);
	}

	/** The Jeweller's line above the head, as a key and its arguments. */
	static String line(GameTestHelper helper, Villager v) {
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.Entry e = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(v, helper.getLevel().getGameTime());
		if (e == null || !(e.line().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)) {
			return "no status";
		}
		StringBuilder out = new StringBuilder(t.getKey());
		for (Object arg : t.getArgs()) {
			out.append(" | ").append(arg instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(arg));
		}
		return out.toString();
	}

	static boolean noMakingsTaken(Container c, int crystals, int gold, int emeralds) {
		return c.countItem(ms("mega_stone_crystal")) == crystals && c.countItem(Items.GOLD_INGOT) == gold && c.countItem(Items.EMERALD) == emeralds;
	}

	static Villager villager(GameTestHelper helper, BlockPos at, net.minecraft.world.entity.npc.VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	static Villager jeweller(GameTestHelper helper, int level) {
		Villager v = villager(helper, STANDING, ModVillagers.JEWELLER, level);
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CUTTER)));
		return v;
	}

	/** Runs a Jeweller's shift on {@code v} every tick, as the WORK activity would. */
	static JewellerWork work(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		JewellerWork work = new JewellerWork();
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

	static List<Item> stonesIn(Container c) {
		List<Item> out = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			for (int n = 0; n < s.getCount() && s.is(STONES); n++) {
				out.add(s.getItem());
			}
		}
		return out;
	}

	static boolean megaRecipe(LuxuryRecipes.Recipe r) {
		return r.id().getPath().startsWith("mega_");
	}

	/** The jar holds what the recipes and the Miner's tag name, and the four recipes load (only with Mega Showdown). */
	//$ gametest 'CompatGameTests.AREA'
	@GameTest(template = CompatGameTests.AREA)
	public void theRecipesAndFindsLoad(GameTestHelper helper) {
		for (String id : List.of("keystone", "keystone_ore", "mega_stone_crystal", "mega_stone", "mega_meteorid_fire_ore", "mega_meteorid_water_ore",
				"mega_bracelet", "charizardite_x", "gengarite")) {
			ms(id);
		}
		for (String id : List.of("mega_keystone", "mega_stone", "mega_stone_cut", "mega_stone_meteorite")) {
			LuxuryRecipes.Recipe r = LuxuryRecipes.all().get(AliveWorkplace.id(id));
			helper.assertTrue(r != null && r.job().equals(AliveWorkplace.id("jeweller")) && r.level() >= 3, "recipe " + id + ": " + r);
		}
		LuxuryRecipes.Recipe key = LuxuryRecipes.all().get(AliveWorkplace.id("mega_keystone"));
		helper.assertTrue(key.output() == ms("keystone") && key.keepOr(LuxuryWork.KEEP) == 2, "the Key Stone recipe: " + key);
		LuxuryRecipes.Recipe stone = LuxuryRecipes.all().get(AliveWorkplace.id("mega_stone"));
		helper.assertTrue(stone.makes(ms("gengarite")) && !stone.makes(ms("keystone")) && !stone.makes(ms("mega_stone")) && stone.keepOr(LuxuryWork.KEEP) == 1,
			"the Mega Stone recipe: " + stone);
		// B174: Mega Showdown's own tag leaves out Abomasite, so a Master with an Abomasnow (1 in 40) wanted a stone no
		// recipe cut. Every stone a Master's team can want is cut (fixed UUIDs, a Master's team is built from its UUID).
		helper.assertTrue(stone.makes(ms("abomasite")), "the Mega Stone recipe doesn't cut Abomasite");
		java.util.Random uuids = new java.util.Random(38_6L);
		for (int i = 0; i < 500; i++) {
			Villager master = new Villager(EntityType.VILLAGER, helper.getLevel());
			master.setUUID(new UUID(uuids.nextLong(), uuids.nextLong()));
			master.setVillagerData(master.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(5));
			List<Item> wanted = MegaStones.stonesOf(master);
			helper.assertTrue(!wanted.isEmpty() && wanted.stream().allMatch(stone::makes), "Master " + master.getUUID() + " wants " + wanted
				+ ", which the Mega Stone recipe doesn't all cut");
		}
		MegaStones.forget();
		for (String id : List.of("keystone_ore", "keystone", "mega_stone_crystal", "mega_stone", "mega_meteorid_fire_ore", "mega_meteorid_dawn_ore")) {
			helper.assertTrue(MegaStones.forTheJeweller(new ItemStack(ms(id))), id + " isn't kept for the Jeweller");
		}
		helper.assertTrue(!MegaStones.forTheJeweller(new ItemStack(Items.COBBLESTONE)) && !MegaStones.forTheJeweller(new ItemStack(Items.RAW_IRON)),
			"the Miner keeps cobblestone or iron for the Jeweller");
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.JEWELLER, 2).stream().noneMatch(MegaStoneCompatTests::megaRecipe)
			&& LuxuryRecipes.forJob(ModVillagers.JEWELLER, 3).stream().filter(MegaStoneCompatTests::megaRecipe).count() == 3
			&& LuxuryRecipes.forJob(ModVillagers.JEWELLER, 4).stream().filter(MegaStoneCompatTests::megaRecipe).count() == 4,
			"recipes by level: " + LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5));
		helper.succeed();
	}

	/**
	 * The Done when: a Journeyman Jeweller with a Mega Stone Crystal, 2 gold ingots and 4 emeralds in the village store cuts
	 * the Mega Stone of a Pokémon on the village Master's team, and only one, into his chest; the makings leave the store.
	 */
	//$ gametest_ticks_batch AREA '1600' '"megaJewellerStone"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "megaJewellerStone")
	public void aJewellerCutsAMegaStoneForTheVillagesTrainers(GameTestHelper helper) {
		helper.assertTrue(MegaStones.on(), "Mega Stones off (Mega Showdown, villageMegas or Jewellers)");
		Container c = shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Villager master = villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		helper.assertTrue(!wanted.isEmpty(), "the Master's team has no Mega Stone to cut");
		Villager v = jeweller(helper, 3);
		List<Item> village = MegaStones.forVillageOf(v);
		helper.assertTrue(!village.isEmpty() && wanted.contains(village.get(0)), "the Jeweller would cut " + village + " for the village's trainers "
			+ MegaStones.trainers(helper.getLevel(), v.blockPosition()).size() + ", but the Master's team wants " + wanted);
		Container store = store(helper);
		work(helper, v);
		helper.succeedWhen(() -> {
			List<Item> made = stonesIn(c);
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "stones made " + made + ", the Master's team wants " + wanted
				+ "; the village's trainers want " + MegaStones.forVillageOf(v) + "; the store holds " + store.countItem(ms("mega_stone_crystal")) + " crystal, "
				+ store.countItem(Items.GOLD_INGOT) + " gold, " + store.countItem(Items.EMERALD) + " emeralds; the Jeweller is " + (v.isAlive() ? "alive" : "gone")
				+ ", says " + line(helper, v));
			helper.assertTrue(noMakingsTaken(store, 0, 0, 0) && noMakingsTaken(c, 0, 0, 0),
				"makings left: " + store.countItem(ms("mega_stone_crystal")) + " crystal, " + store.countItem(Items.GOLD_INGOT) + " gold, "
					+ store.countItem(Items.EMERALD) + " emeralds");
			helper.assertTrue(c.countItem(ModItems.EMERALD_BROOCH) == LuxuryWork.KEEP && c.countItem(ModItems.GOLD_CIRCLET) == LuxuryWork.KEEP,
				"the gold went into other goods: " + c.countItem(ModItems.EMERALD_BROOCH) + " brooches, " + c.countItem(ModItems.GOLD_CIRCLET)
					+ " circlets, expected " + LuxuryWork.KEEP + " each");
		});
	}

	/** Key Stone ore with 2 gold ingots and 4 emeralds from the village store becomes a Key Stone. */
	//$ gametest_ticks_batch AREA '1600' '"megaJewellerKey"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "megaJewellerKey")
	public void aJewellerCutsAKeyStone(GameTestHelper helper) {
		Container c = shop(helper, new ItemStack(ms("keystone_ore")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Container store = store(helper);
		Villager v = jeweller(helper, 3);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(c.countItem(ms("keystone")) + store.countItem(ms("keystone")) == 1, c.countItem(ms("keystone")) + " Key Stones, expected 1; says "
				+ line(helper, v));
			helper.assertTrue(store.countItem(ms("keystone_ore")) == 0 && store.countItem(Items.GOLD_INGOT) == 0 && store.countItem(Items.EMERALD) == 0, "makings left");
		});
	}

	/**
	 * Only stones the store lacks: with every stone the Master's team wants already in the chest, the crystal is left
	 * alone; a Novice can't cut one at all, and the Jeweller with no trainer in the village makes none.
	 */
	//$ gametest_ticks_batch AREA '400' '"megaJewellerHeld"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "megaJewellerHeld")
	public void onlyTheStonesTheStoreLacks(GameTestHelper helper) {
		Container c = shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Villager master = villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		for (int i = 0; i < wanted.size(); i++) {
			c.setItem(10 + i, new ItemStack(wanted.get(i)));
		}
		Villager v = jeweller(helper, 3);
		work(helper, v);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(noMakingsTaken(store(helper), 1, 2, 4), "the crystal was cut though the store holds " + wanted);
			helper.assertTrue(stonesIn(c).size() == wanted.size(), "stones now " + stonesIn(c));
			master.discard();
			MegaStones.forget();
			helper.assertTrue(MegaStones.forVillageOf(v).isEmpty(), "no trainer left, but the Jeweller would cut " + MegaStones.forVillageOf(v));
			helper.succeed();
		});
	}

	/**
	 * 38.6a: never from the Jeweller's own chest. With the full makings in his chest and none in the village store he cuts
	 * nothing, takes nothing, posts nothing on the requests board (a hand-over would go to his own chest), and his line
	 * says what the Storehouse should hold.
	 */
	//$ gametest_ticks_batch AREA '400' '"megaJewellerEmptyStore"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "megaJewellerEmptyStore")
	public void notFromAnEmptyStore(GameTestHelper helper) {
		Container c = shop(helper);
		c.setItem(5, new ItemStack(ms("mega_stone_crystal")));
		c.setItem(6, new ItemStack(Items.GOLD_INGOT, 2));
		c.setItem(7, new ItemStack(Items.EMERALD, 4));
		Villager master = villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		helper.assertTrue(!MegaStones.stonesOf(master).isEmpty(), "the Master's team has no Mega Stone to cut");
		Villager v = jeweller(helper, 3);
		work(helper, v);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(stonesIn(c).isEmpty() && stonesIn(store(helper)).isEmpty(), "cut " + stonesIn(c) + " from nothing in the store");
			helper.assertTrue(noMakingsTaken(c, 1, 2, 4), "makings taken from his own chest: " + c.countItem(ms("mega_stone_crystal")) + " crystal, "
				+ c.countItem(Items.GOLD_INGOT) + " gold, " + c.countItem(Items.EMERALD) + " emeralds left");
			// (the Key Stone is short too, so the line may ask for its ore first: either way one stone's first making)
			String says = line(helper, v);
			String needs = "message.aliveworkplace.luxury.state.needs_store | 1 | ";
			helper.assertTrue(says.equals(needs + ms("mega_stone_crystal").getDescription().getString())
				|| says.equals(needs + ms("keystone_ore").getDescription().getString()), "says " + says + ", expected it to ask the Storehouse for a crystal or ore");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.work.Requests.of(helper.getLevel(), v).isEmpty(),
				"a request posted for a making his own chest would get");
			master.discard();
			helper.succeed();
		});
	}

	/**
	 * 38.6a: partial makings wait. The store holds the crystal and the gold but the emeralds are in his own chest: nothing
	 * is cut and his line asks the Storehouse for 4 emeralds (the stone closest to done, not the Key Stone with no ore); once the player puts them in the store he cuts the stone
	 * from the store's makings and his own emeralds stay put.
	 */
	//$ gametest_ticks_batch AREA '2000' '"megaJewellerPartial"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "megaJewellerPartial")
	public void partialMakingsWaitForTheStore(GameTestHelper helper) {
		Container c = shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2));
		c.setItem(7, new ItemStack(Items.EMERALD, 4));
		Container store = store(helper);
		Villager master = villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		Villager v = jeweller(helper, 3);
		work(helper, v);
		boolean[] stocked = {false};
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(stonesIn(c).isEmpty(), "cut " + stonesIn(c) + " without the store's emeralds");
			helper.assertTrue(noMakingsTaken(store, 1, 2, 0) && c.countItem(Items.EMERALD) == 4, "makings taken while short");
			String expected = "message.aliveworkplace.luxury.state.needs_store | 4 | " + Items.EMERALD.getDescription().getString();
			helper.assertTrue(line(helper, v).equals(expected), "says " + line(helper, v) + ", expected " + expected);
			store.setItem(5, new ItemStack(Items.EMERALD, 4)); // the player stocks the Storehouse
			stocked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stocked[0], "not yet stocked");
			List<Item> made = stonesIn(c);
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "stones made " + made + ", wanted " + wanted + "; says " + line(helper, v));
			helper.assertTrue(noMakingsTaken(store, 0, 0, 0), "store makings left");
			helper.assertTrue(c.countItem(Items.EMERALD) == 4, "his own emeralds were used: " + c.countItem(Items.EMERALD) + " left");
		});
	}

	/**
	 * Switched off: with {@code villageMegas} off the Mega recipes don't run and the Miner keeps everything; with the
	 * Jeweller off (M34) the Jeweller doesn't work at all and the recipes don't run either.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaStonesOff"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaStonesOff")
	public void switchedOffTheRecipesDontRun(GameTestHelper helper) {
		boolean megas = MegaBracelets.ENABLED;
		boolean jewellers = Jewellers.ENABLED;
		try {
			shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
			Villager master = villager(helper, TRAINER, ModVillagers.TRAINER, 5);
			Villager v = jeweller(helper, 5);
			helper.assertTrue(MegaStones.forVillageOf(v).size() > 0, "on: nothing to cut for the Master");
			MegaBracelets.ENABLED = false;
			helper.assertTrue(!MegaStones.on() && LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5).stream().noneMatch(MegaStoneCompatTests::megaRecipe)
				&& LuxuryRecipes.making(ms("keystone")).isEmpty() && MegaStones.forVillageOf(v).isEmpty(), "villageMegas off: Mega recipes still run");
			helper.assertTrue(!MegaStones.forTheJeweller(new ItemStack(ms("keystone"))), "villageMegas off: the Miner still takes Key Stones to the store");
			MegaBracelets.ENABLED = true;
			Jewellers.ENABLED = false;
			JewellerWork work = new JewellerWork();
			ServerLevel level = helper.getLevel();
			helper.assertTrue(!work.tryStart(level, v, level.getGameTime()), "Jewellers off: the Jeweller works");
			helper.assertTrue(!MegaStones.on() && LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5).stream().noneMatch(MegaStoneCompatTests::megaRecipe),
				"Jewellers off: Mega recipes still run");
			helper.assertTrue(!MegaStones.forTheJeweller(new ItemStack(ms("keystone_ore"))), "Jewellers off: the Miner still takes the ore to the store");
			master.discard();
			v.discard();
		} finally {
			MegaBracelets.ENABLED = megas;
			Jewellers.ENABLED = jewellers;
		}
		helper.succeed();
	}

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(2, 2, 13);
	private static final BlockPos STORE_CHEST = new BlockPos(2, 2, 15);

	/** The Done when: Key Stone ore dug by a Miner comes out as Key Stones, which go to the village store, not the Miner's chest. */
	//$ gametest_ticks_batch AREA '3000' '"megaMinerFinds"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "megaMinerFinds")
	public void aMinersKeyStonesGoToTheStore(GameTestHelper helper) {
		DenWardenCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(new BlockPos(15, 2, 15), ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		Container own = helper.getBlockEntity(BENCH_CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		own.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
		BlockPos min = new BlockPos(7, 2, 7);
		BlockPos max = new BlockPos(8, 2, 8);
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			helper.setBlock(p, msBlock("keystone_ore"));
		}
		helper.setBlock(new BlockPos(9, 2, 7), Blocks.STONE);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Miners.employ(level, miner, helper.absolutePos(BENCH));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(new BlockPos(9, 2, 8)));
		QuarrySite site = Miners.start(level, miner, null, box, box.getYSpan());
		site.setStairs(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(level).get(site.id()) == null, "still digging: " + Math.round(site.progress() * 100) + "%");
			helper.assertTrue(store.countItem(ms("keystone")) == 4, store.countItem(ms("keystone")) + " Key Stones in the store, expected 4; "
				+ own.countItem(ms("keystone")) + " in the Miner's chest, the store " + LuxuryWork.store(level, helper.absolutePos(BENCH))
				+ ", the hall " + VillageHalls.nearest(level, helper.absolutePos(BENCH)));
			helper.assertTrue(own.countItem(ms("keystone")) == 0, own.countItem(ms("keystone")) + " Key Stones in the Miner's own chest");
			helper.assertTrue(own.countItem(Items.COBBLESTONE) >= 1 && store.countItem(Items.COBBLESTONE) == 0, "the cobblestone went to the store");
		});
	}

	// ---- the Mega Bracelet from the capital's hall ------------------------------------------------------------------

	private static final BlockPos CAPITAL = new BlockPos(1, 2, 1);
	private static final BlockPos MEMBER = new BlockPos(15, 2, 15);
	private static boolean REALMS_WERE = true;

	record Land(RealmData.Realm realm, Villager first, Villager second, ServerPlayer player) {
	}

	/** Two villages in a realm (the capital and a member), each with its Trainer Leader seen by the hall's round. */
	static Land realm(GameTestHelper helper) {
		DenWardenCompatTests.clearAround(helper);
		REALMS_WERE = Realms.ENABLED;
		Realms.ENABLED = true;
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setBlock(CAPITAL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(MEMBER, ModBlocks.VILLAGE_HALL);
		GlobalPos capital = GlobalPos.of(level.dimension(), helper.absolutePos(CAPITAL));
		GlobalPos member = GlobalPos.of(level.dimension(), helper.absolutePos(MEMBER));
		RealmData data = RealmData.get(level.getServer());
		data.clearRealms();
		RealmData.Realm realm = new RealmData.Realm(UUID.randomUUID(), Optional.empty(), capital,
			List.of(new RealmData.Member(capital, 0), new RealmData.Member(member, 0)), Optional.empty(), "barony", 0, RealmData.Realm.DEFAULT_LEVY, 0, List.of());
		data.putRealm(realm);
		LeaderBracelets.forget(level.getServer());
		Villager first = villager(helper, new BlockPos(2, 2, 3), ModVillagers.TRAINER_LEADER, 4);
		Villager second = villager(helper, new BlockPos(14, 2, 13), ModVillagers.TRAINER_LEADER, 4);
		LeaderBracelets.round(level, capital.pos(), List.of(first));
		LeaderBracelets.round(level, member.pos(), List.of(second));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		return new Land(realm, first, second, player);
	}

	static void cleanUp(GameTestHelper helper, Land r) {
		Realms.ENABLED = REALMS_WERE;
		RealmData.get(helper.getLevel().getServer()).removeRealm(r.realm().id());
		LeaderBracelets.forget(helper.getLevel().getServer());
		helper.getLevel().getServer().getPlayerList().remove(r.player());
		r.first().discard();
		r.second().discard();
	}

	static int bracelets(ServerPlayer player) {
		return player.getInventory().countItem(ms("mega_bracelet"));
	}

	/**
	 * The Done when: beating the capital's Leader gives nothing yet; beating the member village's too (the battle ending, as
	 * Cobblemon reports it) gives one Mega Bracelet; beating them again, or after a save and load, gives no second one.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaLeaderBracelet"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaLeaderBracelet")
	public void beatingEveryLeaderOfARealmGivesOneBracelet(GameTestHelper helper) {
		Land r = realm(helper);
		try {
			helper.assertTrue(LeaderBracelets.on(), "bracelets off (Mega Showdown or villageMegas)");
			var server = helper.getLevel().getServer();
			helper.assertTrue(LeaderBracelets.hasLeader(server, r.realm().capital()) && LeaderBracelets.hasLeader(server, r.realm().members().get(1).hall()),
				"the hall rounds didn't see the Leaders");
			Trainers.battleOver(server, r.first().getUUID(), r.player().getUUID(), true);
			helper.assertTrue(bracelets(r.player()) == 0, "a bracelet after one Leader of two");
			helper.assertTrue(LeaderBracelets.beatenBy(server, r.player().getUUID()).contains(r.realm().capital()), "the capital's Leader not marked beaten");
			Trainers.battleOver(server, r.second().getUUID(), r.player().getUUID(), false);
			helper.assertTrue(bracelets(r.player()) == 0, "a bracelet for losing to the second Leader");
			Trainers.battleOver(server, r.second().getUUID(), r.player().getUUID(), true);
			helper.assertTrue(bracelets(r.player()) == 1, bracelets(r.player()) + " bracelets after beating both Leaders, expected 1");
			helper.assertTrue(LeaderBracelets.given(server, r.player().getUUID(), r.realm().id()), "the bracelet isn't recorded");
			Trainers.battleOver(server, r.first().getUUID(), r.player().getUUID(), true);
			Trainers.battleOver(server, r.second().getUUID(), r.player().getUUID(), true);
			helper.assertTrue(bracelets(r.player()) == 1, bracelets(r.player()) + " bracelets after beating them again, expected 1");
			// Saved and loaded: the bracelet given, the villages beaten and the Leaders' villages are all still known.
			LeaderBracelets.Data data = LeaderBracelets.Data.get(server);
			CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
			LeaderBracelets.Data loaded = LeaderBracelets.Data.load(saved, helper.getLevel().registryAccess());
			CompoundTag again = loaded.save(new CompoundTag(), helper.getLevel().registryAccess());
			helper.assertTrue(saved.equals(again) && saved.toString().contains("megaBracelet") && saved.toString().contains("leaderHalls"),
				"saved " + saved + " loaded as " + again);
			LeaderBracelets.Data empty = LeaderBracelets.Data.load(new CompoundTag(), helper.getLevel().registryAccess());
			helper.assertTrue(empty.save(new CompoundTag(), helper.getLevel().registryAccess()).toString().contains("leaderHalls"), "an old save doesn't load empty");
		} finally {
			cleanUp(helper, r);
		}
		helper.succeed();
	}

	/**
	 * Likely breakages: a village whose Leader left (its round sees none) no longer counts, so the one Leader left gives
	 * the bracelet; with {@code villageMegas} off nothing is given or marked; a Leader in a village with no realm marks
	 * the village but gives nothing.
	 */
	//$ gametest_batch 'CompatGameTests.AREA' '"megaLeaderBraceletEdges"'
	@GameTest(template = CompatGameTests.AREA, batch = "megaLeaderBraceletEdges")
	public void braceletEdges(GameTestHelper helper) {
		Land r = realm(helper);
		boolean megas = MegaBracelets.ENABLED;
		try {
			var server = helper.getLevel().getServer();
			MegaBracelets.ENABLED = false;
			Trainers.battleOver(server, r.first().getUUID(), r.player().getUUID(), true);
			Trainers.battleOver(server, r.second().getUUID(), r.player().getUUID(), true);
			helper.assertTrue(bracelets(r.player()) == 0 && LeaderBracelets.beatenBy(server, r.player().getUUID()).isEmpty(),
				"villageMegas off: a bracelet or a beaten village");
			MegaBracelets.ENABLED = true;
			LeaderBracelets.round(helper.getLevel(), r.realm().members().get(1).hall().pos(), List.of()); // the member's Leader left
			Trainers.battleOver(server, r.first().getUUID(), r.player().getUUID(), true);
			helper.assertTrue(bracelets(r.player()) == 1, bracelets(r.player()) + " bracelets with the only Leader left beaten, expected 1");
			RealmData.get(server).removeRealm(r.realm().id());
			ServerPlayer other = helper.makeMockServerPlayerInLevel();
			other.getInventory().clearContent();
			Trainers.battleOver(server, r.first().getUUID(), other.getUUID(), true);
			helper.assertTrue(bracelets(other) == 0 && LeaderBracelets.beatenBy(server, other.getUUID()).contains(r.realm().capital()),
				"no realm: a bracelet, or the village not marked");
			server.getPlayerList().remove(other);
		} finally {
			MegaBracelets.ENABLED = megas;
			cleanUp(helper, r);
		}
		helper.succeed();
	}
}
