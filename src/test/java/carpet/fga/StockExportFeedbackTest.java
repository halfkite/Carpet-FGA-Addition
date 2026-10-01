//#if MC == 26.3
//$$ package carpet.fga;
//$$
//$$ import net.minecraft.network.chat.Component;
//$$ import io.netty.buffer.Unpooled;
//$$ import io.netty.handler.codec.EncoderException;
//$$ import net.minecraft.SharedConstants;
//$$ import net.minecraft.core.RegistryAccess;
//$$ import net.minecraft.network.RegistryFriendlyByteBuf;
//$$ import net.minecraft.network.chat.contents.TranslatableContents;
//$$ import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
//$$ import net.minecraft.server.Bootstrap;
//$$ import org.junit.jupiter.api.BeforeAll;
//$$ import org.junit.jupiter.api.Test;
//$$
//$$ import java.nio.file.Path;
//$$ import java.util.List;
//$$ import static org.junit.jupiter.api.Assertions.*;
//$$
//$$ final class StockExportFeedbackTest {
//$$     @BeforeAll static void bootstrap() {
//$$         SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
//$$     }
//$$     @Test void originalPathArgumentReproducesPacketEncodingFailure() {
//$$         var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
//$$         try {
//$$             var invalid = Component.translatableWithFallback("carpet.fga.fake_player_item_sort.stock_exported",
//$$                     "Full stock list written to: %s",Path.of("world","库存.txt"));
//$$             assertThrows(EncoderException.class,()->ClientboundSystemChatPacket.STREAM_CODEC.encode(buffer,
//$$                     new ClientboundSystemChatPacket(invalid,false)));
//$$         } finally { buffer.release(); }
//$$     }
//$$     @Test void exportFeedbackRoundTripsAsSystemChatInAllServerLanguages() {
//$$             for (String language : List.of("zh_cn","en_us","zh_tw")) {
//$$                 for (Path path : List.of(Path.of(".","world",".","config","carpetfgaaddition","exports","库存 100%.txt"),
//$$                         Path.of("D:/我的世界/服务器/world/config/carpetfgaaddition/exports/playersort-stock.txt"))) {
//$$                     var feedback = FakePlayerItemSortCommand.stockExportFeedback(path,language);
//$$                     var contents = (TranslatableContents)feedback.getContents();
//$$                     assertEquals(path.toString(),contents.getArgs()[0]);
//$$                     assertEquals(FGAText.formatForLanguage(language,contents.getKey(),path.toString()),contents.getFallback());
//$$                     var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
//$$                     try {
//$$                         ClientboundSystemChatPacket.STREAM_CODEC.encode(buffer,new ClientboundSystemChatPacket(feedback,false));
//$$                         var decoded = ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer);
//$$                         assertEquals(feedback,decoded.content());
//$$                         assertFalse(decoded.overlay());
//$$                         assertEquals(0x55FF55,decoded.content().getStyle().getColor().getValue());
//$$                     } finally { buffer.release(); }
//$$                 }
//$$             }
//$$     }
//$$ }
//#endif
