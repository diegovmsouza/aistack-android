package br.com.amberwrite.aistack.feature.chat.composer

import br.com.amberwrite.aistack.data.model.DirEntry
import br.com.amberwrite.aistack.data.model.EntryKind
import br.com.amberwrite.aistack.data.model.ModelInfo
import br.com.amberwrite.aistack.data.model.SlashCommand
import br.com.amberwrite.aistack.ui.designsystem.components.AttachmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerTextTest {

    private fun dir(name: String) = DirEntry(name, EntryKind.DIR, null, null)
    private fun file(name: String) = DirEntry(name, EntryKind.FILE, 10, null)
    private fun cmd(name: String, desktopOnly: Boolean = false) = SlashCommand(name, null, null, null, desktopOnly)
    private fun model(id: String, efforts: List<String> = emptyList(), default: String? = null, isDefault: Boolean = false) =
        ModelInfo(id, id.uppercase(), null, efforts, default, isDefault)

    // ---------------------------------------------------------------- gatilhos

    @Test fun slashTriggerAtStartOrAfterSpace() {
        assertEquals(Trigger(0, 3, "co"), findSlashTrigger("/co", 3))
        assertEquals(Trigger(4, 8, "com"), findSlashTrigger("oi, /com", 8))
        assertEquals(Trigger(0, 1, ""), findSlashTrigger("/", 1))
    }

    @Test fun slashTriggerIgnoresPathsAndClosedCommands() {
        assertNull(findSlashTrigger("a/b", 3))
        assertNull(findSlashTrigger("/compact ", 9))
        assertNull(findSlashTrigger("/co", 99))
    }

    @Test fun slashTriggerUsesCursorNotEnd() {
        assertEquals(Trigger(0, 2, "c"), findSlashTrigger("/compact", 2))
    }

    @Test fun mentionTriggerAcceptsPaths() {
        val t = findMentionTrigger("veja @src/ma", 12)!!
        assertEquals(5, t.start)
        assertEquals("src/ma", t.query)
        assertNull(findMentionTrigger("email@dominio", 13))
        assertEquals("", findMentionTrigger("@", 1)!!.query)
    }

    @Test fun replaceTriggerAvoidsDoubleSpace() {
        val text = "/co resto"
        val edit = replaceTrigger(text, Trigger(0, 3, "co"), "/compact ")
        assertEquals("/compact resto", edit.text)
        assertEquals(9, edit.cursor)
    }

    @Test fun replaceTriggerKeepsPrefix() {
        val edit = replaceTrigger("olá @sr", Trigger(4, 7, "sr"), "@src/")
        assertEquals("olá @src/", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    // ---------------------------------------------------------------- menções

    @Test fun appendMentionAddsSeparator() {
        assertEquals(TextEdit("@a.kt ", 6), appendMention("", "a.kt"))
        assertEquals("oi @a.kt ", appendMention("oi", "a.kt").text)
        assertEquals("oi @a.kt ", appendMention("oi ", "a.kt").text)
        assertEquals(TextEdit("oi", 2), appendMention("oi", "  "))
    }

    @Test fun relativizeMentionInsideAndOutsideProject() {
        assertEquals("src/a.kt", relativizeMention("/proj/src/a.kt", "/proj"))
        assertEquals("src/a.kt", relativizeMention("/proj/src/a.kt", "/proj/"))
        assertEquals(".", relativizeMention("/proj", "/proj"))
        assertEquals("/outro/a.kt", relativizeMention("/outro/a.kt", "/proj"))
        assertEquals("/projeto2/a.kt", relativizeMention("/projeto2/a.kt", "/proj"))
        assertEquals("/x/a.kt", relativizeMention("/x/a.kt", null))
    }

    @Test fun splitMentionQueryVariants() {
        assertEquals("" to "ma", splitMentionQuery("ma"))
        assertEquals("src" to "ma", splitMentionQuery("src/ma"))
        assertEquals("src/main" to "", splitMentionQuery("src/main/"))
        assertEquals("/" to "us", splitMentionQuery("/us"))
        assertEquals("/usr" to "lo", splitMentionQuery("/usr/lo"))
    }

    @Test fun parentDirVariants() {
        assertEquals("a", parentDir("a/b"))
        assertEquals("", parentDir("a"))
        assertEquals("", parentDir(""))
        assertEquals("/", parentDir("/usr"))
        assertEquals("/usr", parentDir("/usr/lib"))
        assertEquals("/", parentDir("/"))
    }

    @Test fun resolveListPathVariants() {
        assertEquals("/proj", resolveListPath("/proj", ""))
        assertEquals("/proj/src", resolveListPath("/proj/", "src"))
        assertEquals("/usr", resolveListPath("/proj", "/usr/"))
        assertEquals("/", resolveListPath("/proj", "/"))
        assertNull(resolveListPath(null, ""))
        assertNull(resolveListPath("", ""))
        assertEquals("src", resolveListPath(null, "src"))
    }

    @Test fun mentionInsertPathDirsEndWithSlash() {
        assertEquals("src/", mentionInsertPath("", dir("src")))
        assertEquals("src/main/", mentionInsertPath("src", dir("main")))
        assertEquals("src/a.kt", mentionInsertPath("src/", file("a.kt")))
        assertEquals("/usr", mentionInsertPath("/", file("usr")))
    }

    @Test fun filterEntriesOrdersDirsFirstAndHidesDotfiles() {
        val entries = listOf(
            file("main.kt"), dir("build"), file(".env"), dir(".git"), file("domain.kt"),
            DirEntry("sock", EntryKind.OTHER, null, null), dir("main"),
        )
        assertEquals(listOf("build", "main", "main.kt", "domain.kt"), filterEntries(entries, "").map { it.name })
        assertEquals(listOf("main", "main.kt", "domain.kt"), filterEntries(entries, "MAIN").map { it.name })
        assertEquals(listOf(".git", ".env", "main.kt", "domain.kt"), filterEntries(entries, ".").map { it.name })
        assertEquals(2, filterEntries(entries, "", limit = 2).size)
    }

    @Test fun filterEntriesPrefixBeforeSubstring() {
        val entries = listOf(file("domain.kt"), file("main.kt"))
        assertEquals(listOf("main.kt", "domain.kt"), filterEntries(entries, "ma").map { it.name })
    }

    // ---------------------------------------------------------------- paleta «/»

    @Test fun filterSlashByPrefixWithDesktopOnlyLast() {
        val cmds = listOf(cmd("/config", desktopOnly = true), cmd("compact"), cmd("clear"), cmd("review"))
        assertEquals(listOf("compact", "clear", "/config"), filterSlash(cmds, "c").map { it.name })
        assertEquals(listOf("compact", "/config"), filterSlash(cmds, "CO").map { it.name })
        assertEquals(4, filterSlash(cmds, "").size)
        assertTrue(filterSlash(cmds, "zz").isEmpty())
        assertEquals("config", cmds.first().bareName)
    }

    @Test fun filterSlashExactMatchFirst() {
        val cmds = listOf(cmd("compact-all"), cmd("compact"))
        assertEquals("compact", filterSlash(cmds, "compact").first().name)
    }

    // ---------------------------------------------------------------- ditado

    @Test fun joinDictationAddsSpaces() {
        assertEquals(TextEdit("olá mundo", 9), joinDictation("", " olá mundo ", ""))
        assertEquals(TextEdit("abc olá def", 7), joinDictation("abc", "olá", "def"))
        assertEquals(TextEdit("abc olá def", 7), joinDictation("abc ", "olá", " def"))
        assertEquals(TextEdit("abcdef", 3), joinDictation("abc", "  ", "def"))
    }

    @Test fun rmsToLevelClamps() {
        assertEquals(0f, rmsToLevel(-10f), 0f)
        assertEquals(0f, rmsToLevel(-2f), 0f)
        assertEquals(0.5f, rmsToLevel(4f), 1e-6f)
        assertEquals(1f, rmsToLevel(30f), 0f)
    }

    @Test fun uploadProgressNeverReachesFull() {
        var p = 0f
        repeat(500) {
            val next = uploadProgressStep(p)
            assertTrue(next >= p)
            p = next
        }
        assertTrue(p <= 0.95f)
        assertTrue(p > 0.9f)
    }

    // ---------------------------------------------------------------- imagem

    @Test fun sampleSizeKeepsLongestSideAtLeastMax() {
        assertEquals(1, sampleSizeFor(1000, 800, 2048))
        assertEquals(1, sampleSizeFor(4000, 3000, 2048))
        assertEquals(2, sampleSizeFor(4096, 3000, 2048))
        assertEquals(4, sampleSizeFor(3000, 9000, 2048))
        assertEquals(1, sampleSizeFor(0, 0, 2048))
    }

    @Test fun scaledSizeNeverUpscales() {
        assertEquals(1000 to 800, scaledSize(1000, 800, 2048))
        assertEquals(2048 to 1536, scaledSize(4000, 3000, 2048))
        assertEquals(1024 to 2048, scaledSize(2000, 4000, 2048))
    }

    @Test fun exifRotationMapping() {
        assertEquals(0, exifRotation(1))
        assertEquals(0, exifRotation(2))
        assertEquals(180, exifRotation(3))
        assertEquals(90, exifRotation(6))
        assertEquals(270, exifRotation(8))
        assertEquals(0, exifRotation(0))
    }

    @Test fun jpegNameReplacesExtension() {
        assertEquals("foto.jpg", jpegName("foto.heic"))
        assertEquals("foto.jpg", jpegName("foto"))
        assertEquals("a.b.jpg", jpegName("a.b.png"))
        assertEquals("imagem.jpg", jpegName(".png"))
    }

    @Test fun mimeAndKind() {
        assertEquals("image/jpeg", guessMime("A.JPG"))
        assertEquals("application/pdf", guessMime("doc.pdf"))
        assertEquals("application/octet-stream", guessMime("semextensao"))
        assertEquals(AttachmentKind.Image, attachmentKindFor("x.png", "image/png"))
        assertEquals(AttachmentKind.Code, attachmentKindFor("Main.kt", "application/octet-stream"))
        assertEquals(AttachmentKind.Text, attachmentKindFor("notas.md", "text/markdown"))
        assertEquals(AttachmentKind.Text, attachmentKindFor("x", "text/plain"))
        assertEquals(AttachmentKind.Audio, attachmentKindFor("a.m4a", "audio/mp4"))
        assertEquals(AttachmentKind.File, attachmentKindFor("a.zip", "application/zip"))
        assertTrue(shouldCompressImage("image/heic"))
        assertFalse(shouldCompressImage("image/gif"))
        assertFalse(shouldCompressImage("application/pdf"))
    }

    // ---------------------------------------------------------------- modelo/esforço

    @Test fun effortForModelKeepsCurrentWhenSupported() {
        val m = model("m", listOf("low", "medium", "high"), default = "medium")
        assertEquals("high", effortForModel(m, "high"))
        assertEquals("medium", effortForModel(m, "max"))
        assertEquals("medium", effortForModel(m, null))
        assertEquals("low", effortForModel(model("n", listOf("low", "high")), "max"))
        assertNull(effortForModel(model("o"), "high"))
    }

    @Test fun effortIndexFallsBackToDefault() {
        val levels = listOf("low", "medium", "high")
        assertEquals(2, effortIndex(levels, "high", "medium"))
        assertEquals(1, effortIndex(levels, "xx", "medium"))
        assertEquals(0, effortIndex(levels, null, null))
        assertEquals(0, effortIndex(emptyList(), "high", null))
    }

    @Test fun labels() {
        val catalog = listOf(model("a"), model("b", isDefault = true))
        assertEquals("A", modelLabel("a", catalog))
        assertEquals("zzz", modelLabel("zzz", catalog))
        assertEquals("B", modelLabel(null, catalog))
        assertNull(modelLabel(null, emptyList()))
        assertEquals("Médio", effortLabel("medium"))
        assertEquals("Muito alto", effortLabel("XHIGH"))
        assertEquals("Turbo", effortLabel("turbo"))
        assertNull(effortLabel(""))
        assertNull(effortLabel(null))
    }
}
