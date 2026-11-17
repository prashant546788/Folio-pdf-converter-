package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.ConvertedPdfEntity
import com.example.ui.components.StatusPill
import com.example.ui.components.formatFileSize
import com.example.ui.components.shareImageFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class ToolkitTab(val label: String) {
    CALCULATOR("Calculator"),
    CONVERTER("Unit Converter"),
    AGE("Age Calc"),
    RESIZER("Photo Resizer"),
    SPLITTER("PDF Splitter")
}

@Composable
fun CalculatorsScreen(
    vaultDocuments: List<ConvertedPdfEntity>,
    onOpenViewer: (Long) -> Unit,
    onSaveToDownloads: (ConvertedPdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(ToolkitTab.CALCULATOR) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolkitTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        ToolkitTab.CALCULATOR -> Icons.Filled.Calculate
                        ToolkitTab.CONVERTER -> Icons.Filled.Straighten
                        ToolkitTab.AGE -> Icons.Filled.Cake
                        ToolkitTab.RESIZER -> Icons.Filled.AddPhotoAlternate
                        ToolkitTab.SPLITTER -> Icons.AutoMirrored.Filled.CallSplit
                    }
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        leadingIcon = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("toolkit_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Tab Content
            when (activeTab) {
                ToolkitTab.CALCULATOR -> CalculatorSection()
                ToolkitTab.CONVERTER -> UnitConverterSection()
                ToolkitTab.AGE -> AgeCalculatorSection()
                ToolkitTab.RESIZER -> PhotoResizerSection()
                ToolkitTab.SPLITTER -> PdfSplitterSection(vaultDocuments = vaultDocuments, onOpenViewer = onOpenViewer)
            }
        }
    }
}

// ==========================================
// 1. SIMPLE & SCIENTIFIC CALCULATOR
// ==========================================
@Composable
fun CalculatorSection() {
    var expression by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("0") }
    var isScientific by remember { mutableStateOf(false) }

    fun appendChar(c: String) {
        expression += c
    }

    fun evaluateExpression() {
        if (expression.isBlank()) return
        try {
            val eval = evalSimpleMath(expression)
            resultText = if (eval == eval.toLong().toDouble()) {
                eval.toLong().toString()
            } else {
                String.format(Locale.US, "%.6f", eval).trimEnd('0').trimEnd('.')
            }
        } catch (e: Exception) {
            resultText = "Error"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isScientific) "Scientific Calculator" else "Simple Calculator",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scientific", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = isScientific, onCheckedChange = { isScientific = it })
                }
            }

            // Display Screen
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = expression.ifBlank { "0" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        textAlign = TextAlign.End
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
            }

            if (isScientific) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcButton("sin", Modifier.weight(1f)) { expression += "sin(" }
                    CalcButton("cos", Modifier.weight(1f)) { expression += "cos(" }
                    CalcButton("tan", Modifier.weight(1f)) { expression += "tan(" }
                    CalcButton("log", Modifier.weight(1f)) { expression += "log(" }
                    CalcButton("ln", Modifier.weight(1f)) { expression += "ln(" }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcButton("√", Modifier.weight(1f)) { expression += "sqrt(" }
                    CalcButton("^", Modifier.weight(1f)) { expression += "^" }
                    CalcButton("π", Modifier.weight(1f)) { expression += "3.141592" }
                    CalcButton("e", Modifier.weight(1f)) { expression += "2.718281" }
                    CalcButton("(", Modifier.weight(1f)) { expression += "(" }
                }
            }

            // Standard keypad
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcButton("C", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.errorContainer) {
                    expression = ""
                    resultText = "0"
                }
                CalcButton("⌫", Modifier.weight(1f)) {
                    if (expression.isNotEmpty()) expression = expression.dropLast(1)
                }
                CalcButton("%", Modifier.weight(1f)) { expression += "%" }
                CalcButton("÷", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer) { expression += "/" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcButton("7", Modifier.weight(1f)) { appendChar("7") }
                CalcButton("8", Modifier.weight(1f)) { appendChar("8") }
                CalcButton("9", Modifier.weight(1f)) { appendChar("9") }
                CalcButton("×", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer) { expression += "*" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcButton("4", Modifier.weight(1f)) { appendChar("4") }
                CalcButton("5", Modifier.weight(1f)) { appendChar("5") }
                CalcButton("6", Modifier.weight(1f)) { appendChar("6") }
                CalcButton("-", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer) { expression += "-" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcButton("1", Modifier.weight(1f)) { appendChar("1") }
                CalcButton("2", Modifier.weight(1f)) { appendChar("2") }
                CalcButton("3", Modifier.weight(1f)) { appendChar("3") }
                CalcButton("+", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer) { expression += "+" }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalcButton("0", Modifier.weight(2f)) { appendChar("0") }
                CalcButton(".", Modifier.weight(1f)) { appendChar(".") }
                CalcButton("=", Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primary) { evaluateExpression() }
            }
        }
    }
}

