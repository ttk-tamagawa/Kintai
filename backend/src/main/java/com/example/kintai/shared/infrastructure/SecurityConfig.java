package com.example.kintai.shared.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * セキュリティ設定 — JWT認証 + ロールベース認可
 *
 * <p>JWTフィルターを UsernamePasswordAuthenticationFilter の前に配置し、
 * Bearer トークンによるステートレス認証を実現する。
 * メソッドレベルの認可は @PreAuthorize で各コントローラーに設定する。</p>
 *
 * <p>URL認可:
 * <ul>
 *   <li>/api/v1/auth/google → 認証不要（Google OAuth）</li>
 *   <li>/api/v1/auth/dev-login → dev/testプロファイル時のみ認証不要</li>
 *   <li>/api/** → 認証必須</li>
 *   <li>その他 → 全許可</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final Environment environment;

    public SecurityConfig(
            CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler,
            Environment environment) {
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.environment = environment;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS設定を適用
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // CSRF無効化（REST APIではトークンベース認証を使うため不要）
            .csrf(csrf -> csrf.disable())
            // セッション不使用（JWT認証で管理するため）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 認証エラー・認可エラーのハンドラーを設定する
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
            // URL別の認可ルールを設定する
            .authorizeHttpRequests(auth -> {
                // Google認証・リフレッシュエンドポイントは常に全許可
                auth.requestMatchers("/api/v1/auth/google", "/api/v1/auth/refresh").permitAll();
                // dev-loginはdev/testプロファイル時のみ許可（本番ではコントローラー自体が不在 + パスも認証必須）
                if (environment.matchesProfiles("dev | test")) {
                    auth.requestMatchers("/api/v1/auth/dev-login").permitAll();
                }
                // その他のAPIは認証必須
                auth.requestMatchers("/api/**").authenticated();
                // API以外（静的リソース等）は全許可
                auth.anyRequest().permitAll();
            })
            // JWTフィルターを認証フィルターの前に配置する
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
