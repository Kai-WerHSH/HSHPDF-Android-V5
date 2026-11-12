// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Arquivo aberto recentemente (guarda a pasta de onde veio, para continuar no próximo arquivo dela). */
data class Recente(val uri: String, val nome: String, val quando: Long, val arvore: String? = null, val docIdPai: String? = null)

data class PastaBiblioteca(val uri: String, val nome: String)

/** Preferências do app (tema, biblioteca, leitura, criação). Tudo é salvo na hora. */
class Config(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hshpdf", Context.MODE_PRIVATE)

    private fun texto(k: String, padrao: String) = sp.getString(k, padrao) ?: padrao
    private fun salvaT(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun salvaB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun salvaI(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    var tema: String
        get() = texto("tema", "sistema")
        set(v) = salvaT("tema", v)

    var acento: Int
        get() = sp.getInt("acento", 0xFFE84A4A.toInt())
        set(v) = salvaI("acento", v)

    var som: Boolean
        get() = sp.getBoolean("som", true)
        set(v) = salvaB("som", v)

    // ---------------- leitura (estilo Mihon) ----------------
    var modoLeitura: String
        get() = texto("modo_leitura", "padrao")
        set(v) = salvaT("modo_leitura", v)

    var orientacao: String
        get() = texto("orientacao", "padrao")
        set(v) = salvaT("orientacao", v)

    var fundoLeitura: String
        get() = texto("fundo_leitura", "preto")
        set(v) = salvaT("fundo_leitura", v)

    var zonasToque: Boolean
        get() = sp.getBoolean("zonas_toque", true)
        set(v) = salvaB("zonas_toque", v)

    var toqueDuploZoom: Boolean
        get() = sp.getBoolean("toque_duplo_zoom", true)
        set(v) = salvaB("toque_duplo_zoom", v)

    var mostrarNumero: Boolean
        get() = sp.getBoolean("mostrar_numero", false)
        set(v) = salvaB("mostrar_numero", v)

    var mostrarNoRecorte: Boolean
        get() = sp.getBoolean("mostrar_recorte", true)
        set(v) = salvaB("mostrar_recorte", v)

    var manterTela: Boolean
        get() = sp.getBoolean("manter_tela", true)
        set(v) = salvaB("manter_tela", v)

    var animarPaginas: Boolean
        get() = sp.getBoolean("animar_paginas", true)
        set(v) = salvaB("animar_paginas", v)

    var cinza: Boolean
        get() = sp.getBoolean("cinza", false)
        set(v) = salvaB("cinza", v)

    var invertido: Boolean
        get() = sp.getBoolean("invertido", false)
        set(v) = salvaB("invertido", v)

    var margemLateral: Int
        get() = sp.getInt("margem_lateral", 0)
        set(v) = salvaI("margem_lateral", v)

    var brilho: Int
        get() = sp.getInt("brilho", 100)
        set(v) = salvaI("brilho", v)

    var dicaLeitura: Boolean
        get() = sp.getBoolean("dica_leitura", false)
        set(v) = salvaB("dica_leitura", v)

    /** Ao chegar ao fim de um arquivo, abrir o próximo da pasta sozinho (com contagem regressiva). */
    var continuarAuto: Boolean
        get() = sp.getBoolean("continuar_auto", true)
        set(v) = salvaB("continuar_auto", v)

    // ---------------- progresso de leitura ----------------
    fun paginaSalva(chave: String?): Int = if (chave == null) 0 else sp.getInt("pag_${chave.hashCode()}", 0)

    fun salvaProgresso(chave: String?, pagina: Int, total: Int) {
        if (chave == null) return
        sp.edit().putInt("pag_${chave.hashCode()}", pagina).putInt("tot_${chave.hashCode()}", total).apply()
    }

    /** (página atual, total) ou null se nunca foi lido. */
    fun progresso(chave: String): Pair<Int, Int>? {
        val t = sp.getInt("tot_${chave.hashCode()}", 0)
        return if (t <= 0) null else Pair(sp.getInt("pag_${chave.hashCode()}", 0), t)
    }

    fun marcaLido(chave: String, lido: Boolean) {
        val t = sp.getInt("tot_${chave.hashCode()}", 0).coerceAtLeast(1)
        sp.edit().putInt("tot_${chave.hashCode()}", t).putInt("pag_${chave.hashCode()}", if (lido) t - 1 else 0).apply()
    }

    // ---------------- biblioteca ----------------
    fun pastas(): List<PastaBiblioteca> = try {
        val arr = JSONArray(texto("pastas", "[]"))
        (0 until arr.length()).map { val o = arr.getJSONObject(it); PastaBiblioteca(o.getString("uri"), o.getString("nome")) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun gravaPastas(l: List<PastaBiblioteca>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("uri", it.uri).put("nome", it.nome)) }
        salvaT("pastas", arr.toString())
    }

    fun adicionarPasta(p: PastaBiblioteca) = gravaPastas(pastas().filter { it.uri != p.uri } + p)
    fun removerPasta(p: PastaBiblioteca) = gravaPastas(pastas().filter { it.uri != p.uri })

    fun recentes(): List<Recente> = try {
        val arr = JSONArray(texto("recentes", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Recente(o.getString("uri"), o.getString("nome"), o.optLong("quando"), o.optString("arvore", "").ifEmpty { null }, o.optString("pai", "").ifEmpty { null })
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun adicionarRecente(uri: Uri, nome: String, arvore: Uri? = null, docIdPai: String? = null) {
        val resto = recentes().filter { it.uri != uri.toString() }
        val l = (listOf(Recente(uri.toString(), nome, System.currentTimeMillis(), arvore?.toString(), docIdPai)) + resto).take(20)
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("nome", it.nome).put("quando", it.quando).put("arvore", it.arvore ?: "").put("pai", it.docIdPai ?: ""))
        }
        salvaT("recentes", arr.toString())
    }

    fun removerRecente(r: Recente) {
        val arr = JSONArray()
        recentes().filter { it.uri != r.uri }.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("nome", it.nome).put("quando", it.quando).put("arvore", it.arvore ?: "").put("pai", it.docIdPai ?: ""))
        }
        salvaT("recentes", arr.toString())
    }

    // ---------------- criação ----------------
    /** Pasta onde os arquivos criados são salvos (URI de árvore). */
    var pastaSaida: String?
        get() = sp.getString("pasta_saida", null)
        set(v) = sp.edit().putString("pasta_saida", v).apply()

    var nomePastaSaida: String
        get() = texto("nome_pasta_saida", "")
        set(v) = salvaT("nome_pasta_saida", v)

    /** original | alta | media | pequena */
    var preset: String
        get() = texto("preset", "original")
        set(v) = salvaT("preset", v)

    /** Formatos marcados na criação (pdf, cbz, zip, epub), separados por vírgula. */
    var formatos: Set<String>
        get() = texto("formatos", "cbz").split(",").filter { it.isNotBlank() }.toSet()
        set(v) = salvaT("formatos", v.joinToString(","))
}
