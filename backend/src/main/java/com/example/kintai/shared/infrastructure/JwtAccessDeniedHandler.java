package com.example.kintai.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * アクセス拒否ハンドラー — 権限不足のリクエストに対して 403 を返す
 *
 * <p>認証済みだがロールが不足しているリクエストに対して
 * RFC 7807 形式のJSONでエラーレスポンスを返す。</p>
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    // ObjectMapper はDI不可（テスト環境でBean解決失敗するため直接生成）
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        // RFC 7807 形式の 403 レスポンスをMapからJSON生成（URI値を安全にエスケープ）
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://api.example.com/errors/forbidden");
        body.put("title", "Forbidden");
        body.put("status", 403);
        body.put("detail", "この操作を行う権限がありません");
        body.put("instance", request.getRequestURI());

        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
