package test.luvin_backend_v2.common.exception;

public class SurveyOptionNotFoundException extends RuntimeException {
    public SurveyOptionNotFoundException(Long optionId) {
        super("선택지를 찾을 수 없습니다. optionId=" + optionId);
    }
}