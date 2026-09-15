package com.webapp.jobportal.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class CloudinaryServiceTest {

    private Cloudinary cloudinary;
    private Uploader uploader;
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinary = Mockito.mock(Cloudinary.class);
        uploader = Mockito.mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryService = new CloudinaryService(cloudinary);
    }

    @Test
    void testUploadImageSuccess() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "test-avatar.png",
                "image/png",
                "test-content".getBytes()
        );

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/ysilrviy/image/upload/v1/jobportal/candidate/1/test-avatar.png");
        uploadResult.put("public_id", "jobportal/candidate/1/test-avatar");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        String resultUrl = cloudinaryService.uploadImage(file, "jobportal/candidate/1");

        assertNotNull(resultUrl);
        assertEquals("https://res.cloudinary.com/ysilrviy/image/upload/v1/jobportal/candidate/1/test-avatar.png", resultUrl);
        verify(uploader, times(1)).upload(any(byte[].class), anyMap());
    }

    @Test
    void testUploadDocumentSuccess() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "resume.pdf",
                "application/pdf",
                "%PDF-test".getBytes()
        );

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/ysilrviy/raw/upload/v1/jobportal/candidate/1/resume.pdf");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        String resultUrl = cloudinaryService.uploadDocument(file, "jobportal/candidate/1");

        assertNotNull(resultUrl);
        assertEquals("https://res.cloudinary.com/ysilrviy/raw/upload/v1/jobportal/candidate/1/resume.pdf", resultUrl);
    }

    @Test
    void testUploadNullOrEmptyFileReturnsNull() throws IOException {
        String resultNull = cloudinaryService.uploadImage(null, "folder");
        assertNull(resultNull);

        MockMultipartFile emptyFile = new MockMultipartFile("empty", "", "image/png", new byte[0]);
        String resultEmpty = cloudinaryService.uploadImage(emptyFile, "folder");
        assertNull(resultEmpty);

        verify(uploader, never()).upload(any(byte[].class), anyMap());
    }
}
