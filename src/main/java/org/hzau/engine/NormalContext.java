package org.hzau.engine;


import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import jakarta.servlet.http.*;
import org.hzau.Config;
import org.hzau.engine.lifecycle.LifecycleException;
import org.hzau.engine.lifecycle.LifecycleMBeanBase;
import org.hzau.engine.lifecycle.LifecycleState;
import org.hzau.engine.mapping.FilterMapping;
import org.hzau.engine.mapping.ServletMapping;
import org.hzau.engine.servlet.DefaultServlet;
import org.hzau.engine.support.Attributes;
import org.hzau.utils.AnnoUtils;
import org.hzau.utils.HtmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


public class NormalContext extends LifecycleMBeanBase implements ServletContext {

    private final String name;
    private final String path;
    private final boolean fileListings;
    private final String virtualServerName;
    private final String sessionCookieName;
    private final Integer sessionTimeout;
    final Logger logger = LoggerFactory.getLogger(getClass());

    final ClassLoader classLoader;
    final Config config;
    int configIndex = 0;
    // web root dir:
    final Path webRoot;
    // session manager:
    final SessionManager sessionManager;

    private boolean initialized = false;

    // servlet contexts attributes:
    private Attributes attributes = new Attributes(true);
    private SessionCookieConfig sessionCookieConfig;

    private List<Class<?>> autoScannedClasses;

    private Map<String, ServletRegistrationImpl> servletRegistrations = new HashMap<>();
    private Map<String, FilterRegistrationImpl> filterRegistrations = new HashMap<>();

    final Map<String, Servlet> nameToServlets = new HashMap<>();
    final Map<String, Filter> nameToFilters = new HashMap<>();

    final List<ServletMapping> servletMappings = new ArrayList<>();
    final List<FilterMapping> filterMappings = new ArrayList<>();
    Servlet defaultServlet;

    private List<ServletContextListener> servletContextListeners = null;
    private List<ServletContextAttributeListener> servletContextAttributeListeners = null;
    private List<ServletRequestListener> servletRequestListeners = null;
    private List<ServletRequestAttributeListener> servletRequestAttributeListeners = null;
    private List<HttpSessionAttributeListener> httpSessionAttributeListeners = null;
    private List<HttpSessionListener> httpSessionListeners = null;

    public NormalContext(int configIndex, String name, String path, boolean fileListings, String virtualServerName, String sessionCookieName, Integer sessionTimeout, ClassLoader classLoader, Config config, String webRoot, List<Class<?>> autoScannedClasses) throws LifecycleException {
        this.configIndex = configIndex;
        this.name = name;
        this.path = path;
        this.fileListings = fileListings;
        this.virtualServerName = virtualServerName;
        this.sessionCookieName = sessionCookieName;
        this.sessionTimeout = sessionTimeout;

        this.classLoader = classLoader;
        this.config = config;
        this.sessionCookieConfig = new SessionCookieConfigImpl(config);
        this.webRoot = Paths.get(".\\webapps\\"+webRoot).normalize().toAbsolutePath();
        this.autoScannedClasses = autoScannedClasses;
        this.sessionManager = new SessionManager(this, config.server.contexts.get(0).sessionTimeout);
        logger.info("set web root: {}", this.webRoot);

    }

    //TODO:为什么没有责任链
    public void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        // search servlet:
        Servlet servlet = this.defaultServlet;
        if (!"/".equals(path)) {
            for (ServletMapping mapping : this.servletMappings) {
                if (mapping.matches("/" + path.split("/")[2])) {//TODO:应该在MappingData中就匹配好
                    servlet = mapping.servlet;
                    break;
                }
            }
        }
        if (servlet == null) {
            // 404 Not Found:
            PrintWriter pw = response.getWriter();
            pw.write("<h1>404 Not Found</h1><p>No mapping for URL: " + HtmlUtils.encodeHtml(path) + "</p>");
            pw.flush();
            return;
        }
        // search filter:
        List<Filter> enabledFilters = new ArrayList<>();
        for (FilterMapping mapping : this.filterMappings) {
            if (mapping.matches(path)) {
                enabledFilters.add(mapping.filter);
            }
        }
        //创建并执行过滤链
        Filter[] filters = enabledFilters.toArray(Filter[]::new);
        logger.atDebug().log("process {} by filter {}, servlet {}", path, Arrays.toString(filters), servlet);
        FilterChain chain = new FilterChainImpl(filters, servlet);//调用servlet

