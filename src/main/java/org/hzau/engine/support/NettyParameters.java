package org.hzau.engine.support;

import io.netty.handler.codec.http.FullHttpRequest;

import org.hzau.utils.HttpUtils;
import org.hzau.utils.RequestParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.*;

public class NettyParameters {

    final FullHttpRequest exchangeRequest;
    Charset charset;
    Map<String, String> parameters;

    public NettyParameters(FullHttpRequest exchangeRequest, String charset) {
        this.exchangeRequest = exchangeRequest;
        this.charset = Charset.forName(charset);
    }

    public void setCharset(String charset) {
        this.charset = Charset.forName(charset);
    }

    public String getParameter(String name) {
        String[] values = getParameterValues(name);
        if (values == null) {
            return null;
        }
        return values[0];
    }

    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    public String[] getParameterValues(String name) {
        String[] values = new String[1];
        values[0] = getParameterMap().get(name);
        return values;
    }

    public Map<String, String> getParameterMap() {
        if (this.parameters == null) {
            try {
                this.parameters = initParameters();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.parameters;
    }

    Map<String, String> initParameters() throws IOException {
        Map<String, String> params = new HashMap<>();
        String query = this.exchangeRequest.uri();
        if (query != null) {
            Map<String, List<String>> stringListMap = HttpUtils.parseQuery(query, charset);
            stringListMap.forEach((k, v) -> params.put(k, v.get(0)));
        }
        if ("POST".equals(this.exchangeRequest.method().name())) {
            params.putAll(new RequestParser(exchangeRequest).parse()); // 将GET, POST所有请求参数转换成Map对象
//
//            String value = HttpUtils.getHeader(this.exchangeRequest.headers(), "Content-Type");
//            if (value != null && value.startsWith("application/x-www-form-urlencoded")) {
//                String requestBody;
//                //FIXME：修复了这里的编码问题
//
//                requestBody = new String(exchangeRequest.content().toString().getBytes(), charset);
//                Map<String, List<String>> postParams = HttpUtils.parseQuery(requestBody, charset);
//                // merge:
//                for (String key : postParams.keySet()) {
//                    List<String> postValues = postParams.get(key);
//                    List<String> queryValues = params.get(key);
//                    if (queryValues == null) {
//                        params.put(key, postValues);
//                    } else {
//                        queryValues.addAll(postValues);
//                    }
//                }
//            }
        }
        if (params.isEmpty()) {
            return Map.of();
        }
        // convert:
//        Map<String, String> paramsMap = new HashMap<>();
//        for (String key : params.keySet()) {
//            List<String> values = params.get(key);
//            paramsMap.put(key, values.toArray(String[]::new));
//        }


        return params;
    }
}
