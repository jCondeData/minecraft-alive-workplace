package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA lane B, 2026-11-09: the boundaries of three builder fixes, from their specs (ROADMAP Bugs, docs/wiki create.md,
 * sieges.md, village-fire.md), not their code.
 * <ul>
 * <li>B1049: a schematic uploaded again under its name while a builder is half-way: "keeps building [the old one] until the
 * server restarts, then starts the new one's lists from the top (what already stands as drawn is skipped), so it never
 * ends with holes". Same size, bigger, smaller, the identical file, and not restarted.</li>
 * <li>B1080: "an armor stand standing in [a builder's build is spared from a threat's blast or shot]; you can still take
 * either away yourself". A raider's arrow too, a player's own stand beside the build is not spared, and a player's own hits
 * still break the builders' stand.</li>
 * <li>B1083: "An item frame ... that fell when its wall burned is hung again with the wall, from one in the builder's
 * chests; one you took down from a wall that still stands stays down." No frame in the chests, exactly one frame taken, a
 * frame taken down by the player, and a reload mid-repair with the wall already back. (The fire itself is
 * QaThreatSpare1109bBugTests' case; here the burn is the fire's two effects: the block gone and on the threats' list.)</li>
 * </ul>
 */
public class QaBuilderFixes1109bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";

	// ---------------------------------------------------------------- B1049

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(22, 2, 22);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);

	/** A structure file of a w x h x d box of {@code block}, drawn and saved as a player's upload would be. */
	private static byte[] file(GameTestHelper helper, int w, int h, int d, Block block) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(w - 1, h - 1, d - 1))) {
			helper.setBlock(p, block);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(w, h, d), false, Blocks.STRUCTURE_VOID);
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(w - 1, h - 1, d - 1))) {
			helper.setBlock(p, Blocks.AIR);
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
	}

	/** A restart: the sites and the builder saved and loaded back. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static long count(ServerLevel level, BlockPos from, BlockPos to, Block block) {
		return BlockPos.betweenClosedStream(from, to).filter(p -> level.getBlockState(p).is(block)).count();
	}

	/**
	 * A builder builds {@code first} (a w x h x d box of {@code firstBlock}); once {@code from}..{@code to} of its blocks
	 * stand, {@code second} is uploaded under the same id, and with {@code restart} the server restarts. Then
	 * {@code check} must hold once the site has ended.
	 */
	private static void replacedMidBuild(GameTestHelper helper, int[] first, Block firstBlock, int[] second, Block secondBlock,
		int from, int to, boolean restart, java.util.function.Consumer<ServerLevel> check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		byte[] one = file(helper, first[0], first[1], first[2], firstBlock);
		byte[] two = file(helper, second[0], second[1], second[2], secondBlock);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace",
			"create_schematics/qa/b1109_" + Long.toHexString(level.getGameTime()) + "_" + Integer.toHexString(System.identityHashCode(helper)));
		try {
			BlueprintImporter.importAs(level.getServer(), id, one);
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the first file didn't import: " + e);
		}
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, BENCH.east())};
		Builders.employ(level, builder[0], helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder[0], null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		UUID siteId = site.id();
		BlockPos a = helper.absolutePos(ORIGIN);
		BlockPos b = a.offset(first[0] - 1, first[1] - 1, first[2] - 1);
		boolean[] replaced = {false};
		helper.onEachTick(() -> {
			if (replaced[0]) {
				return;
			}
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			long placed = count(level, a, b, firstBlock);
			if (placed >= from && placed <= to) {
				replaced[0] = true;
				try {
					BlueprintImporter.importAs(level.getServer(), id, two);
				} catch (Exception e) {
					throw new GameTestAssertException("(setup) the second file didn't import: " + e);
				}
				if (restart) {
					builder[0] = reload(helper, level, builder[0]);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(replaced[0], "(setup) the build was never caught half-way through its first blueprint");
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(now == null, "the build hasn't ended: " + (now == null ? "-" : now.stage() + " " + now.status() + " missing " + now.missing()));
			check.accept(level);
		});
	}

	/** Same size, another block: after the restart every block of the new file stands, none of the old one is left. */
	//$ gametest_ticks_batch HUGE '3000' '"qaBuilderFixes1109bSameSize"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBuilderFixes1109bSameSize")
	public void aBlueprintReplacedByOneTheSameSizeAndReloadedIsBuiltWhole(GameTestHelper helper) {
		replacedMidBuild(helper, new int[] {5, 1, 5}, Blocks.STONE_BRICKS, new int[] {5, 1, 5}, Blocks.OAK_PLANKS, 8, 20, true, level -> {
			BlockPos a = helper.absolutePos(ORIGIN);
			long planks = count(level, a, a.offset(4, 0, 4), Blocks.OAK_PLANKS);
			long bricks = count(level, a, a.offset(4, 0, 4), Blocks.STONE_BRICKS);
			helper.assertTrue(planks == 25 && bricks == 0, "the re-uploaded 5x1x5 oak pad ended with " + planks + " of 25 planks and "
				+ bricks + " of the old stone bricks left in it");
		});
	}

	/** Bigger: a 3x1x3 replaced half-way by a 5x2x5 and reloaded: all 50 blocks stand. */
	//$ gametest_ticks_batch HUGE '3000' '"qaBuilderFixes1109bBigger"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBuilderFixes1109bBigger")
	public void aBlueprintReplacedByABiggerOneAndReloadedIsBuiltWhole(GameTestHelper helper) {
		replacedMidBuild(helper, new int[] {3, 1, 3}, Blocks.STONE_BRICKS, new int[] {5, 2, 5}, Blocks.OAK_PLANKS, 3, 7, true, level -> {
			BlockPos a = helper.absolutePos(ORIGIN);
			long planks = count(level, a, a.offset(4, 1, 4), Blocks.OAK_PLANKS);
			helper.assertTrue(planks == 50, "the re-uploaded bigger 5x2x5 block ended with " + planks + " of its 50 oak planks");
		});
	}

	/** Smaller, same block: a 5x1x5 pad replaced by a 3x1x3 of the same bricks (most of it already standing), reloaded: it ends whole. */
	//$ gametest_ticks_batch HUGE '3000' '"qaBuilderFixes1109bSmaller"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBuilderFixes1109bSmaller")
	public void aBlueprintReplacedByASmallerOneOfTheSameBlockAndReloadedEnds(GameTestHelper helper) {
		replacedMidBuild(helper, new int[] {5, 1, 5}, Blocks.STONE_BRICKS, new int[] {3, 1, 3}, Blocks.STONE_BRICKS, 12, 20, true, level -> {
			BlockPos a = helper.absolutePos(ORIGIN);
			long bricks = count(level, a, a.offset(2, 0, 2), Blocks.STONE_BRICKS);
			helper.assertTrue(bricks == 9, "the re-uploaded smaller 3x1x3 pad ended with " + bricks + " of its 9 stone bricks");
		});
	}

	/** The identical file uploaded again and the server restarted: the build still ends whole. */
	//$ gametest_ticks_batch HUGE '3000' '"qaBuilderFixes1109bIdentical"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBuilderFixes1109bIdentical")
	public void theSameFileUploadedAgainAndReloadedIsBuiltWhole(GameTestHelper helper) {
		replacedMidBuild(helper, new int[] {5, 1, 5}, Blocks.STONE_BRICKS, new int[] {5, 1, 5}, Blocks.STONE_BRICKS, 8, 20, true, level -> {
			BlockPos a = helper.absolutePos(ORIGIN);
			long bricks = count(level, a, a.offset(4, 0, 4), Blocks.STONE_BRICKS);
			helper.assertTrue(bricks == 25, "the pad uploaded again unchanged ended with " + bricks + " of its 25 stone bricks");
		});
	}

	/** Replaced and NOT restarted: the builder "keeps building [the old one] until the server restarts", so the old pad ends whole. */
	//$ gametest_ticks_batch HUGE '3000' '"qaBuilderFixes1109bNoRestart"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaBuilderFixes1109bNoRestart")
	public void aBlueprintReplacedHalfWayWithoutARestartFinishesTheOldOneWhole(GameTestHelper helper) {
		replacedMidBuild(helper, new int[] {5, 1, 5}, Blocks.STONE_BRICKS, new int[] {3, 2, 3}, Blocks.OAK_PLANKS, 8, 20, false, level -> {
			BlockPos a = helper.absolutePos(ORIGIN);
			long bricks = count(level, a, a.offset(4, 0, 4), Blocks.STONE_BRICKS);
			helper.assertTrue(bricks == 25, "replaced without a restart, the old pad ended with " + bricks + " of its 25 stone bricks (holes)");
		});
	}

	// ---------------------------------------------------------------- B1080

	/** Where the builders' hut stands: a 5x5 plank floor at y 1, corner logs, a log roof at y 4. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** The builders' stand, inside the hut on its floor. */
	private static final BlockPos BUILD_STAND = HUT.offset(3, 0, 3);
	/** A player's own stand beside the hut on the test floor (no build's block), as close to the blast as the builders'. */
	private static final BlockPos OWN_STAND = HUT.offset(-2, 0, 3);

	private static List<ArmorStand> stands(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(helper.absolutePos(at)).inflate(0.6), ArmorStand::isAlive);
	}

	/** The builders' finished hut with an armor stand in it (in its blueprint, as a Scan Tool blueprint carries one). */
	private static ArmorStand standHut(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(HUT.offset(x, -1, z), Blocks.OAK_PLANKS);
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, BUILD_STAND);
		stand.setYRot(0f);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bfix_stand_1109b_" + Long.toHexString(level.getGameTime())
			+ "_" + Integer.toHexString(System.identityHashCode(helper)));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT.below()), new Vec3i(5, 4, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT.below()), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return stand;
	}

	private static void griefing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : List.copyOf(data.damaged())) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
	}

	/**
	 * One raider's creeper blast between the builders' stand (in their hut) and a player's own stand on plain ground beside
	 * it, as far from the blast: the builders' stays, the player's own is broken as the game breaks it (only a build's stand
	 * is spared, and this proves the blast reaches).
	 */
	//$ gametest_batch AREA '"qaBuilderFixes1109bTwoStands"'
	@GameTest(template = AREA, batch = "qaBuilderFixes1109bTwoStands")
	public void aRaidersBlastSparesTheBuildersStandButNotAPlayersOwnBesideIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArmorStand build = standHut(helper);
		griefing(helper);
		helper.setBlock(OWN_STAND.below(), Blocks.STONE);
		ArmorStand own = helper.spawn(EntityType.ARMOR_STAND, OWN_STAND);
		helper.runAfterDelay(4, () -> {
			// halfway between the two stands, inside the hut's west wall line (2.5 blocks from each)
			BlockPos at = helper.absolutePos(HUT.offset(0, 0, 3));
			Creeper raider = EntityType.CREEPER.create(level);
			helper.assertTrue(raider != null, "setup: no creeper");
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			helper.assertTrue(build.isAlive() && !stands(helper, BUILD_STAND).isEmpty(), "the raider's blast broke the armor stand in the builders' finished hut");
			helper.assertTrue(!own.isAlive(), "the raider's blast spared a player's own armor stand on ground that isn't any build's (only builds' stands are spared)");
			helper.succeed();
		});
	}

	/** sieges.md: "A build still under way is spared too": a stand already set up in a site not yet finished stays through a raider's blast. */
	//$ gametest_batch AREA '"qaBuilderFixes1109bSiteStand"'
	@GameTest(template = AREA, batch = "qaBuilderFixes1109bSiteStand")
	public void aRaidersBlastSparesAStandInABuildStillUnderWay(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		griefing(helper);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				helper.setBlock(HUT.offset(x, -1, z), Blocks.OAK_PLANKS);
			}
		}
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, BUILD_STAND);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bfix_site_1109b_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT.below()), new Vec3i(5, 4, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT.below()), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		BuildSite site = manager.create(UUID.randomUUID(), "", id, placement);
		VillageFire.forgetBuilds();
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			VillageFire.forgetBuilds();
		});
		helper.runAfterDelay(2, () -> {
			BlockPos at = helper.absolutePos(HUT.offset(2, 0, 2));
			Creeper raider = EntityType.CREEPER.create(level);
			helper.assertTrue(raider != null, "setup: no creeper");
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			level.explode(raider, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3f, Level.ExplosionInteraction.MOB);
			helper.assertTrue(stand.isAlive(), "the raider's blast broke an armor stand in a build still under way");
			helper.succeed();
		});
	}

	/** "You can still take either away yourself": a player's own hits break the builders' stand as in vanilla. */
	//$ gametest_batch AREA '"qaBuilderFixes1109bPlayerHit"'
	@GameTest(template = AREA, batch = "qaBuilderFixes1109bPlayerHit")
	public void aPlayersOwnHitsStillBreakTheBuildersStand(GameTestHelper helper) {
		ArmorStand build = standHut(helper);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.moveTo(build.getX() + 1, build.getY(), build.getZ());
		helper.runAfterDelay(2, () -> {
			player.attack(build);
			player.attack(build); // two hits in a row: vanilla's way to break a stand by hand
			helper.assertTrue(!build.isAlive(), "a player's two hits didn't break the armor stand in the builders' hut: only threats are refused");
			helper.succeed();
		});
	}

	/** A live raider's arrow, flying west at {@code at} (the stand's middle) from 1.5 blocks off, no gravity. */
	private static void raidersArrowAt(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Pillager raider = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 2, 18));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(raider);
		BlockPos a = helper.absolutePos(at);
		arrow.moveTo(a.getX() + 2.0, a.getY() + 0.8, a.getZ() + 0.5, 90f, 0f);
		arrow.setNoGravity(true);
		arrow.setDeltaMovement(-1.5, 0, 0);
		level.addFreshEntity(arrow);
	}

	/** B1080 says "a threat's blast or shot": a raider's arrow into the builders' stand leaves it standing. */
	//$ gametest_ticks_batch AREA '100' '"qaBuilderFixes1109bArrow"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBuilderFixes1109bArrow")
	public void aRaidersArrowLeavesTheBuildersStand(GameTestHelper helper) {
		ArmorStand build = standHut(helper);
		raidersArrowAt(helper, BUILD_STAND);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(build.isAlive(), "a raider's arrow broke the armor stand in the builders' finished hut");
			helper.succeed();
		});
	}

	/** Control: the same arrow into a stand on ground that isn't a build's breaks it (and so really hits). */
	//$ gametest_ticks_batch AREA '100' '"qaBuilderFixes1109bArrowOwn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBuilderFixes1109bArrowOwn")
	public void aRaidersArrowStillBreaksAPlayersOwnStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		helper.setBlock(BUILD_STAND.below(), Blocks.STONE);
		ArmorStand own = helper.spawn(EntityType.ARMOR_STAND, BUILD_STAND);
		raidersArrowAt(helper, BUILD_STAND);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!own.isAlive(), "the raider's arrow left a player's own stand that isn't on any build (or it missed the stand)");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- B1083

	/** The roof plank the frame hangs under, and the frame's block. */
	private static final BlockPos SUPPORT = HUT.offset(2, 2, 2);
	private static final BlockPos FRAME_AT = HUT.offset(2, 1, 2);
	/** Another roof plank, away from the frame. */
	private static final BlockPos OTHER = HUT.offset(4, 2, 1);
	private static final BlockPos CHEST = new BlockPos(2, 2, 18);

	private record FrameHut(ServerLevel level, BlueprintData.Placement placement, Villager builder, ItemFrame frame, Container chest) {}

	/**
	 * The builders' finished plank-roofed hut with an empty item frame under its middle plank (in the blueprint), the builder
	 * at his bench beside it and a chest with planks, logs and {@code frames} item frames.
	 */
	private static FrameHut frameHut(GameTestHelper helper, int frames) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean was = Upkeep.ENABLED;
		boolean threats = Upkeep.THREATS;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.THREATS = true;
		Upkeep.ASK = false;
		helper.setDayTime(2000);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_PLANKS);
			}
		}
		ItemFrame hung = new ItemFrame(level, helper.absolutePos(FRAME_AT), Direction.DOWN);
		helper.assertTrue(hung.survives(), "setup: the frame can't hang under the roof");
		level.addFreshEntity(hung);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bfix_frame_1109b_" + Long.toHexString(level.getGameTime())
			+ "_" + Integer.toHexString(System.identityHashCode(helper)));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 32));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		if (frames > 0) {
			chest.setItem(2, new ItemStack(Items.ITEM_FRAME, frames));
		}
		BlockPos bench = new BlockPos(1, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		VillageFire.forgetBuilds();
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.THREATS = threats;
			Upkeep.ASK = asks;
			BuildSiteManager manager = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(manager.all())) {
				if (s.placement().equals(placement)) {
					manager.remove(s.id());
				}
			}
			manager.forgetFinished(placement);
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : List.copyOf(data.damaged())) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
		return new FrameHut(level, placement, builder, hung, chest);
	}

	/** What a village fire leaves of a plank: the block gone (burned) and on the threats' repair list. */
	private static void burned(GameTestHelper helper, ServerLevel level, BlockPos plank) {
		helper.setBlock(plank, Blocks.AIR);
		ThreatData.get(level).damaged(helper.absolutePos(plank));
	}

	private static boolean repairing(ServerLevel level, BlueprintData.Placement placement) {
		return BuildSiteManager.get(level).all().stream().anyMatch(s -> s.placement().equals(placement));
	}

	private static List<ItemFrame> framesAt(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FRAME_AT)).inflate(0.3), ItemFrame::isAlive);
	}

	private static int frameItems(FrameHut hut) {
		int n = hut.builder().getInventory().countItem(Items.ITEM_FRAME);
		for (int i = 0; i < hut.chest().getContainerSize(); i++) {
			if (hut.chest().getItem(i).is(Items.ITEM_FRAME)) {
				n += hut.chest().getItem(i).getCount();
			}
		}
		return n;
	}

	private static int looseFrames(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(24, 16, 24),
			e -> e.getItem().is(Items.ITEM_FRAME)).size();
	}

	/**
	 * Runs the repair: the builder looks for damage at tick 5 (and again at 200 in case he was busy); once the repair site
	 * started and has ended, {@code done} is checked {@code settle} ticks later.
	 */
	private static void repairThen(GameTestHelper helper, FrameHut hut, BlockPos plank, Runnable onStart, Runnable done) {
		long[] started = {-1};
		long[] ended = {-1};
		helper.runAfterDelay(5, () -> Upkeep.look(hut.level(), hut.builder()));
		helper.runAfterDelay(200, () -> {
			if (started[0] < 0) {
				Upkeep.look(hut.level(), hut.builder());
			}
		});
		helper.onEachTick(() -> {
			boolean on = repairing(hut.level(), hut.placement());
			if (started[0] < 0 && on) {
				started[0] = helper.getTick();
				onStart.run();
			} else if (started[0] >= 0 && ended[0] < 0 && !on) {
				ended[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started[0] >= 0, "(the builder hasn't started the repair yet)");
			helper.assertTrue(ended[0] >= 0, "(the repair hasn't ended yet)");
			helper.assertTrue(helper.getTick() >= ended[0] + 40, "(settling after the repair)");
			helper.assertTrue(hut.level().getBlockState(helper.absolutePos(plank)).is(Blocks.OAK_PLANKS), "the repair ended without putting the burned plank back");
			done.run();
		});
	}

	/** Exactly one frame goes up, and exactly one comes out of the builder's chest (none made from nothing, none lost). */
	//$ gametest_ticks_batch AREA '2400' '"qaBuilderFixes1109bFrameOne"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBuilderFixes1109bFrameOne")
	public void aBurnedFramesWallIsRepairedWithExactlyOneFrameFromTheChest(GameTestHelper helper) {
		FrameHut hut = frameHut(helper, 4);
		helper.runAfterDelay(2, () -> {
			burned(helper, hut.level(), SUPPORT);
			hut.frame().discard(); // fell with its plank and burned
		});
		repairThen(helper, hut, SUPPORT, () -> {}, () -> {
			helper.assertTrue(framesAt(helper).size() == 1, "after the burned plank was put back, " + framesAt(helper).size() + " frames hang under it (expected 1)");
			helper.assertTrue(frameItems(hut) == 3, "the builder's chest and pockets hold " + frameItems(hut) + " item frames after hanging one of 4 (expected 3)");
		});
	}

	/** No item frame in the chests: the plank is put back, no frame is hung from nothing, and the repair ends. */
	//$ gametest_ticks_batch AREA '2400' '"qaBuilderFixes1109bFrameNone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBuilderFixes1109bFrameNone")
	public void aBurnedFramesWallWithNoFrameInTheChestGetsItsPlankButNoFrameFromNothing(GameTestHelper helper) {
		FrameHut hut = frameHut(helper, 0);
		helper.runAfterDelay(2, () -> {
			burned(helper, hut.level(), SUPPORT);
			hut.frame().discard();
		});
		repairThen(helper, hut, SUPPORT, () -> {}, () -> {
			helper.assertTrue(framesAt(helper).isEmpty(), "with no item frame in his chests, the builder hung one under the put-back plank anyway");
			helper.assertTrue(frameItems(hut) == 0 && looseFrames(helper) == 0, "an item frame appeared from nothing: " + frameItems(hut) + " in chest/pockets, "
				+ looseFrames(helper) + " on the ground");
		});
	}

	/** "One you took down from a wall that still stands stays down", even while a repair of another burned plank runs. */
	//$ gametest_ticks_batch AREA '2400' '"qaBuilderFixes1109bFrameTaken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBuilderFixes1109bFrameTaken")
	public void aFrameThePlayerTookFromAStandingWallStaysDownThroughARepair(GameTestHelper helper) {
		FrameHut hut = frameHut(helper, 4);
		helper.runAfterDelay(2, () -> {
			hut.frame().discard(); // the player took it down; its plank still stands
			burned(helper, hut.level(), OTHER);
		});
		repairThen(helper, hut, OTHER, () -> {}, () -> {
			helper.assertTrue(hut.level().getBlockState(helper.absolutePos(SUPPORT)).is(Blocks.OAK_PLANKS), "(setup) the frame's own plank is gone");
			helper.assertTrue(framesAt(helper).isEmpty(), "the builder hung again a frame the player took down from a wall that still stands");
			helper.assertTrue(frameItems(hut) == 4, "the builder used an item frame from his chest (" + frameItems(hut) + " of 4 left)");
		});
	}

	/**
	 * Save and reload mid-repair, with the wall already back (the builder had just put it back): the frame still goes up
	 * when the repair ends (its wall is still on the threats' list).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaBuilderFixes1109bFrameReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaBuilderFixes1109bFrameReload")
	public void aBurnedFrameIsStillHungAfterASaveAndReloadMidRepairWithItsWallBack(GameTestHelper helper) {
		FrameHut hut = frameHut(helper, 4);
		Villager[] builder = {hut.builder()};
		helper.runAfterDelay(2, () -> {
			burned(helper, hut.level(), SUPPORT);
			hut.frame().discard();
		});
		repairThen(helper, hut, SUPPORT, () -> {
			helper.setBlock(SUPPORT, Blocks.OAK_PLANKS); // the wall already back when the server stops
			builder[0] = reload(helper, hut.level(), builder[0]);
		}, () -> {
			helper.assertTrue(framesAt(helper).size() == 1, "after a reload mid-repair (its plank already back), " + framesAt(helper).size()
				+ " frames hang under the plank (expected 1)");
		});
	}
}
