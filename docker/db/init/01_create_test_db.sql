-- テスト用データベースを作成
-- （開発用の kintai DBはdocker-compose.ymlのPOSTGRES_DBで自動作成される）
CREATE DATABASE kintai_test
    OWNER kintai
    ENCODING 'UTF8'
    LC_COLLATE 'en_US.utf8'
    LC_CTYPE 'en_US.utf8';
