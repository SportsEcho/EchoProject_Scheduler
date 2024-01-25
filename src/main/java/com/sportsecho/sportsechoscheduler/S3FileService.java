package com.sportsecho.sportsechoscheduler;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class S3FileService {

    private final AmazonS3Client amazonS3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String getFile(String filename) {
        try {
            S3Object s3Object = amazonS3Client.getObject(new GetObjectRequest(bucket, filename));
            S3ObjectInputStream objectInputStream = s3Object.getObjectContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(objectInputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            return stringBuilder.toString();
        } catch (AmazonServiceException e) {
            log.error("AmazonServiceException: {}", e.getMessage());
        } catch (SdkClientException e) {
            log.error("SdkClientException: {}", e.getMessage());
        } catch (IOException e) {
            log.error("IOException: {}", e.getMessage());
        }
        return null;
    }

    public String getS3FileContent(String filename) {
        return getFile(filename);
    }

    public void uploadFile(String filename, String dateStr) {
        try {
            byte[] contentAsBytes = dateStr.getBytes(StandardCharsets.UTF_8);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentAsBytes.length);

            InputStream inputStream = new ByteArrayInputStream(contentAsBytes);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, filename, inputStream, metadata);

            amazonS3Client.putObject(putObjectRequest);
        } catch (AmazonServiceException e) {
            log.error("AmazonServiceException: {}", e.getMessage());
        } catch (SdkClientException e) {
            log.error("SdkClientException: {}", e.getMessage());
        }
    }

    public void deleteFile(String filename) {
        try {
            amazonS3Client.deleteObject(bucket, filename);
        } catch (AmazonServiceException e) {
            log.error("AmazonServiceException: {}", e.getMessage());
        } catch (SdkClientException e) {
            log.error("SdkClientException: {}", e.getMessage());
        }
    }
}
