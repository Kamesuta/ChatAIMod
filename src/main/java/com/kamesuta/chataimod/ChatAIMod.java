package com.kamesuta.chataimod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("chataimod")
public class ChatAIMod {
    // ロガー
    public static final Logger LOGGER = LogManager.getLogger();

    public ChatAIMod() {
        // ログ
        LOGGER.info("Hello from ChatAIMod!");

        // クライアント初期化時イベント
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // イベント登録
        MinecraftForge.EVENT_BUS.register(new ChatAIListener());
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // コンフィグの読み込み
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_CONFIG);
        Config.loadConfig(FMLPaths.CONFIGDIR.get().resolve("chataimod.toml").toString());
    }
}
