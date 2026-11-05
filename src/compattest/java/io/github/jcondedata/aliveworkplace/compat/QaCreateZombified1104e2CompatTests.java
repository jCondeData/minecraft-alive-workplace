package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E, 2026-11-04 (Create machine work 39.3, B928/B960 kin: a villager lost without an ordinary death). MachineWork
 * promises "a worker killed mid-trip drops what they carried where they fell (it's never lost with them)". A zombie that
 * kills a villager on Normal or Hard turns him into a zombie villager (vanilla Zombie.killedEntity: convertTo, and no
 * death loot), so the worker's death is a conversion. These check that way out of a machine trip from the player's side:
 * the load lies where he fell, the zombie villager carries neither the load nor the trip, and the villager a player cures
 * back doesn't bring the load a second time (nothing lost, nothing made out of nothing).
 */
public class QaCreateZombified1104e2CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** A farmer on the way to the millstone with 12 wheat, killed by a zombie on Hard: the 12 wheat lie where he fell. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1104_machine_trip_zombified"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1104_machine_trip_zombified")
	public void aWorkerKilledByAZombieMidTripLeavesTheLoadOnceAndTheZombieNoTrip(GameTestHelper helper) {
		Trip t = trip(helper);
		ZombieVillager zombie = zombify(helper, t.worker());
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!ModAttachments.MACHINE_JOB.has(zombie), "the zombie villager still carries the machine trip: " + ModAttachments.MACHINE_JOB.get(zombie));
			helper.assertTrue(!zombie.getMainHandItem().is(Items.WHEAT) && !zombie.getOffhandItem().is(Items.WHEAT), "the zombie villager holds the wheat: "
				+ zombie.getMainHandItem() + " / " + zombie.getOffhandItem());
			int left = ground(helper, Items.WHEAT) + count(t.store(), Items.WHEAT);
			helper.assertTrue(left == 12, "the farmer killed by a zombie left " + left + " of the 12 wheat he carried to the millstone (ground "
				+ ground(helper, Items.WHEAT) + ", store " + count(t.store(), Items.WHEAT) + ")");
			zombie.discard();
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	/**
	 * The same farmer cured by a player (the golden apple's wait cut to a tick): he comes back a farmer with no trip, and
	 * the 12 wheat dropped when he fell are all there is (he doesn't deliver a second load to the millstone).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1104_machine_trip_cured"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1104_machine_trip_cured")
	public void aZombifiedWorkerCuredBringsNoSecondLoad(GameTestHelper helper) {
		Trip t = trip(helper);
		ZombieVillager zombie = zombify(helper, t.worker());
		cure(zombie);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(zombie.isRemoved(), "the zombie villager wasn't cured");
			List<Villager> back = helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2), Villager::isAlive);
			helper.assertTrue(back.size() == 1, "cured villagers: " + back.size());
			Villager cured = back.get(0);
			helper.assertTrue(cured.getVillagerData().getProfession() == t.kind().profession(), "cured, not a farmer: " + cured.getVillagerData().getProfession());
			helper.assertTrue(!ModAttachments.MACHINE_JOB.has(cured), "the cured farmer is back on the old trip: " + ModAttachments.MACHINE_JOB.get(cured));
			helper.assertTrue(!cured.getMainHandItem().is(Items.WHEAT), "the cured farmer holds wheat: " + cured.getMainHandItem());
			int left = ground(helper, Items.WHEAT) + count(t.store(), Items.WHEAT);
			helper.assertTrue(left == 12, "after the cure there are " + left + " wheat, not the 12 the farmer carried (ground "
				+ ground(helper, Items.WHEAT) + ", store " + count(t.store(), Items.WHEAT) + ")");
			cured.discard();
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	record Trip(Villager worker, Container store, MachineWorkCompatTests.Kind kind) {
	}

	/** A farmer at his composter, his store chest and a millstone rig, on a trip to the millstone carrying 12 wheat (day). */
	static Trip trip(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container store = (Container) helper.getBlockEntity(CHEST);
		MachineWorkCompatTests.Kind kind = MachineWorkCompatTests.Kind.MILL;
		BlockPos machine = MachineWorkCompatTests.rig(helper, kind, false);
		helper.setBlock(STATION, kind.station());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), kind.profession());
		helper.setDayTime(2000);
		MachineWorkFiles.Machine file = MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(kind.file))).findFirst().orElseThrow();
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(machine), file.id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		return new Trip(villager, store, kind);
	}

	/**
	 * A zombie kills {@code villager} as it does in a siege, on Hard (where vanilla always turns the villager), and the
	 * zombie villager he became is returned. The world's difficulty is put back at once, inside the same tick.
	 */
	static ZombieVillager zombify(GameTestHelper helper, Villager villager) {
		MinecraftServer server = helper.getLevel().getServer();
		Difficulty was = helper.getLevel().getDifficulty();
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(villager.position().add(1, 0, 0));
		helper.getLevel().addFreshEntity(zombie);
		try {
			server.setDifficulty(Difficulty.HARD, true);
			villager.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1000f);
		} finally {
			server.setDifficulty(was, true);
			zombie.discard();
		}
		helper.assertTrue(villager.isRemoved(), "setup: the zombie didn't turn the villager (alive " + villager.isAlive() + ")");
		List<ZombieVillager> zombies = helper.getLevel().getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2), ZombieVillager::isAlive);
		helper.assertTrue(zombies.size() == 1, "setup: zombie villagers where he fell: " + zombies.size());
		zombies.get(0).setPersistenceRequired();
		return zombies.get(0);
	}

	/** A player's cure (a golden apple on a weakened zombie villager), with its wait cut to a tick. */
	static void cure(ZombieVillager zombie) {
		try {
			var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
			start.setAccessible(true);
			start.invoke(zombie, null, 1);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't start the cure: " + e);
		}
	}

	static int ground(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(item))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	static int count(Container store, Item item) {
		int n = 0;
		for (int i = 0; i < store.getContainerSize(); i++) {
			n += store.getItem(i).is(item) ? store.getItem(i).getCount() : 0;
		}
		return n;
	}
}
