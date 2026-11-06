package com.example.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.EaSubmissionStatus
import com.example.core.model.StrategyCategory
import com.example.ui.XauPilotViewModel
import com.example.ui.theme.*

@Composable
fun PublisherWorkspaceScreen(
    viewModel: XauPilotViewModel,
    modifier: Modifier = Modifier
) {
    val submissions by viewModel.eaSubmissions.collectAsState()
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.0.0") }
    var description by remember { mutableStateOf("") }
    var monthlyPrice by remember { mutableStateOf("49") }
    var selectedCategory by remember { mutableStateOf(StrategyCategory.GOLD_TREND) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedFileName = uri?.lastPathSegment?.substringAfterLast('/')
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CharcoalBackground).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GoldLight)
                }
                Column {
                    Text("PUBLISHER WORKSPACE", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GoldLight)
                    Text("Submit compiled EAs for review and marketplace publishing", fontSize = 12.sp, color = TextSecondaryDark)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalSurface), border = BorderStroke(1.dp, GoldPrimary.copy(alpha = .35f))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit a new EA", fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Text("Upload compiled .ex5 packages only. VPS deployment is intentionally pending until hosting is linked.", fontSize = 11.sp, color = TextSecondaryDark)
                    OutlinedButton(onClick = { filePicker.launch("application/octet-stream") }, modifier = Modifier.fillMaxWidth().testTag("publisher_upload_button")) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(selectedFileName ?: "Choose .ex5 package")
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("EA name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text("Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = monthlyPrice, onValueChange = { monthlyPrice = it }, label = { Text("Monthly price (USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Strategy category", fontSize = 12.sp, color = TextSecondaryDark)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(StrategyCategory.GOLD_TREND, StrategyCategory.GOLD_SCALPING, StrategyCategory.GOLD_BREAKOUT).forEach { category ->
                            FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category }, label = { Text(category.label, fontSize = 10.sp) })
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.submitEaForReview(name, version, selectedFileName ?: "package.ex5", 0L, selectedCategory, description, monthlyPrice.toDoubleOrNull() ?: 49.0)
                            name = ""
                            description = ""
                            selectedFileName = null
                        },
                        enabled = name.isNotBlank() && selectedFileName != null,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("submit_ea_review_button")
                    ) { Text("Submit for review", color = CharcoalBackground, fontWeight = FontWeight.Bold) }
                }
            }
        }

        item { Text("Submission pipeline", fontWeight = FontWeight.Bold, color = GoldLight, modifier = Modifier.padding(top = 4.dp)) }
        items(submissions, key = { it.id }) { submission ->
            Card(colors = CardDefaults.cardColors(containerColor = CharcoalSurface), border = BorderStroke(1.dp, CharcoalCardBorder)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(submission.name, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Text("v${submission.version} • ${submission.fileName}", fontSize = 11.sp, color = TextSecondaryDark)
                        }
                        Text(submission.status.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (submission.status == EaSubmissionStatus.APPROVED) TradingGreen else TradingAmber)
                    }
                    if (submission.status == EaSubmissionStatus.SUBMITTED) {
                        Text("Demo admin review", fontSize = 11.sp, color = TextSecondaryDark)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.rejectEaSubmission(submission.id, "Please add clearer risk documentation") }, modifier = Modifier.weight(1f)) { Text("Request changes", fontSize = 11.sp) }
                            Button(onClick = { viewModel.approveEaSubmission(submission.id) }, colors = ButtonDefaults.buttonColors(containerColor = TradingGreen), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Approve", fontSize = 11.sp)
                            }
                        }
                    } else if (submission.status == EaSubmissionStatus.APPROVED) {
                        Text("Published to marketplace. Customer installs will remain queued until VPS linking is added.", fontSize = 11.sp, color = TradingGreen)
                    } else if (submission.reviewNote != null) {
                        Text(submission.reviewNote, fontSize = 11.sp, color = TradingAmber)
                    }
                }
            }
        }
    }
}
