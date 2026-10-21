package com.carvoice.cockpit.ui.knowledge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carvoice.cockpit.data.DocumentStatus
import com.carvoice.cockpit.data.KnowledgeBase
import com.carvoice.cockpit.data.KnowledgeDocument

@Composable fun KnowledgeBaseScreen(state:KnowledgeBaseUiState,onBackToConversation:()->Unit,onOpenSidebar:(()->Unit)?,onRefresh:()->Unit,onSelect:(String)->Unit,onCreate:(String,String)->Unit,onUpload:()->Unit,onDelete:(KnowledgeDocument)->Unit,onConfirmDelete:()->Unit,onCancelDelete:()->Unit,onDismissError:()->Unit,modifier:Modifier=Modifier){
 var name by remember{mutableStateOf("")};var description by remember{mutableStateOf("")}
 Column(modifier.fillMaxSize().padding(24.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Row{onOpenSidebar?.let{TextButton(onClick=it){Text("会话")}};Text("知识库",style=MaterialTheme.typography.headlineSmall)};TextButton(onClick=onBackToConversation){Text("返回对话")}}
  state.error?.let{Card{Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(it,Modifier.weight(1f));TextButton(onClick=onDismissError){Text("关闭")}}}}
  Row(Modifier.fillMaxSize().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(18.dp)){
   Column(Modifier.width(320.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("知识库列表");OutlinedTextField(name,{name=it},label={Text("名称")},singleLine=true);OutlinedTextField(description,{description=it},label={Text("描述")});Button(onClick={onCreate(name,description)},enabled=!state.creating&&name.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(state.creating)"正在创建" else "创建知识库")};OutlinedButton(onClick=onRefresh,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("刷新")};if(!state.loading&&state.bases.isEmpty())Text("还没有知识库，请先创建一个。")
    LazyColumn{items(state.bases,key=KnowledgeBase::id){base->ListItem(headlineContent={Text(base.name)},supportingContent={Text(base.description)},trailingContent={if(base.id==state.selectedId)Text("已选中")});TextButton(onClick={onSelect(base.id)}){Text("选择")}}}}
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){val selected=state.bases.firstOrNull{it.id==state.selectedId};Text(selected?.name?:"请选择知识库",style=MaterialTheme.typography.titleLarge);Button(onClick=onUpload,enabled=selected!=null&&!state.uploading,modifier=Modifier.heightIn(min=48.dp)){Text(if(state.uploading)"${state.uploadName?:"资料"}：正在上传/处理" else if(selected==null)"请先选择知识库" else "导入 PDF / TXT")};if(state.loading)CircularProgressIndicator();if(selected!=null&&state.documents.isEmpty()&&!state.loading)Text("当前知识库没有文档。")
    LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(state.documents,key=KnowledgeDocument::id){doc->Card{Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Text(doc.fileName);Text("${doc.contentType} · ${doc.status.label()}");doc.errorMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}};TextButton(onClick={onDelete(doc)},enabled=!state.deleting){Text("删除")}}}}}
   }
  }
 }
 state.deleteTarget?.let{AlertDialog(onDismissRequest=onCancelDelete,title={Text("删除文档？")},text={Text("将删除 ${it.fileName} 及其对应切片。")},confirmButton={Button(onClick=onConfirmDelete){Text("确认删除")}},dismissButton={TextButton(onClick=onCancelDelete){Text("取消")}})}
}
private fun DocumentStatus.label()=when(this){DocumentStatus.PENDING->"等待处理";DocumentStatus.PROCESSING->"正在解析和向量化";DocumentStatus.READY->"可用于问答";DocumentStatus.FAILED->"处理失败"}
