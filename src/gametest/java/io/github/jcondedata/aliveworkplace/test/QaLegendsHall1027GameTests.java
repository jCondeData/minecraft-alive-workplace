package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendLook;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA for 29.4 (Legends on the hall, and how they look), 2026-10-27, on what the other Legends hall tests leave open:
 * the look told to clients follows the Legends switch and survives a save and load, every shipped Legend has the
 * outfit the client draws (and the placeholder exists), and the Legends text a player reads is American English.
 */
public class QaLegendsHall1027GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final String NS = "aliveworkplace_test";

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	private static void restore(GameTestHelper helper) {
		Leftovers.after(helper, () -> {
			Legends.ENABLED = true;
			LegendRecord record = LegendRecord.get(helper.getLevel());
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	/**
	 * "Told to the client in a small LegendLook packet": the look is the Legend's outfit while Legends are on, nothing
	 * while they are switched off, back when switched on, and the same outfit for the villager loaded from a save.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHall1027Look"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHall1027Look")
	public void theLookFollowsTheSwitchAndASave(GameTestHelper helper) {
		Leftovers.clear(helper);
		restore(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legend poet = Legends.read(id("qa_look_poet"), JsonParser.parseString(
				"{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"Poet\", \"lore\": \"l\"}").getAsJsonObject());
			Map<ResourceLocation, Legend> roster = new LinkedHashMap<>();
			roster.put(poet.id(), poet);
			Legends.setForTest(roster);
			Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 16));
			ada.setNoAi(true);
			ada.setVillagerData(ada.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
			ada.setCustomName(Component.literal("Ada"));
			Legends.make(level, ada, poet, "test");
			ResourceLocation outfit = ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/villager/legend/qa_look_poet.png");

			LegendLook.Look on = LegendLook.look(ada);
			helper.assertTrue(on != null && on.legend() && on.entityId() == ada.getId() && outfit.equals(on.outfit()), "look while on: " + on);

			Legends.ENABLED = false;
			LegendLook.Look off = LegendLook.look(ada);
			Legends.ENABLED = true;
			helper.assertTrue(off == null, "a look while Legends are off: " + off);
			helper.assertTrue(outfit.equals(LegendLook.look(ada) == null ? null : LegendLook.look(ada).outfit()), "no look once switched back on");

			CompoundTag saved = new CompoundTag();
			ada.saveWithoutId(saved);
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(saved);
			LegendLook.Look again = LegendLook.look(loaded);
			helper.assertTrue(again != null && again.legend() && again.entityId() == loaded.getId() && outfit.equals(again.outfit()),
				"look after a save and load: " + again);
			loaded.discard();
			helper.succeed();
		});
	}

	private static boolean onClasspath(ResourceLocation file) {
		try (InputStream in = QaLegendsHall1027GameTests.class.getResourceAsStream("/assets/" + file.getNamespace() + "/" + file.getPath())) {
			return in != null;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * "Draws textures/entity/villager/legend/<id>.png over the job's outfit": every Legend the mod ships has that file
	 * (or the file its JSON names), and the placeholder the client falls back on is there too.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLegendsHall1027Outfits"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLegendsHall1027Outfits")
	public void everyShippedLegendHasItsOutfit(GameTestHelper helper) {
		Legends.ENABLED = true;
		Legends.reload(helper.getLevel().getServer().getResourceManager());
		List<String> missing = new ArrayList<>();
		int shipped = 0;
		for (Legend legend : Legends.all()) {
			if (!legend.id().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			shipped++;
			ResourceLocation outfit = LegendLook.outfit(legend);
			if (!onClasspath(outfit)) {
				missing.add(legend.id() + " -> " + outfit);
			}
		}
		// the two Pokémon Legends "require" Cobblemon and are skipped without it: their files are checked by name below
		helper.assertTrue(shipped >= 10, "only " + shipped + " shipped Legends read");
		for (var file : helper.getLevel().getServer().getResourceManager().listResources(Legends.FOLDER,
			f -> f.getNamespace().equals("aliveworkplace") && f.getPath().endsWith(".json")).keySet()) {
			String name = file.getPath().substring(Legends.FOLDER.length() + 1, file.getPath().length() - ".json".length());
			ResourceLocation outfit = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "textures/entity/villager/legend/" + name + ".png");
			if (!onClasspath(outfit) && missing.stream().noneMatch(m -> m.endsWith(outfit.toString()))) {
				missing.add(file + " -> " + outfit);
			}
		}
		helper.assertTrue(missing.isEmpty(), "Legends with no outfit texture: " + missing);
		helper.assertTrue(onClasspath(LegendLook.PLACEHOLDER), "no placeholder outfit at " + LegendLook.PLACEHOLDER);
		helper.succeed();
	}

	/** British spellings the owner asked to avoid (2026-10-26), as whole words. */
	private static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour\\w*|armour\\w*|defence\\w*|grey\\w*|cancell\\w*|jeweller\\w*|"
		+ "honour\\w*|favour\\w*|centre\\w*|theatre\\w*|organis\\w*|realis\\w*|recognis\\w*|travell\\w*|labour\\w*|neighbour\\w*|"
		+ "harbour\\w*|splendour\\w*|rumour\\w*|valour\\w*|odour\\w*|metre\\w*|plough\\w*|whilst|amongst|learnt|dreamt|spelt)\\b");

	/**
	 * Every line a player reads about Legends (the hall's cards, needs, strikes, guests, "What next?" and Chatter's
	 * Legend lines) is in en_us.json in American English, and Chatter's six Legend lines are all there.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void legendTextIsAmericanEnglish(GameTestHelper helper) {
		JsonObject lang;
		try (InputStream in = QaLegendsHall1027GameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		List<String> british = new ArrayList<>();
		int checked = 0;
		for (Map.Entry<String, JsonElement> e : lang.entrySet()) {
			String key = e.getKey();
			if (!(key.startsWith("legend.") || key.contains(".legend") || key.contains("legends") || key.startsWith("chatter.aliveworkplace.legend_"))) {
				continue;
			}
			checked++;
			Matcher m = BRITISH.matcher(e.getValue().getAsString());
			while (m.find()) {
				british.add(key + ": \"" + m.group() + "\"");
			}
		}
		helper.assertTrue(checked >= 20, "only " + checked + " Legend lines found");
		helper.assertTrue(british.isEmpty(), "British spellings in Legend text: " + british);
		List<String> absent = new ArrayList<>();
		for (String k : List.of("legend_here.0", "legend_here.1", "legend_guest.0", "legend_guest.1", "legend_strike.0", "legend_self.0")) {
			JsonElement text = lang.get("chatter.aliveworkplace." + k);
			if (text == null || text.getAsString().isBlank()) {
				absent.add(k);
			}
		}
		helper.assertTrue(absent.isEmpty(), "Chatter Legend lines with no English: " + absent);
		helper.succeed();
	}
}
