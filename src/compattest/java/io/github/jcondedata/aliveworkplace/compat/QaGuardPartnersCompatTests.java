package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.guard.GuardPartners;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-10-24) for guards fighting beside their Pokémon (GuardPartners: "Fighting and Dragon types kept in
 * a Pasture Block near the Guard Post follow up every hit the guard lands on a monster with a move of their own — from
 * the pasture, at range"). PastureCompatTests proves one Machop's move on a husk; these prove the limits: a hit on an
 * animal (no monster) brings no move, a husk the Machop can't see (behind a wall) gets none, and a second hit in the
 * same second gets no second move while one a second later does.
 */
public class QaGuardPartnersCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A guard at a post with a Machop pastured near it (let out on the pasture's south side), the move cooldowns forgotten. */
	private static Villager guardWithMachop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GuardPartners.reset();
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PastureCompatTests.pastured(helper, pasture, player, "machop", Direction.SOUTH);
		return guard;
	}

	private static Husk husk(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		// Never knocked about: a husk thrown behind the pasture block is out of the Pokémon's sight, which is not the test.
		husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		return husk;
	}

	/**
	 * {@code husk} put a block and a half from the pastured Pokémon, on the side away from the pasture block, so it is in
	 * sight and reach whichever way the Pokémon has wandered (the pasture block between them hides it).
	 */
	private static Husk beside(GameTestHelper helper, Husk husk) {
		var pokemon = helper.getLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, helper.getBounds(), e -> true);
		helper.assertTrue(pokemon.size() == 1, "the pastured Pokémon: " + pokemon);
		BlockPos pasture = helper.absolutePos(new BlockPos(6, 2, 6));
		net.minecraft.world.phys.Vec3 at = pokemon.get(0).position();
		net.minecraft.world.phys.Vec3 away = at.subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(pasture)).multiply(1, 0, 1);
		away = away.lengthSqr() < 0.01 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : away.normalize();
		husk.teleportTo(at.x + away.x * 1.5, at.y, at.z + away.z * 1.5);
		return husk;
	}

	/** The guard's hit on {@code foe}: how much health it lost. */
	private static float hit(GameTestHelper helper, Villager guard, net.minecraft.world.entity.LivingEntity foe) {
		float before = foe.getHealth();
		foe.invulnerableTime = 0;
		foe.hurt(helper.getLevel().damageSources().mobAttack(guard), 1f);
		return before - foe.getHealth();
	}

	/** A hit on a pig brings no move; a hit on a husk by the pasture does (the Machop is there and joins in). */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersAnimal"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersAnimal")
	public void aHitOnAnAnimalBringsNoMove(GameTestHelper helper) {
		Villager guard = guardWithMachop(helper);
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			helper.assertTrue(Partners.helpers(guard).size() == 1, "partners: " + Partners.helpers(guard));
			Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 3));
			pig.setNoAi(true);
			float lost = hit(helper, guard, pig);
			helper.assertTrue(lost <= 1.01f, "the pig lost " + lost + ": a Pokémon's move joined a hit on an animal");
			helper.assertTrue(pig.getLastDamageSource() == null || !pig.getLastDamageSource().is(GuardPartners.POKEMON_MOVE), "a move hit the pig");
			Husk husk = husk(helper, new BlockPos(5, 2, 4));
			float onHusk = hit(helper, guard, husk);
			helper.assertTrue(onHusk > 1f + 0.8f * GuardPartners.damage(10), "the control: no move on the husk either (" + onHusk + ")");
			helper.succeed();
		});
	}

	/** A husk walled off from the pasture (stone across the whole area between them, floor to top) gets no move. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersWall"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersWall")
	public void aHuskThePokemonCantSeeGetsNoMove(GameTestHelper helper) {
		// The wall goes up first, so the Machop is let out on its far side (south of the pasture).
		for (int x = 0; x < 17; x++) {
			for (int y = 2; y < 8; y++) {
				helper.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
			}
		}
		Villager guard = guardWithMachop(helper);
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			helper.assertTrue(Partners.helpers(guard).size() == 1, "partners: " + Partners.helpers(guard));
			Husk husk = husk(helper, new BlockPos(3, 2, 2));
			float lost = hit(helper, guard, husk);
			List<String> where = helper.getLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, helper.getBounds(), e -> true)
				.stream().map(e -> e.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " sees: " + e.hasLineOfSight(husk)).toList();
			helper.assertTrue(where.stream().noneMatch(w -> w.endsWith("true")), "the wall doesn't hide the husk: " + where);
			helper.assertTrue(lost <= 1.01f, "the husk behind the wall lost " + lost + ": a move went through stone " + where);
			helper.succeed();
		});
	}

	/** Two hits a tick apart bring one move; a third a second (20 ticks) later brings another. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersCooldown"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersCooldown")
	public void aSecondHitInTheSameSecondGetsNoSecondMove(GameTestHelper helper) {
		Villager guard = guardWithMachop(helper);
		float move = GuardPartners.damage(10);
		float[] lost = new float[3];
		Husk[] husk = {null};
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			husk[0] = husk(helper, new BlockPos(5, 2, 4));
			husk[0].setInvulnerable(false);
			lost[0] = hit(helper, guard, beside(helper, husk[0]));
		});
		helper.runAfterDelay(11, () -> lost[1] = hit(helper, guard, beside(helper, husk[0])));
		helper.runAfterDelay(32, () -> {
			lost[2] = hit(helper, guard, beside(helper, husk[0]));
			helper.assertTrue(lost[0] > 1f + 0.8f * move, "the first hit brought no move: " + lost[0]);
			helper.assertTrue(lost[1] <= 1.01f, "the hit a tick later brought a second move: " + lost[1]);
			helper.assertTrue(lost[2] > 1f + 0.8f * move, "the hit a second later brought no move: " + lost[2] + "; the Pokémon: "
				+ helper.getLevel().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, helper.getBounds(), e -> true).stream()
					.map(e -> e.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " sees " + e.hasLineOfSight(husk[0])).toList()
				+ ", the husk at " + husk[0].blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
			helper.succeed();
		});
	}
}
