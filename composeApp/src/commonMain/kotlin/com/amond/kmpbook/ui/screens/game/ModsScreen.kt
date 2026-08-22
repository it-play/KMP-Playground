package com.amond.kmpbook.ui.screens.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amond.kmpbook.modding.model.InstalledMod
import com.amond.kmpbook.modding.model.ModCapability
import com.amond.kmpbook.modding.model.ModLoadIssue
import com.amond.kmpbook.modding.model.ModSettingDefinition
import com.amond.kmpbook.modding.model.ModSettingType
import com.amond.kmpbook.modding.storage.loadModCoverImage
import com.amond.kmpbook.ui.components.LedgerDivider
import com.amond.kmpbook.ui.components.LedgerPanel
import com.amond.kmpbook.ui.components.LoadingFinancialFact
import com.amond.kmpbook.ui.components.MarketButton
import com.amond.kmpbook.ui.components.MarketButtonVariant
import com.amond.kmpbook.ui.components.MarketCheckRow
import com.amond.kmpbook.ui.components.StatusLabel
import com.amond.kmpbook.ui.components.VisibleVerticalScrollbar
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketComponentSize
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun ModsScreen(
    mods: List<InstalledMod>,
    issues: List<ModLoadIssue>,
    statusMessage: String?,
    selectedModId: String?,
    isScanning: Boolean,
    onToggleMod: (InstalledMod, Boolean) -> Unit,
    onSelectMod: (InstalledMod) -> Unit,
    onRefresh: () -> Unit,
    onOpenModsDirectory: () -> Unit,
    isMutating: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val controlsBusy = isScanning || isMutating
    val modListState = rememberLazyListState()

    Column(modifier.fillMaxSize()) {
        ModsHeader(
            isScanning = isScanning,
            isMutating = isMutating,
            onRefresh = onRefresh,
            onOpenModsDirectory = onOpenModsDirectory,
        )
        Spacer(Modifier.height(18.dp))
        LedgerDivider()

        if (!statusMessage.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            ModStatusMessage(statusMessage)
        }
        if (issues.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            ModIssueSummary(issues)
        }

        Spacer(Modifier.height(18.dp))
        if (mods.isEmpty() && isScanning) {
            ModsLoadingState(Modifier.fillMaxWidth().weight(1f))
        } else if (mods.isEmpty()) {
            EmptyModsState(
                onOpenModsDirectory = onOpenModsDirectory,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "설치된 패키지",
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.Ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "행을 선택하면 상세 정보와 설정을 엽니다.",
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                )
            }
            Spacer(Modifier.height(9.dp))
            VisibleVerticalScrollbar(
                state = modListState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(
                    state = modListState,
                    modifier = Modifier.fillMaxSize().padding(end = 13.dp),
                    contentPadding = PaddingValues(start = 1.dp, end = 1.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = mods, key = { it.id }) { mod ->
                        ModListRow(
                            mod = mod,
                            selected = selectedModId == mod.id,
                            enabled = !controlsBusy,
                            onSelect = { onSelectMod(mod) },
                            onToggle = { enabled -> onToggleMod(mod, enabled) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModsHeader(
    isScanning: Boolean,
    isMutating: Boolean,
    onRefresh: () -> Unit,
    onOpenModsDirectory: () -> Unit,
) {
    val controlsBusy = isScanning || isMutating
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "모드",
            style = MarketType.display.copy(fontSize = 30.sp),
            color = MarketColors.Ink,
        )
        Spacer(Modifier.weight(1f))
        if (controlsBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MarketColors.Primary,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isScanning) "폴더 검색 중" else "변경 저장 중",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.width(14.dp))
        }
        MarketButton(
            text = "새로고침",
            onClick = onRefresh,
            modifier = Modifier.width(112.dp),
            enabled = !controlsBusy,
            variant = MarketButtonVariant.Weak,
        )
        Spacer(Modifier.width(9.dp))
        MarketButton(
            text = "폴더 열기",
            onClick = onOpenModsDirectory,
            modifier = Modifier.width(112.dp),
        )
    }
}

@Composable
private fun ModStatusMessage(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MarketColors.PrimaryWeak, RoundedCornerShape(MarketRadii.medium))
            .border(1.dp, MarketColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(MarketRadii.medium))
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(MarketColors.Primary, RoundedCornerShape(MarketRadii.pill)))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MarketType.body, color = MarketColors.PrimaryText)
    }
}

