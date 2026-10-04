package io.github.jcondedata.aliveworkplace.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the mod needs from its mod loader, in one place. The rest of the mod (every feature package) talks to the
 * loader only through this, so a new Fabric API, or one day another loader, changes {@code platform/} and nothing else.
 * The Fabric side is {@code platform/fabric/}; the build's {@code checkLayers} task keeps loader code out of the rest.
 * Add a method here only when a feature genuinely needs the loader.
 */
public interface Platform {
	/** The platform in use (found once, from {@code META-INF/services}). */
	static Platform get() {
		return Holder.INSTANCE;
	}

	String loaderName();

	// ---- mods and files --------------------------------------------------------------------------------------------

	boolean isModLoaded(String modId);

	/** The version of a loaded mod ("1.7.3+1.21.1"), or null. */
	@Nullable
	String modVersion(String modId);

	/** Whether a loaded mod's version is in a Fabric-style range such as {@code ">=1.7.3 <1.8"}; false if not loaded. */
	boolean isModLoaded(String modId, String versionRange);

	Path configDir();

	// ---- events ----------------------------------------------------------------------------------------------------

	/** Once the server is starting (datapacks loaded, levels not yet). */
	void onServerStarting(Consumer<MinecraftServer> action);

	/** At the end of every server tick. */
	void onServerTick(Consumer<MinecraftServer> action);

	/** At the end of every tick of each level. */
	void onLevelTick(Consumer<ServerLevel> action);

	/** When an entity is loaded into (or spawned in) a server level. */
	void onEntityLoad(BiConsumer<Entity, ServerLevel> action);

	/** When a player has joined the server. */
	void onPlayerJoin(Consumer<ServerPlayer> action);

	/** When a player leaves the server. */
	void onPlayerLeave(Consumer<ServerPlayer> action);

	/** When a player right-clicks an entity; anything but {@link InteractionResult#PASS} stops the click there. */
	void onUseEntity(UseEntity handler);

	/** Before a player breaks a block (on the server); false keeps the block. */
	void allowBreakBlock(BreakBlock check);

	/** When a player right-clicks a block (on both sides); anything but {@link InteractionResult#PASS} stops the click there. */
	void onUseBlock(UseBlock handler);

	/** When a player uses the item in their hand in the air (a bucket...); false stops it. */
	void allowUseItem(UseItem check);

	/** When a player attacks an entity; false stops the attack. */
	void allowAttackEntity(AttackEntity check);

	/** Before a living entity takes damage on the server; false cancels it. */
	void allowDamage(AllowDamage check);

	/** After a living entity took damage on the server (or blocked it). */
	void afterDamage(AfterDamage action);

	/** After a living entity died on the server. */
	void afterDeath(BiConsumer<LivingEntity, DamageSource> action);

	/** After a mob was turned into another on the server (a villager into a zombie villager, or cured back): old, new. */
	void onMobConversion(BiConsumer<net.minecraft.world.entity.Mob, net.minecraft.world.entity.Mob> action);

	/** When the server's commands are registered. */
	void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> action);

	/** When tags are (re)loaded, on the server or a client. */
	void onTagsLoaded(Runnable action);

	/** A listener for the server's data (datapacks), run on every load and {@code /reload}. */
	void onDataReload(ResourceLocation id, ResourceManagerReloadListener listener);

	@FunctionalInterface
	interface UseEntity {
		InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit);
	}

	@FunctionalInterface
	interface BreakBlock {
		boolean allow(Level level, Player player, BlockPos pos, BlockState state);
	}

	@FunctionalInterface
	interface UseBlock {
		InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit);
	}

	@FunctionalInterface
	interface UseItem {
		boolean allow(Player player, Level level, InteractionHand hand);
	}

	@FunctionalInterface
	interface AttackEntity {
		boolean allow(Player player, Level level, Entity entity);
	}

	@FunctionalInterface
	interface AllowDamage {
		boolean allow(LivingEntity entity, DamageSource source, float amount);
	}

	@FunctionalInterface
	interface AfterDamage {
		void after(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked);
	}

	// ---- network ---------------------------------------------------------------------------------------------------

	/** A packet the server sends to clients. */
	<T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

	/** A packet clients send to the server, and what the server does with it (on the server thread). */
	<T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
		BiConsumer<T, ServerPlayer> handler);

	void send(ServerPlayer player, CustomPacketPayload payload);

	/** Whether the player's client knows this packet (players without the mod don't). */
	boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

	/** The players who can see {@code entity} (whose clients track it). */
	Collection<ServerPlayer> tracking(Entity entity);

	// ---- registration ----------------------------------------------------------------------------------------------

	/** A workstation (point of interest) type for {@code blocks}. */
	PoiType registerPoi(ResourceLocation id, int tickets, int range, Block... blocks);

	/**
	 * Runs {@code action} with the block {@code id} once it is registered: right away if it already is, or when another
	 * mod registers it (mods start in no fixed order). Never, if no mod does.
	 */
	void whenBlockRegistered(ResourceLocation id, Consumer<Block> action);

	/** Adds trades to a profession's level (1 to 5). */
	void addTrades(VillagerProfession profession, int level, Consumer<List<VillagerTrades.ItemListing>> trades);

	/** A builder for our own creative tab. */
	CreativeModeTab.Builder creativeTab();

	GameRules.Key<GameRules.BooleanValue> booleanRule(String name, GameRules.Category category, boolean value);

	GameRules.Key<GameRules.IntegerValue> intRule(String name, GameRules.Category category, int value, int min, int max);

	/** Data saved on entities, kept across restarts; {@code initial} (may be null) makes a value when one is asked for. */
	<T> Attachment<T> attachment(ResourceLocation id, Codec<T> codec, @Nullable Supplier<T> initial);

	/** A menu type whose screen is opened with some data from the server (a block position...). */
	<T extends AbstractContainerMenu, D> MenuType<T> menuWithData(MenuWithData<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec);

	/** Opens a menu of a {@link #menuWithData} type, sending {@code data} along to the client. */
	<D> void openMenu(ServerPlayer player, MenuProvider menu, D data);

	@FunctionalInterface
	interface MenuWithData<T extends AbstractContainerMenu, D> {
		T create(int id, Inventory inventory, D data);
	}

	// ---- containers ------------------------------------------------------------------------------------------------

	/** Item storage in blocks, the loader's way (so modded storage works too). */
	ItemStores items();

	final class Holder {
		private static final Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
			.orElseThrow(() -> new IllegalStateException("Alive Workplace: no platform found (META-INF/services)"));

		private Holder() {
		}
	}
}
