package cn.keking.config;

import cn.keking.web.filter.TrustDirFilter;
import cn.keking.web.filter.TrustHostFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigSecurityTests {

    private final WebConfig webConfig = new WebConfig();

    @Test
    void shouldProtectAddTaskWithTrustHostFilter() {
        FilterRegistrationBean<TrustHostFilter> registrationBean = webConfig.getTrustHostFilter();

        assertTrue(registrationBean.getUrlPatterns().contains("/addTask"));
    }

    @Test
    void shouldProtectAddTaskWithTrustDirFilter() {
        FilterRegistrationBean<TrustDirFilter> registrationBean = webConfig.getTrustDirFilter();

        assertTrue(registrationBean.getUrlPatterns().contains("/addTask"));
    }
}
