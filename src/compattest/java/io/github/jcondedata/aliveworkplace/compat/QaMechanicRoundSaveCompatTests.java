package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, 2026-10-24, saves): ROADMAP 39.2's Mechanic "walks the village's machines". His round is saved
 * ({@code mechanic_round}), so a save in the middle of it (the server stopping, the chunk unloading) carries on where he
 * was: with a near Millstone just seen to (his line moves on from it) and a Mechanical Press next, the Mechanic loaded from the save goes to the
 * Press, not back to the Millstone. (Lost on a reload, the far machines of a village whose chunks often unload would
 * never be seen to.)
 */
public class QaMechanicRoundSaveCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos MILLSTONE = new BlockPos(5, 2, 4);
	private static final BlockPos PRESS = new BlockPos(9, 2, 9);

	private static String line(GameTestHelper helper, Villager v) {
		WorkerStatus.Entry e = v == null ? null : WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? null : e.line().getString();
	}

	/** The round over a save: the reloaded Mechanic's first machine is the one after the Millstone he had just seen to. */
	//$ gametest_ticks_batch AREA '2400' '"qa_mechanic_round_save"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_mechanic_round_save")
	public void aMechanicSavedMidRoundGoesOnToTheNextMachine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, helper.absolutePos(TOOLBOX), 64)) {
			sites.forgetFinished(f.placement());
		}
		MachineWorkCompatTests.noVillage(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(MILLSTONE, MechanicCompatTests.create("millstone").defaultBlockState());
		helper.setBlock(PRESS, MechanicCompatTests.create("mechanical_press").defaultBlockState());
		helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(level, first, helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
		helper.assertTrue(MechanicWork.isMechanic(first), "setup: no Mechanic at the toolbox");
		helper.setDayTime(2000);

		Villager[] now = {first};
		List<String> before = new ArrayList<>();
		List<String> after = new ArrayList<>();
		helper.onEachTick(() -> {
			String said = line(helper, now[0]);
			if (said == null) {
				return;
			}
			List<String> log = now[0] == first ? before : after;
			String last = log.isEmpty() ? null : log.get(log.size() - 1);
			if (!said.equals(last)) {
				log.add(said);
			}
			if (now[0] == first && "seeing to the Millstone".equals(last) && !said.equals(last)) {
				// The Millstone is seen to and the round has moved on: the world is saved and loaded here.
				CompoundTag saved = new CompoundTag();
				first.save(saved);
				first.discard();
				Villager copy = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
				helper.assertTrue(copy != null, "the Mechanic didn't load");
				helper.assertTrue(level.addFreshEntity(copy), "the reloaded Mechanic couldn't be added to the level");
				now[0] = copy;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(before.contains("seeing to the Millstone"), "the Mechanic never went to the near Millstone first: " + before);
			String firstMachine = after.stream().filter(s -> s.startsWith("seeing to the ")).findFirst().orElse(null);
			helper.assertTrue(firstMachine != null, "the reloaded Mechanic hasn't gone to a machine yet (said " + after
				+ "; before the load " + before + "; reloaded alive " + now[0].isAlive() + ", in the level " + (level.getEntity(now[0].getUUID()) == now[0])
				+ ", Mechanic " + MechanicWork.isMechanic(now[0]) + ")");
			helper.assertTrue(firstMachine.equals("seeing to the Mechanical Press"), "the reloaded Mechanic started his round over: his first "
				+ "machine after the load was '" + firstMachine + "', not the Mechanical Press (before the load: " + before + ", after: " + after + ")");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
