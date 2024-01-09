package org.hzau;

import java.util.List;
import java.util.Map;

public class Config {

    public Server server;

    public static class Server {
        public String host;
        public Integer port;
        public Integer backlog;
        public String requestEncoding;
        public String responseEncoding;
        public String name;
        public String mimeDefault;
        public int threadPoolSize;
        public boolean enableVirtualThread;
        public Map<String, String> mimeTypes;
        public List<Context> contexts;
        public ForwardedHeaders forwardedHeaders;

        public String getMimeType(String url) {
            int n = url.lastIndexOf('.');
            if (n < 0) {
                return this.mimeDefault;
            }
            String ext = url.substring(n).toLowerCase();
            return this.mimeTypes.getOrDefault(ext, this.mimeDefault);
        }

        public static class Context {//一个上下文
            public String name;
            public String docBase;

            public String path;
            public boolean fileListings;
            public String virtualServerName;
            public String sessionCookieName;
            public Integer sessionTimeout;
        }

        public static class ForwardedHeaders {
            public String forwardedProto;
            public String forwardedHost;
            public String forwardedFor;
        }
    }
}
