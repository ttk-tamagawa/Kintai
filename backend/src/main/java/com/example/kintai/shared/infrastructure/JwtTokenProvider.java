package com.example.kintai.shared.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWTトークンの生成・検証・クレーム抽出を担うコンポーネント
 *
 * <p>HS256アルゴリズムで署名し、ペイロードに以下のクレームを含める:
 * sub(email), employeeId, departmentId, departmentName, name, roles</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(JwtProperties properties) {
        // シークレットキーからHS256用の署名キーを生成
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
        this.refreshExpirationMs = properties.refreshExpirationMs();
    }

    /**
     * 認証情報からJWTトークンを生成する
     *
     * @param user 認証済みユーザー情報
     * @return 署名済みJWTトークン文字列
     */
    public String generateToken(AuthenticatedUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        // JWTペイロードにユーザー情報を埋め込む
        return Jwts.builder()
                .subject(user.email())
                .claim("employeeId", user.employeeId())
                .claim("departmentId", user.departmentId())
                .claim("departmentName", user.departmentName())
                .claim("name", user.name())
                .claim("roles", user.roles())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * JWTトークンを検証し、有効であればtrueを返す
     *
     * @param token 検証対象のJWTトークン
     * @return 有効ならtrue、無効・期限切れ・改ざんならfalse
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT検証失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWTトークンからメールアドレス（sub）を取得する
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * JWTトークンからロール一覧を取得する
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    /**
     * JWTトークンから全クレームを抽出する
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * リフレッシュトークンを生成する（最小クレーム: email + tokenType）
     *
     * @param email ユーザーのメールアドレス
     * @return 署名済みリフレッシュトークン文字列
     */
    public String generateRefreshToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);

        // リフレッシュトークンにはemailとtokenTypeのみ含める
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * リフレッシュトークンを検証する（署名 + 有効期限 + tokenType="refresh" を確認）
     *
     * @param token 検証対象のリフレッシュトークン
     * @return 有効なリフレッシュトークンならtrue
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            // tokenType が "refresh" であることを確認（アクセストークンの流用を防止）
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return TOKEN_TYPE_REFRESH.equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("リフレッシュトークン検証失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * リフレッシュトークンからメールアドレスを取得する
     *
     * @param token リフレッシュトークン
     * @return メールアドレス
     */
    public String getEmailFromRefreshToken(String token) {
        return parseClaims(token).getSubject();
    }
}
