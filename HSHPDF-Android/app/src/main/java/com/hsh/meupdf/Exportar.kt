// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Escritor de PDF próprio: cada página vira uma imagem JPEG (DCT) — o arquivo não incha. */
class PdfEscritor(private val saida: OutputStream, private val n: Int) {
    private var pos = 0L
    private val offs = LongArray(3 + 3 * n)

    private fun w(s: String) { val b = s.toByteArray(Charsets.ISO_8859_1); saida.write(b); pos += b.size }
    private fun w(b: ByteArray) { saida.write(b); pos += b.size }
    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    init {
        w("%PDF-1.4\n")
        offs[1] = pos
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        val kids = (0 until n).joinToString(" ") { "${5 + 3 * it} 0 R" }
        offs[2] = pos
        w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $n >>\nendobj\n")
    }

    fun pagina(k: Int, jpeg: ByteArray, pxW: Int, pxH: Int, ptW: Float, ptH: Float) {
        val img = 3 + 3 * k
        val cont = 4 + 3 * k
        val pg = 5 + 3 * k
        offs[img] = pos
        w("$img 0 obj\n<< /Type /XObject /Subtype /Image /Width $pxW /Height $pxH /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
        w(jpeg)
        w("\nendstream\nendobj\n")
        val c = "q ${f(ptW)} 0 0 ${f(ptH)} 0 0 cm /Im0 Do Q"
        offs[cont] = pos
        w("$cont 0 obj\n<< /Length ${c.length} >>\nstream\n$c\nendstream\nendobj\n")
        offs[pg] = pos
        w("$pg 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(ptW)} ${f(ptH)}] /Resources << /XObject << /Im0 $img 0 R >> >> /Contents $cont 0 R >>\nendobj\n")
    }

    fun fechar() {
        val xref = pos
        val total = 3 + 3 * n
        w("xref\n0 $total\n0000000000 65535 f \n")
        for (i in 1 until total) w(String.format(Locale.US, "%010d 00000 n \n", offs[i]))
        w("trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        saida.flush()
    }
}

/**
 * qualidade: JPEG 30-100. ladoMax: maior lado em pixels (0 = sem limite).
 * originais: copia o arquivo da imagem sem recompressar sempre que o formato permitir (rápido e sem perda).
 */
class OpcoesExport(val qualidade: Int, val ladoMax: Int, val originais: Boolean)

object Presets {
    val nomes = listOf(
        "original" to "Original (sem recompressar)", "alta" to "Alta qualidade",
        "media" to "Média (arquivo menor)", "pequena" to "Pequena (economiza espaço)"
    )

    fun de(nome: String): OpcoesExport = when (nome) {
        "alta" -> OpcoesExport(90, 3000, false)
        "media" -> OpcoesExport(80, 2200, false)
        "pequena" -> OpcoesExport(65, 1600, false)
        else -> OpcoesExport(92, 0, true)
    }
}

class PaginaPronta(val dados: ByteArray, val ext: String, val w: Int, val h: Int, val ptW: Float, val ptH: Float)

object Formatos {
    /** (extensão, tipo MIME usado ao criar o arquivo). CBZ usa tipo genérico para o Android não acrescentar ".zip". */
    fun de(f: String): Pair<String, String> = when (f) {
        "pdf" -> Pair("pdf", "application/pdf")
        "cbz" -> Pair("cbz", "application/octet-stream")
        "zip" -> Pair("zip", "application/zip")
        else -> Pair("epub", "application/epub+zip")
    }

    val todos = listOf("pdf" to "PDF", "cbz" to "CBZ", "zip" to "ZIP", "epub" to "EPUB")
}

/** Criação dos arquivos de saída na pasta escolhida (sem sobrescrever: acrescenta "(2)" se já existir). */
object Saida {
    private fun pai(arvore: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))

    private fun existentes(ctx: Context, arvore: Uri): Set<String> {
        val nomes = HashSet<String>()
        val filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))
        ctx.contentResolver.query(filhos, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            while (it.moveToNext()) nomes.add(it.getString(0) ?: "")
        }
        return nomes
    }

    fun criar(ctx: Context, arvore: Uri, nome: String, mime: String): Uri {
        val ja = existentes(ctx, arvore)
        val base = nome.substringBeforeLast('.')
        val ext = nome.substringAfterLast('.', "")
        var final = nome
        var k = 2
        while (final in ja) {
            final = "$base ($k).$ext"
            k++
        }
        return DocumentsContract.createDocument(ctx.contentResolver, pai(arvore), mime, final)
            ?: throw IOException("Não consegui criar o arquivo na pasta de saída. Escolha a pasta de novo.")
    }

    fun apagar(ctx: Context, uri: Uri) {
        try { DocumentsContract.deleteDocument(ctx.contentResolver, uri) } catch (e: Exception) { }
    }

    fun nomeDaPasta(ctx: Context, arvore: Uri): String {
        var nome = "Pasta"
        try {
            ctx.contentResolver.query(pai(arvore), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) nome = it.getString(0) ?: nome
            }
        } catch (e: Exception) { }
        return nome
    }
}