@Composable
private fun ModIssueSummary(issues: List<ModLoadIssue>) {
    val issueScrollState = rememberScrollState()
    LedgerPanel(
        modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp),
        background = MarketColors.AmberSoft,
        padding = 14.dp,
    ) {
        VisibleVerticalScrollbar(
            state = issueScrollState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(issueScrollState)
                    .padding(end = 13.dp),
            ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "불러오지 못한 모드",
                    style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                    color = MarketColors.AmberText,
                )
                Spacer(Modifier.weight(1f))
                Text("${issues.size}개", style = MarketType.number, color = MarketColors.AmberText)
            }
            Spacer(Modifier.height(8.dp))
            issues.forEachIndexed { index, issue ->
                if (index > 0) Spacer(Modifier.height(7.dp))
                Text(
                    "${issue.directoryName} · ${issue.message}",
                    style = MarketType.caption,
                    color = MarketColors.Ink,
                )
            }
            }
        }
    }
}

@Composable
private fun ModsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MarketColors.Primary,
            strokeWidth = 2.5.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text("모드 폴더를 검색하고 있습니다.", style = MarketType.body, color = MarketColors.InkMuted)
        Spacer(Modifier.height(16.dp))
        LoadingFinancialFact(factKey = "mods-discovery")
    }
}

@Composable
private fun EmptyModsState(
    onOpenModsDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LedgerPanel(modifier = modifier, padding = 28.dp) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ModIdentityPlaceholder(
                modId = "mods",
                compact = true,
                modifier = Modifier.size(70.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "설치된 모드가 없습니다.",
                style = MarketType.heading.copy(fontSize = 19.sp),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "mods 폴더에 모드 폴더와 manifest.xml을 넣은 뒤 새로고침하세요.",
                style = MarketType.body,
                color = MarketColors.InkMuted,
            )
            Spacer(Modifier.height(18.dp))
            MarketButton(
                text = "모드 폴더 열기",
                onClick = onOpenModsDirectory,
                modifier = Modifier.widthIn(min = 180.dp),
                variant = MarketButtonVariant.Weak,
            )
        }
    }
}

@Composable
private fun ModListRow(
    mod: InstalledMod,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val highlighted = hovered || focused || selected
    val packageSummary = buildList {
        add(if (mod.settings.isEmpty()) "설정 없음" else "설정 ${mod.settings.size}개")
        mod.instrumentPack?.let { pack -> add("등록 종목 ${pack.instrumentCount}개") }
    }.joinToString(" · ")

    LedgerPanel(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onSelect,
            )
            .semantics { contentDescription = "${mod.name} 모드 상세 정보 열기" },
        background = if (highlighted) MarketColors.PrimaryWeak else MarketColors.Paper,
        padding = 16.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 78.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModCoverImage(
                mod = mod,
                compact = true,
                contentDescription = null,
                modifier = Modifier.width(126.dp).height(68.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mod.name,
                        style = MarketType.heading.copy(fontSize = 18.sp),
                        color = MarketColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(9.dp))
                    StatusLabel(
                        text = if (mod.enabled) "활성" else "비활성",
                        color = if (mod.enabled) MarketColors.Positive else MarketColors.InkMuted,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    mod.description.ifBlank { "설명이 제공되지 않았습니다." },
                    style = MarketType.body,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "${mod.author}  ·  v${mod.version}  ·  수정 ${mod.lastModified}",
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    packageSummary,
                    style = MarketType.caption,
                    color = MarketColors.InkMuted,
                )
                Spacer(Modifier.height(5.dp))
                ModToggle(
                    checked = mod.enabled,
                    onCheckedChange = onToggle,
                    enabled = enabled,
                    contentDescription = "${mod.name} 모드 ${if (mod.enabled) "비활성화" else "활성화"}",
                )
            }
        }
    }
}

