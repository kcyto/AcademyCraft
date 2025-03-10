package cn.academy.network;

import cn.academy.ACConfig;
import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Created by Paindar on 2016/8/31.
 */
public class MessageConfig implements IMessage
{
    public static class Handler implements IMessageHandler<MessageConfig, IMessage>
    {
        @Override
        public IMessage onMessage(MessageConfig message, MessageContext ctx)
        {
            if (ctx.side == Side.CLIENT)
            {
                ACConfig.updateConfig(message.config);
            }

            return null;
        }
    }
    public Config config;

    @Override
    public void fromBytes(ByteBuf byteBuf)
    {
        File file=new File("cache/academy-craft-data.conf");
        if(!file.exists())
            (new File(file.getParent())).mkdirs();
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            String string = byteBuf.toString(Charsets.UTF_8);
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(string);
            bw.close();
            config=ConfigFactory.parseFile(file);
            file.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void toBytes(ByteBuf byteBuf)
    {
        String string=config.root().render();

        byteBuf.writeBytes(string.getBytes(StandardCharsets.UTF_8));
    }
}