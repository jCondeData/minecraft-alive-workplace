package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-08): a mutation check of {@code threat/SiegeReport} and {@code threat/ThreatDamage}
 * (lane-d-1108-0407, 12 mutants each). These kill the survivors that a player would notice, written from the spec
 * (ROADMAP 32.6, the morning after; 32.16a, what threats may damage).
 */
public class QaSiegeDamage1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(10, 2, 10);

	/** A guard (a knight with his post) at (x, 2, z), standing still. */
	private static Villager guard(GameTestHelper helper, int x, int z, String name) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** A still husk at (x, 2, z), one of a raid's. */
	private static Mob raider(GameTestHelper helper, int x, int z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(x, 2, z));
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.addTag(Threats.RAIDER_TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		Leftovers.after(helper, mob::discard);
		return mob;
	}

	/**
	 * 32.6 / ThreatData's save: a siege's count of the dead ("how many fell and to whom", the hero) is saved data, so a
	 * guard's kill must reach the save even when nothing else changed in the threat data that tick: the data is marked to
	 * be written. Without it a server stopped mid-siege (or a level saved by autosave) loses the kills, and the morning's
	 * report names no hero.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSiegeKillSaved1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSiegeKillSaved1108d")
	public void aGuardsKillInASiegeMarksTheThreatDataForSaving(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData data = ThreatData.get(level);
		data.forgetSiege(hall);
		Leftovers.after(helper, () -> data.forgetSiege(hall));
		Villager brannoc = guard(helper, 4, 4, "Brannoc");
		Mob husk = raider(helper, 6, 6);
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = 1;
		data.beginSiege(siege);
		data.setDirty(false);
		husk.hurt(level.damageSources().mobAttack(brannoc), 1000f);
		helper.assertTrue(husk.isDeadOrDying(), "setup: the raider didn't die");
		helper.assertTrue(siege.toGuards == 1 && siege.kills.getOrDefault(brannoc.getUUID(), 0) == 1, "setup: the kill wasn't counted: " + siege.kills);
		helper.assertTrue(data.isDirty(), "a guard's kill in a siege was counted but the threat data isn't marked to be saved: a restart loses it");
		helper.succeed();
	}

	/** An arrow shot by {@code owner}, at the block {@code pos} (helper coordinates), not added to the level. */
	private static Arrow arrow(GameTestHelper helper, LivingEntity owner, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(owner);
		arrow.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		return arrow;
	}

	/**
	 * 32.16a ("nothing else a threat does breaks a builder's block"; a threat is "a raider or what one shot"): when a block
	 * breaks under a projectile the game calls {@code destroyBlock} with the projectile as the breaker (a decorated pot or
	 * a chorus flower an arrow hits). A raider pillager's arrow never takes the log of a finished build; the same arrow
	 * takes a player's own log beside it (the game's rules), and a wild skeleton's arrow takes the builders' log (so the
	 * test sees the rule).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaRaiderArrow1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRaiderArrow1108d")
	public void aRaidersArrowNeverBreaksABuildersBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos log = new BlockPos(6, 2, 6);
		BlockPos playersLog = new BlockPos(10, 2, 6);
		helper.setBlock(log, Blocks.OAK_LOG);
		helper.setBlock(playersLog, Blocks.OAK_LOG);
		finished(helper, log, "qa_arrow_log_1108d_");
		Pillager raider = EntityType.PILLAGER.create(level);
		helper.assertTrue(raider != null, "setup: no pillager");
		raider.addTag(Threats.RAIDER_TAG);
		helper.assertFalse(level.destroyBlock(helper.absolutePos(log), true, arrow(helper, raider, log)), "a raider's arrow broke the builders' log");
		helper.assertBlockPresent(Blocks.OAK_LOG, log);
		helper.assertTrue(level.destroyBlock(helper.absolutePos(playersLog), false, arrow(helper, raider, playersLog)), "a raider's arrow couldn't break a player's own log (the game's rules)");
		Skeleton wild = EntityType.SKELETON.create(level);
		helper.assertTrue(wild != null, "setup: no skeleton");
		helper.assertTrue(level.destroyBlock(helper.absolutePos(log), false, arrow(helper, wild, log)), "a wild skeleton's arrow couldn't break the log either: the test can't see the rule");
		helper.succeed();
	}

	/** Records the one block at {@code pos} (helper coordinates) as a build the builders finished, forgotten when the test ends. */
	static void finished(GameTestHelper helper, BlockPos pos, String name) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(pos), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(pos), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageFire.forgetBuilds();
		});
		VillageFire.forgetBuilds();
	}
}
