package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.compat.ftbquests.FtbQuestsBook;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The questbook's install hook (compat/ftbquests/FtbQuestsBook): it copies our FTB Quests chapters into a config
 * folder, updates only chapters still exactly as we wrote them (by the hash in its marker), never overwrites one edited
 * by the owner (or rewritten by FTB Quests), never brings back one the owner deleted, migrates a marker from before
 * hashes, and waits for an expansion's switch and for a chapter's mods.
 */
public class QuestbookInstallGameTests implements FabricGameTest {
	/** Core chapters only while the expansions are off; then each one once, and never again over the owner's edits. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chaptersAreCopiedOnceAndNeverOverwritten(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> all = FtbQuestsBook.manifest();
			long core = all.stream().filter(c -> c.milestone() == null).count();
			helper.assertTrue(all.size() >= 14 && core >= 5 && core < all.size(),
				"the manifest has " + all.size() + " chapters, " + core + " of them core");
			Path chapters = config.resolve("ftbquests/quests/chapters");

			// First start, expansions off: only the core chapters, byte for byte.
			int first = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(first == core, "copied " + first + " chapters with the expansions off, wanted " + core);
			for (FtbQuestsBook.Chapter c : all) {
				Path file = chapters.resolve(c.file());
				helper.assertTrue(Files.exists(file) == (c.milestone() == null), c.file() + (c.milestone() == null ? " is missing" : " came in while its expansion is off"));
				if (Files.exists(file)) {
					try (InputStream in = FtbQuestsBook.resource(c.file())) {
						helper.assertTrue(java.util.Arrays.equals(in.readAllBytes(), Files.readAllBytes(file)), c.file() + " isn't the jar's copy");
					}
				}
			}
			helper.assertTrue(Files.readString(config.resolve("aliveworkplace/questbook.txt")).startsWith("# Alive Workplace questbook v2"),
				"no version marker");

			// The owner edits one chapter and deletes another; the server starts again.
			Path edited = chapters.resolve(all.get(1).file());
			Files.writeString(edited, "{ owner's edit }", StandardCharsets.UTF_8);
			Path deleted = chapters.resolve(all.get(2).file());
			Files.delete(deleted);
			int second = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(second == 0, "a second start copied " + second + " chapters");
			helper.assertTrue(Files.readString(edited).equals("{ owner's edit }"), "the owner's edit was overwritten");
			helper.assertTrue(!Files.exists(deleted), "a chapter the owner deleted came back");

			// An expansion is switched on: only its chapter comes in.
			FtbQuestsBook.Chapter expansion = all.stream().filter(c -> c.milestone() != null && c.mods().isEmpty()).findFirst().orElseThrow();
			int third = FtbQuestsBook.install(config, c -> c.milestone() == null || c.milestone().equals(expansion.milestone()), mod -> true);
			helper.assertTrue(third == 1 && Files.exists(chapters.resolve(expansion.file())),
				"switching " + expansion.milestone() + " on copied " + third + " chapters");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** A chapter that needs a missing mod waits for it; a file of our name that is already there is kept and counted as installed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chaptersWaitForTheirModsAndKeepFilesAlreadyThere(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> all = FtbQuestsBook.manifest();
			FtbQuestsBook.Chapter pokemon = all.stream().filter(c -> c.milestone() == null && c.mods().contains("cobblemon")).findFirst().orElseThrow();
			FtbQuestsBook.Chapter plain = all.stream().filter(c -> c.milestone() == null && c.mods().isEmpty()).findFirst().orElseThrow();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Files.createDirectories(chapters);
			Files.writeString(chapters.resolve(plain.file()), "{ already here }", StandardCharsets.UTF_8);

			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> !mod.equals("cobblemon"));
			helper.assertTrue(!Files.exists(chapters.resolve(pokemon.file())), pokemon.file() + " came in without Cobblemon");
			helper.assertTrue(Files.readString(chapters.resolve(plain.file())).equals("{ already here }"), "a file already there was overwritten");

			// Cobblemon is added later: its chapters come in; the file that was there is still not touched.
			Files.delete(chapters.resolve(plain.file()));
			int later = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			long pokemonCore = all.stream().filter(c -> c.milestone() == null && c.mods().contains("cobblemon")).count();
			helper.assertTrue(later == pokemonCore && Files.exists(chapters.resolve(pokemon.file())),
				"adding Cobblemon copied " + later + " chapters, wanted " + pokemonCore);
			helper.assertTrue(!Files.exists(chapters.resolve(plain.file())), "the chapter that was already there was copied after all");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** A new version of a chapter replaces the old one the owner never touched; an edited one is kept, a deleted one stays deleted. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void untouchedChaptersAreUpdatedAndEditedOnesKept(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> core = FtbQuestsBook.manifest().stream().filter(c -> c.milestone() == null && c.mods().isEmpty()).toList();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Path marker = config.resolve("aliveworkplace/questbook.txt");
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(Files.readString(marker).startsWith("# Alive Workplace questbook v2"), "no v2 marker");

			// An earlier version wrote an old layout of chapter 0 and recorded its hash; the owner edited 1 and deleted 2.
			Path old = chapters.resolve(core.get(0).file());
			Path edited = chapters.resolve(core.get(1).file());
			Path deleted = chapters.resolve(core.get(2).file());
			String oldText = "{ old layout }";
			Files.writeString(old, oldText, StandardCharsets.UTF_8);
			String jarHash = FtbQuestsBook.sha256(Files.readAllBytes(edited));
			Files.writeString(marker, Files.readString(marker).replace(core.get(0).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(0))),
				core.get(0).file() + " " + FtbQuestsBook.sha256(oldText.getBytes(StandardCharsets.UTF_8))));
			Files.writeString(marker, Files.readString(marker).replace(core.get(1).file() + " " + jarHash,
				core.get(1).file() + " " + FtbQuestsBook.sha256("{ what we wrote last time }".getBytes(StandardCharsets.UTF_8))));
			Files.writeString(edited, "{ owner's edit }", StandardCharsets.UTF_8);
			Files.delete(deleted);

			int written = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(written == 1, "the update wrote " + written + " chapters, wanted 1");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(old), jarBytes(core.get(0))), "the untouched old chapter wasn't updated");
			helper.assertTrue(Files.readString(marker).contains(core.get(0).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(0)))),
				"the updated chapter's new hash wasn't recorded");
			helper.assertTrue(Files.readString(edited).equals("{ owner's edit }"), "the owner's edit was overwritten");
			helper.assertTrue(!Files.exists(deleted), "a chapter the owner deleted came back");

			// Another start changes nothing.
			int again = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(again == 0 && Files.readString(edited).equals("{ owner's edit }") && !Files.exists(deleted),
				"a third start wrote " + again + " chapters");

			// The owner deletes the chapter they edited, to get a fresh copy: it comes back as the jar's version.
			Files.delete(edited);
			int fresh = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(fresh == 1 && java.util.Arrays.equals(Files.readAllBytes(edited), jarBytes(core.get(1))),
				"deleting an edited chapter wrote " + fresh + " chapters, wanted a fresh copy");
			helper.assertTrue(!Files.exists(deleted), "the unedited chapter the owner deleted came back");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** A marker from before hashes: files that are one of our versions get hashes (and updates), edited ones are kept, deleted ones stay deleted. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHashlessMarkerMigrates(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> core = FtbQuestsBook.manifest().stream().filter(c -> c.milestone() == null).toList();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Path marker = config.resolve("aliveworkplace/questbook.txt");
			Files.createDirectories(chapters);
			Files.createDirectories(marker.getParent());
			Files.writeString(marker, "# Alive Workplace questbook v1: chapters already installed\n"
				+ core.get(0).file() + "\n" + core.get(1).file() + "\n" + core.get(2).file() + "\n", StandardCharsets.UTF_8);
			Files.write(chapters.resolve(core.get(0).file()), jarBytes(core.get(0)));
			Files.writeString(chapters.resolve(core.get(1).file()), "{ owner's edit }", StandardCharsets.UTF_8);

			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			String lines = Files.readString(marker);
			helper.assertTrue(lines.startsWith("# Alive Workplace questbook v2"), "the marker wasn't migrated");
			helper.assertTrue(lines.contains(core.get(0).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(0)))), "the untouched chapter got no hash");
			helper.assertTrue(lines.contains(core.get(1).file() + " edited"), "the edited chapter isn't marked as edited");
			helper.assertTrue(Files.readString(chapters.resolve(core.get(1).file())).equals("{ owner's edit }"), "the owner's edit was overwritten");
			helper.assertTrue(!Files.exists(chapters.resolve(core.get(2).file())), "a chapter the owner deleted came back");
			helper.assertTrue(Files.exists(chapters.resolve(core.get(3).file())), "a chapter new to the marker wasn't copied");

			// A later start leaves the edit alone for good.
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(Files.readString(chapters.resolve(core.get(1).file())).equals("{ owner's edit }"), "the owner's edit was overwritten later");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	private static byte[] jarBytes(FtbQuestsBook.Chapter chapter) throws IOException {
		try (InputStream in = FtbQuestsBook.resource(chapter.file())) {
			return in.readAllBytes();
		}
	}

	private static Path tempDir() {
		try {
			return Files.createTempDirectory("aliveworkplace-questbook");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void deleteAll(Path dir) {
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException ignored) {
			// a temp folder left behind is harmless
		}
	}
}
