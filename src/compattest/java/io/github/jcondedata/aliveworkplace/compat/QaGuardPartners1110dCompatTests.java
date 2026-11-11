package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.guard.GuardPartners;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-10) for guards fighting beside their Pokémon, from GuardPartners' spec: "Fighting and Dragon
 * types kept in a Pasture Block near the Guard Post follow up every hit the guard lands on a monster with a move of
 * their own", the move's damage growing with the Pokémon's level (2 at level 1, 5 at 50, 8 at 100), and creepers a
 * guard's foe only once it carries a bow. PastureCompatTests and QaGuardPartnersCompatTests prove a level-10 Machop on a
 * husk, a pig, a wall and the cooldown; these prove the Dragon half, the level, that only a guard's hit calls the
 * partners, and the creeper rule from both sides.
 */
public class QaGuardPartners1110dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos PASTURE = new BlockPos(6, 2, 6);

	/** A guard (no AI: only the test's hits) at a post with {@code properties} pastured near it, cooldowns forgotten. */
	private static Villager guardWith(GameTestHelper helper, String properties) {
		ServerLevel level = helper.getLevel();
		GuardPartners.reset();
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setNoAi(true);
		BlockPos pasture = PastureCompatTests.pasture(helper, PASTURE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pokemon pokemon = PokemonProperties.Companion.parse(properties, " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
		PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
		helper.assertTrue(block != null && block.tether(player, pokemon, Direction.SOUTH), "could not pasture " + properties);
		return guard;
	}

	/** {@code foe} (no AI, never knocked about) a block and a half from the pastured Pokémon, away from the pasture block. */
	private static <T extends Mob> T beside(GameTestHelper helper, T foe) {
		foe.setNoAi(true);
		foe.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		List<PokemonEntity> pokemon = helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds(), e -> true);
		helper.assertTrue(pokemon.size() == 1, "the pastured Pokémon: " + pokemon);
		Vec3 at = pokemon.get(0).position();
		Vec3 away = at.subtract(Vec3.atBottomCenterOf(helper.absolutePos(PASTURE))).multiply(1, 0, 1);
		away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
		foe.teleportTo(at.x + away.x * 1.5, at.y, at.z + away.z * 1.5);
		helper.assertTrue(pokemon.get(0).hasLineOfSight(foe), "setup: the Pokémon can't see the " + foe.getType().getDescription().getString());
		return foe;
	}

	/** A hit of 1 on {@code foe} by {@code attacker}: how much health it lost. */
	private static float hit(GameTestHelper helper, Villager attacker, LivingEntity foe) {
		float before = foe.getHealth();
		foe.invulnerableTime = 0;
		foe.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1f);
		return before - foe.getHealth();
	}

	private static boolean moved(LivingEntity foe) {
		return foe.getLastDamageSource() != null && foe.getLastDamageSource().is(GuardPartners.POKEMON_MOVE);
	}

	/** A Dratini (Dragon) pastured by the post joins the guard's hit on a husk with one move, the husk turning on the guard. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersDragon1110d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersDragon1110d")
	public void aPasturedDragonTypeJoinsTheGuardsHit(GameTestHelper helper) {
		Villager guard = guardWith(helper, "dratini level=10");
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			helper.assertTrue(Partners.helpers(guard).size() == 1, "the Dratini isn't the guard's partner: " + Partners.helpers(guard));
			var husk = beside(helper, helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 4)));
			float lost = hit(helper, guard, husk);
			float move = GuardPartners.damage(10);
			helper.assertTrue(lost > 1f + 0.8f * move && lost < 1f + 1.5f * move, "the husk lost " + lost + "; one Dragon move is " + move);
			helper.assertTrue(moved(husk), "the last hit wasn't a Pokémon's move: " + husk.getLastDamageSource());
			helper.assertTrue(husk.getLastHurtByMob() == guard, "the husk should turn on the guard, not the Dratini: " + husk.getLastHurtByMob());
			helper.succeed();
		});
	}

	/** A level-50 Machop's move hits for 5 (a level-10 one's for 2.6): the move grows with the Pokémon's level. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersLevel1110d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersLevel1110d")
	public void aLevelFiftyPartnersMoveHitsForFive(GameTestHelper helper) {
		Villager guard = guardWith(helper, "machop level=50");
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			var husk = beside(helper, helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 4)));
			float lost = hit(helper, guard, husk);
			helper.assertTrue(Math.abs(GuardPartners.damage(50) - 5f) < 0.001f, "a level-50 move is " + GuardPartners.damage(50) + ", not 5");
			// Husk armor takes a few percent off each: the guard's 1 and a move of 5 is about 5.9; a level-10 move would be 3.5.
			helper.assertTrue(lost > 1f + 0.8f * 5f && lost < 1f + 1.2f * 5f, "the husk lost " + lost + " to the guard's 1 and a level-50 move of 5");
			helper.assertTrue(moved(husk), "no move: " + husk.getLastDamageSource());
			helper.succeed();
		});
	}

	/** A villager who isn't a guard hitting the husk by the post calls no move; the guard's hit right after does. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersNotGuard1110d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersNotGuard1110d")
	public void onlyTheGuardsHitCallsThePartners(GameTestHelper helper) {
		Villager guard = guardWith(helper, "machop level=10");
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		farmer.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			var husk = beside(helper, helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 4)));
			float byFarmer = hit(helper, farmer, husk);
			helper.assertTrue(byFarmer <= 1.01f && !moved(husk), "a villager who isn't a guard called a move: the husk lost " + byFarmer);
			// The Machop's move wasn't spent on the farmer's hit, so the guard's at once brings it.
			float byGuard = hit(helper, guard, husk);
			helper.assertTrue(byGuard > 1f + 0.8f * GuardPartners.damage(10) && moved(husk), "the guard's hit right after brought no move: " + byGuard);
			helper.succeed();
		});
	}

	/**
	 * A move reaches 20 blocks from the Pokémon: with the Machop held in one corner of the area, a husk in sight in the far
	 * corner (about 23 blocks off) gets none, one about 18 blocks off does, at once after (the far hit spent no cooldown).
	 * (The test area is walled and roofed, so the distances are across it, past the pasture, not overhead.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersReach1110d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersReach1110d")
	public void aMoveReachesTwentyBlocksAndNoFarther(GameTestHelper helper) {
		Villager guard = guardWith(helper, "machop level=10");
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			List<PokemonEntity> pokemon = helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds(), e -> true);
			helper.assertTrue(pokemon.size() == 1, "the pastured Pokémon: " + pokemon);
			PokemonEntity machop = pokemon.get(0);
			machop.setNoAi(true); // it stays where the distances are measured from
			Vec3 corner = helper.absoluteVec(new Vec3(0.5, 2, 16.5));
			machop.teleportTo(corner.x, corner.y, corner.z);
			var far = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 4));
			var near = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 4));
			for (var husk : List.of(far, near)) {
				husk.setNoAi(true);
				husk.setNoGravity(true);
				husk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			}
			Vec3 farAt = helper.absoluteVec(new Vec3(16.5, 6, 0.5));
			Vec3 nearAt = helper.absoluteVec(new Vec3(13.5, 2, 3.5));
			far.teleportTo(farAt.x, farAt.y, farAt.z);
			near.teleportTo(nearAt.x, nearAt.y, nearAt.z);
			double farDist = Math.sqrt(machop.distanceToSqr(far));
			double nearDist = Math.sqrt(machop.distanceToSqr(near));
			helper.assertTrue(farDist > 20.5 && nearDist < 19.5, "setup: the husks are " + farDist + " and " + nearDist + " blocks off");
			helper.assertTrue(machop.hasLineOfSight(far) && machop.hasLineOfSight(near), "setup: the Machop can't see the husks: far " + machop.hasLineOfSight(far) + ", near " + machop.hasLineOfSight(near));
			float onFar = hit(helper, guard, far);
			helper.assertTrue(onFar <= 1.01f && !moved(far), "a husk " + farDist + " blocks off drew a move: lost " + onFar);
			float onNear = hit(helper, guard, near);
			helper.assertTrue(onNear > 1f + 0.8f * GuardPartners.damage(10) && moved(near), "a husk " + nearDist + " blocks off drew no move: lost " + onNear);
			far.discard();
			near.discard();
			helper.succeed();
		});
	}

	/** A creeper struck by a guard with only a sword calls no move (not the guard's foe); with a bow in hand it does. */
	//$ gametest_ticks_batch AREA '200' '"qaGuardPartnersCreeper1110d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardPartnersCreeper1110d")
	public void aCreeperIsTheGuardsFoeOnlyWithABow(GameTestHelper helper) {
		Villager guard = guardWith(helper, "machop level=10");
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.runAfterDelay(10, () -> {
			Partners.forget(guard);
			var creeper = beside(helper, helper.spawn(EntityType.CREEPER, new BlockPos(5, 2, 4)));
			float swordOnly = hit(helper, guard, creeper);
			helper.assertTrue(swordOnly <= 1.01f && !moved(creeper), "a creeper hit by a guard without a bow drew a move: lost " + swordOnly);
			guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
			float withBow = hit(helper, guard, creeper);
			helper.assertTrue(withBow > 1f + 0.8f * GuardPartners.damage(10) && moved(creeper),
				"a creeper hit by a guard with a bow drew no move: lost " + withBow);
			creeper.discard();
			helper.succeed();
		});
	}
}
