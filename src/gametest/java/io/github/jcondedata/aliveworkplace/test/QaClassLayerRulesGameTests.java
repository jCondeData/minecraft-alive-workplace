package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA for ROADMAP 34.17 (written from the spec, not the code): a class layer is worn over the job's outfit "never on the
 * head (the job's hat stays), nothing under the level badge, the back painted, the job still readable under it", and
 * the elder's look is a face layer. Checked on the shipped 64x64 textures by the villager sheet's UV layout: the head
 * and the job's hat use rows 0 to 19; the jacket (texOffs 0, 38, 8x20x6) has its front at u 6-13 and its back at u
 * 20-27, rows 44-63; vanilla 1.21.1's five profession_level badges paint only inside u 10-13, v 54-57 of the jacket front.
 */
public class QaClassLayerRulesGameTests implements FabricGameTest {
	private static final List<String> CLASSES = List.of("artisan", "burgher", "noble");

	/** Artisan, Burgher and Noble paint nothing on the head or the hat: the job's hat and face show as before. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaClassLayersLeaveTheHeadAlone(GameTestHelper helper) {
		for (String name : CLASSES) {
			BufferedImage image = image(helper, name);
			int painted = count(image, 0, 64, 0, 20);
			helper.assertTrue(painted == 0, name + "'s layer paints " + painted + " pixels on the head or hat");
		}
		helper.succeed();
	}

	/** Nothing of a class layer sits where a level badge (stone to diamond) is drawn, so the level always shows. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaClassLayersLeaveTheBadgeClear(GameTestHelper helper) {
		for (String name : List.of("artisan", "burgher", "noble", "elder")) {
			BufferedImage image = image(helper, name);
			int painted = count(image, 10, 14, 54, 58);
			helper.assertTrue(painted == 0, name + "'s layer paints " + painted + " pixels under the level badge");
		}
		helper.succeed();
	}

	/** Each class layer paints the jacket's back as well as its front: seen from behind the class still shows. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaClassLayersPaintTheBack(GameTestHelper helper) {
		for (String name : CLASSES) {
			BufferedImage image = image(helper, name);
			helper.assertTrue(count(image, 20, 28, 44, 64) > 0, name + "'s layer leaves the back bare");
			helper.assertTrue(count(image, 6, 14, 44, 64) > 0, name + "'s layer leaves the front bare");
		}
		helper.succeed();
	}

	/** The job stays readable: no class layer covers more than half of the jacket's front, where the job's outfit shows. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaClassLayersLetTheJobShowThrough(GameTestHelper helper) {
		for (String name : CLASSES) {
			BufferedImage image = image(helper, name);
			int front = count(image, 6, 14, 44, 64);
			helper.assertTrue(front * 2 <= 8 * 20, name + "'s layer covers " + front + " of the jacket front's 160 pixels");
		}
		helper.succeed();
	}

	/** The elder's look is a face layer only: on the head's faces, never the hat or the body. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheEldersLookIsOnTheFaceOnly(GameTestHelper helper) {
		BufferedImage elder = image(helper, "elder");
		helper.assertTrue(count(elder, 0, 32, 0, 20) > 0, "the elder's layer paints nothing on the head");
		helper.assertTrue(count(elder, 32, 64, 0, 20) == 0, "the elder's layer paints the job's hat");
		helper.assertTrue(count(elder, 0, 64, 20, 64) == 0, "the elder's layer paints the body");
		helper.succeed();
	}

	private static int count(BufferedImage image, int u0, int u1, int v0, int v1) {
		int n = 0;
		for (int v = v0; v < v1; v++) {
			for (int u = u0; u < u1; u++) {
				if ((image.getRGB(u, v) >>> 24) != 0) {
					n++;
				}
			}
		}
		return n;
	}

	private static BufferedImage image(GameTestHelper helper, String name) {
		String path = "/assets/" + AliveWorkplace.MOD_ID + "/textures/entity/villager/class/" + name + ".png";
		try (InputStream in = QaClassLayerRulesGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "missing " + path);
			BufferedImage image = ImageIO.read(in);
			helper.assertTrue(image.getWidth() == 64 && image.getHeight() == 64, path + " is not 64x64");
			return image;
		} catch (java.io.IOException e) {
			throw new AssertionError(path + ": " + e);
		}
	}
}
