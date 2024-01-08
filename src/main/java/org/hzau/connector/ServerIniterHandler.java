package org.hzau.connector;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.hzau.Config;
import org.hzau.engine.NormalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;



/**
 * descripiton: 服务器初始化
 *
 * @author: www.iknowba.cn
 * @date: 2018/3/23
 * @time: 15:46
 * @modifier:
 * @since:
 */
public class ServerIniterHandler extends ChannelInitializer<SocketChannel> {
    Config config;
    String webRoot;
    Executor executor;
    ClassLoader classLoader;
    List<Class<?>> autoScannedClasses;
    List<NormalContext> servletContext = new ArrayList<>();
    ServerIniterHandler(List<NormalContext> servletContext ,Config config,String webRoot, Executor executor, ClassLoader classLoader, List<Class<?>> autoScannedClasses){
        this.config=config;
        this.servletContext=servletContext;
        this.webRoot=webRoot;
        this.executor=executor;
        this.classLoader=classLoader;
        this.autoScannedClasses=autoScannedClasses;
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
        pipeline.addLast("httpAggregator",new HttpObjectAggregator(512*1024));
        //聊天服务通道处理
        pipeline.addLast("chat", new ServerHandler(servletContext,config,webRoot,executor,classLoader,autoScannedClasses));
    }
}
