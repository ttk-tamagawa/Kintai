package com.example.kintai.shared.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 認証エントリーポイント — 未認証リクエストに対して 401 を返す
 *
 * <p>JWTトークンなし、または無効なトークンでアクセスした場合に
 * RFC 7807 形式のJSONでエラーレスポンスを返す。</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // RFC 7807 形式の 401 レスポンスをJSON文字列として書き出す
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"type":"https://api.example.com/errors/unauthorized",\
                "title":"Unauthorized",\
                "status":401,\
                "detail":"認証が必要です",\
                "instance":"%s"}""".formatted(request.getRequestURI()));
    }
}
