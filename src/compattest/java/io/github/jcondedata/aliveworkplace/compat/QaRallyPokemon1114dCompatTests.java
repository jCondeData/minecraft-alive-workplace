package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-14), the Rally Banner with Cobblemon, from the player guide (guide/guard.md: "Players, villagers,
 * pets and Pokémon are always safe from them"): no test had a following guard near a Pokémon. A wild Pokémon the player
 * strikes, and one that is after the player, are left alone by the guard following the raised banner; a pig the player
 * strikes afterwards is not, which shows the guard was fighting for the player all along.
 */
public class QaRallyPokemon1114dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '1600' '"qaRallyPokemon1114d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaRallyPokemon1114d")
	public void aFollowingGuardLeavesAWildPokemonThePlayerStrikesAlone(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		level.setDayTime(2000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setInvulnerable(true);
		BlockPos stand = helper.absolutePos(new BlockPos(14, 2, 14));
		player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		BlockPos post = new BlockPos(3, 2, 3);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.east());
		Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), true));
		player.getInventory().add(banner);
		PartnerShowsCompatTests.after(helper, () -> {
			player.getInventory().clearContent();
			Escorts.scan(level.getServer());
			level.getServer().getPlayerList().remove(player);
			level.setDayTime(time);
		});
		Escorts.scan(level.getServer());

		PokemonEntity struck = PokemonProperties.Companion.parse("eevee level=5", " ", "=").createEntity(level);
		BlockPos a = helper.absolutePos(new BlockPos(17, 2, 14));
		struck.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0f, 0f);
		struck.setNoAi(true);
		level.addFreshEntity(struck);
		PokemonEntity after = PokemonProperties.Companion.parse("rattata level=5", " ", "=").createEntity(level);
		BlockPos b = helper.absolutePos(new BlockPos(11, 2, 14));
		after.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 0f, 0f);
		after.setNoAi(true);
		level.addFreshEntity(after);
		float[] full = {struck.getHealth(), after.getHealth()};
		Pig[] pig = {null};
		helper.onEachTick(() -> {
			long t = helper.getTick();
			if (pig[0] == null) {
				after.setTarget(player);
				if (t % 40 == 0) {
					player.setLastHurtMob(struck);
				}
				PokemonEntity[] both = {struck, after};
				for (int i = 0; i < 2; i++) {
					if (!both[i].isAlive() || both[i].getHealth() < full[i]) {
						helper.fail("the guard went for a Pokémon: " + both[i].getPokemon().getSpecies().getName() + " at "
							+ both[i].getHealth() + " of " + full[i] + (both[i].isAlive() ? "" : ", gone"));
					}
					if (t > 5 && Escorts.isEscorting(guard) && Escorts.leaderFoe(player, guard) == both[i]) {
						helper.fail("the guard picked " + both[i].getPokemon().getSpecies().getName() + " as the player's foe");
					}
				}
				if (t >= 700) {
					helper.assertTrue(Escorts.isEscorting(guard) && guard.distanceTo(player) < 12, "setup: the guard isn't following the player, "
						+ guard.distanceTo(player) + " blocks off");
					after.setTarget(null);
					pig[0] = helper.spawn(EntityType.PIG, new BlockPos(14, 2, 11));
					pig[0].setNoAi(true);
				}
			} else if (t % 20 == 0) {
				player.setLastHurtMob(pig[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(pig[0] != null && !pig[0].isAlive(), "the guard didn't go for the pig the player struck");
			struck.discard();
			after.discard();
		});
	}
}
