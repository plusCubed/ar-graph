package com.pluscubed.graph;

import org.mariuszgromada.math.mxparser.Expression;

public abstract class Utils {

    public static float evaluateExpression(String exp) {
        return (float) new Expression(exp).calculate();
    }
}
