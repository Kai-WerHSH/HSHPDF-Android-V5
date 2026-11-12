// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException as CancelamentoKotlin

private fun LazyGridState.itemEm(p: Offset): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x < it.offset.x + it.size.width && p.y >= it.offset.y && p.y < it.offset.y + it.size.height
    }?.index

@Composable
private fun Miniatura(p: Pagina, numero: Int, marcada: Boolean, arrastada: Boolean, onClick: () -> Unit) {
    var bmp by remember(p.id, p.rot) { mutableStateOf<Bitmap?>(null) }
    @Suppress("UNUSED_VARIABLE")
    val v = p.fonte.versao
    LaunchedEffect(p.id, p.rot) {
        try {
            bmp = p.bitmap(240)
        } catch (e: CancelamentoKotlin) {
            throw e
        } catch (e: Exception) { }
    }
    val borda = if (marcada) MaterialTheme.colorScheme.primary else if (arrastada) Color(0xFFFFC107) else MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.graphicsLayer { if (arrastada) { scaleX = 1.07f; scaleY = 1.07f } }.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f / p.razao().coerceIn(0.4f, 2.5f)).background(Color(0xFF16161A))
                .border(if (marcada || arrastada) 4.dp else 1.dp, borda)
        ) {
            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Text("$numero", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun tamanhoDe(ctx: Context, uri: Uri): Long {
    var t = 0L
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) t = it.getLong(0) }
    } catch (e: Exception) { }
    return t
}

class Criado(val uri: Uri, val nome: String, val tamanho: Long)

/**
 * Organizar: mudar a ordem arrastando, excluir e girar páginas, e criar PDF/CBZ/ZIP/EPUB.
 * Serve tanto para "Criar" (a partir de imagens) quanto para "Organizar páginas" de um arquivo existente.
 */
