package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

/**
 * One pace, one cap (ROADMAP 30.2) with real Pokémon partners: every bonus there is stacks to exactly twice the pace
 * (three times with {@code maxWorkPace} 300), an ill worker works at exactly half of that, and a sifter's partners
 * count once.
 */
public class PaceCompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(2, 2, 12);

	/**
	 * Five partners with Kinship II, a village kept 100%, Swift Hands III, Diligent and happy: far past the cap, so
	 * exactly twice the usual pace; three times with maxWorkPace 300; and ill, exactly half of twice.
	 */
	//$ gametest_ticks_batch AREA '200' '"pace_stack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "pace_stack")
	public void everyBonusStacksToExactlyTwiceThePace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean traits = Traits.ENABLED;
		boolean moods = Moods.ENABLED;
		VillageHalls.RADIUS = 16;
		Traits.ENABLED = true;
		Moods.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Traits.ENABLED = traits;
			Moods.ENABLED = moods;
			VillageNeeds.forget();
			Research.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = EntityType.VILLAGER.create(level);
		builder.setUUID(withTraits(Set.of(Traits.Trait.DILIGENT, Traits.Trait.CHEERFUL)));
		builder.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		builder.setNoAi(true);
		level.addFreshEntity(builder);
		Jobs.employ(level, builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 2))));

		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		String[] crew = {"machop", "geodude", "onix", "aron", "magnemite"};
		Direction[] sides = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH};
		for (int i = 0; i < crew.length; i++) {
			PastureCompatTests.pastured(helper, pasture, player, crew[i], sides[i]);
		}

		helper.runAfterDelay(20, () -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			hall.setResearch(new Research.State(Map.of("swift_hands", 3, "kinship", 2), Optional.empty(), 0, false));
			hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 1f));
			ModAttachments.LAST_MEAL.set(builder, level.getGameTime());
			VillageNeeds.forget();
			Research.forget();
			Moods.forget();
			Partners.forget(builder);
			helper.assertTrue(Partners.helpers(builder).size() == 5, "partners: " + Partners.helpers(builder));
			Moods.Mood mood = Moods.of(builder);
			helper.assertTrue(mood != null && mood.score() >= Moods.HAPPY, "not happy: " + mood);
			Pace.Breakdown pace = Pace.of(builder);
			helper.assertTrue(pace.faster().size() == 5 && pace.slower().isEmpty() && pace.capped(), "sources: " + pace);

			helper.assertTrue(Pace.factor(builder) == 0.5f, "every bonus: " + Pace.factor(builder));
			helper.assertTrue(BuilderLevels.delay(100, builder) == 50, "twice the pace: " + BuilderLevels.delay(100, builder));
			String status = BuilderLevels.describe(builder).getString();
			helper.assertTrue(status.contains(" · 100% faster: at the cap (") && status.contains(" from the pasture, a well-kept village, Swift Hands, diligent, a happy mood)"),
				"status: " + status);

			int was = Pace.MAX_PERCENT;
			try {
				Pace.MAX_PERCENT = 300;
				helper.assertTrue(Pace.factor(builder) == 100f / 300f, "maxWorkPace 300: " + Pace.factor(builder));
				helper.assertTrue(BuilderLevels.delay(300, builder) == 100, "three times the pace: " + BuilderLevels.delay(300, builder));
			} finally {
				Pace.MAX_PERCENT = was;
			}

			ModAttachments.ILL_SINCE.set(builder, level.getGameTime());
			Moods.forget();
			helper.assertTrue(Pace.factor(builder) == 1f, "ill: half of twice: " + Pace.factor(builder));
			helper.assertTrue(BuilderLevels.delay(100, builder) == 100, "ill: " + BuilderLevels.delay(100, builder));
			builder.discard();
			helper.succeed();
		});
	}

	/** A sifter with two Ground partners takes 70% of the usual time (42 of 60 ticks), not 49% (29) as when they counted twice. */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void aSifterWithTwoPartnersTakesSeventyPercent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos sieve = new BlockPos(2, 2, 2);
		helper.setBlock(sieve, ModBlocks.SIEVE);
		Villager sifter = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		sifter.setNoAi(true);
		Jobs.employ(level, sifter, helper.absolutePos(sieve), ModVillagers.SIEVE_POI, ModVillagers.SIFTER);
		helper.assertTrue(SifterWork.siftTicks(sifter) == 60, "no partners: " + SifterWork.siftTicks(sifter));
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PastureCompatTests.pastured(helper, pasture, player, "diglett", Direction.NORTH);
		PastureCompatTests.pastured(helper, pasture, player, "sandshrew", Direction.WEST);
		helper.runAfterDelay(10, () -> {
			Partners.forget(sifter);
			helper.assertTrue(Partners.helpers(sifter).size() == 2, "partners: " + Partners.helpers(sifter));
			helper.assertTrue(SifterWork.siftTicks(sifter) == 42, "two partners: " + SifterWork.siftTicks(sifter) + " (49% would be 29)");
			sifter.discard();
			helper.succeed();
		});
	}

	/** A UUID whose owner has exactly {@code traits} (in any order). */
	private static UUID withTraits(Set<Traits.Trait> traits) {
		RandomSource random = RandomSource.create(traits.hashCode());
		for (int i = 0; i < 200000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Set.copyOf(Traits.of(id)).equals(traits)) {
				return id;
			}
		}
		throw new IllegalStateException("nobody is " + traits);
	}
}
