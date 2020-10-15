/* 
 * LibertyBans-core
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * LibertyBans-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans-core. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */
package space.arim.libertybans.core.punish;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import space.arim.omnibus.util.concurrent.CentralisedFuture;

import space.arim.libertybans.api.punish.DraftPunishment;
import space.arim.libertybans.api.punish.Punishment;

class DraftPunishmentImpl extends AbstractPunishmentBase implements DraftPunishment {
	
	private final Duration duration;
	
	DraftPunishmentImpl(EnforcementCenter center, DraftPunishmentBuilderImpl builder) {
		super(center, builder.type, builder.victim, builder.operator, builder.reason, builder.scope);
		duration = builder.duration;
	}
	
	@Override
	public Duration getDuration() {
		return duration;
	}
	
	@Override
	public CentralisedFuture<Punishment> enactPunishment() {
		return enactPunishmentWithoutEnforcement().thenCompose((punishment) -> {
			return (punishment == null) ?
					CompletableFuture.completedStage(null) :
					punishment.enforcePunishment().thenApply((ignore) -> punishment);
		});
	}
	
	@Override
	public CentralisedFuture<Punishment> enactPunishmentWithoutEnforcement() {
		return center.getEnactor().enactPunishment(this);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + duration.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!super.equals(object)) {
			return false;
		}
		if (!(object instanceof DraftPunishmentImpl)) {
			return false;
		}
		DraftPunishmentImpl other = (DraftPunishmentImpl) object;
		return duration.equals(other.duration);
	}
	
	@Override
	public String toString() {
		return "DraftPunishmentImpl [duration=" + duration + ", toString()=" + super.toString() + "]";
	}
	
}
