package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;

/**
 * B1054: what a mob turns into in vanilla ({@code Mob.convertTo}, the call behind a husk drowning into a zombie, a zombie
 * into a drowned and a skeleton freezing into a stray) keeps the mod's tags, so a raider stays its raid's raider; a mob
 * the mod never touched converts as vanilla has it.
 */
public class ConvertedMobsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String STAMP = VillageRaids.RAID_STAMP + "10_64_-20_24000";

	/** A raider skeleton frozen into a stray is still that raid's raider: its raider tag, raid stamp and role go with it. */
	//$ gametest_batch AREA '"convertedMobs"'
	@GameTest(template = AREA, batch = "convertedMobs")
	public void aRaiderSkeletonThatFreezesIntoAStrayIsStillThatRaidsRaider(GameTestHelper helper) {
		Leftovers.clear(helper);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
		skeleton.setPersistenceRequired();
		skeleton.addTag(Threats.RAIDER_TAG);
		skeleton.addTag(STAMP);
		skeleton.addTag("aliveworkplace_role_archer");
		Stray stray = skeleton.convertTo(EntityType.STRAY, true);
		helper.assertTrue(stray != null && stray.isAlive(), "setup: the skeleton didn't turn into a stray");
		helper.assertTrue(stray.getTags().contains(Threats.RAIDER_TAG), "the stray lost the raider tag: " + stray.getTags());
		helper.assertTrue(stray.getTags().contains(STAMP), "the stray lost its raid's stamp (B1063): " + stray.getTags());
		helper.assertTrue(stray.getTags().contains("aliveworkplace_role_archer"), "the stray lost its role: " + stray.getTags());
		helper.assertTrue(Threats.isThreatMob(stray), "the stray is no threat");
		helper.assertTrue(!skeleton.isAlive(), "setup: the skeleton is still there");
		stray.discard();
		helper.succeed();
	}

	/** A bandit zombie that drowns is still a bandit, a lair's band still the lair's (every threat tag, THREAT_TAGS). */
	//$ gametest_batch AREA '"convertedMobs"'
	@GameTest(template = AREA, batch = "convertedMobs")
	public void aBanditZombieThatDrownsIsStillABandit(GameTestHelper helper) {
		Leftovers.clear(helper);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		zombie.setPersistenceRequired();
		zombie.addTag(Lairs.BANDIT_TAG);
		zombie.addTag(Lairs.TAG);
		Drowned drowned = zombie.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null, "setup: the zombie didn't turn into a drowned");
		helper.assertTrue(drowned.getTags().contains(Lairs.BANDIT_TAG) && drowned.getTags().contains(Lairs.TAG),
			"the drowned lost its band's tags: " + drowned.getTags());
		helper.assertTrue(Threats.isThreatMob(drowned), "the drowned is no threat");
		drowned.discard();
		helper.succeed();
	}

	/** A skeleton the mod never touched freezes into a plain stray: no tag of ours, and its other tags as vanilla has them. */
	//$ gametest_batch AREA '"convertedMobs"'
	@GameTest(template = AREA, batch = "convertedMobs")
	public void aMobTheModNeverTouchedConvertsAsInVanilla(GameTestHelper helper) {
		Leftovers.clear(helper);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
		skeleton.addTag("another_mods_tag");
		Stray stray = skeleton.convertTo(EntityType.STRAY, true);
		helper.assertTrue(stray != null, "setup: the skeleton didn't turn into a stray");
		helper.assertTrue(stray.getTags().isEmpty(), "a plain skeleton's stray got tags: " + stray.getTags());
		helper.assertTrue(!Threats.isThreatMob(stray), "a plain skeleton's stray is a threat");
		stray.discard();
		helper.succeed();
	}

	/**
	 * A fallen Warlord's band that zombifies ({@link Piglins#zombify}) is still the raid's but no lair's any more: the
	 * conversion carries every tag, and zombify keeps only its own list (32.10).
	 */
	//$ gametest_batch AREA '"convertedMobs"'
	@GameTest(template = AREA, batch = "convertedMobs")
	public void aZombifiedLairPiglinKeepsTheRaidButLeavesTheLair(GameTestHelper helper) {
		Leftovers.clear(helper);
		Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 2, 2));
		piglin.addTag(Threats.RAIDER_TAG);
		piglin.addTag(STAMP);
		piglin.addTag(Lairs.TAG);
		Mob turned = Piglins.zombify(helper.getLevel(), piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: the piglin didn't zombify");
		helper.assertTrue(turned.getTags().contains(Threats.RAIDER_TAG) && turned.getTags().contains(STAMP),
			"the zombified piglin left the raid: " + turned.getTags());
		helper.assertTrue(!turned.getTags().contains(Lairs.TAG), "the zombified piglin is still the lair's: " + turned.getTags());
		turned.discard();
		helper.succeed();
	}
}
