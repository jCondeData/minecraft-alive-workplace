package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B1073, B1077: a banner or a head built from a file keeps what a player's could carry (a loom's 6 layers; a name, an id
 * and one real-sized signed skin), and no less: these check the cap leaves a player's own banner and head whole.
 */
public class BlockDataSizeGameTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBannerFromAFileKeepsALoomsLayersAndNoMore(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(POS);
		BlockState state = Blocks.WHITE_BANNER.defaultBlockState();
		helper.assertTrue(layers(helper, pos, state, 6) == 6, "a banner a loom could make (6 layers) should keep all 6");
		helper.assertTrue(layers(helper, pos, state, 3) == 3, "a banner of 3 layers should keep 3");
		int kept = layers(helper, pos, state, 200);
		helper.assertTrue(kept == BannerBlockEntity.MAX_PATTERNS, "a banner of 200 layers from a file should keep "
			+ BannerBlockEntity.MAX_PATTERNS + ", kept " + kept);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHeadFromAFileKeepsARealSkinButNotAHugeOne(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(POS);
		BlockState state = Blocks.PLAYER_HEAD.defaultBlockState();
		String skin = "e".repeat(600);
		String signature = "s".repeat(684);

		// a real player's head: name, id, one signed skin, all kept
		CompoundTag real = profile("Steve", property("textures", skin, signature));
		CompoundTag kept = sanitize(helper, pos, state, real).getCompound("profile");
		helper.assertTrue(kept.getString("name").equals("Steve") && kept.hasUUID("id"), "a real head should keep its name and id: " + kept);
		ListTag props = kept.getList("properties", Tag.TAG_COMPOUND);
		helper.assertTrue(props.size() == 1 && props.getCompound(0).getString("value").equals(skin)
			&& props.getCompound(0).getString("signature").equals(signature), "a real head should keep its signed skin");

		// a huge skin: only the name and id stay
		CompoundTag huge = profile("Steve", property("textures", "e".repeat(60_000), signature));
		kept = sanitize(helper, pos, state, huge).getCompound("profile");
		helper.assertTrue(kept.getString("name").equals("Steve") && !kept.contains("properties"),
			"a head with a 60,000-character skin should keep only its name and id: " + kept.getAllKeys());

		// many properties: only the name and id stay
		CompoundTag many = profile("Steve", property("textures", skin, signature), property("textures", skin, signature));
		kept = sanitize(helper, pos, state, many).getCompound("profile");
		helper.assertTrue(!kept.contains("properties"), "a head with two properties should keep none of them");

		// a name longer than a player's can be is dropped
		CompoundTag longName = profile("x".repeat(5000));
		kept = sanitize(helper, pos, state, longName).getCompound("profile");
		helper.assertTrue(!kept.contains("name"), "a head's 5,000-character name should be dropped");
		helper.succeed();
	}

	private static int layers(GameTestHelper helper, BlockPos pos, BlockState state, int count) {
		CompoundTag nbt = new CompoundTag();
		ListTag patterns = new ListTag();
		for (int i = 0; i < count; i++) {
			CompoundTag layer = new CompoundTag();
			layer.putString("pattern", "minecraft:stripe_top");
			layer.putString("color", "red");
			patterns.add(layer);
		}
		nbt.put("patterns", patterns);
		CompoundTag out = BlockEntityData.sanitize(helper.getLevel(), pos, state, new BannerBlockEntity(pos, state), nbt);
		return out.getList("patterns", Tag.TAG_COMPOUND).size();
	}

	private static CompoundTag sanitize(GameTestHelper helper, BlockPos pos, BlockState state, CompoundTag nbt) {
		return BlockEntityData.sanitize(helper.getLevel(), pos, state, new SkullBlockEntity(pos, state), nbt);
	}

	private static CompoundTag profile(String name, CompoundTag... properties) {
		CompoundTag profile = new CompoundTag();
		profile.putString("name", name);
		profile.put("id", new IntArrayTag(new int[] {1, 2, 3, 4}));
		if (properties.length > 0) {
			ListTag list = new ListTag();
			for (CompoundTag property : properties) {
				list.add(property);
			}
			profile.put("properties", list);
		}
		CompoundTag nbt = new CompoundTag();
		nbt.put("profile", profile);
		return nbt;
	}

	private static CompoundTag property(String name, String value, String signature) {
		CompoundTag property = new CompoundTag();
		property.putString("name", name);
		property.putString("value", value);
		property.putString("signature", signature);
		return property;
	}
}
