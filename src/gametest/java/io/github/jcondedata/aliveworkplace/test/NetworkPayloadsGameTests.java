package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxMenu;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * The mod's network packets (found by the full check's inventory, ROADMAP 21.2: seven payloads no test named). Each one
 * goes over the wire and back unchanged, a client can't send more than its codec allows, and the two packets a player
 * sends to change the world (posting mail, taking a blueprint) reach their handlers through the real packet path: a
 * missing registration or a handler wired to the wrong arguments breaks the mailbox and the table screen, while every
 * feature test, which calls the server methods directly, stays green.
 */
public class NetworkPayloadsGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);
	private static final BlockPos MAILBOX = new BlockPos(1, 2, 1);

	private static <T> T wire(GameTestHelper helper, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		codec.encode(buf, value);
		T back = codec.decode(buf);
		helper.assertTrue(buf.readableBytes() == 0, value.getClass().getSimpleName() + " left " + buf.readableBytes() + " bytes unread");
		return back;
	}

	private static boolean refused(GameTestHelper helper, StreamCodec<? super RegistryFriendlyByteBuf, ?> codec, Object value) {
		@SuppressWarnings("unchecked")
		StreamCodec<? super RegistryFriendlyByteBuf, Object> any = (StreamCodec<? super RegistryFriendlyByteBuf, Object>) codec;
		try {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			any.encode(buf, value);
			any.decode(buf);
			return false;
		} catch (RuntimeException e) {
			return true;
		}
	}

	/** Every payload the mod sends or receives arrives exactly as it was sent, at its size limits too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPayloadSurvivesTheWire(GameTestHelper helper) {
		ResourceLocation id = AliveWorkplace.id("uploads/alice/tiny_hut");
		BlockPos pos = new BlockPos(-30_000_000 + 7, -64, 29_999_990);

		var open = new TablePayloads.Open(pos, List.of(new TablePayloads.Entry(id, 7, 5, 9, 210),
			new TablePayloads.Entry(StarterBlueprints.MARKET_STALL.id(), 1, 1, 1, 0)), true);
		helper.assertTrue(wire(helper, TablePayloads.Open.CODEC, open).equals(open), "Open changed on the wire");
		var empty = new TablePayloads.Open(BlockPos.ZERO, List.of(), false);
		helper.assertTrue(wire(helper, TablePayloads.Open.CODEC, empty).equals(empty), "an empty library changed on the wire");

		var ask = new TablePayloads.RequestDetails(id);
		helper.assertTrue(wire(helper, TablePayloads.RequestDetails.CODEC, ask).equals(ask), "RequestDetails changed on the wire");
		var details = new TablePayloads.Details(id, List.of(new TablePayloads.Material(ResourceLocation.withDefaultNamespace("oak_planks"), 4096),
			new TablePayloads.Material(ResourceLocation.withDefaultNamespace("glass_pane"), 1)));
		helper.assertTrue(wire(helper, TablePayloads.Details.CODEC, details).equals(details), "Details changed on the wire");
		var take = new TablePayloads.Take(pos, id);
		helper.assertTrue(wire(helper, TablePayloads.Take.CODEC, take).equals(take), "Take changed on the wire");

		for (var result : List.of(new TablePayloads.UploadResult(true, Component.translatable("message.aliveworkplace.mail.posted", 3, "Bob"), Optional.of(id)),
			new TablePayloads.UploadResult(false, Component.literal("Ünïcode ✓ failure"), Optional.empty()))) {
			var back = wire(helper, TablePayloads.UploadResult.CODEC, result);
			helper.assertTrue(back.ok() == result.ok() && back.id().equals(result.id()) && back.message().getString().equals(result.message().getString()),
				"UploadResult changed on the wire: " + back);
		}

		byte[] piece = new byte[TablePayloads.UPLOAD_CHUNK];
		for (int i = 0; i < piece.length; i++) {
			piece[i] = (byte) (i * 31);
		}
		String longName = "a".repeat(250) + ".nbt";
		var chunk = new TablePayloads.UploadChunk(pos, longName, 90_000, 60_000, piece);
		var chunkBack = wire(helper, TablePayloads.UploadChunk.CODEC, chunk);
		helper.assertTrue(chunkBack.table().equals(pos) && chunkBack.fileName().equals(longName) && chunkBack.totalBytes() == 90_000
			&& chunkBack.offset() == 60_000 && Arrays.equals(chunkBack.data(), piece), "a full upload piece changed on the wire");

		var send = new Mail.Send("Alice_The_Builder", "é".repeat(Mail.MAX_MESSAGE));
		helper.assertTrue(wire(helper, Mail.Send.CODEC, send).equals(send), "a full-length letter changed on the wire");

		var request = new PreviewNetworking.Request(StarterBlueprints.STARTER_COTTAGE.id());
		helper.assertTrue(wire(helper, PreviewNetworking.Request.CODEC, request).equals(request), "a preview request changed on the wire");
		int[] blocks = {0, 0, 0, 1, 31, 15, 31, 0};
		var data = new PreviewNetworking.Data(id, List.of(0, 1, 26_000), blocks, false);
		var dataBack = wire(helper, PreviewNetworking.Data.CODEC, data);
		helper.assertTrue(dataBack.id().equals(id) && dataBack.palette().equals(data.palette()) && Arrays.equals(dataBack.blocks(), blocks)
			&& !dataBack.complete(), "preview data changed on the wire");
		helper.succeed();
	}

	/** What a client sends is capped by its codec: an oversized piece, file name or recipient name never gets decoded. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oversizedClientPacketsAreRefused(GameTestHelper helper) {
		helper.assertTrue(refused(helper, TablePayloads.UploadChunk.CODEC,
			new TablePayloads.UploadChunk(BlockPos.ZERO, "big.nbt", 30_001, 0, new byte[TablePayloads.UPLOAD_CHUNK + 1])),
			"an upload piece over " + TablePayloads.UPLOAD_CHUNK + " bytes went through");
		helper.assertTrue(refused(helper, TablePayloads.UploadChunk.CODEC,
			new TablePayloads.UploadChunk(BlockPos.ZERO, "b".repeat(257), 1, 0, new byte[1])), "a 257-character file name went through");
		helper.assertTrue(refused(helper, Mail.Send.CODEC, new Mail.Send("x".repeat(33), "hi")), "a 33-character player name went through");
		helper.assertTrue(refused(helper, Mail.Send.CODEC, new Mail.Send("Bob", "x".repeat(Mail.MAX_MESSAGE * 4 + 1))),
			"a letter over the codec's limit went through");
		helper.succeed();
	}

	private static void receive(ServerPlayer player, CustomPacketPayload payload) {
		player.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(payload));
	}

	private static List<Parcel> parcelsFrom(PostOffice office, ServerPlayer player) {
		List<Parcel> out = new ArrayList<>();
		for (Parcel p : office.parcels()) {
			if (p.from().equals(player.getUUID())) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * The mailbox screen's Send button, as the packet a client sends: a blank name or an unknown player posts nothing
	 * and keeps the items in the top row, a known player with a mailbox gets the items and the letter, and a Send with
	 * no mailbox open does nothing.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"networkPayloadsMail"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "networkPayloadsMail")
	public void theMailboxSendPacketPostsAParcel(GameTestHelper helper) {
		helper.setBlock(MAILBOX, ModBlocks.MAILBOX);
		MailboxBlockEntity mailbox = (MailboxBlockEntity) helper.getBlockEntity(MAILBOX);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(MAILBOX);
		player.moveTo(new Vec3(at.getX() + 1.5, at.getY(), at.getZ() + 0.5));
		PostOffice office = PostOffice.get(helper.getLevel().getServer());
		GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), at);
		// Every mock player has the same name: the parcel goes to whichever of them the server finds by that name.
		String me = player.getGameProfile().getName();
		ServerPlayer addressee = helper.getLevel().getServer().getPlayerList().getPlayerByName(me);
		office.register(addressee.getUUID(), home);
		try {
			MailboxMenu menu = new MailboxMenu(7, player.getInventory(), mailbox);
			player.containerMenu = menu;
			menu.getSlot(0).set(new ItemStack(Items.APPLE, 3));
			menu.getSlot(4).set(new ItemStack(Items.EMERALD, 2));

			receive(player, new Mail.Send("   ", "hello"));
			helper.assertTrue(parcelsFrom(office, player).isEmpty() && menu.hasOutgoing(), "a blank name posted a parcel or took the items");
			receive(player, new Mail.Send("Nobody_Ever_Joined", "hello"));
			helper.assertTrue(parcelsFrom(office, player).isEmpty() && menu.hasOutgoing(), "an unknown player got a parcel or the items were taken");

			receive(player, new Mail.Send(" " + me + " ", "  See you at the market!  "));
			List<Parcel> posted = parcelsFrom(office, player);
			helper.assertTrue(posted.size() == 1, "the Send packet posted " + posted.size() + " parcels, not 1");
			Parcel parcel = posted.get(0);
			helper.assertTrue(parcel.to().equals(addressee.getUUID()) && parcel.origin().equals(home), "parcel addressed wrong: to " + parcel.toName() + " from " + parcel.origin());
			helper.assertTrue(parcel.count() == 6, "parcel holds " + parcel.count() + " items, not 3 apples + 2 emeralds + the letter");
			ItemStack letter = parcel.items().get(0);
			var content = letter.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(letter.is(Items.WRITTEN_BOOK) && content != null && content.pages().get(0).raw().getString().equals("See you at the market!"),
				"the letter isn't first or isn't trimmed: " + letter);
			helper.assertTrue(!menu.hasOutgoing(), "the top row kept items after posting");

			player.containerMenu = player.inventoryMenu;
			receive(player, new Mail.Send(me, "no mailbox open"));
			helper.assertTrue(parcelsFrom(office, player).size() == 1, "a Send with no mailbox open posted a parcel");
		} finally {
			for (Parcel p : parcelsFrom(office, player)) {
				office.remove(p);
			}
			office.unregister(addressee.getUUID(), home);
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** The table screen's Take button, as the packet a client sends: it costs a Blank Blueprint and gives the blueprint. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTableTakePacketGivesTheBlueprint(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		try {
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			receive(player, new TablePayloads.Take(table, StarterBlueprints.MARKET_STALL.id()));
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the Take packet didn't use a Blank Blueprint");
			int found = 0;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (BlueprintItem.data(player.getInventory().getItem(i)).map(d -> d.structure().equals(StarterBlueprints.MARKET_STALL.id())).orElse(false)) {
					found++;
				}
			}
			helper.assertTrue(found == 1, "the Take packet gave " + found + " Market Stall blueprints, not 1");
			receive(player, new TablePayloads.Take(table, AliveWorkplace.id("no_such_blueprint")));
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "a Take for an unknown blueprint used a Blank Blueprint");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
