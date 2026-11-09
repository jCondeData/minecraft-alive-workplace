package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * The Trainer screen (ROADMAP 43.1) without Cobblemon: an empty hand on a Trainer still opens it, with Challenge grayed
 * out and the old "needs Cobblemon" line as the reason, no team or party, and Trade still working.
 */
public class TrainerScreenGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** No Cobblemon: the screen opens, Challenge is grayed with "needs Cobblemon", pressing it does nothing, Trade trades. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void withoutCobblemonTheScreenSaysSo(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(trainer.getX() + 1, trainer.getY(), trainer.getZ());
		var result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, trainer, null);
		helper.assertTrue(result.consumesAction(), "the click did nothing: " + result);
		helper.assertTrue(player.containerMenu instanceof TrainerMenu, "no Trainer screen: " + player.containerMenu);
		TrainerMenu menu = (TrainerMenu) player.containerMenu;
		TrainerView view = menu.view();
		helper.assertFalse(view.canChallenge(), "Challenge isn't grayed out without Cobblemon");
		helper.assertTrue(view.refusal().getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.trainer.no_cobblemon"),
			"the reason: " + view.refusal().getString());
		helper.assertTrue(view.team().isEmpty() && view.party().isEmpty() && view.partyNote().getString().isEmpty(), "Pokémon shown without Cobblemon");
		helper.assertValueEqual(view.rules().getString(), "Singles", "the rules without Cobblemon");
		helper.assertValueEqual(view.reward().getString(), "Win to earn 1 emerald.", "a Novice's reward in emeralds");
		helper.assertTrue(menu.clickMenuButton(player, TrainerMenu.CHALLENGE), "Challenge wasn't taken");
		helper.assertFalse(player.containerMenu instanceof TrainerMenu, "Challenge left the screen open");
		// the view goes to the client and back unchanged
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		TrainerView.STREAM_CODEC.encode(buf, view);
		TrainerView back = TrainerView.STREAM_CODEC.decode(buf);
		helper.assertValueEqual(back.refusal().getString(), view.refusal().getString(), "the reason after the trip");
		helper.assertValueEqual(back.record().getString(), view.record().getString(), "the record after the trip");
		helper.assertValueEqual(back.entityId(), view.entityId(), "the villager after the trip");
		// Trade: the trainer's trades, as a sneak-click opens them (a Trainer's own trades are Cobblemon's goods: one of ours here)
		trainer.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
			new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 4, 1, 0.05f));
		UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, trainer, null);
		((TrainerMenu) player.containerMenu).clickMenuButton(player, TrainerMenu.TRADE);
		helper.assertTrue(trainer.getTradingPlayer() == player, "Trade didn't open the trades");
		trainer.setTradingPlayer(null);
		player.closeContainer();
		helper.succeed();
	}
}