@Composable
fun OrganizarTela(
    proj: Projeto, cfg: Config, refSaida: Int, onFechar: () -> Unit,
    onAdicionarImagens: () -> Unit, onAdicionarPasta: () -> Unit, onAdicionarArquivos: () -> Unit,
    onEscolherSaida: () -> Unit, onAbrirCriado: (Uri) -> Unit
) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val paginas = proj.estado.paginas
    val grade = rememberLazyGridState()
    var selecionando by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var arrastando by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var dMover by remember { mutableStateOf(false) }
    var dExcluir by remember { mutableStateOf(false) }
    var dCriar by remember { mutableStateOf(false) }
    var dSair by remember { mutableStateOf(false) }

    LaunchedEffect(proj) { for (f in proj.fontes) f.medirTudo(0) }

    fun sair() {
        if (proj.sujo && paginas.isNotEmpty() && proj.chave == null) dSair = true else onFechar()
    }
    BackHandler(enabled = true) { sair() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable { sair() }.padding(12.dp)) { Ico("voltar", MaterialTheme.colorScheme.onBackground) }
            Column(Modifier.weight(1f)) {
                Text(proj.nome, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.onBackground)
                Text("${paginas.size} páginas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { selecionando = !selecionando; sel = emptySet() }) {
                Text(if (selecionando) "Pronto" else "Selecionar")
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Desfazer") }, enabled = proj.podeDesfazer, onClick = { menu = false; proj.desfazer(); sel = emptySet() })
                    DropdownMenuItem(text = { Text("Refazer") }, enabled = proj.podeRefazer, onClick = { menu = false; proj.refazer(); sel = emptySet() })
                    DropdownMenuItem(text = { Text("Ordenar por nome") }, onClick = { menu = false; proj.ordenarPorNome(); sel = emptySet() })
                    DropdownMenuItem(text = { Text("Inverter a ordem") }, onClick = { menu = false; proj.inverter(); sel = emptySet() })
                    DropdownMenuItem(text = { Text("Selecionar todas") }, onClick = { menu = false; selecionando = true; sel = paginas.indices.toSet() })
                    DropdownMenuItem(text = { Text("Adicionar imagens...") }, onClick = { menu = false; onAdicionarImagens() })
                    DropdownMenuItem(text = { Text("Adicionar pasta de imagens...") }, onClick = { menu = false; onAdicionarPasta() })
                    DropdownMenuItem(text = { Text("Adicionar páginas de outro arquivo...") }, onClick = { menu = false; onAdicionarArquivos() })
                }
            }
        }
        Text(
            if (selecionando) "Toque nas páginas para selecionar e use os botões de baixo." else "Segure uma página e arraste para mudar a ordem.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (paginas.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Nenhuma página ainda.", color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                    Button(onClick = onAdicionarImagens, modifier = Modifier.padding(top = 12.dp)) { Text("Adicionar imagens") }
                }
            }
            LazyVerticalGrid(
                state = grade, columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().pointerInput(selecionando) {
                    if (!selecionando) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                val i = grade.itemEm(off)
                                if (i != null) {
                                    arrastando = i
                                    proj.comecarGesto()
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val pos = change.position
                                val alvo = grade.itemEm(pos)
                                val atual = arrastando
                                if (atual != null && alvo != null && alvo != atual) {
                                    val l = proj.estado.paginas.toMutableList()
                                    val x = l.removeAt(atual)
                                    l.add(alvo, x)
                                    proj.aplicarGesto(Estado(l))
                                    arrastando = alvo
                                }
                                if (pos.y < 100f) escopo.launch { grade.scrollBy(-30f) }
                                else if (pos.y > size.height - 100f) escopo.launch { grade.scrollBy(30f) }
                            },
                            onDragEnd = { arrastando = null; proj.terminarGesto() },
                            onDragCancel = { arrastando = null; proj.terminarGesto() }
                        )
                    }
                }
            ) {
                items(count = paginas.size, key = { paginas[it].id }) { i ->
                    Miniatura(paginas[i], i + 1, marcada = i in sel, arrastada = i == arrastando) {
                        if (selecionando) sel = if (i in sel) sel - i else sel + i
                    }
                }
            }
        }
        if (selecionando && sel.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for ((rot, tipo, acao) in listOf<Triple<String, String, () -> Unit>>(
                        Triple("Girar", "girarE") { proj.girar(sel, -90) },
                        Triple("Girar", "girar") { proj.girar(sel, 90) },
                        Triple("Duplicar", "duplicar") { proj.duplicar(sel); sel = emptySet() },
                        Triple("Mover", "ini") { dMover = true },
                        Triple("Excluir", "lixo") { dExcluir = true }
                    )) {
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { acao() }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Ico(tipo, MaterialTheme.colorScheme.onSurface)
                            Text(rot, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Button(onClick = { dCriar = true }, enabled = paginas.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text("Criar arquivo (PDF, CBZ, ZIP, EPUB)", modifier = Modifier.padding(6.dp))
            }
        }
    }

    if (dExcluir) {
        AlertDialog(
            onDismissRequest = { dExcluir = false }, title = { Text("Excluir páginas") },
            text = { Text("Excluir ${sel.size} página(s) desta lista? (O arquivo original não é alterado.)") },
            confirmButton = { TextButton(onClick = { proj.excluir(sel); sel = emptySet(); dExcluir = false }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { dExcluir = false }) { Text("Cancelar") } }
        )
    }
    if (dMover) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dMover = false }, title = { Text("Mover páginas") },
            text = {
                Column {
                    Text("Mover as selecionadas para ANTES da página nº (1 a ${paginas.size + 1}; ${paginas.size + 1} = fim):", fontSize = 13.sp)
                    OutlinedTextField(value = txt, onValueChange = { txt = it.filter { c -> c.isDigit() } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = txt.toIntOrNull()
                    if (n != null) { proj.mover(sel, (n - 1).coerceIn(0, paginas.size)); sel = emptySet() }
                    dMover = false
                }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { dMover = false }) { Text("Cancelar") } }
        )
    }
    if (dSair) {
        AlertDialog(
            onDismissRequest = { dSair = false }, title = { Text("Descartar?") },
            text = { Text("Você montou uma lista de páginas e não criou o arquivo. Sair mesmo assim?") },
            confirmButton = { TextButton(onClick = { dSair = false; onFechar() }) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { dSair = false }) { Text("Continuar editando") } }
        )
    }
    if (dCriar) PainelCriar(proj, cfg, refSaida, onFechar = { dCriar = false }, onEscolherSaida = onEscolherSaida, onAbrirCriado = onAbrirCriado)
}

