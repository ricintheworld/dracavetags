<div align="center">

# 🏷️ DraCaveTags v2

**称号仓库 + 商店 + 自定义 + 管理面板 —— 一体化动态称号插件**

[![MineBBS](https://img.shields.io/badge/MineBBS-教程帖-orange?style=flat-square)](https://www.minebbs.com/threads/dracavetitle-gui.48962/#post-229305)
[![QQ Group](https://img.shields.io/badge/QQ群-1101907336-blue?style=flat-square&logo=tencentqq)](https://qm.qq.com/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-green?style=flat-square&logo=papermc)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21+-yellow?style=flat-square&logo=openjdk)](https://adoptium.net/)

[简介](#-简介) • [快速开始](#-快速开始) • [亮点](#-亮点) • [命令一览](#-命令一览) • [权限](#-权限一览) • [变量](#-变量一览) • [GUI 自定义](#-gui-自定义) • [升级指南](#-从-114-升级)

</div>

---

## 📖 简介

DraCaveTags 是一款支持 **Paper 系服务端**的动态称号插件，将「称号仓库 + 称号商店 + 自定义称号 + 管理面板」缝进一个轻量插件中。

- 🎨 **真动态效果**：渐变、彩虹、闪烁、帧动画等，实时渲染无需资源包
- ✨ **称号增值**：可挂载粒子特效与药水效果，支持数量排行榜
- 💰 **四种货币出售**：Vault / PlayerPoints / 称号币 / 物品（兼容 IA / CE 等自定义物品）
- 🌐 **多语言支持**：内置 `zh_cn.yml` 与 `en_us.yml`，热重载即时生效
- 🗄️ **跨服兼容**：SQLite / MySQL 双存储后端无缝切换

> 💡 **2.0.0 亮点**：可视化 GUI 自定义（6 个界面全部 YAML 配置驱动）、`/ttt old` 支持 1.1.4 数据迁移、管理面板创建称号、多语言支持。

适用场景：公益服、商业服、RPG 服、小游戏服。

---

## 🚀 快速开始

1.  **安装前置**
    -   ✅ PlaceholderAPI（必须）
    -   ⬜ TAB 或其他聊天格式插件（可选）
2.  **配置占位符**
    在聊天 / TAB 配置中使用 `%dracavetags_title%` 等变量放置称号
3.  **上传称号**
    加载插件后，管理员执行 `/dct upload all` 或打开管理 GUI 上传称号

---

## ✨ 亮点

| 特性 | 说明 |
| :--- | :--- |
| 🎬 真动态称号 | 五种动画实时渲染，丝滑可控，无需资源包 |
| 🖥️ 可视化 GUI 自定义 | 6 个界面布局全部 YAML 驱动，管理员直接改 `gui/*.yml` |
| 🔧 一体化管理 | 仓库、商店、自定义、管理面板全部集成 |
| 🎁 称号增值 | 粒子特效、药水效果、数量排行榜 |
| 👤 玩家信息头 | 仓库/商店/自定义界面显示玩家装备称号和余额 |
| 🔄 跨服兼容 | SQLite / MySQL 双存储 |
| 🌈 渐变模式 | 单向循环 / 回弹两种模式，可覆盖动画周期、速度、粒度 |

### 🎨 自定义称号

-   编辑 `plugins/DraCaveTags/tags.yml`，参考注释和示例自行定义
-   支持动态颜色、渐变方向、五种动画类型
-   不想手写？**管理面板可以直接可视化编辑一切**
-   玩家可使用 `/dct custom` 创建个人自定义称号

---

## 📋 命令一览

> 💡 命令别名：`/dctags`、`/dct`、`/dracavetags`、`/tags`

### 玩家命令

| 命令 | 说明 |
| :--- | :--- |
| `/dct help` | 查看帮助 |
| `/dct open` | 打开称号仓库 |
| `/dct shop` | 打开称号商店 |
| `/dct list` | 列出所有称号 |
| `/dct wear <ID>` | 穿戴称号 |
| `/dct wear none` / `/dct clear` | 卸下称号 |
| `/dct custom` | 打开自定义称号 GUI |
| `/dct custom <名称>` | 快速创建静态自定义称号 |
| `/dct custom create <类型> <参数>` | 创建自定义称号 |
| `/dct custom edit <ID> <类型> <参数>` | 编辑自定义称号 |
| `/dct custom delete <ID>` | 删除自定义称号 |
| `/dct view [玩家]` | 查看自己/他人称号列表 |
| `/dct reward` | 打开奖励中心 |
| `/dct ranking` | 称号数量排行榜 |

### 管理员命令

| 命令 | 说明 |
| :--- | :--- |
| `/dct add <货币> <名称> <价格> [天数] [隐藏] [玩家]` | 创建称号 |
| `/dct del <ID>` | 删除称号 |
| `/dct set <玩家> <ID> [天数]` | 设置并强制穿戴 |
| `/dct addPlayerTitle <玩家> <ID> [天数]` | 发放称号 |
| `/dct setDescription <ID> <描述>` | 设置描述 |
| `/dct addPermission <ID> <权限>` | 设置购买权限 |
| `/dct setTitleBuff <ID> <效果> [等级]` | 添加药水效果 |
| `/dct delBuff <ID> <效果>` | 移除药水效果 |
| `/dct setTitleParticle <ID> <粒子> [id] [颜色]` | 设置粒子 |
| `/dct removeTitleParticle <ID>` | 移除粒子 |
| `/dct addCoin <玩家> <金额>` | 增加称号币 |
| `/dct subtractCoin <玩家> <金额>` | 扣除称号币 |
| `/dct setCustom <玩家> <次数>` | 设置自定义额度 |
| `/dct addCustom <玩家> <次数>` | 追加自定义额度 |
| `/dct addReward <数量> <货币> <金额>` | 配置里程碑奖励 |
| `/dct randomCard <货币> <天数>` | 生成随机称号卡 |
| `/dct changeItem <ID> <天数> <数量> [玩家]` | 称号转物品卡 |
| `/dct adminShop` | 称号管理商店 GUI |
| `/dct panel` / `/dct panel-id <ID>` | 按名称/ID 打开管理面板 |
| `/dct panel-edit <ID> text <新文本>` | 命令行改文本 |
| `/dct panel-edit <ID> price <金额\|none>` | 命令行改价格 |
| `/dct upload all` | 上传 tags.yml 到数据库 |
| `/dct upload data` | 从数据库同步到 tags.yml |
| `/dct upload all --check` | 仅校验 tags.yml |
| `/dct convert <MYSQL\|SQLITE>` | 转换存储类型 |
| `/dct reload` | 重载配置 |

### 🔄 数据迁移命令（/ttt）

| 命令 | 说明 |
| :--- | :--- |
| `/ttt title null [源库]` | 静态迁移 PlayerTitle 称号 |
| `/ttt title color [源库]` | 动态迁移 PlayerTitle 称号 |
| `/ttt db [源库]` | 迁移 PlayerTitle 玩家数据 |
| `/ttt old --check` | 检查旧版 DraCaveTitle 1.1.4 数据 |
| `/ttt old db` | 迁移旧版玩家数据 |
| `/ttt old title` | 迁移旧版称号定义 |

---

## 🔐 权限一览

| 权限节点 | 说明 | 默认 |
| :--- | :--- | :--- |
| `dracave.tags.use` | 玩家基础权限 | 全员 |
| `dracave.tags.admin` | 管理员命令 | OP |
| `dracave.tags.admin.panel` | 管理面板 | OP |
| `dracave.tags.admin.upload` | 上传 tags.yml | OP |
| `dracave.tags.migrate` | 数据迁移命令 | OP |
| `dracave.tags.custom.static` | 创建静态自定义称号 | 无 |
| `dracave.tags.custom.dynamic` | 创建动态自定义称号 | 无 |
| `dracave.tags.custom.limit.1` | 自定义上限 1 个 | 无 |
| `dracave.tags.custom.limit.2` | 自定义上限 2 个 | 无 |
| `dracave.tags.custom.limit.3` | 自定义上限 3 个 | 无 |
| `dracave.tags.custom.limit.5` | 自定义上限 5 个 | 无 |
| `dracave.tags.custom.limit.10` | 自定义上限 10 个 | 无 |
| `dracave.tags.custom.limit.unlimited` | 无限自定义额度 | OP |
| `dracave.tags.*` | 全部权限 | OP |
| `ttt.use` | 旧版迁移兼容 | OP |

---

## 🏷️ 变量一览

> 需安装 PlaceholderAPI

| 变量 | 说明 |
| :--- | :--- |
| `%dracavetags_title%` | 当前称号 MiniMessage 格式 |
| `%dracavetags_title_v%` | 当前称号 & 颜色码格式 |
| `%dracavetags_title_s%` | 当前称号 § 颜色码格式 |
| `%dracavetags_title_only%` | 当前称号纯文本 |
| `%dracavetags_title_id%` | 当前称号 ID |
| `%dracavetags_title_yesno%` | 是否穿戴称号 true/false |
| `%dracavetags_coin%` | 称号币余额 |
| `%dracavetags_coin_raw%` | 称号币余额（纯数字） |

---

## 🖥️ GUI 自定义

六种界面布局全部通过 `plugins/DraCaveTags/gui/*.yml` 配置：

| 文件 | 界面 |
| :--- | :--- |
| `main.yml` | 称号主菜单 |
| `self.yml` | 称号仓库（个人称号） |
| `shop.yml` | 称号商店 |
| `custom.yml` | 自定义称号 |
| `admin.yml` | 管理员菜单 |
| `reward.yml` | 奖励中心 |

---

## ⬆️ 从 1.1.4 升级

1.  安装 JAR，重启服务器
2.  执行 `/ttt old --check` 检查旧数据
3.  执行 `/ttt old db` 迁移玩家数据（称号穿戴、解锁、称号币、额度）
4.  执行 `/ttt old title` 迁移称号定义
5.  更新 TAB/聊天配置中的占位符：`%dracavetitle_%` → `%dracavetags_%`
6.  更新权限节点：`dracave.title.*` → `dracave.tags.*`

---

<div align="center">

**如果这个插件对你有帮助，欢迎点亮 ⭐ Star 支持！**

Made with ❤️ by [ricintheworld](https://github.com/ricintheworld)

</div>
