// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/** Opções do modo leitura (guardadas no aparelho). Cada mudança já é salva. */
class OpcoesLeitura(private val cfg: Config) {
    var modo by mutableStateOf(cfg.modoLeitura)
    var orientacao by mutableStateOf(cfg.orientacao)
    var fundo by mutableStateOf(cfg.fundoLeitura)
    var zonas by mutableStateOf(cfg.zonasToque)
    var toqueDuplo by mutableStateOf(cfg.toqueDuploZoom)
    var numero by mutableStateOf(cfg.mostrarNumero)
    var recorte by mutableStateOf(cfg.mostrarNoRecorte)
    var manterTela by mutableStateOf(cfg.manterTela)
    var animar by mutableStateOf(cfg.animarPaginas)
    var cinza by mutableStateOf(cfg.cinza)
    var invertido by mutableStateOf(cfg.invertido)
    var margem by mutableIntStateOf(cfg.margemLateral)
    var brilho by mutableIntStateOf(cfg.brilho)
    var continuar by mutableStateOf(cfg.continuarAuto)

    fun salva() {
        cfg.modoLeitura = modo; cfg.orientacao = orientacao; cfg.fundoLeitura = fundo
        cfg.zonasToque = zonas; cfg.toqueDuploZoom = toqueDuplo; cfg.mostrarNumero = numero
        cfg.mostrarNoRecorte = recorte; cfg.manterTela = manterTela; cfg.animarPaginas = animar
        cfg.cinza = cinza; cfg.invertido = invertido; cfg.margemLateral = margem; cfg.brilho = brilho; cfg.continuarAuto = continuar
    }
}

/** "Padrão": quadrinhos (imagens) em tira sem espaços; documentos com um respiro entre as páginas. */
fun modoEfetivo(op: OpcoesLeitura, ehHq: Boolean): String =
    if (op.modo != "padrao") op.modo else if (ehHq) "tira" else "lacunas"

fun espacoDoModo(modo: String): Dp = if (modo == "tira") 0.dp else 8.dp

fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun orientacaoAndroid(modo: String): Int = when (modo) {
    "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    "retrato" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    "paisagem" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

/** Filtro de cor aplicado às páginas: nível de cinza e/ou invertido. */
fun filtroDeCor(op: OpcoesLeitura): ColorFilter? {
    val g = 0.213f
    val h = 0.715f
    val b = 0.072f
    val m = when {
        op.cinza && op.invertido -> floatArrayOf(
            -g, -h, -b, 0f, 255f, -g, -h, -b, 0f, 255f, -g, -h, -b, 0f, 255f, 0f, 0f, 0f, 1f, 0f
        )
        op.cinza -> floatArrayOf(g, h, b, 0f, 0f, g, h, b, 0f, 0f, g, h, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        op.invertido -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f
        )
        else -> return null
    }
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

/** Cor do fundo atrás das páginas: preto, cinza, branco ou "Auto" (cor das bordas da página atual). */
@Composable
fun corDoFundo(op: OpcoesLeitura, proj: Projeto, indice: Int): Color {
    var auto by remember { mutableStateOf(Color.Black) }
    if (op.fundo == "auto") {
        val pg = proj.estado.paginas.getOrNull(indice)
        LaunchedEffect(pg?.id, indice) {
            val f = pg?.fonte
            auto = if (pg == null) Color.Black else if (f == null) Color.White else try {
                Color(f.corBorda(pg.indice))
            } catch (e: Exception) {
                Color.Black
            }
        }
    }
    return when (op.fundo) {
        "cinza" -> Color(0xFF3A3A3A)
        "branco" -> Color.White
        "auto" -> auto
        else -> Color.Black
    }
}

/** De onde o arquivo veio: a lista de arquivos da pasta (para continuar no próximo ao terminar). */
class ContextoLeitura(val arquivos: List<ItemPasta>, val indice: Int, val arvore: Uri?, val docIdPai: String?) {
    val proximo: ItemPasta? get() = arquivos.getOrNull(indice + 1)
    val anterior: ItemPasta? get() = arquivos.getOrNull(indice - 1)
}

@Composable
private fun BotaoIco(tipo: String, rotulo: String? = null, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Ico(tipo)
        if (rotulo != null) Text(rotulo, color = Color(0xFFD0D0D6), fontSize = 10.sp)
    }
}

/** Uma página no leitor: ocupa `larguraPx` na tela, mas é desenhada em `larguraRender` (mais nítida quando há zoom). */
@Composable
fun PaginaLeitura(pagina: Pagina, larguraPx: Int, larguraRender: Int, espaco: Dp, filtro: ColorFilter?) {
    val dens = LocalDensity.current
    @Suppress("UNUSED_VARIABLE")
    val v = pagina.fonte.versao  // recompõe quando o tamanho real da página é descoberto
    val altura = (larguraPx * pagina.razao()).toInt()
    var bmp by remember(pagina.id, pagina.rot) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pagina.id, pagina.rot, larguraRender) {
        delay(30)
        try {
            bmp = pagina.bitmap(larguraRender)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { }
    }
    Column {
        Box(Modifier.width(with(dens) { larguraPx.toDp() }).height(with(dens) { altura.toDp() }).background(Color(0xFF16161A))) {
            bmp?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = filtro, contentScale = ContentScale.FillBounds)
            }
        }
        if (espaco > 0.dp) Spacer(Modifier.height(espaco))
    }
}