@Composable
private fun ModToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(MarketRadii.small)

    Box(
        modifier = modifier
            .size(width = 56.dp, height = MarketComponentSize.minimumInteractiveTarget)
            .background(
                if (hovered || focused) MarketColors.PrimaryWeak else Color.Transparent,
                shape,
            )
            .hoverable(interaction)
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .width(38.dp)
                .height(22.dp)
                .background(
                    if (checked) MarketColors.Positive else MarketColors.Grey400,
                    RoundedCornerShape(MarketRadii.pill),
                )
                .padding(3.dp),
            horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(16.dp).background(Color.White, RoundedCornerShape(MarketRadii.pill)))
        }
    }
}

@Composable
internal fun ModDetailDrawer(
    mod: InstalledMod,
    onDismiss: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSettingChanged: (String, String) -> Unit,
    controlsEnabled: Boolean,
) {
    val detailScrollState = rememberScrollState()
    VisibleVerticalScrollbar(
        state = detailScrollState,
        modifier = Modifier
            .width(430.dp)
            .fillMaxHeight()
            .background(MarketColors.Paper)
            .border(1.dp, MarketColors.Line),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(detailScrollState)
                .padding(end = 13.dp),
        ) {
        ModCoverImage(
            mod = mod,
            compact = false,
            contentDescription = "${mod.name} 대표 이미지",
            modifier = Modifier.fillMaxWidth().height(232.dp),
        )

        Column(Modifier.fillMaxWidth().padding(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("모드 상세", style = MarketType.caption, color = MarketColors.InkMuted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("닫기  ×", style = MarketType.label, color = MarketColors.InkMuted)
                }
            }
            Text(
                mod.name,
                style = MarketType.display.copy(fontSize = 28.sp),
                color = MarketColors.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                mod.description.ifBlank { "이 모드는 별도의 설명을 제공하지 않습니다." },
                style = MarketType.body,
                color = MarketColors.InkMuted,
            )

            Spacer(Modifier.height(20.dp))
            LedgerDivider()
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ModMetadata("제작자", mod.author, Modifier.weight(1f))
                ModMetadata("버전", mod.version, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ModMetadata("최종 수정 일자", mod.lastModified.toString(), Modifier.weight(1f))
                ModMetadata("API 버전", mod.apiVersion.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            ModMetadata("모드 ID", mod.id, Modifier.fillMaxWidth())
            mod.instrumentPack?.let { pack ->
                Spacer(Modifier.height(14.dp))
                ModMetadata("선언형 종목팩", "등록 종목 ${pack.instrumentCount}개", Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
            ModEnableControl(mod = mod, onToggle = onToggle, enabled = controlsEnabled)

            Spacer(Modifier.height(24.dp))
            DetailSectionTitle("요청 권한", "${mod.requestedCapabilities.size}개")
            Spacer(Modifier.height(9.dp))
            ModCapabilities(mod.requestedCapabilities)

            Spacer(Modifier.height(24.dp))
            DetailSectionTitle("설정", if (mod.settings.isEmpty()) null else "${mod.settings.size}개")
            Spacer(Modifier.height(9.dp))
            if (mod.settings.isEmpty()) {
                DetailEmptyMessage("이 모드는 조정 가능한 설정을 제공하지 않습니다.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    mod.settings.forEach { definition ->
                        ModSettingEditor(
                            mod = mod,
                            definition = definition,
                            onSettingChanged = onSettingChanged,
                            enabled = controlsEnabled,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        }
    }
}

@Composable
private fun ModMetadata(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MarketType.caption, color = MarketColors.InkMuted)
        Spacer(Modifier.height(3.dp))
        Text(
            value.ifBlank { "—" },
            style = MarketType.number,
            color = MarketColors.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModEnableControl(mod: InstalledMod, onToggle: (Boolean) -> Unit, enabled: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.medium))
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (mod.enabled) "모드 활성" else "모드 비활성",
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = MarketColors.Ink,
            )
            Text(
                if (mod.enabled) "새 게임에 이 모드를 포함합니다." else "새 게임에서 이 모드를 제외합니다.",
                style = MarketType.caption,
                color = MarketColors.InkMuted,
            )
        }
        ModToggle(
            checked = mod.enabled,
            onCheckedChange = onToggle,
            enabled = enabled,
            contentDescription = "${mod.name} 모드 ${if (mod.enabled) "비활성화" else "활성화"}",
        )
    }
}

@Composable
private fun DetailSectionTitle(title: String, value: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MarketType.heading.copy(fontSize = 17.sp),
            color = MarketColors.Ink,
        )
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MarketType.number, color = MarketColors.InkMuted)
        }
    }
}

