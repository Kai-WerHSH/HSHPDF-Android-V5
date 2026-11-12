// Criador da fonte: Henrique de Souza Henrique
package com.hsh.meupdf

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ACENTOS = listOf(0xFFE84A4A, 0xFF3B82F6, 0xFF22A55B, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899).map { it.toInt() }

@Composable
private fun CartaoAcao(titulo: String, sub: String, icone: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Ico(icone, Color.White, 26.dp) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Criar CBZ, PDF, ZIP e EPUB a partir de imagens de qualquer tipo (JPG, PNG, WebP, GIF, HEIC...) ou juntando arquivos. */
@Composable
fun CriarTela(cfg: Config, refSaida: Int, onImagens: () -> Unit, onPasta: () -> Unit, onArquivos: () -> Unit, onEscolherSaida: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Criar", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Monte um CBZ, PDF, ZIP ou EPUB a partir das suas imagens. Funciona com JPG, PNG, WebP, GIF, BMP, HEIC e outros formatos de imagem.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CartaoAcao("Escolher imagens", "Fotos, capturas, páginas digitalizadas...", "plus", onImagens)
        CartaoAcao("Escolher uma pasta de imagens", "Todas as imagens da pasta, em ordem de nome", "pasta", onPasta)
        CartaoAcao("Juntar ou converter arquivos", "PDF, CBZ, CBR ou EPUB para outro formato", "livro", onArquivos)
        Spacer(Modifier.height(6.dp))
        Text("Onde os arquivos criados são salvos", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nome = remember(refSaida) { cfg.nomePastaSaida }
            Text(if (cfg.pastaSaida == null) "Nenhuma pasta escolhida" else nome, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            OutlinedButton(onClick = onEscolherSaida) { Text(if (cfg.pastaSaida == null) "Escolher" else "Trocar") }
        }
        Text(
            "Dica: depois de escolher as imagens você pode arrastar as páginas para mudar a ordem, girar, excluir e criar vários formatos de uma vez.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AjustesTela(
    cfg: Config, tema: String, refSaida: Int, podeLimpar: Boolean,
    onTema: (String) -> Unit, onAcento: (Int) -> Unit, onEscolherSaida: () -> Unit
) {
    val ctx = LocalContext.current
    var cache by remember { mutableStateOf(Abertura.tamanhoCache(ctx)) }
    var som by remember { mutableStateOf(cfg.som) }
    var auto by remember { mutableStateOf(cfg.continuarAuto) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ajustes", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Text("Aparência", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        for ((rot, v) in listOf("Escuro" to "escuro", "Claro" to "claro", "Igual ao sistema" to "sistema")) {
            Row(Modifier.fillMaxWidth().clickable { onTema(v) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = tema == v, onClick = { onTema(v) })
                Text(rot, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            for (c in ACENTOS) Box(Modifier.size(34.dp).background(Color(c), RoundedCornerShape(8.dp)).clickable { onAcento(c) })
        }
        Spacer(Modifier.height(8.dp))
        Text("Leitura", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Abrir o próximo arquivo da pasta sozinho ao terminar", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = auto, onCheckedChange = { auto = it; cfg.continuarAuto = it })
        }
        Text("As demais opções (modo de leitura, fundo, brilho...) ficam na engrenagem dentro do leitor.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Criação", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val nome = remember(refSaida) { cfg.nomePastaSaida }
            Text("Salvar em: " + (if (cfg.pastaSaida == null) "(nenhuma)" else nome), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
            OutlinedButton(onClick = onEscolherSaida) { Text("Trocar") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Som ao terminar de criar", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
            Switch(checked = som, onCheckedChange = { som = it; cfg.som = it })
        }
        Spacer(Modifier.height(8.dp))
        Text("Armazenamento", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Os arquivos que você abre não são copiados para o app.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Cache (capas e arquivos temporários): ${Pastas.formataTamanho(cache)}", color = MaterialTheme.colorScheme.onBackground)
        Row {
            TextButton(enabled = podeLimpar, onClick = { Abertura.limparTudo(ctx); Abertura.limparCapas(ctx); cache = Abertura.tamanhoCache(ctx) }) { Text("Limpar cache") }
        }
        if (!podeLimpar) Text("Feche o leitor para limpar o cache.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("HSHPDF", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}
