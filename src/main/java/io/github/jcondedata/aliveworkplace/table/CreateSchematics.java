package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/**
 * Create's schematics at the Blueprint Table (ROADMAP 39.6, design note M39, "Schematics"). Create keeps the schematics
 * players upload with its Schematic Table in the game folder's {@code schematics/uploaded/<player>/<name>.nbt}: vanilla
 * structure files, which {@link BlueprintImporter} reads. When a table opens, every such file not yet imported (or
 * changed since) becomes the blueprint {@code aliveworkplace:create_schematics/<player>/<name>}, so the table lists it;
 * a written Schematic ({@code create:schematic}) used on the table imports its own file, named by the item's data
 * components {@code create:schematic_owner} and {@code create:schematic_file}, read by id (no Create class). Only with
 * Create installed and the {@code createSchematics} switch on (its expansion, M39, open).
 * <p>
 * Names are checked as Create checks them: a file name or owner with a path in it ({@code ../}, a slash) is refused,
 * and the file must lie in the uploaded folder.
 */
public final class CreateSchematics {
	/** Config {@code createSchematics}. */
	public static volatile boolean ENABLED = true;
	/** Where the imports go, under our namespace. */
	public static final String FOLDER = "create_schematics";
	static final String CREATE = "create";
	/** Most files imported at one opening of a table; the rest come the next time. */
	static final int MAX_IMPORTS_AT_ONCE = 16;
	/** Files that wouldn't import, with the time they were changed: not tried again until they change. */
	private static final Map<Path, Long> FAILED = Collections.synchronizedMap(new HashMap<>());
	/** Files imported since the server started, with the time they were changed: not imported again until they change. */
	private static final Map<Path, Long> IMPORTED = Collections.synchronizedMap(new HashMap<>());

	private CreateSchematics() {
	}

	/** What one import did: the blueprint's id (null if none was made) and what the player is told. */
	public record Result(@Nullable ResourceLocation id, int unknownBlocks, Component message) {
		public boolean imported() {
			return id != null;
		}
	}

	/** Whether the table takes Create's schematics: Create installed and the switch on. */
	public static boolean active() {
		return ENABLED && Platform.get().isModLoaded(CREATE);
	}

	/** Create's uploaded schematics folder: {@code <game folder>/schematics/uploaded} (Create's {@code CreatePaths}). */
	public static Path uploadedFolder(MinecraftServer server) {
		return server.getServerDirectory().toAbsolutePath().resolve("schematics").resolve("uploaded").normalize();
	}

	/** The blueprint a schematic becomes: {@code aliveworkplace:create_schematics/<owner>/<name>}, cleaned as uploads are. */
	public static ResourceLocation idFor(String owner, String fileName) {
		return AliveWorkplace.id(FOLDER + "/" + BlueprintImporter.sanitizeFolder(owner) + "/" + BlueprintImporter.sanitize(fileName));
	}

