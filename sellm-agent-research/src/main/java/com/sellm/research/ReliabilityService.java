package com.sellm.research;

import org.springframework.stereotype.Component;

/** 信效度纯算法:Cronbach α / 分半信度(Spearman-Brown)/ 项总相关。总体方差(/N),约定一致。 */
@Component
public class ReliabilityService {

    public ReliabilityResult compute(double[][] scores) {
        validate(scores);
        int n = scores.length;          // 被试数
        int k = scores[0].length;       // 题目数
        ReliabilityResult r = new ReliabilityResult();
        r.setSubjectCount(n);
        r.setItemCount(k);

        if (n < 2) {
            r.getNotes().add("被试数不足(N<2),无法计算方差/相关");
            r.setItemTotal(new double[k]);
            return r;
        }

        // 被试总分
        double[] totals = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = 0; j < k; j++) s += scores[i][j];
            totals[i] = s;
        }

        // Cronbach α(需 K≥2)
        if (k >= 2) {
            double sumItemVar = 0;
            for (int j = 0; j < k; j++) {
                double[] col = column(scores, j);
                sumItemVar += variance(col);
            }
            double totalVar = variance(totals);
            if (totalVar == 0.0) {
                r.getNotes().add("总分方差为 0,Cronbach α 无法计算");
            } else {
                double alpha = (k / (double) (k - 1)) * (1 - sumItemVar / totalVar);
                r.setAlpha(alpha);
            }
        } else {
            r.getNotes().add("题数不足(K<2),Cronbach α 无法计算");
        }

        // 项总相关
        double[] itemTotal = new double[k];
        for (int j = 0; j < k; j++) {
            Double corr = pearson(column(scores, j), totals);
            if (corr == null) {
                itemTotal[j] = 0.0;
                r.getNotes().add("第 " + (j + 1) + " 题方差为 0,项总相关置 0");
            } else {
                itemTotal[j] = corr;
            }
        }
        r.setItemTotal(itemTotal);

        // 分半信度(Spearman-Brown):奇偶分半
        if (k >= 2) {
            double[] half1 = new double[n], half2 = new double[n];
            for (int i = 0; i < n; i++) {
                double a = 0, b = 0;
                for (int j = 0; j < k; j++) {
                    if (j % 2 == 0) a += scores[i][j]; else b += scores[i][j];
                }
                half1[i] = a; half2[i] = b;
            }
            Double rHalf = pearson(half1, half2);
            if (rHalf == null) {
                r.getNotes().add("分半得分方差为 0,分半信度无法计算");
            } else {
                r.setSplitHalf(2 * rHalf / (1 + rHalf));
            }
        }

        return r;
    }

    private void validate(double[][] scores) {
        if (scores == null || scores.length == 0 || scores[0] == null || scores[0].length == 0) {
            throw new IllegalArgumentException("分数矩阵不能为空");
        }
        int k = scores[0].length;
        for (double[] row : scores) {
            if (row == null || row.length != k) {
                throw new IllegalArgumentException("分数矩阵必须为矩形(各被试题数一致)");
            }
        }
    }

    private double[] column(double[][] m, int j) {
        double[] c = new double[m.length];
        for (int i = 0; i < m.length; i++) c[i] = m[i][j];
        return c;
    }

    /** 总体方差(/N)。 */
    private double variance(double[] x) {
        double mean = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return s / x.length;
    }

    /** Pearson 相关;任一方差为 0(分母 0)返回 null。 */
    private Double pearson(double[] x, double[] y) {
        int n = x.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy;
        }
        if (sxx == 0.0 || syy == 0.0) return null;
        return sxy / Math.sqrt(sxx * syy);
    }
}
