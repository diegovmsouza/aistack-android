// Arquivo gerado por script (lucide-react 1.48.0, licença ISC — ver assets/licenses/LICENSE-lucide.txt).
// Não edite à mão: regenere a partir do pacote lucide do desktop.
package br.com.amberwrite.aistack.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Subconjunto do lucide como [ImageVector] (traço 2, pontas e junções arredondadas),
 * idêntico ao usado no desktop. Use com `Icon(Lucide.Send, null)`: o traço é tingido
 * pelo `tint` do Icon. Os vetores são criados sob demanda e memorizados.
 */
object Lucide {
    private fun lucide(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = "lucide.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Smartphone: ImageVector by lazy {
        lucide(
            "smartphone",
            "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-16a2 2 0 0 1 2 -2Z",
            "M12 18h.01",
        )
    }

    val Send: ImageVector by lazy {
        lucide(
            "send",
            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
            "m21.854 2.147-10.94 10.939",
        )
    }

    val Mic: ImageVector by lazy {
        lucide(
            "mic",
            "M12 19v3",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M12 2h0a3 3 0 0 1 3 3v7a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-7a3 3 0 0 1 3 -3Z",
        )
    }

    val MicOff: ImageVector by lazy {
        lucide(
            "mic-off",
            "M12 19v3",
            "M15 9.34V5a3 3 0 0 0-5.68-1.33",
            "M16.95 16.95A7 7 0 0 1 5 12v-2",
            "M18.89 13.23A7 7 0 0 0 19 12v-2",
            "m2 2 20 20",
            "M9 9v3a3 3 0 0 0 5.12 2.12",
        )
    }

    val Camera: ImageVector by lazy {
        lucide(
            "camera",
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
        )
    }

