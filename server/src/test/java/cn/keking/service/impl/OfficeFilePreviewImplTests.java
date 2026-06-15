package cn.keking.service.impl;

import cn.keking.config.ConfigConstants;
import cn.keking.model.FileAttribute;
import cn.keking.service.FileHandlerService;
import cn.keking.service.FilePreview;
import cn.keking.service.OfficeToPdfService;
import cn.keking.service.PdfToJpgService;
import cn.keking.utils.FileConvertStatusManager;
import cn.keking.web.filter.BaseUrlFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfficeFilePreviewImplTests {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        ConfigConstants.setCacheEnabledValueValue(true);
        ConfigConstants.setOfficeTypeWebValue("web");
        ConfigConstants.setRefreshScheduleValue(5);
    }

    @Test
    void shouldRegenerateImagesWhenPdfCacheExistsButImageCacheIsMissing() throws Exception {
        FileHandlerService fileHandlerService = mock(FileHandlerService.class);
        OfficeToPdfService officeToPdfService = mock(OfficeToPdfService.class);
        OtherFilePreviewImpl otherFilePreview = mock(OtherFilePreviewImpl.class);
        PdfToJpgService pdfToJpgService = mock(PdfToJpgService.class);
        OfficeFilePreviewImpl preview = new OfficeFilePreviewImpl(fileHandlerService, officeToPdfService,
                otherFilePreview, pdfToJpgService);
        String cacheName = "demo-" + System.nanoTime() + ".pdf";
        Path pdfPath = Files.createFile(tempDir.resolve(cacheName));
        FileAttribute fileAttribute = new FileAttribute();
        fileAttribute.setOfficePreviewType(OfficeFilePreviewImpl.OFFICE_PREVIEW_TYPE_IMAGE);
        fileAttribute.setSuffix("docx");
        fileAttribute.setName("demo.docx");
        fileAttribute.setCacheName(cacheName);
        fileAttribute.setOutFilePath(pdfPath.toString());
        Model model = new ExtendedModelMap();
        setBaseUrl();

        ConfigConstants.setCacheEnabledValueValue(true);
        ConfigConstants.setOfficeTypeWebValue("web");
        ConfigConstants.setRefreshScheduleValue(1);
        when(fileHandlerService.listConvertedFiles()).thenReturn(Map.of(cacheName, cacheName));
        when(fileHandlerService.loadPdf2jpgCache(pdfPath.toString())).thenReturn(Collections.emptyList());
        when(pdfToJpgService.hasEncryptedPdfCacheSimple(pdfPath.toString())).thenReturn(false);
        when(pdfToJpgService.pdf2jpg(eq(pdfPath.toString()), eq(pdfPath.toString()), same(fileAttribute)))
                .thenReturn(List.of("http://127.0.0.1:8012/demo/0.jpg"));

        String view = preview.filePreviewHandle("http://example.com/demo.docx", model, fileAttribute);

        assertEquals(FilePreview.WAITING_FILE_PREVIEW_PAGE, view);
        assertEquals("demo.docx", model.getAttribute("fileName"));
        verify(pdfToJpgService, timeout(1000))
                .pdf2jpg(eq(pdfPath.toString()), eq(pdfPath.toString()), same(fileAttribute));
        verifyNoInteractions(officeToPdfService);
        FileConvertStatusManager.convertSuccess(cacheName);
    }

    private void setBaseUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(8012);
        new BaseUrlFilter().doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
        });
    }
}
