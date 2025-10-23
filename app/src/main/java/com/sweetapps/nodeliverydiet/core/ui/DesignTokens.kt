package com.sweetapps.nodeliverydiet.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 앱 전역 디자인 토큰
 * - Elevation: 플랫 기본(0dp), 주목 버튼만 살짝(2dp)
 *   CARD = 0.dp: 기본 카드/컨테이너는 평면
 *   CARD_HIGH = 2.dp: 원형 주요 버튼(시작/중지 등)
 */
object AppAlphas {
    const val SurfaceTint: Float = 0.1f
}

object AppElevation {
    val ZERO = 0.dp
    val CARD = 0.dp
    val CARD_HIGH = 2.dp
}

/** 전역 Border 두께 토큰 */
object AppBorder {
    val Hairline = 0.75.dp
}

/**
 * 선택된 항목 하이라이트용 소프트 그레이 배경.
 * 배경이 흰색(#FFFFFF)일 때도 충분히 식별되도록 명도 대비를 높인 톤입니다.
 */
object AppColors {
    // 기존: Color(0xFFFBFBFC) -> 거의 흰색이라 시인성이 낮았음
    // 변경: 약한 블루-그레이 톤으로 대비 강화
    val SurfaceOverlaySoft = Color(0xFFE9EEF5)
}
