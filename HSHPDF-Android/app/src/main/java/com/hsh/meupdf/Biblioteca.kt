// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Um arquivo ou pasta dentro de uma pasta da biblioteca. */
data class ItemPasta(
    val docId: String, val nome: String, val ehPasta: Boolean, val tamanho: Long, val modificado: Long, val uri: Uri
) {
    /** pdf, epub, cbz, cbr, imagem ou outro */
    val tipo: String
        get() = when (nome.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "pdf"
            "epub" -> "epub"
            "cbz", "zip" -> "cbz"
            "cbr", "rar" -> "cbr"
            else -> if (FonteImagens.ehImagem(nome)) "imagem" else "outro"
        }
    val legivel: Boolean get() = !ehPasta && tipo in setOf("pdf", "epub", "cbz", "cbr")
    val ehImagem: Boolean get() = !ehPasta && tipo == "imagem"
}

object Pastas {
    fun raiz(arvore: Uri): String = DocumentsContract.getTreeDocumentId(arvore)

    /** Lista o conteúdo de uma pasta (pastas primeiro; nomes em ordem natural). */
    suspend fun listar(ctx: Context, arvore: Uri, docIdPai: String): List<ItemPasta> = withContext(Dispatchers.IO) {
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, docIdPai)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val saida = ArrayList<ItemPasta>()
        ctx.contentResolver.query(filhos, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val nome = c.getString(1) ?: continue
                if (nome.startsWith(".")) continue
                val dir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                saida.add(ItemPasta(id, nome, dir, if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4),
                    DocumentsContract.buildDocumentUriUsingTree(arvore, id)))
            }
        }
        saida.sortedWith { a, b -> if (a.ehPasta != b.ehPasta) (if (a.ehPasta) -1 else 1) else comparaNatural(a.nome, b.nome) }
    }

    /** Nome real de um documento (o Android pode ter acrescentado "(2)" ao criar). */
    fun nomeDoUri(ctx: Context, uri: Uri, padrao: String): String {
        var nome = padrao
        try {
            ctx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) nome = it.getString(0) ?: nome
            }
        } catch (e: Exception) { }
        return nome
    }

    fun formataTamanho(b: Long): String = when {
        b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
        b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
        b >= 1024 -> "${b / 1024} KB"
        else -> "$b B"
    }
}

/** Capas dos arquivos (primeira página), guardadas em disco para a lista abrir rápido da próxima vez. */
object Capas {
    private val limite = Semaphore(2)

