package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-16): the first mutation pass on {@code threat/Piglins} (ROADMAP 32.10). Of 8 mutants the existing
 * piglin tests let 3 through; two are in the spec ("every {@code EVERY} ticks a raiding piglin ... is angered at the
 * nearest villager within {@code SIGHT} blocks"), the third only moves a portal's smoke puff by a block.
 * <ul>
 * <li>a raiding piglin exactly 16 blocks from a villager is angered at it (within 16 includes 16), and one 17 blocks
 *     off is not (it is sent on toward it instead);</li>
 * <li>the anger comes on a round, a game time that is a multiple of 20, within one round of the raid starting.</li>
 * </ul>
 * The piglins and villagers have no AI, so nothing but the raid's steering sets the piglin's anger.
 */
public class QaPiglinsMutants1116d2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "setup: expected piglin_incursion.json loaded");
		return found.get();
	}

	/** A village round {@link #HALL} of radius 20, everything put back when the test ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	/** A still raiding piglin of the piglins' roster, as a raid makes it, in the middle of block {@code at}. */
	private static Mob piglin(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		level.addFreshEntityWithPassengers(mob);
		mob.setNoAi(true);
		return mob;
	}

	/** A still villager in the middle of block {@code at}. */
	private static Villager villager(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		BlockPos p = helper.absolutePos(at);
		villager.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		return villager;
	}

	private static UUID angryAt(Mob mob) {
		return mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).orElse(null);
	}

	/** Within 16 includes 16: the only villager of the village, exactly 16 blocks east, angers the piglin on the first round. */
	//$ gametest_ticks_batch AREA '80' '"qaPiglinSight16_1116d2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPiglinSight16_1116d2")
	public void aRaidingPiglinIsAngeredAtAVillagerExactlySixteenBlocksOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(5, 2, 15));
		Villager villager = villager(helper, new BlockPos(21, 2, 15));
		helper.assertTrue(piglin.distanceToSqr(villager) == Piglins.SIGHT * Piglins.SIGHT,
			"setup: expected the piglin exactly " + Piglins.SIGHT + " blocks from the villager, got " + piglin.distanceTo(villager));
		VillageRaids.track(level, hall, culture, List.of(piglin));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(villager.getUUID().equals(angryAt(piglin)),
				"a raiding piglin exactly 16 blocks from the village's only villager wasn't angered at it after two rounds: angry at " + angryAt(piglin)
					+ ", expected " + villager.getUUID());
			helper.succeed();
		});
	}

	/** Past sight: a villager 17 blocks east doesn't anger the piglin; it is sent walking toward it instead. */
	//$ gametest_ticks_batch AREA '80' '"qaPiglinSight17_1116d2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPiglinSight17_1116d2")
	public void aRaidingPiglinIsNotAngeredAtAVillagerSeventeenBlocksOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(5, 2, 15));
		Villager villager = villager(helper, new BlockPos(22, 2, 15));
		VillageRaids.track(level, hall, culture, List.of(piglin));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(angryAt(piglin) == null,
				"a raiding piglin 17 blocks from the villager (past its 16-block sight) was angered at " + angryAt(piglin) + ", expected no anger yet");
			helper.assertTrue(piglin.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(),
				"a raiding piglin with the villager out of sight wasn't sent on toward it: no walk target");
			helper.succeed();
		});
	}

	/** "Every 20 ticks": watched every tick, the piglin's anger first comes on a game time that is a multiple of 20, within one round. */
	//$ gametest_ticks_batch AREA '80' '"qaPiglinRound1116d2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPiglinRound1116d2")
	public void aRaidingPiglinIsAngeredOnlyOnTheTwentyTickRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(10, 2, 15));
		Villager villager = villager(helper, new BlockPos(14, 2, 15));
		long start = level.getGameTime();
		VillageRaids.track(level, hall, culture, List.of(piglin));
		long[] angered = {-1};
		helper.onEachTick(() -> {
			if (angered[0] < 0 && angryAt(piglin) != null) {
				angered[0] = level.getGameTime();
			}
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(angered[0] >= 0 && villager.getUUID().equals(angryAt(piglin)),
				"setup: the piglin 4 blocks from the villager was never angered at it: angry at " + angryAt(piglin));
			helper.assertTrue(angered[0] % Piglins.EVERY == 0,
				"the piglin was angered at game time " + angered[0] + " (" + (angered[0] % Piglins.EVERY) + " ticks past a round), expected only on a round, a multiple of " + Piglins.EVERY);
			helper.assertTrue(angered[0] - start <= Piglins.EVERY,
				"the piglin was angered " + (angered[0] - start) + " ticks after the raid started, expected within one round (" + Piglins.EVERY + ")");
			helper.succeed();
		});
	}
}
