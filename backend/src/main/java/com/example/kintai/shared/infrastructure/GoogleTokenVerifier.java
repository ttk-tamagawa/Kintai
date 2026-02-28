package com.example.kintai.shared.infrastructure;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Google IDトークン検証サービス
 *
 * <p>Google OAuth 2.0 のIDトークンを検証し、メールアドレスを抽出する。
 * Google Workspace SSO でログインしたユーザーの認証に使用する。</p>
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        // Google IDトークン検証器を初期化する
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    /**
     * Google IDトークンを検証し、メールアドレスを返す
     *
     * @param idTokenString Google から受け取った ID トークン
     * @return メールアドレス。検証失敗時はnull
     */
    public String verifyAndGetEmail(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                log.debug("Google IDトークン検証成功: email={}", email);
                return email;
            }
        } catch (Exception e) {
            log.warn("Google IDトークン検証失敗: {}", e.getMessage());
        }
        return null;
    }
}
