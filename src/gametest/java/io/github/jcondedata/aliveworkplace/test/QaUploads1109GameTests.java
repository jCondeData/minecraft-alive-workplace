package io.github.jcondedata.aliveworkplace.test;

import com.mojang.authlib.properties.Property;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * QA (lane b, 2026-11-09), from the specs of B1073, B1074 and B1077: a block a builder builds from a blueprint file a
 * player uploaded carries no more than a player's own block could, and no less.
 * <ul>
 * <li>B1073: a banner keeps at most a loom's 6 pattern layers: 6 stay whole and in order, 7 lose only the last.</li>
 * <li>B1074: a sign line shows at most the 384 letters a player could write, counted as the game shows them: a line at
 * the limit (plain, colored, or through a repeated translation argument) stays whole and one past it is cut to 384. (A
 * line through the language's own keys is QaSignLanguageKeys1109BugTests, on tests/uploads-1109.)</li>
 * <li>B1077: a head keeps what a player's head carries in its profile (a name of up to 16 letters, an id, one signed
 * 'textures' skin of real size) and drops the rest.</li>
 * </ul>
 * Each goes through a player's entry point: the upload import a Table upload uses, then a builder building it. Built
 * blocks are also saved and loaded again (a chunk save) to check what was kept survives.
 */
