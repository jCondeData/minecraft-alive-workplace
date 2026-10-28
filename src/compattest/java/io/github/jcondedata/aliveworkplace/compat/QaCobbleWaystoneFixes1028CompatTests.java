package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-d-1028-0708), B622 and B623 (29.22 the Pokémon Ranger): "once a day they befriend a wild Pokémon
 * within 64 blocks", and the fix "the day counts as used only when a befriending walk starts". What the Ranger tests
 * already there leave out: an owner set, or a pasture slot freed, later the same day; a walk that started and came to
 * nothing still spends the day; the next day brings a new befriending; the spent day survives a restart; and the
 * 64-block reach from the hall, 64 and 65 blocks out, through the Ranger's own look.
 *
 * <p>B528 (41.2 village waystones): "a village whose plaza is gone (demolished, forgotten) leaves the list at its next
 * hall round", and a neighbor's plaza within the hall radius doesn't keep it on. What {@link WaystoneQaBugCompatTests}
 * leaves out: the plaza forgotten while its stone still stands, a neighbor's plaza at the very edge of the radius, and
 * the village coming back once its plaza is built again.
 */
public class QaCobbleWaystoneFixes1028CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos PASTURE = new BlockPos(6, 2, 6);
	private static final BlockPos RANGER = new BlockPos(7, 2, 9);
	/** Beside the Ranger (within 3 blocks), so a walk that starts turns at once into leading it. */
	private static final BlockPos BESIDE = new BlockPos(8, 2, 9);
	private static final String[] KINDS = {"rattata", "meowth", "eevee", "snorlax", "caterpie", "charmander", "vulpix", "growlithe", "psyduck"};
	private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

	/** This class's own chunk ticket for the far reach spots (B647: never the "forced" flag other tests share). */
	private static final TicketType<ChunkPos> TICKET = TicketType.create("aliveworkplace_qa_ranger1028", Comparator.comparingLong(ChunkPos::toLong));

	/** A hall in the middle of the area (radius 14), Legend needs off, Pokémon far round cleared; all put back after. */
	private static void setUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
		level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
		int radius = VillageHalls.RADIUS;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 14;
		LegendNeeds.ENABLED = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
		// past the morning: no Alpha to calm first, only the day's befriending
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static Villager ranger(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, Legends.get(PokemonRanger.ID).orElseThrow(() -> new AssertionError("pokemon_ranger.json didn't load")), "test");
		LegendPowers.forget();
		return v;
	}

	private static PokemonEntity wild(GameTestHelper helper, String properties, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse(properties, " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	/** Held where it was put (the far spots lie over other tests' areas, where it could fall or choke). */
	private static PokemonEntity pinned(PokemonEntity e) {
		e.setNoGravity(true);
		e.setInvulnerable(true);
		e.setDeltaMovement(Vec3.ZERO);
		return e;
	}

	private static ServerPlayer owner(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		hall(helper).setOwner(player.getUUID(), player.getGameProfile().getName());
		return player;
	}

	private static PokemonPastureBlockEntity block(GameTestHelper helper, BlockPos pasture) {
		return (PokemonPastureBlockEntity) helper.getLevel().getBlockEntity(pasture);
	}

	private static boolean tetheredAs(PokemonPastureBlockEntity block, UUID owner, String species) {
		return block.getTetheredPokemon().stream().anyMatch(t -> t.getPokemon() != null && owner.equals(t.getPlayerId())
			&& owner.equals(t.getPokemon().getOwnerUUID()) && t.getPokemon().getSpecies().getResourceIdentifier().getPath().equals(species));
	}

	private static boolean stillWild(PokemonEntity e) {
		return e.isAlive() && e.getPokemon().getOwnerUUID() == null;
	}

	// ---- B622: the day is spent only when a walk starts ------------------------------------------------------------

	/**
	 * The hall has no owner at the Ranger's first looks of the day, so nothing is befriended; the player claims the hall
	 * later that day, and the wild Pikachu beside the Ranger is befriended that same day, into the pasture as theirs.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_ranger1028_owner"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_ranger1028_owner")
	public void anOwnerSetLaterTheSameDayGetsThatDaysBefriending(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		PokemonEntity pikachu = wild(helper, "pikachu level=10", BESIDE);
		Villager ranger = ranger(helper, RANGER);
		helper.runAfterDelay(5, () -> {
			long today = Chronicle.day(level);
			for (int i = 0; i < 3; i++) {
				PokemonRanger.tick(ranger); // the day's first looks: no owner yet
			}
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a walk with no owner: " + PokemonRanger.state(ranger));
			helper.assertTrue(stillWild(pikachu), "the Pikachu was taken with no owner");
			ServerPlayer player = owner(helper); // claimed later the same day
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() != PokemonRanger.NONE,
				"the hall got an owner later the same day, and the Ranger doesn't walk to the wild Pikachu beside them: the day was spent on the looks before ("
					+ PokemonRanger.state(ranger) + ")");
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(tetheredAs(block(helper, pasture), player.getUUID(), "pikachu"), "the Pikachu isn't the new owner's, in the pasture");
			helper.assertTrue(PokemonRanger.state(ranger).befriendDay() == today, "the day's befriending isn't today's: " + PokemonRanger.state(ranger));
			helper.assertTrue(Chronicle.day(level) == today, "the test ran into the next day");
			helper.succeed();
		});
	}

	/**
	 * The village's only pasture is full at the Ranger's first looks of the day; the owner takes one Pokémon out later that
	 * day, and the wild Pikachu beside the Ranger goes into the freed place that same day.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_ranger1028_full"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_ranger1028_full")
	public void aPastureSlotFreedLaterTheSameDayIsFilled(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = owner(helper);
		PokemonPastureBlockEntity block = block(helper, pasture);
		int max = block.getMaxTethered();
		helper.assertTrue(max > 0 && max <= 40, "the pasture's limit: " + max);
		for (int i = 0; i < max; i++) {
			PastureCompatTests.pastured(helper, pasture, player, KINDS[i % KINDS.length], SIDES[i % 4]);
		}
		PokemonEntity pikachu = wild(helper, "pikachu level=10", BESIDE);
		Villager ranger = ranger(helper, RANGER);
		helper.runAfterDelay(10, () -> {
			long today = Chronicle.day(level);
			helper.assertTrue(block.getTetheredPokemon().size() == max, "the pasture isn't full: " + block.getTetheredPokemon().size() + " of " + max);
			for (int i = 0; i < 3; i++) {
				PokemonRanger.tick(ranger); // the day's first looks: no room
			}
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a walk for a full pasture: " + PokemonRanger.state(ranger));
			helper.assertTrue(stillWild(pikachu), "the Pikachu was taken with the pasture full");
			// later that day the owner takes one out
			block.releasePokemon(block.getTetheredPokemon().get(0).getPokemon().getUuid());
			helper.assertTrue(block.getTetheredPokemon().size() == max - 1, "taking one out left " + block.getTetheredPokemon().size() + " of " + max);
			PokemonRanger.tick(ranger);
			helper.assertTrue(PokemonRanger.state(ranger).task() != PokemonRanger.NONE,
				"a place in the pasture freed later the same day, and the Ranger doesn't walk to the wild Pikachu beside them: the day was spent on the looks before ("
					+ PokemonRanger.state(ranger) + ")");
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(tetheredAs(block, player.getUUID(), "pikachu"), "the Pikachu isn't in the freed place, as the owner's");
			helper.assertTrue(block.getTetheredPokemon().size() == max, "the pasture holds " + block.getTetheredPokemon().size() + " of " + max);
			helper.assertTrue(PokemonRanger.state(ranger).befriendDay() == today, "the day's befriending isn't today's: " + PokemonRanger.state(ranger));
			helper.succeed();
		});
	}

	/**
	 * Still once a day: the Ranger sets out for a wild Pikachu; a Bulbasaur turns up beside them meanwhile and isn't
	 * taken instead; the Pikachu is gone before they reach it, and the Bulbasaur still isn't befriended that day (the
	 * walk that started spent the day).
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_ranger1028_once"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_ranger1028_once")
	public void aWalkThatStartedSpendsTheDayEvenIfItComesToNothing(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = owner(helper);
		PokemonEntity pikachu = wild(helper, "pikachu level=10", new BlockPos(20, 2, 22)); // about 18 blocks off
		Villager ranger = ranger(helper, RANGER);
		helper.runAfterDelay(5, () -> {
			long today = Chronicle.day(level);
			PokemonRanger.tick(ranger);
			PokemonRanger.State set = PokemonRanger.state(ranger);
			helper.assertTrue(set.task() == PokemonRanger.FETCH && set.target().map(pikachu.getUUID()::equals).orElse(false),
				"the Ranger didn't set out for the Pikachu: " + set);
			helper.assertTrue(set.befriendDay() == today, "the walk started but the day isn't spent: " + set);
			PokemonEntity bulbasaur = wild(helper, "bulbasaur level=10", BESIDE);
			helper.runAfterDelay(3, () -> {
				PokemonRanger.tick(ranger);
				PokemonRanger.tick(ranger);
				helper.assertTrue(PokemonRanger.state(ranger).target().map(pikachu.getUUID()::equals).orElse(false),
					"the Ranger turned from the Pikachu to the Bulbasaur: " + PokemonRanger.state(ranger));
				pikachu.discard(); // gone before the Ranger reaches it
				for (int i = 0; i < 4; i++) {
					PokemonRanger.tick(ranger);
				}
				helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE,
					"a second befriending walk the same day, for the Bulbasaur: " + PokemonRanger.state(ranger));
				helper.assertTrue(stillWild(bulbasaur), "the Bulbasaur was befriended the same day as the walk for the Pikachu");
				helper.assertTrue(block(helper, pasture).getTetheredPokemon().isEmpty(), "something went into the pasture");
				helper.assertTrue(PokemonRanger.state(ranger).befriendDay() == today, "the day: " + PokemonRanger.state(ranger));
				helper.assertTrue(player.isAlive(), "the owner left");
				helper.succeed();
			});
		});
	}

	/**
	 * The next day brings a new befriending: the Ranger befriends a Pikachu, a Bulbasaur beside them waits out the rest of
	 * the day wild, and the next day it's befriended too; the pasture then holds both, the owner's.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_ranger1028_nextday"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_ranger1028_nextday")
	public void theNextDayBringsANewBefriending(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = owner(helper);
		PokemonEntity pikachu = wild(helper, "pikachu level=10", BESIDE);
		Villager ranger = ranger(helper, RANGER);
		helper.runAfterDelay(5, () -> {
			long today = Chronicle.day(level);
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			PokemonRanger.tick(ranger);
			helper.assertTrue(tetheredAs(block(helper, pasture), player.getUUID(), "pikachu"), "the first day's Pikachu wasn't befriended: " + PokemonRanger.state(ranger));
			helper.assertTrue(!pikachu.isAlive(), "the wild Pikachu is still out");
			PokemonEntity bulbasaur = wild(helper, "bulbasaur level=10", BESIDE);
			helper.runAfterDelay(3, () -> {
				PokemonRanger.tick(ranger);
				PokemonRanger.tick(ranger);
				helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE && stillWild(bulbasaur), "a second befriending the same day: " + PokemonRanger.state(ranger));
				level.setDayTime(level.getDayTime() + 24000L); // the next day, the same hour
				helper.assertTrue(Chronicle.day(level) == today + 1, "not the next day: " + Chronicle.day(level) + " after " + today);
				PokemonRanger.tick(ranger);
				helper.assertTrue(PokemonRanger.state(ranger).task() != PokemonRanger.NONE
						&& PokemonRanger.state(ranger).target().map(bulbasaur.getUUID()::equals).orElse(false),
					"the next day the Ranger doesn't set out for the wild Bulbasaur beside them: " + PokemonRanger.state(ranger));
				PokemonRanger.tick(ranger);
				PokemonRanger.tick(ranger);
				PokemonPastureBlockEntity block = block(helper, pasture);
				helper.assertTrue(tetheredAs(block, player.getUUID(), "bulbasaur") && tetheredAs(block, player.getUUID(), "pikachu"),
					"the pasture doesn't hold both days' Pokémon: " + block.getTetheredPokemon().size());
				helper.assertTrue(PokemonRanger.state(ranger).befriendDay() == today + 1, "the day: " + PokemonRanger.state(ranger));
				helper.succeed();
			});
		});
	}

	// ---- B623: the 64-block reach, measured from the hall ------------------------------------------------------------

	/** Keeps the chunk at {@code relative} loaded and ticking for this test, with this class's own ticket (B647). */
	private static void hold(GameTestHelper helper, BlockPos relative) {
		ServerLevel level = helper.getLevel();
		ChunkPos pos = new ChunkPos(helper.absolutePos(relative));
		level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
		level.getChunk(pos.x, pos.z);
		PartnerShowsCompatTests.after(helper, () -> level.getChunkSource().removeRegionTicket(TICKET, pos, 2, pos));
	}

	/** Runs {@code then} once the chunk at {@code relative} ticks entities and has loaded its own (up to 200 ticks). */
	private static void whenReady(GameTestHelper helper, BlockPos relative, int waited, Runnable then) {
		BlockPos abs = helper.absolutePos(relative);
		ServerLevel level = helper.getLevel();
		if (level.isPositionEntityTicking(abs) && level.areEntitiesLoaded(ChunkPos.asLong(abs))) {
			then.run();
		} else {
			helper.assertTrue(waited < 200, "the chunk at " + relative + " never took entities");
			helper.runAfterDelay(1, () -> whenReady(helper, relative, waited + 1, then));
		}
	}

	/**
	 * A wild Pokémon standing on the hall's floor exactly 64 blocks east of the hall is within 64 blocks: the Ranger
	 * (beside the hall) sets out for it.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_ranger1028_reach64"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_ranger1028_reach64")
	public void aWildPokemonExactly64BlocksFromTheHallIsBefriended(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = HALL.offset(64, 0, 0);
		hold(helper, at);
		PastureCompatTests.pasture(helper, PASTURE);
		owner(helper);
		Villager ranger = ranger(helper, HALL.offset(1, 0, 0));
		whenReady(helper, at, 0, () -> {
			PokemonEntity far = pinned(wild(helper, "pikachu level=10", at));
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(far.isAlive() && level.getEntity(far.getUUID()) == far, "the Pikachu 64 blocks out didn't spawn");
				double flat = Math.sqrt(Math.pow(far.getX() - (helper.absolutePos(HALL).getX() + 0.5), 2) + Math.pow(far.getZ() - (helper.absolutePos(HALL).getZ() + 0.5), 2));
				helper.assertTrue(Math.abs(flat - 64) < 0.01 && Math.abs(far.getY() - helper.absolutePos(HALL).getY()) < 0.01,
					"the Pikachu isn't 64 blocks out on the hall's floor: " + flat + " blocks, y " + far.getY());
				PokemonRanger.tick(ranger);
				PokemonRanger.State s = PokemonRanger.state(ranger);
				helper.assertTrue(s.task() == PokemonRanger.FETCH && s.target().map(far.getUUID()::equals).orElse(false),
					"the Ranger doesn't set out for a wild Pikachu exactly 64 blocks from the hall: " + s);
				helper.succeed();
			});
		});
	}

	/**
	 * One 65 blocks east of the hall isn't within 64, even though it is 64 from the Ranger (who stands a block east of
	 * the hall): no walk all day; a Pikachu 5 blocks from the hall then brings one (the control).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_ranger1028_reach65"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_ranger1028_reach65")
	public void aWildPokemon65BlocksFromTheHallIsNotBefriended(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = HALL.offset(65, 0, 0);
		hold(helper, at);
		PastureCompatTests.pasture(helper, PASTURE);
		owner(helper);
		Villager ranger = ranger(helper, HALL.offset(1, 0, 0));
		whenReady(helper, at, 0, () -> {
			PokemonEntity far = pinned(wild(helper, "pikachu level=10", at));
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(far.isAlive() && level.getEntity(far.getUUID()) == far, "the Pikachu 65 blocks out didn't spawn");
				for (int i = 0; i < 3; i++) {
					PokemonRanger.tick(ranger);
				}
				helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE,
					"the Ranger sets out for a wild Pikachu 65 blocks from the hall: " + PokemonRanger.state(ranger));
				helper.assertTrue(stillWild(far), "the Pikachu 65 blocks out was taken");
				PokemonEntity near = wild(helper, "pikachu level=10", HALL.offset(5, 0, 0));
				helper.runAfterDelay(3, () -> {
					PokemonRanger.tick(ranger);
					PokemonRanger.State s = PokemonRanger.state(ranger);
					helper.assertTrue(s.task() != PokemonRanger.NONE && s.target().map(near.getUUID()::equals).orElse(false),
						"the control: the Ranger doesn't set out for a Pikachu 5 blocks from the hall either: " + s);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The spent day survives a restart: the Ranger befriends a Pikachu, is saved and loaded again (a server restart), and a
	 * Bulbasaur beside them isn't befriended the rest of that day; the next day the reloaded Ranger befriends it.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_ranger1028_reload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_ranger1028_reload")
	public void theSpentDaySurvivesARestart(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = owner(helper);
		wild(helper, "pikachu level=10", BESIDE);
		Villager ranger = ranger(helper, RANGER);
		helper.runAfterDelay(5, () -> {
			long today = Chronicle.day(level);
			for (int i = 0; i < 3; i++) {
				PokemonRanger.tick(ranger);
			}
			helper.assertTrue(tetheredAs(block(helper, pasture), player.getUUID(), "pikachu"), "the day's Pikachu wasn't befriended: " + PokemonRanger.state(ranger));
			Villager copy = reload(helper, ranger);
			helper.assertTrue(PokemonRanger.state(copy).befriendDay() == today, "after a restart the Ranger's day is " + PokemonRanger.state(copy) + ", not " + today);
			PokemonEntity bulbasaur = wild(helper, "bulbasaur level=10", BESIDE);
			helper.runAfterDelay(3, () -> {
				for (int i = 0; i < 3; i++) {
					PokemonRanger.tick(copy);
				}
				helper.assertTrue(PokemonRanger.state(copy).task() == PokemonRanger.NONE && stillWild(bulbasaur),
					"after a restart the Ranger befriends a second time the same day: " + PokemonRanger.state(copy));
				level.setDayTime(level.getDayTime() + 24000L);
				for (int i = 0; i < 3; i++) {
					PokemonRanger.tick(copy);
				}
				helper.assertTrue(tetheredAs(block(helper, pasture), player.getUUID(), "bulbasaur"),
					"the next day the reloaded Ranger doesn't befriend the Bulbasaur: " + PokemonRanger.state(copy));
				helper.succeed();
			});
		});
	}

	/** Saved and loaded again, as a restart does. */
	private static Villager reload(GameTestHelper helper, Villager villager) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		helper.assertTrue(copy != null, "no villager made");
		copy.load(tag);
		level.addFreshEntity(copy);
		LegendPowers.forget();
		helper.assertTrue(PokemonRanger.working(copy), "the reloaded villager isn't a working Ranger");
		return copy;
	}

	// ---- B528: a village whose plaza is gone leaves the network ------------------------------------------------------

	/**
	 * Brookmere's plaza forgotten (no longer a finished plaza) while its stone still stands: its next round takes it off
	 * the network all the same; Ashford's Map lists only Ashford, and the two villages are no longer linked by waystone.
	 */
	//$ gametest_batch AREA '"qa_waystone1028_forgotten"'
	@GameTest(template = AREA, batch = "qa_waystone1028_forgotten")
	public void aForgottenPlazaWithItsStoneStandingLeavesTheNetwork(GameTestHelper helper) {
		WaystoneQaCompatTests.cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			WaystoneQaCompatTests.villages(helper, true);
			BlockPos a = helper.absolutePos(WaystoneQaCompatTests.HALL_A);
			BlockPos b = helper.absolutePos(WaystoneQaCompatTests.HALL_B);
			helper.assertTrue(VillageWaystones.linked(level, a, b), "setup: the two villages aren't linked by waystone");
			BuildSiteManager.get(level).forgetFinished(WaystoneQaCompatTests.placement(helper, WaystoneQaCompatTests.PLAZA_B));
			helper.assertTrue(WaystoneQaCompatTests.standsAt(helper, WaystoneQaCompatTests.PLAZA_B), "setup: Brookmere's stone was taken down");
			VillageWaystones.round(level, b);
			VillageWaystones.round(level, a);
			helper.assertTrue(VillageWaystones.stoneOf(level, b).isEmpty(),
				"Brookmere's plaza is forgotten but the village is kept on the network with stone " + VillageWaystones.stoneOf(level, b) + " (its stone still stands)");
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(lines.equals(List.of("Waystone network (1):", "Ashford, this village: not discovered yet")), "Ashford's Map: " + lines);
			helper.assertFalse(VillageWaystones.linked(level, a, b), "Brookmere left the network but caravans still go to it by waystone");
		} finally {
			WaystoneQaCompatTests.cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The neighbor's plaza at the very edge of the hall radius (radius 20 here: 19.8 blocks from Brookmere's hall, 18.4
	 * from Ashford's, so Ashford's): Brookmere's plaza gone, its next round still takes it off; Ashford stays on.
	 */
	//$ gametest_batch AREA '"qa_waystone1028_edge"'
	@GameTest(template = AREA, batch = "qa_waystone1028_edge")
	public void aNeighborPlazaAtTheRadiusEdgeDoesNotKeepAGoneVillage(GameTestHelper helper) {
		BlockPos edge = new BlockPos(14, 2, 14);
		List<BlockPos> halls = List.of(WaystoneQaCompatTests.HALL_A, WaystoneQaCompatTests.HALL_B);
		List<BlockPos> plazas = List.of(WaystoneQaCompatTests.PLAZA_A, WaystoneQaCompatTests.PLAZA_B, edge);
		WaystoneQaCompatTests.cleanUp(helper, halls, plazas);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		try {
			WaystoneQaCompatTests.villages(helper, true);
			BlockPos a = helper.absolutePos(WaystoneQaCompatTests.HALL_A);
			BlockPos b = helper.absolutePos(WaystoneQaCompatTests.HALL_B);
			WaystoneQaCompatTests.breakStone(helper, WaystoneQaCompatTests.PLAZA_B);
			BuildSiteManager.get(level).forgetFinished(WaystoneQaCompatTests.placement(helper, WaystoneQaCompatTests.PLAZA_B));
			WaystoneQaCompatTests.plaza(helper, edge); // a second plaza of Ashford's
			BlockPos e = helper.absolutePos(edge);
			double toB = Math.sqrt(e.distSqr(b));
			helper.assertTrue(toB <= 20 && toB > 19.5, "setup: the edge plaza is " + toB + " blocks from Brookmere's hall");
			helper.assertTrue(VillageHalls.nearest(level, e).map(a::equals).orElse(false), "setup: the edge plaza's nearest hall isn't Ashford's: " + VillageHalls.nearest(level, e));
			VillageWaystones.round(level, b);
			VillageWaystones.round(level, a);
			helper.assertTrue(VillageWaystones.stoneOf(level, b).isEmpty(),
				"Brookmere has no plaza but is kept with stone " + VillageWaystones.stoneOf(level, b) + " (Ashford's plaza stands at the edge of its radius)");
			helper.assertTrue(VillageWaystones.standing(level, a), "Ashford left the network");
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(lines.size() == 2 && lines.stream().noneMatch(l -> l.contains("Brookmere")), "Ashford's Map: " + lines);
		} finally {
			VillageHalls.RADIUS = radius;
			WaystoneQaCompatTests.cleanUp(helper, halls, plazas);
		}
		helper.succeed();
	}

	/**
	 * Brookmere's plaza demolished, so it leaves the network; built again, its next round puts it back: its stone works
	 * again with its name, and Ashford's Map lists it once more.
	 */
	//$ gametest_batch AREA '"qa_waystone1028_rebuilt"'
	@GameTest(template = AREA, batch = "qa_waystone1028_rebuilt")
	public void aVillageWhosePlazaIsBuiltAgainRejoinsTheNetwork(GameTestHelper helper) {
		WaystoneQaCompatTests.cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			WaystoneQaCompatTests.villages(helper, true);
			BlockPos a = helper.absolutePos(WaystoneQaCompatTests.HALL_A);
			BlockPos b = helper.absolutePos(WaystoneQaCompatTests.HALL_B);
			WaystoneQaCompatTests.breakStone(helper, WaystoneQaCompatTests.PLAZA_B);
			BuildSiteManager.get(level).forgetFinished(WaystoneQaCompatTests.placement(helper, WaystoneQaCompatTests.PLAZA_B));
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.stoneOf(level, b).isEmpty(), "Brookmere's plaza is gone but it stays on the network");
			WaystoneQaCompatTests.plaza(helper, WaystoneQaCompatTests.PLAZA_B); // built again
			VillageWaystones.round(level, b);
			helper.assertTrue(VillageWaystones.standing(level, b), "Brookmere's plaza was built again but its stone isn't back on the network: " + VillageWaystones.stoneOf(level, b));
			helper.assertTrue(WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_B).equals(java.util.Optional.of("Brookmere")),
				"the rebuilt stone's name: " + WaystoneQaCompatTests.nameAt(helper, WaystoneQaCompatTests.PLAZA_B));
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), "screen.aliveworkplace.hall.waystone");
			helper.assertTrue(lines.size() == 3 && lines.get(0).equals("Waystone network (2):") && lines.stream().anyMatch(l -> l.startsWith("Brookmere, ")),
				"Ashford's Map after Brookmere's plaza was rebuilt: " + lines);
		} finally {
			WaystoneQaCompatTests.cleanUp(helper);
		}
		helper.succeed();
	}
}
