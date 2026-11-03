package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * QA (lane D, 2026-11-03), verifying B211 from its text at the boundaries B211GameTests and the 1101 QA classes leave:
 * "our own spawns of these go through the same checked path (forced when forceVillageSpawns is on), with a clear log line
 * when refused"; "relief riders' horses (refused: the rider comes on foot)"; "pups now bred as vanilla breeds them".
 * <ul>
 * <li>A mob forced past the blocklist is a living part of the world, not a ghost: it ticks, a relief rider stays in the
 * saddle, and a whole band of three rides in on three horses.</li>
 * <li>Vanilla breeding: a pup of two tame wolves is tame and its owner's, also when forced past the blocklist.</li>
 * <li>Only what the world refuses is touched: with only golems refused, a llama, a foal and a pup come the usual way
 * and nothing is logged; the golem then is, by its own id.</li>
 * <li>With the switch off, every rider of a band of three still comes, on foot, none lost.</li>
 * </ul>
 */
public class QaVillageSpawns1103dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** Cleans the area and readies the switch; everything (refusal, switch, bands, spawned mobs) undone at the end. */
	private static List<Entity> refusing(GameTestHelper helper, boolean force, List<Entity> made) {
		Leftovers.clear(helper);
		int edge = Relief.EDGE;
		Relief.forget();
		Relief.EDGE = 4;
		HostileSpawns.forget();
		HostileSpawns.FORCE_VILLAGE = force;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE_VILLAGE = true;
			HostileSpawns.forget();
			Relief.EDGE = edge;
			Relief.forget();
			made.forEach(Entity::discard);
			helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(32),
				e -> e.getTags().contains(Relief.HORSE_TAG) || e.getTags().contains(CaravanSights.TAG) || e instanceof IronGolem
					|| e instanceof Wolf || e instanceof AbstractHorse || e instanceof Villager).forEach(Entity::discard);
		});
		return made;
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && level.getEntity(e.getUUID()) == e;
	}

	private static <T extends net.minecraft.world.entity.animal.Animal> List<T> pair(GameTestHelper helper, EntityType<T> type, int x, List<Entity> made) {
		List<T> two = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			T a = helper.spawn(type, new BlockPos(x + i, 2, 24));
			a.setAge(0);
			two.add(a);
			made.add(a);
		}
		return two;
	}

	private static Relief.Band band(ServerLevel level, BlockPos hall, int riders) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		long now = level.getGameTime();
		return new Relief.Band(at, at, Component.literal("Ashford"), riders, now, now, false);
	}

	private static void villagers(GameTestHelper helper, List<Entity> made) {
		for (int i = 0; i < 8; i++) {
			made.add(helper.spawn(EntityType.VILLAGER, new BlockPos(10 + i, 2, 20)));
		}
	}

	/**
	 * Forced past a blocklist refusing golems, wolves and horses: the Golem Smith's golem and a band of three relief riders'
	 * horses are in the world and keep ticking (40 ticks later each has lived through them, every rider still mounted), and
	 * a pup of two tame wolves is tame and their owner's, as vanilla breeds it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1103dForcedTick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103dForcedTick")
	public void forcedVillageMobsTickAndABandOfThreeRidesInMounted(GameTestHelper helper) {
		List<Entity> made = refusing(helper, true, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		villagers(helper, made);
		List<Wolf> wolves = pair(helper, EntityType.WOLF, 8, made);
		UUID owner = UUID.randomUUID();
		for (Wolf w : wolves) {
			w.setTame(true, true);
			w.setOwnerUUID(owner);
		}
		helper.runAfterDelay(2, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof IronGolem || e instanceof Wolf || e instanceof AbstractHorse;
			IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY);
			helper.assertTrue(in(level, golem), "the Golem Smith's golem isn't in the world: " + golem);
			Wolf pup = Beastmaster.pup(level, wolves.get(0), wolves.get(1));
			helper.assertTrue(in(level, pup) && pup.isBaby(), "the pup isn't in the world: " + pup);
			helper.assertTrue(pup.isTame() && owner.equals(pup.getOwnerUUID()),
				"a pup of two tame wolves isn't tame and theirs, as vanilla breeds it: tame " + pup.isTame() + ", owner " + pup.getOwnerUUID());
			Relief.arrive(level, band(level, hall, 3));
			List<Horse> horses = level.getEntitiesOfClass(Horse.class, helper.getBounds().inflate(16), h -> h.getTags().contains(Relief.HORSE_TAG));
			helper.assertTrue(horses.size() == 3 && horses.stream().allMatch(h -> in(level, h) && h.getFirstPassenger() instanceof Villager),
				"a band of three, mounted: " + horses + " riders " + horses.stream().map(Entity::getFirstPassenger).toList());
			made.addAll(horses);
			horses.forEach(h -> made.add(h.getFirstPassenger()));
			made.add(golem);
			made.add(pup);
			int golemAt = golem.tickCount;
			int pupAt = pup.tickCount;
			List<Integer> horsesAt = horses.stream().map(h -> h.tickCount).toList();
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(in(level, golem) && golem.tickCount - golemAt >= 30, "the forced golem doesn't tick: " + golemAt + " -> " + golem.tickCount);
				helper.assertTrue(in(level, pup) && pup.tickCount - pupAt >= 30, "the forced pup doesn't tick: " + pupAt + " -> " + pup.tickCount);
				for (int i = 0; i < horses.size(); i++) {
					Horse h = horses.get(i);
					helper.assertTrue(in(level, h) && h.tickCount - horsesAt.get(i) >= 30, "a forced relief horse doesn't tick: " + horsesAt.get(i) + " -> " + h.tickCount);
					helper.assertTrue(h.getFirstPassenger() instanceof Villager v && in(level, v), "a rider fell out of the saddle: " + h.getPassengers());
				}
				helper.succeed();
			});
		});
	}

	/**
	 * Only golems refused: a pack llama, a foal and a pup come the usual way and nothing is logged for them; the golem then
	 * is forced in and the log line names it by its id.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa1103dOnlyGolems"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1103dOnlyGolems")
	public void onlyWhatTheWorldRefusesIsForcedAndLogged(GameTestHelper helper) {
		List<Entity> made = refusing(helper, true, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		List<Horse> horses = pair(helper, EntityType.HORSE, 4, made);
		List<Wolf> wolves = pair(helper, EntityType.WOLF, 8, made);
		helper.runAfterDelay(2, () -> {
			Predicate<Entity> golems = e -> e instanceof IronGolem;
			HostileSpawns.REFUSED_FOR_TESTS = golems;
			Llama llama = CaravanSights.llama(level, helper.absolutePos(new BlockPos(24, 2, 4)), 0f, Items.WHITE_CARPET, 0);
			AbstractHorse foal = Beastmaster.breed(level, horses.get(0), horses.get(1), 0.1f);
			Wolf pup = Beastmaster.pup(level, wolves.get(0), wolves.get(1));
			made.add(llama);
			made.add(foal);
			made.add(pup);
			helper.assertTrue(in(level, llama) && in(level, foal) && in(level, pup), "an animal nobody refused is missing: " + llama + ", " + foal + ", " + pup);
			helper.assertTrue(HostileSpawns.lastWarning().isEmpty(), "a log line for animals nobody refused: " + HostileSpawns.lastWarning());
			IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY);
			made.add(golem);
			helper.assertTrue(in(level, golem), "the refused golem isn't forced in: " + golem);
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Another mod refused to let minecraft:iron_golem spawn"), "the golem's log line: " + said);
			helper.succeed();
		});
	}

	/** forceVillageSpawns off, horses refused: a band of three relief riders all come, on foot, none lost; the llama nobody refused comes. */
	//$ gametest_ticks_batch AREA '100' '"qa1103dOffBand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1103dOffBand")
	public void withForcingOffEveryRiderOfABandComesOnFoot(GameTestHelper helper) {
		List<Entity> made = refusing(helper, false, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		villagers(helper, made);
		helper.runAfterDelay(2, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof AbstractHorse && !(e instanceof Llama);
			Relief.arrive(level, band(level, hall, 3));
			List<Villager> riders = Relief.riders(level, hall);
			made.addAll(riders);
			helper.assertTrue(riders.size() == 3 && riders.stream().allMatch(r -> in(level, r) && r.getVehicle() == null),
				"three relief riders on foot: " + riders + " vehicles " + riders.stream().map(Entity::getVehicle).toList());
			long horses = level.getEntitiesOfClass(Horse.class, helper.getBounds().inflate(16), h -> h.getTags().contains(Relief.HORSE_TAG)).size();
			helper.assertTrue(horses == 0, horses + " refused relief horses in the world");
			helper.assertTrue(HostileSpawns.lastWarning().startsWith("Could not spawn minecraft:horse") && HostileSpawns.lastWarning().contains("forceVillageSpawns is off"),
				"the log line: " + HostileSpawns.lastWarning());
			Llama llama = CaravanSights.llama(level, helper.absolutePos(new BlockPos(24, 2, 4)), 0f, Items.WHITE_CARPET, 0);
			made.add(llama);
			helper.assertTrue(in(level, llama), "a llama nobody refused is left out with the switch off: " + llama);
			helper.succeed();
		});
	}
}
