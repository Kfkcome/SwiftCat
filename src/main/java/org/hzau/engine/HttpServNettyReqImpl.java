package org.hzau.engine;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.http.Cookie;
import org.hzau.Config;
import org.hzau.connector.MappingData;
import org.hzau.engine.support.Attributes;
import org.hzau.engine.support.NettyParameters;
import org.hzau.utils.HttpUtils;

import java.io.*;
import java.security.Principal;
import java.util.*;

public class HttpServNettyReqImpl implements HttpServletRequest {

    final Config config;

    public final MappingData mappingData = new MappingData();
    final FullHttpRequest exchange;
    final HttpServletResponse response;
    final String method;
    final HttpHeaders headers;
    final NettyParameters parameters;
    final String remoteIP;
    final int remotePort;
    final int localPort;
    final String localIP;

    String characterEncoding;
    int contentLength = 0;

    String requestId = null;
    Attributes attributes = new Attributes();

    Boolean inputCalled = null;

    public HttpServNettyReqImpl(Config config, FullHttpRequest exchange, HttpServletResponse response, String remoteIP, int remotePort, int localPort, String localIP) {
        this.config = config;
        this.exchange = exchange;
        this.response = response;
        this.characterEncoding = config.server.requestEncoding;
        this.method = exchange.method().name();
        this.headers = exchange.headers();
        this.parameters = new NettyParameters(exchange, this.characterEncoding);
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.localPort = localPort;
        this.localIP = localIP;
        if ("POST".equals(this.method) || "PUT".equals(this.method) || "DELETE".equals(this.method) || "PATCH".equals(this.method)) {
            this.contentLength = getIntHeader("Content-Length");
        }
    }

