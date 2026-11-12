// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix as MatrizAndroid
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Pixmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/** O MuPDF não é thread-safe: todo acesso a ele passa por esta única thread (com pilha grande). */
object Trabalho {
    val mupdf: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(null, r, "mupdf", 32L * 1024 * 1024).apply { isDaemon = true }
    }.asCoroutineDispatcher()
}

/** Índices em ordem de distância do centro: centro, +1, -1, +2, -2... (mede primeiro o que o leitor vai mostrar). */
fun ordemDesde(centro: Int, n: Int): List<Int> {
    val saida = ArrayList<Int>(n)
    if (n == 0) return saida
    val c = centro.coerceIn(0, n - 1)
    saida.add(c)
    var d = 1
    while (saida.size < n) {
        if (c + d < n) saida.add(c + d)
        if (c - d >= 0) saida.add(c - d)
        d++
    }
    return saida
}

/** Ordenação natural: 2.jpg vem antes de 10.jpg. */
fun comparaNatural(a: String, b: String): Int {
    val re = Regex("(\\d+)|(\\D+)")
    val pa = re.findAll(a.lowercase()).map { it.value }.toList()
    val pb = re.findAll(b.lowercase()).map { it.value }.toList()
    for (k in 0 until minOf(pa.size, pb.size)) {
        val x = pa[k]
        val y = pb[k]
        val nx = x.toLongOrNull()
        val ny = y.toLongOrNull()
        val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
        if (c != 0) return c
    }
    return pa.size.compareTo(pb.size)
}

fun rotacionar(bmp: Bitmap, graus: Int): Bitmap {
    if (graus % 360 == 0) return bmp
    val m = MatrizAndroid()
    m.postRotate(graus.toFloat())
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/**
 * Um documento aberto (PDF, EPUB, CBZ, CBR ou imagens) que sabe desenhar suas páginas.
 * Os tamanhos das páginas são medidos em segundo plano: enquanto isso o leitor usa uma estimativa e já mostra tudo.
 */
abstract class Fonte {
    abstract val paginas: Int

    /** Muda toda vez que algum tamanho de página é descoberto (as telas se atualizam sozinhas). */
    var versao by mutableIntStateOf(0)
    var nomeOrigem: String? = null
    val limpezas = ArrayList<() -> Unit>()

    @Volatile protected var estW = 595f
    @Volatile protected var estH = 842f

    open val ehPdf: Boolean get() = false
    protected abstract fun tamanhoReal(i: Int): Pair<Float, Float>?
    fun tamanho(i: Int): Pair<Float, Float> = tamanhoReal(i) ?: Pair(estW, estH)
    fun conhecido(i: Int): Boolean = tamanhoReal(i) != null

    protected abstract fun desenhar(i: Int, larguraPx: Int): Bitmap
    open val despachante: CoroutineDispatcher get() = Trabalho.mupdf

    /** Mede as páginas (começando pelas mais próximas de `centro`). */
    open suspend fun medirTudo(centro: Int) {}

    /** Bytes originais da página, se ela for uma imagem (para copiar sem recomprimir). */
    open fun abrirOriginal(i: Int): InputStream? = null
    open fun nomeDaPagina(i: Int): String = "%05d".format(i)
    open fun grausOriginal(i: Int): Int = 0
    protected abstract fun fecharInterno()

    fun fechar() {
        try { fecharInterno() } catch (e: Exception) { }
        for (l in limpezas) {
            try { l() } catch (e: Exception) { }
        }
        limpezas.clear()
    }

    private val cache = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 1024) / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Desenha a página com a largura pedida (arredondada para múltiplos de 64 px, para aproveitar o cache). */
    suspend fun renderizar(i: Int, larguraPx: Int): Bitmap {
        val q = ((larguraPx + 63) / 64) * 64
        val chave = "$i@$q"
        cache.get(chave)?.let { return it }
        val bmp = withContext(despachante) { desenhar(i, q) }
        cache.put(chave, bmp)
        return bmp
    }

    /** Igual a renderizar, mas sem guardar no cache (para exportar centenas de páginas sem encher a memória). */
    suspend fun bitmapParaExportar(i: Int, larguraPx: Int): Bitmap = withContext(despachante) { desenhar(i, larguraPx) }

    private val bordas = HashMap<Int, Int>()

    /** Cor predominante nas bordas da página (fundo "Auto" do leitor). */
    suspend fun corBorda(i: Int): Int {
        bordas[i]?.let { return it }
        val b = renderizar(i, 64)
        val w = b.width
        val h = b.height
        val esp = maxOf(1, (minOf(w, h) * 0.04f).toInt())
        val cont = HashMap<Int, IntArray>()
        fun amostra(x: Int, y: Int) {
            val p = b.getPixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val bl = p and 0xFF
            val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (bl shr 4)
            val a = cont.getOrPut(k) { IntArray(4) }
            a[0]++; a[1] += r; a[2] += g; a[3] += bl
        }
        for (y in 0 until h) {
            if (y < esp || y >= h - esp) {
                for (x in 0 until w) amostra(x, y)
            } else {
                for (x in 0 until esp) amostra(x, y)
                for (x in w - esp until w) amostra(x, y)
            }
        }
        val m = cont.values.maxByOrNull { it[0] } ?: return 0xFF000000.toInt()
        val cor = (0xFF shl 24) or ((m[1] / m[0]) shl 16) or ((m[2] / m[0]) shl 8) or (m[3] / m[0])
        bordas[i] = cor
        return cor
    }
}

