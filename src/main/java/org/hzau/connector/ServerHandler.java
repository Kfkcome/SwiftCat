package org.hzau.connector;


import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.hzau.Config;
import org.hzau.engine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;


public class ServerHandler extends ChannelInboundHandlerAdapter {
    private Config config;
    final Logger logger = LoggerFactory.getLogger(getClass());
    List<NormalContext> servletContext = new ArrayList<>();


    ServerHandler(List<NormalContext> servletContext, Config config) {
        this.config = config;
        this.servletContext = servletContext;


    }


    /**
     * 所有的活动用户
     */
    public static final ChannelGroup group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    /**
     * 读取消息通道
     *
     * @param context
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext context, Object msg)
            throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            logger.error("不是FullHttpRequest对象 出现问题");
            return;
        }
        //封装成adopter
        Channel channel = context.channel();
        FullHttpRequest req = (FullHttpRequest) msg;
        ByteBuf msg2 = Unpooled.buffer(0);
        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                msg2);

        System.out.println("[" + channel.remoteAddress() + "]: " + msg.toString() + "\n");
        HttpServNettyResImpl response = new HttpServNettyResImpl(config, res);
        String ip = channel.remoteAddress().toString();
        String ip_true = ip.split("/")[1].split(":")[0];
        Integer port = Integer.valueOf(ip.split("/")[1].split(":")[1]);
        ip = channel.localAddress().toString();
        String ip_true_local = ip.split("/")[1].split(":")[0];
        Integer port_true_local = Integer.valueOf(ip.split("/")[1].split(":")[1]);
        HttpServNettyReqImpl request = new HttpServNettyReqImpl(config, req, response, ip, port, port_true_local, ip_true_local);
        request.mappingData.requestPath = req.uri();
        request.mappingData.contexts = this.servletContext;

        for (Config.Server.Context context1 : config.server.contexts) {
            //FIXME:自带的defaultServlet无法匹配的到config中的context
            if (request.mappingData.requestPath.split("/").length >= 1 && context1.path.equals("/" + request.mappingData.requestPath.split("/")[1])) {
                request.mappingData.info = context1;
                break;
            }
        }
        //获取resquest和response后开始处理
        for (NormalContext context1 : request.mappingData.contexts) {
            //处理默认的
            if (request.mappingData.requestPath.split("/").length == 1 && context1.getContextPath().equals("/")) {
                request.mappingData.context = context1;
                break;
            }
            if (request.mappingData.requestPath.split("/").length >= 1 && context1.getContextPath().equals("/" + request.mappingData.requestPath.split("/")[1])) {
                request.mappingData.context = context1;
                break;
            }


        }

        if (request.mappingData.context == null) {
            logger.error("找不到对应的url");
            response.sendError(404);
            return;
        }
        // process:
        try {
            Thread.currentThread().setContextClassLoader(request.mappingData.context.getClassLoader());
            //调用context模块的process方法 calling ContextImpl.process
            request.mappingData.context.process(request, response);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(null);
            //response.setHeader(HttpHeaderNames.CONTENT_TYPE.toString(), "text/html; charset=UTF-8");
            //response.setHeader(HttpHeaderNames.CONTENT_LENGTH.toString(), String.valueOf(res.content().readableBytes()));

            context.writeAndFlush(response);
            response.cleanup();
        }


        context.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);

    }

    /**
     * 处理新加的消息通道
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        for (Channel ch : group) {
            if (ch == channel) {
                ch.writeAndFlush("[" + channel.remoteAddress() + "] coming");
            }
        }
        group.add(channel);
    }

    /**
     * 处理退出消息通道
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        for (Channel ch : group) {
            if (ch == channel) {
                ch.writeAndFlush("[" + channel.remoteAddress() + "] leaving");
            }
        }
        group.remove(channel);
    }

    /**
     * 在建立连接时发送消息
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        boolean active = channel.isActive();
        if (active) {
            System.out.println("[" + channel.remoteAddress() + "] is online");
        } else {
            System.out.println("[" + channel.remoteAddress() + "] is offline");
        }
        ctx.writeAndFlush("[server]: welcome");
    }

    /**
     * 退出时发送消息
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (!channel.isActive()) {
            System.out.println("[" + channel.remoteAddress() + "] is offline");
        } else {
            System.out.println("[" + channel.remoteAddress() + "] is online");
        }
    }

    /**
     * 异常捕获
     *
     * @param ctx
     * @param e
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("[" + channel.remoteAddress() + "] leave the room");
        ctx.close().sync();
    }

}
