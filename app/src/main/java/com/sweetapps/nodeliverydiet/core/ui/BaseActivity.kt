package com.sweetapps.nodeliverydiet.core.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sweetapps.nodeliverydiet.core.ui.theme.AlcoholicTimerTheme
import com.sweetapps.nodeliverydiet.feature.level.LevelActivity
import com.sweetapps.nodeliverydiet.feature.profile.NicknameEditActivity
import com.sweetapps.nodeliverydiet.feature.run.RunActivity
import com.sweetapps.nodeliverydiet.feature.settings.SettingsActivity
import com.sweetapps.nodeliverydiet.feature.start.StartActivity
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import androidx.compose.ui.res.colorResource
import com.sweetapps.nodeliverydiet.R
import android.view.MotionEvent
import android.view.View
import com.sweetapps.nodeliverydiet.core.ui.AppBorder

// 전역 입력 잠금 요청을 위한 CompositionLocal
val LocalRequestGlobalLock = compositionLocalOf<(Long) -> Unit> { { _: Long -> } }

abstract class BaseActivity : ComponentActivity() {
    private var nicknameState = mutableStateOf("")

    // Ensure declaration before first usage
    private fun getNickname(): String {
        val sharedPref = getSharedPreferences("user_settings", MODE_PRIVATE)
        val legacy = "알중이1"
        val newDefault = "내일은 날씬이1"
        val stored = sharedPref.getString("nickname", null)
        val resolved = if (stored.isNullOrBlank() || stored == legacy) newDefault else stored
        if (resolved != stored) {
            // 저장된 레거시 값을 새 기본값으로 교체 저장
            sharedPref.edit().putString("nickname", resolved).apply()
        }
        return resolved
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge: 시스템 창 적합 해제 후 Compose 인셋만 사용
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Splash 설치 제거: 런처 액티비티(StartActivity)에서만 수행
        super.onCreate(savedInstanceState)
        nicknameState.value = getNickname()
    }

    override fun onResume() {
        super.onResume()
        nicknameState.value = getNickname()
    }

