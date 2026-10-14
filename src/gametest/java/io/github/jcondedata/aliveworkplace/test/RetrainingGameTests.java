package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;
import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.rightClick;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Retraining a villager who already has a job (ROADMAP 26.6, the owner's playtest): sneak-right-clicked with a job's
 * item by a free block of that job, they're offered it for a price by level; a second click pays and retrains them, the
 * old job's trades and level gone. Everything goes through the player's own click.
 */
public class RetrainingGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The new job's block, by the villager. */
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);
	/** The Librarian's lectern, out of reach of where they stand. */
	private static final BlockPos LECTERN = new BlockPos(10, 2, 10);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** A Cobbleverse village's Librarian: works at a lectern, traded with up to {@code level}, named. */
	private static Villager librarian(GameTestHelper helper, int level, String name) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true); // stands by the new block while clicked
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		villager.setVillagerData(villager.getVillagerData().setLevel(level));
		villager.setVillagerXp(level == 1 ? 5 : 70 * level);
		villager.setCustomName(Component.literal(name));
		helper.assertTrue(!villager.getOffers().isEmpty(), "setup: the librarian has no trades");
		return villager;
	}

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds)); // off the hand the item goes in
		}
		return player;
	}

	private static void sneakClick(ServerPlayer player, Villager villager, Item item) {
		rightClick(player, villager, new ItemStack(item), true);
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

	/**
	 * The owner's case: a Journeyman Librarian with trades, by a Blueprint Table, handed a Blank Blueprint. The first click
	 * shows the price (Journeyman: 20 emeralds) and changes nothing; the second pays and makes them a Novice Builder at
	 * the table, their trades gone and their lectern free for another librarian.
	 */
	//$ gametest_batch AREA '"retrainLibrarian"'
	@GameTest(template = AREA, batch = "retrainLibrarian")
	public void aLibrarianWithTradesBecomesABuilderForTheShownPrice(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, 3, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 25);

		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the first click already retrained her: " + job(dara));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 25, "the first click took money");
		helper.assertTrue(seen.contains("Dara (Librarian, Journeyman) can be retrained as a Builder for 20 emeralds. Their trades and level are lost: they start again as a Novice."),
			"the offer wasn't shown with its price: " + seen);
		helper.assertTrue(seen.contains("Sneak-right-click them again within 5 seconds to pay."), "not told how to pay: " + seen);

		seen.clear();
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER, "not a Builder after paying: " + job(dara));
		helper.assertTrue(site(dara).equals(Optional.of(helper.absolutePos(STATION))), "the builder works at " + site(dara));
		helper.assertTrue(dara.getVillagerData().getLevel() == 1, "kept level " + dara.getVillagerData().getLevel());
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 5, "paid " + (25 - player.getInventory().countItem(Items.EMERALD)) + ", not 20");
		helper.assertTrue(seen.contains("Dara gave up the Librarian job and was retrained as a Builder at the Blueprint Table, for 20 emeralds."),
			"not told: " + seen);
		List<String> after = trades(dara);
		helper.assertTrue(after.stream().noneMatch(before::contains), "the librarian's trades stayed: " + before + " now " + after);
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(LECTERN)) == 1, "her lectern is still taken");
		helper.succeed();
	}

	/**
	 * Another mod's job (the Ferryman stands in: a job our blocks don't know, with no block nearby) at Apprentice: a
	 * Blueprint by a free Blueprint Table retrains them as a Builder for 10 emeralds.
	 */
	//$ gametest_batch AREA '"retrainOtherMod"'
	@GameTest(template = AREA, batch = "retrainOtherMod")
	public void anotherModsWorkerBecomesABuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.FERRYMAN).setLevel(2));
		villager.setVillagerXp(80);
		villager.setCustomName(Component.literal("Oswin"));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 10);
		sneakClick(player, villager, ModItems.BLUEPRINT);
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Oswin (") && s.contains("Apprentice) can be retrained as a Builder for 10 emeralds.")),
			"the offer: " + seen);
		sneakClick(player, villager, ModItems.BLUEPRINT);
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER, "now a " + job(villager));
		helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the builder works at " + site(villager));
		helper.assertTrue(villager.getVillagerData().getLevel() == 1 && player.getInventory().countItem(Items.EMERALD) == 0,
			"level " + villager.getVillagerData().getLevel() + ", emeralds left " + player.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/** Too little money: told how much is short, and nothing changes (job, level, trades, block, money). */
	//$ gametest_batch AREA '"retrainShort"'
	@GameTest(template = AREA, batch = "retrainShort")
	public void tooLittleMoneyChangesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, 1, "Dara");
		List<String> before = trades(dara);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 3);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(seen.contains("Retraining Dara costs 5 emeralds: you're 2 emeralds short. Nothing changed."), "not told what's short: " + seen);
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "now a " + job(dara));
		helper.assertTrue(site(dara).equals(Optional.of(helper.absolutePos(LECTERN))), "she left her lectern: " + site(dara));
		helper.assertTrue(trades(dara).equals(before), "her trades changed: " + trades(dara));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "money was taken");
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STATION)) == 1, "the table was taken");
		helper.succeed();
	}

	/**
	 * A builder mid-build given another job (a pickaxe by a free blast furnace: Miner) hands the build back first: the
	 * site is gone from the builder, what was built stays, and the blueprint comes back to its owner still placed, so
	 * another builder carries it on from there.
	 */
	//$ gametest_batch AREA '"retrainBuilder"'
	@GameTest(template = AREA, batch = "retrainBuilder")
	public void aBuilderMidSiteHandsTheSiteBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, 2, 6);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(STATION, Blocks.BLAST_FURNACE);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		Villager bob = helper.spawn(EntityType.VILLAGER, STANDING);
		bob.setNoAi(true);
		bob.setCustomName(Component.literal("Bob"));
		Builders.employ(level, bob, helper.absolutePos(table));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, bob, player, TEST_HUT, placement);
		helper.assertTrue(Builders.activeSite(level, bob) == site, "setup: no build under way");

		sneakClick(player, bob, Items.IRON_PICKAXE);
		helper.assertTrue(bob.getVillagerData().getProfession() == ModVillagers.MINER, "now a " + job(bob));
		helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the site still stands as Bob's");
		helper.assertTrue(!ModAttachments.BUILDER_JOB.has(bob), "the miner still has the build");
		helper.assertTrue(seen.contains("Bob handed back the Test Hut: what's built stays, and its blueprint came back, still placed, for another builder to carry on."),
			"not told: " + seen);
		ItemStack blueprint = ItemStack.EMPTY;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				blueprint = player.getInventory().getItem(i);
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the blueprint didn't come back to its owner");
		helper.assertTrue(BlueprintItem.data(blueprint).flatMap(BlueprintData::placement).map(BlueprintData.Placement::origin)
			.equals(Optional.of(placement.origin())), "the blueprint came back unplaced: " + BlueprintItem.data(blueprint));
		helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(table)) == 1, "Bob still holds the Blueprint Table");

		// Another builder at the table carries it on, at the same place.
		Villager ann = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 7));
		ann.setNoAi(true);
		Builders.employ(level, ann, helper.absolutePos(table));
		Builders.assign(player, ann, blueprint);
		BuildSite again = Builders.activeSite(level, ann);
		helper.assertTrue(again != null && again.placement().origin().equals(placement.origin()), "the build can't be carried on: " + again);
		Builders.cancel(level, again);
		helper.succeed();
	}

	/** Children and nitwits are never retrained: two clicks with the money there change nothing. */
	//$ gametest_batch AREA '"retrainRefuse"'
	@GameTest(template = AREA, batch = "retrainRefuse")
	public void childrenAndNitwitsRefuse(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		Villager child = helper.spawn(EntityType.VILLAGER, STANDING);
		child.setNoAi(true);
		child.setBaby(true);
		child.setVillagerData(child.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		sneakClick(player, child, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, child, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(child.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "a child became a " + job(child));
		child.discard();
		Villager nitwit = helper.spawn(EntityType.VILLAGER, STANDING);
		nitwit.setNoAi(true);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		sneakClick(player, nitwit, ModItems.BLANK_BLUEPRINT);
		sneakClick(player, nitwit, ModItems.BLANK_BLUEPRINT);
		helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT, "a nitwit became a " + job(nitwit));
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "money was taken");
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("retrained")), "offered: " + seen);
		helper.succeed();
	}

	/** Retrained, saved and loaded: still a Novice Builder at the table, with the builder's trades. */
	//$ gametest_batch AREA '"retrainSave"'
	@GameTest(template = AREA, batch = "retrainSave")
	public void aRetrainedVillagerStaysRetrainedOverASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, 5, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 50);
		sneakClick(player, dara, ModItems.BLUEPRINT);
		sneakClick(player, dara, ModItems.BLUEPRINT);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Master's price isn't 50");
		List<String> trades = trades(dara);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(dara.saveWithoutId(new CompoundTag()));
		helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.BUILDER, "loaded as a " + job(copy));
		helper.assertTrue(copy.getVillagerData().getLevel() == 1, "loaded at level " + copy.getVillagerData().getLevel());
		helper.assertTrue(site(copy).equals(Optional.of(helper.absolutePos(STATION))), "loaded working at " + site(copy));
		helper.assertTrue(trades(copy).equals(trades), "loaded with trades " + trades(copy) + ", not " + trades);
		copy.discard();
		helper.succeed();
	}

	/**
	 * With workplaceRetraining off, the old rule: a Blueprint Table takes nobody who works already, and a pickaxe by a free
	 * blast furnace switches a librarian to Miner at once, for nothing.
	 */
	//$ gametest_batch AREA '"retrainOff"'
	@GameTest(template = AREA, batch = "retrainOff")
	public void theSwitchOffBringsBackTheOldRule(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.RETRAINING).set(false, level.getServer());
		try {
			helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
			Villager dara = librarian(helper, 3, "Dara");
			List<String> seen = new ArrayList<>();
			ServerPlayer player = payer(helper, seen, 30);
			sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
			sneakClick(player, dara, ModItems.BLANK_BLUEPRINT);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "a Blueprint Table took a librarian: " + job(dara));
			helper.setBlock(STATION, Blocks.BLAST_FURNACE);
			sneakClick(player, dara, Items.IRON_PICKAXE);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.MINER, "one click didn't switch her: " + job(dara));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 30, "money was taken with retraining off");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("retrained")), "offered with retraining off: " + seen);
		} finally {
			level.getGameRules().getRule(ModGameRules.RETRAINING).set(true, level.getServer());
		}
		helper.succeed();
	}
}
