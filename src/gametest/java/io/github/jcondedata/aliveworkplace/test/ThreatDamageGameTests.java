package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * What threats may damage (ROADMAP 32.16a, the owner's rule of 2026-10-12), on a small log hut the builders "finished"
 * (a blueprint taken from the world and recorded as a finished build): a raider's blast leaves every block of the hut
 * standing but takes a player's planks beside it (and the same blast from a creeper that isn't a raider does break the
 * hut, so the test would see it); a raider's {@code destroyBlock} (a ravager trampling) never takes a builder's block,
 * nor one of a build still under way, while a wild ravager's does; a raider never breaks down a builder's door; what a
 * village fire burned is put back by the builder with {@code builderRepairs} off, and the list of it survives a save
 * and load; with the threat repairs off too nothing is repaired; and every changed sentence has its text.
 * (The ram breaking only the gate, and its repair, is in {@code SiegeGameTests}.)
 */
public class ThreatDamageGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The hut's corner: logs at its four corners (y 2 and 3), a 5 x 5 roof of logs at y 4. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private record Hut(ServerLevel level, BlueprintData.Placement placement, ResourceLocation id) {
	}

	/** The log hut, finished by the builders for {@code owner}; every other test's leftovers gone. */
	private static Hut hut(GameTestHelper helper, String name, UUID owner) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		boolean repairs = Upkeep.ENABLED;
		boolean threats = Upkeep.THREATS;
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			Upkeep.ENABLED = repairs;
			Upkeep.THREATS = threats;
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : data.damaged()) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				if (corner) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "threat_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return new Hut(level, placement, id);
	}

	/** Every log of the hut (helper positions). */
	private static List<BlockPos> logs() {
		List<BlockPos> out = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					out.add(HUT.offset(x, 0, z));
					out.add(HUT.offset(x, 1, z));
				}
				out.add(HUT.offset(x, 2, z));
			}
		}
		return out;
	}

	private static long standing(GameTestHelper helper) {
		return logs().stream().filter(p -> helper.getBlockState(p).is(Blocks.OAK_LOG)).count();
	}

	/**
	 * A raider's creeper blows up beside the hut's roof: every log stands, the player's planks on the other side of the
	 * blast are gone. The same blast from a wild creeper takes some of the roof (the test would see a broken rule).
	 */
	//$ gametest_ticks_batch AREA '100' '"threatBlast"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatBlast")
	public void aRaidersBlastSparesTheBuildersBlocks(GameTestHelper helper) {
		Hut hut = hut(helper, "blast", UUID.randomUUID());
		ServerLevel level = hut.level();
		BlockPos players = new BlockPos(4, 4, 8);
		helper.setBlock(players, Blocks.OAK_PLANKS);
		BlockPos at = helper.absolutePos(new BlockPos(5, 4, 8));
		helper.runAfterDelay(2, () -> {
			Creeper raider = EntityType.CREEPER.create(level);
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			helper.assertTrue(standing(helper) == logs().size(), "the raider's blast took " + (logs().size() - standing(helper)) + " of the hut's logs");
			helper.assertBlockNotPresent(Blocks.OAK_PLANKS, players);
			Creeper wild = EntityType.CREEPER.create(level);
			wild.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(wild, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			helper.assertTrue(standing(helper) < logs().size(), "a wild creeper's blast took nothing: the test can't see the rule");
			helper.succeed();
		});
	}

	/**
	 * A raider's ravager tramples (the game's {@code destroyBlock} with the ravager as breaker): no builder's block goes,
	 * nor one of a build still under way; a wild ravager's does, and so does a raider's on a player's block.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatTrample"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatTrample")
	public void aRaiderNeverBreaksABuildersBlock(GameTestHelper helper) {
		Hut hut = hut(helper, "trample", UUID.randomUUID());
		ServerLevel level = hut.level();
		BuildSiteManager manager = BuildSiteManager.get(level);
		helper.runAfterDelay(2, () -> {
			Ravager raider = EntityType.RAVAGER.create(level);
			raider.addTag(Threats.RAIDER_TAG);
			BlockPos roof = helper.absolutePos(HUT.offset(2, 2, 2));
			BlockPos wall = helper.absolutePos(HUT.offset(0, 1, 0));
			helper.assertFalse(level.destroyBlock(roof, true, raider), "the raider broke the hut's roof");
			helper.assertFalse(level.destroyBlock(wall, true, raider), "the raider broke the hut's wall");
			helper.assertTrue(standing(helper) == logs().size(), "a log is gone");
			// A player's block beside it: the game's rules, as before.
			BlockPos players = new BlockPos(3, 2, 3);
			helper.setBlock(players, Blocks.OAK_LEAVES);
			helper.assertTrue(level.destroyBlock(helper.absolutePos(players), true, raider), "the raider couldn't break a player's leaves");
			// The hut as a build under way, no longer finished: still the builders'.
			manager.forgetFinished(hut.placement());
			VillageFire.forgetBuilds();
			BuildSite site = manager.create(UUID.randomUUID(), "", hut.id(), hut.placement());
			try {
				helper.assertFalse(level.destroyBlock(roof, true, raider), "the raider broke a block of a build under way");
				Ravager wild = EntityType.RAVAGER.create(level);
				helper.assertTrue(level.destroyBlock(roof, false, wild), "a wild ravager couldn't break it: the test can't see the rule");
			} finally {
				manager.remove(site.id());
			}
			helper.succeed();
		});
	}

	/** A raider never breaks down a builder's door; a vindicator that isn't a raider may (vanilla's rule). */
	//$ gametest_ticks_batch AREA '100' '"threatDoor"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatDoor")
	public void aRaiderNeverBreaksABuildersDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(6, 2, 6);
		helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "threat_door_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(door), new Vec3i(1, 2, 1), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(door), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		Vindicator raider = EntityType.VINDICATOR.create(level);
		raider.addTag(Threats.RAIDER_TAG);
		Vindicator wild = EntityType.VINDICATOR.create(level);
		helper.assertTrue(ThreatDamage.spares(level, helper.absolutePos(door), raider), "a raider may break the builder's door");
		helper.assertFalse(ThreatDamage.spares(level, helper.absolutePos(door), wild), "a wild vindicator is held to the raiders' rule");
		helper.assertFalse(ThreatDamage.spares(level, helper.absolutePos(new BlockPos(2, 2, 2)), raider), "a player's block is spared");
		helper.succeed();
	}

	/**
	 * Fire burned two logs of the roof (as the game's fire does, counted by the fire's mixin). With {@code builderRepairs}
	 * off the builder still puts them back: they're on the saved list, which survives a save and load, and come off it
	 * once they're back.
	 */
	//$ gametest_ticks_batch AREA '1200' '"threatFireRepair"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "threatFireRepair")
	public void whatAFireBurnedIsPutBackWithRepairsOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Hut hut = hut(helper, "fire", UUID.randomUUID());
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		BuildSiteManager.get(level).recordFinished(hut.id(), hut.placement(), builder.getUUID()); // the builder's to look after
		Upkeep.ENABLED = false; // builderRepairs off
		Upkeep.THREATS = true; // as in a player's game
		BlockPos chestPos = new BlockPos(1, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 8));
		BlockPos bench = new BlockPos(1, 2, 1);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Builders.employ(level, builder, helper.absolutePos(bench));
		List<BlockPos> burned = List.of(HUT.offset(1, 2, 2), HUT.offset(2, 2, 2));
		helper.runAfterDelay(2, () -> {
			for (BlockPos p : burned) {
				BlockPos at = helper.absolutePos(p);
				level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at)); // the fire took the log
				VillageFire.burned(level, at); // (what the fire's mixin says when it burns a builder's block)
			}
		});
		helper.runAfterDelay(10, () -> {
			for (BlockPos p : burned) {
				VillageFire.douse(level, helper.absolutePos(p), 0, null);
			}
			for (BlockPos p : burned) {
				helper.assertTrue(ThreatData.get(level).damaged().contains(helper.absolutePos(p)), "the burned log isn't on the list: " + p);
			}
			ThreatData.get(level).roundTrip(level.registryAccess()); // a save and load
			for (BlockPos p : burned) {
				helper.assertTrue(ThreatData.get(level).damaged().contains(helper.absolutePos(p)), "the list was lost in the load: " + p);
			}
		});
		helper.runAfterDelay(20, () -> {
			BuildSite site = Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair of what burned with builderRepairs off");
		});
		helper.succeedWhen(() -> {
			for (BlockPos p : burned) {
				helper.assertBlockPresent(Blocks.OAK_LOG, p);
			}
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still repairing");
			Upkeep.look(level, builder); // (looks again: what's back comes off the list)
			for (BlockPos p : burned) {
				helper.assertFalse(ThreatData.get(level).damaged().contains(helper.absolutePos(p)), "still on the list once back: " + p);
			}
		});
	}

	/**
	 * Both repairs off ({@code builderRepairs} and the threat repairs, as in GameTests by default): the builder leaves
	 * the burned roof alone; and with only {@code builderRepairs} off a hole no threat made is left alone too.
	 */
	//$ gametest_ticks_batch AREA '100' '"threatRepairOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "threatRepairOff")
	public void withRepairsOffOnlyThreatDamageIsRepaired(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Hut hut = hut(helper, "off", UUID.randomUUID());
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		BuildSiteManager.get(level).recordFinished(hut.id(), hut.placement(), builder.getUUID()); // the builder's to look after
		Upkeep.ENABLED = false;
		Upkeep.THREATS = true;
		BlockPos bench = new BlockPos(1, 2, 1);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Builders.employ(level, builder, helper.absolutePos(bench));
		helper.runAfterDelay(2, () -> {
			helper.setBlock(HUT.offset(2, 2, 2), Blocks.AIR); // a hole a player made
			helper.assertTrue(Upkeep.look(level, builder) == null, "a repair of a hole no threat made, with builderRepairs off");
			BlockPos at = helper.absolutePos(HUT.offset(1, 2, 2));
			level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
			VillageFire.burned(level, at);
			Upkeep.THREATS = false;
			helper.assertTrue(Upkeep.look(level, builder) == null, "a repair with both repairs off");
			helper.succeed();
		});
	}

	/** Every changed sentence a player reads has its text, and it says what the builders do. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyChangedSentenceHasItsText(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("aliveworkplace.config.builderRepairs.tooltip", "aliveworkplace.config.siegeDamage.tooltip")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.assertTrue(lang.getOrDefault("aliveworkplace.config.builderRepairs.tooltip").contains("raiders and village fires"),
			"the builderRepairs tooltip doesn't say threats' damage is still repaired");
		helper.assertTrue(lang.getOrDefault("aliveworkplace.config.siegeDamage.tooltip").contains("only the gate blocks"),
			"the siegeDamage tooltip doesn't say only gate blocks break");
		helper.succeed();
	}
}
