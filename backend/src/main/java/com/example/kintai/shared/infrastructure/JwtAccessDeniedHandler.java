package com.example.kintai.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * アクセス拒否ハンドラー — 権限不足のリクエストに対して 403 を返す
 *
 * <p>認証済みだがロールが不足しているリクエストに対して
 * RFC 7807 形式の ProblemDetail でエラーレスポンスを返す。</p>
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        // RFC 7807 形式の 403 レスポンスを生成する
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "この操作を行う権限がありません");
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.example.com/errors/forbidden"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        // JSON形式でレスポンスを書き出す
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
