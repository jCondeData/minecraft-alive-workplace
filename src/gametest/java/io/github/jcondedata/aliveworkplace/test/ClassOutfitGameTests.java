package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.ClassOutfits;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * Dressed by class (ROADMAP 34.17): every class but the Peasant has its own layer over the job's outfit, and the
 * texture it names ships in the mod at {@code textures/entity/villager/class/<class>.png} (64x64, like every villager
 * layer), as does the elder's face. What a villager wears follows their class and their age: a Peasant nothing, a
 * Noble the Noble's layer, an elder the elder's face on top of their class's, a child their class's but never the
 * elder's, and with {@code villagerAges} off no elder's face at all. The textures are the owner's picks (34.17a).
 */
public class ClassOutfitGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The classes' files name their layers, and every layer named ships as a 64x64 texture. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyClassButThePeasantHasItsLayer(GameTestHelper helper) {
		helper.assertTrue(ClassOutfits.outfit(cls("peasant")) == null, "a Peasant wears a class layer: " + ClassOutfits.outfit(cls("peasant")));
		helper.assertTrue(ClassOutfits.outfit(null) == null, "no class, a class layer");
		for (String name : List.of("artisan", "burgher", "noble")) {
			ResourceLocation outfit = ClassOutfits.outfit(cls(name));
			helper.assertTrue(AliveWorkplace.id("textures/entity/villager/class/" + name + ".png").equals(outfit), name + "'s layer: " + outfit);
			ships(helper, outfit);
		}
		ships(helper, ClassOutfits.ELDER);
		helper.assertTrue(ClassOutfits.ELDER.equals(AliveWorkplace.id("textures/entity/villager/class/elder.png")), "the elder's face: " + ClassOutfits.ELDER);
		helper.succeed();
	}

	/** Villagers of each class, grown, elder and child: what each wears over the job, bottom to top. */
	//$ gametest_ticks_batch AREA '40' '"classOutfits"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "classOutfits")
	public void whatAVillagerWearsFollowsClassAndAge(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager peasant = villager(helper, 1, "peasant");
		Villager noble = villager(helper, 2, "noble");
		Villager oldPeasant = villager(helper, 3, "peasant");
		Villager oldBurgher = villager(helper, 4, "burgher");
		Villager child = villager(helper, 5, "artisan");
		child.setAge(-24000);
		long today = Chronicle.day(helper.getLevel());
		LifeStages.setAdultSince(peasant, today - 10);
		LifeStages.setAdultSince(noble, today - 10);
		LifeStages.setAdultSince(oldPeasant, today - LifeStages.ELDER_DAYS);
		LifeStages.setAdultSince(oldBurgher, today - LifeStages.ELDER_DAYS - 30);
		ResourceLocation nobleLayer = AliveWorkplace.id("textures/entity/villager/class/noble.png");
		ResourceLocation burgherLayer = AliveWorkplace.id("textures/entity/villager/class/burgher.png");
		ResourceLocation artisanLayer = AliveWorkplace.id("textures/entity/villager/class/artisan.png");

		wears(helper, peasant, today, List.of(), "a grown Peasant");
		wears(helper, noble, today, List.of(nobleLayer), "a grown Noble");
		wears(helper, oldPeasant, today, List.of(ClassOutfits.ELDER), "an elder Peasant");
		wears(helper, oldBurgher, today, List.of(burgherLayer, ClassOutfits.ELDER), "an elder Burgher");
		wears(helper, child, today, List.of(artisanLayer), "an Artisan's child");
		// A villager no class has been given yet (an older save before seeding) wears the job's outfit alone.
		Villager unseeded = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		unseeded.setNoAi(true);
		wears(helper, unseeded, today, List.of(), "a villager with no class yet");

		boolean ages = LifeStages.AGES;
		try {
			LifeStages.AGES = false;
			wears(helper, oldBurgher, today, List.of(burgherLayer), "an old Burgher with villagerAges off");
		} finally {
			LifeStages.AGES = ages;
		}
		helper.succeed();
	}

	/**
	 * The owner's picks (34.17a): Artisan version 1 (a blue neckerchief, a belt pouch with a brass stud), Burgher
	 * version 2 (a dark frock coat's buttoned edge, a pocket square with a red peak), Noble version 1 (an ermine collar,
	 * a chain of office with its medallion), Elder version 1 (grey brows and a short beard, no spectacles). Each is
	 * checked by a few pixels that only the picked version paints; the Burgher's old version 1 (a linen collar, no
	 * buttons) fails here.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theOwnersPicksAreTheLayersShipped(GameTestHelper helper) {
		// Texture pixels (u, v) on the 64x64 villager sheet: the jacket's front face starts at (6, 44), its west side at
		// (0, 44), its back at (20, 44); the head's front at (8, 8).
		BufferedImage artisan = image(helper, "artisan");
		pixel(helper, artisan, 6, 44, 0x3f739d, "the Artisan's blue neckerchief");
		pixel(helper, artisan, 3, 55, 0xe0b03a, "the brass stud on the Artisan's belt pouch");
		BufferedImage burgher = image(helper, "burgher");
		pixel(helper, burgher, 12, 44, 0xc0392b, "the red peak of the Burgher's pocket square");
		pixel(helper, burgher, 7, 53, 0xe0b03a, "the top brass button down the Burgher's coat edge");
		pixel(helper, burgher, 6, 44, 0x453f55, "the Burgher's dark lapel (not version 1's white collar)");
		pixel(helper, burgher, 23, 60, 0x221d2a, "the vent between the Burgher's coat tails");
		BufferedImage noble = image(helper, "noble");
		pixel(helper, noble, 6, 44, 0xf7f5ef, "the Noble's ermine collar");
		pixel(helper, noble, 9, 46, 0xe0b03a, "the medallion of the Noble's chain of office");
		BufferedImage elder = image(helper, "elder");
		pixel(helper, elder, 9, 13, 0xd8d6d0, "the elder's grey brow");
		helper.assertTrue((elder.getRGB(8, 14) >>> 24) == 0, "the elder wears version 3's spectacles");
		for (int v = 20; v < 64; v++) {
			for (int u = 0; u < 64; u++) {
				helper.assertTrue((elder.getRGB(u, v) >>> 24) == 0, "the elder's face layer paints the body at " + u + "," + v);
			}
		}
		helper.succeed();
	}

	/** A Burgher elder saved and loaded again wears the same layers: the class and the age are saved, the look follows. */
	//$ gametest_ticks_batch AREA '40' '"classOutfits"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "classOutfits")
	public void theLayersSurviveASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager burgher = villager(helper, 2, "burgher");
		long today = Chronicle.day(helper.getLevel());
		LifeStages.setAdultSince(burgher, today - LifeStages.ELDER_DAYS - 5);
		List<ResourceLocation> before = ClassOutfits.layers(burgher, today);
		helper.assertTrue(before.equals(List.of(AliveWorkplace.id("textures/entity/villager/class/burgher.png"), ClassOutfits.ELDER)),
			"an elder Burgher wears " + before);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(burgher.saveWithoutId(new CompoundTag()));
		List<ResourceLocation> after = ClassOutfits.layers(copy, today);
		copy.discard();
		helper.assertTrue(after.equals(before), "after a reload the Burgher wears " + after + ", not " + before);
		helper.succeed();
	}

	private static BufferedImage image(GameTestHelper helper, String name) {
		String path = "/assets/aliveworkplace/textures/entity/villager/class/" + name + ".png";
		try (InputStream in = ClassOutfitGameTests.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("no texture at " + path);
			}
			return ImageIO.read(in);
		} catch (java.io.IOException e) {
			throw new IllegalStateException("couldn't read " + path + ": " + e);
		}
	}

	private static void pixel(GameTestHelper helper, BufferedImage image, int u, int v, int rgb, String what) {
		int argb = image.getRGB(u, v);
		helper.assertTrue((argb >>> 24) == 0xff && (argb & 0xffffff) == rgb,
			what + ": pixel " + u + "," + v + " is " + String.format("%08x", argb) + ", not " + String.format("%06x", rgb));
	}

	private static void wears(GameTestHelper helper, Villager villager, long day, List<ResourceLocation> expected, String who) {
		List<ResourceLocation> layers = ClassOutfits.layers(villager, day);
		helper.assertTrue(layers.equals(expected), who + " wears " + layers + ", not " + expected);
	}

	private static void ships(GameTestHelper helper, ResourceLocation texture) {
		String path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
		try (InputStream in = ClassOutfitGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "no texture at " + path);
			// A PNG's size is in its header: the IHDR chunk's width and height, bytes 16..23.
			byte[] head = in.readNBytes(24);
			java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(head);
			boolean png = head.length == 24 && (head[1] & 0xff) == 'P' && (head[2] & 0xff) == 'N' && (head[3] & 0xff) == 'G';
			helper.assertTrue(png && b.getInt(16) == 64 && b.getInt(20) == 64,
				path + " is not a 64x64 villager layer: " + (png ? b.getInt(16) + "x" + b.getInt(20) : "not a PNG"));
		} catch (java.io.IOException e) {
			helper.fail("couldn't read " + path + ": " + e);
		}
	}

	private static Villager villager(GameTestHelper helper, int x, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 2));
		v.setNoAi(true);
		SocialClasses.seed(v, cls(cls));
		return v;
	}

	private static SocialClasses.SocialClass cls(String name) {
		SocialClasses.SocialClass c = SocialClasses.get(AliveWorkplace.id(name));
		if (c == null) {
			throw new IllegalStateException("no class " + name + " loaded: " + SocialClasses.ladder());
		}
		return c;
	}
}
