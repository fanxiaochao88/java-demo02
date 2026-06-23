package com.sky.controller.user;

import com.sky.entity.AddressBook;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/user/addressBook")
@RestController("userAddressBookController")
@Slf4j
@Api(tags = "C端地址簿接口")
public class AddressBookController {

    @Autowired
    private AddressBookService addressBookService;

    /**
     * 新增用户收获地址
     */
    @PostMapping
    @ApiOperation("新增用户收获地址")
    public Result save(@RequestBody AddressBook addressBook) {
        log.info("新增地址：{}", addressBook);
        addressBookService.insert(addressBook);
        return Result.success();
    }

    /**
     * 查询用户所有的地址列表
     */
    @GetMapping("/list")
    @ApiOperation("查询用户所有的地址列表")
    public Result<List<AddressBook>> list() {
        log.info("查询地址列表");
        return Result.success(addressBookService.list());
    }

    /**
     * 查询默认地址
     */
    @GetMapping("/default")
    @ApiOperation("查询默认地址")
    public Result<AddressBook> getDefault() {
        log.info("查询默认地址");
        return Result.success(addressBookService.getDefault());
    }

    /**
     * 设置默认地址
     */
    @PutMapping("/default")
    @ApiOperation("设置默认地址")
    public Result setDefault(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址：{}", addressBook);
        addressBookService.setDefault(addressBook);
        return Result.success();
    }

    /**
     * 删除地址
     */
    @DeleteMapping
    @ApiOperation("删除地址")
    public Result delete(@ApiParam("地址id") @RequestParam Long id) {
        log.info("删除地址：{}", id);
        addressBookService.delete(id);
        return Result.success();
    }

    /**
     * 根据id查询地址详情
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询地址详情")
    public Result<AddressBook> getById(@PathVariable Long id) {
        log.info("查询地址详情：{}", id);
        return Result.success(addressBookService.getById(id));
    }

    /**
     * 修改地址
     */
    @PutMapping
    @ApiOperation("修改地址")
    public Result update(@RequestBody AddressBook addressBook) {
        log.info("修改地址：{}", addressBook);
        addressBookService.update(addressBook);
        return Result.success();
    }
}
