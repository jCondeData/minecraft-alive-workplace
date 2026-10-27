package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

	/**
	 * Data that must not be copied: contents, identity, motion and anything that makes it unbreakable. Its facing
	 * ("Rotation") is kept, so an armour stand faces the way the blueprint draws it (B439).
	 */
	private static final Set<String> STRIP = Set.of(
		"UUID", "Pos", "Motion", "Item", "ItemDropChance", "ArmorItems", "HandItems", "ArmorDropChances",
		"HandDropChances", "body_armor_item", "body_armor_drop_chance", "Invulnerable", "Fixed", "Tags", "Passengers",
		"leash", "Leash", "Fire", "Air", "FallDistance", "PortalCooldown", "OnGround", "active_effects", "Brain",
		"DisabledSlots", "CustomName", "CustomNameVisible"
	);

	/** A copy of {@code nbt} that is safe to build from, or null if it isn't an entity builders put up. */
	@Nullable
	public static CompoundTag clean(CompoundTag nbt) {
		String id = Nbt.getString(nbt, "id");
		if (!COST.containsKey(id) || Nbt.getBoolean(nbt, "Marker")) {
			return null; // marker armor stands are technical, not decoration
		}
		CompoundTag out = nbt.copy();
		for (String key : STRIP) {
			out.remove(key);
		}
		return out;
	}

	/**
	 * B441: keeps only the entities builders put up in a saved structure's {@code entities} list (a template's
	 * {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate#save save}), each cleaned:
	 * a scan keeps its item frames, paintings and armor stands, empty, and never a villager, a mob, a dropped item or a
	 * cart with its load. Returns how many were kept.
	 */
	public static int keepBuildable(CompoundTag structure) {
		ListTag all = Nbt.getList(structure, "entities", Tag.TAG_COMPOUND);
		ListTag kept = new ListTag();
		for (int i = 0; i < all.size(); i++) {
			CompoundTag e = Nbt.compoundAt(all, i).copy();
			CompoundTag nbt = clean(Nbt.getCompound(e, "nbt"));
			if (nbt != null) {
				e.put("nbt", nbt);
				kept.add(e);
			}
		}
		structure.put("entities", kept);
		return kept.size();
	}

	/** The item that putting this entity up uses (air if it isn't one we build). */
	public static Item cost(CompoundTag nbt) {
		return COST.getOrDefault(Nbt.getString(nbt, "id"), Items.AIR);
	}

	private BlueprintEntities() {
	}
}
