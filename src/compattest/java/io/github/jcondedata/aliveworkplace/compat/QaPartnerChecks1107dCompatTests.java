package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-07) of the pastured-Pokémon checks the jobs share (CobblemonPartners), from their specs: the
 * Shepherd shears "woolly Pokémon kept in a pasture there (Wooloo, Dubwool)" (ShepherdWork, ROADMAP 28.x), never a wild
 * one; a Pokémon chore ("miltank", "gogoat gender=female") is a Pokémon's, never a vanilla animal's. Kills two nightly
 * survivors of the compat mutation of CobblemonPartners: :199 (isPastured flipped) and :178 (a non-Pokémon matching).
 */
public class QaPartnerChecks1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static PokemonEntity wild(GameTestHelper helper, String properties, BlockPos at) {
		PokemonEntity e = PokemonProperties.Companion.parse(properties, " ", "=").createEntity(helper.getLevel());
		e.setPos(helper.absoluteVec(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
		e.setNoAi(true);
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	private static boolean pastured(Entity entity) {
		return PokemonPartners.EXTENSION.call(p -> p.isPastured(entity), false);
	}

	private static boolean matches(Entity entity, String properties) {
		return PokemonPartners.EXTENSION.call(p -> p.matches(entity, properties), false);
	}

	/**
	 * A Shepherd at his loom with shears in his chest, a sheep and a wild Wooloo (nobody's, no pasture) beside him: he
	 * shears the sheep, and the wild Wooloo keeps its wool (still the same 300 ticks after the sheep's shearing).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_shepherd_wild_wooloo_1107d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_shepherd_wild_wooloo_1107d")
	public void aShepherdShearsTheSheepAndLeavesAWildWoolooAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.LOOM);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.SHEARS));
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, shepherd, helper.absolutePos(new BlockPos(2, 2, 2)), net.minecraft.world.entity.ai.village.poi.PoiTypes.SHEPHERD,
			net.minecraft.world.entity.npc.VillagerProfession.SHEPHERD);
		PokemonEntity wooloo = wild(helper, "wooloo level=10", new BlockPos(5, 2, 3));
		Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(5, 2, 6));
		sheep.setNoAi(true);
		helper.assertTrue(wooloo.readyForShearing(), "setup: the wild Wooloo should be woolly");
		long[] shornAt = {-1};
		helper.onEachTick(() -> {
			if (shornAt[0] < 0 && sheep.isSheared()) {
				shornAt[0] = level.getGameTime();
			}
			helper.assertTrue(wooloo.isAlive() && wooloo.readyForShearing(), "the shepherd sheared a wild Wooloo (nobody's pasture): sheared "
				+ ModAttachments.ANIMALS_SHEARED.getOrElse(shepherd, 0) + ", the sheep " + (sheep.isSheared() ? "shorn" : "woolly"));
			if (shornAt[0] >= 0 && level.getGameTime() - shornAt[0] >= 300) {
				helper.succeed();
			}
		});
	}

	/** A pastured Wooloo is pastured; a wild one, a sheep and a cow aren't. */
	//$ gametest AREA
	@GameTest(template = AREA, timeoutTicks = 200)
	public void onlyAPokemonInAPastureIsPastured(GameTestHelper helper) {
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		Pokemon kept = PastureCompatTests.pastured(helper, pasture, helper.makeMockServerPlayerInLevel(), "wooloo", Direction.NORTH);
		PokemonEntity wild = wild(helper, "wooloo level=10", new BlockPos(3, 2, 3));
		Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 6));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(kept.getEntity() != null && pastured(kept.getEntity()), "the Wooloo in the pasture isn't pastured: " + kept.getEntity());
			helper.assertTrue(!pastured(wild), "a wild Wooloo counts as pastured");
			helper.assertTrue(!pastured(sheep) && !pastured(cow), "a sheep or a cow counts as a pastured Pokémon");
		});
	}

	/** "miltank" matches a Miltank, not a cow; "wooloo" not a sheep; "gogoat gender=female" not a goat. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPokemonChoreIsNeverAVanillaAnimals(GameTestHelper helper) {
		PokemonEntity miltank = wild(helper, "miltank level=10", new BlockPos(3, 2, 3));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 3));
		Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(3, 2, 6));
		Entity goat = helper.spawn(EntityType.GOAT, new BlockPos(6, 2, 6));
		helper.assertTrue(matches(miltank, "miltank"), "a Miltank doesn't match \"miltank\"");
		helper.assertTrue(!matches(miltank, "wooloo"), "a Miltank matches \"wooloo\"");
		helper.assertTrue(!matches(cow, "miltank"), "a cow matches \"miltank\"");
		helper.assertTrue(!matches(sheep, "wooloo"), "a sheep matches \"wooloo\"");
		helper.assertTrue(!matches(goat, "gogoat gender=female"), "a goat matches \"gogoat gender=female\"");
		helper.succeed();
	}
}
