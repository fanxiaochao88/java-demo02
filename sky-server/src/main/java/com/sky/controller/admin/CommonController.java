package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.CommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private CommonService commonService;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@ApiParam("上传的文件") MultipartFile  file) throws Exception {
        log.info("文件上传：{}", file);
        String url = commonService.uploadFile(file);
        if (url == null) {
            return Result.error("上传失败");
        }
        return Result.success(url);
    }
}
