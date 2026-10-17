package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageMoments;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Households;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * 36.2: the eight village moments the questbook's village quests now complete on (no checkmark left): a baby born, 3
 * and 7 levels of research, an edict, a Burgher and a Noble household, a realm founded and the takings collected. Each
 * grants its advancement to a player at the village, a second time grants nothing new, and a village that passed a
 * moment before it was one gets it at its next dawn round.
 */
public class VillageMomentGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 2);

	private static boolean has(ServerPlayer player, String moment) {
		var advancement = player.getServer().getAdvancements().get(AliveWorkplace.id(VillageMoments.PREFIX + moment));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
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
		return new Research.State(Map.of("test_topic", n), Optional.empty(), 0, false);
	}

	/** 3 and 7 levels done: each moment as the research crosses it, not before, and only once. */
	//$ gametest_batch AREA '"momentResearch"'
	@GameTest(template = AREA, batch = "momentResearch")
	public void researchReachingThreeAndSevenLevelsAreMoments(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		entity.setResearch(levels(2));
		helper.assertFalse(has(player, VillageMoments.RESEARCH_3), "research_3 at 2 levels");
		entity.setResearch(levels(3));
		helper.assertTrue(has(player, VillageMoments.RESEARCH_3), "no research_3 at 3 levels");
		helper.assertFalse(has(player, VillageMoments.RESEARCH_7), "research_7 at 3 levels");
		entity.setResearch(levels(7));
		helper.assertTrue(has(player, VillageMoments.RESEARCH_7), "no research_7 at 7 levels");
		helper.assertTrue(VillageMoments.award(level, entity.getBlockPos(), VillageMoments.RESEARCH_7, null) == 0, "research_7 granted twice");
		helper.succeed();
	}

	/** An edict proclaimed by the owner is a moment, once; a refused one (no free slot) grants nothing. */
	//$ gametest_batch AREA '"momentEdict"'
	@GameTest(template = AREA, batch = "momentEdict")
	public void aProclaimedEdictIsAMoment(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = player(helper);
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.HAMLET);
		entity.setEdicts(List.of());
		Edicts.Edict shifts = Edicts.get(AliveWorkplace.id("long_shifts")).orElseThrow();
		// A Hamlet's one slot taken: refused, nothing for anyone.
		entity.setEdicts(List.of(new Edicts.InForce("aliveworkplace_test:taken", Chronicle.day(level))));
		Edicts.Result refused = Edicts.proclaim(level, hall, player, shifts);
		helper.assertFalse(refused.done(), "proclaimed with no free slot");
		helper.assertFalse(has(player, VillageMoments.EDICT), "edict granted for a refused proclamation");
		entity.setEdicts(List.of());
		Edicts.Result done = Edicts.proclaim(level, hall, player, shifts);
		helper.assertTrue(done.done(), "not proclaimed: " + done.message().getString());
		helper.assertTrue(has(player, VillageMoments.EDICT), "no edict moment");
		helper.assertTrue(VillageMoments.award(level, hall, VillageMoments.EDICT, player) == 0, "edict granted twice");
		helper.succeed();
	}

	/** A household rising to Burgher, then to Noble: one moment each, and an Artisan's rise is neither. */
	//$ gametest_batch AREA '"momentClasses"'
	@GameTest(template = AREA, batch = "momentClasses")
	public void burgherAndNobleRisesAreMoments(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = player(helper);
		Villager lead = grown(helper, new BlockPos(5, 2, 2));
		Households.Household household = new Households.Household(List.of(lead));
		SocialClasses.SocialClass artisan = SocialClasses.get(AliveWorkplace.id("artisan"));
		SocialClasses.SocialClass burgher = SocialClasses.get(VillageMoments.BURGHER_CLASS);
		SocialClasses.SocialClass noble = SocialClasses.get(VillageMoments.NOBLE_CLASS);
		helper.assertTrue(artisan != null && burgher != null && noble != null, "the ladder isn't loaded: " + SocialClasses.ladder());
		SocialClasses.turned(level, hall, household, artisan, true);
		helper.assertFalse(has(player, VillageMoments.BURGHERS) || has(player, VillageMoments.NOBLES), "an Artisan's rise counted");
		SocialClasses.turned(level, hall, household, burgher, false);
		helper.assertFalse(has(player, VillageMoments.BURGHERS), "a fall to Burgher counted as a rise");
		SocialClasses.turned(level, hall, household, burgher, true);
		helper.assertTrue(has(player, VillageMoments.BURGHERS), "no burghers moment");
		helper.assertFalse(has(player, VillageMoments.NOBLES), "nobles before a Noble");
		SocialClasses.turned(level, hall, household, noble, true);
		helper.assertTrue(has(player, VillageMoments.NOBLES), "no nobles moment");
		helper.assertTrue(VillageMoments.award(level, hall, VillageMoments.BURGHERS, null) == 0, "burghers granted twice");
		helper.succeed();
	}

	/** A Town founding a realm: the founder gets the moment; a second founding is refused and grants nothing new. */
	//$ gametest_ticks_batch AREA '100' '"momentRealm"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "momentRealm")
	public void foundingARealmIsAMoment(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN);
		ServerPlayer owner = land.owner();
		helper.assertFalse(has(owner, VillageMoments.REALM), "realm before founding");
		Realms.Result result = Realms.found(land.level(), land.hall(0), owner);
		helper.assertTrue(result.refusal() == null, "not founded: " + result.refusal());
		helper.assertTrue(has(owner, VillageMoments.REALM), "no realm moment for the founder");
		helper.assertTrue(Realms.found(land.level(), land.hall(0), owner).refusal() == Realms.Refusal.IN_REALM, "founded twice");
		helper.assertTrue(VillageMoments.award(land.level(), land.hall(0), VillageMoments.REALM, owner) == 0, "realm granted twice");
		helper.succeed();
	}

	/** Collecting at least an emerald of takings is a moment; an empty treasury isn't. */
	//$ gametest_batch AREA '"momentTreasury"'
	@GameTest(template = AREA, batch = "momentTreasury")
	public void collectingTheTakingsIsAMoment(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = player(helper);
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setTreasury(50); // half an emerald: nothing to take
		Treasury.collect(level, hall, player);
		helper.assertFalse(has(player, VillageMoments.TREASURY), "treasury granted for an empty treasury");
		entity.setTreasury(250);
		Treasury.collect(level, hall, player);
		helper.assertTrue(entity.treasury() == 50, "not collected: " + entity.treasury());
		helper.assertTrue(has(player, VillageMoments.TREASURY), "no treasury moment");
		entity.setTreasury(300);
		Treasury.collect(level, hall, player);
		helper.assertTrue(VillageMoments.award(level, hall, VillageMoments.TREASURY, player) == 0, "treasury granted twice");
		helper.succeed();
	}

	/** A couple's baby, had the vanilla way, in a village with a hall: the moment for a player there. */
	//$ gametest_batch AREA '"momentBaby"'
	@GameTest(template = AREA, batch = "momentBaby")
	public void aBabyBornInTheVillageIsAMoment(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Villager mother = grown(helper, new BlockPos(5, 2, 5));
		Villager father = grown(helper, new BlockPos(6, 2, 5));
		helper.assertFalse(has(player, VillageMoments.FIRST_BABY), "first_baby before a birth");
		Villager baby = mother.getBreedOffspring(level, father);
		helper.assertTrue(baby != null, "no baby");
		helper.assertTrue(has(player, VillageMoments.FIRST_BABY), "no first_baby moment");
		helper.assertTrue(VillageMoments.award(level, entity.getBlockPos(), VillageMoments.FIRST_BABY, null) == 0, "first_baby granted twice");
		helper.succeed();
	}

	/**
	 * A village that passed its moments before they were moments (7 levels of research, a Burgher household, an edict in
	 * force, a baby counted) gets them at its next dawn round, through the hall's own round; the day's later rounds don't
	 * look again, and the next day's look grants nothing twice.
	 */
	//$ gametest_batch AREA '"momentCatchUp"'
	@GameTest(template = AREA, batch = "momentCatchUp")
	public void aVillageThatAlreadyQualifiesGetsItsMomentsAtTheNextDawnRound(GameTestHelper helper) {
		VillageHallBlockEntity entity = hall(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = entity.getBlockPos();
		// Before the update: nobody's there, so the village's past has no one to grant anything to.
		entity.setResearch(levels(8));
		entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), Chronicle.day(level))));
		Villager burgher = grown(helper, new BlockPos(5, 2, 2));
		ModAttachments.SOCIAL_CLASS.set(burgher, VillageMoments.BURGHER_CLASS);
		ServerPlayer player = player(helper);
		helper.assertFalse(has(player, VillageMoments.RESEARCH_7) || has(player, VillageMoments.BURGHERS), "granted before the round");
		VillageMoments.forgetCatchUp();
		entity.setNeeds(null); // the next tick is a round
		VillageHallBlockEntity.serverTick(level, hall, level.getBlockState(hall), entity);
		helper.assertTrue(has(player, VillageMoments.RESEARCH_3) && has(player, VillageMoments.RESEARCH_7), "research moments not caught up");
		helper.assertTrue(has(player, VillageMoments.BURGHERS), "burghers not caught up");
		helper.assertFalse(has(player, VillageMoments.NOBLES), "nobles with no Noble household");
		helper.assertTrue(has(player, VillageMoments.EDICT), "edict not caught up");
		helper.assertFalse(has(player, VillageMoments.REALM), "realm with no realm");
		helper.assertTrue(VillageMoments.catchUp(level, hall, entity, List.of(burgher)).isEmpty(), "looked twice in a day");
		VillageMoments.forgetCatchUp(); // the next day
		List<String> again = VillageMoments.catchUp(level, hall, entity, List.of(burgher));
		helper.assertTrue(again.contains(VillageMoments.RESEARCH_7) && again.contains(VillageMoments.BURGHERS), "the next day's look: " + again);
		helper.assertTrue(VillageMoments.award(level, hall, VillageMoments.RESEARCH_7, null) == 0, "research_7 granted twice");
		helper.succeed();
	}
}
