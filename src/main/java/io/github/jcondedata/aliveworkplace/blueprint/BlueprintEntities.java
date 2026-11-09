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
 * frame or the armor on a stand — and never invulnerable or fixed in place, and only with the looks a survival
 * player's would have (B1101).
 */
public final class BlueprintEntities {
	private static final Map<String, Item> COST = Map.of(
		"minecraft:item_frame", Items.ITEM_FRAME,
		"minecraft:glow_item_frame", Items.GLOW_ITEM_FRAME,
		"minecraft:painting", Items.PAINTING,
		"minecraft:armor_stand", Items.ARMOR_STAND
	);

	/**
	 * B1101: the only data copied, the looks a decoration can have where a player puts it up: what it is, the way it
	 * faces ("Rotation" for a stand, B439; "Facing" or "facing" and the block it hangs on for a frame or a painting),
	 * a painting's picture, and the stand looks the mod has always copied (pose, arms, small, base plate). Everything
	 * else is dropped: contents and identity, and the looks only commands give (attributes such as scale, Glowing,
	 * Invisible, NoGravity, Silent, Invulnerable, Fixed, a name), so a file made in a creative world can't put up a
	 * giant, glowing or invisible decoration. An allow-list, so a key Minecraft adds later stays out until chosen.
	 */
	private static final Set<String> KEEP = Set.of(
		"id", "Rotation", "Facing", "facing", "TileX", "TileY", "TileZ", "variant", "ItemRotation",
		"Pose", "ShowArms", "Small", "NoBasePlate"
	);

	/** A copy of {@code nbt} that is safe to build from, or null if it isn't an entity builders put up. */
	@Nullable
	public static CompoundTag clean(CompoundTag nbt) {
		String id = Nbt.getString(nbt, "id");
		if (!COST.containsKey(id) || Nbt.getBoolean(nbt, "Marker")) {
			return null; // marker armor stands are technical, not decoration
		}
		CompoundTag out = new CompoundTag();
		for (String key : KEEP) {
			Tag value = nbt.get(key);
			if (value != null) {
				out.put(key, value.copy());
			}
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
