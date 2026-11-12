// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val uriExterna = mutableStateOf<Uri?>(null)
    private val imagensExternas = mutableStateOf<List<Uri>>(emptyList())

    private fun lerIntent(i: Intent?) {
        if (i == null) return
        when (i.action) {
            Intent.ACTION_VIEW -> uriExterna.value = i.data
            Intent.ACTION_SEND -> {
                val u = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (u != null) {
                    if (i.type?.startsWith("image/") == true) imagensExternas.value = listOf(u) else uriExterna.value = u
                }
            }
            Intent.ACTION_SEND_MULTIPLE ->
                imagensExternas.value = IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        }
    }

    /** Esconde (ou mostra) hora, bateria e botões do sistema. Deslizar na borda mostra por um instante. */
    fun definirTelaCheia(ligada: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (ligada) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { Abertura.limparTudo(applicationContext) } catch (e: Exception) { }  // apaga restos temporários e cópias antigas
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val a = window.attributes
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = a
        }
        lerIntent(intent)
        val cfg = Config(this)
        setContent { App(cfg, uriExterna, imagensExternas) { definirTelaCheia(it) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lerIntent(intent)
    }
}

class SessaoLeitor(val proj: Projeto, val contexto: ContextoLeitura?)
data class PastaRef(val arvore: Uri, val docId: String, val titulo: String)

