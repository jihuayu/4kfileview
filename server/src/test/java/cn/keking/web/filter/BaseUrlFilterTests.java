package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseUrlFilterTests {

    private final BaseUrlFilter filter = new BaseUrlFilter();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        ConfigConstants.setBaseUrlValue(ConfigConstants.DEFAULT_VALUE);
    }

    @Test
    void shouldBuildBaseUrlFromForwardedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("internal");
        request.setServerPort(8012);
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "files.example.com");
        request.addHeader("X-Forwarded-Port", "443");
        request.addHeader("X-Forwarded-Prefix", "/preview");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("https://files.example.com/preview/", request.getAttribute("baseUrl"));
    }

    @Test
    void shouldBuildBaseUrlFromStandardForwardedHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("internal");
        request.setServerPort(8012);
        request.addHeader("Forwarded", "for=192.0.2.60;proto=https;host=\"files.example.com:8443\"");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("https://files.example.com:8443/", request.getAttribute("baseUrl"));
    }
}
