package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.travel.Ferrymen;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;

/** Travel posts and ferrymen on a real (headless) server. */
public class TravelGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A ferryman sells tickets to the posts you know (not the one you're at); a ticket takes you there. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void ferrymanSellsTicketsThatWork(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos home = new BlockPos(2, 2, 2);
		BlockPos away = new BlockPos(16, 2, 16);
		BlockPos unknown = new BlockPos(16, 2, 2);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		helper.setBlock(unknown, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post homePost = network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(home)), "Home");
		TravelNetwork.Post awayPost = network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Away");
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(unknown)), "Unknown");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		network.visit(player.getUUID(), awayPost);

		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		ferryman.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(ferryman.getOffers().size() == 1, ferryman.getOffers().size() + " tickets on offer, expected 1 (Away)");
		MerchantOffer offer = ferryman.getOffers().get(0);
		helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD) && offer.getBaseCostA().getCount() == 1, "a short trip costs 1 emerald");
		helper.assertTrue(network.known(player.getUUID(), null).contains(homePost), "talking to the ferryman should add their post");

		ItemStack ticket = offer.getResult().copy();
		player.moveTo(helper.absolutePos(new BlockPos(4, 2, 4)).getCenter());
		helper.assertTrue(Ferrymen.travel(player, ticket), "the ticket did not work at a post");
		helper.assertTrue(player.blockPosition().closerThan(helper.absolutePos(away), 4), "arrived at " + player.blockPosition());
		helper.succeed();
	}

	/** A travel post that came with a village (nobody placed it) joins the network under a made-up village name. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void villagePostsJoinTheNetwork(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos postPos = new BlockPos(4, 2, 4);
		helper.setBlock(postPos, ModBlocks.TRAVEL_POST);
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(postPos), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		TravelNetwork.Post post = Ferrymen.postOf(helper.getLevel(), ferryman);
		helper.assertTrue(post != null, "the village post didn't join the network");
		helper.assertTrue(post.name().equals(TravelNetwork.villageName(helper.absolutePos(postPos))), "named " + post.name());
		helper.assertTrue(post.name().matches("[A-Z][a-z]+"), "not a village name: " + post.name());
		helper.assertTrue(Ferrymen.postOf(helper.getLevel(), ferryman).id().equals(post.id()), "added twice");
		helper.succeed();
	}
}
