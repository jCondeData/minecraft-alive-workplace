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
 * folder, overwrites one of ours when its new version differs from the one recorded in its marker (even after FTB Quests
 * rewrote the file and moved its text to the lang file, whose entries for our objects it then removes), never brings
 * back one the owner deleted, keeps every file with questbookKeepEdits, migrates a marker from before hashes, and waits
 * for an expansion's switch and for a chapter's mods.
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

	/** A chapter that needs a missing mod waits for it; a file of our name that is already there is ours and replaced. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chaptersWaitForTheirModsAndReplaceFilesOfOurName(GameTestHelper helper) {
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
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(chapters.resolve(plain.file())), jarBytes(plain)),
				"a stale file of our name wasn't replaced with the jar's chapter");

			// Cobblemon is added later: its chapters come in; the chapter the owner deleted stays deleted.
			Files.delete(chapters.resolve(plain.file()));
			int later = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			long pokemonCore = all.stream().filter(c -> c.milestone() == null && c.mods().contains("cobblemon")).count();
			helper.assertTrue(later == pokemonCore && Files.exists(chapters.resolve(pokemon.file())),
				"adding Cobblemon copied " + later + " chapters, wanted " + pokemonCore);
			helper.assertTrue(!Files.exists(chapters.resolve(plain.file())), "a chapter the owner deleted came back");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** A new version of a chapter replaces the old one, edited or not; a deleted one stays deleted, even when a new version comes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void newVersionsReplaceOldChaptersAndDeletedOnesStayDeleted(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> core = FtbQuestsBook.manifest().stream().filter(c -> c.milestone() == null && c.mods().isEmpty()).toList();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Path marker = config.resolve("aliveworkplace/questbook.txt");
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(Files.readString(marker).startsWith("# Alive Workplace questbook v2"), "no v2 marker");

			// An earlier version wrote old layouts of chapters 0, 1 and 2; the owner edited 1 and deleted 2.
			Path old = chapters.resolve(core.get(0).file());
			Path edited = chapters.resolve(core.get(1).file());
			Path deleted = chapters.resolve(core.get(2).file());
			for (int i = 0; i < 3; i++) {
				recordOldVersion(marker, core.get(i));
			}
			Files.writeString(old, "{ old layout }", StandardCharsets.UTF_8);
			Files.writeString(edited, "{ owner's edit }", StandardCharsets.UTF_8);
			Files.delete(deleted);

			int written = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(written == 2, "the update wrote " + written + " chapters, wanted 2");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(old), jarBytes(core.get(0))), "the old chapter wasn't updated");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(edited), jarBytes(core.get(1))), "the edited old chapter wasn't updated");
			helper.assertTrue(Files.readString(marker).contains(core.get(0).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(0)))),
				"the updated chapter's new hash wasn't recorded");
			helper.assertTrue(!Files.exists(deleted), "a chapter the owner deleted came back with a new version");

			// Another start changes nothing, and an edit made now (no new version) is kept.
			Files.writeString(edited, "{ owner's edit }", StandardCharsets.UTF_8);
			int again = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(again == 0 && Files.readString(edited).equals("{ owner's edit }") && !Files.exists(deleted),
				"a start without a new version wrote " + again + " chapters");

			// A marker from the version that took FTB Quests' rewrites for edits: such a file is updated, and deleting one brings a fresh copy as promised.
			Files.writeString(marker, Files.readString(marker).replace(core.get(0).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(0))),
				core.get(0).file() + " edited").replace(core.get(1).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(1))), core.get(1).file() + " edited"));
			Files.writeString(old, "{ rewritten by FTB Quests }", StandardCharsets.UTF_8);
			Files.delete(edited);
			int fresh = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(fresh == 2 && java.util.Arrays.equals(Files.readAllBytes(old), jarBytes(core.get(0)))
				&& java.util.Arrays.equals(Files.readAllBytes(edited), jarBytes(core.get(1))), "an \"edited\" marker line wrote " + fresh + " chapters, wanted 2");
			helper.assertTrue(!Files.exists(deleted), "the chapter the owner deleted came back");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/**
	 * The owner's bug: FTB Quests rewrites our chapter (reformatted, its text moved to lang/en_us.snbt). A new version
	 * still replaces it, and the lang entries of our objects go from every lang file while every other entry stays.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chaptersRewrittenByFtbQuestsAreUpdatedAndTheirLangEntriesRemoved(GameTestHelper helper) {
		Path config = tempDir();
		try {
			FtbQuestsBook.Chapter chapter = FtbQuestsBook.manifest().stream().filter(c -> c.milestone() == null && c.mods().isEmpty()).findFirst().orElseThrow();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Path lang = config.resolve("ftbquests/quests/lang");
			Path marker = config.resolve("aliveworkplace/questbook.txt");
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			Path file = chapters.resolve(chapter.file());
			String jarText = new String(jarBytes(chapter), StandardCharsets.UTF_8);
			List<String> ids = List.copyOf(new java.util.TreeSet<>(FtbQuestsBook.objectIds(jarText)));
			helper.assertTrue(ids.size() > 3, "found " + ids.size() + " object ids in " + chapter.file());

			// FTB Quests saves: text out of the chapter (reformatted), into its lang files, beside the pack's own entries.
			String rewritten = jarText.replaceAll("(?m)^\\s*(title|subtitle|description): .*$\\n", "").replace("\t", "  ");
			Files.writeString(file, rewritten, StandardCharsets.UTF_8);
			Files.createDirectories(lang);
			String[] ours = {"chapter." + ids.get(0) + ".title", "quest." + ids.get(1) + ".quest_desc", "task." + ids.get(2) + ".title"};
			String ftbStyle = "{\n\tchapter.0123456789ABCDEF.title: \"The pack's chapter\"\n"
				+ "\t" + ours[0] + ": \"Old title\"\n"
				+ "\t" + ours[1] + ": [\n\t\t\"Old line, with a } and a ] in it\"\n\t\t\"\"\n\t]\n"
				+ "\tquest.FEDCBA9876543210.quest_desc: [\n\t\t\"The pack's quest\"\n\t]\n"
				+ "\t" + ours[2] + ": \"Old task\"\n}\n";
			Files.writeString(lang.resolve("en_us.snbt"), ftbStyle, StandardCharsets.UTF_8);
			String commaStyle = "{\"" + ours[0] + "\": \"Ancien titre\", \"quest.FEDCBA9876543210.title\": \"Quête du pack\", " + ours[2] + ": \"Ancienne tâche\"}";
			Files.writeString(lang.resolve("fr_fr.snbt"), commaStyle, StandardCharsets.UTF_8);

			// Same version as recorded: the rewrite is FTB Quests' own and nothing is touched.
			int same = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(same == 0 && Files.readString(file).equals(rewritten) && Files.readString(lang.resolve("en_us.snbt")).equals(ftbStyle),
				"a start without a new version wrote " + same + " chapters or touched the lang file");

			// A new version ships: the rewritten chapter is replaced, our lang entries go, the pack's stay.
			recordOldVersion(marker, chapter);
			int written = FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(written == 1 && java.util.Arrays.equals(Files.readAllBytes(file), jarBytes(chapter)),
				"the chapter FTB Quests rewrote wasn't updated (wrote " + written + ")");
			String en = Files.readString(lang.resolve("en_us.snbt"));
			String fr = Files.readString(lang.resolve("fr_fr.snbt"));
			for (String key : ours) {
				helper.assertTrue(!en.contains(key) && !fr.contains(key), key + " is still in a lang file");
			}
			String enWanted = "{\n\tchapter.0123456789ABCDEF.title: \"The pack's chapter\"\n\tquest.FEDCBA9876543210.quest_desc: [\n\t\t\"The pack's quest\"\n\t]\n}\n";
			helper.assertTrue(en.equals(enWanted), "en_us.snbt became:\n" + en);
			net.minecraft.nbt.CompoundTag frTag = net.minecraft.nbt.TagParser.parseTag(fr);
			helper.assertTrue(frTag.size() == 1 && frTag.getString("quest.FEDCBA9876543210.title").equals("Quête du pack"), "fr_fr.snbt became: " + fr);
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException("fr_fr.snbt isn't valid SNBT any more", e);
		} finally {
			deleteAll(config);
		}
	}

	/** questbookKeepEdits keeps every existing file of ours, even with a new version; new chapters still come in. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keepEditsKeepsExistingChapters(GameTestHelper helper) {
		Path config = tempDir();
		try {
			List<FtbQuestsBook.Chapter> all = FtbQuestsBook.manifest();
			List<FtbQuestsBook.Chapter> core = all.stream().filter(c -> c.milestone() == null && c.mods().isEmpty()).toList();
			FtbQuestsBook.Chapter expansion = all.stream().filter(c -> c.milestone() != null && c.mods().isEmpty()).findFirst().orElseThrow();
			Path chapters = config.resolve("ftbquests/quests/chapters");
			Path marker = config.resolve("aliveworkplace/questbook.txt");
			Path lang = config.resolve("ftbquests/quests/lang/en_us.snbt");
			Files.createDirectories(chapters);
			Files.writeString(chapters.resolve(core.get(1).file()), "{ already here }", StandardCharsets.UTF_8);
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true, true);
			helper.assertTrue(Files.readString(chapters.resolve(core.get(1).file())).equals("{ already here }"), "a file already there was replaced");
			helper.assertTrue(Files.exists(chapters.resolve(core.get(0).file())), "a missing chapter wasn't added with questbookKeepEdits on");

			recordOldVersion(marker, core.get(0));
			Path edited = chapters.resolve(core.get(0).file());
			Files.writeString(edited, "{ owner's edit }", StandardCharsets.UTF_8);
			String langText = "{\n\tquest." + FtbQuestsBook.objectIds(new String(jarBytes(core.get(0)), StandardCharsets.UTF_8)).iterator().next() + ".title: \"Mine\"\n}\n";
			Files.createDirectories(lang.getParent());
			Files.writeString(lang, langText, StandardCharsets.UTF_8);
			int written = FtbQuestsBook.install(config, c -> c.milestone() == null || c.equals(expansion), mod -> true, true);
			helper.assertTrue(written == 1 && Files.exists(chapters.resolve(expansion.file())), "switching an expansion on wrote " + written + " chapters, wanted 1");
			helper.assertTrue(Files.readString(edited).equals("{ owner's edit }"), "questbookKeepEdits didn't keep the edited chapter");
			helper.assertTrue(Files.readString(lang).equals(langText), "questbookKeepEdits didn't keep the kept chapter's lang entries");
			helper.assertTrue(Files.readString(chapters.resolve(core.get(1).file())).equals("{ already here }"), "the file already there was replaced later");

			// Switched off again: the new version arrives.
			FtbQuestsBook.install(config, c -> c.milestone() == null || c.equals(expansion), mod -> true, false);
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(edited), jarBytes(core.get(0))), "with questbookKeepEdits off the new version didn't arrive");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(chapters.resolve(core.get(1).file())), jarBytes(core.get(1))),
				"with questbookKeepEdits off the file of our name wasn't replaced");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** A marker from before hashes: every chapter still there gets the jar's version and its hash; deleted ones stay deleted. */
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
			helper.assertTrue(lines.contains(core.get(1).file() + " " + FtbQuestsBook.sha256(jarBytes(core.get(1)))), "the changed chapter got no hash");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(chapters.resolve(core.get(1).file())), jarBytes(core.get(1))), "the changed chapter wasn't updated");
			helper.assertTrue(!Files.exists(chapters.resolve(core.get(2).file())), "a chapter the owner deleted came back");
			helper.assertTrue(Files.exists(chapters.resolve(core.get(3).file())), "a chapter new to the marker wasn't copied");

			// A later start, with no new version, leaves an edit made since alone.
			Files.writeString(chapters.resolve(core.get(1).file()), "{ owner's edit }", StandardCharsets.UTF_8);
			FtbQuestsBook.install(config, c -> c.milestone() == null, mod -> true);
			helper.assertTrue(Files.readString(chapters.resolve(core.get(1).file())).equals("{ owner's edit }"), "an edit was overwritten without a new version");
			helper.succeed();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteAll(config);
		}
	}

	/** Makes the marker say an older version of {@code chapter} was installed, as after a jar update. */
	private static void recordOldVersion(Path marker, FtbQuestsBook.Chapter chapter) throws IOException {
		String line = chapter.file() + " " + FtbQuestsBook.sha256(jarBytes(chapter));
		String text = Files.readString(marker);
		if (!text.contains(line)) {
			throw new IllegalStateException("the marker has no line " + line);
		}
		Files.writeString(marker, text.replace(line, chapter.file() + " " + FtbQuestsBook.sha256(("{ older " + chapter.file() + " }").getBytes(StandardCharsets.UTF_8))));
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
