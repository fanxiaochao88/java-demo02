package com.sky.service.impl;

import com.sky.config.CosConfig;
import com.sky.service.CommonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonServiceImpl implements CommonService {
    private final CosConfig cosConfig;
    public String uploadFile(MultipartFile file) throws Exception {
        COSCredentials cred = new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey());
        COSClient cosClient = new COSClient(cred, new ClientConfig(new Region(cosConfig.getRegion())));

        try {
            // 用UUID生成唯一文件名，保留原始扩展名
            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            LocalDate now = LocalDate.now();
            String key = String.format("uploads/%d/%02d/%s%s", now.getYear(), now.getMonthValue(), UUID.randomUUID(), ext);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());

            cosClient.putObject(new PutObjectRequest(cosConfig.getBucketName(), key, file.getInputStream(), metadata));

            String url = "https://" + cosConfig.getBucketName() + ".cos." + cosConfig.getRegion() + ".myqcloud.com/" + key;
            log.info("文件上传成功, url: {}", url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            return null;
        } finally {
            cosClient.shutdown();
        }
    }
}
