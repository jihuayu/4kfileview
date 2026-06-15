package cn.keking.service;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.model.FileType;
import cn.keking.service.cache.CacheService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class FileHandlerServiceTests {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void shouldInferPdfSuffixForExtensionlessHttpUrlFromContentType() throws Exception {
        String url = startServer("application/pdf", null);
        FileHandlerService fileHandlerService = newFileHandlerService();

        FileAttribute fileAttribute = fileHandlerService.getFileAttribute(url, null);

        assertEquals("download.pdf", fileAttribute.getName());
        assertEquals("pdf", fileAttribute.getSuffix());
        assertEquals(FileType.PDF, fileAttribute.getType());
        assertEquals("download.pdf", fileAttribute.getCacheName());
    }

    @Test
    void shouldPreferContentDispositionFilenameForExtensionlessHttpUrl() throws Exception {
        String url = startServer("application/octet-stream", "attachment; filename=\"report.docx\"");
        FileHandlerService fileHandlerService = newFileHandlerService();

        FileAttribute fileAttribute = fileHandlerService.getFileAttribute(url, null);

        assertEquals("report.docx", fileAttribute.getName());
        assertEquals("docx", fileAttribute.getSuffix());
        assertEquals(FileType.OFFICE, fileAttribute.getType());
    }

    private FileHandlerService newFileHandlerService() {
        ConfigConstants.setIgnoreSSLValue(false);
        ConfigConstants.setEnableRedirectValue(true);
        ConfigConstants.setUserAgentValue("false");
        ConfigConstants.setBasicNameValue("");
        FileHandlerService fileHandlerService = new FileHandlerService(mock(CacheService.class));
        ReflectionTestUtils.setField(fileHandlerService, "uriEncoding", "UTF-8");
        return fileHandlerService;
    }

    private String startServer(String contentType, String contentDisposition) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/download", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", contentType);
            if (contentDisposition != null) {
                exchange.getResponseHeaders().add("Content-Disposition", contentDisposition);
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        httpServer.start();
        return "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/download";
    }
}
