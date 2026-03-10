-- ============================================
-- 初期化スクリプト（PostgreSQL コンテナ初回起動時のみ実行）
-- ============================================

-- テスト用データベースを作成
-- （開発用の kintai DBはdocker-compose.ymlのPOSTGRES_DBで自動作成される）
CREATE DATABASE kintai_test
    OWNER kintai
    ENCODING 'UTF8'
    LC_COLLATE 'en_US.utf8'
    LC_CTYPE 'en_US.utf8';

-- === タイムゾーン設定 ===
-- kintai DB（開発用）
ALTER DATABASE kintai SET timezone TO 'Asia/Tokyo';

-- kintai_test DB（テスト用）
ALTER DATABASE kintai_test SET timezone TO 'Asia/Tokyo';
