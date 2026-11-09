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
 * QA (lane b, 2026-11-09), from B1074's promise: a sign line from an uploaded blueprint file shows at most the 384 letters
 * a player could write, counted as the game shows them. BlockText measures a translation by its key as if the key were
 * its text, but the game shows the language's text for a key it knows: "translation.test.complex" (in every client's
 * en_us) uses its first argument three times, so nesting it 10 deep in 563 characters of JSON shows about 1.4 million
 * letters (13 deep, still under 1,024 characters: about 37 million, which freezes every client drawing the sign), and
 * a long key such as "multiplayerWarning.message" three times shows 834 letters from 130 characters. Both go through a
 * player's upload and a builder building it.
 */
public class QaSignLanguageKeys1109BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(26, 2, 26);
	private static final BlockPos ORIGIN = new BlockPos(10, 2, 10);
	private static final int MAX_LINE = 384;

	private static final String[] PATTERNS = {"minecraft:stripe_top", "minecraft:stripe_bottom", "minecraft:cross",
		"minecraft:border", "minecraft:stripe_left", "minecraft:stripe_right", "minecraft:stripe_center"};
	private static final String[] COLORS = {"red", "blue", "yellow", "lime", "black", "white", "orange"};

	//$ gametest_ticks_batch HUGE '2400' '"qaSignLanguageKeys1109Repeat"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignLanguageKeys1109Repeat")
	public void anUploadedLineRepeatedThroughALanguageKeysPlaceholdersShowsNoMoreThanAPlayerCouldWrite(GameTestHelper helper) {
		// The game's own language has a key whose text uses its first argument three times ("translation.test.complex":
		// "Prefix, %s%2$s again %s and %1$s lastly %s and also %1$s again!"), so each level of nesting triples the words
		String json = "\"W\"";
		for (int i = 0; i < 10; i++) {
			json = "{\"translate\":\"translation.test.complex\",\"with\":[" + json + ",\"\",\"\"]}";
		}
		String line = json;
		helper.assertTrue(Component.translatable("translation.test.complex", "a", "b", "c").getString().contains("again"),
			"(setup) the language has no translation.test.complex");
		build(helper, Blocks.OAK_SIGN, "qa_signer", List.of(nbt -> line(nbt, line)), built -> {
			helper.assertTrue(built.get(0) instanceof SignBlockEntity, "(setup) no sign built: " + built.get(0));
			String words = ((SignBlockEntity) built.get(0)).getFrontText().getMessage(0, false).getString();
			helper.assertTrue(words.length() <= MAX_LINE, "the first line of a sign a builder built from an uploaded file shows "
				+ words.length() + " letters from " + line.length() + " characters of saved JSON (one W repeated through the"
				+ " language key translation.test.complex nested 10 deep); a player's line shows at most " + MAX_LINE
				+ ", and each level deeper triples it");
		});
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaSignLanguageKeys1109Text"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaSignLanguageKeys1109Text")
	public void anUploadedLineOfLongLanguageTextsShowsNoMoreThanAPlayerCouldWrite(GameTestHelper helper) {
		// Each key is short, but the game shows its whole English text
		String key = "multiplayerWarning.message";
		int one = Component.translatable(key).getString().length();
		helper.assertTrue(one > 200, "(setup) " + key + " shows only " + one + " letters");
		String line = "[{\"translate\":\"" + key + "\"},{\"translate\":\"" + key + "\"},{\"translate\":\"" + key + "\"}]";
		build(helper, Blocks.OAK_SIGN, "qa_signer", List.of(nbt -> line(nbt, line)), built -> {
			helper.assertTrue(built.get(0) instanceof SignBlockEntity, "(setup) no sign built: " + built.get(0));
			String words = ((SignBlockEntity) built.get(0)).getFrontText().getMessage(0, false).getString();
			helper.assertTrue(words.length() <= MAX_LINE, "the first line of a sign a builder built from an uploaded file shows "
				+ words.length() + " letters from " + line.length() + " characters of saved JSON (the language key " + key
				+ " three times); a player's line shows at most " + MAX_LINE);
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
