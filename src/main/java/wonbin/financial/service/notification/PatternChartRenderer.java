package wonbin.financial.service.notification;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.springframework.stereotype.Component;
import wonbin.financial.constant.PatternType;
import wonbin.financial.dto.candle.PivotPoint;
import wonbin.financial.dto.candle.pattern.DetectedPatternDto;
import wonbin.financial.service.candle.pattern.PatternContext;

/**
 * 탐지된 패턴을 캔들 차트 PNG로 렌더링한다(카카오 feed 템플릿 첨부용).
 * 캔들 위에 ZigZag 스윙 라인과 넥라인/목표가/무효화 수평선, 돌파 시점 수직선을 그려
 * 왜 해당 패턴으로 판정됐는지 시각적으로 보여준다.
 * 한글 라벨을 사용하므로 서버(Linux 배포 시)에 한글 글꼴이 설치돼 있어야 한다.
 */
@Component
public class PatternChartRenderer {

    /** 카카오 feed 이미지 권장 비율 2:1 */
    public static final int WIDTH = 800;
    public static final int HEIGHT = 400;

    // 국내 관례: 상승 = 빨강, 하락 = 파랑
    private static final Color UP = new Color(0xD2, 0x4F, 0x45);
    private static final Color DOWN = new Color(0x12, 0x61, 0xC4);
    private static final Color WICK = new Color(0x4B, 0x55, 0x63);
    private static final Color NECKLINE = new Color(0x11, 0x18, 0x27);
    private static final Color TARGET = new Color(0x18, 0x80, 0x38);
    private static final Color INVALIDATION = new Color(0xE8, 0x71, 0x0A);
    private static final Color ZIGZAG = new Color(0x9C, 0xA3, 0xAF);
    private static final Color GRID = new Color(0xE5, 0xE7, 0xEB);
    private static final Color AXIS_INK = new Color(0x6B, 0x72, 0x80);
    private static final Color LABEL_BG = new Color(255, 255, 255, 210);

