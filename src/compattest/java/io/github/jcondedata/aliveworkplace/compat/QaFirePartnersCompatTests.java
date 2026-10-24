package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * QA (2026-10-24) for the firewarden's Pokémon partners (ROADMAP 32.16): "With Cobblemon, pastured Water types within 16
 * blocks of the grindstone douse a fire at range every 2 s with their move's effect, as guards' partners strike (through
 * work/PokemonPartners; nothing without Cobblemon)". PastureCompatTests proves one Squirtle beside the grindstone puts a
 * fire out; these prove the limits: the 16-block edge (15 and 16 do, 17 doesn't), only Water types, only pastured ones,
 * one move every 2 seconds (each partner its own), never a fire the village didn't light (a player's netherrack hearth,
 * a fire outside the village), nothing at a grindstone without a firewarden, and nothing with {@code firewardens} off.
 *
 * <p>Each test is alone in its batch, in the huge area. The game's fire ticking is off while they run (and put back), so
 * a fire stays lit until something puts it out: a "still burning" is the partners' doing, not the fire's own.
 */
public class QaFirePartnersCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The firewarden's grindstone; the Pokémon stand east of it along the same row. */
	static final BlockPos GRINDSTONE = new BlockPos(2, 2, 15);
	/** The log the village fire burns on (the fire is on top of it): about 10 blocks from the Pokémon. */
	static final BlockPos LOG = new BlockPos(12, 2, 22);
	static final BlockPos HALL = new BlockPos(15, 2, 26);

	/** What a test changes and puts back: fire ticking, the firewardens switch and the village's size. */
	record Before(boolean fireTick, boolean enabled, int radius) {
	}

	/** The village (a hall, 20 across), fire ticking off, what is in memory forgotten; put back before the timeout. */
	static Before setUp(GameTestHelper helper, int timeout) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		GameRules.BooleanValue fireTick = level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
		Before before = new Before(fireTick.get(), FirewardenWork.ENABLED, VillageHalls.RADIUS);
		VillageHalls.RADIUS = 20;
		VillageFire.forget();
		VillageFire.resetPartners();
		fireTick.set(false, level.getServer());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5) {
				putBack(helper, before);
			}
		});
		return before;
	}

	static void putBack(GameTestHelper helper, Before before) {
		helper.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(before.fireTick(), helper.getLevel().getServer());
		FirewardenWork.ENABLED = before.enabled();
		VillageHalls.RADIUS = before.radius();
	}

	/** Runs {@code step}; if it fails, puts everything back first (a failed test ends there). */
	static Runnable guarded(GameTestHelper helper, Before before, Runnable step) {
		return () -> {
			try {
				step.run();
			} catch (RuntimeException | AssertionError e) {
				putBack(helper, before);
				throw e;
			}
		};
	}

	/** The last checks: {@code checks}, everything put back, and the test passes. */
	static void finish(GameTestHelper helper, Before before, Runnable checks) {
		try {
			checks.run();
		} finally {
			putBack(helper, before);
		}
		helper.succeed();
	}

	/** A firewarden at the grindstone (no AI: only the Pokémon put fires out); {@code checkIn} keeps them on duty each tick. */
	static Villager warden(GameTestHelper helper, boolean ai, boolean checkIn) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		Villager warden = helper.spawn(EntityType.VILLAGER, GRINDSTONE.offset(1, 0, 1));
		warden.setNoAi(!ai);
		Jobs.employ(level, warden, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, ModVillagers.FIREWARDEN);
		if (checkIn) {
			helper.onEachTick(() -> VillageFire.onDuty(warden, level.getGameTime()));
		}
		return warden;
	}

	/**
	 * A {@code species} kept in a pasture beside {@code at} and then held still exactly there (no AI, so it can't wander
	 * nearer or further than the test means).
	 */
	static Pokemon pastured(GameTestHelper helper, String species, BlockPos at) {
		BlockPos pasture = PastureCompatTests.pasture(helper, at.offset(0, 0, -2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pokemon pokemon = PastureCompatTests.pastured(helper, pasture, player, species, Direction.SOUTH);
		if (pokemon.getEntity() != null) {
			hold(helper, pokemon.getEntity(), at);
		}
		return pokemon;
	}

	/** The pastured {@code pokemon}'s entity, held still at {@code at}. */
	static PokemonEntity hold(GameTestHelper helper, Pokemon pokemon, BlockPos at) {
		PokemonEntity entity = pokemon.getEntity();
		helper.assertTrue(entity != null && entity.isAlive() && entity.getTethering() != null,
			"the pastured " + pokemon.getSpecies().getName() + " isn't out in its pasture");
		hold(helper, entity, at);
		return entity;
	}

	static void hold(GameTestHelper helper, PokemonEntity entity, BlockPos at) {
		entity.setNoAi(true);
		entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		var to = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(at));
		entity.teleportTo(to.x, to.y, to.z);
	}

	/** Sets the village fire on top of {@code log} (a log is put there first); returns the fire's absolute position. */
	static BlockPos light(GameTestHelper helper, BlockPos log) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(log, Blocks.OAK_LOG);
		BlockPos fire = helper.absolutePos(log.above());
		level.setBlockAndUpdate(fire, BaseFireBlock.getState(level, fire));
		return fire;
	}

	static boolean burning(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getBlockState(absolute).is(BlockTags.FIRE);
	}

	static int dousedBy(GameTestHelper helper, Villager warden) {
		return VillageFire.incident(helper.getLevel(), helper.absolutePos(HALL)).map(i -> i.dousedBy(warden.getUUID())).orElse(0);
	}

	/** The village noticed the fire (so a fire still burning later is a test of the partners, not of nothing to put out). */
	static void noticed(GameTestHelper helper, BlockPos fire) {
		helper.assertTrue(burning(helper, fire), "the fire didn't light");
		helper.assertTrue(VillageFire.incident(helper.getLevel(), helper.absolutePos(HALL)).map(i -> i.fires() >= 1).orElse(false),
			"the village didn't notice the fire, so this test proves nothing");
	}

	/** A Squirtle {@code distance} blocks east of the grindstone; true: the fire must go out (to the firewarden's credit). */
	private static void atDistance(GameTestHelper helper, int distance, boolean douses, int timeout) {
		Before before = setUp(helper, timeout);
		Villager warden = warden(helper, false, true);
		Pokemon squirtle = pastured(helper, "squirtle", GRINDSTONE.offset(distance, 0, 0));
		BlockPos[] fire = {null};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, squirtle, GRINDSTONE.offset(distance, 0, 0));
			fire[0] = light(helper, LOG);
			noticed(helper, fire[0]);
		}));
		if (douses) {
			helper.succeedWhen(() -> {
				helper.assertTrue(fire[0] != null && !burning(helper, fire[0]), "the fire still burns with a Squirtle " + distance + " blocks from the grindstone");
				helper.assertTrue(dousedBy(helper, warden) > 0, "the fire went out, but not to the firewarden's credit");
				putBack(helper, before);
			});
		} else {
			helper.runAfterDelay(250, () -> finish(helper, before, () -> {
				helper.assertTrue(burning(helper, fire[0]), "a Squirtle " + distance + " blocks from the grindstone (over 16) put the fire out");
				helper.assertTrue(dousedBy(helper, warden) == 0, "doused by the firewarden's partners: " + dousedBy(helper, warden));
			}));
		}
	}

	/** A Squirtle 15 blocks from the grindstone (inside the 16) puts the village fire out. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartners15"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartners15")
	public void aWaterTypeFifteenBlocksAwayDouses(GameTestHelper helper) {
		atDistance(helper, 15, true, 300);
	}

	/** A Squirtle 16 blocks from the grindstone (the edge) still puts it out. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartners16"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartners16")
	public void aWaterTypeSixteenBlocksAwayDouses(GameTestHelper helper) {
		atDistance(helper, 16, true, 300);
	}

	/** A Squirtle 17 blocks from the grindstone is out of it: the fire burns on. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartners17"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartners17")
	public void aWaterTypeSeventeenBlocksAwayDoesNot(GameTestHelper helper) {
		atDistance(helper, 17, false, 300);
	}

	/** A Charmander (Fire) pastured by the grindstone, alone: no move on the fire. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartnersCharmander"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartnersCharmander")
	public void aNonWaterTypeDoesNotDouse(GameTestHelper helper) {
		Before before = setUp(helper, 300);
		Villager warden = warden(helper, false, true);
		Pokemon charmander = pastured(helper, "charmander", GRINDSTONE.offset(6, 0, 0));
		BlockPos[] fire = {null};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, charmander, GRINDSTONE.offset(6, 0, 0));
			fire[0] = light(helper, LOG);
			noticed(helper, fire[0]);
		}));
		helper.runAfterDelay(250, () -> finish(helper, before, () -> {
			helper.assertTrue(burning(helper, fire[0]), "a Charmander put the fire out");
			helper.assertTrue(dousedBy(helper, warden) == 0, "doused by partners: " + dousedBy(helper, warden));
		}));
	}

	/** A wild Squirtle (not in a pasture) standing by the grindstone: no move on the fire. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartnersWild"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartnersWild")
	public void aWaterTypeNotPasturedDoesNotDouse(GameTestHelper helper) {
		Before before = setUp(helper, 300);
		ServerLevel level = helper.getLevel();
		Villager warden = warden(helper, false, true);
		Pokemon pokemon = PokemonProperties.Companion.parse("squirtle level=10", " ", "=").create();
		PokemonEntity wild = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
		wild.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(GRINDSTONE.offset(6, 0, 0))));
		level.addFreshEntity(wild);
		hold(helper, wild, GRINDSTONE.offset(6, 0, 0));
		BlockPos[] fire = {null};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			helper.assertTrue(wild.isAlive() && wild.getTethering() == null, "the wild Squirtle is gone or pastured");
			hold(helper, wild, GRINDSTONE.offset(6, 0, 0));
			fire[0] = light(helper, LOG);
			noticed(helper, fire[0]);
		}));
		helper.runAfterDelay(250, () -> finish(helper, before, () -> {
			helper.assertTrue(wild.isAlive(), "the wild Squirtle left (the test proves nothing)");
			helper.assertTrue(burning(helper, fire[0]), "a wild Squirtle (not pastured) put the fire out");
			helper.assertTrue(dousedBy(helper, warden) == 0, "doused by partners: " + dousedBy(helper, warden));
			wild.discard();
		}));
	}

	/** A grindstone with a Squirtle pastured by it but no firewarden at it: nobody's partners, nothing doused. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartnersNoWarden"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartnersNoWarden")
	public void noFirewardenNoPartners(GameTestHelper helper) {
		Before before = setUp(helper, 300);
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		Pokemon squirtle = pastured(helper, "squirtle", GRINDSTONE.offset(6, 0, 0));
		BlockPos[] fire = {null};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, squirtle, GRINDSTONE.offset(6, 0, 0));
			fire[0] = light(helper, LOG);
			noticed(helper, fire[0]);
		}));
		helper.runAfterDelay(250, () -> finish(helper, before, () -> {
			helper.assertTrue(burning(helper, fire[0]), "a Squirtle by a grindstone with no firewarden put the fire out");
		}));
	}

	/**
	 * Every 2 s, not faster: one Squirtle and three village fires 5 blocks apart (one move can't reach two) go out one
	 * at a time, exactly 40 ticks apart.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaFirePartnersCadence"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFirePartnersCadence")
	public void oneMoveEveryTwoSeconds(GameTestHelper helper) {
		Before before = setUp(helper, 400);
		warden(helper, false, true);
		Pokemon squirtle = pastured(helper, "squirtle", GRINDSTONE.offset(6, 0, 0));
		List<BlockPos> logs = List.of(new BlockPos(8, 2, 22), new BlockPos(13, 2, 22), new BlockPos(18, 2, 22));
		List<BlockPos> fires = new ArrayList<>();
		long[] outAt = {-1, -1, -1};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, squirtle, GRINDSTONE.offset(6, 0, 0));
			for (BlockPos log : logs) {
				fires.add(light(helper, log));
			}
			fires.forEach(f -> noticed(helper, f));
		}));
		helper.onEachTick(() -> {
			for (int i = 0; i < fires.size(); i++) {
				if (outAt[i] < 0 && !burning(helper, fires.get(i))) {
					outAt[i] = helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fires.size() == 3 && outAt[0] >= 0 && outAt[1] >= 0 && outAt[2] >= 0,
				"not all three fires are out yet: " + java.util.Arrays.toString(outAt));
			long[] sorted = outAt.clone();
			java.util.Arrays.sort(sorted);
			helper.assertTrue(sorted[1] - sorted[0] == 40 && sorted[2] - sorted[1] == 40,
				"one Squirtle's fires went out at ticks " + java.util.Arrays.toString(outAt) + ": not one every 40 ticks");
			putBack(helper, before);
		});
	}

	/** Each partner its own move: two Squirtles and two fires far apart, both out on the same tick. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartnersEach"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartnersEach")
	public void eachWaterTypeUsesItsOwnMove(GameTestHelper helper) {
		Before before = setUp(helper, 300);
		warden(helper, false, true);
		Pokemon one = pastured(helper, "squirtle", GRINDSTONE.offset(6, 0, -6));
		Pokemon two = pastured(helper, "squirtle", GRINDSTONE.offset(6, 0, 4));
		List<BlockPos> fires = new ArrayList<>();
		long[] outAt = {-1, -1};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, one, GRINDSTONE.offset(6, 0, -6));
			hold(helper, two, GRINDSTONE.offset(6, 0, 4));
			fires.add(light(helper, new BlockPos(8, 2, 22)));
			fires.add(light(helper, new BlockPos(16, 2, 22)));
			fires.forEach(f -> noticed(helper, f));
		}));
		helper.onEachTick(() -> {
			for (int i = 0; i < fires.size(); i++) {
				if (outAt[i] < 0 && !burning(helper, fires.get(i))) {
					outAt[i] = helper.getTick();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fires.size() == 2 && outAt[0] >= 0 && outAt[1] >= 0, "not both fires are out: " + java.util.Arrays.toString(outAt));
			helper.assertTrue(outAt[0] == outAt[1], "two Squirtles' fires went out at ticks " + java.util.Arrays.toString(outAt) + ": not one move each");
			putBack(helper, before);
		});
	}

	/** {@code firewardens} off: the firewarden (with AI, as in play) stands idle and their Squirtle never douses the fire. */
	//$ gametest_ticks_batch AREA '300' '"qaFirePartnersOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFirePartnersOff")
	public void firewardensOffMeansNoPartners(GameTestHelper helper) {
		Before before = setUp(helper, 300);
		FirewardenWork.ENABLED = false;
		Villager warden = warden(helper, true, false);
		Pokemon squirtle = pastured(helper, "squirtle", GRINDSTONE.offset(6, 0, 0));
		BlockPos[] fire = {null};
		helper.runAfterDelay(10, guarded(helper, before, () -> {
			hold(helper, squirtle, GRINDSTONE.offset(6, 0, 0));
			fire[0] = light(helper, LOG);
			noticed(helper, fire[0]);
		}));
		helper.runAfterDelay(250, () -> finish(helper, before, () -> {
			helper.assertTrue(burning(helper, fire[0]), "with firewardens off the fire was put out");
			helper.assertTrue(dousedBy(helper, warden) == 0, "doused by the firewarden or their partners: " + dousedBy(helper, warden));
		}));
	}
}