public class QaUploads1109GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(26, 2, 26);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final int MAX_LINE = 384;

	private static final String[] PATTERNS = {"minecraft:stripe_top", "minecraft:stripe_bottom", "minecraft:cross",
		"minecraft:border", "minecraft:stripe_left", "minecraft:stripe_right", "minecraft:stripe_center"};
	private static final String[] COLORS = {"red", "blue", "yellow", "lime", "black", "white", "orange"};

	// ---- B1073: banners ----

	//$ gametest_ticks_batch HUGE '2400' '"qaUploads1109Banners"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploads1109Banners")
	public void anUploadedBannerKeepsALoomsSixLayersInOrderAndDropsOnlyTheSeventh(GameTestHelper helper) {
		List<Consumer<CompoundTag>> banners = List.of(
			nbt -> nbt.put("patterns", layers(6)),
			nbt -> nbt.put("patterns", layers(7)),
			nbt -> nbt.put("patterns", new ListTag()),
			nbt -> nbt.putString("patterns", "not a list")); // a broken file must not stop the build
		build(helper, Blocks.WHITE_BANNER, "qa_bannerman", banners, built -> {
			for (int i = 0; i < built.size(); i++) {
				helper.assertTrue(built.get(i) instanceof BannerBlockEntity, "(setup) banner " + i + " wasn't built: " + built.get(i));
			}
			List<String> six = layersOf((BannerBlockEntity) built.get(0));
			helper.assertTrue(six.equals(expected(6)), "a banner uploaded with 6 layers (what a loom gives) should keep all 6 in order, kept " + six);
			List<String> seven = layersOf((BannerBlockEntity) built.get(1));
			helper.assertTrue(seven.equals(expected(6)), "a banner uploaded with 7 layers should keep its first 6 in order (the 7th dropped), kept " + seven);
			helper.assertTrue(layersOf((BannerBlockEntity) built.get(2)).isEmpty(), "a plain banner should stay plain");
			helper.assertTrue(layersOf((BannerBlockEntity) built.get(3)).isEmpty(), "a banner whose file layers aren't a list should be built plain");

			// saved and loaded again (a chunk save): the kept layers stay
			for (int i = 0; i < 2; i++) {
				BlockEntity again = reload(helper, built.get(i));
				helper.assertTrue(again instanceof BannerBlockEntity b && layersOf(b).equals(expected(6)),
					"banner " + i + " should keep its 6 layers after a save and load, kept " + (again instanceof BannerBlockEntity b2 ? layersOf(b2) : again));
			}
		});
	}

	// ---- B1074: sign lines ----

	//$ gametest_ticks_batch HUGE '2400' '"qaUploads1109SignLimits"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploads1109SignLimits")
	public void anUploadedSignLineAtTheLimitStaysWholeAndOnePastItIsCut(GameTestHelper helper) {
		String w384 = "W".repeat(MAX_LINE);
		List<Consumer<CompoundTag>> signs = List.of(
			// a colored line of exactly 384 letters, as a player's sign could hold
			nbt -> line(nbt, "{\"text\":\"" + w384 + "\",\"color\":\"red\"}"),
			// 192 letters shown twice by one translation: 384 as the game shows them
			nbt -> line(nbt, "{\"translate\":\"%1$s%1$s\",\"with\":[\"" + "W".repeat(192) + "\"]}"),
			// 193 letters shown twice: 386, two past what a player could write
			nbt -> line(nbt, "{\"translate\":\"%1$s%1$s\",\"with\":[\"" + "W".repeat(193) + "\"]}"),
			// %% shows one % (so 383 W's and a % is 384) and stays whole
			nbt -> line(nbt, "{\"translate\":\"%%%1$s\",\"with\":[\"" + "W".repeat(383) + "\"]}"));
		build(helper, Blocks.OAK_SIGN, "qa_signer", signs, built -> {
			List<Component> lines = new ArrayList<>();
			for (int i = 0; i < built.size(); i++) {
				helper.assertTrue(built.get(i) instanceof SignBlockEntity, "(setup) sign " + i + " wasn't built: " + built.get(i));
				lines.add(((SignBlockEntity) built.get(i)).getFrontText().getMessage(0, false));
			}
			helper.assertTrue(lines.get(0).getString().equals(w384), "a 384-letter line should stay whole, shows "
				+ lines.get(0).getString().length() + " letters");
			helper.assertTrue(lines.get(0).getStyle().getColor() != null && "red".equals(lines.get(0).getStyle().getColor().serialize()),
				"a 384-letter red line should stay red: " + lines.get(0).getStyle());
			helper.assertTrue(lines.get(1).getString().equals(w384), "a translation showing 192 letters twice (384) should stay whole, shows "
				+ lines.get(1).getString().length() + " letters");
			String cut = lines.get(2).getString();
			helper.assertTrue(cut.equals(w384), "a translation showing 193 letters twice (386) should be cut to the first 384 letters, shows "
				+ cut.length());
			String percent = lines.get(3).getString();
			helper.assertTrue(percent.equals("%" + "W".repeat(383)), "a '%%' and 383 letters (384) should stay whole, shows "
				+ percent.length() + " letters starting '" + percent.substring(0, Math.min(3, percent.length())) + "'");

			// saved and loaded again: the lines stay as built
			for (int i = 0; i < built.size(); i++) {
				BlockEntity again = reload(helper, built.get(i));
				String after = again instanceof SignBlockEntity s ? s.getFrontText().getMessage(0, false).getString() : "(not a sign)";
				helper.assertTrue(after.equals(lines.get(i).getString()), "sign " + i + "'s line changed after a save and load: "
					+ after.length() + " letters, was " + lines.get(i).getString().length());
			}
		});
	}

	// ---- B1077: heads ----

	//$ gametest_ticks_batch HUGE '2400' '"qaUploads1109Heads"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaUploads1109Heads")
	public void anUploadedHeadKeepsWhatAPlayersHeadCarriesAndNoMore(GameTestHelper helper) {
		String skin = "ewogICJ0aW1lc3RhbXAiIDog".repeat(30); // 720 characters of base64, a real skin's size
		String signature = "s".repeat(684);
		UUID id = new UUID(0x1234_5678_9abc_def0L, 0x0fed_cba9_8765_4321L);
		String sixteen = "Abcdefghijklmnop";
		List<Consumer<CompoundTag>> heads = List.of(
			nbt -> nbt.put("profile", profile("Steve", id, property("textures", skin, signature))), // a real player's head
			nbt -> nbt.put("profile", profile(sixteen, id)), // the longest name a player can have
			nbt -> nbt.put("profile", profile(sixteen + "q", id)), // one letter longer
			nbt -> nbt.put("profile", profile("Alex", id, property("textures", skin, signature), property("textures", skin, signature))),
			nbt -> nbt.put("profile", profile("Alex", id, property("cape", skin, signature)))); // not a skin
		build(helper, Blocks.PLAYER_HEAD, "qa_header", heads, built -> {
			List<ResolvableProfile> profiles = new ArrayList<>();
			for (int i = 0; i < built.size(); i++) {
				helper.assertTrue(built.get(i) instanceof SkullBlockEntity, "(setup) head " + i + " wasn't built: " + built.get(i));
				profiles.add(((SkullBlockEntity) built.get(i)).getOwnerProfile());
			}
			ResolvableProfile real = profiles.get(0);
			helper.assertTrue(real != null && real.name().orElse("").equals("Steve") && real.id().orElse(null) != null
				&& real.id().get().equals(id), "a real player's head should keep its name and id: " + describe(real));
			List<Property> textures = new ArrayList<>(real.properties().get("textures"));
			helper.assertTrue(real.properties().size() == 1 && textures.size() == 1 && textures.get(0).value().equals(skin)
				&& signature.equals(textures.get(0).signature()), "a real player's head should keep its one signed skin: " + describe(real));

			helper.assertTrue(profiles.get(1) != null && profiles.get(1).name().orElse("").equals(sixteen),
				"a head named with 16 letters (a player's longest name) should keep it: " + describe(profiles.get(1)));
			helper.assertTrue(profiles.get(2) == null || !profiles.get(2).name().orElse("").equals(sixteen + "q"),
				"a head named with 17 letters (longer than any player's) shouldn't keep it: " + describe(profiles.get(2)));

			for (int i = 3; i < 5; i++) {
				ResolvableProfile p = profiles.get(i);
				helper.assertTrue(p != null && p.properties().isEmpty(), "head " + i + " (two skins, or a property that"
					+ " isn't a skin) should keep no properties: " + describe(p));
				helper.assertTrue(p.name().orElse("").equals("Alex"), "head " + i + " should keep its name: " + describe(p));
			}

			// saved and loaded again (a chunk save): the real head keeps its skin
			BlockEntity again = reload(helper, built.get(0));
			ResolvableProfile reloaded = again instanceof SkullBlockEntity s ? s.getOwnerProfile() : null;
			helper.assertTrue(reloaded != null && reloaded.properties().get("textures").stream().anyMatch(p -> p.value().equals(skin)),
				"a real player's head should keep its skin after a save and load: " + describe(reloaded));
		});
	}

	// ---- helpers ----

	/**
	 * Scans one {@code block} the game placed (for its real saved form), writes a file of one such block per edit along
	 * x, each with its saved data changed by that edit, imports it as a player's upload, lets a builder build it with free
	 * materials, then runs {@code check} on the built block entities in order.
	 */
	private static void build(GameTestHelper helper, Block block, String player, List<Consumer<CompoundTag>> edits,
		Consumer<List<BlockEntity>> check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, block);
		if (helper.getBlockEntity(SRC) instanceof SignBlockEntity sign) {
			sign.setWaxed(true);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag one = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0);
		ListTag blocks = new ListTag();
		for (int x = 0; x < edits.size(); x++) {
			CompoundTag entry = one.copy();
			CompoundTag nbt = entry.getCompound("nbt");
			edits.get(x).accept(nbt);
			entry.put("nbt", nbt);
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(x));
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			entry.put("pos", pos);
			blocks.add(entry);
		}
		file.put("blocks", blocks);
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(edits.size()));
		size.add(IntTag.valueOf(1));
		size.add(IntTag.valueOf(1));
		file.put("size", size);
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
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/" + player,
				"qa1109_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
		BlockPos origin = helper.absolutePos(ORIGIN);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			if (!checked[0]) {
				List<BlockEntity> built = new ArrayList<>();
				for (int x = 0; x < edits.size(); x++) {
					built.add(level.getBlockEntity(origin.offset(x, 0, 0)));
				}
				check.accept(built);
				checked[0] = true;
			}
		});
	}

	/** {@code blockEntity} saved and loaded again, as a chunk save does. */
	private static BlockEntity reload(GameTestHelper helper, BlockEntity blockEntity) {
		CompoundTag saved = blockEntity.saveWithFullMetadata(helper.getLevel().registryAccess());
		return BlockEntity.loadStatic(blockEntity.getBlockPos(), blockEntity.getBlockState(), saved, helper.getLevel().registryAccess());
	}

	private static void line(CompoundTag nbt, String json) {
		CompoundTag text = nbt.getCompound("front_text");
		ListTag messages = text.getList("messages", Tag.TAG_STRING);
		messages.set(0, StringTag.valueOf(json));
		text.put("messages", messages);
		nbt.put("front_text", text);
	}

	private static ListTag layers(int count) {
		ListTag patterns = new ListTag();
		for (int i = 0; i < count; i++) {
			CompoundTag layer = new CompoundTag();
			layer.putString("pattern", PATTERNS[i]);
			layer.putString("color", COLORS[i]);
			patterns.add(layer);
		}
		return patterns;
	}

	private static List<String> expected(int count) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			out.add(PATTERNS[i] + " " + COLORS[i]);
		}
		return out;
	}

	private static List<String> layersOf(BannerBlockEntity banner) {
		List<String> out = new ArrayList<>();
		for (BannerPatternLayers.Layer layer : banner.getPatterns().layers()) {
			out.add(layer.pattern().unwrapKey().map(k -> k.location().toString()).orElse("?") + " " + layer.color().getSerializedName());
		}
		return out;
	}

	private static CompoundTag profile(String name, UUID id, CompoundTag... properties) {
		CompoundTag profile = new CompoundTag();
		profile.putString("name", name);
		profile.put("id", new IntArrayTag(new int[] {(int) (id.getMostSignificantBits() >> 32), (int) id.getMostSignificantBits(),
			(int) (id.getLeastSignificantBits() >> 32), (int) id.getLeastSignificantBits()}));
		if (properties.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag property : properties) {
				list.add(property);
			}
			profile.put("properties", list);
		}
		return profile;
	}

	private static CompoundTag property(String name, String value, String signature) {
		CompoundTag property = new CompoundTag();
		property.putString("name", name);
		property.putString("value", value);
		property.putString("signature", signature);
		return property;
	}

	private static String describe(ResolvableProfile profile) {
		if (profile == null) {
			return "(no profile)";
		}
		StringBuilder props = new StringBuilder();
		for (Property p : profile.properties().values()) {
			props.append(p.name()).append('(').append(p.value().length()).append(p.signature() != null ? ", signed" : "").append(") ");
		}
		return "name " + profile.name().orElse("-") + ", id " + profile.id().map(UUID::toString).orElse("-") + ", properties [" + props.toString().trim() + "]";
	}
}
