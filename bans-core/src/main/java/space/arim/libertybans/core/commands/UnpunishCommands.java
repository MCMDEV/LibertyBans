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
package space.arim.libertybans.core.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import space.arim.libertybans.api.CompositeVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.Victim;
import space.arim.libertybans.api.punish.EnforcementOptions;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.api.punish.PunishmentRevoker;
import space.arim.libertybans.api.punish.RevocationOrder;
import space.arim.libertybans.core.commands.extra.AsCompositeWildcard;
import space.arim.libertybans.core.commands.extra.NotificationMessage;
import space.arim.libertybans.core.commands.extra.PunishmentPermissionCheck;
import space.arim.libertybans.core.commands.extra.TabCompletion;
import space.arim.libertybans.core.config.InternalFormatter;
import space.arim.libertybans.core.config.RemovalsSection.PunishmentRemoval;
import space.arim.libertybans.core.config.RemovalsSection.WarnRemoval;
import space.arim.libertybans.core.env.CmdSender;
import space.arim.libertybans.core.event.PardonEventImpl;
import space.arim.libertybans.core.event.PostPardonEventImpl;
import space.arim.libertybans.core.punish.EnforcementOpts;
import space.arim.libertybans.core.punish.Mode;
import space.arim.libertybans.core.punish.PunishmentPermission;
import space.arim.omnibus.util.concurrent.CentralisedFuture;
import space.arim.omnibus.util.concurrent.ReactionStage;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class UnpunishCommands extends AbstractSubCommandGroup implements PunishUnpunishCommands {
	
	private final PunishmentRevoker revoker;
	private final InternalFormatter formatter;
	private final TabCompletion tabCompletion;
	
	UnpunishCommands(Dependencies dependencies, Stream<String> matches,
					 PunishmentRevoker revoker, InternalFormatter formatter,
					 TabCompletion tabCompletion) {
		super(dependencies, matches);
		this.revoker = revoker;
		this.formatter = formatter;
		this.tabCompletion = tabCompletion;
	}

	@Override
	public final CommandExecution execute(CmdSender sender, CommandPackage command, String arg) {
		PunishmentType type = parseType(arg.toUpperCase(Locale.ROOT));
		PunishmentPermissionCheck permissionCheck;
		return new Execution(
				sender, command, type,
				(permissionCheck = new PunishmentPermissionCheck(sender, new PunishmentPermission(type, Mode.UNDO))),
				new NotificationMessage(permissionCheck));
	}
	
	@Override
	public final Stream<String> suggest(CmdSender sender, String arg, int argIndex) {
		if (argIndex == 0) {
			Stream<String> availableNames = tabCompletion.completeOfflinePlayerNames(sender);
			PunishmentType type = parseType(arg.toUpperCase(Locale.ROOT));
			if (type == PunishmentType.BAN) {
				// Online players are not banned, so exclude names of known online players
				Set<String> knownToBeOnline = sender.getPlayerNames().collect(Collectors.toUnmodifiableSet());
				return availableNames.filter((name) -> !knownToBeOnline.contains(name));
			}
			return availableNames;
		}
		return Stream.empty();
	}
	
	private class Execution extends TypeSpecificExecution {

		private final PunishmentPermissionCheck permissionCheck;
		private final NotificationMessage notificationMessage;
		private final PunishmentRemoval section;

		Execution(CmdSender sender, CommandPackage command, PunishmentType type,
				  PunishmentPermissionCheck permissionCheck, NotificationMessage notificationMessage) {
			super(sender, command, type);
			this.permissionCheck = permissionCheck;
			this.notificationMessage = notificationMessage;
			section = messages().removals().forType(type);
		}

		@Override
		public ReactionStage<Void> execute() {
			if (!command().hasNext()) {
				sender().sendMessage(section.usage());
				return null;
			}
			String targetArg = command().next();
			return parseVictim(sender(), command(), targetArg, type()).thenCompose((victim) -> {
				if (victim == null) {
					return completedFuture(null);
				}
				if (!permissionCheck.checkPermission(victim, section.permission())) {
					return completedFuture(null);
				}
				return performUndo(victim, targetArg);

			}).thenCompose((punishment) -> {
				if (punishment == null) {
					return completedFuture(null);
				}
				return sendSuccess(punishment, targetArg);
			});
		}

		private CompletionStage<Punishment> performUndo(Victim victim, String targetArg) {
			final PunishmentType type = type();

			RevocationOrder revocationOrder;
			final int id;
			if (type == PunishmentType.WARN) {

				if (!command().hasNext()) {
					sender().sendMessage(section.usage());
					return completedFuture(null);
				}
				String idArg = command().next();
				try {
					id = Integer.parseInt(idArg);
				} catch (NumberFormatException ignored) {
					sender().sendMessage(((WarnRemoval) section).notANumber().replaceText("%ID_ARG%", idArg));
					return completedFuture(null);
				}
				revocationOrder = revoker.revokeByIdAndType(id, type);
			} else {
				assert type.isSingular() : type;
				// Try to revoke this punishment for either the simple victim or composite wildcard victim
				CompositeVictim compositeWildcard = new AsCompositeWildcard().apply(victim);
				revocationOrder = revoker.revokeByTypeAndPossibleVictims(type, List.of(victim, compositeWildcard));
				id = -1;
			}
			return fireWithTimeout(new PardonEventImpl(sender().getOperator(), victim, type)).thenCompose((event) -> {
				if (event.isCancelled()) {
					return completedFuture(null);
				}
				// Unenforce the punishment later, after we are sure it exists
				EnforcementOptions enforcementOptions = revocationOrder
						.enforcementOptionsBuilder()
						.enforcement(EnforcementOptions.Enforcement.NONE)
						.broadcasting(EnforcementOptions.Broadcasting.NONE)
						.build();
				return revocationOrder.undoAndGetPunishment(enforcementOptions).thenApply((optPunishment) -> {
					if (optPunishment.isEmpty()) {
						sendNotFound(targetArg, id);
						return null;
					}
					return optPunishment.get();
				});
			});
		}

		// Outcomes

		private void sendNotFound(String targetArg, int id) {
			boolean isWarn = type() == PunishmentType.WARN;
			String idString = (isWarn) ? Integer.toString(id) : null;
			ComponentLike notFound = section.notFound().replaceText((str) -> {
				if (isWarn) {
					str = str.replace("%ID%", idString);
				}
				return str.replace("%TARGET%", targetArg);
			});
			sender().sendMessage(notFound);
		}

		private CentralisedFuture<Void> sendSuccess(Punishment punishment, String targetArg) {

			notificationMessage.evaluate(command()); // Evaluate -s

			EnforcementOptions enforcementOptions = EnforcementOpts
					.builder()
					.enforcement(EnforcementOptions.Enforcement.GLOBAL)
					.broadcasting(notificationMessage.isSilent() ?
							EnforcementOptions.Broadcasting.SILENT : EnforcementOptions.Broadcasting.NORMAL
					)
					.targetArgument(targetArg)
					.unOperator(sender().getOperator())
					.build();
			CentralisedFuture<?> unenforcement = punishment
					.unenforcePunishment(enforcementOptions)
					.toCompletableFuture();
			CentralisedFuture<Component> futureMessage = formatter.formatWithPunishment(
					section.successMessage().replaceText("%TARGET%", targetArg), punishment);

			return futuresFactory().allOf(unenforcement, futureMessage).thenCompose((ignore) -> {
				return fireWithTimeout(new PostPardonEventImpl(sender().getOperator(), punishment));
			}).thenRun(() -> {
				sender().sendMessage(futureMessage.join());
			});
		}
	}

}
