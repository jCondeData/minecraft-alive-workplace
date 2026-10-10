package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.story.JobFamilies;
import io.github.jcondedata.aliveworkplace.story.Keepsakes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Keepsakes (ROADMAP 31.8) with the real Cobblemon installed: the Pokémon family's is Cobblemon's Premier Ball, found by its id. */
public class KeepsakeCompatTests implements FabricGameTest {
	/** Every job of the Pokémon family gives "<Name>'s First Poké Ball", a Premier Ball (without Cobblemon: a snowball, see HeartEventSetGameTests). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonFamilysKeepsakeIsAPremierBall(GameTestHelper helper) {
		helper.assertTrue(Keepsakes.POKE_BALL.toString().equals("cobblemon:premier_ball") && BuiltInRegistries.ITEM.containsKey(Keepsakes.POKE_BALL),
			"Cobblemon has no item " + Keepsakes.POKE_BALL);
		for (ResourceLocation job : JobFamilies.Family.POKEMON.jobs) {
			Villager villager = EntityType.VILLAGER.create(helper.getLevel());
			villager.setVillagerData(villager.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(job)));
			villager.setCustomName(Component.literal("Ana"));
			ItemStack keepsake = Keepsakes.of(helper.getLevel(), villager).get(0);
			helper.assertTrue(BuiltInRegistries.ITEM.getKey(keepsake.getItem()).equals(Keepsakes.POKE_BALL) && !keepsake.is(Items.SNOWBALL) && keepsake.getCount() == 1,
				job + " gives " + keepsake);
			helper.assertTrue(keepsake.getHoverName().getString().equals("Ana's First Poké Ball"), job + "'s keepsake is named " + keepsake.getHoverName().getString());
			villager.discard();
		}
		helper.succeed();
	}
}
