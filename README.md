# AiStack Android (`aistack-android`)

> **Cliente móvel oficial nativo do AiStack para Android**  
> Clone direto da experiência mobile do Claude e ChatGPT, conectado em tempo real ao seu núcleo desktop do AiStack através de relay próprio com cifra ponta a ponta (E2E), sem necessidade de VPN.

---

## 📱 Visão Geral

O **AiStack Android** é a extensão móvel do ecossistema [AiStack](https://github.com/diegovmsouza/AiStack). Ele permite monitorar, interagir e controlar remotamente seus agentes de inteligência artificial executados no seu computador de trabalho ou servidor doméstico.

Diferente de interfaces web móveis genéricas, o AiStack Android foi concebido como uma **aplicação 100% nativa em Kotlin e Jetpack Compose com Material 3**, projetada especificamente para telas de toque com rolagem fluida a 120Hz, feedback tátil, visualizador dinâmico de diffs e pensamentos, e notificações interativas para acompanhamento de tarefas de longa duração.

---

## 🛡️ Conexão Remota & Criptografia E2E (Zero-Trust)

A conexão entre o smartphone e o PC host é operada através do protocolo de relay proprietário (`relay/PROTOCOL.md`), garantindo conectividade em redes 4G/5G ou Wi-Fi sem portas abertas no roteador e sem exigência de VPN (Tailscale):

1. **Pareamento Instantâneo por QR Code ou Deep Link:**  
   O desktop exibe um QR Code com esquema `aistack://pair?relay=...&host=...&pk=...&code=...`. O app reconhece o link automaticamente através de leitor nativo com **CameraX e Google ML Kit**, ou via deep link do sistema operacional.
2. **Túnel Criptográfico Ponta a Ponta:**  
   - Troca de chaves **X25519** com derivação **HKDF-SHA256**.
   - Cifra autenticada **AES-256-GCM** (perfil 'a') ou **XChaCha20-Poly1305** (perfil 'x').
   - Contador u64 big-endian como Dado Adicional Autenticado (AAD) e janela anti-replay de 256 posições.
   - Prova de posse do host via assinatura **Ed25519** (`hostAuth`) contra ataques man-in-the-middle (MITM).
   - O servidor relay transita apenas bytes opacos cifrados, sem acesso a mensagens, arquivos ou chaves.

---

## ✨ Recursos Principais

- **Feed de Chat Nativo em Alta Performance:** Renderização de Markdown completo, blocos de código com destaque de sintaxe e botão de cópia com um toque.
- **Raciocínio Transparente (Thinking):** Visualização de pensamento do modelo em tempo real com cronômetro contínuo (`Pensando por Xs ▾` -> colapsável pós-execução).
- **Inspeção de Ferramentas & Diffs:** Visualização colorida de edições de código feitas pela IA (linhas adicionadas em verde, removidas em vermelho).
- **Aprovação de Permissões no Celular e na Notificação:** Cards interativos para autorizar comandos bash ou escritas de arquivo (`Permitir`, `Negar`, `Sempre permitir`). Quando o celular estiver bloqueado, aprove tarefas diretamente pela barra de notificações do Android através de **Ações Rápidas** (`[ Permitir ]` / `[ Negar ]`).
- **Composer Flutuante Completo:** Reconhecimento de fala nativo (Speech-to-Text), anexo de fotos e documentos da galeria, e fila de mensagens.
- **Identidade Visual AiStack:** Cores oficiais dos provedores (Claude âmbar/terracota, Gemini/Agy azul celeste, Codex grafite, Kimi azul, DeepSeek azul, GLM azul ciano, Qwen violeta/índigo) com suporte a tema claro e escuro.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Kotlin 2.2+
- **Interface:** Jetpack Compose (BOM 2025.x / Material 3)
- **Câmera & Visão:** CameraX 1.4+ e Google ML Kit Barcode Scanning
- **Rede & WebSocket:** OkHttp 4.12+ com reconexão resiliente e heartbeat
- **Criptografia:** Bouncy Castle (`X25519`, `Ed25519`) e Android Keystore / `javax.crypto` (`AES-GCM`)
- **Persistência Segura:** AndroidX Security Crypto (`EncryptedSharedPreferences`)
- **Tarefas de Background:** Android Foreground Service & NotificationManager com canais de alta prioridade

---

## 🚀 Como Compilar e Executar

### Pré-requisitos
- Android Studio Ladybug ou superior
- JDK 17 ou 21
- Android SDK com Platform API 35/36 e Build Tools instalados

### Compilação via Linha de Comando
```bash
# Compilar versão de depuração (Debug APK)
./gradlew assembleDebug

# Executar testes unitários do motor criptográfico
./gradlew testDebugUnitTest

# Instalar no dispositivo conectado via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Licença
Propriedade de Diego Souza / Amberwrite. Desenvolvido para o ecossistema AiStack.
