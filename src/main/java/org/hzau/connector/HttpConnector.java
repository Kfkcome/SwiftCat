package org.hzau.connector;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.hzau.Config;
import org.hzau.engine.HttpServletRequestImpl;
import org.hzau.engine.HttpServletResponseImpl;
import org.hzau.engine.ServletContextImpl;
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
    final ServletContextImpl servletContext;
    final HttpServer httpServer;
    final Duration stopDelay = Duration.ofSeconds(5);

    public HttpConnector(Config config, String webRoot, Executor executor, ClassLoader classLoader, List<Class<?>> autoScannedClasses) throws IOException {
        logger.info("starting SwiftCat http server at {}:{}...", config.server.host, config.server.port);
        this.config = config;
        this.classLoader = classLoader;

        // init servlet context:
        Thread.currentThread().setContextClassLoader(this.classLoader);
        ServletContextImpl ctx = new ServletContextImpl(classLoader, config, webRoot);
        ctx.initialize(autoScannedClasses);
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
        this.servletContext.destroy();
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
