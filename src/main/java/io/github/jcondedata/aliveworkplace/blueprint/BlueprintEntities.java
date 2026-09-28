package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The entities a builder can put up from a blueprint, and what each costs: item frames, glow item frames,
 * paintings and armor stands. They are always put up empty — a blueprint never hands out the items in a
 * frame or the armor on a stand — and never invulnerable or fixed in place.
 */
public final class BlueprintEntities {
	private static final Map<String, Item> COST = Map.of(
		"minecraft:item_frame", Items.ITEM_FRAME,
		"minecraft:glow_item_frame", Items.GLOW_ITEM_FRAME,
		"minecraft:painting", Items.PAINTING,
		"minecraft:armor_stand", Items.ARMOR_STAND
	);

	/** Data that must not be copied: contents, identity, motion and anything that makes it unbreakable. */
	private static final Set<String> STRIP = Set.of(
		"UUID", "Pos", "Motion", "Rotation", "Item", "ItemDropChance", "ArmorItems", "HandItems", "ArmorDropChances",
		"HandDropChances", "body_armor_item", "body_armor_drop_chance", "Invulnerable", "Fixed", "Tags", "Passengers",
		"leash", "Leash", "Fire", "Air", "FallDistance", "PortalCooldown", "OnGround", "active_effects", "Brain",
		"DisabledSlots", "CustomName", "CustomNameVisible"
	);

	/** A copy of {@code nbt} that is safe to build from, or null if it isn't an entity builders put up. */
	@Nullable
	public static CompoundTag clean(CompoundTag nbt) {
		String id = nbt.getString("id");
		if (!COST.containsKey(id) || nbt.getBoolean("Marker")) {
			return null; // marker armor stands are technical, not decoration
		}
		CompoundTag out = nbt.copy();
		for (String key : STRIP) {
			out.remove(key);
		}
		return out;
	}

	/** The item that putting this entity up uses (air if it isn't one we build). */
	public static Item cost(CompoundTag nbt) {
		return COST.getOrDefault(nbt.getString("id"), Items.AIR);
	}

	private BlueprintEntities() {
	}
}