@Composable
private fun ModCapabilities(capabilities: Set<ModCapability>) {
    if (capabilities.isEmpty()) {
        DetailEmptyMessage("이 모드는 게임 API 권한을 요청하지 않습니다.")
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        capabilities.sortedBy { it.manifestValue }.forEach { capability ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MarketColors.Line, RoundedCornerShape(MarketRadii.small))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(MarketColors.Primary, RoundedCornerShape(MarketRadii.pill)))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(capability.label, style = MarketType.label, color = MarketColors.Ink)
                    Text(capability.manifestValue, style = MarketType.caption, color = MarketColors.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun DetailEmptyMessage(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MarketColors.PaperMuted, RoundedCornerShape(MarketRadii.small))
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        Text(message, style = MarketType.body, color = MarketColors.InkMuted)
    }
}

@Composable
private fun ModSettingEditor(
    mod: InstalledMod,
    definition: ModSettingDefinition,
    onSettingChanged: (String, String) -> Unit,
    enabled: Boolean,
) {
    when (definition.type) {
        ModSettingType.BOOLEAN -> BooleanModSetting(
            definition = definition,
            checked = mod.settingValue(definition.key).toBoolean(),
            enabled = enabled,
            onCheckedChange = { checked -> onSettingChanged(definition.key, checked.toString()) },
        )

        ModSettingType.ENUM -> EnumModSetting(
            definition = definition,
            selectedValue = mod.settingValue(definition.key).orEmpty(),
            enabled = enabled,
            onValueSelected = { value -> onSettingChanged(definition.key, value) },
        )

        ModSettingType.INTEGER,
        ModSettingType.DECIMAL,
        ModSettingType.STRING,
        -> TextModSetting(
            mod = mod,
            definition = definition,
            onSettingChanged = onSettingChanged,
            enabled = enabled,
        )
    }
}

@Composable
private fun BooleanModSetting(
    definition: ModSettingDefinition,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MarketCheckRow(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = definition.name,
        detail = definition.description.ifBlank { "켜거나 끌 수 있는 모드 설정입니다." },
        enabled = enabled,
    )
}

