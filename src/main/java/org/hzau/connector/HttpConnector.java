package org.hzau.connector;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.hzau.Config;
import org.hzau.engine.HttpServletRequestImpl;
import org.hzau.engine.HttpServletResponseImpl;
import org.hzau.engine.NormalContext;
import org.hzau.engine.lifecycle.LifecycleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

public class HttpConnector implements HttpHandler, AutoCloseable {

    final Logger logger = LoggerFactory.getLogger(getClass());

    final Config config;
    final ClassLoader classLoader;
    final NormalContext servletContext;
    final HttpServer httpServer;
    final Duration stopDelay = Duration.ofSeconds(5);

    public HttpConnector(Config config, String webRoot, Executor executor, ClassLoader classLoader, List<Class<?>> autoScannedClasses) throws IOException {
        logger.info("starting SwiftCat http server at {}:{}...", config.server.host, config.server.port);
        this.config = config;
        this.classLoader = classLoader;

        // init servlet context:
        Thread.currentThread().setContextClassLoader(this.classLoader);
        //TODO:为什么只能注册一个Context 应该配置几个context然后注册几个context
        // 并且如果不是放在一个目录的servlet应该注册为不同Context 意思就是一个webapp目录一个context
        NormalContext ctx=null;
        try {
            ctx = new NormalContext(classLoader, config, webRoot,autoScannedClasses);
            ctx.init();//TODO:为什么初始化的时候就注册servlet
            ctx.start();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);//TODO:处理加载context加载异常
        }
        this.servletContext = ctx;
        Thread.currentThread().setContextClassLoader(null);

        // start http server: TODO: 为什么要用sun的httpserver
        this.httpServer = HttpServer.create(new InetSocketAddress(config.server.host, config.server.port), config.server.backlog);
        this.httpServer.createContext("/", this);
        this.httpServer.setExecutor(executor);
        this.httpServer.start();//TODO:init和start分开
        logger.info("SwiftCat http server started at {}:{}...", config.server.host, config.server.port);
    }

    @Override
    public void close() {
        try {
            this.servletContext.destroy();
        } catch (LifecycleException e) {
            throw new RuntimeException(e);
        }
        this.httpServer.stop((int) this.stopDelay.toSeconds());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var adapter = new HttpExchangeAdapter(exchange);
        var response = new HttpServletResponseImpl(this.config, adapter);
        var request = new HttpServletRequestImpl(this.config, this.servletContext, adapter, response);
        // process:
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            //调用context模块的process方法 calling ContextImpl.process
            //TODO: 为什么要用HttpHandler 为什么没有Host这一层
            this.servletContext.process(request, response);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(null);
            response.cleanup();
        }
    }
}
