package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-07): the Builder selling and building the grander homes (ROADMAP 34.15, 34.15a, 34.15b, 34.16, wiki
 * grander-homes.md), written from the spec. Covers what HomeGradeGameTests, ManorGameTests, QaGranderHomesGameTests and
 * the BuilderGameTests builds leave out: the Artisan's House coming with the level-up itself; the "village round the
 * nearest hall" from both sides of its rim (a household just outside the circle doesn't count, one on it does); each
 * upgrade only from the builder who finished the tier below; a builder's level-ups never putting a grander home in the
 * Table; every bed of every tier, turned and mirrored, giving its grade before and after a save and reload; and a real
 * Artisan's House built across a save and reload that ends finished with exactly its materials and a builder selling
 * its upgrade.
 */
public class QaGranderHomes1107GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** A hall at {@code hallAt} (a Hamlet) of {@code radius}, the shipped class ladder, classes on; all put back afterwards. */
	static BlockPos village(GameTestHelper helper, BlockPos hallAt, int radius) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int old = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = old;
			new WorkplaceConfig().apply();
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			VillageHalls.forgetNearest();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return helper.absolutePos(hallAt);
	}

	static Villager classed(GameTestHelper helper, BlockPos pos, String name, String social) {
		Villager v = villager(helper, pos, name);
		SocialClasses.SocialClass c = SocialClasses.get(ours(social));
		helper.assertTrue(c != null, "no shipped class " + social);
		SocialClasses.seed(v, c);
		return v;
	}

	/** A builder of {@code level} with one ordinary trade (a villager with no offers won't open the trade screen). */
	static Villager builder(GameTestHelper helper, BlockPos pos, String name, int level) {
		Villager b = villager(helper, pos, name);
		b.setVillagerData(new VillagerData(b.getVillagerData().getType(), ModVillagers.BUILDER, level));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.STICK, 4), 12, 1, 0.05f));
		b.setOffers(offers);
		return b;
	}

	/** {@code player} right-clicks {@code villager}, the way a player opens its trades, and closes the screen again. */
	static void openTrades(GameTestHelper helper, Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trade screen for " + villager.getName().getString() + ": " + player.containerMenu);
		player.closeContainer();
	}

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static List<MerchantOffer> offersOf(Villager v, ResourceLocation id) {
		return v.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult()).map(d -> d.structure().equals(id)).orElse(false)).toList();
	}

	static long sold(Villager v, StarterBlueprints.Entry e) {
		return offersOf(v, e.id()).size();
	}

	/** Every blueprint id a villager sells. */
	static List<ResourceLocation> blueprintsSold(Villager v) {
		return v.getOffers().stream().flatMap(o -> BlueprintItem.data(o.getResult()).map(d -> d.structure()).stream()).toList();
	}

	// --- selling ---------------------------------------------------------------------------------

	/**
	 * "A builder from Apprentice up sells the Artisan's House (10 emeralds) ... added when the builder levels up": a Novice
	 * whose trades a player opens doesn't sell it; the level-up to Apprentice adds it at once, for 10 emeralds, its item
	 * named for the house; the next level-up doesn't add it twice. In a village of Peasants only, no Townhouse or Manor
	 * comes with it, and no upgrade tier ever does.
	 */
	//$ gametest_batch AREA '"qaGrand1107LevelUp"'
	@GameTest(template = AREA, batch = "qaGrand1107LevelUp")
	public void theLevelUpToApprenticeAddsTheArtisansHouseOnce(GameTestHelper helper) {
		village(helper, new BlockPos(19, 2, 19), 32);
		classed(helper, new BlockPos(14, 2, 14), "Pip", "peasant");
		classed(helper, new BlockPos(16, 2, 14), "Wat", "peasant");
		Villager novice = builder(helper, new BlockPos(6, 2, 6), "Hob", 1);
		ServerPlayer player = player(helper);
		try {
			openTrades(helper, novice, player);
			helper.assertTrue(sold(novice, StarterBlueprints.ARTISANS_HOUSE) == 0, "a Novice sells the Artisan's House: " + blueprintsSold(novice));
			((VillagerAccessor) novice).aliveworkplace$increaseMerchantCareer();
			helper.assertTrue(novice.getVillagerData().getLevel() == 2, "not an Apprentice after the level-up: " + novice.getVillagerData().getLevel());
			List<MerchantOffer> house = offersOf(novice, StarterBlueprints.ARTISANS_HOUSE.id());
			helper.assertTrue(house.size() == 1, "the level-up to Apprentice gives " + house.size() + " Artisan's House trades: " + blueprintsSold(novice));
			MerchantOffer offer = house.get(0);
			helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 10 && offer.getCostB().isEmpty(),
				"the Artisan's House costs " + offer.getBaseCostA() + " + " + offer.getCostB() + ", not 10 emeralds");
			String name = offer.getResult().getHoverName().getString();
			helper.assertTrue(name.contains("Artisan's House") && !name.contains("aliveworkplace"), "the bought item reads '" + name + "'");
			((VillagerAccessor) novice).aliveworkplace$increaseMerchantCareer();
			openTrades(helper, novice, player);
			helper.assertTrue(sold(novice, StarterBlueprints.ARTISANS_HOUSE) == 1, "after another level-up and a look: "
				+ sold(novice, StarterBlueprints.ARTISANS_HOUSE) + " Artisan's Houses");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.assertTrue(sold(novice, StarterBlueprints.TOWNHOUSE) == 0 && sold(novice, StarterBlueprints.MANOR) == 0,
			"Peasants alone bring a Townhouse or a Manor: " + blueprintsSold(novice));
		for (ResourceLocation id : blueprintsSold(novice)) {
			helper.assertFalse(StarterBlueprints.SOLD_ONLY.stream().anyMatch(e -> e.id().equals(id)) && !id.equals(StarterBlueprints.ARTISANS_HOUSE.id()),
				"a Journeyman who finished nothing sells " + id);
		}
		helper.succeed();
	}

	/**
	 * "The village round the nearest hall" from both sides of its rim, through the player opening a builder's trades: an
	 * Artisan just outside the hall's circle (inside the square round it) doesn't bring the Townhouse, nor a Burgher there
	 * the Manor; a household of Peasants inside doesn't either. An Artisan exactly on the rim brings the Townhouse (14
	 * emeralds) and not the Manor; a Burgher on the rim brings the Manor (20 emeralds).
	 */
	//$ gametest_batch AREA '"qaGrand1107Rim"'
	@GameTest(template = AREA, batch = "qaGrand1107Rim")
	public void theVillagesRimDecidesFromBothSides(GameTestHelper helper) {
		BlockPos hallAt = new BlockPos(4, 2, 4);
		village(helper, hallAt, 12);
		Villager builder = builder(helper, new BlockPos(6, 2, 6), "Hob", 3);
		// 9 and 9 across from the hall: 12.7 blocks, outside the circle of 12, inside the square
		classed(helper, hallAt.offset(9, 0, 9), "Ann", "artisan");
		classed(helper, hallAt.offset(10, 0, 8), "Bertram", "burgher"); // 10 and 8 across: 12.8 blocks, outside
		classed(helper, new BlockPos(8, 2, 4), "Pip", "peasant");
		ServerPlayer player = player(helper);
		try {
			openTrades(helper, builder, player);
			helper.assertTrue(sold(builder, StarterBlueprints.TOWNHOUSE) == 0 && sold(builder, StarterBlueprints.MANOR) == 0,
				"households outside the circle (and Peasants inside) bring: " + blueprintsSold(builder));
			classed(helper, hallAt.offset(12, 0, 0), "Cal", "artisan"); // exactly 12 across: in
			openTrades(helper, builder, player);
			List<MerchantOffer> town = offersOf(builder, StarterBlueprints.TOWNHOUSE.id());
			helper.assertTrue(town.size() == 1 && town.get(0).getBaseCostA().getCount() == 14,
				"an Artisan on the rim brings one Townhouse for 14 emeralds: " + town.stream().map(o -> o.getBaseCostA().toString()).toList());
			helper.assertTrue(sold(builder, StarterBlueprints.MANOR) == 0, "an Artisan brings the Manor: " + blueprintsSold(builder));
			classed(helper, hallAt.offset(0, 0, 12), "Dara", "burgher");
			openTrades(helper, builder, player);
			List<MerchantOffer> manor = offersOf(builder, StarterBlueprints.MANOR.id());
			helper.assertTrue(manor.size() == 1 && manor.get(0).getBaseCostA().getCount() == 20,
				"a Burgher on the rim brings one Manor for 20 emeralds: " + manor.stream().map(o -> o.getBaseCostA().toString()).toList());
			helper.assertTrue(sold(builder, StarterBlueprints.TOWNHOUSE) == 1, "the Townhouse twice: " + blueprintsSold(builder));
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/**
	 * "Each upgrade comes from the builder who finishes the tier below": in a village with a Noble household, two Master
	 * builders whose trades a player opens sell the three first tiers and no upgrade. Mira finishes each tier in turn (the
	 * trade a finished build adds): she then sells the next tier and not the one after it; Odo, who finished nothing,
	 * sells none of them, however often his trades are opened. Each upgrade is added once, keeps with Mira across a save
	 * and reload, and the chat line says so in words.
	 */
	//$ gametest_batch AREA '"qaGrand1107Upgrades"'
	@GameTest(template = AREA, batch = "qaGrand1107Upgrades")
	public void eachUpgradeComesOnlyFromTheBuilderWhoFinishedTheTierBelow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper, new BlockPos(19, 2, 19), 32);
		classed(helper, new BlockPos(14, 2, 14), "Lady Ysolde", "noble");
		Villager mira = builder(helper, new BlockPos(6, 2, 6), "Mira", 5);
		Villager odo = builder(helper, new BlockPos(6, 2, 12), "Odo", 5);
		List<StarterBlueprints.Entry> firsts = List.of(StarterBlueprints.ARTISANS_HOUSE, StarterBlueprints.TOWNHOUSE, StarterBlueprints.MANOR);
		List<List<StarterBlueprints.Entry>> families = List.of(
			List.of(StarterBlueprints.ARTISANS_HOUSE, StarterBlueprints.ARTISANS_HOUSE_2, StarterBlueprints.ARTISANS_HOUSE_3),
			List.of(StarterBlueprints.TOWNHOUSE, StarterBlueprints.TOWNHOUSE_2, StarterBlueprints.TOWNHOUSE_3),
			List.of(StarterBlueprints.MANOR, StarterBlueprints.MANOR_2, StarterBlueprints.MANOR_3));
		ServerPlayer player = player(helper);
		try {
			for (Villager b : List.of(mira, odo)) {
				openTrades(helper, b, player);
				for (StarterBlueprints.Entry e : firsts) {
					helper.assertTrue(sold(b, e) == 1, b.getName().getString() + " sells " + sold(b, e) + " of " + e.id() + ": " + blueprintsSold(b));
				}
				for (StarterBlueprints.Entry e : StarterBlueprints.SOLD_ONLY) {
					helper.assertTrue(firsts.contains(e) || sold(b, e) == 0, b.getName().getString() + " sells " + e.id() + " having finished nothing");
				}
			}
			for (List<StarterBlueprints.Entry> family : families) {
				for (int i = 0; i + 1 < family.size(); i++) {
					StarterBlueprints.Entry below = family.get(i);
					StarterBlueprints.Entry next = family.get(i + 1);
					var added = UpgradeOffers.offer(level, mira, below.id());
					helper.assertTrue(added.equals(java.util.Optional.of(next.id())), "Mira finished " + below.id() + " and was offered " + added + ", not " + next.id());
					helper.assertTrue(sold(mira, next) == 1, "Mira sells " + sold(mira, next) + " of " + next.id());
					if (i + 2 < family.size()) {
						helper.assertTrue(sold(mira, family.get(i + 2)) == 0, "Mira sells " + family.get(i + 2).id() + " having finished only " + below.id());
					}
					helper.assertTrue(UpgradeOffers.offer(level, mira, below.id()).isEmpty() && sold(mira, next) == 1, "finishing " + below.id() + " again adds " + next.id() + " twice");
				}
			}
			openTrades(helper, odo, player);
			openTrades(helper, odo, player);
			for (StarterBlueprints.Entry e : StarterBlueprints.SOLD_ONLY) {
				helper.assertTrue(firsts.contains(e) || sold(odo, e) == 0, "Odo, who finished nothing, sells " + e.id());
			}
			// Mira saved and loaded back keeps every upgrade, once; opening her trades adds no first tier twice
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(mira.save(tag), "Mira wasn't saved");
			mira.discard();
			Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(back instanceof Villager, "Mira didn't load back: " + back);
			level.addFreshEntity(back);
			Villager loaded = (Villager) back;
			openTrades(helper, loaded, player);
			for (List<StarterBlueprints.Entry> family : families) {
				for (StarterBlueprints.Entry e : family) {
					helper.assertTrue(sold(loaded, e) == 1, "after a reload Mira sells " + sold(loaded, e) + " of " + e.id());
				}
			}
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		String said = Component.translatable("message.aliveworkplace.upgrade_for_sale", Component.literal("Mira"),
			Blueprints.displayName(StarterBlueprints.TOWNHOUSE_2.id())).getString();
		helper.assertTrue(said.equals("Mira now sells the Burgher's Townhouse II blueprint: trade with them to build the upgrade."), "the chat line: " + said);
		String manor = Component.translatable("message.aliveworkplace.upgrade_for_sale", Component.literal("Mira"),
			Blueprints.displayName(StarterBlueprints.MANOR_3.id())).getString();
		helper.assertTrue(manor.equals("Mira now sells the Noble's Manor III blueprint: trade with them to build the upgrade."), "the chat line: " + manor);
		helper.succeed();
	}

	/**
	 * "Not in the Blueprint Table" (34.15a, 34.15b): a builder of the village leveling up from Novice to Master unlocks
	 * three builds in the village's library each time, never a grander home. Through it all the village's Table lists no
	 * tier of them by name (plain or in any style), and a request for one is refused, keeping the Blank Blueprint.
	 */
	//$ gametest_batch AREA '"qaGrand1107Table"'
	@GameTest(template = AREA, batch = "qaGrand1107Table")
	public void aBuildersLevelUpsNeverPutAGranderHomeInTheTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, new BlockPos(19, 2, 19), 32);
		boolean unlocks = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = unlocks);
		Villager hob = builder(helper, new BlockPos(6, 2, 6), "Hob", 1);
		Set<ResourceLocation> soldOnly = new java.util.HashSet<>();
		for (StarterBlueprints.Entry e : StarterBlueprints.SOLD_ONLY) {
			soldOnly.add(e.id());
		}
		int before = BlueprintUnlocks.unlocked(level, hall).size();
		for (int lvl = 2; lvl <= 5; lvl++) {
			((VillagerAccessor) hob).aliveworkplace$increaseMerchantCareer();
			int now = BlueprintUnlocks.unlocked(level, hall).size();
			helper.assertTrue(now > before, "the level-up to " + lvl + " unlocked nothing (" + before + " -> " + now + ")");
			before = now;
			for (ResourceLocation id : soldOnly) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) != BlueprintUnlocks.State.UNLOCKED, "the level-up to " + lvl + " unlocked " + id + " in the Table");
			}
			for (TablePayloads.Entry entry : TableServer.listing(level, hall)) {
				helper.assertFalse(soldOnly.contains(BlueprintStyles.base(entry.id())), "the Table lists " + entry.id() + " after the level-up to " + lvl);
			}
		}
		BlockPos tablePos = new BlockPos(17, 2, 17);
		helper.setBlock(tablePos, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = player(helper);
		try {
			BlockPos at = helper.absolutePos(tablePos);
			player.moveTo(new Vec3(at.getX() + 1.5, at.getY(), at.getZ() + 0.5));
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
			for (ResourceLocation id : List.of(StarterBlueprints.ARTISANS_HOUSE.id(), BlueprintStyles.styled(StarterBlueprints.TOWNHOUSE.id(), "stonework"),
					StarterBlueprints.MANOR_2.id())) {
				helper.assertFalse(TableServer.take(player, at, id), "the Table handed out " + id);
			}
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 3, "a refused request used up a Blank Blueprint");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// --- the grade a bed gives -------------------------------------------------------------------

	/** Where a blueprint's bed heads stand for {@code placement}. */
	private static List<BlockPos> bedHeads(ServerLevel level, ResourceLocation id, BlueprintData.Placement placement) {
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new IllegalStateException("no blueprint " + id));
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(blueprint, placement).targets().entrySet()) {
			BlockState s = e.getValue();
			if (s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(e.getKey().immutable());
			}
		}
		return out;
	}

	/** A placement of {@code entry}'s biggest tier turned and mirrored, moved so its box starts at (1, 2, 1) of the area. */
	private static BlueprintData.Placement turnedAndMirrored(GameTestHelper helper, StarterBlueprints.Entry biggest) {
		ResourceLocation dim = helper.getLevel().dimension().location();
		BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
		BlueprintData.Placement trial = new BlueprintData.Placement(dim, start, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		BoundingBox box = BlueprintOutline.bounds(trial, biggest.size());
		BlockPos origin = start.offset(start.getX() - box.minX(), start.getY() - box.minY(), start.getZ() - box.minZ());
		return new BlueprintData.Placement(dim, origin, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
	}

	/**
	 * As a save and a load of the world would: the build sites and finished builds from their save (the manager replaced),
	 * and the villager from its save. Returns the loaded villager.
	 */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the villager wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the villager didn't load back: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	/**
	 * Every bed of every tier of {@code family} (each upgrade recorded on the same placement, turned a quarter and
	 * mirrored), gives the tier's grade ({@code grades}) and its garden beauty, and the same after a save and reload.
	 */
	private static void bedsGiveTheirGrade(GameTestHelper helper, List<StarterBlueprints.Entry> family, int[] grades, int[] garden) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		BlueprintData.Placement placement = turnedAndMirrored(helper, family.get(family.size() - 1));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		net.minecraft.world.phys.AABB area = helper.getBounds();
		Villager[] dweller = {villager(helper, new BlockPos(1, 2, 28), "Dweller")};
		int checked = 0;
		for (int t = 0; t < family.size(); t++) {
			StarterBlueprints.Entry tier = family.get(t);
			BuildSiteManager.get(level).recordFinished(tier.id(), placement, UUID.randomUUID());
			List<BlockPos> beds = bedHeads(level, tier.id(), placement);
			helper.assertTrue(!beds.isEmpty(), tier.id() + " has no beds");
			for (BlockPos head : beds) {
				helper.assertTrue(area.contains(Vec3.atCenterOf(head)), "a bed of " + tier.id() + " at " + head + " is outside the test area " + area);
				Beds.home(dweller[0], head);
				int grade = Homes.of(level, dweller[0]).map(Homes.Home::tier).orElse(0);
				helper.assertTrue(grade == grades[t], "a bed of " + tier.id() + " at " + helper.relativePos(head) + " (turned, mirrored) gives grade " + grade
					+ ", not " + grades[t] + ": " + Homes.of(level, dweller[0]));
				helper.assertTrue(ClassNeeds.beauty(level, dweller[0]) == garden[t], "a bed of " + tier.id() + " counts " + ClassNeeds.beauty(level, dweller[0])
					+ " beauty, not " + garden[t]);
				checked++;
			}
			// After a save and reload of the world, the last bed still gives the same
			dweller[0] = reload(helper, level, dweller[0]);
			int grade = Homes.of(level, dweller[0]).map(Homes.Home::tier).orElse(0);
			helper.assertTrue(grade == grades[t], "after a save and reload a bed of " + tier.id() + " gives grade " + grade + ", not " + grades[t]);
			helper.assertTrue(ClassNeeds.beauty(level, dweller[0]) == garden[t], "after a save and reload a bed of " + tier.id() + " counts "
				+ ClassNeeds.beauty(level, dweller[0]) + " beauty, not " + garden[t]);
			Component name = Homes.of(level, dweller[0]).map(Homes.Home::name).orElse(Component.empty());
			helper.assertTrue(name.getString().equals(Blueprints.displayName(tier.id()).getString()) && !name.getString().contains("aliveworkplace"),
				"the home's name reads '" + name.getString() + "'");
		}
		AliveWorkplace.LOG.info("qaGrand1107: {} beds checked in the {} family", checked, family.get(0).id());
		helper.succeed();
	}

	/** The Artisan's House I, II and III (I, II and III): its two beds, the workshop wing's third, every one turned and mirrored. */
	//$ gametest_batch HUGE '"qaGrand1107BedsArtisan"'
	@GameTest(template = HUGE, batch = "qaGrand1107BedsArtisan")
	public void everyBedOfTheArtisansHouseGivesItsGrade(GameTestHelper helper) {
		bedsGiveTheirGrade(helper, List.of(StarterBlueprints.ARTISANS_HOUSE, StarterBlueprints.ARTISANS_HOUSE_2, StarterBlueprints.ARTISANS_HOUSE_3),
			new int[] {1, 2, 3}, new int[] {0, 0, 0});
	}

	/** The Townhouse I, II and III (II, III and III), the loft's third bedroom too. */
	//$ gametest_batch HUGE '"qaGrand1107BedsTownhouse"'
	@GameTest(template = HUGE, batch = "qaGrand1107BedsTownhouse")
	public void everyBedOfTheTownhouseGivesItsGrade(GameTestHelper helper) {
		bedsGiveTheirGrade(helper, List.of(StarterBlueprints.TOWNHOUSE, StarterBlueprints.TOWNHOUSE_2, StarterBlueprints.TOWNHOUSE_3),
			new int[] {2, 3, 3}, new int[] {0, 0, 0});
	}

	/** The Manor I, II and III (all III), with II's and III's garden counting 3 beauty for whoever sleeps there. */
	//$ gametest_batch HUGE '"qaGrand1107BedsManor"'
	@GameTest(template = HUGE, batch = "qaGrand1107BedsManor")
	public void everyBedOfTheManorGivesItsGrade(GameTestHelper helper) {
		bedsGiveTheirGrade(helper, List.of(StarterBlueprints.MANOR, StarterBlueprints.MANOR_2, StarterBlueprints.MANOR_3),
			new int[] {3, 3, 3}, new int[] {0, 3, 3});
	}

	// --- building one ----------------------------------------------------------------------------

	/**
	 * A builder puts an Artisan's House up from bare ground with exactly the materials its plan asks for, in barrels by
	 * the bench, and the world is saved and loaded back (the site and the builder) while its walls are going up. It ends
	 * finished, every block as drawn, with nothing of its materials left over anywhere (the barrels, the chest, the
	 * builder's pockets); a bed in it is a grade I home; and the builder who finished it now sells the Artisan's House II
	 * (and not the III).
	 */
	//$ gametest_ticks_batch HUGE '50000' '"qaGrand1107BuildReload"'
	@GameTest(template = HUGE, timeoutTicks = 50000, batch = "qaGrand1107BuildReload")
	public void anArtisansHouseBuiltAcrossASaveAndReloadEndsFinishedWithExactlyItsMaterials(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		BlockPos origin = new BlockPos(9, 2, 8);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.ARTISANS_HOUSE.id()).orElseThrow();
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			for (int left = e.getValue(); left > 0; ) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.ARTISANS_HOUSE.id(), placement);
		helper.assertTrue(site != null, "the Artisan's House didn't start");
		UUID id = site.id();
		Villager[] who = {builder};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (reloaded[0] || s == null) {
				return;
			}
			if (s.stage() == BuildPlan.Stage.STRUCTURE && s.placed() >= 40) {
				reloaded[0] = true;
				who[0] = reload(helper, level, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded while the walls were going up");
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing "
				+ s.missing() + ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
			Map<Item, Integer> left = new LinkedHashMap<>();
			List<Container> containers = new ArrayList<>();
			for (BlockPos b : barrels) {
				containers.add(helper.getBlockEntity(b));
			}
			containers.add(helper.getBlockEntity(new BlockPos(2, 2, 4)));
			containers.add(who[0].getInventory());
			for (Container c : containers) {
				for (int slot = 0; slot < c.getContainerSize(); slot++) {
					ItemStack st = c.getItem(slot);
					if (!st.isEmpty() && needs.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			helper.assertTrue(left.isEmpty(), "materials left over after the build (it should use exactly what it needs): " + left);
			helper.assertTrue(UpgradeOffers.sells(who[0], StarterBlueprints.ARTISANS_HOUSE_2.id()), "the builder who finished it doesn't sell the Artisan's House II");
			helper.assertFalse(UpgradeOffers.sells(who[0], StarterBlueprints.ARTISANS_HOUSE_3.id()), "the builder sells the Artisan's House III already");
			List<BlockPos> beds = bedHeads(level, StarterBlueprints.ARTISANS_HOUSE.id(), placement);
			helper.assertTrue(beds.size() == 2, "the Artisan's House has " + beds.size() + " beds, the wiki says two");
			for (BlockPos head : beds) {
				helper.assertTrue(level.getBlockState(head).getBlock() instanceof BedBlock, "no bed built at " + helper.relativePos(head));
				helper.assertTrue(Homes.at(level, head).map(Homes.Home::tier).orElse(0) == 1, "a bed in the built Artisan's House: " + Homes.at(level, head));
			}
		});
	}
}
