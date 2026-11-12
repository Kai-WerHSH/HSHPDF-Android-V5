// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícones desenhados no próprio app (não dependem de fontes nem de bibliotecas de ícones). */
@Composable
fun Ico(tipo: String, cor: Color = Color.White, tam: Dp = 24.dp) {
    Canvas(Modifier.size(tam)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.1f
        when (tipo) {
            "voltar" -> {
                drawLine(cor, Offset(w * 0.88f, h * 0.5f), Offset(w * 0.12f, h * 0.5f), sw)
                drawLine(cor, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.46f, h * 0.16f), sw)
                drawLine(cor, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.46f, h * 0.84f), sw)
            }
            "mais" -> for (k in 0..2) drawCircle(cor, w * 0.075f, Offset(w * 0.5f, h * (0.2f + 0.3f * k)))
            "ini" -> {
                drawRect(cor, Offset(w * 0.15f, h * 0.2f), Size(w * 0.1f, h * 0.6f))
                val p = Path()
                p.moveTo(w * 0.85f, h * 0.2f); p.lineTo(w * 0.3f, h * 0.5f); p.lineTo(w * 0.85f, h * 0.8f); p.close()
                drawPath(p, cor)
            }
            "fim" -> {
                drawRect(cor, Offset(w * 0.75f, h * 0.2f), Size(w * 0.1f, h * 0.6f))
                val p = Path()
                p.moveTo(w * 0.15f, h * 0.2f); p.lineTo(w * 0.7f, h * 0.5f); p.lineTo(w * 0.15f, h * 0.8f); p.close()
                drawPath(p, cor)
            }
            "girar", "girarE" -> {
                val espelho = if (tipo == "girarE") -1f else 1f
                scale(espelho, 1f, Offset(w / 2, h / 2)) {
                    drawArc(cor, 40f, 280f, false, Offset(w * 0.15f, h * 0.15f), Size(w * 0.7f, h * 0.7f), style = Stroke(sw))
                    val p = Path()
                    p.moveTo(w * 0.62f, h * 0.05f); p.lineTo(w * 0.95f, h * 0.22f); p.lineTo(w * 0.58f, h * 0.34f); p.close()
                    drawPath(p, cor)
                }
            }
            "modo" -> {
                drawRect(cor, Offset(w * 0.1f, h * 0.2f), Size(w * 0.34f, h * 0.6f), style = Stroke(sw))
                drawRect(cor, Offset(w * 0.56f, h * 0.2f), Size(w * 0.34f, h * 0.6f), style = Stroke(sw))
            }
            "fundo" -> {
                drawCircle(cor, w * 0.4f, Offset(w / 2, h / 2), style = Stroke(sw))
                drawArc(cor, -90f, 180f, true, Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f))
            }
            "pasta" -> {
                val p = Path()
                p.moveTo(w * 0.08f, h * 0.22f); p.lineTo(w * 0.38f, h * 0.22f); p.lineTo(w * 0.48f, h * 0.34f)
                p.lineTo(w * 0.92f, h * 0.34f); p.lineTo(w * 0.92f, h * 0.8f); p.lineTo(w * 0.08f, h * 0.8f); p.close()
                drawPath(p, cor)
            }
            "plus" -> {
                drawLine(cor, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f), sw * 1.3f)
                drawLine(cor, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), sw * 1.3f)
            }
            "lixo" -> {
                drawLine(cor, Offset(w * 0.15f, h * 0.25f), Offset(w * 0.85f, h * 0.25f), sw)
                drawLine(cor, Offset(w * 0.38f, h * 0.15f), Offset(w * 0.62f, h * 0.15f), sw)
                drawRect(cor, Offset(w * 0.24f, h * 0.3f), Size(w * 0.52f, h * 0.55f), style = Stroke(sw))
            }
            "check" -> {
                drawLine(cor, Offset(w * 0.15f, h * 0.55f), Offset(w * 0.4f, h * 0.8f), sw * 1.2f)
                drawLine(cor, Offset(w * 0.4f, h * 0.8f), Offset(w * 0.88f, h * 0.22f), sw * 1.2f)
            }
            "duplicar" -> {
                drawRect(cor, Offset(w * 0.1f, h * 0.1f), Size(w * 0.5f, h * 0.6f), style = Stroke(sw))
                drawRect(cor, Offset(w * 0.4f, h * 0.3f), Size(w * 0.5f, h * 0.6f), style = Stroke(sw))
            }
            "livro" -> {
                drawRect(cor, Offset(w * 0.18f, h * 0.12f), Size(w * 0.64f, h * 0.76f), style = Stroke(sw))
                drawLine(cor, Offset(w * 0.32f, h * 0.32f), Offset(w * 0.68f, h * 0.32f), sw * 0.8f)
                drawLine(cor, Offset(w * 0.32f, h * 0.5f), Offset(w * 0.68f, h * 0.5f), sw * 0.8f)
            }
            else -> { // engrenagem
                val c = Offset(w / 2, h / 2)
                for (k in 0 until 8) rotate(45f * k, c) { drawRect(cor, Offset(w * 0.43f, h * 0.04f), Size(w * 0.14f, h * 0.22f)) }
                drawCircle(cor, w * 0.3f, c, style = Stroke(w * 0.13f))
            }
        }
    }
}
