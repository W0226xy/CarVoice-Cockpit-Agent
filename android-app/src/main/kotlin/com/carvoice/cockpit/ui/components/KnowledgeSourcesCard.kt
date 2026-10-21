package com.carvoice.cockpit.ui.components
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.model.KnowledgeSourceUi
@Composable fun KnowledgeSourcesCard(sources:List<KnowledgeSourceUi>,modifier:Modifier=Modifier){Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("车辆知识库 · ${sources.size} 项",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.tertiary);sources.forEach{source->var expanded by remember(source.documentId){mutableStateOf(false)};Card{Column(Modifier.fillMaxWidth().padding(12.dp).clickable{expanded=!expanded}){Text("[${source.index}] ${source.documentName}");val meta=listOfNotNull(source.pageNumber?.let{"第${it}页"},source.sectionTitle).joinToString(" · ");if(meta.isNotBlank())Text(meta,style=MaterialTheme.typography.labelSmall);Text(source.snippet,maxLines=if(expanded)Int.MAX_VALUE else 2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}}}}