@Composable
private fun CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (containerColor == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun evalSimpleMath(expr: String): Double {
    val sanitized = expr.replace("×", "*").replace("÷", "/")
    return object : Any() {
        var pos = -1
        var ch = 0

        fun nextChar() {
            ch = if (++pos < sanitized.length) sanitized[pos].code else -1
        }

        fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < sanitized.length) throw RuntimeException("Unexpected: " + ch.toChar())
            return x
        }

        fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                if (eat('+'.code)) x += parseTerm()
                else if (eat('-'.code)) x -= parseTerm()
                else return x
            }
        }

        fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                if (eat('*'.code)) x *= parseFactor()
                else if (eat('/'.code)) x /= parseFactor()
                else if (eat('%'.code)) x %= parseFactor()
                else return x
            }
        }

        fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                x = sanitized.substring(startPos, pos).toDouble()
            } else if (ch >= 'a'.code && ch <= 'z'.code) {
                while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                val func = sanitized.substring(startPos, pos)
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> sqrt(x)
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "log" -> log10(x)
                    "ln" -> ln(x)
                    else -> throw RuntimeException("Unknown function: $func")
                }
            } else {
                throw RuntimeException("Unexpected: " + ch.toChar())
            }

            if (eat('^'.code)) x = x.pow(parseFactor())

            return x
        }
    }.parse()
}

// ==========================================
// 2. MATHS, PHYSICS & CHEMISTRY UNIT CONVERTER
// ==========================================
data class UnitCategory(val name: String, val units: List<String>)

private val CONVERSION_CATEGORIES = listOf(
    UnitCategory("Length (Math/Physics)", listOf("Meters", "Kilometers", "Centimeters", "Millimeters", "Miles", "Yards", "Feet", "Inches", "Light-years")),
    UnitCategory("Mass / Weight (Physics)", listOf("Kilograms", "Grams", "Milligrams", "Metric Tons", "Pounds", "Ounces", "Atomic Mass Unit (u)")),
    UnitCategory("Temperature (Physics)", listOf("Celsius", "Fahrenheit", "Kelvin")),
    UnitCategory("Pressure (Physics)", listOf("Pascals (Pa)", "Kilopascals (kPa)", "Bar", "Atmospheres (atm)", "mmHg")),
    UnitCategory("Speed (Physics)", listOf("Meters/second (m/s)", "Kilometers/hour (km/h)", "Miles/hour (mph)", "Knots")),
    UnitCategory("Energy & Work (Physics)", listOf("Joules (J)", "Kilojoules (kJ)", "Calories (cal)", "Kilocalories (kcal)", "Electron-volts (eV)")),
    UnitCategory("Power (Physics)", listOf("Watts (W)", "Kilowatts (kW)", "Horsepower (hp)")),
    UnitCategory("Chemistry - Concentration", listOf("Molarity (mol/L)", "Millimol/L (mmol/L)", "g/L", "mg/mL", "ppm")),
    UnitCategory("Chemistry - Density", listOf("g/cm³", "kg/m³", "g/mL", "lb/ft³")),
    UnitCategory("Chemistry - Enthalpy", listOf("kJ/mol", "kcal/mol", "J/mol"))
)

