package io.github.nathan1227n.nodewalker.core.model;

/** Aritmética exata de custos não negativos, compartilhável pelos futuros solvers. */
public final class CostMath {
    private CostMath() {
    }

    /**
     * Soma dois custos sem saturação nem truncamento.
     * Um overflow deve interromper o cálculo e ser tratado como erro pelo chamador,
     * nunca como custo válido ou resultado ótimo.
     *
     * @param left custo não negativo (por exemplo, o acumulado)
     * @param right custo não negativo a adicionar
     * @return soma exata, inclusive {@link Long#MAX_VALUE} quando representável
     * @throws IllegalArgumentException se algum custo for negativo
     * @throws ArithmeticException se a soma exceder {@link Long#MAX_VALUE}
     */
    public static long addExact(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("Os custos devem ser não negativos: " + left + " e " + right + ".");
        }
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException cause) {
            ArithmeticException overflow = new ArithmeticException("A soma dos custos excede Long.MAX_VALUE: "
                    + left + " + " + right + ".");
            overflow.initCause(cause);
            throw overflow;
        }
    }
}
