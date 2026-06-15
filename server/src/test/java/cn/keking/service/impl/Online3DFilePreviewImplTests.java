package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.service.FilePreview;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class Online3DFilePreviewImplTests {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldExposeExternalGltfResourcesToTemplate() throws Exception {
        String modelUrl = startGltfServer();
        CommonPreviewImpl commonPreview = mock(CommonPreviewImpl.class);
        doAnswer(invocation -> null).when(commonPreview).filePreviewHandle(any(), any(), any());
        Online3DFilePreviewImpl preview = new Online3DFilePreviewImpl(commonPreview);
        FileAttribute fileAttribute = new FileAttribute();
        fileAttribute.setSuffix("gltf");
        fileAttribute.setName("model.gltf");
        Model model = new ExtendedModelMap();
        configureHttpClientDefaults();

        String view = preview.filePreviewHandle(modelUrl, model, fileAttribute);

        assertEquals(FilePreview.ONLINE3D_PREVIEW_PAGE, view);
        @SuppressWarnings("unchecked")
        List<String> resourceUrls = (List<String>) model.asMap().get("online3DResourceUrls");
        String baseUrl = modelUrl.substring(0, modelUrl.lastIndexOf('/') + 1);
        assertEquals(List.of(baseUrl + "model.bin", baseUrl + "textures/diffuse.png"), resourceUrls);
    }

    @Test
    void shouldPassGltfResourceUrlsToOnline3DViewer() throws IOException {
        String template = readResource("/web/online3D.ftl");

        assertTrue(template.contains("online3DResourceUrls"));
        assertTrue(template.contains("modelUrls.map(toViewerUrl).join(\",\")"));
        assertTrue(template.contains("fullfilename=/"));
    }

    private void configureHttpClientDefaults() {
        ConfigConstants.setIgnoreSSLValue(false);
        ConfigConstants.setEnableRedirectValue(true);
        ConfigConstants.setUserAgentValue("false");
        ConfigConstants.setBasicNameValue("");
    }

    private String startGltfServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/models/model.gltf", exchange -> {
            byte[] body = """
                    {
                      "asset": {"version": "2.0"},
                      "buffers": [{"uri": "model.bin"}],
                      "images": [
                        {"uri": "textures/diffuse.png"},
                        {"uri": "data:image/png;base64,AAAA"}
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "model/gltf+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/models/model.gltf";
    }

    private String readResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(inputStream);
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
