package test.luvin_backend_v2.common.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Long memberId) {
        super("사용자를 찾을 수 없습니다. memberId=" + memberId);
    }
}
