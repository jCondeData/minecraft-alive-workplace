package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.people.Traits.Trait;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
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
import net.minecraft.world.phys.Vec3;

/**
 * QA for B632 and B633 (31.22 reform stories) from the other side of each fix. The spec's askers are "the Bard (else a
 * Cheerful villager)" and "the Nurse (else a mother)": with the first there, it asks even when the fallback is there
 * too; with neither, the story still starts and some grown villager asks.
 */
public class QaReformAskers1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);

	private static List<Villager> plain(GameTestHelper helper, long seed, int z) {
		List<Villager> out = new ArrayList<>();
		RandomSource random = RandomSource.create(seed);
		while (out.size() < 5) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (!Traits.of(id).contains(Trait.CHEERFUL)) {
				out.add(QaReformStoryBugTests.add(helper, new BlockPos(3 + out.size(), 2, z), id));
			}
		}
		return out;
	}

	private static void traitsOn(GameTestHelper helper) {
		boolean traits = Traits.ENABLED;
		Traits.ENABLED = true;
		Leftovers.after(helper, () -> Traits.ENABLED = traits);
	}

	/** With neither asker there, five rounds: the story starts each time and one of the villagers asks. */
	private static void someoneAsks(GameTestHelper helper, String edictPath, List<Villager> all) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
		entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id(edictPath).toString(), Chronicle.day(level))));
		for (int i = 0; i < 5; i++) {
			Arcs.stopAll(level, hall);
			all.forEach(QaReformStoryGameTests::untag);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1, "round " + i + ": with neither asker about, no story started");
			helper.assertTrue(all.stream().filter(QaReformStoryGameTests::asks).count() == 1,
				"round " + i + ": " + all.stream().filter(QaReformStoryGameTests::asks).count() + " villagers ask, not one");
		}
		helper.succeed();
	}

	/** A Bard and a Cheerful villager both about: the Bard asks, every time. */
	//$ gametest_ticks_batch AREA '100' '"qaReformAskBardFirst1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformAskBardFirst1102")
	public void qaTheBardAsksBeforeACheerfulVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		traitsOn(helper);
		Villager bard = QaReformStoryGameTests.villager(helper, new BlockPos(10, 2, 6), ModVillagers.BARD);
		List<Villager> others = new ArrayList<>(plain(helper, 31102, 10));
		others.add(QaReformStoryBugTests.add(helper, new BlockPos(8, 2, 6), PeopleGameTests.withTraits(Trait.CHEERFUL)));
		helper.runAfterDelay(2, () -> QaReformStoryBugTests.askedEveryTime(helper, "festival_season", bard, others, "the Bard"));
	}

	/** No Bard and nobody Cheerful: the Festival Fund still starts, asked by one of them. */
	//$ gametest_ticks_batch AREA '100' '"qaReformAskNoCheer1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformAskNoCheer1102")
	public void qaWithNoBardAndNobodyCheerfulSomeoneStillAsks(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		traitsOn(helper);
		List<Villager> all = plain(helper, 41102, 10);
		helper.runAfterDelay(2, () -> someoneAsks(helper, "festival_season", all));
	}

	/** A Nurse and a mother both about: the Nurse asks for the Midwives, every time. */
	//$ gametest_ticks_batch AREA '100' '"qaReformAskNurseFirst1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformAskNurseFirst1102")
	public void qaTheNurseAsksBeforeAMother(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager nurse = QaReformStoryGameTests.villager(helper, new BlockPos(10, 2, 6), ModVillagers.NURSE);
		Villager mother = QaReformStoryBugTests.add(helper, new BlockPos(8, 2, 6), UUID.nameUUIDFromBytes("qa-mother-1102".getBytes()));
		Villager baby = EntityType.VILLAGER.create(helper.getLevel());
		baby.setBaby(true);
		baby.setNoAi(true);
		baby.moveTo(helper.absoluteVec(new Vec3(9.5, 2, 6.5)));
		ModAttachments.PARENTS.set(baby, new Families.Parents(mother.getDisplayName().copy(), Component.literal("Tom"), "", "", false, false, false,
			mother.getUUID(), null));
		helper.getLevel().addFreshEntity(baby);
		Leftovers.after(helper, baby::discard);
		List<Villager> others = new ArrayList<>(plain(helper, 51102, 10));
		others.add(mother);
		helper.runAfterDelay(2, () -> QaReformStoryBugTests.askedEveryTime(helper, "large_families", nurse, others, "the Nurse"));
	}

	/** No Nurse and no mother: the Midwives still start, asked by one grown villager. */
	//$ gametest_ticks_batch AREA '100' '"qaReformAskNoMother1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformAskNoMother1102")
	public void qaWithNoNurseAndNoMotherSomeoneStillAsks(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		List<Villager> all = plain(helper, 61102, 10);
		helper.runAfterDelay(2, () -> someoneAsks(helper, "large_families", all));
	}
}
