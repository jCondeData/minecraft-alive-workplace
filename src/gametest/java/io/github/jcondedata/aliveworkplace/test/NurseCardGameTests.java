package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.nurse.NurseCard;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 43.3, without Cobblemon: an empty hand on a Nurse opens her card (the player's health and the bad effects she'd
 * clear, Heal and Close) instead of being treated at once; Heal does what the click did, and while she'd refuse (asleep,
 * the wait since the last treatment) it's grayed out with her reason, and pressing it anyway gets the same answer and no
 * treatment. A sneak-click still trades; the card reads the same after a save and reload. Every click is the player's
 * own, through the server's packet handler.
 */
public class NurseCardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STAND = new BlockPos(2, 2, 2);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	static Villager nurse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Nurses.resetCooldowns();
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, AT);
		nurse.setNoAi(true);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(STAND), PoiTypes.CLERIC, ModVillagers.NURSE);
		nurse.setCustomName(Component.literal("Joy"));
		helper.setDayTime(6000);
		return nurse;
	}

	/** An empty-hand right-click (sneaking or not) through the packet handler. */
	static void click(ServerPlayer player, Villager villager, boolean sneak) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.setShiftKeyDown(sneak);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
	}

	static StatusCardMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		click(player, villager, false);
		helper.assertTrue(player.containerMenu instanceof StatusCardMenu, "no card opened: " + player.containerMenu.getClass().getSimpleName());
		return (StatusCardMenu) player.containerMenu;
	}

	static void press(ServerPlayer player, StatusCardMenu menu, int id) {
		player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, id));
	}

	static List<String> keys(List<Component> seen) {
		List<String> out = new ArrayList<>();
		for (Component c : seen) {
			out.add(c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString());
		}
		return out;
	}

	/** Hurt and poisoned: the card shows "You: 6 / 20 HP" (orange, a bar) and Poison "to be cleared"; Heal heals and clears it. */
	//$ gametest_batch AREA '"nurseCardHeal"'
	@GameTest(template = AREA, batch = "nurseCardHeal")
	public void nurseCardOpensOnAClickAndHealTreats(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setHealth(6f);
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 1200));
		StatusCardMenu menu = open(helper, player, nurse);
		helper.assertTrue(player.getHealth() == 6f, "the click treated the player before Heal");
		StatusCard card = menu.card();
		helper.assertValueEqual(card.title().getString(), "Joy", "the title");
		helper.assertValueEqual(card.tag().getString(), "Nurse · Novice", "the tag");
		helper.assertValueEqual(card.subtitle().getString(), "Heals you and clears bad effects, once every "
			+ Nurses.secondsLeft(BuilderLevels.delay(Nurses.COOLDOWN, nurse)) + " seconds.", "the subtitle");
		helper.assertValueEqual(card.lines().get(0).label().getString() + ": " + card.lines().get(0).value().getString(), "You: 6 / 20 HP", "the player's line");
		helper.assertTrue(card.lines().get(0).color() == StatusCard.ORANGE && Math.abs(card.lines().get(0).bar() - 0.3f) < 0.01f, "the player's bar");
		helper.assertValueEqual(card.lines().get(1).label().getString() + ": " + card.lines().get(1).value().getString(), "Poison: to be cleared", "the effect");
		helper.assertTrue(card.button(NurseCard.HEAL).usable() && card.button(StatusCardMenu.CLOSE) != null, "Heal grayed out or no Close");
		press(player, menu, NurseCard.HEAL);
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "the card stayed open");
		helper.assertTrue(player.getHealth() == player.getMaxHealth() && !player.hasEffect(MobEffects.POISON), "not treated: " + player.getHealth());
		helper.assertTrue(keys(seen).contains("message.aliveworkplace.nurse.healed"), "her line: " + keys(seen));
		helper.assertFalse(player.containerMenu instanceof MerchantMenu, "a trade screen opened");
		helper.succeed();
	}

	/**
	 * Treated once, then hurt again: the card's Heal is grayed with "Joy just treated you. Come back in N seconds.";
	 * pressed anyway (as an old client could), she says the same and heals nothing. Close only closes.
	 */
	//$ gametest_batch AREA '"nurseCardWait"'
	@GameTest(template = AREA, batch = "nurseCardWait")
	public void nurseCardGraysHealDuringTheWait(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		press(player, open(helper, player, nurse), NurseCard.HEAL);
		player.setHealth(5f);
		StatusCardMenu menu = open(helper, player, nurse);
		int seconds = Nurses.secondsLeft(BuilderLevels.delay(Nurses.COOLDOWN, nurse));
		helper.assertValueEqual(menu.card().button(NurseCard.HEAL).refusal().getString(), "Joy just treated you. Come back in " + seconds + " seconds.", "Heal's reason");
		press(player, menu, StatusCardMenu.CLOSE);
		helper.assertTrue(player.containerMenu == player.inventoryMenu && player.getHealth() == 5f, "Close did more than close");
		seen.clear();
		press(player, open(helper, player, nurse), NurseCard.HEAL);
		helper.assertTrue(keys(seen).contains("message.aliveworkplace.nurse.wait"), "pressed during the wait: " + keys(seen));
		helper.assertTrue(player.getHealth() == 5f, "healed during the wait: " + player.getHealth());
		helper.succeed();
	}

	/** Asleep: Heal is grayed with "Joy is asleep. Come back in the morning."; pressed anyway, the same and no treatment. */
	//$ gametest_batch AREA '"nurseCardAsleep"'
	@GameTest(template = AREA, batch = "nurseCardAsleep")
	public void nurseCardGraysHealWhileSheSleeps(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setHealth(5f);
		nurse.startSleeping(nurse.blockPosition());
		StatusCardMenu menu = open(helper, player, nurse);
		helper.assertValueEqual(menu.card().button(NurseCard.HEAL).refusal().getString(), "Joy is asleep. Come back in the morning.", "Heal's reason");
		press(player, menu, NurseCard.HEAL);
		helper.assertTrue(keys(seen).contains("message.aliveworkplace.nurse.asleep"), "pressed while she sleeps: " + keys(seen));
		helper.assertTrue(player.getHealth() == 5f, "treated while she sleeps");
		helper.succeed();
	}

	/** A sneak-click opens no card and treats nothing (her trades, as before). */
	//$ gametest_batch AREA '"nurseCardSneak"'
	@GameTest(template = AREA, batch = "nurseCardSneak")
	public void nurseCardNotOnASneakClick(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setHealth(5f);
		click(player, nurse, true);
		helper.assertFalse(player.containerMenu instanceof StatusCardMenu, "a sneak-click opened the card");
		helper.assertTrue(player.getHealth() == 5f, "a sneak-click treated the player");
		helper.succeed();
	}

	/** A Journeyman nurse saved and loaded: her card names her level and pace and still heals. */
	//$ gametest_batch AREA '"nurseCardReload"'
	@GameTest(template = AREA, batch = "nurseCardReload")
	public void nurseCardAfterASaveAndReload(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		nurse.setVillagerData(nurse.getVillagerData().setLevel(3));
		CompoundTag tag = new CompoundTag();
		nurse.saveWithoutId(tag);
		nurse.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "couldn't put the loaded nurse back");
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setHealth(5f);
		StatusCardMenu menu = open(helper, player, copy);
		helper.assertValueEqual(menu.card().tag().getString(), "Nurse · Journeyman", "the tag after a reload");
		int seconds = Nurses.secondsLeft(BuilderLevels.delay(Nurses.COOLDOWN, copy));
		helper.assertValueEqual(menu.card().subtitle().getString(), "Heals you and clears bad effects, once every " + seconds + " seconds.", "the pace");
		press(player, menu, NurseCard.HEAL);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the loaded nurse didn't treat");
		helper.succeed();
	}
}
