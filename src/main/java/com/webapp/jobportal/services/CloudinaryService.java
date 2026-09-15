package com.webapp.jobportal.services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger logger = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;

    @Autowired
    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Upload an image file (e.g. profile photo, testimonial picture) to Cloudinary.
     *
     * @param file   The uploaded MultipartFile
     * @param folder Destination folder in Cloudinary
     * @return The secure HTTPS URL of the uploaded image
     * @throws IOException If upload fails
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            logger.info("Uploading image [{}] to Cloudinary folder [{}]", file.getOriginalFilename(), folder);
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image",
                    "use_filename", true,
                    "unique_filename", true
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            logger.info("Image successfully uploaded to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (IOException e) {
            logger.error("Failed to upload image [{}] to Cloudinary: {}", file.getOriginalFilename(), e.getMessage());
            throw e;
        }
    }

    /**
     * Upload a document (e.g. PDF resume, ID verification doc, business license) to Cloudinary.
     * Uses resource_type 'auto' to ensure PDFs, images, and office documents are handled natively.
     *
     * @param file   The uploaded MultipartFile
     * @param folder Destination folder in Cloudinary
     * @return The secure HTTPS URL of the uploaded document
     * @throws IOException If upload fails
     */
    public String uploadDocument(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            logger.info("Uploading document [{}] to Cloudinary folder [{}]", file.getOriginalFilename(), folder);
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto",
                    "use_filename", true,
                    "unique_filename", true
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            logger.info("Document successfully uploaded to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (IOException e) {
            logger.error("Failed to upload document [{}] to Cloudinary: {}", file.getOriginalFilename(), e.getMessage());
            throw e;
        }
    }

    /**
     * Upload any media/file attachment (e.g. Chat attachments: image, audio note, or document).
     *
     * @param file   The uploaded MultipartFile
     * @param folder Destination folder in Cloudinary
     * @return The secure HTTPS URL of the uploaded media
     * @throws IOException If upload fails
     */
    public String uploadMedia(MultipartFile file, String folder) throws IOException {
        return uploadDocument(file, folder);
    }

    /**
     * Delete an asset from Cloudinary by its public ID.
     *
     * @param publicId Cloudinary public_id
     */
    public void deleteFile(String publicId) {
        if (publicId == null || publicId.trim().isEmpty()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            logger.info("Deleted file from Cloudinary: {}", publicId);
        } catch (Exception e) {
            logger.warn("Could not delete file [{}] from Cloudinary: {}", publicId, e.getMessage());
        }
    }
}