/** A página já girada (e redimensionada) para a largura pedida. */
suspend fun Pagina.bitmap(larguraPx: Int): Bitmap {
    val (w, h) = fonte.tamanho(indice)
    val girada = rot % 180 != 0
    val lb = if (girada) (larguraPx * w / h).toInt().coerceAtLeast(32) else larguraPx
    val base = fonte.renderizar(indice, lb)
    return if (rot == 0) base else withContext(Dispatchers.Default) { rotacionar(base, rot) }
}

// ====================================================== PDF, EPUB, XPS (MuPDF)
class FontePdf(caminho: String) : Fonte() {
    private val doc: Document = Document.openDocument(caminho)
    override val paginas: Int
    private val larg: FloatArray
    private val alt: FloatArray
    override val ehPdf: Boolean get() = true

    init {
        if (doc.isReflowable()) doc.layout(480f, 720f, 11f)
        paginas = doc.countPages()
        larg = FloatArray(paginas)
        alt = FloatArray(paginas)
        if (paginas > 0) {
            val p = doc.loadPage(0)
            val b = p.getBounds()
            larg[0] = maxOf(1f, b.x1 - b.x0)
            alt[0] = maxOf(1f, b.y1 - b.y0)
            p.destroy()
            estW = larg[0]
            estH = alt[0]
        }
    }

    override fun tamanhoReal(i: Int): Pair<Float, Float>? = if (larg[i] > 0f) Pair(larg[i], alt[i]) else null

    override suspend fun medirTudo(centro: Int) {
        for (bloco in ordemDesde(centro, paginas).chunked(24)) {
            withContext(Trabalho.mupdf) {
                for (i in bloco) {
                    if (larg[i] <= 0f) {
                        val p = doc.loadPage(i)
                        val b = p.getBounds()
                        larg[i] = maxOf(1f, b.x1 - b.x0)
                        alt[i] = maxOf(1f, b.y1 - b.y0)
                        p.destroy()
                    }
                }
            }
            versao++
        }
    }

    override fun desenhar(i: Int, larguraPx: Int): Bitmap {
        val pagina = doc.loadPage(i)
        try {
            val b = pagina.getBounds()
            if (larg[i] <= 0f) {
                larg[i] = maxOf(1f, b.x1 - b.x0)
                alt[i] = maxOf(1f, b.y1 - b.y0)
                versao++
            }
            val s = larguraPx / (b.x1 - b.x0)
            val ctm = Matrix(s, 0f, 0f, s, -b.x0 * s, -b.y0 * s)
            val pix = pagina.toPixmap(ctm, ColorSpace.DeviceRGB, false, true)
            try {
                return paraBitmap(pix)
            } finally {
                pix.destroy()
            }
        } finally {
            pagina.destroy()
        }
    }

