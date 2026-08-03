package top.tiangalon.dydanmakuforge;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import top.tiangalon.dydanmakuforge.client.DyDanmakuForgeClient;

@Mod(value = DyDanmakuForge.MOD_ID, dist = Dist.CLIENT)
public final class DyDanmakuForge {
    public static final String MOD_ID = "dydanmakuforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DyDanmakuForge(IEventBus modEventBus) {
        LOGGER.info("[DyDanmaku]正在加载 NeoForge 客户端模组");
        DyDanmakuForgeClient.register(modEventBus);
    }
}
