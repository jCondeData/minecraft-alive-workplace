package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * ROADMAP 33.14, the Royal Hall: a stone great hall for a realm's capital. It isn't in the Blueprint Table; the capital's
 * owner draws it on the capital's Realm tab for a Blank Blueprint (in the capital's style; once a Royal Hall stands, its
 * upgrade, the Royal Hall II). Finished with its origin within {@link #RANGE} blocks of the capital's hall, it doubles
 * the realm treasury's cap (the II triples it). Nothing is saved: the cap goes by the finished buildings the build site
 * manager keeps, so a hall taken down or a capital moved elsewhere changes it at once.
 */
public final class RoyalHalls {
	/** How near the capital's hall a Royal Hall must stand to count. */
	public static final int RANGE = 32;
	/** Where the Realm tab's button sits: the capital never has "Put forward" there. */
	public static final int BUTTON = VillageHallScreen.FIRST_ROW + 3;

	private RoyalHalls() {
	}

	/** What a finished Royal Hall by the capital multiplies the cap by: 3 for a Royal Hall II, 2 for a Royal Hall, else 1 (and 1 with realms off). */
	public static int multiplier(MinecraftServer server, RealmData.Realm realm) {
		if (!Realms.ENABLED) {
			return 1;
		}
		return multiplier(server.getLevel(realm.capital().dimension()), realm.capital().pos());
	}

	/** The tier of the best finished Royal Hall within {@link #RANGE} of {@code hall} in {@code level}: as {@link #multiplier(MinecraftServer, RealmData.Realm)}. */
	public static int multiplier(@Nullable ServerLevel level, BlockPos hall) {
		if (level == null) {
			return 1;
		}
		int best = 1;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, RANGE)) {
			ResourceLocation base = BlueprintStyles.base(f.structure());
			if (base.equals(StarterBlueprints.ROYAL_HALL_2.id())) {
				best = Math.max(best, 3);
			} else if (base.equals(StarterBlueprints.ROYAL_HALL.id())) {
				best = Math.max(best, 2);
			}
		}
		return best;
	}

	/** The blueprint the capital at {@code hall} would draw next: the Royal Hall, or with one standing the Royal Hall II (null: the II stands). */
	@Nullable
	static StarterBlueprints.Entry next(ServerLevel level, BlockPos hall) {
		int tier = multiplier(level, hall);
		return tier >= 3 ? null : tier == 2 ? StarterBlueprints.ROYAL_HALL_2 : StarterBlueprints.ROYAL_HALL;
	}

	/** The capital's owner draws the next Royal Hall for a Blank Blueprint (none needed in creative), in the capital's style. */
	public static Realms.Result draw(ServerLevel level, BlockPos capitalHall, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		GlobalPos capital = GlobalPos.of(level.dimension(), capitalHall);
		RealmData.Realm realm = RealmData.get(server).realmOf(capital);
		Component village = VillageHalls.name(level, capitalHall);
		if (!Realms.ENABLED) {
			return Realms.refused(Realms.Refusal.OFF, Component.translatable("message.aliveworkplace.realm.off"));
		}
		Realms.Refusal refusal = Realms.refusalToRule(server, realm, capital, player);
		if (refusal != null) {
			return Realms.refused(refusal, Realms.ruleRefusal(server, refusal, realm, capital, village));
		}
		StarterBlueprints.Entry entry = next(level, capitalHall);
		if (entry == null) {
			return Realms.refused(Realms.Refusal.TAKEN, Component.translatable("message.aliveworkplace.realm.royal_hall.built", village));
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLANK_BLUEPRINT)) {
				slot = i;
			}
		}
		if (slot < 0 && !player.getAbilities().instabuild) {
			return Realms.refused(Realms.Refusal.NO_MONEY, Component.translatable("message.aliveworkplace.realm.royal_hall.needs_blank"));
		}
		ResourceLocation id = ColonyArrival.styled(level, entry.id(), Founder.styles(level, capitalHall));
		Vec3i size = BlueprintLibrary.get(level, id).map(Blueprint::size).orElse(entry.size());
		if (slot >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(slot).shrink(1);
		}
		ItemStack blueprint = BlueprintItem.create(id, size);
		if (!player.getInventory().add(blueprint)) {
			player.drop(blueprint, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.royal_hall.drawn", Blueprints.displayName(id), RANGE, village)
			.withStyle(ChatFormatting.GREEN));
	}

	/** The Realm tab's button at the capital: a lectern; what the hall does, what stands, and how to draw it. */
	static ItemStack icon(ServerLevel level, BlockPos hall, RealmData.Realm realm, boolean rules) {
		int tier = multiplier(level, hall);
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.royal_hall.about", RANGE), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(tier >= 3 ? Component.translatable("screen.aliveworkplace.realm.royal_hall.has_2")
			: tier == 2 ? Component.translatable("screen.aliveworkplace.realm.royal_hall.has_1")
			: Component.translatable("screen.aliveworkplace.realm.royal_hall.none"), tier > 1 ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
		if (tier < 3) {
			lore.add(VillageHallScreen.line(rules ? "screen.aliveworkplace.realm.royal_hall.how" : "screen.aliveworkplace.realm.royal_hall.capital", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.LECTERN, Component.translatable(tier >= 2 ? "screen.aliveworkplace.realm.royal_hall.name_2"
			: "screen.aliveworkplace.realm.royal_hall.name"), ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}
}