    private fun paraBitmap(pix: Pixmap): Bitmap {
        val w = pix.getWidth()
        val h = pix.getHeight()
        val n = pix.getNumberOfComponents()
        val stride = pix.getStride()
        val s = pix.getSamples()
        val px = IntArray(w * h)
        var o = 0
        for (y in 0 until h) {
            var p = y * stride
            for (x in 0 until w) {
                val r = s[p].toInt() and 0xFF
                val g = s[p + 1].toInt() and 0xFF
                val bl = s[p + 2].toInt() and 0xFF
                px[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                p += n
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    override fun fecharInterno() {
        try { doc.destroy() } catch (e: Exception) { }
    }
}

// ====================================================== CBZ, CBR, pastas e imagens soltas
class Entrada(val nome: String, val abrir: () -> InputStream)

class FonteImagens(private val entradas: List<Entrada>, private val zip: ZipFile? = null) : Fonte() {
    override val paginas: Int get() = entradas.size
    private val larg = FloatArray(entradas.size)
    private val alt = FloatArray(entradas.size)
    private val rot = IntArray(entradas.size)
    override val despachante: CoroutineDispatcher get() = Dispatchers.Default

    init {
        if (entradas.isNotEmpty()) {
            medir(0)
            estW = larg[0]
            estH = alt[0]
        }
    }

    private fun lerGraus(i: Int): Int {
        val ext = entradas[i].nome.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("jpg", "jpeg", "heic", "heif", "webp", "avif")) return 0
        return try {
            entradas[i].abrir().use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun medir(i: Int) {
        if (larg[i] > 0f) return
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            entradas[i].abrir().use { BitmapFactory.decodeStream(it, null, o) }
        } catch (e: Exception) { }
        var w = o.outWidth.toFloat()
        var h = o.outHeight.toFloat()
        if (w <= 0f || h <= 0f) {
            w = 1000f
            h = 1400f
        }
        val g = lerGraus(i)
        rot[i] = g
        if (g == 90 || g == 270) {
            val t = w; w = h; h = t
        }
        alt[i] = h
        larg[i] = w
    }

    override fun tamanhoReal(i: Int): Pair<Float, Float>? = if (larg[i] > 0f) Pair(larg[i], alt[i]) else null

    override suspend fun medirTudo(centro: Int) {
        for (bloco in ordemDesde(centro, paginas).chunked(16)) {
            withContext(Dispatchers.IO) { for (i in bloco) medir(i) }
            versao++
        }
    }

    override fun desenhar(i: Int, larguraPx: Int): Bitmap {
        medir(i)
        val larguraFinal = larg[i]
        var amostra = 1
        while (larguraFinal / (amostra * 2) >= larguraPx) amostra *= 2
        val o = BitmapFactory.Options().apply {
            inSampleSize = amostra
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = entradas[i].abrir().use { BitmapFactory.decodeStream(it, null, o) }
            ?: return Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        return if (rot[i] != 0) rotacionar(bmp, rot[i]) else bmp
    }

    override fun abrirOriginal(i: Int): InputStream? = entradas[i].abrir()
    override fun nomeDaPagina(i: Int): String = entradas[i].nome
    override fun grausOriginal(i: Int): Int {
        medir(i)
        return rot[i]
    }

    override fun fecharInterno() {
        try { zip?.close() } catch (e: Exception) { }
    }

    companion object {
        val extensoes = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

        fun ehImagem(nome: String): Boolean = nome.substringAfterLast('.', "").lowercase() in extensoes

        fun deEntradas(l: List<Entrada>): FonteImagens = FonteImagens(l, null)

        fun deZip(arq: File): FonteImagens {
            val z = ZipFile(arq)
            val nomes = z.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { n -> ehImagem(n) && !n.substringAfterLast('/').startsWith(".") && !n.contains("__MACOSX") }
                .sortedWith { a, b -> comparaNatural(a, b) }
                .toList()
            require(nomes.isNotEmpty()) { "Não encontrei imagens dentro do arquivo." }
            return FonteImagens(nomes.map { n -> Entrada(n) { z.getInputStream(z.getEntry(n)) } }, z)
        }

        fun deArquivo(arq: File): FonteImagens = FonteImagens(listOf(Entrada(arq.name) { arq.inputStream() }), null)

        private fun nomeCabecalho(h: Any): String {
            for (m in listOf("getFileName", "getFileNameString", "getFileNameW")) {
                try { return (h.javaClass.getMethod(m).invoke(h) as String).replace('\\', '/') } catch (e: Exception) { }
            }
            return "pagina"
        }

        /**
         * CBR (RAR): começa a ler assim que a 1ª imagem é extraída; o resto continua em segundo plano
         * (por isso abre na hora, mesmo em arquivos grandes). Tudo é apagado ao fechar.
         */
        fun deRar(arq: File): FonteImagens {
            val dir = File(arq.parentFile, arq.name + "_rar").apply { mkdirs() }
            val classe = try { Class.forName("com.github.junrar.Archive") } catch (e: Exception) {
                throw IllegalStateException("Biblioteca de RAR não encontrada no app.")
            }
            val arch = classe.getConstructor(File::class.java).newInstance(arq)
            val cabs = (classe.getMethod("getFileHeaders").invoke(arch) as List<*>).filterNotNull()
            val validos = cabs.filter { h ->
                val dirFlag = try { h.javaClass.getMethod("isDirectory").invoke(h) as Boolean } catch (e: Exception) { false }
                !dirFlag && ehImagem(nomeCabecalho(h)) && !nomeCabecalho(h).contains("__MACOSX")
            }
            require(validos.isNotEmpty()) { "Não encontrei imagens dentro do arquivo." }
            val ordem = validos.map { nomeCabecalho(it) }.distinct().sortedWith { a, b -> comparaNatural(a, b) }
            val posicao = HashMap<String, Int>()
            ordem.forEachIndexed { i, n -> posicao[n] = i }
            fun destinoDe(nome: String) = File(dir, "%05d_%s".format(posicao[nome] ?: 0, nome.substringAfterLast('/')))
            val extrair = classe.methods.first { it.name == "extractFile" && it.parameterCount == 2 }
            val terminou = AtomicBoolean(false)
            val fio = Thread {
                try {
                    for (h in validos) {
                        if (Thread.currentThread().isInterrupted) break
                        val destino = destinoDe(nomeCabecalho(h))
                        if (destino.exists()) continue
                        val tmp = File(dir, destino.name + ".part")
                        tmp.outputStream().use { os -> extrair.invoke(arch, h, os) }
                        tmp.renameTo(destino)
                    }
                } catch (e: Throwable) {
                } finally {
                    terminou.set(true)
                    try { classe.getMethod("close").invoke(arch) } catch (e: Exception) { }
                }
            }
            fio.isDaemon = true
            fio.start()
            val entradas = ordem.map { nome ->
                val f = destinoDe(nome)
                Entrada(nome) {
                    while (!f.exists()) {
                        if (terminou.get() && !f.exists()) throw IOException("Página não extraída: $nome")
                        Thread.sleep(40)
                    }
                    f.inputStream()
                }
            }
            val fonte = FonteImagens(entradas, null)
            fonte.limpezas.add {
                fio.interrupt()
                dir.deleteRecursively()
            }
            return fonte
        }
    }
}

// ====================================================== abrir sem copiar
/** Arquivo pronto para ler + como limpar depois (apagar o link, fechar o descritor ou apagar a cópia temporária). */
class Origem(val arquivo: File, val nome: String, val limpeza: () -> Unit)

object Abertura {
    private val mupdfExts = setOf("pdf", "epub", "xps", "oxps", "fb2", "mobi")

    fun nomeDe(ctx: Context, uri: Uri): String {
        var nome = "documento"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) nome = it.getString(0) ?: nome
        }
        return nome
    }

    /**
     * Abre o arquivo SEM copiá-lo: cria um link para o descritor do arquivo original (nada ocupa espaço).
     * Se o provedor não permitir, usa uma cópia TEMPORÁRIA no cache, apagada ao fechar.
     */
    suspend fun abrirOrigem(ctx: Context, uri: Uri): Origem = withContext(Dispatchers.IO) {
        val nome = nomeDe(ctx, uri)
        val seguro = nome.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val base = System.nanoTime()
        try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                var seekavel = true
                try {
                    Os.lseek(pfd.fileDescriptor, 0L, OsConstants.SEEK_CUR)
                } catch (e: ErrnoException) {
                    seekavel = false
                }
                if (seekavel) {
                    val pasta = File(ctx.cacheDir, "links").apply { mkdirs() }
                    val link = File(pasta, "${base}_$seguro")
                    try {
                        Os.symlink("/proc/self/fd/${pfd.fd}", link.absolutePath)
                        if (link.canRead() && link.length() > 0L) {
                            return@withContext Origem(link, nome) {
                                try { link.delete() } catch (e: Exception) { }
                                try { pfd.close() } catch (e: Exception) { }
                            }
                        }
                    } catch (e: Exception) { }
                    try { link.delete() } catch (e: Exception) { }
                }
                pfd.close()
            }
        } catch (e: Exception) { }
        val pasta = File(ctx.cacheDir, "temp").apply { mkdirs() }
        val destino = File(pasta, "${base}_$seguro")
        val entrada = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Não consegui ler o arquivo.")
        entrada.use { e -> destino.outputStream().use { o -> e.copyTo(o, 1 shl 16) } }
        Origem(destino, nome) { try { destino.delete() } catch (e: Exception) { } }
    }

    suspend fun abrirArquivo(arquivo: File, nome: String): Fonte = withContext(Dispatchers.IO) {
        val ext = nome.substringAfterLast('.', "").lowercase()
        when {
            ext in mupdfExts -> withContext(Trabalho.mupdf) { FontePdf(arquivo.absolutePath) }
            ext == "cbz" || ext == "zip" -> FonteImagens.deZip(arquivo)
            FonteImagens.ehImagem(nome) -> FonteImagens.deArquivo(arquivo)
            ext == "cbr" || ext == "rar" -> FonteImagens.deRar(arquivo)
            ext == "cb7" || ext == "7z" -> throw IllegalStateException("CB7/7z ainda não é suportado. Use CBZ ou CBR.")
            else -> withContext(Trabalho.mupdf) { FontePdf(arquivo.absolutePath) }
        }
    }

    /** Abre o documento a partir do URI escolhido (sem deixar cópia no app). */
    suspend fun abrirFonte(ctx: Context, uri: Uri): Fonte {
        val o = abrirOrigem(ctx, uri)
        try {
            val f = abrirArquivo(o.arquivo, o.nome)
            f.limpezas.add(o.limpeza)
            f.nomeOrigem = o.nome
            return f
        } catch (e: Throwable) {
            o.limpeza()
            throw e
        }
    }

    suspend fun abrir(ctx: Context, uri: Uri): Projeto {
        val f = abrirFonte(ctx, uri)
        return Projeto.deFonte(f.nomeOrigem ?: "documento", uri.toString(), f)
    }

    /** Várias imagens (de qualquer formato que o Android decodifique) viram uma fonte, em ordem natural de nome. */
    suspend fun abrirImagens(ctx: Context, itens: List<Pair<Uri, String>>): Fonte = withContext(Dispatchers.IO) {
        val ordenados = itens.sortedWith { a, b -> comparaNatural(a.second, b.second) }
        val f = FonteImagens.deEntradas(ordenados.map { (u, n) ->
            Entrada(n) { ctx.contentResolver.openInputStream(u) ?: throw IOException("Não consegui abrir $n") }
        })
        f.nomeOrigem = "Imagens"
        f
    }

    suspend fun abrirImagensUris(ctx: Context, uris: List<Uri>): Fonte =
        abrirImagens(ctx, withContext(Dispatchers.IO) { uris.map { Pair(it, nomeDe(ctx, it)) } })

    // ---------------- armazenamento ----------------
    private fun tamanho(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        return (f.listFiles() ?: emptyArray()).sumOf { tamanho(it) }
    }

    /** Tamanho do cache temporário em bytes. */
    fun tamanhoCache(ctx: Context): Long = tamanho(ctx.cacheDir)

    /** Apaga cópias/pastas temporárias e capas. Só chamar sem documentos abertos. */
    fun limparTudo(ctx: Context) {
        File(ctx.filesDir, "trabalho").deleteRecursively()
        File(ctx.filesDir, "projetos").deleteRecursively()
        File(ctx.filesDir, "itens").deleteRecursively()
        for (nome in listOf("links", "temp", "abertos")) File(ctx.cacheDir, nome).deleteRecursively()
    }

    fun limparCapas(ctx: Context) {
        File(ctx.cacheDir, "capas").deleteRecursively()
    }
}