@Composable
fun TransicaoFim(proximo: String?, contagem: Int, onProximo: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Fim", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (proximo != null) {
            Text("Próximo", color = Color(0xFF9A9AA2), fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            Text(proximo, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 3)
            Button(onClick = onProximo, modifier = Modifier.padding(top = 18.dp)) { Text(if (contagem > 0) "Ler agora ($contagem)" else "Ler próximo") }
            if (contagem > 0) Text("Role para trás para cancelar", color = Color(0xFF9A9AA2), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * Leitor (igual ao Mihon): toque no CENTRO mostra/esconde as barras; nas LATERAIS vira a página; toque duplo e pinça dão zoom
 * em qualquer ponto da tela (e dá para arrastar); Voltar sai. Ao terminar, abre o próximo arquivo da pasta.
 */
@Composable
fun Leitor(
    proj: Projeto, cfg: Config, contexto: ContextoLeitura?,
    onSair: () -> Unit, onProximo: () -> Unit, onAnterior: () -> Unit, onPrefetch: () -> Unit, onTelaCheia: (Boolean) -> Unit
) {
    val paginas = proj.estado.paginas
    val total = paginas.size
    val ehHq = paginas.firstOrNull()?.fonte is FonteImagens
    val escopo = rememberCoroutineScope()
    val dens = LocalDensity.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val op = remember(proj) { OpcoesLeitura(cfg) }
    val modo = modoEfetivo(op, ehHq)
    val paginado = modo == "esq_dir" || modo == "dir_esq" || modo == "vertical"
    val rtl = modo == "dir_esq"
    val espaco = espacoDoModo(modo)
    val filtro = filtroDeCor(op)
    val temProximo = contexto?.proximo != null
    var zoom by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var zoomRender by remember { mutableFloatStateOf(1f) }
    var menu by remember { mutableStateOf(false) }
    var folha by remember { mutableStateOf(false) }
    var maisAberto by remember { mutableStateOf(false) }
    var modoAberto by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var dica by remember { mutableStateOf(!cfg.dicaLeitura) }
    var toques by remember { mutableIntStateOf(0) }
    var jobToque by remember { mutableStateOf<Job?>(null) }
    var contagem by remember { mutableIntStateOf(0) }
    var rolou by remember { mutableStateOf(false) }
    val inicio = remember(proj) { cfg.paginaSalva(proj.chave).coerceIn(0, (total - 1).coerceAtLeast(0)) }
    val lista = rememberLazyListState(initialFirstVisibleItemIndex = inicio)
    val pager = rememberPagerState(initialPage = inicio, pageCount = { total + (if (temProximo) 1 else 0) })
    val indiceAtual = (if (paginado) pager.currentPage else lista.firstVisibleItemIndex).coerceIn(0, (total - 1).coerceAtLeast(0))
    val noFim by remember(paginado, total) {
        derivedStateOf { if (paginado) pager.currentPage >= total else lista.layoutInfo.visibleItemsInfo.lastOrNull()?.key == "fim" }
    }
    val corFundo = corDoFundo(op, proj, indiceAtual)
    val perto = total > 0 && indiceAtual >= (total * 0.6f).toInt()

    // ---- janela: tela cheia, orientação, tela ligada ----
    LaunchedEffect(Unit) { onTelaCheia(true) }
    DisposableEffect(Unit) { onDispose { onTelaCheia(false) } }
    LaunchedEffect(op.orientacao) { ctx.activity()?.requestedOrientation = orientacaoAndroid(op.orientacao) }
    DisposableEffect(Unit) { onDispose { ctx.activity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    DisposableEffect(op.manterTela) {
        view.keepScreenOn = op.manterTela
        onDispose { view.keepScreenOn = false }
    }
    // ---- medir as páginas em segundo plano, começando perto de onde você está ----
    LaunchedEffect(proj) { for (f in proj.fontes) f.medirTudo(inicio) }
    LaunchedEffect(paginado) {
        if (paginado) pager.scrollToPage(lista.firstVisibleItemIndex.coerceIn(0, (total - 1).coerceAtLeast(0)))
        else lista.scrollToItem(pager.currentPage.coerceIn(0, (total - 1).coerceAtLeast(0)))
    }
    LaunchedEffect(pager.currentPage) { zoom = 1f; tx = 0f; ty = 0f }
    LaunchedEffect(zoom) {
        delay(220)
        zoomRender = if (zoom < 1.25f) 1f else if (zoom < 2f) 2f else 3f
    }
    LaunchedEffect(indiceAtual) { cfg.salvaProgresso(proj.chave, indiceAtual, total) }
    LaunchedEffect(perto) { if (perto && temProximo) onPrefetch() }
    LaunchedEffect(aviso) {
        if (aviso != null) {
            delay(1700)
            aviso = null
        }
    }
    LaunchedEffect(lista.isScrollInProgress, pager.isScrollInProgress) { if (lista.isScrollInProgress || pager.isScrollInProgress) rolou = true }
    // ---- ao chegar ao fim: contagem e abre o próximo arquivo da pasta ----
    LaunchedEffect(noFim, rolou, op.continuar, temProximo) {
        if (noFim && rolou && op.continuar && temProximo) {
            for (s in 3 downTo 1) {
                contagem = s
                delay(1000)
            }
            contagem = 0
            onProximo()
        } else {
            contagem = 0
        }
    }
    BackHandler(enabled = true) { if (folha) folha = false else onSair() }

    fun irPara(i: Int) {
        val d = i.coerceIn(0, (total - 1).coerceAtLeast(0))
        escopo.launch { if (paginado) pager.scrollToPage(d) else lista.scrollToItem(d) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(corFundo)) {
        val vw = constraints.maxWidth.toFloat()
        val vh = constraints.maxHeight.toFloat()
        val larguraBase = (vw * (1f - 2f * op.margem / 100f)).toInt().coerceAtLeast(100)

        // pré-carrega as próximas páginas (já desenhadas quando você chegar nelas)
        LaunchedEffect(indiceAtual, zoomRender, larguraBase) {
            for (k in 1..2) {
                val p = paginas.getOrNull(indiceAtual + k) ?: break
                try { p.bitmap(minOf(larguraBase * zoomRender, 3200f).toInt()) } catch (e: CancellationException) { throw e } catch (e: Exception) { }
            }
        }

        /** Zoom/arrasto em torno de um ponto da tela: o ponto sob os dedos fica no mesmo lugar. */
        fun transforma(f: Float, pan: Offset, c: Offset) {
            val nz = (zoom * f).coerceIn(1f, 6f)
            if (nz <= 1.001f) {
                zoom = 1f; tx = 0f; ty = 0f
                return
            }
            val k = nz / zoom
            val ntx = c.x - (c.x - tx) * k + pan.x
            val nty = c.y - (c.y - ty) * k + pan.y
            zoom = nz
            tx = ntx.coerceIn(vw * (1 - nz), 0f)
            val cy = nty.coerceIn(vh * (1 - nz), 0f)
            val sobra = nty - cy
            ty = cy
            if (!paginado && sobra != 0f) lista.dispatchRawDelta(-sobra / zoom)  // o que não coube vira rolagem
        }

        val navega: (Int) -> Unit = { dir ->
            escopo.launch {
                if (!paginado) {
                    lista.animateScrollBy(dir * vh * 0.85f)
                } else {
                    val passo = if (modo == "vertical") dir else if (rtl) -dir else dir
                    val destino = (pager.currentPage + passo).coerceIn(0, (total - 1 + (if (temProximo) 1 else 0)).coerceAtLeast(0))
                    if (op.animar) pager.animateScrollToPage(destino) else pager.scrollToPage(destino)
                }
            }
        }
        val toqueUnico: (Offset) -> Unit = { pos ->
            if (menu) {
                menu = false
            } else {
                val terco = vw / 3f
                val dir = if (pos.x < terco) -1 else if (pos.x > 2 * terco) 1 else 0
                if (op.zonas && dir != 0 && zoom < 1.2f) {
                    navega(dir)
                } else {
                    menu = true
                }
            }
        }
        val registraToque: (Offset) -> Unit = { pos ->
            toques++
            jobToque?.cancel()
            jobToque = escopo.launch {
                delay(260)
                val n = toques
                toques = 0
                if (dica) {
                    dica = false
                    cfg.dicaLeitura = true
                } else if (n >= 3) {
                    folha = true
                } else if (n == 2) {
                    if (op.toqueDuplo) {
                        transforma(if (zoom > 1.2f) 1f / zoom else 2.5f / zoom, Offset.Zero, pos)
                    } else {
                        toqueUnico(pos)
                    }
                } else {
                    toqueUnico(pos)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(vw, vh, paginado) {
                    awaitEachGesture {  // pinça (2 dedos) e arrasto com zoom
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val dois = ev.changes.size >= 2
                            if (dois || zoom > 1.01f) {
                                val f = if (dois) ev.calculateZoom() else 1f
                                val pan = ev.calculatePan()
                                val c = ev.calculateCentroid(useCurrent = true)
                                if (f != 1f || pan != Offset.Zero) {
                                    transforma(f, pan, c)
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        } while (ev.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {  // toques (1, 2 ou 3 seguidos)
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop * 2) registraToque(down.position)
                    }
                }
        ) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = tx
                    translationY = ty
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            ) {
                if (!paginado) {
                    val folga = ((vw - larguraBase) / 2f).coerceAtLeast(0f)
                    val larguraRender = minOf(larguraBase * zoomRender, 3200f).toInt()
                    LazyColumn(
                        state = lista,
                        modifier = Modifier.padding(start = with(dens) { folga.toDp() }).width(with(dens) { larguraBase.toDp() }).fillMaxHeight()
                    ) {
                        items(count = total, key = { paginas[it].id }) { i ->
                            PaginaLeitura(paginas[i], larguraBase, larguraRender, espaco, filtro)
                        }
                        if (temProximo) {
                            item(key = "fim") {
                                TransicaoFim(contexto?.proximo?.nome, contagem, onProximo, Modifier.height(320.dp))
                            }
                        }
                    }
                } else {
                    val conteudo: @Composable (Int) -> Unit = { i ->
                        if (i >= total) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TransicaoFim(contexto?.proximo?.nome, contagem, onProximo) }
                        } else {
                            val p = paginas[i]
                            val ajuste = minOf(vw, vh / p.razao())
                            val larguraRender = minOf(ajuste * zoomRender, 3200f).toInt()
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                PaginaLeitura(p, ajuste.toInt().coerceAtLeast(50), larguraRender, 0.dp, filtro)
                            }
                        }
                    }
                    if (modo == "vertical") {
                        VerticalPager(state = pager, userScrollEnabled = zoom <= 1.01f, modifier = Modifier.fillMaxSize()) { i -> conteudo(i) }
                    } else {
                        HorizontalPager(state = pager, reverseLayout = rtl, userScrollEnabled = zoom <= 1.01f, modifier = Modifier.fillMaxSize()) { i -> conteudo(i) }
                    }
                }
            }
        }

        if (op.numero && !menu && !folha) {
            Text(
                "${indiceAtual + 1} / $total", color = Color.White, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp).background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        if (op.brilho < 100) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ((100 - op.brilho) / 100f).coerceIn(0f, 0.9f))))
        }

        // ---------------- barra de cima: voltar, título, menu ----------------
        AnimatedVisibility(
            visible = menu, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xF01C1C1E))
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable { onSair() }.padding(12.dp)) { Ico("voltar", tam = 24.dp) }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(proj.nome, color = Color.White, fontSize = 18.sp, maxLines = 1)
                    Text("Página ${indiceAtual + 1} de $total", color = Color(0xFFB0B0B8), fontSize = 13.sp)
                }
                Box {
                    Box(Modifier.clip(RoundedCornerShape(24.dp)).clickable { maisAberto = true }.padding(12.dp)) { Ico("mais", tam = 24.dp) }
                    DropdownMenu(expanded = maisAberto, onDismissRequest = { maisAberto = false }) {
                        DropdownMenuItem(text = { Text("Zoom 100%") }, onClick = { maisAberto = false; transforma(1f / zoom, Offset.Zero, Offset.Zero) })
                        if (contexto?.anterior != null) DropdownMenuItem(text = { Text("Arquivo anterior") }, onClick = { maisAberto = false; onAnterior() })
                        if (temProximo) DropdownMenuItem(text = { Text("Próximo arquivo") }, onClick = { maisAberto = false; onProximo() })
                        DropdownMenuItem(text = { Text("Sair da leitura") }, onClick = { maisAberto = false; onSair() })
                    }
                }
            }
        }

        // ---------------- barra de baixo: página + ícones ----------------
        AnimatedVisibility(
            visible = menu, modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
        ) {
            Column(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) {
                if (aviso != null) {
                    Text(
                        aviso ?: "", color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                            .background(Color(0xE61C1C1E), RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).background(Color(0xF02A2A2E)).clickable {
                        if (contexto?.anterior != null) onAnterior() else irPara(if (rtl) total - 1 else 0)
                    }.padding(14.dp)) { Ico("ini") }
                    Row(
                        Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xF02A2A2E)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${indiceAtual + 1}", color = Color.White, fontSize = 15.sp)
                        val valor = if (rtl) (total - indiceAtual).toFloat() else (indiceAtual + 1).toFloat()
                        Slider(
                            value = valor.coerceIn(1f, total.coerceAtLeast(2).toFloat()), enabled = total > 1,
                            valueRange = 1f..total.coerceAtLeast(2).toFloat(),
                            onValueChange = { v ->
                                val n = v.roundToInt()
                                irPara(if (rtl) total - n else n - 1)
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )
                        Text("$total", color = Color.White, fontSize = 15.sp)
                    }
                    Box(Modifier.clip(CircleShape).background(Color(0xF02A2A2E)).clickable {
                        if (temProximo) onProximo() else irPara(if (rtl) 0 else total - 1)
                    }.padding(14.dp)) { Ico("fim") }
                }
                Row(
                    Modifier.fillMaxWidth().background(Color(0xF01C1C1E)).windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box {
                        BotaoIco("modo", "Modo") { modoAberto = true }
                        DropdownMenu(expanded = modoAberto, onDismissRequest = { modoAberto = false }) {
                            for ((rot, v) in listOf(
                                "Padrão" to "padrao", "Tira longa (sem espaços)" to "tira", "Tira longa com lacunas" to "lacunas",
                                "Paginado (esquerda para direita)" to "esq_dir", "Paginado (direita para esquerda)" to "dir_esq", "Paginado (vertical)" to "vertical"
                            )) {
                                DropdownMenuItem(
                                    text = { Text((if (op.modo == v) "✓ " else "") + rot) },
                                    onClick = { modoAberto = false; op.modo = v; op.salva(); aviso = rot }
                                )
                            }
                        }
                    }
                    BotaoIco("girar", "Orientação") {
                        val ordem = listOf("padrao", "auto", "retrato", "paisagem")
                        val prox = ordem[(ordem.indexOf(op.orientacao) + 1) % ordem.size]
                        op.orientacao = prox; op.salva()
                        aviso = "Orientação: " + when (prox) { "auto" -> "automática"; "retrato" -> "retrato"; "paisagem" -> "paisagem"; else -> "padrão" }
                    }
                    BotaoIco("fundo", "Fundo") {
                        val ordem = listOf("preto", "cinza", "branco", "auto")
                        val prox = ordem[(ordem.indexOf(op.fundo) + 1) % ordem.size]
                        op.fundo = prox; op.salva()
                        aviso = "Fundo: " + when (prox) { "cinza" -> "cinza"; "branco" -> "branco"; "auto" -> "automático"; else -> "preto" }
                    }
                    BotaoIco("gear", "Ajustes") { folha = true }
                }
            }
        }

        if (folha) {
            FolhaOpcoes(
                op = op, onFechar = { folha = false }, onSair = { folha = false; onSair() },
                onOrientacao = { }, modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ---------------- dica das zonas de toque (só na 1ª vez) ----------------
        if (dica) {
            Row(
                Modifier.fillMaxSize().background(Color(0xCC000000)).pointerInput(Unit) { detectTapGestures { dica = false; cfg.dicaLeitura = true } }
            ) {
                for ((titulo, sub) in listOf("VOLTAR" to "toque aqui:\npágina anterior", "MENU" to "toque aqui:\nmostra a barra e as opções", "AVANÇAR" to "toque aqui:\npróxima página")) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(titulo, color = Color.White, fontSize = 20.sp)
                        Text(sub, color = Color(0xFFD0D0D6), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Text(
                "Toque para começar   •   Toque duplo ou pinça = zoom onde você tocar   •   Voltar = sair",
                color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 12.dp, end = 12.dp)
            )
        }
    }
}

// ====================================================== folha de opções (como no Mihon)
@Composable
private fun Chip(rotulo: String, marcado: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (marcado) Color(0xFF8A8A8A) else Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF6A6A72)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(rotulo, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, color = Color(0xFFE6E6EA), fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
private fun LinhaCheck(rotulo: String, marcado: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!marcado) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcado, onCheckedChange = { onChange(it) })
        Text(rotulo, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolhaOpcoes(
    op: OpcoesLeitura, onFechar: () -> Unit, onSair: () -> Unit,
    onOrientacao: (String) -> Unit, modifier: Modifier = Modifier
) {
    var aba by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().background(Color(0x66000000)).clickable { onFechar() }) {
        Surface(
            color = Color(0xFF151517), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 620.dp)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onSair) { Text("Sair do modo leitura", color = Color(0xFFFF8A80)) }
                    TextButton(onClick = onFechar) { Text("Fechar", color = Color.White) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    listOf("Modo de leitura", "Geral", "Filtro").forEachIndexed { i, rot ->
                        TextButton(onClick = { aba = i }, modifier = Modifier.weight(1f)) {
                            Text(rot, color = if (aba == i) Color.White else Color(0xFF9A9AA2), fontSize = 16.sp)
                        }
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp)) {
                    when (aba) {
                        0 -> {
                            Titulo("Modo de leitura")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf(
                                    "Padrão" to "padrao", "Tira longa (sem espaços)" to "tira", "Tira longa com lacunas" to "lacunas",
                                    "Paginado (esquerda para direita)" to "esq_dir", "Paginado (direita para esquerda)" to "dir_esq", "Paginado (vertical)" to "vertical"
                                )) Chip(rot, op.modo == v) { op.modo = v; op.salva() }
                            }
                            Titulo("Orientação")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf("Padrão" to "padrao", "Automática" to "auto", "Retrato" to "retrato", "Paisagem" to "paisagem")) {
                                    Chip(rot, op.orientacao == v) { op.orientacao = v; op.salva(); onOrientacao(v) }
                                }
                            }
                            Titulo("Zonas de toque")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Ativadas (esquerda/direita)", op.zonas) { op.zonas = true; op.salva() }
                                Chip("Desativadas", !op.zonas) { op.zonas = false; op.salva() }
                            }
                            Titulo("Margem lateral: ${op.margem}%")
                            Slider(value = op.margem.toFloat(), onValueChange = { op.margem = it.toInt() }, onValueChangeFinished = { op.salva() }, valueRange = 0f..25f)
                            LinhaCheck("Toque duplo para dar zoom", op.toqueDuplo) { op.toqueDuplo = it; op.salva() }
                        }
                        1 -> {
                            Titulo("Cor de fundo")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for ((rot, v) in listOf("Preto" to "preto", "Cinza" to "cinza", "Branco" to "branco", "Auto" to "auto")) {
                                    Chip(rot, op.fundo == v) { op.fundo = v; op.salva() }
                                }
                            }
                            Spacer(Modifier.padding(top = 10.dp))
                            LinhaCheck("Mostrar o número da página", op.numero) { op.numero = it; op.salva() }
                            LinhaCheck("Mostrar conteúdo na área de recorte da tela", op.recorte) { op.recorte = it; op.salva() }
                            LinhaCheck("Manter a tela ligada", op.manterTela) { op.manterTela = it; op.salva() }
                            LinhaCheck("Animar as transições de página", op.animar) { op.animar = it; op.salva() }
                            LinhaCheck("Abrir o próximo arquivo da pasta sozinho ao terminar", op.continuar) { op.continuar = it; op.salva() }
                        }
                        else -> {
                            Titulo("Brilho: ${op.brilho}%")
                            Slider(value = op.brilho.toFloat(), onValueChange = { op.brilho = it.toInt() }, onValueChangeFinished = { op.salva() }, valueRange = 10f..100f)
                            LinhaCheck("Nível de cinza", op.cinza) { op.cinza = it; op.salva() }
                            LinhaCheck("Invertido", op.invertido) { op.invertido = it; op.salva() }
                        }
                    }
                    Spacer(Modifier.padding(bottom = 24.dp))
                }
            }
        }
    }
}
