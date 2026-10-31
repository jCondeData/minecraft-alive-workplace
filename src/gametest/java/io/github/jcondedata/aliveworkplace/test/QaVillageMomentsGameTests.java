package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMoments;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.realm.RealmEdicts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA for 36.2 (village moments complete their quests), written from the spec: each of the eight moments grants its
 * {@code aliveworkplace:village/<moment>} advancement to a player at the village through the event's own entry point,
 * and not to a player far away; a second event doesn't grant it again (the date it was earned stays); refused edicts,
 * refused foundings, an empty treasury and a baby with no hall grant nothing; a realm edict counts for the capital's
 * village; the collecting player gets the takings' moment wherever they stand; and a village that passed its moments
 * before the update (a Noble household, a baby on record, a realm with a realm edict) gets them at its next dawn round.
 * Each test is alone in its batch.
 */
public class QaVillageMomentsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final List<String> EIGHT = List.of(VillageMoments.FIRST_BABY, VillageMoments.RESEARCH_3, VillageMoments.RESEARCH_7,
		VillageMoments.EDICT, VillageMoments.BURGHERS, VillageMoments.NOBLES, VillageMoments.REALM, VillageMoments.TREASURY);

	private static AdvancementHolder advancement(ServerPlayer player, String moment) {
		return player.getServer().getAdvancements().get(AliveWorkplace.id(VillageMoments.PREFIX + moment));
	}

	private static boolean has(ServerPlayer player, String moment) {
		AdvancementHolder advancement = advancement(player, moment);
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	/** When {@code player} earned {@code moment} (null: not yet). */
	private static Instant earned(ServerPlayer player, String moment) {
		AdvancementHolder advancement = advancement(player, moment);
		if (advancement == null) {
			return null;
		}
		CriterionProgress progress = player.getAdvancements().getOrStartProgress(advancement).getCriterion(VillageMoments.CRITERION);
		return progress == null ? null : progress.getObtained();
	}

	/** A survival player standing at {@code rel} (relative to the test), gone after the test. */
	private static ServerPlayer playerAt(GameTestHelper helper, Vec3 absolute) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(absolute);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	private static ServerPlayer near(GameTestHelper helper, BlockPos absHall) {
		return playerAt(helper, absHall.offset(2, 0, 2).getCenter());
	}

	/** Well out of the village's reach (twice its radius away, on the same level). */
	private static ServerPlayer far(GameTestHelper helper, BlockPos absHall) {
		return playerAt(helper, absHall.offset(VillageHalls.RADIUS * 2, 0, 0).getCenter());
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	private static Villager grown(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setAge(0);
		return villager;
	}

	private static Research.State levels(int n) {
		return new Research.State(Map.of("qa_topic", n), Optional.empty(), 0, false);
	}

	/** The hall's own round, now (as at dawn): what the update's catch-up runs in. */
	private static void dawnRound(ServerLevel level, VillageHallBlockEntity entity) {
		VillageMoments.forgetCatchUp();
		entity.setNeeds(null); // the next tick is a round
		VillageHallBlockEntity.serverTick(level, entity.getBlockPos(), level.getBlockState(entity.getBlockPos()), entity);
	}

	// --- the eight advancements and their titles ------------------------------------------------------------------

	/**
	 * Every one of the eight moments is a loaded advancement whose title is its lang title, in American English, as the
	 * questbook and the toast show it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEightMomentAdvancementsExistWithTheirTitles(GameTestHelper helper) {
		Map<String, String> titles = new LinkedHashMap<>();
		titles.put(VillageMoments.FIRST_BABY, "Born here");
		titles.put(VillageMoments.RESEARCH_3, "Three levels of research");
		titles.put(VillageMoments.RESEARCH_7, "Seven levels of research");
		titles.put(VillageMoments.EDICT, "An edict proclaimed");
		titles.put(VillageMoments.BURGHERS, "A Burgher household");
		titles.put(VillageMoments.NOBLES, "A Noble household");
		titles.put(VillageMoments.REALM, "A realm founded");
		titles.put(VillageMoments.TREASURY, "The takings collected");
		helper.assertTrue(titles.keySet().containsAll(EIGHT) && titles.size() == EIGHT.size(), "the eight moments: " + EIGHT);
		var advancements = helper.getLevel().getServer().getAdvancements();
		for (var entry : titles.entrySet()) {
			String key = "advancements.aliveworkplace.village." + entry.getKey();
			helper.assertTrue(Component.translatable(key).getString().equals(entry.getValue()),
				key + " reads \"" + Component.translatable(key).getString() + "\"");
			AdvancementHolder holder = advancements.get(AliveWorkplace.id(VillageMoments.PREFIX + entry.getKey()));
			helper.assertTrue(holder != null, "no advancement village/" + entry.getKey());
			holder.value().display().ifPresent(display -> helper.assertTrue(display.getTitle().getString().equals(entry.getValue()),
				entry.getKey() + "'s title reads \"" + display.getTitle().getString() + "\""));
			helper.assertTrue(holder.value().criteria().containsKey(VillageMoments.CRITERION), entry.getKey() + " has no \"done\" criterion");
		}
		helper.succeed();
	}

	// --- first_baby ---------------------------------------------------------------------------------------------------

	/**
	 * A couple's baby (Families.born) in a village with a hall: a player at the village gets "Born here", a player far
	 * away doesn't; a second baby doesn't grant it again (the date it was earned stays the same).
	 */
	//$ gametest_batch AREA '"qaMomentCoupleBaby"'
	@GameTest(template = AREA, batch = "qaMomentCoupleBaby")
	public void qaACouplesBabyGrantsBornHereOnceAndOnlyAtTheVillage(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer here = near(helper, hall);
		ServerPlayer away = far(helper, hall);
		Villager mother = grown(helper, new BlockPos(5, 2, 5));
		Villager father = grown(helper, new BlockPos(6, 2, 5));
		helper.assertTrue(VillageHalls.nearest(helper.getLevel(), mother.blockPosition()).isPresent(), "the mother isn't in the village");
		helper.assertFalse(has(here, VillageMoments.FIRST_BABY), "Born here before any baby");
		Villager baby = EntityType.VILLAGER.create(helper.getLevel());
		baby.setAge(-24000);
		baby.moveTo(mother.position());
		Families.born(baby, mother, father);
		helper.assertTrue(has(here, VillageMoments.FIRST_BABY), "a couple's baby didn't grant Born here to the player at the village");
		helper.assertFalse(has(away, VillageMoments.FIRST_BABY), "Born here granted to a player " + VillageHalls.RADIUS * 2 + " blocks away");
		Instant first = earned(here, VillageMoments.FIRST_BABY);
		Villager second = EntityType.VILLAGER.create(helper.getLevel());
		second.setAge(-24000);
		second.moveTo(mother.position());
		Families.born(second, mother, father);
		helper.assertTrue(first != null && first.equals(earned(here, VillageMoments.FIRST_BABY)), "a second baby granted Born here again");
		helper.assertFalse(has(away, VillageMoments.FIRST_BABY), "a second baby reached the far player");
		helper.succeed();
	}

	/** A baby born far from any hall is no village's moment: the player beside its parents gets nothing. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaMomentNoHallBaby"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaMomentNoHallBaby")
	public void qaABabyWithNoHallGrantsNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 sky = helper.absoluteVec(new Vec3(5, 200, 5));
		Villager mother = EntityType.VILLAGER.create(level);
		Villager father = EntityType.VILLAGER.create(level);
		mother.moveTo(sky);
		father.moveTo(sky.add(1, 0, 0));
		helper.assertTrue(VillageHalls.nearest(level, mother.blockPosition()).isEmpty(), "a hall in reach");
		ServerPlayer beside = playerAt(helper, sky.add(0, 0, 1));
		Villager baby = EntityType.VILLAGER.create(level);
		baby.setAge(-24000);
		baby.moveTo(sky);
		Families.born(baby, mother, father);
		helper.assertFalse(has(beside, VillageMoments.FIRST_BABY), "Born here with no hall");
		helper.succeed();
	}

	// --- research_3 and research_7 ----------------------------------------------------------------------------------

	/**
	 * Research jumping from none to 8 levels at once crosses both 3 and 7: both moments, for the player at the village,
	 * none for the far one; falling back and rising again grants neither again.
	 */
	//$ gametest_batch AREA '"qaMomentResearchJump"'
	@GameTest(template = AREA, batch = "qaMomentResearchJump")
	public void qaResearchJumpingPastThreeAndSevenGrantsBothOnce(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer here = near(helper, hall);
		ServerPlayer away = far(helper, hall);
		entity.setResearch(levels(0));
		helper.assertFalse(has(here, VillageMoments.RESEARCH_3) || has(here, VillageMoments.RESEARCH_7), "research moments with no research");
		entity.setResearch(levels(8));
		helper.assertTrue(has(here, VillageMoments.RESEARCH_3), "a jump to 8 levels skipped Three levels of research");
		helper.assertTrue(has(here, VillageMoments.RESEARCH_7), "a jump to 8 levels skipped Seven levels of research");
		helper.assertFalse(has(away, VillageMoments.RESEARCH_3) || has(away, VillageMoments.RESEARCH_7), "research moments reached a far player");
		Instant three = earned(here, VillageMoments.RESEARCH_3);
		Instant seven = earned(here, VillageMoments.RESEARCH_7);
		entity.setResearch(levels(1));
		entity.setResearch(levels(9));
		helper.assertTrue(three.equals(earned(here, VillageMoments.RESEARCH_3)) && seven.equals(earned(here, VillageMoments.RESEARCH_7)),
			"research crossing 3 and 7 again granted them again");
		helper.succeed();
	}

	// --- edict --------------------------------------------------------------------------------------------------------

	/**
	 * A village edict proclaimed by the owner: the owner and a player at the village get "An edict proclaimed", the far
	 * player doesn't; the same edict proclaimed again is refused and a player who arrived since gets nothing from it.
	 */
	//$ gametest_batch AREA '"qaMomentEdict"'
	@GameTest(template = AREA, batch = "qaMomentEdict")
	public void qaAnEdictGrantsTheVillageAndARefusedOneGrantsNothing(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		boolean enabled = Edicts.ENABLED;
		Edicts.setEnabled(true);
		Leftovers.after(helper, () -> Edicts.setEnabled(enabled));
		ServerPlayer owner = near(helper, hall);
		ServerPlayer bystander = near(helper, hall);
		ServerPlayer away = far(helper, hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setEdicts(List.of());
		Edicts.Edict shifts = Edicts.get(AliveWorkplace.id("long_shifts")).orElseThrow();
		Edicts.Result done = Edicts.proclaim(level, hall, owner, shifts);
		helper.assertTrue(done.done(), "not proclaimed: " + done.message().getString());
		helper.assertTrue(has(owner, VillageMoments.EDICT), "the proclaiming owner didn't get An edict proclaimed");
		helper.assertTrue(has(bystander, VillageMoments.EDICT), "a player at the village didn't get An edict proclaimed");
		helper.assertFalse(has(away, VillageMoments.EDICT), "An edict proclaimed reached a far player");
		ServerPlayer late = near(helper, hall);
		Edicts.Result again = Edicts.proclaim(level, hall, owner, shifts);
		helper.assertFalse(again.done(), "the same edict proclaimed twice");
		helper.assertFalse(has(late, VillageMoments.EDICT), "a refused proclamation granted An edict proclaimed");
		helper.succeed();
	}

	/**
	 * A realm edict proclaimed at the capital counts for the capital's village: a player there (not the ruler) gets "An
	 * edict proclaimed"; the same realm edict again is refused and grants a newcomer nothing; and a capital with only a
	 * realm edict, at its next dawn round, grants both the realm and the edict moment to that newcomer.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaMomentRealmEdict"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaMomentRealmEdict")
	public void qaARealmEdictCountsForTheCapitalsVillage(GameTestHelper helper) {
		RealmGameTests.Land land = RealmEdictGameTests.realm(helper, 0, VillageRanks.Rank.TOWN);
		ServerLevel level = land.level();
		BlockPos capital = land.hall(0);
		boolean enabled = Edicts.ENABLED;
		Edicts.setEnabled(true);
		Leftovers.after(helper, () -> Edicts.setEnabled(enabled));
		helper.assertTrue(land.entity(0).edicts().isEmpty(), "the capital has village edicts: " + land.entity(0).edicts());
		ServerPlayer bystander = near(helper, capital);
		helper.assertFalse(has(bystander, VillageMoments.EDICT), "An edict proclaimed before any edict");
		Edicts.Result done = RealmEdicts.proclaim(level, capital, land.owner(), RealmEdictGameTests.edict("royal_highway"));
		helper.assertTrue(done.done(), "realm edict not proclaimed: " + done.message().getString());
		helper.assertTrue(has(land.owner(), VillageMoments.EDICT), "the ruler didn't get An edict proclaimed for a realm edict");
		helper.assertTrue(has(bystander, VillageMoments.EDICT), "a realm edict didn't count for the capital's village");
		ServerPlayer late = near(helper, capital);
		helper.assertFalse(RealmEdicts.proclaim(level, capital, land.owner(), RealmEdictGameTests.edict("royal_highway")).done(),
			"the same realm edict proclaimed twice");
		helper.assertFalse(has(late, VillageMoments.EDICT), "a refused realm edict granted An edict proclaimed");
		helper.assertFalse(has(late, VillageMoments.REALM), "A realm founded for a newcomer before any round");
		dawnRound(level, land.entity(0));
		helper.assertTrue(has(late, VillageMoments.REALM), "the capital's dawn round didn't catch up A realm founded");
		helper.assertTrue(has(late, VillageMoments.EDICT), "the capital's dawn round didn't count its realm edict");
		helper.succeed();
	}

	// --- realm --------------------------------------------------------------------------------------------------------

	/**
	 * A refused founding grants nothing: a Village (below a Town) and a stranger's try at someone else's Town. The owner's
	 * founding of the Town grants "A realm founded" to the owner and to a player at that village, not to the far stranger.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaMomentRealmRefused"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaMomentRealmRefused")
	public void qaARefusedFoundingGrantsNothingAndAFoundingGrantsTheVillage(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.TOWN);
		ServerLevel level = land.level();
		ServerPlayer owner = land.owner();
		ServerPlayer atTown = near(helper, land.hall(1));
		ServerPlayer stranger = far(helper, land.hall(1));
		Realms.Result small = Realms.found(level, land.hall(0), owner);
		helper.assertTrue(small.refusal() == Realms.Refusal.RANK, "a Village founded a realm: " + small.refusal());
		helper.assertFalse(has(owner, VillageMoments.REALM) || has(atTown, VillageMoments.REALM), "a refused founding (rank) granted A realm founded");
		Realms.Result notHis = Realms.found(level, land.hall(1), stranger);
		helper.assertTrue(notHis.refusal() != null, "a stranger founded a realm with someone else's Town");
		helper.assertFalse(has(stranger, VillageMoments.REALM) || has(owner, VillageMoments.REALM) || has(atTown, VillageMoments.REALM),
			"a stranger's refused founding granted A realm founded");
		Realms.Result founded = Realms.found(level, land.hall(1), owner);
		helper.assertTrue(founded.ok(), "the owner's Town didn't found a realm: " + founded.refusal());
		helper.assertTrue(has(owner, VillageMoments.REALM), "the founder didn't get A realm founded");
		helper.assertTrue(has(atTown, VillageMoments.REALM), "a player at the capital didn't get A realm founded");
		helper.assertFalse(has(stranger, VillageMoments.REALM), "A realm founded reached the far stranger");
		helper.succeed();
	}

	// --- treasury -----------------------------------------------------------------------------------------------------

	/**
	 * The takings: a stranger's refused collection, 99 cents (less than an emerald) and nothing at all grant nothing;
	 * exactly one emerald collected grants "The takings collected" to the collecting owner even far from the village,
	 * and to a player at the village; collecting again doesn't grant it again.
	 */
	//$ gametest_batch AREA '"qaMomentTreasury"'
	@GameTest(template = AREA, batch = "qaMomentTreasury")
	public void qaTheTakingsNeedAnEmeraldAndGrantTheCollectorAnywhere(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		ServerPlayer owner = far(helper, hall);
		ServerPlayer bystander = near(helper, hall);
		ServerPlayer stranger = near(helper, hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		helper.assertFalse(Treasury.mayCollect(level, entity, stranger) && io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED,
			"a stranger may collect another's takings");
		entity.setTreasury(500);
		Treasury.collect(level, hall, stranger);
		if (io.github.jcondedata.aliveworkplace.trade.Economy.ENABLED) {
			helper.assertTrue(entity.treasury() == 500, "a stranger took the takings: " + entity.treasury());
			helper.assertFalse(has(stranger, VillageMoments.TREASURY) || has(owner, VillageMoments.TREASURY) || has(bystander, VillageMoments.TREASURY),
				"a refused collection granted The takings collected");
		}
		entity.setTreasury(0);
		Treasury.collect(level, hall, owner);
		entity.setTreasury(99);
		Treasury.collect(level, hall, owner);
		helper.assertTrue(entity.treasury() == 99, "99 cents collected as an emerald: " + entity.treasury());
		helper.assertFalse(has(owner, VillageMoments.TREASURY) || has(bystander, VillageMoments.TREASURY), "collecting no emerald granted The takings collected");
		entity.setTreasury(100);
		Treasury.collect(level, hall, owner);
		helper.assertTrue(entity.treasury() == 0, "one emerald not collected: " + entity.treasury());
		helper.assertTrue(has(owner, VillageMoments.TREASURY), "the collecting player (away from the village) didn't get The takings collected");
		helper.assertTrue(has(bystander, VillageMoments.TREASURY), "a player at the village didn't get The takings collected");
		Instant first = earned(owner, VillageMoments.TREASURY);
		entity.setTreasury(300);
		Treasury.collect(level, hall, owner);
		helper.assertTrue(first.equals(earned(owner, VillageMoments.TREASURY)), "collecting again granted The takings collected again");
		helper.succeed();
	}

	// --- catching up at dawn ------------------------------------------------------------------------------------------

	/**
	 * A village saved before the update with a baby on record and a Noble household (and no Burgher one any more) gets
	 * "Born here", "A Noble household" and "A Burgher household" at its next dawn round, through the hall's own round,
	 * for the player at the village and not the far one; nothing it didn't pass (research, an edict, a realm, the takings).
	 */
	//$ gametest_batch AREA '"qaMomentCatchUpNobles"'
	@GameTest(template = AREA, batch = "qaMomentCatchUpNobles")
	public void qaAVillageWithANobleAndABabyOnRecordCatchesUpAtDawn(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		// The village's save from before the update: one baby on record.
		CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
		saved.putInt("births", 1);
		entity.loadWithComponents(saved, level.registryAccess());
		helper.assertTrue(entity.births() == 1, "the saved birth didn't load: " + entity.births());
		Villager noble = grown(helper, new BlockPos(5, 2, 2));
		ModAttachments.SOCIAL_CLASS.set(noble, VillageMoments.NOBLE_CLASS);
		ServerPlayer here = near(helper, hall);
		ServerPlayer away = far(helper, hall);
		helper.assertFalse(has(here, VillageMoments.FIRST_BABY) || has(here, VillageMoments.NOBLES), "granted before the round");
		dawnRound(level, entity);
		helper.assertTrue(has(here, VillageMoments.FIRST_BABY), "Born here not caught up for a baby on record");
		helper.assertTrue(has(here, VillageMoments.NOBLES), "A Noble household not caught up");
		helper.assertTrue(has(here, VillageMoments.BURGHERS), "a village with a Noble household left A Burgher household unearnable at dawn");
		for (String not : List.of(VillageMoments.RESEARCH_3, VillageMoments.RESEARCH_7, VillageMoments.EDICT, VillageMoments.REALM, VillageMoments.TREASURY)) {
			helper.assertFalse(has(here, not), not + " caught up for a village that never passed it");
		}
		for (String moment : EIGHT) {
			helper.assertFalse(has(away, moment), moment + " caught up for a far player");
		}
		helper.succeed();
	}
}
