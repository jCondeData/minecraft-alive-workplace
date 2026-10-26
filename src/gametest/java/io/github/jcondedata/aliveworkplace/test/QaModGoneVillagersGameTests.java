package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * QA (lane-e-1026-1614): upgrading a world after an optional mod was taken off the server. The Mechanic and Conductor
 * (Create, 39.2/39.7d) and the Furnicrafter (CobbleFurnies, 38.12) only exist with their mod; ModVillagers promises
 * "a villager saved as one in a world [the mod] was removed from loads as a jobless one". The existing tests load a
 * fresh level-1 plains villager with no trades. A real one has worked: it was born in a desert, has levelled up and
 * has its trades. After the mod is gone a player should still see the same villager (its desert clothes, its name),
 * now jobless, and a jobless villager doesn't trade (vanilla's never do): it mustn't keep selling its old job's trades.
 * None of these mods are in the core GameTests, so this runs here.
 */
public class QaModGoneVillagersGameTests implements FabricGameTest {

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSeasonedDesertMechanicLoadsAsTheSameJoblessVillagerWithoutCreate(GameTestHelper helper) {
		check(helper, "aliveworkplace:mechanic");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSeasonedDesertConductorLoadsAsTheSameJoblessVillagerWithoutCreate(GameTestHelper helper) {
		check(helper, "aliveworkplace:conductor");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSeasonedDesertFurnicrafterLoadsAsTheSameJoblessVillagerWithoutCobbleFurnies(GameTestHelper helper) {
		check(helper, "aliveworkplace:furnicrafter");
		helper.succeed();
	}

	/**
	 * A desert villager named Rosa, level 3 with 70 xp and her trades (a level-3 toolsmith's stand in for the modded
	 * job's, the same Offers list in the save), saved as {@code job}, loaded where that job doesn't exist.
	 */
	private static void check(GameTestHelper helper, String job) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		villager.setVillagerData(villager.getVillagerData().setType(VillagerType.DESERT).setProfession(VillagerProfession.TOOLSMITH).setLevel(3));
		villager.setVillagerXp(70);
		villager.setCustomName(Component.literal("Rosa"));
		helper.assertTrue(!villager.getOffers().isEmpty(), "the setup: Rosa has no trades");
		CompoundTag saved = new CompoundTag();
		villager.saveWithoutId(saved);
		villager.discard();
		CompoundTag data = saved.getCompound("VillagerData");
		data.put("profession", StringTag.valueOf(job));
		saved.put("VillagerData", data);

		Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
		loaded.load(saved);
		try {
			helper.assertTrue(loaded.getVillagerData().getProfession() == VillagerProfession.NONE,
				"a saved " + job + " loads as " + loaded.getVillagerData().getProfession() + ", not jobless");
			helper.assertTrue(loaded.getCustomName() != null && loaded.getCustomName().getString().equals("Rosa"),
				"the saved " + job + " lost her name: " + loaded.getCustomName());
			helper.assertTrue(loaded.getVillagerData().getType() == VillagerType.DESERT,
				"a desert " + job + " loads as a " + loaded.getVillagerData().getType() + " villager: her clothes change when " + job.substring(job.indexOf(':') + 1)
					+ "'s mod is removed (the whole VillagerData is dropped for one unknown job)");
			// The player's way in: a right click on her. A jobless villager shakes her head; she mustn't open a trade screen.
			BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
			loaded.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			helper.getLevel().addFreshEntity(loaded);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				loaded.mobInteract(player, InteractionHand.MAIN_HAND);
				helper.assertTrue(loaded.getTradingPlayer() == null && !(player.containerMenu instanceof MerchantMenu),
					"a right click on the now jobless " + job + " opens a trade screen with her old job's " + loaded.getOffers().size()
						+ " trades (a jobless villager doesn't trade)");
			} finally {
				loaded.setTradingPlayer(null);
				player.closeContainer();
				helper.getLevel().getServer().getPlayerList().remove(player);
			}
		} finally {
			loaded.discard();
		}
	}
}
