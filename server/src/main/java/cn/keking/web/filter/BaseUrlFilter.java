package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author chenjh
 * @since 2020/5/13 18:27
 */
public class BaseUrlFilter implements Filter {

    private static String BASE_URL;

    public static String getBaseUrl() {
        String baseUrl;
        try {
            baseUrl = (String) RequestContextHolder.currentRequestAttributes().getAttribute("baseUrl", 0);
        } catch (Exception e) {
            baseUrl = BASE_URL;
        }
        return baseUrl;
    }


    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        String baseUrl;
        String configBaseUrl = ConfigConstants.getBaseUrl();

        final HttpServletRequest servletRequest = (HttpServletRequest) request;
        //1、支持通过 http header 中 X-Base-Url 来动态设置 baseUrl 以支持多个域名/项目的共享使用
        final String urlInHeader = servletRequest.getHeader("X-Base-Url");
        if (StringUtils.isNotEmpty(urlInHeader)) {
            baseUrl = urlInHeader;
        } else if (configBaseUrl != null && !ConfigConstants.DEFAULT_VALUE.equalsIgnoreCase(configBaseUrl)) {
            //2、如果配置文件中配置了 baseUrl 且不为 default 则以配置文件为准
            baseUrl = configBaseUrl;
        } else {
            //3、默认动态拼接 baseUrl，优先使用反向代理透传的外部访问地址
            baseUrl = buildBaseUrl(servletRequest);
        }

        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl.concat("/");
        }

        BASE_URL = baseUrl;
        request.setAttribute("baseUrl", baseUrl);
        filterChain.doFilter(request, response);
    }

    private String buildBaseUrl(HttpServletRequest request) {
        Map<String, String> forwarded = parseForwardedHeader(firstHeaderValue(request.getHeader("Forwarded")));
        String scheme = firstNonEmpty(
                firstHeaderValue(request.getHeader("X-Forwarded-Proto")),
                forwarded.get("proto"),
                request.getScheme()
        );
        String host = firstNonEmpty(
                firstHeaderValue(request.getHeader("X-Forwarded-Host")),
                forwarded.get("host"),
                request.getServerName() + ":" + request.getServerPort()
        );
        String port = firstHeaderValue(request.getHeader("X-Forwarded-Port"));
        if (StringUtils.isNotEmpty(port) && !hostContainsPort(host) && !isDefaultPort(scheme, port)) {
            host = host + ":" + port;
        }
        String prefix = firstNonEmpty(firstHeaderValue(request.getHeader("X-Forwarded-Prefix")), request.getContextPath());
        return scheme + "://" + host + normalizePrefix(prefix) + "/";
    }

    private Map<String, String> parseForwardedHeader(String header) {
        Map<String, String> result = new HashMap<>();
        if (StringUtils.isEmpty(header)) {
            return result;
        }
        String[] parts = header.split(";");
        for (String part : parts) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) {
                result.put(pair[0].trim().toLowerCase(Locale.ROOT), stripQuotes(pair[1].trim()));
            }
        }
        return result;
    }

    private String firstHeaderValue(String headerValue) {
        if (StringUtils.isEmpty(headerValue)) {
            return null;
        }
        return stripQuotes(headerValue.split(",", 2)[0].trim());
    }

    private String stripQuotes(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (StringUtils.isNotEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hostContainsPort(String host) {
        if (StringUtils.isEmpty(host)) {
            return false;
        }
        if (host.startsWith("[")) {
            return host.contains("]:");
        }
        return host.contains(":");
    }

    private boolean isDefaultPort(String scheme, String port) {
        return ("http".equalsIgnoreCase(scheme) && "80".equals(port))
                || ("https".equalsIgnoreCase(scheme) && "443".equals(port));
    }

    private String normalizePrefix(String prefix) {
        if (StringUtils.isEmpty(prefix) || "/".equals(prefix)) {
            return "";
        }
        String normalized = prefix.startsWith("/") ? prefix : "/" + prefix;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public void destroy() {

    }
}
