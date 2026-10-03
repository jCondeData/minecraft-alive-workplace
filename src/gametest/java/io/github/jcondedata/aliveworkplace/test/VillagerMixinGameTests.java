package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Schedule;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Two things {@code VillagerMixin} does that no test checked (found by the full check's inventory, ROADMAP 21.2): the
 * day plan each kind of villager gets, and that nobody can trade with a netherworker away in the Nether.
 */
public class VillagerMixinGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static Villager villager(GameTestHelper helper, BlockPos pos, VillagerProfession profession, boolean baby) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(2));
		if (baby) {
			v.setAge(-24000);
		}
		v.refreshBrain(helper.getLevel()); // what vanilla does when a villager takes a job
		return v;
	}

	private static String name(Schedule schedule) {
		return String.valueOf(net.minecraft.core.registries.BuiltInRegistries.SCHEDULE.getKey(schedule));
	}

	/**
	 * Workers keep the builders' working day, guards the guard shifts and bards the bard's evening; a baby with a job
	 * profession, and villagers in vanilla jobs, keep vanilla's day.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void eachKindOfVillagerGetsItsDayPlan(GameTestHelper helper) {
		Object[][] cases = {
			{ModVillagers.BUILDER, false, ModVillagers.BUILDER_SCHEDULE},
			{ModVillagers.NETHERWORKER, false, ModVillagers.BUILDER_SCHEDULE},
			{ModVillagers.GUARD, false, ModVillagers.GUARD_SCHEDULE},
			{ModVillagers.BARD, false, ModVillagers.BARD_SCHEDULE},
			{ModVillagers.BUILDER, true, Schedule.VILLAGER_BABY},
			{ModVillagers.GUARD, true, Schedule.VILLAGER_BABY},
			{VillagerProfession.LIBRARIAN, false, Schedule.VILLAGER_DEFAULT},
			{VillagerProfession.FARMER, false, Schedule.VILLAGER_DEFAULT}, // a farmer with no field of ours
		};
		for (int i = 0; i < cases.length; i++) {
			VillagerProfession profession = (VillagerProfession) cases[i][0];
			boolean baby = (Boolean) cases[i][1];
			Schedule expected = (Schedule) cases[i][2];
			Villager v = villager(helper, new BlockPos(2 + 2 * (i % 6), 2, 2 + 3 * (i / 6)), profession, baby);
			Schedule actual = v.getBrain().getSchedule();
			helper.assertTrue(actual == expected, (baby ? "a baby " : "a ") + profession + " should have the " + name(expected) + " day plan, not "
				+ name(actual));
		}
		helper.succeed();
	}

	/** A netherworker away in the Nether can't be traded with; back home, he trades again. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void nobodyTradesWithAnAwayNetherworker(GameTestHelper helper) {
		Villager worker = villager(helper, new BlockPos(5, 2, 5), ModVillagers.NETHERWORKER, false);
		helper.assertTrue(!worker.getOffers().isEmpty(), "the netherworker has nothing to trade, so the test proves nothing");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.moveTo(worker.getX() + 1, worker.getY(), worker.getZ());
		long now = helper.getLevel().getGameTime();
		ModAttachments.NETHER_TRIP.set(worker, new Netherworkers.Trip(now, now + 6000, worker.blockPosition(), 0));
		helper.assertTrue(Netherworkers.isAway(worker), "the netherworker isn't away");

		InteractionResult away = worker.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(away == InteractionResult.PASS, "clicking an away netherworker should do nothing, not " + away);
		helper.assertTrue(worker.getTradingPlayer() == null, "an away netherworker started trading");

		ModAttachments.NETHER_TRIP.remove(worker);
		InteractionResult home = worker.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(home != InteractionResult.PASS && worker.getTradingPlayer() == player, "back home, the netherworker should trade: " + home);
		worker.setTradingPlayer(null);
		player.closeContainer();
		helper.succeed();
	}
}
