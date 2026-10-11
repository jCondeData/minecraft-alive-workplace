package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.Nullable;

/**
 * Keepsakes (ROADMAP 31.8): when a villager has told a player their 10-heart event, they give them a keepsake, once:
 * an item of their job family ({@link JobFamilies}) named after them ("Dara's Lucky Pick"), with two lines of lore:
 * what it was to them, and who it came from. Noted on the friendship ({@link Friendship.Bond#keepsake}), so each
 * player gets each villager's keepsake once, whatever is told again later.
 *
 * <p>The items are vanilla's, but for the Pokémon family's Premier Ball, which is Cobblemon's and looked up by id:
 * without Cobblemon (or if the item is gone) the "First Poké Ball" is a snowball, the nearest thing to a white ball.
 */
public final class Keepsakes {
	/** The Pokémon family's keepsake, by id (Cobblemon is optional), and what stands in for it without Cobblemon. */
	public static final ResourceLocation POKE_BALL = ResourceLocation.fromNamespaceAndPath("cobblemon", "premier_ball");
	public static final Item POKE_BALL_FALLBACK = Items.SNOWBALL;
	/** A written book's title holds no more than this. */
	private static final int TITLE_LENGTH = 32;

	/**
	 * What {@code villager} gives as a keepsake: the keepsake itself first, then anything that goes with it (the cook's
	 * two pumpkin pies).
	 */
	public static List<ItemStack> of(ServerLevel level, Villager villager) {
		JobFamilies.Family family = JobFamilies.of(villager);
		Component name = Component.literal(villager.getDisplayName().getString());
		List<ItemStack> out = new ArrayList<>();
		ItemStack keepsake = switch (family) {
			case BUILDING -> enchanted(level, Items.IRON_SHOVEL, Enchantments.EFFICIENCY, 2);
			case MINING -> enchanted(level, Items.IRON_PICKAXE, Enchantments.FORTUNE, 1);
			case LAND -> new ItemStack(Items.TORCHFLOWER_SEEDS, 4);
			case ANIMALS -> enchanted(level, Items.FISHING_ROD, Enchantments.LUCK_OF_THE_SEA, 2);
			case KITCHEN -> recipe(name);
			case LEARNING -> enchanted(level, Items.ENCHANTED_BOOK, Enchantments.MENDING, 1);
			case HEALING -> new ItemStack(Items.GOLDEN_APPLE);
			case ARMS -> enchanted(level, Items.SHIELD, Enchantments.UNBREAKING, 2);
			case TRADE -> new ItemStack(Items.SPYGLASS);
			case MUSIC -> new ItemStack(Items.MUSIC_DISC_OTHERSIDE);
			case POKEMON -> new ItemStack(BuiltInRegistries.ITEM.getOptional(POKE_BALL).orElse(POKE_BALL_FALLBACK));
			case NONE -> new ItemStack(Items.CORNFLOWER);
		};
		keepsake.set(DataComponents.ITEM_NAME, name(family, name));
		Object village = HeartEvents.args(level, villager, Component.empty())[2];
		keepsake.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.aliveworkplace.keepsake." + family.id() + ".lore").withStyle(s -> s.withItalic(true).withColor(ChatFormatting.GRAY)),
			Component.translatable("item.aliveworkplace.keepsake.from", name, village).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)))));
		out.add(keepsake);
		if (family == JobFamilies.Family.KITCHEN) {
			out.add(new ItemStack(Items.PUMPKIN_PIE, 2));
		}
		return out;
	}

	/** "Dara's Lucky Pick": the keepsake of {@code family} as {@code villager} names it. */
	public static Component name(JobFamilies.Family family, Component villager) {
		return Component.translatable("item.aliveworkplace.keepsake." + family.id(), villager);
	}

	private static ItemStack enchanted(ServerLevel level, Item item, ResourceKey<Enchantment> enchantment, int strength) {
		ItemStack stack = new ItemStack(item);
		Registry<Enchantment> registry = Lookup.registry(level.registryAccess(), Registries.ENCHANTMENT);
		Lookup.holder(registry, enchantment).ifPresent(holder -> stack.enchant(holder, strength));
		return stack;
	}

	/** The cook's recipe, written out: a signed book of two pages. */
	private static ItemStack recipe(Component name) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		String title = name(JobFamilies.Family.KITCHEN, name).getString();
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough(title.length() > TITLE_LENGTH ? title.substring(0, TITLE_LENGTH) : title), name.getString(), 0,
			List.of(Filterable.passThrough(Component.translatable("item.aliveworkplace.keepsake.kitchen.page.1", name)),
				Filterable.passThrough(Component.translatable("item.aliveworkplace.keepsake.kitchen.page.2"))),
			true));
		return book;
	}

	/** Whether {@code villager} has given {@code player} their keepsake. */
	public static boolean given(Villager villager, ServerPlayer player) {
		return Friendship.of(villager).bond(player.getUUID()).keepsake();
	}

	/**
	 * {@code villager} gives {@code player} their keepsake, if they haven't yet: into the inventory (at their feet if
	 * it's full), with a line in the chat. Returns the keepsake given, or null if they had it already.
	 */
	@Nullable
	public static ItemStack give(ServerLevel level, Villager villager, ServerPlayer player) {
		if (given(villager, player)) {
			return null;
		}
		List<ItemStack> items = of(level, villager);
		ItemStack shown = items.get(0).copy();
		Friendship.keepsakeGiven(villager, player);
		for (ItemStack stack : items) {
			if (!player.getInventory().add(stack) || !stack.isEmpty()) {
				// A full inventory: at their feet, theirs to pick up (dropped as the player's own, whatever the game rules say of block drops).
				ItemEntity dropped = player.drop(stack, false);
				if (dropped != null) {
					dropped.setNoPickUpDelay();
					dropped.setTarget(player.getUUID());
				}
			}
		}
		Chat.chat(player, Component.translatable("message.aliveworkplace.keepsake.given", Component.literal(villager.getDisplayName().getString()),
			shown.getHoverName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.LIGHT_PURPLE));
		level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6f, 1.2f);
		return shown;
	}

	private Keepsakes() {
	}
}