    val Image: ImageVector by lazy {
        lucide(
            "image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        )
    }

    val Paperclip: ImageVector by lazy {
        lucide(
            "paperclip",
            "m16 6-8.414 8.586a2 2 0 0 0 2.829 2.829l8.414-8.586a4 4 0 1 0-5.657-5.657l-8.379 8.551a6 6 0 1 0 8.485 8.485l8.379-8.551",
        )
    }

    val Folder: ImageVector by lazy {
        lucide(
            "folder",
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val FolderOpen: ImageVector by lazy {
        lucide(
            "folder-open",
            "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
        )
    }

    val File: ImageVector by lazy {
        lucide(
            "file",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
        )
    }

    val FileText: ImageVector by lazy {
        lucide(
            "file-text",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }

    val FileCode: ImageVector by lazy {
        lucide(
            "file-code",
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 12.5 8 15l2 2.5",
            "m14 12.5 2 2.5-2 2.5",
        )
    }

    val FilePen: ImageVector by lazy {
        lucide(
            "file-pen",
            "M12.659 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 14 2H6a2 2 0 0 0-2 2v9.34",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10.378 12.622a1 1 0 0 1 3 3.003L8.36 20.637a2 2 0 0 1-.854.506l-2.867.837a.5.5 0 0 1-.62-.62l.836-2.869a2 2 0 0 1 .506-.853z",
        )
    }

    val ChevronDown: ImageVector by lazy {
        lucide(
            "chevron-down",
            "m6 9 6 6 6-6",
        )
    }

    val ChevronUp: ImageVector by lazy {
        lucide(
            "chevron-up",
            "m18 15-6-6-6 6",
        )
    }

    val ChevronLeft: ImageVector by lazy {
        lucide(
            "chevron-left",
            "m15 18-6-6 6-6",
        )
    }

    val ChevronRight: ImageVector by lazy {
        lucide(
            "chevron-right",
            "m9 18 6-6-6-6",
        )
    }

    val ChevronsUpDown: ImageVector by lazy {
        lucide(
            "chevrons-up-down",
            "m7 15 5 5 5-5",
            "m7 9 5-5 5 5",
        )
    }

    val X: ImageVector by lazy {
        lucide(
            "x",
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }

    val Check: ImageVector by lazy {
        lucide(
            "check",
            "M20 6 9 17l-5-5",
        )
    }

    val Square: ImageVector by lazy {
        lucide(
            "square",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
        )
    }

    val CircleStop: ImageVector by lazy {
        lucide(
            "circle-stop",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "M10 9h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z",
        )
    }

    val Slash: ImageVector by lazy {
        lucide(
            "slash",
            "M22 2 2 22",
        )
    }

    val AtSign: ImageVector by lazy {
        lucide(
            "at-sign",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z",
            "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8",
        )
    }

    val Terminal: ImageVector by lazy {
        lucide(
            "terminal",
            "M12 19h8",
            "m4 17 6-6-6-6",
        )
    }

    val SquareTerminal: ImageVector by lazy {
        lucide(
            "square-terminal",
            "m7 11 2-2-2-2",
            "M11 13h4",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
        )
    }

    val GitBranch: ImageVector by lazy {
        lucide(
            "git-branch",
            "M15 6a9 9 0 0 0-9 9V3",
            "M15 6a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
            "M3 18a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
        )
    }

    val Bot: ImageVector by lazy {
        lucide(
            "bot",
            "M12 8V4H8",
            "M6 8h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z",
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2",
        )
    }

    val Sparkles: ImageVector by lazy {
        lucide(
            "sparkles",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2 20a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z",
        )
    }

    val Settings: ImageVector by lazy {
        lucide(
            "settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
        )
    }

    val Bell: ImageVector by lazy {
        lucide(
            "bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
        )
    }

    val BellOff: ImageVector by lazy {
        lucide(
            "bell-off",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M17 17H4a1 1 0 0 1-.74-1.673C4.59 13.956 6 12.499 6 8a6 6 0 0 1 .258-1.742",
            "m2 2 20 20",
            "M8.668 3.01A6 6 0 0 1 18 8c0 2.687.77 4.653 1.707 6.05",
        )
    }

    val User: ImageVector by lazy {
        lucide(
            "user",
            "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
            "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z",
        )
    }

    val Shield: ImageVector by lazy {
        lucide(
            "shield",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        )
    }

    val ShieldAlert: ImageVector by lazy {
        lucide(
            "shield-alert",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "M12 8v4",
            "M12 16h.01",
        )
    }

    val ShieldCheck: ImageVector by lazy {
        lucide(
            "shield-check",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4",
        )
    }

    val Wifi: ImageVector by lazy {
        lucide(
            "wifi",
            "M12 20h.01",
            "M2 8.82a15 15 0 0 1 20 0",
            "M5 12.859a10 10 0 0 1 14 0",
            "M8.5 16.429a5 5 0 0 1 7 0",
        )
    }

    val WifiOff: ImageVector by lazy {
        lucide(
            "wifi-off",
            "M12 20h.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
            "m2 2 20 20",
        )
    }

    val RefreshCw: ImageVector by lazy {
        lucide(
            "refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val Search: ImageVector by lazy {
        lucide(
            "search",
            "m21 21-4.34-4.34",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z",
        )
    }

    val Archive: ImageVector by lazy {
        lucide(
            "archive",
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8",
            "M10 12h4",
        )
    }

    val Pencil: ImageVector by lazy {
        lucide(
            "pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4",
        )
    }

    val PencilLine: ImageVector by lazy {
        lucide(
            "pencil-line",
            "M13 21h8",
            "m15 5 4 4",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        )
    }

    val Trash2: ImageVector by lazy {
        lucide(
            "trash-2",
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    val Plus: ImageVector by lazy {
        lucide(
            "plus",
            "M5 12h14",
            "M12 5v14",
        )
    }

    val Ellipsis: ImageVector by lazy {
        lucide(
            "ellipsis",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
            "M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
            "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
        )
    }

    val EllipsisVertical: ImageVector by lazy {
        lucide(
            "ellipsis-vertical",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
        )
    }

    val Copy: ImageVector by lazy {
        lucide(
            "copy",
            "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        )
    }

    val ExternalLink: ImageVector by lazy {
        lucide(
            "external-link",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        )
    }

    val Clock: ImageVector by lazy {
        lucide(
            "clock",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "M12 6v6l4 2",
        )
    }

    val Zap: ImageVector by lazy {
        lucide(
            "zap",
            "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
        )
    }

    val Layers: ImageVector by lazy {
        lucide(
            "layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
            "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
            "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
        )
    }

    val Cpu: ImageVector by lazy {
        lucide(
            "cpu",
            "M12 20v2",
            "M12 2v2",
            "M17 20v2",
            "M17 2v2",
            "M2 12h2",
            "M2 17h2",
            "M2 7h2",
            "M20 12h2",
            "M20 17h2",
            "M20 7h2",
            "M7 20v2",
            "M7 2v2",
            "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z",
            "M9 8h6a1 1 0 0 1 1 1v6a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-6a1 1 0 0 1 1 -1Z",
        )
    }

    val QrCode: ImageVector by lazy {
        lucide(
            "qr-code",
            "M4 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M17 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M4 16h3a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M21 16h-3a2 2 0 0 0-2 2v3",
            "M21 21v.01",
            "M12 7v3a2 2 0 0 1-2 2H7",
            "M3 12h.01",
            "M12 3h.01",
            "M12 16v.01",
            "M16 12h1",
            "M21 12v.01",
            "M12 21v-1",
        )
    }

    val Link: ImageVector by lazy {
        lucide(
            "link",
            "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
            "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
        )
    }

    val Sun: ImageVector by lazy {
        lucide(
            "sun",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }

    val Moon: ImageVector by lazy {
        lucide(
            "moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    val Battery: ImageVector by lazy {
        lucide(
            "battery",
            "M 22 14 L 22 10",
            "M4 6h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z",
        )
    }

    val BatteryLow: ImageVector by lazy {
        lucide(
            "battery-low",
            "M22 14v-4",
            "M6 14v-4",
            "M4 6h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z",
        )
    }

    val BatteryCharging: ImageVector by lazy {
        lucide(
            "battery-charging",
            "m11 7-3 5h4l-3 5",
            "M14.856 6H16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.935",
            "M22 14v-4",
            "M5.14 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.936",
        )
    }

    val BatteryFull: ImageVector by lazy {
        lucide(
            "battery-full",
            "M10 10v4",
            "M14 10v4",
            "M22 14v-4",
            "M6 10v4",
            "M4 6h12a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-8a2 2 0 0 1 2 -2Z",
        )
    }

    val Globe: ImageVector by lazy {
        lucide(
            "globe",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20",
        )
    }

    val ListChecks: ImageVector by lazy {
        lucide(
            "list-checks",
            "M13 5h8",
            "M13 12h8",
            "M13 19h8",
            "m3 17 2 2 4-4",
            "m3 7 2 2 4-4",
        )
    }

    val Wrench: ImageVector by lazy {
        lucide(
            "wrench",
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z",
        )
    }

    val Map: ImageVector by lazy {
        lucide(
            "map",
            "M14.106 5.553a2 2 0 0 0 1.788 0l3.659-1.83A1 1 0 0 1 21 4.619v12.764a1 1 0 0 1-.553.894l-4.553 2.277a2 2 0 0 1-1.788 0l-4.212-2.106a2 2 0 0 0-1.788 0l-3.659 1.83A1 1 0 0 1 3 19.381V6.618a1 1 0 0 1 .553-.894l4.553-2.277a2 2 0 0 1 1.788 0z",
            "M15 5.764v15",
            "M9 3.236v15",
        )
    }

    val ArrowUp: ImageVector by lazy {
        lucide(
            "arrow-up",
            "m5 12 7-7 7 7",
            "M12 19V5",
        )
    }

    val ArrowDown: ImageVector by lazy {
        lucide(
            "arrow-down",
            "M12 5v14",
            "m19 12-7 7-7-7",
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucide(
            "arrow-left",
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }

    val LoaderCircle: ImageVector by lazy {
        lucide(
            "loader-circle",
            "M21 12a9 9 0 1 1-6.219-8.56",
        )
    }

    val TriangleAlert: ImageVector by lazy {
        lucide(
            "triangle-alert",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    val Info: ImageVector by lazy {
        lucide(
            "info",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "M12 16v-4",
            "M12 8h.01",
        )
    }

    val CircleCheck: ImageVector by lazy {
        lucide(
            "circle-check",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "m16 9-5.5 5.5L8 12",
        )
    }

    val CircleX: ImageVector by lazy {
        lucide(
            "circle-x",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
            "m15 9-6 6",
            "m9 9 6 6",
        )
    }

    val Menu: ImageVector by lazy {
        lucide(
            "menu",
            "M4 5h16",
            "M4 12h16",
            "M4 19h16",
        )
    }

    val LogOut: ImageVector by lazy {
        lucide(
            "log-out",
            "m16 17 5-5-5-5",
            "M21 12H9",
            "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
        )
    }

    val History: ImageVector by lazy {
        lucide(
            "history",
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
            "M12 7v5l4 2",
        )
    }

    val MessageSquare: ImageVector by lazy {
        lucide(
            "message-square",
            "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
        )
    }

    val Brain: ImageVector by lazy {
        lucide(
            "brain",
            "M12 18V5",
            "M15 13a4.17 4.17 0 0 1-3-4 4.17 4.17 0 0 1-3 4",
            "M17.598 6.5A3 3 0 1 0 12 5a3 3 0 1 0-5.598 1.5",
            "M17.997 5.125a4 4 0 0 1 2.526 5.77",
            "M18 18a4 4 0 0 0 2-7.464",
            "M19.967 17.483A4 4 0 1 1 12 18a4 4 0 1 1-7.967-.517",
            "M6 18a4 4 0 0 1-2-7.464",
            "M6.003 5.125a4 4 0 0 0-2.526 5.77",
        )
    }

    val Play: ImageVector by lazy {
        lucide(
            "play",
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
        )
    }

    val ScanLine: ImageVector by lazy {
        lucide(
            "scan-line",
            "M3 7V5a2 2 0 0 1 2-2h2",
            "M17 3h2a2 2 0 0 1 2 2v2",
            "M21 17v2a2 2 0 0 1-2 2h-2",
            "M7 21H5a2 2 0 0 1-2-2v-2",
            "M7 12h10",
        )
    }

    val Keyboard: ImageVector by lazy {
        lucide(
            "keyboard",
            "M10 8h.01",
            "M12 12h.01",
            "M14 8h.01",
            "M16 12h.01",
            "M18 8h.01",
            "M6 8h.01",
            "M7 16h10",
            "M8 12h.01",
            "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2Z",
        )
    }

    val Monitor: ImageVector by lazy {
        lucide(
            "monitor",
            "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z",
            "M8 21L16 21",
            "M12 17L12 21",
        )
    }

    val Laptop: ImageVector by lazy {
        lucide(
            "laptop",
            "M18 5a2 2 0 0 1 2 2v8.526a2 2 0 0 0 .212.897l1.068 2.127a1 1 0 0 1-.9 1.45H3.62a1 1 0 0 1-.9-1.45l1.068-2.127A2 2 0 0 0 4 15.526V7a2 2 0 0 1 2-2z",
            "M20.054 15.987H3.946",
        )
    }

    val GitCommitHorizontal: ImageVector by lazy {
        lucide(
            "git-commit-horizontal",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z",
            "M3 12L9 12",
            "M15 12L21 12",
        )
    }

    val Hash: ImageVector by lazy {
        lucide(
            "hash",
            "M4 9L20 9",
            "M4 15L20 15",
            "M10 3L8 21",
            "M16 3L14 21",
        )
    }

    val ListTodo: ImageVector by lazy {
        lucide(
            "list-todo",
            "M13 5h8",
            "M13 12h8",
            "M13 19h8",
            "m3 17 2 2 4-4",
            "M4 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1Z",
        )
    }

    val CircleDashed: ImageVector by lazy {
        lucide(
            "circle-dashed",
            "M10.1 2.182a10 10 0 0 1 3.8 0",
            "M13.9 21.818a10 10 0 0 1-3.8 0",
            "M17.609 3.721a10 10 0 0 1 2.69 2.7",
            "M2.182 13.9a10 10 0 0 1 0-3.8",
            "M20.279 17.609a10 10 0 0 1-2.7 2.69",
            "M21.818 10.1a10 10 0 0 1 0 3.8",
            "M3.721 6.391a10 10 0 0 1 2.7-2.69",
            "M6.391 20.279a10 10 0 0 1-2.69-2.7",
        )
    }

    val CircleDot: ImageVector by lazy {
        lucide(
            "circle-dot",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0Z",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z",
        )
    }

    val SunMoon: ImageVector by lazy {
        lucide(
            "sun-moon",
            "M12 2v2",
            "M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715",
            "M16 12a4 4 0 0 0-4-4",
            "m19 5-1.256 1.256",
            "M20 12h2",
        )
    }

    val Lock: ImageVector by lazy {
        lucide(
            "lock",
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val Unplug: ImageVector by lazy {
        lucide(
            "unplug",
            "m19 5 3-3",
            "m2 22 3-3",
            "M6.3 20.3a2.4 2.4 0 0 0 3.4 0L12 18l-6-6-2.3 2.3a2.4 2.4 0 0 0 0 3.4Z",
            "M7.5 13.5 10 11",
            "M10.5 16.5 13 14",
            "m12 6 6 6 2.3-2.3a2.4 2.4 0 0 0 0-3.4l-2.6-2.6a2.4 2.4 0 0 0-3.4 0Z",
        )
    }

    /** Todos os ícones por nome lucide, útil para o catálogo e para buscas por string. */
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "smartphone" to Smartphone,
            "send" to Send,
            "mic" to Mic,
            "mic-off" to MicOff,
            "camera" to Camera,
            "image" to Image,
            "paperclip" to Paperclip,
            "folder" to Folder,
            "folder-open" to FolderOpen,
            "file" to File,
            "file-text" to FileText,
            "file-code" to FileCode,
            "file-pen" to FilePen,
            "chevron-down" to ChevronDown,
            "chevron-up" to ChevronUp,
            "chevron-left" to ChevronLeft,
            "chevron-right" to ChevronRight,
            "chevrons-up-down" to ChevronsUpDown,
            "x" to X,
            "check" to Check,
            "square" to Square,
            "circle-stop" to CircleStop,
            "slash" to Slash,
            "at-sign" to AtSign,
            "terminal" to Terminal,
            "square-terminal" to SquareTerminal,
            "git-branch" to GitBranch,
            "bot" to Bot,
            "sparkles" to Sparkles,
            "settings" to Settings,
            "bell" to Bell,
            "bell-off" to BellOff,
            "user" to User,
            "shield" to Shield,
            "shield-alert" to ShieldAlert,
            "shield-check" to ShieldCheck,
            "wifi" to Wifi,
            "wifi-off" to WifiOff,
            "refresh-cw" to RefreshCw,
            "search" to Search,
            "archive" to Archive,
            "pencil" to Pencil,
            "pencil-line" to PencilLine,
            "trash-2" to Trash2,
            "plus" to Plus,
            "ellipsis" to Ellipsis,
            "ellipsis-vertical" to EllipsisVertical,
            "copy" to Copy,
            "external-link" to ExternalLink,
            "clock" to Clock,
            "zap" to Zap,
            "layers" to Layers,
            "cpu" to Cpu,
            "qr-code" to QrCode,
            "link" to Link,
            "sun" to Sun,
            "moon" to Moon,
            "battery" to Battery,
            "battery-low" to BatteryLow,
            "battery-charging" to BatteryCharging,
            "battery-full" to BatteryFull,
            "globe" to Globe,
            "list-checks" to ListChecks,
            "wrench" to Wrench,
            "map" to Map,
            "arrow-up" to ArrowUp,
            "arrow-down" to ArrowDown,
            "arrow-left" to ArrowLeft,
            "loader-circle" to LoaderCircle,
            "triangle-alert" to TriangleAlert,
            "info" to Info,
            "circle-check" to CircleCheck,
            "circle-x" to CircleX,
            "menu" to Menu,
            "log-out" to LogOut,
            "history" to History,
            "message-square" to MessageSquare,
            "brain" to Brain,
            "play" to Play,
            "scan-line" to ScanLine,
            "keyboard" to Keyboard,
            "monitor" to Monitor,
            "laptop" to Laptop,
            "git-commit-horizontal" to GitCommitHorizontal,
            "hash" to Hash,
            "list-todo" to ListTodo,
            "circle-dashed" to CircleDashed,
            "circle-dot" to CircleDot,
            "sun-moon" to SunMoon,
            "lock" to Lock,
            "unplug" to Unplug,
        )
    }
}
