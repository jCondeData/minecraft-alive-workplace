package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-16): the first mutation pass on {@code threat/Plunder} (ROADMAP 32.10, the piglins' tactic
 * {@code plunder}), tested from the spec and the class's own contract, for the mutants the existing plunder tests let
 * through.
 * <ul>
 * <li>(the tactic looks about "every {@code Plunder.EVERY} ticks") a piglin beside a chest of gold takes it on a round,
 *     the game time a multiple of 20, never on the ticks between, and within one round of the raid starting;</li>
 * <li>("within 4 blocks of a village chest", the reach's edge in height) a chest 4 blocks along on a ledge 2 up, or in
 *     a hollow 2 down, is plundered; one 4 along and 3 up is not;</li>
 * <li>(the chronicle tells what was carried off "when there was no siege") a raid that carried nothing off writes no
 *     "carried off 0 gold" line, while one that carried off one nugget writes "carried off 1 gold".</li>
 * </ul>
 */
public class QaPlunder1116dGameTests implements FabricGameTest {
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
			Sieges.forget();
			Moods.forget();
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
		ThreatData.get(level).forgetSiege(hall);
		ThreatData.get(level).forgetPast(hall);
		return hall;
	}

	/** A raiding piglin of the piglins' roster, as a raid makes it, standing still and bare-headed at {@code at}. */
	private static Mob piglin(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		mob.addTag(VillageRaids.TAG);
		mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(mob);
		mob.setNoAi(true);
		return mob;
	}

	/** A chest at {@code at} holding {@code stack} in its first slot. */
	private static Container chest(GameTestHelper helper, BlockPos at, ItemStack stack) {
		helper.setBlock(at, Blocks.CHEST);
		Container box = helper.getBlockEntity(at);
		box.setItem(0, stack);
		return box;
	}

	/**
	 * "Every {@code EVERY} (20) ticks a raider ... looks about": a still piglin 2 blocks from a chest of 6 gold ingots,
	 * watched every tick from the moment its raid starts. It carries nothing on every tick until the one it takes the 6,
	 * and that tick's game time is a multiple of 20, at most 20 ticks after the raid started.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderEvery1116d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderEvery1116d")
	public void aPiglinTakesTheGoldOnlyOnTheTwentyTickRound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Container box = chest(helper, new BlockPos(8, 2, 6), new ItemStack(Items.GOLD_INGOT, 6));
		Mob piglin = piglin(helper, culture, new BlockPos(6, 2, 6));
		long start = level.getGameTime();
		VillageRaids.track(level, hall, culture, List.of(piglin));
		long[] took = {-1};
		helper.onEachTick(() -> {
			if (took[0] < 0 && !Plunder.carried(piglin).isEmpty()) {
				took[0] = level.getGameTime();
			}
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(took[0] >= 0 && Plunder.carried(piglin).is(Items.GOLD_INGOT) && Plunder.carried(piglin).getCount() == 6 && box.getItem(0).isEmpty(),
				"setup: the piglin didn't plunder the chest beside it: it carries " + Plunder.carried(piglin) + ", the chest holds " + box.getItem(0));
			helper.assertTrue(took[0] % Plunder.EVERY == 0,
				"the piglin took the gold at game time " + took[0] + " (" + (took[0] % Plunder.EVERY) + " ticks past a round), expected only on a round, a multiple of " + Plunder.EVERY);
			helper.assertTrue(took[0] - start <= Plunder.EVERY,
				"the piglin took the gold " + (took[0] - start) + " ticks after the raid started, expected within one round (" + Plunder.EVERY + ")");
			helper.succeed();
		});
	}

	/**
	 * "Within 4 blocks of a village chest", the reach's edge in height: three still piglins, each with one chest of 5 gold
	 * ingots 4 blocks east of it. Two rounds later the chest on a ledge 2 blocks up and the one in a hollow 2 blocks down
	 * are plundered; the one 3 blocks up (5 blocks off) is whole and its piglin carries nothing.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaPlunderReachHeight1116d"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaPlunderReachHeight1116d")
	public void aChestFourAlongAndTwoUpOrDownIsPlunderedAndThreeUpIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Container up2 = chest(helper, new BlockPos(9, 6, 5), new ItemStack(Items.GOLD_INGOT, 5));
		Container down2 = chest(helper, new BlockPos(9, 2, 15), new ItemStack(Items.GOLD_INGOT, 5));
		Container up3 = chest(helper, new BlockPos(9, 7, 25), new ItemStack(Items.GOLD_INGOT, 5));
		// The middle piglin stands 2 blocks higher, on a stone pillar.
		helper.setBlock(new BlockPos(5, 2, 15), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 3, 15), Blocks.STONE);
		Mob a = piglin(helper, culture, new BlockPos(5, 4, 5));
		Mob b = piglin(helper, culture, new BlockPos(5, 4, 15));
		Mob c = piglin(helper, culture, new BlockPos(5, 4, 25));
		// a and c stand on stone too, 2 above the floor, so the three differ only in the chest's height.
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 2, 25), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 3, 25), Blocks.STONE);
		VillageRaids.track(level, hall, culture, List.of(a, b, c));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Plunder.carried(a).is(Items.GOLD_INGOT) && Plunder.carried(a).getCount() == 5 && up2.getItem(0).isEmpty(),
				"the chest 4 blocks east and 2 up wasn't plundered: the piglin carries " + Plunder.carried(a) + ", the chest holds " + up2.getItem(0));
			helper.assertTrue(Plunder.carried(b).is(Items.GOLD_INGOT) && Plunder.carried(b).getCount() == 5 && down2.getItem(0).isEmpty(),
				"the chest 4 blocks east and 2 down wasn't plundered: the piglin carries " + Plunder.carried(b) + ", the chest holds " + down2.getItem(0));
			helper.assertTrue(Plunder.carried(c).isEmpty() && up3.getItem(0).is(Items.GOLD_INGOT) && up3.getItem(0).getCount() == 5,
				"the chest 4 blocks east and 3 up (5 off) was plundered: the piglin carries " + Plunder.carried(c) + ", the chest holds " + up3.getItem(0));
			helper.succeed();
		});
	}

	/**
	 * 32.10: the chronicle tells the gold carried off when there was no siege. A piglin raid ends having carried nothing
	 * off: the hall's chronicle gets no "carried off" line at all (no "carried off 0 gold"). Then one that carried off a
	 * single nugget ends: "The piglins carried off 1 gold from the village's chests", once.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaPlunderNothing1116d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPlunderNothing1116d")
	public void aRaidThatCarriedNothingOffWritesNoPlunderLine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "setup: no Village Hall");
		Culture culture = piglins(helper);
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(data.siege(hall).isEmpty() && data.plundered(hall) == 0, "setup: a siege or a count left over: " + data.plundered(hall));
		Threats.tactics(culture).forEach(t -> t.end(level, hall, culture, false));
		List<String> lines = entity.chronicle().stream().map(e -> e.text().getString()).toList();
		helper.assertTrue(lines.stream().noneMatch(s -> s.contains("carried off")),
			"a raid that carried nothing off wrote a plunder line: " + lines);
		data.plunder(hall, 1);
		Threats.tactics(culture).forEach(t -> t.end(level, hall, culture, false));
		List<String> raids = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).map(e -> e.text().getString())
			.filter(s -> s.contains("carried off")).toList();
		helper.assertTrue(raids.equals(List.of("The piglins carried off 1 gold from the village's chests")),
			"a raid that carried off one nugget wrote: " + raids);
		helper.succeed();
	}
}
