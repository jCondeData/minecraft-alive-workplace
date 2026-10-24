package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.MachineWorkCompatTests.Kind;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-10-24), B244 with the owner's Create port, from the bug's Expected ("the carried items go back to the
 * store or drop when the trip can't go on"), for the jobs its own fix tests don't take (they use the farmer): the miner's
 * raw iron for the crushing wheels and the armorer's iron for the press. The load comes back once: a save and reload of
 * the worker afterwards, machine work switched on again, or the worker's death bring none of it a second time.
 */
public class QaMachineTripCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * A miner carrying 10 raw iron to the crushing wheels when the owner switches {@code machineWork} off: the 10 go back into
	 * the store. Saved and loaded again and machine work switched back on, the reloaded miner has no trip and the iron
	 * still comes to exactly 10: never 20.
	 */
	//$ gametest_ticks_batch AREA '900' '"qa_machine_trip_reload"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qa_machine_trip_reload")
	public void aMinersLoadPutBackStaysPutBackOverASaveAndTheSwitchTurnedOnAgain(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos crusher = MachineWorkCompatTests.rig(helper, Kind.CRUSHER, false); // standing still: nothing may be fed to it
		Villager miner = worker(helper, Kind.CRUSHER);
		Item iron = Kind.CRUSHER.input();
		ModAttachments.MACHINE_JOB.set(miner, new MachineJob(helper.absolutePos(crusher), file(Kind.CRUSHER).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(iron, 10))));
		boolean before = MachineWork.ENABLED;
		MachineWork.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> MachineWork.ENABLED = before);
		Villager[] now = {miner};
		boolean[] reloaded = {false};
		int[] at = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			int all = total(helper, chest, iron, now[0]);
			helper.assertTrue(all == 10, "the miner's raw iron came to " + all + ", not 10 (store " + count(chest, iron) + ", trip "
				+ ModAttachments.MACHINE_JOB.get(now[0]) + ")");
			if (!reloaded[0]) {
				if (!MachineWork.busy(now[0]) && count(chest, iron) == 10) {
					CompoundTag saved = new CompoundTag();
					now[0].save(saved);
					now[0].discard();
					Villager copy = (Villager) EntityType.loadEntityRecursive(saved, helper.getLevel(), e -> e);
					helper.assertTrue(copy != null, "the miner didn't load");
					helper.getLevel().addFreshEntity(copy);
					helper.assertTrue(ModAttachments.MACHINE_JOB.get(copy) == null, "the reloaded miner has a trip again: " + ModAttachments.MACHINE_JOB.get(copy));
					now[0] = copy;
					MachineWork.ENABLED = true; // and the owner switches machine work back on
					reloaded[0] = true;
					at[0] = tick[0];
				} else if (tick[0] > 300) {
					helper.fail("machineWork off, but the miner's 10 raw iron aren't back in the store: " + count(chest, iron) + " ("
						+ ModAttachments.MACHINE_JOB.get(now[0]) + ")");
				}
				return;
			}
			if (tick[0] >= at[0] + 400) {
				helper.assertTrue(count(chest, iron) + carried(now[0], iron) == 10, "after the reload the iron came to "
					+ (count(chest, iron) + carried(now[0], iron)));
				MachineWorkCompatTests.clearMachines(helper);
				helper.succeed();
			}
		});
	}

	/**
	 * A Novice armorer carrying 8 iron to the press loses the job (the blast furnace broken, the work day over): the 8 go
	 * back into the store, and when the armorer is then killed nothing more drops: the trip's record went with the load.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa_machine_trip_killed"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_machine_trip_killed")
	public void anArmorerWhoseLoadWentBackDropsNoneOfItWhenKilled(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos press = MachineWorkCompatTests.rig(helper, Kind.PRESS, false);
		Villager armorer = worker(helper, Kind.PRESS);
		armorer.setVillagerXp(0); // a Novice who never traded: vanilla takes the job back with the workstation
		Item iron = Kind.PRESS.input();
		ModAttachments.MACHINE_JOB.set(armorer, new MachineJob(helper.absolutePos(press), file(Kind.PRESS).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(iron, 8))));
		helper.setDayTime(13000);
		helper.destroyBlock(STATION);
		boolean[] killed = {false};
		int[] at = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			int all = count(chest, iron) + ground(helper, iron) + carried(armorer, iron);
			helper.assertTrue(all <= 8, "the armorer's iron came to " + all + ", more than the 8 carried (store " + count(chest, iron)
				+ ", on the ground " + ground(helper, iron) + ")");
			if (!killed[0]) {
				if (!MachineWork.busy(armorer) && count(chest, iron) == 8) {
					armorer.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
					helper.assertTrue(armorer.isDeadOrDying(), "setup: the armorer didn't die");
					killed[0] = true;
					at[0] = tick[0];
				} else if (tick[0] > 450) {
					helper.fail("the jobless armorer's 8 iron aren't back in the store: " + count(chest, iron) + " (job "
						+ armorer.getVillagerData().getProfession() + ", trip " + ModAttachments.MACHINE_JOB.get(armorer) + ")");
				}
				return;
			}
			if (tick[0] >= at[0] + 40) {
				helper.assertTrue(ground(helper, iron) == 0, "the killed armorer dropped " + ground(helper, iron) + " iron that was already back in the store");
				helper.assertTrue(count(chest, iron) == 8, "the store has " + count(chest, iron) + " of the 8 iron");
				MachineWorkCompatTests.clearMachines(helper);
				helper.succeed();
			}
		});
	}

	private static Villager worker(GameTestHelper helper, Kind kind) {
		helper.setBlock(STATION, kind.station());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), kind.profession());
		helper.assertTrue(villager.getVillagerData().getProfession() == kind.profession(), "setup: no " + kind.job + " at the workstation");
		helper.setDayTime(2000);
		return villager;
	}

	private static Container chest(GameTestHelper helper) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		return (Container) helper.getBlockEntity(CHEST);
	}

	private static MachineWorkFiles.Machine file(Kind kind) {
		return MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(kind.file))).findFirst()
			.orElseThrow(() -> new AssertionError("no machine_work file " + kind.file + ": " + MachineWorkFiles.all()));
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int ground(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.isAlive() && e.getItem().is(item))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int carried(Villager villager, Item item) {
		MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
		int inHand = 0;
		for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
			if (villager.getInventory().getItem(i).is(item)) {
				inHand += villager.getInventory().getItem(i).getCount();
			}
		}
		return inHand + (job == null ? 0 : job.carried().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum());
	}

	/** The item in the store, on the ground and carried by the worker. */
	private static int total(GameTestHelper helper, Container chest, Item item, Villager worker) {
		return count(chest, item) + ground(helper, item) + carried(worker, item);
	}
}
