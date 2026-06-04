package com.sky.service;

import org.springframework.web.multipart.MultipartFile;

public interface CommonService {
    public String uploadFile(MultipartFile file) throws Exception;
}
