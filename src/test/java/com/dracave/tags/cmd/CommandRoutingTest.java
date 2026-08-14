package com.dracave.tags.cmd;

public final class CommandRoutingTest {
    private CommandRoutingTest() {
    }

    public static void main(String[] args) {
        check("menu".equals(CommandRouting.resolve(new String[0])), "empty command should open menu");
        check("home".equals(CommandRouting.resolve(new String[]{"HOME"})), "aliases should be normalized");
        check(CommandRouting.isPlayerCommand("menu"), "menu should use player permission");
        check(CommandRouting.isPlayerCommand("main"), "main should use player permission");
        check(CommandRouting.isPlayerCommand("home"), "home should use player permission");
        check(CommandRouting.isPlayerCommand("help"), "help should be available to players");
        check(CommandRouting.isPlayerCommand("list"), "list should be available to players");
        check(!CommandRouting.isPlayerCommand("reload"), "reload must stay admin-only");

        String labeledPrice = CommandHints.hint("0", "不上架，仅创建或发放");
        String labeledCommand = CommandHints.hint("add", "快捷添加称号");
        String[] normalized = CommandHints.normalize(new String[]{labeledCommand, "vault", "傻fu龙娘", labeledPrice});
        check("add".equals(normalized[0]), "command hint should be removed before execution");
        check("0".equals(normalized[3]), "value hint should be removed before execution");
        check("傻fu龙娘".equals(normalized[2]), "player text must not be changed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
