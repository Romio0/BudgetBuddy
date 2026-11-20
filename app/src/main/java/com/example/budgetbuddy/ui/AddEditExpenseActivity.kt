package com.example.budgetbuddy.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetbuddy.R
import com.example.budgetbuddy.data.AppDatabase
import com.example.budgetbuddy.utils.IntentKeys.EXTRA_GROUP_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

private val TealDark = Color(0xFF2E5B58)
private val TealLightPill = Color(0xFFB4D8D6)
private val BorderColor = Color(0xFF8DAAA6)
private val TextDark = Color(0xFF1C1B1F)
private val PlaceholderGray = Color(0xFF7D8C8A)

class AddEditExpenseActivity : AppCompatActivity() {

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            Toast.makeText(this, "Photo captured", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_edit_expense)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val db = AppDatabase.getInstance(this)

        val composeView = findViewById<ComposeView>(R.id.compose_view)
        composeView.setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White,
                ) {
                    val groupIdParam = intent.getStringExtra(com.example.budgetbuddy.utils.IntentKeys.EXTRA_GROUP_ID).orEmpty()
                    var selectedGroupId by remember { mutableStateOf(groupIdParam) }

                    // Fetch all groups
                    val allGroups by remember(db) {
                        kotlinx.coroutines.flow.flow { emit(db.groupDao().getAllGroups()) }
                    }.collectAsState(initial = emptyList())

                    // Fetch members for the selected group
                    val memberFlow: Flow<List<String>> = remember(db, selectedGroupId) {
                        if (selectedGroupId.isNotEmpty()) {
                            db.memberDao().observeMembersForGroup(selectedGroupId).map { list -> list.map { it.memberName } }
                        } else {
                            kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                    }
                    val memberNames by memberFlow.collectAsState(initial = emptyList())
                    
                    AddEditExpenseScreen(
                        groups = allGroups,
                        selectedGroupId = selectedGroupId,
                        onGroupSelected = { selectedGroupId = it },
                        members = memberNames,
                        onCameraClick = { cameraLauncher.launch(null) }
                    ) { title, category, amount, currency, paidBy, selectedMembers, date ->
                        // Todo: Save Expense Data into Room Database Here Instead of sending back using Intent
                        val intent = Intent(this, GroupDetailsActivity::class.java).apply {
                            putExtra(com.example.budgetbuddy.utils.IntentKeys.EXTRA_GROUP_ID, selectedGroupId)
                            putExtra("EXTRA_TITLE", title)
                            putExtra("EXTRA_CATEGORY", category)
                            putExtra("EXTRA_AMOUNT", amount)
                            putExtra("EXTRA_CURRENCY", currency)
                            putExtra("EXTRA_PAID_BY", paidBy)
                            putExtra("EXTRA_DATE", date)
                            putStringArrayListExtra(
                                "EXTRA_SPLIT",
                                ArrayList(selectedMembers)
                            )
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }


@Preview
@Composable
fun AddEditExpenseScreen(
    groups: List<com.example.budgetbuddy.data.SharedGroup> = emptyList(),
    selectedGroupId: String = "",
    onGroupSelected: (String) -> Unit = {},
    members: List<String> = emptyList(),
    onCameraClick: () -> Unit = {},
    onAddExpenseClick: (
        title: String,
        category: String,
        amount: String,
        currency: String,
        paidBy: String,
        selectedMembers: List<String>,
        date: String
    ) -> Unit = { _, _, _, _, _, _, _ -> }
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCurrency by remember { mutableStateOf("EGP") }
    var selectedPaidBy by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf("") }

    val categories = listOf("Food & Dining", "Transport", "Rent", "Utilities", "Entertainment", "Shopping", "Other")
    val currencies = listOf("EGP", "USD", "EUR", "GBP", "SAR", "AED")

    val memberSelections = remember(members) {
        mutableStateMapOf<String, Boolean>().apply {
            members.forEach { put(it, false) }
        }
    }

    val customSplitAmounts = remember(members) {
        mutableStateMapOf<String, String>()
    }

    val isCustomSplit = remember(members) {
        mutableStateMapOf<String, Boolean>()
    }

    val context = LocalContext.current

    val totalAmount = amount.toDoubleOrNull() ?: 0.0

    val customTotal = memberSelections.entries
        .filter { (m, checked) -> checked && (isCustomSplit[m] == true) }
        .sumOf { (m, _) -> customSplitAmounts[m]?.toDoubleOrNull() ?: 0.0 }

    val nonCustomCheckedCount = memberSelections.entries
        .count { (m, checked) -> checked && (isCustomSplit[m] != true) }

    val remainingAmount = maxOf(0.0, totalAmount - customTotal)
    val defaultSharePerNonCustom = if (nonCustomCheckedCount > 0) remainingAmount / nonCustomCheckedCount else 0.0
    val formattedDefaultShare = String.format(Locale.US, "%.2f", defaultSharePerNonCustom)

    fun getMemberSplitDisplayAmount(member: String): String {
        val isChecked = memberSelections[member] ?: false
        if (!isChecked) {
            return "0.00"
        }
        if (isCustomSplit[member] == true) {
            return customSplitAmounts[member] ?: ""
        }
        return formattedDefaultShare
    }

    val onToggleMember: (String) -> Unit = { member ->
        val currentChecked = memberSelections[member] ?: false
        val newChecked = !currentChecked
        memberSelections[member] = newChecked
        if (!newChecked) {
            isCustomSplit[member] = false
            customSplitAmounts[member] = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Add Expense",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = TextDark,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            textAlign = TextAlign.Center
        )

        // Group Selection
        if (groups.isNotEmpty()) {
            FormFieldLabel(text = "Group")
            val groupNames = groups.map { it.groupName }
            val selectedGroupName = groups.find { it.id == selectedGroupId }?.groupName ?: ""
            CustomDropdownMenu(
                options = groupNames,
                selectedOption = selectedGroupName,
                placeholder = "Select Group",
                onOptionSelected = { name -> 
                    val group = groups.find { it.groupName == name }
                    if (group != null) onGroupSelected(group.id)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }

        // Title Section
        FormFieldLabel(text = "Title")
        CustomOutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "Expense Title",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category Section
        FormFieldLabel(text = "Category")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CustomDropdownMenu(
                options = categories,
                selectedOption = selectedCategory,
                placeholder = "Categories",
                onOptionSelected = { selectedCategory = it },
                modifier = Modifier.weight(1f)
            )

            // Camera Icon Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(10.dp))
                    .clickable { onCameraClick() },
                contentAlignment = Alignment.Center
            ) {
                CameraIcon(modifier = Modifier.size(24.dp), color = BorderColor)
            }

            // Calendar Icon Button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(10.dp))
                    .clickable {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                selectedDate = "$dayOfMonth/${month + 1}/$year"
                            },
                            calendar[Calendar.YEAR],
                            calendar[Calendar.MONTH],
                            calendar[Calendar.DAY_OF_MONTH]
                        ).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                CalendarIcon(modifier = Modifier.size(22.dp), color = BorderColor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Amount Section
        FormFieldLabel(text = "Amount")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CustomDropdownMenu(
                options = currencies,
                selectedOption = selectedCurrency,
                placeholder = "EGP",
                onOptionSelected = { selectedCurrency = it },
                modifier = Modifier.width(100.dp)
            )

            CustomOutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = "Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Paid By Section
        FormFieldLabel(text = "Paid By")
        CustomDropdownMenu(
            options = members,
            selectedOption = selectedPaidBy,
            placeholder = "Who Paid For It?",
            onOptionSelected = { selectedPaidBy = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Split Section
        FormFieldLabel(text = "Split")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(10.dp))
                .background(Color.White, shape = RoundedCornerShape(10.dp))
        ) {
            if (members.isEmpty()) {

            } else {
                Column {
                    members.forEachIndexed { index, member ->
                        val isChecked = memberSelections[member] ?: false
                        val displayAmount = getMemberSplitDisplayAmount(member)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggleMember(member) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleMember(member) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BorderColor,
                                        uncheckedColor = BorderColor,
                                        checkmarkColor = Color.White
                                    )
                                )

                                Text(
                                    text = member,
                                    fontSize = 16.sp,
                                    color = TextDark,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }

                            BasicTextField(
                                value = if (!isChecked && displayAmount == "0.00") "" else displayAmount,
                                onValueChange = { newText ->
                                    isCustomSplit[member] = true
                                    customSplitAmounts[member] = newText
                                    if (!isChecked) {
                                        memberSelections[member] = true
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(
                                    color = TextDark,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(TealDark),
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(36.dp)
                                    .background(TealLightPill, shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        val currentText = if (!isChecked && displayAmount == "0.00") "" else displayAmount
                                        if (currentText.isEmpty()) {
                                            Text(
                                                text = "0.00",
                                                fontSize = 15.sp,
                                                color = PlaceholderGray,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        if (index < (members.size - 1)) {
                            HorizontalDivider(
                                color = BorderColor.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Add Expense Primary Button
        Button(
            onClick = {
                val selectedList = memberSelections.filterValues { it }.keys.toList()
                onAddExpenseClick(
                    title,
                    selectedCategory,
                    amount,
                    selectedCurrency,
                    selectedPaidBy,
                    selectedList,
                    selectedDate
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealDark,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Add Expense",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FormFieldLabel(text: String = "Label") {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        color = TextDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    contentPadding: PaddingValues = PaddingValues(top = 8.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = TextStyle(color = TextDark, fontSize = 15.sp),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(
                        text = placeholder,
                        color = PlaceholderGray,
                        fontSize = 15.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderColor,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = TealDark
                ),
                contentPadding = contentPadding,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderColor,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            )
        },
        modifier = modifier.height(52.dp)
    )
}

@Composable
fun CustomDropdownMenu(
    modifier: Modifier = Modifier,
    options: List<String> = listOf("Option 1", "Option 2"),
    selectedOption: String = "",
    placeholder: String = "Select option",
    onOptionSelected: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(10.dp))
                .background(Color.White, shape = RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedOption.ifEmpty { placeholder },
                    color = if (selectedOption.isNotEmpty()) TextDark else PlaceholderGray,
                    fontSize = 15.sp
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand dropdown",
                    tint = BorderColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option, color = TextDark) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CameraIcon(modifier: Modifier = Modifier, color: Color = BorderColor) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        val bumpWidth = w * 0.35f
        val bumpHeight = h * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset((w - bumpWidth) / 2, 0f),
            size = Size(bumpWidth, bumpHeight * 2),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val bodyTop = h * 0.18f
        val bodyHeight = h * 0.78f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, bodyTop),
            size = Size(w, bodyHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val lensRadius = h * 0.22f
        drawCircle(
            color = color,
            center = Offset(w / 2, bodyTop + bodyHeight / 2),
            radius = lensRadius,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun CalendarIcon(modifier: Modifier = Modifier, color: Color = BorderColor) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()

        val top = h * 0.15f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = Size(w, h - top),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val ringWidth = 2.dp.toPx()
        val ringHeight = h * 0.22f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f - ringWidth / 2, 0f),
            size = Size(ringWidth, ringHeight),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.72f - ringWidth / 2, 0f),
            size = Size(ringWidth, ringHeight),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )

        val lineY = top + (h - top) * 0.3f
        drawLine(
            color = color,
            start = Offset(0f, lineY),
            end = Offset(w, lineY),
            strokeWidth = strokeWidth
        )

        val dotRadius = 1.2.dp.toPx()
        val startX = w * 0.3f
        val gapX = w * 0.4f
        val startY = lineY + (h - lineY) * 0.32f
        val gapY = (h - lineY) * 0.32f

        for (i in 0..1) {
            for (j in 0..1) {
                drawCircle(
                    color = color,
                    center = Offset(startX + i * gapX, startY + j * gapY),
                    radius = dotRadius
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddEditExpenseScreenPreview() {
    MaterialTheme {
        AddEditExpenseScreen()
    }
}
}