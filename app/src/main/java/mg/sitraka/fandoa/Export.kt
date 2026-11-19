package mg.sitraka.fandoa

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object Export {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    private fun share(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun csv(ctx: Context, list: List<Payment>) {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("\uFEFF")
        sb.append("Nom;Catégorie;Motif;Montant;Payé;Reste;Statut;Échéance;Note\n")
        for (p in list) {
            sb.append(
                listOf(
                    q(p.name), q(p.category), q(p.reason), trimNum(p.amount), trimNum(p.paid),
                    trimNum(p.remaining), q(p.status().label), q(p.dueDate?.dateFr() ?: ""), q(p.note)
                ).joinToString(";")
            ).append("\n")
        }
        val f = File(dir(ctx), "fandoa-paiements.csv")
        f.writeText(sb.toString())
        share(ctx, f, "text/csv")
    }

    fun pdf(ctx: Context, list: List<Payment>) {
        val doc = PdfDocument()
        val normal = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint().apply { textSize = 12f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        val title = Paint().apply { textSize = 20f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        var no = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, no).create())
        var c = page.canvas
        var y = 60f
        c.drawText("Fandoa · Rapport des paiements", 40f, y, title); y += 22f
        c.drawText("Édité le " + System.currentTimeMillis().dateFr(), 40f, y, normal); y += 20f
        c.drawText(
            "Total ${list.sumOf { it.amount }.ar()}  ·  Payé ${list.sumOf { it.paid }.ar()}  ·  Reste ${list.sumOf { it.remaining }.ar()}",
            40f, y, bold
        ); y += 30f
        for (p in list) {
            if (y > 780f) {
                doc.finishPage(page); no++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, no).create())
                c = page.canvas; y = 50f
            }
            c.drawText("${p.name.take(30)} · ${p.category}", 40f, y, bold)
            c.drawText(p.status().label, 480f, y, bold); y += 15f
            c.drawText("Montant ${p.amount.ar()}   Payé ${p.paid.ar()}   Reste ${p.remaining.ar()}", 40f, y, normal); y += 15f
            val extra = listOfNotNull(
                p.dueDate?.let { "Échéance " + it.dateFr() },
                p.reason.takeIf { it.isNotBlank() }?.take(50)
            ).joinToString(" · ")
            if (extra.isNotBlank()) { c.drawText(extra, 40f, y, normal); y += 15f }
            y += 12f
        }
        doc.finishPage(page)
        val f = File(dir(ctx), "fandoa-rapport.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        share(ctx, f, "application/pdf")
    }

    fun receipt(ctx: Context, p: Payment) {
        val sb = StringBuilder()
        sb.append("REÇU DE PAIEMENT - Fandoa\n\n")
        sb.append("Bénéficiaire : ${p.name}\n")
        sb.append("Catégorie : ${p.category}\n")
        if (p.reason.isNotBlank()) sb.append("Motif : ${p.reason}\n")
        sb.append("Montant dû : ${p.amount.ar()}\n")
        sb.append("Déjà payé : ${p.paid.ar()}\n")
        sb.append("Reste à payer : ${p.remaining.ar()}\n")
        if (p.tranches.isNotEmpty()) {
            sb.append("\nVersements :\n")
            p.tranches.forEach { sb.append("• ${it.date.dateFr()} : ${it.amount.ar()}\n") }
        }
        sb.append("\nStatut : ${p.status().label}")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString())
        ctx.startActivity(Intent.createChooser(send, "Envoyer le reçu").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