	/** Whether {@code stack} is Create's Schematic (written or not), by id. */
	public static boolean isSchematic(ItemStack stack) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return CREATE.equals(id.getNamespace()) && "schematic".equals(id.getPath());
	}

	/**
	 * Imports every uploaded schematic that isn't a blueprint yet or changed since it was imported (at most
	 * {@link #MAX_IMPORTS_AT_ONCE}), and says what came of each. Nothing without Create or with the switch off.
	 */
	public static List<Component> sync(MinecraftServer server) {
		if (!active()) {
			return List.of();
		}
		Path root = uploadedFolder(server);
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		List<Component> messages = new ArrayList<>();
		Set<ResourceLocation> seen = new HashSet<>();
		for (Path ownerDir : list(root, Files::isDirectory)) {
			String owner = ownerDir.getFileName().toString();
			if (!plainName(owner)) {
				continue;
			}
			for (Path file : list(ownerDir, p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))) {
				String name = file.getFileName().toString();
				ResourceLocation id = idFor(owner, name);
				if (!plainName(name) || !seen.add(id)) {
					continue; // two names that clean up to the same id: the first, sorted
				}
				long changed = modified(file);
				Long failedAt = FAILED.get(file);
				Long importedAt = IMPORTED.get(file);
				boolean current = importedAt != null ? importedAt == changed : upToDate(server, id, changed);
				if (current || failedAt != null && failedAt == changed) {
					continue;
				}
				if (messages.size() >= MAX_IMPORTS_AT_ONCE) {
					return messages;
				}
				messages.add(importFile(server, owner, name, file).message());
			}
		}
		return messages;
	}

	/**
	 * Imports the file a written Schematic names. Refused (and said so) when the item isn't written, when its names hold
	 * a path, and when the file isn't in the uploaded folder.
	 */
	public static Result importItem(MinecraftServer server, ItemStack stack) {
		String file = text(stack, "schematic_file");
		String owner = text(stack, "schematic_owner");
		if (file == null || owner == null || file.isEmpty() || owner.isEmpty()) {
			return failed(Component.translatable("message.aliveworkplace.create_schematic.not_written"));
		}
		Path root = uploadedFolder(server);
		Path path = root.resolve(owner).resolve(file).normalize();
		if (!plainName(owner) || !plainName(file) || !file.endsWith(".nbt") || !path.startsWith(root)
			|| !root.equals(path.getParent().getParent())) {
			AliveWorkplace.LOG.info("Refused a schematic naming {} / {}: not a file in {}", owner, file, root);
			return failed(Component.translatable("message.aliveworkplace.create_schematic.bad_name", file));
		}
		if (!Files.isRegularFile(path)) {
			return failed(Component.translatable("message.aliveworkplace.create_schematic.missing", file, owner));
		}
		return importFile(server, owner, file, path);
	}

	/**
	 * A written Schematic used on a table: its file imported, the player told how it went (then the table opens). A
	 * Schematic written without its size (by a command or another mod, not by Create's table) gets the rest of what
	 * Create's table writes ({@link #complete}): Create's own client code reads the size of the Schematic in hand and
	 * crashes the game without it (B189).
	 */
	public static Result useOnTable(ServerPlayer player, ItemStack stack) {
		MinecraftServer server = io.github.jcondedata.aliveworkplace.mc.Players.level(player).getServer();
		Result result = importItem(server, stack);
		if (result.imported()) {
			BlueprintLibrary.get(server, result.id()).ifPresent(blueprint -> complete(stack, blueprint.size()));
		}
		player.sendSystemMessage(result.message());
		return result;
	}

	/**
	 * A written Schematic naming {@code owner}'s uploaded {@code fileName}, {@code size} big, as Create's Schematic Table
	 * hands it back (Create's {@code SchematicItem.create}): every data component set, by id. Empty without Create.
	 */
	public static ItemStack written(String owner, String fileName, Vec3i size) {
		Item item = Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CREATE, "schematic"));
		if (item == null || item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		setIfAbsent(stack, "schematic_owner", owner);
		setIfAbsent(stack, "schematic_file", fileName);
		complete(stack, size);
		return stack;
	}

	/**
	 * Fills in what Create's Schematic Table writes on a Schematic and {@code stack} lacks: not deployed, anchored at the
	 * origin, not turned or mirrored, and {@code size} as its bounds. Create's client handler reads the bounds of a written
	 * Schematic in hand every tick and crashes when they are missing ({@code SchematicHandler.getBounds()} is null).
	 * Components already there are kept.
	 */
	public static void complete(ItemStack stack, Vec3i size) {
		if (!isSchematic(stack) || text(stack, "schematic_file") == null) {
			return;
		}
		setIfAbsent(stack, "schematic_deployed", Boolean.FALSE);
		setIfAbsent(stack, "schematic_anchor", BlockPos.ZERO);
		setIfAbsent(stack, "schematic_rotation", Rotation.NONE);
		setIfAbsent(stack, "schematic_mirror", Mirror.NONE);
		setIfAbsent(stack, "schematic_bounds", new Vec3i(size.getX(), size.getY(), size.getZ()));
	}

	/** Sets Create's data component {@code component} on {@code stack} to {@code value} when it isn't set, by id. */
	@SuppressWarnings("unchecked")
	private static <T> void setIfAbsent(ItemStack stack, String component, T value) {
		DataComponentType<?> type = Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(CREATE, component));
		if (type != null && !stack.has(type)) {
			stack.set((DataComponentType<T>) type, value);
		}
	}

	/** Imports {@code path} as {@link #idFor}, replacing an older import of it. */
	static Result importFile(MinecraftServer server, String owner, String name, Path path) {
		long changed = modified(path);
		try {
			if (Files.size(path) > BlueprintFiles.MAX_FILE_BYTES) {
				FAILED.put(path, changed);
				return failed(Component.translatable("message.aliveworkplace.import.failed", name, Component.translatable(
					"message.aliveworkplace.import.error.file_too_big", Files.size(path) / 1024, BlueprintFiles.MAX_FILE_BYTES / 1024)));
			}
			BlueprintImporter.Imported imported = BlueprintImporter.importAs(server, idFor(owner, name), Files.readAllBytes(path));
			FAILED.remove(path);
			IMPORTED.put(path, changed);
			return new Result(imported.id(), imported.unknownBlocks(),
				Component.translatable("message.aliveworkplace.create_schematic.imported", name, owner, imported.summary()));
		} catch (BlueprintFormatException e) {
			FAILED.put(path, changed);
			return failed(Component.translatable("message.aliveworkplace.import.failed", name, e.toComponent()));
		} catch (IOException e) {
			FAILED.put(path, changed);
			AliveWorkplace.LOG.warn("Could not read the schematic {}", path, e);
			return failed(Component.translatable("message.aliveworkplace.create_schematic.unreadable", name));
		}
	}

	private static Result failed(Component message) {
		return new Result(null, 0, message.copy().withStyle(ChatFormatting.YELLOW));
	}

	/** A plain file or folder name: no path in it, nothing hidden, nothing a file system reads as a drive or a parent. */
	static boolean plainName(String name) {
		return !name.isEmpty() && name.length() <= 128 && !name.startsWith(".") && name.indexOf('/') < 0 && name.indexOf('\\') < 0
			&& name.indexOf(':') < 0 && name.indexOf('\0') < 0;
	}

	/** Whether {@code id}'s saved blueprint is at least as new as the file it came from (for files not imported since the start). */
	private static boolean upToDate(MinecraftServer server, ResourceLocation id, long changed) {
		try {
			Path saved = server.getStructureManager().createAndValidatePathToGeneratedStructure(id, ".nbt");
			return Files.isRegularFile(saved) && modified(saved) >= changed;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static long modified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return -1;
		}
	}

	private static List<Path> list(Path dir, Predicate<Path> keep) {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(keep).sorted().limit(1024).toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	/** A string data component of Create's on {@code stack}, read by id; null if it isn't there. */
	@Nullable
	static String text(ItemStack stack, String component) {
		DataComponentType<?> type = Lookup.value(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(CREATE, component));
		if (type == null) {
			return null;
		}
		Object value = stack.get(type);
		return value instanceof String s ? s : null;
	}
}