    @Override
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    @Override
    public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
        this.characterEncoding = env;
        this.parameters.setCharset(env);
    }

    @Override
    public int getContentLength() {
        return this.contentLength;
    }

    @Override
    public long getContentLengthLong() {
        return this.contentLength;
    }

    @Override
    public String getContentType() {
        return getHeader("Content-Type");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
//        if (this.inputCalled == null) {
//            this.inputCalled = Boolean.TRUE;
//            return new ServletInputStreamImpl(this.exchange.getRequestBody());
//        }
//        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
        ByteBuf content = ((HttpContent) exchange).content();
        return new NettyServletInputStream(new ByteBufInputStream(content));
    }

    @Override
    public BufferedReader getReader() throws IOException {
//        if (this.inputCalled == null) {
//            this.inputCalled = Boolean.FALSE;
//            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.exchange.getRequestBody()), this.characterEncoding));
//        }
//        throw new IllegalStateException("Cannot reopen input stream after " + (this.inputCalled ? "getInputStream()" : "getReader()") + " was called.");
        ByteBuf content = ((HttpContent) exchange).content();
        return new BufferedReader(new InputStreamReader(new ByteBufInputStream(content)));
    }

    @Override
    public String getParameter(String name) {
        return this.parameters.getParameter(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return this.parameters.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return this.parameters.getParameterValues(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String> parameterMap = parameters.getParameterMap();
        Map<String, String[]> map = new HashMap<>();
        parameterMap.forEach((k, v) -> {
            String[] strings = new String[1];
            strings[0] = v;
            map.put(k, strings);
        });
        return map;
    }

    @Override
    public String getProtocol() {
        return "HTTP/1.1";
    }

    @Override
    public String getScheme() {
        String header = "http";
        String forwarded = config.server.forwardedHeaders.forwardedProto;
        if (!forwarded.isEmpty()) {
            String forwardedHeader = getHeader(forwarded);
            if (forwardedHeader != null) {
                header = forwardedHeader;
            }
        }
        return header;
    }

    @Override
    public String getServerName() {
        String header = getHeader("Host");
        String forwarded = config.server.forwardedHeaders.forwardedHost;
        if (!forwarded.isEmpty()) {
            String forwardedHeader = getHeader(forwarded);
            if (forwardedHeader != null) {
                header = forwardedHeader;
            }
        }
//        if (header == null) {
//            InetSocketAddress address = this.exchange.getLocalAddress();
//            header = address.getHostString();
//        }
        return header;
    }

    @Override
    public int getServerPort() {
        return localPort;
    }

    @Override
    public Locale getLocale() {
        String langs = getHeader("Accept-Language");
        if (langs == null) {
            return HttpUtils.DEFAULT_LOCALE;
        }
        return HttpUtils.parseLocales(langs).get(0);
    }

    @Override
    public Enumeration<Locale> getLocales() {
        String langs = getHeader("Accept-Language");
        if (langs == null) {
            return Collections.enumeration(HttpUtils.DEFAULT_LOCALES);
        }
        return Collections.enumeration(HttpUtils.parseLocales(langs));
    }

    @Override
    public boolean isSecure() {
        return "https".equals(getScheme().toLowerCase());
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        // do not support request dispatcher:
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        return this.mappingData.context;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async is not supported.");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getRequestId() {
        if (this.requestId == null) {
            this.requestId = UUID.randomUUID().toString();
        }
        return this.requestId;
    }

    @Override
    public String getProtocolRequestId() {
        // empty string for http 1.x:
        return "";
    }

    @Override
    public ServletConnection getServletConnection() {
        throw new UnsupportedOperationException("getServletConnection");
    }

    @Override
    public String getAuthType() {
        // not support auth:
        return null;
    }

    @Override
    public Cookie[] getCookies() {
        String cookieValue = this.getHeader("Cookie");
        return HttpUtils.parseCookies(cookieValue);
    }

    @Override
    public String getMethod() {
        return this.method;
    }

    @Override
    public String getPathInfo() {
        return null;
    }

    @Override
    public String getPathTranslated() {
        return this.mappingData.context.getRealPath(getRequestURI());
    }

    @Override
    public String getContextPath() {
        // root contexts path:
        return this.mappingData.context.getContextPath();
    }

    @Override
    public String getQueryString() {
        //TODO:get the true query string
        return null;
    }

    @Override
    public String getRemoteUser() {
        // not support auth:
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        // not support auth:
        return false;
    }

    @Override
    public Principal getUserPrincipal() {
        // not support auth:
        return null;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String getRequestURI() {
        return this.exchange.uri();
    }

    @Override
    public StringBuffer getRequestURL() {
        StringBuffer sb = new StringBuffer(128);
        sb.append(getScheme()).append("://").append(getServerName()).append(':').append(getServerPort()).append(getRequestURI());
        return sb;
    }

    @Override
    public String getServletPath() {
        return getRequestURI();
    }

    @Override
    public HttpSession getSession(boolean create) {
        String sessionId = null;
        Cookie[] cookies = getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (mappingData.info.sessionCookieName.equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        if (sessionId == null && !create) {
            return null;
        }
        if (sessionId == null) {
            if (this.response.isCommitted()) {
                throw new IllegalStateException("Cannot create session for response is commited.");
            }
            sessionId = UUID.randomUUID().toString();
            // set cookie:
            String cookieValue = config.server.contexts.get(0).sessionCookieName + "=" + sessionId + "; Path=/; SameSite=Strict; HttpOnly";
            this.response.addHeader("Set-Cookie", cookieValue);
        }
        return this.mappingData.context.sessionManager.getSession(sessionId);
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    @Override
    public String changeSessionId() {
        throw new UnsupportedOperationException("changeSessionId() is not supported.");
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return true;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
        // not support auth:
        return false;
    }

    @Override
    public void login(String username, String password) throws ServletException {
        // not support auth:
    }

    @Override
    public void logout() throws ServletException {
        // not support auth:
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        // not suport multipart:
        return List.of();
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        // not suport multipart:
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
        // not suport websocket:
        return null;
    }

    // header operations //////////////////////////////////////////////////////

    @Override
    public long getDateHeader(String name) {
        //TODO:获取数据header
        return 0;

//        return this.headers.get();
    }

    @Override
    public String getHeader(String name) {
        return this.headers.get(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> hs = this.headers.getAll(name);
        if (hs == null) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(hs);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(this.headers.names());
    }

    @Override
    public int getIntHeader(String name) {
        return this.headers.getInt(name);
    }

    // attribute operations ///////////////////////////////////////////////////

    @Override
    public Object getAttribute(String name) {
        return this.attributes.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return this.attributes.getAttributeNames();
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else {
            Object oldValue = this.attributes.setAttribute(name, value);
            if (oldValue == null) {
                this.mappingData.context.invokeServletRequestAttributeAdded(this, name, value);
            } else {
                this.mappingData.context.invokeServletRequestAttributeReplaced(this, name, value);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object oldValue = this.attributes.removeAttribute(name);
        this.mappingData.context.invokeServletRequestAttributeRemoved(this, name, oldValue);
    }

    // address and port ///////////////////////////////////////////////////////

    @Override
    public String getRemoteAddr() {
//        String addr = null;
//        String forwarded = config.server.forwardedHeaders.forwardedFor;
//        if (forwarded != null && !forwarded.isEmpty()) {
//            String forwardedHeader = getHeader(forwarded);
//            if (forwardedHeader != null) {
//                int n = forwardedHeader.indexOf(',');
//                addr = n < 0 ? forwardedHeader : forwardedHeader.substring(n);
//            }
//        }
//        if (addr == null) {
//            InetSocketAddress address = this.exchange.();
//            addr = address.getHostString();
//        }
        return remoteIP;
    }

    @Override
    public String getRemoteHost() {
        // avoid DNS lookup by IP:
        return getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
//        InetSocketAddress address = this.exchange.getRemoteAddress();
        return remotePort;
    }

    @Override
    public String getLocalAddr() {
//        InetSocketAddress address = this.exchange.getLocalAddress();
        return localIP;
    }

    @Override
    public String getLocalName() {
        // avoid DNS lookup:
        return getLocalAddr();
    }

    @Override
    public int getLocalPort() {
//        InetSocketAddress address = this.exchange.getLocalAddress();
        return localPort;
    }

    @Override
    public String toString() {
        return String.format("HttpServletRequestImpl@%s[%s:%s]", Integer.toHexString(hashCode()), getMethod(), getRequestURI());
    }


    private static class NettyServletInputStream extends ServletInputStream {

        private final ByteBufInputStream inputStream;

        public NettyServletInputStream(ByteBufInputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public boolean isFinished() {
            try {
                return inputStream.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // 不支持异步处理，留空即可
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return inputStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return inputStream.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}

