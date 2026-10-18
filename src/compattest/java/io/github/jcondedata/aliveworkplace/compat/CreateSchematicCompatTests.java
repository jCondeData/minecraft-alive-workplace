package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.CreateSchematics;
import io.github.jcondedata.aliveworkplace.table.TableServer;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 39.6 with the owner's Create port: the schematics players upload with Create's Schematic Table
 * ({@code schematics/uploaded/<player>/<name>.nbt} in the game folder) appear at the Blueprint Table when it opens, and a
 * written Schematic used on the table imports its own file, its names read from the item's data components by id. Blocks
 * of mods that aren't installed are counted and named; a missing file, a blank Schematic and a name with a path in it
 * are each refused with their own line. Each test writes into its own player folder and removes it after. Without
 * Create: {@code CreateBuildsGameTests.noSchematicsWithoutCreate}.
 */
public class CreateSchematicCompatTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	/** A file in a player's uploaded folder is at the table once someone opens it (with an empty hand), and builds as drawn. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUploadedSchematicAppearsAtTheTable(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_listed";
		ResourceLocation id = CreateSchematics.idFor(owner, "Little Mill.nbt");
		clear(server, owner, id);
		ServerPlayer player = playerAtTable(helper);
		try {
			write(server, owner, "Little Mill.nbt", structure(3, false));
			helper.assertTrue(id.getPath().equals("create_schematics/gametest_listed/little_mill"), "the schematic's blueprint is " + id);
			helper.assertTrue(!TableServer.listing(server).stream().anyMatch(e -> e.id().equals(id)), "listed before the table opened");
			helper.useBlock(TABLE, player); // an empty hand: the table opens
			helper.assertTrue(TableServer.listing(server).stream().anyMatch(e -> e.id().equals(id)), "the uploaded schematic isn't at the table");
			Blueprint blueprint = BlueprintLibrary.get(server, id).orElse(null);
			helper.assertTrue(blueprint != null && blueprint.solidBlockCount() == 9 && blueprint.size().getX() == 3,
				"the schematic didn't import as drawn: " + (blueprint == null ? "none" : blueprint.solidBlockCount() + " blocks, " + blueprint.size()));
			// Opening it again imports nothing new: no lines for files already in the library
			helper.assertTrue(CreateSchematics.sync(server).stream().noneMatch(m -> m.getString().contains("Little Mill")),
				"an imported schematic is imported again with nothing changed");
		} finally {
			leave(helper, player);
			clear(server, owner, id);
		}
		helper.succeed();
	}

	/** A written Schematic in hand used on the table imports the file it names; the unknown block is counted and named. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWrittenSchematicImportsItsFile(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_written";
		ResourceLocation id = CreateSchematics.idFor(owner, "gizmo_hut.nbt");
		clear(server, owner, id);
		ServerPlayer player = playerAtTable(helper);
		try {
			write(server, owner, "gizmo_hut.nbt", structure(3, true));
			ItemStack schematic = schematic("gizmo_hut.nbt", owner);
			helper.assertTrue(CreateSchematics.isSchematic(schematic), "create:schematic isn't taken for a Schematic");
			player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
			helper.useBlock(TABLE, player);
			Blueprint blueprint = BlueprintLibrary.get(server, id).orElse(null);
			helper.assertTrue(blueprint != null && blueprint.solidBlockCount() == 8, "the written schematic didn't import: "
				+ (blueprint == null ? "none" : blueprint.solidBlockCount() + " blocks"));
			helper.assertTrue(player.getMainHandItem().getCount() == 1, "the Schematic was used up");
			// What the player is told: the file, its uploader, and the block of a mod that isn't here, by name
			CreateSchematics.Result again = CreateSchematics.importItem(server, schematic);
			String said = again.message().getString();
			helper.assertTrue(again.imported() && id.equals(again.id()) && again.unknownBlocks() == 1, "imported again as " + again.id()
				+ " with " + again.unknownBlocks() + " unknown blocks");
			helper.assertTrue(said.contains("gizmo_hut.nbt") && said.contains(owner) && said.contains("nosuchmod:gizmo") && said.contains("1 block"),
				"the import line doesn't name the file, its uploader and the unknown block: " + said);
			helper.assertTrue(BlueprintLibrary.get(server, CreateSchematics.idFor(owner, "gizmo_hut_2.nbt")).isEmpty(), "imported again under a new name");
		} finally {
			leave(helper, player);
			clear(server, owner, id);
		}
		helper.succeed();
	}

	/** A Schematic whose file is gone says so; a blank Schematic says it's blank; neither makes a blueprint. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMissingFileSaysSo(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_missing";
		ResourceLocation id = CreateSchematics.idFor(owner, "gone.nbt");
		clear(server, owner, id);
		CreateSchematics.Result gone = CreateSchematics.importItem(server, schematic("gone.nbt", owner));
		helper.assertTrue(!gone.imported() && key(gone.message()).equals("message.aliveworkplace.create_schematic.missing"),
			"a missing file: " + key(gone.message()));
		String said = gone.message().getString();
		helper.assertTrue(said.contains("gone.nbt") && said.contains(owner), "the line doesn't name the file and its uploader: " + said);
		helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), "a blueprint came of a missing file");
		ItemStack blank = new ItemStack(Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("create", "schematic")));
		CreateSchematics.Result unwritten = CreateSchematics.importItem(server, blank);
		helper.assertTrue(!unwritten.imported() && key(unwritten.message()).equals("message.aliveworkplace.create_schematic.not_written"),
			"a blank Schematic: " + key(unwritten.message()));
		Language en = Language.getInstance();
		for (String line : List.of("imported", "missing", "not_written", "bad_name", "unreadable")) {
			helper.assertTrue(en.has("message.aliveworkplace.create_schematic." + line), line + " has no text");
		}
		helper.assertTrue(en.has("aliveworkplace.config.createSchematics") && en.has("aliveworkplace.config.createSchematics.tooltip"),
			"the createSchematics switch has no text");
		helper.succeed();
	}

	/** Names with a path in them are refused, as Create refuses them: nothing outside a player's uploaded folder is read. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void namesOutsideTheUploadsAreRefused(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_escape";
		Path outside = CreateSchematics.uploadedFolder(server).getParent().resolve("gametest_escape.nbt"); // schematics/, beside uploaded/
		ResourceLocation[] ids = {CreateSchematics.idFor(owner, "gametest_escape.nbt"), CreateSchematics.idFor("..", "gametest_escape.nbt"),
			CreateSchematics.idFor(owner, "../../gametest_escape.nbt"), CreateSchematics.idFor(owner, "hut.litematic")};
		for (ResourceLocation id : ids) {
			clear(server, owner, id);
		}
		try {
			writeTo(outside, structure(3, false));
			write(server, owner, "hut.litematic", structure(3, false));
			String[][] tries = {{"../gametest_escape.nbt", owner}, {"../../schematics/gametest_escape.nbt", owner}, {"gametest_escape.nbt", ".."},
				{"gametest_escape.nbt", "../.."}, {"x/../../gametest_escape.nbt", owner}, {outside.toString(), owner}, {"hut.litematic", owner},
				{"..\\gametest_escape.nbt", owner}};
			for (String[] t : tries) {
				CreateSchematics.Result result = CreateSchematics.importItem(server, schematic(t[0], t[1]));
				helper.assertTrue(!result.imported() && key(result.message()).equals("message.aliveworkplace.create_schematic.bad_name"),
					t[1] + " / " + t[0] + " wasn't refused: " + key(result.message()));
			}
			for (ResourceLocation id : ids) {
				helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), id + " came of a name outside the uploads");
			}
			helper.assertTrue(Files.exists(outside), "the file outside the uploads was touched");
		} finally {
			try {
				Files.deleteIfExists(outside);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			for (ResourceLocation id : ids) {
				clear(server, owner, id);
			}
		}
		helper.succeed();
	}

	/** Uploaded again under its name, a schematic replaces its blueprint (no "_2"); a broken file is said once, not at every opening. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUploadAgainReplacesItsBlueprint(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_again";
		ResourceLocation id = CreateSchematics.idFor(owner, "shed.nbt");
		ResourceLocation broken = CreateSchematics.idFor(owner, "broken.nbt");
		clear(server, owner, id, broken);
		try {
			Path file = write(server, owner, "shed.nbt", structure(3, false));
			helper.assertTrue(lines(CreateSchematics.sync(server), "shed.nbt") == 1, "the shed wasn't imported once");
			helper.assertTrue(BlueprintLibrary.get(server, id).map(Blueprint::solidBlockCount).orElse(0) == 9, "the first shed isn't 3 × 3");
			write(server, owner, "shed.nbt", structure(4, false));
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 60_000)); // uploaded again, a minute on
			helper.assertTrue(lines(CreateSchematics.sync(server), "shed.nbt") == 1, "the shed uploaded again wasn't imported again");
			helper.assertTrue(BlueprintLibrary.get(server, id).map(Blueprint::solidBlockCount).orElse(0) == 16,
				"the shed's blueprint wasn't replaced: " + BlueprintLibrary.get(server, id).map(Blueprint::solidBlockCount).orElse(0) + " blocks");
			helper.assertTrue(BlueprintLibrary.get(server, CreateSchematics.idFor(owner, "shed_2.nbt")).isEmpty(), "uploaded again as shed_2");
			writeTo(CreateSchematics.uploadedFolder(server).resolve(owner).resolve("broken.nbt"), null);
			List<Component> third = CreateSchematics.sync(server);
			helper.assertTrue(lines(third, "broken.nbt") == 1, "a broken file isn't said");
			List<Component> fourth = CreateSchematics.sync(server);
			helper.assertTrue(lines(fourth, "broken.nbt") == 0, "a broken file is said again with nothing changed");
			// Its time is a minute ahead of its blueprint's, but it hasn't changed since it was imported: not imported again
			helper.assertTrue(lines(third, "shed.nbt") + lines(fourth, "shed.nbt") == 0, "the shed is imported again with nothing changed");
			helper.assertTrue(BlueprintLibrary.get(server, broken).isEmpty(), "a broken file made a blueprint");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			clear(server, owner, id, broken);
		}
		helper.succeed();
	}

	/** With {@code createSchematics} off the table leaves Create's schematics alone: not listed, and a written one only opens it. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"create_schematics_off"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "create_schematics_off")
	public void theSwitchOffLeavesThemAlone(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_off";
		ResourceLocation id = CreateSchematics.idFor(owner, "kept_out.nbt");
		clear(server, owner, id);
		boolean was = CreateSchematics.ENABLED;
		ServerPlayer player = playerAtTable(helper);
		try {
			CreateSchematics.ENABLED = false;
			write(server, owner, "kept_out.nbt", structure(3, false));
			helper.useBlock(TABLE, player);
			player.setItemInHand(InteractionHand.MAIN_HAND, schematic("kept_out.nbt", owner));
			helper.useBlock(TABLE, player);
			helper.assertTrue(!CreateSchematics.active() && CreateSchematics.sync(server).isEmpty(), "the switch off still syncs");
			helper.assertTrue(BlueprintLibrary.get(server, id).isEmpty(), "imported with createSchematics off");
			CreateSchematics.ENABLED = true;
			helper.useBlock(TABLE, player);
			helper.assertTrue(BlueprintLibrary.get(server, id).isPresent(), "not imported once the switch is back on");
		} finally {
			CreateSchematics.ENABLED = was;
			leave(helper, player);
			clear(server, owner, id);
		}
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

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

	/** A structure file as Create saves one: {@code side} × 1 × {@code side} of stone, one of them a block of a mod that isn't here. */
	private static CompoundTag structure(int side, boolean gizmo) {
		CompoundTag tag = new CompoundTag();
		tag.put("size", ints(side, 1, side));
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		CompoundTag unknown = new CompoundTag();
		unknown.putString("Name", "nosuchmod:gizmo");
		palette.add(unknown);
		tag.put("palette", palette);
		ListTag blocks = new ListTag();
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				CompoundTag b = new CompoundTag();
				b.put("pos", ints(x, 0, z));
				b.putInt("state", gizmo && x == 1 && z == 1 ? 1 : 0);
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

	private static Path write(MinecraftServer server, String owner, String name, CompoundTag tag) {
		Path file = CreateSchematics.uploadedFolder(server).resolve(owner).resolve(name);
		writeTo(file, tag);
		return file;
	}

	/** Writes {@code tag} packed, as Create does; null writes a file cut short. */
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

	private static long lines(List<Component> messages, String file) {
		return messages.stream().filter(m -> m.getString().contains(file)).count();
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString();
	}

	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}
}
