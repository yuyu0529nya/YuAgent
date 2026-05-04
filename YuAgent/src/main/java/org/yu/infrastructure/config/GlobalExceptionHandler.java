package org.yu.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yu.infrastructure.exception.BusinessException;
import org.yu.infrastructure.exception.EntityNotFoundException;
import org.yu.infrastructure.exception.ParamValidationException;
import org.yu.interfaces.api.common.Result;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        logger.error("Business exception: {}, URL: {}", e.getMessage(), request.getRequestURL(), e);
        return Result.error(400, e.getMessage());
    }

    @ExceptionHandler(ParamValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleParamValidationException(ParamValidationException e, HttpServletRequest request) {
        logger.error("Param validation exception: {}, URL: {}", e.getMessage(), request.getRequestURL(), e);
        return Result.badRequest(e.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleEntityNotFoundException(EntityNotFoundException e, HttpServletRequest request) {
        logger.error("Entity not found: {}, URL: {}", e.getMessage(), request.getRequestURL(), e);
        return Result.notFound(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String errorMessage = fieldErrors.stream().map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));

        logger.error("Method argument validation exception: {}, URL: {}", errorMessage, request.getRequestURL(), e);
        return Result.badRequest(errorMessage);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String errorMessage = fieldErrors.stream().map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        logger.error("Bind exception: {}, URL: {}", errorMessage, request.getRequestURL(), e);
        return Result.badRequest(errorMessage);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Object handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e, HttpServletRequest request) {
        logger.error("Async request timeout: {}", request.getRequestURL(), e);

        String contentType = request.getContentType();
        if (contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"请求超时，请重试\",\"done\":true}"));
                emitter.complete();
            } catch (IOException ex) {
                logger.error("Failed to send SSE timeout message", ex);
            }
            return emitter;
        }

        return Result.error(503, "请求处理超时，请重试");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        logger.error("Unexpected exception, URL: {}", request.getRequestURL(), e);
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return Result.serverError("服务器内部错误");
        }
        return Result.serverError("服务器内部错误: " + message);
    }
}
