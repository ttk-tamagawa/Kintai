package com.example.kintai.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 認証エントリーポイント — 未認証リクエストに対して 401 を返す
 *
 * <p>JWTトークンなし、または無効なトークンでアクセスした場合に
 * RFC 7807 形式のJSONでエラーレスポンスを返す。</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // ObjectMapper はDI不可（テスト環境でBean解決失敗するため直接生成）
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // RFC 7807 形式の 401 レスポンスをMapからJSON生成（URI値を安全にエスケープ）
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.example.com/errors/unauthorized");
        body.put("title", "Unauthorized");
        body.put("status", 401);
        body.put("detail", "認証が必要です");
        body.put("instance", request.getRequestURI());

        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