        try {
            this.invokeServletRequestInitialized(request);
            chain.doFilter(request, response);
        } catch (ServletException e) {
            logger.error(e.getMessage(), e);
            throw new IOException(e);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw e;
        } finally {
            this.invokeServletRequestDestroyed(request);
        }
    }

    // invoke listeners ///////////////////////////////////////////////////////

    /**
     * 功能描述：
     *
     * @author kfk
     * @date 2023/12/29
     */
    void invokeServletContextInitialized() {
        logger.debug("invoke ServletContextInitialized: {}", this);
        if (this.servletContextListeners != null) {
            var event = new ServletContextEvent(this);
            for (var listener : this.servletContextListeners) {
                listener.contextInitialized(event);
            }
        }
    }

    void invokeServletContextDestroyed() {
        logger.debug("invoke ServletContextDestroyed: {}", this);
        if (this.servletContextListeners != null) {
            var event = new ServletContextEvent(this);
            for (var listener : this.servletContextListeners) {
                listener.contextDestroyed(event);
            }
        }
    }

    void invokeServletContextAttributeAdded(String name, Object value) {
        logger.debug("invoke ServletContextAttributeAdded: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeServletContextAttributeRemoved(String name, Object value) {
        logger.debug("invoke ServletContextAttributeRemoved: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeServletContextAttributeReplaced(String name, Object value) {
        logger.debug("invoke ServletContextAttributeReplaced: {} = {}", name, value);
        if (this.servletContextAttributeListeners != null) {
            var event = new ServletContextAttributeEvent(this, name, value);
            for (var listener : this.servletContextAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeServletRequestAttributeAdded(HttpServletRequest request, String name, Object value) {
        logger.debug("invoke ServletRequestAttributeAdded: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeServletRequestAttributeRemoved(HttpServletRequest request, String name, Object value) {
        logger.debug("invoke ServletRequestAttributeRemoved: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeServletRequestAttributeReplaced(HttpServletRequest request, String name, Object value) {
        logger.debug("invoke ServletRequestAttributeReplaced: {} = {}, request = {}", name, value, request);
        if (this.servletRequestAttributeListeners != null) {
            var event = new ServletRequestAttributeEvent(this, request, name, value);
            for (var listener : this.servletRequestAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeHttpSessionAttributeAdded(HttpSession session, String name, Object value) {
        logger.debug("invoke HttpSessionAttributeAdded: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeAdded(event);
            }
        }
    }

    void invokeHttpSessionAttributeRemoved(HttpSession session, String name, Object value) {
        logger.debug("invoke ServletContextAttributeRemoved: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeRemoved(event);
            }
        }
    }

    void invokeHttpSessionAttributeReplaced(HttpSession session, String name, Object value) {
        logger.debug("invoke ServletContextAttributeReplaced: {} = {}, session = {}", name, value, session);
        if (this.httpSessionAttributeListeners != null) {
            var event = new HttpSessionBindingEvent(session, name, value);
            for (var listener : this.httpSessionAttributeListeners) {
                listener.attributeReplaced(event);
            }
        }
    }

    void invokeServletRequestInitialized(HttpServletRequest request) {
        logger.debug("invoke ServletRequestInitialized: request = {}", request);
        if (this.servletRequestListeners != null) {
            var event = new ServletRequestEvent(this, request);
            for (var listener : this.servletRequestListeners) {
                listener.requestInitialized(event);
            }
        }
    }

    void invokeServletRequestDestroyed(HttpServletRequest request) {
        logger.debug("invoke ServletRequestDestroyed: request = {}", request);
        if (this.servletRequestListeners != null) {
            var event = new ServletRequestEvent(this, request);
            for (var listener : this.servletRequestListeners) {
                listener.requestDestroyed(event);
            }
        }
    }

    void invokeHttpSessionCreated(HttpSession session) {
        logger.debug("invoke HttpSessionCreated: session = {}", session);
        if (this.httpSessionListeners != null) {
            var event = new HttpSessionEvent(session);
            for (var listener : this.httpSessionListeners) {
                listener.sessionCreated(event);
            }
        }
    }

    void invokeHttpSessionDestroyed(HttpSession session) {
        logger.debug("invoke HttpSessionDestroyed: session = {}", session);
        if (this.httpSessionListeners != null) {
            var event = new HttpSessionEvent(session);
            for (var listener : this.httpSessionListeners) {
                listener.sessionDestroyed(event);
            }
        }
    }

    @Override
    public String getContextPath() {
        // only support root contexts path:
        return path;
    }

    @Override
    public ServletContext getContext(String uripath) {
        if ("".equals(uripath)) {
            return this;
        }
        // all others are not exist:
        return null;
    }

    @Override
    public String getMimeType(String file) {
        return config.server.getMimeType(file);
    }

    @Override
    public Set<String> getResourcePaths(String path) {
        String originPath = path;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Path loc = this.webRoot.resolve(path).normalize();
        if (loc.startsWith(this.webRoot)) {
            if (Files.isDirectory(loc)) {
                try {
                    return Files.list(loc).map(p -> p.getFileName().toString()).collect(Collectors.toSet());
                } catch (IOException e) {
                    logger.warn("list files failed for path: {}", originPath);
                }
            }
        }
        return null;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException {
        String originPath = path;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Path loc = this.webRoot.resolve(path).normalize();
        if (loc.startsWith(this.webRoot)) {
            return URI.create("file://" + loc.toString()).toURL();
        }
        throw new MalformedURLException("Path not found: " + originPath);
    }

    @Override
    public InputStream getResourceAsStream(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Path loc = this.webRoot.resolve(path).normalize();
        if (loc.startsWith(this.webRoot)) {
            if (Files.isReadable(loc)) {
                try {
                    return new BufferedInputStream(new FileInputStream(loc.toFile()));
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return null;
    }

    @Override
    public String getRealPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Path loc = this.webRoot.resolve(path).normalize();
        if (loc.startsWith(this.webRoot)) {
            return loc.toString().split("\\.")[0];
        }
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        // do not support request dispatcher:
        return null;
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name) {
        // do not support request dispatcher:
        return null;
    }

    @Override
    public void log(String msg) {
        logger.info(msg);
    }

    @Override
    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public String getServerInfo() {
        return this.config.server.name;
    }

    @Override
    public String getInitParameter(String name) {
        // no init parameters:
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        // no init parameters:
        return Collections.emptyEnumeration();
    }

    @Override
    public boolean setInitParameter(String name, String value) {
        throw new UnsupportedOperationException("setInitParameter");
    }

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
            Object old = this.attributes.setAttribute(name, value);
            if (old == null) {
                this.invokeServletContextAttributeAdded(name, value);
            } else {
                this.invokeServletContextAttributeReplaced(name, value);
            }
        }
    }

    @Override
    public void removeAttribute(String name) {
        Object old = this.attributes.removeAttribute(name);
        this.invokeServletContextAttributeRemoved(name, old);
    }

    @Override
    public String getServletContextName() {
        return this.config.server.contexts.get(configIndex).name;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, String className) {
        checkNotInitialized("addServlet");
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class name is null or empty.");
        }
        Servlet servlet = null;
        try {
            Class<? extends Servlet> clazz = createInstance(className);
            servlet = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addServlet(name, servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, Class<? extends Servlet> clazz) {
        checkNotInitialized("addServlet");
        if (clazz == null) {
            throw new IllegalArgumentException("class is null.");
        }
        Servlet servlet = null;
        try {
            servlet = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addServlet(name, servlet);
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String name, Servlet servlet) {
        checkNotInitialized("addServlet");
        if (name == null) {
            throw new IllegalArgumentException("name is null.");
        }
        if (servlet == null) {
            throw new IllegalArgumentException("servlet is null.");
        }
        var registration = new ServletRegistrationImpl(this, name, servlet);
        this.servletRegistrations.put(name, registration);
        return registration;
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String name, String jspFile) {
        throw new UnsupportedOperationException("addJspFile");
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
        checkNotInitialized("createServlet");
        return createInstance(clazz);
    }

    @Override
    public ServletRegistration getServletRegistration(String name) {
        return this.servletRegistrations.get(name);
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations() {
        return Map.copyOf(this.servletRegistrations);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, String className) {
        checkNotInitialized("addFilter");
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class name is null or empty.");
        }
        Filter filter = null;
        try {
            Class<? extends Filter> clazz = createInstance(className);
            filter = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addFilter(name, filter);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, Class<? extends Filter> clazz) {
        checkNotInitialized("addFilter");
        if (clazz == null) {
            throw new IllegalArgumentException("class is null.");
        }
        Filter filter = null;
        try {
            filter = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        return addFilter(name, filter);
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String name, Filter filter) {
        checkNotInitialized("addFilter");
        if (name == null) {
            throw new IllegalArgumentException("name is null.");
        }
        if (filter == null) {
            throw new IllegalArgumentException("filter is null.");
        }
        var registration = new FilterRegistrationImpl(this, name, filter);
        this.filterRegistrations.put(name, registration);
        return registration;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
        checkNotInitialized("createFilter");
        return createInstance(clazz);
    }

    @Override
    public FilterRegistration getFilterRegistration(String name) {
        return this.filterRegistrations.get(name);
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
        return Map.copyOf(this.filterRegistrations);
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return this.sessionCookieConfig;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
        throw new UnsupportedOperationException("setSessionTrackingModes");
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        // only support tracking by cookie:
        return Set.of(SessionTrackingMode.COOKIE);
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return this.getDefaultSessionTrackingModes();
    }

    @Override
    public void addListener(String className) {
        checkNotInitialized("addListener");
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("class name is null or empty.");
        }
        EventListener listener = null;
        try {
            Class<EventListener> clazz = createInstance(className);
            listener = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        addListener(listener);
    }

    @Override
    public void addListener(Class<? extends EventListener> clazz) {
        checkNotInitialized("addListener");
        if (clazz == null) {
            throw new IllegalArgumentException("class is null.");
        }
        EventListener listener = null;
        try {
            listener = createInstance(clazz);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        addListener(listener);
    }

    @Override
    public <T extends EventListener> void addListener(T t) {
        checkNotInitialized("addListener");
        if (t == null) {
            throw new IllegalArgumentException("listener is null.");
        }


//        if (t instanceof ServletContextListener listener) {
//            if (this.servletContextListeners == null) {
//                this.servletContextListeners = new ArrayList<>();
//            }
//            this.servletContextListeners.add(listener);
//        } else if (t instanceof ServletContextAttributeListener listener) {
//            if (this.servletContextAttributeListeners == null) {
//                this.servletContextAttributeListeners = new ArrayList<>();
//            }
//            this.servletContextAttributeListeners.add(listener);
//        } else if (t instanceof ServletRequestListener listener) {
//            if (this.servletRequestListeners == null) {
//                this.servletRequestListeners = new ArrayList<>();
//            }
//            this.servletRequestListeners.add(listener);
//        } else if (t instanceof ServletRequestAttributeListener listener) {
//            if (this.servletRequestAttributeListeners == null) {
//                this.servletRequestAttributeListeners = new ArrayList<>();
//            }
//            this.servletRequestAttributeListeners.add(listener);
//        } else if (t instanceof HttpSessionAttributeListener listener) {
//            if (this.httpSessionAttributeListeners == null) {
//                this.httpSessionAttributeListeners = new ArrayList<>();
//            }
//            this.httpSessionAttributeListeners.add(listener);
//        } else if (t instanceof HttpSessionListener listener) {
//            if (this.httpSessionListeners == null) {
//                this.httpSessionListeners = new ArrayList<>();
//            }
//            this.httpSessionListeners.add(listener);
//        } else {
//            throw new IllegalArgumentException("Unsupported listener: " + t.getClass().getName());
//        }
        if (t instanceof ServletContextListener) {
            ServletContextListener listener = (ServletContextListener) t;
            if (this.servletContextListeners == null) {
                this.servletContextListeners = new ArrayList<>();
            }
            this.servletContextListeners.add(listener);
        } else if (t instanceof ServletContextAttributeListener) {
            ServletContextAttributeListener listener = (ServletContextAttributeListener) t;
            if (this.servletContextAttributeListeners == null) {
                this.servletContextAttributeListeners = new ArrayList<>();
            }
            this.servletContextAttributeListeners.add(listener);
        } else if (t instanceof ServletRequestListener) {
            ServletRequestListener listener = (ServletRequestListener) t;
            if (this.servletRequestListeners == null) {
                this.servletRequestListeners = new ArrayList<>();
            }
            this.servletRequestListeners.add(listener);
        } else if (t instanceof ServletRequestAttributeListener) {
            ServletRequestAttributeListener listener = (ServletRequestAttributeListener) t;
            if (this.servletRequestAttributeListeners == null) {
                this.servletRequestAttributeListeners = new ArrayList<>();
            }
            this.servletRequestAttributeListeners.add(listener);
        } else if (t instanceof HttpSessionAttributeListener) {
            HttpSessionAttributeListener listener = (HttpSessionAttributeListener) t;
            if (this.httpSessionAttributeListeners == null) {
                this.httpSessionAttributeListeners = new ArrayList<>();
            }
            this.httpSessionAttributeListeners.add(listener);
        } else if (t instanceof HttpSessionListener) {
            HttpSessionListener listener = (HttpSessionListener) t;
            if (this.httpSessionListeners == null) {
                this.httpSessionListeners = new ArrayList<>();
            }
            this.httpSessionListeners.add(listener);
        } else {
            throw new IllegalArgumentException("Unsupported listener: " + t.getClass().getName());
        }

    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
        checkNotInitialized("createListener");
        return createInstance(clazz);
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor() {
        // not support JSP:
        return null;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    @Override
    public void declareRoles(String... roleNames) {
        throw new UnsupportedOperationException("declareRoles");
    }

    @Override
    public String getVirtualServerName() {
        return this.config.server.contexts.get(configIndex).virtualServerName;
    }

    @Override
    public int getSessionTimeout() {
        return this.config.server.contexts.get(configIndex).sessionTimeout;
    }

    @Override
    public void setSessionTimeout(int sessionTimeout) {
        checkNotInitialized("setSessionTimeout");
        this.config.server.contexts.get(configIndex).sessionTimeout = sessionTimeout;
    }

    @Override
    public String getRequestCharacterEncoding() {
        return this.config.server.requestEncoding;
    }

    @Override
    public void setRequestCharacterEncoding(String encoding) {
        checkNotInitialized("setRequestCharacterEncoding");
        this.config.server.requestEncoding = encoding;
    }

    @Override
    public String getResponseCharacterEncoding() {
        return this.config.server.responseEncoding;
    }

    @Override
    public void setResponseCharacterEncoding(String encoding) {
        checkNotInitialized("setResponseCharacterEncoding");
        this.config.server.responseEncoding = encoding;
    }

    // Servlet API version: 6.0.0

    @Override
    public int getMajorVersion() {
        return 6;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion() {
        return 6;
    }

    @Override
    public int getEffectiveMinorVersion() {
        return 0;
    }

    @Override
    public void initInternal() throws LifecycleException {
        super.initInternal();
        if (this.initialized) {
            throw new IllegalStateException("Cannot re-initialize.");
        }

        // register @WebListener:
        for (Class<?> c : autoScannedClasses) {
            if (c.isAnnotationPresent(WebListener.class)) {
                logger.info("auto register @WebListener: {}", c.getName());
                @SuppressWarnings("unchecked")
                Class<? extends EventListener> clazz = (Class<? extends EventListener>) c;
                this.addListener(clazz);
            }
        }

        this.invokeServletContextInitialized();

    }

    @Override
    protected void startInternal() throws LifecycleException {
        setState(LifecycleState.STARTING);
        // register @WebServlet and @WebFilter: 注册servlet
        for (Class<?> c : autoScannedClasses) {
            WebServlet ws = c.getAnnotation(WebServlet.class);
            if (ws != null) {
                logger.info("auto register @WebServlet: {}", c.getName());
                @SuppressWarnings("unchecked")
                Class<? extends Servlet> clazz = (Class<? extends Servlet>) c;
                ServletRegistration.Dynamic registration = this.addServlet(AnnoUtils.getServletName(clazz), clazz);
                registration.addMapping(AnnoUtils.getServletUrlPatterns(clazz));
                registration.setInitParameters(AnnoUtils.getServletInitParams(clazz));
            }
            WebFilter wf = c.getAnnotation(WebFilter.class);
            if (wf != null) {
                logger.info("auto register @WebFilter: {}", c.getName());
                @SuppressWarnings("unchecked")
                Class<? extends Filter> clazz = (Class<? extends Filter>) c;
                FilterRegistration.Dynamic registration = this.addFilter(AnnoUtils.getFilterName(clazz), clazz);
                registration.addMappingForUrlPatterns(AnnoUtils.getFilterDispatcherTypes(clazz), true, AnnoUtils.getFilterUrlPatterns(clazz));
                registration.setInitParameters(AnnoUtils.getFilterInitParams(clazz));
            }
        }

        // init servlets while find default servlet:
        Servlet defaultServlet = null;
        for (String name : this.servletRegistrations.keySet()) {
            ServletRegistrationImpl registration = this.servletRegistrations.get(name);
            try {
                registration.servlet.init(registration.getServletConfig());
                this.nameToServlets.put(name, registration.servlet);
                for (String urlPattern : registration.getMappings()) {
                    this.servletMappings.add(new ServletMapping(urlPattern, registration.servlet));
                    if (urlPattern.equals("/")) {
                        if (defaultServlet == null) {
                            defaultServlet = registration.servlet;
                            logger.info("set default servlet: " + registration.getClassName());
                        } else {
                            logger.warn("found duplicate default servlet: " + registration.getClassName());
                        }
                    }
                }
                registration.initialized = true;
            } catch (ServletException e) {
                logger.error("init servlet failed: " + name + " / " + registration.servlet.getClass().getName(), e);
            }
        }
        //TODO:应该单独给DefaultServlet设置一个Context
        //FIXME:修复DefaultServlet不能使用
        if (defaultServlet == null && config.server.contexts.get(configIndex).fileListings) {
            logger.info("no default servlet. auto register {}...", DefaultServlet.class.getName());
            defaultServlet = new DefaultServlet();
            try {
                defaultServlet.init(new ServletConfig() {
                    @Override
                    public String getServletName() {
                        return "DefaultServlet";
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return NormalContext.this;
                    }

                    @Override
                    public String getInitParameter(String name) {
                        return null;
                    }

                    @Override
                    public Enumeration<String> getInitParameterNames() {
                        return Collections.emptyEnumeration();
                    }
                });
                this.servletMappings.add(new ServletMapping("/", defaultServlet));
            } catch (ServletException e) {
                logger.error("init default servlet failed.", e);
            }
        }
        this.defaultServlet = defaultServlet;

        // init filters:
        for (String name : this.filterRegistrations.keySet()) {
            var registration = this.filterRegistrations.get(name);
            try {
                registration.filter.init(registration.getFilterConfig());
                this.nameToFilters.put(name, registration.filter);
                for (String urlPattern : registration.getUrlPatternMappings()) {
                    this.filterMappings.add(new FilterMapping(name, urlPattern, registration.filter));
                }
                registration.initialized = true;
            } catch (ServletException e) {
                logger.error("init filter failed: " + name + " / " + registration.filter.getClass().getName(), e);
            }
        }
        // important: sort by servlet mapping:
        Collections.sort(this.servletMappings);
        // important: sort by filter name:
        Collections.sort(this.filterMappings, (f1, f2) -> {
            int cmp = f1.filterName.compareTo(f2.filterName);
            if (cmp == 0) {
                cmp = f1.compareTo(f2);
            }
            return cmp;
        });
        this.initialized = true;
    }

    @Override
    protected void stopInternal() throws LifecycleException {
        //TODO:完成stop 也就是stop后再start还可以正常运行
    }


    @Override
    public void destroyInternal() throws LifecycleException {
        // destroy filter and servlet:
        this.filterMappings.forEach(mapping -> {
            try {
                mapping.filter.destroy();
            } catch (Exception e) {
                logger.error("destroy filter '" + mapping.filter + "' failed.", e);
            }
        });

        this.servletMappings.forEach(mapping -> {
            try {
                mapping.servlet.destroy();
            } catch (Exception e) {
                logger.error("destroy servlet '" + mapping.servlet + "' failed.", e);
            }
        });
        // notify:
        this.invokeServletContextDestroyed();
        super.destroyInternal();
    }

    private void checkNotInitialized(String name) {
        //TODO:应该舍弃此方法 而使用lifecycle中的方法
        if (this.initialized) {
            throw new IllegalStateException("Cannot call " + name + " after initialization.");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createInstance(String className) throws ServletException {
        Class<T> clazz;
        try {
            clazz = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found.", e);
        }
        return createInstance(clazz);
    }

    private <T> T createInstance(Class<T> clazz) throws ServletException {
        try {
            Constructor<T> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ServletException("Cannot instantiate class " + clazz.getName(), e);
        }
    }

    @Override
    protected String getDomainInternal() {

        return "Engine";
    }

    @Override
    protected String getObjectNameKeyProperties() {

        return "type=Context,name=" + this.config.server.contexts.get(configIndex).name + ",host=" + this.config.server.contexts.get(configIndex).virtualServerName + ",version=1.0.0";
    }
}
