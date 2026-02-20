package com.aurora.modifypositioning.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.PlaceSuggestion

@Composable
fun PlaceSearchBar(
    query: String,
    suggestions: List<PlaceSuggestion>,
    isSearching: Boolean,
    searchError: String?,
    onQueryChanged: (String) -> Unit,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("输入位置（地址搜索）", color = Color(0xFF1F2937)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF111827)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Color(0xFF111827),
                unfocusedTextColor = Color(0xFF111827),
                focusedBorderColor = Color(0xFF2563EB),
                unfocusedBorderColor = Color(0xFF94A3B8),
                focusedLabelColor = Color(0xFF1F2937),
                unfocusedLabelColor = Color(0xFF475569),
                cursorColor = Color(0xFF2563EB),
            ),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
            },
        )

        if (searchError != null) {
            Text(
                text = searchError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    suggestions.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSuggestion(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF111827),
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF334155),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
