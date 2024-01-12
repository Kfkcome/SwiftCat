package org.hzau.connector;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.hzau.Config;
import org.hzau.engine.NormalContext;


import java.util.List;


public class ServerIniterHandler extends ChannelInitializer<SocketChannel> {
    Config config;
    List<NormalContext> servletContext;

    ServerIniterHandler(List<NormalContext> servletContext, Config config) {
        this.servletContext = servletContext;
        this.config = config;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        //管道注册handler
        ChannelPipeline pipeline = socketChannel.pipeline();
//        //编码通道处理
//        pipeline.addLast("decode", new HttpResponseEncoder());
//        //转码通道处理
//        pipeline.addLast("encode", new HttpRequestDecoder());
        pipeline.addLast(new HttpServerCodec());// http 编解码
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(512 * 1024));
        //聊天服务通道处理
        pipeline.addLast("chat", new ServerHandler(servletContext, config));
    }
}
