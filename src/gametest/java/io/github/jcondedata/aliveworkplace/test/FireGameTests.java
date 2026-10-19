package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The village fire and the Firewarden (ROADMAP 32.16), on a small wooden hut the builders "finished" (a blueprint taken
 * from the world and recorded as a finished build) in a village: a Firewarden 15 blocks away puts a fire on its roof out
 * within 400 ticks, gains experience, and the chronicle names them; without one it still burns at 400; the fire never
 * spreads into a plank the test placed as a player's; the job starts from a grindstone and a water bucket and the
 * grindstone's tooltip lists it (and not with config {@code firewardens} off); lightning in a thunderstorm and a
 * raider's burning arrow set the roof alight (and not without thunder, from anyone else's arrow, or with
 * {@code mobGriefing} off); a Journeyman's bell calls a bucket brigade that puts it out; buckets are refilled at water;
 * the water carried survives a save and load; the builder puts back what burned; and every new sentence has its text.
 */
public class FireGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(19, 2, 2);
	/** The hut's corner: logs at its four corners (y 2 and 3), a 5 x 5 roof of logs at y 4. */
	private static final BlockPos HUT = new BlockPos(12, 2, 12);
	private static final BlockPos ROOF_TOP = new BlockPos(14, 5, 14);
	private static final BlockPos GRINDSTONE = new BlockPos(3, 2, 3);

	private record Hut(ServerLevel level, BlockPos hall, BlueprintData.Placement placement, ResourceLocation id) {
	}

	/** A village (its hall) with the hut finished by the builders, fire ticking, every other test's leftovers gone. */
	private static Hut hut(GameTestHelper helper, String name) {
		return hut(helper, name, false);
	}

	/**
	 * {@link #hut(GameTestHelper, String)}; {@code tall}: its corner logs reach y 7 and its roof at y 8 is a row of stairs
	 * all over, a pitched roof's ridge, with nowhere on it or near it to stand (B218).
	 */
	private static Hut hut(GameTestHelper helper, String name, boolean tall) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		VillageFire.forget();
		// B217: nothing but this test's own Firewarden may put the fire out. Rain does (the game's fire goes out under an
		// open sky, and the sky's rain fades out over a hundred ticks after an earlier test's storm), and so would any other
		// test's Firewarden still about whose grindstone is within answering reach, wherever they stand (Leftovers.clear
		// reaches 48 blocks round the area; a Firewarden answers a fire 48 blocks from their grindstone).
		level.setWeatherParameters(6000, 0, false, false);
		level.setRainLevel(0f);
		level.setThunderLevel(0f);
		Vec3 middle = helper.getBounds().getCenter();
		for (Villager other : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class), FirewardenWork::isFirewarden)) {
			if (other.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
				.filter(g -> g.dimension() == level.dimension() && Vec3.atCenterOf(g.pos()).distanceTo(middle) <= VillageFire.ANSWER + 32).isPresent()) {
				other.discard();
			}
		}
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setWeatherParameters(6000, 0, false, false);
			FirewardenWork.ENABLED = true;
			Sieges.DAMAGE = true;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
				if (level.getBlockState(p).is(BlockTags.FIRE)) {
					level.removeBlock(p, false);
				}
			}
			VillageFire.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean corner = (x == 0 || x == 4) && (z == 0 || z == 4);
				int walls = tall ? 6 : 2;
				if (corner) {
					for (int y = 0; y < walls; y++) {
						helper.setBlock(HUT.offset(x, y, z), Blocks.OAK_LOG);
					}
				}
				// a log roof: it burns slowly, as a roof should in a test (the tall hut's: stairs, nowhere to stand)
				helper.setBlock(HUT.offset(x, walls, z), tall ? Blocks.OAK_STAIRS : Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "fire_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, tall ? 7 : 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return new Hut(level, helper.absolutePos(HALL), placement, id);
	}

	private static void fire(GameTestHelper helper, BlockPos local) {
		BlockPos at = helper.absolutePos(local);
		helper.getLevel().setBlockAndUpdate(at, BaseFireBlock.getState(helper.getLevel(), at));
	}

	/** Fire blocks in the test's area. */
	private static long fires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return BlockPos.betweenClosedStream(helper.getBounds()).filter(p -> level.getBlockState(p).is(BlockTags.FIRE)).count();
	}

	private static Villager firewarden(GameTestHelper helper, BlockPos standing) {
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Villager v = helper.spawn(EntityType.VILLAGER, standing);
		Stations.assign(helper.getLevel(), v, helper.absolutePos(GRINDSTONE), ModVillagers.FIREWARDEN);
		return v;
	}

	/** What a failure needs to know (B217): the Firewarden's state, the sky, the roof under the fire and the chronicle's word. */
	private static String scene(GameTestHelper helper, Hut hut, Villager warden) {
		ServerLevel level = hut.level();
		return "the firewarden " + (warden.isAlive() && level.getEntity(warden.getUUID()) == warden ? "in the world" : "GONE (" + warden.getRemovalReason() + ")")
			+ " at " + warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ", a "
			+ warden.getVillagerData().getProfession() + " level " + warden.getVillagerData().getLevel() + ", post "
			+ warden.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(g -> g.pos().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString()).orElse("none")
			+ ", " + FirewardenWork.water(warden) + " buckets, activity " + warden.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("none")
			+ "; rain " + level.getRainLevel(1f) + ", day time " + level.getDayTime() % 24000 + "; roof under the fire "
			+ level.getBlockState(helper.absolutePos(ROOF_TOP.below())).getBlock() + "; chronicle: " + fireEntry(hut).map(e -> e.text().getString()).orElse("nothing yet");
	}

	private static Optional<Chronicle.Entry> fireEntry(Hut hut) {
		if (!(hut.level().getBlockEntity(hut.hall()) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FIRE).reduce((a, b) -> b);
	}

	/**
	 * A fire on the hut's roof with a Firewarden 15 blocks away at their grindstone: they run over and throw their
	 * buckets on it; it's out within 400 ticks, they gained experience, and the chronicle says who put it out.
	 */
	//$ gametest_ticks_batch AREA '900' '"fireWarden"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "fireWarden")
	public void aFirewardenPutsTheFireOut(GameTestHelper helper) {
		Hut hut = hut(helper, "warden");
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER); // the pond by the grindstone, should they need it
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setCustomName(net.minecraft.network.chat.Component.literal("Rowan"));
		helper.assertTrue(Math.sqrt(helper.absolutePos(GRINDSTONE).distSqr(helper.absolutePos(ROOF_TOP))) >= 15, "the grindstone is too near the fire");
		int[] xp = {-1};
		long[] lit = {-1};
		long[] out = {-1};
		helper.runAfterDelay(2, () -> {
			xp[0] = warden.getVillagerXp();
			fire(helper, ROOF_TOP);
			fire(helper, ROOF_TOP.offset(-1, 0, -1));
			lit[0] = helper.getTick();
			helper.assertTrue(VillageFire.incident(hut.level(), hut.hall()).isPresent(), "the fire wasn't noticed when it was placed");
			helper.assertTrue(fires(helper) == 2, "the fire didn't take: " + fires(helper) + " fire blocks; " + scene(helper, hut, warden));
		});
		helper.onEachTick(() -> {
			if (lit[0] >= 0 && out[0] < 0 && fires(helper) == 0) {
				out[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(out[0] >= 0, "the fire is still burning (" + fires(helper) + " fire blocks); " + scene(helper, hut, warden));
			helper.assertTrue(out[0] - lit[0] <= 400, "the fire took " + (out[0] - lit[0]) + " ticks to put out; " + scene(helper, hut, warden));
			// (B217: the chronicle says who put it out, or that it burned out, so a failure here names what did it)
			helper.assertTrue(warden.getVillagerXp() > xp[0], "the firewarden gained no experience: " + warden.getVillagerXp() + " (fire out "
				+ (out[0] - lit[0]) + " ticks after it was lit); " + scene(helper, hut, warden));
			Optional<Chronicle.Entry> entry = fireEntry(hut);
			helper.assertTrue(entry.isPresent(), "no fire in the chronicle");
			String text = entry.get().text().getString();
			helper.assertTrue(text.contains("Rowan the Firewarden put it out") && text.startsWith("Fire in the "), "the chronicle says: " + text);
		});
	}

	/**
	 * B218: a fire on the ridge of a tall pitched roof, with nowhere on the roof or near it to stand: the Firewarden runs
	 * over and throws the buckets up onto it from the ground below; it's out within 400 ticks and they gained experience.
	 * (Before, they stood at the grindstone and watched it burn: their throw reached only 3.5 blocks.)
	 */
	//$ gametest_ticks_batch AREA '900' '"fireWardenHigh"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "fireWardenHigh")
	public void aFirewardenThrowsUpOntoAHighRoof(GameTestHelper helper) {
		Hut hut = hut(helper, "high", true);
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setCustomName(net.minecraft.network.chat.Component.literal("Rowan"));
		BlockPos ridge = new BlockPos(14, 9, 14);
		helper.assertTrue(Walker.standingSpot(hut.level(), helper.absolutePos(ridge), helper.absolutePos(new BlockPos(3, 2, 4)), 3.5) == null,
			"there is somewhere to stand near the ridge: the test doesn't test a high roof");
		int[] xp = {-1};
		long[] lit = {-1};
		long[] out = {-1};
		helper.runAfterDelay(2, () -> {
			xp[0] = warden.getVillagerXp();
			fire(helper, ridge);
			lit[0] = helper.getTick();
			helper.assertTrue(VillageFire.incident(hut.level(), hut.hall()).isPresent(), "the fire wasn't noticed when it was placed");
			helper.assertTrue(fires(helper) == 1, "the fire didn't take: " + fires(helper) + " fire blocks");
		});
		helper.onEachTick(() -> {
			if (lit[0] >= 0 && out[0] < 0 && fires(helper) == 0) {
				out[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(out[0] >= 0, "the fire on the ridge is still burning (" + fires(helper) + " fire blocks); the firewarden at "
				+ warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " with " + FirewardenWork.water(warden) + " buckets");
			helper.assertTrue(out[0] - lit[0] <= 400, "the fire took " + (out[0] - lit[0]) + " ticks to put out");
			helper.assertTrue(warden.getVillagerXp() > xp[0], "the firewarden gained no experience: " + warden.getVillagerXp());
		});
	}

	/** Without a Firewarden the fire on the roof is still burning 400 ticks later. */
	//$ gametest_ticks_batch AREA '500' '"fireAlone"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "fireAlone")
	public void withoutAFirewardenItStillBurns(GameTestHelper helper) {
		Hut hut = hut(helper, "alone");
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			fire(helper, ROOF_TOP.offset(1, 0, 1));
			fire(helper, ROOF_TOP.offset(-1, 0, 1));
		});
		helper.runAfterDelay(402, () -> {
			helper.assertTrue(fires(helper) > 0, "the fire went out by itself");
			helper.assertTrue(VillageFire.incident(hut.level(), hut.hall()).isPresent(), "the village's fire is over");
			helper.succeed();
		});
	}

	/**
	 * The fire never spreads into a plank the test placed against the hut as a player's: with fire on it and all along
	 * the roof's edge beside it, for 400 ticks, it stays; the roof's planks are the builders' and may burn.
	 */
	//$ gametest_ticks_batch AREA '500' '"fireSpread"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "fireSpread")
	public void theFireNeverSpreadsToAPlayersBlock(GameTestHelper helper) {
		Hut hut = hut(helper, "spread");
		List<BlockPos> players = List.of(new BlockPos(17, 4, 12), new BlockPos(17, 4, 13), new BlockPos(17, 4, 14), new BlockPos(17, 4, 15),
			new BlockPos(17, 4, 16), new BlockPos(18, 4, 14));
		for (BlockPos p : players) {
			helper.setBlock(p, Blocks.OAK_PLANKS); // after the blueprint was taken: not the builders'
		}
		helper.runAfterDelay(2, () -> {
			ServerLevel level = hut.level();
			helper.assertTrue(VillageFire.mayBurn(level, helper.absolutePos(new BlockPos(16, 4, 14))), "the builders' roof log can't burn");
			helper.assertFalse(VillageFire.mayBurn(level, helper.absolutePos(new BlockPos(17, 4, 14))), "the player's plank can burn");
			for (int z = 12; z <= 16; z++) {
				fire(helper, new BlockPos(16, 5, z));
				fire(helper, new BlockPos(17, 5, z));
			}
		});
		helper.onEachTick(() -> {
			for (BlockPos p : players) {
				helper.assertBlockPresent(Blocks.OAK_PLANKS, p);
			}
		});
		helper.runAfterDelay(402, helper::succeed);
	}

	/**
	 * With {@code siegeDamage} off a village fire burns nothing, the builders' blocks included; outside a village (no hall
	 * near) fire burns as the game has it.
	 */
	//$ gametest_ticks_batch AREA '100' '"fireNoDamage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fireNoDamage")
	public void withSiegeDamageOffNothingBurns(GameTestHelper helper) {
		Hut hut = hut(helper, "nodamage");
		helper.runAfterDelay(2, () -> {
			BlockPos roof = helper.absolutePos(new BlockPos(14, 4, 14));
			helper.assertTrue(VillageFire.builderMade(hut.level(), roof), "the roof isn't the builders'");
			Sieges.DAMAGE = false;
			helper.assertFalse(VillageFire.mayBurn(hut.level(), roof), "a builder's block burns with siegeDamage off");
			Sieges.DAMAGE = true;
			hut.level().removeBlock(hut.hall(), false);
			VillageFire.forgetHalls();
			helper.assertTrue(VillageFire.mayBurn(hut.level(), helper.absolutePos(new BlockPos(14, 1, 14))), "outside a village fire is held back");
			helper.succeed();
		});
	}

	/** A grindstone and a water bucket make a Firewarden, and the grindstone's tooltip lists the job; off, neither. */
	//$ gametest_ticks_batch AREA '100' '"fireJob"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fireJob")
	public void theJobStartsFromAGrindstoneAndAWaterBucket(GameTestHelper helper) {
		hut(helper, "job");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		Villager v = helper.spawn(EntityType.VILLAGER, GRINDSTONE.south());
		helper.runAfterDelay(2, () -> {
			Stations.Station station = Stations.at(Blocks.GRINDSTONE).orElseThrow();
			helper.assertTrue(station.jobs().stream().filter(Stations::available).anyMatch(j -> j.profession().get() == ModVillagers.FIREWARDEN),
				"the grindstone's tooltip doesn't list the Firewarden");
			helper.assertTrue(Language.getInstance().has(Stations.itemKey(station.jobFor(new ItemStack(Items.WATER_BUCKET)).orElseThrow())),
				"the tooltip doesn't say a water bucket picks it");
			FirewardenWork.ENABLED = false;
			helper.assertTrue(station.jobs().stream().filter(Stations::available).noneMatch(j -> j.profession().get() == ModVillagers.FIREWARDEN),
				"firewardens off: the tooltip still lists the Firewarden");
			helper.assertTrue(Stations.choose(player, v, new ItemStack(Items.WATER_BUCKET)) == InteractionResult.PASS
				&& v.getVillagerData().getProfession() != ModVillagers.FIREWARDEN, "firewardens off: a water bucket still made a Firewarden");
			FirewardenWork.ENABLED = true;
			InteractionResult result = Stations.choose(player, v, new ItemStack(Items.WATER_BUCKET));
			helper.assertTrue(result.consumesAction() && v.getVillagerData().getProfession() == ModVillagers.FIREWARDEN,
				"a water bucket at the grindstone gave " + v.getVillagerData().getProfession() + " (" + result + ")");
			helper.assertTrue(Stations.choose(player, v, new ItemStack(Items.IRON_SWORD)).consumesAction()
				&& v.getVillagerData().getProfession() == ModVillagers.GUARD, "a sword no longer makes a Guard there");
			helper.succeed();
		});
	}

	/** Lightning in a thunderstorm sets the hut's roof alight (by its chance); without thunder it never does. */
	//$ gametest_ticks_batch AREA '100' '"fireLightning"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fireLightning")
	public void lightningSetsTheRoofAlight(GameTestHelper helper) {
		Hut hut = hut(helper, "lightning");
		helper.runAfterDelay(2, () -> {
			ServerLevel level = hut.level();
			BlockPos strike = helper.absolutePos(new BlockPos(13, 8, 13));
			RandomSource dry = RandomSource.create(7L);
			for (int i = 0; i < 40; i++) {
				helper.assertTrue(VillageFire.lightning(level, strike, dry).isEmpty(), "lightning lit the roof without thunder");
			}
			level.setWeatherParameters(0, 6000, true, true);
			helper.assertTrue(VillageFire.factor(level, hut.hall()) > 0, "the hut's village isn't wooden enough to burn: " + Disasters.woodShare(level, hut.hall()));
			Optional<BlockPos> fire = Optional.empty();
			int lit = 0;
			RandomSource random = RandomSource.create(42L);
			for (int i = 0; i < 60; i++) {
				Optional<BlockPos> f = VillageFire.lightning(level, strike, random);
				if (f.isPresent()) {
					lit++;
					fire = f;
					level.removeBlock(f.get(), false);
				}
			}
			helper.assertTrue(lit > 5 && lit < 55, "lightning lit the roof " + lit + " times in 60, not about 1 in 3 (or more with all that wood)");
			BlockPos onRoof = fire.get();
			helper.assertTrue(onRoof.getY() == helper.absolutePos(ROOF_TOP).getY() && level.getBlockState(onRoof.below()).is(Blocks.OAK_LOG),
				"the fire isn't on the roof: " + onRoof);
			helper.succeed();
		});
	}

	/**
	 * A raider's burning arrow on the roof sets it alight by its chance; a skeleton that isn't a raider never does, nor
	 * a raider with {@code mobGriefing} off.
	 */
	//$ gametest_ticks_batch AREA '100' '"fireArrow"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fireArrow")
	public void aRaidersBurningArrowLightsTheRoof(GameTestHelper helper) {
		Hut hut = hut(helper, "arrow");
		Skeleton raider = helper.spawn(EntityType.SKELETON, new BlockPos(5, 2, 18));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		Skeleton stray = helper.spawn(EntityType.SKELETON, new BlockPos(7, 2, 18));
		stray.setNoAi(true);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = hut.level();
			BlockPos roof = helper.absolutePos(new BlockPos(15, 4, 15));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(roof).add(0, 0.5, 0), Direction.UP, roof, false);
			RandomSource never = RandomSource.create(7L);
			for (int i = 0; i < 40; i++) {
				helper.assertTrue(VillageFire.arrow(level, stray, hit, never).isEmpty(), "a stray skeleton's arrow lit the roof");
			}
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
			for (int i = 0; i < 40; i++) {
				helper.assertTrue(VillageFire.arrow(level, raider, hit, never).isEmpty(), "an arrow lit the roof with mobGriefing off");
			}
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
			int lit = 0;
			RandomSource random = RandomSource.create(42L);
			for (int i = 0; i < 40; i++) {
				Optional<BlockPos> f = VillageFire.arrow(level, raider, hit, random);
				if (f.isPresent()) {
					helper.assertTrue(f.get().equals(roof.above()), "the arrow's fire isn't where it hit: " + f.get());
					lit++;
					level.removeBlock(f.get(), false);
				}
			}
			helper.assertTrue(lit > 3 && lit < 37, "the raider's arrows lit the roof " + lit + " times in 40, not about 1 in 4 (more with the wood)");
			helper.succeed();
		});
	}

	/**
	 * A Journeyman Firewarden rings the bell: two villagers off work nearby join the bucket brigade (once a fire), and
	 * they put the fire out, a block every two seconds; the chronicle says the brigade did. The roof fire is held still
	 * once lit (fire ticks off, put back after the test): the game's own fire, by the level's random, sometimes burned the
	 * log under it and went out by itself before the brigade arrived (B157), and a spreading roof under load outran the
	 * timeout (B163). The brigade's walk, bucket and douse are the real ones; 2,400 ticks leave a loaded machine room.
	 */
	//$ gametest_ticks_batch AREA '2400' '"fireBrigade"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "fireBrigade")
	public void aJourneymanCallsABucketBrigade(GameTestHelper helper) {
		Hut hut = hut(helper, "brigade");
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true); // the brigade alone puts this one out
		warden.setVillagerData(warden.getVillagerData().setLevel(3));
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		Villager c = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8));
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			// Held still: it can't burn out (or spread) before the brigade gets there; hut() restores the rule.
			hut.level().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, hut.level().getServer());
			List<Villager> called = VillageFire.ring(hut.level(), warden, helper.absolutePos(ROOF_TOP));
			helper.assertTrue(called.size() == VillageFire.BRIGADE_SIZE, "the bell called " + called.size() + " villagers");
			helper.assertTrue(called.contains(a) && called.contains(b) && !called.contains(c), "the brigade isn't the two nearest: " + called);
			helper.assertTrue(VillageFire.brigadeOf(a).isPresent(), "the first villager isn't on the brigade");
			helper.assertTrue(VillageFire.ring(hut.level(), warden, helper.absolutePos(ROOF_TOP)).isEmpty(), "the bell rang twice for one fire");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fires(helper) == 0, "still burning");
			Optional<Chronicle.Entry> entry = fireEntry(hut);
			helper.assertTrue(entry.isPresent() && entry.get().text().getString().contains("the bucket brigade put it out"),
				"the chronicle says: " + entry.map(e -> e.text().getString()).orElse("nothing"));
			helper.assertTrue(VillageFire.brigadeOf(a).isEmpty(), "the brigade wasn't sent home");
		});
	}

	/**
	 * B152: villagers called to the brigade carry a water bucket in the hand that held their tool (a hired fisher's rod,
	 * a worn axe). Once the fire is out the very same tool is back in their hand, wear and all.
	 */
	//$ gametest_ticks_batch AREA '1200' '"fireBrigadeTools"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "fireBrigadeTools")
	public void theBrigadeKeepsTheirTools(GameTestHelper helper) {
		Hut hut = hut(helper, "tools");
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true); // the brigade alone puts this one out
		warden.setVillagerData(warden.getVillagerData().setLevel(3));
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.setDamageValue(17);
		a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, axe.copy());
		b.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		boolean[] bucket = {false};
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			List<Villager> called = VillageFire.ring(hut.level(), warden, helper.absolutePos(ROOF_TOP));
			helper.assertTrue(called.contains(a) && called.contains(b), "the brigade isn't the two tool holders: " + called);
		});
		helper.onEachTick(() -> {
			if (a.getMainHandItem().is(Items.WATER_BUCKET)) {
				bucket[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bucket[0], "the brigade never carried a bucket");
			helper.assertTrue(fires(helper) == 0, "still burning");
			helper.assertTrue(VillageFire.brigadeOf(a).isEmpty() && VillageFire.brigadeOf(b).isEmpty(), "the brigade wasn't sent home");
			ItemStack held = a.getMainHandItem();
			helper.assertTrue(held.is(Items.IRON_AXE) && held.getDamageValue() == 17, "the axe wasn't given back: " + held);
			helper.assertTrue(b.getMainHandItem().is(Items.FISHING_ROD), "the rod wasn't given back: " + b.getMainHandItem());
			helper.assertTrue(!ModAttachments.BRIGADE_HELD.has(a) && !ModAttachments.BRIGADE_HELD.has(b), "a tool is still kept aside");
		});
	}

	/**
	 * B152: a world saved while a villager was on the brigade (bucket in hand, their axe kept aside) and loaded once the
	 * fire is over: the axe goes back in their hand.
	 */
	//$ gametest_ticks_batch AREA '200' '"fireBrigadeSaved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "fireBrigadeSaved")
	public void aBrigadeSavedMidCallGetsTheToolBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageFire.forget();
		Villager before = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));
		before.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		ModAttachments.BRIGADE_HELD.set(before, new ItemStack(Items.IRON_AXE));
		CompoundTag tag = before.saveWithoutId(new CompoundTag());
		before.discard();
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(tag);
		helper.getLevel().addFreshEntity(loaded);
		helper.succeedWhen(() -> {
			helper.assertTrue(loaded.getMainHandItem().is(Items.IRON_AXE), "after the load the hand holds " + loaded.getMainHandItem());
			helper.assertTrue(!ModAttachments.BRIGADE_HELD.has(loaded), "the axe is still kept aside");
			loaded.discard();
		});
	}

	/** Out of water, a Firewarden fills their buckets at the water by the grindstone. */
	//$ gametest_ticks_batch AREA '600' '"fireRefill"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "fireRefill")
	public void anEmptyFirewardenRefillsAtWater(GameTestHelper helper) {
		hut(helper, "refill");
		helper.setBlock(new BlockPos(6, 1, 4), Blocks.WATER);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		FirewardenWork.setWater(warden, 0);
		helper.succeedWhen(() -> helper.assertTrue(FirewardenWork.water(warden) == FirewardenWork.BUCKETS,
			"the firewarden has " + FirewardenWork.water(warden) + " buckets of water (a " + warden.getVillagerData().getProfession() + " at "
				+ warden.blockPosition() + ", the water at " + helper.absolutePos(new BlockPos(6, 1, 4)) + ", "
				+ FirewardenWork.findWater(helper.getLevel(), helper.absolutePos(GRINDSTONE)) + ")"));
	}

	/** The water a Firewarden carries is saved with them. */
	//$ gametest_ticks_batch AREA '100' '"fireSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "fireSaved")
	public void theWaterCarriedSurvivesASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager warden = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));
		warden.setVillagerData(warden.getVillagerData().setProfession(ModVillagers.FIREWARDEN));
		helper.assertTrue(FirewardenWork.water(warden) == FirewardenWork.BUCKETS, "a new firewarden's buckets aren't full");
		FirewardenWork.setWater(warden, 1);
		CompoundTag tag = warden.saveWithoutId(new CompoundTag());
		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(tag);
		helper.assertTrue(FirewardenWork.water(loaded) == 1 && ModAttachments.FIREWARDEN_WATER.has(loaded),
			"after a save and load the firewarden has " + FirewardenWork.water(loaded) + " buckets");
		loaded.discard();
		warden.discard();
		helper.succeed();
	}

	/** What burned is put back by the builder (Upkeep), once the fire is out. */
	//$ gametest_ticks_batch AREA '1200' '"fireUpkeep"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "fireUpkeep")
	public void theBuilderPutsBackWhatBurned(GameTestHelper helper) {
		Hut hut = hut(helper, "upkeep");
		boolean was = io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = was);
		ServerLevel level = hut.level();
		BlockPos chestPos = new BlockPos(6, 2, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 8));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, helper.absolutePos(bench));
		// The finished hut is the builder's to look after.
		BuildSiteManager.get(level).recordFinished(hut.id(), hut.placement(), builder.getUUID());
		List<BlockPos> burned = List.of(new BlockPos(13, 4, 14), new BlockPos(14, 4, 14));
		helper.runAfterDelay(2, () -> {
			for (BlockPos p : burned) {
				fire(helper, p); // the fire took the plank, as the game's fire does
			}
		});
		helper.runAfterDelay(30, () -> {
			for (BlockPos p : burned) {
				VillageFire.douse(level, helper.absolutePos(p), 0, null);
			}
		});
		helper.runAfterDelay(100, () -> {
			var site = ModAttachments.BUILDER_JOB.has(builder) ? io.github.jcondedata.aliveworkplace.build.Builders.activeSite(level, builder)
				: io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started after the fire");
		});
		helper.succeedWhen(() -> {
			for (BlockPos p : burned) {
				helper.assertBlockPresent(Blocks.OAK_LOG, p);
			}
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still repairing");
		});
	}

	/** Config {@code firewardens} off: a Firewarden already hired leaves the fire alone. */
	//$ gametest_ticks_batch AREA '300' '"fireOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "fireOff")
	public void switchedOffAFirewardenStandsIdle(GameTestHelper helper) {
		hut(helper, "off");
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		FirewardenWork.ENABLED = false;
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			fire(helper, ROOF_TOP.offset(1, 0, 1));
			fire(helper, ROOF_TOP.offset(-1, 0, 1));
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(fires(helper) > 0, "the fire was put out with firewardens off");
			helper.assertTrue(FirewardenWork.water(warden) == FirewardenWork.BUCKETS, "the firewarden threw water with firewardens off");
			helper.succeed();
		});
	}

	/** Every new sentence a player reads has its text. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("entity.minecraft.villager.firewarden", "entity.minecraft.zombie_villager.firewarden",
			"station.aliveworkplace.item.aliveworkplace.firewarden", "aliveworkplace.config.firewardens", "aliveworkplace.config.firewardens.tooltip",
			"message.aliveworkplace.firewarden.title", "message.aliveworkplace.firewarden.state.fire", "message.aliveworkplace.firewarden.state.water",
			"disaster.aliveworkplace.village_fire.name", "disaster.aliveworkplace.village_fire.start", "disaster.aliveworkplace.village_fire.foretold",
			"disaster.aliveworkplace.village_fire.end", "disaster.aliveworkplace.village_fire.burning", "disaster.aliveworkplace.village_fire.out",
			"chronicle.aliveworkplace.village_fire.start", "chronicle.aliveworkplace.village_fire.foretold", "chronicle.aliveworkplace.village_fire.end",
			"chronicle.aliveworkplace.village_fire.village", "chronicle.aliveworkplace.village_fire.out", "chronicle.aliveworkplace.village_fire.out_one",
			"chronicle.aliveworkplace.village_fire.brigade", "chronicle.aliveworkplace.village_fire.brigade_one",
			"chronicle.aliveworkplace.village_fire.burned_out", "chronicle.aliveworkplace.village_fire.burned_out_one")) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.assertTrue(Disasters.get(VillageFire.ID).map(d -> d.ignition().lightning() > 0.3 && d.ignition().burningArrow() == 0.25
			&& d.ignition().chimney() == 0.02).orElse(false), "village_fire.json isn't loaded as written: " + Disasters.get(VillageFire.ID));
		helper.succeed();
	}
}