    private fun chaveDe(item: ItemPasta): String {
        val md = MessageDigest.getInstance("MD5").digest("${item.uri}|${item.tamanho}|${item.modificado}".toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    suspend fun obter(ctx: Context, item: ItemPasta): Bitmap? = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "capas").apply { mkdirs() }
        val arq = File(dir, chaveDe(item) + ".jpg")
        if (arq.exists()) return@withContext BitmapFactory.decodeFile(arq.path)
        limite.withPermit {
            try {
                val f = Abertura.abrirFonte(ctx, item.uri)
                try {
                    var b = f.bitmapParaExportar(0, 240)
                    if (b.width > 320) {
                        val e = Bitmap.createScaledBitmap(b, 240, (b.height * 240f / b.width).toInt().coerceAtLeast(1), true)
                        if (e !== b) b.recycle()
                        b = e
                    }
                    arq.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                    b
                } finally {
                    f.fechar()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Composable
fun CapaMiniatura(item: ItemPasta, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, item.uri) { value = Capas.obter(ctx, item) }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Ico(if (item.tipo == "epub") "livro" else "pasta", MaterialTheme.colorScheme.onSurfaceVariant, 26.dp)
        }
    }
}

private fun etiqueta(tipo: String) = when (tipo) {
    "pdf" -> "PDF"
    "epub" -> "EPUB"
    "cbz" -> "CBZ"
    "cbr" -> "CBR"
    "imagem" -> "Imagem"
    else -> ""
}

// ====================================================== início: continuar lendo + pastas
@Composable
fun BibliotecaTela(
    cfg: Config, pastas: List<PastaBiblioteca>, recentes: List<Recente>, refresco: Int,
    onAdicionar: () -> Unit, onAbrirPasta: (PastaBiblioteca) -> Unit, onRemoverPasta: (PastaBiblioteca) -> Unit,
    onAbrirRecente: (Recente) -> Unit, onAbrirArquivo: () -> Unit
) {
    val fundo = MaterialTheme.colorScheme.background
    LazyColumn(Modifier.fillMaxSize().background(fundo).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Biblioteca", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 20.dp))
        }
        if (recentes.isNotEmpty()) {
            item { Text("Continuar lendo", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
            item {
                val r = recentes.first()
                val prog = remember(refresco, r.uri) { cfg.progresso(r.uri) }
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth().clickable { onAbrirRecente(r) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CapaMiniatura(ItemPasta("", r.nome, false, 0L, 0L, Uri.parse(r.uri)), Modifier.width(72.dp).height(104.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(r.nome, maxLines = 2, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (prog != null) {
                                Text("Página ${prog.first + 1} de ${prog.second}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                LinearProgressIndicator(progress = { ((prog.first + 1f) / prog.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            }
                            Button(onClick = { onAbrirRecente(r) }, modifier = Modifier.padding(top = 10.dp)) { Text("Continuar") }
                        }
                    }
                }
            }
            if (recentes.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentes.drop(1).take(10), key = { it.uri }) { r ->
                            Column(Modifier.width(96.dp).clickable { onAbrirRecente(r) }) {
                                CapaMiniatura(ItemPasta("", r.nome, false, 0L, 0L, Uri.parse(r.uri)), Modifier.width(96.dp).height(136.dp))
                                Text(r.nome, maxLines = 2, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        item { Text("Minhas pastas", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp)) }
        if (pastas.isEmpty()) {
            item {
                Text(
                    "Escolha a pasta onde ficam seus quadrinhos, PDFs e livros. O app navega por ela, mostra as capas, lembra onde você parou e abre o próximo arquivo sozinho quando você termina um.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(pastas, key = { it.uri }) { p ->
            var menu by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth().clickable { onAbrirPasta(p) }) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico("pasta", MaterialTheme.colorScheme.primary, 32.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.nome, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text("Toque para abrir", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 18.sp) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Remover da biblioteca") }, onClick = { menu = false; onRemoverPasta(p) })
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                Button(onClick = onAdicionar) { Text("Adicionar pasta") }
                OutlinedButton(onClick = onAbrirArquivo) { Text("Abrir um arquivo") }
            }
        }
    }
}

// ====================================================== dentro de uma pasta
@Composable
fun PastaTela(
    cfg: Config, arvore: Uri, docId: String, titulo: String, refresco: Int,
    onVoltar: () -> Unit, onEntrar: (ItemPasta) -> Unit,
    onLer: (legiveis: List<ItemPasta>, indice: Int) -> Unit, onOrganizar: (ItemPasta) -> Unit,
    onLerImagens: (List<ItemPasta>) -> Unit, onCriarDeImagens: (List<ItemPasta>) -> Unit
) {
    val ctx = LocalContext.current
    var itens by remember(docId) { mutableStateOf<List<ItemPasta>?>(null) }
    var erro by remember(docId) { mutableStateOf<String?>(null) }
    LaunchedEffect(docId) {
        try {
            itens = Pastas.listar(ctx, arvore, docId)
        } catch (e: Exception) {
            erro = e.message ?: "Não consegui abrir esta pasta."
        }
    }
    val lista = itens
    val legiveis = lista?.filter { it.legivel } ?: emptyList()
    val imagens = lista?.filter { it.ehImagem } ?: emptyList()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onVoltar() }.padding(12.dp)) { Ico("voltar", MaterialTheme.colorScheme.onBackground) }
            Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        }
        when {
            erro != null -> Text(erro ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
            lista == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (imagens.isNotEmpty()) {
                    item {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${imagens.size} imagens nesta pasta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    Button(onClick = { onLerImagens(imagens) }) { Text("Ler como quadrinho") }
                                    OutlinedButton(onClick = { onCriarDeImagens(imagens) }) { Text("Criar CBZ/PDF...") }
                                }
                            }
                        }
                    }
                }
                if (lista.isEmpty()) item { Text("Pasta vazia.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                items(lista.filter { !it.ehImagem && (it.ehPasta || it.legivel) }, key = { it.docId }) { item ->
                    LinhaItem(cfg, item, refresco, onClick = {
                        if (item.ehPasta) onEntrar(item) else onLer(legiveis, legiveis.indexOf(item).coerceAtLeast(0))
                    }, onOrganizar = { onOrganizar(item) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LinhaItem(cfg: Config, item: ItemPasta, refresco: Int, onClick: () -> Unit, onOrganizar: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var versao by remember { mutableStateOf(0) }
    val prog = remember(refresco, versao, item.uri) { if (item.ehPasta) null else cfg.progresso(item.uri.toString()) }
    val lido = prog != null && prog.first >= prog.second - 1
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.ehPasta) {
                Box(Modifier.width(48.dp).height(48.dp), contentAlignment = Alignment.Center) { Ico("pasta", MaterialTheme.colorScheme.primary, 32.dp) }
            } else {
                CapaMiniatura(item, Modifier.width(48.dp).height(68.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.nome, fontSize = 15.sp, maxLines = 2, color = if (lido) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (!item.ehPasta) {
                    val extra = when {
                        lido -> "  •  Lido ✓"
                        prog != null -> "  •  pág ${prog.first + 1}/${prog.second}"
                        else -> ""
                    }
                    Text("${etiqueta(item.tipo)}  •  ${Pastas.formataTamanho(item.tamanho)}$extra", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (prog != null && !lido) {
                        LinearProgressIndicator(progress = { ((prog.first + 1f) / prog.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 12.dp))
                    }
                }
            }
            if (!item.ehPasta) {
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 18.sp) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ler") }, onClick = { menu = false; onClick() })
                        DropdownMenuItem(text = { Text("Organizar páginas (editar a ordem)") }, onClick = { menu = false; onOrganizar() })
                        DropdownMenuItem(text = { Text(if (lido) "Marcar como não lido" else "Marcar como lido") },
                            onClick = { menu = false; cfg.marcaLido(item.uri.toString(), !lido); versao++ })
                    }
                }
            }
        }
    }
}
