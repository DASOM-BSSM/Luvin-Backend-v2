package com.luvin.survey.scoring;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * 임의 정밀도 유리수. Python reference(classifier.py)의 fractions.Fraction과 동일한 정확한 연산을
 * 재현하기 위한 것으로, 점수/거리 계산 중 어떤 단계에서도 소수 반올림을 하지 않는다. 표시용 반올림은
 * {@link #toBigDecimal(int)}로 변환한 이후에만 한다.
 */
final class Rational implements Comparable<Rational> {

    static final Rational ZERO = of(0);

    private final BigInteger numerator;
    private final BigInteger denominator;

    private Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.signum() == 0) {
            throw new ArithmeticException("분모가 0입니다.");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (gcd.signum() != 0 && !gcd.equals(BigInteger.ONE)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    static Rational of(long value) {
        return new Rational(BigInteger.valueOf(value), BigInteger.ONE);
    }

    static Rational of(long numerator, long denominator) {
        return new Rational(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    Rational add(Rational other) {
        return new Rational(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    Rational subtract(Rational other) {
        return add(other.negate());
    }

    Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    Rational multiply(Rational other) {
        return new Rational(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    Rational multiply(long scalar) {
        return multiply(of(scalar));
    }

    Rational divide(Rational other) {
        return new Rational(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
    }

    static Rational max(Rational a, Rational b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** 표시/거리 계산용 소수 변환. 40자리 정밀도로 나눈 뒤 places자리 HALF_UP으로 반올림한다. */
    BigDecimal toBigDecimal(int places) {
        BigDecimal exact = new BigDecimal(numerator).divide(new BigDecimal(denominator), new MathContext(40));
        return exact.setScale(places, java.math.RoundingMode.HALF_UP);
    }

    /** 반올림하지 않은 정밀 소수(추가 연산·sqrt 입력용). */
    BigDecimal toBigDecimal() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), new MathContext(50));
    }

    @Override
    public int compareTo(Rational other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rational r && numerator.equals(r.numerator) && denominator.equals(r.denominator);
    }

    @Override
    public int hashCode() {
        return numerator.hashCode() * 31 + denominator.hashCode();
    }

    @Override
    public String toString() {
        return numerator + "/" + denominator;
    }
}
