package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

/**
 * QA (lane E, saves, 2026-10-26): the data we keep on a villager ({@code registry/ModAttachments}, every field
 * {@link SavedDataGameTests} has a sample for), found in a save holding something of another kind: at the field itself,
 * or at any key or entry inside it, down to four levels (a field whose shape changed between versions, a save opened
 * with an older jar after a newer one wrote it, an NBT edit). Each field is read with the codec Fabric reads it with:
 * a codec that answers "not this kind" lets Fabric skip that one field, but one that throws escapes the villager's load,
 * and Minecraft drops an entity whose load throws ("Exception loading entity"): the villager gone from the world.
 */
public class QaAttachmentKindsGameTests implements FabricGameTest {
	/** Values of every other kind a key could be found holding. */
	private static List<Tag> odd() {
		ListTag strings = new ListTag();
		strings.add(StringTag.valueOf("x"));
		ListTag compounds = new ListTag();
		CompoundTag entry = new CompoundTag();
		entry.putString("kind", "x");
		entry.putString("id", "x");
		entry.putString("type", "x");
		compounds.add(entry);
		return List.of(StringTag.valueOf("x"), StringTag.valueOf(""), StringTag.valueOf("NO_SUCH_KIND"), IntTag.valueOf(-7), new CompoundTag(),
			strings, compounds, new IntArrayTag(new int[] {-1, 999, Integer.MIN_VALUE}));
	}

	/**
	 * Calls {@code visit} with a copy of {@code tag} changed at each place in it (the tag itself, every key of a compound
	 * and every entry of a list, depth first): {@code change} gives what goes there.
	 */
	private static void eachPlace(Tag tag, int depth, UnaryOperator<Tag> change, Consumer<Tag> visit) {
		visit.accept(change.apply(tag));
		if (depth == 0) {
			return;
		}
		if (tag instanceof CompoundTag compound) {
			for (String key : compound.getAllKeys()) {
				eachPlace(compound.get(key), depth - 1, change, changed -> {
					CompoundTag copy = compound.copy();
					copy.put(key, changed);
					visit.accept(copy);
				});
			}
		} else if (tag instanceof ListTag list) {
			for (int i = 0; i < list.size(); i++) {
				int at = i;
				eachPlace(list.get(i), depth - 1, change, changed -> {
					// a list holds one kind of tag: an entry of another kind replaces the whole list's entries
					ListTag copy = new ListTag();
					if (changed.getId() == list.getElementType() || list.size() == 1) {
						for (int j = 0; j < list.size(); j++) {
							copy.add(j == at ? changed : list.get(j).copy());
						}
					} else {
						copy.add(changed);
					}
					visit.accept(copy);
				});
			}
		}
	}

	/** The codec Fabric saves an attachment with (FabricAttachment is a record of its AttachmentType). */
	@SuppressWarnings("unchecked")
	private static Codec<Object> codec(Object attachment) {
		try {
			java.lang.reflect.Method accessor = attachment.getClass().getRecordComponents()[0].getAccessor();
			accessor.setAccessible(true);
			return (Codec<Object>) ((AttachmentType<?>) accessor.invoke(attachment)).persistenceCodec();
		} catch (ReflectiveOperationException | RuntimeException e) {
			throw new GameTestAssertException("can't find the codec of " + attachment + ": " + e);
		}
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaAttachmentKinds"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaAttachmentKinds")
	public void aVillagersDataOfAnotherKindDoesNotLoseTheVillager(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DynamicOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<SavedDataGameTests.Entry> entries = SavedDataGameTests.entries(helper);
		List<String> wrong = new ArrayList<>();
		int[] tried = {0};
		int fields = 0;
		for (SavedDataGameTests.Entry entry : entries) {
			Codec<Object> codec = codec(entry.attachment());
			if (codec == null) {
				continue; // not saved
			}
			Tag value;
			try {
				value = codec.encodeStart(ops, entry.value()).getOrThrow();
			} catch (RuntimeException e) {
				wrong.add(entry.name() + ": its sample doesn't save: " + e);
				continue;
			}
			fields++;
			Set<String> seen = new HashSet<>();
			for (Tag oddValue : odd()) {
				eachPlace(value, 4, t -> oddValue.copy(), changed -> {
					if (!seen.add(changed.toString())) {
						return;
					}
					tried[0]++;
					try {
						codec.parse(ops, changed);
					} catch (Throwable e) {
						if (wrong.size() < 30) {
							wrong.add(entry.name() + " = " + changed + ": " + e);
						}
					}
				});
			}
		}
		helper.assertTrue(fields >= 60, "only " + fields + " of " + entries.size() + " fields have a codec");
		helper.assertTrue(tried[0] > 500, "only " + tried[0] + " odd saves were tried");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " villager saves whose load throws (" + tried[0] + " tried): " + wrong);
		helper.succeed();
	}
}
