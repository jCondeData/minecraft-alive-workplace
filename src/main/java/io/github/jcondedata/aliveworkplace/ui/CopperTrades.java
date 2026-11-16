package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * The trading screen of the mod's own jobs in Copperline (ROADMAP 44.2): who gets it, how it opens, and where its parts
 * sit. The menu is vanilla's merchant menu ({@link CopperTradeMenu}) with its slots where vanilla puts them; the client
 * reads those slots as a 176-wide slot frame starting {@link #SHIFT} pixels in (the payments at 36 and 62, the result at
 * 120, the pack at 8), and the offer list sits in the {@link #SHIFT}-wide gutter left of it. Every box is {x, y, w, h}
 * relative to that slot frame, like {@link CopperSlots}, so the GameTests can check that nothing overlaps.
 */
public final class CopperTrades {
	/** How far the slot frame starts inside vanilla's merchant menu (its slots' x minus this is their frame x). */
	public static final int SHIFT = 100;
	/** The slot frame's height: vanilla's merchant screen, hotbar included. */
	public static final int FRAME_H = 166;
	/** The offer list: seven rows of twenty, as vanilla shows. */
	public static final int ROWS = 7;
	public static final int ROW_W = 88;
	public static final int ROW_H = 20;
	public static final int LIST_X = -SHIFT + 4;
	public static final int LIST_Y = 18;
	/** The scroll bar right of the offer list. */
	public static final int SCROLL_X = LIST_X + ROW_W + 2;
	/** Where in a row the price, the second price and the goods are drawn (vanilla's places). */
	public static final int COST_A = 5;
	public static final int COST_B = 35;
	public static final int GOODS = 68;
	/** The level row over the payment slots: the level's name, then its bar to the frame's right edge. */
	public static final int[] LEVEL = {8, 18, 160, 9};
	/** The arrow between the second payment slot and the result. */
	public static final int[] ARROW = {86, 37, 26, 16};

	/** Whether {@code villager} works one of this mod's jobs (the retired ones too): their trades open in Copperline. */
	public static boolean ownJob(Villager villager) {
		ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
		return id != null && AliveWorkplace.MOD_ID.equals(id.getNamespace());
	}

	/** Vanilla's {@code Merchant.openTradingScreen} on the Copperline menu type: the same menu, offers and level packet. */
	public static void open(Villager villager, Player player, Component name, int level) {
		OptionalInt id = player.openMenu(new SimpleMenuProvider((i, inventory, p) -> new CopperTradeMenu(i, inventory, villager), name));
		if (id.isPresent()) {
			MerchantOffers offers = villager.getOffers();
			if (!offers.isEmpty()) {
				player.sendMerchantOffers(id.getAsInt(), offers, level, villager.getVillagerXp(), villager.showProgressBar(), villager.canRestock());
			}
		}
	}

	/** Offer row {@code i} on screen (0 is the top row shown). */
	public static int[] row(int i) {
		return new int[]{LIST_X, LIST_Y + i * ROW_H, ROW_W, ROW_H};
	}

	/** The scroll bar's track. */
	public static int[] scrollbar() {
		return new int[]{SCROLL_X, LIST_Y, 4, ROWS * ROW_H};
	}

	/** The window round it all (the gutter is the offer list). */
	public static int[] window() {
		return CopperSlots.window(FRAME_H, SHIFT, 0);
	}

	/** The row under {@code fx, fy} (frame pixels), 0 to {@link #ROWS} - 1, or -1. */
	public static int rowAt(double fx, double fy) {
		for (int i = 0; i < ROWS; i++) {
			if (CopperLayout.inside(row(i), fx, fy)) {
				return i;
			}
		}
		return -1;
	}

	/** The most the list can scroll with {@code offers} offers. */
	public static int maxScroll(int offers) {
		return Math.max(0, offers - ROWS);
	}

	/**
	 * The level bar's fill as vanilla's merchant screen draws it: {earned, this trade's} as fractions of the level's span,
	 * or null at Master (or past the levels), where no bar shows.
	 */
	public static float[] levelFill(int level, int xp, int futureXp) {
		if (level < 1 || level >= 5 || !VillagerData.canLevelUp(level)) {
			return null;
		}
		int min = VillagerData.getMinXpPerLevel(level);
		int span = VillagerData.getMaxXpPerLevel(level) - min;
		if (span <= 0) {
			return null;
		}
		float earned = Math.max(0f, Math.min(1f, (xp - min) / (float) span));
		float more = Math.max(0f, Math.min(1f - earned, futureXp / (float) span));
		return new float[]{earned, more};
	}

	/** {earned, needed} experience within the level, for the whisper line, or null at Master. */
	public static int[] levelNumbers(int level, int xp) {
		if (level < 1 || level >= 5 || !VillagerData.canLevelUp(level)) {
			return null;
		}
		int min = VillagerData.getMinXpPerLevel(level);
		return new int[]{Math.max(0, xp - min), VillagerData.getMaxXpPerLevel(level) - min};
	}

	private CopperTrades() {
	}
}
