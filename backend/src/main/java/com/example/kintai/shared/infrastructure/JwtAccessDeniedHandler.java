package com.example.kintai.shared.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * アクセス拒否ハンドラー — 権限不足のリクエストに対して 403 を返す
 *
 * <p>認証済みだがロールが不足しているリクエストに対して
 * RFC 7807 形式のJSONでエラーレスポンスを返す。</p>
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        // RFC 7807 形式の 403 レスポンスをJSON文字列として書き出す
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"https://api.example.com/errors/forbidden",\
                "title":"Forbidden",\
                "status":403,\
                "detail":"この操作を行う権限がありません",\
                "instance":"%s"}""".formatted(request.getRequestURI()));
    }
}
