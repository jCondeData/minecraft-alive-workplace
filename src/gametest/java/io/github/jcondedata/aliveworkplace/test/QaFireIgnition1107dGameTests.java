package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1107-0108), from 32.16's spec (the village fire): a raider's burning arrow sets alight only a builder's
 * wooden block, so a player's wooden block in the village and a builder's stone are never lit, however lucky the arrow;
 * and the chronicle says the bucket brigade put a fire out only when it did (a bucket thrown where the fire had already
 * gone out is no credit: a fire that then burns out on its own "burned out"). Both from survivors of a mutation run on
 * VillageFire (:753 a flammable-or-builder's check, :557 credit for nothing put out). And the chimney spark, which no
 * test reached (:731): by its chance it sets a builder's wooden build alight only on a dry day (at least the data file's
 * 5 days without rain) or while a drought is under way, never on a wet day, before a drought begins or in a plague.
 */
public class QaFireIgnition1107dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(19, 2, 2);
	/** The hut's corner: logs at its four corners (y 2 and 3), a cobblestone in its front wall, a 5 x 5 roof of logs at y 4. */
	private static final BlockPos HUT = new BlockPos(12, 2, 12);
	private static final BlockPos ROOF_TOP = new BlockPos(14, 5, 14);
	private static final BlockPos STONE = HUT.offset(2, 0, 0);
	private static final BlockPos PLAYERS_PLANKS = new BlockPos(6, 2, 6);

	/** A village (its hall) with the hut finished by the builders, fire ticking, no rain, every other test's leftovers gone. */
	private static ServerLevel hut(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		VillageFire.reset();
		level.setWeatherParameters(6000, 0, false, false);
		level.setRainLevel(0f);
		level.setThunderLevel(0f);
		// No Firewarden of another test may answer this fire (they answer from 48 blocks).
		Vec3 middle = helper.getBounds().getCenter();
		for (Villager other : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class), FirewardenWork::isFirewarden)) {
			if (other.distanceToSqr(middle) <= (VillageFire.ANSWER + 32) * (VillageFire.ANSWER + 32)) {
				other.discard();
			}
		}
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			Sieges.DAMAGE = true;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
				if (level.getBlockState(p).is(BlockTags.FIRE)) {
					level.removeBlock(p, false);
				}
			}
			VillageFire.reset();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		helper.setBlock(STONE, Blocks.COBBLESTONE);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qafire_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		// A player's own wooden block in the village, out of the hut's blueprint.
		helper.setBlock(PLAYERS_PLANKS, Blocks.OAK_PLANKS);
		return level;
	}

	/** A random that always rolls 0: every chance comes up. */
	private static RandomSource lucky() {
		return new LegacyRandomSource(1L) {
			@Override
			public double nextDouble() {
				return 0.0;
			}

			@Override
			public float nextFloat() {
				return 0f;
			}
		};
	}

	private static BlockHitResult onTop(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
	}

	/**
	 * A raider's burning arrow, however lucky, never lights a player's plank block in the village nor the builders' own
	 * cobblestone; the same arrow does light the builders' log roof (so the arrow could have lit them).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFireArrowWhat1107d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFireArrowWhat1107d")
	public void aRaidersArrowLightsOnlyTheBuildersWood(GameTestHelper helper) {
		ServerLevel level = hut(helper, "arrow");
		Skeleton raider = helper.spawn(EntityType.SKELETON, new BlockPos(5, 2, 18));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
			BlockPos planks = helper.absolutePos(PLAYERS_PLANKS);
			BlockPos stone = helper.absolutePos(STONE);
			for (int i = 0; i < 20; i++) {
				Optional<BlockPos> f = VillageFire.arrow(level, raider, onTop(planks), lucky());
				helper.assertTrue(f.isEmpty() && !level.getBlockState(planks.above()).is(BlockTags.FIRE), "a raider's arrow lit a player's plank block in the village: " + f);
				f = VillageFire.arrow(level, raider, onTop(stone), lucky());
				helper.assertTrue(f.isEmpty() && !level.getBlockState(stone.above()).is(BlockTags.FIRE), "a raider's arrow set fire on the builders' cobblestone: " + f);
			}
			BlockPos roof = helper.absolutePos(ROOF_TOP.below());
			Optional<BlockPos> lit = VillageFire.arrow(level, raider, onTop(roof), lucky());
			helper.assertTrue(lit.isPresent() && lit.get().equals(roof.above()), "(control) the lucky arrow didn't light the builders' log roof: " + lit);
			level.removeBlock(lit.get(), false);
			helper.succeed();
		});
	}

	/**
	 * A fire on the hut's roof; the bucket brigade throws a bucket on a spot of the roof with no fire (nothing goes out),
	 * then the fire is gone by itself (taken away by hand, as one that burned out). Once it's over, the chronicle says the
	 * fire burned out, not that the bucket brigade put it out.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFireNoCredit1107d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFireNoCredit1107d")
	public void aBucketOnNoFireIsNoCreditForTheBrigade(GameTestHelper helper) {
		ServerLevel level = hut(helper, "credit");
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			BlockPos fire = helper.absolutePos(ROOF_TOP);
			level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
			helper.assertTrue(VillageFire.incident(level, hall).isPresent(), "(setup) the fire on the roof wasn't noticed");
			int out = VillageFire.douse(level, fire.offset(-2, 0, -2), 0, null);
			helper.assertTrue(out == 0, "(setup) the brigade's bucket put out " + out + " fires where there was none");
			level.removeBlock(fire, false);
			helper.runAfterDelay(VillageFire.QUIET + 40, () -> {
				helper.assertTrue(VillageFire.incident(level, hall).isEmpty(), "the fire never ended");
				helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "(setup) no hall");
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				Optional<Chronicle.Entry> entry = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FIRE).reduce((a, b) -> b);
				helper.assertTrue(entry.isPresent(), "the chronicle has no word of the fire");
				String key = entry.get().text().getContents() instanceof TranslatableContents t ? t.getKey() : "";
				helper.assertTrue(key.startsWith("chronicle.aliveworkplace.village_fire.burned_out"),
					"the chronicle credits someone with a fire that burned out by itself: " + entry.get().text().getString());
				helper.succeed();
			});
		});
	}

	/**
	 * A fire of a single block on the hut's roof, put out by one bucket of the brigade: once it's over, the chronicle says
	 * the bucket brigade put it out (one block out is as much a credit as many).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFireOneCredit1107d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFireOneCredit1107d")
	public void oneFireOutIsCreditForTheBrigade(GameTestHelper helper) {
		ServerLevel level = hut(helper, "one");
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(2, () -> {
			BlockPos fire = helper.absolutePos(ROOF_TOP);
			level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
			helper.assertTrue(VillageFire.incident(level, hall).isPresent(), "(setup) the fire on the roof wasn't noticed");
			int out = VillageFire.douse(level, fire, 0, null);
			helper.assertTrue(out == 1, "the brigade's bucket put out " + out + " fires, not the one");
			helper.runAfterDelay(VillageFire.QUIET + 40, () -> {
				helper.assertTrue(VillageFire.incident(level, hall).isEmpty(), "the fire never ended");
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				Optional<Chronicle.Entry> entry = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FIRE).reduce((a, b) -> b);
				helper.assertTrue(entry.isPresent(), "the chronicle has no word of the fire");
				String key = entry.get().text().getContents() instanceof TranslatableContents t ? t.getKey() : "";
				helper.assertTrue(key.startsWith("chronicle.aliveworkplace.village_fire.brigade"),
					"the brigade put the one fire out, but the chronicle says: " + entry.get().text().getString());
				helper.succeed();
			});
		});
	}

	/** The world's days without rain set to {@code dry} (it last rained that many days ago); put back afterwards. */
	private static void dry(GameTestHelper helper, ServerLevel level, long dry) {
		long today = Chronicle.day(level);
		Disasters.rainedOn(level, today - dry);
		helper.assertTrue(Disasters.dryDays(level) == dry, "(setup) the dry days read " + Disasters.dryDays(level) + ", not " + dry);
	}

	private static RandomSource unlucky() {
		return new LegacyRandomSource(1L) {
			@Override
			public double nextDouble() {
				return 0.999;
			}
		};
	}

	/** The hall, its census and the builders' hut: the spark's roll with {@code random}; any fire it set taken away again. */
	private static Optional<BlockPos> spark(GameTestHelper helper, ServerLevel level, RandomSource random) {
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Optional<BlockPos> fire = VillageFire.chimneySpark(level, hall, entity, VillageHalls.census(level, hall), random);
		fire.ifPresent(f -> {
			helper.assertTrue(level.getBlockState(f).is(BlockTags.FIRE) && level.getBlockState(f.below()).is(Blocks.OAK_LOG)
				&& f.getY() == helper.absolutePos(ROOF_TOP).getY(), "the spark's fire isn't on the hut's roof: " + f + " over " + level.getBlockState(f.below()));
			level.removeBlock(f, false);
		});
		return fire;
	}

	/**
	 * The chimney spark on a dry day: with 6 and with exactly 5 days without rain a lucky roll sets the hut's roof alight;
	 * with 4 days, or rain today, even the luckiest roll sets nothing alight; and on a dry day an unlucky roll sets none.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFireChimneyDry1107d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFireChimneyDry1107d")
	public void aChimneySparksOnlyOnADryDay(GameTestHelper helper) {
		ServerLevel level = hut(helper, "chimney");
		long was = Disasters.dryDays(level);
		Leftovers.after(helper, () -> Disasters.rainedOn(level, Chronicle.day(level) - was));
		helper.runAfterDelay(2, () -> {
			dry(helper, level, 6);
			helper.assertTrue(spark(helper, level, lucky()).isPresent(), "6 days without rain, a lucky spark set nothing alight");
			dry(helper, level, 5);
			helper.assertTrue(spark(helper, level, lucky()).isPresent(), "5 days without rain (the file's dry_days), a lucky spark set nothing alight");
			dry(helper, level, 4);
			helper.assertTrue(spark(helper, level, lucky()).isEmpty(), "only 4 days without rain, a spark set the hut alight");
			dry(helper, level, 0);
			helper.assertTrue(spark(helper, level, lucky()).isEmpty(), "it rained today, yet a spark set the hut alight");
			dry(helper, level, 6);
			helper.assertTrue(spark(helper, level, unlucky()).isEmpty(), "an unlucky roll (0.999 against 2%) set the hut alight");
			helper.succeed();
		});
	}

	/**
	 * A drought under way sparks the chimney even the day it rained; one that begins tomorrow doesn't yet, nor does a plague
	 * under way.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFireChimneyDrought1107d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFireChimneyDrought1107d")
	public void aDroughtUnderWaySparksTheChimneyEvenAfterRain(GameTestHelper helper) {
		ServerLevel level = hut(helper, "drought");
		long was = Disasters.dryDays(level);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Disasters.rainedOn(level, Chronicle.day(level) - was);
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				entity.setDisaster(null, -1, -1, -100);
			}
		});
		helper.runAfterDelay(2, () -> {
			dry(helper, level, 0);
			long today = Chronicle.day(level);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setDisaster(Drought.ID, today, today + 5, today);
			helper.assertTrue(spark(helper, level, lucky()).isPresent(), "a drought under way (since today), but a lucky spark set nothing alight");
			entity.setDisaster(Drought.ID, today + 1, today + 6, today);
			helper.assertTrue(spark(helper, level, lucky()).isEmpty(), "a drought that begins tomorrow already sparked the chimney");
			entity.setDisaster(Plague.ID, today, today + 5, today);
			helper.assertTrue(spark(helper, level, lucky()).isEmpty(), "a plague under way sparked the chimney as a drought does");
			helper.succeed();
		});
	}

	/**
	 * The hall's daily round rolls the chimney spark once a day: on a dry day a lucky first roll sets the hut alight, a
	 * second the same day sets nothing alight however lucky, and the next day it rolls again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFireChimneyDaily1107d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFireChimneyDaily1107d")
	public void theChimneyRollsOnceADay(GameTestHelper helper) {
		ServerLevel level = hut(helper, "daily");
		long was = Disasters.dryDays(level);
		long time = level.getDayTime();
		boolean rolling = Disasters.ROLLING;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Disasters.ROLLING = rolling;
			level.setDayTime(time);
			Disasters.rainedOn(level, Chronicle.day(level) - was);
		});
		helper.runAfterDelay(2, () -> {
			Disasters.ROLLING = true;
			dry(helper, level, 6);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			Optional<BlockPos> first = VillageFire.chimney(level, hall, entity, VillageHalls.census(level, hall), lucky());
			first.ifPresent(f -> level.removeBlock(f, false));
			helper.assertTrue(first.isPresent(), "a lucky spark on a dry day (the day's first roll) set nothing alight");
			Optional<BlockPos> again = VillageFire.chimney(level, hall, entity, VillageHalls.census(level, hall), lucky());
			again.ifPresent(f -> level.removeBlock(f, false));
			helper.assertTrue(again.isEmpty(), "the chimney sparked twice in one day: " + again);
			level.setDayTime(level.getDayTime() + io.github.jcondedata.aliveworkplace.hall.VillageNeeds.DAY);
			dry(helper, level, 7);
			Optional<BlockPos> tomorrow = VillageFire.chimney(level, hall, entity, VillageHalls.census(level, hall), lucky());
			tomorrow.ifPresent(f -> level.removeBlock(f, false));
			helper.assertTrue(tomorrow.isPresent(), "the next day the chimney didn't roll again");
			helper.succeed();
		});
	}

	/**
	 * A fire burning on top of a player's plank block in the village (one at its last age, as any fire gets): the fire may
	 * not burn the player's block, so it goes out by itself in time, the planks whole; it doesn't linger there for good.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaFireOnPlayersPlanks1107d"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaFireOnPlayersPlanks1107d")
	public void aFireOnAPlayersPlanksInTheVillageGoesOut(GameTestHelper helper) {
		ServerLevel level = hut(helper, "planks");
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
			BlockPos planks = helper.absolutePos(PLAYERS_PLANKS);
			BlockPos fire = planks.above();
			level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire).setValue(net.minecraft.world.level.block.FireBlock.AGE, 15));
			helper.assertTrue(level.getBlockState(fire).is(BlockTags.FIRE), "(setup) no fire on the planks");
			helper.succeedWhen(() -> {
				helper.assertTrue(!level.getBlockState(fire).is(BlockTags.FIRE), "the fire on the player's planks is still burning");
				helper.assertTrue(level.getBlockState(planks).is(Blocks.OAK_PLANKS), "the player's planks burned: " + level.getBlockState(planks));
			});
		});
	}
}
