package io.github.jcondedata.aliveworkplace.mc;

import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Reading saved data. Changes: 1.21.5 makes the typed getters return {@code Optional} ({@code getInt(k)} becomes
 * {@code getIntOr(k, 0)}...) and drops the UUID helpers (porting.md "NBT getters"). Every getter here returns what
 * 1.21.1's did for a missing key: 0, false, "", or an empty tag/list/array.
 */
public final class Nbt {
	public static int getInt(CompoundTag tag, String key) {
		return tag.getInt(key);
	}

	public static long getLong(CompoundTag tag, String key) {
		return tag.getLong(key);
	}

	public static short getShort(CompoundTag tag, String key) {
		return tag.getShort(key);
	}

	public static byte getByte(CompoundTag tag, String key) {
		return tag.getByte(key);
	}

	public static float getFloat(CompoundTag tag, String key) {
		return tag.getFloat(key);
	}

	public static double getDouble(CompoundTag tag, String key) {
		return tag.getDouble(key);
	}

	public static boolean getBoolean(CompoundTag tag, String key) {
		return tag.getBoolean(key);
	}

	public static String getString(CompoundTag tag, String key) {
		return tag.getString(key);
	}

	public static CompoundTag getCompound(CompoundTag tag, String key) {
		return tag.getCompound(key);
	}

	/** The list at {@code key} if its elements are of {@code type} ({@link net.minecraft.nbt.Tag#TAG_COMPOUND}...). */
	public static ListTag getList(CompoundTag tag, String key, int type) {
		return tag.getList(key, type);
	}

	public static int[] getIntArray(CompoundTag tag, String key) {
		return tag.getIntArray(key);
	}

	public static long[] getLongArray(CompoundTag tag, String key) {
		return tag.getLongArray(key);
	}

	public static byte[] getByteArray(CompoundTag tag, String key) {
		return tag.getByteArray(key);
	}

	/** Whether {@code key} holds a tag of {@code type} (numbers match any number type). */
	public static boolean has(CompoundTag tag, String key, int type) {
		return tag.contains(key, type);
	}

	public static Set<String> keys(CompoundTag tag) {
		return tag.getAllKeys();
	}

	public static UUID getUuid(CompoundTag tag, String key) {
		return tag.getUUID(key);
	}

	public static boolean hasUuid(CompoundTag tag, String key) {
		return tag.hasUUID(key);
	}

	public static void putUuid(CompoundTag tag, String key, UUID value) {
		tag.putUUID(key, value);
	}

	/** The compound at {@code index} of a list (an empty one if it isn't there). */
	public static CompoundTag compoundAt(ListTag list, int index) {
		return list.getCompound(index);
	}

	public static ListTag listAt(ListTag list, int index) {
		return list.getList(index);
	}

	public static int intAt(ListTag list, int index) {
		return list.getInt(index);
	}

	public static double doubleAt(ListTag list, int index) {
		return list.getDouble(index);
	}

	private Nbt() {
	}
}
