package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (lane-e-1109-0115, saves): a villager a zombie turns, then cured. The guide's graves page: "A villager a zombie turns
 * into a zombie villager leaves no grave: cure them the vanilla way instead", and the Legends page: "cure them to have
 * them back". Vanilla's cure gives back the same villager (name, job, level, trades, gossip), so what the mod saved on
 * them should come back too. These are the controls that pass: vanilla's part, and what the mod already carries over (a
 * Legend's place is {@code LegendSlotsGameTests}; a strange mood). What's lost is in {@link QaCuredVillager1109eBugTests}.
 */
public class QaCuredVillager1109eGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";

	/** A named Journeyman farmer, a zombie's victim on Hard, then cured: the same name, job and level come back. */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109eVanilla"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109eVanilla")
	public void aCuredFarmerKeepsHisNameJobAndLevel(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = farmer(helper, new BlockPos(2, 2, 2), "Ada");
		turnAndCure(helper, ada, cured -> {
			helper.assertTrue(cured.getVillagerData().getProfession() == VillagerProfession.FARMER, "cured, not a farmer: " + cured.getVillagerData());
			helper.assertTrue(cured.getVillagerData().getLevel() == 3, "cured, level " + cured.getVillagerData().getLevel() + " not 3");
			helper.assertTrue(cured.hasCustomName() && cured.getCustomName().getString().equals("Ada"), "cured, named " + cured.getCustomName());
			helper.succeed();
		});
	}

	// ---- helpers, shared with the bug tests ----------------------------------------------------------------------

	static Villager farmer(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setPersistenceRequired();
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(3));
		v.setVillagerXp(70);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** Friendship points a player has with a villager, set as a gift-giver would have earned them. */
	static void befriend(Villager v, UUID player, int points) {
		Friendship.Bond bond = new Friendship.Bond("Steve", points, -1, -1, 0, Map.of(), -1, List.of(), false, -1, -1);
		ModAttachments.FRIENDSHIP.set(v, new Friendship.Data(Map.of(player, bond)));
	}

	static final ResourceLocation BURGHER = AliveWorkplace.id("burgher");

	/**
	 * {@code victim} killed by a zombie on Hard (vanilla turns every such villager), then cured as a golden apple does,
	 * the wait cut to a tick; {@code then} gets the villager who comes back.
	 */
	static void turnAndCure(GameTestHelper helper, Villager victim, java.util.function.Consumer<Villager> then) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
		helper.runAfterDelay(2, () -> {
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
			zombie.setNoAi(true);
			zombie.setCanPickUpLoot(false);
			victim.hurt(level.damageSources().mobAttack(zombie), 1000f);
			zombie.discard();
			List<ZombieVillager> turned = level.getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2), z -> z.isAlive());
			helper.assertTrue(turned.size() == 1, "setup: the villager didn't become one zombie villager: " + turned.size());
			ZombieVillager zv = turned.get(0);
			zv.setNoAi(true);
			try {
				var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
				start.setAccessible(true);
				start.invoke(zv, null, 1);
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("can't start the cure: " + e);
			}
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zv.isRemoved(), "setup: the zombie villager wasn't cured");
				List<Villager> back = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
					v -> v.isAlive() && v.hasCustomName() && v.getCustomName().getString().equals(victim.getCustomName().getString()));
				helper.assertTrue(back.size() == 1, "setup: cured villagers named " + victim.getCustomName().getString() + ": " + back.size());
				then.accept(back.get(0));
			});
		});
	}

	/** Marries {@code a} and {@code b} the way a wedding records it (each other's partner, married). */
	static void marry(Villager a, Villager b, long day) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), day, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), day, true));
	}
}
