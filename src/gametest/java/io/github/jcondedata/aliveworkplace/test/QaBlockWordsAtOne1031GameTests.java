package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1031-2213, the sentences a player reads, counted sentences 24.5): every block of the mod, set down by a
 * small village (a hall, one villager), right-clicked by its owner with an empty hand and then sneaking, as a player
 * first meets it: what the click says in chat, and, when it opens one of our screens, every line of that screen and of
 * every page its buttons open; the same for a Novice of every profession, and every item of the mod (its tooltip and
 * used in the air). No line may say "1" followed by a plural ("1 days", "1 blocks").
 */
public class QaBlockWordsAtOne1031GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos SPOT = new BlockPos(11, 2, 11);
	private static final Pattern ONE_PLURAL = Pattern.compile("(?<![\\d.,/:#×x-])\\b1 ([A-Za-z]+s)\\b");
	private static final Set<String> NOT_PLURAL = Set.of("is", "was", "has", "does", "plus", "its", "this", "us", "as", "less", "CobbleDollars", "Ss");

	//$ gametest_ticks_batch BIG '300' '"qaBlockWordsAtOne1031"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaBlockWordsAtOne1031")
	public void noBlockSaysOneWithAPluralWhenFirstClicked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos spot = helper.absolutePos(SPOT);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			Map<String, String> wrong = new LinkedHashMap<>();
			int blocks = 0;
			int screens = 0;
			int heard = 0;
			for (Block block : BuiltInRegistries.BLOCK) {
				String id = BuiltInRegistries.BLOCK.getKey(block).toString();
				if (!id.startsWith("aliveworkplace:") || block == ModBlocks.VILLAGE_HALL) {
					continue;
				}
				blocks++;
				for (boolean sneak : new boolean[] {false, true}) {
					helper.setBlock(SPOT, block);
					player.moveTo(Vec3.atCenterOf(spot.south(3)));
					player.setShiftKeyDown(sneak);
					player.closeContainer();
					client.seen().clear();
					click(player, helper, spot);
					List<String> said = said(client);
					heard += said.size();
					check(id + (sneak ? " sneaking" : ""), said, wrong);
					if (player.containerMenu instanceof ChoiceMenu menu) {
						screens++;
						List<String> page = read(menu);
						check(id + " screen", page, wrong);
						for (int a = 0; a < ChoiceMenu.SIZE; a++) {
							if (menu.icon(a).isEmpty()) {
								continue;
							}
							player.closeContainer();
							helper.setBlock(SPOT, block);
							click(player, helper, spot);
							client.seen().clear();
							if (player.containerMenu instanceof ChoiceMenu again) {
								again.press(a, player);
								check(id + " screen, button " + a, read(again), wrong);
								check(id + " screen, button " + a + " chat", said(client), wrong);
							}
						}
					}
					player.setShiftKeyDown(false);
					player.closeContainer();
				}
				helper.setBlock(SPOT, Blocks.AIR);
			}
			helper.assertTrue(blocks > 20 && screens > 0 && heard > 0, "setup: " + blocks + " blocks, " + screens + " screens, " + heard + " chat lines");
			helper.assertTrue(wrong.isEmpty(), blocks + " blocks of the mod, first clicks in " + entity.getBlockPos() + "'s village say: " + wrong);
			helper.succeed();
		});
	}

	/**
	 * Every profession (ours, and vanilla's: our screens hang on some of theirs too), a grown villager of it at Novice
	 * by the hall, right-clicked by the village's owner with an empty hand and then sneaking.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaVillagerWordsAtOne1031"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaVillagerWordsAtOne1031")
	public void noVillagerSaysOneWithAPluralWhenFirstClicked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			Map<String, String> wrong = new LinkedHashMap<>();
			int jobs = 0;
			int screens = 0;
			int heard = 0;
			for (net.minecraft.world.entity.npc.VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
				String id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
				jobs++;
				for (boolean sneak : new boolean[] {false, true}) {
					net.minecraft.world.entity.npc.Villager villager = helper.spawn(EntityType.VILLAGER, SPOT);
					villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(1));
					villager.setNoAi(true);
					player.moveTo(Vec3.atCenterOf(helper.absolutePos(SPOT).south(2)));
					player.setShiftKeyDown(sneak);
					player.closeContainer();
					client.seen().clear();
					talk(player, villager);
					List<String> said = said(client);
					heard += said.size();
					check(id + (sneak ? " sneaking" : ""), said, wrong);
					if (player.containerMenu instanceof ChoiceMenu menu) {
						screens++;
						check(id + " screen", read(menu), wrong);
						for (int a = 0; a < ChoiceMenu.SIZE; a++) {
							if (menu.icon(a).isEmpty()) {
								continue;
							}
							player.closeContainer();
							talk(player, villager);
							client.seen().clear();
							if (player.containerMenu instanceof ChoiceMenu again) {
								again.press(a, player);
								check(id + " screen, button " + a, read(again), wrong);
								check(id + " screen, button " + a + " chat", said(client), wrong);
							}
						}
					}
					player.setShiftKeyDown(false);
					player.closeContainer();
					villager.discard();
				}
			}
			helper.assertTrue(jobs > 20 && heard > 0, "setup: " + jobs + " jobs, " + screens + " screens, " + heard + " chat lines");
			helper.assertTrue(wrong.isEmpty(), jobs + " jobs, first clicks on a villager of each say: " + wrong);
			helper.succeed();
		});
	}

	/**
	 * Every item of the mod, one of it: its tooltip, and used in the air (then sneaking) by the village's owner: what
	 * that says in chat, and the lines of any screen of ours it opens.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaItemWordsAtOne1031"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaItemWordsAtOne1031")
	public void noItemSaysOneWithAPluralWhenFirstUsed(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			Map<String, String> wrong = new LinkedHashMap<>();
			int items = 0;
			int lines = 0;
			int heard = 0;
			for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
				String id = BuiltInRegistries.ITEM.getKey(item).toString();
				if (!id.startsWith("aliveworkplace:")) {
					continue;
				}
				items++;
				ItemStack one = new ItemStack(item);
				List<String> tip = one.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(helper.getLevel()), player,
					net.minecraft.world.item.TooltipFlag.NORMAL).stream().map(Component::getString).toList();
				lines += tip.size();
				check(id + " tooltip", tip, wrong);
				for (boolean sneak : new boolean[] {false, true}) {
					player.moveTo(Vec3.atCenterOf(helper.absolutePos(SPOT)));
					player.setShiftKeyDown(sneak);
					player.closeContainer();
					player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
					client.seen().clear();
					player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
					List<String> said = said(client);
					heard += said.size();
					check(id + " used" + (sneak ? " sneaking" : ""), said, wrong);
					if (player.containerMenu instanceof ChoiceMenu menu) {
						check(id + " screen", read(menu), wrong);
					}
					player.stopUsingItem();
					player.setShiftKeyDown(false);
					player.closeContainer();
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				}
			}
			helper.assertTrue(items > 40 && lines > items && heard > 0, "setup: " + items + " items, " + lines + " tooltip lines, " + heard + " chat lines");
			helper.assertTrue(wrong.isEmpty(), items + " items of the mod, one of each: " + wrong);
			helper.succeed();
		});
	}

	private static void talk(ServerPlayer player, net.minecraft.world.entity.npc.Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		net.minecraft.world.InteractionResult r = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
			.interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == net.minecraft.world.InteractionResult.PASS) {
			player.interactOn(villager, InteractionHand.MAIN_HAND);
		}
	}

	private static void click(ServerPlayer player, GameTestHelper helper, BlockPos spot) {
		ItemStack empty = ItemStack.EMPTY;
		player.setItemInHand(InteractionHand.MAIN_HAND, empty);
		player.gameMode.useItemOn(player, helper.getLevel(), empty, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(spot), Direction.UP, spot, false));
	}

	private static List<String> said(QaStageFiveGameTests.Client client) {
		List<String> out = client.seen().stream().map(Component::getString).toList();
		client.seen().clear();
		client.sent();
		return out;
	}

	private static List<String> read(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			ItemStack icon = menu.icon(i);
			if (icon.isEmpty()) {
				continue;
			}
			out.add(icon.getHoverName().getString());
			for (Component line : icon.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
				out.add(line.getString());
			}
		}
		return out;
	}

	private static void check(String where, List<String> lines, Map<String, String> wrong) {
		for (String line : lines) {
			Matcher m = ONE_PLURAL.matcher(line);
			while (m.find()) {
				if (!NOT_PLURAL.contains(m.group(1))) {
					wrong.putIfAbsent("'" + line + "'", where);
				}
			}
		}
	}
}
