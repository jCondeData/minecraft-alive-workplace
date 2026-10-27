package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.CreateSchematics;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, Create, 2026-10-27), from the wiki's Create page (39.6, "Schematics"): "at most 16 at one opening" of a
 * Blueprint Table, the rest at the next, and "Files over the importer's 8 MB limit are refused before they're read".
 * Each through the player's own entry point (opening the table with an empty hand, a written Schematic used on it),
 * both sides of each boundary. Each test has its own batch, since the table imports every player's uploads at once.
 */
public class QaCreateSchematicLimitsCompatTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	/** 18 new uploads: the first opening of the table imports 16 and says so 16 times, the next the last 2, a third nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_schematic_cap")
	public void eighteenUploadsComeSixteenAtOneOpeningAndTheRestAtTheNext(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_cap18";
		List<ResourceLocation> ids = new ArrayList<>();
		for (int i = 0; i < 18; i++) {
			ids.add(CreateSchematics.idFor(owner, name(i)));
		}
		clear(server, owner, ids);
		ServerPlayer player = playerAtTable(helper);
		try {
			for (int i = 0; i < 18; i++) {
				write(server, owner, name(i), structure(2));
			}
			helper.useBlock(TABLE, player);
			int first = imported(server, ids);
			helper.assertTrue(first == 16, "the first opening imported " + first + " of 18, not 16");
			// the files go in name order, so the last two are the ones left
			helper.assertTrue(BlueprintLibrary.get(server, ids.get(16)).isEmpty() && BlueprintLibrary.get(server, ids.get(17)).isEmpty(),
				"the files left for the next opening aren't the last two by name");
			helper.useBlock(TABLE, player);
			int second = imported(server, ids);
			helper.assertTrue(second == 18, "the next opening didn't bring the rest: " + second + " of 18");
			List<Component> third = CreateSchematics.sync(server);
			helper.assertTrue(third.stream().noneMatch(m -> m.getString().contains(owner)), "a third opening imported again: " + third);
		} finally {
			leave(helper, player);
			clear(server, owner, ids);
		}
		helper.succeed();
	}

	/** The cap counts imports, not files: exactly 16 new uploads all come at one opening, each with its own line. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_schematic_cap16")
	public void sixteenUploadsAllComeAtOneOpening(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_cap16";
		List<ResourceLocation> ids = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			ids.add(CreateSchematics.idFor(owner, name(i)));
		}
		clear(server, owner, ids);
		try {
			for (int i = 0; i < 16; i++) {
				write(server, owner, name(i), structure(2));
			}
			List<Component> lines = CreateSchematics.sync(server);
			long ours = lines.stream().filter(m -> m.getString().contains(owner)).count();
			helper.assertTrue(ours == 16 && imported(server, ids) == 16, "16 uploads at one opening: " + ours + " lines, "
				+ imported(server, ids) + " imported");
		} finally {
			clear(server, owner, ids);
		}
		helper.succeed();
	}

	/**
	 * An upload just over 8 MB is refused at the opening with the size line (its size and the limit, in KB), makes no
	 * blueprint and isn't tried again at the next opening; a written Schematic naming it is refused the same way. One
	 * of exactly 8 MB isn't refused for its size (it's read, and as it's no build file, refused for that).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_schematic_big")
	public void anUploadOverEightMegabytesIsRefusedBeforeItsRead(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String owner = "gametest_bigfile";
		ResourceLocation big = CreateSchematics.idFor(owner, "huge.nbt");
		ResourceLocation edge = CreateSchematics.idFor(owner, "just_eight.nbt");
		clear(server, owner, List.of(big, edge));
		ServerPlayer player = playerAtTable(helper);
		try {
			Path hugeFile = raw(server, owner, "huge.nbt", BlueprintFiles.MAX_FILE_BYTES + 1);
			raw(server, owner, "just_eight.nbt", BlueprintFiles.MAX_FILE_BYTES);
			List<Component> lines = CreateSchematics.sync(server);
			Component hugeLine = lines.stream().filter(m -> m.getString().contains("huge.nbt")).findFirst().orElse(null);
			helper.assertTrue(hugeLine != null, "nothing said of the 8 MB + 1 byte upload: " + lines);
			helper.assertTrue(hasKey(hugeLine, "message.aliveworkplace.import.error.file_too_big"),
				"the too-big upload isn't refused for its size: " + hugeLine.getString());
			String said = hugeLine.getString();
			helper.assertTrue(said.contains("8192 KB") && said.contains("is 8192 KB"), "the size line doesn't give the file's size and the limit in KB: " + said);
			helper.assertTrue(BlueprintLibrary.get(server, big).isEmpty(), "a blueprint came of a file over the limit");
			Component edgeLine = lines.stream().filter(m -> m.getString().contains("just_eight.nbt")).findFirst().orElse(null);
			helper.assertTrue(edgeLine != null && !hasKey(edgeLine, "message.aliveworkplace.import.error.file_too_big"),
				"a file of exactly 8 MB was refused for its size: " + (edgeLine == null ? "nothing said" : edgeLine.getString()));
			// not tried again while it's unchanged
			helper.assertTrue(CreateSchematics.sync(server).stream().noneMatch(m -> m.getString().contains(owner)),
				"the refused uploads were tried again at the next opening");
			// the player's other way in: a written Schematic naming the huge file
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, schematic("huge.nbt", owner));
			CreateSchematics.Result used = CreateSchematics.useOnTable(player, player.getMainHandItem());
			helper.assertTrue(!used.imported() && hasKey(used.message(), "message.aliveworkplace.import.error.file_too_big"),
				"a written Schematic of the huge file: " + used.message().getString());
			// uploaded again, small: it comes in
			write(server, owner, "huge.nbt", structure(2));
			Files.setLastModifiedTime(hugeFile, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
			helper.useBlock(TABLE, player);
			helper.assertTrue(BlueprintLibrary.get(server, big).isPresent(), "the file uploaded again under the limit wasn't imported");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			leave(helper, player);
			clear(server, owner, List.of(big, edge));
		}
		helper.succeed();
	}

	private static String name(int i) {
		return String.format("part_%02d.nbt", i);
	}

	private static int imported(MinecraftServer server, List<ResourceLocation> ids) {
		return (int) ids.stream().filter(id -> BlueprintLibrary.get(server, id).isPresent()).count();
	}

	/** Whether {@code message} or any of its arguments (all the way down) is the line {@code key}. */
	private static boolean hasKey(Component message, String key) {
		if (message.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) {
				return true;
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component c && hasKey(c, key)) {
					return true;
				}
			}
		}
		return message.getSiblings().stream().anyMatch(s -> hasKey(s, key));
	}

	@SuppressWarnings("unchecked")
	private static ItemStack schematic(String file, String owner) {
		ItemStack stack = new ItemStack(Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("create", "schematic")));
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_file")), file);
		stack.set((DataComponentType<String>) Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ResourceLocation.fromNamespaceAndPath("create", "schematic_owner")), owner);
		return stack;
	}

	/** A {@code side} × 1 × {@code side} slab of stone, as Create saves a schematic. */
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

	private static Path write(MinecraftServer server, String owner, String name, CompoundTag tag) {
		Path file = CreateSchematics.uploadedFolder(server).resolve(owner).resolve(name);
		try {
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(tag, file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return file;
	}

	/** A file of {@code bytes} zero bytes (no build file) in {@code owner}'s uploads. */
	private static Path raw(MinecraftServer server, String owner, String name, int bytes) throws IOException {
		Path file = CreateSchematics.uploadedFolder(server).resolve(owner).resolve(name);
		Files.createDirectories(file.getParent());
		Files.write(file, new byte[bytes]);
		return file;
	}

	private static void clear(MinecraftServer server, String owner, List<ResourceLocation> ids) {
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
