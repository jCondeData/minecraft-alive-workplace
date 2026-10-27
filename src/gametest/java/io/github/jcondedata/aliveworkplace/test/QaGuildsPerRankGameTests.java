package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA for the {@code guildsPerRank} setting's effect in play (30.17, docs/wiki/guilds.md "How many"): a village may have
 * {@code guildsPerRank} guilds per rank above Hamlet (Village 1x, Town 2x, City 3x), one per trade; a full village refuses
 * the next charter with its count (singular for one, plural otherwise). Written from the spec: the existing tests only
 * cover the default of 1 and the setting's parsing.
 */
public class QaGuildsPerRankGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** The config file's value reaches the game: {@code guildsPerRank} 3 in the file makes a Village hold 3, a City 9. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaGuildsPerRankFromTheFileSetsTheCaps(GameTestHelper helper) {
		int before = Guilds.PER_RANK;
		Set<String> arcsBefore = Arcs.DISABLED;
		try {
			WorkplaceConfig.parse("{\"guildsPerRank\": 3}").apply();
			helper.assertTrue(Guilds.PER_RANK == 3, "guildsPerRank 3 in the file: PER_RANK " + Guilds.PER_RANK);
			helper.assertTrue(Guilds.cap(VillageRanks.Rank.HAMLET) == 0 && Guilds.cap(VillageRanks.Rank.VILLAGE) == 3
				&& Guilds.cap(VillageRanks.Rank.TOWN) == 6 && Guilds.cap(VillageRanks.Rank.CITY) == 9,
				"caps at 3: " + Guilds.cap(VillageRanks.Rank.VILLAGE) + "/" + Guilds.cap(VillageRanks.Rank.TOWN) + "/" + Guilds.cap(VillageRanks.Rank.CITY));
			WorkplaceConfig.parse("{\"guildsPerRank\": 4}").apply();
			helper.assertTrue(Guilds.cap(VillageRanks.Rank.HAMLET) == 0 && Guilds.cap(VillageRanks.Rank.VILLAGE) == 4
				&& Guilds.cap(VillageRanks.Rank.TOWN) == 8 && Guilds.cap(VillageRanks.Rank.CITY) == 12, "at 4 a City holds 12: " + Guilds.cap(VillageRanks.Rank.CITY));
			WorkplaceConfig.parse("{\"guildsPerRank\": 99}").apply();
			helper.assertTrue(Guilds.cap(VillageRanks.Rank.CITY) == 12, "99 in the file is held to 4 in play: City " + Guilds.cap(VillageRanks.Rank.CITY));
		} finally {
			new WorkplaceConfig().apply();
			Guilds.PER_RANK = before;
			Arcs.DISABLED = arcsBefore;
		}
		helper.succeed();
	}

	/**
	 * At 2, through the player's sneak-right-click: a Village takes a second charter and refuses a third, in the
	 * plural with its count ("may have 2 guilds"), and keeps the refused charter; once a Town it takes the third.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuildsPerRankTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildsPerRankTwo")
	public void qaTwoPerRankAVillageTakesTwoAndRefusesAThird(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = master(helper, new BlockPos(4, 2, 4), "Dara", ModVillagers.BUILDER);
		Villager bram = master(helper, new BlockPos(7, 2, 4), "Bram", ModVillagers.MINER);
		Villager fenn = master(helper, new BlockPos(10, 2, 4), "Fenn", ModVillagers.LUMBERJACK);
		helper.runAfterDelay(5, () -> {
			int before = Guilds.PER_RANK;
			try {
				Guilds.PER_RANK = 2;
				ServerLevel level = helper.getLevel();
				String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
				hall.setRank(VillageRanks.Rank.VILLAGE);
				Guilds.forget();
				ServerPlayer player = player(helper);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.GUILD_CHARTER, 5));
				player.setShiftKeyDown(true);
				sneakClick(player, dara);
				sneakClick(player, bram);
				helper.assertTrue(hall.guilds().size() == 2 && ModAttachments.GUILD_MASTER.has(dara) && ModAttachments.GUILD_MASTER.has(bram),
					"at 2 a Village should take two charters: " + hall.guilds());
				helper.assertTrue(player.getMainHandItem().getCount() == 3, "two charters used: " + player.getMainHandItem().getCount());
				sneakClick(player, fenn);
				helper.assertTrue(hall.guilds().size() == 2 && !ModAttachments.GUILD_MASTER.has(fenn), "a third chartered in a Village at 2: " + hall.guilds());
				helper.assertTrue(player.getMainHandItem().getCount() == 3, "the refused charter was used up: " + player.getMainHandItem().getCount());
				Guilds.Offer third = Guilds.offer(player, fenn, player.getMainHandItem());
				say(helper, third, Guilds.Outcome.FULL, village + " is a Village and may have 2 guilds; it has them all.");
				hall.setRank(VillageRanks.Rank.TOWN);
				sneakClick(player, fenn);
				helper.assertTrue(hall.guilds().size() == 3 && ModAttachments.GUILD_MASTER.has(fenn), "a Town at 2 refused its third: " + hall.guilds());
			} finally {
				Guilds.PER_RANK = before;
			}
			helper.succeed();
		});
	}

	/**
	 * At 4 (the most): a Town holds 8 (the 9th refused with "may have 8 guilds"); a City holds 12, so every guild that
	 * loads (fewer than 12 without Cobblemon) can be chartered in one City.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuildsPerRankFour"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildsPerRankFour")
	public void qaFourPerRankATownHoldsEightAndACityTwelve(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		List<Villager> masters = new ArrayList<>();
		int i = 0;
		for (Guilds.Guild guild : Guilds.all().values()) {
			VillagerProfession job = professionOf(guild);
			if (job == null) {
				continue;
			}
			int x = 2 + (i % 7) * 3;
			int z = i < 7 ? 3 : 19;
			masters.add(master(helper, new BlockPos(x, 2, z), "M" + i, job));
			i++;
		}
		helper.runAfterDelay(5, () -> {
			int before = Guilds.PER_RANK;
			try {
				Guilds.PER_RANK = 4;
				ServerLevel level = helper.getLevel();
				String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
				helper.assertTrue(masters.size() >= 10, "fewer than 10 guilds with a trade to charter: " + Guilds.all().keySet());
				hall.setRank(VillageRanks.Rank.TOWN);
				Guilds.forget();
				for (int n = 0; n < 8; n++) {
					Guilds.Offer o = Guilds.grant(level, masters.get(n));
					helper.assertTrue(o.outcome() == Guilds.Outcome.GRANTED, "a Town at 4, guild " + (n + 1) + ": " + o.outcome() + " " + o.message().getString());
				}
				say(helper, Guilds.grant(level, masters.get(8)), Guilds.Outcome.FULL, village + " is a Town and may have 8 guilds; it has them all.");
				hall.setRank(VillageRanks.Rank.CITY);
				for (int n = 8; n < Math.min(12, masters.size()); n++) {
					Guilds.Offer o = Guilds.grant(level, masters.get(n));
					helper.assertTrue(o.outcome() == Guilds.Outcome.GRANTED, "a City at 4, guild " + (n + 1) + ": " + o.outcome() + " " + o.message().getString());
				}
				helper.assertTrue(hall.guilds().size() == Math.min(12, masters.size()), "a City at 4: " + hall.guilds().size() + " guilds");
			} finally {
				Guilds.PER_RANK = before;
			}
			helper.succeed();
		});
	}

	/**
	 * Lowering the setting after guilds were chartered loses nothing: a Village at 3 with 3 guilds, the setting put to 1,
	 * keeps all 3 (masters, the hall's list, after the hall's round and over a save and reload of the hall), and refuses
	 * the next one in the singular ("may have 1 guild").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGuildsPerRankLowered"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGuildsPerRankLowered")
	public void qaLoweringTheSettingKeepsTheGuildsButRefusesNewOnes(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager dara = master(helper, new BlockPos(4, 2, 4), "Dara", ModVillagers.BUILDER);
		Villager bram = master(helper, new BlockPos(7, 2, 4), "Bram", ModVillagers.MINER);
		Villager fenn = master(helper, new BlockPos(10, 2, 4), "Fenn", ModVillagers.LUMBERJACK);
		Villager gus = master(helper, new BlockPos(13, 2, 4), "Gus", VillagerProfession.TOOLSMITH);
		helper.runAfterDelay(5, () -> {
			int before = Guilds.PER_RANK;
			try {
				ServerLevel level = helper.getLevel();
				BlockPos pos = helper.absolutePos(HALL);
				String village = VillageHalls.name(level, pos).getString();
				Guilds.PER_RANK = 3;
				hall.setRank(VillageRanks.Rank.VILLAGE);
				Guilds.forget();
				for (Villager v : List.of(dara, bram, fenn)) {
					helper.assertTrue(Guilds.grant(level, v).outcome() == Guilds.Outcome.GRANTED, "at 3 a Village refused " + v.getName().getString());
				}
				List<Guilds.Charter> chartered = hall.guilds();
				helper.assertTrue(chartered.size() == 3, "three chartered: " + chartered);

				Guilds.PER_RANK = 1;
				Guilds.forget();
				Guilds.round(level, pos, hall);
				helper.assertTrue(hall.guilds().equals(chartered), "the hall's round after lowering the setting changed its guilds: " + hall.guilds());
				helper.assertTrue(ModAttachments.GUILD_MASTER.has(dara) && ModAttachments.GUILD_MASTER.has(bram) && ModAttachments.GUILD_MASTER.has(fenn),
					"a Guild Master lost the title when the setting went down");
				say(helper, Guilds.grant(level, gus), Guilds.Outcome.FULL, village + " is a Village and may have 1 guild; it has them all.");
				helper.assertTrue(!ModAttachments.GUILD_MASTER.has(gus) && hall.guilds().size() == 3, "a fourth chartered: " + hall.guilds());

				CompoundTag tag = hall.saveWithoutMetadata(level.registryAccess());
				hall.setGuilds(List.of());
				hall.loadWithComponents(tag, level.registryAccess());
				Guilds.forget();
				CivicEffects.forget();
				Guilds.round(level, pos, hall);
				helper.assertTrue(hall.guilds().equals(chartered), "after a save and reload at 1: " + hall.guilds());
				say(helper, Guilds.grant(level, gus), Guilds.Outcome.FULL, village + " is a Village and may have 1 guild; it has them all.");

				Guilds.PER_RANK = 4; // raised again: the room is back
				helper.assertTrue(Guilds.grant(level, gus).outcome() == Guilds.Outcome.GRANTED && hall.guilds().size() == 4, "at 4 the fourth: " + hall.guilds());
			} finally {
				Guilds.PER_RANK = before;
			}
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static void say(GameTestHelper helper, Guilds.Offer offer, Guilds.Outcome outcome, String message) {
		helper.assertTrue(offer.outcome() == outcome, "expected " + outcome + ", got " + offer.outcome() + ": " + offer.message().getString());
		helper.assertTrue(offer.message().getString().equals(message), "said \"" + offer.message().getString() + "\", expected \"" + message + "\"");
	}

	private static void sneakClick(ServerPlayer player, Villager villager) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
	}

	/** The first trade of {@code guild} that is a registered profession (none: a guild of a mod not installed). */
	private static VillagerProfession professionOf(Guilds.Guild guild) {
		for (ResourceLocation trade : guild.trades()) {
			var job = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(trade);
			if (job.isPresent()) {
				return job.get();
			}
		}
		return null;
	}

	/** A Village Hall (radius 16) alone in the batch, a Hamlet with no guilds; everything put back when the test ends. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos pos = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.removeBlock(pos, false);
			VillageHalls.RADIUS = radius;
			Moods.ENABLED = moods;
			Guilds.forget();
			CivicEffects.forget();
			VillageNeeds.forget();
		});
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(pos);
		entity.setRank(VillageRanks.Rank.HAMLET);
		entity.setGuilds(List.of());
		return entity;
	}

	private static Villager master(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(Component.literal(name));
		villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(5));
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}
}
