package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.people.Traits.Trait;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA bug tests for ROADMAP 31.22 (the eight reforms as stories): spec mismatches found by QA, failing until fixed.
 * Not on main until the builder commits each with its fix.
 */
public class QaReformStoryBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);

	/** Spec: The Festival Fund is "asked by the Bard (else a Cheerful villager)". With no Bard, the Cheerful villager asks. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryBugCheerful"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryBugCheerful")
	public void festivalFundWithNoBardIsAskedByACheerfulVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		boolean traits = Traits.ENABLED;
		Traits.ENABLED = true;
		Leftovers.after(helper, () -> Traits.ENABLED = traits);
		Villager cheerful = add(helper, new BlockPos(8, 2, 6), PeopleGameTests.withTraits(Trait.CHEERFUL));
		List<Villager> others = new ArrayList<>();
		RandomSource random = RandomSource.create(31022);
		while (others.size() < 6) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (!Traits.of(id).contains(Trait.CHEERFUL)) {
				others.add(add(helper, new BlockPos(4 + others.size(), 2, 10), id));
			}
		}
		helper.runAfterDelay(2, () -> askedEveryTime(helper, "festival_season", cheerful, others, "the Cheerful villager"));
	}

	/** Spec: The Midwives are "asked by the Nurse (else a mother)". With no Nurse, a mother asks, not a childless villager. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryBugMother"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryBugMother")
	public void midwivesWithNoNurseAreAskedByAMother(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager mother = add(helper, new BlockPos(8, 2, 6), UUID.nameUUIDFromBytes("qa-mother".getBytes()));
		Villager baby = EntityType.VILLAGER.create(helper.getLevel());
		baby.setBaby(true);
		baby.setNoAi(true);
		baby.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 6.5)));
		ModAttachments.PARENTS.set(baby, new Families.Parents(mother.getDisplayName().copy(), Component.literal("Tom"), "", "", false, false, false,
			mother.getUUID(), null));
		helper.getLevel().addFreshEntity(baby);
		List<Villager> others = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			others.add(add(helper, new BlockPos(4 + i, 2, 10), UUID.nameUUIDFromBytes(("qa-other-" + i).getBytes())));
		}
		helper.runAfterDelay(2, () -> askedEveryTime(helper, "large_families", mother, others, "the mother"));
	}

	/**
	 * Spec: The Lamplighters are "asked by a Porter (else anyone)". When a Farmer asks (no Porter), the ask the player
	 * reads must not send them to a porter who isn't there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryBugAskText"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryBugAskText")
	public void lamplightersAskedByAFarmerDontTellYouToTalkToThePorter(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager farmer = QaReformStoryGameTests.villager(helper, new BlockPos(8, 2, 6), VillagerProfession.FARMER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("curfew").toString(), Chronicle.day(level))));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(QaReformStoryGameTests.asks(farmer), "the farmer didn't ask");
			List<Quest> quests = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			String name = quests.isEmpty() ? "" : quests.get(0).name.map(Component::getString).orElse("");
			helper.assertTrue(!name.toLowerCase(java.util.Locale.ROOT).contains("porter"),
				"the Farmer asks, but the quest reads \"" + name + "\" (there is no Porter)");
			helper.succeed();
		});
	}

	/**
	 * Spec: drill day ends with "a chronicle line". With the guards alone (no dummies) one villager may spar; the line
	 * the code writes (ReformStories.tick, with the number who sparred) then reads "1 villagers".
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void drillDayLineWithOneVillagerIsGrammatical(GameTestHelper helper) {
		String line = Component.translatable("chronicle.aliveworkplace.reform_story.drilled", "Ashford", 1).getString();
		helper.assertTrue(!line.contains("1 villagers"), "the drill's chronicle line with one villager: \"" + line + "\"");
		helper.succeed();
	}

	private static void askedEveryTime(GameTestHelper helper, String edictPath, Villager want, List<Villager> others, String who) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
		entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id(edictPath).toString(), Chronicle.day(level))));
		for (int i = 0; i < 5; i++) {
			Arcs.stopAll(level, hall);
			QaReformStoryGameTests.untag(want);
			others.forEach(QaReformStoryGameTests::untag);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1, "round " + i + ": no story started");
			Villager asker = others.stream().filter(QaReformStoryGameTests::asks).findFirst().orElse(null);
			helper.assertTrue(QaReformStoryGameTests.asks(want), "round " + i + ": " + who + " should ask, but "
				+ (asker == null ? "nobody" : asker.getDisplayName().getString() + " (" + asker.getUUID() + ")") + " did");
		}
		helper.succeed();
	}

	private static Villager add(GameTestHelper helper, BlockPos at, UUID id) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setUUID(id);
		v.setNoAi(true);
		v.moveTo(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		helper.getLevel().addFreshEntity(v);
		return v;
	}
}
