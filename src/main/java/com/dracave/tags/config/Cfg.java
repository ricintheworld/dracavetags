package com.dracave.tags.config;

public final class Cfg {
    private Cfg() {}

    // === 聊天 ===
    public static final String CHAT_ENABLED = "chat.enabled";
    public static final String CHAT_DEFAULT_TITLE = "chat.default-title";
    public static final String CHAT_FORMAT = "chat.format";

    // === 商店 ===
    public static final String SHOP_ENABLED = "shop.enabled";
    public static final String SHOP_AUTO_EQUIP = "shop.auto-equip";
    public static final String SHOP_CURRENCIES = "shop.currencies";

    // === 数据库 ===
    public static final String DB_TYPE = "database.type";
    public static final String DB_SYNC_INTERVAL = "database.sync-interval-ticks";
    public static final String DB_MYSQL_TABLE_PREFIX = "database.mysql.table-prefix";
    public static final String DB_MYSQL_HOST = "database.mysql.host";
    public static final String DB_MYSQL_PORT = "database.mysql.port";
    public static final String DB_MYSQL_DATABASE = "database.mysql.database";
    public static final String DB_MYSQL_USER = "database.mysql.user";
    public static final String DB_MYSQL_PASSWORD = "database.mysql.password";
    public static final String DB_MYSQL_PARAMS = "database.mysql.params";
    public static final String DB_MYSQL_POOL_SIZE = "database.mysql.pool-size";
    public static final String DB_SQLITE_FILE = "database.sqlite.file";

    // === 购买确认界面 ===
    public static final String IFACE_CONFIRM_TITLE = "interface.purchase-confirm.title";
    public static final String IFACE_CONFIRM_SIZE = "interface.purchase-confirm.size";
    public static final String IFACE_CONFIRM_TITLE_SLOT = "interface.purchase-confirm.title-slot";
    public static final String IFACE_CONFIRM_CONFIRM_SLOT = "interface.purchase-confirm.confirm-slot";
    public static final String IFACE_CONFIRM_CANCEL_SLOT = "interface.purchase-confirm.cancel-slot";
    public static final String IFACE_CONFIRM_CONFIRM_MATERIAL = "interface.purchase-confirm.confirm-material";
    public static final String IFACE_CONFIRM_CANCEL_MATERIAL = "interface.purchase-confirm.cancel-material";
    public static final String IFACE_SOUNDS = "interface.sounds";

    // === 切换冷却 ===
    public static final String DISPLAY_TOGGLES_COOLDOWN = "display.toggles-cooldown";

    // === 动画 ===
    public static final String ANIM_FRAME_STEP = "animation.frame-step-ticks";
    public static final String ANIM_GRADIENT_CHAR_STEP = "animation.gradient-char-step";

    // === 自定义称号 ===
    public static final String CUSTOM_ENABLED = "custom.enabled";
    public static final String CUSTOM_CREATION_COST_ENABLED = "custom.creation-cost.enabled";
    public static final String CUSTOM_CREATION_COST_TYPE = "custom.creation-cost.type";
    public static final String CUSTOM_CREATION_COST_AMOUNT = "custom.creation-cost.amount";
    public static final String CUSTOM_DELETE_COST_ENABLED = "custom.delete-cost.enabled";
    public static final String CUSTOM_DELETE_COST_TYPE = "custom.delete-cost.type";
    public static final String CUSTOM_DELETE_COST_AMOUNT = "custom.delete-cost.amount";
    public static final String CUSTOM_TEXT_MAX_LENGTH = "custom.text.max-length";
    public static final String CUSTOM_DYNAMIC_MAX_COLORS = "custom.dynamic.max-colors";
    public static final String CUSTOM_DYNAMIC_MAX_TEXT_FRAMES = "custom.dynamic.max-text-frames";
    public static final String CUSTOM_DYNAMIC_MIN_PERIOD = "custom.dynamic.min-period-ticks";
    public static final String CUSTOM_DYNAMIC_MAX_PERIOD = "custom.dynamic.max-period-ticks";
    public static final String CUSTOM_FILTER_BLOCKED_WORDS = "custom.filter.blocked-words";
}
