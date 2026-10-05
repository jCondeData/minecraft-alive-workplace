package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.grave.UndertakerWork;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.GuardPatrol;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.nurse.NurseWork;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.PorterWork;
import io.github.jcondedata.aliveworkplace.tutor.Tutors;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Healers', Merchants' and Wardens' Guilds (ROADMAP 30.20) and the Trainers' Guild's absence without Cobblemon:
 * their data, and each number with the guild founded and not, through the real code that uses it: the hall's round
 * cures an illness after two days (not three) once the Healers' Guild is founded, nurses and undertakers look 48 blocks
 * out (not 32); a level-3 traveller costs 12 emeralds to hire (not 16) and a player with 12 hires them only once the
 * Merchants' Guild is founded, a porter carries 12 stacks (not 9); an Expert guard trains again (up to Master) and hits
 * 10% harder in a founded Wardens' Guild. The Trainers' Guild's numbers are proven in the compat suite.
 */
public class GuildServiceGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos GUILDHALL = new BlockPos(2, 2, 14);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void serviceGuildsLoadFromData(GameTestHelper helper) {
		Guilds.Guild healers = check(helper, "healers", "Healers' Guild", List.of(AliveWorkplace.id("nurse"), mc("cleric"), AliveWorkplace.id("undertaker")),
			"Nurses, Clerics and Undertakers work 15% faster; the village's ill get well in two days (not three), and nurses and undertakers look 48 blocks out (not 32).");
		helper.assertTrue(healers.perks().get(0) instanceof CivicEffects.WorkPace p && p.percent() == 15, "healers 15% faster");
		helper.assertTrue(healers.perks().get(1) instanceof Guilds.RecoveryDays r && r.days() == -1, "healers: a day sooner");
		helper.assertTrue(healers.perks().get(2) instanceof Guilds.WorkRadius w && w.blocks() == 16
			&& w.jobs().equals(List.of(AliveWorkplace.id("nurse"), AliveWorkplace.id("undertaker"))), "healers: 16 further for nurses and undertakers");
		Guilds.Guild merchants = check(helper, "merchants", "Merchants' Guild", List.of(AliveWorkplace.id("shopkeeper"), AliveWorkplace.id("innkeeper"),
				AliveWorkplace.id("ferryman"), AliveWorkplace.id("postman"), AliveWorkplace.id("porter")),
			"Shopkeepers, Innkeepers, Ferrymen, Postmen and Porters work 15% faster; travellers cost a quarter less to hire, and porters carry 3 more stacks.");
		helper.assertTrue(merchants.perks().get(0) instanceof CivicEffects.WorkPace p && p.percent() == 15, "merchants 15% faster");
		helper.assertTrue(merchants.perks().get(1) instanceof Guilds.HirePrice h && h.percent() == -25, "merchants: hire a quarter less");
		helper.assertTrue(merchants.perks().get(2) instanceof Guilds.Carry c && c.stacks() == 3, "merchants: porters carry 3 more");
		Guilds.Guild wardens = check(helper, "wardens", "Wardens' Guild", List.of(AliveWorkplace.id("guard")),
			"Guards train on the dummies up to Master (not Expert) and hit 10% harder.");
		helper.assertTrue(wardens.perks().size() == 2 && wardens.perks().get(0) instanceof Guilds.TrainUpTo t && t.level() == 5
			&& wardens.perks().get(1) instanceof Guilds.Strength s && s.percent() == 10, "wardens: " + wardens.perks());
		// The Trainers' Guild's file asks for Cobblemon, which this suite doesn't have.
		helper.assertTrue(Guilds.get(AliveWorkplace.id("trainers")) == null, "the Trainers' Guild loaded without Cobblemon");
		String body = "\"name\": \"X\", \"trades\": [], \"perks\": []";
		helper.assertTrue(Guilds.read(AliveWorkplace.id("c1"), com.google.gson.JsonParser.parseString(
			"{\"fabric:load_conditions\": [{\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"minecraft\", \"aliveworkplace\"]}], " + body + "}")) != null,
			"a guild whose mods are all there loads");
		helper.assertTrue(Guilds.read(AliveWorkplace.id("c2"), com.google.gson.JsonParser.parseString(
			"{\"fabric:load_conditions\": [{\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"minecraft\", \"no_such_mod\"]}], " + body + "}")) == null,
			"a guild missing a mod doesn't");
		helper.assertTrue(Guilds.read(AliveWorkplace.id("c3"), com.google.gson.JsonParser.parseString(
			"{\"fabric:load_conditions\": [{\"condition\": \"fabric:any_mods_loaded\", \"values\": [\"no_such_mod\", \"minecraft\"]}], " + body + "}")) != null,
			"any_mods_loaded with one there");
		helper.assertTrue(Guilds.read(AliveWorkplace.id("c4"), com.google.gson.JsonParser.parseString(
			"{\"fabric:load_conditions\": [{\"condition\": \"fabric:not\", \"value\": {\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"minecraft\"]}}], " + body + "}")) == null,
			"fabric:not");
		String bad = "";
		try {
			Guilds.read(AliveWorkplace.id("bad"), com.google.gson.JsonParser.parseString(
				"{\"name\": \"X\", \"trades\": [], \"perks\": [{\"type\": \"aliveworkplace:train_up_to\", \"level\": 9}]}"));
		} catch (IllegalArgumentException e) {
			bad = e.getMessage();
		}
		helper.assertTrue(bad.contains("9") || bad.contains("level"), "training up to level 9 is refused: " + bad);
		helper.assertTrue(Guilds.byPercent(16, -25) == 12 && Guilds.byPercent(8, -20) == 7 && Guilds.byPercent(5, 25) == 7 && Guilds.byPercent(4, 0) == 4,
			"percent, rounded up");
		helper.succeed();
	}

	/**
	 * An illness 2.5 days old: the hall's round leaves it (three days) until the Healers' Guild is founded, then cures
	 * it (two); nurses and undertakers look 48 blocks out only once founded, a cleric's reach stays as it was.
	 */
	//$ gametest_ticks_batch AREA '200' '"guildHealers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guildHealers")
	public void healersGuildCuresInTwoDaysAndLooksFurther(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerLevel level = helper.getLevel();
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Hilde", ModVillagers.NURSE, 5);
		Villager nurse = villager(helper, new BlockPos(17, 2, 4), "Nell", ModVillagers.NURSE, 2);
		Villager undertaker = villager(helper, new BlockPos(18, 2, 4), "Ulf", ModVillagers.UNDERTAKER, 2);
		Villager cleric = villager(helper, new BlockPos(19, 2, 4), "Cid", VillagerProfession.CLERIC, 2);
		Villager ill = villager(helper, new BlockPos(6, 2, 6), "Ike", VillagerProfession.MASON, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			long sick = (long) (2.5 * VillageNeeds.DAY);
			ModAttachments.ILL_SINCE.set(ill, level.getGameTime() - sick);
			helper.assertTrue(Sickness.recovery(ill) == 3 * VillageNeeds.DAY, "not founded: three days, got " + Sickness.recovery(ill));
			Sickness.round(level, ill, 1);
			helper.assertTrue(Sickness.isIll(ill), "not founded: still ill after 2.5 days");
			helper.assertTrue(NurseWork.cureRange(nurse) == 32 && UndertakerWork.radius(undertaker) == 32, "not founded: 32 blocks");
			found(helper, hall);
			helper.assertTrue(Sickness.recovery(ill) == 2 * VillageNeeds.DAY, "founded: two days, got " + Sickness.recovery(ill));
			helper.assertTrue(NurseWork.cureRange(nurse) == 48 && UndertakerWork.radius(undertaker) == 48,
				"founded: 48 blocks, got " + NurseWork.cureRange(nurse) + "/" + UndertakerWork.radius(undertaker));
			helper.assertTrue(Guilds.radius(cleric, 32) == 32, "a cleric's reach isn't changed");
			helper.assertTrue(Math.abs(Guilds.pace(cleric) * 1.15f - 1f) < 1e-4f, "a cleric works 15% faster");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(Sickness.recovery(ill) == 3 * VillageNeeds.DAY && NurseWork.cureRange(nurse) == 32, "guilds off: three days, 32 blocks");
			} finally {
				Guilds.ENABLED = true;
			}
			Sickness.round(level, ill, 1);
			helper.assertTrue(!Sickness.isIll(ill), "founded: well after 2.5 days");
			helper.succeed();
		});
	}

	/**
	 * A level-3 traveller costs 16 emeralds, then 12 with the Merchants' Guild founded: a player with 12 can't hire
	 * them before and does after, paying all 12; a porter carries 12 stacks (not 9), a shopkeeper's load is unchanged.
	 */
	//$ gametest_ticks_batch AREA '200' '"guildMerchants"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guildMerchants")
	public void merchantsGuildHiresForLessAndPortersCarryMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerLevel level = helper.getLevel();
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Mara", ModVillagers.INNKEEPER, 5);
		Villager porter = villager(helper, new BlockPos(17, 2, 4), "Pip", ModVillagers.PORTER, 1);
		Villager guest = villager(helper, new BlockPos(6, 2, 6), "Tam", VillagerProfession.NITWIT, 1);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
			Traveller t = Innkeepers.traveller(guest);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // creative players pay nothing
			player.getInventory().add(new ItemStack(Items.EMERALD, 12));
			helper.assertTrue(Innkeepers.emeralds(guest, t) == 16, "not founded: 16 emeralds, got " + Innkeepers.emeralds(guest, t));
			helper.assertTrue(PorterWork.capacity(porter) == 9, "not founded: 9 stacks, got " + PorterWork.capacity(porter));
			helper.assertTrue(!Innkeepers.hire(player, guest), "not founded: hired for 12 emeralds");
			found(helper, hall);
			helper.assertTrue(Innkeepers.emeralds(guest, t) == 12, "founded: 12 emeralds, got " + Innkeepers.emeralds(guest, t));
			helper.assertTrue(PorterWork.capacity(porter) == 12, "founded: 12 stacks, got " + PorterWork.capacity(porter));
			helper.assertTrue(Math.abs(Guilds.pace(porter) * 1.15f - 1f) < 1e-4f, "a porter works 15% faster");
			helper.assertTrue(Guilds.carry(master, 9) == 9, "only porters carry more");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(Innkeepers.emeralds(guest, t) == 16 && PorterWork.capacity(porter) == 9, "guilds off: 16 and 9");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.assertTrue(Innkeepers.hire(player, guest), "founded: not hired for 12 emeralds");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "paid: " + (12 - player.getInventory().countItem(Items.EMERALD)));
			helper.succeed();
		});
	}

	/**
	 * An Expert guard: done with the dummies (Expert) until the Wardens' Guild is founded, then training again up to
	 * Master; a sword hits 10% harder once founded.
	 */
	//$ gametest_ticks_batch AREA '200' '"guildWardens"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guildWardens")
	public void wardensGuildTrainsToMasterAndHitsHarder(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Wulf", ModVillagers.GUARD, 5);
		Villager guard = villager(helper, new BlockPos(17, 2, 4), "Gerd", ModVillagers.GUARD, 4);
		Villager journeyman = villager(helper, new BlockPos(18, 2, 4), "Jory", ModVillagers.GUARD, 3);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			float before = GuardCombat.damage(guard, sword);
			helper.assertTrue(!GuardPatrol.canTrain(guard) && GuardPatrol.canTrain(journeyman), "not founded: an Expert is done training, a Journeyman isn't");
			helper.assertTrue(GuardPatrol.trainUpTo(guard) == 4, "not founded: up to Expert");
			found(helper, hall);
			helper.assertTrue(GuardPatrol.canTrain(guard) && GuardPatrol.trainUpTo(guard) == 5, "founded: an Expert trains up to Master");
			helper.assertTrue(!GuardPatrol.canTrain(master), "a Master has nothing left to learn");
			float after = GuardCombat.damage(guard, sword);
			helper.assertTrue(before > 0 && Math.abs(after / before - 1.1f) < 1e-4f, "founded: 10% harder, " + before + " -> " + after);
			helper.assertTrue(Guilds.pace(guard) == 1f, "the Wardens' Guild doesn't change the pace");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(!GuardPatrol.canTrain(guard) && GuardCombat.damage(guard, sword) == before, "guilds off: Expert, the usual damage");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/** Without Cobblemon a Master Move Tutor has no guild, and lessons, revivals and battle experience are as they were. */
	//$ gametest_ticks_batch AREA '100' '"guildTrainersAbsent"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildTrainersAbsent")
	public void trainersGuildIsAbsentWithoutCobblemon(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager tutor = villager(helper, new BlockPos(16, 2, 4), "Tova", ModVillagers.TUTOR, 5);
		Villager scientist = villager(helper, new BlockPos(17, 2, 4), "Fitz", ModVillagers.FOSSIL_SCIENTIST, 5);
		Villager trainer = villager(helper, new BlockPos(18, 2, 4), "Rex", ModVillagers.TRAINER, 5);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = hallAt(helper);
			hall.setRank(VillageRanks.Rank.CITY);
			hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
			hall.setGuilds(List.of());
			Guilds.Offer offer = Guilds.grant(helper.getLevel(), tutor);
			helper.assertTrue(offer.outcome() == Guilds.Outcome.NO_GUILD, "a tutor's guild without Cobblemon: " + offer.message().getString());
			helper.assertTrue(Tutors.price(tutor, 5) == 24 && FossilScientists.price(scientist) == 8, "the usual prices");
			helper.assertTrue(Trainers.battleXp(trainer, false) == 8 && Trainers.battleXp(trainer, true) == 5, "the usual experience");
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static ResourceLocation mc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}

	private static Guilds.Guild check(GameTestHelper helper, String id, String name, List<ResourceLocation> trades, String perk) {
		Guilds.Guild g = Guilds.get(AliveWorkplace.id(id));
		helper.assertTrue(g != null, "the " + name + " didn't load: " + Guilds.all().keySet());
		helper.assertTrue(g.name().getString().equals(name), "name: " + g.name().getString());
		helper.assertTrue(g.trades().equals(trades), name + " trades: " + g.trades());
		helper.assertTrue(g.perk().getString().equals(perk), name + " perk: " + g.perk().getString());
		return g;
	}

	/** A Village Hall at {@link #HALL} (the hall's radius 16 for the test); what the test changed is put back when it ends. */
	private static void village(GameTestHelper helper) {
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlueprintData.Placement at = placement(helper);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			BuildSiteManager.get(helper.getLevel()).forgetFinished(at);
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(GUILDHALL), Rotation.NONE, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	private static VillageHallBlockEntity hallAt(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	/** A City hall whose {@code master} was just chartered (no Guildhall yet: the guild waits). */
	private static VillageHallBlockEntity chartered(GameTestHelper helper, Villager master) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = hallAt(helper);
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		Guilds.Offer offer = Guilds.grant(level, master);
		helper.assertTrue(offer.outcome() == Guilds.Outcome.GRANTED, "chartered: " + offer.message().getString());
		helper.assertTrue(!Guilds.founded(level, hall, hall.guilds().get(0).id()), "not founded without a Guildhall");
		return hall;
	}

	/** A Guildhall is finished: the guild claims it and is founded. */
	private static void found(GameTestHelper helper, VillageHallBlockEntity hall) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), placement(helper), UUID.randomUUID());
		Guilds.round(level, helper.absolutePos(HALL), hall);
		helper.assertTrue(Guilds.founded(level, hall, hall.guilds().get(0).id()), "founded: " + hall.guilds());
	}
}
