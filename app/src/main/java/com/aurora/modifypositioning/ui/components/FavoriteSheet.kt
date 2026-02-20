package com.aurora.modifypositioning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurora.modifypositioning.model.FavoriteLocation

@Composable
fun FavoriteSheet(
    favorites: List<FavoriteLocation>,
    favoriteNameInput: String,
    onFavoriteNameInputChanged: (String) -> Unit,
    onAddFavorite: () -> Unit,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onDeleteFavorite: (FavoriteLocation) -> Unit,
    onRenameFavorite: (FavoriteLocation, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("收藏点", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = favoriteNameInput,
                    onValueChange = onFavoriteNameInputChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("收藏名称") },
                    singleLine = true,
                )
                Button(onClick = onAddFavorite) {
                    Text("添加")
                }
            }

            if (favorites.isEmpty()) {
                Text("暂无收藏")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(favorites, key = { it.id }) { item ->
                        FavoriteRow(
                            item = item,
                            onSelectFavorite = onSelectFavorite,
                            onDeleteFavorite = onDeleteFavorite,
                            onRenameFavorite = onRenameFavorite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    item: FavoriteLocation,
    onSelectFavorite: (FavoriteLocation) -> Unit,
    onDeleteFavorite: (FavoriteLocation) -> Unit,
    onRenameFavorite: (FavoriteLocation, String) -> Unit,
) {
    var renameMode by remember(item.id) { mutableStateOf(false) }
    var renameText by remember(item.id) { mutableStateOf(item.name) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium)
            Text("(${item.lat}, ${item.lng})", style = MaterialTheme.typography.bodySmall)

            if (renameMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("新名称") },
                    )
                    Button(
                        onClick = {
                            onRenameFavorite(item, renameText)
                            renameMode = false
                        },
                    ) {
                        Text("保存")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSelectFavorite(item) }) {
                    Text("使用")
                }
                TextButton(onClick = { renameMode = !renameMode }) {
                    Text(if (renameMode) "取消重命名" else "重命名")
                }
                TextButton(onClick = { onDeleteFavorite(item) }) {
                    Text("删除")
                }
            }
        }
    }
}
