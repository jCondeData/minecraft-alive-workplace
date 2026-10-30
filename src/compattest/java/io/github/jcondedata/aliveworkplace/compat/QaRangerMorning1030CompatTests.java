package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-30), ROADMAP 29.22: the Pokémon Ranger calms an Alpha "in the morning", the same morning the
 * Legends' guests keep (LegendGuests.MORNING_FROM to MORNING_TO, 1,000 to 6,000 ticks into the day): an Alpha beside the
 * Ranger is calmed at the morning's first tick and at its last, and not a tick before it starts or once it has ended.
 * Kills the two surviving mutants on PokemonRanger.choose's morning test (>= to >, < to <=) from this run's spot check.
 */
public class QaRangerMorning1030CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;

	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(100)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
	}

	private static Villager ranger(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, Legends.get(PokemonRanger.ID).orElseThrow(() -> new AssertionError("no Ranger file")), "test");
		LegendPowers.forget();
		return v;
	}

	private static PokemonEntity alpha(GameTestHelper helper, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse("machamp level=60", " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	/** Sets the clock to {@code timeOfDay} on day {@code day} (counting from the level's current day) and lets the Ranger look twice. */
	private static boolean calmsAt(GameTestHelper helper, Villager ranger, PokemonEntity alpha, long day, long timeOfDay) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(day * DAY + timeOfDay);
		PokemonRanger.tick(ranger); // chooses the Alpha (in the morning), and is beside it
		PokemonRanger.tick(ranger);
		return PokemonRanger.calmed(alpha);
	}

	/** Before the morning (999) and at its end (6,000) the Alpha beside the Ranger stays wild; at 1,000 it's calmed. */
	//$ gametest_ticks_batch AREA '200' '"qa_ranger_morning_start"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ranger_morning_start")
	public void qaTheRangerCalmsFromTheMorningsFirstTick(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		long day = level.getDayTime() / DAY + 1;
		PokemonEntity alpha = alpha(helper, new BlockPos(18, 2, 18));
		Villager ranger = ranger(helper, new BlockPos(19, 2, 17));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(LegendGuests.MORNING_FROM == 1000 && LegendGuests.MORNING_TO == 6000,
				"the morning moved: " + LegendGuests.MORNING_FROM + " to " + LegendGuests.MORNING_TO);
			helper.assertFalse(calmsAt(helper, ranger, alpha, day, LegendGuests.MORNING_FROM - 1), "the Alpha was calmed at 999, before the morning");
			helper.assertFalse(PokemonRanger.state(ranger).calmDay() == Chronicle.day(level), "the morning's calming was spent at 999: " + PokemonRanger.state(ranger));
			helper.assertFalse(calmsAt(helper, ranger, alpha, day, LegendGuests.MORNING_TO), "the Alpha was calmed at 6,000, when the morning is over");
			helper.assertTrue(calmsAt(helper, ranger, alpha, day, LegendGuests.MORNING_FROM), "the Alpha beside the Ranger wasn't calmed at 1,000, the morning's first tick: "
				+ PokemonRanger.state(ranger));
			helper.succeed();
		});
	}

	/** At 5,999, the morning's last tick, an Alpha beside the Ranger is still calmed. */
	//$ gametest_ticks_batch AREA '200' '"qa_ranger_morning_end"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_ranger_morning_end")
	public void qaTheRangerCalmsUntilTheMorningsLastTick(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		long day = level.getDayTime() / DAY + 1;
		PokemonEntity alpha = alpha(helper, new BlockPos(18, 2, 18));
		Villager ranger = ranger(helper, new BlockPos(19, 2, 17));
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(calmsAt(helper, ranger, alpha, day, LegendGuests.MORNING_TO - 1), "the Alpha beside the Ranger wasn't calmed at 5,999, the morning's last tick: "
				+ PokemonRanger.state(ranger));
			helper.succeed();
		});
	}
}
