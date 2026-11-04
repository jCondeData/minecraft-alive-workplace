package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1104-0938), builders mid-job: the builder is not killed but turned into something else, the two ways
 * vanilla turns a villager. builder.md: "The builder died: drop its bag and the blueprint where it fell, keep what was
 * built" (and CLAUDE.md: "the villager dead or unloaded" are the ways a job breaks). A builder a zombie kills on Hard
 * becomes a zombie villager (it dies first, then is converted); one struck by lightning becomes a witch (no death at
 * all). Either way the villager who carried the bag is gone: what was in his bag must not vanish with him, and the build
 * must not stay with a builder who no longer exists. The struck case is a bug (B928, QaBuilderConverted1104BugTests on tests/builders-1104).
 */
public class QaBuilderConverted1104GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-test");
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** A bite on Hard mid-STRUCTURE, his bag full: he becomes a zombie villager; the bag and the blueprint fall where he was. */
	//$ gametest_ticks_batch HUGE '6000' '"qaConvertedZombie"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaConvertedZombie")
	public void aBuilderAZombieTurnsMidJobDropsHisBagAndTheBlueprint(GameTestHelper helper) {
		convertedMidJob(helper, Difficulty.HARD, (level, builder) -> {
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(12, 2, 12));
			zombie.setNoAi(true);
			zombie.setCanPickUpLoot(false);
			builder.hurt(level.damageSources().mobAttack(zombie), 1000f);
			zombie.discard();
		}, ZombieVillager.class);
	}

	/**
	 * Mid-STRUCTURE with materials in his bag, the builder is turned into {@code into}; 20 ticks later every material is
	 * still in the chests or on the ground (none vanished with the villager), and the site is either closed with its
	 * blueprint lying there, or open with nobody on it, never left to the villager who is gone.
	 */
	static void convertedMidJob(GameTestHelper helper, Difficulty difficulty, java.util.function.BiConsumer<ServerLevel, Villager> turn,
			Class<? extends net.minecraft.world.entity.Mob> into) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(difficulty, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		Map<Item, Integer> before = new HashMap<>();
		String[] at = {null};
		long[] turnedAt = {-1};
		int[] atStart = {-1};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (turnedAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int unbuilt = r.plan().unfinished(level).size();
			if (atStart[0] < 0) {
				atStart[0] = unbuilt;
			}
			Villager v = r.builder()[0];
			if (atStart[0] - unbuilt < 4 || bag(v, r.plan()).isEmpty()) {
				return;
			}
			for (Item item : r.plan().materials().keySet()) {
				before.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item));
			}
			at[0] = QaBuilderTerrain1102GameTests.state(helper, r) + ", bag " + bag(v, r.plan());
			turn.accept(level, v);
			turnedAt[0] = helper.getTick();
			// what he turned into never picks up what fell (a zombie villager may pick up blocks)
			for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(into, helper.getBounds().inflate(16))) {
				m.setCanPickUpLoot(false);
				m.setNoAi(true);
			}
			LOGGER.info("qa converted: the builder turned into a {} with {}", into.getSimpleName(), at[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(turnedAt[0] >= 0 && helper.getTick() - turnedAt[0] >= 20, "the builder isn't turned yet (or just now)");
			helper.assertTrue(!level.getEntitiesOfClass(into, helper.getBounds().inflate(16)).isEmpty(),
				"setup: the builder didn't turn into a " + into.getSimpleName() + " (still a villager: " + r.builder()[0].isAlive() + ")");
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : before.entrySet()) {
				int now = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
				if (now != e.getValue()) {
					off.put(e.getKey(), e.getValue() + " -> " + now);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials lost when the builder turned into a " + into.getSimpleName()
				+ " (chests, bags and ground, before -> 20 ticks after): " + off + "; turned with " + at[0]);
			BuildSite s = r.site();
			int blueprints = 0;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				blueprints += e.getItem().is(ModItems.BLUEPRINT) ? e.getItem().getCount() : 0;
			}
			Entity builder = s == null || s.builder() == null ? null : level.getEntity(s.builder());
			helper.assertTrue(s == null ? blueprints == 1 : s.builder() == null || builder instanceof Villager v && v.isAlive(),
				"after the builder turned into a " + into.getSimpleName() + ": " + (s == null ? "the site is closed but " + blueprints
					+ " blueprint(s) lie on the ground, not 1" : "the site is still the gone builder's (" + s.builder() + " is "
					+ (builder == null ? "nobody" : builder.getType().toShortString()) + "), stage " + s.stage() + ", status " + s.status()));
			LOGGER.info("qa converted: {} checked: site {}, {} blueprint(s) on the ground", into.getSimpleName(), s == null ? "closed" : "open", blueprints);
		});
	}

	/** What the builder carries of the hut's materials (empty for none). */
	static Map<Item, Integer> bag(Villager v, BuildPlan plan) {
		Map<Item, Integer> out = new HashMap<>();
		for (Item item : plan.materials().keySet()) {
			int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			if (n > 0) {
				out.put(item, n);
			}
		}
		return out;
	}
}
