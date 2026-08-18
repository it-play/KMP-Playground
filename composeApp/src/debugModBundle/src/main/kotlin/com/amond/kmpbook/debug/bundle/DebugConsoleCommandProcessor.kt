package com.amond.kmpbook.debug.bundle

import com.amond.kmpbook.domain.model.instrument.StockDefinition
import com.amond.kmpbook.domain.model.market.Currency
import com.amond.kmpbook.domain.simulation.event.DebugEventGuide
import com.amond.kmpbook.domain.simulation.market.ExternalMarketForces
import com.amond.kmpbook.domain.time.GameCalendar
import com.amond.kmpbook.presentation.simulator.DebugPriceCurrency
import com.amond.kmpbook.presentation.simulator.DebugRuntimeResult
import com.amond.kmpbook.modding.api.TrustedDebugGameApi
import com.amond.kmpbook.modding.api.runtime.ModConsoleCommandHandler
import com.amond.kmpbook.modding.api.runtime.ModConsoleCommandResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Parser and typed command router for the trusted bundled developer console. */
internal class DebugConsoleCommandProcessor(
    private val viewModel: TrustedDebugGameApi,
) : ModConsoleCommandHandler {
    override suspend fun execute(commandLine: String): ModConsoleCommandResult = try {
        withContext(Dispatchers.Main.immediate) { executeOnMain(commandLine) }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: RuntimeException) {
        ModConsoleCommandResult.failure("명령 처리 중 예기치 못한 오류가 발생했습니다.")
    }

    private fun executeOnMain(commandLine: String): ModConsoleCommandResult {
        if (!viewModel.isDebugConsoleEnabled()) {
            return ModConsoleCommandResult.failure("현재 캠페인에 호환되는 디버그 모드가 활성화되지 않았습니다.")
        }
        val tokens = tokenize(commandLine) ?: return ModConsoleCommandResult.failure(
            "따옴표가 닫히지 않았거나 명령 형식이 올바르지 않습니다.",
        )
        if (tokens.isEmpty()) return ModConsoleCommandResult.success()
        return when (tokens.first().lowercase()) {
            "help", "?" -> help(tokens.drop(1))
            "status" -> if (tokens.size == 1) status() else usage("status")
            "stocks" -> stocks(tokens.drop(1).joinToString(" ").ifBlank { null })
            "stock" -> stock(tokens.drop(1).joinToString(" "))
            "turn" -> turn(tokens)
            "price" -> price(tokens)
            "cash" -> cash(tokens)
            "fx" -> fx(tokens)
            "ending", "end" -> ending(tokens)
            "value" -> value(tokens)
            "rule" -> rule(tokens)
            "force" -> force(tokens)
            "event" -> event(tokens)
            "orders" -> orders(tokens)
            "pause" -> if (tokens.size == 1) fromRuntime(viewModel.debugPause()) else usage("pause")
            "resume" -> if (tokens.size == 1) fromRuntime(viewModel.debugResume()) else usage("resume")
            "save-check", "validate" -> if (tokens.size == 1) {
                fromRuntime(viewModel.debugValidationStatus())
            } else {
                usage("save-check")
            }
            "clear" -> if (tokens.size == 1) ModConsoleCommandResult.success() else usage("clear")
            else -> ModConsoleCommandResult.failure(
                "알 수 없는 명령 '${tokens.first()}'입니다. 'help'로 명령 목록을 확인하세요.",
            )
        }
    }

    private fun help(arguments: List<String>): ModConsoleCommandResult {
        val query = arguments.joinToString(" ").trim()
        val lines = if (query.isEmpty()) HELP_LINES else HELP_LINES.filter {
            it.contains(query, ignoreCase = true)
        }
        return if (lines.isEmpty()) {
            ModConsoleCommandResult.failure("'$query'에 해당하는 도움말이 없습니다.")
        } else {
            ModConsoleCommandResult(
                success = true,
                lines = listOf("사용 가능한 디버그 명령 (${lines.size}개)") + lines,
            )
        }
    }

    private fun status(): ModConsoleCommandResult {
        val state = viewModel.currentState
        val maxTurn = GameCalendar.turnAt(GameCalendar.endInstant)
        return ModConsoleCommandResult(
            success = true,
            lines = listOf(
                "단계=${state.phase.name}, 턴=${state.turn}/$maxTurn, 시각=${state.currentTime}",
                "총자산(KRW)=${state.totalAssetsKrw}, 현금(KRW)=${state.cashByCurrency[Currency.KRW] ?: 0.0}, 현금(USD)=${state.cashByCurrency[Currency.USD] ?: 0.0}",
                "USD/KRW=${state.macro.usdKrw}, 미체결 주문=${state.openOrders.size}, 활성 이벤트=${state.activeEvents.size}",
                "소수점 거래=${state.options.usFractionalTrading}, 자동 환전=${state.options.autoExchange}, 철인=${state.options.ironmanMode}, 계산 중=${state.isAdvancing}",
            ),
        )
    }

    private fun stocks(query: String?): ModConsoleCommandResult {
        val normalized = query?.trim().orEmpty()
        val matches = viewModel.currentState.stocks.filter { stock ->
            normalized.isEmpty() || stock.matches(normalized, partial = true)
        }
        if (matches.isEmpty()) return ModConsoleCommandResult.failure("조건에 맞는 종목이 없습니다.")
        val shown = matches.take(MAX_LIST_RESULTS)
        val lines = shown.map { stock ->
            val price = viewModel.currentState.quotes[stock.id]?.price
            "${stock.id} | ${stock.name} | ${stock.currency.name} | price=$price"
        }.toMutableList()
        if (matches.size > shown.size) lines += "... ${matches.size - shown.size}개 결과 생략"
        return ModConsoleCommandResult(success = true, lines = lines)
    }

    private fun stock(query: String): ModConsoleCommandResult {
        val resolved = resolveInstrument(query)
        if (resolved.error != null) return ModConsoleCommandResult.failure(resolved.error)
        val stock = requireNotNull(resolved.stock)
        val state = viewModel.currentState
        val quote = state.quotes.getValue(stock.id)
        val holding = state.holdings[stock.id]
        return ModConsoleCommandResult(
            success = true,
            lines = listOf(
                "${stock.id} | ${stock.name} (${stock.englishName})",
                "시장=${stock.market.name}, 산업=${stock.sector.name}, 통화=${stock.currency.name}, 가격=${quote.price}",
                "전일종가=${quote.previousClose}, 변동률=${quote.changeRate * 100.0}%, 보유=${holding?.quantity ?: 0.0}",
            ),
        )
    }

    private fun turn(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size == 1) return status()
        return when (tokens[1].lowercase()) {
            "cancel" -> if (tokens.size == 2) {
                fromRuntime(viewModel.debugCancelTurnJump())
            } else {
                usage("turn cancel")
            }
            "jump" -> {
                val rawTarget = tokens.getOrNull(2)
                    ?: return usage("turn jump <turn|max> [--reset]")
                val target = if (rawTarget.equals("max", ignoreCase = true)) {
                    GameCalendar.turnAt(GameCalendar.endInstant)
                } else {
                    rawTarget.toLongOrNull() ?: return usage("턴은 0 이상의 정수여야 합니다.")
                }
                val options = tokens.drop(3)
                if (options.size > 1 || options.any { !it.equals("--reset", ignoreCase = true) }) {
                    return usage("turn jump <turn|max> [--reset]")
                }
                val reset = options.singleOrNull()?.equals("--reset", ignoreCase = true) == true
                fromRuntime(viewModel.debugStartTurnJump(target, reset))
            }
            else -> usage("turn jump <turn|max> [--reset] | turn cancel")
        }
    }

    private fun price(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size < 2) return usage("price set <instrument> <amount> <native|krw|usd> | price change <instrument> <percent>")
        return when (tokens[1].lowercase()) {
            "set" -> {
                if (tokens.size != 5) return usage("price set <instrument> <amount> <native|krw|usd>")
                val stock = resolvedStock(tokens[2]) ?: return lastResolutionFailure
                val amount = parseNumber(tokens[3]) ?: return invalidNumber(tokens[3])
                val currency = parsePriceCurrency(tokens[4])
                    ?: return usage("가격 통화는 native, krw, usd 중 하나여야 합니다.")
                fromRuntime(
                    viewModel.debugSetInstrumentPrice(stock.id, amount, currency),
                    warnings = listOf("관리자 가격 조정은 일일 가격제한을 우회하며 미체결 주문을 즉시 체결하지 않습니다."),
                )
            }
            "change", "percent" -> {
                if (tokens.size != 4) return usage("price change <instrument> <percent>")
                val stock = resolvedStock(tokens[2]) ?: return lastResolutionFailure
                val percent = parseNumber(tokens[3]) ?: return invalidNumber(tokens[3])
                fromRuntime(
                    viewModel.debugChangeInstrumentPrice(stock.id, percent),
                    warnings = listOf("관리자 가격 조정은 일일 가격제한을 우회하며 다음 정상 진행부터 시장 엔진이 반응합니다."),
                )
            }
            else -> usage("price set <instrument> <amount> <native|krw|usd> | price change <instrument> <percent>")
        }
    }

    private fun cash(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size != 4) return usage("cash add|set <krw|usd> <amount>")
        val currency = parseCurrency(tokens[2]) ?: return usage("통화는 krw 또는 usd여야 합니다.")
        val amount = parseNumber(tokens[3]) ?: return invalidNumber(tokens[3])
        return when (tokens[1].lowercase()) {
            "add" -> fromRuntime(viewModel.debugAddCash(currency, amount))
            "set" -> fromRuntime(viewModel.debugSetCash(currency, amount))
            else -> usage("cash add|set <krw|usd> <amount>")
        }
    }

    private fun fx(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size == 1) return ModConsoleCommandResult.success("USD/KRW=${viewModel.currentState.macro.usdKrw}")
        if (tokens.size != 3) return usage("fx set <usdKrw> | fx change <percent>")
        val value = parseNumber(tokens[2]) ?: return invalidNumber(tokens[2])
        return when (tokens[1].lowercase()) {
            "set" -> fromRuntime(viewModel.debugSetUsdKrw(value))
            "change", "percent" -> {
                if (value <= -100.0) return ModConsoleCommandResult.failure("환율 변화율은 -100%보다 커야 합니다.")
                fromRuntime(viewModel.debugSetUsdKrw(viewModel.currentState.macro.usdKrw * (1.0 + value / 100.0)))
            }
            else -> usage("fx set <usdKrw> | fx change <percent>")
        }
    }

    private fun ending(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size != 2) return usage("ending settle|finish")
        val target = GameCalendar.turnAt(GameCalendar.endInstant)
        val command = tokens.getOrNull(1)?.lowercase() ?: return usage("ending settle|finish")
        return when (command) {
            "settle", "settlement" -> fromRuntime(
                viewModel.debugStartTurnJump(target, resetForBackwardJump = false),
                warnings = listOf("일정·세금·기업행동을 보존하기 위해 남은 시간을 정상 엔진으로 계산합니다."),
            )
            "finish" -> fromRuntime(
                viewModel.debugStartTurnJump(target, resetForBackwardJump = false, finishSettlement = true),
                warnings = listOf("최종 정산 진입 뒤 종료까지 수행합니다. 'turn cancel'로 진행 계산을 취소할 수 있습니다."),
            )
            else -> usage("ending settle|finish")
        }
    }

    private fun rule(tokens: List<String>): ModConsoleCommandResult {
        val state = viewModel.currentState
        if (tokens.size == 1 || tokens.getOrNull(1).equals("list", ignoreCase = true)) {
            return ModConsoleCommandResult(
                success = true,
                lines = listOf(
                    "fractional=${state.options.usFractionalTrading} — 미국 종목 소수점 거래",
                    "auto_exchange=${state.options.autoExchange} — 자동 환전",
                    "ironman=${state.options.ironmanMode} — 철인 모드",
                ),
            )
        }
        if (tokens.size != 4 || !tokens[1].equals("set", ignoreCase = true)) {
            return usage("rule list | rule set <fractional|auto_exchange|ironman> <on|off>")
        }
        val enabled = parseBoolean(tokens[3]) ?: return usage("규칙 값은 on 또는 off여야 합니다.")
        val result = when (tokens[2].lowercase()) {
            "fractional", "fractional_trading" -> viewModel.debugSetFractionalTrading(enabled)
            "auto_exchange", "auto-exchange" -> viewModel.debugSetAutoExchange(enabled)
            "ironman" -> viewModel.debugSetIronman(enabled)
            else -> return usage("지원 규칙: fractional, auto_exchange, ironman")
        }
        return fromRuntime(result, warnings = listOf("규칙 변경은 현재 캠페인 저장 데이터에 유지됩니다."))
    }

    private fun force(tokens: List<String>): ModConsoleCommandResult {
        val current = viewModel.currentState.externalMarketForcesTarget
        if (tokens.size == 1 || tokens.getOrNull(1).equals("list", ignoreCase = true)) {
            return ModConsoleCommandResult(
                success = true,
                lines = FORCE_NAMES.map { name -> "$name=${forceValue(current, name)}" },
            )
        }
        if (tokens.size != 4 || !tokens[1].equals("set", ignoreCase = true)) {
            return usage("force list | force set <name> <0..1>")
        }
        val value = parseNumber(tokens[3]) ?: return invalidNumber(tokens[3])
        if (value !in 0.0..1.0) return ModConsoleCommandResult.failure("시장 환경 값은 0..1 범위여야 합니다.")
        val updated = copyForce(current, tokens[2], value)
            ?: return usage("지원 값: ${FORCE_NAMES.joinToString()}")
        return fromRuntime(viewModel.debugSetExternalMarketForces(updated))
    }

    private fun value(tokens: List<String>): ModConsoleCommandResult {
        if (tokens.size < 3) return usage("value get <path> | value set|add <path> <number>")
        val action = tokens[1].lowercase()
        val path = tokens[2]
        if (action == "get") {
            return if (tokens.size == 3) getValue(path) else usage("value get <path>")
        }
        if (tokens.size != 4 || action !in setOf("set", "add")) {
            return usage("value get <path> | value set|add <path> <number>")
        }
        val number = parseNumber(tokens[3]) ?: return invalidNumber(tokens[3])
        return mutateValue(action, path, number)
    }

    private fun getValue(path: String): ModConsoleCommandResult {
        val normalized = path.lowercase()
        val state = viewModel.currentState
        val value = when {
            normalized == "cash.krw" -> state.cashByCurrency[Currency.KRW]
            normalized == "cash.usd" -> state.cashByCurrency[Currency.USD]
            normalized in setOf("fx.usdkrw", "fx.usd_krw") -> state.macro.usdKrw
            normalized.startsWith("force.") -> forceValue(
                state.externalMarketForcesTarget,
                normalized.removePrefix("force."),
            ) ?: return ModConsoleCommandResult.failure("알 수 없는 force 경로 '$path'입니다.")
            normalized.startsWith("price.") -> {
                val stock = resolvedStock(path.substringAfter('.')) ?: return lastResolutionFailure
                state.quotes[stock.id]?.price
            }
            else -> return ModConsoleCommandResult.failure(
                "허용된 경로: cash.krw, cash.usd, fx.usdkrw, price.<instrument>, force.<name>",
            )
        }
        return ModConsoleCommandResult.success("$path=$value")
    }

    private fun mutateValue(action: String, path: String, number: Double): ModConsoleCommandResult {
        val normalized = path.lowercase()
        val state = viewModel.currentState
        val isAdd = action == "add"
        return when {
            normalized == "cash.krw" -> fromRuntime(
                if (isAdd) viewModel.debugAddCash(Currency.KRW, number) else viewModel.debugSetCash(Currency.KRW, number),
            )
            normalized == "cash.usd" -> fromRuntime(
                if (isAdd) viewModel.debugAddCash(Currency.USD, number) else viewModel.debugSetCash(Currency.USD, number),
            )
            normalized in setOf("fx.usdkrw", "fx.usd_krw") -> fromRuntime(
                viewModel.debugSetUsdKrw(if (isAdd) state.macro.usdKrw + number else number),
            )
            normalized.startsWith("price.") -> {
                val stock = resolvedStock(path.substringAfter('.')) ?: return lastResolutionFailure
                val amount = if (isAdd) state.quotes.getValue(stock.id).price + number else number
                fromRuntime(viewModel.debugSetInstrumentPrice(stock.id, amount, DebugPriceCurrency.NATIVE))
            }
            normalized.startsWith("force.") -> {
                val name = normalized.removePrefix("force.")
                val current = forceValue(state.externalMarketForcesTarget, name)
                    ?: return ModConsoleCommandResult.failure("알 수 없는 force 경로 '$path'입니다.")
                val next = if (isAdd) current + number else number
                if (next !in 0.0..1.0) return ModConsoleCommandResult.failure("시장 환경 값은 0..1 범위여야 합니다.")
                val updated = requireNotNull(copyForce(state.externalMarketForcesTarget, name, next))
                fromRuntime(viewModel.debugSetExternalMarketForces(updated))
            }
            else -> ModConsoleCommandResult.failure(
                "임의 reflection 경로는 허용하지 않습니다. help value로 허용 목록을 확인하세요.",
            )
        }
    }

    private fun event(tokens: List<String>): ModConsoleCommandResult {
        if (viewModel.currentState.isAdvancing) {
            return ModConsoleCommandResult.failure(
                "게임 진행 계산 중에는 이벤트 카탈로그를 읽거나 발동할 수 없습니다. help, status, turn cancel은 계속 사용할 수 있습니다.",
            )
        }
        val action = tokens.getOrNull(1)?.lowercase() ?: return usage(
            "event list [filter] | event describe <templateId> | event trigger <templateId> [target]",
        )
        return when (action) {
            "list", "guide" -> {
                val query = tokens.drop(2).joinToString(" ").ifBlank { null }
                val guides = viewModel.debugEventGuide(query)
                if (guides.isEmpty()) return ModConsoleCommandResult.failure("조건에 맞는 이벤트 템플릿이 없습니다.")
                val lines = guides.map(::eventSummary)
                ModConsoleCommandResult(success = true, lines = lines)
            }
            "describe", "show" -> {
                if (tokens.size != 3) return usage("event describe <templateId>")
                val id = tokens.getOrNull(2) ?: return usage("event describe <templateId>")
                val guide = viewModel.debugEventGuide(id).firstOrNull { it.templateId == id }
                    ?: return ModConsoleCommandResult.failure("이벤트 템플릿 '$id'을(를) 찾을 수 없습니다.")
                ModConsoleCommandResult(success = true, lines = eventDetails(guide))
            }
            "trigger", "fire" -> {
                if (tokens.size !in 3..4) return usage("event trigger <templateId> [target]")
                val id = tokens.getOrNull(2) ?: return usage("event trigger <templateId> [target]")
                val target = tokens.getOrNull(3)
                fromRuntime(viewModel.debugTriggerEvent(id, target))
            }
            else -> usage("event list [filter] | event describe <templateId> | event trigger <templateId> [target]")
        }
    }

    private fun orders(tokens: List<String>): ModConsoleCommandResult = when {
        tokens.size == 2 && tokens[1].lowercase() in setOf("cancel-all", "cancel_all") ->
            fromRuntime(viewModel.debugCancelAllOrders())
        else -> usage("orders cancel-all")
    }

    private fun eventSummary(guide: DebugEventGuide): String {
        val argument = guide.argumentName?.let { " <$it>" }.orEmpty()
        val flags = buildList {
            if (guide.oneShot) add("one-shot")
            if (guide.condition.name != "ALWAYS") add("condition=${guide.condition.name}")
        }.joinToString(", ").let { if (it.isEmpty()) "" else " [$it]" }
        return "${guide.templateId}$argument — ${guide.title} (${guide.scope.name})$flags"
    }

    private fun eventDetails(guide: DebugEventGuide): List<String> {
        val targets = if (guide.eligibleTargets.isEmpty()) {
            listOf("target=없음")
        } else {
            val shown = guide.eligibleTargets.take(MAX_EVENT_TARGETS)
            buildList {
                add("target(${guide.argumentName})=${shown.joinToString()}")
                if (guide.eligibleTargets.size > shown.size) add("... ${guide.eligibleTargets.size - shown.size}개 대상 생략")
            }
        }
        return listOf(
            "id=${guide.templateId}",
            "title=${guide.title}",
            "scope=${guide.scope.name}, condition=${guide.condition.name}, oneShot=${guide.oneShot}",
        ) + targets + "사용: event trigger ${guide.templateId}${guide.eligibleTargets.firstOrNull()?.let { " $it" }.orEmpty()}"
    }

    private fun resolvedStock(query: String): StockDefinition? {
        val resolved = resolveInstrument(query)
        lastResolutionFailure = ModConsoleCommandResult.failure(resolved.error ?: "종목을 찾을 수 없습니다.")
        return resolved.stock
    }

    private fun resolveInstrument(query: String): InstrumentResolution {
        val normalized = query.trim()
        if (normalized.isEmpty()) return InstrumentResolution(error = "종목 ID, 코드 또는 이름을 입력하세요.")
        val stocks = viewModel.currentState.stocks
        val exact = stocks.filter { it.matches(normalized, partial = false) }
        if (exact.size == 1) return InstrumentResolution(stock = exact.single())
        if (exact.size > 1) return InstrumentResolution(error = "종목 '$query'이(가) 여러 시장에 있습니다. MARKET:SYMBOL ID를 사용하세요.")
        val partial = stocks.filter { it.matches(normalized, partial = true) }
        return when (partial.size) {
            0 -> InstrumentResolution(error = "종목 '$query'을(를) 찾을 수 없습니다.")
            1 -> InstrumentResolution(stock = partial.single())
            else -> InstrumentResolution(
                error = "종목 '$query'이(가) 모호합니다: ${partial.take(8).joinToString { it.id }}",
            )
        }
    }

    private fun StockDefinition.matches(query: String, partial: Boolean): Boolean {
        val values = listOf(id, symbol, name, englishName)
        return if (partial) values.any { it.contains(query, ignoreCase = true) }
        else values.any { it.equals(query, ignoreCase = true) }
    }

    private fun fromRuntime(
        result: DebugRuntimeResult,
        warnings: List<String> = emptyList(),
    ): ModConsoleCommandResult = ModConsoleCommandResult(
        success = result.success,
        lines = listOf(result.message) + result.value?.let { listOf("result=$it") }.orEmpty(),
        warnings = if (result.success) warnings else emptyList(),
    )

    private fun parseCurrency(value: String): Currency? = when (value.lowercase()) {
        "krw", "won", "원" -> Currency.KRW
        "usd", "dollar", "달러" -> Currency.USD
        else -> null
    }

    private fun parsePriceCurrency(value: String): DebugPriceCurrency? = when (value.lowercase()) {
        "native", "local" -> DebugPriceCurrency.NATIVE
        "krw", "won", "원" -> DebugPriceCurrency.KRW
        "usd", "dollar", "달러" -> DebugPriceCurrency.USD
        else -> null
    }

    private fun parseBoolean(value: String): Boolean? = when (value.lowercase()) {
        "on", "true", "1", "yes", "켜기", "켬" -> true
        "off", "false", "0", "no", "끄기", "끔" -> false
        else -> null
    }

    private fun parseNumber(value: String): Double? = value.replace(",", "").replace("_", "").toDoubleOrNull()
        ?.takeIf(Double::isFinite)

    private fun forceValue(forces: ExternalMarketForces, rawName: String): Double? = when (normalizeForceName(rawName)) {
        "chaos" -> forces.chaos
        "world_tension" -> forces.worldTension
        "retail_power" -> forces.retailBuyingPower
        "institutional_power" -> forces.institutionalBuyingPower
        "liquidity" -> forces.marketLiquidity
        "momentum" -> forces.economicMomentum
        else -> null
    }

    private fun copyForce(forces: ExternalMarketForces, rawName: String, value: Double): ExternalMarketForces? =
        when (normalizeForceName(rawName)) {
            "chaos" -> forces.copy(chaos = value)
            "world_tension" -> forces.copy(worldTension = value)
            "retail_power" -> forces.copy(retailBuyingPower = value)
            "institutional_power" -> forces.copy(institutionalBuyingPower = value)
            "liquidity" -> forces.copy(marketLiquidity = value)
            "momentum" -> forces.copy(economicMomentum = value)
            else -> null
        }

    private fun normalizeForceName(value: String): String = when (value.lowercase().replace('-', '_')) {
        "worldtension" -> "world_tension"
        "retail", "retail_buying_power" -> "retail_power"
        "institutional", "institutional_buying_power" -> "institutional_power"
        "market_liquidity" -> "liquidity"
        "economic_momentum" -> "momentum"
        else -> value.lowercase().replace('-', '_')
    }

    private fun usage(text: String): ModConsoleCommandResult = ModConsoleCommandResult.failure("사용법: $text")

    private fun invalidNumber(value: String): ModConsoleCommandResult =
        ModConsoleCommandResult.failure("'$value'은(는) 유한한 숫자가 아닙니다.")

    private fun tokenize(input: String): List<String>? {
        if (input.length > MAX_COMMAND_LENGTH) return null
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        fun commit() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }
        for (character in input) {
            when {
                escaping -> {
                    current.append(character)
                    escaping = false
                }
                character == '\\' && quote != null -> escaping = true
                quote != null && character == quote -> quote = null
                quote != null -> current.append(character)
                character == '\'' || character == '"' -> quote = character
                character.isWhitespace() -> commit()
                else -> current.append(character)
            }
            if (tokens.size > MAX_TOKENS) return null
        }
        if (escaping || quote != null) return null
        commit()
        return tokens.takeIf { it.size <= MAX_TOKENS }
    }

    private var lastResolutionFailure: ModConsoleCommandResult =
        ModConsoleCommandResult.failure("종목을 찾을 수 없습니다.")

    private companion object {
        const val MAX_COMMAND_LENGTH = 4_096
        const val MAX_TOKENS = 32
        const val MAX_LIST_RESULTS = 80
        const val MAX_EVENT_TARGETS = 60

        val FORCE_NAMES = listOf(
            "chaos",
            "world_tension",
            "retail_power",
            "institutional_power",
            "liquidity",
            "momentum",
        )

        val HELP_LINES = listOf(
            "help [command] — 전체 또는 검색된 명령 도움말",
            "status — 현재 게임·자산·규칙·진행 상태",
            "stocks [query] — 종목 ID/코드/이름 검색",
            "stock <instrument> — 종목 상세 확인",
            "turn jump <turn|max> [--reset] — 원하는 턴으로 정상 엔진 진행; 과거 이동은 명시적 초기화 필요",
            "turn cancel — 실행 중인 턴 이동 취소",
            "price set <instrument> <amount> <native|krw|usd> — 종목 가격 설정",
            "price change <instrument> <percent> — 종목 가격을 백분율로 조정",
            "cash add <krw|usd> <amount> — 현금 증감",
            "cash set <krw|usd> <amount> — 현금 잔액 설정",
            "fx set <usdKrw> — USD/KRW 설정",
            "fx change <percent> — USD/KRW 백분율 조정",
            "ending settle — 남은 시간을 계산해 최종 정산 진입",
            "ending finish — 남은 시간을 계산하고 정산 종료",
            "value get <path> — 허용된 수치 경로 조회",
            "value set|add <path> <number> — 허용된 수치 경로 설정/증감",
            "rule list — 변경 가능한 게임 규칙 목록",
            "rule set <fractional|auto_exchange|ironman> <on|off> — 게임 규칙 변경",
            "force list — 외부 시장 환경 목표 목록",
            "force set <name> <0..1> — 외부 시장 환경 목표 변경",
            "event list [filter] — 이벤트 template ID와 필요 인자 가이드",
            "event describe <templateId> — 이벤트 대상과 조건 상세",
            "event trigger <templateId> [target] — 기존 템플릿 이벤트 강제 발동",
            "orders cancel-all — 모든 미체결 주문 취소",
            "pause | resume — 게임 일시 정지/재개",
            "save-check | validate — 현재 상태 저장 불변식 검사",
            "clear — 콘솔 출력 지우기",
        )
    }
}
