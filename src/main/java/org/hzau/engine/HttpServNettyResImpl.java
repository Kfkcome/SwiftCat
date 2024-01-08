package org.hzau.engine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.*;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.hzau.Config;
import org.hzau.connector.HttpExchangeResponse;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;

public class HttpServNettyResImpl implements HttpServletResponse {

    final Config config;
    final FullHttpResponse exchange;
    final HttpHeaders headers;

    int status = 200;
    int bufferSize = 1024;
    Boolean callOutput = null;
    ServletOutputStream output;
    PrintWriter writer;

    String contentType;
    String characterEncoding;
    long contentLength = 0;
    Locale locale = null;
    List<Cookie> cookies = null;
    boolean committed = false;

    public HttpServNettyResImpl(Config config, FullHttpResponse exchangeResponse) {
        this.config = config;
        this.exchange = exchangeResponse;
        this.headers = exchangeResponse.headers();
        this.characterEncoding = config.server.responseEncoding;
        this.setContentType("text/html");
    }

    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (callOutput == null) {
            commitHeaders(0);
            ByteBuf content = exchange.content();
            this.output = new NettyServletOutputStream(new ByteBufOutputStream(content));
            this.callOutput = Boolean.TRUE;
            return this.output;
        }
        if (callOutput.booleanValue()) {
            return this.output;
        }
        throw new IllegalStateException("Cannot open output stream when writer is opened.");
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (this.callOutput != null) {
            if (!this.callOutput) {
                return this.writer;
            } else {
                throw new IllegalStateException("Cannot open writer when output stream is opened.");
            }
        }

        this.callOutput = Boolean.FALSE;

        Charset charset;
        try {
            charset = Charset.forName(this.characterEncoding);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            charset = StandardCharsets.UTF_8;
        }

        // 创建一个写入到 Netty HttpResponse 的 PrintWriter
        this.writer = new PrintWriter(new OutputStreamWriter(new ByteBufOutputStream(exchange.content()), charset), true);
        return this.writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
    }

    @Override
    public void setContentLength(int len) {
        this.contentLength = len;
    }

    @Override
    public void setContentLengthLong(long len) {
        this.contentLength = len;
    }

    @Override
    public void setContentType(String type) {
        this.contentType = type;
        if (type.startsWith("text/")) {
            setHeader("Content-Type", contentType + "; charset=" + this.characterEncoding);
        } else {
            setHeader("Content-Type", contentType);
        }
    }

    @Override
    public void setBufferSize(int size) {
        if (this.callOutput != null) {
            throw new IllegalStateException("Output stream or writer is opened.");
        }
        if (size < 0) {
            throw new IllegalArgumentException("Invalid size: " + size);
        }
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (this.callOutput == null) {
            throw new IllegalStateException("Output stream or writer is not opened.");
        }
        if (this.callOutput.booleanValue()) {
            this.output.flush();
        } else {
            this.writer.flush();
        }
    }

    @Override
    public void resetBuffer() {
        checkNotCommitted();
    }

    @Override
    public boolean isCommitted() {
        return this.committed;
    }

    @Override
    public void reset() {
        checkNotCommitted();
        this.status = 200;
        this.headers.clear();
    }

    @Override
    public void setLocale(Locale locale) {
        checkNotCommitted();
        this.locale = locale;
    }

    @Override
    public Locale getLocale() {
        return this.locale == null ? Locale.getDefault() : this.locale;
    }

    @Override
    public void addCookie(Cookie cookie) {
        checkNotCommitted();
        if (this.cookies == null) {
            this.cookies = new ArrayList<>();
        }
        this.cookies.add(cookie);
    }

    @Override
    public String encodeURL(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public String encodeRedirectURL(String url) {
        // no need to append session id:
        return url;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        checkNotCommitted();
        this.status = sc;
        commitHeaders(-1);
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, "Error");
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        checkNotCommitted();
        this.status = 302;
        exchange.setStatus(HttpResponseStatus.valueOf(this.status));
        exchange.headers().set("Location", location);
        this.headers.set("Location", location);
        commitHeaders(-1);
    }

    @Override
    public void setStatus(int sc) {
        checkNotCommitted();
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    // header operations //////////////////////////////////////////////////////

    @Override
    public boolean containsHeader(String name) {
        return this.headers.contains(name);
    }

    @Override
    public String getHeader(String name) {
        return this.headers.get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> hs = this.headers.getAll(name);
        if (hs == null) {
            return List.of();
        }
        return hs;
    }

    @Override
    public Collection<String> getHeaderNames() {
        return Collections.unmodifiableSet(this.headers.names());
    }

    @Override
    public void setDateHeader(String name, long date) {
        checkNotCommitted();
        this.headers.set(name, date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        checkNotCommitted();
        this.headers.add(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
        checkNotCommitted();
        this.headers.set(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        checkNotCommitted();
        this.headers.add(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        checkNotCommitted();
        this.headers.setInt(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        checkNotCommitted();
        this.headers.addInt(name, value);
    }

    void commitHeaders(long length) throws IOException {
        if (length >= 0) {
            this.headers.set(HttpHeaderNames.CONTENT_LENGTH, length);
        }
        exchange.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
        // 如果有其他需要提交的头部，也应该在这里处理
        this.committed = true;
    }

    public void cleanup() throws IOException {
        if (!this.committed) {
            commitHeaders(-1);
        }
        if (this.callOutput != null) {
            if (this.callOutput.booleanValue()) {
                this.output.close();
            } else {
                this.writer.close();
            }
        }
    }

    // check if not committed:
    void checkNotCommitted() {
        if (this.committed) {
            throw new IllegalStateException("Response is committed.");
        }
    }


    // 内部类实现 ServletOutputStream
    private static class NettyServletOutputStream extends ServletOutputStream {

        private final ByteBufOutputStream outputStream;

        public NettyServletOutputStream(ByteBufOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void write(int b) throws IOException {
            // 将字节写入 nettyResponse.content()
            // 实现将字节写入 ServletOutputStream 的逻辑
            outputStream.write(b);
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

        }
    }
}
