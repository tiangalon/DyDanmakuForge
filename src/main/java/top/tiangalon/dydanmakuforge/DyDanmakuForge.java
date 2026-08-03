package top.tiangalon.dydanmakuforge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import top.tiangalon.dydanmakuforge.client.DyDanmakuForgeClient;

@Mod(DyDanmakuForge.MOD_ID)
public final class DyDanmakuForge {
    public static final String MOD_ID = "dydanmakuforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DyDanmakuForge() {
        LOGGER.info("[DyDanmaku]正在加载 Forge 客户端模组");
        DyDanmakuForgeClient.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