// ====================================================== painel único para criar os arquivos
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainelCriar(proj: Projeto, cfg: Config, refSaida: Int, onFechar: () -> Unit, onEscolherSaida: () -> Unit, onAbrirCriado: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var nome by remember { mutableStateOf(proj.nome.substringBeforeLast('.').ifBlank { "Novo" }) }
    var formatos by remember { mutableStateOf(cfg.formatos.ifEmpty { setOf("cbz") }) }
    var preset by remember { mutableStateOf(cfg.preset) }
    var andamento by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var criados by remember { mutableStateOf<List<Criado>?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    val saida = cfg.pastaSaida
    val nomeSaida = remember(refSaida, saida) { cfg.nomePastaSaida }

    if (andamento != null) {
        val (fmt, a, b) = andamento!!
        AlertDialog(
            onDismissRequest = { }, title = { Text("Criando ${fmt.uppercase()}...") },
            text = {
                Column {
                    Text("Página $a de $b")
                    LinearProgressIndicator(progress = { if (b > 0) a / b.toFloat() else 0f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            },
            confirmButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancelar") } }
        )
        return
    }
    val prontos = criados
    if (prontos != null) {
        AlertDialog(
            onDismissRequest = { onFechar() }, title = { Text("Pronto!") },
            text = {
                Column {
                    for (c in prontos) Text("✓ ${c.nome}  (${Pastas.formataTamanho(c.tamanho)})", modifier = Modifier.padding(vertical = 2.dp))
                    Text("Salvo em: $nomeSaida", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { onFechar(); prontos.firstOrNull()?.let { onAbrirCriado(it.uri) } }) { Text("Abrir para ler") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_STREAM, prontos.first().uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(Intent.createChooser(i, "Compartilhar"))
                    }) { Text("Compartilhar") }
                    TextButton(onClick = { onFechar() }) { Text("Fechar") }
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onFechar, title = { Text("Criar arquivo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, singleLine = true, label = { Text("Nome do arquivo") }, modifier = Modifier.fillMaxWidth())
                Text("Formatos (pode marcar mais de um)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((f, rot) in Formatos.todos) {
                        val marcado = f in formatos
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = if (marcado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { formatos = if (marcado) formatos - f else formatos + f }
                        ) {
                            Text(rot, color = if (marcado) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                        }
                    }
                }
                Text("Qualidade e tamanho", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, rot) in Presets.nomes) {
                        val marcado = preset == k
                        Surface(
                            shape = RoundedCornerShape(12.dp), color = if (marcado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { preset = k }
                        ) {
                            Text(rot, color = if (marcado) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
                Text(
                    "Original: copia as imagens sem recompressar (mais rápido e sem perda) quando o formato permite; HEIC e outros são convertidos para JPEG.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                )
                Text("Salvar em", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (saida == null) "Nenhuma pasta escolhida" else nomeSaida, modifier = Modifier.weight(1f), maxLines = 1)
                    OutlinedButton(onClick = onEscolherSaida) { Text(if (saida == null) "Escolher" else "Trocar") }
                }
                if (erro != null) Text(erro ?: "", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = saida != null && formatos.isNotEmpty() && nome.isNotBlank(),
                onClick = {
                    cfg.formatos = formatos
                    cfg.preset = preset
                    erro = null
                    val arvore = Uri.parse(saida)
                    val paginas = proj.estado.paginas
                    val op = Presets.de(preset)
                    val limpo = nome.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    job = escopo.launch {
                        val feitos = ArrayList<Criado>()
                        try {
                            for (f in Formatos.todos.map { it.first }.filter { it in formatos }) {
                                andamento = Triple(f, 0, paginas.size)
                                val uri = Exportador.gerar(ctx, paginas, f, limpo, arvore, op) { a, b -> andamento = Triple(f, a, b) }
                                feitos.add(Criado(uri, Pastas.nomeDoUri(ctx, uri, "$limpo.$f"), tamanhoDe(ctx, uri)))
                            }
                            andamento = null
                            Som.concluir(ctx)
                            criados = feitos
                        } catch (e: CancellationException) {
                            andamento = null
                            Toast.makeText(ctx, "Cancelado.", Toast.LENGTH_SHORT).show()
                        } catch (e: Throwable) {
                            andamento = null
                            erro = e.message ?: e.toString()
                        }
                    }
                }
            ) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}
