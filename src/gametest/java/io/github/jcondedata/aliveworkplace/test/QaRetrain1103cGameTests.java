package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;
import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.rightClick;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, ROADMAP 26.6 (retraining a villager who already has a job), from the spec: every price tier through the click,
 * money exactly equal to the price and one short, the confirm window's edge (5 seconds), a second click on another
 * villager or with another item, the Blueprint Table's reach (about 4 blocks), a villager who already is a Builder, and
 * the config prices. Everything through the player's sneak-right-click.
 */
public class QaRetrain1103cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);
	private static final BlockPos LECTERN = new BlockPos(10, 2, 10);
	private static final String[] LEVELS = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};

	private static Villager librarian(GameTestHelper helper, BlockPos at, BlockPos lectern, int level, String name) {
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		villager.setVillagerData(villager.getVillagerData().setLevel(level));
		villager.setVillagerXp(level == 1 ? 5 : 70 * level);
		villager.setCustomName(Component.literal(name));
		helper.assertTrue(!villager.getOffers().isEmpty(), "setup: the librarian has no trades");
		return villager;
	}

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds));
		}
		return player;
	}

	private static void sneakClick(ServerPlayer player, Villager villager, Item item) {
		rightClick(player, villager, new ItemStack(item), true);
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static List<String> trades(Villager villager) {
		List<String> out = new ArrayList<>();
		for (MerchantOffer offer : villager.getOffers()) {
			out.add(BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem()) + "->" + BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()));
		}
		return out;
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static String job(Villager villager) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString();
	}

	private static String offer(String name, String level, int price) {
		return name + " (Librarian, " + level + ") can be retrained as a Builder for " + price + (price == 1 ? " emerald" : " emeralds")
			+ ". Their trades and level are lost: they start again as a Novice.";
	}

	/** The librarian's state is unchanged: job, level, lectern and trades. */
	private static void unchanged(GameTestHelper helper, Villager v, int level, List<String> trades, String when) {
		helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, when + ": now a " + job(v));
		helper.assertTrue(v.getVillagerData().getLevel() == level, when + ": level " + v.getVillagerData().getLevel() + ", not " + level);
		helper.assertTrue(site(v).equals(Optional.of(helper.absolutePos(LECTERN))), when + ": works at " + site(v));
		helper.assertTrue(trades(v).equals(trades), when + ": trades changed " + trades(v));
	}

	/**
	 * One price tier through the click: one emerald short is refused, told "1 emerald short", nothing changed; then with
	 * exactly the price (in two stacks) the offer shows the tier's price and the second click takes all of it.
	 */
	private static void tier(GameTestHelper helper, int level, int price) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, level, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, price - 1);

		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains(offer("Dara", LEVELS[level - 1], price)), "tier " + level + ": the offer wasn't shown with " + price + ": " + seen);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains("Retraining Dara costs " + price + " emeralds: you're 1 emerald short. Nothing changed."),
			"tier " + level + ": not told 1 short: " + seen);
		unchanged(helper, dara, level, before, "tier " + level + " one short");
		helper.assertTrue(emeralds(player) == price - 1, "tier " + level + ": money taken while short: " + emeralds(player));

		player.getInventory().setItem(20, new ItemStack(Items.EMERALD, 1)); // exactly the price now, in two stacks
		seen.clear();
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "tier " + level + ": the first click after a refusal paid");
		helper.assertTrue(seen.contains(offer("Dara", LEVELS[level - 1], price)), "tier " + level + ": not offered again: " + seen);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER, "tier " + level + ": exact money didn't pay: " + job(dara) + " " + seen);
		helper.assertTrue(emeralds(player) == 0, "tier " + level + ": " + emeralds(player) + " emeralds left of exactly " + price);
		helper.assertTrue(dara.getVillagerData().getLevel() == 1, "tier " + level + ": kept level " + dara.getVillagerData().getLevel());
		helper.assertTrue(trades(dara).stream().noneMatch(before::contains), "tier " + level + ": old trades stayed " + trades(dara));
		helper.assertTrue(seen.contains("Dara gave up the Librarian job and was retrained as a Builder at the Blueprint Table, for " + price + " emeralds."),
			"tier " + level + ": not told: " + seen);
		helper.succeed();
	}

	//$ gametest_batch AREA '"qaRetrainTier1"'
	@GameTest(template = AREA, batch = "qaRetrainTier1")
	public void noviceCostsExactlyFive(GameTestHelper helper) {
		tier(helper, 1, 5);
	}

	//$ gametest_batch AREA '"qaRetrainTier2"'
	@GameTest(template = AREA, batch = "qaRetrainTier2")
	public void apprenticeCostsExactlyTen(GameTestHelper helper) {
		tier(helper, 2, 10);
	}

	//$ gametest_batch AREA '"qaRetrainTier3"'
	@GameTest(template = AREA, batch = "qaRetrainTier3")
	public void journeymanCostsExactlyTwenty(GameTestHelper helper) {
		tier(helper, 3, 20);
	}

	//$ gametest_batch AREA '"qaRetrainTier4"'
	@GameTest(template = AREA, batch = "qaRetrainTier4")
	public void expertCostsExactlyThirtyFive(GameTestHelper helper) {
		tier(helper, 4, 35);
	}

	//$ gametest_batch AREA '"qaRetrainTier5"'
	@GameTest(template = AREA, batch = "qaRetrainTier5")
	public void masterCostsExactlyFifty(GameTestHelper helper) {
		tier(helper, 5, 50);
	}

	/**
	 * The second click {@code delay} ticks after the offer: within the 5 seconds (100 ticks) it pays; later it only offers
	 * again, and a click right after that one pays.
	 */
	private static void window(GameTestHelper helper, int delay, boolean pays) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 1, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 10);
		long t0 = helper.getLevel().getGameTime();
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains(offer("Dara", "Novice", 5)), "no offer: " + seen);
		long[] clickedAt = {-1};
		String[] after = {null};
		helper.onEachTick(() -> {
			long dt = helper.getLevel().getGameTime() - t0;
			if (clickedAt[0] < 0 && dt >= delay) {
				clickedAt[0] = dt;
				seen.clear();
				sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
				after[0] = job(dara) + " " + emeralds(player) + " " + seen;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(clickedAt[0] >= 0, "not clicked yet");
			helper.assertTrue(clickedAt[0] == delay, "clicked " + clickedAt[0] + " ticks after, not " + delay);
			if (pays) {
				helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER && emeralds(player) == 5,
					"a click " + delay + " ticks after the offer (inside 5 s) didn't pay: " + after[0]);
			} else {
				helper.assertTrue(after[0].startsWith("minecraft:librarian 10 "), "a click " + delay + " ticks after the offer (expired) paid: " + after[0]);
				unchanged(helper, dara, 1, before, "expired click");
				helper.assertTrue(after[0].contains(offer("Dara", "Novice", 5)), "the expired click didn't offer again: " + after[0]);
				sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
				helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER && emeralds(player) == 5,
					"the click right after the new offer didn't pay: " + job(dara) + " " + emeralds(player));
			}
		});
	}

	//$ gametest_ticks_batch AREA '200' '"qaRetrainWindowIn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRetrainWindowIn")
	public void aSecondClickAtFiveSecondsPays(GameTestHelper helper) {
		window(helper, Retraining.CONFIRM_TICKS, true);
	}

	//$ gametest_ticks_batch AREA '200' '"qaRetrainWindowOut"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRetrainWindowOut")
	public void aSecondClickAfterFiveSecondsOffersAgain(GameTestHelper helper) {
		window(helper, Retraining.CONFIRM_TICKS + 1, false);
	}

	/**
	 * An offer made for one villager isn't paid by a click on another: that click offers the other villager instead, and
	 * coming back to the first offers again. Only a second click on the same villager pays, for that villager.
	 */
	//$ gametest_batch AREA '"qaRetrainOtherVillager"'
	@GameTest(template = AREA, batch = "qaRetrainOtherVillager")
	public void aClickOnAnotherVillagerDoesNotPayTheFirstOffer(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 1, "Dara");
		Villager eli = librarian(helper, new BlockPos(2, 2, 4), new BlockPos(12, 2, 12), 2, "Eli");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		seen.clear();
		sneakClick(player, eli, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && eli.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
			"a click on Eli paid an offer: Dara " + job(dara) + ", Eli " + job(eli));
		helper.assertTrue(emeralds(player) == 64, "money taken: " + emeralds(player));
		helper.assertTrue(seen.contains(offer("Eli", "Apprentice", 10)), "Eli wasn't offered: " + seen);
		seen.clear();
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && emeralds(player) == 64,
			"Dara's old offer paid after Eli's: " + job(dara) + " " + emeralds(player));
		helper.assertTrue(seen.contains(offer("Dara", "Novice", 5)), "Dara wasn't offered again: " + seen);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER && emeralds(player) == 59,
			"Dara's second click: " + job(dara) + ", " + emeralds(player) + " left");
		helper.assertTrue(eli.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "Eli changed too: " + job(eli));
		helper.succeed();
	}

	/** A second click holding another job's item (a pickaxe, no blast furnace near) doesn't pay for the Builder offer. */
	//$ gametest_batch AREA '"qaRetrainOtherItem"'
	@GameTest(template = AREA, batch = "qaRetrainOtherItem")
	public void aSecondClickWithAnotherJobsItemDoesNotPay(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 3, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, dara, Items.IRON_PICKAXE);
		unchanged(helper, dara, 3, before, "pickaxe second click");
		helper.assertTrue(emeralds(player) == 64, "the pickaxe click paid: " + emeralds(player));
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("was retrained")), "told retrained: " + seen);
		helper.succeed();
	}

	/**
	 * The Blueprint Table's reach (about 4 blocks; Stations.REACH 4.5 from the villager's feet to the table's centre): a
	 * table 4 across and 2 along (20.25, exactly the reach squared) is taken; tables 5 across, or 4 and 3, are not.
	 */
	//$ gametest_batch AREA '"qaRetrainReachIn"'
	@GameTest(template = AREA, batch = "qaRetrainReachIn")
	public void aTableAtTheEdgeOfReachRetrains(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos edge = STANDING.offset(4, 0, 2);
		helper.setBlock(edge, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 1, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains(offer("Dara", "Novice", 5)), "a table 4 and 2 away wasn't in reach: " + seen);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER, "not retrained: " + job(dara));
		helper.assertTrue(site(dara).equals(Optional.of(helper.absolutePos(edge))), "works at " + site(dara));
		helper.succeed();
	}

	//$ gametest_batch AREA '"qaRetrainReachOut"'
	@GameTest(template = AREA, batch = "qaRetrainReachOut")
	public void aTableJustOutOfReachIsNotUsed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STANDING.offset(5, 0, 0), ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(STANDING.offset(4, 0, 3), ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 1, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		unchanged(helper, dara, 1, before, "tables out of reach");
		helper.assertTrue(emeralds(player) == 64, "paid with no table in reach: " + emeralds(player));
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("can be retrained")), "offered with no table in reach: " + seen);
		helper.assertTrue(seen.contains("The Builder job needs a Blueprint Table: stand them next to one, then hand them this again."),
			"not told it needs a table: " + seen);
		helper.succeed();
	}

	/** A villager who is already a Builder (the same job as the item) isn't offered or charged, and keeps their table. */
	//$ gametest_batch AREA '"qaRetrainSameJob"'
	@GameTest(template = AREA, batch = "qaRetrainSameJob")
	public void aBuilderIsNotOfferedTheBuildersJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos own = new BlockPos(6, 2, 4);
		helper.setBlock(own, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager bob = helper.spawn(EntityType.VILLAGER, STANDING);
		bob.setNoAi(true);
		bob.setCustomName(Component.literal("Bob"));
		Builders.employ(helper.getLevel(), bob, helper.absolutePos(own));
		bob.setVillagerData(bob.getVillagerData().setLevel(3));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		sneakClick(player, bob, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, bob, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(bob.getVillagerData().getProfession() == ModVillagers.BUILDER, "now a " + job(bob));
		helper.assertTrue(bob.getVillagerData().getLevel() == 3, "a builder lost their level: " + bob.getVillagerData().getLevel());
		helper.assertTrue(site(bob).equals(Optional.of(helper.absolutePos(own))), "moved to " + site(bob));
		helper.assertTrue(emeralds(player) == 64, "a builder was charged: " + emeralds(player));
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("retrained")), "offered: " + seen);
		helper.succeed();
	}

	/**
	 * The prices are a config value (retrainPrices): set to 0, 2, 3, 4, 7, a Journeyman costs 3 (shown and charged), and a
	 * Novice is retrained for nothing by a player with no emeralds at all.
	 */
	//$ gametest_batch AREA '"qaRetrainConfig"'
	@GameTest(template = AREA, batch = "qaRetrainConfig")
	public void theConfigPricesAreShownAndCharged(GameTestHelper helper) {
		Leftovers.clear(helper);
		int[] old = Retraining.PRICES.clone();
		Leftovers.after(helper, () -> Retraining.PRICES = old);
		Retraining.prices(WorkplaceConfig.parse("{\"retrainPrices\": [0, 2, 3, 4, 7]}").retrainPrices);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, STANDING, LECTERN, 3, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 4);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains(offer("Dara", "Journeyman", 3)), "the config price wasn't shown: " + seen);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER && emeralds(player) == 1,
			"the config price wasn't charged: " + job(dara) + ", " + emeralds(player) + " left of 4");

		helper.setBlock(new BlockPos(3, 2, 9), ModBlocks.BLUEPRINT_TABLE);
		BlockPos lectern2 = new BlockPos(12, 2, 12);
		Villager eli = librarian(helper, new BlockPos(4, 2, 9), lectern2, 1, "Eli");
		List<String> seen2 = new ArrayList<>();
		ServerPlayer broke = payer(helper, seen2, 0);
		sneakClick(broke, eli, ModItems.BLANK_BLUEPRINT);
		sneakClick(broke, eli, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(eli.getVillagerData().getProfession() == ModVillagers.BUILDER, "a free (0) retraining was refused: " + job(eli) + " " + seen2);
		helper.assertTrue(site(eli).equals(Optional.of(helper.absolutePos(new BlockPos(3, 2, 9)))), "Eli works at " + site(eli));
		helper.succeed();
	}
}
