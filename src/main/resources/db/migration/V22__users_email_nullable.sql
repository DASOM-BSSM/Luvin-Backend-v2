-- Apple 로그인은 이메일이 없을 수 있어(identityToken에 email이 빠지는 경우) users.email을 비워 둘 수 있게 한다.
-- UNIQUE는 그대로 둔다(PostgreSQL UNIQUE는 NULL을 여러 개 허용한다). users는 Hibernate가 만드는 테이블이라 빈 DB(CI)는
-- 조건부 no-op이다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'email'
                 AND is_nullable = 'NO') THEN
        ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
    END IF;
END $$;
