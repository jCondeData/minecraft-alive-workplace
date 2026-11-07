package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (lane-e-1107-2212), B959 and B960's fix, from the bugs' words: "struck or killed, the carried part goes back
 * to the store or falls where he stood (one, never two)", and a machine trip's load "falls where he stood ... as on a
 * death, once". The fix's tests strike or kill a worker who never left the chunk; these add the ways a player meets it
 * on a live server:
 * <ul>
 * <li>a player kills the Mechanic and vanilla's own hand drop comes up (made certain here, 8.5% in play): one cogwheel,
 * not two;</li>
 * <li>the world is saved and opened again while he carries the cogwheel: struck after that, it's still not lost; left
 * alone, he mends the hole and nothing is kept in his hand or made twice;</li>
 * <li>a farmer saved and reloaded on the way to the millstone, then struck: the 12 wheat fall, once.</li>
 * </ul>
 */
public class QaCarriedPartsGone1107eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Saves {@code v} and loads it as a new villager in the same place (a restart while he's mid-work). */
	static Villager reload(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(copy != null, "a villager to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	private static int cogs(GameTestHelper helper, QaMechanicPartStruck1104eCompatTests.Setup s) {
		return MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog())
			+ QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
	}

	/** Killed by a player while vanilla's hand drop comes up: the store's 2 cogwheels are 2 again, not 3. */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1107_mechanic_player_kill"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1107_mechanic_player_kill")
	public void aMechanicKilledByAPlayerLeavesTheCogwheelOnce(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		long[] killedAt = {-1};
		helper.onEachTick(() -> {
			if (killedAt[0] < 0 && s.mechanic().getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				s.mechanic().setDropChance(EquipmentSlot.MAINHAND, 1.0f); // vanilla's 8.5% for a player's kill, made certain
				killedAt[0] = helper.getTick();
				s.mechanic().hurt(helper.getLevel().damageSources().playerAttack(player), 1000f);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killedAt[0] >= 0, "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(!s.mechanic().isAlive(), "the Mechanic survived the player's blow");
			helper.assertTrue(helper.getTick() >= killedAt[0] + 20, "waiting a second for the drops");
			int store = MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog());
			int ground = QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
			helper.assertTrue(store + ground == 2, "a player killed the Mechanic carrying 1 of the store's 2 cogwheels and "
				+ (store + ground) + " are left (store " + store + ", ground " + ground + "): expected 2, one never two");
		});
	}

	/** Reloaded with the cogwheel in hand, then struck into a witch: the 2 cogwheels are still in the store or on the ground. */
	//$ gametest_ticks_batch AREA '1600' '"qa_e1107_mechanic_reload_struck"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_e1107_mechanic_reload_struck")
	public void aMechanicReloadedWithTheCogwheelThenStruckLeavesIt(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		Villager[] m = {s.mechanic()};
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			if (struckAt[0] < 0 && m[0].getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				m[0] = reload(helper, m[0]);
				helper.assertTrue(m[0].getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog()), "the cogwheel isn't in his hand after the reload: "
					+ m[0].getMainHandItem());
				struckAt[0] = helper.getTick();
				QaMechanicPartStruck1104eCompatTests.strike(helper.getLevel(), m[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0, "the Mechanic never took the cogwheel from the store");
			helper.assertTrue(m[0].isRemoved(), "lightning didn't turn the reloaded Mechanic into a witch");
			helper.assertTrue(helper.getTick() >= struckAt[0] + 20, "waiting a second for the drops");
			helper.assertTrue(cogs(helper, s) == 2, "reloaded with a cogwheel in hand, then struck: " + cogs(helper, s)
				+ " of the store's 2 cogwheels are in the store or on the ground");
		});
	}

	/** Reloaded with the cogwheel in hand and left alone: he mends the hole, his hand ends empty, the store keeps 1. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1107_mechanic_reload_mends"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1107_mechanic_reload_mends")
	public void aMechanicReloadedWithTheCogwheelStillMendsAndKeepsNone(GameTestHelper helper) {
		QaMechanicPartStruck1104eCompatTests.Setup s = QaMechanicPartStruck1104eCompatTests.setup(helper);
		Villager[] m = {s.mechanic()};
		long[] loadedAt = {-1};
		helper.onEachTick(() -> {
			if (loadedAt[0] < 0 && m[0].getMainHandItem().is(QaMechanicPartStruck1104eCompatTests.cog())) {
				m[0] = reload(helper, m[0]);
				loadedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(loadedAt[0] >= 0, "the Mechanic never took the cogwheel from the store");
			boolean mended = helper.getBlockState(s.hole()).is(MechanicCompatTests.create("cogwheel"));
			ItemStack hand = m[0].getMainHandItem();
			int store = MechanicCompatTests.count(s.store(), QaMechanicPartStruck1104eCompatTests.cog());
			int ground = QaMechanicPartStruck1104eCompatTests.ground(helper, QaMechanicPartStruck1104eCompatTests.cog());
			helper.assertTrue(mended && hand.isEmpty() && store == 1 && ground == 0, "reloaded with the store's cogwheel in hand "
				+ (helper.getTick() - loadedAt[0]) + " ticks ago: hole mended " + mended + ", in his hand " + hand + ", store " + store
				+ " (1 expected), ground " + ground);
		});
	}

	/** A farmer reloaded on the way to the millstone with 12 wheat, then struck: the 12 wheat lie where he stood, once. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1107_machine_trip_reload_struck"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1107_machine_trip_reload_struck")
	public void aWorkerReloadedMidTripThenStruckLeavesTheLoadOnce(GameTestHelper helper) {
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
		MachineWorkFiles.Machine file = MachineWorkFiles.all().stream().filter(f -> f.id().equals(AliveWorkplace.id(kind.file))).findFirst().orElseThrow();
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(machine), file.id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		Villager loaded = reload(helper, villager);
		MachineJob job = ModAttachments.MACHINE_JOB.get(loaded);
		helper.assertTrue(job != null && job.carried().stream().mapToInt(ItemStack::getCount).sum() == 12,
			"the trip's load after the reload: " + job);
		QaMechanicPartStruck1104eCompatTests.strike(helper.getLevel(), loaded);
		helper.assertTrue(loaded.isRemoved(), "setup: lightning didn't turn the farmer into a witch");
		helper.runAfterDelay(10, () -> {
			int ground = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.WHEAT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			int inStore = MechanicCompatTests.count(store, Items.WHEAT);
			helper.assertTrue(ground + inStore == 12, "reloaded mid-trip, then struck, the farmer left " + (ground + inStore)
				+ " of the 12 wheat (ground " + ground + ", store " + inStore + "): 12 expected, once");
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}
}
