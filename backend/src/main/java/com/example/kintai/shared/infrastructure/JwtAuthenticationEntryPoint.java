package com.example.kintai.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * 認証エントリーポイント — 未認証リクエストに対して 401 を返す
 *
 * <p>JWTトークンなし、または無効なトークンでアクセスした場合に
 * RFC 7807 形式の ProblemDetail でエラーレスポンスを返す。</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // RFC 7807 形式の 401 レスポンスを生成する
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "認証が必要です");
        problemDetail.setTitle("Unauthorized");
        problemDetail.setType(URI.create("https://api.example.com/errors/unauthorized"));
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        // JSON形式でレスポンスを書き出す
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problemDetail);
    }
}
