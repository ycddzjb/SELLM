package com.sellm.research;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReliabilityServiceTest {

    private final ReliabilityService svc = new ReliabilityService();

    @Test
    void cronbach_alpha_对标手算值() {
        // 4 被试 × 3 题。手算:
        // 题方差(总体/N): 列方差之和;总分方差(总体/N)
        // 被试总分: [9,6,9,6]? 用一个非平凡矩阵
        double[][] scores = {
            {5, 4, 3},
            {4, 4, 3},
            {3, 2, 2},
            {2, 1, 1}
        };
        ReliabilityResult r = svc.compute(scores);
        assertEquals(3, r.getItemCount());
        assertEquals(4, r.getSubjectCount());
        assertNotNull(r.getAlpha());
        // α 应在合理范围(此矩阵题间高度一致,α 偏高);对标重算值
        // 总体方差(/N):ΣitemVar=3.625, totalVar=10.25, α=(3/2)(1−3.625/10.25)=0.969512(容差 1e-3)
        assertEquals(0.9695, r.getAlpha(), 1e-3);
    }

    @Test
    void 项总相关每题一个值且范围合理() {
        double[][] scores = {
            {5, 4, 3},
            {4, 4, 3},
            {3, 2, 2},
            {2, 1, 1}
        };
        ReliabilityResult r = svc.compute(scores);
        assertEquals(3, r.getItemTotal().length);
        for (double v : r.getItemTotal()) {
            assertTrue(v >= -1.0 && v <= 1.0, "相关应在 [-1,1]");
        }
    }

    @Test
    void 分半信度可算且范围合理() {
        double[][] scores = {
            {5, 4, 3, 5},
            {4, 4, 3, 4},
            {3, 2, 2, 3},
            {2, 1, 1, 2}
        };
        ReliabilityResult r = svc.compute(scores);
        assertNotNull(r.getSplitHalf());
        assertTrue(r.getSplitHalf() <= 1.0 + 1e-9);
    }

    @Test
    void 题数不足alpha为null并有note() {
        double[][] scores = { {5}, {4}, {3} }; // K=1
        ReliabilityResult r = svc.compute(scores);
        assertNull(r.getAlpha());
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 被试不足全null并有note() {
        double[][] scores = { {5, 4, 3} }; // N=1
        ReliabilityResult r = svc.compute(scores);
        assertNull(r.getAlpha());
        assertNull(r.getSplitHalf());
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 某题零方差该项总相关为NaN置null安全() {
        // 第3题全同分 → 零方差 → 项总相关分母0
        double[][] scores = {
            {5, 4, 2},
            {4, 3, 2},
            {3, 2, 2},
            {2, 1, 2}
        };
        ReliabilityResult r = svc.compute(scores);
        // itemTotal[2] 应为 NaN 或被安全处理(不抛异常);约定:零方差项置 Double.NaN 不可,改置 0 或标记
        // 本设计:零方差相关置 0.0 并加 note(或单独 nullable 数组);这里断言不抛 + 长度对
        assertEquals(3, r.getItemTotal().length);
        assertFalse(r.getNotes().isEmpty());
    }

    @Test
    void 非矩形矩阵抛IllegalArgumentException() {
        double[][] scores = { {5, 4, 3}, {4, 3} }; // 行长不一
        assertThrows(IllegalArgumentException.class, () -> svc.compute(scores));
    }

    @Test
    void 空矩阵抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> svc.compute(new double[0][]));
    }
}