    // Returns the drawer menu title that matches current screen, or null if none
    private fun currentDrawerSelection(): String? = when (this) {
        is RunActivity, is StartActivity,
        is com.sweetapps.nodeliverydiet.feature.run.QuitActivity -> "노딜리버리"
        is com.sweetapps.nodeliverydiet.feature.records.RecordsActivity,
        is com.sweetapps.nodeliverydiet.feature.records.AllRecordsActivity,
        is com.sweetapps.nodeliverydiet.feature.detail.DetailActivity -> "기록"
        is LevelActivity -> "레벨"
        is SettingsActivity -> "설정"
        is com.sweetapps.nodeliverydiet.feature.about.AboutActivity,
        is com.sweetapps.nodeliverydiet.feature.about.AboutLicensesActivity -> "앱 정보"
        else -> null
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun BaseScreen(
        applyBottomInsets: Boolean = true,
        applySystemBars: Boolean = true,
        showBackButton: Boolean = false,
        onBackClick: (() -> Unit)? = null,
        content: @Composable () -> Unit
    ) {
        AlcoholicTimerTheme(darkTheme = false, applySystemBars = applySystemBars) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val currentNickname by nicknameState

            // 입력/키보드 컨트롤러
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            // 전역 입력 차단(설정 화면 제외)
            val enableGlobalOverlay = this !is SettingsActivity
            var globalInputLocked by remember { mutableStateOf(false) }
            var lockDurationMs by remember { mutableStateOf(250L) }
            var lockTick by remember { mutableIntStateOf(0) }

            val requestGlobalLock: (Long) -> Unit = remember(enableGlobalOverlay) {
                { duration ->
                    if (enableGlobalOverlay) {
                        globalInputLocked = true
                        lockDurationMs = duration
                        lockTick++
                    }
                }
            }

            LaunchedEffect(lockTick) {
                if (globalInputLocked) {
                    delay(lockDurationMs)
                    globalInputLocked = false
                }
            }

            val blurRadius by animateFloatAsState(
                targetValue = if (drawerState.targetValue == DrawerValue.Open) 8f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "blur"
            )

            // 드로어 열림/애니메이션/닫힘 직후 입력 완전 차단 가드
            var drawerInputGuardActive by remember { mutableStateOf(false) }
            val drawerGuardGraceMs = 200L
            LaunchedEffect(drawerState) {
                snapshotFlow { Triple(drawerState.isAnimationRunning, drawerState.currentValue, drawerState.targetValue) }
                    .collect { (isAnimating, current, target) ->
                        // 드로어 열리기 시작하면 즉시 포커스 해제 + 키보드 숨김 (제스처 오픈 포함)
                        if (isAnimating || target != DrawerValue.Closed || current != DrawerValue.Closed) {
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                            drawerInputGuardActive = true
                        } else {
                            // 닫힘이 안정화된 직후에도 잠시 입력을 소비해 클릭 스루 방지
                            drawerInputGuardActive = true
                            delay(drawerGuardGraceMs)
                            drawerInputGuardActive = false
                        }
                    }
            }

            CompositionLocalProvider(LocalRequestGlobalLock provides requestGlobalLock) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .background(Color.White),
                            drawerContainerColor = Color.Transparent,
                            drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        ) {
                            DrawerMenu(
                                nickname = currentNickname,
                                selectedItem = currentDrawerSelection(),
                                onNicknameClick = {
                                    // 전역 입력 잠금 요청
                                    requestGlobalLock(300)
                                    scope.launch {
                                        drawerState.close()
                                        var navigated = false
                                        snapshotFlow { drawerState.isAnimationRunning }
                                            .collect { isAnimating ->
                                                if (!isAnimating && drawerState.currentValue == DrawerValue.Closed && !navigated) {
                                                    navigated = true
                                                    navigateToNicknameEdit()
                                                    return@collect
                                                }
                                            }
                                    }
                                },
                                onItemSelected = { menuItem ->
                                    // 전역 입력 잠금 요청
                                    requestGlobalLock(300)
                                    scope.launch {
                                        drawerState.close()
                                        snapshotFlow { drawerState.isAnimationRunning }
                                            .collect { isAnimating ->
                                                if (!isAnimating && drawerState.currentValue == DrawerValue.Closed) {
                                                    handleMenuSelection(menuItem)
                                                    return@collect
                                                }
                                            }
                                    }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (applySystemBars) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier),
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                color = Color.White
                            ) {
                                Column {
                                    TopAppBar(
                                        title = {
                                            CompositionLocalProvider(
                                                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.2f)
                                            ) {
                                                Text(
                                                    text = getScreenTitle(),
                                                    color = Color(0xFF2C3E50),
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color.Transparent,
                                            titleContentColor = Color(0xFF2C3E50),
                                            navigationIconContentColor = Color(0xFF2C3E50),
                                            actionIconContentColor = Color(0xFF2C3E50)
                                        ),
                                        navigationIcon = {
                                            Surface(
                                                modifier = Modifier.padding(8.dp).size(48.dp),
                                                shape = CircleShape,
                                                color = Color(0xFFF8F9FA),
                                                shadowElevation = 2.dp
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        // 전역 입력 잠금 + 포커스/키보드 정리 후 드로어 동작
                                                        requestGlobalLock(300)
                                                        focusManager.clearFocus(force = true)
                                                        keyboardController?.hide()
                                                        if (showBackButton) {
                                                            onBackClick?.invoke() ?: run { this@BaseActivity.onBackPressedDispatcher.onBackPressed() }
                                                        } else {
                                                            scope.launch { drawerState.open() }
                                                        }
                                                    }
                                                ) {
                                                    if (showBackButton) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                            contentDescription = "뒤로가기",
                                                            tint = Color(0xFF2C3E50),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Filled.Menu,
                                                            contentDescription = "메뉴",
                                                            tint = Color(0xFF2C3E50),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    // Global subtle divider under app bar
                                    HorizontalDivider(
                                        thickness = AppBorder.Hairline,
                                        color = colorResource(id = R.color.color_border_light)
                                    )
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { paddingValues ->
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 기존 Color.White에서 전역 배경색(연회색)으로 변경
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            val insetModifier = if (applyBottomInsets) {
                                // 하단 safe area는 전역 적용하지 않고, 수평만 적용
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                )
                            } else {
                                Modifier
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                                    .then(insetModifier)
                                    .blur(radius = blurRadius.dp)
                            ) { content() }

                            // 전역 입력 차단 오버레이(설정 화면 제외) + 드로어 가드: 모든 입력 및 접근성 포커스 차단
                            if ((enableGlobalOverlay && globalInputLocked) || drawerInputGuardActive) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clearAndSetSemantics { },
                                    factory = { context ->
                                        View(context).apply {
                                            // 모든 MotionEvent을 소비하고 포커스를 가질 수 있도록 설정
                                            isClickable = true
                                            isFocusable = true
                                            setOnTouchListener { v, e ->
                                                if (e.action == MotionEvent.ACTION_UP) {
                                                    v.performClick()
                                                }
                                                true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleMenuSelection(menuItem: String) {
        when (menuItem) {
            "노딜리버리" -> {
                // 진행 중 여부에 따라 메인 홈(Start/Run)으로 이동
                navigateToMainHome()
            }
            "기록" -> if (this !is com.sweetapps.nodeliverydiet.feature.records.RecordsActivity) {
                navigateToActivity(com.sweetapps.nodeliverydiet.feature.records.RecordsActivity::class.java)
            }
            "레벨" -> if (this !is LevelActivity) navigateToActivity(LevelActivity::class.java)
            "설정" -> if (this !is SettingsActivity) navigateToActivity(SettingsActivity::class.java)
            "앱 정보" -> if (this !is com.sweetapps.nodeliverydiet.feature.about.AboutActivity) {
                navigateToActivity(com.sweetapps.nodeliverydiet.feature.about.AboutActivity::class.java)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass).apply {
            // 드로어 네비게이션: 기존 인스턴스가 있으면 재사용하며 그 위 스택 제거
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    private fun navigateToNicknameEdit() {
        val intent = Intent(this, NicknameEditActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    protected fun navigateToMainHome() {
        val sharedPref = getSharedPreferences("user_settings", MODE_PRIVATE)
        val startTime = sharedPref.getLong("start_time", 0L)
        val isRunning = startTime > 0L

        val target = if (isRunning) RunActivity::class.java else StartActivity::class.java

        // 이미 해당 메인 홈이라면 아무것도 하지 않음
        if ((isRunning && this is RunActivity) || (!isRunning && this is StartActivity)) {
            return
        }

        @Suppress("DEPRECATION")
        val intent = Intent(this, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (!isRunning) {
                putExtra("skip_splash", true)
            }
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        // 현재 화면 종료하여 스택 정리
        finish()
    }

    protected abstract fun getScreenTitle(): String
}

@Composable
fun DrawerMenu(
    nickname: String,
    selectedItem: String?,
    onNicknameClick: () -> Unit,
    onItemSelected: (String) -> Unit
) {
    val menuItems = listOf(
        "노딜리버리" to Icons.Filled.PlayArrow,
        "기록" to Icons.AutoMirrored.Filled.List,
        "레벨" to Icons.Filled.Star
    )
    val settingsItems = listOf(
        "설정" to Icons.Filled.Settings,
        "앱 정보" to Icons.Filled.Info
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onNicknameClick() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                    shadowElevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "아바타",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
                        Text(
                            text = nickname,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "프로필 편집",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 1.dp
        )
        Text(
            text = "메뉴",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        menuItems.forEach { (title, icon) ->
            val isSelected = title == selectedItem
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onItemSelected(title) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.2f else 0.08f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 1.dp
        )
        Text(
            text = "설정",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        settingsItems.forEach { (title, icon) ->
            val isSelected = title == selectedItem
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onItemSelected(title) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.2f else 0.08f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
