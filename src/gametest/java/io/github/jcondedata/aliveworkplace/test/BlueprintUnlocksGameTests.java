package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Blueprint Table as a collection (ROADMAP 26.10): the starting set is unlocked and the rest locked, rare builds are
 * a "???" with nothing sent; buying, a builder's level-up, the command and a found blueprint unlock builds; an old
 * village unlocks what stands; a locked build is refused; the switch off unlocks everything; the unlocks are saved.
 * Each test is alone in its batch, with its own hall and a small village radius.
 */
public class BlueprintUnlocksGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos TABLE = new BlockPos(5, 2, 5);

	/** Its own hall, the switch on and the village radius small, both put back after the test. */
	private static void setUp(GameTestHelper helper, boolean enabled) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int radius = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = enabled;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = radius;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
	}

	/** Runs {@code check} once the hall's point of interest is in (the level adds it a tick later), then succeeds. */
	private static void then(GameTestHelper helper, Runnable check) {
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(helper.getLevel(), helper.absolutePos(TABLE))), "the Table isn't in the test's village");
			check.run();
			helper.succeed();
		});
	}

	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static State state(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return BlueprintUnlocks.state(helper.getLevel(), helper.absolutePos(HALL), entry.id());
	}

	/** The starting set is unlocked; the rest is locked with how to get it; a rare build is a "???" with nothing about it sent. */
	//$ gametest_ticks_batch AREA '40' '"unlocks_listing"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_listing")
	public void startingSetUnlockedRestLockedRareHidden(GameTestHelper helper) {
		setUp(helper, true);
		then(helper, () -> {
			ServerLevel level = helper.getLevel();
			Set<ResourceLocation> starting = BlueprintUnlocks.starting();
			helper.assertTrue(starting.size() >= 15 && starting.size() <= 20, "the starting set has " + starting.size() + " builds");
			helper.assertTrue(starting.contains(StarterBlueprints.STARTER_COTTAGE.id()) && starting.contains(StarterBlueprints.SMITHY.id())
				&& starting.contains(StarterBlueprints.FARMSTEAD.id()), "the basics aren't in the starting set: " + starting);
			List<TablePayloads.Entry> list = TableServer.listing(level, helper.absolutePos(HALL));
			Map<ResourceLocation, TablePayloads.Entry> byId = list.stream().collect(Collectors.toMap(TablePayloads.Entry::id, Function.identity()));
			for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
				if (starting.contains(id)) {
					helper.assertTrue(byId.get(id) != null && byId.get(id).unlocked(), id + " of the starting set isn't unlocked");
				}
			}
			TablePayloads.Entry stall = byId.get(StarterBlueprints.MARKET_STALL.id());
			helper.assertTrue(stall != null && stall.locked() && stall.hint().equals("screen.aliveworkplace.table.hint.builder.2"), "the Market Stall: " + stall);
			TablePayloads.Entry upgrade = byId.get(StarterBlueprints.SMITHY_2.id());
			helper.assertTrue(upgrade != null && upgrade.locked() && !upgrade.hint().isEmpty(), "Smithy II: " + upgrade);
			TablePayloads.Entry well = byId.get(StarterBlueprints.WELL.id());
			helper.assertTrue(well != null && well.locked() && well.hint().equals("screen.aliveworkplace.table.hint.market"), "the Well: " + well);
			// Unlocked first, then locked, then the "???"s.
			int last = -1;
			for (TablePayloads.Entry e : list) {
				int rank = e.hidden() ? 2 : e.locked() ? 1 : 0;
				helper.assertTrue(rank >= last, "the listing isn't unlocked first: " + e.id() + " after a later kind");
				last = rank;
			}
			// Rare builds: a "???" with a stand-in id, no size, and no name of theirs on the wire.
			helper.assertFalse(byId.containsKey(StarterBlueprints.MANOR.id()) || byId.containsKey(StarterBlueprints.CHAPEL.id()), "a rare build is listed by id");
			List<TablePayloads.Entry> hidden = list.stream().filter(TablePayloads.Entry::hidden).toList();
			helper.assertTrue(hidden.size() >= StarterBlueprints.SOLD_ONLY.size() + 1, "only " + hidden.size() + " ??? entries");
			for (TablePayloads.Entry e : hidden) {
				helper.assertTrue(e.id().getPath().startsWith("hidden/") && e.sizeX() == 0 && e.sizeY() == 0 && e.sizeZ() == 0 && e.blocks() == 0
					&& BlueprintLibrary.get(level, e.id()).isEmpty(), "a ??? entry gives something away: " + e);
			}
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
			TablePayloads.Open.CODEC.encode(buf, new TablePayloads.Open(helper.absolutePos(TABLE), list, true));
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			String wire = new String(bytes, StandardCharsets.ISO_8859_1);
			helper.assertFalse(wire.contains("manor") || wire.contains("chapel") || wire.contains("townhouse"), "a ??? build's id went to the client");
		});
	}

	/** Get Blueprint refuses a locked build and a "???" (no Blank Blueprint used) and hands out an unlocked one. */
	//$ gametest_ticks_batch AREA '40' '"unlocks_refused"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_refused")
	public void lockedGetBlueprintIsRefused(GameTestHelper helper) {
		setUp(helper, true);
		then(helper, () -> {
			ServerPlayer player = playerAtTable(helper);
			try {
				BlockPos table = helper.absolutePos(TABLE);
				player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
				helper.assertFalse(TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "the Table handed out a locked Market Stall");
				helper.assertFalse(TableServer.take(player, table, StarterBlueprints.MANOR.id()), "the Table handed out a ??? Manor asked for by id");
				helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 3, "a refusal used a Blank Blueprint");
				helper.assertTrue(TableServer.take(player, table, StarterBlueprints.STONE_HOUSE.id()), "the Table refused the Stone House of the starting set");
				helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "the Stone House didn't cost a Blank Blueprint");
			} finally {
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
		});
	}

	/** Buying a blueprint from a builder, or from a market-day trader, unlocks it in the village (the item is still sold). */
	//$ gametest_ticks_batch AREA '40' '"unlocks_bought"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_bought")
	public void buyingFromABuilderUnlocksIt(GameTestHelper helper) {
		setUp(helper, true);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(6, 2, 3));
		then(helper, () -> {
			ServerPlayer player = playerAtTable(helper);
			try {
				helper.assertTrue(state(helper, StarterBlueprints.MARKET_STALL) == State.LOCKED, "the Market Stall isn't locked to begin with");
				builder.setTradingPlayer(player);
				builder.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 8),
					BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size()), 3, 10, 0.05f));
				builder.setTradingPlayer(null);
				helper.assertTrue(state(helper, StarterBlueprints.MARKET_STALL) == State.UNLOCKED, "buying the Market Stall didn't unlock it");
				player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
				helper.assertTrue(TableServer.take(player, helper.absolutePos(TABLE), StarterBlueprints.MARKET_STALL.id()), "the bought Market Stall isn't handed out");
				helper.assertTrue(state(helper, StarterBlueprints.FOUNTAIN) == State.LOCKED, "the Fountain isn't locked to begin with");
				trader.setTradingPlayer(player);
				trader.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 10),
					BlueprintItem.create(StarterBlueprints.FOUNTAIN.id(), StarterBlueprints.FOUNTAIN.size()), 1, 1, 0.05f));
				trader.setTradingPlayer(null);
				helper.assertTrue(state(helper, StarterBlueprints.FOUNTAIN) == State.UNLOCKED, "buying the Fountain from a trader didn't unlock it");
			} finally {
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
		});
	}

	/**
	 * A builder's level-up unlocks {@link BlueprintUnlocks#PER_LEVEL} builds; {@code /workplace unlock} (the questbook's
	 * reward) and a found blueprint used on the Table unlock theirs, a rare one too; a village chest can hold a blueprint.
	 */
	//$ gametest_ticks_batch AREA '40' '"unlocks_ways"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_ways")
	public void levelUpsCommandsAndFoundBlueprintsUnlock(GameTestHelper helper) {
		setUp(helper, true);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		then(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<ResourceLocation> next = BlueprintUnlocks.nextForLevelUp(level, hall);
			helper.assertTrue(next.size() == BlueprintUnlocks.PER_LEVEL, "a level-up would unlock " + next);
			// (a Novice with no bench loses the job in a few ticks: hired again just before)
			builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, builder, 30, null);
			helper.assertTrue(builder.getVillagerData().getLevel() == 2, "the builder didn't level up: " + builder.getVillagerData().getLevel());
			for (ResourceLocation id : next) {
				helper.assertTrue(BlueprintUnlocks.state(level, hall, id) == State.UNLOCKED, "the level-up didn't unlock " + id);
			}

			ServerPlayer player = playerAtTable(helper);
			try {
				helper.assertTrue(state(helper, StarterBlueprints.TERRACE_2) == State.LOCKED, "Terrace II isn't locked to begin with");
				level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
					"workplace unlock " + player.getGameProfile().getName() + " " + StarterBlueprints.TERRACE_2.id());
				helper.assertTrue(state(helper, StarterBlueprints.TERRACE_2) == State.UNLOCKED, "/workplace unlock didn't unlock Terrace II");

				helper.assertTrue(state(helper, StarterBlueprints.CHAPEL) == State.HIDDEN, "the Chapel isn't a ??? to begin with");
				ItemStack found = BlueprintItem.create(StarterBlueprints.CHAPEL.id(), null);
				player.setItemInHand(InteractionHand.MAIN_HAND, found);
				BlockPos table = helper.absolutePos(TABLE);
				player.gameMode.useItemOn(player, level, found, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
				helper.assertTrue(state(helper, StarterBlueprints.CHAPEL) == State.UNLOCKED, "a found Chapel used on the Table didn't unlock it");
				helper.assertTrue(player.getMainHandItem().is(ModItems.BLUEPRINT), "the Table took the blueprint used on it");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}

			// Found in loot: a village storehouse's chest holds a blueprint now and then, of a build of the mod's.
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(hall)).create(LootContextParamSets.CHEST);
			var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.LOOT_TABLE, AliveWorkplace.id("chests/village_storehouse")));
			Set<ResourceLocation> foundIds = new HashSet<>();
			for (int i = 0; i < 300; i++) {
				for (ItemStack stack : table.getRandomItems(params, 2610L + i)) {
					BlueprintItem.data(stack).map(BlueprintData::structure).ifPresent(foundIds::add);
				}
			}
			helper.assertFalse(foundIds.isEmpty(), "no blueprint in 300 village storehouse chests");
			for (ResourceLocation id : foundIds) {
				helper.assertTrue(BlueprintUnlocks.managed(id) && BlueprintLibrary.get(level, id).isPresent(), "a chest's blueprint isn't of a build: " + id);
			}
		});
	}

	/** A village from before 26.10 unlocks, on first use, what it has standing or under construction. */
	//$ gametest_ticks_batch AREA '40' '"unlocks_old_save"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_old_save")
	public void anOldSaveUnlocksWhatStands(GameTestHelper helper) {
		setUp(helper, true);
		then(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			CompoundTag old = entity.saveWithoutMetadata(level.registryAccess());
			old.remove("blueprintUnlocks");
			old.remove("blueprintUnlocksSeeded");
			entity.loadWithComponents(old, level.registryAccess());
			helper.assertFalse(entity.blueprintUnlocksSeeded(), "a hall from an old save counts as seeded");
			BuildSiteManager sites = BuildSiteManager.get(level);
			ResourceLocation dimension = io.github.jcondedata.aliveworkplace.mc.Ids.of(level.dimension());
			BlueprintData.Placement finished = new BlueprintData.Placement(dimension, hall.offset(6, 0, 6), Rotation.NONE, Mirror.NONE);
			sites.recordFinished(StarterBlueprints.SMITHY_2.id(), finished, UUID.randomUUID());
			BuildSite site = sites.create(UUID.randomUUID(), "Tester", StarterBlueprints.LOOKOUT_TOWER.id(),
				new BlueprintData.Placement(dimension, hall.offset(-6, 0, 4), Rotation.NONE, Mirror.NONE));
			try {
				helper.assertTrue(state(helper, StarterBlueprints.SMITHY_2) == State.UNLOCKED, "the Smithy II standing in the village isn't unlocked");
				helper.assertTrue(state(helper, StarterBlueprints.LOOKOUT_TOWER) == State.UNLOCKED, "the Lookout Tower being built isn't unlocked");
				helper.assertTrue(state(helper, StarterBlueprints.STARTER_COTTAGE) == State.UNLOCKED, "the starting set isn't unlocked");
				helper.assertTrue(state(helper, StarterBlueprints.MARKET_STALL) == State.LOCKED, "the Market Stall, nowhere in the village, is unlocked");
				helper.assertTrue(entity.blueprintUnlocksSeeded(), "the hall wasn't marked seeded");
			} finally {
				sites.forgetFinished(finished);
				sites.remove(site.id());
			}
		});
	}

	/** The unlocks and the seeded mark are saved with the hall and come back on load. */
	//$ gametest_ticks_batch AREA '40' '"unlocks_saved"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_saved")
	public void unlocksSurviveSaveAndReload(GameTestHelper helper) {
		setUp(helper, true);
		then(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(BlueprintUnlocks.unlock(level, hall, StarterBlueprints.SMITHY_2.id(), List.of()), "Smithy II wasn't locked");
			helper.assertFalse(BlueprintUnlocks.unlock(level, hall, StarterBlueprints.SMITHY_2.id(), List.of()), "Smithy II unlocked twice");
			CompoundTag saved = level.getBlockEntity(hall).saveWithoutMetadata(level.registryAccess());
			VillageHallBlockEntity loaded = new VillageHallBlockEntity(hall, level.getBlockState(hall));
			loaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(loaded.blueprintUnlocks().contains(StarterBlueprints.SMITHY_2.id()) && loaded.blueprintUnlocksSeeded(),
				"the unlocks didn't come back: " + loaded.blueprintUnlocks());
			helper.assertFalse(loaded.blueprintUnlocks().contains(StarterBlueprints.MARKET_STALL.id()), "the Market Stall came back unlocked");
		});
	}

	/** {@code blueprintUnlocks} off: every build is unlocked and the Table lists what it always did, with no "???". */
	//$ gametest_ticks_batch AREA '40' '"unlocks_off"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "unlocks_off")
	public void theSwitchOffUnlocksEverything(GameTestHelper helper) {
		setUp(helper, false);
		then(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<TablePayloads.Entry> list = TableServer.listing(level, hall);
			helper.assertTrue(list.stream().allMatch(TablePayloads.Entry::unlocked), "an entry isn't unlocked with the switch off");
			helper.assertTrue(list.stream().map(TablePayloads.Entry::id).toList().equals(TableServer.listing(level.getServer()).stream().map(TablePayloads.Entry::id).toList()),
				"the listing depends on the village with the switch off");
			helper.assertTrue(state(helper, StarterBlueprints.MARKET_STALL) == State.UNLOCKED && state(helper, StarterBlueprints.CHAPEL) == State.UNLOCKED,
				"a build is locked with the switch off");
			helper.assertFalse(BlueprintUnlocks.unlock(level, hall, StarterBlueprints.MARKET_STALL.id(), List.of()), "something unlocked with the switch off");
			ServerPlayer player = playerAtTable(helper);
			try {
				player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
				helper.assertTrue(TableServer.take(player, helper.absolutePos(TABLE), StarterBlueprints.MARKET_STALL.id()), "the Market Stall was refused with the switch off");
				helper.assertFalse(TableServer.take(player, helper.absolutePos(TABLE), StarterBlueprints.MANOR.id()), "the Manor came from the Table with the switch off");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
		});
	}
}
