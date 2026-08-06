package com.amond.kmpbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class MealViewModel : ViewModel() {

    val prefs = SchoolPreferences()

    private val _currentDate = MutableStateFlow(today())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _mealState = MutableStateFlow<MealUiState>(MealUiState.Loading)
    val mealState: StateFlow<MealUiState> = _mealState.asStateFlow()

    private val _searchState = MutableStateFlow<SchoolSearchState>(SchoolSearchState.Idle)
    val searchState: StateFlow<SchoolSearchState> = _searchState.asStateFlow()

    init {
        if (prefs.isConfigured) loadMeals()
    }

    fun loadMeals(date: LocalDate = _currentDate.value) {
        if (!prefs.isConfigured) return
        viewModelScope.launch {
            _mealState.value = MealUiState.Loading
            val dateStr = date.toNeisFormat()
            NeisApi.getMeals(
                officeCode = prefs.officeCode,
                schoolCode = prefs.schoolCode,
                date = dateStr,
                apiKey = prefs.apiKey
            ).fold(
                onSuccess = { meals ->
                    _mealState.value = if (meals.isEmpty()) MealUiState.Empty()
                    else MealUiState.Success(meals)
                },
                onFailure = { e ->
                    _mealState.value = MealUiState.Error(e.message ?: "오류가 발생했습니다")
                }
            )
        }
    }

    fun previousDay() {
        val newDate = _currentDate.value.minus(1, DateTimeUnit.DAY)
        _currentDate.value = newDate
        loadMeals(newDate)
    }

    fun nextDay() {
        val newDate = _currentDate.value.plus(1, DateTimeUnit.DAY)
        _currentDate.value = newDate
        loadMeals(newDate)
    }

    fun goToToday() {
        val newDate = today()
        _currentDate.value = newDate
        loadMeals(newDate)
    }

    fun searchSchools(query: String) {
        if (query.isBlank()) {
            _searchState.value = SchoolSearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = SchoolSearchState.Loading
            NeisApi.searchSchools(query, prefs.apiKey).fold(
                onSuccess = { schools ->
                    _searchState.value = if (schools.isEmpty()) SchoolSearchState.Error("검색 결과가 없습니다")
                    else SchoolSearchState.Success(schools)
                },
                onFailure = { e ->
                    _searchState.value = SchoolSearchState.Error(e.message ?: "검색 실패")
                }
            )
        }
    }

    fun selectSchool(school: SchoolInfo) {
        prefs.saveSchool(school)
        _searchState.value = SchoolSearchState.Idle
        _currentDate.value = today()
        loadMeals()
    }

    fun resetSchool() {
        prefs.clear()
        _mealState.value = MealUiState.Empty("학교를 설정해주세요")
        _searchState.value = SchoolSearchState.Idle
    }
}

private fun today(): LocalDate = Clock.System.todayIn(TimeZone.of("Asia/Seoul"))

fun LocalDate.toNeisFormat(): String =
    "${year}${month.number.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}"

fun LocalDate.toDisplayFormat(): String =
    "${year}년 ${month.number}월 ${day}일"

fun LocalDate.toDayOfWeekKorean(): String = when (dayOfWeek.name) {
    "MONDAY" -> "월"
    "TUESDAY" -> "화"
    "WEDNESDAY" -> "수"
    "THURSDAY" -> "목"
    "FRIDAY" -> "금"
    "SATURDAY" -> "토"
    "SUNDAY" -> "일"
    else -> ""
}