package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Lane D QA, 2026-11-07: the Habitat Keeper's Cobblemon side (ROADMAP 28.10, CobblemonHabitat), from the spec: she
 * tells of shiny, rare or Alpha WILD Pokémon, so a player's own shiny by her pasture is no sighting, and an Alpha (its
 * "alpha" aspect) is told as an Alpha; a Poké Snack is set out only on a spot a player could place one (not into a
 * solid block) and only from a Poké Snack.
 */
public class QaHabitatWild1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = QaHabitatKeeperCompatTests.PASTURE;

	private static List<String> kinds(List<HabitatKeepers.Sighting> found) {
		return found.stream().map(s -> s.kind() + ": " + s.what().getString()).toList();
	}

	/** A player's own shiny, standing by her pasture, isn't a wild sighting; a wild shiny next to it is. */
	//$ gametest_ticks_batch AREA '200' '"qa_hk_owned_1107d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_hk_owned_1107d")
	public void qaAPlayersOwnShinyIsNoSighting(GameTestHelper helper) {
		QaHabitatKeeperCompatTests.clearWilds(helper);
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		PokemonEntity mine = QaHabitatKeeperCompatTests.wild(helper, "eevee shiny=yes level=5", new Vec3(PASTURE.getX() + 6.5, 2, PASTURE.getZ() + 0.5));
		helper.assertTrue(Cobblemon.INSTANCE.getStorage().getParty(owner).add(mine.getPokemon()), "couldn't give the shiny Eevee to a player");
		helper.assertFalse(mine.getPokemon().isWild(), "the player's Eevee still counts as wild, so the test proves nothing");
		List<HabitatKeepers.Sighting> found = HabitatKeepers.sight(level, keeper, helper.absolutePos(PASTURE));
		helper.assertTrue(found.isEmpty(), "a player's own shiny was told as a sighting: " + kinds(found));
		QaHabitatKeeperCompatTests.wild(helper, "pikachu shiny=yes level=5", new Vec3(PASTURE.getX() + 0.5, 2, PASTURE.getZ() + 8.5));
		found = HabitatKeepers.sight(level, keeper, helper.absolutePos(PASTURE));
		helper.assertTrue(found.size() == 1 && found.get(0).what().getString().equals("a shiny Pikachu"),
			"the wild shiny next to it: " + kinds(found));
		helper.succeed();
	}

	/** A wild Alpha (Cobblemon's "alpha" aspect) of a common species is told as an Alpha; the same species plain isn't told. */
	//$ gametest_ticks_batch AREA '200' '"qa_hk_alpha_1107d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_hk_alpha_1107d")
	public void qaAnAlphaIsToldAsAnAlpha(GameTestHelper helper) {
		QaHabitatKeeperCompatTests.clearWilds(helper);
		ServerLevel level = helper.getLevel();
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos pasture = helper.absolutePos(PASTURE);
		QaHabitatKeeperCompatTests.wild(helper, "pidgey shiny=no level=5", new Vec3(PASTURE.getX() + 6.5, 2, PASTURE.getZ() + 0.5));
		List<HabitatKeepers.Sighting> found = HabitatKeepers.sight(level, keeper, pasture);
		helper.assertTrue(found.isEmpty(), "a plain Pidgey was told: " + kinds(found));
		PokemonEntity alpha = QaHabitatKeeperCompatTests.wild(helper, "pidgey shiny=no level=5", new Vec3(PASTURE.getX() + 0.5, 2, PASTURE.getZ() + 8.5));
		alpha.getPokemon().setForcedAspects(Set.of("alpha"));
		alpha.getPokemon().updateAspects();
		helper.assertTrue(alpha.getPokemon().getAspects().contains("alpha"), "couldn't make the Pidgey an Alpha: " + alpha.getPokemon().getAspects());
		found = HabitatKeepers.sight(level, keeper, pasture);
		helper.assertTrue(found.size() == 1 && found.get(0).kind().equals("alpha"), "the Alpha Pidgey: " + kinds(found));
		helper.succeed();
	}

	/**
	 * A snack is set out the way a player places one: on air it's the Poké Snack block; into stone it isn't (the stone
	 * stays); from a honey bottle it isn't (the air stays); an empty hand is no snack.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaASnackIsSetOutOnlyWhereAndFromWhatAPlayerCould(GameTestHelper helper) {
		HabitatKeepers.Snacks snacks = HabitatKeepers.snacks();
		helper.assertTrue(snacks != null, "no snack hook with Cobblemon installed");
		ServerLevel level = helper.getLevel();
		ServerPlayer placer = helper.makeMockServerPlayerInLevel();
		ItemStack snack = new ItemStack(QaHabitatKeeperCompatTests.item("poke_snack"));
		helper.assertTrue(snacks.isSnack(snack) && !snacks.isSnack(ItemStack.EMPTY) && !snacks.isSnack(new ItemStack(Items.HONEY_BOTTLE)),
			"which items are snacks");

		BlockPos stone = new BlockPos(1, 2, 1);
		helper.setBlock(stone, Blocks.STONE);
		helper.assertFalse(snacks.setOut(level, helper.absolutePos(stone), snack.copy(), Direction.NORTH, placer), "a snack set out into stone");
		helper.assertBlockPresent(Blocks.STONE, stone);

		BlockPos air = new BlockPos(3, 2, 1);
		helper.setBlock(air, Blocks.AIR);
		helper.assertFalse(snacks.setOut(level, helper.absolutePos(air), new ItemStack(Items.HONEY_BOTTLE), Direction.NORTH, placer),
			"a honey bottle set out as a snack");
		helper.assertBlockPresent(Blocks.AIR, air);

		helper.assertTrue(snacks.setOut(level, helper.absolutePos(air), snack.copy(), Direction.NORTH, placer), "a snack on air wasn't set out");
		helper.assertTrue(QaHabitatKeeperCompatTests.snackAt(helper, air), "no Poké Snack block where it was set out: " + helper.getBlockState(air));
		helper.assertTrue(snacks.isSnackBlock(helper.getBlockState(air)) && !snacks.isSnackBlock(helper.getBlockState(stone)), "which blocks are snacks");
		helper.succeed();
	}
}
