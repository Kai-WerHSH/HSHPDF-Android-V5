// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Uma página do projeto: uma página de uma fonte (PDF, CBZ, imagens...), com giro opcional. */
data class Pagina(val id: Long, val fonte: Fonte, val indice: Int, val rot: Int) {
    /** altura / largura já considerando o giro (se o tamanho ainda não foi medido, é uma estimativa). */
    fun razao(): Float {
        val (w, h) = fonte.tamanho(indice)
        return if (rot % 180 != 0) w / h else h / w
    }
}

data class Estado(val paginas: List<Pagina>)

/**
 * Conjunto de páginas em organização (ou só leitura). A ordem/exclusão/giro têm histórico de desfazer e refazer.
 * Não existe nenhuma ferramenta de desenho ou edição de conteúdo.
 */
class Projeto(val nome: String, val chave: String?) {
    var estado by mutableStateOf(Estado(emptyList()))
    var sujo by mutableStateOf(false)
    var versaoHistorico by mutableIntStateOf(0)
        private set
    val fontes = ArrayList<Fonte>()
    private var contador = System.nanoTime()
    private val pilhaDesfazer = ArrayList<Estado>()
    private val pilhaRefazer = ArrayList<Estado>()
    private var base: Estado? = null

    fun novoId(): Long = ++contador
    val podeDesfazer: Boolean get() = versaoHistorico >= 0 && pilhaDesfazer.isNotEmpty()
    val podeRefazer: Boolean get() = versaoHistorico >= 0 && pilhaRefazer.isNotEmpty()

    fun confirmar(novo: Estado) {
        pilhaDesfazer.add(estado)
        if (pilhaDesfazer.size > 100) pilhaDesfazer.removeAt(0)
        pilhaRefazer.clear()
        estado = novo
        sujo = true
        versaoHistorico++
    }

    // arrastar páginas: o estado muda ao vivo, mas vira UM passo de desfazer
    fun comecarGesto() { base = estado }
    fun aplicarGesto(novo: Estado) { estado = novo }
    fun terminarGesto() {
        val b = base
        base = null
        if (b != null && b !== estado) {
            pilhaDesfazer.add(b)
            if (pilhaDesfazer.size > 100) pilhaDesfazer.removeAt(0)
            pilhaRefazer.clear()
            sujo = true
            versaoHistorico++
        }
    }

    fun desfazer() {
        if (pilhaDesfazer.isEmpty()) return
        pilhaRefazer.add(estado)
        estado = pilhaDesfazer.removeAt(pilhaDesfazer.size - 1)
        versaoHistorico++
    }

    fun refazer() {
        if (pilhaRefazer.isEmpty()) return
        pilhaDesfazer.add(estado)
        estado = pilhaRefazer.removeAt(pilhaRefazer.size - 1)
        versaoHistorico++
    }

    fun excluir(indices: Set<Int>) {
        if (indices.isEmpty() || indices.size >= estado.paginas.size) return
        confirmar(Estado(estado.paginas.filterIndexed { i, _ -> i !in indices }))
    }

    fun girar(indices: Set<Int>, graus: Int) =
        confirmar(Estado(estado.paginas.mapIndexed { i, p -> if (i in indices) p.copy(rot = ((p.rot + graus) % 360 + 360) % 360) else p }))

    fun duplicar(indices: Set<Int>) {
        val novas = ArrayList<Pagina>()
        estado.paginas.forEachIndexed { i, p ->
            novas.add(p)
            if (i in indices) novas.add(p.copy(id = novoId()))
        }
        confirmar(Estado(novas))
    }

    /** Move as páginas escolhidas para ANTES da página `destino` (0..n; n = fim), mantendo a ordem entre elas. */
    fun mover(indices: Set<Int>, destino: Int) {
        val ps = estado.paginas
        val mov = ps.filterIndexed { i, _ -> i in indices }
        val resto = ps.filterIndexed { i, _ -> i !in indices }
        val k = (0 until destino.coerceIn(0, ps.size)).count { it !in indices }
        confirmar(Estado(resto.take(k) + mov + resto.drop(k)))
    }

    fun inserir(paginas: List<Pagina>, posicao: Int) {
        val ps = estado.paginas
        val k = posicao.coerceIn(0, ps.size)
        confirmar(Estado(ps.take(k) + paginas + ps.drop(k)))
    }

    fun inverter() = confirmar(Estado(estado.paginas.reversed()))

    /** Ordena pelo nome do arquivo de origem (ordem natural: 2 antes de 10). */
    fun ordenarPorNome() {
        confirmar(Estado(estado.paginas.sortedWith { a, b -> comparaNatural(a.fonte.nomeDaPagina(a.indice), b.fonte.nomeDaPagina(b.indice)) }))
    }

    fun adicionarFonte(fonte: Fonte) {
        fontes.add(fonte)
        val novas = List(fonte.paginas) { i -> Pagina(novoId(), fonte, i, 0) }
        confirmar(Estado(estado.paginas + novas))
    }

    fun fechar() {
        fontes.forEach { it.fechar() }
        fontes.clear()
    }

    companion object {
        fun deFonte(nome: String, chave: String?, fonte: Fonte): Projeto {
            val p = Projeto(nome, chave)
            p.fontes.add(fonte)
            p.estado = Estado(List(fonte.paginas) { i -> Pagina(p.novoId(), fonte, i, 0) })
            return p
        }

        fun vazio(nome: String): Projeto = Projeto(nome, null)
    }
}
