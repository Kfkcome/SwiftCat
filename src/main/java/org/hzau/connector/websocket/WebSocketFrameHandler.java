package org.hzau.connector.websocket;

import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.GlobalEventExecutor;



        import io.netty.channel.ChannelHandlerContext;
        import io.netty.channel.SimpleChannelInboundHandler;
        import io.netty.handler.codec.http.websocketx.*;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private FrameListener frameListener;

    private static final ChannelGroup clients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本帧
            String request = ((TextWebSocketFrame) frame).text();
            System.out.println("Received: " + request);
            broadcast(new TextWebSocketFrame(request));
        } else if (frame instanceof BinaryWebSocketFrame) {
            // 处理二进制帧（此处未实现具体逻辑）
        } else if (frame instanceof CloseWebSocketFrame) {
            // 处理关闭帧
            ctx.close();
        } else if (frame instanceof PingWebSocketFrame) {
            // 处理 Ping 帧
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
        // 其他帧类型的处理逻辑（如果需要）
    }


    // 用于设置帧监听器
    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        clients.add(ctx.channel());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        clients.remove(ctx.channel());
    }

    // 广播消息到所有连接的客户端
    private void broadcast(TextWebSocketFrame frame) {
        for (var ch : clients) {
            ch.writeAndFlush(frame.copy());
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (frameListener != null) {
            frameListener.onError(cause);
        }
        ctx.close();
    }

    //使得外部代码能够注册回调来接收帧和错误通知
    public interface FrameListener {
        void onFrame(WebSocketFrame frame);
        void onError(Throwable throwable);
    }
}

