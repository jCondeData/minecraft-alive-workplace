package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * B120: a builder died while building a Palisade Tower in the walls scene. Builders hop onto the build's upper rows and
 * walked, jumped or were shoved off them: on a tower standing on another (as the walls scene once laid one out, 10
 * blocks up) a crew of three fell 8 to 12 blocks several times each, 8 to 9 health a fall. Builders build the tower
 * (alone, as a crew of three, and as a crew on top of another tower) and must finish it alive and unhurt: every hurt is
 * reported with its cause and place. A builder at work takes no fall damage; one without a build still does.
 */
public class B120GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(12, 2, 12);
	private static final BlockPos[] BENCHES = {new BlockPos(3, 2, 3), new BlockPos(3, 2, 8), new BlockPos(3, 2, 13)};

	//$ gametest_ticks_batch HUGE '12000' '"b120PalisadeTowerAlone"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b120PalisadeTowerAlone")
	public void b120ABuilderBuildsThePalisadeTowerUnhurt(GameTestHelper helper) {
		build(helper, 1);
	}

	//$ gametest_ticks_batch HUGE '12000' '"b120PalisadeTowerCrew"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b120PalisadeTowerCrew")
	public void b120ACrewBuildsThePalisadeTowerUnhurt(GameTestHelper helper) {
		build(helper, 3);
	}

	//$ gametest_ticks_batch HUGE '12000' '"b120PalisadeTowerStacked"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b120PalisadeTowerStacked")
	public void b120ACrewBuildsAPalisadeTowerOnTopOfAnotherUnhurt(GameTestHelper helper) {
		build(helper, 3, true);
	}

	/** Only a builder at work is spared the fall: the same villager without a build still takes it. */
	//$ gametest_ticks_batch HUGE '100' '"b120FallOnlyAtWork"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b120FallOnlyAtWork")
	public void b120OnlyABuilderAtWorkIsSparedFallDamage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCHES[0], ModBlocks.BUILDERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, BENCHES[0].east());
		Builders.employ(level, v, helper.absolutePos(BENCHES[0]));
		float full = v.getHealth();
		io.github.jcondedata.aliveworkplace.mc.Damage.hurt(v, level.damageSources().fall(), 4);
		helper.assertTrue(v.getHealth() < full, "a builder with no build took no fall damage");
		v.setHealth(full);
		v.invulnerableTime = 0;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		helper.assertTrue(Builders.start(level, v, null, StarterBlueprints.PALISADE_TOWER.id(), placement) != null, "the Palisade Tower didn't start");
		io.github.jcondedata.aliveworkplace.mc.Damage.hurt(v, level.damageSources().fall(), 4);
		helper.assertTrue(v.getHealth() >= full, "a builder at work took fall damage: " + v.getHealth() + " of " + full);
		v.invulnerableTime = 0;
		io.github.jcondedata.aliveworkplace.mc.Damage.hurt(v, level.damageSources().generic(), 4);
		helper.assertTrue(v.getHealth() < full, "a builder at work took no other damage either");
		helper.succeed();
	}

	private static void build(GameTestHelper helper, int crewSize) {
		build(helper, crewSize, false);
	}

	private static void build(GameTestHelper helper, int crewSize, boolean stacked) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(crewSize > 1, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		List<Villager> crew = new ArrayList<>();
		for (int i = 0; i < crewSize; i++) {
			helper.setBlock(BENCHES[i], ModBlocks.BUILDERS_BENCH);
			Villager v = helper.spawn(EntityType.VILLAGER, BENCHES[i].east());
			Builders.employ(level, v, helper.absolutePos(BENCHES[i]));
			crew.add(v);
		}
		if (stacked) {
			var tower = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, StarterBlueprints.PALISADE_TOWER.id()).orElseThrow();
			for (var e : tower.blocks()) {
				level.setBlock(helper.absolutePos(ORIGIN.below()).offset(e.pos()), e.state(), 2);
			}
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(stacked ? ORIGIN.above(9) : ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, crew.get(0), null, StarterBlueprints.PALISADE_TOWER.id(), placement);
		if (site == null) {
			throw new GameTestAssertException("the Palisade Tower didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the Palisade Tower");
		}
		List<String> hurts = new ArrayList<>();
		float[] health = new float[crewSize];
		for (int i = 0; i < crewSize; i++) {
			health[i] = crew.get(i).getHealth();
		}
		helper.onEachTick(() -> {
			for (int i = 0; i < crewSize; i++) {
				Villager v = crew.get(i);
				if (v.getHealth() < health[i] || (!v.isAlive() && health[i] > 0)) {
					DamageSource source = v.getLastDamageSource();
					hurts.add("builder " + i + " lost " + (health[i] - v.getHealth()) + " health to "
						+ (source == null ? "?" : source.getMsgId()) + " at blueprint " + v.blockPosition().subtract(placement.origin()).toShortString()
						+ " (tick " + helper.getTick() + ", stage " + site.stage() + ", " + level.getBlockState(v.blockPosition()) + ")");
				}
				health[i] = v.isAlive() ? v.getHealth() : 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hurts.isEmpty(), "a builder was hurt building the Palisade Tower: " + String.join("; ", hurts));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"the Palisade Tower isn't finished: stage=" + site.stage() + " progress=" + Math.round(site.progress(plan) * 100) + "%");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the Palisade Tower wrong, e.g. "
				+ unfinished.stream().limit(30).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p).getBlock()).toList() + " skipped=" + site.skipped());
			for (Villager v : crew) {
				helper.assertTrue(v.isAlive() && v.getHealth() >= v.getMaxHealth(), "a builder ended at " + v.getHealth() + " health");
			}
		});
	}
}
