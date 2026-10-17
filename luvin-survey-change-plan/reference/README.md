# 빵 유형 설문 설계 패키지

1. `SURVEY_AND_CLASSIFICATION.md`: 질문 20개·각 답변 3개·다중 변수 매핑·점수식·8개 유형 분류·한계와 검증 결과.
2. `survey_config.json`: 앱/Spring 구현에 사용할 원본 문항과 계산 설정.
3. `classifier.py`: 외부 의존성 없는 결정적 Python 참조 구현.
4. `sample_answers.json`, `example_result.json`: 전부 중간 답변을 고른 가상 입력과 출력.
5. `validation_report.json`: 구성 검사와 무작위 가상 응답 10,000개 확인 결과.

심리검사로 검증된 설문이 아닌 제품 개발용 초기안입니다. 구현 검사 통과와 분류 정확도/심리 타당성 검증은 다릅니다. 문항과 매핑을 변경하면 버전을 올리고 재검증하세요.
