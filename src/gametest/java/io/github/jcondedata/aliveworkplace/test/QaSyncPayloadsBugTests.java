package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.netty.buffer.Unpooled;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * QA, a bug found (not registered: it fails until the bug is fixed). {@link BlueprintNames#put} cuts a name to 128
 * chars with {@code substring}, which can cut an emoji (a surrogate pair) in half; the half that's left can't be
 * written as UTF-8, so the name players' games get ends in "?" where the server's ends in half an emoji.
 */
public class QaSyncPayloadsBugTests {
	/** Expected: the name kept and the name a client reads back are the same. */
	//$ gametest 'net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = net.fabricmc.fabric.api.gametest.v1.FabricGameTest.EMPTY_STRUCTURE)
	public void aNameCutAtTheCapThroughAnEmojiReadsBackTheSame(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sync/emoji_cut");
		try {
			BlueprintNames.put(server, id, "a".repeat(127) + "🏰 Castle");
			String kept = BlueprintNames.get(id).orElseThrow();
			BlueprintNames.Sync sent = new BlueprintNames.Sync(Map.of(id, kept), false);
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			BlueprintNames.Sync.CODEC.encode(buf, sent);
			String back = BlueprintNames.Sync.CODEC.decode(buf).names().get(id);
			helper.assertTrue(kept.equals(back), "the server keeps a name ending in U+" + Integer.toHexString(kept.charAt(kept.length() - 1)).toUpperCase()
				+ " (" + kept.length() + " chars); a client reads back one ending in \"" + back.substring(back.length() - 1) + "\" (" + back.length() + " chars)");
		} finally {
			BlueprintNames.put(server, id, "");
		}
		helper.succeed();
	}
}
