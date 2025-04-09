package com.zyq.chirp.chirperserver.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.zyq.chirp.chirpclient.dto.LikeDto;
import com.zyq.chirp.chirperserver.service.LikeService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞相关接口控制器
 * 处理推文的点赞和取消点赞功能
 */
@RestController
@RequestMapping("/like")
public class LikeController {
    @Resource
    LikeService likeService;

    /**
     * 点赞推文
     * POST /like/give
     * 
     * @param likeDto 点赞信息，包含推文ID
     * @return 点赞结果
     */
    @PostMapping("/give")
    public ResponseEntity add(@RequestBody LikeDto likeDto) {
        likeDto.setUserId(StpUtil.getLoginIdAsLong());
        likeService.addLike(likeDto);
        return ResponseEntity.ok(null);
    }

    /**
     * 取消点赞
     * POST /like/cancel
     * 
     * @param likeDto 点赞信息，包含推文ID
     * @return 取消结果
     */
    @PostMapping("/cancel")
    public ResponseEntity cancel(@RequestBody LikeDto likeDto) {
        likeDto.setUserId(StpUtil.getLoginIdAsLong());
        likeService.cancelLike(likeDto);
        return ResponseEntity.ok(null);
    }
}
