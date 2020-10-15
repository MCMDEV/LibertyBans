/* 
 * LibertyBans-api
 * Copyright © 2020 Anand Beh <https://www.arim.space>
 * 
 * LibertyBans-api is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * LibertyBans-api is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with LibertyBans-api. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU Affero General Public License.
 */
package space.arim.libertybans.api.punish;

import java.time.Duration;

import space.arim.omnibus.util.concurrent.CentralisedFuture;

/**
 * A punishment ready to be created. Does not yet have an ID or start and end time, but does contain a duration. <br>
 * <br>
 * To obtain an instance, use a {@link DraftPunishmentBuilder} obtained from {@link PunishmentDrafter}
 * 
 * @author A248
 *
 */
public interface DraftPunishment extends PunishmentBase {
	
	/**
	 * Gets the duration of this draft punishment
	 * 
	 * @return the duration
	 */
	Duration getDuration();
	
	/**
	 * Enacts this punishment, adding it to the database, then enforces it. <br>
	 * <br>
	 * If the punishment type is a ban or mute, and there is already an active ban or mute for the victim,
	 * the future will yield {@code null}. See {@link space.arim.libertybans.api.punish} for a description of
	 * active and historical punishments.
	 * 
	 * @return a centralised future which yields the punishment or {@code null} if there was a conflict
	 */
	CentralisedFuture<Punishment> enactPunishment();
	
	/**
	 * Enacts this punishment, adding it to the database. <br>
	 * <br>
	 * If the punishment type is a ban or mute, and there is already an active ban or mute for the victim,
	 * the future will yield {@code null}. See {@link space.arim.libertybans.api.punish} for a description of
	 * active and historical punishments. <br>
	 * <br>
	 * Most callers will want to use {@link #enactPunishment()} instead, which has the added effect
	 * of enforcing the punishment.
	 * 
	 * @return a centralised future which yields the punishment or {@code null} if there was a conflict
	 */
	CentralisedFuture<Punishment> enactPunishmentWithoutEnforcement();
	
	/**
	 * Whether this draft punishment is equal to another. The other draft punishment
	 * must have the same details as this one
	 * 
	 * @param object the object to determine equality with
	 * @return true if equal, false otherwise
	 */
	@Override
	boolean equals(Object object);
	
}
