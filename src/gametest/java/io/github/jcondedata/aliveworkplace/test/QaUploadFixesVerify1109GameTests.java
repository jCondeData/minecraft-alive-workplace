package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Requests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * QA (qa-lane-b-1109-1537): checks the fixes for B1088, B1099, B1100, B1101 and B1098, written from each bug's Expected,
 * through a real upload (BlueprintImporter.importBytes, as the Blueprint Table's upload) and a real builder:
 * <ul>
 * <li>B1088/B1100: a line from a file shows at most 384 letters measured as the game shows it (the language's text for a
 * known key, a translation's fallback, a keybind with no binding shows its name's language text), on both sides of 384, and
 * odd formats (a mix, a nested fallback, a format the game can't read, %N$ indexes too big) copy without an error;</li>
 * <li>B1099: lines and names nested deeper than any player's, in a sign's back side, a banner's name and an object chain,
 * are copied as their words, the build finishes, and also after a save and reload in the middle of the job;</li>
 * <li>B1101: an armor stand and a glow item frame from a creative world's file keep their facing and the stand looks the
 * spec keeps (pose, arms, small, base plate) and lose every look a survival player can't give;</li>
 * <li>B1098: the Storehouse board's messages read as sentences for each stand-alone request name, one and many.</li>
 * </ul>
 */
public class QaUploadFixesVerify1109GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(2, 2, 26);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final int MAX_LINE = 384;
	/** A vanilla key every client's English knows, much longer than its name. */
	private static final String LONG_KEY = "multiplayerWarning.message";

	// ---------------------------------------------------------------- an upload built by a real builder

	/** One block of the file: what it is and how its saved data is changed in the file. */
	record Part(Block block, Consumer<CompoundTag> edit) {
	}

	/**
	 * Uploads a file of {@code parts} in a row (each block's data changed by its edit, in the file only), has a builder build
	 * it, optionally saves and reloads the site and the builder in the middle of the job, and checks the built block entities.
	 */
	static void build(GameTestHelper helper, List<Part> parts, boolean reloadMidJob, Consumer<List<BlockEntity>> check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < parts.size(); x++) {
			helper.setBlock(SRC.offset(x, 0, 0), parts.get(x).block());
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(parts.size(), 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < parts.size(); x++) {
			helper.setBlock(SRC.offset(x, 0, 0), Blocks.AIR);
		}
		CompoundTag file = template.save(new CompoundTag());
		ListTag blocks = file.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag entry = blocks.getCompound(i);
			int x = entry.getList("pos", Tag.TAG_INT).getInt(0);
			if (entry.contains("nbt", Tag.TAG_COMPOUND)) {
				CompoundTag nbt = entry.getCompound("nbt");
				parts.get(x).edit().accept(nbt);
				entry.put("nbt", nbt);
			}
		}
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_verifier",
				"qa1109v_" + Long.toHexString(level.getGameTime()) + "_" + parts.size() + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, BENCH.east())};
		Builders.employ(level, builder[0], helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(ORIGIN);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder[0], null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		boolean[] reloaded = {false};
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (!reloadMidJob || reloaded[0] || BuildSiteManager.get(level).get(site.id()) == null) {
				return;
			}
			// mid-job: something of the row is up, not all of it
			int up = 0;
			for (int x = 0; x < parts.size(); x++) {
				if (!level.getBlockState(origin.offset(x, 0, 0)).isAir()) {
					up++;
				}
			}
			if (up > 0 && up < parts.size()) {
				reloaded[0] = true;
				builder[0] = reload(helper, level, builder[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "the site didn't come back from its save");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status()
				+ " missing " + site.missing());
			if (reloadMidJob) {
				helper.assertTrue(reloaded[0], "(setup) the site was never saved and reloaded in the middle of the job");
			}
			if (!checked[0]) {
				List<BlockEntity> built = new ArrayList<>();
				for (int x = 0; x < parts.size(); x++) {
					built.add(level.getBlockEntity(origin.offset(x, 0, 0)));
				}
				check.accept(built);
				checked[0] = true;
			}
		});
	}

	/** Saves and loads the build sites and the builder, as a server restart does. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	static Consumer<CompoundTag> front(String json) {
		return nbt -> setLine(nbt, "front_text", json);
	}

	static Consumer<CompoundTag> back(String json) {
		return nbt -> setLine(nbt, "back_text", json);
	}

	private static void setLine(CompoundTag nbt, String side, String json) {
		CompoundTag text = nbt.getCompound(side);
		ListTag messages = text.getList("messages", Tag.TAG_STRING);
		while (messages.size() < 4) {
			messages.add(StringTag.valueOf("\"\""));
		}
		messages.set(0, StringTag.valueOf(json));
		text.put("messages", messages);
		nbt.put(side, text);
	}

	private static String savedLine(GameTestHelper helper, BlockEntity sign, String side) {
		CompoundTag nbt = sign.saveWithoutMetadata(helper.getLevel().registryAccess());
		return nbt.getCompound(side).getList("messages", Tag.TAG_STRING).getString(0);
	}

	/**
	 * The letters a client shows for {@code component}: as the server shows it (the same English for vanilla keys), except
	 * that a keybind part, which the server shows as its name, shows its name's language text on a client with no such
	 * binding (KeyMapping.createNameSupplier).
	 */
	static int shownOnAClient(Component component) {
		int count = component.getString().length();
		List<Component> todo = new ArrayList<>(List.of(component));
		while (!todo.isEmpty()) {
			Component part = todo.remove(todo.size() - 1);
			if (part.getContents() instanceof KeybindContents keybind) {
				count += Language.getInstance().getOrDefault(keybind.getName(), keybind.getName()).length() - keybind.getName().length();
			}
			todo.addAll(part.getSiblings());
		}
		return count;
	}

	private static String text(String letters) {
		return "{\"text\":\"" + letters + "\"}";
	}

	// ---------------------------------------------------------------- B1088, B1100: 384 letters, as the game shows them

	/**
	 * Each line on both sides of 384 letters shown: plain text, a known vanilla key's English text, a keybind of that key
	 * (no binding has the name, so a client shows its English), and an unknown key's fallback. 384 is kept whole as it was
	 * written; 385 shows no more than 384.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaUploadFixesVerify1109Boundary"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadFixesVerify1109Boundary")
	public void uploadedLinesAreKeptAtThreeHundredEightyFourLettersShownAndCutAtOneMore(GameTestHelper helper) {
		int key = Language.getInstance().getOrDefault(LONG_KEY, LONG_KEY).length();
		helper.assertTrue(key > 200 && key < MAX_LINE, "(setup) " + LONG_KEY + " shows " + key + " letters");
		List<String> lines = new ArrayList<>();
		for (int extra = 0; extra <= 1; extra++) {
			int n = MAX_LINE + extra;
			lines.add(text("a".repeat(n)));
			lines.add("[{\"translate\":\"" + LONG_KEY + "\"}," + text("b".repeat(n - key)) + "]");
			lines.add("[{\"keybind\":\"" + LONG_KEY + "\"}," + text("c".repeat(n - key)) + "]");
			lines.add("{\"translate\":\"qa.verify.no_such_key\",\"fallback\":\"" + "d".repeat(n) + "\"}");
		}
		String[] what = {"plain text", "a vanilla key's text and plain text", "a keybind to a vanilla key and plain text",
			"an unknown key's fallback"};
		List<Part> parts = new ArrayList<>();
		for (String line : lines) {
			parts.add(new Part(Blocks.OAK_SIGN, front(line)));
		}
		build(helper, parts, false, built -> {
			List<String> wrong = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				if (!(built.get(i) instanceof SignBlockEntity sign)) {
					wrong.add("no sign built for line " + i);
					continue;
				}
				int written = MAX_LINE + i / 4;
				int shown = shownOnAClient(sign.getFrontText().getMessage(0, false));
				String saved = savedLine(helper, sign, "front_text");
				// kept whole: the same letters, and still the part it was written as (the game saves it in its own JSON form; plain
				// text as a bare string)
				String[] part = {"a", "\"translate\"", "\"keybind\"", "\"fallback\""};
				if (written == MAX_LINE && (!saved.contains(part[i % 4]) || shown != MAX_LINE)) {
					wrong.add(what[i % 4] + " showing exactly 384 letters wasn't kept whole: shows " + shown + ", saved as "
						+ saved.substring(0, Math.min(80, saved.length())));
				}
				if (written > MAX_LINE && (shown > MAX_LINE || shown == 0)) {
					wrong.add(what[i % 4] + " showing 385 letters shows " + shown + " once built (expected 1 to 384)");
				}
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		});
	}

	/**
	 * Lines a file can carry that no sign editor writes: a mix of a translation, a keybind and plain text; a fallback that
	 * repeats its argument, nested; formats the game can't read (an unsupported %d, a %0$s, indexes past an int); and a
	 * keybind of a real binding. Each is copied without an error, shows at most 384 letters, and the build finishes.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaUploadFixesVerify1109Formats"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadFixesVerify1109Formats")
	public void oddTranslationsAndKeybindsInAnUploadShowAtMostThreeHundredEightyFourLetters(GameTestHelper helper) {
		String repeat = "{\"translate\":\"qa.verify.unknown_repeat\",\"fallback\":\"%1$s%1$s%1$s%1$s%1$s\",\"with\":[%s]}";
		String nested = "\"abc\"";
		for (int i = 0; i < 7; i++) {
			nested = repeat.replace("[%s]", "[" + nested + "]");
		}
		List<String> lines = List.of(
			// translation.test.complex uses its first argument three times; with a keybind and plain words after it
			"[{\"translate\":\"translation.test.complex\",\"with\":[\"" + "x".repeat(40) + "\",\"" + "y".repeat(40) + "\",\""
				+ "z".repeat(40) + "\"]},{\"keybind\":\"" + LONG_KEY + "\"},\"" + "w".repeat(60) + "\"]",
			nested,
			"{\"translate\":\"qa.verify.unknown_d\",\"fallback\":\"%d and %s\",\"with\":[\"" + "e".repeat(300) + "\"]}",
			"{\"translate\":\"qa.verify.unknown_zero\",\"fallback\":\"%0$s\",\"with\":[\"" + "f".repeat(300) + "\"]}",
			"{\"translate\":\"qa.verify.unknown_big\",\"fallback\":\"%2147483648$s\",\"with\":[\"g\"]}",
			"{\"translate\":\"qa.verify.unknown_huge\",\"fallback\":\"%99999999999999999999$s%1$s\",\"with\":[\"h\"]}",
			"{\"keybind\":\"key.jump\"}");
		List<Part> parts = new ArrayList<>();
		for (String line : lines) {
			parts.add(new Part(Blocks.OAK_SIGN, front(line)));
		}
		build(helper, parts, false, built -> {
			List<String> wrong = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				if (!(built.get(i) instanceof SignBlockEntity sign)) {
					wrong.add("no sign built for line " + i + ": " + built.get(i));
					continue;
				}
				int shown;
				try {
					shown = shownOnAClient(sign.getFrontText().getMessage(0, false));
				} catch (RuntimeException e) {
					wrong.add("line " + i + " throws when shown: " + e);
					continue;
				}
				if (shown > MAX_LINE) {
					wrong.add("line " + i + " (" + lines.get(i).substring(0, Math.min(60, lines.get(i).length())) + "...) shows " + shown
						+ " letters once built");
				}
			}
			// the mix shows 3*40+40+40+the complex format's own words+the key's English+60, well past 384: it must be cut
			// and keep words
			if (built.get(0) instanceof SignBlockEntity sign && sign.getFrontText().getMessage(0, false).getString().isEmpty()) {
				wrong.add("the mixed line lost all its words");
			}
			// the jump keybind is a real binding: a client shows the bound key, a few letters, so it stays a keybind
			if (built.get(6) instanceof SignBlockEntity sign && !savedLine(helper, sign, "front_text").contains("\"keybind\":\"key.jump\"")) {
				wrong.add("the jump keybind didn't stay a keybind: " + savedLine(helper, sign, "front_text"));
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		});
	}

	// ---------------------------------------------------------------- B1099: text nested deeper than any player's

	private static String deepArrays(int depth, String words) {
		return "[".repeat(depth) + "\"" + words + "\"" + "]".repeat(depth);
	}

	private static String deepObjects(int depth, String words) {
		return "{\"text\":\"\",\"extra\":[".repeat(depth) + "{\"text\":\"" + words + "\"}" + "]}".repeat(depth);
	}

	/**
	 * A sign whose back line is 30,000 arrays deep, one whose front line is a 2,500-deep chain of objects (each part's
	 * "extra"), lines just under and just over a few hundred levels, and a banner whose name is 30,000 deep: the builder
	 * copies them all without an error and the build finishes, each line showing its words and at most 384 letters.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaUploadFixesVerify1109Deep"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadFixesVerify1109Deep")
	public void textNestedDeeperThanAnyPlayersIsCopiedAsItsWordsAndTheBuildFinishes(GameTestHelper helper) {
		String banner = deepArrays(30000, "Deep banner");
		helper.assertTrue(banner.length() < 65535 && deepObjects(2500, "x").length() < 65535, "(setup) a line doesn't fit an NBT string");
		List<Part> parts = List.of(
			new Part(Blocks.OAK_SIGN, back(deepArrays(30000, "deep back"))),
			new Part(Blocks.OAK_SIGN, front(deepObjects(2500, "deep chain"))),
			new Part(Blocks.OAK_SIGN, front(deepArrays(250, "just under"))),
			new Part(Blocks.OAK_SIGN, front(deepArrays(260, "just over"))),
			new Part(Blocks.OAK_SIGN, front(deepObjects(300, "objects over"))),
			new Part(Blocks.WHITE_BANNER, nbt -> nbt.putString("CustomName", banner)));
		String[] words = {"deep back", "deep chain", "just under", "just over", "objects over"};
		build(helper, parts, false, built -> {
			List<String> wrong = new ArrayList<>();
			for (int i = 0; i < words.length; i++) {
				if (!(built.get(i) instanceof SignBlockEntity sign)) {
					wrong.add("no sign built for '" + words[i] + "': " + built.get(i));
					continue;
				}
				Component line = (i == 0 ? sign.getBackText() : sign.getFrontText()).getMessage(0, false);
				String shown = line.getString();
				if (!shown.contains(words[i]) || shown.length() > MAX_LINE) {
					wrong.add("the line '" + words[i] + "' shows '" + shown.substring(0, Math.min(60, shown.length())) + "' ("
						+ shown.length() + " letters)");
				}
			}
			if (!(built.get(5) instanceof BannerBlockEntity flag)) {
				wrong.add("no banner built: " + built.get(5));
			} else {
				String saved = flag.saveWithoutMetadata(helper.getLevel().registryAccess()).getString("CustomName");
				if (saved.length() > 2 * MAX_LINE + 256 || (!saved.isEmpty() && !saved.contains("Deep banner"))) {
					wrong.add("the banner's name was kept as " + saved.length() + " characters: "
						+ saved.substring(0, Math.min(60, saved.length())));
				}
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		});
	}

	/**
	 * B1099's other half: "the site is saved, so the restarted server likely crashes again as the builder resumes". A file
	 * of planks and two signs nested 30,000 deep, saved and reloaded (sites and builder) in the middle of the job: the
	 * builder resumes, copies both signs without an error, and the build finishes.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaUploadFixesVerify1109DeepReload"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadFixesVerify1109DeepReload")
	public void aDeepLineSurvivesASaveAndReloadInTheMiddleOfTheBuild(GameTestHelper helper) {
		Consumer<CompoundTag> none = nbt -> {
		};
		List<Part> parts = List.of(
			new Part(Blocks.OAK_PLANKS, none), new Part(Blocks.OAK_PLANKS, none), new Part(Blocks.OAK_PLANKS, none),
			new Part(Blocks.OAK_PLANKS, none), new Part(Blocks.OAK_PLANKS, none), new Part(Blocks.OAK_PLANKS, none),
			new Part(Blocks.OAK_SIGN, front(deepArrays(30000, "after the restart"))),
			new Part(Blocks.OAK_SIGN, back(deepObjects(2500, "back after the restart"))));
		build(helper, parts, true, built -> {
			helper.assertTrue(built.get(6) instanceof SignBlockEntity && built.get(7) instanceof SignBlockEntity,
				"the signs weren't built after the reload: " + built.get(6) + ", " + built.get(7));
			String front = ((SignBlockEntity) built.get(6)).getFrontText().getMessage(0, false).getString();
			String back = ((SignBlockEntity) built.get(7)).getBackText().getMessage(0, false).getString();
			helper.assertTrue(front.equals("after the restart") && back.equals("back after the restart"),
				"after a reload mid-build the deep lines show '" + front + "' and '" + back + "'");
		});
	}

	// ---------------------------------------------------------------- B1101: stands and frames as a survival player's

	/**
	 * A creative world's armor stand with every look commands give (a name shown above it, silent, invulnerable, on fire
	 * and showing fire, locked slots, tags, an invisibility effect, twice the health, a bigger scale, glowing, invisible,
	 * floating, thrown upwards) and the looks the spec keeps (facing, a head and arm pose, arms, small, no base plate), a
	 * marker stand, and a fixed, silent, named, invisible, glowing glow item frame. The builder puts up one stand with only
	 * the kept looks, no marker stand, and the glow frame facing the same way with none of the commands' looks.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaUploadFixesVerify1109Stand"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploadFixesVerify1109Stand")
	public void aCreativeWorldsStandAndGlowFrameArePutUpWithOnlyTheLooksASurvivalPlayerCouldGive(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(22, 2, 22);
		helper.setBlock(src, Blocks.OAK_PLANKS);
		helper.setBlock(src.east(), Blocks.STONE);
		BlockPos at = helper.absolutePos(src.above());
		ArmorStand stand = new ArmorStand(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.getAttribute(Attributes.SCALE).setBaseValue(3.0);
		stand.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		level.addFreshEntity(stand);
		GlowItemFrame frame = new GlowItemFrame(level, helper.absolutePos(src.east().south()), Direction.SOUTH);
		level.addFreshEntity(frame);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(2, 2, 2), true, Blocks.STRUCTURE_VOID);
		CompoundTag saved = template.save(new CompoundTag());
		stand.discard();
		frame.discard();
		helper.setBlock(src, Blocks.AIR);
		helper.setBlock(src.east(), Blocks.AIR);
		ListTag entities = saved.getList("entities", Tag.TAG_COMPOUND);
		helper.assertTrue(entities.size() == 2, "(setup) the file holds " + entities.size() + " entities");
		CompoundTag marker = null;
		for (int i = 0; i < entities.size(); i++) {
			CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
			nbt.putString("CustomName", "\"Bob\"");
			nbt.putBoolean("CustomNameVisible", true);
			nbt.putBoolean("Silent", true);
			nbt.putBoolean("Invulnerable", true);
			nbt.putBoolean("Glowing", true);
			nbt.putBoolean("Invisible", true);
			ListTag tags = new ListTag();
			tags.add(StringTag.valueOf("qa_tag"));
			nbt.put("Tags", tags);
			if ("minecraft:armor_stand".equals(nbt.getString("id"))) {
				nbt.putBoolean("NoGravity", true);
				nbt.putShort("Fire", (short) 2000);
				nbt.putBoolean("HasVisualFire", true);
				nbt.putInt("DisabledSlots", 4144959);
				ListTag motion = new ListTag();
				motion.add(DoubleTag.valueOf(0));
				motion.add(DoubleTag.valueOf(3));
				motion.add(DoubleTag.valueOf(0));
				nbt.put("Motion", motion);
				CompoundTag effect = new CompoundTag();
				effect.putString("id", "minecraft:invisibility");
				effect.putInt("duration", 1000000);
				effect.putByte("amplifier", (byte) 0);
				ListTag effects = new ListTag();
				effects.add(effect);
				nbt.put("active_effects", effects);
				ListTag rotation = new ListTag();
				rotation.add(FloatTag.valueOf(90f));
				rotation.add(FloatTag.valueOf(0f));
				nbt.put("Rotation", rotation);
				CompoundTag pose = new CompoundTag();
				ListTag head = new ListTag();
				head.add(FloatTag.valueOf(30f));
				head.add(FloatTag.valueOf(0f));
				head.add(FloatTag.valueOf(0f));
				pose.put("Head", head);
				ListTag arm = new ListTag();
				arm.add(FloatTag.valueOf(-60f));
				arm.add(FloatTag.valueOf(0f));
				arm.add(FloatTag.valueOf(10f));
				pose.put("RightArm", arm);
				nbt.put("Pose", pose);
				nbt.putBoolean("ShowArms", true);
				nbt.putBoolean("Small", true);
				nbt.putBoolean("NoBasePlate", true);
				// control: the file's stand, loaded as it is, has these looks
				ArmorStand control = EntityType.ARMOR_STAND.create(level);
				control.load(nbt);
				helper.assertTrue(control.getScale() > 2.5f && control.isCustomNameVisible() && control.isSilent() && control.isSmall()
					&& control.hasEffect(MobEffects.INVISIBILITY) && control.getTags().contains("qa_tag") && control.getMaxHealth() > 50,
					"(setup) the file's stand doesn't have the looks: scale " + control.getScale());
				control.discard();
				CompoundTag markerEntry = entities.getCompound(i).copy();
				markerEntry.getCompound("nbt").putBoolean("Marker", true);
				marker = markerEntry;
			} else {
				nbt.putBoolean("Fixed", true);
				nbt.putByte("ItemRotation", (byte) 3);
			}
		}
		entities.add(marker);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(saved, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_verifier",
				"qa1109v_stand_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import: " + e);
		}
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		AABB around = new AABB(helper.absolutePos(ORIGIN)).inflate(6);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status()
				+ " missing " + site.missing());
			List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, around, Entity::isAlive);
			List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, around, Entity::isAlive);
			helper.assertTrue(stands.size() == 1 && frames.size() == 1, "the builder put up " + stands.size() + " armor stands (one is a"
				+ " marker) and " + frames.size() + " item frames, not 1 and 1");
			List<String> wrong = new ArrayList<>();
			ArmorStand built = stands.get(0);
			CompoundTag nbt = built.saveWithoutId(new CompoundTag());
			// kept: what the spec keeps
			if (!built.isSmall() || !built.isShowArms() || !built.isNoBasePlate()) {
				wrong.add("the stand lost a kept look (small " + built.isSmall() + ", arms " + built.isShowArms() + ", base plate "
					+ !built.isNoBasePlate() + ")");
			}
			if (Math.abs(built.getHeadPose().getX() - 30f) > 0.5f || Math.abs(built.getRightArmPose().getX() + 60f) > 0.5f) {
				wrong.add("the stand lost its pose (head " + built.getHeadPose().getX() + ", right arm " + built.getRightArmPose().getX() + ")");
			}
			float yaw = ((built.getYRot() % 360f) + 360f) % 360f;
			if (Math.abs(yaw - 90f) > 1f) {
				wrong.add("the stand faces " + yaw + ", not 90 as in the file");
			}
			// dropped: what commands give
			if (built.getScale() != 1.0f) {
				wrong.add("scale " + built.getScale());
			}
			if (built.getMaxHealth() > 20.5f) {
				wrong.add("max health " + built.getMaxHealth());
			}
			if (built.getCustomName() != null || built.isCustomNameVisible()) {
				wrong.add("the stand has a name " + built.getCustomName() + " (shown " + built.isCustomNameVisible() + ")");
			}
			if (built.isSilent() || built.isInvulnerable() || built.hasGlowingTag() || built.isInvisible() || built.isNoGravity()) {
				wrong.add("the stand is silent " + built.isSilent() + ", invulnerable " + built.isInvulnerable() + ", glowing "
					+ built.hasGlowingTag() + ", invisible " + built.isInvisible() + ", floating " + built.isNoGravity());
			}
			if (built.getRemainingFireTicks() > 0 || nbt.getBoolean("HasVisualFire")) {
				wrong.add("the stand burns (fire " + built.getRemainingFireTicks() + ", shows fire " + nbt.getBoolean("HasVisualFire") + ")");
			}
			if (nbt.getInt("DisabledSlots") != 0) {
				wrong.add("the stand's slots are locked (" + nbt.getInt("DisabledSlots") + ")");
			}
			if (built.getTags().contains("qa_tag")) {
				wrong.add("the stand keeps the file's tags");
			}
			if (!built.getActiveEffects().isEmpty()) {
				wrong.add("the stand has effects " + built.getActiveEffects());
			}
			ItemFrame glow = frames.get(0);
			if (glow.getType() != EntityType.GLOW_ITEM_FRAME || glow.getDirection() != Direction.SOUTH) {
				wrong.add("the frame is " + glow.getType() + " facing " + glow.getDirection() + ", not a glow item frame facing south");
			}
			CompoundTag frameNbt = glow.saveWithoutId(new CompoundTag());
			if (glow.isInvisible() || glow.hasGlowingTag() || frameNbt.getBoolean("Fixed") || glow.isSilent() || glow.isInvulnerable()
				|| glow.getCustomName() != null || glow.getTags().contains("qa_tag")) {
				wrong.add("the frame is invisible " + glow.isInvisible() + ", glowing " + glow.hasGlowingTag() + ", fixed "
					+ frameNbt.getBoolean("Fixed") + ", silent " + glow.isSilent() + ", invulnerable " + glow.isInvulnerable() + ", named "
					+ glow.getCustomName() + ", tagged " + glow.getTags());
			}
			helper.assertTrue(wrong.isEmpty(), "a creative world's stand and glow frame from an uploaded file, put up by a builder: "
				+ String.join("; ", wrong));
		});
	}

	// ---------------------------------------------------------------- B1098: the Storehouse board's sentences

	private static final Pattern COUNT_THEN_ARTICLE = Pattern.compile("\\b\\d+ (a|an|A|An) ");
	private static final Pattern NO_ARTICLE = Pattern.compile("\\bno (a|an|A|An) ");
	private static final Pattern CAPITAL_MID = Pattern.compile(".\\b(A|An|Seeds)\\b");
	private static final Pattern ONE_PLURAL = Pattern.compile("\\b1 seeds\\b");

	/** What reads wrong in {@code sentence}, or an empty list. */
	static List<String> problems(String sentence) {
		List<String> out = new ArrayList<>();
		if (COUNT_THEN_ARTICLE.matcher(sentence).find()) {
			out.add("a count before 'a'");
		}
		if (NO_ARTICLE.matcher(sentence).find()) {
			out.add("'no a'");
		}
		if (CAPITAL_MID.matcher(sentence).find()) {
			out.add("a capital mid-sentence");
		}
		if (ONE_PLURAL.matcher(sentence).find()) {
			out.add("'1 seeds'");
		}
		return out;
	}

	static Villager worker(GameTestHelper helper, VillagerProfession profession, int x) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 1));
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		return villager;
	}

	static Requests.Request request(GameTestHelper helper, Villager worker, ItemStack icon, int count, Component what) {
		return new Requests.Request(worker, worker.blockPosition(), icon, count, what, stack -> stack.is(icon.getItem()));
	}

	/**
	 * Every stand-alone request name the board shows (a Miner's pickaxe, a Lumberjack's axe, a Fisher's rod, a Farmer's
	 * seeds), handed over one at a time and many at a time, and refused for want of one: each message reads as a sentence
	 * (no "Gave 1 a pickaxe", "Gave 1 seeds", "no a pickaxe" or capital "A" mid-line) and names the worker; an item's name
	 * reads as before.
	 */
	//$ gametest_batch AREA '"qaUploadFixesVerify1109Board"'
	@GameTest(template = AREA, batch = "qaUploadFixesVerify1109Board")
	public void theStorehouseBoardsMessagesReadAsSentencesForEveryToolAndSeedRequest(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<String> wrong = new ArrayList<>();
		Object[][] kinds = {
			// as the jobs post them: a tool is asked for one at a time (Requests.postTool), seeds 16 at a time (FieldWork)
			{ModVillagers.MINER, Items.IRON_PICKAXE, "pickaxe", "a pickaxe", 1},
			{ModVillagers.LUMBERJACK, Items.IRON_AXE, "axe", "an axe", 1},
			{VillagerProfession.FISHERMAN, Items.FISHING_ROD, "fishing_rod", "a fishing rod", 1},
			{VillagerProfession.FARMER, Items.WHEAT_SEEDS, "seeds", "seeds", 16}};
		int x = 1;
		for (Object[] kind : kinds) {
			Villager v = worker(helper, (VillagerProfession) kind[0], x++);
			String job = Component.translatable("entity.minecraft.villager." + v.getVillagerData().getProfession().name()).getString();
			int count = (Integer) kind[4];
			Requests.Request r = request(helper, v, new ItemStack((net.minecraft.world.level.ItemLike) kind[1]), count,
				Component.translatable("request.aliveworkplace." + kind[2]));
			// a click hands over at most what was asked for: one, and (for seeds) some or all of the 16
			List<String> sentences = new ArrayList<>(List.of(StorehouseBoard.gaveMessage(r, 1).getString()));
			if (count > 1) {
				sentences.add(StorehouseBoard.gaveMessage(r, 7).getString());
				sentences.add(StorehouseBoard.gaveMessage(r, count).getString());
			}
			for (String s : sentences) {
				if (!s.contains((String) kind[3]) || !s.contains("the " + job + "'s chests")) {
					wrong.add("'" + s + "' doesn't name " + kind[3] + " and the " + job + "'s chests");
				}
			}
			String none = StorehouseBoard.noneMessage(r).getString();
			sentences.add(none);
			if (!none.contains("have " + kind[3] + " to give")) {
				wrong.add("'" + none + "' doesn't say you don't have " + kind[3]);
			}
			for (String s : sentences) {
				List<String> p = problems(s);
				if (!p.isEmpty()) {
					wrong.add("'" + s + "': " + String.join(", ", p));
				}
			}
			if (!sentences.get(0).equals("Gave " + kind[3] + " to the " + job + "'s chests")) {
				wrong.add("one handed over reads '" + sentences.get(0) + "'");
			}
		}
		Villager sifter = worker(helper, ModVillagers.SIFTER, x);
		Requests.Request gravel = request(helper, sifter, new ItemStack(Items.GRAVEL), 16, Items.GRAVEL.getDescription());
		String gave = StorehouseBoard.gaveMessage(gravel, 16).getString();
		if (!gave.startsWith("Gave 16 Gravel to the ") || !problems(gave).isEmpty()) {
			wrong.add("an item's name reads '" + gave + "'");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