object Exportador {
    /** Prepara uma página: copia o original (se puder) ou decodifica, gira, limita o tamanho e grava em JPEG. */
    suspend fun preparar(p: Pagina, op: OpcoesExport, aceitos: Set<String>): PaginaPronta {
        val f = p.fonte
        val (w0, h0) = f.tamanho(p.indice)
        val girada = p.rot % 180 != 0
        val wF = if (girada) h0 else w0
        val hF = if (girada) w0 else h0
        if (op.originais && !f.ehPdf && p.rot == 0 && f.grausOriginal(p.indice) == 0) {
            var ext = f.nomeDaPagina(p.indice).substringAfterLast('.', "").lowercase()
            if (ext == "jpeg") ext = "jpg"
            if (ext in aceitos) {
                val bytes = withContext(Dispatchers.IO) { f.abrirOriginal(p.indice)?.use { it.readBytes() } }
                if (bytes != null && bytes.isNotEmpty()) {
                    return PaginaPronta(bytes, ext, wF.toInt(), hF.toInt(), wF * 72f / 150f, hF * 72f / 150f)
                }
            }
        }
        val base = if (f.ehPdf) 2f else 1f
        var fator = base
        if (op.ladoMax > 0 && maxOf(wF, hF) * base > op.ladoMax) fator = op.ladoMax / maxOf(wF, hF)
        val alvo = (wF * fator).toInt().coerceAtLeast(64)
        val larguraBase = if (girada) (alvo * w0 / h0).toInt().coerceAtLeast(32) else alvo
        val bmp = f.bitmapParaExportar(p.indice, larguraBase)
        return withContext(Dispatchers.Default) {
            var g = if (p.rot != 0) rotacionar(bmp, p.rot) else bmp
            if (g.width > alvo * 1.02f) {
                val nh = (g.height * alvo.toFloat() / g.width).toInt().coerceAtLeast(1)
                val e = Bitmap.createScaledBitmap(g, alvo, nh, true)
                if (e !== g) {
                    if (g !== bmp) g.recycle()
                    g = e
                }
            }
            val saida = Bitmap.createBitmap(g.width, g.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(saida)
            c.drawColor(Color.WHITE)  // JPEG não tem transparência
            c.drawBitmap(g, 0f, 0f, null)
            val o = ByteArrayOutputStream()
            saida.compress(Bitmap.CompressFormat.JPEG, op.qualidade.coerceIn(30, 100), o)
            val w = saida.width
            val h = saida.height
            if (g !== bmp) g.recycle()
            bmp.recycle()
            saida.recycle()
            val ptW = if (f.ehPdf) wF else w * 72f / 150f
            val ptH = if (f.ehPdf) hF else h * 72f / 150f
            PaginaPronta(o.toByteArray(), "jpg", w, h, ptW, ptH)
        }
    }

    /** Prepara as páginas em paralelo (até `paralelo` ao mesmo tempo) e entrega na ORDEM certa. */
    private suspend fun <T> emOrdem(n: Int, paralelo: Int, preparar: suspend (Int) -> T, consumir: suspend (Int, T) -> Unit) = coroutineScope {
        val fila = ArrayDeque<Deferred<T>>()
        var proximo = 0
        for (k in 0 until n) {
            while (fila.size < paralelo && proximo < n) {
                val i = proximo++
                fila.addLast(async(Dispatchers.Default) { preparar(i) })
            }
            val d = fila.removeFirst()
            consumir(k, d.await())
        }
    }

    /** Cria o arquivo no formato pedido dentro da pasta de saída. Devolve o URI do arquivo criado. */
    suspend fun gerar(
        ctx: Context, paginas: List<Pagina>, formato: String, nomeBase: String, arvore: Uri,
        op: OpcoesExport, progresso: (Int, Int) -> Unit
    ): Uri {
        val (ext, mime) = Formatos.de(formato)
        val uri = Saida.criar(ctx, arvore, "$nomeBase.$ext", mime)
        try {
            val os = ctx.contentResolver.openOutputStream(uri, "w") ?: throw IOException("Não consegui gravar o arquivo.")
            withContext(Dispatchers.IO) {
                os.use { escreve(formato, paginas, it, op, nomeBase, progresso) }
            }
        } catch (e: Throwable) {
            Saida.apagar(ctx, uri)
            throw e
        }
        return uri
    }

    private suspend fun escreve(formato: String, paginas: List<Pagina>, os: OutputStream, op: OpcoesExport, titulo: String, progresso: (Int, Int) -> Unit) {
        val n = paginas.size
        when (formato) {
            "pdf" -> {
                val esc = PdfEscritor(BufferedOutputStream(os, 1 shl 16), n)
                emOrdem(n, 3, { i -> preparar(paginas[i], op, setOf("jpg")) }) { k, p ->
                    currentCoroutineContext().ensureActive()
                    esc.pagina(k, p.dados, p.w, p.h, p.ptW, p.ptH)
                    progresso(k + 1, n)
                }
                esc.fechar()
            }
            "cbz", "zip" -> {
                val z = ZipOutputStream(BufferedOutputStream(os, 1 shl 16))
                z.setLevel(1)
                emOrdem(n, 3, { i -> preparar(paginas[i], op, setOf("jpg", "png", "gif", "webp", "bmp")) }) { k, p ->
                    currentCoroutineContext().ensureActive()
                    z.putNextEntry(ZipEntry(String.format(Locale.US, "%04d.%s", k + 1, p.ext)))
                    z.write(p.dados)
                    z.closeEntry()
                    progresso(k + 1, n)
                }
                z.finish()
                z.flush()
            }
            else -> escreveEpub(paginas, os, op, titulo, progresso)
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun tipoDe(ext: String) = when (ext) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** EPUB de layout fixo: cada página é uma imagem (fiel ao original). */
    private suspend fun escreveEpub(paginas: List<Pagina>, saida: OutputStream, op: OpcoesExport, titulo: String, progresso: (Int, Int) -> Unit) {
        val n = paginas.size
        val z = ZipOutputStream(BufferedOutputStream(saida, 1 shl 16))
        val mt = "application/epub+zip".toByteArray()
        val e0 = ZipEntry("mimetype")
        e0.method = ZipEntry.STORED
        e0.size = mt.size.toLong()
        e0.compressedSize = mt.size.toLong()
        e0.crc = CRC32().also { it.update(mt) }.value
        z.putNextEntry(e0); z.write(mt); z.closeEntry()
        fun texto(nome: String, conteudo: String) {
            z.putNextEntry(ZipEntry(nome)); z.write(conteudo.toByteArray(Charsets.UTF_8)); z.closeEntry()
        }
        texto("META-INF/container.xml", "<?xml version=\"1.0\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
        texto("OEBPS/style.css", "html,body{margin:0;padding:0}.pg{text-align:center}img{max-width:100%;height:auto}")
        val cab = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n"
        val ns = "xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\""
        val manifest = StringBuilder("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
        val spine = StringBuilder()
        val navLi = StringBuilder()
        val ncx = StringBuilder()
        emOrdem(n, 3, { i -> preparar(paginas[i], op, setOf("jpg", "png", "gif")) }) { k, p ->
            currentCoroutineContext().ensureActive()
            val num = k + 1
            val pid = String.format(Locale.US, "p%04d", num)
            z.putNextEntry(ZipEntry("OEBPS/images/$pid.${p.ext}")); z.write(p.dados); z.closeEntry()
            val capa = if (num == 1) " properties=\"cover-image\"" else ""
            manifest.append("<item id=\"img$pid\" href=\"images/$pid.${p.ext}\" media-type=\"${tipoDe(p.ext)}\"$capa/>")
            texto("OEBPS/$pid.xhtml", "$cab<html $ns><head><meta charset=\"utf-8\"/><title>Página $num</title><meta name=\"viewport\" content=\"width=${p.w}, height=${p.h}\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body><div class=\"pg\"><img src=\"images/$pid.${p.ext}\" alt=\"Página $num\" width=\"${p.w}\" height=\"${p.h}\"/></div></body></html>")
            manifest.append("<item id=\"$pid\" href=\"$pid.xhtml\" media-type=\"application/xhtml+xml\"/>")
            spine.append("<itemref idref=\"$pid\"/>")
            navLi.append("<li><a href=\"$pid.xhtml\">Página $num</a></li>")
            ncx.append("<navPoint id=\"n$num\" playOrder=\"$num\"><navLabel><text>Página $num</text></navLabel><content src=\"$pid.xhtml\"/></navPoint>")
            progresso(num, n)
        }
        val uid = "urn:uuid:" + UUID.randomUUID()
        val agora = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
        texto("OEBPS/nav.xhtml", "$cab<html $ns><head><meta charset=\"utf-8\"/><title>${esc(titulo)}</title></head><body><nav epub:type=\"toc\" id=\"toc\"><h1>${esc(titulo)}</h1><ol>$navLi</ol></nav></body></html>")
        texto("OEBPS/toc.ncx", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><head><meta name=\"dtb:uid\" content=\"$uid\"/></head><docTitle><text>${esc(titulo)}</text></docTitle><navMap>$ncx</navMap></ncx>")
        texto("OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"uid\">$uid</dc:identifier><dc:title>${esc(titulo)}</dc:title><dc:language>pt-BR</dc:language><meta property=\"dcterms:modified\">$agora</meta><meta property=\"rendition:layout\">pre-paginated</meta><meta property=\"rendition:spread\">auto</meta></metadata><manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>$manifest</manifest><spine toc=\"ncx\">$spine</spine></package>")
        z.finish()
        z.flush()
    }
}
