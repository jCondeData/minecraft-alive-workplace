package io.github.jcondedata.aliveworkplace.blueprint.io;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Turns uploaded or dropped-in files into blueprints the whole server can use. Imported builds are
 * stored as ordinary structure templates in {@code <world>/generated/aliveworkplace/structures/},
 * so they show up in the blueprint library, survive restarts, and can be edited with Structure Blocks.
 */
public final class BlueprintImporter {
	public static final String IMPORT_FOLDER = "aliveworkplace/import";
	private static final List<String> EXTENSIONS = List.of(".litematic", ".schem", ".schematic", ".nbt");

	public record Imported(ResourceLocation id, Blueprint blueprint, String format, int unknownBlocks, List<String> unknownNames) {
		public Component summary() {
			return io.github.jcondedata.aliveworkplace.work.Words.counted(unknownBlocks > 0 ? "message.aliveworkplace.import.done_with_unknown"
				: "message.aliveworkplace.import.done", unknownBlocks > 0 ? unknownBlocks : 0, id.toString(), blueprint.size().getX(), blueprint.size().getY(),
				blueprint.size().getZ(), unknownBlocks, names(unknownNames));
		}
	}

	/** Up to three block ids, "and N more" after them: what a player needs to know which mod is missing. */
	public static Component names(List<String> ids) {
		String shown = String.join(", ", ids.subList(0, Math.min(3, ids.size())));
		return ids.size() > 3 ? Component.translatable("message.aliveworkplace.import.and_more", shown, ids.size() - 3) : Component.literal(shown);
	}

	/**
	 * Imports one file. {@code folder} groups imports ("imported" for the server folder,
	 * "uploads/&lt;player&gt;" for uploads); the file name becomes the blueprint's name.
	 */
	public static Imported importBytes(MinecraftServer server, String folder, String fileName, byte[] bytes) throws BlueprintFormatException {
		StructureTemplateManager manager = server.getStructureManager();
		ResourceLocation id = uniqueId(manager, folder, fileName);
		BlueprintFiles.Result result = BlueprintFiles.read(id, bytes, server.getFixerUpper(), BlueprintFiles.DEFAULT_MAX_VOLUME);
		if (result.blueprint().solidBlockCount() == 0) {
			if (result.unknownBlocks() > 0) {
				throw new BlueprintFormatException("only_unknown", names(result.unknownNames()));
			}
			throw new BlueprintFormatException("empty");
		}
		StructureTemplate template = manager.getOrCreate(id);
		template.load(Lookup.lookup(BuiltInRegistries.BLOCK), BlueprintFiles.toStructureNbt(result.blueprint()));
		if (!manager.save(id)) {
			manager.remove(id);
			throw new BlueprintFormatException("save_failed");
		}
		AliveWorkplace.LOG.info("Imported blueprint {} ({} format, {} blocks, {} unknown)", id, result.format(),
			result.blueprint().solidBlockCount(), result.unknownBlocks());
		return new Imported(id, result.blueprint(), result.format(), result.unknownBlocks(), result.unknownNames());
	}

	/** Imports every supported file in {@code <world>/aliveworkplace/import/}, moving each to {@code done/} or {@code failed/}. */
	public static List<Component> importFolder(MinecraftServer server) {
		List<Component> messages = new ArrayList<>();
		Path folder = importFolderPath(server);
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			messages.add(Component.translatable("message.aliveworkplace.import.error.folder", folder.toString()));
			return messages;
		}
		List<Path> files;
		try (Stream<Path> stream = Files.list(folder)) {
			files = stream.filter(Files::isRegularFile).filter(p -> hasSupportedExtension(p.getFileName().toString())).sorted().toList();
		} catch (IOException e) {
			messages.add(Component.translatable("message.aliveworkplace.import.error.folder", folder.toString()));
			return messages;
		}
		for (Path file : files) {
			String name = file.getFileName().toString();
			try {
				Imported imported = importBytes(server, "imported", name, Files.readAllBytes(file));
				messages.add(imported.summary());
				moveTo(file, folder.resolve("done"));
			} catch (BlueprintFormatException e) {
				messages.add(Component.translatable("message.aliveworkplace.import.failed", name, e.toComponent()));
				moveTo(file, folder.resolve("failed"));
			} catch (IOException e) {
				messages.add(Component.translatable("message.aliveworkplace.import.failed", name, e.getMessage()));
			}
		}
		if (files.isEmpty()) {
			messages.add(Component.translatable("message.aliveworkplace.import.nothing", folder.toString()));
		}
		return messages;
	}

	public static Path importFolderPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(IMPORT_FOLDER).normalize();
	}

	public static boolean hasSupportedExtension(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		return EXTENSIONS.stream().anyMatch(lower::endsWith);
	}

	/** {@code aliveworkplace:<folder>/<clean_name>}, with _2, _3... if that name is taken. */
	public static ResourceLocation uniqueId(StructureTemplateManager manager, String folder, String fileName) {
		String base = folder + "/" + sanitize(fileName);
		ResourceLocation id = AliveWorkplace.id(base);
		for (int n = 2; manager.get(id).isPresent(); n++) {
			id = AliveWorkplace.id(base + "_" + n);
		}
		return id;
	}

	public static String sanitize(String fileName) {
		String name = fileName;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		name = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
		if (name.length() > 48) {
			name = name.substring(0, 48);
		}
		return name.isEmpty() ? "blueprint" : name;
	}

	public static String sanitizeFolder(String playerName) {
		String clean = playerName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
		return clean.isEmpty() ? "player" : clean;
	}

	private static void moveTo(Path file, Path dir) {
		try {
			Files.createDirectories(dir);
			Files.move(file, dir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			AliveWorkplace.LOG.warn("Could not move {} to {}", file, dir, e);
		}
	}

	private BlueprintImporter() {
	}
}