@Composable
private fun TextModSetting(
    mod: InstalledMod,
    definition: ModSettingDefinition,
    onSettingChanged: (String, String) -> Unit,
    enabled: Boolean,
) {
    val externalValue = mod.settingValue(definition.key).orEmpty()
    var draft by remember(mod.id, definition.key) { mutableStateOf(externalValue) }
    val error = definition.validate(draft)

    LaunchedEffect(externalValue) {
        if (definition.validate(draft) == null) draft = externalValue
    }

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MarketColors.Line, RoundedCornerShape(MarketRadii.medium))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            definition.name,
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
        )
        if (definition.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(definition.description, style = MarketType.caption, color = MarketColors.InkMuted)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { input ->
                draft = input.take(2_048)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = if (definition.type == ModSettingType.STRING) MarketType.body else MarketType.number,
            singleLine = true,
            isError = error != null,
            supportingText = {
                Text(
                    error ?: settingConstraint(definition),
                    style = MarketType.caption,
                    color = if (error != null) MarketColors.RiseText else MarketColors.InkMuted,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        MarketButton(
            text = "설정 저장",
            onClick = { onSettingChanged(definition.key, draft) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && error == null && draft != externalValue,
            variant = MarketButtonVariant.Weak,
        )
    }
}

@Composable
private fun EnumModSetting(
    definition: ModSettingDefinition,
    selectedValue: String,
    enabled: Boolean,
    onValueSelected: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MarketColors.Line, RoundedCornerShape(MarketRadii.medium))
            .padding(12.dp),
    ) {
        Text(
            definition.name,
            style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
            color = MarketColors.Ink,
        )
        if (definition.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(definition.description, style = MarketType.caption, color = MarketColors.InkMuted)
        }
        Spacer(Modifier.height(9.dp))
        if (definition.options.isEmpty()) {
            Text("선택 가능한 항목이 없습니다.", style = MarketType.body, color = MarketColors.RiseText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                definition.options.forEach { option ->
                    val selected = selectedValue == option.value
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MarketComponentSize.minimumInteractiveTarget)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MarketColors.Primary else MarketColors.Line,
                                shape = RoundedCornerShape(MarketRadii.small),
                            )
                            .background(
                                if (selected) MarketColors.PrimaryWeak else MarketColors.Paper,
                                RoundedCornerShape(MarketRadii.small),
                            )
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onValueSelected(option.value) },
                            )
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .border(
                                    1.dp,
                                    if (selected) MarketColors.Primary else MarketColors.Grey400,
                                    RoundedCornerShape(MarketRadii.pill),
                                )
                                .padding(3.dp),
                        ) {
                            if (selected) {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        MarketColors.Primary,
                                        RoundedCornerShape(MarketRadii.pill),
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            option.label,
                            modifier = Modifier.weight(1f),
                            style = MarketType.label.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (selected) MarketColors.PrimaryText else MarketColors.Ink,
                        )
                        Text(option.value, style = MarketType.caption, color = MarketColors.InkMuted)
                    }
                }
            }
        }
    }
}

private fun settingConstraint(definition: ModSettingDefinition): String {
    if (definition.type == ModSettingType.STRING) return "기본값: ${definition.defaultValue}"
    val range = when {
        definition.minValue != null && definition.maxValue != null ->
            "${formatSettingNumber(definition.minValue)} – ${formatSettingNumber(definition.maxValue)}"

        definition.minValue != null -> "${formatSettingNumber(definition.minValue)} 이상"
        definition.maxValue != null -> "${formatSettingNumber(definition.maxValue)} 이하"
        else -> "범위 제한 없음"
    }
    return "허용 범위: $range · 기본값 ${definition.defaultValue}"
}

private fun formatSettingNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

@Composable
private fun ModCoverImage(
    mod: InstalledMod,
    compact: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val cover by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = mod.id,
        key2 = mod.coverPath,
        key3 = mod.lastModified,
    ) {
        value = mod.coverPath?.let { loadModCoverImage(it) }
    }
    val bitmap = cover
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(
                RoundedCornerShape(if (compact) MarketRadii.medium else 0.dp),
            ),
            contentScale = ContentScale.Crop,
        )
    } else {
        ModIdentityPlaceholder(
            modId = mod.id,
            compact = compact,
            modifier = modifier,
        )
    }
}

@Composable
private fun ModIdentityPlaceholder(
    modId: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalizedId = modId.filter { it.isLetterOrDigit() }.ifBlank { "mod" }
    val monogram = normalizedId.take(2).uppercase()
    val packageNumber = (modId.hashCode() and Int.MAX_VALUE) % 1_000
    val shape = RoundedCornerShape(if (compact) MarketRadii.medium else 0.dp)

    Box(modifier.clip(shape).background(MarketColors.Navy)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp)
                .align(Alignment.BottomCenter)
                .background(MarketColors.Primary),
        )
        if (compact) {
            Text(
                monogram,
                modifier = Modifier.align(Alignment.Center),
                style = MarketType.numberLarge.copy(fontSize = 19.sp),
                color = Color.White,
            )
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "MOD PACKAGE",
                        style = MarketType.caption.copy(letterSpacing = 0.35.sp),
                        color = Color.White.copy(alpha = 0.62f),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "PKG-${packageNumber.toString().padStart(3, '0')}",
                        style = MarketType.number,
                        color = MarketColors.Primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    monogram,
                    style = MarketType.display.copy(fontSize = 48.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    modId.uppercase(),
                    style = MarketType.number,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