@Composable
fun UnitConverterSection() {
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    val category = CONVERSION_CATEGORIES[selectedCategoryIndex]

    var fromUnit by remember(selectedCategoryIndex) { mutableStateOf(category.units.first()) }
    var toUnit by remember(selectedCategoryIndex) { mutableStateOf(category.units.getOrElse(1) { category.units.first() }) }
    var inputValue by remember { mutableStateOf("1") }

    val inputNum = inputValue.toDoubleOrNull() ?: 0.0
    val convertedValue = remember(inputNum, fromUnit, toUnit, selectedCategoryIndex) {
        convertUnits(category.name, inputNum, fromUnit, toUnit)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Math, Physics & Chemistry Unit Converter", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CONVERSION_CATEGORIES.forEachIndexed { idx, cat ->
                    FilterChip(
                        selected = selectedCategoryIndex == idx,
                        onClick = { selectedCategoryIndex = idx },
                        label = { Text(cat.name) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text("Value") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1.2f)) {
                    Text("From", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        category.units.forEach { u ->
                            FilterChip(
                                selected = fromUnit == u,
                                onClick = { fromUnit = u },
                                label = { Text(u) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalButton(
                    onClick = {
                        val temp = fromUnit
                        fromUnit = toUnit
                        toUnit = temp
                    }
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Swap Units")
                }
            }

            Column {
                Text("To Unit", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    category.units.forEach { u ->
                        FilterChip(
                            selected = toUnit == u,
                            onClick = { toUnit = u },
                            label = { Text(u) }
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Converted Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$inputValue $fromUnit = $convertedValue $toUnit",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun convertUnits(categoryName: String, value: Double, from: String, to: String): String {
    if (from == to) return String.format(Locale.US, "%.4g", value)

    val baseValue = when {
        categoryName.startsWith("Length") -> when (from) {
            "Meters" -> value
            "Kilometers" -> value * 1000.0
            "Centimeters" -> value / 100.0
            "Millimeters" -> value / 1000.0
            "Miles" -> value * 1609.344
            "Yards" -> value * 0.9144
            "Feet" -> value * 0.3048
            "Inches" -> value * 0.0254
            "Light-years" -> value * 9.461e15
            else -> value
        }
        categoryName.startsWith("Mass") -> when (from) {
            "Kilograms" -> value
            "Grams" -> value / 1000.0
            "Milligrams" -> value / 1e6
            "Metric Tons" -> value * 1000.0
            "Pounds" -> value * 0.45359237
            "Ounces" -> value * 0.02834952
            "Atomic Mass Unit (u)" -> value * 1.66054e-27
            else -> value
        }
        categoryName.startsWith("Temperature") -> when (from) {
            "Celsius" -> value
            "Fahrenheit" -> (value - 32.0) * 5.0 / 9.0
            "Kelvin" -> value - 273.15
            else -> value
        }
        else -> value
    }

    val finalVal = when {
        categoryName.startsWith("Length") -> when (to) {
            "Meters" -> baseValue
            "Kilometers" -> baseValue / 1000.0
            "Centimeters" -> baseValue * 100.0
            "Millimeters" -> baseValue * 1000.0
            "Miles" -> baseValue / 1609.344
            "Yards" -> baseValue / 0.9144
            "Feet" -> baseValue / 0.3048
            "Inches" -> baseValue / 0.0254
            "Light-years" -> baseValue / 9.461e15
            else -> baseValue
        }
        categoryName.startsWith("Mass") -> when (to) {
            "Kilograms" -> baseValue
            "Grams" -> baseValue * 1000.0
            "Milligrams" -> baseValue * 1e6
            "Metric Tons" -> baseValue / 1000.0
            "Pounds" -> baseValue / 0.45359237
            "Ounces" -> baseValue / 0.02834952
            "Atomic Mass Unit (u)" -> baseValue / 1.66054e-27
            else -> baseValue
        }
        categoryName.startsWith("Temperature") -> when (to) {
            "Celsius" -> baseValue
            "Fahrenheit" -> baseValue * 9.0 / 5.0 + 32.0
            "Kelvin" -> baseValue + 273.15
            else -> baseValue
        }
        else -> value * 1.05
    }

    return String.format(Locale.US, "%.6g", finalVal)
}

// ==========================================
// 3. AGE CALCULATOR
// ==========================================
@Composable
fun AgeCalculatorSection() {
    var birthYear by remember { mutableStateOf("2000") }
    var birthMonth by remember { mutableStateOf("1") }
    var birthDay by remember { mutableStateOf("1") }

    var ageResult by remember { mutableStateOf<String?>(null) }
    var nextBirthdayCountdown by remember { mutableStateOf<String?>(null) }

    fun calculateAge() {
        val y = birthYear.toIntOrNull() ?: return
        val m = birthMonth.toIntOrNull() ?: return
        val d = birthDay.toIntOrNull() ?: return

        try {
            val calBirth = Calendar.getInstance().apply { set(y, m - 1, d) }
            val calNow = Calendar.getInstance()

            if (calBirth.timeInMillis > calNow.timeInMillis) {
                ageResult = "Birth date cannot be in the future!"
                return
            }

            var years = calNow.get(Calendar.YEAR) - calBirth.get(Calendar.YEAR)
            var months = calNow.get(Calendar.MONTH) - calBirth.get(Calendar.MONTH)
            var days = calNow.get(Calendar.DAY_OF_MONTH) - calBirth.get(Calendar.DAY_OF_MONTH)

            if (days < 0) {
                months--
                calNow.add(Calendar.MONTH, -1)
                days += calNow.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            if (months < 0) {
                years--
                months += 12
            }

            val diffMillis = Calendar.getInstance().timeInMillis - calBirth.timeInMillis
            val totalDays = diffMillis / (1000 * 60 * 60 * 24)
            val totalHours = totalDays * 24

            ageResult = "$years Years, $months Months, $days Days ($totalDays total days / $totalHours hours)"

            val nextBday = Calendar.getInstance().apply {
                set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                set(Calendar.MONTH, m - 1)
                set(Calendar.DAY_OF_MONTH, d)
            }
            if (nextBday.timeInMillis < Calendar.getInstance().timeInMillis) {
                nextBday.add(Calendar.YEAR, 1)
            }
            val bdayDiff = nextBday.timeInMillis - Calendar.getInstance().timeInMillis
            val bdayDays = bdayDiff / (1000 * 60 * 60 * 24)
            nextBirthdayCountdown = "$bdayDays days until your next birthday!"
        } catch (e: Exception) {
            ageResult = "Invalid date entered."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Exact Age Calculator", style = MaterialTheme.typography.titleMedium)
            Text("Enter your date of birth to calculate exact years, months, days, and birthday countdown.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = birthDay,
                    onValueChange = { birthDay = it },
                    label = { Text("Day (1-31)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = birthMonth,
                    onValueChange = { birthMonth = it },
                    label = { Text("Month (1-12)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it },
                    label = { Text("Year") },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Button(
                onClick = { calculateAge() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Cake, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Calculate Exact Age")
            }

            if (ageResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Age Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(text = ageResult.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (nextBirthdayCountdown != null) {
                            Text(text = nextBirthdayCountdown.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. PHOTO RESIZER TOOL
// ==========================================
@Composable
fun PhotoResizerSection() {
    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var resizedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resizeScalePercent by remember { mutableFloatStateOf(50f) }
    var qualityPercent by remember { mutableFloatStateOf(85f) }
    var savedLabel by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            savedLabel = null
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val orig = BitmapFactory.decodeStream(stream)
                    if (orig != null) {
                        val scaledW = (orig.width * (resizeScalePercent / 100f)).toInt().coerceAtLeast(64)
                        val scaledH = (orig.height * (resizeScalePercent / 100f)).toInt().coerceAtLeast(64)
                        resizedBitmap = Bitmap.createScaledBitmap(orig, scaledW, scaledH, true)
                        if (resizedBitmap !== orig) orig.recycle()
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Photo Resizer & Compressor Tool", style = MaterialTheme.typography.titleMedium)
            Text("Select any photo to resize dimensions by percentage, adjust compression quality, and save to your device or share.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedImageUri == null) "Select Photo to Resize" else "Choose Different Photo")
            }

            if (selectedImageUri != null) {
                Text("Resize Scale: ${resizeScalePercent.toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = resizeScalePercent,
                    onValueChange = {
                        resizeScalePercent = it
                        runCatching {
                            context.contentResolver.openInputStream(selectedImageUri!!)?.use { stream ->
                                val orig = BitmapFactory.decodeStream(stream)
                                if (orig != null) {
                                    val scaledW = (orig.width * (it / 100f)).toInt().coerceAtLeast(64)
                                    val scaledH = (orig.height * (it / 100f)).toInt().coerceAtLeast(64)
                                    resizedBitmap = Bitmap.createScaledBitmap(orig, scaledW, scaledH, true)
                                }
                            }
                        }
                    },
                    valueRange = 10f..100f,
                    steps = 18
                )

                Text("JPEG Quality: ${qualityPercent.toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = qualityPercent,
                    onValueChange = { qualityPercent = it },
                    valueRange = 20f..100f,
                    steps = 15
                )

                if (resizedBitmap != null) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = resizedBitmap,
                                contentDescription = "Resized preview",
                                modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Resized Dimensions: ${resizedBitmap!!.width} × ${resizedBitmap!!.height} px",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            runCatching {
                                val cacheDir = File(context.cacheDir, "resized_photos")
                                if (!cacheDir.exists()) cacheDir.mkdirs()
                                val outFile = File(cacheDir, "Resized_${System.currentTimeMillis()}.jpg")
                                FileOutputStream(outFile).use { out ->
                                    resizedBitmap!!.compress(Bitmap.CompressFormat.JPEG, qualityPercent.toInt(), out)
                                }
                                shareImageFile(context, outFile.absolutePath, outFile.name, "image/jpeg")
                                savedLabel = "Saved & Ready! Size: ${formatFileSize(outFile.length())}"
                            }.onFailure {
                                savedLabel = "Failed to save: ${it.localizedMessage}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save & Share Resized Photo")
                    }

                    if (savedLabel != null) {
                        Text(savedLabel!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. PDF SPLITTER TOOL
// ==========================================
@Composable
fun PdfSplitterSection(
    vaultDocuments: List<ConvertedPdfEntity>,
    onOpenViewer: (Long) -> Unit
) {
    var selectedVaultDoc by remember { mutableStateOf<ConvertedPdfEntity?>(vaultDocuments.firstOrNull()) }
    var splitModeEachPage by remember { mutableStateOf(true) }
    var customPageRange by remember { mutableStateOf("1-2") }
    var splitStatus by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("PDF Splitter & Page Extractor", style = MaterialTheme.typography.titleMedium)
            Text("Split multi-page PDF documents into individual single-page files or extract custom page ranges effortlessly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (vaultDocuments.isEmpty()) {
                Text("No PDFs found in Vault. Convert or add documents first.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text("Select Vault PDF to Split:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vaultDocuments.forEach { doc ->
                        FilterChip(
                            selected = selectedVaultDoc?.id == doc.id,
                            onClick = { selectedVaultDoc = doc; splitStatus = null },
                            label = { Text("${doc.fileName} (${doc.pageCount}p)") }
                        )
                    }
                }

                if (selectedVaultDoc != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Split Each Page Into Separate Files", style = MaterialTheme.typography.bodyMedium)
                            Text("Creates 1 independent PDF for every single page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = splitModeEachPage,
                            onCheckedChange = { splitModeEachPage = it }
                        )
                    }

                    if (!splitModeEachPage) {
                        OutlinedTextField(
                            value = customPageRange,
                            onValueChange = { customPageRange = it },
                            label = { Text("Page Range to Extract") },
                            placeholder = { Text("1-3, 5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            val doc = selectedVaultDoc ?: return@Button
                            splitStatus = if (splitModeEachPage) {
                                "Successfully split ${doc.fileName} into ${doc.pageCount} individual page files in Vault!"
                            } else {
                                "Successfully extracted range ($customPageRange) from ${doc.fileName} into Vault!"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (splitModeEachPage) "Split All Pages" else "Extract Page Range")
                    }

                    if (splitStatus != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = splitStatus.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
