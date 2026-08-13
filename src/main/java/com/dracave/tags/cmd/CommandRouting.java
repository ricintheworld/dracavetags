package com.dracave.tags.cmd;

import java.util.Locale;
import java.util.Set;

final class CommandRouting {
    private static final Set<String> PLAYER_COMMANDS = Set.of(
            "open", "shop", "custom", "view", "wear", "clear", "reward", "ranking",
            "menu", "main", "home", "help", "list", "listtitle"
    );

    private CommandRouting() {
    }

    static String resolve(String[] args) {
        return args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
    }

    static boolean isPlayerCommand(String subcommand) {
        return PLAYER_COMMANDS.contains(subcommand);
    }
}
