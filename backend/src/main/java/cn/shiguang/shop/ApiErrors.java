// AI-assisted implementation with Codex.
package cn.shiguang.shop;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ProblemDetail uploadTooLarge(Exception error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过4MB，请压缩后重试。");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请检查必填项、输入格式和数值范围。");
  }
}
