package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.table.CreateSchematics;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane E, 2026-10-24), ROADMAP 39.6 from the player's side: Create's Schematic Table sends a schematic to the server
 * a piece at a time, so the file in {@code schematics/uploaded/<player>/} is cut short until the upload ends. Someone
 * opening the Blueprint Table meanwhile must not leave that schematic out for good: once the upload is complete the
 * table imports it. A written Schematic naming a file the server can't read says so, naming the file, and makes no
 * blueprint. Helpers as in
 * {@code CreateSchematicCompatTests}; each test writes into its own player folder and removes it after.
 */
public class QaCreateSchematicFilesCompatTests implements FabricGameTest {
	/** Opened mid-upload (the file cut short), the table imports the schematic once the upload has finished. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSchematicSeenMidUploadIsImportedOnceItsComplete(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_qa_upload";
		ResourceLocation id = CreateSchematics.idFor(owner, "barn.nbt");
		clear(server, owner, id);
		try {
			Path file = CreateSchematics.uploadedFolder(server).resolve(owner).resolve("barn.nbt");
			writeTo(file, null); // the upload under way
			CreateSchematics.sync(server);
			helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), "a blueprint came of a schematic cut short");
			writeTo(file, structure(3)); // the upload done, a few seconds on
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 30_000));
			List<Component> said = CreateSchematics.sync(server);
			Blueprint blueprint = BlueprintLibrary.get(server, id).orElse(null);
			helper.assertTrue(blueprint != null && blueprint.solidBlockCount() == 9, "the finished upload wasn't imported: "
				+ (blueprint == null ? "no blueprint" : blueprint.solidBlockCount() + " blocks") + "; the table said " + said.stream().map(Component::getString).toList());
			helper.assertTrue(said.stream().anyMatch(m -> m.getString().contains("barn.nbt")), "the import of the finished upload isn't said: "
				+ said.stream().map(Component::getString).toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			clear(server, owner, id);
		}
		helper.succeed();
	}

	/** A written Schematic whose file can't be read (cut short) says so, naming the file, and makes no blueprint. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWrittenSchematicOfAnUnreadableFileSaysSo(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_qa_unreadable";
		ResourceLocation id = CreateSchematics.idFor(owner, "torn.nbt");
		clear(server, owner, id);
		try {
			writeTo(CreateSchematics.uploadedFolder(server).resolve(owner).resolve("torn.nbt"), null);
			CreateSchematics.Result result = CreateSchematics.importItem(server, schematic("torn.nbt", owner));
			String said = result.message().getString();
			helper.assertTrue(!result.imported(), "a file that can't be read was imported: " + said);
			// The line every import uses for a file cut short, or 39.6's own unreadable line: either names the file
			helper.assertTrue(List.of("message.aliveworkplace.import.failed", "message.aliveworkplace.create_schematic.unreadable")
				.contains(key(result.message())), "not a line saying the file can't be read: " + key(result.message()) + " (" + said + ")");
			helper.assertTrue(said.contains("torn.nbt"), "the line doesn't name the file: " + said);
			helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), "a blueprint came of a file that can't be read");
		} finally {
			clear(server, owner, id);
		}
		helper.succeed();
	}

	// --- helpers (as in CreateSchematicCompatTests) -------------------------------------------------------------------

	/** A written Schematic naming {@code file}, uploaded by {@code owner}, as Create's Schematic Table writes it. */
	@SuppressWarnings("unchecked")
	private static ItemStack schematic(String file, String owner) {
		ItemStack stack = new ItemStack(Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("create", "schematic")));
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_file")), file);
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_owner")), owner);
		return stack;
	}

	/** A structure file as Create saves one: {@code side} × 1 × {@code side} of stone. */
	private static CompoundTag structure(int side) {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(side, 1, side));
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		tag.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				CompoundTag b = new CompoundTag();
				b.put("pos", ints(x, 0, z));
				b.putInt("state", 0);
				blocks.add(b);
			}
		}
		tag.put("blocks", blocks);
		tag.put("entities", new ListTag());
		tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return tag;
	}

	private static ListTag ints(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(IntTag.valueOf(v));
		}
		return list;
	}

	/** Writes {@code tag} packed, as Create does; null writes a file cut short (the start of an upload). */
	private static void writeTo(Path file, CompoundTag tag) {
		try {
			Files.createDirectories(file.getParent());
			if (tag == null) {
				Files.write(file, new byte[] {0x1F, (byte) 0x8B, 8, 0, 0});
			} else {
				NbtIo.writeCompressed(tag, file);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Removes {@code owner}'s uploaded folder and the blueprints made from it. */
	private static void clear(MinecraftServer server, String owner, ResourceLocation... ids) {
		Path dir = CreateSchematics.uploadedFolder(server).resolve(owner);
		try {
			if (Files.isDirectory(dir)) {
				try (Stream<Path> walk = Files.walk(dir)) {
					for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
						Files.deleteIfExists(p);
					}
				}
			}
			for (ResourceLocation id : ids) {
				Files.deleteIfExists(server.getStructureManager().createAndValidatePathToGeneratedStructure(id, ".nbt"));
				server.getStructureManager().remove(id);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString();
	}
}
