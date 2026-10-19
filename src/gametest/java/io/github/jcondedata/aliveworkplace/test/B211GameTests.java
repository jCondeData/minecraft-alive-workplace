package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * B211 (the owner's live Cobbleverse server): the pack's MobsBeGone blocklist refuses iron golems, wolves, horses, cats
 * and llamas at {@code addEntity}, as it does pillagers (B208), so the village's own golems, relief horses, Beastmaster
 * foals and pups and caravan llamas never appeared. Here the world refuses those types the same way: each of the mod's
 * own spawns of them is in the world anyway and stays; with {@code forceVillageSpawns} off each is left out (a relief
 * rider comes on foot, the Golem Smith keeps his iron) and the log says why.
 */
public class B211GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** Cleans the area and readies the switch; {@link #refuse} then makes the world refuse as MobsBeGone does. All undone at the end. */
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

	private static void refuse() {
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof IronGolem || e instanceof Wolf || e instanceof AbstractHorse || e instanceof Cat;
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && level.getEntity(e.getUUID()) == e;
	}

	/** Two grown animals of {@code type} to breed, spawned before the world starts refusing them. */
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

	private static Relief.Band band(ServerLevel level, BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		long now = level.getGameTime();
		return new Relief.Band(at, at, Component.literal("Ashford"), 1, now, now, false);
	}

	/** Golems, a relief horse, a foal, a pup and a pack llama the world refuses are all put in it anyway, and stay. */
	//$ gametest_ticks_batch AREA '200' '"b211Forced"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b211Forced")
	public void b211VillageAnimalsAndGolemsRefusedByAnotherModStillSpawn(GameTestHelper helper) {
		List<Entity> made = refusing(helper, true, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (int i = 0; i < 8; i++) {
			made.add(helper.spawn(EntityType.VILLAGER, new BlockPos(10 + i, 2, 20)));
		}
		List<Horse> horses = pair(helper, EntityType.HORSE, 4, made);
		List<Wolf> wolves = pair(helper, EntityType.WOLF, 8, made);
		helper.runAfterDelay(2, () -> {
			refuse();
			// The refusal is real: an iron golem added the usual way is refused and gone.
			IronGolem plain = EntityType.IRON_GOLEM.create(level);
			plain.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(20, 2, 4)));
			helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "the world didn't refuse an iron golem as MobsBeGone does");

			List<Entity> spawned = new ArrayList<>();
			IronGolem pact = OldSage.pactGolem(level, hall);
			helper.assertTrue(in(level, pact), "the Iron Pact's golem isn't in the world: " + pact);
			IronGolem forged = GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY);
			helper.assertTrue(in(level, forged), "the Golem Smith's golem isn't in the world: " + forged);
			AbstractHorse foal = Beastmaster.breed(level, horses.get(0), horses.get(1), 0.1f);
			helper.assertTrue(in(level, foal) && foal.isBaby(), "the Beastmaster's foal isn't in the world: " + foal);
			Wolf pup = Beastmaster.pup(level, wolves.get(0), wolves.get(1));
			helper.assertTrue(in(level, pup) && pup.isBaby(), "the Beastmaster's pup isn't in the world: " + pup);
			Llama llama = CaravanSights.llama(level, helper.absolutePos(new BlockPos(24, 2, 4)), 0f, Items.WHITE_CARPET, 0);
			helper.assertTrue(in(level, llama), "the caravan's llama isn't in the world: " + llama);
			Relief.arrive(level, band(level, hall));
			List<Horse> relief = level.getEntitiesOfClass(Horse.class, helper.getBounds().inflate(8), h -> h.getTags().contains(Relief.HORSE_TAG));
			helper.assertTrue(relief.size() == 1 && in(level, relief.get(0)) && relief.get(0).getFirstPassenger() instanceof Villager,
				"the relief rider's horse, with him on it: " + relief);
			spawned.addAll(List.of(pact, forged, foal, pup, llama, relief.get(0)));
			made.addAll(spawned);
			made.add(relief.get(0).getFirstPassenger());
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Another mod refused to let minecraft:") && said.contains("MobsBeGone") && said.contains("forceVillageSpawns"),
				"no clear log line: " + said);
			helper.runAfterDelay(100, () -> {
				List<Entity> gone = spawned.stream().filter(e -> !in(level, e)).toList();
				helper.assertTrue(gone.isEmpty(), "gone after 100 ticks: " + gone);
				helper.succeed();
			});
		});
	}

	/** With forceVillageSpawns off each refused animal or golem is left out, a relief rider comes on foot, and the log says why. */
	//$ gametest_ticks_batch AREA '100' '"b211Off"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b211Off")
	public void b211WithForcingOffRefusedVillageAnimalsAreLeftOutAndLogged(GameTestHelper helper) {
		List<Entity> made = refusing(helper, false, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (int i = 0; i < 8; i++) {
			made.add(helper.spawn(EntityType.VILLAGER, new BlockPos(10 + i, 2, 20)));
		}
		List<Horse> horses = pair(helper, EntityType.HORSE, 4, made);
		List<Wolf> wolves = pair(helper, EntityType.WOLF, 8, made);
		helper.runAfterDelay(2, () -> {
			refuse();
			helper.assertTrue(OldSage.pactGolem(level, hall) == null, "an Iron Pact golem was reported though refused");
			helper.assertTrue(GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 2, 4)), GolemSmith.Role.SENTRY) == null,
				"a Golem Smith's golem was reported though refused");
			helper.assertTrue(Beastmaster.breed(level, horses.get(0), horses.get(1), 0.1f) == null, "a foal was reported though refused");
			helper.assertTrue(Beastmaster.pup(level, wolves.get(0), wolves.get(1)) == null, "a pup was reported though refused");
			helper.assertTrue(CaravanSights.llama(level, helper.absolutePos(new BlockPos(24, 2, 4)), 0f, Items.WHITE_CARPET, 0) == null,
				"a llama was reported though refused");
			Relief.arrive(level, band(level, hall));
			List<Villager> riders = Relief.riders(level, hall);
			made.addAll(riders);
			helper.assertTrue(riders.size() == 1 && riders.get(0).getVehicle() == null, "the relief rider, on foot: " + riders);
			long animals = level.getEntitiesOfClass(Entity.class, helper.getBounds().inflate(8),
				e -> e instanceof IronGolem || e instanceof Llama || e instanceof AbstractHorse && !horses.contains(e) || e instanceof Wolf && !wolves.contains(e)).size();
			helper.assertTrue(animals == 0, animals + " refused animals or golems in the world");
			String said = HostileSpawns.lastWarning();
			helper.assertTrue(said.startsWith("Could not spawn minecraft:") && said.contains("forceVillageSpawns is off"), "the log line: " + said);
			helper.succeed();
		});
	}
}
