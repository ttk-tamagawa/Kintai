package com.example.kintai.shared.infrastructure;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT認証フィルター — リクエストごとにBearerトークンを検証する
 *
 * <p>Authorization ヘッダーから "Bearer {token}" 形式のJWTを抽出し、
 * 検証に成功した場合は SecurityContext にユーザー情報を設定する。
 * トークンがない場合や検証に失敗した場合は、未認証のままチェーンを通過させる。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Authorization ヘッダーからJWTトークンを抽出する
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // トークンが有効なら、クレームを解析してSecurityContextに設定する
            Claims claims = jwtTokenProvider.parseClaims(token);

            // ロール一覧を Spring Security の GrantedAuthority に変換する
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

            // 認証済みユーザー情報を作成する
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    claims.getSubject(),
                    claims.get("employeeId", String.class),
                    claims.get("departmentId", String.class),
                    claims.get("departmentName", String.class),
                    claims.get("name", String.class),
                    roles
            );

            // SecurityContext に認証情報を設定する
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT認証成功: email={}, roles={}", claims.getSubject(), roles);
        }

        // 次のフィルターへ処理を委譲する
        filterChain.doFilter(request, response);
    }

    /**
     * Authorization ヘッダーから Bearer トークンを抽出する
     *
     * @return トークン文字列。ヘッダーがない場合はnull
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
