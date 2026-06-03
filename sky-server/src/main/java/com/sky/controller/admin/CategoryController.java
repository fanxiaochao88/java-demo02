package com.sky.controller.admin;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/category")
@Slf4j
@Api(tags = "菜品和套餐分类相关接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 新增分类
     * @param category
     * @return
     */
    @PostMapping
    @ApiOperation("新增分类")
    public Result save(@RequestBody CategoryDTO  category) {
        log.info("新增分类：{}", category);
        categoryService.save(category);
        return Result.success();
    }

    /**
     * 分类分页查询
     * @param pageDto
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("分页查询")
    public Result<PageResult> page(CategoryPageQueryDTO  pageDto) {
        log.info("分页查询：{}", pageDto);
        PageResult pageResult = categoryService.pageQuery(pageDto);
        return Result.success(pageResult);
    }

    /**
     * 启用/禁用 分类
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用分类")
    public Result startOrStop(
            @ApiParam(value = "状态", required = true) @PathVariable Integer status,
            @ApiParam(value = "员工id", required = true) @RequestParam Long id) {
        log.info("启用禁用分类：{}", id);
        categoryService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 修改分类
     * @param category
     * @return
     */
    @PutMapping
    @ApiOperation("修改分类")
    public Result update(@RequestBody CategoryDTO category) {
        log.info("修改分类：{}", category);
        categoryService.update(category);
        return Result.success();
    }

    /**
     * 删除分类
     * @param id
     * @return
     */
    @DeleteMapping
    @ApiOperation("删除分类")
    public Result delete(@ApiParam("分类id") Integer id) {
        log.info("删除分类：{}", id);
        categoryService.delete(id);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("根据分类进行查询")
    public Result<List<Category>> list(@ApiParam("分类类型") Integer type) {
        log.info("查询分类：{}", type);
        List<Category> list = categoryService.list(type);
        return Result.success(list);
    }
}
