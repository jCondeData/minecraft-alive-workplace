package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Guild Charters, the Guildhall and the Builders' Guild (ROADMAP 30.17): guilds load from data (ours and the test
 * pack's {@code test_farmers}), the charter's refusals and grant (told, chronicled, on the hall's list and the status,
 * Guildhall blueprints for sale), a Guild Master kept over a reload, the perks off before a finished Guildhall and on
 * after (a carpenter 15% faster, 5 helpers at a build), waiting while the Guildhall is gone, the config switch,
 * succession and dissolution, and the Book of Edicts' last row.
 */
public class GuildGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation BUILDERS = AliveWorkplace.id("builders");
	private static final ResourceLocation FARMERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_farmers");

	/** Ours loads with its lang texts and perks, the test pack's with plain ones; the config's switches and cap. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void guildsLoadFromData(GameTestHelper helper) {
		Guilds.Guild builders = Guilds.get(BUILDERS);
		helper.assertTrue(builders != null, "the Builders' Guild didn't load: " + Guilds.all().keySet());
		helper.assertTrue(builders.name().getString().equals("Builders' Guild"), "name: " + builders.name().getString());
		helper.assertTrue(builders.perk().getString().equals("Builders, Carpenters, Masons and Dyers work 15% faster; up to 5 idle builders help at a build (not 3)."),
			"perk: " + builders.perk().getString());
		helper.assertTrue(builders.trades().equals(List.of(AliveWorkplace.id("builder"), AliveWorkplace.id("carpenter"),
			ResourceLocation.withDefaultNamespace("mason"), ResourceLocation.withDefaultNamespace("leatherworker"))), "trades: " + builders.trades());
		helper.assertTrue(builders.perks().size() == 2 && builders.perks().get(0) instanceof CivicEffects.WorkPace pace && pace.percent() == 15
			&& builders.perks().get(1) instanceof Guilds.BuildHelpers h && h.max() == 5, "perks: " + builders.perks());
		Guilds.Guild farmers = Guilds.get(FARMERS);
		helper.assertTrue(farmers != null && farmers.name().getString().equals("Test Farmers' Guild"), "the test pack's guild: " + farmers);
		helper.assertTrue(Guilds.read(AliveWorkplace.id("off"), JsonParser.parseString("{\"enabled\": false}")) == null, "switched off");
		String missing = "";
		try {
			Guilds.read(AliveWorkplace.id("broken"), JsonParser.parseString("{\"name\": \"X\"}"));
		} catch (IllegalArgumentException e) {
			missing = e.getMessage();
		}
		helper.assertTrue(missing.contains("trades"), "a file without trades names the field: " + missing);
		helper.assertTrue(Guilds.cap(VillageRanks.Rank.HAMLET) == 0 && Guilds.cap(VillageRanks.Rank.VILLAGE) == 1
			&& Guilds.cap(VillageRanks.Rank.TOWN) == 2 && Guilds.cap(VillageRanks.Rank.CITY) == 3, "one guild per rank above Hamlet");
		helper.assertTrue(Guilds.isGuildhall(StarterBlueprints.GUILDHALL.id()) && Guilds.isGuildhall(StarterBlueprints.GUILDHALL_2.id())
			&& !Guilds.isGuildhall(StarterBlueprints.WELL.id()), "which builds are Guildhalls");
		WorkplaceConfig defaults = WorkplaceConfig.parse("{}");
		helper.assertTrue(defaults.guilds && defaults.guildsPerRank == 1, "defaults");
		helper.assertTrue(WorkplaceConfig.parse("{\"guildsPerRank\": 9}").guildsPerRank == 4, "clamped to 4");
		helper.assertTrue(WorkplaceConfig.parse("{\"guildsPerRank\": 0}").guildsPerRank == 1, "clamped to 1");
		helper.succeed();
	}

	/** Not a Master, a Hamlet, a guild already, over the cap, no guild for the trade, switched off; then granted. */
	//$ gametest_ticks_batch AREA '100' '"guildCharter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildCharter")
	public void charterRefusalsAndGrant(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 4);
		Villager bram = villager(helper, new BlockPos(7, 2, 6), "Bram", ModVillagers.CARPENTER, 5);
		Villager fenn = villager(helper, new BlockPos(8, 2, 6), "Fenn", VillagerProfession.FARMER, 5);
		Villager gus = villager(helper, new BlockPos(9, 2, 6), "Gus", VillagerProfession.CLERIC, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
			var player = helper.makeMockServerPlayerInLevel();
			player.setShiftKeyDown(true);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a charter is used up (not in creative)
			ItemStack charters = new ItemStack(ModItems.GUILD_CHARTER, 8);

			say(helper, Guilds.offer(player, dara, charters), Guilds.Outcome.NOT_A_MASTER, "Only a Master of their trade can lead a guild, and Dara isn't one yet.");
			dara.setVillagerData(dara.getVillagerData().setLevel(5));
			say(helper, Guilds.offer(player, dara, charters), Guilds.Outcome.HAMLET, village + " is only a Hamlet: a village needs Village rank for a guild.");
			hall.setRank(VillageRanks.Rank.VILLAGE);
			say(helper, Guilds.offer(player, gus, charters), Guilds.Outcome.NO_GUILD, "There is no guild for Gus's trade.");
			helper.assertTrue(charters.getCount() == 8, "refused charters are kept");

			long soldBefore = dara.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).count();
			say(helper, Guilds.offer(player, dara, charters), Guilds.Outcome.GRANTED, "Dara is now the Guild Master of the Builders' Guild in " + village + "!");
			helper.assertTrue(charters.getCount() == 7, "the charter is used up: " + charters.getCount());
			Guilds.Master m = ModAttachments.GUILD_MASTER.get(dara);
			helper.assertTrue(m != null && m.guild().equals(BUILDERS) && m.hall().equals(helper.absolutePos(HALL)), "attachment: " + m);
			helper.assertTrue(hall.guilds().size() == 1 && hall.guilds().get(0).masterName().equals("Dara") && hall.guilds().get(0).guildhall().isEmpty(),
				"the hall's guilds: " + hall.guilds());
			helper.assertTrue(chronicle(hall).contains("Dara was chartered Guild Master of the Builders' Guild"), "chronicle: " + chronicle(hall));
			helper.assertTrue(Guilds.masterLine(dara).getString().equals("Guild Master of the Builders' Guild"), "status line");
			ItemLore lore = VillageHallScreen.person(level, helper.absolutePos(HALL), dara).get(DataComponents.LORE);
			helper.assertTrue(lore != null && lore.lines().stream().map(Component::getString).anyMatch(l -> l.equals("Guild Master of the Builders' Guild")),
				"hall list: " + (lore == null ? "none" : lore.lines().stream().map(Component::getString).toList()));
			long sold = dara.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).count();
			helper.assertTrue(sold == soldBefore + 2, "Guildhall I and II for sale: " + soldBefore + " -> " + sold);

			say(helper, Guilds.offer(player, dara, charters), Guilds.Outcome.ALREADY, "Dara already leads a guild.");
			say(helper, Guilds.offer(player, bram, charters), Guilds.Outcome.TAKEN, village + " already has the Builders' Guild, led by Dara.");
			say(helper, Guilds.offer(player, fenn, charters), Guilds.Outcome.FULL, village + " is a Village and may have 1 guilds; it has them all.");
			hall.setRank(VillageRanks.Rank.TOWN);
			try {
				Guilds.ENABLED = false;
				say(helper, Guilds.offer(player, fenn, charters), Guilds.Outcome.DISABLED, "Guilds are switched off on this server.");
			} finally {
				Guilds.ENABLED = true;
			}
			say(helper, Guilds.offer(player, fenn, charters), Guilds.Outcome.GRANTED, "Fenn is now the Guild Master of the Test Farmers' Guild in " + village + "!");
			helper.assertTrue(hall.guilds().size() == 2, "a Town has two");
			helper.succeed();
		});
	}

	/** A Guild Master and the hall's charters come back from a save; a hall saved before guilds has none. */
	//$ gametest_ticks_batch AREA '100' '"guildSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildSave")
	public void guildMasterIsKeptOverAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "granted");
			CompoundTag villagerTag = dara.saveWithoutId(new CompoundTag());
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(villagerTag);
			Guilds.Master m = ModAttachments.GUILD_MASTER.get(copy);
			helper.assertTrue(m != null && m.guild().equals(BUILDERS) && m.hall().equals(hallPos), "the villager's attachment: " + m);
			CompoundTag tag = hall.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity loaded = (VillageHallBlockEntity) BlockEntity.loadStatic(hallPos, level.getBlockState(hallPos), tag, level.registryAccess());
			helper.assertTrue(loaded != null && loaded.guilds().equals(hall.guilds()), "the hall's guilds: " + (loaded == null ? null : loaded.guilds()));
			tag.remove("guilds");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hallPos, level.getBlockState(hallPos), tag, level.registryAccess());
			helper.assertTrue(old != null && old.guilds().isEmpty(), "a hall saved before guilds");
			helper.succeed();
		});
	}

	/**
	 * Before a Guildhall the perks wait (the usual pace, 3 helpers); a finished one founds the guild (told, chronicled):
	 * a carpenter 15% faster with the status saying why, 5 helpers at a build; the Guildhall gone, it waits again;
	 * switched off, no perks. The Book's last row shows it.
	 */
	//$ gametest_ticks_batch AREA '100' '"guildPerks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildPerks")
	public void perksWaitForTheGuildhall(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = villager(helper, new BlockPos(7, 2, 6), "Bram", ModVillagers.CARPENTER, 2);
		Villager fenn = villager(helper, new BlockPos(8, 2, 6), "Fenn", VillagerProfession.FARMER, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "granted");
			float before = Pace.factor(bram);
			helper.assertTrue(Guilds.pace(bram) == 1f && Guilds.helpers(level, hallPos) == Builders.MAX_HELPERS, "no perks before a Guildhall");
			helper.assertTrue(bookLine(helper, hallPos, "Waiting for a finished Guildhall: its perks wait until then"), "the Book says it waits");

			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), at, UUID.randomUUID());
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(hall.guilds().get(0).guildhall().equals(java.util.Optional.of(at.origin())), "claimed: " + hall.guilds());
			helper.assertTrue(chronicle(hall).contains("The Builders' Guild was founded in its Guildhall"), "chronicle: " + chronicle(hall));
			float after = Pace.factor(bram);
			helper.assertTrue(Math.abs(after * 1.15f - before) < 1e-4f, "a carpenter 15% faster: " + before + " -> " + after);
			helper.assertTrue(Pace.describe(bram).getString().contains("the Builders' Guild"), "status: " + Pace.describe(bram).getString());
			helper.assertTrue(Guilds.pace(fenn) == 1f, "a farmer isn't a member");
			helper.assertTrue(Guilds.helpers(level, hallPos) == 5, "5 helpers at a build: " + Guilds.helpers(level, hallPos));
			helper.assertTrue(bookLine(helper, hallPos, "Founded: its Guildhall stands, its perks are in force")
				&& bookLine(helper, hallPos, "Guild Master: Dara") && bookLine(helper, hallPos, "Members: 2"), "the Book's guild");

			try {
				Guilds.ENABLED = false;
				helper.assertTrue(Guilds.pace(bram) == 1f && Guilds.helpers(level, hallPos) == Builders.MAX_HELPERS, "off: no perks");
				helper.assertTrue(bookLine(helper, hallPos, "Guilds are switched off on this server."), "the Book says it's off");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.assertTrue(hall.guilds().size() == 1, "kept while off");

			BuildSiteManager.get(level).forgetFinished(at);
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(Guilds.pace(bram) == 1f && Guilds.helpers(level, hallPos) == Builders.MAX_HELPERS, "the Guildhall gone: waiting");
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL_2.id(), at, UUID.randomUUID());
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(Math.abs(Guilds.pace(bram) * 1.15f - 1f) < 1e-4f, "repaired (as the Guildhall II): founded again");
			helper.succeed();
		});
	}

	/** With five idle builders by a build, five help at once under a founded Builders' Guild. */
	//$ gametest_ticks_batch AREA '200' '"guildHelpers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guildHelpers")
	public void fiveHelpersAtOneBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "granted");
			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), at, UUID.randomUUID());
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(Guilds.helpers(level, hallPos) == 5 && Builders.MAX_HELPERS == 3, "5 with the guild, 3 without");
			helper.succeed();
		});
	}

	/** The Guild Master dies: the most experienced member takes over (told, chronicled); none left: dissolved. */
	//$ gametest_ticks_batch AREA '100' '"guildSuccession"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildSuccession")
	public void theMostExperiencedMemberSucceeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager dara = villager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = villager(helper, new BlockPos(7, 2, 6), "Bram", ModVillagers.CARPENTER, 3);
		Villager cole = villager(helper, new BlockPos(8, 2, 6), "Cole", VillagerProfession.MASON, 4);
		Villager fenn = villager(helper, new BlockPos(9, 2, 6), "Fenn", VillagerProfession.FARMER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "granted");
			dara.kill();
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(cole) != null && ModAttachments.GUILD_MASTER.get(fenn) == null, "Cole (an Expert) takes over");
			helper.assertTrue(hall.guilds().size() == 1 && hall.guilds().get(0).masterName().equals("Cole") && hall.guilds().get(0).master().equals(cole.getUUID()),
				"the hall's guild: " + hall.guilds());
			helper.assertTrue(chronicle(hall).contains("Cole became Guild Master of the Builders' Guild after Dara"), "chronicle: " + chronicle(hall));
			helper.assertTrue(cole.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).count() >= 2, "Cole sells the Guildhall now");
			cole.kill();
			helper.assertTrue(hall.guilds().get(0).masterName().equals("Bram"), "then Bram: " + hall.guilds());
			bram.kill();
			helper.assertTrue(hall.guilds().isEmpty(), "no one left: " + hall.guilds());
			helper.assertTrue(chronicle(hall).contains("The Builders' Guild had no one left to lead it and was dissolved"), "chronicle: " + chronicle(hall));
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static void say(GameTestHelper helper, Guilds.Offer offer, Guilds.Outcome outcome, String message) {
		helper.assertTrue(offer.outcome() == outcome, "expected " + outcome + ", got " + offer.outcome() + ": " + offer.message().getString());
		helper.assertTrue(offer.message().getString().equals(message), "said: " + offer.message().getString());
	}

	/** A Village Hall (the hall's radius 16 for the test). */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	/** The hall after its first round: a Hamlet, no guilds, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		return hall;
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/** Whether a guild icon on the Book's last row has {@code line} in its lore. */
	private static boolean bookLine(GameTestHelper helper, BlockPos hall, String line) {
		var menu = EdictBook.forTest(helper.makeMockServerPlayerInLevel(), hall);
		for (int slot = EdictBook.FIRST_GUILD; slot < EdictBook.RESERVED_ROW * 9 + 9; slot++) {
			ItemLore lore = menu.icon(slot).get(DataComponents.LORE);
			if (lore != null && lore.lines().stream().map(Component::getString).anyMatch(line::equals)) {
				return true;
			}
		}
		return false;
	}
}