    private static final BasicStroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 1f, new float[]{6f, 4f}, 0f);
    private static final BasicStroke ZIGZAG_STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 14);

    public byte[] render(DetectedPatternDto dto, PatternContext ctx) {
        XYPlot plot = new XYPlot(buildDataset(ctx), buildDomainAxis(), buildRangeAxis(dto, ctx),
                buildCandleRenderer());
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(GRID);
        plot.setOutlineVisible(false);

        addZigZag(plot, ctx);
        plot.addRangeMarker(priceMarker(dto.getNecklinePrice(), "넥라인", NECKLINE));
        plot.addRangeMarker(priceMarker(dto.getTargetPrice(), "목표가", TARGET));
        plot.addRangeMarker(priceMarker(dto.getInvalidationPrice(), "무효화", INVALIDATION));
        plot.addDomainMarker(breakoutMarker(dto));

        JFreeChart chart = new JFreeChart(buildTitle(dto), TITLE_FONT, plot, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setPadding(new RectangleInsets(8, 8, 8, 8));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ChartUtils.writeChartAsPNG(out, chart, WIDTH, HEIGHT);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("차트 PNG 인코딩 실패", e);
        }
    }

    /** 휴장일(null 봉)은 건너뛰고 OHLC 배열을 구성한다. */
    private DefaultHighLowDataset buildDataset(PatternContext ctx) {
        List<Double> opens = ctx.getOpens();
        List<Double> highs = ctx.getHighs();
        List<Double> lows = ctx.getLows();
        List<Double> closes = ctx.getCloses();
        List<Long> timestamps = ctx.getTimestamps();
        if (opens == null || highs == null || lows == null) {
            throw new IllegalStateException("OHLC 데이터가 없어 차트를 그릴 수 없습니다.");
        }

        List<Date> dates = new ArrayList<>();
        List<Double> o = new ArrayList<>();
        List<Double> h = new ArrayList<>();
        List<Double> l = new ArrayList<>();
        List<Double> c = new ArrayList<>();
        int size = Math.min(closes.size(), timestamps.size());
        for (int i = 0; i < size; i++) {
            if (opens.get(i) == null || highs.get(i) == null
                    || lows.get(i) == null || closes.get(i) == null) {
                continue;
            }
            dates.add(new Date(timestamps.get(i) * 1000L));
            o.add(opens.get(i));
            h.add(highs.get(i));
            l.add(lows.get(i));
            c.add(closes.get(i));
        }
        if (dates.size() < 2) {
            throw new IllegalStateException("차트를 그릴 봉 데이터가 부족합니다.");
        }
        return new DefaultHighLowDataset(ctx.getSymbol(), dates.toArray(new Date[0]),
                toArray(h), toArray(l), toArray(o), toArray(c), new double[dates.size()]);
    }

    private double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private CandlestickRenderer buildCandleRenderer() {
        CandlestickRenderer renderer = new CandlestickRenderer();
        renderer.setDrawVolume(false);
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        renderer.setUpPaint(UP);
        renderer.setDownPaint(DOWN);
        renderer.setUseOutlinePaint(false);
        renderer.setSeriesPaint(0, WICK); // 꼬리(고가-저가 선) 색
        return renderer;
    }

    private DateAxis buildDomainAxis() {
        DateAxis axis = new DateAxis();
        axis.setLowerMargin(0.01);
        axis.setUpperMargin(0.02);
        axis.setTickLabelFont(LABEL_FONT);
        axis.setTickLabelPaint(AXIS_INK);
        axis.setAxisLinePaint(GRID);
        axis.setTickMarksVisible(false);
        return axis;
    }

    /** 목표가/무효화선이 봉 범위 밖에 있어도 보이도록 y축 범위를 직접 계산한다. */
    private NumberAxis buildRangeAxis(DetectedPatternDto dto, PatternContext ctx) {
        double min = Math.min(dto.getTargetPrice(),
                Math.min(dto.getNecklinePrice(), dto.getInvalidationPrice()));
        double max = Math.max(dto.getTargetPrice(),
                Math.max(dto.getNecklinePrice(), dto.getInvalidationPrice()));
        int size = Math.min(ctx.getCloses().size(), ctx.getTimestamps().size());
        for (int i = 0; i < size; i++) {
            Double high = ctx.getHighs().get(i);
            Double low = ctx.getLows().get(i);
            if (high != null) {
                max = Math.max(max, high);
            }
            if (low != null) {
                min = Math.min(min, low);
            }
        }
        double pad = (max - min) * 0.06;

        NumberAxis axis = new NumberAxis();
        axis.setAutoRangeIncludesZero(false);
        axis.setRange(min - pad, max + pad);
        axis.setTickLabelFont(LABEL_FONT);
        axis.setTickLabelPaint(AXIS_INK);
        axis.setAxisLinePaint(GRID);
        axis.setTickMarksVisible(false);
        return axis;
    }

    /** ZigZag 스윙을 잇는 선 — 패턴의 형태(천장/바닥/어깨)를 드러낸다. */
    private void addZigZag(XYPlot plot, PatternContext ctx) {
        List<PivotPoint> swings = ctx.getSwings();
        List<Long> timestamps = ctx.getTimestamps();
        for (int i = 1; i < swings.size(); i++) {
            PivotPoint from = swings.get(i - 1);
            PivotPoint to = swings.get(i);
            if (from.getIndex() >= timestamps.size() || to.getIndex() >= timestamps.size()) {
                continue;
            }
            plot.addAnnotation(new XYLineAnnotation(
                    timestamps.get(from.getIndex()) * 1000.0, from.getPrice(),
                    timestamps.get(to.getIndex()) * 1000.0, to.getPrice(),
                    ZIGZAG_STROKE, ZIGZAG));
        }
    }

    private ValueMarker priceMarker(double price, String name, Color color) {
        ValueMarker marker = new ValueMarker(price, color, DASHED);
        marker.setLabel(String.format("%s $%.2f", name, price));
        marker.setLabelFont(LABEL_FONT);
        marker.setLabelPaint(color);
        marker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
        marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
        marker.setLabelOffset(new RectangleInsets(2, 0, 2, 4));
        marker.setLabelBackgroundColor(LABEL_BG); // 기본값(반투명 회색)은 글자를 탁하게 만든다
        return marker;
    }

    private ValueMarker breakoutMarker(DetectedPatternDto dto) {
        String label = dto.getPatternType().isBullish() ? "돌파" : "이탈";
        ValueMarker marker = new ValueMarker(dto.getBreakoutTimestamp() * 1000.0, AXIS_INK, DASHED);
        marker.setLabel(label);
        marker.setLabelFont(LABEL_FONT);
        marker.setLabelPaint(AXIS_INK);
        // 수평선 라벨들이 우측 상단에 몰리므로 돌파 라벨은 선 하단 왼쪽에 붙인다
        marker.setLabelAnchor(RectangleAnchor.BOTTOM_LEFT);
        marker.setLabelTextAnchor(TextAnchor.BOTTOM_RIGHT);
        marker.setLabelOffset(new RectangleInsets(0, 0, 6, 4));
        marker.setLabelBackgroundColor(LABEL_BG);
        return marker;
    }

    private String buildTitle(DetectedPatternDto dto) {
        PatternType type = dto.getPatternType();
        String direction = type.isBullish() ? "상승 반전" : "하락 반전";
        return String.format("%s · %s (%s)", dto.getSymbol(), type.getKoreanName(), direction);
    }
}
