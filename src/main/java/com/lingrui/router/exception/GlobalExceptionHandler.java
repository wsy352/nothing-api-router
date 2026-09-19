package com.lingrui.router.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 集中处理接口异常，将错误说明转换为JSON响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理主动抛出的 HTTP 状态异常
     * @param exception 请求处理过程中抛出的异常
     * @return 包含对应 HTTP 状态码和错误说明的响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatusException(ResponseStatusException exception) {

        //内层对象，保存错误说明
        Map<String, Object> error = new HashMap<>();
        error.put("message" ,exception.getReason());

        //外层对象，保存错误码和内层对象
        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(body);
    }



}
