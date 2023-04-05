package com.github.proegssilb.saberbuilder.ui

import android.graphics.Point
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.github.proegssilb.saberbuilder.R

data class Module(
    val name: String,
    val render_location: Point,
    val target_location: Point
)

@Composable
fun ModuleList(
    modules: List<Module>,
    onModuleSelected: (Module) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.saber_with_runes_blue),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(modules) { module ->
                ModuleListItem(module = module) {
                    onModuleSelected(module)
                }
            }
        }
    }
}

@Composable
fun ModuleListItem(
    module: Module,
    onModuleClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onModuleClick),
        backgroundColor = Color.White,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = module.name,
                style = MaterialTheme.typography.h6,
                color = Color.Black
            )
        }
    }
}