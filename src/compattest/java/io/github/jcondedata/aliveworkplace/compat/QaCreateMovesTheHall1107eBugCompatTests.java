package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Stations;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMovesTheHall1107eCompatTests.*;

/**
 * QA (lane E, Create and saves, 2026-11-07), the failing half of {@link QaCreateMovesTheHall1107eCompatTests}: what a
 * Village Hall pushed two steps by Create's Mechanical Piston loses of its village. A player expects to keep it all; today
 * the hall block's removal ({@code VillageHallBlock#onRemove}) treats the push as the hall being broken, and the village's
 * made-up name comes from the hall's spot. Each case first proves the piston turned and the hall stands two steps west.
 * The setup (piston, hall, checks) is the passing half's. Every assertion is the player's expectation, so these fail until the mod keeps the village through a move.
 */
public class QaCreateMovesTheHall1107eBugCompatTests implements FabricGameTest {

	private static final String AREA = CompatGameTests.AREA;

	/** A village the player never named is still called what he's always seen it called, after the push. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_made_up_name"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_made_up_name")
	public void anUnnamedVillageKeepsItsNameWhenAMechanicalPistonPushesItsHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), null);
		String was = VillageHalls.name(helper.getLevel(), helper.absolutePos(TARGET)).getString();
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			assertPushed(helper);
			String now = VillageHalls.name(helper.getLevel(), helper.absolutePos(MOVED)).getString();
			helper.assertTrue(was.equals(now), "the village " + was + " is called " + now + " after a Mechanical Piston pushed its hall two blocks");
			helper.succeed();
		});
	}

	// ---- (2) the Steward ---------------------------------------------------------------------------------------------

	/** The hall's Steward still is its Steward, at the hall's new spot, once the piston has pushed it. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_steward"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_steward")
	public void aHallsStewardKeepsHisJobWhenAMechanicalPistonPushesTheHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), Component.literal("Thornholm"));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos at = helper.absolutePos(TARGET);
			Villager sam = helper.spawn(EntityType.VILLAGER, STEWARD_AT);
			sam.setCustomName(Component.literal("Sam"));
			Stations.assign(level, sam, at, ModVillagers.STEWARD);
			sam.setVillagerData(sam.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(2));
			helper.assertTrue(Stewards.stewardOf(level, at) == sam, "setup: Sam isn't the hall's Steward");
			piston(helper);
			helper.runAfterDelay(PUSH, () -> {
				assertPushed(helper);
				BlockPos now = helper.absolutePos(MOVED);
				Villager steward = Stewards.stewardOf(level, now);
				helper.assertTrue(sam.isAlive(), "the Steward Sam died during the push");
				helper.assertTrue(sam.getVillagerData().getProfession() == ModVillagers.STEWARD,
					"Sam lost his Steward's job when a Mechanical Piston pushed his hall two blocks: he's a "
						+ sam.getVillagerData().getProfession() + " now");
				helper.assertTrue(steward == sam, "the pushed hall has no Steward (Sam's work site is "
					+ sam.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					+ ", the hall is at " + now + ")");
				helper.succeed();
			});
		});
	}

	// ---- (3) the realm -----------------------------------------------------------------------------------------------

	/** Two villages' realm: {@code capitalMoved} decides whether the pushed hall is its capital or its other member. */
	static UUID realm(GameTestHelper helper, boolean capitalMoved) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(OTHER, ModBlocks.VILLAGE_HALL);
		GlobalPos moved = GlobalPos.of(level.dimension(), helper.absolutePos(TARGET));
		GlobalPos other = GlobalPos.of(level.dimension(), helper.absolutePos(OTHER));
		GlobalPos capital = capitalMoved ? moved : other;
		GlobalPos member = capitalMoved ? other : moved;
		UUID id = UUID.randomUUID();
		RealmData.get(level.getServer()).putRealm(new RealmData.Realm(id, Optional.empty(), capital,
			List.of(new RealmData.Member(capital, 0), new RealmData.Member(member, 0)), Optional.empty(), "barony", 0));
		return id;
	}

	/** The capital of a realm pushed two steps by a Mechanical Piston is still the capital, of the same realm, with both villages. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_capital"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_capital")
	public void aRealmsCapitalStaysTheCapitalWhenAMechanicalPistonPushesItsHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), Component.literal("Thornholm"));
		UUID id = realm(helper, true);
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			ServerLevel level = helper.getLevel();
			RealmData data = RealmData.get(level.getServer());
			RealmData.Realm realm = data.realm(id);
			RealmData.Realm ofMoved = Realms.realmOf(level, helper.absolutePos(MOVED));
			GlobalPos now = GlobalPos.of(level.dimension(), helper.absolutePos(MOVED));
			data.removeRealm(id); // tidy up before any assertion can stop the test
			assertPushed(helper);
			helper.assertTrue(realm != null, "the Barony of Thornholm ended when a Mechanical Piston pushed its capital's hall two blocks");
			helper.assertTrue(realm.capital().equals(now), "Thornholm is no longer its realm's capital after a Mechanical Piston pushed its hall"
				+ " two blocks: the capital is " + realm.capital().pos() + ", Thornholm's hall is at " + now.pos());
			helper.assertTrue(ofMoved != null && ofMoved.id().equals(id), "Thornholm's pushed hall is in no realm (or another one): " + ofMoved);
			helper.assertTrue(realm.members().size() == 2, "the realm has " + realm.members().size() + " villages after the push, not 2");
			helper.succeed();
		});
	}

	/** A member village pushed two steps is still in its realm, and the other village still its capital. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_member"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_member")
	public void aRealmsMemberStaysInTheRealmWhenAMechanicalPistonPushesItsHall(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), Component.literal("Thornholm"));
		UUID id = realm(helper, false);
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			ServerLevel level = helper.getLevel();
			RealmData data = RealmData.get(level.getServer());
			RealmData.Realm realm = data.realm(id);
			RealmData.Realm ofMoved = Realms.realmOf(level, helper.absolutePos(MOVED));
			GlobalPos other = GlobalPos.of(level.dimension(), helper.absolutePos(OTHER));
			data.removeRealm(id);
			assertPushed(helper);
			helper.assertTrue(realm != null, "the realm ended when a Mechanical Piston pushed a member village's hall two blocks");
			helper.assertTrue(realm.capital().equals(other), "the realm's capital changed when a member's hall was pushed: " + realm.capital().pos());
			helper.assertTrue(ofMoved != null && ofMoved.id().equals(id),
				"Thornholm left its realm when a Mechanical Piston pushed its hall two blocks (members now " + Realms.members(realm) + ")");
			helper.succeed();
		});
	}

	// ---- (4) open quests ---------------------------------------------------------------------------------------------

	/** An open quest on the hall's board is still there after the push, with its progress. */
	//$ gametest_ticks_batch AREA '400' '"qa_create_hall_quests"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_create_hall_quests")
	public void aHallsOpenQuestsSurviveAMechanicalPistonPushingIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		hall(helper, UUID.randomUUID(), Component.literal("Thornholm"));
		ServerLevel level = helper.getLevel();
		Quest quest = new Quest(UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_moved_hall_apples"), "hall",
			Optional.of(Component.literal("Apples for Thornholm")), "", level.getGameTime(), -1,
			List.of(new Objectives.Bring("minecraft:apple", 10, "hall", Optional.empty())), new int[] {4},
			List.of(new Rewards.Money_(3, 0, 0, false)));
		Stories.post(level, helper.absolutePos(TARGET), quest);
		piston(helper);
		helper.runAfterDelay(PUSH, () -> {
			List<Quest> open = Stories.open(level, helper.absolutePos(MOVED));
			List<Quest> left = Stories.open(level, helper.absolutePos(TARGET));
			Stories.Data.get(level).remove(helper.absolutePos(MOVED));
			Stories.Data.get(level).remove(helper.absolutePos(TARGET));
			assertPushed(helper);
			Quest kept = open.stream().filter(q -> q.id.equals(quest.id)).findFirst().orElse(null);
			helper.assertTrue(kept != null, "the open quest \"Apples for Thornholm\" (4 of 10 apples brought) is gone from the hall's board after a"
				+ " Mechanical Piston pushed the hall two blocks (" + open.size() + " quests at the new spot, " + left.size() + " left at the old one)");
			helper.assertTrue(kept.progress[0] == 4, "the pushed hall's quest forgot the apples brought: " + kept.progress[0] + " of 10, was 4");
			helper.succeed();
		});
	}
}
