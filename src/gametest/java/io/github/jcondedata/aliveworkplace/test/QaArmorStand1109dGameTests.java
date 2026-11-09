package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane D, 2026-11-09, B1080 from its spec and docs/wiki/sieges.md: "a raider's blast or shot never breaks an armor
 * stand standing on or in a builder's block; a threat's explosion passes it by, so it isn't pushed off its spot; sparing
 * keeps whatever a player put on it; players' own hits and anything else's damage break it as before".
 */
public class QaArmorStand1109dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The hut's corner; its floor is one below. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos STAND = HUT.offset(3, 0, 3);

	/** A 5x5 log hut on a plank floor, recorded as a finished build of the builders when {@code builders}. */
	private static void hut(GameTestHelper helper, boolean builders, boolean carpetOnly) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				// carpetOnly: the player's own floor, of obsidian so the blast can't take it from under the builders' carpet
				helper.setBlock(HUT.offset(x, -1, z), carpetOnly ? Blocks.OBSIDIAN : Blocks.OAK_PLANKS);
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		if (carpetOnly) {
			helper.setBlock(STAND, Blocks.RED_CARPET);
		}
		if (builders) {
			// carpetOnly: the blueprint starts at the hut's own level, so the floor under the carpet is the player's
			BlockPos from = carpetOnly ? HUT : HUT.below();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_stand_1109d_" + Long.toHexString(level.getGameTime()));
			level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(from), new Vec3i(5, carpetOnly ? 3 : 4, 5), false, Blocks.STRUCTURE_VOID);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(from), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		}
		VillageFire.forgetBuilds();
	}

	private static ArmorStand stand(GameTestHelper helper, boolean onCarpet) {
		BlockPos at = helper.absolutePos(STAND);
		ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
		helper.assertTrue(stand != null, "setup: no armor stand");
		stand.moveTo(at.getX() + 0.5, at.getY() + (onCarpet ? 0.0625 : 0.0), at.getZ() + 0.5, 0f, 0f);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		helper.getLevel().addFreshEntity(stand);
		return stand;
	}

	/** A creeper (a raider's when {@code raider}) blows up two blocks from the stand, inside the hut. */
	private static void blast(GameTestHelper helper, boolean raider) {
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		BlockPos at = helper.absolutePos(HUT.offset(1, 0, 2));
		Creeper creeper = EntityType.CREEPER.create(level);
		helper.assertTrue(creeper != null, "setup: no creeper");
		if (raider) {
			creeper.addTag(Threats.RAIDER_TAG);
		}
		creeper.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.explode(creeper, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
	}

	private static void standsWithItsArmor(GameTestHelper helper, ArmorStand stand, Vec3 was, String what) {
		helper.assertTrue(stand.isAlive(), what + ": the armor stand was broken");
		helper.assertTrue(stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET) && stand.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
			what + ": the stand lost the armor a player put on it (head " + stand.getItemBySlot(EquipmentSlot.HEAD) + ", chest " + stand.getItemBySlot(EquipmentSlot.CHEST) + ")");
		helper.assertTrue(stand.position().distanceTo(was) < 0.05, what + ": the stand was pushed off its spot, from " + was + " to " + stand.position());
	}

	/** A raider's creeper blast beside it: the builders' stand stays where it stood, with its helmet and chestplate. */
	//$ gametest_batch AREA '"qaArmorStand1109dBlast"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dBlast")
	public void aRaidersBlastLeavesTheBuildersStandOnItsSpotWithItsArmor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		Vec3 was = stand.position();
		blast(helper, true);
		helper.runAfterDelay(10, () -> {
			standsWithItsArmor(helper, stand, was, "a raider's blast in the builders' finished hut");
			helper.succeed();
		});
	}

	/** "On or in": a stand on a carpet the builders laid (the floor under it is the player's) is spared too. */
	//$ gametest_batch AREA '"qaArmorStand1109dCarpet"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dCarpet")
	public void aRaidersBlastLeavesAStandOnTheBuildersCarpet(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, true);
		ArmorStand stand = stand(helper, true);
		helper.assertTrue(stand.blockPosition().equals(helper.absolutePos(STAND)), "setup: the stand doesn't stand in the carpet's block (" + stand.blockPosition() + ")");
		Vec3 was = stand.position();
		blast(helper, true);
		helper.runAfterDelay(10, () -> {
			standsWithItsArmor(helper, stand, was, "a raider's blast by a stand on the builders' carpet");
			helper.succeed();
		});
	}

	/** A build still under way is spared too: a stand on the floor of a site not yet finished. */
	//$ gametest_batch AREA '"qaArmorStand1109dSite"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dSite")
	public void aRaidersBlastLeavesAStandInABuildUnderWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		hut(helper, false, false);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_standsite_1109d_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT.below()), new Vec3i(5, 4, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT.below()), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		VillageFire.forgetBuilds();
		BuildSite site = manager.create(UUID.randomUUID(), "", id, placement);
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			VillageFire.forgetBuilds();
		});
		ArmorStand stand = stand(helper, false);
		Vec3 was = stand.position();
		blast(helper, true);
		helper.runAfterDelay(10, () -> {
			standsWithItsArmor(helper, stand, was, "a raider's blast in a build still under way");
			helper.succeed();
		});
	}

	/** Control: the same raider's blast breaks a stand in the same hut when it is no build of the builders'. */
	//$ gametest_batch AREA '"qaArmorStand1109dNoBuild"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dNoBuild")
	public void aRaidersBlastStillBreaksAStandThatIsNoBuilds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, false, false);
		ArmorStand stand = stand(helper, false);
		blast(helper, true);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!stand.isAlive(), "a raider's blast left a stand that stands on no build of the builders' (only theirs is spared)");
			helper.succeed();
		});
	}

	/** Only threats are refused: a wild creeper's blast in the builders' hut breaks their stand as in the game. */
	//$ gametest_batch AREA '"qaArmorStand1109dWild"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dWild")
	public void aWildCreepersBlastStillBreaksTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		blast(helper, false);
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!stand.isAlive(), "a wild creeper's blast (no raider) left the builders' armor stand: only threats are refused");
			helper.succeed();
		});
	}

	/** A live pillager's arrow (a raider's) flies into the builders' stand: it stays, with its armor. */
	//$ gametest_ticks_batch AREA '100' '"qaArmorStand1109dArrow"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArmorStand1109dArrow")
	public void aRaidersArrowLeavesTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		Vec3 was = stand.position();
		shoot(helper, true);
		helper.runAfterDelay(30, () -> {
			standsWithItsArmor(helper, stand, was, "a raider's arrow into the builders' stand");
			helper.succeed();
		});
	}

	/** Control: the same arrow from a pillager who is no raider breaks the builders' stand, as an arrow does in the game. */
	//$ gametest_ticks_batch AREA '100' '"qaArmorStand1109dArrowWild"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArmorStand1109dArrowWild")
	public void aWildPillagersArrowStillBreaksTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		shoot(helper, false);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!stand.isAlive(), "a wild pillager's arrow left the builders' stand (or missed it)");
			helper.succeed();
		});
	}

	/** An arrow from a pillager (a raider when {@code raider}) flying east into the stand's chest, without gravity. */
	private static Arrow shoot(GameTestHelper helper, boolean raider) {
		ServerLevel level = helper.getLevel();
		Pillager pillager = helper.spawn(EntityType.PILLAGER, HUT.offset(1, 0, 3));
		pillager.setNoAi(true);
		if (raider) {
			pillager.addTag(Threats.RAIDER_TAG);
		}
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(pillager);
		BlockPos at = helper.absolutePos(STAND);
		arrow.moveTo(at.getX() - 0.5, at.getY() + 1.2, at.getZ() + 0.5, -90f, 0f);
		arrow.setNoGravity(true);
		arrow.setDeltaMovement(1.5, 0, 0);
		level.addFreshEntity(arrow);
		return arrow;
	}

	/**
	 * "A raider's blast (a creeper, a ghast's fireball, TNT it lit)": TNT a raider lit goes off beside the builders' stand.
	 * The blast's direct source is the TNT, only its igniter is a raider; the stand stays, unpushed, with its armor.
	 */
	//$ gametest_batch AREA '"qaArmorStand1109dTnt"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dTnt")
	public void tntARaiderLitLeavesTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer()));
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		Vec3 was = stand.position();
		Pillager raider = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 2, 2));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		BlockPos at = helper.absolutePos(HUT.offset(1, 0, 2));
		PrimedTnt tnt = new PrimedTnt(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, raider);
		tnt.setFuse(1);
		level.addFreshEntity(tnt);
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(tnt.isRemoved(), "setup: the TNT hasn't gone off");
			standsWithItsArmor(helper, stand, was, "TNT a raider lit, beside the builders' stand");
			helper.succeed();
		});
	}

	/**
	 * "A raider's shot stays a raider's" (B1066): the arrow of a pillager a guard killed while it flew still leaves the
	 * builders' stand standing, though the damage has no causing entity any more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaArmorStand1109dKilled"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaArmorStand1109dKilled")
	public void aKilledRaidersArrowLeavesTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		Vec3 was = stand.position();
		Arrow arrow = shoot(helper, true);
		Entity shooter = arrow.getOwner();
		helper.assertTrue(shooter != null, "setup: the arrow has no shooter");
		shooter.kill();
		shooter.discard();
		helper.assertTrue(arrow.getOwner() == null, "setup: the killed raider still reads as the arrow's owner");
		helper.runAfterDelay(30, () -> {
			standsWithItsArmor(helper, stand, was, "a killed raider's arrow into the builders' stand");
			helper.succeed();
		});
	}

	/** A player's own two punches still break the builders' stand (one a player takes away stays away). */
	//$ gametest_batch AREA '"qaArmorStand1109dPlayer"'
	@GameTest(template = AREA, batch = "qaArmorStand1109dPlayer")
	public void aPlayersPunchesStillBreakTheBuildersStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		hut(helper, true, false);
		ArmorStand stand = stand(helper, false);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		stand.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
		stand.hurt(helper.getLevel().damageSources().playerAttack(player), 1f);
		helper.assertTrue(!stand.isAlive(), "a player's two quick punches didn't break the builders' armor stand");
		helper.succeed();
	}
}