@Composable
fun App(cfg: Config, uriExterna: MutableState<Uri?>, imagensExternas: MutableState<List<Uri>>, telaCheia: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val escopo = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }  // uma falha em segundo plano não derruba o resto
    DisposableEffect(Unit) { onDispose { escopo.cancel() } }
    var tema by remember { mutableStateOf(cfg.tema) }
    var acento by remember { mutableStateOf(cfg.acento) }
    var aba by remember { mutableIntStateOf(0) }
    var pilha by remember { mutableStateOf(listOf<PastaRef>()) }
    var leitor by remember { mutableStateOf<SessaoLeitor?>(null) }
    var organizar by remember { mutableStateOf<Projeto?>(null) }
    var carregando by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var refresco by remember { mutableIntStateOf(0) }
    var refSaida by remember { mutableIntStateOf(0) }
    var recentes by remember { mutableStateOf(cfg.recentes()) }
    var pastasBib by remember { mutableStateOf(cfg.pastas()) }
    var prefetch by remember { mutableStateOf<Pair<String, Deferred<Projeto?>>?>(null) }
    var escolhaArquivo by remember { mutableStateOf<Uri?>(null) }

    fun persiste(uri: Uri, escrita: Boolean) {
        try {
            val f = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (escrita) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            ctx.contentResolver.takePersistableUriPermission(uri, f)
        } catch (e: Exception) { }
    }

    fun descartaPrefetch() {
        val p = prefetch ?: return
        prefetch = null
        escopo.launch { try { p.second.await()?.fechar() } catch (e: Exception) { } }  // fecha o que foi pré-carregado e não usado
    }

    fun fecharLeitor() {
        leitor?.proj?.fechar()
        leitor = null
        descartaPrefetch()
        refresco++
        recentes = cfg.recentes()
    }

    fun abrirParaLer(item: ItemPasta, cl: ContextoLeitura?) {
        escopo.launch {
            carregando = "Abrindo ${item.nome}..."
            try {
                val chave = item.uri.toString()
                val pre = prefetch?.takeIf { it.first == chave }
                val proj = (if (pre != null) { prefetch = null; pre.second.await() } else null) ?: Abertura.abrir(ctx, item.uri)
                val antigo = leitor?.proj
                leitor = SessaoLeitor(proj, cl)
                antigo?.fechar()
                if (pre == null) descartaPrefetch()
                cfg.adicionarRecente(item.uri, item.nome, cl?.arvore, cl?.docIdPai)
                recentes = cfg.recentes()
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    fun trocarArquivo(delta: Int) {
        val s = leitor ?: return
        val c = s.contexto ?: return
        val novo = c.indice + delta
        val item = c.arquivos.getOrNull(novo) ?: return
        abrirParaLer(item, ContextoLeitura(c.arquivos, novo, c.arvore, c.docIdPai))
    }

    fun preparaProximo() {
        val prox = leitor?.contexto?.proximo ?: return
        val chave = prox.uri.toString()
        if (prefetch?.first == chave) return
        descartaPrefetch()
        prefetch = Pair(chave, escopo.async { try { Abertura.abrir(ctx, prox.uri) } catch (e: Exception) { null } })
    }

    fun abrirParaOrganizar(item: ItemPasta) {
        escopo.launch {
            carregando = "Abrindo ${item.nome}..."
            try {
                organizar = Abertura.abrir(ctx, item.uri)
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    fun itemDeUri(uri: Uri): ItemPasta = ItemPasta("", Abertura.nomeDe(ctx, uri), false, 0L, 0L, uri)

    fun novoComImagens(itens: List<Pair<Uri, String>>, titulo: String) {
        escopo.launch {
            carregando = "Preparando ${itens.size} imagens..."
            try {
                val f = Abertura.abrirImagens(ctx, itens)
                val org = organizar
                if (org == null) organizar = Projeto.deFonte(titulo, null, f) else org.adicionarFonte(f)
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    fun comUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        escopo.launch {
            carregando = "Preparando ${uris.size} imagens..."
            try {
                val f = Abertura.abrirImagensUris(ctx, uris)
                val org = organizar
                if (org == null) organizar = Projeto.deFonte("Novo", null, f) else org.adicionarFonte(f)
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    fun comPastaImagens(arvore: Uri) {
        escopo.launch {
            carregando = "Lendo a pasta..."
            try {
                val raiz = Pastas.raiz(arvore)
                val imgs = Pastas.listar(ctx, arvore, raiz).filter { it.ehImagem }
                if (imgs.isEmpty()) {
                    erro = "Não achei imagens nesta pasta."
                } else {
                    val f = Abertura.abrirImagens(ctx, imgs.map { Pair(it.uri, it.nome) })
                    val org = organizar
                    val titulo = Saida.nomeDaPasta(ctx, arvore)
                    if (org == null) organizar = Projeto.deFonte(titulo, null, f) else org.adicionarFonte(f)
                }
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    fun comArquivos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        escopo.launch {
            carregando = "Abrindo arquivos..."
            try {
                val proj = organizar ?: Projeto.vazio("Juntar")
                for (u in uris) proj.adicionarFonte(Abertura.abrirFonte(ctx, u))
                if (organizar == null) organizar = proj
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = null
            }
        }
    }

    // ---------------- seletores de arquivos e pastas ----------------
    val lAdicionarPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persiste(uri, true)
            cfg.adicionarPasta(PastaBiblioteca(uri.toString(), Saida.nomeDaPasta(ctx, uri)))
            pastasBib = cfg.pastas()
        }
    }
    val lSaida = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persiste(uri, true)
            cfg.pastaSaida = uri.toString()
            cfg.nomePastaSaida = Saida.nomeDaPasta(ctx, uri)
            refSaida++
        }
    }
    val lArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persiste(uri, false)
            escolhaArquivo = uri
        }
    }
    val lImagens = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> comUris(uris) }
    val lPastaImagens = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) comPastaImagens(uri) }
    val lArquivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> comArquivos(uris) }

    LaunchedEffect(uriExterna.value) {
        uriExterna.value?.let {
            abrirParaLer(itemDeUri(it), null)
            uriExterna.value = null
        }
    }
    LaunchedEffect(imagensExternas.value) {
        if (imagensExternas.value.isNotEmpty()) {
            comUris(imagensExternas.value)
            imagensExternas.value = emptyList()
        }
    }
    BackHandler(enabled = leitor == null && organizar == null && aba == 0 && pilha.isNotEmpty()) { pilha = pilha.dropLast(1) }
    BackHandler(enabled = leitor == null && organizar == null && aba != 0) { aba = 0 }

    HshTema(tema, acento) {
        Surface(
            Modifier.fillMaxSize().then(
                if (leitor != null) Modifier else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            ),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                // ---- biblioteca / criar / ajustes (continua montada por baixo do leitor, para voltar no mesmo ponto da lista) ----
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (aba) {
                            0 -> {
                                val atual = pilha.lastOrNull()
                                if (atual == null) {
                                    BibliotecaTela(
                                        cfg = cfg, pastas = pastasBib, recentes = recentes, refresco = refresco,
                                        onAdicionar = { lAdicionarPasta.launch(null) },
                                        onAbrirPasta = { p -> val u = Uri.parse(p.uri); pilha = listOf(PastaRef(u, Pastas.raiz(u), p.nome)) },
                                        onRemoverPasta = { p -> cfg.removerPasta(p); pastasBib = cfg.pastas() },
                                        onAbrirRecente = { r ->
                                            escopo.launch {
                                                var contexto: ContextoLeitura? = null
                                                try {
                                                    if (r.arvore != null && r.docIdPai != null) {
                                                        val itens = Pastas.listar(ctx, Uri.parse(r.arvore), r.docIdPai).filter { it.legivel }
                                                        val idx = itens.indexOfFirst { it.uri.toString() == r.uri }
                                                        if (idx >= 0) contexto = ContextoLeitura(itens, idx, Uri.parse(r.arvore), r.docIdPai)
                                                    }
                                                } catch (e: Exception) { }
                                                abrirParaLer(ItemPasta("", r.nome, false, 0L, 0L, Uri.parse(r.uri)), contexto)
                                            }
                                        },
                                        onAbrirArquivo = { lArquivo.launch(arrayOf("*/*")) }
                                    )
                                } else {
                                    key(atual.docId) {
                                        PastaTela(
                                            cfg = cfg, arvore = atual.arvore, docId = atual.docId, titulo = atual.titulo, refresco = refresco,
                                            onVoltar = { pilha = pilha.dropLast(1) },
                                            onEntrar = { item -> pilha = pilha + PastaRef(atual.arvore, item.docId, item.nome) },
                                            onLer = { legiveis, indice ->
                                                abrirParaLer(legiveis[indice], ContextoLeitura(legiveis, indice, atual.arvore, atual.docId))
                                            },
                                            onOrganizar = { item -> abrirParaOrganizar(item) },
                                            onLerImagens = { imgs ->
                                                escopo.launch {
                                                    carregando = "Preparando as imagens..."
                                                    try {
                                                        val f = Abertura.abrirImagens(ctx, imgs.map { Pair(it.uri, it.nome) })
                                                        val proj = Projeto.deFonte(atual.titulo, null, f)
                                                        leitor?.proj?.fechar()
                                                        leitor = SessaoLeitor(proj, null)
                                                    } catch (e: Throwable) {
                                                        erro = e.message ?: e.toString()
                                                    } finally {
                                                        carregando = null
                                                    }
                                                }
                                            },
                                            onCriarDeImagens = { imgs -> novoComImagens(imgs.map { Pair(it.uri, it.nome) }, atual.titulo) }
                                        )
                                    }
                                }
                            }
                            1 -> CriarTela(
                                cfg = cfg, refSaida = refSaida,
                                onImagens = { lImagens.launch(arrayOf("image/*")) },
                                onPasta = { lPastaImagens.launch(null) },
                                onArquivos = { lArquivos.launch(arrayOf("*/*")) },
                                onEscolherSaida = { lSaida.launch(null) }
                            )
                            else -> AjustesTela(
                                cfg = cfg, tema = tema, refSaida = refSaida, podeLimpar = leitor == null,
                                onTema = { tema = it; cfg.tema = it }, onAcento = { acento = it; cfg.acento = it },
                                onEscolherSaida = { lSaida.launch(null) }
                            )
                        }
                    }
                    NavigationBar {
                        NavigationBarItem(selected = aba == 0, onClick = { aba = 0 }, icon = { Ico("pasta", MaterialTheme.colorScheme.onSurface) }, label = { Text("Biblioteca") })
                        NavigationBarItem(selected = aba == 1, onClick = { aba = 1 }, icon = { Ico("plus", MaterialTheme.colorScheme.onSurface) }, label = { Text("Criar") })
                        NavigationBarItem(selected = aba == 2, onClick = { aba = 2 }, icon = { Ico("gear", MaterialTheme.colorScheme.onSurface) }, label = { Text("Ajustes") })
                    }
                }

                // ---- organizar páginas / criar arquivos (por cima) ----
                organizar?.let { org ->
                    key(org) {
                        OrganizarTela(
                            proj = org, cfg = cfg, refSaida = refSaida,
                            onFechar = { org.fechar(); organizar = null },
                            onAdicionarImagens = { lImagens.launch(arrayOf("image/*")) },
                            onAdicionarPasta = { lPastaImagens.launch(null) },
                            onAdicionarArquivos = { lArquivos.launch(arrayOf("*/*")) },
                            onEscolherSaida = { lSaida.launch(null) },
                            onAbrirCriado = { uri -> org.fechar(); organizar = null; abrirParaLer(itemDeUri(uri), null) }
                        )
                    }
                }

                // ---- leitor (por cima de tudo) ----
                leitor?.let { sessao ->
                    key(sessao.proj) {
                        Leitor(
                            proj = sessao.proj, cfg = cfg, contexto = sessao.contexto,
                            onSair = { fecharLeitor() },
                            onProximo = { trocarArquivo(1) }, onAnterior = { trocarArquivo(-1) },
                            onPrefetch = { preparaProximo() }, onTelaCheia = telaCheia
                        )
                    }
                }

                if (carregando != null) {
                    Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable { }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(carregando ?: "", color = Color.White, modifier = Modifier.padding(top = 14.dp))
                        }
                    }
                }
            }
        }
        if (erro != null) {
            AlertDialog(onDismissRequest = { erro = null }, title = { Text("Ops") }, text = { Text(erro ?: "") }, confirmButton = { TextButton(onClick = { erro = null }) { Text("OK") } })
        }
        escolhaArquivo?.let { uri ->
            AlertDialog(
                onDismissRequest = { escolhaArquivo = null }, title = { Text("O que fazer com este arquivo?") },
                text = { Text(Abertura.nomeDe(ctx, uri)) },
                confirmButton = { TextButton(onClick = { escolhaArquivo = null; abrirParaLer(itemDeUri(uri), null) }) { Text("Ler") } },
                dismissButton = {
                    TextButton(onClick = { escolhaArquivo = null; abrirParaOrganizar(itemDeUri(uri)) }) { Text("Organizar páginas") }
                }
            )
        }
    }
}
