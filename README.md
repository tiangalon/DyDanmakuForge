# DyDanmaku Forge 1.21.1

在 Minecraft 客户端聊天框中显示抖音直播间消息。本分支面向 Minecraft 1.21.1，并与 DyDanmaku Fabric `0.1.7` 的功能保持一致。

## 环境

- Minecraft 1.21.1
- Forge 52.1.16
- Java 21

## 使用

将 `DyDanmaku-forge-1.21.1-0.1.7.2.jar` 放入客户端的 `mods` 目录。进入游戏后按 `F7` 打开控制界面，可输入抖音直播间 URL 或房间号并连接；输入框默认填入示例房间号 `594357732923`，可直接替换。

输入可以是 URL 或者房间号。示例 URL：`https://live.douyin.com/594357732923`；示例房间号：`594357732923`。

也可以使用客户端命令：

```text
/dydanmaku connect <直播间 URL 或房间号>
/dydanmaku disconnect
/dydanmaku status
```

控制界面的消息类型复选框默认全部勾选，分别控制消息、入场、统计、点赞、礼物和粉丝团消息。选择会保存到 `DyDanmakuSettings.toml`。也可以使用命令调整过滤：

点击控制界面的“更多设置”可直接在游戏内修改 `DySessionId`，切换弹幕关键词过滤器的关闭、黑名单、白名单模式，以及新增、修改、删除过滤关键词。修改会立即写入配置文件；`DySessionId` 在下次连接直播间时生效。

```text
/dydanmaku filter <chat|member|stats|like|gift|fansclub|all>
/dydanmaku unfilter <chat|member|stats|like|gift|fansclub|all>
/dydanmaku filter status
```

`filter` 表示屏蔽，`unfilter` 表示恢复显示；`all` 表示全部类型。`filter status` 会显示当前允许的消息类型。

首次启动会创建 `config/dydanmaku/DyDanmakuSettings.toml`，可配置 `DySessionId`、消息类型开关、关键词过滤、用户等级过滤和输出模板。

## 构建

PowerShell 构建脚本启动后会询问自定义 Java 路径，直接回车即可自动选择。Java 的选择优先级为：自定义路径 → `JAVA_21_HOME` → `JAVA_HOME` → `Path` 中的 `java`。仅在脚本进程内临时切换 `JAVA_HOME` 和 `Path`，结束后会恢复原值：

```powershell
.\build.ps1

# 完整清理后构建
.\build.ps1 -Clean

# 也可以直接通过参数指定，不再交互询问
.\build.ps1 -Clean -JavaHome 'C:\path\to\jdk-21'
```

环境变量的读取优先级为当前进程、用户环境变量、系统环境变量。脚本会校验所选 Java 是否为 Java 21。构建产物位于：

```text
build/libs/DyDanmaku-forge-1.21.1-0.1.7.2.jar
```

## 说明

- Mod 仅在客户端加载，不要求服务器安装。
- Protobuf、TOML 和所需的 Netty HTTP 类已包含在发行 JAR 中；Nashorn 及其 ASM 依赖复用 Forge 自带版本，避免 Java 模块冲突。
- 抖音网页结构和签名算法可能随平台更新而变化；连接失败时请查看游戏日志中的 `[DyDanmaku]` 信息。
