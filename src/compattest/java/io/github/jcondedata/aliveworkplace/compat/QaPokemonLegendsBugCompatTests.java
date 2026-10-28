package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonRanger;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-d-1027-1612), a bug test for ROADMAP 29.22: "once a day they befriend a wild Pokémon within 64
 * blocks". The Ranger looks for one on the first second of the day; with none near at that moment, the day's
 * befriending is spent, and a wild Pokémon that wanders in later that day is never befriended.
 */
public class QaPokemonLegendsBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A wild Pokémon arriving after the Ranger's first look of the day is still befriended that day. */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_bug_later"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_bug_later")
	public void aWildPokemonArrivingLaterTheSameDayIsBefriended(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(100)).forEach(e -> e.discard());
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
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(8)).forEach(e -> e.discard());
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			CivicEffects.forget();
			LegendPowers.forget();
			VillageNeeds.forget();
			Moods.forget();
		});
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 7000L); // past the morning: no calming
		PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setOwner(player.getUUID(), player.getGameProfile().getName());
		Villager ranger = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 9));
		ranger.setNoAi(true);
		Legends.make(level, ranger, Legends.get(PokemonRanger.ID).orElseThrow(), "test");
		LegendPowers.forget();
		helper.runAfterDelay(5, () -> {
			PokemonRanger.tick(ranger); // the day's first look: no wild Pokémon near yet
			helper.assertTrue(PokemonRanger.state(ranger).task() == PokemonRanger.NONE, "a walk with nothing to befriend");
			PokemonEntity pikachu = PokemonProperties.Companion.parse("pikachu level=10", " ", "=").createEntity(level);
			pikachu.setPos(helper.absoluteVec(new Vec3(8.5, 2, 9.5)));
			pikachu.setNoAi(true);
			level.addFreshEntity(pikachu);
			helper.runAfterDelay(5, () -> {
				PokemonRanger.tick(ranger);
				helper.assertTrue(PokemonRanger.state(ranger).task() != PokemonRanger.NONE,
					"a wild Pikachu beside the Ranger an hour later isn't befriended today: the day was spent on the empty first look ("
						+ PokemonRanger.state(ranger) + ")");
				helper.succeed();
			});
		});
	}

	/**
	 * "Within 96 blocks" from the edge: an Alpha standing on the hall's own floor exactly 96 blocks east is within 96
	 * blocks, and not within 95. The reach was measured from the hall block's center (half a block above the floor), so it
	 * missed by 0.0013.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_legends_bug_edge"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_legends_bug_edge")
	public void anAlphaExactly96BlocksAwayCounts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
		BlockPos at = HALL.offset(96, 0, 0);
		BlockPos abs = helper.absolutePos(at);
		boolean forced = level.setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, true);
		PartnerShowsCompatTests.after(helper, () -> {
			level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(110)).forEach(e -> e.discard());
			if (forced) {
				level.setChunkForced(abs.getX() >> 4, abs.getZ() >> 4, false);
			}
		});
		// setChunkForced only queues the load, and an entity added before its chunk's entities have loaded stays hidden from
		// every search (the first fix of B623 failed on this, not on the distance): the Machamp is added once they have
		whenEntitiesLoaded(helper, level, abs, () -> {
			PokemonEntity alpha = PokemonProperties.Companion.parse("machamp level=60", " ", "=").createEntity(level);
			alpha.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
			alpha.setNoAi(true);
			level.addFreshEntity(alpha);
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(alpha.isAlive() && level.getEntity(alpha.getUUID()) == alpha, "the Machamp didn't spawn");
				helper.assertTrue(!io.github.jcondedata.aliveworkplace.legend.WildPokemon.alphasNear(level, helper.absolutePos(HALL), 96).isEmpty(),
					"an Alpha standing exactly 96 blocks from the hall, on its floor, isn't within 96 blocks");
				helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.WildPokemon.alphasNear(level, helper.absolutePos(HALL), 95).isEmpty(),
					"an Alpha standing 96 blocks from the hall counts as within 95 blocks");
				helper.succeed();
			});
		});
	}

	/** Runs {@code then} once the entities of the chunk at {@code pos} have loaded (checked each tick, for up to 200). */
	private static void whenEntitiesLoaded(GameTestHelper helper, ServerLevel level, BlockPos pos, Runnable then) {
		whenEntitiesLoaded(helper, level, pos, then, 0);
	}

	private static void whenEntitiesLoaded(GameTestHelper helper, ServerLevel level, BlockPos pos, Runnable then, int waited) {
		if (level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(pos))) {
			then.run();
		} else {
			helper.assertTrue(waited < 200, "the chunk 96 blocks east never loaded its entities");
			helper.runAfterDelay(1, () -> whenEntitiesLoaded(helper, level, pos, then, waited + 1));
		}
	}
}
