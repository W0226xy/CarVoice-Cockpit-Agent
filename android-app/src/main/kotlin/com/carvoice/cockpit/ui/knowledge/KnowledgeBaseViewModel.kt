package com.carvoice.cockpit.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carvoice.cockpit.data.DocumentStatus
import com.carvoice.cockpit.data.KnowledgeBase
import com.carvoice.cockpit.data.KnowledgeBaseRepository
import com.carvoice.cockpit.data.KnowledgeDocument
import com.carvoice.cockpit.data.UploadDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnowledgeBaseUiState(val loading:Boolean=true,val bases:List<KnowledgeBase> = emptyList(),val selectedId:String?=null,val documents:List<KnowledgeDocument> = emptyList(),val creating:Boolean=false,val uploading:Boolean=false,val uploadName:String?=null,val deleteTarget:KnowledgeDocument?=null,val deleting:Boolean=false,val error:String?=null,val retryable:Boolean=false)
class KnowledgeBaseViewModel(private val repository: KnowledgeBaseRepository):ViewModel(){
    private val _ui=MutableStateFlow(KnowledgeBaseUiState());val uiState:StateFlow<KnowledgeBaseUiState> = _ui.asStateFlow();private var poll:Job?=null
    init { viewModelScope.launch { repository.observeSelectedKnowledgeBaseId().collect { stored -> load(stored) } } }
    fun refresh()=load(_ui.value.selectedId)
    private fun load(preferred:String?){viewModelScope.launch(Dispatchers.IO){_ui.update{it.copy(loading=true,error=null)};repository.getKnowledgeBases().onSuccess { bases -> val selected=bases.firstOrNull{it.id==preferred}?.id ?: bases.firstOrNull()?.id; if(selected!=preferred) repository.selectKnowledgeBase(selected); _ui.update{it.copy(loading=false,bases=bases,selectedId=selected)};if(selected!=null) loadDocuments(selected)}.onFailure{fail(it)}}}
    fun select(id:String){if(id==_ui.value.selectedId)return;viewModelScope.launch(Dispatchers.IO){poll?.cancel();repository.selectKnowledgeBase(id);_ui.update{it.copy(selectedId=id,documents=emptyList())};loadDocuments(id)}}
    fun create(name:String,description:String){if(_ui.value.creating||name.isBlank())return;viewModelScope.launch(Dispatchers.IO){_ui.update{it.copy(creating=true,error=null)};repository.createKnowledgeBase(name.trim(),description.trim()).onSuccess{base->repository.selectKnowledgeBase(base.id);_ui.update{it.copy(creating=false)};load(base.id)}.onFailure{_ui.update{it.copy(creating=false)};fail(it)}}}
    fun upload(document:UploadDocument){val base=_ui.value.selectedId?:return;if(_ui.value.uploading)return;viewModelScope.launch(Dispatchers.IO){_ui.update{it.copy(uploading=true,uploadName=document.fileName,error=null)};repository.uploadDocument(base,document).onSuccess{_ui.update{it.copy(uploading=false,uploadName=null)};loadDocuments(base)}.onFailure{_ui.update{it.copy(uploading=false,uploadName=null)};fail(it)}}}
    private fun loadDocuments(base:String){viewModelScope.launch(Dispatchers.IO){repository.getDocuments(base).onSuccess{docs->_ui.update{it.copy(documents=docs)};schedulePoll(base,docs)}.onFailure(::fail)}}
    private fun schedulePoll(base:String,docs:List<KnowledgeDocument>){poll?.cancel();if(docs.none{it.status==DocumentStatus.PENDING||it.status==DocumentStatus.PROCESSING})return;poll=viewModelScope.launch(Dispatchers.IO){repeat(10){delay(3000);if(_ui.value.selectedId!=base)return@launch;val result=repository.getDocuments(base);result.onSuccess{new-> _ui.update{it.copy(documents=new)};if(new.all{it.status==DocumentStatus.READY||it.status==DocumentStatus.FAILED})return@launch}.onFailure{fail(it);return@launch}}}}
    fun requestDelete(doc:KnowledgeDocument){_ui.update{it.copy(deleteTarget=doc)}};fun cancelDelete(){_ui.update{it.copy(deleteTarget=null)}}
    fun confirmDelete(){val base=_ui.value.selectedId?:return;val doc=_ui.value.deleteTarget?:return;if(_ui.value.deleting)return;viewModelScope.launch(Dispatchers.IO){_ui.update{it.copy(deleting=true)};repository.deleteDocument(base,doc.id).onSuccess{_ui.update{it.copy(deleting=false,deleteTarget=null)};loadDocuments(base)}.onFailure{_ui.update{it.copy(deleting=false)};fail(it)}}}
    fun dismissError(){_ui.update{it.copy(error=null)}};private fun fail(error:Throwable){_ui.update{it.copy(loading=false,error=error.message?:"知识库操作失败，请稍后重试。",retryable=true)}}
    override fun onCleared(){poll?.cancel()}
}
class KnowledgeBaseViewModelFactory(private val repository:KnowledgeBaseRepository):ViewModelProvider.Factory{override fun <T:ViewModel> create(modelClass:Class<T>):T{@Suppress("UNCHECKED_CAST") return KnowledgeBaseViewModel(repository) as T}}
