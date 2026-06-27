package wonbin.financial.dto.candle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 컨플루언스 엔진에 투입되는 "후보 가격선" 한 개.
 * 모든 신호 소스(피벗/매물대/이동평균/추세선)는 일단 이 형태로 정규화된 뒤 한데 모여 군집화된다.
 * 이동평균선·추세선처럼 동적인 선은 '현재 시점으로 투영한 가격'을 price에 담는다.
 */
@Getter
@AllArgsConstructor
public class LevelCandidate {
    private double price;     // 후보 가격(동적선은 현재 투영가)
    private double weight;    // 신뢰 가중치(거래량·최근성·소스 기본가중 반영)
    private String source;    // 근거 태그: "pivot", "ma20", "trendline", "volumePOC" 등
    private int touchCount;   // 터치 횟수(매물대/이평선처럼 해당 없으면 0)
}
