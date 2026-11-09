package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1109-1913, saves): the Habitat Keeper's sightings over a restart (ROADMAP 28.10: "each is told once"; her
 * last sightings are the hall's list). What she has told of is saved on her (the {@code sighted} and {@code sightings}
 * attachments), which no test reloaded before: a restart must not tell the village of the same shiny again, nor lose
 * her list, and she must still tell of a new one.
 */
public class QaSightedSaves1109eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(2, 2, 2);
	private static final BlockPos HALL = new BlockPos(1, 2, 8);

	/** A shiny told of, then the keeper saved and loaded: the same shiny isn't told again, her list keeps it, a new one is told. */
	//$ gametest_ticks_batch AREA '200' '"qa1109e_sighted_saves"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1109e_sighted_saves")
	public void aShinyToldBeforeARestartIsNotToldAgainAfterIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(HabitatKeepers.SIGHT_RANGE + 2)).forEach(Entity::discard);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		BlockPos pasture = helper.absolutePos(PASTURE);
		PokemonEntity eevee = wild(helper, "eevee", new Vec3(12.5, 2, 12.5));
		List<HabitatKeepers.Sighting> first = HabitatKeepers.sight(level, keeper, pasture);
		helper.assertTrue(first.size() == 1 && first.get(0).kind().equals("shiny"), "before the restart, the first look: " + first);

		CompoundTag tag = new CompoundTag();
		helper.assertTrue(keeper.save(tag), "the keeper wasn't saved");
		keeper.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != keeper, "the save didn't load as a new villager: " + loaded);
		Villager again = (Villager) loaded;
		level.addFreshEntity(again);
		PartnerShowsCompatTests.after(helper, again::discard);
		helper.assertTrue(HabitatKeepers.isKeeper(again), "after the restart she isn't a Habitat Keeper");

		List<HabitatKeepers.Sighting> afterRestart = HabitatKeepers.sight(level, again, pasture);
		helper.assertTrue(afterRestart.isEmpty(), "after the restart she told of the same shiny Eevee again: " + afterRestart);
		helper.assertTrue(HabitatKeepers.sightings(again).size() == 1 && HabitatKeepers.sightingLines(again).size() == 1,
			"after the restart her last sightings are " + HabitatKeepers.sightings(again) + ", not the one shiny");
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		long written = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIGHTING).count();
		helper.assertTrue(written == 1, "the chronicle has " + written + " sightings, not 1, over the restart");

		eevee.discard();
		wild(helper, "pikachu", new Vec3(14.5, 2, 12.5));
		List<HabitatKeepers.Sighting> fresh = HabitatKeepers.sight(level, again, pasture);
		helper.assertTrue(fresh.size() == 1 && fresh.get(0).what().getString().toLowerCase().contains("pikachu"),
			"after the restart a new shiny Pikachu was told as " + fresh);
		helper.assertTrue(HabitatKeepers.sightings(again).size() == 2, "her last sightings are " + HabitatKeepers.sightings(again) + ", not the two");
		helper.succeed();
	}

	/** A shiny wild {@code species} at level 5, standing still at {@code rel}; gone with the test. */
	private static PokemonEntity wild(GameTestHelper helper, String species, Vec3 rel) {
		PokemonEntity wild = PokemonProperties.Companion.parse(species + " shiny=yes level=5", " ", "=").createEntity(helper.getLevel());
		helper.assertTrue(wild.getPokemon().getShiny() && wild.getPokemon().isWild(), "not a shiny wild " + species);
		wild.setPos(helper.absoluteVec(rel));
		wild.setNoAi(true);
		helper.getLevel().addFreshEntity(wild);
		PartnerShowsCompatTests.after(helper, wild::discard);
		return wild;
	}
}
