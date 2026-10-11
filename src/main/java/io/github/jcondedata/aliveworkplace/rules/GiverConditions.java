package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.JobFamilies;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * The conditions on the villager who gives a quest (personal requests, ROADMAP 31.9), and on the player it is for:
 * {@code hearts_at_least} (the player's hearts with them), {@code job} (one of some professions), {@code job_family}
 * ({@link JobFamilies}), {@code level_below} (a worker whose job level is under it, 5 a Master), {@code villager_type}
 * (plains, desert, ...), {@code home_tier_below} (their home's tier, 0 without one) and {@code no_bed}. In a quest file
 * with no giver (the hall's board) none of them ever holds, so a typo never posts a quest for everyone.
 */
public final class GiverConditions {
	/** A condition read against the quest's giver (and the player), never against the village alone. */
	public interface OnGiver extends Condition {
		boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player);

		@Override
		default Progress progress(ServerLevel level, BlockPos hall) {
			return new Progress(0, 1, Component.translatable("rule.aliveworkplace." + type()));
		}

		@Override
		default boolean met(ServerLevel level, BlockPos hall, @Nullable Villager giver, @Nullable ServerPlayer player) {
			return giver != null && holds(level, giver, player);
		}
	}

	static void register() {
		Conditions.register("hearts_at_least", j -> new HeartsAtLeast(range(j, "hearts", 1, Friendship.HEARTS)));
		Conditions.register("job", j -> new Job(ids(j, "jobs")));
		Conditions.register("job_family", GiverConditions::family);
		Conditions.register("level_below", j -> new LevelBelow(range(j, "level", 2, 6)));
		Conditions.register("villager_type", j -> new Type(ids(j, "types")));
		Conditions.register("home_tier_below", j -> new HomeTierBelow(range(j, "tier", 1, 9)));
		Conditions.register("no_bed", j -> new NoBed());
	}

	private static int range(JsonObject json, String field, int min, int max) {
		int n = Conditions.count(json, field);
		if (n < min || n > max) {
			throw new IllegalArgumentException("'" + field + "' must be " + min + " to " + max);
		}
		return n;
	}

	/** A list of ids ({@code "jobs": ["minecraft:farmer"]}); throws when it's missing, empty or holds something that isn't an id. */
	private static Set<ResourceLocation> ids(JsonObject json, String field) {
		if (!json.has(field) || !json.get(field).isJsonArray()) {
			throw new IllegalArgumentException("missing '" + field + "' (a list of ids)");
		}
		Set<ResourceLocation> out = new LinkedHashSet<>();
		for (JsonElement e : json.getAsJsonArray(field)) {
			ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
			if (id == null) {
				throw new IllegalArgumentException("'" + e.getAsString() + "' in '" + field + "' isn't an id");
			}
			out.add(id);
		}
		if (out.isEmpty()) {
			throw new IllegalArgumentException("'" + field + "' is empty");
		}
		return Set.copyOf(out);
	}

	private static JobFamily family(JsonObject json) {
		String name = json.has("family") ? json.get("family").getAsString() : "";
		try {
			return new JobFamily(JobFamilies.Family.valueOf(name.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("no job family '" + name + "'");
		}
	}

	/** {@code hearts_at_least}: the player has {@code hearts} hearts or more with the giver. */
	public record HeartsAtLeast(int hearts) implements OnGiver {
		@Override
		public String type() {
			return "hearts_at_least";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return player != null && Friendship.hearts(Friendship.points(giver, player.getUUID())) >= hearts;
		}
	}

	/** {@code job}: the giver's profession is one of {@code jobs}. */
	public record Job(Set<ResourceLocation> jobs) implements OnGiver {
		@Override
		public String type() {
			return "job";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return jobs.contains(BuiltInRegistries.VILLAGER_PROFESSION.getKey(giver.getVillagerData().getProfession()));
		}
	}

	/** {@code job_family}: the giver's job is of the family {@code family}. */
	public record JobFamily(JobFamilies.Family family) implements OnGiver {
		@Override
		public String type() {
			return "job_family";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return JobFamilies.of(giver) == family;
		}
	}

	/** {@code level_below}: the giver has a job (not jobless, not a nitwit) and their job level is under {@code level}. */
	public record LevelBelow(int level) implements OnGiver {
		@Override
		public String type() {
			return "level_below";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			VillagerProfession job = giver.getVillagerData().getProfession();
			return job != VillagerProfession.NONE && job != VillagerProfession.NITWIT && giver.getVillagerData().getLevel() < this.level;
		}
	}

	/** {@code villager_type}: the giver's villager type (where they or their forebears come from) is one of {@code types}. */
	public record Type(Set<ResourceLocation> types) implements OnGiver {
		@Override
		public String type() {
			return "villager_type";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return types.contains(BuiltInRegistries.VILLAGER_TYPE.getKey(giver.getVillagerData().getType()));
		}
	}

	/** {@code home_tier_below}: the giver's home is under tier {@code tier} (0: no bed, or a bed in no finished building). */
	public record HomeTierBelow(int tier) implements OnGiver {
		@Override
		public String type() {
			return "home_tier_below";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return Homes.of(level, giver).map(Homes.Home::tier).orElse(0) < tier;
		}
	}

	/** {@code no_bed}: the giver has no bed of their own. */
	public record NoBed() implements OnGiver {
		@Override
		public String type() {
			return "no_bed";
		}

		@Override
		public boolean holds(ServerLevel level, Villager giver, @Nullable ServerPlayer player) {
			return VillageNeeds.bed(level, giver) == null;
		}
	}

	private GiverConditions() {
	}
}
