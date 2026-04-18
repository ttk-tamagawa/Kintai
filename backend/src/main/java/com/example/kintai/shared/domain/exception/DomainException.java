package com.example.kintai.shared.domain.exception;

/**
 * ドメイン例外の基底クラス
 *
 * <p>ビジネスロジック由来のエラーを表す抽象クラス。
 * すべてのドメイン固有例外はこのクラスを継承する。
 * catch (DomainException) でビジネスロジック由来のエラーだけをまとめて捕捉できる。</p>
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
